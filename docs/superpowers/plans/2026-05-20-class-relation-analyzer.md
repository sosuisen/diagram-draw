# Class Relation Analyzer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `ClassRelationScanner` to `com.sosuisha.classdiagram.analyzer` that scans compiled `.class` files in a given package and returns `List<ClassRelation>` describing composition and aggregation relationships.

**Architecture:** Three new production classes (`RelationType`, `ClassRelation`, `ClassRelationScanner`) in a new `analyzer` subpackage. Test fixture POJOs in `src/test/java` under `analyzer/fixture` serve as analysis targets. Scanner uses `URLClassLoader` with `Class.forName(..., false, loader)` to avoid static initializer execution, enabling safe analysis of JavaFX classes.

**Tech Stack:** Java 25, Maven, JUnit Jupiter 5.12, `java.lang.reflect` (Field, Constructor, ParameterizedType), `java.net.URLClassLoader`

---

## File Structure

**Create (main):**
- `src/main/java/com/sosuisha/classdiagram/analyzer/RelationType.java` — enum: COMPOSITION, AGGREGATION
- `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelation.java` — record: sourceClass, targetClass, type, isMany
- `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java` — scanner: scans .class files and returns List<ClassRelation>

**Create (test):**
- `src/test/java/com/sosuisha/classdiagram/analyzer/fixture/FixtureItem.java` — plain POJO, no same-package fields
- `src/test/java/com/sosuisha/classdiagram/analyzer/fixture/FixtureCustomer.java` — plain POJO, no same-package fields
- `src/test/java/com/sosuisha/classdiagram/analyzer/fixture/FixtureOrder.java` — has `List<FixtureItem>` (COMPOSITION) and `FixtureCustomer` constructor arg (AGGREGATION)
- `src/test/java/com/sosuisha/classdiagram/analyzer/RelationTypeTest.java`
- `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationTest.java`
- `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java`

---

### Task 1: Create test fixture classes

**Files:**
- Create: `src/test/java/com/sosuisha/classdiagram/analyzer/fixture/FixtureItem.java`
- Create: `src/test/java/com/sosuisha/classdiagram/analyzer/fixture/FixtureCustomer.java`
- Create: `src/test/java/com/sosuisha/classdiagram/analyzer/fixture/FixtureOrder.java`

- [ ] **Step 1: Create `FixtureItem.java`**

```java
package com.sosuisha.classdiagram.analyzer.fixture;

public class FixtureItem {
    private String name;

    public FixtureItem(String name) {
        this.name = name;
    }
}
```

- [ ] **Step 2: Create `FixtureCustomer.java`**

```java
package com.sosuisha.classdiagram.analyzer.fixture;

public class FixtureCustomer {
    private String email;

    public FixtureCustomer(String email) {
        this.email = email;
    }
}
```

- [ ] **Step 3: Create `FixtureOrder.java`**

`items` は `List<FixtureItem>` でコンストラクタ引数にない → COMPOSITION, isMany=true  
`customer` は `FixtureCustomer` でコンストラクタ引数にある → AGGREGATION, isMany=false

```java
package com.sosuisha.classdiagram.analyzer.fixture;

import java.util.ArrayList;
import java.util.List;

public class FixtureOrder {
    private List<FixtureItem> items = new ArrayList<>();
    private FixtureCustomer customer;

    public FixtureOrder(FixtureCustomer customer) {
        this.customer = customer;
    }
}
```

- [ ] **Step 4: Verify fixture classes compile**

```
mvn test-compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/sosuisha/classdiagram/analyzer/fixture/
git commit -m "test: add fixture classes for ClassRelationScanner"
```

---

### Task 2: Create `RelationType` enum

**Files:**
- Create: `src/main/java/com/sosuisha/classdiagram/analyzer/RelationType.java`
- Create: `src/test/java/com/sosuisha/classdiagram/analyzer/RelationTypeTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.sosuisha.classdiagram.analyzer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RelationTypeTest {

    @Test
    void compositionEnumConstantExists() {
        assertNotNull(RelationType.COMPOSITION);
    }

    @Test
    void aggregationEnumConstantExists() {
        assertNotNull(RelationType.AGGREGATION);
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```
mvn test -Dtest=RelationTypeTest -q
```

Expected: FAIL (RelationType does not exist)

- [ ] **Step 3: Create `RelationType.java`**

```java
package com.sosuisha.classdiagram.analyzer;

public enum RelationType {
    COMPOSITION,
    AGGREGATION
}
```

- [ ] **Step 4: Run to confirm pass**

```
mvn test -Dtest=RelationTypeTest -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sosuisha/classdiagram/analyzer/RelationType.java \
        src/test/java/com/sosuisha/classdiagram/analyzer/RelationTypeTest.java
git commit -m "feat: add RelationType enum"
```

---

### Task 3: Create `ClassRelation` record

**Files:**
- Create: `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelation.java`
- Create: `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.sosuisha.classdiagram.analyzer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassRelationTest {

    @Test
    void storesSourceClass() {
        var r = new ClassRelation("com.example.Order", "com.example.Item", RelationType.COMPOSITION, true);
        assertEquals("com.example.Order", r.sourceClass());
    }

    @Test
    void storesTargetClass() {
        var r = new ClassRelation("com.example.Order", "com.example.Item", RelationType.COMPOSITION, true);
        assertEquals("com.example.Item", r.targetClass());
    }

    @Test
    void storesType() {
        var r = new ClassRelation("com.example.Order", "com.example.Item", RelationType.COMPOSITION, true);
        assertEquals(RelationType.COMPOSITION, r.type());
    }

    @Test
    void storesIsMany() {
        var r = new ClassRelation("com.example.Order", "com.example.Item", RelationType.COMPOSITION, true);
        assertTrue(r.isMany());
    }

    @Test
    void isManyFalseWhenSingleReference() {
        var r = new ClassRelation("com.example.Order", "com.example.Customer", RelationType.AGGREGATION, false);
        assertFalse(r.isMany());
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```
mvn test -Dtest=ClassRelationTest -q
```

Expected: FAIL (ClassRelation does not exist)

- [ ] **Step 3: Create `ClassRelation.java`**

```java
package com.sosuisha.classdiagram.analyzer;

public record ClassRelation(
    String sourceClass,
    String targetClass,
    RelationType type,
    boolean isMany
) {}
```

- [ ] **Step 4: Run to confirm pass**

```
mvn test -Dtest=ClassRelationTest -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelation.java \
        src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationTest.java
git commit -m "feat: add ClassRelation record"
```

---

### Task 4: `ClassRelationScanner` scaffold — null checks and empty result

**Files:**
- Create: `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java`
- Create: `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.sosuisha.classdiagram.analyzer;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
```

- [ ] **Step 2: Run to confirm failure**

```
mvn test -Dtest=ClassRelationScannerTest -q
```

Expected: FAIL (ClassRelationScanner does not exist)

- [ ] **Step 3: Create `ClassRelationScanner.java` scaffold**

```java
package com.sosuisha.classdiagram.analyzer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ClassRelationScanner {

    /**
     * 指定パッケージ内のクラスを分析し、コンポジション・集約関係を返す。
     *
     * @param classRoot コンパイル済みクラスのルートディレクトリ
     * @param packageName 分析対象パッケージ名
     * @return 検出された関係のリスト
     * @throws NullPointerException classRootまたはpackageNameがnullの場合
     */
    public List<ClassRelation> scan(Path classRoot, String packageName) {
        Objects.requireNonNull(classRoot, "classRoot must not be null");
        Objects.requireNonNull(packageName, "packageName must not be null");

        var packageDir = classRoot.resolve(packageName.replace('.', '/'));
        if (!Files.isDirectory(packageDir)) {
            return List.of();
        }

        var targetClassNames = collectClassNames(packageDir, packageName);
        if (targetClassNames.isEmpty()) {
            return List.of();
        }

        return List.of();
    }

    private static Set<String> collectClassNames(Path packageDir, String packageName) {
        var names = new HashSet<String>();
        try (var stream = Files.list(packageDir)) {
            stream
                .filter(p -> p.toString().endsWith(".class"))
                .map(p -> p.getFileName().toString())
                .filter(name -> !name.contains("$"))
                .map(name -> packageName + "." + name.replace(".class", ""))
                .forEach(names::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return names;
    }
}
```

- [ ] **Step 4: Run to confirm pass**

```
mvn test -Dtest=ClassRelationScannerTest -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java \
        src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java
git commit -m "feat: add ClassRelationScanner scaffold"
```

---

### Task 5: Plain field relation detection (AGGREGATION and COMPOSITION)

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java`

- [ ] **Step 1: Add failing tests**

Append these two tests to the existing `ClassRelationScannerTest.java` (inside the class, before the closing `}`):

```java
    @Test
    void scanDetectsAggregationForConstructorInjectedField() {
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXTURE_PKG);
        assertTrue(relations.stream().anyMatch(r ->
            r.sourceClass().equals(FIXTURE_PKG + ".FixtureOrder") &&
            r.targetClass().equals(FIXTURE_PKG + ".FixtureCustomer") &&
            r.type() == RelationType.AGGREGATION &&
            !r.isMany()
        ));
    }

    @Test
    void scanReturnsNoRelationsForPojoWithoutSamePackageFields() {
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXTURE_PKG);
        assertTrue(relations.stream().noneMatch(r ->
            r.sourceClass().equals(FIXTURE_PKG + ".FixtureItem")
        ));
    }
```

- [ ] **Step 2: Run to confirm failure**

```
mvn test -Dtest="ClassRelationScannerTest#scanDetectsAggregationForConstructorInjectedField" -q
```

Expected: FAIL (scan returns empty list)

- [ ] **Step 3: Replace `ClassRelationScanner.java` with full implementation**

```java
package com.sosuisha.classdiagram.analyzer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ClassRelationScanner {

    /**
     * 指定パッケージ内のクラスを分析し、コンポジション・集約関係を返す。
     *
     * @param classRoot コンパイル済みクラスのルートディレクトリ
     * @param packageName 分析対象パッケージ名
     * @return 検出された関係のリスト
     * @throws NullPointerException classRootまたはpackageNameがnullの場合
     */
    public List<ClassRelation> scan(Path classRoot, String packageName) {
        Objects.requireNonNull(classRoot, "classRoot must not be null");
        Objects.requireNonNull(packageName, "packageName must not be null");

        var packageDir = classRoot.resolve(packageName.replace('.', '/'));
        if (!Files.isDirectory(packageDir)) {
            return List.of();
        }

        var targetClassNames = collectClassNames(packageDir, packageName);
        if (targetClassNames.isEmpty()) {
            return List.of();
        }

        return analyzeRelations(classRoot, targetClassNames);
    }

    private List<ClassRelation> analyzeRelations(Path classRoot, Set<String> targetClassNames) {
        var relations = new ArrayList<ClassRelation>();

        try (var loader = new URLClassLoader(new java.net.URL[]{toUrl(classRoot)}, getClass().getClassLoader())) {
            for (var className : targetClassNames) {
                var clazz = loadClass(className, loader);
                if (clazz == null) continue;

                var constructorParamTypeNames = collectConstructorParamTypeNames(clazz);

                for (var field : clazz.getDeclaredFields()) {
                    var fieldTypeName = field.getType().getName();
                    if (!targetClassNames.contains(fieldTypeName)) continue;

                    var type = constructorParamTypeNames.contains(fieldTypeName)
                        ? RelationType.AGGREGATION
                        : RelationType.COMPOSITION;
                    relations.add(new ClassRelation(className, fieldTypeName, type, false));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return List.copyOf(relations);
    }

    private static Set<String> collectConstructorParamTypeNames(Class<?> clazz) {
        var names = new HashSet<String>();
        for (var constructor : clazz.getDeclaredConstructors()) {
            for (var paramType : constructor.getParameterTypes()) {
                names.add(paramType.getName());
            }
        }
        return names;
    }

    private static Class<?> loadClass(String className, ClassLoader loader) {
        try {
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static java.net.URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid path: " + path, e);
        }
    }

    private static Set<String> collectClassNames(Path packageDir, String packageName) {
        var names = new HashSet<String>();
        try (var stream = Files.list(packageDir)) {
            stream
                .filter(p -> p.toString().endsWith(".class"))
                .map(p -> p.getFileName().toString())
                .filter(name -> !name.contains("$"))
                .map(name -> packageName + "." + name.replace(".class", ""))
                .forEach(names::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return names;
    }
}
```

- [ ] **Step 4: Run to confirm pass**

```
mvn test -Dtest=ClassRelationScannerTest -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java \
        src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java
git commit -m "feat: detect aggregation and composition from plain fields"
```

---

### Task 6: Collection field detection (isMany=true)

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java`

- [ ] **Step 1: Add failing test**

Append this test to `ClassRelationScannerTest.java` (inside the class, before the closing `}`):

```java
    @Test
    void scanDetectsCompositionWithCollectionField() {
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXTURE_PKG);
        assertTrue(relations.stream().anyMatch(r ->
            r.sourceClass().equals(FIXTURE_PKG + ".FixtureOrder") &&
            r.targetClass().equals(FIXTURE_PKG + ".FixtureItem") &&
            r.type() == RelationType.COMPOSITION &&
            r.isMany()
        ));
    }
```

- [ ] **Step 2: Run to confirm failure**

```
mvn test -Dtest="ClassRelationScannerTest#scanDetectsCompositionWithCollectionField" -q
```

Expected: FAIL (`List<FixtureItem>` not yet detected)

- [ ] **Step 3: Replace `ClassRelationScanner.java` with Collection-aware implementation**

```java
package com.sosuisha.classdiagram.analyzer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ClassRelationScanner {

    /**
     * 指定パッケージ内のクラスを分析し、コンポジション・集約関係を返す。
     *
     * @param classRoot コンパイル済みクラスのルートディレクトリ
     * @param packageName 分析対象パッケージ名
     * @return 検出された関係のリスト
     * @throws NullPointerException classRootまたはpackageNameがnullの場合
     */
    public List<ClassRelation> scan(Path classRoot, String packageName) {
        Objects.requireNonNull(classRoot, "classRoot must not be null");
        Objects.requireNonNull(packageName, "packageName must not be null");

        var packageDir = classRoot.resolve(packageName.replace('.', '/'));
        if (!Files.isDirectory(packageDir)) {
            return List.of();
        }

        var targetClassNames = collectClassNames(packageDir, packageName);
        if (targetClassNames.isEmpty()) {
            return List.of();
        }

        return analyzeRelations(classRoot, targetClassNames);
    }

    private List<ClassRelation> analyzeRelations(Path classRoot, Set<String> targetClassNames) {
        var relations = new ArrayList<ClassRelation>();

        try (var loader = new URLClassLoader(new java.net.URL[]{toUrl(classRoot)}, getClass().getClassLoader())) {
            for (var className : targetClassNames) {
                var clazz = loadClass(className, loader);
                if (clazz == null) continue;

                var constructorParamTypeNames = collectConstructorParamTypeNames(clazz);

                for (var field : clazz.getDeclaredFields()) {
                    var resolved = resolveFieldTarget(field, targetClassNames);
                    if (resolved == null) continue;

                    var type = constructorParamTypeNames.contains(resolved.targetClassName())
                        ? RelationType.AGGREGATION
                        : RelationType.COMPOSITION;
                    relations.add(new ClassRelation(className, resolved.targetClassName(), type, resolved.isMany()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return List.copyOf(relations);
    }

    private record FieldTarget(String targetClassName, boolean isMany) {}

    private static FieldTarget resolveFieldTarget(Field field, Set<String> targetClassNames) {
        var fieldTypeName = field.getType().getName();
        if (targetClassNames.contains(fieldTypeName)) {
            return new FieldTarget(fieldTypeName, false);
        }
        if (Collection.class.isAssignableFrom(field.getType())) {
            var genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType pt) {
                var args = pt.getActualTypeArguments();
                if (args.length == 1 && args[0] instanceof Class<?> argClass) {
                    var argName = argClass.getName();
                    if (targetClassNames.contains(argName)) {
                        return new FieldTarget(argName, true);
                    }
                }
            }
        }
        return null;
    }

    private static Set<String> collectConstructorParamTypeNames(Class<?> clazz) {
        var names = new HashSet<String>();
        for (var constructor : clazz.getDeclaredConstructors()) {
            for (var paramType : constructor.getParameterTypes()) {
                names.add(paramType.getName());
            }
        }
        return names;
    }

    private static Class<?> loadClass(String className, ClassLoader loader) {
        try {
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static java.net.URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid path: " + path, e);
        }
    }

    private static Set<String> collectClassNames(Path packageDir, String packageName) {
        var names = new HashSet<String>();
        try (var stream = Files.list(packageDir)) {
            stream
                .filter(p -> p.toString().endsWith(".class"))
                .map(p -> p.getFileName().toString())
                .filter(name -> !name.contains("$"))
                .map(name -> packageName + "." + name.replace(".class", ""))
                .forEach(names::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return names;
    }
}
```

- [ ] **Step 4: Run all scanner tests to confirm pass**

```
mvn test -Dtest=ClassRelationScannerTest -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Run full test suite**

```
mvn test -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java \
        src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java
git commit -m "feat: detect collection field relations with isMany flag"
```
