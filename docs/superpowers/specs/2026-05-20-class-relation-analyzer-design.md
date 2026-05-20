# Class Relation Analyzer Design

## Overview

Add reflection-based class analysis to `diagram-draw` that extracts composition and aggregation relationships from compiled Java classes. The analyzer lives in a new subpackage `com.sosuisha.classdiagram.analyzer` and returns a plain data model (`List<ClassRelation>`) for downstream use by the diagram-draw rendering pipeline.

**Primary use case:** A Maven plugin passes `target/classes/` root path and a package name; the analyzer scans `.class` files and returns the relationships.

---

## Package Structure

```
com.sosuisha.classdiagram
├── SvgElement.java         (existing)
├── ClassBox.java           (existing)
├── SVGBuilder.java         (existing)
├── Dependency.java         (existing)
├── DependencyType.java     (existing)
└── analyzer/               (new subpackage)
    ├── RelationType.java
    ├── ClassRelation.java
    └── ClassRelationScanner.java
```

---

## Data Model

### `RelationType`

```java
package com.sosuisha.classdiagram.analyzer;

public enum RelationType {
    COMPOSITION,  // field type is NOT a constructor parameter
    AGGREGATION   // field type IS a constructor parameter
}
```

### `ClassRelation`

```java
package com.sosuisha.classdiagram.analyzer;

public record ClassRelation(
    String sourceClass,  // fully qualified name e.g. "com.sosuisha.classdiagram.Order"
    String targetClass,  // fully qualified name e.g. "com.sosuisha.classdiagram.Item"
    RelationType type,
    boolean isMany       // true when field type is a Collection<T>
) {}
```

---

## `ClassRelationScanner` Algorithm

### API

```java
public class ClassRelationScanner {
    public List<ClassRelation> scan(Path classRoot, String packageName) { ... }
}
```

### Steps

1. **Collect target class names**
   - Convert `packageName` to a directory path under `classRoot`
   - List direct `.class` files; exclude inner classes (names containing `$`)
   - Build `Set<String>` of fully qualified class names in the target package

2. **Load classes**
   - Create `URLClassLoader(new URL[]{classRoot.toUri().toURL()}, currentClassLoader)`
   - For each class name: `Class.forName(name, false, loader)`
   - `initialize=false` prevents static initializers from running (avoids JavaFX Application Thread requirement)

3. **Collect field types**
   - Call `getDeclaredFields()` on each loaded class
   - For each field:
     - **Plain type**: `field.getType().getName()` is in `targetClassNames` → candidate (isMany=false)
     - **Collection type**: `field.getGenericType()` is `ParameterizedType`, raw type is assignable from `java.util.Collection`, type argument name is in `targetClassNames` → candidate (isMany=true). Arrays are out of scope.

4. **Determine relation type**
   - Collect all parameter types from `getDeclaredConstructors()`
   - If the target class name appears in constructor parameter types → `AGGREGATION`
   - Otherwise → `COMPOSITION`

5. **Return** `List<ClassRelation>`

### Out of scope

- Inheritance (`extends` / `implements`)
- Static fields
- Inner and anonymous classes

---

## Test Fixture Strategy

Test fixture classes are placed under `src/test/java` in a dedicated package. They are compiled to `target/test-classes/` by Maven's `test-compile` phase, which runs before `test`.

```
src/test/java/com/sosuisha/classdiagram/analyzer/fixture/
    FixtureOrder.java     - has FixtureItem field (no constructor arg) → COMPOSITION
                          - has FixtureCustomer constructor arg       → AGGREGATION
    FixtureItem.java      - plain POJO, no relations
    FixtureCustomer.java  - plain POJO, no relations
```

Tests call:

```java
scanner.scan(Path.of("target/test-classes"), "com.sosuisha.classdiagram.analyzer.fixture")
```

---

## Caller Examples

### In tests (development)

```java
var scanner = new ClassRelationScanner();
var relations = scanner.scan(
    Path.of("target/test-classes"),
    "com.sosuisha.classdiagram.analyzer.fixture"
);
```

### In a Maven plugin Mojo (future)

```java
@Parameter(property = "classRoot", defaultValue = "${project.build.outputDirectory}")
private String classRoot;

@Parameter(property = "packageName", required = true)
private String packageName;

// In execute():
var relations = new ClassRelationScanner().scan(Path.of(classRoot), packageName);
```
