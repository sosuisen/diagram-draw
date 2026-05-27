# diagram-draw 仕様書

## 概要

コンポジション・集約・実現（implements）・依存関係を自動検出してクラス図を生成する Java Maven ライブラリ。
コンパイル済み `.class` ファイルを Java Class-File API で解析し、UML 標準表記のスケッチ風 SVG を出力する。

- **Maven 座標**: `com.sosuisha:classdiagram-maven-plugin`
- **Java**: 25+

---

## パッケージ構成

```
com.sosuisha.classdiagram
├── SvgElement.java              インターフェース: SVG描画要素
├── ClassStereotype.java         ステレオタイプ列挙型（NONE, INTERFACE）
├── ClassBox.java                クラスボックス（SVG要素）
├── PackageGroupBox.java         パッケージ矩形（UMLタブ付きフォルダ形状、SVG要素）
├── Dependency.java              関係線（SVG要素）
├── DependencyType.java          関係の種類（列挙型）
├── SVGBuilder.java              SVGドキュメントビルダー
├── LayoutResult.java            レイアウト計算結果（record）
├── ClassDiagramLayout.java      レイアウトエンジン
├── ClassDiagramGenerator.java   ファサード（一括生成API）
├── analyzer/
│   ├── ClassInfo.java                 クラス識別子（final class）
│   ├── ClassRelation.java             クラス間関係（record）
│   ├── ClassRelationScanner.java      .classファイルスキャナー
│   ├── ClassRelationSorter.java       トポロジカルソーター
│   ├── ConnectedComponentSplitter.java 連結成分分割器
│   └── CircularRelationException.java 循環参照例外
└── intention/
    ├── IntentionParseException.java   DSL構文エラー例外
    ├── PlaceDirection.java            配置方向列挙型（ABOVE / BELOW / RIGHT_OF / LEFT_OF）
    ├── PlaceConstraint.java           パース済み place 制約（record）
    ├── ArrowEdge.java                 矢印アンカー辺列挙型（TOP / BOTTOM / LEFT / RIGHT）
    ├── ArrowConstraint.java           パース済み arrow 制約（record）
    ├── ParseResult.java               パーサー出力（PlaceConstraint + ArrowConstraint の両リスト）
    └── IntentionDslParser.java        DSL パーサー
```

---

## 描画要素クラス

### `SvgElement` インターフェース

```java
public interface SvgElement {
    String draw();
}
```

---

### `ClassStereotype` 列挙型

クラスのステレオタイプ。

| 値 | 意味 | SVG表現 |
|----|------|---------|
| `NONE` | 通常クラス（ステレオタイプなし） | クラス名のみ表示 |
| `INTERFACE` | インタフェース | `«interface»`（font-size 12）をクラス名の上に表示 |

将来の拡張: `ABSTRACT`（抽象クラス）、`ENUM`（列挙型）など。

---

### `DependencyType` 列挙型

クラス間の関係の種類。

| 値 | 意味 | SVG表現 |
|----|------|---------|
| `COMPOSITION` | コンポジション（強い所有関係） | 塗りつぶしダイアモンド＋曲線 |
| `AGGREGATION` | 集約（弱い所有関係） | 白抜きダイアモンド＋曲線 |
| `REALIZATION` | 実現（implements関係） | 破線＋白抜き三角 |
| `DEPENDENCY` | 依存（ローカル変数・メソッドパラメータ経由の使用関係） | 破線＋開いた矢頭（V字） |

---

### `ClassBox` クラス

UML クラスボックスを表す。`SvgElement` を実装した `final class`。

- 名前・フィールド・メソッドの3コンパートメント構造
- 内容（ステレオタイプ・名前・フィールド・メソッド）は構築後イミュータブル
- **デフォルトはクラス名のみ表示（`classNameOnly` モード）**。`showDetails()` で詳細表示に切り替える
- 描画位置は `setPosition()` で設定（デフォルト: 原点）
- 幅・高さはコンテンツから自動計算
- 輪郭線・区切り線はコンテンツのハッシュを種としたゆらぎを持つスケッチ風パス

#### API

```java
new ClassBox("MyClass")
new ClassBox("MyClass", ClassStereotype.INTERFACE)
new ClassBox("MyClass", List.of("id: Long"), List.of("getId(): Long"))
new ClassBox("MyClass", ClassStereotype.INTERFACE, List.of(), List.of())

box.setPosition(50, 60);
box.setFillColor("#FFFFBB");          // null で塗りなし
box.setStrokeColor("#000000");        // 枠線色（デフォルト #000000）
box.showDetails();                    // 詳細表示モードに切り替え（自分自身を返す）
box.picturesque(true);                // 装飾的描画（二重線）を有効化（自分自身を返す）

box.stereotype();                     // ClassStereotype
box.name();                           // String
box.fields();                         // List<String>（不変）
box.methods();                        // List<String>（不変）
box.x(); box.y();                     // 位置
box.width(); box.height();            // サイズ
box.fillColor(); box.strokeColor();   // 色
```

#### `draw()` の出力構造

```xml
<g data-diagram-draw="box" data-diagram-draw-type="class" data-diagram-draw-name="{name}"
   transform="translate(x,y)">
  <!-- fillColor 設定時のみ: <rect width=w height=h fill=fillColor/> -->
  <!-- 輪郭線4本（スケッチ風パス、picturesque=true で二重線） -->
  <!-- 名前コンパートメント（INTERFACE時はステレオタイプ行を上に追加） -->
  <!-- showDetails 時のみ:
       区切り線1
       フィールドテキスト（0個以上）
       区切り線2
       メソッドテキスト（0個以上） -->
</g>
```

`x = 0 && y = 0` の場合は `transform` 属性が省略される。

#### サイズ自動計算

| 定数 | 値 | 意味 |
|------|----|------|
| `FONT_SIZE` | 14 | フォントサイズ（px） |
| `ASCENT` | `FONT_SIZE * 4 / 5` | ベースラインより上の高さの概算 |
| `LINE_GAP` | 4 | 複数行の行間（px） |
| `PADDING_X` | 8 | 左右の余白（px） |
| `PADDING_Y` | 4 | コンパートメント上下の余白（px） |
| `NAME_ONLY_PADDING_Y` | 6 | 名前のみ表示時の上下余白（px） |
| `MIN_WIDTH` | 100 | 最小幅（px） |

- **幅**: 表示行の最大文字数 × `CHAR_WIDTH` + `PADDING_X * 2`（最小 `MIN_WIDTH`）
  - `classNameOnly` モード: 名前とステレオタイプのみ評価
  - `showDetails` モード: 名前・ステレオタイプ・フィールド・メソッドすべて評価
- **コンパートメント高さ**:
  - `showDetails` モード: `n * FONT_SIZE + (n-1) * LINE_GAP + PADDING_Y * 2`（n=0 のとき `PADDING_Y * 2`）
  - `classNameOnly` モード: `n * FONT_SIZE + (n-1) * LINE_GAP + NAME_ONLY_PADDING_Y * 2`

#### スケッチ表現

- 各辺は2本のベジエ曲線で `wobble` 量だけ揺らぐ
- 揺らぎ量は `sketchMax` で制御:
  - `classNameOnly` あるいは詳細表示でもフィールド／メソッドなし: `1.0`
  - フィールドかメソッドのみ存在: `1.5`
  - 両方存在: `2.0`
- `picturesque(true)` 時は輪郭線を 1px シフトした2本目のスケッチ線で重ね描き（手描きの重ね書きを模倣）

---

### `PackageGroupBox` クラス

サブパッケージのクラス群を囲む UML 標準のタブ付きフォルダ形状を描画する。`SvgElement` を実装した `final class`。

- 位置・寸法・ラベル・色は構築後イミュータブル
- 上部にタブ領域（高さ `TAB_HEIGHT = FONT_SIZE + LABEL_PADDING_Y * 2 = 20px`）、その下に本体矩形
- タブ幅 = `min(label幅 + LABEL_PADDING_X * 2, 本体幅)`
- 輪郭はクロックワイズに7線（タブ右辺のみ直線、他はスケッチ風）

#### API

```java
new PackageGroupBox(label, x, y, width, height)                                            // 塗りなし
new PackageGroupBox(label, x, y, width, height, fillColor)                                 // 塗りつぶし指定
new PackageGroupBox(label, x, y, width, height, fillColor, picturesque)                    // 影付き
new PackageGroupBox(label, x, y, width, height, fillColor, picturesque, strokeColor)       // 枠線色も指定

box.label(); box.x(); box.y(); box.width(); box.height();
box.fillColor(); box.picturesque(); box.strokeColor();
```

#### `draw()` の出力構造

```xml
<g data-diagram-draw="package-group" data-diagram-draw-name="{label}" transform="translate(x,y)">
  <!-- fillColor 指定時: タブ+本体の輪郭をなぞる polygon を塗りなし枠線で先に描画 -->
  <!-- タブ上辺（スケッチ） -->
  <!-- タブ右辺（直線。短いため揺らぎ不自然） -->
  <!-- 本体上辺（タブ右） / 本体右辺 / 本体下辺 / 左辺 -->
  <!-- picturesque=true: 右下隅から左上方向に5本の影線（後半は破線化） -->
  <text x="LABEL_PADDING_X" y="..." font-size="12">{label}</text>
</g>
```

| 定数 | 値 |
|------|----|
| `FONT_SIZE` | 12 |
| `LABEL_PADDING_X` | 6 |
| `LABEL_PADDING_Y` | 4 |
| `TAB_HEIGHT` | 20 |
| `SHADOW_LINE_COUNT` | 5 |
| `SHADOW_START_OFFSET` | 2 |
| `SHADOW_LINE_GAP` | 4 |
| `SKETCH_MAX` | 1.5 |

---

### `Dependency` クラス

UML 関係線を表す。`SvgElement` を実装した `final class`。

```java
new Dependency(ClassBox source, ClassBox target, DependencyType type)

dep.edgeColor("#000000");                        // メソッドチェーン可
dep.setSourceAnchor(x, y, dirX, dirY);            // 端点と出口方向を手動指定（任意）
dep.setTargetAnchor(x, y, dirX, dirY);            // 端点と入口方向を手動指定（任意）
dep.lockSourceAnchor(x, y, dirX, dirY);           // アンカーを固定設定（spread 対象外）
dep.lockTargetAnchor(x, y, dirX, dirY);           // アンカーを固定設定（spread 対象外）
dep.setSourceEdgeOverride(BoxEdge edge);           // intention 由来の辺上書き（spread には参加する）
dep.setTargetEdgeOverride(BoxEdge edge);           // intention 由来の辺上書き（spread には参加する）

dep.isSourceAnchorLocked();                       // ロック済みか
dep.isTargetAnchorLocked();
dep.isSourceEdgeOverridden();                     // intention によって辺が上書きされているか
dep.isTargetEdgeOverridden();

dep.source(); dep.target(); dep.type();
```

- `source`: 所有側（コンポジション/集約）、実装クラス（実現）、依存元（依存）
- `target`: 所有される側、インタフェース（実現）、依存先（依存）
- **アンカー API**: `ClassDiagramLayout` が同一辺を共有する複数エッジを分散配置するために使用する。未設定時は中心レイ法でソース／ターゲットの辺と交差する点を自動計算する
- **ロックアンカー API**: `lockSourceAnchor()` / `lockTargetAnchor()` で設定したアンカーは `spreadDependencyEndpoints()` の分散対象から除外される
- **辺上書き API**: `setSourceEdgeOverride()` / `setTargetEdgeOverride()` で辺を指定すると、`spreadDependencyEndpoints()` の辺グループ割り当てをその辺に固定する（分散配置には参加する）。intention DSL の `arrow` 制約が使用する
- 内部列挙型 `BoxEdge { TOP, RIGHT, BOTTOM, LEFT }`

#### `draw()` の出力構造

```xml
<g data-diagram-draw="dependency" data-diagram-draw-type="{type}">
  <!-- COMPOSITION / AGGREGATION:
       <polygon ... fill="(edgeColor|white)"/>  ダイアモンド
       <path d="M ... C ..." fill="none"/>       三次ベジエ曲線 -->
  <!-- REALIZATION:
       <path d="M ... C ..." stroke-dasharray="8,4"/>  破線曲線
       <polygon ... fill="white"/>                      白抜き三角 -->
  <!-- DEPENDENCY:
       <path d="M ... C ..." stroke-dasharray="8,4"/>  破線曲線
       <polyline points="..."/>                          V字の開いた矢頭 -->
</g>
```

`data-diagram-draw-type` の値: `"composition"` / `"aggregation"` / `"realization"` / `"dependency"`

#### 描画アルゴリズム

1. ソース中心 → ターゲット中心の方向ベクトルを正規化
2. ソース／ターゲット双方の辺との交差点（または手動アンカー）を取得
3. 各端点における外向き法線方向を取得し、それを出入口方向とする
4. 三次ベジエ曲線で両端を接続。各端点の制御点は外向き法線方向に端点ごと個別に
   `max(CURVE_OFFSET_MIN=20, |proj| * CURVE_OFFSET_RATIO=1/3)` だけ突き出す
   （ソース側は exitProj、ターゲット側は entryProj を使用）
5. 関係種別ごとに頭端の装飾（ダイアモンド／白抜き三角／矢頭）を描く
   - COMPOSITION/AGGREGATION: ソース辺上の交点からダイアモンドを外向きに配置（後端＝交点、前端から曲線開始）
   - REALIZATION: ターゲット辺の交点を頂点とする三角を曲線の入射方向に配置
   - DEPENDENCY: ターゲット辺の交点で `±30°` 開いた長さ10 px の V 字矢頭を描画

| 定数 | 値 |
|------|----|
| `DIAMOND_HALF_LEN` | 10 |
| `DIAMOND_HALF_WIDTH` | 5 |
| `TRIANGLE_LEN` | 16 |
| `TRIANGLE_HALF_WIDTH` | 6 |
| `ARROWHEAD_LEN` | 10 |
| `ARROWHEAD_HALF_ANGLE` | π/6（30°） |
| `CURVE_OFFSET_MIN` | 20 |
| `CURVE_OFFSET_RATIO` | 1/3 |

---

### `SVGBuilder` クラス

SVG ドキュメントを構築するビルダー。

```java
public SVGBuilder(int width, int height)             // width/height は正数必須
public SVGBuilder fontFamily(String fontFamily)       // フォント設定（オプション）
public SVGBuilder add(SvgElement element)             // 要素追加（メソッドチェーン可）
public String build()                                 // SVG文字列を返す
```

#### `build()` の出力構造

```xml
<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}">
  <style>text { font-family: '{fontFamily}'; }</style>  <!-- fontFamily設定時のみ -->
  <rect data-diagram-draw="background" width="{width}" height="{height}" fill="white"/>
  <!-- add() で追加した要素 -->
</svg>
```

---

## analyzer サブパッケージ

`.class` ファイルを解析してクラス間関係を抽出する。Java Class-File API を使用（リフレクション不使用）。

### `ClassInfo` クラス

クラスのパッケージ名・単純名・ステレオタイプを保持する識別子。`final class` として実装。

```java
new ClassInfo(packageName, simpleName)                          // stereotype = NONE
new ClassInfo(packageName, simpleName, stereotype)

ClassInfo.fromFullyQualifiedName("com.example.Order")
ClassInfo.fromFullyQualifiedName("com.example.IService", ClassStereotype.INTERFACE)

info.packageName(); info.simpleName(); info.stereotype();
info.groupIndex();                                              // デフォルト 0
info.setGroupIndex(1);                                          // ConnectedComponentSplitter が設定
info.addDependencyTargetFqn("com.example.Helper");              // ClassRelationScanner が設定
info.dependencyTargetFqns();                                    // 不変 Set<String>
```

`equals` / `hashCode` は `packageName + simpleName + stereotype` のみ（`groupIndex` および `dependencyTargetFqns` は同一性に含まれない）。

---

### `ClassRelation` record

2クラス間の関係を表す。

```java
public record ClassRelation(
    ClassInfo sourceClassInfo,  // 所有側・実装クラス・依存元
    ClassInfo targetClassInfo,  // 所有される側・インタフェース・依存先
    DependencyType type,        // COMPOSITION / AGGREGATION / REALIZATION
    boolean isMany              // Collectionフィールドの場合 true（REALIZATIONは常に false）
)
```

`DEPENDENCY` は `ClassRelation` としては保持されず、`ClassInfo.dependencyTargetFqns()` に格納される（同一連結成分内では描画されないため）。

---

### `ClassRelationScanner` クラス

コンパイル済みクラスをスキャンして `ClassRelation` のリストを返す。**2パス方式**:

1. 第1パスで全クラスのステレオタイプ（INTERFACE か NONE か）を収集
2. 第2パスで関係を構築

```java
List<ClassRelation> scan(Path classRoot, String packageName)
```

#### スキャン対象

- `packageName` およびサブパッケージ内の `.class` ファイル（内部クラス `$` を含むファイル名は除外）
- パッケージディレクトリが存在しない場合は空リストを返す

#### 検出する関係

| 種別 | 条件 |
|------|------|
| **COMPOSITION** | フィールドの型がターゲットパッケージ内のクラスで、コンストラクタ引数に同型がない |
| **AGGREGATION** | フィールドの型がターゲットパッケージ内のクラスで、コンストラクタ引数に同型がある |
| **REALIZATION** | `implements` でターゲットパッケージ内のインタフェースを実装している |
| **DEPENDENCY** | メソッドのパラメータ型／ジェネリックパラメータ／ローカル変数型がターゲットパッケージ内のクラス（自分自身およびフィールド型を除く） |

- `ACC_ENUM` フラグを持つフィールド（enum 定数）は対象外（自己参照による誤検出を防ぐため）
- コレクション型（`List<T>` 等）のフィールドはジェネリック引数を見て `isMany = true`
- 両クラスが同一スキャンパッケージ内に存在する場合のみ関係を生成

#### 検出するコレクション型

`Collection`, `List`, `Set`, `Queue`, `Deque`, `ArrayList`, `LinkedList`, `HashSet`, `LinkedHashSet`, `TreeSet`, `ArrayDeque`

#### DEPENDENCY 検出元

メソッドごとに以下を走査:

1. メソッド記述子のパラメータ型（非ジェネリック）
2. メソッド `Signature` 属性（ジェネリックパラメータ）
3. `Code` 属性内の `LocalVariableTable`（非ジェネリック）
4. `Code` 属性内の `LocalVariableTypeTable`（ジェネリック）

自クラス自身およびフィールド型として既に検出されているクラスは除外。

---

### `ConnectedComponentSplitter` クラス

`ClassRelation` のリストを走査して無向グラフの連結成分を Union-Find で検出し、各 `ClassInfo` に `groupIndex` を設定する。

```java
List<ClassRelation> split(List<ClassRelation> relations)
```

- relations をそのまま返す（ClassInfo の groupIndex が書き換わっている）
- relations 出現順で最初に現れた成分が `groupIndex = 0`
- `@throws NullPointerException` relations が null の場合

---

### `ClassRelationSorter` クラス

`ClassRelation` のリストをトポロジカルソート（Kahn's BFS）し、レイヤーごとの `ClassInfo` リストを返す。

```java
List<List<ClassInfo>> sort(List<ClassRelation> relations)
```

- インデックス 0 が最上位レイヤー（入次数 0 のクラス群）
- **COMPOSITION / AGGREGATION**: source（所有側）が上位レイヤー、target（所有される側）が下位
- **REALIZATION**: edge を反転し、interface（target）が上位、実装クラス（source）が下位
- 同一レイヤー内は `simpleName → packageName` の辞書順
- 重複辺は dedupe（`Set` 化）
- 入次数の残るノードがある場合は `CircularRelationException` をスロー

---

### `CircularRelationException`

`ClassRelationSorter` が循環参照を検出した際にスローする `RuntimeException`。
メッセージに巻き込まれたクラスの単純名一覧を含める。

---

## レイアウト・生成クラス

### `LayoutResult` record

レイアウト計算結果。

```java
public record LayoutResult(
    List<ClassBox> boxes,                  // 位置設定済みClassBox一覧
    List<Dependency> dependencies,         // 生成されたDependency一覧
    List<PackageGroupBox> packageGroups,   // PackageGroupBox一覧（無効時は空リスト）
    int canvasWidth,
    int canvasHeight
)
```

後方互換用に `(boxes, dependencies, canvasWidth, canvasHeight)` の4引数コンストラクタも提供。`packageGroups` は空リスト扱い。

---

### `ClassDiagramLayout` クラス

レイヤーと関係リストからレイアウト位置を計算するエンジン。

```java
public ClassDiagramLayout(int horizontalGap, int verticalGap,
                           int canvasPaddingX, int canvasPaddingY, int groupGap)

// メソッドチェーン可な設定群
.enableSubPackageGrouping(String rootPackage, int packageGap)
.classFillColor(String hex)            // デフォルト #FFFFBB
.interfaceFillColor(String hex)        // デフォルト #BDFFDE
.packageFillColor(String hex)          // デフォルト #f0f0f0（階層が深いほど暗化）
.packageStrokeColor(String hex)        // デフォルト #000000
.classBoxStrokeColor(String hex)       // デフォルト #000000
.edgeColor(String hex)                 // デフォルト #000000
.showDetails()                         // クラスボックス詳細表示
.picturesque(boolean)                  // 装飾的な描画
.intention(String dsl)                 // intention DSL 文字列（配置制約・矢印アンカーを設定）
.intentionFile(Path path)              // intention DSL ファイル（intention() より優先）

LayoutResult layout(List<List<ClassInfo>> layers, List<ClassRelation> relations)
```

#### レイアウトアルゴリズム

1. **ClassBox 生成**: 各 `ClassInfo` から `ClassBox` を生成し、ステレオタイプ・色・showDetails・picturesque を反映
2. **辺交差の最小化（重心法）**:
   - Sugiyama フレームワークのバリセンター法を上下交互に最大 12 パス実行
   - 各ノードの重心 = 隣接レイヤー内のインデックスの平均値で並び替える
   - REALIZATION は同一インタフェースを実装するクラスを連続ブロックとしてグループ化
3. **groupIndex ごとのサブレイヤー構築**: 空レイヤーは除去
4. **グループごとに**:
   - 各サブレイヤーの幅（ボックス幅合計 + `horizontalGap × (n-1)`）と最大高さを計算
   - グループのコンテンツ幅 = 最大レイヤー幅
5. **グループ配置**: 横方向に `groupGap` 間隔で左→右に並べる
6. **グループ内のボックス配置**: グループ幅基準で中央揃え、上→下に `verticalGap` 間隔
7. **キャンバスサイズ計算**:
   - 幅 = 全グループ幅合計 + `groupGap × (グループ数 - 1)` + `canvasPaddingX × 2`
   - 高さ = 最も高いグループのコンテンツ高さ + `canvasPaddingY × 2`
8. **Dependency 生成**: relations から `Dependency` を生成（edgeColor 適用）
9. **クロスグループ DEPENDENCY 矢印生成**: `ClassInfo.dependencyTargetFqns()` から、別グループのターゲットへの `DEPENDENCY` を追加生成。ただし `srcInfo` が実装するインタフェースがそのターゲットを既に依存している場合は冗長として省略
10. **サブパッケージグルーピング（オプション）**: 後述
11. **矢印アンカー適用**: `applyArrowConstraints()` が arrow 制約を `Dependency.setSourceEdgeOverride()/setTargetEdgeOverride()` で辺を上書きし、中点を `setSourceAnchor()/setTargetAnchor()` でデフォルトアンカーとして設定する。A→B の関係が存在しない場合は `IntentionParseException` をスロー
12. **エッジ端点の分散配置**: 同じ `(box, edge)` を共有する複数エッジの端点を辺上で等間隔に分散（自然交差順を保持）。辺上書き済み（`isSourceEdgeOverridden()/isTargetEdgeOverridden()` が `true`）の端点は指定辺のグループに参加して分散される（中点を自然位置として使用）。ロック済みアンカー（`isSourceAnchorLocked()/isTargetAnchorLocked()` が `true`）は分散対象から除外。`spreadDependencyEndpoints()` が `Dependency.setSourceAnchor()/setTargetAnchor()` を呼び出す

#### サブパッケージグルーピング

`enableSubPackageGrouping(rootPackage, packageGap)` を呼ぶと、ConnectedComponent 内をパッケージツリーに分解し再配置する:

1. 各 ConnectedComponent のクラス群から `rootPackage` 基準の相対パスでパッケージツリーを構築
2. ツリーを再帰的にレイアウト:
   - 直接クラスは上部に1スロットとして縦配置（同じ元レイヤーインデックスのクラスを同一行に中央寄せ）
   - 子ノード（サブパッケージ）はその下に **2D スカイラインの Bottom-Left fill** で詰め込み
   - 子の挿入順は重心法でソート（兄弟間 relation の重心で並べる）
3. 非ルートノード（`localLabel != ""`）は `PackageGroupBox` で囲む
4. ルートパッケージのクラスは矩形なしで上端配置
5. パッケージ矩形の塗り色は階層深度に応じて `factor = 0.9^(depth-1)` で暗化される

| 定数 | 値 |
|------|----|
| `GROUP_PADDING_LEFT` | 15 |
| `GROUP_PADDING_RIGHT` | 15 |
| `GROUP_PADDING_TOP` | 25 |
| `GROUP_PADDING_BOTTOM` | 10 |
| `MAX_CROSSING_PASSES` | 12 |
| `PACKAGE_DEPTH_DARKEN_FACTOR` | 0.9 |
| `EDGE_SPREAD_MARGIN_MAX` | 20.0 |
| `EDGE_SPREAD_MARGIN_RATIO` | 0.15 |

`PackageGroupBox` はレンダリング順序として外側を先に描画する必要があるため、`layoutPackageNode` の post-order 蓄積結果を `Collections.reverse()` してから返す。

---

### `ClassDiagramGenerator` クラス

スキャン → 連結成分分割 → ソート → レイアウト → SVG 生成のパイプラインをまとめたファサード。

```java
public ClassDiagramGenerator(int horizontalGap, int verticalGap,
                               int canvasPaddingX, int canvasPaddingY, int groupGap)

// メソッドチェーン可な設定群
.fontFamily(String)
.enableSubPackageGrouping(int packageGap)
.classFillColor(String)
.interfaceFillColor(String)
.packageFillColor(String)
.packageStrokeColor(String)
.classBoxStrokeColor(String)
.edgeColor(String)
.showDetails()
.picturesque(boolean)
.intention(String dsl)                 // intention DSL 文字列
.intentionFile(Path path)              // intention DSL ファイル（intention() より優先）

public String generate(Path classRoot, String packageName)
```

#### 内部パイプライン

```
ClassRelationScanner.scan()
        ↓ List<ClassRelation>
ConnectedComponentSplitter.split()
        ↓ List<ClassRelation>  (ClassInfo.groupIndex 設定済み)
ClassRelationSorter.sort()
        ↓ List<List<ClassInfo>>
ClassDiagramLayout.layout()
        ↓ LayoutResult (boxes, dependencies, packageGroups, w, h)
SVGBuilder
  .add(packageGroups)
  .add(boxes)
  .add(dependencies)
  .build()
        ↓ String (SVG)
```

関係が0件の場合は `canvasPaddingX * 2` × `canvasPaddingY * 2` の空 SVG を返す。

`enableSubPackageGrouping` は `ClassDiagramLayout` 側に `packageName` を渡して有効化する。

---

## data 属性の命名規則

| 属性 | 説明 |
|------|------|
| `data-diagram-draw="background"` | 白背景矩形（SVGBuilder が自動挿入） |
| `data-diagram-draw="box"` | クラスボックス |
| `data-diagram-draw-type="class"` | クラスボックスの種別 |
| `data-diagram-draw-name="{name}"` | クラス名（ボックス）／パッケージラベル |
| `data-diagram-draw="package-group"` | パッケージ矩形 |
| `data-diagram-draw="package-shadow-solid"` | パッケージ右下隅影（実線） |
| `data-diagram-draw="package-shadow-dashed"` | パッケージ右下隅影（破線） |
| `data-diagram-draw="dependency"` | 関係線グループ |
| `data-diagram-draw-type="composition"` | コンポジション関係線 |
| `data-diagram-draw-type="aggregation"` | 集約関係線 |
| `data-diagram-draw-type="realization"` | 実現（implements）関係線 |
| `data-diagram-draw-type="dependency"` | 依存関係線 |

---

## 使用例

### ファサードで自動生成（推奨）

```java
String svg = new ClassDiagramGenerator(30, 50, 30, 30, 60)
    .fontFamily("HackGen")
    .enableSubPackageGrouping(20)
    .classFillColor("#FFFFBB")
    .interfaceFillColor("#BDFFDE")
    .packageFillColor("#f0f0f0")
    .showDetails()
    .picturesque(true)
    .generate(Path.of("target/classes"), "com.example");
```

### 手動パイプライン

```java
var relations = new ClassRelationScanner()
    .scan(Path.of("target/classes"), "com.example");

new ConnectedComponentSplitter().split(relations);

var layers = new ClassRelationSorter().sort(relations);

var result = new ClassDiagramLayout(30, 50, 30, 30, 60)
    .enableSubPackageGrouping("com.example", 20)
    .showDetails()
    .picturesque(true)
    .layout(layers, relations);

var builder = new SVGBuilder(result.canvasWidth(), result.canvasHeight())
    .fontFamily("HackGen");
result.packageGroups().forEach(builder::add);
result.boxes().forEach(builder::add);
result.dependencies().forEach(builder::add);
String svg = builder.build();
```

### ClassBox と Dependency を直接組み合わせる

```java
var order = new ClassBox("Order", List.of("id: Long"), List.of("getId(): Long"))
    .showDetails();
order.setPosition(15, 75);
order.setFillColor("#FFFFBB");

var item = new ClassBox("Item");
item.setPosition(200, 75);

var svg = new SVGBuilder(400, 200)
    .add(order)
    .add(item)
    .add(new Dependency(order, item, DependencyType.COMPOSITION).edgeColor("#444"))
    .build();
```

---

## 制約・スコープ外

- 内部クラス・匿名クラス（`$` を含むクラス）は対象外
- 配列フィールドは `isMany` 判定の対象外（コレクション型のみ）
- 異なるスキャンパッケージ間の関係は生成しない（同一 `packageName` 配下のみ）
- クラス継承（`extends`）は未対応
- enum 定数（`ACC_ENUM` フラグを持つフィールド）は COMPOSITION/AGGREGATION の検出対象外（自己参照フィールドによる誤検出を防ぐため）。通常の `static` フィールドは対象

---

## intention パッケージ

`com.sosuisha.classdiagram.intention` パッケージは、自動レイアウトでは表現しきれないユーザの
作図意図（intention）を独自 DSL で記述するための型群を提供する。PlantUML との互換は意図しない。

文ごとに 1 行、行頭の動詞（`place` / `arrow`）で文種を識別する。空行・`#` 始まりのコメント行はスキップ。
識別子は単純名で記述する（FQN 不可）。

### `IntentionParseException`

DSL 構文エラーを表す `RuntimeException`。

```java
ex.lineNumber()   // エラーが発生した行番号（1始まり）
ex.getMessage()   // "line N: <message>" 形式
```

### `PlaceDirection` 列挙型

| 値 | DSL トークン | 意味 |
|----|------------|------|
| `ABOVE` | `above` | reference の上側に target を配置 |
| `BELOW` | `below` | reference の下側に target を配置 |
| `RIGHT_OF` | `right of` | reference の右側に target を配置 |
| `LEFT_OF` | `left of` | reference の左側に target を配置 |

### `PlaceConstraint` record

```java
public record PlaceConstraint(
    String target,           // 配置するクラスの単純名
    PlaceDirection direction, // 方向
    String reference,        // 基準クラスの単純名
    int lineNumber           // エラー報告用行番号
)
```

`target`・`direction`・`reference` は null 禁止。

### `ArrowEdge` 列挙型

| 値 | DSL トークン |
|----|-------------|
| `TOP` | `top` |
| `BOTTOM` | `bottom` |
| `LEFT` | `left` |
| `RIGHT` | `right` |

### `ArrowConstraint` record

```java
public record ArrowConstraint(
    String source,       // ソースクラスの単純名
    String target,       // ターゲットクラスの単純名
    ArrowEdge fromEdge,  // ソース側出口辺
    ArrowEdge toEdge,    // ターゲット側入口辺（null = 自動計算）
    int lineNumber       // エラー報告用行番号
)
```

`source`・`target`・`fromEdge` は null 禁止。`toEdge` は null 許容。

### `ParseResult` record

```java
public record ParseResult(
    List<PlaceConstraint> placeConstraints,   // 変更不可リスト
    List<ArrowConstraint> arrowConstraints    // 変更不可リスト
)
```

両リストは null 禁止かつ `List.copyOf()` でラップされたイミュータブルリスト。

### `IntentionDslParser` クラス

```java
ParseResult parse(String dsl)
// @throws NullPointerException dsl が null
// @throws IntentionParseException 構文エラー（行番号付きメッセージ）
```

#### place 構文

```
place <target> above <reference>
place <target> below <reference>
place <target> right of <reference>
place <target> left of <reference>
```

- `above` / `below`: トークン数 4
- `right of` / `left of`: トークン数 5（`of` を含む）

#### arrow 構文

```
arrow <source> <target> from <fromEdge>
arrow <source> <target> from <fromEdge> to <toEdge>
```

| トークン数 | 結果 |
|-----------|------|
| 5 | `fromEdge` のみ設定、`toEdge = null` |
| 7 | `fromEdge` と `toEdge` を両方設定 |
| 6 / 8以上 / <5 | `IntentionParseException` |

不明な辺トークンは `"unknown edge: '<token>'"` でエラー。

#### エラー一覧

| 状況 | メッセージ例 |
|------|-------------|
| 未知の動詞 | `line 1: unknown verb: 'connect'` |
| place 構文不正 | `line 1: invalid place statement` |
| arrow 構文不正 | `line 1: invalid arrow statement` |
| 不明な辺トークン | `line 1: unknown edge: 'side'` |
| A→B 関係が存在しない | `line 1: no relation from 'A' to 'B'` |

#### 語彙の使い分け

| 用途 | 語彙 | 例 |
|------|------|-----|
| 配置（B と A の相対位置） | 前置詞句 `above` / `below` / `right of` / `left of` | `place B below A` |
| 矢印アンカー（A のどの辺から出すか） | 名詞 `top` / `bottom` / `left` / `right` | `arrow A B from bottom` |

---

## intention DSL の使用例

```java
String svg = new ClassDiagramGenerator(30, 50, 30, 30, 60)
    .fontFamily("HackGen")
    .enableSubPackageGrouping(40)
    .picturesque(true)
    .intention("""
        place TimelineServiceFake right of TimelineServiceImpl
        arrow TimelineServiceImpl TimelineService from bottom
        arrow TimelineServiceFake TimelineService from bottom
        """)
    .generate(Path.of("target/classes"), "com.example");
```

- `place` 制約はトポロジカルソート後、座標確定前に `ClassBox` の配置順／レイヤーを調整する
- `arrow` 制約は `layout()` 内の `applyArrowConstraints()` で適用され、辺上書き済みの端点は指定辺のグループに参加して等間隔分散される（中点がデフォルトアンカーとして機能し、複数が同辺に集まると自動で広がる）
- `intentionFile(Path)` を指定した場合は `intention(String)` より優先される
- arrow 制約で指定した `source→target` の関係が存在しない場合は `IntentionParseException` をスロー
- 同名 A→B の関係が複数ある場合（COMPOSITION + AGGREGATION など）はすべてに適用される

---

## 今後の予定

- 絶対座標指定: `place B at (100, 50)`
- 関係種別の明示: `arrow A B kind=composition`
- 色・ラベルの上書き
- クラス継承（`extends`）の検出
