# Place Constraint DSL — Design

**Date**: 2026-05-26  
**Scope**: intention DSL 基盤 + Stage 2 `place` コマンド

---

## 概要

スキャナーが自動検出したクラス間関係に対して、ユーザーが明示的にレイアウト配置を指定できる
軽量 DSL（intention DSL）の基盤と `place` コマンドを実装する。

- DSL は複数行の文字列またはファイルとして入力
- `place <target> <direction> <reference>` 構文でクラスの相対配置を強制
- `arrow` コマンド（Stage 1）は対象外（将来追加）

---

## 新規パッケージ: `com.sosuisha.classdiagram.intention`

| クラス | 種別 | 責務 |
|--------|------|------|
| `IntentionParseException` | `RuntimeException` | 行番号付きパースエラー |
| `PlaceDirection` | `enum` | 配置方向トークン |
| `PlaceConstraint` | `record` | パース済み `place` 文 |
| `IntentionDslParser` | `class` | DSL 文字列 → `List<PlaceConstraint>` |

### `IntentionParseException`

```java
public class IntentionParseException extends RuntimeException {
    private final int lineNumber;
    public IntentionParseException(int lineNumber, String message) { ... }
    public int lineNumber() { ... }
}
```

メッセージ形式: `"line <N>: <detail>"`

### `PlaceDirection` 列挙型

| 値 | DSL トークン | 意味 |
|----|-------------|------|
| `ABOVE` | `above` | target を reference の上に |
| `BELOW` | `below` | target を reference の下に |
| `RIGHT_OF` | `right of` | target を reference の右に |
| `LEFT_OF` | `left of` | target を reference の左に |

### `PlaceConstraint` record

```java
public record PlaceConstraint(
    String target,      // ターゲットクラス単純名
    PlaceDirection direction,
    String reference,   // 基準クラス単純名
    int lineNumber      // エラー報告用
) {}
```

### `IntentionDslParser`

```java
public List<PlaceConstraint> parse(String dsl)
```

**パース規則**:
- 空行 → スキップ
- `#` で始まる行 → コメント、スキップ
- 動詞が `place` 以外 → `IntentionParseException`
- `place <target> <direction> <reference>` を解析
  - `right of` / `left of` は 2 トークンの方向句
- トークン数不足・不明方向 → `IntentionParseException(lineNumber, ...)`

---

## 既存クラスへの変更

### `ClassDiagramLayout`

**追加フィールド**:
```java
private List<PlaceConstraint> placeConstraints = List.of();
```

**追加メソッド** (メソッドチェーン可):
```java
public ClassDiagramLayout intention(String dsl)
public ClassDiagramLayout intentionFile(Path path)
```

- `intention(dsl)`: `IntentionDslParser` でパースして `placeConstraints` に設定
- `intentionFile(path)`: ファイルを UTF-8 で読み込み → `intention(dsl)` に委譲
  - ファイル読み込み失敗 → `UncheckedIOException`

**`layout()` の変更**:  
`minimizeCrossings()` の直後に `applyPlaceConstraints(orderedLayers)` を呼び出す。

### `ClassDiagramGenerator`

**追加フィールド**:
```java
private String intentionDsl = null;
private Path intentionFilePath = null;
```

**追加メソッド** (メソッドチェーン可):
```java
public ClassDiagramGenerator intention(String dsl)
public ClassDiagramGenerator intentionFile(Path path)
```

**`generate()` の変更**:  
`ClassDiagramLayout` を生成後、`intentionDsl` または `intentionFilePath` が設定されていれば
対応するメソッドを呼び出す（両方設定時は `intentionFile` が優先）。

---

## 制約適用アルゴリズム (`applyPlaceConstraints`)

`minimizeCrossings()` の後、座標確定前の `orderedLayers: List<List<ClassInfo>>` に対して
制約を順番に適用する。後の制約が前の制約を自然に上書きする（逐次処理による last-wins）。

### 前処理

`simpleName → ClassInfo` のマップを構築（同名クラスが複数存在する場合は最初の出現を使用）。  
不明クラス名 → `IntentionParseException(constraint.lineNumber(), "unknown class: <name>")`

### `BELOW` (target を reference より下のレイヤーへ)

1. `targetLayerIdx`、`refLayerIdx` を `orderedLayers` 内で検索
2. 異グループ（`groupIndex` が異なる）→ `IntentionParseException`
3. `targetLayerIdx > refLayerIdx` → 制約達成済み、no-op
4. `targetLayerIdx <= refLayerIdx`:
   - target を現在のレイヤーから削除（レイヤーが空になれば除去）
   - `refLayerIdx + 1` にレイヤーを確保（既存レイヤーがあれば先頭に挿入、なければ新規レイヤーを追加）
   - target を確保したレイヤーに追加

### `ABOVE` (target を reference より上のレイヤーへ)

1. `targetLayerIdx`、`refLayerIdx` を `orderedLayers` 内で検索
2. 異グループ → `IntentionParseException`
3. `targetLayerIdx < refLayerIdx` → 制約達成済み、no-op
4. `targetLayerIdx >= refLayerIdx`:
   - target を現在のレイヤーから削除（レイヤーが空になれば除去）
   - `refLayerIdx` の直前にレイヤーを確保（新規レイヤーを挿入することで reference が 1 つ下にずれる）
   - target を確保したレイヤーに追加

### `RIGHT_OF` (同一レイヤー内で target を reference の後ろへ)

1. 異グループ → `IntentionParseException`
2. 異レイヤー → `IntentionParseException`
3. 同レイヤー: target の現在インデックス ≤ reference のインデックス なら
   - target を削除し、reference の直後に挿入

### `LEFT_OF` (同一レイヤー内で target を reference の前へ)

`RIGHT_OF` の対称処理。

---

## エラー一覧

| 状況 | 例外 | メッセージ例 |
|------|------|-------------|
| 不明な動詞 | `IntentionParseException` | `line 3: unknown verb: 'arrow'` |
| トークン不足 | `IntentionParseException` | `line 5: invalid place statement` |
| 不明な方向 | `IntentionParseException` | `line 2: unknown direction: 'under'` |
| 不明クラス名 | `IntentionParseException` | `line 1: unknown class: 'Foo'` |
| 異グループ BELOW/ABOVE | `IntentionParseException` | `line 1: 'Item' and 'Service' are in different connected components` |
| 異グループ/異レイヤー RIGHT_OF/LEFT_OF | `IntentionParseException` | `line 2: 'Repo' and 'Service' are not in the same layer` |
| ファイル読み込み失敗 | `UncheckedIOException` | — |

---

## テスト方針

- `IntentionDslParser`: 正常パース・各エラーケースを単体テスト
- `ClassDiagramLayout`: 2〜4クラスの小レイアウトで `below`/`above`/`right of`/`left of` の座標検証
- 異グループ・異レイヤーの例外ケースも網羅

---

## 使用例

```java
// 文字列で指定
String svg = new ClassDiagramGenerator(30, 50, 30, 30, 60)
    .intention("""
        place Item below Order
        place Repository right of Service
        """)
    .generate(Path.of("target/classes"), "com.example");

// ファイルで指定
String svg = new ClassDiagramGenerator(30, 50, 30, 30, 60)
    .intentionFile(Path.of("diagram.intention"))
    .generate(Path.of("target/classes"), "com.example");

// ClassDiagramLayout 直接使用
var result = new ClassDiagramLayout(30, 50, 30, 30, 60)
    .intention("place Item below Order")
    .layout(layers, relations);
```
