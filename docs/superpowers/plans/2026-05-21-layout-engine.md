# Layout Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a layout/positioning engine and a facade that take ClassRelationScanner + ClassRelationSorter output and produce a ready-to-render SVG string, while removing the now-redundant `RelationType` enum.

**Architecture:** `ClassRelation` is refactored to hold `DependencyType` (dropping `RelationType`). `ClassDiagramLayout` receives Kahn-sorted layers and reassigns them with the longest-path algorithm (leaf nodes sink to the bottom), then positions `ClassBox` objects and creates `Dependency` objects. `ClassDiagramGenerator` is the public facade that chains Scanner → Sorter → Layout → SVGBuilder.

**Tech Stack:** Java 25, Maven, JUnit Jupiter 5.12 (`mvn test`)

---

## File Map

| Action | File |
|--------|------|
| Modify | `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelation.java` |
| Modify | `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java` |
| Delete | `src/main/java/com/sosuisha/classdiagram/analyzer/RelationType.java` |
| Modify | `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationTest.java` |
| Modify | `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java` |
| Modify | `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationSorterTest.java` |
| Delete | `src/test/java/com/sosuisha/classdiagram/analyzer/RelationTypeTest.java` |
| Create | `src/main/java/com/sosuisha/classdiagram/LayoutResult.java` |
| Create | `src/test/java/com/sosuisha/classdiagram/LayoutResultTest.java` |
| Create | `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java` |
| Create | `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java` |
| Create | `src/main/java/com/sosuisha/classdiagram/ClassDiagramGenerator.java` |
| Create | `src/test/java/com/sosuisha/classdiagram/ClassDiagramGeneratorTest.java` |

---

## Task 1: Replace `RelationType` with `DependencyType`

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelation.java`
- Modify: `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationTest.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationSorterTest.java`
- Delete: `src/main/java/com/sosuisha/classdiagram/analyzer/RelationType.java`
- Delete: `src/test/java/com/sosuisha/classdiagram/analyzer/RelationTypeTest.java`

- [ ] **Step 1: Update `ClassRelation.java`**

Replace the file content entirely:

```java
package com.sosuisha.classdiagram.analyzer;

import com.sosuisha.classdiagram.DependencyType;
import java.util.Objects;

/**
 * 2クラス間の関係を表す。
 *
 * @param sourceClassInfo フィールドを持つクラス（所有側）
 * @param targetClassInfo フィールドの型クラス（所有される側）
 * @param type            COMPOSITION または AGGREGATION
 * @param isMany          コレクションフィールドの場合true
 * @throws NullPointerException sourceClassInfo、targetClassInfo、またはtypeがnullの場合
 */
public record ClassRelation(
    ClassInfo sourceClassInfo,
    ClassInfo targetClassInfo,
    DependencyType type,
    boolean isMany
) {
    public ClassRelation {
        Objects.requireNonNull(sourceClassInfo, "sourceClassInfo must not be null");
        Objects.requireNonNull(targetClassInfo, "targetClassInfo must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }
}
```

- [ ] **Step 2: Update `ClassRelationScanner.java`**

Add to imports:
```java
import com.sosuisha.classdiagram.DependencyType;
```

Replace:
```java
var type = constructorParamTypeNames.contains(resolved.targetClassName())
    ? RelationType.AGGREGATION
    : RelationType.COMPOSITION;
```
With:
```java
var type = constructorParamTypeNames.contains(resolved.targetClassName())
    ? DependencyType.AGGREGATION
    : DependencyType.COMPOSITION;
```

- [ ] **Step 3: Update `ClassRelationTest.java`**

Replace the file content entirely:

```java
package com.sosuisha.classdiagram.analyzer;

import com.sosuisha.classdiagram.DependencyType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassRelationTest {

    @Test
    void storesSourceClass() {
        var r = new ClassRelation(
            ClassInfo.fromFullyQualifiedName("com.example.Order"),
            ClassInfo.fromFullyQualifiedName("com.example.Item"),
            DependencyType.COMPOSITION,
            true
        );
        assertEquals("com.example", r.sourceClassInfo().packageName());
        assertEquals("Order", r.sourceClassInfo().simpleName());
    }

    @Test
    void storesTargetClass() {
        var r = new ClassRelation(
            ClassInfo.fromFullyQualifiedName("com.example.Order"),
            ClassInfo.fromFullyQualifiedName("com.example.Item"),
            DependencyType.COMPOSITION,
            true
        );
        assertEquals("com.example", r.targetClassInfo().packageName());
        assertEquals("Item", r.targetClassInfo().simpleName());
    }

    @Test
    void storesType() {
        var r = new ClassRelation(
            ClassInfo.fromFullyQualifiedName("com.example.Order"),
            ClassInfo.fromFullyQualifiedName("com.example.Item"),
            DependencyType.COMPOSITION,
            true
        );
        assertEquals(DependencyType.COMPOSITION, r.type());
    }

    @Test
    void storesIsMany() {
        var r = new ClassRelation(
            ClassInfo.fromFullyQualifiedName("com.example.Order"),
            ClassInfo.fromFullyQualifiedName("com.example.Item"),
            DependencyType.COMPOSITION,
            true
        );
        assertTrue(r.isMany());
    }

    @Test
    void isManyFalseWhenSingleReference() {
        var r = new ClassRelation(
            ClassInfo.fromFullyQualifiedName("com.example.Order"),
            ClassInfo.fromFullyQualifiedName("com.example.Customer"),
            DependencyType.AGGREGATION,
            false
        );
        assertFalse(r.isMany());
    }

    @Test
    void constructorThrowsForNullSourceClassInfo() {
        assertThrows(NullPointerException.class, () ->
            new ClassRelation(null, ClassInfo.fromFullyQualifiedName("com.example.B"),
                DependencyType.COMPOSITION, false));
    }

    @Test
    void constructorThrowsForNullTargetClassInfo() {
        assertThrows(NullPointerException.class, () ->
            new ClassRelation(ClassInfo.fromFullyQualifiedName("com.example.A"), null,
                DependencyType.COMPOSITION, false));
    }

    @Test
    void constructorThrowsForNullType() {
        assertThrows(NullPointerException.class, () ->
            new ClassRelation(
                ClassInfo.fromFullyQualifiedName("com.example.A"),
                ClassInfo.fromFullyQualifiedName("com.example.B"),
                null, false));
    }
}
```

- [ ] **Step 4: Update `ClassRelationScannerTest.java`**

Replace the file content entirely:

```java
package com.sosuisha.classdiagram.analyzer;

import com.sosuisha.classdiagram.DependencyType;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void scanDetectsAggregationForConstructorInjectedField() {
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXTURE_PKG);
        assertTrue(relations.stream().anyMatch(r ->
            r.sourceClassInfo().packageName().equals(FIXTURE_PKG) &&
            r.sourceClassInfo().simpleName().equals("FixtureOrder") &&
            r.targetClassInfo().packageName().equals(FIXTURE_PKG) &&
            r.targetClassInfo().simpleName().equals("FixtureCustomer") &&
            r.type() == DependencyType.AGGREGATION &&
            !r.isMany()
        ));
    }

    @Test
    void scanReturnsNoRelationsForPojoWithoutSamePackageFields() {
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXTURE_PKG);
        assertTrue(relations.stream().noneMatch(r ->
            r.sourceClassInfo().packageName().equals(FIXTURE_PKG) &&
            r.sourceClassInfo().simpleName().equals("FixtureItem")
        ));
    }

    @Test
    void scanDetectsCompositionWithCollectionField() {
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXTURE_PKG);
        assertTrue(relations.stream().anyMatch(r ->
            r.sourceClassInfo().simpleName().equals("FixtureOrder") &&
            r.targetClassInfo().simpleName().equals("FixtureItem") &&
            r.type() == DependencyType.COMPOSITION &&
            r.isMany()
        ));
    }
}
```

- [ ] **Step 5: Update `ClassRelationSorterTest.java`**

Add to imports:
```java
import com.sosuisha.classdiagram.DependencyType;
```

Replace:
```java
private static ClassRelation rel(String src, String tgt) {
    return new ClassRelation(ci(src), ci(tgt), RelationType.COMPOSITION, false);
}
```
With:
```java
private static ClassRelation rel(String src, String tgt) {
    return new ClassRelation(ci(src), ci(tgt), DependencyType.COMPOSITION, false);
}
```

- [ ] **Step 6: Run tests to confirm compilation and all tests pass**

```
mvn test
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 7: Delete `RelationType.java` and `RelationTypeTest.java`**

Delete:
- `src/main/java/com/sosuisha/classdiagram/analyzer/RelationType.java`
- `src/test/java/com/sosuisha/classdiagram/analyzer/RelationTypeTest.java`

- [ ] **Step 8: Run tests again to confirm no regressions**

```
mvn test
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 9: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelation.java
git add src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java
git add src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationTest.java
git add src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java
git add src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationSorterTest.java
git rm src/main/java/com/sosuisha/classdiagram/analyzer/RelationType.java
git rm src/test/java/com/sosuisha/classdiagram/analyzer/RelationTypeTest.java
git commit -m "refactor: replace RelationType with DependencyType"
```

---

## Task 2: Create `LayoutResult`

**Files:**
- Create: `src/main/java/com/sosuisha/classdiagram/LayoutResult.java`
- Create: `src/test/java/com/sosuisha/classdiagram/LayoutResultTest.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/sosuisha/classdiagram/LayoutResultTest.java`:

```java
package com.sosuisha.classdiagram;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LayoutResultTest {

    @Test
    void constructorThrowsForNullBoxes() {
        assertThrows(NullPointerException.class,
            () -> new LayoutResult(null, List.of(), 100, 100));
    }

    @Test
    void constructorThrowsForNullDependencies() {
        assertThrows(NullPointerException.class,
            () -> new LayoutResult(List.of(), null, 100, 100));
    }

    @Test
    void boxesIsUnmodifiable() {
        var mutable = new ArrayList<ClassBox>();
        mutable.add(new ClassBox("A"));
        var result = new LayoutResult(mutable, List.of(), 100, 100);
        assertThrows(UnsupportedOperationException.class,
            () -> result.boxes().add(new ClassBox("B")));
    }

    @Test
    void dependenciesIsUnmodifiable() {
        var boxA = new ClassBox("A");
        var boxB = new ClassBox("B");
        var mutable = new ArrayList<Dependency>();
        mutable.add(new Dependency(boxA, boxB, DependencyType.COMPOSITION));
        var result = new LayoutResult(List.of(), mutable, 100, 100);
        assertThrows(UnsupportedOperationException.class,
            () -> result.dependencies().add(new Dependency(boxA, boxB, DependencyType.COMPOSITION)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
mvn test -pl . -Dtest=LayoutResultTest
```

Expected: FAIL — `LayoutResult` does not exist yet.

- [ ] **Step 3: Create `LayoutResult.java`**

```java
package com.sosuisha.classdiagram;

import java.util.List;
import java.util.Objects;

/**
 * レイアウト計算の結果を格納するイミュータブルなスナップショット。
 *
 * @param boxes        配置済みClassBoxのリスト（変更不可コピー）
 * @param dependencies Dependencyのリスト（変更不可コピー）
 * @param canvasWidth  キャンバスの幅（px）
 * @param canvasHeight キャンバスの高さ（px）
 * @throws NullPointerException boxesまたはdependenciesがnullの場合
 */
public record LayoutResult(
    List<ClassBox> boxes,
    List<Dependency> dependencies,
    int canvasWidth,
    int canvasHeight
) {
    public LayoutResult {
        Objects.requireNonNull(boxes, "boxes must not be null");
        Objects.requireNonNull(dependencies, "dependencies must not be null");
        boxes = List.copyOf(boxes);
        dependencies = List.copyOf(dependencies);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
mvn test -pl . -Dtest=LayoutResultTest
```

Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/LayoutResult.java
git add src/test/java/com/sosuisha/classdiagram/LayoutResultTest.java
git commit -m "feat: add LayoutResult record"
```

---

## Task 3: Create `ClassDiagramLayout`

**Files:**
- Create: `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java`
- Create: `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`

### Background: longest-path layer reassignment

`ClassRelationSorter` uses Kahn's BFS, which places each node as early as possible. For example, given `A→B→D` and `A→C` (C is a leaf with no outgoing edges):

- Kahn's result: `[[A], [B, C], [D]]` — C gets placed in layer 1 because its only predecessor A is in layer 0, even though C is a leaf just like D.
- Longest-path result: `[[A], [B], [C, D]]` — C is pushed down to the bottom layer alongside D, because both are leaves.

The reassignment algorithm:
1. For each node, compute `depth[v]` = length of the longest path from `v` to any leaf (0 = leaf).
2. Process nodes in reverse Kahn order: `depth[v] = max(depth[w] + 1)` for all successors `w`; 0 if no successors.
3. `maxDepth = max(depth[v])`.
4. `layer[v] = maxDepth - depth[v]`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java`:

```java
package com.sosuisha.classdiagram;

import com.sosuisha.classdiagram.analyzer.ClassInfo;
import com.sosuisha.classdiagram.analyzer.ClassRelation;
import com.sosuisha.classdiagram.analyzer.ClassRelationSorter;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ClassDiagramLayoutTest {

    private static final String PKG = "com.example";

    private static ClassInfo ci(String name) {
        return new ClassInfo(PKG, name);
    }

    private static ClassRelation rel(ClassInfo src, ClassInfo tgt) {
        return new ClassRelation(src, tgt, DependencyType.COMPOSITION, false);
    }

    @Test
    void layoutThrowsForNullLayers() {
        assertThrows(NullPointerException.class,
            () -> new ClassDiagramLayout(20, 40, 20, 20).layout(null, List.of()));
    }

    @Test
    void layoutThrowsForNullRelations() {
        assertThrows(NullPointerException.class,
            () -> new ClassDiagramLayout(20, 40, 20, 20).layout(List.of(), null));
    }

    @Test
    void layoutLongestPathReassignment() {
        // A→B→D, A→C  (C is a leaf like D)
        // Kahn: [[A],[B,C],[D]] — longest-path must produce [[A],[B],[C,D]]
        var a = ci("A"); var b = ci("B"); var c = ci("C"); var d = ci("D");
        var rels = List.of(rel(a, b), rel(b, d), rel(a, c));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20).layout(layers, rels);

        var boxA = result.boxes().stream().filter(bx -> bx.name().equals("A")).findFirst().orElseThrow();
        var boxB = result.boxes().stream().filter(bx -> bx.name().equals("B")).findFirst().orElseThrow();
        var boxC = result.boxes().stream().filter(bx -> bx.name().equals("C")).findFirst().orElseThrow();
        var boxD = result.boxes().stream().filter(bx -> bx.name().equals("D")).findFirst().orElseThrow();

        // C and D must be in the same bottom layer (same y)
        assertEquals(boxC.y(), boxD.y());
        // Layers flow top to bottom: A < B < C/D
        assertTrue(boxA.y() < boxB.y());
        assertTrue(boxB.y() < boxC.y());
    }

    @Test
    void layoutPositionsTopLayer() {
        // The topmost layer's y must equal canvasPaddingY
        var a = ci("A"); var b = ci("B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20).layout(layers, rels);

        var boxA = result.boxes().stream().filter(bx -> bx.name().equals("A")).findFirst().orElseThrow();
        assertEquals(20, boxA.y()); // canvasPaddingY = 20
    }

    @Test
    void layoutCentersLayer() {
        // A→B, A→C: B and C end up in the same layer.
        // Their x-center average must equal canvasWidth / 2.
        // Both "B" and "C" are 1-char names → same width (MIN_WIDTH = 100).
        var a = ci("A"); var b = ci("B"); var c = ci("C");
        var rels = List.of(rel(a, b), rel(a, c));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20).layout(layers, rels);

        int canvasWidth = result.canvasWidth();
        var boxB = result.boxes().stream().filter(bx -> bx.name().equals("B")).findFirst().orElseThrow();
        var boxC = result.boxes().stream().filter(bx -> bx.name().equals("C")).findFirst().orElseThrow();

        int centerB = boxB.x() + boxB.width() / 2;
        int centerC = boxC.x() + boxC.width() / 2;
        assertEquals(canvasWidth / 2, (centerB + centerC) / 2);
    }

    @Test
    void layoutCanvasSize() {
        var a = ci("A"); var b = ci("B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        int padding = 20;
        var result = new ClassDiagramLayout(20, 40, padding, padding).layout(layers, rels);

        assertTrue(result.canvasWidth() >= 2 * padding);
        assertTrue(result.canvasHeight() >= 2 * padding);
    }

    @Test
    void layoutCreatesDependencies() {
        var a = ci("A"); var b = ci("B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20).layout(layers, rels);

        assertEquals(1, result.dependencies().size());
        assertEquals(DependencyType.COMPOSITION, result.dependencies().get(0).type());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
mvn test -pl . -Dtest=ClassDiagramLayoutTest
```

Expected: FAIL — `ClassDiagramLayout` does not exist yet.

- [ ] **Step 3: Create `ClassDiagramLayout.java`**

```java
package com.sosuisha.classdiagram;

import com.sosuisha.classdiagram.analyzer.ClassInfo;
import com.sosuisha.classdiagram.analyzer.ClassRelation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * クラス関係情報からレイアウト位置を計算するエンジン。
 *
 * <p>Kahn法のレイヤー結果を最長パス法で再割り当てし、末端クラスを最下層へ集める。
 * 各レイヤー内のボックスはキャンバス中央揃えで配置される。
 */
public class ClassDiagramLayout {

    private final int horizontalGap;
    private final int verticalGap;
    private final int canvasPaddingX;
    private final int canvasPaddingY;

    /**
     * ClassDiagramLayoutを生成する。
     *
     * @param horizontalGap  同一レイヤー内のボックス間水平隙間（px）
     * @param verticalGap    レイヤー間の垂直隙間（px）
     * @param canvasPaddingX キャンバス左右の余白（px）
     * @param canvasPaddingY キャンバス上下の余白（px）
     */
    public ClassDiagramLayout(int horizontalGap, int verticalGap,
                               int canvasPaddingX, int canvasPaddingY) {
        this.horizontalGap = horizontalGap;
        this.verticalGap = verticalGap;
        this.canvasPaddingX = canvasPaddingX;
        this.canvasPaddingY = canvasPaddingY;
    }

    /**
     * レイヤーと関係リストからレイアウト結果を計算する。
     *
     * @param layers    ClassRelationSorterが出力したレイヤーリスト
     * @param relations ClassRelationのリスト
     * @return レイアウト計算結果
     * @throws NullPointerException layersまたはrelationsがnullの場合
     */
    public LayoutResult layout(List<List<ClassInfo>> layers, List<ClassRelation> relations) {
        Objects.requireNonNull(layers, "layers must not be null");
        Objects.requireNonNull(relations, "relations must not be null");

        if (layers.isEmpty()) {
            return new LayoutResult(List.of(), List.of(), canvasPaddingX * 2, canvasPaddingY * 2);
        }

        // Step 1: 最長パス法でレイヤーを再割り当て
        var reassigned = reassignLayers(layers, relations);

        // Step 2: ClassInfo → ClassBox マップ作成（挿入順保持）
        Map<ClassInfo, ClassBox> boxMap = new LinkedHashMap<>();
        for (var layer : reassigned) {
            for (var info : layer) {
                boxMap.put(info, new ClassBox(info.simpleName()));
            }
        }

        // Step 3: レイヤーごとに幅と最大高さを計算
        int numLayers = reassigned.size();
        int[] maxBoxHeight = new int[numLayers];
        int[] layerWidth = new int[numLayers];

        for (int i = 0; i < numLayers; i++) {
            var layer = reassigned.get(i);
            int w = 0;
            int maxH = 0;
            for (var info : layer) {
                var box = boxMap.get(info);
                w += box.width();
                maxH = Math.max(maxH, box.height());
            }
            if (layer.size() > 1) {
                w += (layer.size() - 1) * horizontalGap;
            }
            layerWidth[i] = w;
            maxBoxHeight[i] = maxH;
        }

        // canvasContentWidth = 全レイヤーの最大幅
        int canvasContentWidth = 0;
        for (int w : layerWidth) {
            canvasContentWidth = Math.max(canvasContentWidth, w);
        }

        // Step 4: 中央揃えで各ボックスに座標を設定
        int currentY = canvasPaddingY;
        for (int i = 0; i < numLayers; i++) {
            var layer = reassigned.get(i);
            int startX = canvasPaddingX + (canvasContentWidth - layerWidth[i]) / 2;
            int x = startX;
            for (var info : layer) {
                var box = boxMap.get(info);
                box.setPosition(x, currentY);
                x += box.width() + horizontalGap;
            }
            currentY += maxBoxHeight[i] + verticalGap;
        }

        // Step 5: キャンバスサイズ計算
        int totalHeight = 0;
        for (int h : maxBoxHeight) totalHeight += h;
        totalHeight += (numLayers - 1) * verticalGap;
        int canvasWidth = canvasContentWidth + 2 * canvasPaddingX;
        int canvasHeight = totalHeight + 2 * canvasPaddingY;

        // Step 6: Dependency 生成
        var dependencies = new ArrayList<Dependency>();
        for (var rel : relations) {
            var src = boxMap.get(rel.sourceClassInfo());
            var tgt = boxMap.get(rel.targetClassInfo());
            if (src != null && tgt != null) {
                dependencies.add(new Dependency(src, tgt, rel.type()));
            }
        }

        return new LayoutResult(
            List.copyOf(boxMap.values()),
            List.copyOf(dependencies),
            canvasWidth,
            canvasHeight
        );
    }

    /**
     * Kahn法のレイヤーを最長パス法で再割り当てする。
     *
     * <p>depth[v] = vから最も遠い末端までの最長パス長。末端ノードは depth=0。
     * layer[v] = maxDepth - depth[v] により末端が最下層に集まる。
     */
    private List<List<ClassInfo>> reassignLayers(
            List<List<ClassInfo>> kahnsLayers,
            List<ClassRelation> relations) {

        // 隣接マップ（ソース→サクセッサ集合）を構築
        Map<ClassInfo, Set<ClassInfo>> adjacency = new HashMap<>();
        for (var rel : relations) {
            adjacency.computeIfAbsent(rel.sourceClassInfo(), k -> new HashSet<>())
                     .add(rel.targetClassInfo());
        }

        // Kahnレイヤーの逆順 = reverse topological order でdepthを計算
        Map<ClassInfo, Integer> depth = new HashMap<>();
        for (int i = kahnsLayers.size() - 1; i >= 0; i--) {
            for (var node : kahnsLayers.get(i)) {
                int maxSuccDepth = -1;
                for (var succ : adjacency.getOrDefault(node, Set.of())) {
                    maxSuccDepth = Math.max(maxSuccDepth, depth.getOrDefault(succ, 0));
                }
                depth.put(node, maxSuccDepth + 1);
            }
        }

        int maxDepth = depth.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int numLayers = maxDepth + 1;

        List<List<ClassInfo>> result = new ArrayList<>();
        for (int i = 0; i < numLayers; i++) {
            result.add(new ArrayList<>());
        }
        for (var entry : depth.entrySet()) {
            int layerIdx = maxDepth - entry.getValue();
            result.get(layerIdx).add(entry.getKey());
        }

        // 各レイヤー内を名前でソート（決定論的順序）
        for (var layer : result) {
            layer.sort(Comparator.comparing(ClassInfo::simpleName)
                                 .thenComparing(ClassInfo::packageName));
        }

        return result;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
mvn test -pl . -Dtest=ClassDiagramLayoutTest
```

Expected: PASS (7 tests).

- [ ] **Step 5: Run full test suite to check for regressions**

```
mvn test
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 6: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java
git commit -m "feat: add ClassDiagramLayout positioning engine"
```

---

## Task 4: Create `ClassDiagramGenerator`

**Files:**
- Create: `src/test/java/com/sosuisha/classdiagram/ClassDiagramGeneratorTest.java`
- Create: `src/main/java/com/sosuisha/classdiagram/ClassDiagramGenerator.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/sosuisha/classdiagram/ClassDiagramGeneratorTest.java`:

```java
package com.sosuisha.classdiagram;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ClassDiagramGeneratorTest {

    @Test
    void generateThrowsForNullClassRoot() {
        assertThrows(NullPointerException.class,
            () -> new ClassDiagramGenerator(20, 40, 20, 20).generate(null, "com.example"));
    }

    @Test
    void generateThrowsForNullPackageName() {
        assertThrows(NullPointerException.class,
            () -> new ClassDiagramGenerator(20, 40, 20, 20)
                      .generate(Path.of("target/test-classes"), null));
    }

    @Test
    void generateReturnsEmptySvgForNonExistentPackage() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20)
            .generate(Path.of("target/test-classes"), "com.does.not.exist");
        assertTrue(svg.startsWith("<svg"));
    }

    @Test
    void generateProducesFullSvgForFixturePackage() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20)
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.contains("FixtureOrder"));
        assertTrue(svg.contains("FixtureItem"));
        assertTrue(svg.contains("FixtureCustomer"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
mvn test -pl . -Dtest=ClassDiagramGeneratorTest
```

Expected: FAIL — `ClassDiagramGenerator` does not exist yet.

- [ ] **Step 3: Create `ClassDiagramGenerator.java`**

```java
package com.sosuisha.classdiagram;

import com.sosuisha.classdiagram.analyzer.ClassRelationScanner;
import com.sosuisha.classdiagram.analyzer.ClassRelationSorter;
import java.nio.file.Path;
import java.util.Objects;

/**
 * クラス図SVGを生成するファサード。
 *
 * <p>ClassRelationScanner → ClassRelationSorter → ClassDiagramLayout → SVGBuilder の
 * パイプラインを一括実行し、SVG文字列を返す。
 */
public class ClassDiagramGenerator {

    private final int horizontalGap;
    private final int verticalGap;
    private final int canvasPaddingX;
    private final int canvasPaddingY;

    /**
     * ClassDiagramGeneratorを生成する。
     *
     * @param horizontalGap  同一レイヤー内のボックス間水平隙間（px）
     * @param verticalGap    レイヤー間の垂直隙間（px）
     * @param canvasPaddingX キャンバス左右の余白（px）
     * @param canvasPaddingY キャンバス上下の余白（px）
     */
    public ClassDiagramGenerator(int horizontalGap, int verticalGap,
                                  int canvasPaddingX, int canvasPaddingY) {
        this.horizontalGap = horizontalGap;
        this.verticalGap = verticalGap;
        this.canvasPaddingX = canvasPaddingX;
        this.canvasPaddingY = canvasPaddingY;
    }

    /**
     * 指定パッケージのクラス図SVGを生成して返す。
     *
     * @param classRoot   コンパイル済みクラスのルートディレクトリ
     * @param packageName 分析対象パッケージ名
     * @return SVG文字列
     * @throws NullPointerException classRootまたはpackageNameがnullの場合
     * @throws com.sosuisha.classdiagram.analyzer.CircularRelationException 循環参照が検出された場合
     */
    public String generate(Path classRoot, String packageName) {
        Objects.requireNonNull(classRoot, "classRoot must not be null");
        Objects.requireNonNull(packageName, "packageName must not be null");

        var relations = new ClassRelationScanner().scan(classRoot, packageName);
        if (relations.isEmpty()) {
            return new SVGBuilder(canvasPaddingX * 2, canvasPaddingY * 2).build();
        }

        var layers = new ClassRelationSorter().sort(relations);
        var result = new ClassDiagramLayout(horizontalGap, verticalGap, canvasPaddingX, canvasPaddingY)
                         .layout(layers, relations);
        var builder = new SVGBuilder(result.canvasWidth(), result.canvasHeight());
        result.boxes().forEach(builder::add);
        result.dependencies().forEach(builder::add);
        return builder.build();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
mvn test -pl . -Dtest=ClassDiagramGeneratorTest
```

Expected: PASS (4 tests).

- [ ] **Step 5: Run full test suite to check for regressions**

```
mvn test
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 6: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramGenerator.java
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramGeneratorTest.java
git commit -m "feat: add ClassDiagramGenerator facade"
```
