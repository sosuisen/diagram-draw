# Interface-Implementation Relationship Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add detection and SVG visualization of `implements` relationships between classes in the same scanned package, using standard UML realization notation (dashed line + hollow triangle).

**Architecture:** Approach A — `ClassRelation` stores source=impl, target=interface (UML arrow direction). `ClassRelationSorter` and `ClassDiagramLayout.reassignLayers()` both reverse REALIZATION edges when building their internal graph so the interface appears above the implementation. `Dependency.draw()` adds a new branch for REALIZATION that draws a dashed line with a hollow triangle at the target (interface) end.

**Tech Stack:** Java 25, Java Class-File API (`java.lang.classfile`), JUnit 5, Maven

---

## File Map

| File | Change |
|------|--------|
| `src/main/java/com/sosuisha/classdiagram/DependencyType.java` | Add `REALIZATION` |
| `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java` | Scan `model.interfaces()` |
| `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationSorter.java` | Reverse REALIZATION edges |
| `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java` | Reverse REALIZATION in `reassignLayers` |
| `src/main/java/com/sosuisha/classdiagram/Dependency.java` | Draw dashed line + hollow triangle |
| `src/test/java/com/sosuisha/classdiagram/analyzer/fixture/FixtureService.java` | New interface fixture |
| `src/test/java/com/sosuisha/classdiagram/analyzer/fixture/FixtureServiceImpl.java` | New impl fixture |
| `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java` | Add REALIZATION detection test |
| `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationSorterTest.java` | Add interface-above-impl test |
| `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java` | Add layout ordering test |
| `src/test/java/com/sosuisha/classdiagram/DependencyTest.java` | Add REALIZATION SVG tests |

---

### Task 1: Add REALIZATION enum value and test fixtures

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/DependencyType.java`
- Create: `src/test/java/com/sosuisha/classdiagram/analyzer/fixture/FixtureService.java`
- Create: `src/test/java/com/sosuisha/classdiagram/analyzer/fixture/FixtureServiceImpl.java`

- [ ] **Step 1: Add REALIZATION to DependencyType**

Replace the entire content of `src/main/java/com/sosuisha/classdiagram/DependencyType.java`:

```java
package com.sosuisha.classdiagram;

/**
 * クラス間の依存関係の種類。
 */
public enum DependencyType {
    /** コンポジション（強い所有関係）*/
    COMPOSITION,
    /** 集約（弱い所有関係）*/
    AGGREGATION,
    /** 実現（インタフェースと実装クラスの関係）*/
    REALIZATION
}
```

- [ ] **Step 2: Create FixtureService interface**

Create `src/test/java/com/sosuisha/classdiagram/analyzer/fixture/FixtureService.java`:

```java
package com.sosuisha.classdiagram.analyzer.fixture;

/**
 * ClassRelationScannerのテスト用フィクスチャ。REALIZATION検出用インタフェース。
 */
public interface FixtureService {
    void execute();
}
```

- [ ] **Step 3: Create FixtureServiceImpl class**

Create `src/test/java/com/sosuisha/classdiagram/analyzer/fixture/FixtureServiceImpl.java`:

```java
package com.sosuisha.classdiagram.analyzer.fixture;

/**
 * ClassRelationScannerのテスト用フィクスチャ。FixtureServiceの実装クラス。
 */
public class FixtureServiceImpl implements FixtureService {
    @Override
    public void execute() {}
}
```

- [ ] **Step 4: Compile to verify**

Run: `mvn test-compile -q`
Expected: BUILD SUCCESS (no compilation errors)

- [ ] **Step 5: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/DependencyType.java
git add src/test/java/com/sosuisha/classdiagram/analyzer/fixture/FixtureService.java
git add src/test/java/com/sosuisha/classdiagram/analyzer/fixture/FixtureServiceImpl.java
git commit -m "feat: add REALIZATION to DependencyType; add FixtureService fixtures"
```

---

### Task 2: Detect REALIZATION in ClassRelationScanner

**Files:**
- Modify: `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java`
- Modify: `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java`

- [ ] **Step 1: Write the failing test**

Add this test method to `ClassRelationScannerTest`:

```java
@Test
void scanDetectsRealizationForImplementsInterface() {
    var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXTURE_PKG);
    assertTrue(relations.stream().anyMatch(r ->
        r.sourceClassInfo().simpleName().equals("FixtureServiceImpl") &&
        r.targetClassInfo().simpleName().equals("FixtureService") &&
        r.type() == DependencyType.REALIZATION &&
        !r.isMany()
    ));
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -Dtest=ClassRelationScannerTest#scanDetectsRealizationForImplementsInterface -q`
Expected: FAIL with `AssertionError` (relation not found)

- [ ] **Step 3: Add interface scanning to ClassRelationScanner**

In `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java`, inside `analyzeRelations()`, add the interface scan block **after** the existing `for (var field : model.fields())` loop and **before** the closing brace of the outer `for (var className : targetClassNames)` loop.

The method currently ends with:
```java
        for (var field : model.fields()) {
            var resolved = resolveFieldTarget(field, targetClassNames);
            if (resolved == null) continue;

            var type = constructorParamTypeNames.contains(resolved.targetClassName())
                ? DependencyType.AGGREGATION
                : DependencyType.COMPOSITION;
            relations.add(new ClassRelation(
                ClassInfo.fromFullyQualifiedName(className),
                ClassInfo.fromFullyQualifiedName(resolved.targetClassName()),
                type,
                resolved.isMany()
            ));
        }
    }

    return List.copyOf(relations);
```

Change it to:
```java
        for (var field : model.fields()) {
            var resolved = resolveFieldTarget(field, targetClassNames);
            if (resolved == null) continue;

            var type = constructorParamTypeNames.contains(resolved.targetClassName())
                ? DependencyType.AGGREGATION
                : DependencyType.COMPOSITION;
            relations.add(new ClassRelation(
                ClassInfo.fromFullyQualifiedName(className),
                ClassInfo.fromFullyQualifiedName(resolved.targetClassName()),
                type,
                resolved.isMany()
            ));
        }

        for (var iface : model.interfaces()) {
            var ifaceName = internalNameToBinary(iface.asInternalName());
            if (targetClassNames.contains(ifaceName)) {
                relations.add(new ClassRelation(
                    ClassInfo.fromFullyQualifiedName(className),
                    ClassInfo.fromFullyQualifiedName(ifaceName),
                    DependencyType.REALIZATION,
                    false
                ));
            }
        }
    }

    return List.copyOf(relations);
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn test -Dtest=ClassRelationScannerTest -q`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java
git add src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java
git commit -m "feat: detect REALIZATION (implements) in ClassRelationScanner"
```

---

### Task 3: Fix ClassRelationSorter to place interface above implementation

**Files:**
- Modify: `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationSorterTest.java`
- Modify: `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationSorter.java`

- [ ] **Step 1: Write the failing test**

Add this test method to `ClassRelationSorterTest`:

```java
@Test
void sortPlacesInterfaceAboveImplementationForRealization() {
    var impl = ci("FooImpl");
    var iface = ci("IFoo");
    var rel = new ClassRelation(impl, iface, DependencyType.REALIZATION, false);
    var layers = sorter.sort(List.of(rel));

    int ifaceLayerIdx = -1, implLayerIdx = -1;
    for (int i = 0; i < layers.size(); i++) {
        if (layers.get(i).contains(iface)) ifaceLayerIdx = i;
        if (layers.get(i).contains(impl)) implLayerIdx = i;
    }
    assertTrue(ifaceLayerIdx >= 0 && implLayerIdx >= 0,
        "Both interface and impl must appear in layers");
    assertTrue(ifaceLayerIdx < implLayerIdx,
        "Interface layer (%d) must have lower index than impl layer (%d)".formatted(ifaceLayerIdx, implLayerIdx));
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -Dtest=ClassRelationSorterTest#sortPlacesInterfaceAboveImplementationForRealization -q`
Expected: FAIL — interface ends up in a HIGHER index layer than impl (reversed)

- [ ] **Step 3: Fix ClassRelationSorter**

In `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationSorter.java`, find these lines (around line 45):

```java
        var uniqueEdges = relations.stream()
            .map(r -> Map.entry(r.sourceClassInfo(), r.targetClassInfo()))
            .collect(Collectors.toSet());
```

Replace with:

```java
        var uniqueEdges = relations.stream()
            .map(r -> r.type() == DependencyType.REALIZATION
                ? Map.entry(r.targetClassInfo(), r.sourceClassInfo())
                : Map.entry(r.sourceClassInfo(), r.targetClassInfo()))
            .collect(Collectors.toSet());
```

This reverses REALIZATION edges so the interface (previously `targetClassInfo`) becomes the graph source with low in-degree and rises to the top layer.

Also add the import near the top of `ClassRelationSorter.java` (after the existing `import com.sosuisha.classdiagram.analyzer.ClassRelation;` — note: `ClassRelationSorter` is in the `analyzer` sub-package, so it needs an explicit import):

```java
import com.sosuisha.classdiagram.DependencyType;
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn test -Dtest=ClassRelationSorterTest -q`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationSorter.java
git add src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationSorterTest.java
git commit -m "feat: reverse REALIZATION edges in ClassRelationSorter so interface goes to top layer"
```

---

### Task 4: Fix ClassDiagramLayout to place interface above implementation

**Files:**
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java`
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`

- [ ] **Step 1: Write the failing test**

Add this test to `ClassDiagramLayoutTest`:

```java
@Test
void layoutPlacesInterfaceAboveImplementationForRealization() {
    var iface = ci("IFoo");
    var impl = ci("FooImpl");
    var rel = new ClassRelation(impl, iface, DependencyType.REALIZATION, false);
    var layers = new ClassRelationSorter().sort(List.of(rel));
    var result = new ClassDiagramLayout(20, 40, 20, 20).layout(layers, List.of(rel));

    var ifaceBox = result.boxes().stream()
        .filter(b -> b.name().equals("IFoo")).findFirst().orElseThrow();
    var implBox = result.boxes().stream()
        .filter(b -> b.name().equals("FooImpl")).findFirst().orElseThrow();
    assertTrue(ifaceBox.y() < implBox.y(),
        "Interface y=%d must be less than impl y=%d".formatted(ifaceBox.y(), implBox.y()));
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -Dtest=ClassDiagramLayoutTest#layoutPlacesInterfaceAboveImplementationForRealization -q`
Expected: FAIL — impl box ends up above interface box (y values swapped)

- [ ] **Step 3: Fix ClassDiagramLayout.reassignLayers()**

In `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`, find the `reassignLayers` method. Locate these lines (the adjacency map construction, around line 147):

```java
        Map<ClassInfo, Set<ClassInfo>> adjacency = new HashMap<>();
        for (var rel : relations) {
            adjacency.computeIfAbsent(rel.sourceClassInfo(), k -> new HashSet<>())
                     .add(rel.targetClassInfo());
        }
```

Replace with:

```java
        Map<ClassInfo, Set<ClassInfo>> adjacency = new HashMap<>();
        for (var rel : relations) {
            ClassInfo layoutSrc = rel.type() == DependencyType.REALIZATION
                ? rel.targetClassInfo()
                : rel.sourceClassInfo();
            ClassInfo layoutTgt = rel.type() == DependencyType.REALIZATION
                ? rel.sourceClassInfo()
                : rel.targetClassInfo();
            adjacency.computeIfAbsent(layoutSrc, k -> new HashSet<>()).add(layoutTgt);
        }
```

(`ClassDiagramLayout` is in the same package as `DependencyType` — no import needed.)

- [ ] **Step 4: Run to verify it passes**

Run: `mvn test -Dtest=ClassDiagramLayoutTest -q`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java
git commit -m "feat: reverse REALIZATION edges in ClassDiagramLayout.reassignLayers"
```

---

### Task 5: Draw REALIZATION as dashed line with hollow triangle in Dependency

**Files:**
- Modify: `src/test/java/com/sosuisha/classdiagram/DependencyTest.java`
- Modify: `src/main/java/com/sosuisha/classdiagram/Dependency.java`

- [ ] **Step 1: Write the failing tests**

Add these methods to `DependencyTest`:

```java
private static Dependency realizationDep() {
    var source = new ClassBox("FooImpl");
    source.setPosition(50, 200);
    var target = new ClassBox("IFoo");
    target.setPosition(50, 0);
    return new Dependency(source, target, DependencyType.REALIZATION);
}

@Test
void drawRealizationHasDataAttribute() {
    assertTrue(realizationDep().draw().contains("data-diagram-draw-type=\"realization\""));
}

@Test
void drawRealizationHasDashedLine() {
    assertTrue(realizationDep().draw().contains("stroke-dasharray"));
}

@Test
void drawRealizationHasHollowTriangle() {
    var svg = realizationDep().draw();
    assertTrue(svg.contains("<polygon"));
    assertTrue(svg.contains("fill=\"white\""));
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `mvn test -Dtest="DependencyTest#drawRealizationHasDataAttribute+drawRealizationHasDashedLine+drawRealizationHasHollowTriangle" -q`
Expected: FAIL (REALIZATION type falls through to diamond drawing with wrong attributes)

- [ ] **Step 3: Add constants and drawRealization method to Dependency**

In `src/main/java/com/sosuisha/classdiagram/Dependency.java`:

**3a.** Add two constants after the existing diamond constants (around line 13):

```java
    private static final int TRIANGLE_LEN = 20;
    private static final int TRIANGLE_HALF_WIDTH = 8;
```

**3b.** In `draw()`, add a REALIZATION branch right after `sp` and `tp` are calculated (after line 77, before the `// ダイアモンド` comment):

```java
        double[] sp = edgeIntersection(source, nx, ny);
        double[] tp = edgeIntersection(target, -nx, -ny);

        if (type == DependencyType.REALIZATION) {
            return drawRealization(sp, tp, nx, ny);
        }

        // ダイアモンドの後端をソース辺上に合わせ、全体をボックス外に配置する
```

**3c.** Add the private helper method `drawRealization` at the end of the class, before the final `}`:

```java
    private String drawRealization(double[] sp, double[] tp, double nx, double ny) {
        double px = -ny;
        double py = nx;
        double baseCx = tp[0] - nx * TRIANGLE_LEN;
        double baseCy = tp[1] - ny * TRIANGLE_LEN;
        double bx1 = baseCx + px * TRIANGLE_HALF_WIDTH;
        double by1 = baseCy + py * TRIANGLE_HALF_WIDTH;
        double bx2 = baseCx - px * TRIANGLE_HALF_WIDTH;
        double by2 = baseCy - py * TRIANGLE_HALF_WIDTH;

        var sb = new StringBuilder();
        sb.append("<g data-diagram-draw=\"dependency\" data-diagram-draw-type=\"realization\">");
        sb.append("<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"black\" stroke-dasharray=\"8,4\"/>".formatted(
            sp[0], sp[1], baseCx, baseCy));
        sb.append("<polygon points=\"%.1f,%.1f %.1f,%.1f %.1f,%.1f\" fill=\"white\" stroke=\"black\"/>".formatted(
            tp[0], tp[1], bx1, by1, bx2, by2));
        sb.append("</g>");
        return sb.toString();
    }
```

- [ ] **Step 4: Run to verify all Dependency tests pass**

Run: `mvn test -Dtest=DependencyTest -q`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/Dependency.java
git add src/test/java/com/sosuisha/classdiagram/DependencyTest.java
git commit -m "feat: draw REALIZATION as dashed line with hollow triangle in Dependency"
```

---

### Task 6: Run full test suite and verify no regressions

**Files:** none (verification only)

- [ ] **Step 1: Run all tests**

Run: `mvn test`
Expected: BUILD SUCCESS, all tests PASS

If any test fails, investigate before proceeding. Common issues:
- `ClassDiagramLayout` missing `import com.sosuisha.classdiagram.DependencyType` → add the import
- `ClassRelationScannerTest` new fixture tests interfere with existing tests → check fixture package scan includes both new classes

- [ ] **Step 2: Commit if any fixes were needed**

If no changes were needed, no additional commit is required. If fixes were needed:

```
git add <fixed files>
git commit -m "fix: <description of fix>"
```
