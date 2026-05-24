# サブパッケージグルーピング設計

**作成日:** 2026-05-24
**ステータス:** Draft

## 概要

クラス図のレイアウト時に、同一サブパッケージに属するクラスを矩形で囲み、空間的にまとめて表示する機能を追加する。本機能はオプションであり、既定では既存レイアウト挙動を維持する。

## 用語

- **ルートパッケージ**: `ClassDiagramGenerator.generate(classRoot, packageName)` に渡されるスキャン対象パッケージ名（例: `com.example.app`）
- **サブパッケージ**: ルートパッケージ配下の任意階層のパッケージ（例: `com.example.app.service`, `com.example.app.service.impl`）
- **相対サブパッケージ名**: ルートパッケージプレフィックスを除いた表記（例: `service`, `service.impl`）
- **ConnectedComponent**: `ConnectedComponentSplitter` が割り当てた `groupIndex` で識別される連結成分グループ
- **サブスロット**: 1 つの ConnectedComponent 内における 1 つの (groupIndex, packageName) ペアの占有領域

## 要件

### 機能要件

1. 同一 ConnectedComponent 内かつ同一サブパッケージに属するクラスを 1 つの矩形で囲む
2. パッケージは全階層を区別する（`service` と `service.impl` は別矩形）
3. 異なる ConnectedComponent に属するクラスは、同一サブパッケージであっても別矩形になる
4. 矩形の上部に相対サブパッケージ名のラベルを表示する
5. ルートパッケージ直下のクラスは矩形で囲わない
6. 矩形のスタイルは既存 `ClassBox` と同じスケッチ風（手書き感のある線）
7. ConnectedComponent 内で同サブパッケージのクラスが Sugiyama 層の縦方向に飛び飛びに配置されても、1 つの矩形にまとめられ、間に他サブパッケージのクラスが入り込まないこと
8. クロスサブパッケージ依存矢印は水平／斜めになることを許容する（Y 軸の依存方向は保証しない）

### 非機能要件

1. **オプション化**: 本機能は明示的に有効化されたときのみ動作する。既定（API 未呼び出し）では現在のレイアウト出力と完全に同一
2. **後方互換**: 既存の全テスト（`ClassDiagramLayoutTest`, `ClassDiagramGeneratorTest`, `DiagramDrawExampleTest` 他）が改修なしでパスすること
3. **既存コーディング規約準拠**: Javadoc、`Objects.requireNonNull` による事前条件、`var` 使用方針（[[java-coding-conventions]]）に従う

## アーキテクチャ

### パイプライン

```
ClassRelationScanner
  → ConnectedComponentSplitter (groupIndex 付与)
  → ClassRelationSorter (Sugiyama 層)
  → ClassDiagramLayout
       [既存ロジック] groupIndex ごとのサブレイヤー構築・配置
       ★[新規 (オプション ON 時のみ)]
          1. 各 groupIndex 内のクラスを packageName で分割
          2. ルートパッケージのサブスロットを左端に固定
          3. 残りのサブパッケージサブスロットを重心ベースで右側に水平整列
          4. 各サブスロット内を元の Sugiyama 層インデックス順で縦方向に配置
          5. 非ルートサブスロットに PackageGroupBox を生成
  → SVGBuilder (PackageGroupBox → ClassBox → Dependency の順で描画)
```

### オプション API

既存の fluent setter スタイル（`fontFamily()` と同形式）で提供する。

```java
// ClassDiagramGenerator
public ClassDiagramGenerator enableSubPackageGrouping(int packageGap)

// ClassDiagramLayout
public ClassDiagramLayout enableSubPackageGrouping(String rootPackage, int packageGap)
```

`ClassDiagramGenerator.enableSubPackageGrouping(int)` は内部で `generate()` 実行時に `ClassDiagramLayout.enableSubPackageGrouping(packageName, packageGap)` を呼び出す。

未呼び出し時は新ロジックを実行せず、既存レイアウトと完全に同じ出力を返す（`LayoutResult.packageGroups` は空リスト）。

## コンポーネント

### 新規クラス: `PackageGroupBox`

パッケージ: `com.sosuisha.classdiagram`
実装インタフェース: `SvgElement`
特性: イミュータブル

| フィールド | 型 | 説明 |
|---|---|---|
| `label` | `String` | 相対サブパッケージ名 |
| `x`, `y` | `int` | 矩形左上座標 |
| `width`, `height` | `int` | 矩形寸法 |

**メソッド:**
- コンストラクタ `(String label, int x, int y, int width, int height)`: 全フィールド初期化、`label` の null チェック、`width`/`height` ≤ 0 で `IllegalArgumentException`
- `draw()`: `ClassBox.sketchyLine()` と同じ手法で 4 辺を描画。左上にラベル `<text>` を配置し、ラベル文字の下に背景白の `<rect>` を敷いて枠線を抜く（UML パッケージフレーム風）。`<g data-diagram-draw="package-group" data-diagram-draw-name="{label}">` で囲む

### 変更クラス: `LayoutResult`

新フィールド `packageGroups` を追加。

```java
public record LayoutResult(
    List<ClassBox> boxes,
    List<Dependency> dependencies,
    List<PackageGroupBox> packageGroups,
    int canvasWidth,
    int canvasHeight
)
```

コンパクトコンストラクタで `packageGroups` も `List.copyOf` し null チェック。オプション OFF 時は空リスト。

### 変更クラス: `ClassDiagramLayout`

内部状態として `rootPackage`（`String`, 既定 `null`）と `packageGap`（`int`, 既定 `0`）を追加。

`enableSubPackageGrouping(String rootPackage, int packageGap)`:
- `rootPackage` null チェック
- `packageGap` < 0 で `IllegalArgumentException`
- 内部フィールドを設定し `this` を返す

`layout()` 内の分岐:
- `rootPackage == null` → 既存ロジックのみ実行（現状の Step 1〜8 をそのまま）
- `rootPackage != null` → 既存 Step 1, Step 2 (`minimizeCrossings`) の後、新ロジック（後述）に分岐

**新規 private メソッド:**

- `splitGroupByPackage(int groupIndex, List<List<ClassInfo>> groupOrderedLayers) → Map<String, PackageSlot>`: groupIndex 内のクラスを packageName で分割。slot key は `""`（ルート）または相対サブパッケージ名
- `orderPackageSlotsByBarycenter(Map<String, PackageSlot> slots, List<ClassRelation> relations) → List<String>`: ルート (`""`) を先頭固定、残りを重心ベースでソート。重心は対向側スロットの暫定インデックスで計算（既存 `minimizeCrossings` と同手法、1〜2 パス）
- `layoutPackageSlot(PackageSlot slot, int startX, int startY) → SlotLayoutResult`: スロット内クラスを「元 Sugiyama 層インデックス」昇順で縦配置。同一元層のメンバーは横並び。スロット幅・高さを算出
- `buildPackageGroupBox(PackageSlot slot, String labelKey) → PackageGroupBox`: 配置済みボックスのバウンディングからパディング適用して生成

**内部データクラス（仮）:**

```java
private record PackageSlot(
    String key,                         // "" = root, それ以外 = 相対サブパッケージ名
    List<ClassInfo> members,
    Map<ClassInfo, Integer> originalLayerIndex
)
```

### 変更クラス: `ClassDiagramGenerator`

`enableSubPackageGrouping(int packageGap)` を追加（fluent setter）。`packageGap` < 0 で `IllegalArgumentException`。

`generate()` 内で `enableSubPackageGrouping` が呼び出されていれば `ClassDiagramLayout.enableSubPackageGrouping(packageName, packageGap)` を呼ぶ。

SVG 描画順序の変更:

```java
result.packageGroups().forEach(builder::add);  // 背景
result.boxes().forEach(builder::add);
result.dependencies().forEach(builder::add);
```

## データフロー（オプション ON 時）

### Step A: groupIndex 内のサブパッケージ分割

入力: 各 groupIndex の `orderedLayers`、`rootPackage`

各クラスを slot key で分類:
- `info.packageName().equals(rootPackage)` → key = `""`
- それ以外 → key = `info.packageName().substring(rootPackage.length() + 1)`

slot ごとに `originalLayerIndex`（元の Sugiyama 層インデックス）も記録。

### Step B: サブスロット順序決定

1. slot key `""`（ルート）を**インデックス 0** に固定
2. 残り slot を相対パッケージ名のアルファベット順で初期インデックス付与（ルートが存在しなければインデックス 0 から、存在すれば 1 から）
3. 各非ルート slot `s` について、重心を以下で計算:
   - 対象 relation: `s` のメンバーが source または target に含まれる ConnectedComponent 内 relation のうち、相手側クラスが `s` と異なる slot に属するもの（ルート slot との接続も含む）
   - 重心 = (対象 relation 群における相手側 slot の暫定インデックス) の平均値
   - 対象 relation が 0 件の slot は現在のインデックスをそのまま重心とする
4. ルート以外を重心昇順で安定ソート。同点は前パスのインデックス順を保持（既存 `sortLayerByBarycenter` と同様の安定性）
5. 1〜2 パス繰り返して収束させ、最終順序 `[root, slot1, slot2, ...]` を得る

### Step C: 各サブスロット内の縦配置

各 slot 内:
- メンバーを `originalLayerIndex` 昇順でソート
- 同一 `originalLayerIndex` のメンバーは横並び（`horizontalGap` で区切り）
- 各「行」の幅 = メンバー幅合計 + (メンバー数 - 1) × `horizontalGap`
- スロット幅 = 全行の最大幅
- スロット高さ = 各行の最大高さ合計 + (行数 - 1) × `verticalGap`

各メンバーボックスに座標を設定:
- 行内 X = スロット開始 X + (スロット幅 - 行幅) / 2 + 累積幅
- 行 Y = スロット開始 Y + 累積高さ

### Step D: ConnectedComponent 内でのサブスロット水平配置

- サブスロットを Step B の順序で左から配置
- 各サブスロット間は `packageGap` で分離
- 全サブスロットの開始 Y は ConnectedComponent 開始 Y（揃える）
- ConnectedComponent 全体の幅 = サブスロット幅合計 + (サブスロット数 - 1) × `packageGap`
- ConnectedComponent 全体の高さ = サブスロット高さの最大値

### Step E: PackageGroupBox 生成

各非ルート（key ≠ `""`）サブスロットについて:
- バウンディング: サブスロット内全 `ClassBox` の min/max 座標
- パディング: 左右 `15px`、上 `25px`（ラベル領域確保）、下 `10px`
- ラベル: slot key（相対サブパッケージ名）

### Step F: ConnectedComponent 間配置

既存ロジックと同じ。ConnectedComponent 全体の幅・高さがサブスロット配置によって決まる点だけ変わる。

### Step G: Dependency 生成

既存ロジックと同じ。クロスサブパッケージ依存も `boxMap` を経由して `Dependency` を生成し、描画時に始点・終点座標を結ぶ。水平・斜め線として自然に描かれる。

## エラー処理

| 場所 | 条件 | 例外 |
|---|---|---|
| `PackageGroupBox` コンストラクタ | `label` が null | `NullPointerException` |
| `PackageGroupBox` コンストラクタ | `width` または `height` ≤ 0 | `IllegalArgumentException` |
| `ClassDiagramGenerator.enableSubPackageGrouping` | `packageGap` < 0 | `IllegalArgumentException` |
| `ClassDiagramLayout.enableSubPackageGrouping` | `rootPackage` が null | `NullPointerException` |
| `ClassDiagramLayout.enableSubPackageGrouping` | `packageGap` < 0 | `IllegalArgumentException` |

すべての public ref-type 引数は `Objects.requireNonNull` で検証し、Javadoc に `@throws NullPointerException` を記載する（[[feedback_null_checks]] 準拠）。

## テスト方針

### 新規テストファイル

**`PackageGroupBoxTest.java`** (`src/test/java/com/sosuisha/classdiagram/`)

- 引数バリデーション（null `label`、負数の寸法）
- `draw()` 出力に `data-diagram-draw="package-group"`、ラベル文字列、4 辺の `<path>` が含まれることを確認
- `data-diagram-draw-name` 属性にラベルが入ることを確認

**`ClassDiagramLayoutSubPackageTest.java`** (`src/test/java/com/sosuisha/classdiagram/`)

| ケース | 検証内容 |
|---|---|
| オプション未呼び出し（回帰防止） | `packageGroups` 空、全ボックス座標が既存挙動と完全一致 |
| 全クラスがルート | `packageGroups` 空 |
| 単一サブパッケージのみ | 1 個の `PackageGroupBox`、ラベル = 相対パッケージ名 |
| ルート + 1 サブパッケージ混在 | ルートが左端、サブパッケージが右側に配置 |
| 2 サブパッケージ + クロス relation | 重心ベース順序（relation 向きに応じて並び替え） |
| 多階層サブパッケージ | ラベルが `service.impl` のような完全相対名 |
| 同サブパッケージが Sugiyama 層で飛び飛び | 1 つの矩形でまとめて囲まれ、間に他サブパッケージのクラスが入らないこと |
| バウンディング矩形 | 全メンバーボックスを内包、パディング適用 |
| `enableSubPackageGrouping` 引数バリデーション | null `rootPackage`、負数の `packageGap` |

### 新規フィクスチャ

`src/test/java/com/sosuisha/classdiagram/analyzer/fixture/multipkg/` 配下に複数サブパッケージのテスト用クラス群を追加（具体的なクラス構成は実装時に確定）。

### 既存テスト回帰確認

- `ClassDiagramLayoutTest`, `ClassDiagramGeneratorTest`, `DiagramDrawExampleTest`, `LayoutResultTest`, `SVGBuilderTest`, `ClassBoxTest` 他、既存テストが改修なしで全件パスすること

### TDD 進行

[[tdd]] スキルで Red-Green-Refactor サイクルを回す前提。最初の Red は「オプション ON でルート + 1 サブパッケージ混在ケース」を推奨（既存挙動からの最小逸脱点）。

## 既存挙動への影響

- `ClassDiagramLayout` の既存コンストラクタシグネチャは変更しない
- `ClassDiagramGenerator` の既存コンストラクタシグネチャは変更しない
- `LayoutResult` レコードに `packageGroups` フィールドが追加されるため、既存テストで `LayoutResult` をパターンマッチ／コンストラクタ呼び出ししているコードがあれば追従が必要（既存コードを Grep で確認し、必要なら最小限の修正を行う）
- オプション未呼び出し時のレイアウト出力は完全に同一であることを回帰テストで保証する

## 未確定事項

なし（実装中に必要に応じて仕様調整）。

## 関連メモリ

- [[feedback_null_checks]] — 全 public ref-type param の `Objects.requireNonNull` 必須
- [[java-coding-conventions]] — Javadoc、契約プログラミング、`var` 使用方針
- [[tdd]] — Red-Green-Refactor サイクルでの実装進行
