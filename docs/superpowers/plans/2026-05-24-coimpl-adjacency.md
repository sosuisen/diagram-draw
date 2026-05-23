# Co-Implementor Adjacency Constraint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Modify `minimizeCrossings()` so that classes implementing the same interface are always placed adjacent in the horizontal layer order.

**Architecture:** Build an `ifaceOfImpl` map (implementer→interface) from REALIZATION relations. In `sortLayerByBarycenter()`, replace the single-key sort with a two-level sort: group nodes by their interface (or themselves if no interface), compute a group barycenter as the mean of individual barycenters, sort groups by group barycenter, then sort within each group by individual barycenter.

**Tech Stack:** Java 25, Maven (`mvn test` to run tests)

---

## File Map

| Action | File |
|--------|------|
| Modify | `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java` |
| Modify | `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java` |

---

## Task 1: Write failing test for co-implementor adjacency

**Files:**
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java`

- [ ] **Step 1: Append new test method at the end of `ClassDiagramLayoutTest`**

Append this method inside `ClassDiagramLayoutTest`, after the last existing `@Test` method:

```java
@Test
void coImplementorsStayAdjacentWhenSameBarycenter() {
    // Layer 0: [A, IFoo, B]  A at 0, IFoo at 1, B at 2
    // Layer 1: [ImplFoo1, X, ImplFoo2]
    //   ImplFoo1 → IFoo (REALIZATION)         bary = 1
    //   COMP(A, X) and COMP(B, X)             bary(X) = (0+2)/2 = 1
    //   ImplFoo2 → IFoo (REALIZATION)         bary = 1
    // Without grouping: all bary = 1, stable sort keeps initial order
    //   → [ImplFoo1, X, ImplFoo2]  (co-implementors NOT adjacent)
    // With grouping: IFoo-group [ImplFoo1, ImplFoo2] and X-group [X]
    //   → [ImplFoo1, ImplFoo2, X]  (co-implementors adjacent)
    var a = ci("A"); var b = ci("B"); var x = ci("X");
    var iFoo = new ClassInfo(PKG, "IFoo", ClassStereotype.INTERFACE);
    var implFoo1 = ci("ImplFoo1"); var implFoo2 = ci("ImplFoo2");

    var layers = List.of(List.of(a, iFoo, b), List.of(implFoo1, x, implFoo2));
    var rels = List.of(
        new ClassRelation(implFoo1, iFoo, DependencyType.REALIZATION, false),
        new ClassRelation(implFoo2, iFoo, DependencyType.REALIZATION, false),
        new ClassRelation(a, x, DependencyType.COMPOSITION, false),
        new ClassRelation(b, x, DependencyType.COMPOSITION, false)
    );
    var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);

    var box1 = result.boxes().stream().filter(bx -> bx.name().equals("ImplFoo1")).findFirst().orElseThrow();
    var box2 = result.boxes().stream().filter(bx -> bx.name().equals("ImplFoo2")).findFirst().orElseThrow();

    // ImplFoo1 and ImplFoo2 have identical name length → identical widths.
    // Adjacent ⇔ |x1 - x2| == width + horizontalGap(=20).
    int dist = Math.abs(box1.x() - box2.x());
    int adjacentDist = box1.width() + 20;
    assertEquals(adjacentDist, dist,
        "ImplFoo1 and ImplFoo2 (co-implementors of IFoo) must be adjacent in the same layer");
}
```

- [ ] **Step 2: Run the new test and confirm it FAILS**

```
mvn test -Dtest="ClassDiagramLayoutTest#coImplementorsStayAdjacentWhenSameBarycenter" -pl .
```

Expected: FAIL. The current `sortLayerByBarycenter` does single-key bary sort; with all three nodes having bary=1, stable sort preserves `[ImplFoo1, X, ImplFoo2]` and the two ImplFoo* are not adjacent.

- [ ] **Step 3: Commit the failing test**

```
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java
git commit -m "test: add failing test for co-implementor adjacency constraint (RED)"
```

---

## Task 2: Implement `ifaceOfImpl` and two-level sort

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`

- [ ] **Step 1: Build `ifaceOfImpl` map in `minimizeCrossings()`**

In `minimizeCrossings()`, after the `for (var rel : relations) { ... }` block that builds `upNeighbors` and `downNeighbors` (around line 230), add:

```java
        Map<ClassInfo, ClassInfo> ifaceOfImpl = new HashMap<>();
        for (var rel : relations) {
            if (rel.type() == DependencyType.REALIZATION) {
                ifaceOfImpl.put(rel.sourceClassInfo(), rel.targetClassInfo());
            }
        }
```

- [ ] **Step 2: Pass `ifaceOfImpl` to both `sortLayerByBarycenter` call sites**

Change the two `sortLayerByBarycenter` calls inside the pass loop:

```java
                for (int i = 0; i + 1 < result.size(); i++) {
                    changed |= sortLayerByBarycenter(result.get(i + 1), result.get(i), upNeighbors, ifaceOfImpl);
                }
```

and

```java
                for (int i = result.size() - 2; i >= 0; i--) {
                    changed |= sortLayerByBarycenter(result.get(i), result.get(i + 1), downNeighbors, ifaceOfImpl);
                }
```

- [ ] **Step 3: Replace `sortLayerByBarycenter()` body with the two-level sort**

Replace the entire `sortLayerByBarycenter()` method with:

```java
    private boolean sortLayerByBarycenter(
            List<ClassInfo> layer, List<ClassInfo> referenceLayer,
            Map<ClassInfo, List<ClassInfo>> adj,
            Map<ClassInfo, ClassInfo> ifaceOfImpl) {
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

        // Group nodes by their group key: ifaceOfImpl(node) or the node itself.
        // Co-implementors of the same interface share a group key → become a contiguous block.
        Map<ClassInfo, List<ClassInfo>> groups = new LinkedHashMap<>();
        for (var node : layer) {
            var key = ifaceOfImpl.getOrDefault(node, node);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(node);
        }

        // Group barycenter = mean of individual barycenters in the group.
        Map<ClassInfo, Double> groupBary = new HashMap<>();
        for (var entry : groups.entrySet()) {
            double sum = 0;
            for (var n : entry.getValue()) sum += bary.get(n);
            groupBary.put(entry.getKey(), sum / entry.getValue().size());
        }

        // Stable sort groups by group barycenter; within each group, stable sort by individual barycenter.
        var sortedGroups = new ArrayList<>(groups.entrySet());
        sortedGroups.sort((e1, e2) -> Double.compare(
            groupBary.get(e1.getKey()), groupBary.get(e2.getKey())));

        var sorted = new ArrayList<ClassInfo>();
        for (var entry : sortedGroups) {
            var members = new ArrayList<>(entry.getValue());
            members.sort(Comparator.comparingDouble(bary::get));
            sorted.addAll(members);
        }

        if (!sorted.equals(layer)) {
            layer.clear();
            layer.addAll(sorted);
            return true;
        }
        return false;
    }
```

- [ ] **Step 4: Run the new test and confirm it PASSES**

```
mvn test -Dtest="ClassDiagramLayoutTest#coImplementorsStayAdjacentWhenSameBarycenter" -pl .
```

Expected: PASS.

- [ ] **Step 5: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java
git commit -m "feat: enforce co-implementor adjacency via two-level barycenter sort"
```

---

## Task 3: Full test suite verification

**Files:** (verification only, no edits)

- [ ] **Step 1: Run the full test suite**

```
mvn test -pl .
```

Expected: 175 tests pass (174 pre-existing + 1 new co-implementor adjacency test). If any pre-existing test fails, do NOT proceed — investigate.
