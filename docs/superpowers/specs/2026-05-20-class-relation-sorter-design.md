# Class Relation Sorter Design

## Overview

Introduce `ClassInfo` as a structured class identity type, refactor `ClassRelation` to use it, update `ClassRelationScanner` accordingly, and add `ClassRelationSorter` — a topological sort engine that takes `List<ClassRelation>` and returns `List<List<ClassInfo>>` (layers of class info objects, top to bottom). The layout engine decides which fields of `ClassInfo` to display.

**Primary use case:** After `ClassRelationScanner` produces a relation graph, `ClassRelationSorter` determines the rendering order for the SVG layout engine.

---

## Package Structure

All new and modified files live in `com.sosuisha.classdiagram.analyzer`.

```
com.sosuisha.classdiagram.analyzer/
    ClassInfo.java                  ← NEW
    ClassRelation.java              ← MODIFIED
    ClassRelationScanner.java       ← MODIFIED
    RelationType.java               (unchanged)
    CircularRelationException.java  ← NEW
    ClassRelationSorter.java        ← NEW
```

---

## Data Model Changes

### `ClassInfo` (new)

```java
package com.sosuisha.classdiagram.analyzer;

public record ClassInfo(String packageName, String simpleName) {

    public static ClassInfo fromFullyQualifiedName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return new ClassInfo(fqn.substring(0, dot), fqn.substring(dot + 1));
    }
}
```

- `packageName` — e.g. `"com.sosuisha.classdiagram"`
- `simpleName` — e.g. `"Order"` (no package prefix)
- Full name is `packageName + "." + simpleName`
- Designed for future extension (methods, fields) via additional record methods
- Record equality: two `ClassInfo` instances are equal when both `packageName` and `simpleName` match — safe to use as map keys

### `ClassRelation` (modified)

```java
public record ClassRelation(
    ClassInfo sourceClassInfo,
    ClassInfo targetClassInfo,
    RelationType type,
    boolean isMany
) {}
```

Replaces `String sourceClass` / `String targetClass` with typed `ClassInfo` fields.

---

## `ClassRelationScanner` Changes

The scanner currently builds FQN strings like `"com.sosuisha.classdiagram.Order"`. It now calls `ClassInfo.fromFullyQualifiedName(fqn)` to construct `ClassInfo` objects, which are passed to `ClassRelation`.

```java
// Before
new ClassRelation(sourceClassName, targetClassName, type, isMany)

// After
new ClassRelation(
    ClassInfo.fromFullyQualifiedName(sourceClassName),
    ClassInfo.fromFullyQualifiedName(targetClassName),
    type,
    isMany
)
```

---

## `CircularRelationException` (new)

Unchecked exception thrown by `ClassRelationSorter` when a cycle is detected.

```java
package com.sosuisha.classdiagram.analyzer;

public class CircularRelationException extends RuntimeException {
    public CircularRelationException(String message) {
        super(message);
    }
}
```

---

## `ClassRelationSorter` Algorithm

### API

```java
public class ClassRelationSorter {
    // @throws NullPointerException if relations is null
    // @throws CircularRelationException if a cycle is detected
    public List<List<ClassInfo>> sort(List<ClassRelation> relations) { ... }
}
```

Input: `List<ClassRelation>` — the relation graph from `ClassRelationScanner`.
Output: `List<List<ClassInfo>>` — topological layers. Index 0 is the top (owner classes with no incoming edges); the last index is the bottom (pure components). The layout engine decides which fields of `ClassInfo` to use for display.

### Steps (Kahn's BFS)

1. **Collect nodes** — extract unique `ClassInfo` instances from all `sourceClassInfo` and `targetClassInfo` values. `ClassInfo` record equality is used for deduplication.

2. **Build graph** — construct:
   - `adjacency`: `Map<ClassInfo, Set<ClassInfo>>` — source → set of targets
   - `inDegree`: `Map<ClassInfo, Integer>` — node → count of incoming edges

3. **Seed first layer** — collect all nodes where `inDegree == 0` into `currentLayer`.

4. **BFS loop** — while `currentLayer` is not empty:
   - Add `currentLayer` (sorted by `simpleName` for determinism) to result
   - For each node in `currentLayer`, decrement `inDegree` of each successor; if a successor reaches 0, add to `nextLayer`
   - Set `currentLayer = nextLayer`

5. **Cycle check** — if total emitted nodes < total nodes, throw:
   ```
   CircularRelationException("Circular relation detected among: [SimpleName1, SimpleName2, ...]")
   ```
   The listed names are the `simpleName` values of nodes that never reached in-degree 0.

6. **Return** the accumulated layer list.

### Edge direction

`sourceClassInfo` **owns** `targetClassInfo` (has it as a field). Owners are higher in the diagram; components are lower. An edge points downward: source → target. Classes with no incoming edges (nobody owns them) appear in layer 0.

### Empty input

`sort(List.of())` returns an empty list `[]`.

### Note on longest-path assignment

Kahn's BFS assigns each class to its **earliest possible** layer. A future layout phase may reassign layers using the longest-path (critical path) algorithm to produce a more balanced visual layout.

---

## Test Strategy

### `ClassRelationSorterTest`

Test cases construct `ClassInfo` objects directly — no compiled fixture classes needed.

| Scenario | Input | Expected output |
|----------|-------|-----------------|
| Empty | `[]` | `[]` |
| Linear chain A→B→C | two relations | `[[A], [B], [C]]` |
| Diamond A→B, A→C, B→D, C→D | four relations | `[[A], [B, C], [D]]` |
| Cycle A→B, B→A | two relations | throws `CircularRelationException` |

### `ClassRelationScannerTest` (updated assertions)

Existing tests assert on `sourceClass` / `targetClass` strings. Update to assert on:
- `relation.sourceClassInfo().simpleName()`
- `relation.sourceClassInfo().packageName()`
- `relation.targetClassInfo().simpleName()`

---

## `Objects.requireNonNull` Convention

All public methods whose parameters are reference types call `Objects.requireNonNull` and document `@throws NullPointerException` in Javadoc. This applies to:
- `ClassInfo.fromFullyQualifiedName(String fqn)`
- `ClassRelationSorter.sort(List<ClassRelation> relations)`
