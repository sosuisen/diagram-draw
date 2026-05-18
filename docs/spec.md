# diagram-draw 仕様書

## 概要

コンポジションと集約関係に特化したクラス図を自動作図するJava Mavenライブラリ。
出力フォーマットはSVG。

- **Maven座標**: `com.sosuisha:diagram-draw`
- **Java**: 25+

---

## クラス設計

### `SvgElement` インターフェース

SVGの描画要素を表す。

```java
public interface SvgElement {
    String draw();
}
```

---

### `ClassBox` クラス

UMLクラスボックスを表すクラス。`SvgElement` を実装する。

- 名前・フィールド・メソッドの3コンパートメント構造
- 内容（名前・フィールド・メソッド）は構築後イミュータブル
- 幅・高さはコンテンツから自動計算される
- 描画位置は `setPosition()` で設定する（デフォルト: 原点）

#### 描画アルゴリズム（2フェーズ）

```
Phase 1: ClassBox を内容だけで生成 → width() / height() でサイズ取得
Phase 2: レイアウトエンジンが setPosition(x, y) で位置を設定
Phase 3: SVGBuilder.add() → draw() で描画
```

#### API

```java
// 生成
new ClassBox("MyClass")
new ClassBox("MyClass", List.of("id: Long"), List.of("getId(): Long"))

// サイズ取得（Phase 1）
int w = box.width();
int h = box.height();

// 位置設定（Phase 2）
box.setPosition(50, 60);
```

#### `draw()` の出力構造

位置が原点の場合：

```xml
<g data-diagram-draw="box" data-diagram-draw-type="class" data-diagram-draw-name="{name}">
  <rect x="0" y="0" width="{width}" height="{height}" fill="none" stroke="black"/>
  <text ...>{name}</text>
  <line .../>  <!-- 区切り線1 -->
  <text ...>{field}</text>
  <line .../>  <!-- 区切り線2 -->
  <text ...>{method}</text>
</g>
```

`setPosition(x, y)` で位置を設定した場合、`<g>` タグに `transform="translate(x,y)"` が付加される。

#### サイズ自動計算

| 定数 | 値 | 意味 |
|------|----|------|
| `FONT_SIZE` | 14 | フォントサイズ（px） |
| `ASCENT` | `FONT_SIZE * 4 / 5` | ベースラインより上の高さの概算 |
| `LINE_GAP` | 4 | 複数行の行間（px） |
| `PADDING_X` | 8 | 左右の余白（px） |
| `PADDING_Y` | 4 | コンパートメント上下の余白（px） |

- **コンパートメント高さ**: `n * FONT_SIZE + (n-1) * LINE_GAP + PADDING_Y * 2`（n行のとき）
- **テキストベースライン**: `startY + PADDING_Y + ASCENT`
- **幅**: 最長テキスト長 × `CHAR_WIDTH` + `PADDING_X * 2`（最小100px）

---

### `SVGBuilder` クラス

SVGドキュメントを構築するビルダー。

```java
public class SVGBuilder {
    public SVGBuilder(int width, int height)
    public SVGBuilder add(SvgElement element)
    public String build()
}
```

#### `build()` の出力構造

```xml
<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}">
  <rect data-diagram-draw="background" width="{width}" height="{height}" fill="white"/>
  <!-- add() で追加した要素 -->
</svg>
```

- `add()` はメソッドチェーン可能（`this` を返す）
- 白背景矩形は自動挿入される

---

## data属性の命名規則

ライブラリ内部の要素を識別するため `data-diagram-draw` プレフィックスを使用する。

| 属性 | 説明 |
|------|------|
| `data-diagram-draw="background"` | 白背景矩形（SVGBuilderが自動挿入） |
| `data-diagram-draw="box"` | クラスボックス |
| `data-diagram-draw-type="class"` | Javaクラスの種別 |
| `data-diagram-draw-name="MyClass"` | クラス名 |

---

## 使用例

### フィールド・メソッドなし

```java
var svg = new SVGBuilder(300, 200)
    .add(new ClassBox("MyClass"))
    .build();
```

`ClassBox` の出力（`<g>` 部分）：

```xml
<g data-diagram-draw="box" data-diagram-draw-type="class" data-diagram-draw-name="MyClass">
  <rect x="0" y="0" width="100" height="38" fill="none" stroke="black"/>
  <text x="50" y="15" font-size="14" text-anchor="middle">MyClass</text>
  <line x1="0" y1="22" x2="100" y2="22" stroke="black"/>
  <line x1="0" y1="30" x2="100" y2="30" stroke="black"/>
</g>
```

フィールド・メソッドがない場合、`<text>` 要素は出力されず区切り線2本のみとなる。  
空コンパートメントの高さは `PADDING_Y * 2 = 8`（`FONT_SIZE` は含まない）。

### フィールド・メソッドあり・位置指定

```java
var box = new ClassBox("Order",
    List.of("id: Long", "status: String"),
    List.of("getId(): Long", "getStatus(): String"));
box.setPosition(50, 80);

var svg = new SVGBuilder(500, 400)
    .add(box)
    .build();
```

---

## 今後の予定

- レイアウトエンジン（ClassBoxの位置・サイズ自動計算）
- コンポジション・集約の関係線
