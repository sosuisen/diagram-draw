# UML Dependency Cross-Group Arrow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Detect UML Dependency relationships (local variable types and method parameter types in the scanned package) and draw dashed + open-arrowhead arrows only between classes in different connected-component groups.

**Architecture:** `ClassRelationScanner` populates a `Set<String> dependencyTargetFqns` on each `ClassInfo` during scan; `ClassDiagramLayout` resolves those FQNs against its boxMap and creates `Dependency(src, tgt, DEPENDENCY)` only when source and target have different `groupIndex`. `ConnectedComponentSplitter` is unchanged — it ignores dependency FQNs so they never affect group assignment, which guarantees cross-group arrows are always drawn.

**Tech Stack:** Java 25, Class File API (`java.lang.classfile`), JUnit 5, Maven

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `src/main/java/com/sosuisha/classdiagram/analyzer/ClassInfo.java` | Modify | Add `dependencyTargetFqns` field, getter, adder |
| `src/main/java/com/sosuisha/classdiagram/DependencyType.java` | Modify | Add `DEPENDENCY` enum value |
| `src/main/java/com/sosuisha/classdiagram/Dependency.java` | Modify | Add `drawDependency()` for dashed line + V-arrowhead |
| `src/test/java/com/sosuisha/classdiagram/analyzer/fixdep/FixtureDepSourcePart.java` | Create | Test fixture: part of source group |
| `src/test/java/com/sosuisha/classdiagram/analyzer/fixdep/FixtureDepSource.java` | Create | Test fixture: uses FixtureDepTarget as local var |
| `src/test/java/com/sosuisha/classdiagram/analyzer/fixdep/FixtureDepTargetPart.java` | Create | Test fixture: part of target group |
| `src/test/java/com/sosuisha/classdiagram/analyzer/fixdep/FixtureDepTarget.java` | Create | Test fixture: isolated dependency target |
| `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java` | Modify | Scan method params + local vars, populate dependencyTargetFqns |
| `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java` | Modify | Resolve FQNs, create cross-group DEPENDENCY arrows |
| `src/test/java/com/sosuisha/classdiagram/analyzer/ClassInfoTest.java` | Modify | Tests for dependencyTargetFqns |
| `src/test/java/com/sosuisha/classdiagram/DependencyTest.java` | Modify | Tests for DEPENDENCY SVG rendering |
| `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java` | Modify | Tests for FQN collection |
| `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java` | Modify | Tests for cross-group DEPENDENCY arrows |
| `src/test/java/com/sosuisha/classdiagram/ClassDiagramGeneratorTest.java` | Modify | Integration test |

---

### Task 1: ClassInfo — add dependencyTargetFqns

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/analyzer/ClassInfo.java`
- Test: `src/test/java/com/sosuisha/classdiagram/analyzer/ClassInfoTest.java`

- [ ] **Step 1: Write failing tests**

Add to `ClassInfoTest.java` (append after the last test):

```java
@Test
void dependencyTargetFqnsDefaultsToEmpty() {
    var info = new ClassInfo("com.example", "Foo");
    assertTrue(info.dependencyTargetFqns().isEmpty());
}

@Test
void addDependencyTargetFqnAddsToSet() {
    var info = new ClassInfo("com.example", "Foo");
    info.addDependencyTargetFqn("com.example.Bar");
    assertTrue(info.dependencyTargetFqns().contains("com.example.Bar"));
}

@Test
void addDependencyTargetFqnThrowsForNull() {
    var info = new ClassInfo("com.example", "Foo");
    assertThrows(NullPointerException.class, () -> info.addDependencyTargetFqn(null));
}

@Test
void dependencyTargetFqnsIsUnmodifiable() {
    var info = new ClassInfo("com.example", "Foo");
    assertThrows(UnsupportedOperationException.class,
        () -> info.dependencyTargetFqns().add("com.example.Bar"));
}

@Test
void equalsIgnoresDependencyTargetFqns() {
    var a = new ClassInfo("com.example", "Foo");
    var b = new ClassInfo("com.example", "Foo");
    b.addDependencyTargetFqn("com.example.Bar");
    assertEquals(a, b);
}

@Test
void hashCodeIgnoresDependencyTargetFqns() {
    var a = new ClassInfo("com.example", "Foo");
    var b = new ClassInfo("com.example", "Foo");
    b.addDependencyTargetFqn("com.example.Bar");
    assertEquals(a.hashCode(), b.hashCode());
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
mvn test -pl . -Dtest=ClassInfoTest -q
```

Expected: FAIL — `dependencyTargetFqns()` and `addDependencyTargetFqn()` do not exist.

- [ ] **Step 3: Implement in ClassInfo.java**

Add imports at the top of `ClassInfo.java` (after `import java.util.Objects;`):

```java
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
```

Add field after `private int groupIndex;`:

```java
private final Set<String> dependencyTargetFqns = new LinkedHashSet<>();
```

Add methods after `setGroupIndex()`:

```java
/**
 * dependency依存先のFQNを追加する。{@link com.sosuisha.classdiagram.analyzer.ClassRelationScanner} が呼び出す。
 *
 * @param fqn 完全修飾名
 * @throws NullPointerException fqnがnullの場合
 */
public void addDependencyTargetFqn(String fqn) {
    Objects.requireNonNull(fqn, "fqn must not be null");
    dependencyTargetFqns.add(fqn);
}

/** @return dependency依存先のFQNセット（変更不可） */
public Set<String> dependencyTargetFqns() {
    return Collections.unmodifiableSet(dependencyTargetFqns);
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
mvn test -pl . -Dtest=ClassInfoTest -q
```

Expected: All tests PASS.

- [ ] **Step 5: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/analyzer/ClassInfo.java
git add src/test/java/com/sosuisha/classdiagram/analyzer/ClassInfoTest.java
git commit -m "feat: add dependencyTargetFqns to ClassInfo"
```

---

### Task 2: DependencyType and Dependency — DEPENDENCY rendering

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/DependencyType.java`
- Modify: `src/main/java/com/sosuisha/classdiagram/Dependency.java`
- Test: `src/test/java/com/sosuisha/classdiagram/DependencyTest.java`

- [ ] **Step 1: Write failing tests**

Add to `DependencyTest.java` (append after the last test):

```java
private static Dependency dependencyDep() {
    var source = new ClassBox("OrderService");
    source.setPosition(50, 200);
    var target = new ClassBox("InventoryRepo");
    target.setPosition(50, 0);
    return new Dependency(source, target, DependencyType.DEPENDENCY);
}

@Test
void drawDependencyHasDataAttribute() {
    assertTrue(dependencyDep().draw().contains("data-diagram-draw-type=\"dependency\""));
}

@Test
void drawDependencyHasDashedLine() {
    assertTrue(dependencyDep().draw().contains("stroke-dasharray"));
}

@Test
void drawDependencyHasOpenArrowhead() {
    var svg = dependencyDep().draw();
    assertTrue(svg.contains("<polyline"));
    assertTrue(svg.contains("fill=\"none\""));
}

@Test
void drawDependencyHasNoFilledDiamond() {
    assertFalse(dependencyDep().draw().contains("fill=\"black\""));
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
mvn test -pl . -Dtest=DependencyTest -q
```

Expected: FAIL — `DependencyType.DEPENDENCY` does not exist (compile error).

- [ ] **Step 3: Add DEPENDENCY to DependencyType.java**

Replace the enum body:

```java
public enum DependencyType {
    /** コンポジション（強い所有関係）*/
    COMPOSITION,
    /** 集約（弱い所有関係）*/
    AGGREGATION,
    /** 実現（インタフェースと実装クラスの関係）*/
    REALIZATION,
    /** 依存（ローカル変数・メソッドパラメータ経由の使用関係）*/
    DEPENDENCY
}
```

- [ ] **Step 4: Add drawDependency() to Dependency.java**

Add these constants after `TRIANGLE_HALF_WIDTH`:

```java
private static final int ARROWHEAD_LEN = 10;
private static final double ARROWHEAD_HALF_ANGLE = Math.PI / 6.0; // 30 degrees
```

In `draw()`, add the DEPENDENCY branch **before** the diamond block (insert after the REALIZATION check):

```java
if (type == DependencyType.DEPENDENCY) {
    return drawDependency(sp, tp, nx, ny);
}
```

Add `drawDependency()` method after `drawRealization()`:

```java
private String drawDependency(double[] sp, double[] tp, double nx, double ny) {
    double angle = Math.atan2(ny, nx);
    double ax1 = tp[0] - ARROWHEAD_LEN * Math.cos(angle - ARROWHEAD_HALF_ANGLE);
    double ay1 = tp[1] - ARROWHEAD_LEN * Math.sin(angle - ARROWHEAD_HALF_ANGLE);
    double ax2 = tp[0] - ARROWHEAD_LEN * Math.cos(angle + ARROWHEAD_HALF_ANGLE);
    double ay2 = tp[1] - ARROWHEAD_LEN * Math.sin(angle + ARROWHEAD_HALF_ANGLE);

    var sb = new StringBuilder();
    sb.append("<g data-diagram-draw=\"dependency\" data-diagram-draw-type=\"%s\">".formatted(type.name().toLowerCase()));
    sb.append("<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"black\" stroke-dasharray=\"8,4\"/>".formatted(
        sp[0], sp[1], tp[0], tp[1]));
    sb.append("<polyline points=\"%.1f,%.1f %.1f,%.1f %.1f,%.1f\" fill=\"none\" stroke=\"black\"/>".formatted(
        ax1, ay1, tp[0], tp[1], ax2, ay2));
    sb.append("</g>");
    return sb.toString();
}
```

- [ ] **Step 5: Run tests to verify they pass**

```
mvn test -pl . -Dtest=DependencyTest -q
```

Expected: All tests PASS.

- [ ] **Step 6: Run full test suite**

```
mvn test -q
```

Expected: All tests PASS (no regressions).

- [ ] **Step 7: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/DependencyType.java
git add src/main/java/com/sosuisha/classdiagram/Dependency.java
git add src/test/java/com/sosuisha/classdiagram/DependencyTest.java
git commit -m "feat: add DEPENDENCY type and dashed open-arrowhead SVG rendering"
```

---

### Task 3: Create fixture classes for dependency scan tests

**Files:**
- Create: `src/test/java/com/sosuisha/classdiagram/analyzer/fixdep/FixtureDepSourcePart.java`
- Create: `src/test/java/com/sosuisha/classdiagram/analyzer/fixdep/FixtureDepSource.java`
- Create: `src/test/java/com/sosuisha/classdiagram/analyzer/fixdep/FixtureDepTargetPart.java`
- Create: `src/test/java/com/sosuisha/classdiagram/analyzer/fixdep/FixtureDepTarget.java`

These fixtures form two groups when scanned:
- Group A: `FixtureDepSource` ─AGGREGATION→ `FixtureDepSourcePart`
- Group B: `FixtureDepTarget` ─COMPOSITION→ `FixtureDepTargetPart`

`FixtureDepSource` uses `FixtureDepTarget` as a local variable (DEPENDENCY) and `FixtureDepTargetPart` as a method parameter (DEPENDENCY).

- [ ] **Step 1: Create FixtureDepSourcePart.java**

```java
package com.sosuisha.classdiagram.analyzer.fixdep;

/** Fixture: plain part class in the source group. */
public class FixtureDepSourcePart {
    private String value;
    public FixtureDepSourcePart(String value) { this.value = value; }
    public String value() { return value; }
}
```

- [ ] **Step 2: Create FixtureDepTargetPart.java**

```java
package com.sosuisha.classdiagram.analyzer.fixdep;

/** Fixture: plain part class in the target group. */
public class FixtureDepTargetPart {
    private String label;
    public FixtureDepTargetPart(String label) { this.label = label; }
    public String label() { return label; }
}
```

- [ ] **Step 3: Create FixtureDepTarget.java**

```java
package com.sosuisha.classdiagram.analyzer.fixdep;

/** Fixture: dependency target. Has COMPOSITION to FixtureDepTargetPart. */
public class FixtureDepTarget {
    private FixtureDepTargetPart part;

    public FixtureDepTarget() {
        this.part = new FixtureDepTargetPart("default");
    }

    public String id() { return part.label(); }
}
```

- [ ] **Step 4: Create FixtureDepSource.java**

```java
package com.sosuisha.classdiagram.analyzer.fixdep;

/** Fixture: dependency source.
 *  AGGREGATION to FixtureDepSourcePart (stored constructor param → not a dependency).
 *  process() has local var FixtureDepTarget → DEPENDENCY.
 *  check() has param FixtureDepTargetPart → DEPENDENCY.
 */
public class FixtureDepSource {
    private FixtureDepSourcePart part;

    public FixtureDepSource(FixtureDepSourcePart part) {
        this.part = part;
    }

    public String process() {
        FixtureDepTarget target = new FixtureDepTarget();
        return target.id();
    }

    public String check(FixtureDepTargetPart query) {
        return query.label().equals(part.value()) ? "match" : "no";
    }
}
```

- [ ] **Step 5: Compile to verify fixtures build**

```
mvn test-compile -q
```

Expected: BUILD SUCCESS (no compile errors).

- [ ] **Step 6: Commit**

```
git add src/test/java/com/sosuisha/classdiagram/analyzer/fixdep/
git commit -m "test: add fixdep fixture classes for dependency scan tests"
```

---

### Task 4: ClassRelationScanner — collect dependency FQNs

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java`
- Test: `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java`

- [ ] **Step 1: Write failing tests**

Add to `ClassRelationScannerTest.java` (append after the last test). Note: `ClassInfo` is accessed via `var` inference (no new import needed — pattern already used in existing tests):

```java
private static final String FIXDEP_PKG = "com.sosuisha.classdiagram.analyzer.fixdep";

@Test
void scanDetectsDependencyFqnForLocalVariable() {
    var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXDEP_PKG);
    var src = relations.stream()
        .filter(r -> r.sourceClassInfo().simpleName().equals("FixtureDepSource"))
        .map(ClassRelation::sourceClassInfo)
        .findFirst()
        .orElseThrow();
    assertTrue(src.dependencyTargetFqns().contains(FIXDEP_PKG + ".FixtureDepTarget"),
        "FixtureDepTarget must be in dependencyTargetFqns (local var in process())");
}

@Test
void scanDetectsDependencyFqnForMethodParam() {
    var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXDEP_PKG);
    var src = relations.stream()
        .filter(r -> r.sourceClassInfo().simpleName().equals("FixtureDepSource"))
        .map(ClassRelation::sourceClassInfo)
        .findFirst()
        .orElseThrow();
    assertTrue(src.dependencyTargetFqns().contains(FIXDEP_PKG + ".FixtureDepTargetPart"),
        "FixtureDepTargetPart must be in dependencyTargetFqns (param in check())");
}

@Test
void scanExcludesFieldTypeFromDependencyFqns() {
    var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXDEP_PKG);
    var src = relations.stream()
        .filter(r -> r.sourceClassInfo().simpleName().equals("FixtureDepSource"))
        .map(ClassRelation::sourceClassInfo)
        .findFirst()
        .orElseThrow();
    assertFalse(src.dependencyTargetFqns().contains(FIXDEP_PKG + ".FixtureDepSourcePart"),
        "FixtureDepSourcePart is a stored field — must NOT be in dependencyTargetFqns");
}

@Test
void scanDoesNotIncludeSelfInDependencyFqns() {
    var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXDEP_PKG);
    var src = relations.stream()
        .filter(r -> r.sourceClassInfo().simpleName().equals("FixtureDepSource"))
        .map(ClassRelation::sourceClassInfo)
        .findFirst()
        .orElseThrow();
    assertFalse(src.dependencyTargetFqns().contains(FIXDEP_PKG + ".FixtureDepSource"),
        "A class must not depend on itself");
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
mvn test -pl . -Dtest=ClassRelationScannerTest -q
```

Expected: FAIL — `dependencyTargetFqns()` returns empty set.

- [ ] **Step 3: Add imports to ClassRelationScanner.java**

Add after existing imports:

```java
import java.util.regex.Pattern;
```

- [ ] **Step 4: Add helper methods to ClassRelationScanner.java**

Add these three private static methods after `internalNameToBinary()`:

```java
private static final Pattern CLASS_TYPE_PATTERN = Pattern.compile("L([^<;>]+)");

private static Set<String> extractFqnsFromSignature(String sig) {
    var result = new HashSet<String>();
    var matcher = CLASS_TYPE_PATTERN.matcher(sig);
    while (matcher.find()) {
        result.add(matcher.group(1).replace('/', '.'));
    }
    return result;
}

private static String extractParamSection(String methodSig) {
    int start = methodSig.indexOf('(');
    if (start < 0) return "";
    int depth = 0;
    for (int i = start + 1; i < methodSig.length(); i++) {
        char c = methodSig.charAt(i);
        if (c == '<') depth++;
        else if (c == '>') depth--;
        else if (c == ')' && depth == 0) {
            return methodSig.substring(start + 1, i);
        }
    }
    return "";
}

private static void addIfDependency(String fqn, Set<String> targetClassNames,
                                     Set<String> fieldFqns, String selfFqn,
                                     Set<String> result) {
    if (targetClassNames.contains(fqn) && !fieldFqns.contains(fqn) && !fqn.equals(selfFqn)) {
        result.add(fqn);
    }
}

private static Set<String> collectDependencyFqns(ClassModel model,
                                                   Set<String> targetClassNames,
                                                   Set<String> fieldFqns,
                                                   String selfFqn) {
    var result = new HashSet<String>();
    for (var method : model.methods()) {
        // 1. Non-generic parameter types from method descriptor
        var desc = method.methodTypeSymbol();
        for (int i = 0; i < desc.parameterCount(); i++) {
            addIfDependency(classDescToBinaryName(desc.parameterType(i)),
                targetClassNames, fieldFqns, selfFqn, result);
        }
        // 2. Generic parameter types from method Signature attribute
        var sigAttr = method.findAttribute(Attributes.signature());
        if (sigAttr.isPresent()) {
            var paramSection = extractParamSection(sigAttr.get().signature().stringValue());
            for (var fqn : extractFqnsFromSignature(paramSection)) {
                addIfDependency(fqn, targetClassNames, fieldFqns, selfFqn, result);
            }
        }
        // 3. Non-generic and generic local variable types from Code attribute
        var codeAttr = method.findAttribute(Attributes.code());
        if (codeAttr.isPresent()) {
            var code = codeAttr.get();
            var lvt = code.findAttribute(Attributes.localVariableTable());
            if (lvt.isPresent()) {
                for (var lv : lvt.get().localVariables()) {
                    if (!lv.typeSymbol().isPrimitive() && !lv.typeSymbol().isArray()) {
                        addIfDependency(classDescToBinaryName(lv.typeSymbol()),
                            targetClassNames, fieldFqns, selfFqn, result);
                    }
                }
            }
            var lvtt = code.findAttribute(Attributes.localVariableTypeTable());
            if (lvtt.isPresent()) {
                for (var lv : lvtt.get().localVariableTypes()) {
                    for (var fqn : extractFqnsFromSignature(lv.signatureSymbol().stringValue())) {
                        addIfDependency(fqn, targetClassNames, fieldFqns, selfFqn, result);
                    }
                }
            }
        }
    }
    return result;
}
```

- [ ] **Step 5: Modify analyzeRelations() to collect fieldFqns and call collectDependencyFqns()**

In `analyzeRelations()`, find the existing field loop:

```java
for (var field : model.fields()) {
    var resolved = resolveFieldTarget(field, targetClassNames);
    if (resolved == null) continue;

    var targetInfo = ClassInfo.fromFullyQualifiedName(resolved.targetClassName(),
        stereotypes.getOrDefault(resolved.targetClassName(), ClassStereotype.NONE));
    var type = constructorParamTypeNames.contains(resolved.targetClassName())
        ? DependencyType.AGGREGATION
        : DependencyType.COMPOSITION;
    relations.add(new ClassRelation(sourceInfo, targetInfo, type, resolved.isMany()));
}
```

Replace it with:

```java
var fieldFqns = new HashSet<String>();
for (var field : model.fields()) {
    var resolved = resolveFieldTarget(field, targetClassNames);
    if (resolved == null) continue;
    fieldFqns.add(resolved.targetClassName());

    var targetInfo = ClassInfo.fromFullyQualifiedName(resolved.targetClassName(),
        stereotypes.getOrDefault(resolved.targetClassName(), ClassStereotype.NONE));
    var type = constructorParamTypeNames.contains(resolved.targetClassName())
        ? DependencyType.AGGREGATION
        : DependencyType.COMPOSITION;
    relations.add(new ClassRelation(sourceInfo, targetInfo, type, resolved.isMany()));
}

for (var fqn : collectDependencyFqns(model, targetClassNames, fieldFqns, className)) {
    sourceInfo.addDependencyTargetFqn(fqn);
}
```

- [ ] **Step 6: Run tests to verify they pass**

```
mvn test -pl . -Dtest=ClassRelationScannerTest -q
```

Expected: All tests PASS.

- [ ] **Step 7: Run full test suite**

```
mvn test -q
```

Expected: All tests PASS.

- [ ] **Step 8: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java
git add src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java
git commit -m "feat: ClassRelationScanner collects dependency target FQNs from method params and local vars"
```

---

### Task 5: ClassDiagramLayout — cross-group DEPENDENCY arrows

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`
- Test: `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java`

- [ ] **Step 1: Write failing tests**

Add to `ClassDiagramLayoutTest.java` (append after the last test):

```java
@Test
void layoutCreatesDependencyArrowForCrossGroupFqn() {
    var a = ci("A"); var b = ci("B");
    var x = ci("X"); var y = ci("Y");
    x.setGroupIndex(1); y.setGroupIndex(1);
    // A depends on X (cross-group: group 0 → group 1)
    a.addDependencyTargetFqn(PKG + ".X");

    var rels = List.of(rel(a, b), rel(x, y));
    var layers = new ClassRelationSorter().sort(rels);
    var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);

    assertTrue(result.dependencies().stream().anyMatch(d ->
        d.source().name().equals("A")
        && d.target().name().equals("X")
        && d.type() == DependencyType.DEPENDENCY),
        "Cross-group FQN must produce a DEPENDENCY arrow from A to X");
}

@Test
void layoutDoesNotCreateDependencyArrowForSameGroupFqn() {
    var a = ci("A"); var b = ci("B");
    // A depends on B (same group 0)
    a.addDependencyTargetFqn(PKG + ".B");

    var rels = List.of(rel(a, b));
    var layers = new ClassRelationSorter().sort(rels);
    var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);

    assertTrue(result.dependencies().stream().noneMatch(d -> d.type() == DependencyType.DEPENDENCY),
        "Same-group FQN must not produce a DEPENDENCY arrow");
}

@Test
void layoutIgnoresUnresolvableDependencyFqn() {
    var a = ci("A"); var b = ci("B");
    a.addDependencyTargetFqn("com.example.NonExistent");

    var rels = List.of(rel(a, b));
    var layers = new ClassRelationSorter().sort(rels);
    // must not throw
    var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);

    assertTrue(result.dependencies().stream().noneMatch(d -> d.type() == DependencyType.DEPENDENCY),
        "Unresolvable FQN must be silently ignored");
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
mvn test -pl . -Dtest=ClassDiagramLayoutTest -q
```

Expected: FAIL — layout does not yet create DEPENDENCY arrows.

- [ ] **Step 3: Add import to ClassDiagramLayout.java**

Add after existing imports:

```java
import java.util.HashMap;
```

- [ ] **Step 4: Add cross-group DEPENDENCY arrow generation to layout()**

In `ClassDiagramLayout.java`, find Step 7 (Dependency generation):

```java
// Step 7: Dependency 生成
var dependencies = new ArrayList<Dependency>();
for (var rel : relations) {
    var src = boxMap.get(rel.sourceClassInfo());
    var tgt = boxMap.get(rel.targetClassInfo());
    if (src != null && tgt != null) {
        dependencies.add(new Dependency(src, tgt, rel.type()));
    }
}
```

Replace it with:

```java
// Step 7: Dependency 生成
var dependencies = new ArrayList<Dependency>();
for (var rel : relations) {
    var src = boxMap.get(rel.sourceClassInfo());
    var tgt = boxMap.get(rel.targetClassInfo());
    if (src != null && tgt != null) {
        dependencies.add(new Dependency(src, tgt, rel.type()));
    }
}

// Step 8: cross-group DEPENDENCY 矢印生成
Map<String, ClassInfo> fqnToInfo = new HashMap<>();
for (var info : boxMap.keySet()) {
    fqnToInfo.put(info.packageName() + "." + info.simpleName(), info);
}
for (var srcInfo : boxMap.keySet()) {
    for (var fqn : srcInfo.dependencyTargetFqns()) {
        var tgtInfo = fqnToInfo.get(fqn);
        if (tgtInfo == null) continue;
        if (srcInfo.groupIndex() == tgtInfo.groupIndex()) continue;
        var src = boxMap.get(srcInfo);
        var tgt = boxMap.get(tgtInfo);
        if (src != null && tgt != null) {
            dependencies.add(new Dependency(src, tgt, DependencyType.DEPENDENCY));
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```
mvn test -pl . -Dtest=ClassDiagramLayoutTest -q
```

Expected: All tests PASS.

- [ ] **Step 6: Run full test suite**

```
mvn test -q
```

Expected: All tests PASS.

- [ ] **Step 7: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java
git commit -m "feat: ClassDiagramLayout draws cross-group DEPENDENCY arrows from dependencyTargetFqns"
```

---

### Task 6: Integration test

**Files:**
- Test: `src/test/java/com/sosuisha/classdiagram/ClassDiagramGeneratorTest.java`

- [ ] **Step 1: Write failing integration test**

Add to `ClassDiagramGeneratorTest.java` (append after the last test):

```java
@Test
void generateIncludesCrossGroupDependencyArrowForFixdepPackage() {
    var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
        .generate(Path.of("target/test-classes"),
                  "com.sosuisha.classdiagram.analyzer.fixdep");
    assertTrue(svg.contains("data-diagram-draw-type=\"dependency\""),
        "SVG must contain a cross-group DEPENDENCY arrow for fixdep package");
    assertTrue(svg.contains("stroke-dasharray"),
        "DEPENDENCY arrow must use a dashed line");
}
```

- [ ] **Step 2: Run full test suite**

```
mvn test -q
```

Expected: All tests PASS — the complete pipeline (scanner populates FQNs → layout draws cross-group arrows → SVGBuilder renders) produces the expected SVG. If this test fails, check that Tasks 1-5 are fully committed and the fixdep fixture classes compiled correctly.

- [ ] **Step 4: Commit**

```
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramGeneratorTest.java
git commit -m "test: integration test for cross-group DEPENDENCY arrow in SVG output"
```
