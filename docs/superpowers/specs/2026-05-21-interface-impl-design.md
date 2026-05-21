# Interface-Implementation Relationship in Class Diagram

**Date**: 2026-05-21  
**Status**: Approved

## Summary

Add detection and visualization of interface-implementation relationships (`implements`) to the class diagram generator. Only relationships where both the interface and implementing class belong to the scanned package are included. Class inheritance (`extends`) is out of scope.

---

## Architecture

### Relationship Direction Convention

- `ClassRelation.sourceClassInfo()` = implementing class  
- `ClassRelation.targetClassInfo()` = interface  

This matches the UML arrow direction (arrow points from impl → interface). However, for layout purposes (interface above impl), edges are **reversed** when building the dependency graph in two places: `ClassRelationSorter` and `ClassDiagramLayout.reassignLayers()`.

---

## Components

### 1. `DependencyType` (modify)

Add `REALIZATION` value:

```java
public enum DependencyType {
    COMPOSITION,
    AGGREGATION,
    REALIZATION
}
```

### 2. `ClassRelationScanner` (modify)

After the existing field scan loop, add an interface scan per class:

- Call `model.interfaces()` → `List<ClassEntry>`
- Convert each entry via `internalNameToBinary(entry.asInternalName())`
- If the result is in `targetClassNames`, create:  
  `new ClassRelation(ClassInfo.fromFQN(implClass), ClassInfo.fromFQN(interfaceClass), REALIZATION, false)`
- Same "target package only" constraint as field scanning

### 3. `ClassRelationSorter` (modify)

When building the adjacency map and in-degree map, reverse REALIZATION edges:

```
For REALIZATION edges (source=impl, target=interface):
  adjacency[interface → impl]
  inDegree[impl]++          // impl gets high in-degree → lower layer
  inDegree[interface] stays 0 (or low) → top layer
```

All other edge types remain unchanged.

### 4. `ClassDiagramLayout.reassignLayers()` (modify)

Same reversal as the sorter when building the adjacency map for longest-path depth calculation:

```
For REALIZATION edges:
  adjacency[targetClassInfo(interface) → sourceClassInfo(impl)]
```

This ensures the interface receives a higher depth value and is placed in the top layer.

### 5. `Dependency.draw()` (modify)

Add a `REALIZATION` branch:

- **Line style**: dashed (`stroke-dasharray="8,4"`)
- **Arrowhead**: hollow equilateral triangle at the target (interface) end
  - Tip at `tp` = edge intersection with the interface box
  - Base center at `tp - nx * TRIANGLE_LEN`
  - Two base vertices = base center ± perpendicular * `TRIANGLE_HALF_WIDTH`
  - Fill: white, Stroke: black
- **Line**: from source (impl) edge `sp` to triangle base center (so the line does not overlap the triangle interior)
- Triangle constants (same order of magnitude as the diamond): `TRIANGLE_LEN = 20`, `TRIANGLE_HALF_WIDTH = 8`

SVG `data-diagram-draw-type` attribute: `"realization"`

---

## Test Fixtures

Add two files under `src/test/java/com/sosuisha/classdiagram/analyzer/fixture/`:

| File | Role |
|------|------|
| `FixtureService.java` | Interface with one method signature |
| `FixtureServiceImpl.java` | Class that `implements FixtureService`; no same-package fields |

---

## Tests

| Test class | Assertion |
|-----------|-----------|
| `ClassRelationScannerTest` | REALIZATION detected; source=`FixtureServiceImpl`, target=`FixtureService` |
| `ClassRelationSorterTest` | `FixtureService` is in a strictly higher layer than `FixtureServiceImpl` |
| `ClassDiagramGeneratorTest` or `DiagramDrawExampleTest` | Generated SVG contains an element with `data-diagram-draw-type="realization"` |

---

## Out of Scope

- Class inheritance (`extends`)
- Interfaces from outside the scanned package
- Multiple interface implementation (handled automatically by the loop over `model.interfaces()`)
