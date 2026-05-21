# Equalize Implementation Layers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After longest-path layer reassignment in `ClassDiagramLayout`, add a post-processing step that places implementation classes sharing the same interface at the same (shallowest) layer; classes implementing 2+ interfaces are excluded.

**Architecture:** Add private method `equalizeImplementationLayers()` to `ClassDiagramLayout`, called between `reassignLayers()` and box-map creation in `layout()`. The method builds a `layerOf` map, groups single-interface impls (ifaceCount == 1) by their interface, equalizes each group of 2+ impls to its minimum layer index, then rebuilds and returns the layer list with empty layers removed.

**Tech Stack:** Java 25, JUnit 5, `java.util.stream.Collectors`

---

### Task 1: Implement `equalizeImplementationLayers` with TDD

**Files:**
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java`
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`

- [ ] **Step 1: Write failing tests in `ClassDiagramLayoutTest.java`**

Add these three tests inside `ClassDiagramLayoutTest`, after the existing `layoutPlacesInterfaceAboveImplementationForRealization` test at line 122:

```java
@Test
void equalizesCoImplementationsToSameLayer() {
    // IFoo (interface), FooImplA (implements IFoo + owns LeafA), FooImplB (implements IFoo only)
    // After longest-path reassignment: IFoo=layer0, FooImplA=layer1, FooImplB=layer2, LeafA=layer2
    // After equalization: both FooImplA and FooImplB must be at layer1 (the shallower one)
    var iface = ci("IFoo");
    var implA = ci("FooImplA");
    var implB = ci("FooImplB");
    var leaf = ci("LeafA");
    var rels = List.of(
        new ClassRelation(implA, iface, DependencyType.REALIZATION, false),
        new ClassRelation(implB, iface, DependencyType.REALIZATION, false),
        new ClassRelation(implA, leaf, DependencyType.COMPOSITION, false)
    );
    var layers = new ClassRelationSorter().sort(rels);
    var result = new ClassDiagramLayout(20, 40, 20, 20).layout(layers, rels);

    var boxImplA = result.boxes().stream().filter(b -> b.name().equals("FooImplA")).findFirst().orElseThrow();
    var boxImplB = result.boxes().stream().filter(b -> b.name().equals("FooImplB")).findFirst().orElseThrow();
    assertEquals(boxImplA.y(), boxImplB.y(),
        "Both impls of IFoo must be at the same layer after equalization");
}

@Test
void doesNotEqualizeImplWithMultipleInterfaces() {
    // IFoo, IBar (two interfaces)
    // FooImplA: implements IFoo only (+ owns Leaf → lands at layer1 after longest-path)
    // FooImplB: implements IFoo only (no other relations → lands at layer2)
    // FooBarImpl: implements both IFoo and IBar → excluded from equalization, stays at layer2
    // After equalization: FooImplA and FooImplB equalized to layer1; FooBarImpl stays deeper
    var iface1 = ci("IFoo");
    var iface2 = ci("IBar");
    var implA = ci("FooImplA");
    var implB = ci("FooImplB");
    var multiImpl = ci("FooBarImpl");
    var leaf = ci("Leaf");
    var rels = List.of(
        new ClassRelation(implA, iface1, DependencyType.REALIZATION, false),
        new ClassRelation(implB, iface1, DependencyType.REALIZATION, false),
        new ClassRelation(multiImpl, iface1, DependencyType.REALIZATION, false),
        new ClassRelation(multiImpl, iface2, DependencyType.REALIZATION, false),
        new ClassRelation(implA, leaf, DependencyType.COMPOSITION, false)
    );
    var layers = new ClassRelationSorter().sort(rels);
    var result = new ClassDiagramLayout(20, 40, 20, 20).layout(layers, rels);

    var boxImplA = result.boxes().stream().filter(b -> b.name().equals("FooImplA")).findFirst().orElseThrow();
    var boxImplB = result.boxes().stream().filter(b -> b.name().equals("FooImplB")).findFirst().orElseThrow();
    var boxMulti = result.boxes().stream().filter(b -> b.name().equals("FooBarImpl")).findFirst().orElseThrow();
    assertEquals(boxImplA.y(), boxImplB.y(),
        "FooImplA and FooImplB (single-interface) must be equalized to the same layer");
    assertTrue(boxMulti.y() > boxImplA.y(),
        "FooBarImpl (multi-interface) must not be equalized and must remain deeper");
}

@Test
void removesEmptyLayersAfterEqualization() {
    // IFoo (interface), FooImplA (implements IFoo + owns FooImplB), FooImplB (implements IFoo)
    // After longest-path: IFoo=layer0, FooImplA=layer1, FooImplB=layer2
    // After equalization: FooImplB moves to layer1 → layer2 becomes empty → must be dropped
    // Result: exactly 2 distinct y-values in boxes
    var iface = ci("IFoo");
    var implA = ci("FooImplA");
    var implB = ci("FooImplB");
    var rels = List.of(
        new ClassRelation(implA, iface, DependencyType.REALIZATION, false),
        new ClassRelation(implB, iface, DependencyType.REALIZATION, false),
        new ClassRelation(implA, implB, DependencyType.COMPOSITION, false)
    );
    var layers = new ClassRelationSorter().sort(rels);
    var result = new ClassDiagramLayout(20, 40, 20, 20).layout(layers, rels);

    long distinctYCount = result.boxes().stream().mapToInt(ClassBox::y).distinct().count();
    assertEquals(2, distinctYCount,
        "Empty layer after equalization must be removed, leaving exactly 2 distinct y-values");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=ClassDiagramLayoutTest#equalizesCoImplementationsToSameLayer+doesNotEqualizeImplWithMultipleInterfaces+removesEmptyLayersAfterEqualization -q`

Expected: `TESTS FAILED` — equalization logic does not exist yet, so `FooImplA.y() != FooImplB.y()` etc.

- [ ] **Step 3: Add `import java.util.stream.Collectors;` to `ClassDiagramLayout.java`**

In `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`, the current import block ends with `import java.util.Set;`. Add one line after it:

```java
import java.util.Set;
import java.util.stream.Collectors;
```

- [ ] **Step 4: Add `equalizeImplementationLayers` method to `ClassDiagramLayout.java`**

Insert the following private method after the closing `}` of the `reassignLayers` method (line 190 in the current file), before the final `}` of the class:

```java
/**
 * 同一インタフェースを実装するクラスを同一レイヤーに揃える後処理。
 *
 * <p>2つ以上のインタフェースを実装するクラスは対象外。
 * 対象グループが2クラス未満の場合も変更なし。
 * 移動後に空になったレイヤーは除去する。
 */
private List<List<ClassInfo>> equalizeImplementationLayers(
        List<List<ClassInfo>> layers,
        List<ClassRelation> relations) {

    Map<ClassInfo, Integer> layerOf = new HashMap<>();
    for (int i = 0; i < layers.size(); i++) {
        for (var info : layers.get(i)) {
            layerOf.put(info, i);
        }
    }

    Map<ClassInfo, Long> ifaceCount = relations.stream()
        .filter(r -> r.type() == DependencyType.REALIZATION)
        .collect(Collectors.groupingBy(ClassRelation::sourceClassInfo, Collectors.counting()));

    Map<ClassInfo, List<ClassInfo>> ifaceToImpls = new HashMap<>();
    for (var rel : relations) {
        if (rel.type() != DependencyType.REALIZATION) continue;
        if (ifaceCount.getOrDefault(rel.sourceClassInfo(), 0L) != 1L) continue;
        ifaceToImpls.computeIfAbsent(rel.targetClassInfo(), k -> new ArrayList<>())
                    .add(rel.sourceClassInfo());
    }

    for (var impls : ifaceToImpls.values()) {
        if (impls.size() < 2) continue;
        int minLayer = impls.stream()
            .mapToInt(impl -> layerOf.getOrDefault(impl, 0))
            .min()
            .orElse(0);
        for (var impl : impls) {
            layerOf.put(impl, minLayer);
        }
    }

    int numLayers = layers.size();
    List<List<ClassInfo>> result = new ArrayList<>();
    for (int i = 0; i < numLayers; i++) {
        result.add(new ArrayList<>());
    }
    for (var entry : layerOf.entrySet()) {
        result.get(entry.getValue()).add(entry.getKey());
    }
    for (var layer : result) {
        layer.sort(Comparator.comparing(ClassInfo::simpleName)
                             .thenComparing(ClassInfo::packageName));
    }
    result.removeIf(List::isEmpty);
    return result;
}
```

- [ ] **Step 5: Call `equalizeImplementationLayers` in `layout()`**

In `ClassDiagramLayout.java`, in the `layout()` method, find this comment block (around line 63):

```java
        // Step 2: ClassInfo → ClassBox マップ作成（挿入順保持）
        Map<ClassInfo, ClassBox> boxMap = new LinkedHashMap<>();
```

Insert one line immediately before it:

```java
        // Step 1.5: 同一インタフェースを実装するクラスを同一レイヤーに揃える
        reassigned = equalizeImplementationLayers(reassigned, relations);

        // Step 2: ClassInfo → ClassBox マップ作成（挿入順保持）
        Map<ClassInfo, ClassBox> boxMap = new LinkedHashMap<>();
```

- [ ] **Step 6: Run all tests**

Run: `mvn test -q`

Expected: `BUILD SUCCESS` — all tests pass including the 3 new ones

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java
git commit -m "feat: equalize co-implementation layers after longest-path reassignment"
```
