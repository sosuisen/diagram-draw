# Class Relation Sorter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce `ClassInfo` as a structured class identity type, refactor `ClassRelation` and `ClassRelationScanner` to use it, and implement `ClassRelationSorter` — a Kahn's BFS topological sort engine that returns `List<List<ClassInfo>>` layers.

**Architecture:** `ClassInfo` is a new record in `com.sosuisha.classdiagram.analyzer` that holds `packageName` and `simpleName`; `ClassRelation` is updated to use it instead of plain strings; `ClassRelationScanner` calls `ClassInfo.fromFullyQualifiedName()` when building results; `ClassRelationSorter` runs Kahn's BFS over the relation graph and emits sorted layers, throwing `CircularRelationException` on cycle detection.

**Tech Stack:** Java 25, JUnit Jupiter 5.12, Maven (`mvn test` to run all tests).

---

## File Map

| Action | File |
|--------|------|
| Create | `src/main/java/com/sosuisha/classdiagram/analyzer/ClassInfo.java` |
| Create | `src/test/java/com/sosuisha/classdiagram/analyzer/ClassInfoTest.java` |
| Modify | `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelation.java` |
| Modify | `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java` |
| Modify | `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java` |
| Create | `src/main/java/com/sosuisha/classdiagram/analyzer/CircularRelationException.java` |
| Create | `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationSorterTest.java` |
| Create | `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationSorter.java` |

---

## Task 1: `ClassInfo` record

**Files:**
- Create: `src/main/java/com/sosuisha/classdiagram/analyzer/ClassInfo.java`
- Create: `src/test/java/com/sosuisha/classdiagram/analyzer/ClassInfoTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/sosuisha/classdiagram/analyzer/ClassInfoTest.java`:

```java
package com.sosuisha.classdiagram.analyzer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassInfoTest {

    @Test
    void fromFullyQualifiedNameSplitsCorrectly() {
        var info = ClassInfo.fromFullyQualifiedName("com.sosuisha.classdiagram.Order");
        assertEquals("com.sosuisha.classdiagram", info.packageName());
        assertEquals("Order", info.simpleName());
    }

    @Test
    void fromFullyQualifiedNameThrowsForNull() {
        assertThrows(NullPointerException.class,
            () -> ClassInfo.fromFullyQualifiedName(null));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl . -Dtest=ClassInfoTest`
Expected: FAIL — `ClassInfo` does not exist yet.

- [ ] **Step 3: Implement `ClassInfo`**

Create `src/main/java/com/sosuisha/classdiagram/analyzer/ClassInfo.java`:

```java
package com.sosuisha.classdiagram.analyzer;

import java.util.Objects;

/**
 * クラスのパッケージ名と単純名を保持する識別子。
 *
 * @param packageName パッケージ名（例: {@code "com.sosuisha.classdiagram"}）
 * @param simpleName  単純名（パッケージを除く。例: {@code "Order"}）
 */
public record ClassInfo(String packageName, String simpleName) {

    /**
     * 完全修飾名からClassInfoを生成する。
     *
     * @param fqn 完全修飾名（例: {@code "com.sosuisha.classdiagram.Order"}）
     * @return ClassInfoインスタンス
     * @throws NullPointerException fqnがnullの場合
     */
    public static ClassInfo fromFullyQualifiedName(String fqn) {
        Objects.requireNonNull(fqn, "fqn must not be null");
        int dot = fqn.lastIndexOf('.');
        return new ClassInfo(fqn.substring(0, dot), fqn.substring(dot + 1));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl . -Dtest=ClassInfoTest`
Expected: PASS — both tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sosuisha/classdiagram/analyzer/ClassInfo.java \
        src/test/java/com/sosuisha/classdiagram/analyzer/ClassInfoTest.java
git commit -m "feat: add ClassInfo record with fromFullyQualifiedName factory"
```

---

## Task 2: Refactor `ClassRelation` and `ClassRelationScanner` to use `ClassInfo`

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelation.java`
- Modify: `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java`

- [ ] **Step 1: Replace `ClassRelation.java` entirely**

Overwrite `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelation.java`:

```java
package com.sosuisha.classdiagram.analyzer;

/**
 * 2クラス間の関係を表す。
 *
 * @param sourceClassInfo フィールドを持つクラス（所有側）
 * @param targetClassInfo フィールドの型クラス（所有される側）
 * @param type            COMPOSITION または AGGREGATION
 * @param isMany          コレクションフィールドの場合true
 */
public record ClassRelation(
    ClassInfo sourceClassInfo,
    ClassInfo targetClassInfo,
    RelationType type,
    boolean isMany
) {}
```

- [ ] **Step 2: Update `ClassRelationScanner` — replace the `new ClassRelation(...)` call**

In `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java`, find the line:

```java
relations.add(new ClassRelation(className, resolved.targetClassName(), type, resolved.isMany()));
```

Replace it with:

```java
relations.add(new ClassRelation(
    ClassInfo.fromFullyQualifiedName(className),
    ClassInfo.fromFullyQualifiedName(resolved.targetClassName()),
    type,
    resolved.isMany()
));
```

No other changes to `ClassRelationScanner.java` are needed.

- [ ] **Step 3: Update `ClassRelationScannerTest` — replace all `sourceClass()`/`targetClass()` references**

Overwrite `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java`:

```java
package com.sosuisha.classdiagram.analyzer;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ClassRelationScannerTest {

    private static final Path CLASS_ROOT = Path.of("target/test-classes");
    private static final String FIXTURE_PKG = "com.sosuisha.classdiagram.analyzer.fixture";

    @Test
    void scanThrowsForNullClassRoot() {
        assertThrows(NullPointerException.class,
            () -> new ClassRelationScanner().scan(null, "com.example"));
    }

    @Test
    void scanThrowsForNullPackageName() {
        assertThrows(NullPointerException.class,
            () -> new ClassRelationScanner().scan(CLASS_ROOT, null));
    }

    @Test
    void scanReturnsEmptyListForNonExistentPackage() {
        var result = new ClassRelationScanner().scan(CLASS_ROOT, "com.does.not.exist");
        assertTrue(result.isEmpty());
    }

    @Test
    void scanDetectsAggregationForConstructorInjectedField() {
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXTURE_PKG);
        assertTrue(relations.stream().anyMatch(r ->
            r.sourceClassInfo().packageName().equals(FIXTURE_PKG) &&
            r.sourceClassInfo().simpleName().equals("FixtureOrder") &&
            r.targetClassInfo().packageName().equals(FIXTURE_PKG) &&
            r.targetClassInfo().simpleName().equals("FixtureCustomer") &&
            r.type() == RelationType.AGGREGATION &&
            !r.isMany()
        ));
    }

    @Test
    void scanReturnsNoRelationsForPojoWithoutSamePackageFields() {
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXTURE_PKG);
        assertTrue(relations.stream().noneMatch(r ->
            r.sourceClassInfo().simpleName().equals("FixtureItem")
        ));
    }

    @Test
    void scanDetectsCompositionWithCollectionField() {
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXTURE_PKG);
        assertTrue(relations.stream().anyMatch(r ->
            r.sourceClassInfo().simpleName().equals("FixtureOrder") &&
            r.targetClassInfo().simpleName().equals("FixtureItem") &&
            r.type() == RelationType.COMPOSITION &&
            r.isMany()
        ));
    }
}
```

- [ ] **Step 4: Run all tests to verify**

Run: `mvn test`
Expected: All tests pass (no compilation errors, all assertions green).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelation.java \
        src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java \
        src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java
git commit -m "refactor: use ClassInfo in ClassRelation and ClassRelationScanner"
```

---

## Task 3: `ClassRelationSorter` with Kahn's BFS

**Files:**
- Create: `src/main/java/com/sosuisha/classdiagram/analyzer/CircularRelationException.java`
- Create: `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationSorterTest.java`
- Create: `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationSorter.java`

- [ ] **Step 1: Create `CircularRelationException`**

Create `src/main/java/com/sosuisha/classdiagram/analyzer/CircularRelationException.java`:

```java
package com.sosuisha.classdiagram.analyzer;

/**
 * ClassRelationSorterが循環参照を検出した際にスローする例外。
 */
public class CircularRelationException extends RuntimeException {
    public CircularRelationException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Write the failing tests**

Create `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationSorterTest.java`:

```java
package com.sosuisha.classdiagram.analyzer;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ClassRelationSorterTest {

    private static final String PKG = "com.example";
    private final ClassRelationSorter sorter = new ClassRelationSorter();

    private static ClassInfo ci(String name) {
        return new ClassInfo(PKG, name);
    }

    private static ClassRelation rel(String src, String tgt) {
        return new ClassRelation(ci(src), ci(tgt), RelationType.COMPOSITION, false);
    }

    @Test
    void sortThrowsForNullInput() {
        assertThrows(NullPointerException.class, () -> sorter.sort(null));
    }

    @Test
    void sortReturnsEmptyForEmptyInput() {
        assertEquals(List.of(), sorter.sort(List.of()));
    }

    @Test
    void sortLinearChain() {
        // A → B → C  =>  [[A], [B], [C]]
        var result = sorter.sort(List.of(rel("A", "B"), rel("B", "C")));
        assertEquals(3, result.size());
        assertEquals(List.of(ci("A")), result.get(0));
        assertEquals(List.of(ci("B")), result.get(1));
        assertEquals(List.of(ci("C")), result.get(2));
    }

    @Test
    void sortDiamond() {
        // A->B, A->C, B->D, C->D  =>  [[A], [B, C], [D]]
        var result = sorter.sort(List.of(
            rel("A", "B"), rel("A", "C"), rel("B", "D"), rel("C", "D")
        ));
        assertEquals(3, result.size());
        assertEquals(List.of(ci("A")), result.get(0));
        assertEquals(List.of(ci("B"), ci("C")), result.get(1));
        assertEquals(List.of(ci("D")), result.get(2));
    }

    @Test
    void sortThrowsCircularRelationException() {
        // A -> B -> A
        var ex = assertThrows(CircularRelationException.class,
            () -> sorter.sort(List.of(rel("A", "B"), rel("B", "A"))));
        assertTrue(ex.getMessage().contains("A"));
        assertTrue(ex.getMessage().contains("B"));
    }
}
```

- [ ] **Step 3: Create a stub `ClassRelationSorter` so tests compile**

Create `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationSorter.java`:

```java
package com.sosuisha.classdiagram.analyzer;

import java.util.List;

public class ClassRelationSorter {
    public List<List<ClassInfo>> sort(List<ClassRelation> relations) {
        return null;
    }
}
```

- [ ] **Step 4: Run tests to verify they fail (not crash)**

Run: `mvn test -pl . -Dtest=ClassRelationSorterTest`
Expected: FAIL — assertions fail (e.g. `NullPointerException` on null return, not `NullPointerException` thrown by `requireNonNull`).

- [ ] **Step 5: Implement `ClassRelationSorter` fully**

Overwrite `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationSorter.java`:

```java
package com.sosuisha.classdiagram.analyzer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link ClassRelation} のリストをトポロジカルソートし、
 * 描画レイヤーごとに分類された {@link ClassInfo} のリストを返す。
 *
 * <p>アルゴリズム: Kahn's BFS（幅優先探索によるトポロジカルソート）。
 * 各クラスを入次数（自分を所有するクラスの数）で管理し、
 * 入次数が0のクラスから順にレイヤーへ割り当てる。
 * 全クラスを処理できなかった場合は循環参照と判定して例外をスローする。
 */
public class ClassRelationSorter {

    /**
     * 関係リストをトポロジカルソートし、レイヤーごとの {@link ClassInfo} リストを返す。
     * index 0 が最上位レイヤー（入次数0のクラス群）。
     *
     * @param relations 関係リスト
     * @return トポロジカル順に並べたレイヤーのリスト
     * @throws NullPointerException      relationsがnullの場合
     * @throws CircularRelationException 循環参照が検出された場合
     */
    public List<List<ClassInfo>> sort(List<ClassRelation> relations) {
        Objects.requireNonNull(relations, "relations must not be null");

        if (relations.isEmpty()) {
            return List.of();
        }

        // Kahn's BFS: 隣接リストと入次数マップを構築する
        Map<ClassInfo, Set<ClassInfo>> adjacency = new HashMap<>();
        Map<ClassInfo, Integer> inDegree = new HashMap<>();

        for (var relation : relations) {
            var src = relation.sourceClassInfo();
            var tgt = relation.targetClassInfo();
            inDegree.putIfAbsent(src, 0);
            inDegree.putIfAbsent(tgt, 0);
            adjacency.computeIfAbsent(src, k -> new HashSet<>()).add(tgt);
            inDegree.merge(tgt, 1, Integer::sum);
        }

        // 入次数が0のノードを最初のレイヤーとして投入する
        var currentLayer = inDegree.entrySet().stream()
            .filter(e -> e.getValue() == 0)
            .map(Map.Entry::getKey)
            .sorted(Comparator.comparing(ClassInfo::simpleName))
            .collect(Collectors.toCollection(ArrayList::new));

        var result = new ArrayList<List<ClassInfo>>();
        int emitted = 0;

        // 各レイヤーを順に処理し、後継ノードの入次数を減らす
        while (!currentLayer.isEmpty()) {
            result.add(List.copyOf(currentLayer));
            emitted += currentLayer.size();

            var nextLayer = new ArrayList<ClassInfo>();
            for (var node : currentLayer) {
                for (var successor : adjacency.getOrDefault(node, Set.of())) {
                    var newDegree = inDegree.merge(successor, -1, Integer::sum);
                    if (newDegree == 0) {
                        nextLayer.add(successor);
                    }
                }
            }
            nextLayer.sort(Comparator.comparing(ClassInfo::simpleName));
            currentLayer = nextLayer;
        }

        // 入次数が残っているノードは循環参照に含まれる
        if (emitted < inDegree.size()) {
            var cycleNames = inDegree.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> e.getKey().simpleName())
                .sorted()
                .collect(Collectors.joining(", "));
            throw new CircularRelationException(
                "Circular relation detected among: [" + cycleNames + "]");
        }

        return List.copyOf(result);
    }
}
```

- [ ] **Step 6: Run all tests to verify**

Run: `mvn test`
Expected: All tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/sosuisha/classdiagram/analyzer/CircularRelationException.java \
        src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationSorter.java \
        src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationSorterTest.java
git commit -m "feat: add ClassRelationSorter with Kahn's BFS topological sort"
```
