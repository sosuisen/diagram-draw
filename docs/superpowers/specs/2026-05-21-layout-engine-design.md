# Layout Engine Design

## Overview

Add a layout/positioning engine and a top-level facade to `diagram-draw` that take the output of `ClassRelationScanner` + `ClassRelationSorter` and produce a ready-to-render SVG string. This also removes the now-redundant `RelationType` enum and replaces all its uses with the existing `DependencyType`.

**Primary use case:** A Maven plugin (or test) calls `ClassDiagramGenerator.generate(Path, String)` and receives a complete SVG class diagram.

---

## Changes to Existing Code

### Delete `RelationType.java` and `RelationTypeTest.java`

`RelationType` duplicates `DependencyType`. Remove it entirely.

### Refactor `ClassRelation` — replace `RelationType` with `DependencyType`

```java
// com.sosuisha.classdiagram.analyzer
import com.sosuisha.classdiagram.DependencyType;

public record ClassRelation(
    ClassInfo sourceClassInfo,
    ClassInfo targetClassInfo,
    DependencyType type,       // was RelationType
    boolean isMany
) {
    public ClassRelation {
        Objects.requireNonNull(sourceClassInfo, "sourceClassInfo must not be null");
        Objects.requireNonNull(targetClassInfo, "targetClassInfo must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }
}
```

### Refactor `ClassRelationScanner`

Replace all `RelationType.COMPOSITION` / `RelationType.AGGREGATION` with `DependencyType.COMPOSITION` / `DependencyType.AGGREGATION`. No structural changes.

### Update tests

`ClassRelationScannerTest`, `ClassRelationTest`, and any other test that imports or asserts on `RelationType` must be updated to `DependencyType`.

---

## New Package Structure

All new files live in `com.sosuisha.classdiagram` (alongside `ClassBox`, `SVGBuilder`).

```
com.sosuisha.classdiagram/
    LayoutResult.java          ← NEW
    ClassDiagramLayout.java    ← NEW
    ClassDiagramGenerator.java ← NEW
```

---

## `LayoutResult` Record

```java
package com.sosuisha.classdiagram;

public record LayoutResult(
    List<ClassBox> boxes,
    List<Dependency> dependencies,
    int canvasWidth,
    int canvasHeight
) {}
```

Immutable snapshot of one layout computation. `boxes` and `dependencies` are unmodifiable copies.

---

## `ClassDiagramLayout` — Positioning Engine

### Constructor

```java
public ClassDiagramLayout(int horizontalGap, int verticalGap,
                           int canvasPaddingX, int canvasPaddingY)
```

All four parameters are stored as fields. `Objects.requireNonNull` is not needed (primitives).

### API

```java
// @throws NullPointerException if layers or relations is null
public LayoutResult layout(List<List<ClassInfo>> layers, List<ClassRelation> relations)
```

### Algorithm

#### Step 1 — Longest-path layer reassignment

The sorter produces layers using Kahn's BFS (earliest-layer assignment). The layout engine reassigns each node to its **latest possible** layer so leaf nodes are pushed to the bottom:

1. Build adjacency map `source → Set<target>` from `relations`.
2. For each node compute `depth[v]` — length of the longest path from `v` to any leaf:
   - Process nodes in reverse topological order (iterate the sorter layers in reverse).
   - `depth[v] = 0` if `v` has no outgoing edges in the adjacency map.
   - Otherwise `depth[v] = max(depth[w] + 1)` for all successors `w`.
3. `maxDepth = max(depth[v])` over all nodes.
4. `layer[v] = maxDepth - depth[v]`.
5. Re-group `ClassInfo` into `List<List<ClassInfo>>` indexed by `layer[v]`.

**Example where longest-path differs from Kahn's:**
- Relations: `A→B→D`, `A→C` (C has no further dependencies)
- Kahn's BFS result: `[[A], [B, C], [D]]`
- Longest-path result: `[[A], [B], [C, D]]` — C is pushed to the bottom layer

#### Step 2 — Create `ClassBox` per class

For each `ClassInfo` in the reassigned layers, create `new ClassBox(classInfo.simpleName())`.
Fields and methods are empty (not in scope at this stage).
Store in a `Map<ClassInfo, ClassBox>` for lookup in later steps.

#### Step 3 — Position boxes (centered per layer)

For each layer `i` (0 = top):

1. Compute `layerWidth[i]` = sum of `box.width()` for all boxes in the layer + `(n−1) × horizontalGap`.
2. `canvasContentWidth` = `max(layerWidth[i])` across all layers.
3. `startX[i]` = `canvasPaddingX + (canvasContentWidth − layerWidth[i]) / 2` — centers the layer group.
4. Assign each box's x by accumulating widths + `horizontalGap` from `startX[i]`.
5. `layerY[i]` = `canvasPaddingY + sum of (maxBoxHeightInLayer[j] + verticalGap)` for `j < i`.
6. Call `box.setPosition(x, layerY[i])` for each box.

#### Step 4 — Compute canvas size

- `canvasWidth` = `canvasContentWidth + 2 × canvasPaddingX`
- `canvasHeight` = `sum of maxBoxHeightInLayer[i]` + `(numLayers−1) × verticalGap` + `2 × canvasPaddingY`

#### Step 5 — Create `Dependency` per relation

For each `ClassRelation`:
- Look up `sourceBox = boxMap.get(relation.sourceClassInfo())`
- Look up `targetBox = boxMap.get(relation.targetClassInfo())`
- Create `new Dependency(sourceBox, targetBox, relation.type())`

No mapping step needed — `ClassRelation.type()` is already `DependencyType`.

#### Step 6 — Return

```java
return new LayoutResult(
    List.copyOf(boxes),
    List.copyOf(dependencies),
    canvasWidth,
    canvasHeight
);
```

---

## `ClassDiagramGenerator` — Facade

### Constructor

```java
public ClassDiagramGenerator(int horizontalGap, int verticalGap,
                               int canvasPaddingX, int canvasPaddingY)
```

Stores all four parameters; constructs a `ClassDiagramLayout` lazily (or eagerly) with them.

### API

```java
// @throws NullPointerException if classRoot or packageName is null
// @throws CircularRelationException if a cycle is detected
public String generate(Path classRoot, String packageName)
```

### Pipeline

```java
var relations = new ClassRelationScanner().scan(classRoot, packageName);
var layers    = new ClassRelationSorter().sort(relations);
var result    = new ClassDiagramLayout(horizontalGap, verticalGap,
                                        canvasPaddingX, canvasPaddingY)
                    .layout(layers, relations);
var builder   = new SVGBuilder(result.canvasWidth(), result.canvasHeight());
result.boxes().forEach(builder::add);
result.dependencies().forEach(builder::add);
return builder.build();
```

If `relations` is empty (no relations found), return an empty SVG: `new SVGBuilder(2 * canvasPaddingX, 2 * canvasPaddingY).build()`.

---

## Test Strategy

### `ClassDiagramLayoutTest`

Unit tests — construct `ClassInfo` and `ClassRelation` directly, no compiled classes needed.

| Test | Input | Assertion |
|------|-------|-----------|
| `layoutLongestPathReassignment` | `A→B→D`, `A→C` | Layer 0=[A], Layer 1=[B], Layer 2=[C,D] |
| `layoutPositionsTopLayer` | `A→B` | Box A: `y == canvasPaddingY` |
| `layoutCentersLayer` | Two boxes in one layer | Both boxes' x-centers average = canvas midpoint |
| `layoutCanvasSize` | Single relation | `canvasWidth >= 2*canvasPaddingX`, `canvasHeight >= 2*canvasPaddingY` |
| `layoutCreatesDependencies` | `A→B` COMPOSITION | One `Dependency` with `type == DependencyType.COMPOSITION` |
| `layoutThrowsForNullLayers` | null layers | `NullPointerException` |
| `layoutThrowsForNullRelations` | null relations | `NullPointerException` |

### `ClassDiagramGeneratorTest`

Integration test using the fixture package:

```java
var svg = new ClassDiagramGenerator(20, 40, 20, 20)
    .generate(Path.of("target/test-classes"),
              "com.sosuisha.classdiagram.analyzer.fixture");
assertTrue(svg.contains("FixtureOrder"));
assertTrue(svg.contains("FixtureItem"));
assertTrue(svg.contains("FixtureCustomer"));
assertTrue(svg.startsWith("<svg"));
```

---

## `Objects.requireNonNull` Convention

- `ClassDiagramLayout.layout(layers, relations)` — both params
- `ClassDiagramGenerator.generate(classRoot, packageName)` — both params
- `LayoutResult` compact constructor — `boxes`, `dependencies` (the int fields are primitives)
