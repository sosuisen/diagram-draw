# Edge Crossing Minimization (Barycenter Method) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `minimizeCrossings()` to `ClassDiagramLayout` so that node order within each layer is optimized by the Sugiyama barycenter method before box positions are computed.

**Architecture:** A private `minimizeCrossings()` method is inserted between Step 1 (boxMap creation) and Step 2 (groupSubLayers construction) in `layout()`. It runs up to 12 alternating top-down / bottom-up passes over the global layer list, sorting each layer by the barycenter of adjacent-layer neighbors, and exits early when a pass makes no change.

**Tech Stack:** Java 25, Maven (`mvn test` to run tests)

---

## File Map

| Action | File |
|--------|------|
| Modify | `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java` |
| Modify | `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java` |

---

## Task 1: Write failing tests for crossing minimization

**Files:**
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java`

- [ ] **Step 1: Add three new tests at the end of `ClassDiagramLayoutTest`**

Append these tests inside the `ClassDiagramLayoutTest` class, after the last existing `@Test` method:

```java
@Test
void minimizeCrossingsReducesCrossings() {
    // Layer 0: [A, B]  A at index 0, B at index 1
    // Layer 1: [C, D]  A→D and B→C → crossing before minimization
    // After minimization: bary(D)=0 (parent A at 0), bary(C)=1 (parent B at 1)
    // → D must be left of C
    var a = ci("A"); var b = ci("B"); var c = ci("C"); var d = ci("D");
    var layers = List.of(List.of(a, b), List.of(c, d));
    var rels = List.of(
        new ClassRelation(a, d, DependencyType.COMPOSITION, false),
        new ClassRelation(b, c, DependencyType.COMPOSITION, false)
    );
    var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);

    var boxC = result.boxes().stream().filter(bx -> bx.name().equals("C")).findFirst().orElseThrow();
    var boxD = result.boxes().stream().filter(bx -> bx.name().equals("D")).findFirst().orElseThrow();
    assertTrue(boxD.x() < boxC.x(),
        "D (parent=A at idx 0) must be left of C (parent=B at idx 1) after crossing minimization");
}

@Test
void minimizeCrossingsNoNeighborNodeKeepsRelativeOrder() {
    // Layer 0: [B, A]  B at index 0, A at index 1
    // Layer 1: [C, D, E]  COMP(A,C) → bary(C)=1, COMP(B,D) → bary(D)=0, E has no parent → bary(E)=2 (current idx)
    // After minimization: [D, C, E]
    var a = ci("A"); var b = ci("B"); var c = ci("C"); var d = ci("D"); var e = ci("E");
    var layers = List.of(List.of(b, a), List.of(c, d, e));
    var rels = List.of(
        new ClassRelation(a, c, DependencyType.COMPOSITION, false),
        new ClassRelation(b, d, DependencyType.COMPOSITION, false)
    );
    var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);

    var boxC = result.boxes().stream().filter(bx -> bx.name().equals("C")).findFirst().orElseThrow();
    var boxD = result.boxes().stream().filter(bx -> bx.name().equals("D")).findFirst().orElseThrow();
    var boxE = result.boxes().stream().filter(bx -> bx.name().equals("E")).findFirst().orElseThrow();
    assertTrue(boxD.x() < boxC.x(), "D (bary=0) must be left of C (bary=1)");
    assertTrue(boxC.x() < boxE.x(), "E (no neighbor, bary=current idx=2) must be rightmost");
}

@Test
void minimizeCrossingsRealizationReducesCrossings() {
    // Layer 0: [IA, IB]  IA at index 0, IB at index 1 (interfaces)
    // Layer 1: [ImplB, ImplA]  ImplA implements IA, ImplB implements IB → crossing!
    // After minimization: bary(ImplA)=0, bary(ImplB)=1 → [ImplA, ImplB]
    var ia = new ClassInfo(PKG, "IA", ClassStereotype.INTERFACE);
    var ib = new ClassInfo(PKG, "IB", ClassStereotype.INTERFACE);
    var implA = ci("ImplA");
    var implB = ci("ImplB");
    var layers = List.of(List.of(ia, ib), List.of(implB, implA));
    var rels = List.of(
        new ClassRelation(implA, ia, DependencyType.REALIZATION, false),
        new ClassRelation(implB, ib, DependencyType.REALIZATION, false)
    );
    var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);

    var boxImplA = result.boxes().stream().filter(bx -> bx.name().equals("ImplA")).findFirst().orElseThrow();
    var boxImplB = result.boxes().stream().filter(bx -> bx.name().equals("ImplB")).findFirst().orElseThrow();
    assertTrue(boxImplA.x() < boxImplB.x(),
        "ImplA (parent=IA at idx 0) must be left of ImplB (parent=IB at idx 1)");
}
```

- [ ] **Step 2: Run new tests to verify they fail**

```
mvn test -Dtest=ClassDiagramLayoutTest#minimizeCrossingsReducesCrossings+minimizeCrossingsNoNeighborNodeKeepsRelativeOrder+minimizeCrossingsRealizationReducesCrossings -pl .
```

Expected: 3 FAIL (tests exist but the algorithm is not yet implemented — current layout preserves original layer order).

---

## Task 2: Implement `minimizeCrossings()` and `sortLayerByBarycenter()`

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`

- [ ] **Step 1: Add `Comparator` import**

In `ClassDiagramLayout.java`, the existing imports end before `import java.util.stream.Collectors;`. Add `Comparator` import so the import block reads:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
```

- [ ] **Step 2: Add `MAX_CROSSING_PASSES` constant**

Add this field after the existing instance fields (after `private final int groupGap;`):

```java
private static final int MAX_CROSSING_PASSES = 12;
```

- [ ] **Step 3: Insert `minimizeCrossings()` call into `layout()`**

In `layout()`, after the boxMap creation block (Step 1) and before the `// Step 2:` comment, insert:

```java
        // 辺交差の最小化（重心法）
        var orderedLayers = minimizeCrossings(layers, relations);
```

Then change Step 2 to use `orderedLayers` instead of `layers`. The full Step 2 block becomes:

```java
        // Step 2: groupIndex ごとにサブレイヤーを構築（空レイヤーは除去）
        var groupIndices = orderedLayers.stream()
            .flatMap(List::stream)
            .mapToInt(ClassInfo::groupIndex)
            .distinct()
            .sorted()
            .toArray();
        int numGroups = groupIndices.length;

        var groupSubLayers = new ArrayList<List<List<ClassInfo>>>(numGroups);
        for (int gi : groupIndices) {
            var subLayers = new ArrayList<List<ClassInfo>>();
            for (var layer : orderedLayers) {
                var filtered = layer.stream()
                    .filter(info -> info.groupIndex() == gi)
                    .collect(Collectors.toCollection(ArrayList::new));
                if (!filtered.isEmpty()) subLayers.add(filtered);
            }
            groupSubLayers.add(subLayers);
        }
```

- [ ] **Step 4: Add `minimizeCrossings()` private method**

Add this method before the existing `buildImplToIfaceInfosMap` private method:

```java
    private List<List<ClassInfo>> minimizeCrossings(
            List<List<ClassInfo>> layers, List<ClassRelation> relations) {
        if (layers.size() <= 1) return layers;

        Map<ClassInfo, List<ClassInfo>> parents = new HashMap<>();
        Map<ClassInfo, List<ClassInfo>> children = new HashMap<>();
        for (var rel : relations) {
            if (rel.type() == DependencyType.REALIZATION) {
                parents.computeIfAbsent(rel.sourceClassInfo(), k -> new ArrayList<>())
                       .add(rel.targetClassInfo());
                children.computeIfAbsent(rel.targetClassInfo(), k -> new ArrayList<>())
                        .add(rel.sourceClassInfo());
            } else if (rel.type() == DependencyType.COMPOSITION
                    || rel.type() == DependencyType.AGGREGATION) {
                parents.computeIfAbsent(rel.targetClassInfo(), k -> new ArrayList<>())
                       .add(rel.sourceClassInfo());
                children.computeIfAbsent(rel.sourceClassInfo(), k -> new ArrayList<>())
                        .add(rel.targetClassInfo());
            }
        }

        var result = layers.stream()
            .map(ArrayList::new)
            .collect(Collectors.toCollection(ArrayList::new));

        for (int pass = 0; pass < MAX_CROSSING_PASSES; pass++) {
            boolean changed = false;
            if (pass % 2 == 0) {
                for (int i = 0; i + 1 < result.size(); i++) {
                    changed |= sortLayerByBarycenter(result.get(i + 1), result.get(i), parents);
                }
            } else {
                for (int i = result.size() - 2; i >= 0; i--) {
                    changed |= sortLayerByBarycenter(result.get(i), result.get(i + 1), children);
                }
            }
            if (!changed) break;
        }

        return result;
    }
```

- [ ] **Step 5: Add `sortLayerByBarycenter()` private method**

Add this method immediately after `minimizeCrossings()`:

```java
    private boolean sortLayerByBarycenter(
            List<ClassInfo> layer, List<ClassInfo> referenceLayer,
            Map<ClassInfo, List<ClassInfo>> adj) {
        Map<ClassInfo, Integer> pos = new HashMap<>();
        for (int i = 0; i < referenceLayer.size(); i++) {
            pos.put(referenceLayer.get(i), i);
        }

        Map<ClassInfo, Double> bary = new HashMap<>();
        for (int i = 0; i < layer.size(); i++) {
            var node = layer.get(i);
            var neighbors = adj.getOrDefault(node, List.of());
            double sum = 0;
            int count = 0;
            for (var neighbor : neighbors) {
                var p = pos.get(neighbor);
                if (p != null) {
                    sum += p;
                    count++;
                }
            }
            bary.put(node, count > 0 ? sum / count : (double) i);
        }

        var sorted = new ArrayList<>(layer);
        sorted.sort(Comparator.comparingDouble(bary::get));

        if (!sorted.equals(layer)) {
            layer.clear();
            layer.addAll(sorted);
            return true;
        }
        return false;
    }
```

---

## Task 3: Run all tests and commit

**Files:** (no new edits — verification only)

- [ ] **Step 1: Run the three new tests**

```
mvn test -Dtest=ClassDiagramLayoutTest#minimizeCrossingsReducesCrossings+minimizeCrossingsNoNeighborNodeKeepsRelativeOrder+minimizeCrossingsRealizationReducesCrossings -pl .
```

Expected: 3 PASS

- [ ] **Step 2: Run the full test suite**

```
mvn test -pl .
```

Expected: All tests PASS. If any existing test fails, do not proceed — investigate and fix before committing.

- [ ] **Step 3: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java
git commit -m "feat: minimize edge crossings using Sugiyama barycenter method"
```
