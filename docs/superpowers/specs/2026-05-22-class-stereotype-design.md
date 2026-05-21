# Class Stereotype — Design Spec

**Date**: 2026-05-22  
**Status**: Approved

## Summary

Add a general stereotype mechanism to the class diagram pipeline. Initially support `«interface»` displayed above the class name in `ClassBox`. The design must be extensible for future stereotypes (`«abstract»`, `«enum»`, etc.).

---

## Scope

- In scope: new `ClassStereotype` enum; `ClassInfo` gains `stereotype` field; `ClassRelationScanner` detects interface flag; `ClassBox` renders stereotype text; backward-compatible constructors
- Out of scope: `«abstract»`, `«enum»` detection (reserved for future; enum value placeholders may be added)

---

## Architecture

### Data flow (unchanged)

```
ClassRelationScanner.scan()    → List<ClassRelation>   ← ClassInfo now carries stereotype
ClassRelationSorter.sort()     → List<List<ClassInfo>>
ClassDiagramLayout.layout()    → LayoutResult          ← ClassBox now receives stereotype
SVGBuilder.build()             → String (SVG)
```

### New file

**`ClassStereotype.java`** (new enum, `com.sosuisha.classdiagram` package):

```java
public enum ClassStereotype {
    NONE,
    INTERFACE
}
```

---

## Changes per file

### `ClassInfo.java` — add `stereotype` component

```java
public record ClassInfo(String packageName, String simpleName, ClassStereotype stereotype) {
    // compact canonical constructor: null-checks all three fields
    public ClassInfo(String packageName, String simpleName) {
        this(packageName, simpleName, ClassStereotype.NONE);
    }
    // fromFullyQualifiedName(fqn) — returns NONE (unchanged behaviour)
    // fromFullyQualifiedName(fqn, ClassStereotype) — new overload
}
```

Existing callers using `new ClassInfo(pkg, name)` continue to compile unchanged.  
`fromFullyQualifiedName(fqn)` still returns `ClassStereotype.NONE`; a new overload `fromFullyQualifiedName(fqn, ClassStereotype)` is added for scanner use.

### `ClassRelationScanner.java` — two-pass scanning

**Pass 1** — `collectStereotypes(classRoot, targetClassNames) → Map<String fqn, ClassStereotype>`  
Iterates all target `.class` files; uses `ClassFile.of().parse(...).flags().has(AccessFlag.INTERFACE)` to determine `INTERFACE` vs `NONE`.

**Pass 2** — existing `analyzeRelations`, modified  
When constructing `ClassInfo` for source or target, look up the stereotype map:
```java
ClassInfo.fromFullyQualifiedName(fqn, stereotypes.getOrDefault(fqn, ClassStereotype.NONE))
```

All `ClassInfo` objects in the returned `ClassRelation` list carry the correct stereotype.

### `ClassBox.java` — add stereotype rendering

Constructor change:

```java
// new 2-arg constructor (name + stereotype)
public ClassBox(String name, ClassStereotype stereotype)
// existing 3-arg constructor gains stereotype param
public ClassBox(String name, ClassStereotype stereotype, List<String> fields, List<String> methods)
// existing 1-arg constructor delegates: ClassBox(name, ClassStereotype.NONE)
public ClassBox(String name)  // unchanged signature, adds NONE default
```

**`height()` change**  
When `stereotype != NONE`, the name compartment holds 2 lines (stereotype + name) instead of 1:
```java
int nameLineCount = (stereotype == ClassStereotype.NONE) ? 1 : 2;
compartmentHeight(nameLineCount) + compartmentHeight(fields.size()) + compartmentHeight(methods.size())
```

**`width()` change**  
The stereotype label string (`«interface»` etc.) also participates in max-length calculation.

**`draw()` change — `appendNameCompartment`**  
When `stereotype != NONE`, render stereotype text on the first line in italic, then class name on the second line, both centered:

```xml
<text x="{cx}" y="{stereotypeY}" font-size="12" font-style="italic" text-anchor="middle">«interface»</text>
<text x="{cx}" y="{nameY}"       font-size="14" text-anchor="middle">{name}</text>
```

Stereotype font size: 12 (slightly smaller than name). Font style: italic.

Stereotype label strings:
| `ClassStereotype` | Label text |
|---|---|
| `INTERFACE` | `«interface»` |

### `ClassDiagramLayout.java` — pass stereotype to ClassBox

One-line change in Step 1:
```java
// before
boxMap.put(info, new ClassBox(info.simpleName()));
// after
boxMap.put(info, new ClassBox(info.simpleName(), info.stereotype()));
```

No other changes.

---

## SVG output (name compartment, stereotype = INTERFACE)

```xml
<!-- stereotype line (italic, font-size 12) -->
<text x="{cx}" y="{stereotypeY}" font-size="12" font-style="italic" text-anchor="middle">«interface»</text>
<!-- name line (font-size 14) -->
<text x="{cx}" y="{nameY}"       font-size="14" text-anchor="middle">{name}</text>
```

---

## Edge cases

| Case | Behaviour |
|------|-----------|
| `stereotype == NONE` | No stereotype line; layout identical to current |
| `«interface»` text longer than class name | Width auto-calculation uses max of both |
| Manual `new ClassBox("X")` | `NONE` stereotype; backward compatible |
| Tests using `new ClassInfo(pkg, name)` | `NONE` stereotype; no test changes needed |

---

## Tests

| Test | Assertion |
|------|-----------|
| `ClassBox` with `INTERFACE` stereotype renders `«interface»` text in SVG | `draw()` output contains `«interface»` |
| `ClassBox` with `INTERFACE` stereotype has taller name compartment than `NONE` | `height()` difference = one extra line |
| `ClassBox` with `INTERFACE` stereotype widens for long stereotype label | `width()` accounts for `«interface»` length |
| `ClassBox` with `NONE` stereotype unchanged | `draw()` output matches current format |
| `ClassRelationScanner` marks interface classes with `INTERFACE` stereotype | Scanned `ClassInfo.stereotype()` == `INTERFACE` for interfaces |
| `ClassRelationScanner` marks regular classes with `NONE` | Scanned `ClassInfo.stereotype()` == `NONE` for concrete classes |
| Integration: `ClassDiagramGenerator` produces SVG with `«interface»` for interface classes | SVG contains `«interface»` label |
