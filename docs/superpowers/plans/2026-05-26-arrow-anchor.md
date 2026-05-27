# Arrow Anchor DSL Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** intention DSL に `arrow <source> <target> from <edge> [to <edge>]` 構文を追加し、エッジのアンカー辺をユーザーが明示指定できるようにする。

**Architecture:** `IntentionDslParser.parse()` の戻り型を `ParseResult`（`PlaceConstraint` + `ArrowConstraint` の両リスト）に変更し、`ClassDiagramLayout` が `applyArrowConstraints()` で `Dependency.lockSourceAnchor()` / `lockTargetAnchor()` を呼び出す。ロック済みアンカーは `spreadDependencyEndpoints()` の分散対象から除外する。

**Tech Stack:** Java 25, JUnit Jupiter 5.12.2, Maven (`mvn test`)

---

## ファイル一覧

| 操作 | パス |
|------|------|
| 新規作成 | `src/main/java/com/sosuisha/classdiagram/intention/ArrowEdge.java` |
| 新規作成 | `src/main/java/com/sosuisha/classdiagram/intention/ArrowConstraint.java` |
| 新規作成 | `src/main/java/com/sosuisha/classdiagram/intention/ParseResult.java` |
| 修正 | `src/main/java/com/sosuisha/classdiagram/intention/IntentionDslParser.java` |
| 修正 | `src/main/java/com/sosuisha/classdiagram/Dependency.java` |
| 修正 | `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java` |
| 修正 | `src/test/java/com/sosuisha/classdiagram/intention/IntentionDslParserTest.java` |
| 修正 | `src/test/java/com/sosuisha/classdiagram/DependencyTest.java` |
| 修正 | `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java` |

---

## Task 1: ArrowEdge / ArrowConstraint / ParseResult データクラス

**Files:**
- Create: `src/main/java/com/sosuisha/classdiagram/intention/ArrowEdge.java`
- Create: `src/main/java/com/sosuisha/classdiagram/intention/ArrowConstraint.java`
- Create: `src/main/java/com/sosuisha/classdiagram/intention/ParseResult.java`
- Test: `src/test/java/com/sosuisha/classdiagram/intention/IntentionDslParserTest.java`

- [ ] **Step 1: Write failing tests for new data classes**

`IntentionDslParserTest.java` のクラス末尾（`}` の前）に追加:

```java
    // --- ArrowConstraint ---

    @Test
    void arrowConstraintFieldsAreAccessible() {
        var c = new ArrowConstraint("A", "B", ArrowEdge.BOTTOM, ArrowEdge.TOP, 2);
        assertEquals("A", c.source());
        assertEquals("B", c.target());
        assertEquals(ArrowEdge.BOTTOM, c.fromEdge());
        assertEquals(ArrowEdge.TOP, c.toEdge());
        assertEquals(2, c.lineNumber());
    }

    @Test
    void arrowConstraintToEdgeCanBeNull() {
        var c = new ArrowConstraint("A", "B", ArrowEdge.BOTTOM, null, 1);
        assertNull(c.toEdge());
    }

    @Test
    void arrowConstraintNullSourceThrows() {
        assertThrows(NullPointerException.class,
            () -> new ArrowConstraint(null, "B", ArrowEdge.BOTTOM, null, 1));
    }

    @Test
    void arrowConstraintNullTargetThrows() {
        assertThrows(NullPointerException.class,
            () -> new ArrowConstraint("A", null, ArrowEdge.BOTTOM, null, 1));
    }

    @Test
    void arrowConstraintNullFromEdgeThrows() {
        assertThrows(NullPointerException.class,
            () -> new ArrowConstraint("A", "B", null, null, 1));
    }
```

- [ ] **Step 2: Run test to verify it fails (compile error)**

```
mvn test -Dtest=IntentionDslParserTest
```

Expected: コンパイルエラー（`ArrowConstraint`, `ArrowEdge` 未定義）

- [ ] **Step 3: Create ArrowEdge.java**

`src/main/java/com/sosuisha/classdiagram/intention/ArrowEdge.java`:

```java
package com.sosuisha.classdiagram.intention;

/** 矢印アンカーの辺を表す列挙型。DSLトークン: {@code top} / {@code bottom} / {@code left} / {@code right}。 */
public enum ArrowEdge {
    TOP, BOTTOM, LEFT, RIGHT
}
```

- [ ] **Step 4: Create ArrowConstraint.java**

`src/main/java/com/sosuisha/classdiagram/intention/ArrowConstraint.java`:

```java
package com.sosuisha.classdiagram.intention;

import java.util.Objects;

/**
 * パース済みの {@code arrow} 制約文を表すレコード。
 *
 * @param source     ソースクラスの単純名
 * @param target     ターゲットクラスの単純名
 * @param fromEdge   ソース側出口辺
 * @param toEdge     ターゲット側入口辺（{@code null} = 自動計算）
 * @param lineNumber エラー報告用の元行番号（1始まり）
 */
public record ArrowConstraint(
    String source,
    String target,
    ArrowEdge fromEdge,
    ArrowEdge toEdge,
    int lineNumber
) {
    public ArrowConstraint {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(fromEdge, "fromEdge must not be null");
    }
}
```

- [ ] **Step 5: Create ParseResult.java**

`src/main/java/com/sosuisha/classdiagram/intention/ParseResult.java`:

```java
package com.sosuisha.classdiagram.intention;

import java.util.List;
import java.util.Objects;

/**
 * {@link IntentionDslParser#parse} の解析結果。
 * {@link PlaceConstraint} と {@link ArrowConstraint} の両リストを保持する。
 *
 * @param placeConstraints パース済み {@code place} 制約のリスト（変更不可）
 * @param arrowConstraints パース済み {@code arrow} 制約のリスト（変更不可）
 */
public record ParseResult(
    List<PlaceConstraint> placeConstraints,
    List<ArrowConstraint> arrowConstraints
) {
    public ParseResult {
        Objects.requireNonNull(placeConstraints, "placeConstraints must not be null");
        Objects.requireNonNull(arrowConstraints, "arrowConstraints must not be null");
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

```
mvn test -Dtest=IntentionDslParserTest
```

Expected: 追加した 5 tests を含む全 IntentionDslParserTest が PASS

- [ ] **Step 7: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/intention/ArrowEdge.java
git add src/main/java/com/sosuisha/classdiagram/intention/ArrowConstraint.java
git add src/main/java/com/sosuisha/classdiagram/intention/ParseResult.java
git add src/test/java/com/sosuisha/classdiagram/intention/IntentionDslParserTest.java
git commit -m "feat: add ArrowEdge, ArrowConstraint, ParseResult data classes"
```

---

## Task 2: IntentionDslParser.parse() → ParseResult への移行

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/intention/IntentionDslParser.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/intention/IntentionDslParserTest.java`
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`

- [ ] **Step 1: IntentionDslParser.java を書き換える**

`IntentionDslParser.java` 全体を以下に置き換える:

```java
package com.sosuisha.classdiagram.intention;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * intention DSL文字列を解析して {@link ParseResult} を返すパーサー。
 *
 * <p>1行に1文を記述する。空行および {@code #} で始まるコメント行はスキップ。
 * サポートする動詞: {@code place}, {@code arrow}。
 */
public class IntentionDslParser {

    /**
     * DSL文字列を解析して {@link ParseResult} を返す。
     *
     * @param dsl intention DSL文字列（複数行可）
     * @return パース済み制約の結果（各リストは変更不可）
     * @throws NullPointerException    dslがnullの場合
     * @throws IntentionParseException 構文エラーがある場合（行番号を含むメッセージ）
     */
    public ParseResult parse(String dsl) {
        Objects.requireNonNull(dsl, "dsl must not be null");
        var placeList = new ArrayList<PlaceConstraint>();
        var arrowList = new ArrayList<ArrowConstraint>();
        var lines = dsl.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            int lineNumber = i + 1;
            var line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            var tokens = line.split("\\s+");
            switch (tokens[0]) {
                case "place" -> placeList.add(parsePlaceStatement(tokens, lineNumber));
                case "arrow" -> arrowList.add(parseArrowStatement(tokens, lineNumber));
                default -> throw new IntentionParseException(lineNumber,
                    "unknown verb: '" + tokens[0] + "'");
            }
        }
        return new ParseResult(List.copyOf(placeList), List.copyOf(arrowList));
    }

    private PlaceConstraint parsePlaceStatement(String[] tokens, int lineNumber) {
        if (tokens.length < 4) {
            throw new IntentionParseException(lineNumber, "invalid place statement");
        }
        var target = tokens[1];
        switch (tokens[2]) {
            case "above" -> {
                if (tokens.length != 4)
                    throw new IntentionParseException(lineNumber, "invalid place statement");
                return new PlaceConstraint(target, PlaceDirection.ABOVE, tokens[3], lineNumber);
            }
            case "below" -> {
                if (tokens.length != 4)
                    throw new IntentionParseException(lineNumber, "invalid place statement");
                return new PlaceConstraint(target, PlaceDirection.BELOW, tokens[3], lineNumber);
            }
            case "right" -> {
                if (tokens.length != 5 || !tokens[3].equals("of"))
                    throw new IntentionParseException(lineNumber, "invalid place statement");
                return new PlaceConstraint(target, PlaceDirection.RIGHT_OF, tokens[4], lineNumber);
            }
            case "left" -> {
                if (tokens.length != 5 || !tokens[3].equals("of"))
                    throw new IntentionParseException(lineNumber, "invalid place statement");
                return new PlaceConstraint(target, PlaceDirection.LEFT_OF, tokens[4], lineNumber);
            }
            default -> throw new IntentionParseException(lineNumber,
                "unknown direction: '" + tokens[2] + "'");
        }
    }

    private ArrowConstraint parseArrowStatement(String[] tokens, int lineNumber) {
        // arrow <source> <target> from <fromEdge> [to <toEdge>]
        // 最小: 5トークン (arrow A B from bottom)
        // 最大: 7トークン (arrow A B from bottom to top)
        if (tokens.length < 5 || !tokens[3].equals("from")) {
            throw new IntentionParseException(lineNumber, "invalid arrow statement");
        }
        if (tokens.length == 6 || tokens.length > 7) {
            throw new IntentionParseException(lineNumber, "invalid arrow statement");
        }
        var source = tokens[1];
        var target = tokens[2];
        var fromEdge = parseEdge(tokens[4], lineNumber);
        ArrowEdge toEdge = null;
        if (tokens.length == 7) {
            if (!tokens[5].equals("to")) {
                throw new IntentionParseException(lineNumber, "invalid arrow statement");
            }
            toEdge = parseEdge(tokens[6], lineNumber);
        }
        return new ArrowConstraint(source, target, fromEdge, toEdge, lineNumber);
    }

    private ArrowEdge parseEdge(String token, int lineNumber) {
        return switch (token) {
            case "top"    -> ArrowEdge.TOP;
            case "bottom" -> ArrowEdge.BOTTOM;
            case "left"   -> ArrowEdge.LEFT;
            case "right"  -> ArrowEdge.RIGHT;
            default -> throw new IntentionParseException(lineNumber,
                "unknown edge: '" + token + "'");
        };
    }
}
```

- [ ] **Step 2: IntentionDslParserTest.java のテストを ParseResult に対応させる**

以下の変更を加える（`parse()` が返す `ParseResult` から `placeConstraints()` を取り出す形に更新）。

`parsesPlaceBelow` テストを置き換え:

```java
    @Test
    void parsesPlaceBelow() {
        var result = parser.parse("place Item below Order");
        assertEquals(1, result.placeConstraints().size());
        var c = result.placeConstraints().get(0);
        assertEquals("Item", c.target());
        assertEquals(PlaceDirection.BELOW, c.direction());
        assertEquals("Order", c.reference());
        assertEquals(1, c.lineNumber());
    }
```

`parsesPlaceAbove` テストを置き換え:

```java
    @Test
    void parsesPlaceAbove() {
        var result = parser.parse("place Item above Order");
        assertEquals(1, result.placeConstraints().size());
        assertEquals(PlaceDirection.ABOVE, result.placeConstraints().get(0).direction());
        assertEquals("Item", result.placeConstraints().get(0).target());
        assertEquals("Order", result.placeConstraints().get(0).reference());
    }
```

`parsesPlaceRightOf` テストを置き換え:

```java
    @Test
    void parsesPlaceRightOf() {
        var result = parser.parse("place Repository right of Service");
        assertEquals(1, result.placeConstraints().size());
        assertEquals(PlaceDirection.RIGHT_OF, result.placeConstraints().get(0).direction());
        assertEquals("Repository", result.placeConstraints().get(0).target());
        assertEquals("Service", result.placeConstraints().get(0).reference());
    }
```

`parsesPlaceLeftOf` テストを置き換え:

```java
    @Test
    void parsesPlaceLeftOf() {
        var result = parser.parse("place A left of B");
        assertEquals(1, result.placeConstraints().size());
        assertEquals(PlaceDirection.LEFT_OF, result.placeConstraints().get(0).direction());
        assertEquals("A", result.placeConstraints().get(0).target());
        assertEquals("B", result.placeConstraints().get(0).reference());
    }
```

`skipsBlankLines` テストを置き換え:

```java
    @Test
    void skipsBlankLines() {
        var result = parser.parse("\n\n  \nplace A below B");
        assertEquals(1, result.placeConstraints().size());
        assertEquals(4, result.placeConstraints().get(0).lineNumber());
    }
```

`skipsCommentLines` テストを置き換え:

```java
    @Test
    void skipsCommentLines() {
        var result = parser.parse("# comment\nplace A below B");
        assertEquals(1, result.placeConstraints().size());
        assertEquals(2, result.placeConstraints().get(0).lineNumber());
    }
```

`parsesMultipleStatements` テストを置き換え:

```java
    @Test
    void parsesMultipleStatements() {
        var result = parser.parse("place A below B\nplace C right of D");
        assertEquals(2, result.placeConstraints().size());
        assertEquals(PlaceDirection.BELOW, result.placeConstraints().get(0).direction());
        assertEquals(PlaceDirection.RIGHT_OF, result.placeConstraints().get(1).direction());
        assertEquals(1, result.placeConstraints().get(0).lineNumber());
        assertEquals(2, result.placeConstraints().get(1).lineNumber());
    }
```

`returnsImmutableList` テストを置き換え:

```java
    @Test
    void returnsImmutableList() {
        var result = parser.parse("place A below B");
        assertThrows(UnsupportedOperationException.class,
            () -> result.placeConstraints().add(null));
        assertThrows(UnsupportedOperationException.class,
            () -> result.arrowConstraints().add(null));
    }
```

`throwsForUnknownVerb` テストを置き換え（`arrow` は今後 valid になるので `connect` に変更）:

```java
    @Test
    void throwsForUnknownVerb() {
        var ex = assertThrows(IntentionParseException.class,
            () -> parser.parse("connect A B"));
        assertEquals(1, ex.lineNumber());
        assertTrue(ex.getMessage().contains("1"));
        assertTrue(ex.getMessage().contains("connect"));
    }
```

`lineNumberReflectsActualLineInMultilineInput` テストを置き換え（line 2 も `arrow` は valid になるので `connect` に変更）:

```java
    @Test
    void lineNumberReflectsActualLineInMultilineInput() {
        var ex = assertThrows(IntentionParseException.class,
            () -> parser.parse("place A below B\nconnect X Y"));
        assertEquals(2, ex.lineNumber());
    }
```

- [ ] **Step 3: ClassDiagramLayout.java の intention() を ParseResult 対応にする**

`ClassDiagramLayout.java` で `intention(String dsl)` メソッドを以下に置き換え:

```java
    public ClassDiagramLayout intention(String dsl) {
        Objects.requireNonNull(dsl, "dsl must not be null");
        var parsed = new IntentionDslParser().parse(dsl);
        this.placeConstraints = parsed.placeConstraints();
        this.arrowConstraints = parsed.arrowConstraints();
        return this;
    }
```

また、`arrowConstraints` フィールドを `placeConstraints` フィールドの直後に追加:

```java
    private List<ArrowConstraint> arrowConstraints = List.of();
```

`ClassDiagramLayout.java` の import に以下を追加:

```java
import com.sosuisha.classdiagram.intention.ArrowConstraint;
import com.sosuisha.classdiagram.intention.ArrowEdge;
import com.sosuisha.classdiagram.intention.ParseResult;
```

- [ ] **Step 4: 全テスト実行**

```
mvn test
```

Expected: 全テスト PASS（`ClassDiagramGeneratorTest` や `ClassDiagramLayoutTest` も含む）

- [ ] **Step 5: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/intention/IntentionDslParser.java
git add src/test/java/com/sosuisha/classdiagram/intention/IntentionDslParserTest.java
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java
git commit -m "refactor: migrate IntentionDslParser.parse() to return ParseResult"
```

---

## Task 3: IntentionDslParser — arrow 構文のテスト追加

**Files:**
- Modify: `src/test/java/com/sosuisha/classdiagram/intention/IntentionDslParserTest.java`

- [ ] **Step 1: arrow テストを追加**

`IntentionDslParserTest.java` のクラス末尾（`}` の前）に追加:

```java
    // --- arrow parsing ---

    @Test
    void parsesArrowFromOnly() {
        var result = parser.parse("arrow A B from bottom");
        assertEquals(0, result.placeConstraints().size());
        assertEquals(1, result.arrowConstraints().size());
        var c = result.arrowConstraints().get(0);
        assertEquals("A", c.source());
        assertEquals("B", c.target());
        assertEquals(ArrowEdge.BOTTOM, c.fromEdge());
        assertNull(c.toEdge());
        assertEquals(1, c.lineNumber());
    }

    @Test
    void parsesArrowFromTop() {
        var result = parser.parse("arrow X Y from top");
        assertEquals(ArrowEdge.TOP, result.arrowConstraints().get(0).fromEdge());
    }

    @Test
    void parsesArrowFromLeft() {
        var result = parser.parse("arrow X Y from left");
        assertEquals(ArrowEdge.LEFT, result.arrowConstraints().get(0).fromEdge());
    }

    @Test
    void parsesArrowFromRight() {
        var result = parser.parse("arrow X Y from right");
        assertEquals(ArrowEdge.RIGHT, result.arrowConstraints().get(0).fromEdge());
    }

    @Test
    void parsesArrowFromBottomToTop() {
        var result = parser.parse("arrow A B from bottom to top");
        var c = result.arrowConstraints().get(0);
        assertEquals(ArrowEdge.BOTTOM, c.fromEdge());
        assertEquals(ArrowEdge.TOP, c.toEdge());
    }

    @Test
    void parsesArrowFromRightToLeft() {
        var result = parser.parse("arrow A B from right to left");
        var c = result.arrowConstraints().get(0);
        assertEquals(ArrowEdge.RIGHT, c.fromEdge());
        assertEquals(ArrowEdge.LEFT, c.toEdge());
    }

    @Test
    void parsesMixedPlaceAndArrow() {
        var result = parser.parse("place A below B\narrow A B from bottom");
        assertEquals(1, result.placeConstraints().size());
        assertEquals(1, result.arrowConstraints().size());
        assertEquals(PlaceDirection.BELOW, result.placeConstraints().get(0).direction());
        assertEquals(ArrowEdge.BOTTOM, result.arrowConstraints().get(0).fromEdge());
        assertEquals(1, result.placeConstraints().get(0).lineNumber());
        assertEquals(2, result.arrowConstraints().get(0).lineNumber());
    }

    @Test
    void arrowReturnsImmutableList() {
        var result = parser.parse("arrow A B from bottom");
        assertThrows(UnsupportedOperationException.class,
            () -> result.arrowConstraints().add(null));
    }

    @Test
    void throwsForArrowMissingFromKeyword() {
        var ex = assertThrows(IntentionParseException.class,
            () -> parser.parse("arrow A B bottom"));
        assertEquals(1, ex.lineNumber());
        assertTrue(ex.getMessage().contains("invalid arrow statement"));
    }

    @Test
    void throwsForArrowTooFewTokens() {
        var ex = assertThrows(IntentionParseException.class,
            () -> parser.parse("arrow A B"));
        assertEquals(1, ex.lineNumber());
        assertTrue(ex.getMessage().contains("invalid arrow statement"));
    }

    @Test
    void throwsForArrowUnknownFromEdge() {
        var ex = assertThrows(IntentionParseException.class,
            () -> parser.parse("arrow A B from side"));
        assertEquals(1, ex.lineNumber());
        assertTrue(ex.getMessage().contains("unknown edge: 'side'"));
    }

    @Test
    void throwsForArrowUnknownToEdge() {
        var ex = assertThrows(IntentionParseException.class,
            () -> parser.parse("arrow A B from bottom to side"));
        assertEquals(1, ex.lineNumber());
        assertTrue(ex.getMessage().contains("unknown edge: 'side'"));
    }

    @Test
    void throwsForArrowMissingToKeyword() {
        // 6トークン: arrow A B from bottom top (to が欠落)
        var ex = assertThrows(IntentionParseException.class,
            () -> parser.parse("arrow A B from bottom top"));
        assertEquals(1, ex.lineNumber());
        assertTrue(ex.getMessage().contains("invalid arrow statement"));
    }

    @Test
    void throwsForArrowExtraTokens() {
        // 8トークン以上
        var ex = assertThrows(IntentionParseException.class,
            () -> parser.parse("arrow A B from bottom to top extra"));
        assertEquals(1, ex.lineNumber());
        assertTrue(ex.getMessage().contains("invalid arrow statement"));
    }
```

- [ ] **Step 2: 全テスト実行**

```
mvn test -Dtest=IntentionDslParserTest
```

Expected: 全 IntentionDslParserTest が PASS

- [ ] **Step 3: Commit**

```
git add src/test/java/com/sosuisha/classdiagram/intention/IntentionDslParserTest.java
git commit -m "test: add arrow parsing tests to IntentionDslParserTest"
```

---

## Task 4: Dependency ロックメソッド + spreadDependencyEndpoints スキップ

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/Dependency.java`
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`
- Test: `src/test/java/com/sosuisha/classdiagram/DependencyTest.java`

- [ ] **Step 1: DependencyTest にロックメソッドのテストを追加**

`DependencyTest.java` のクラス末尾（`}` の前）に追加:

```java
    @Test
    void sourceAnchorNotLockedByDefault() {
        var dep = dep(DependencyType.COMPOSITION);
        assertFalse(dep.isSourceAnchorLocked());
    }

    @Test
    void targetAnchorNotLockedByDefault() {
        var dep = dep(DependencyType.COMPOSITION);
        assertFalse(dep.isTargetAnchorLocked());
    }

    @Test
    void lockSourceAnchorSetsFlag() {
        var dep = dep(DependencyType.COMPOSITION);
        dep.lockSourceAnchor(50, 0, 0, -1);
        assertTrue(dep.isSourceAnchorLocked());
    }

    @Test
    void lockTargetAnchorSetsFlag() {
        var dep = dep(DependencyType.COMPOSITION);
        dep.lockTargetAnchor(50, 200, 0, 1);
        assertTrue(dep.isTargetAnchorLocked());
    }
```

- [ ] **Step 2: Run test to verify it fails**

```
mvn test -Dtest=DependencyTest
```

Expected: コンパイルエラー（`isSourceAnchorLocked` / `lockSourceAnchor` 未定義）

- [ ] **Step 3: Dependency.java にフィールドとメソッドを追加**

`Dependency.java` で `customTargetDir` フィールドの直後（既存フィールドブロックの末尾）に追加:

```java
    private boolean sourceAnchorLocked = false;
    private boolean targetAnchorLocked = false;
```

既存の `setTargetAnchor()` メソッドの直後に追加:

```java
    /**
     * ソース端点と出口方向を intention 由来アンカーとして設定する。
     * {@code spreadDependencyEndpoints()} の分散対象から除外される。
     */
    public void lockSourceAnchor(double x, double y, double dirX, double dirY) {
        setSourceAnchor(x, y, dirX, dirY);
        this.sourceAnchorLocked = true;
    }

    /**
     * ターゲット端点と入口方向を intention 由来アンカーとして設定する。
     * {@code spreadDependencyEndpoints()} の分散対象から除外される。
     */
    public void lockTargetAnchor(double x, double y, double dirX, double dirY) {
        setTargetAnchor(x, y, dirX, dirY);
        this.targetAnchorLocked = true;
    }

    /** @return ソースアンカーが intention によってロックされている場合 {@code true} */
    public boolean isSourceAnchorLocked() { return sourceAnchorLocked; }

    /** @return ターゲットアンカーが intention によってロックされている場合 {@code true} */
    public boolean isTargetAnchorLocked() { return targetAnchorLocked; }
```

- [ ] **Step 4: ClassDiagramLayout.java の spreadDependencyEndpoints() を修正**

`spreadDependencyEndpoints()` 内のアンカー収集ループを以下に変更する。

変更前（アンカー収集部分、`groups.computeIfAbsent` が2回ある箇所）:

```java
            groups.computeIfAbsent(new EdgeKey(src, srcEdge), k -> new ArrayList<>())
                .add(new AnchorInfo(dep, true, src, srcEdge, srcNatural));
            groups.computeIfAbsent(new EdgeKey(tgt, tgtEdge), k -> new ArrayList<>())
                .add(new AnchorInfo(dep, false, tgt, tgtEdge, tgtNatural));
```

変更後:

```java
            if (!dep.isSourceAnchorLocked()) {
                groups.computeIfAbsent(new EdgeKey(src, srcEdge), k -> new ArrayList<>())
                    .add(new AnchorInfo(dep, true, src, srcEdge, srcNatural));
            }
            if (!dep.isTargetAnchorLocked()) {
                groups.computeIfAbsent(new EdgeKey(tgt, tgtEdge), k -> new ArrayList<>())
                    .add(new AnchorInfo(dep, false, tgt, tgtEdge, tgtNatural));
            }
```

- [ ] **Step 5: 全テスト実行**

```
mvn test
```

Expected: 全テスト PASS

- [ ] **Step 6: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/Dependency.java
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java
git add src/test/java/com/sosuisha/classdiagram/DependencyTest.java
git commit -m "feat: add Dependency lock anchor methods and skip locked in spread"
```

---

## Task 5: ClassDiagramLayout — applyArrowConstraints() 実装 + テスト

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java`

- [ ] **Step 1: 失敗するテストを追加**

`ClassDiagramLayoutTest.java` のクラス末尾（`}` の前）に追加:

```java
    @Test
    void intentionArrowFromBottomLocksSourceAnchor() {
        var a = ci("A"); var b = ci("B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .intention("arrow A B from bottom")
            .layout(layers, rels);
        var dep = result.dependencies().stream()
            .filter(d -> d.source().name().equals("A") && d.target().name().equals("B"))
            .findFirst().orElseThrow();
        assertTrue(dep.isSourceAnchorLocked(), "source anchor must be locked by intention");
        assertFalse(dep.isTargetAnchorLocked(), "target anchor must not be locked when 'to' is omitted");
    }

    @Test
    void intentionArrowFromBottomToTopLocksBothAnchors() {
        var a = ci("A"); var b = ci("B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .intention("arrow A B from bottom to top")
            .layout(layers, rels);
        var dep = result.dependencies().stream()
            .filter(d -> d.source().name().equals("A") && d.target().name().equals("B"))
            .findFirst().orElseThrow();
        assertTrue(dep.isSourceAnchorLocked(), "source anchor must be locked");
        assertTrue(dep.isTargetAnchorLocked(), "target anchor must be locked");
    }

    @Test
    void intentionArrowThrowsForNoRelation() {
        var a = ci("A"); var b = ci("B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        var ex = assertThrows(
            com.sosuisha.classdiagram.intention.IntentionParseException.class,
            () -> new ClassDiagramLayout(20, 40, 20, 20, 60)
                .intention("arrow B A from bottom")  // B→A は存在しない
                .layout(layers, rels));
        assertEquals(1, ex.lineNumber());
        assertTrue(ex.getMessage().contains("no relation"));
        assertTrue(ex.getMessage().contains("'B'"));
        assertTrue(ex.getMessage().contains("'A'"));
    }

    @Test
    void intentionArrowAppliesToAllMatchingRelations() {
        // A→B が COMPOSITION と AGGREGATION の2本存在する場合、両方ロックされる
        var a = ci("A"); var b = ci("B");
        var rels = List.of(
            rel(a, b),
            new ClassRelation(a, b, DependencyType.AGGREGATION, false)
        );
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .intention("arrow A B from bottom")
            .layout(layers, rels);
        var locked = result.dependencies().stream()
            .filter(d -> d.source().name().equals("A") && d.target().name().equals("B"))
            .filter(Dependency::isSourceAnchorLocked)
            .count();
        assertEquals(2, locked, "both A→B relations must have locked source anchor");
    }
```

- [ ] **Step 2: Run test to verify it fails**

```
mvn test "-Dtest=ClassDiagramLayoutTest#intentionArrowFromBottomLocksSourceAnchor"
```

Expected: FAIL（`applyArrowConstraints` が未実装のため `arrowConstraints` が空 → ロックされない）

- [ ] **Step 3: applyArrowConstraints() とヘルパーを ClassDiagramLayout.java に追加**

`applyPlaceConstraints()` メソッドの直前（`applyBelow()` の手前）に追加:

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
                double[] fromPt  = edgeMidpoint(dep.source(), constraint.fromEdge());
                double[] fromDir = edgeOutwardDir(constraint.fromEdge());
                dep.lockSourceAnchor(fromPt[0], fromPt[1], fromDir[0], fromDir[1]);
                if (constraint.toEdge() != null) {
                    double[] toPt  = edgeMidpoint(dep.target(), constraint.toEdge());
                    double[] toDir = edgeOutwardDir(constraint.toEdge());
                    dep.lockTargetAnchor(toPt[0], toPt[1], toDir[0], toDir[1]);
                }
            }
        }
    }

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

- [ ] **Step 4: layout() に applyArrowConstraints() の呼び出しを追加**

`layout()` メソッド内の `spreadDependencyEndpoints(dependencies)` の直前に追加:

変更前:
```java
        spreadDependencyEndpoints(dependencies);
```

変更後:
```java
        applyArrowConstraints(dependencies);
        spreadDependencyEndpoints(dependencies);
```

また、`layout()` の Javadoc に追記:

```java
     * @throws com.sosuisha.classdiagram.intention.IntentionParseException arrow制約でエラーが発生した場合（関係が存在しない等）
```

- [ ] **Step 5: 全テスト実行**

```
mvn test -Dtest=ClassDiagramLayoutTest
```

Expected: 全テスト PASS（新規 4 テスト含む）

- [ ] **Step 6: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java
git commit -m "feat: add applyArrowConstraints() to ClassDiagramLayout"
```

---

## Task 6: 全テスト + SVG 出力確認

**Files:**
- Modify: `src/test/java/com/sosuisha/classdiagram/DiagramDrawExampleTest.java`

- [ ] **Step 1: 全テスト実行**

```
mvn test
```

Expected: 全テスト PASS

- [ ] **Step 2: DiagramDrawExampleTest に arrow を含む出力テストを追加**

`DiagramDrawExampleTest.java` の `outputSampleSnsClientPicturesqueArrangedSvgFile` メソッドを更新して arrow 制約を追加。

現在の `intention("place TimelineServiceFake right of TimelineServiceImpl")` の行を:

```java
                .intention("place TimelineServiceFake right of TimelineServiceImpl")
```

以下に置き換え:

```java
                .intention("""
                    place TimelineServiceFake right of TimelineServiceImpl
                    arrow TimelineService TimelineServiceImpl from bottom
                    arrow TimelineService TimelineServiceFake from bottom
                    """)
```

- [ ] **Step 3: SVG を生成して確認**

```
mvn test "-Dtest=DiagramDrawExampleTest#outputSampleSnsClientPicturesqueArrangedSvgFile"
```

Expected: PASS、`target/svg-output/sample-sns-client-picturesque-arranged.svg` が更新される

- [ ] **Step 4: Commit**

```
git add src/test/java/com/sosuisha/classdiagram/DiagramDrawExampleTest.java
git commit -m "feat: update arranged SVG example with arrow anchor constraints"
```
