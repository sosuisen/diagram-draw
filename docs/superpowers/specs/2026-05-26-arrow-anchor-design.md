# Arrow Anchor DSL — Design

**Date**: 2026-05-26  
**Scope**: intention DSL への `arrow` コマンド追加（ソース辺・ターゲット辺の明示指定）

---

## 概要

`place` コマンドに続く intention DSL の第二機能として、`arrow` コマンドを追加する。
自動計算されるエッジのアンカー点（辺上の出発点・到達点）をユーザーが明示的に辺単位で上書きできる。

```
arrow <source> <target> from <edge>
arrow <source> <target> from <edge> to <edge>
```

- `<edge>`: `top` / `bottom` / `left` / `right`
- `from` のみ指定 → ソース辺を上書き、ターゲット辺は中心レイ法で自動計算
- `from … to …` → 両辺を明示指定

---

## 新規クラス（`com.sosuisha.classdiagram.intention` パッケージ）

| クラス | 種別 | 責務 |
|--------|------|------|
| `ArrowEdge` | `enum` | 辺トークン（TOP / BOTTOM / LEFT / RIGHT） |
| `ArrowConstraint` | `record` | パース済み `arrow` 文 |
| `ParseResult` | `record` | パーサーの出力（`PlaceConstraint` + `ArrowConstraint` の両リスト） |

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
    String source,       // ソースクラス単純名
    String target,       // ターゲットクラス単純名
    ArrowEdge fromEdge,  // ソース側出口辺
    ArrowEdge toEdge,    // ターゲット側入口辺（null = 自動）
    int lineNumber       // エラー報告用
) {
    public ArrowConstraint {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(fromEdge, "fromEdge must not be null");
        // toEdge は null 許容
    }
}
```

### `ParseResult` record

```java
public record ParseResult(
    List<PlaceConstraint> placeConstraints,
    List<ArrowConstraint> arrowConstraints
) {}
```

---

## `IntentionDslParser` の変更

### 戻り型変更

`parse(String dsl)` の戻り型を `List<PlaceConstraint>` → **`ParseResult`** に変更する。

### `arrow` 構文パース規則

```
arrow <source> <target> from <fromEdge> [to <toEdge>]
```

トークン列の期待値：

| パターン | トークン数 | 解析結果 |
|---------|-----------|---------|
| `arrow A B from bottom` | 5 | `ArrowConstraint(A, B, BOTTOM, null, line)` |
| `arrow A B from bottom to top` | 7 | `ArrowConstraint(A, B, BOTTOM, TOP, line)` |

パースエラー条件：
- `tokens[3]` が `"from"` でない → `"invalid arrow statement"`
- トークン数 < 5 → `"invalid arrow statement"`
- トークン数 == 6 → `"invalid arrow statement"`（`to` が来るべきところに辺が来た等）
- トークン数 > 7 → `"invalid arrow statement"`
- `fromEdge` が不明 → `"unknown edge: '<token>'"` 
- `toEdge` が不明 → `"unknown edge: '<token>'"`

---

## `Dependency` の変更

### 追加フィールド

```java
private boolean sourceAnchorLocked = false;
private boolean targetAnchorLocked = false;
```

### 追加メソッド

```java
public void lockSourceAnchor(double x, double y, double dirX, double dirY) {
    setSourceAnchor(x, y, dirX, dirY);
    this.sourceAnchorLocked = true;
}

public void lockTargetAnchor(double x, double y, double dirX, double dirY) {
    setTargetAnchor(x, y, dirX, dirY);
    this.targetAnchorLocked = true;
}

public boolean isSourceAnchorLocked() { return sourceAnchorLocked; }
public boolean isTargetAnchorLocked() { return targetAnchorLocked; }
```

---

## `ClassDiagramLayout` の変更

### 追加フィールド

```java
private List<ArrowConstraint> arrowConstraints = List.of();
```

### `intention()` / `intentionFile()` の変更

`IntentionDslParser().parse(dsl)` の戻り値が `ParseResult` になるため、`placeConstraints` と `arrowConstraints` の両方を設定する。

```java
public ClassDiagramLayout intention(String dsl) {
    Objects.requireNonNull(dsl, "dsl must not be null");
    var result = new IntentionDslParser().parse(dsl);
    this.placeConstraints = result.placeConstraints();
    this.arrowConstraints = result.arrowConstraints();
    return this;
}
```

### `layout()` の変更

`spreadDependencyEndpoints(dependencies)` の直前に `applyArrowConstraints(dependencies)` を追加する。

```java
applyArrowConstraints(dependencies);
spreadDependencyEndpoints(dependencies);
```

`layout()` Javadoc に `@throws IntentionParseException arrow制約でエラーが発生した場合` を追記。

### `applyArrowConstraints()`

```java
private void applyArrowConstraints(List<Dependency> dependencies) {
    if (arrowConstraints.isEmpty()) return;
    for (var constraint : arrowConstraints) {
        var matched = dependencies.stream()
            .filter(d -> d.source().name().equals(constraint.source())
                      && d.target().name().equals(constraint.target()))
            .toList();
        if (matched.isEmpty()) {
            throw new IntentionParseException(constraint.lineNumber(),
                "no relation from '" + constraint.source()
                + "' to '" + constraint.target() + "'");
        }
        for (var dep : matched) {
            double[] fromPt = edgeMidpoint(dep.source(), constraint.fromEdge());
            double[] fromDir = edgeOutwardDir(constraint.fromEdge());
            dep.lockSourceAnchor(fromPt[0], fromPt[1], fromDir[0], fromDir[1]);
            if (constraint.toEdge() != null) {
                double[] toPt = edgeMidpoint(dep.target(), constraint.toEdge());
                double[] toDir = edgeOutwardDir(constraint.toEdge());
                dep.lockTargetAnchor(toPt[0], toPt[1], toDir[0], toDir[1]);
            }
        }
    }
}
```

### `edgeMidpoint()` / `edgeOutwardDir()` ヘルパー

```java
private static double[] edgeMidpoint(ClassBox box, ArrowEdge edge) {
    return switch (edge) {
        case TOP    -> new double[]{ box.x() + box.width() / 2.0, box.y() };
        case BOTTOM -> new double[]{ box.x() + box.width() / 2.0, box.y() + box.height() };
        case LEFT   -> new double[]{ box.x(),                     box.y() + box.height() / 2.0 };
        case RIGHT  -> new double[]{ box.x() + box.width(),       box.y() + box.height() / 2.0 };
    };
}

private static double[] edgeOutwardDir(ArrowEdge edge) {
    return switch (edge) {
        case TOP    -> new double[]{ 0, -1 };
        case BOTTOM -> new double[]{ 0,  1 };
        case LEFT   -> new double[]{ -1, 0 };
        case RIGHT  -> new double[]{ 1,  0 };
    };
}
```

### `spreadDependencyEndpoints()` の変更

各グループのアンカー収集ループ内で、ロック済みのアンカーをスキップする：

```java
// ソース側
if (dep.isSourceAnchorLocked()) {
    // スキップ：intention 由来のアンカーは分散対象から除外
} else {
    groups.computeIfAbsent(new EdgeKey(src, srcEdge), k -> new ArrayList<>())
        .add(new AnchorInfo(dep, true, src, srcEdge, srcNatural));
}

// ターゲット側
if (dep.isTargetAnchorLocked()) {
    // スキップ
} else {
    groups.computeIfAbsent(new EdgeKey(tgt, tgtEdge), k -> new ArrayList<>())
        .add(new AnchorInfo(dep, false, tgt, tgtEdge, tgtNatural));
}
```

---

## `ClassDiagramGenerator` の変更

なし。`ClassDiagramLayout.intention()` が `ParseResult` の両リストを内部で処理するため、ジェネレーター側の変更は不要。

---

## エラー一覧

| 状況 | 例外 | メッセージ例 |
|------|------|-------------|
| `from` キーワードなし / トークン不足 | `IntentionParseException` | `line 1: invalid arrow statement` |
| 不明な辺トークン | `IntentionParseException` | `line 1: unknown edge: 'side'` |
| A→B 関係が存在しない | `IntentionParseException` | `line 1: no relation from 'A' to 'B'` |

---

## テスト方針

- `IntentionDslParserTest`: `ParseResult` 対応への更新 + `arrow` 構文の正常・エラーテスト
- `ClassDiagramLayoutTest`: `arrow from` / `arrow from … to` の座標検証、no-relation エラー
- `Dependency` に対する単体テストは不要（`lockSourceAnchor` は単純な setter）

---

## 使用例

```java
String svg = new ClassDiagramGenerator(30, 50, 30, 30, 60)
    .intention("""
        place TimelineServiceFake right of TimelineServiceImpl
        arrow TimelineService TimelineServiceImpl from bottom
        arrow TimelineService TimelineServiceFake from bottom to top
        """)
    .generate(Path.of("target/classes"), "com.example");
```
