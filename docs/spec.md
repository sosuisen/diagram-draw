# diagram-draw 仕様書

## 概要

コンポジション・集約・実現（implements）関係を自動検出してクラス図を生成するJava Mavenライブラリ。
コンパイル済み `.class` ファイルをJava Class-File APIで解析し、UML標準表記のSVGを出力する。

- **Maven座標**: `com.sosuisha:classdiagram-maven-plugin`
- **Java**: 25+

---

## パッケージ構成

```
com.sosuisha.classdiagram
├── SvgElement.java              インターフェース: SVG描画要素
├── ClassBox.java                クラスボックス（SVG要素）
├── Dependency.java              関係線（SVG要素）
├── DependencyType.java          関係の種類（列挙型）
├── SVGBuilder.java              SVGドキュメントビルダー
├── LayoutResult.java            レイアウト計算結果（record）
├── ClassDiagramLayout.java      レイアウトエンジン
├── ClassDiagramGenerator.java   ファサード（一括生成API）
└── analyzer/
    ├── ClassInfo.java           クラス識別子（record）
    ├── ClassRelation.java       クラス間関係（record）
    ├── ClassRelationScanner.java .classファイルスキャナー
    ├── ClassRelationSorter.java  トポロジカルソーター
    └── CircularRelationException.java 循環参照例外
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

### `DependencyType` 列挙型

クラス間の依存関係の種類。

| 値 | 意味 | SVG表現 |
|----|------|---------|
| `COMPOSITION` | コンポジション（強い所有関係） | 塗りつぶしダイアモンド＋線 |
| `AGGREGATION` | 集約（弱い所有関係） | 白抜きダイアモンド＋線 |
| `REALIZATION` | 実現（implements関係） | 破線＋白抜き三角 |

---

### `ClassBox` クラス

UMLクラスボックスを表す。`SvgElement` を実装。

- 名前・フィールド・メソッドの3コンパートメント構造
- 内容（名前・フィールド・メソッド）は構築後イミュータブル
- 幅・高さはコンテンツから自動計算される
- 描画位置は `setPosition()` で設定する（デフォルト: 原点）

#### API

```java
new ClassBox("MyClass")
new ClassBox("MyClass", List.of("id: Long"), List.of("getId(): Long"))

int w = box.width();
int h = box.height();
box.setPosition(50, 60);
String name = box.name();
```

#### `draw()` の出力構造

```xml
<g data-diagram-draw="box" data-diagram-draw-type="class" data-diagram-draw-name="{name}"
   transform="translate(x,y)">
  <!-- 輪郭線4本（スケッチ風パス） -->
  <text x="{cx}" y="{textY}" font-size="14" text-anchor="middle">{name}</text>
  <!-- 区切り線1 -->
  <!-- フィールドテキスト（0個以上） -->
  <!-- 区切り線2 -->
  <!-- メソッドテキスト（0個以上） -->
</g>
```

`setPosition()` 未呼び出しの場合、`transform` は省略される。

#### サイズ自動計算

| 定数 | 値 | 意味 |
|------|----|------|
| `FONT_SIZE` | 14 | フォントサイズ（px） |
| `ASCENT` | `FONT_SIZE * 4 / 5` | ベースラインより上の高さの概算 |
| `LINE_GAP` | 4 | 複数行の行間（px） |
| `PADDING_X` | 8 | 左右の余白（px） |
| `PADDING_Y` | 4 | コンパートメント上下の余白（px） |
| `MIN_WIDTH` | 100 | 最小幅（px） |

- **コンパートメント高さ**: `n * FONT_SIZE + (n-1) * LINE_GAP + PADDING_Y * 2`（n行; n=0は `PADDING_Y * 2`）
- **幅**: 最長テキスト長 × `CHAR_WIDTH` + `PADDING_X * 2`（最小 `MIN_WIDTH`）

---

### `Dependency` クラス

UML関係線を表す。`SvgElement` を実装。

```java
new Dependency(ClassBox source, ClassBox target, DependencyType type)
```

- `source`: 所有側（コンポジション/集約）または実装クラス（実現）
- `target`: 所有される側または インタフェース（実現）

#### `draw()` の出力構造

```xml
<g data-diagram-draw="dependency" data-diagram-draw-type="{type}">
  <!-- COMPOSITION/AGGREGATION: ダイアモンド(polygon) + 線(line) -->
  <!-- REALIZATION: 破線(line stroke-dasharray) + 白抜き三角(polygon fill=white) -->
</g>
```

`data-diagram-draw-type` の値: `"composition"` / `"aggregation"` / `"realization"`

#### 描画アルゴリズム概要

1. `source` 中心 → `target` 中心の方向ベクトルを正規化
2. `source` ボックス辺との交差点 `sp`、`target` ボックス辺との交差点 `tp` を求める
3. **COMPOSITION/AGGREGATION**: `sp` 付近にダイアモンドを描画し、`tp` まで線を引く
4. **REALIZATION**: `sp` から破線を引き、`tp`（インタフェース辺）に向かう白抜き三角を描画

---

### `SVGBuilder` クラス

SVGドキュメントを構築するビルダー。

```java
public SVGBuilder(int width, int height)
public SVGBuilder fontFamily(String fontFamily)  // フォント設定（オプション）
public SVGBuilder add(SvgElement element)         // 要素追加（メソッドチェーン可）
public String build()                             // SVG文字列を返す
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

## analyzerサブパッケージ

`.class` ファイルを解析してクラス間関係を抽出する。Java Class-File API を使用（リフレクション不使用）。

### `ClassInfo` record

クラスのパッケージ名と単純名を保持する識別子。

```java
public record ClassInfo(String packageName, String simpleName)

// 完全修飾名から生成
ClassInfo.fromFullyQualifiedName("com.example.Order")
// → ClassInfo("com.example", "Order")
```

---

### `ClassRelation` record

2クラス間の関係を表す。

```java
public record ClassRelation(
    ClassInfo sourceClassInfo,  // 所有側または実装クラス
    ClassInfo targetClassInfo,  // 所有される側またはインタフェース
    DependencyType type,        // COMPOSITION / AGGREGATION / REALIZATION
    boolean isMany              // Collectionフィールドの場合true（REALIZATIONは常にfalse）
)
```

---

### `ClassRelationScanner` クラス

コンパイル済みクラスをスキャンして `ClassRelation` のリストを返す。

```java
List<ClassRelation> scan(Path classRoot, String packageName)
```

- 対象: `packageName` およびサブパッケージ内の `.class` ファイル（内部クラス `$` を除く）
- **COMPOSITION**: フィールドの型がターゲットパッケージ内のクラスで、コンストラクタ引数でない
- **AGGREGATION**: フィールドの型がターゲットパッケージ内のクラスで、コンストラクタ引数である
- **REALIZATION**: `implements` でターゲットパッケージ内のインタフェースを実装している
- コレクション型（`List<T>` 等）は `isMany=true`
- 両クラスが同一パッケージ内に存在する場合のみ関係を生成

#### 検出するコレクション型

`Collection`, `List`, `Set`, `Queue`, `Deque`, `ArrayList`, `LinkedList`, `HashSet`, `LinkedHashSet`, `TreeSet`, `ArrayDeque`

---

### `ClassRelationSorter` クラス

`ClassRelation` のリストをトポロジカルソート（Kahn's BFS）し、レイヤーごとの `ClassInfo` リストを返す。

```java
List<List<ClassInfo>> sort(List<ClassRelation> relations)
```

- インデックス 0 が最上位レイヤー
- **COMPOSITION/AGGREGATION**: source（所有側）が上位レイヤー、target（所有される側）が下位
- **REALIZATION**: インタフェース（target）が上位レイヤー、実装クラス（source）が下位
- 循環参照を検出した場合 `CircularRelationException` をスロー

---

## レイアウト・生成クラス

### `LayoutResult` record

レイアウト計算結果。

```java
public record LayoutResult(
    List<ClassBox> boxes,           // 位置設定済みClassBox一覧
    List<Dependency> dependencies,  // 生成されたDependency一覧
    int canvasWidth,
    int canvasHeight
)
```

---

### `ClassDiagramLayout` クラス

レイヤーと関係リストからレイアウト位置を計算する。

```java
public ClassDiagramLayout(int horizontalGap, int verticalGap,
                           int canvasPaddingX, int canvasPaddingY)

LayoutResult layout(List<List<ClassInfo>> layers, List<ClassRelation> relations)
```

#### レイアウトアルゴリズム

1. **最長パス法によるレイヤー再割り当て**: Kahnレイヤーを入力とし、各ノードから末端までの最長パスを計算。末端ノードを最下層に集める
2. **中央揃え**: 各レイヤーはキャンバス横方向に中央揃えで配置
3. **キャンバスサイズ**: 全レイヤーの最大幅と全レイヤーの高さ合計から自動計算

---

### `ClassDiagramGenerator` クラス

スキャン → ソート → レイアウト → SVG生成のパイプラインをまとめたファサード。

```java
public ClassDiagramGenerator(int horizontalGap, int verticalGap,
                               int canvasPaddingX, int canvasPaddingY)

public ClassDiagramGenerator fontFamily(String fontFamily)
public String generate(Path classRoot, String packageName)
```

#### 内部パイプライン

```
ClassRelationScanner.scan()
        ↓ List<ClassRelation>
ClassRelationSorter.sort()
        ↓ List<List<ClassInfo>>
ClassDiagramLayout.layout()
        ↓ LayoutResult
SVGBuilder.build()
        ↓ String (SVG)
```

関係が0件の場合は空のSVGを返す。

---

## data属性の命名規則

| 属性 | 説明 |
|------|------|
| `data-diagram-draw="background"` | 白背景矩形（SVGBuilderが自動挿入） |
| `data-diagram-draw="box"` | クラスボックス |
| `data-diagram-draw-type="class"` | クラスボックスの種別 |
| `data-diagram-draw-name="{name}"` | クラス名 |
| `data-diagram-draw="dependency"` | 関係線グループ |
| `data-diagram-draw-type="composition"` | コンポジション関係線 |
| `data-diagram-draw-type="aggregation"` | 集約関係線 |
| `data-diagram-draw-type="realization"` | 実現（implements）関係線 |

---

## 使用例

### ファサードで自動生成（推奨）

```java
String svg = new ClassDiagramGenerator(30, 50, 30, 30)
    .fontFamily("HackGen")
    .generate(Path.of("target/classes"), "com.example");
```

### 手動パイプライン

```java
var relations = new ClassRelationScanner()
    .scan(Path.of("target/classes"), "com.example");

var layers = new ClassRelationSorter().sort(relations);

var result = new ClassDiagramLayout(30, 50, 30, 30).layout(layers, relations);

var builder = new SVGBuilder(result.canvasWidth(), result.canvasHeight())
    .fontFamily("HackGen");
result.boxes().forEach(builder::add);
result.dependencies().forEach(builder::add);
String svg = builder.build();
```

### ClassBoxとDependencyを直接組み合わせる

```java
var order = new ClassBox("Order", List.of("id: Long"), List.of("getId(): Long"));
order.setPosition(15, 75);
var item = new ClassBox("Item");
item.setPosition(200, 75);

var svg = new SVGBuilder(400, 200)
    .add(order)
    .add(item)
    .add(new Dependency(order, item, DependencyType.COMPOSITION))
    .build();
```

---

## 制約・スコープ外

- 内部クラス・匿名クラス（`$` を含むクラス）は対象外
- 配列フィールドは `isMany` 判定の対象外（コレクション型のみ）
- 異なるパッケージ間の関係は生成しない（同一スキャンパッケージ内のみ）
- クラス継承（`extends`）は未対応
- 静的フィールドは対象外
