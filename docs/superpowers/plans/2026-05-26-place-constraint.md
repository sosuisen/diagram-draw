# Place Constraint DSL Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** intention DSL 基盤（`com.sosuisha.classdiagram.intention` パッケージ）を新設し、`place <target> <direction> <reference>` 構文でレイアウト配置を強制できるようにする。

**Architecture:** `IntentionDslParser` が DSL 文字列を `PlaceConstraint` リストへ変換し、`ClassDiagramLayout.layout()` が `minimizeCrossings()` 直後に `applyPlaceConstraints()` として適用する。`ClassDiagramGenerator` はそのパススルー API を提供する。

**Tech Stack:** Java 25, JUnit Jupiter 5.12.2, Maven (`mvn test`)

---

## ファイル一覧

| 操作 | パス |
|------|------|
| 新規作成 | `src/main/java/com/sosuisha/classdiagram/intention/IntentionParseException.java` |
| 新規作成 | `src/main/java/com/sosuisha/classdiagram/intention/PlaceDirection.java` |
| 新規作成 | `src/main/java/com/sosuisha/classdiagram/intention/PlaceConstraint.java` |
| 新規作成 | `src/main/java/com/sosuisha/classdiagram/intention/IntentionDslParser.java` |
| 新規作成 | `src/test/java/com/sosuisha/classdiagram/intention/IntentionDslParserTest.java` |
| 修正 | `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java` |
| 修正 | `src/main/java/com/sosuisha/classdiagram/ClassDiagramGenerator.java` |
| 修正 | `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java` |
| 修正 | `src/test/java/com/sosuisha/classdiagram/ClassDiagramGeneratorTest.java` |

---

## Task 1: Data classes — IntentionParseException / PlaceDirection / PlaceConstraint

**Files:**
- Create: `src/main/java/com/sosuisha/classdiagram/intention/IntentionParseException.java`
- Create: `src/main/java/com/sosuisha/classdiagram/intention/PlaceDirection.java`
- Create: `src/main/java/com/sosuisha/classdiagram/intention/PlaceConstraint.java`
- Test: `src/test/java/com/sosuisha/classdiagram/intention/IntentionDslParserTest.java`

- [ ] **Step 1: Write failing tests for data classes**

`src/test/java/com/sosuisha/classdiagram/intention/IntentionDslParserTest.java` を新規作成:

```java
package com.sosuisha.classdiagram.intention;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class IntentionDslParserTest {

    // --- IntentionParseException ---

    @Test
    void exceptionLineNumberIsStoredAndAppearsInMessage() {
        var ex = new IntentionParseException(3, "bad input");
        assertEquals(3, ex.lineNumber());
        assertTrue(ex.getMessage().contains("3"), "message must contain line number");
        assertTrue(ex.getMessage().contains("bad input"));
    }

    // --- PlaceConstraint ---

    @Test
    void placeConstraintFieldsAreAccessible() {
        var c = new PlaceConstraint("Item", PlaceDirection.BELOW, "Order", 1);
        assertEquals("Item", c.target());
        assertEquals(PlaceDirection.BELOW, c.direction());
        assertEquals("Order", c.reference());
        assertEquals(1, c.lineNumber());
    }

    @Test
    void placeConstraintNullTargetThrows() {
        assertThrows(NullPointerException.class,
            () -> new PlaceConstraint(null, PlaceDirection.BELOW, "Order", 1));
    }

    @Test
    void placeConstraintNullDirectionThrows() {
        assertThrows(NullPointerException.class,
            () -> new PlaceConstraint("Item", null, "Order", 1));
    }

    @Test
    void placeConstraintNullReferenceThrows() {
        assertThrows(NullPointerException.class,
            () -> new PlaceConstraint("Item", PlaceDirection.BELOW, null, 1));
    }
}
```

- [ ] **Step 2: Run test to verify it fails (compile error)**

```
mvn test -Dtest=IntentionDslParserTest
```

Expected: コンパイルエラー（クラス未定義）

- [ ] **Step 3: Create IntentionParseException**

`src/main/java/com/sosuisha/classdiagram/intention/IntentionParseException.java`:

```java
package com.sosuisha.classdiagram.intention;

/**
 * intention DSLのパースエラーまたは適用エラーを表す例外。
 *
 * <p>行番号を保持し、メッセージに "{@code line <N>: <detail>}" 形式で含める。
 */
public class IntentionParseException extends RuntimeException {

    private final int lineNumber;

    /**
     * IntentionParseExceptionを生成する。
     *
     * @param lineNumber エラーが発生した行番号（1始まり）
     * @param message    エラーの詳細メッセージ
     */
    public IntentionParseException(int lineNumber, String message) {
        super("line " + lineNumber + ": " + message);
        this.lineNumber = lineNumber;
    }

    /**
     * エラーが発生した行番号を返す（1始まり）。
     *
     * @return 行番号
     */
    public int lineNumber() {
        return lineNumber;
    }
}
```

- [ ] **Step 4: Create PlaceDirection**

`src/main/java/com/sosuisha/classdiagram/intention/PlaceDirection.java`:

```java
package com.sosuisha.classdiagram.intention;

/** 配置制約の方向を表す列挙型。 */
public enum PlaceDirection {
    /** ターゲットを基準クラスの上のレイヤーに配置する。 */
    ABOVE,
    /** ターゲットを基準クラスの下のレイヤーに配置する。 */
    BELOW,
    /** ターゲットを基準クラスの右に配置する（同一レイヤー内）。 */
    RIGHT_OF,
    /** ターゲットを基準クラスの左に配置する（同一レイヤー内）。 */
    LEFT_OF
}
```

- [ ] **Step 5: Create PlaceConstraint**

`src/main/java/com/sosuisha/classdiagram/intention/PlaceConstraint.java`:

```java
package com.sosuisha.classdiagram.intention;

import java.util.Objects;

/**
 * パース済みの {@code place} 制約文を表すレコード。
 *
 * @param target     配置対象クラスの単純名
 * @param direction  配置方向
 * @param reference  基準クラスの単純名
 * @param lineNumber エラー報告用の元行番号（1始まり）
 */
public record PlaceConstraint(
    String target,
    PlaceDirection direction,
    String reference,
    int lineNumber
) {
    public PlaceConstraint {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(reference, "reference must not be null");
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

```
mvn test -Dtest=IntentionDslParserTest
```

Expected: 4 tests PASS

- [ ] **Step 7: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/intention/IntentionParseException.java
git add src/main/java/com/sosuisha/classdiagram/intention/PlaceDirection.java
git add src/main/java/com/sosuisha/classdiagram/intention/PlaceConstraint.java
git add src/test/java/com/sosuisha/classdiagram/intention/IntentionDslParserTest.java
git commit -m "feat: add IntentionParseException, PlaceDirection, PlaceConstraint"
```

---

## Task 2: IntentionDslParser

**Files:**
- Create: `src/main/java/com/sosuisha/classdiagram/intention/IntentionDslParser.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/intention/IntentionDslParserTest.java`

- [ ] **Step 1: Append failing tests to IntentionDslParserTest**

`IntentionDslParserTest.java` の末尾（クラス閉じ括弧の前）に追加:

```java
    // --- IntentionDslParser ---

    private final IntentionDslParser parser = new IntentionDslParser();

    @Test
    void parsesPlaceBelow() {
        var result = parser.parse("place Item below Order");
        assertEquals(1, result.size());
        var c = result.get(0);
        assertEquals("Item", c.target());
        assertEquals(PlaceDirection.BELOW, c.direction());
        assertEquals("Order", c.reference());
        assertEquals(1, c.lineNumber());
    }

    @Test
    void parsesPlaceAbove() {
        var result = parser.parse("place Item above Order");
        assertEquals(1, result.size());
        assertEquals(PlaceDirection.ABOVE, result.get(0).direction());
        assertEquals("Item", result.get(0).target());
        assertEquals("Order", result.get(0).reference());
    }

    @Test
    void parsesPlaceRightOf() {
        var result = parser.parse("place Repository right of Service");
        assertEquals(1, result.size());
        assertEquals(PlaceDirection.RIGHT_OF, result.get(0).direction());
        assertEquals("Repository", result.get(0).target());
        assertEquals("Service", result.get(0).reference());
    }

    @Test
    void parsesPlaceLeftOf() {
        var result = parser.parse("place A left of B");
        assertEquals(1, result.size());
        assertEquals(PlaceDirection.LEFT_OF, result.get(0).direction());
        assertEquals("A", result.get(0).target());
        assertEquals("B", result.get(0).reference());
    }

    @Test
    void skipsBlankLines() {
        var result = parser.parse("\n\n  \nplace A below B");
        assertEquals(1, result.size());
        assertEquals(4, result.get(0).lineNumber());
    }

    @Test
    void skipsCommentLines() {
        var result = parser.parse("# comment\nplace A below B");
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).lineNumber());
    }

    @Test
    void parsesMultipleStatements() {
        var result = parser.parse("place A below B\nplace C right of D");
        assertEquals(2, result.size());
        assertEquals(PlaceDirection.BELOW, result.get(0).direction());
        assertEquals(PlaceDirection.RIGHT_OF, result.get(1).direction());
        assertEquals(1, result.get(0).lineNumber());
        assertEquals(2, result.get(1).lineNumber());
    }

    @Test
    void returnsImmutableList() {
        var result = parser.parse("place A below B");
        assertThrows(UnsupportedOperationException.class, () -> result.add(null));
    }

    @Test
    void throwsForNullDsl() {
        assertThrows(NullPointerException.class, () -> parser.parse(null));
    }

    @Test
    void throwsForUnknownVerb() {
        var ex = assertThrows(IntentionParseException.class,
            () -> parser.parse("arrow A B from bottom"));
        assertEquals(1, ex.lineNumber());
        assertTrue(ex.getMessage().contains("1"));
        assertTrue(ex.getMessage().contains("arrow"));
    }

    @Test
    void throwsForUnknownDirection() {
        var ex = assertThrows(IntentionParseException.class,
            () -> parser.parse("place A under B"));
        assertEquals(1, ex.lineNumber());
        assertTrue(ex.getMessage().contains("under"));
    }

    @Test
    void throwsForTooFewTokens() {
        var ex = assertThrows(IntentionParseException.class,
            () -> parser.parse("place A"));
        assertEquals(1, ex.lineNumber());
    }

    @Test
    void throwsForIncompleteRightOf() {
        var ex = assertThrows(IntentionParseException.class,
            () -> parser.parse("place A right B"));
        assertEquals(1, ex.lineNumber());
    }

    @Test
    void lineNumberReflectsActualLineInMultilineInput() {
        var ex = assertThrows(IntentionParseException.class,
            () -> parser.parse("place A below B\narrow X Y from top"));
        assertEquals(2, ex.lineNumber());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```
mvn test -Dtest=IntentionDslParserTest
```

Expected: コンパイルエラー（`IntentionDslParser` 未定義）

- [ ] **Step 3: Create IntentionDslParser**

`src/main/java/com/sosuisha/classdiagram/intention/IntentionDslParser.java`:

```java
package com.sosuisha.classdiagram.intention;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * intention DSL文字列を解析して {@link PlaceConstraint} のリストを返すパーサー。
 *
 * <p>1行に1文を記述する。空行および {@code #} で始まるコメント行はスキップ。
 * 現在サポートする動詞は {@code place} のみ。
 */
public class IntentionDslParser {

    /**
     * DSL文字列を解析して配置制約のリストを返す。
     *
     * @param dsl intention DSL文字列（複数行可）
     * @return パース済み配置制約のリスト（変更不可）
     * @throws NullPointerException    dslがnullの場合
     * @throws IntentionParseException 構文エラーがある場合（行番号を含むメッセージ）
     */
    public List<PlaceConstraint> parse(String dsl) {
        Objects.requireNonNull(dsl, "dsl must not be null");
        var result = new ArrayList<PlaceConstraint>();
        var lines = dsl.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            int lineNumber = i + 1;
            var line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            var tokens = line.split("\\s+");
            if (!tokens[0].equals("place")) {
                throw new IntentionParseException(lineNumber,
                    "unknown verb: '" + tokens[0] + "'");
            }
            result.add(parsePlaceStatement(tokens, lineNumber));
        }
        return List.copyOf(result);
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
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
mvn test -Dtest=IntentionDslParserTest
```

Expected: 17 tests PASS

- [ ] **Step 5: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/intention/IntentionDslParser.java
git add src/test/java/com/sosuisha/classdiagram/intention/IntentionDslParserTest.java
git commit -m "feat: add IntentionDslParser"
```

---

## Task 3: ClassDiagramLayout — intention() API + BELOW constraint

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java`

- [ ] **Step 1: Write failing test**

`ClassDiagramLayoutTest.java` の末尾（クラス閉じ括弧の前）に追加:

```java
    @Test
    void intentionPlaceBelowOverridesAutoLayout() {
        // rel(a, b): a が所有側 → auto-layout で a が上、b が下
        var a = ci("A"); var b = ci("B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        // "place A below B": a を b より下に強制
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .intention("place A below B")
            .layout(layers, rels);
        var boxA = result.boxes().stream().filter(bx -> bx.name().equals("A")).findFirst().orElseThrow();
        var boxB = result.boxes().stream().filter(bx -> bx.name().equals("B")).findFirst().orElseThrow();
        assertTrue(boxA.y() > boxB.y(), "A must be below B after constraint");
    }

    @Test
    void intentionPlaceBelowNoOpWhenAlreadySatisfied() {
        // rel(a, b): auto-layout で b が既に a より下
        var a = ci("A"); var b = ci("B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        // "place B below A": b は既に a より下 → 変更なし
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .intention("place B below A")
            .layout(layers, rels);
        var boxA = result.boxes().stream().filter(bx -> bx.name().equals("A")).findFirst().orElseThrow();
        var boxB = result.boxes().stream().filter(bx -> bx.name().equals("B")).findFirst().orElseThrow();
        assertTrue(boxB.y() > boxA.y(), "B must remain below A");
    }

    @Test
    void intentionNullThrows() {
        assertThrows(NullPointerException.class,
            () -> new ClassDiagramLayout(20, 40, 20, 20, 60).intention(null));
    }
```

- [ ] **Step 2: Run test to verify it fails**

```
mvn test -Dtest=ClassDiagramLayoutTest#intentionPlaceBelowOverridesAutoLayout
```

Expected: コンパイルエラー（`intention` メソッド未定義）

- [ ] **Step 3: Add imports and field to ClassDiagramLayout**

`ClassDiagramLayout.java` の先頭 import セクションに以下を追加（既存 import の後):

```java
import com.sosuisha.classdiagram.intention.IntentionDslParser;
import com.sosuisha.classdiagram.intention.IntentionParseException;
import com.sosuisha.classdiagram.intention.PlaceConstraint;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
```

`picturesque` フィールドの直後に追加:

```java
    private List<PlaceConstraint> placeConstraints = List.of();
```

- [ ] **Step 4: Add intention() method to ClassDiagramLayout**

`picturesque(boolean)` メソッドの直後に追加:

```java
    /**
     * intention DSL文字列をパースして配置制約を設定する。
     *
     * @param dsl intention DSL文字列（複数行可）
     * @return このレイアウト自身（メソッドチェーン用）
     * @throws NullPointerException    dslがnullの場合
     * @throws IntentionParseException DSL構文エラーがある場合
     */
    public ClassDiagramLayout intention(String dsl) {
        Objects.requireNonNull(dsl, "dsl must not be null");
        this.placeConstraints = new IntentionDslParser().parse(dsl);
        return this;
    }

    /**
     * intention DSLファイルを読み込んで配置制約を設定する。
     *
     * @param path intention DSLファイルのパス
     * @return このレイアウト自身（メソッドチェーン用）
     * @throws NullPointerException  pathがnullの場合
     * @throws UncheckedIOException  ファイル読み込みに失敗した場合
     * @throws IntentionParseException DSL構文エラーがある場合
     */
    public ClassDiagramLayout intentionFile(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        try {
            return intention(Files.readString(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
```

- [ ] **Step 5: Update layout() Javadoc and call applyPlaceConstraints()**

`ClassDiagramLayout.java` の `layout()` メソッドの Javadoc に `@throws` を追加:

```java
     * @throws NullPointerException layersまたはrelationsがnullの場合
     * @throws com.sosuisha.classdiagram.intention.IntentionParseException intention制約でエラーが発生した場合
```

`layout()` メソッド内の `minimizeCrossings` 呼び出し直後の行を変更する。

変更前:
```java
        var orderedLayers = minimizeCrossings(layers, relations);

        // Step 2: groupIndex ごとにサブレイヤーを構築（空レイヤーは除去）
```

変更後:
```java
        var orderedLayers = minimizeCrossings(layers, relations);
        applyPlaceConstraints(orderedLayers);

        // Step 2: groupIndex ごとにサブレイヤーを構築（空レイヤーは除去）
```

- [ ] **Step 6: Add applyPlaceConstraints() + BELOW helpers**

`spreadDependencyEndpoints` メソッドの直前に追加:

```java
    private void applyPlaceConstraints(List<List<ClassInfo>> orderedLayers) {
        if (placeConstraints.isEmpty()) return;

        var nameToInfo = new LinkedHashMap<String, ClassInfo>();
        for (var layer : orderedLayers) {
            for (var info : layer) {
                nameToInfo.putIfAbsent(info.simpleName(), info);
            }
        }

        for (var constraint : placeConstraints) {
            var targetInfo = nameToInfo.get(constraint.target());
            var refInfo = nameToInfo.get(constraint.reference());
            if (targetInfo == null) {
                throw new IntentionParseException(constraint.lineNumber(),
                    "unknown class: '" + constraint.target() + "'");
            }
            if (refInfo == null) {
                throw new IntentionParseException(constraint.lineNumber(),
                    "unknown class: '" + constraint.reference() + "'");
            }
            switch (constraint.direction()) {
                case BELOW -> applyBelow(orderedLayers, targetInfo, refInfo, constraint.lineNumber());
                case ABOVE -> applyAbove(orderedLayers, targetInfo, refInfo, constraint.lineNumber());
                case RIGHT_OF -> applyRightOf(orderedLayers, targetInfo, refInfo, constraint.lineNumber());
                case LEFT_OF -> applyLeftOf(orderedLayers, targetInfo, refInfo, constraint.lineNumber());
            }
        }
    }

    private static int findLayerIndex(List<List<ClassInfo>> layers, ClassInfo info) {
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i).contains(info)) return i;
        }
        return -1;
    }

    private void applyBelow(List<List<ClassInfo>> orderedLayers,
            ClassInfo target, ClassInfo ref, int lineNumber) {
        if (target.groupIndex() != ref.groupIndex()) {
            throw new IntentionParseException(lineNumber,
                "'" + target.simpleName() + "' and '" + ref.simpleName()
                + "' are in different connected components");
        }
        int targetIdx = findLayerIndex(orderedLayers, target);
        int refIdx = findLayerIndex(orderedLayers, ref);
        if (targetIdx > refIdx) return;

        orderedLayers.get(targetIdx).remove(target);
        if (orderedLayers.get(targetIdx).isEmpty()) {
            orderedLayers.remove(targetIdx);
            if (refIdx > targetIdx) refIdx--;
        }
        int insertIdx = refIdx + 1;
        if (insertIdx < orderedLayers.size()) {
            orderedLayers.get(insertIdx).add(0, target);
        } else {
            var newLayer = new ArrayList<ClassInfo>();
            newLayer.add(target);
            orderedLayers.add(newLayer);
        }
    }

    private void applyAbove(List<List<ClassInfo>> orderedLayers,
            ClassInfo target, ClassInfo ref, int lineNumber) {
        // placeholder — implemented in Task 4
        throw new UnsupportedOperationException("not yet implemented");
    }

    private void applyRightOf(List<List<ClassInfo>> orderedLayers,
            ClassInfo target, ClassInfo ref, int lineNumber) {
        // placeholder — implemented in Task 5
        throw new UnsupportedOperationException("not yet implemented");
    }

    private void applyLeftOf(List<List<ClassInfo>> orderedLayers,
            ClassInfo target, ClassInfo ref, int lineNumber) {
        // placeholder — implemented in Task 6
        throw new UnsupportedOperationException("not yet implemented");
    }
```

- [ ] **Step 7: Run tests to verify they pass**

```
mvn test -Dtest=ClassDiagramLayoutTest
```

Expected: 全テスト PASS（`intentionPlaceBelowOverridesAutoLayout`, `intentionPlaceBelowNoOpWhenAlreadySatisfied`, `intentionNullThrows` 含む）

- [ ] **Step 8: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java
git commit -m "feat: add ClassDiagramLayout.intention() API and BELOW constraint"
```

---

## Task 4: ABOVE constraint

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java`

- [ ] **Step 1: Write failing test**

`ClassDiagramLayoutTest.java` の末尾に追加:

```java
    @Test
    void intentionPlaceAboveOverridesAutoLayout() {
        // rel(a, b): auto-layout で a が上、b が下
        var a = ci("A"); var b = ci("B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        // "place B above A": b を a より上に強制
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .intention("place B above A")
            .layout(layers, rels);
        var boxA = result.boxes().stream().filter(bx -> bx.name().equals("A")).findFirst().orElseThrow();
        var boxB = result.boxes().stream().filter(bx -> bx.name().equals("B")).findFirst().orElseThrow();
        assertTrue(boxB.y() < boxA.y(), "B must be above A after constraint");
    }

    @Test
    void intentionPlaceAboveNoOpWhenAlreadySatisfied() {
        var a = ci("A"); var b = ci("B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        // "place A above B": a は既に b より上 → 変更なし
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .intention("place A above B")
            .layout(layers, rels);
        var boxA = result.boxes().stream().filter(bx -> bx.name().equals("A")).findFirst().orElseThrow();
        var boxB = result.boxes().stream().filter(bx -> bx.name().equals("B")).findFirst().orElseThrow();
        assertTrue(boxA.y() < boxB.y(), "A must remain above B");
    }
```

- [ ] **Step 2: Run test to verify it fails**

```
mvn test -Dtest=ClassDiagramLayoutTest#intentionPlaceAboveOverridesAutoLayout
```

Expected: FAIL（`applyAbove` が `UnsupportedOperationException` をスロー）

- [ ] **Step 3: Implement applyAbove**

`ClassDiagramLayout.java` の `applyAbove` メソッドを置き換え:

```java
    private void applyAbove(List<List<ClassInfo>> orderedLayers,
            ClassInfo target, ClassInfo ref, int lineNumber) {
        if (target.groupIndex() != ref.groupIndex()) {
            throw new IntentionParseException(lineNumber,
                "'" + target.simpleName() + "' and '" + ref.simpleName()
                + "' are in different connected components");
        }
        int targetIdx = findLayerIndex(orderedLayers, target);
        int refIdx = findLayerIndex(orderedLayers, ref);
        if (targetIdx < refIdx) return;

        orderedLayers.get(targetIdx).remove(target);
        if (orderedLayers.get(targetIdx).isEmpty()) {
            orderedLayers.remove(targetIdx);
            if (refIdx > targetIdx) refIdx--;
        }
        if (refIdx > 0) {
            orderedLayers.get(refIdx - 1).add(target);
        } else {
            var newLayer = new ArrayList<ClassInfo>();
            newLayer.add(target);
            orderedLayers.add(0, newLayer);
        }
    }
```

- [ ] **Step 4: Run tests**

```
mvn test -Dtest=ClassDiagramLayoutTest
```

Expected: 全テスト PASS

- [ ] **Step 5: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java
git commit -m "feat: add ABOVE constraint to ClassDiagramLayout"
```

---

## Task 5: RIGHT_OF constraint

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java`

- [ ] **Step 1: Write failing test**

`ClassDiagramLayoutTest.java` の末尾に追加:

```java
    @Test
    void intentionPlaceRightOfEnforcesOrder() {
        // rel(a, b) + rel(a, c): b と c が同一レイヤー
        var a = ci("A"); var b = ci("B"); var c = ci("C");
        var rels = List.of(rel(a, b), rel(a, c));
        var layers = new ClassRelationSorter().sort(rels);
        // "place C right of B": c を b より右に強制
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .intention("place C right of B")
            .layout(layers, rels);
        var boxB = result.boxes().stream().filter(bx -> bx.name().equals("B")).findFirst().orElseThrow();
        var boxC = result.boxes().stream().filter(bx -> bx.name().equals("C")).findFirst().orElseThrow();
        assertTrue(boxC.x() > boxB.x(), "C must be to the right of B");
    }

    @Test
    void intentionPlaceRightOfNoOpWhenAlreadySatisfied() {
        // B は C より左になるよう "place B right of C" は C を B の右にするが
        // すでに C が B の右ならそのまま
        var a = ci("A"); var b = ci("B"); var c = ci("C");
        var rels = List.of(rel(a, b), rel(a, c));
        var layers = new ClassRelationSorter().sort(rels);
        // まず C を B の右に強制してから再確認
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .intention("place C right of B\nplace C right of B")  // 2回適用しても同じ結果
            .layout(layers, rels);
        var boxB = result.boxes().stream().filter(bx -> bx.name().equals("B")).findFirst().orElseThrow();
        var boxC = result.boxes().stream().filter(bx -> bx.name().equals("C")).findFirst().orElseThrow();
        assertTrue(boxC.x() > boxB.x());
    }
```

- [ ] **Step 2: Run test to verify it fails**

```
mvn test -Dtest=ClassDiagramLayoutTest#intentionPlaceRightOfEnforcesOrder
```

Expected: FAIL（`applyRightOf` が `UnsupportedOperationException` をスロー）

- [ ] **Step 3: Implement applyRightOf**

`ClassDiagramLayout.java` の `applyRightOf` メソッドを置き換え:

```java
    private void applyRightOf(List<List<ClassInfo>> orderedLayers,
            ClassInfo target, ClassInfo ref, int lineNumber) {
        if (target.groupIndex() != ref.groupIndex()) {
            throw new IntentionParseException(lineNumber,
                "'" + target.simpleName() + "' and '" + ref.simpleName()
                + "' are in different connected components");
        }
        int targetIdx = findLayerIndex(orderedLayers, target);
        int refIdx = findLayerIndex(orderedLayers, ref);
        if (targetIdx != refIdx) {
            throw new IntentionParseException(lineNumber,
                "'" + target.simpleName() + "' and '" + ref.simpleName()
                + "' are not in the same layer");
        }
        var layer = orderedLayers.get(targetIdx);
        int targetPos = layer.indexOf(target);
        int refPos = layer.indexOf(ref);
        if (targetPos > refPos) return;
        layer.remove(targetPos);
        refPos = layer.indexOf(ref);
        layer.add(refPos + 1, target);
    }
```

- [ ] **Step 4: Run tests**

```
mvn test -Dtest=ClassDiagramLayoutTest
```

Expected: 全テスト PASS

- [ ] **Step 5: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java
git commit -m "feat: add RIGHT_OF constraint to ClassDiagramLayout"
```

---

## Task 6: LEFT_OF constraint

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java`

- [ ] **Step 1: Write failing test**

`ClassDiagramLayoutTest.java` の末尾に追加:

```java
    @Test
    void intentionPlaceLeftOfEnforcesOrder() {
        var a = ci("A"); var b = ci("B"); var c = ci("C");
        var rels = List.of(rel(a, b), rel(a, c));
        var layers = new ClassRelationSorter().sort(rels);
        // "place B left of C": b を c より左に強制
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .intention("place B left of C")
            .layout(layers, rels);
        var boxB = result.boxes().stream().filter(bx -> bx.name().equals("B")).findFirst().orElseThrow();
        var boxC = result.boxes().stream().filter(bx -> bx.name().equals("C")).findFirst().orElseThrow();
        assertTrue(boxB.x() < boxC.x(), "B must be to the left of C");
    }
```

- [ ] **Step 2: Run test to verify it fails**

```
mvn test -Dtest=ClassDiagramLayoutTest#intentionPlaceLeftOfEnforcesOrder
```

Expected: FAIL（`applyLeftOf` が `UnsupportedOperationException` をスロー）

- [ ] **Step 3: Implement applyLeftOf**

`ClassDiagramLayout.java` の `applyLeftOf` メソッドを置き換え:

```java
    private void applyLeftOf(List<List<ClassInfo>> orderedLayers,
            ClassInfo target, ClassInfo ref, int lineNumber) {
        if (target.groupIndex() != ref.groupIndex()) {
            throw new IntentionParseException(lineNumber,
                "'" + target.simpleName() + "' and '" + ref.simpleName()
                + "' are in different connected components");
        }
        int targetIdx = findLayerIndex(orderedLayers, target);
        int refIdx = findLayerIndex(orderedLayers, ref);
        if (targetIdx != refIdx) {
            throw new IntentionParseException(lineNumber,
                "'" + target.simpleName() + "' and '" + ref.simpleName()
                + "' are not in the same layer");
        }
        var layer = orderedLayers.get(targetIdx);
        int targetPos = layer.indexOf(target);
        int refPos = layer.indexOf(ref);
        if (targetPos < refPos) return;
        layer.remove(targetPos);
        refPos = layer.indexOf(ref);
        layer.add(refPos, target);
    }
```

- [ ] **Step 4: Run tests**

```
mvn test -Dtest=ClassDiagramLayoutTest
```

Expected: 全テスト PASS

- [ ] **Step 5: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java
git commit -m "feat: add LEFT_OF constraint to ClassDiagramLayout"
```

---

## Task 7: Error cases in applyPlaceConstraints + intentionFile()

**Files:**
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java`

- [ ] **Step 1: Write failing tests**

`ClassDiagramLayoutTest.java` の末尾に追加:

```java
    @Test
    void intentionThrowsForUnknownTargetClass() {
        var a = ci("A"); var b = ci("B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        var ex = assertThrows(
            com.sosuisha.classdiagram.intention.IntentionParseException.class,
            () -> new ClassDiagramLayout(20, 40, 20, 20, 60)
                .intention("place Unknown below A")
                .layout(layers, rels));
        assertEquals(1, ex.lineNumber());
        assertTrue(ex.getMessage().contains("Unknown"));
    }

    @Test
    void intentionThrowsForUnknownReferenceClass() {
        var a = ci("A"); var b = ci("B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        var ex = assertThrows(
            com.sosuisha.classdiagram.intention.IntentionParseException.class,
            () -> new ClassDiagramLayout(20, 40, 20, 20, 60)
                .intention("place A below Ghost")
                .layout(layers, rels));
        assertEquals(1, ex.lineNumber());
        assertTrue(ex.getMessage().contains("Ghost"));
    }

    @Test
    void intentionBelowThrowsForCrossGroup() {
        var a = ci("A"); var b = ci("B");
        b.setGroupIndex(1);
        // 手動で2グループのレイヤーを構築
        var layers = new ArrayList<>(List.of(
            new ArrayList<>(List.of(a)),
            new ArrayList<>(List.of(b))
        ));
        var ex = assertThrows(
            com.sosuisha.classdiagram.intention.IntentionParseException.class,
            () -> new ClassDiagramLayout(20, 40, 20, 20, 60)
                .intention("place A below B")
                .layout(layers, List.of()));
        assertEquals(1, ex.lineNumber());
        assertTrue(ex.getMessage().contains("connected components"));
    }

    @Test
    void intentionRightOfThrowsForDifferentLayers() {
        // A→B→C: B と C は異なるレイヤー
        var a = ci("A"); var b = ci("B"); var c = ci("C");
        var rels = List.of(rel(a, b), rel(b, c));
        var layers = new ClassRelationSorter().sort(rels);
        var ex = assertThrows(
            com.sosuisha.classdiagram.intention.IntentionParseException.class,
            () -> new ClassDiagramLayout(20, 40, 20, 20, 60)
                .intention("place C right of B")
                .layout(layers, rels));
        assertEquals(1, ex.lineNumber());
        assertTrue(ex.getMessage().contains("same layer"));
    }

    @Test
    void intentionFileThrowsForNullPath() {
        assertThrows(NullPointerException.class,
            () -> new ClassDiagramLayout(20, 40, 20, 20, 60).intentionFile(null));
    }

    @Test
    void intentionFileLoadsConstraintFromFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir)
            throws Exception {
        var file = tempDir.resolve("test.intention");
        java.nio.file.Files.writeString(file, "place A below B");
        var a = ci("A"); var b = ci("B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .intentionFile(file)
            .layout(layers, rels);
        var boxA = result.boxes().stream().filter(bx -> bx.name().equals("A")).findFirst().orElseThrow();
        var boxB = result.boxes().stream().filter(bx -> bx.name().equals("B")).findFirst().orElseThrow();
        assertTrue(boxA.y() > boxB.y(), "A must be below B after constraint loaded from file");
    }
```

- [ ] **Step 2: Add ArrayList import if needed**

`ClassDiagramLayoutTest.java` の import セクションを確認し、`java.util.ArrayList` が未 import なら追加:

```java
import java.util.ArrayList;
```

- [ ] **Step 3: Run all layout tests**

```
mvn test -Dtest=ClassDiagramLayoutTest
```

Expected: 全テスト PASS（エラーハンドリングは Task 3〜6 で実装済み。`intentionFileLoadsConstraintFromFile` も Task 3 の `intentionFile()` 実装で動作する）

- [ ] **Step 4: Commit**

```
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java
git commit -m "test: add error case and intentionFile tests for ClassDiagramLayout"
```

---

## Task 8: ClassDiagramGenerator — intention API

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramGenerator.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassDiagramGeneratorTest.java`

- [ ] **Step 1: Write failing tests**

`ClassDiagramGeneratorTest.java` の末尾に追加:

```java
    @Test
    void intentionReturnsSelf() {
        var gen = new ClassDiagramGenerator(20, 40, 20, 20, 60);
        assertSame(gen, gen.intention("place A below B"));
    }

    @Test
    void intentionFileReturnsSelf(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir)
            throws Exception {
        var file = tempDir.resolve("test.intention");
        java.nio.file.Files.writeString(file, "# empty");
        var gen = new ClassDiagramGenerator(20, 40, 20, 20, 60);
        assertSame(gen, gen.intentionFile(file));
    }

    @Test
    void intentionNullThrows() {
        assertThrows(NullPointerException.class,
            () -> new ClassDiagramGenerator(20, 40, 20, 20, 60).intention(null));
    }

    @Test
    void intentionFileNullThrows() {
        assertThrows(NullPointerException.class,
            () -> new ClassDiagramGenerator(20, 40, 20, 20, 60).intentionFile(null));
    }

    @Test
    void intentionIsAppliedDuringGenerate() {
        // FixtureOrder と FixtureItem の相対位置を制約で逆転させる
        // 制約なしでは FixtureOrder が上（FixtureItem を所有するため）
        // "place FixtureOrder below FixtureItem" で逆転することを SVG の transform 座標で確認
        var svgNormal = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        var svgConstrained = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .intention("place FixtureOrder below FixtureItem")
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        // どちらも有効な SVG が返ること
        assertTrue(svgNormal.startsWith("<svg"));
        assertTrue(svgConstrained.startsWith("<svg"));
        // 2つの SVG は異なること（制約が適用されて座標が変わるはず）
        assertNotEquals(svgNormal, svgConstrained,
            "Constrained SVG must differ from normal SVG");
    }

    @Test
    void intentionFileOverridesDslWhenBothSet(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        // intentionFile が intentionDsl より優先される
        var file = tempDir.resolve("test.intention");
        java.nio.file.Files.writeString(file, "place FixtureOrder below FixtureItem");
        var svgDslOnly = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .intention("# no constraint")
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        var svgFileOverride = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .intention("# no constraint")
            .intentionFile(file)  // file overrides DSL string
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        assertNotEquals(svgDslOnly, svgFileOverride,
            "intentionFile must override intention() when both are set");
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```
mvn test -Dtest=ClassDiagramGeneratorTest#intentionReturnsSelf
```

Expected: コンパイルエラー（`intention` / `intentionFile` メソッド未定義）

- [ ] **Step 3: Add fields to ClassDiagramGenerator**

`picturesque` フィールドの直後に追加:

```java
    private String intentionDsl = null;
    private Path intentionFilePath = null;
```

`ClassDiagramGenerator.java` の import セクションに追加（`java.nio.file.Path` が未 import なら）:

```java
import java.nio.file.Path;
```

- [ ] **Step 4: Add intention() and intentionFile() methods to ClassDiagramGenerator**

`picturesque(boolean)` メソッドの直後に追加:

```java
    /**
     * intention DSL文字列を設定する。{@link #generate} 時にレイアウトへ渡される。
     * {@link #intentionFile} と両方設定した場合は {@code intentionFile} が優先される。
     *
     * @param dsl intention DSL文字列
     * @return このジェネレーター自身（メソッドチェーン用）
     * @throws NullPointerException dslがnullの場合
     */
    public ClassDiagramGenerator intention(String dsl) {
        Objects.requireNonNull(dsl, "dsl must not be null");
        this.intentionDsl = dsl;
        return this;
    }

    /**
     * intention DSLファイルのパスを設定する。{@link #generate} 時にレイアウトへ渡される。
     * {@link #intention} より優先される。
     *
     * @param path intention DSLファイルのパス
     * @return このジェネレーター自身（メソッドチェーン用）
     * @throws NullPointerException pathがnullの場合
     */
    public ClassDiagramGenerator intentionFile(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        this.intentionFilePath = path;
        return this;
    }
```

- [ ] **Step 5: Wire intention into generate()**

`generate()` メソッド内の `if (edgeColor != null) layoutEngine.edgeColor(edgeColor);` 直後に追加:

```java
        if (intentionFilePath != null) {
            layoutEngine.intentionFile(intentionFilePath);
        } else if (intentionDsl != null) {
            layoutEngine.intention(intentionDsl);
        }
```

- [ ] **Step 6: Run all tests**

```
mvn test
```

Expected: 全テスト PASS

- [ ] **Step 7: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramGenerator.java
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramGeneratorTest.java
git commit -m "feat: add ClassDiagramGenerator.intention() and intentionFile() API"
```
