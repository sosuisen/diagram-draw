# 設計: 複数グループの横並びレイアウト

**日付**: 2026-05-23  
**対象機能**: 依存関係のない複数グループの検出と水平配置

---

## 概要

`ClassRelationScanner` がスキャンした結果、互いに依存のない複数の連結成分（グループ）が存在する場合、現在はそれらが1つの垂直スタックに混在してしまう。本機能では各グループを独立したレイアウト単位として認識し、横並びに配置する。

---

## アーキテクチャ・パイプライン

```
ClassRelationScanner.scan()
        ↓ List<ClassRelation>  (groupIndex=0)
ConnectedComponentSplitter.split()   ← 新クラス
        ↓ List<ClassRelation>  (groupIndex 割り当て済み)
ClassRelationSorter.sort()
        ↓ List<List<ClassInfo>>  (groupIndex 保持)
ClassDiagramLayout.layout()          ← groupGap 追加・横並び対応
        ↓ LayoutResult
SVGBuilder.build()
        ↓ String (SVG)
```

---

## 変更クラス一覧

| クラス | 変更種別 | 内容 |
|--------|---------|------|
| `ClassInfo` | 変更 | recordから通常クラスへ変換。`groupIndex` フィールド追加（mutable、デフォルト0） |
| `ConnectedComponentSplitter` | 新規 | 無向グラフで連結成分を検出し `groupIndex` を設定 |
| `ClassDiagramLayout` | 変更 | コンストラクタに `groupGap` 追加。`groupIndex` 基準で横並び配置 |
| `ClassDiagramGenerator` | 変更 | パイプラインに `ConnectedComponentSplitter.split()` を挿入 |
| `ClassRelationSorter` | 変更なし | — |

---

## `ClassInfo` クラス設計

### 変更方針

- `record` から `final class` に変換
- `groupIndex` は一方向パイプラインの途中で `ConnectedComponentSplitter` が書き込むため mutable（setter あり）
- `equals`/`hashCode` は `packageName` + `simpleName` + `stereotype` のみを使用（`groupIndex` は除外）

### API

```java
public final class ClassInfo {
    public ClassInfo(String packageName, String simpleName)
    public ClassInfo(String packageName, String simpleName, ClassStereotype stereotype)
    public static ClassInfo fromFullyQualifiedName(String fqn)
    public static ClassInfo fromFullyQualifiedName(String fqn, ClassStereotype stereotype)

    public String packageName()
    public String simpleName()
    public ClassStereotype stereotype()
    public int groupIndex()
    public void setGroupIndex(int groupIndex)

    // equals/hashCode: packageName + simpleName + stereotype のみ
}
```

---

## `ConnectedComponentSplitter` クラス設計

### パッケージ

`com.sosuisha.classdiagram.analyzer`

### API

```java
public class ConnectedComponentSplitter {
    public List<ClassRelation> split(List<ClassRelation> relations)
}
```

### アルゴリズム

1. `relations` から全 `ClassInfo` を収集
2. Union-Find で無向グラフ（辺の方向を無視）の連結成分を検出
3. 各連結成分に `0, 1, 2, ...` の `groupIndex` を割り当て（最初に出現した成分が `0`）
4. 各 `ClassInfo` に `setGroupIndex()` を呼び出す
5. 入力の `relations` をそのまま返す（`ClassInfo` はミュータブルなので参照先が書き換わっている）

### 前提・制約

- `relations` が空の場合は空リストをそのまま返す
- `@throws NullPointerException` relations が null の場合

---

## `ClassDiagramLayout` 変更設計

### コンストラクタ変更

```java
public ClassDiagramLayout(int horizontalGap, int verticalGap,
                           int canvasPaddingX, int canvasPaddingY, int groupGap)
```

### レイアウトアルゴリズム

1. `layers` 内の `ClassInfo` から `groupIndex` の一覧を昇順で取得
2. 各グループについて：
   - そのグループのノードのみを含むサブレイヤーを構築（空レイヤーは除去）
   - レイヤー幅・最大ボックス高さを計算
   - グループのコンテンツ幅 = サブレイヤーの最大幅
   - グループのコンテンツ高さ = 全サブレイヤーの最大ボックス高さ合計 + `verticalGap × (サブレイヤー数-1)`
3. 横並び配置：
   - グループ 0 の左端 = `canvasPaddingX`
   - グループ N の左端 = 前グループ右端 + `groupGap`
   - 各グループ内でレイヤーを中央揃え（グループコンテンツ幅基準）
4. 縦の揃え：全グループを上揃え（y = `canvasPaddingY` から開始）
5. キャンバスサイズ：
   - 幅 = 全グループコンテンツ幅合計 + `groupGap × (グループ数-1)` + `canvasPaddingX × 2`
   - 高さ = 最も高いグループのコンテンツ高さ + `canvasPaddingY × 2`

グループが1つの場合は `groupGap` が使われず現在と同じ動作になる。

---

## `ClassDiagramGenerator` 変更設計

### コンストラクタ変更

```java
public ClassDiagramGenerator(int horizontalGap, int verticalGap,
                               int canvasPaddingX, int canvasPaddingY, int groupGap)
```

### パイプライン変更

`scan()` と `sort()` の間に `ConnectedComponentSplitter.split()` を挿入する。

---

## テスト方針

| テスト対象 | 内容 |
|-----------|------|
| `ClassInfoTest` | `groupIndex` のデフォルト値（0）、setter、equals/hashCode が `groupIndex` を無視することの確認 |
| `ConnectedComponentSplitterTest` | 単一成分、2成分、3成分、空リストのケース |
| `ClassDiagramLayoutTest` | グループが2つ以上のとき横並びになることの確認（X座標、キャンバス幅） |
| 統合テスト | `ClassDiagramGeneratorTest` で2グループのフィクスチャを使ったSVG生成確認 |
