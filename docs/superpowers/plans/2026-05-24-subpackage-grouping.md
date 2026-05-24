# Sub-Package Grouping Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opt-in layout feature that encloses classes belonging to the same `(connected-component, sub-package)` pair in a sketchy rectangle with a sub-package label, while preserving the existing layout output when the feature is not enabled.

**Architecture:** Introduce a new `PackageGroupBox` SVG element. Extend `LayoutResult` with a `packageGroups` field. Extend `ClassDiagramLayout` with a fluent `enableSubPackageGrouping(rootPackage, packageGap)` setter that, when set, partitions each connected component into per-sub-package horizontal slots (root package fixed at left, remaining slots ordered by barycenter), stacks slot members vertically by their original Sugiyama-layer index, and emits a `PackageGroupBox` per non-root slot. Expose the same option on `ClassDiagramGenerator` so callers can flip it on without touching `ClassDiagramLayout` directly.

**Tech Stack:** Java 25, Maven, JUnit 5. Follows project Javadoc + `Objects.requireNonNull` + `var` conventions.

**Spec:** `docs/superpowers/specs/2026-05-24-subpackage-grouping-design.md`

---

## File Map

**Create:**
- `src/main/java/com/sosuisha/classdiagram/PackageGroupBox.java`
- `src/test/java/com/sosuisha/classdiagram/PackageGroupBoxTest.java`
- `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutSubPackageTest.java`

**Modify:**
- `src/main/java/com/sosuisha/classdiagram/LayoutResult.java` — add `packageGroups` field
- `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java` — add `enableSubPackageGrouping` setter and new layout branch
- `src/main/java/com/sosuisha/classdiagram/ClassDiagramGenerator.java` — add `enableSubPackageGrouping` setter; reorder SVG element emission so package groups render first
- `src/test/java/com/sosuisha/classdiagram/LayoutResultTest.java` — update all `new LayoutResult(...)` call sites to pass the new `packageGroups` argument

---

## Task 1: Create `PackageGroupBox` with constructor validation

**Files:**
- Create: `src/main/java/com/sosuisha/classdiagram/PackageGroupBox.java`
- Create: `src/test/java/com/sosuisha/classdiagram/PackageGroupBoxTest.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/sosuisha/classdiagram/PackageGroupBoxTest.java`:

```java
package com.sosuisha.classdiagram;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackageGroupBoxTest {

    @Test
    void storesLabelAndGeometry() {
        var box = new PackageGroupBox("service", 10, 20, 100, 200);
        assertEquals("service", box.label());
        assertEquals(10, box.x());
        assertEquals(20, box.y());
        assertEquals(100, box.width());
        assertEquals(200, box.height());
    }

    @Test
    void throwsWhenLabelIsNull() {
        assertThrows(NullPointerException.class,
            () -> new PackageGroupBox(null, 0, 0, 10, 10));
    }

    @Test
    void throwsWhenWidthIsZeroOrNegative() {
        assertThrows(IllegalArgumentException.class,
            () -> new PackageGroupBox("p", 0, 0, 0, 10));
        assertThrows(IllegalArgumentException.class,
            () -> new PackageGroupBox("p", 0, 0, -1, 10));
    }

    @Test
    void throwsWhenHeightIsZeroOrNegative() {
        assertThrows(IllegalArgumentException.class,
            () -> new PackageGroupBox("p", 0, 0, 10, 0));
        assertThrows(IllegalArgumentException.class,
            () -> new PackageGroupBox("p", 0, 0, 10, -1));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -pl . test -Dtest=PackageGroupBoxTest`
Expected: FAIL with compilation error (class does not exist).

- [ ] **Step 3: Create the minimal implementation**

Create `src/main/java/com/sosuisha/classdiagram/PackageGroupBox.java`:

```java
package com.sosuisha.classdiagram;

import java.util.Objects;

/**
 * 同一サブパッケージのクラス群を囲むスケッチ風矩形を表すSVG要素。
 *
 * <p>位置・寸法・ラベルは構築後イミュータブル。
 */
public final class PackageGroupBox implements SvgElement {

    private final String label;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    /**
     * PackageGroupBoxを生成する。
     *
     * @param label  サブパッケージラベル
     * @param x      左上X座標
     * @param y      左上Y座標
     * @param width  幅（px、正数）
     * @param height 高さ（px、正数）
     * @throws NullPointerException     labelがnullの場合
     * @throws IllegalArgumentException widthまたはheightが0以下の場合
     */
    public PackageGroupBox(String label, int x, int y, int width, int height) {
        Objects.requireNonNull(label, "label must not be null");
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive: " + width);
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive: " + height);
        }
        this.label = label;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** @return サブパッケージラベル */
    public String label() { return label; }

    /** @return 左上X座標 */
    public int x() { return x; }

    /** @return 左上Y座標 */
    public int y() { return y; }

    /** @return 幅（px） */
    public int width() { return width; }

    /** @return 高さ（px） */
    public int height() { return height; }

    /**
     * 暫定draw実装。Task 2で完成版に置き換える。
     *
     * @return 空文字列
     */
    @Override
    public String draw() {
        return "";
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl . test -Dtest=PackageGroupBoxTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sosuisha/classdiagram/PackageGroupBox.java src/test/java/com/sosuisha/classdiagram/PackageGroupBoxTest.java
git commit -m "feat: add PackageGroupBox skeleton with constructor validation"
```

---

## Task 2: Implement `PackageGroupBox.draw()`

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/PackageGroupBox.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/PackageGroupBoxTest.java`

- [ ] **Step 1: Append failing tests**

Append to `PackageGroupBoxTest`:

```java
    @Test
    void drawIncludesLabel() {
        var box = new PackageGroupBox("service.impl", 0, 0, 100, 50);
        assertTrue(box.draw().contains("service.impl"));
    }

    @Test
    void drawIncludesDiagramDrawAttribute() {
        var box = new PackageGroupBox("svc", 0, 0, 100, 50);
        var svg = box.draw();
        assertTrue(svg.contains("data-diagram-draw=\"package-group\""));
        assertTrue(svg.contains("data-diagram-draw-name=\"svc\""));
    }

    @Test
    void drawIncludesFourPathEdges() {
        var box = new PackageGroupBox("p", 10, 20, 100, 50);
        var svg = box.draw();
        // 4 sketchy edges → 4 <path elements
        int pathCount = svg.split("<path", -1).length - 1;
        assertEquals(4, pathCount);
    }

    @Test
    void drawAppliesTranslationWhenPositioned() {
        var box = new PackageGroupBox("p", 10, 20, 100, 50);
        assertTrue(box.draw().contains("translate(10,20)"));
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -pl . test -Dtest=PackageGroupBoxTest`
Expected: 4 new tests FAIL (draw returns empty string).

- [ ] **Step 3: Replace `draw()` with the full implementation**

Edit `src/main/java/com/sosuisha/classdiagram/PackageGroupBox.java`. Replace the existing `draw()` method (and add private helpers) with:

```java
    private static final int FONT_SIZE = 12;
    private static final int LABEL_PADDING_X = 6;
    private static final int LABEL_PADDING_Y = 4;
    private static final double SKETCH_MAX = 1.5;

    /**
     * PackageGroupBoxのSVG表現を返す。
     *
     * @return SVGのgタグ文字列
     */
    @Override
    public String draw() {
        var rng = new java.util.Random(Objects.hash(label, width, height));
        var sb = new StringBuilder();
        sb.append("<g data-diagram-draw=\"package-group\" data-diagram-draw-name=\"%s\" transform=\"translate(%d,%d)\">"
            .formatted(label, x, y));
        // 4 sketchy edges (top, right, bottom, left)
        sb.append(sketchyLine(0, 0, width, 0, rng));
        sb.append(sketchyLine(width, 0, width, height, rng));
        sb.append(sketchyLine(width, height, 0, height, rng));
        sb.append(sketchyLine(0, height, 0, 0, rng));
        // Label background (white rect to "cut" the top edge under the text)
        int labelWidth = label.length() * (FONT_SIZE / 2 + 1) + LABEL_PADDING_X * 2;
        sb.append("<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"white\"/>"
            .formatted(LABEL_PADDING_X, -FONT_SIZE / 2, labelWidth, FONT_SIZE + LABEL_PADDING_Y));
        // Label text
        int textY = LABEL_PADDING_Y + FONT_SIZE * 4 / 5 - FONT_SIZE / 2;
        sb.append("<text x=\"%d\" y=\"%d\" font-size=\"%d\">%s</text>"
            .formatted(LABEL_PADDING_X * 2, textY, FONT_SIZE, label));
        sb.append("</g>");
        return sb.toString();
    }

    private static String sketchyLine(int x1, int y1, int x2, int y2, java.util.Random rng) {
        double wobble = rng.nextDouble() * SKETCH_MAX * 2 - SKETCH_MAX;
        int mx = (x1 + x2) / 2;
        int my = (y1 + y2) / 2;
        double cp1x = (x1 + mx) / 2.0;
        double cp1y = (y1 + my) / 2.0;
        double cp2x = (mx + x2) / 2.0;
        double cp2y = (my + y2) / 2.0;
        if (Math.abs(x2 - x1) >= Math.abs(y2 - y1)) {
            cp1y += wobble;
            cp2y -= wobble;
        } else {
            cp1x += wobble;
            cp2x -= wobble;
        }
        return "<path d=\"M %d,%d Q %.1f,%.1f %d,%d Q %.1f,%.1f %d,%d\" fill=\"none\" stroke=\"black\"/>"
            .formatted(x1, y1, cp1x, cp1y, mx, my, cp2x, cp2y, x2, y2);
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl . test -Dtest=PackageGroupBoxTest`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sosuisha/classdiagram/PackageGroupBox.java src/test/java/com/sosuisha/classdiagram/PackageGroupBoxTest.java
git commit -m "feat: implement PackageGroupBox sketchy draw output"
```

---

## Task 3: Add `packageGroups` field to `LayoutResult`

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/LayoutResult.java`
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java:64`
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java:199`
- Modify: `src/test/java/com/sosuisha/classdiagram/LayoutResultTest.java`

- [ ] **Step 1: Append failing tests to `LayoutResultTest`**

Append to `src/test/java/com/sosuisha/classdiagram/LayoutResultTest.java` (inside the class):

```java
    @Test
    void constructorThrowsForNullPackageGroups() {
        assertThrows(NullPointerException.class,
            () -> new LayoutResult(List.of(), List.of(), null, 100, 100));
    }

    @Test
    void packageGroupsIsUnmodifiable() {
        var mutable = new ArrayList<PackageGroupBox>();
        mutable.add(new PackageGroupBox("p", 0, 0, 10, 10));
        var result = new LayoutResult(List.of(), List.of(), mutable, 100, 100);
        assertThrows(UnsupportedOperationException.class,
            () -> result.packageGroups().add(new PackageGroupBox("q", 0, 0, 10, 10)));
    }

    @Test
    void packageGroupsDefaultsToEmptyListInLegacyConstructor() {
        var result = new LayoutResult(List.of(), List.of(), 100, 100);
        assertTrue(result.packageGroups().isEmpty());
    }
```

Also update the three existing tests to use the new 5-arg constructor:

Edit `src/test/java/com/sosuisha/classdiagram/LayoutResultTest.java`. Replace:

```java
            () -> new LayoutResult(null, List.of(), 100, 100));
```
with:
```java
            () -> new LayoutResult(null, List.of(), List.of(), 100, 100));
```

Replace:
```java
            () -> new LayoutResult(List.of(), null, 100, 100));
```
with:
```java
            () -> new LayoutResult(List.of(), null, List.of(), 100, 100));
```

Replace:
```java
        var result = new LayoutResult(mutable, List.of(), 100, 100);
```
with:
```java
        var result = new LayoutResult(mutable, List.of(), List.of(), 100, 100);
```

Replace:
```java
        var result = new LayoutResult(List.of(), mutable, 100, 100);
```
with:
```java
        var result = new LayoutResult(List.of(), mutable, List.of(), 100, 100);
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -pl . test -Dtest=LayoutResultTest`
Expected: FAIL with compile errors (LayoutResult does not accept 5 args; `packageGroups()` accessor missing).

- [ ] **Step 3: Update `LayoutResult` record**

Replace the entire body of `src/main/java/com/sosuisha/classdiagram/LayoutResult.java`:

```java
package com.sosuisha.classdiagram;

import java.util.List;
import java.util.Objects;

/**
 * レイアウト計算の結果を格納するイミュータブルなスナップショット。
 *
 * @param boxes         配置済みClassBoxのリスト（変更不可コピー）
 * @param dependencies  Dependencyのリスト（変更不可コピー）
 * @param packageGroups PackageGroupBoxのリスト（変更不可コピー、無効時は空）
 * @param canvasWidth   キャンバスの幅（px）
 * @param canvasHeight  キャンバスの高さ（px）
 * @throws NullPointerException boxes、dependencies、またはpackageGroupsがnullの場合
 */
public record LayoutResult(
    List<ClassBox> boxes,
    List<Dependency> dependencies,
    List<PackageGroupBox> packageGroups,
    int canvasWidth,
    int canvasHeight
) {
    public LayoutResult {
        Objects.requireNonNull(boxes, "boxes must not be null");
        Objects.requireNonNull(dependencies, "dependencies must not be null");
        Objects.requireNonNull(packageGroups, "packageGroups must not be null");
        boxes = List.copyOf(boxes);
        dependencies = List.copyOf(dependencies);
        packageGroups = List.copyOf(packageGroups);
    }

    /**
     * 後方互換用コンストラクタ。{@code packageGroups} は空リスト扱い。
     *
     * @param boxes        配置済みClassBoxのリスト
     * @param dependencies Dependencyのリスト
     * @param canvasWidth  キャンバスの幅（px）
     * @param canvasHeight キャンバスの高さ（px）
     * @throws NullPointerException boxesまたはdependenciesがnullの場合
     */
    public LayoutResult(List<ClassBox> boxes, List<Dependency> dependencies,
                        int canvasWidth, int canvasHeight) {
        this(boxes, dependencies, List.of(), canvasWidth, canvasHeight);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl . test -Dtest=LayoutResultTest`
Expected: PASS (7 tests).

- [ ] **Step 5: Run full test suite to confirm no regression**

Run: `mvn -q test`
Expected: ALL tests pass (the legacy 4-arg constructor preserved in `ClassDiagramLayout.layout()` still works).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/sosuisha/classdiagram/LayoutResult.java src/test/java/com/sosuisha/classdiagram/LayoutResultTest.java
git commit -m "feat: add packageGroups field to LayoutResult"
```

---

## Task 4: Add `enableSubPackageGrouping` setter to `ClassDiagramLayout` (validation only)

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`
- Create: `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutSubPackageTest.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutSubPackageTest.java`:

```java
package com.sosuisha.classdiagram;

import com.sosuisha.classdiagram.analyzer.ClassInfo;
import com.sosuisha.classdiagram.analyzer.ClassRelation;
import com.sosuisha.classdiagram.analyzer.ClassRelationSorter;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ClassDiagramLayoutSubPackageTest {

    private static final String ROOT = "com.example";

    private static ClassInfo ci(String pkg, String name) {
        return new ClassInfo(pkg, name);
    }

    private static ClassRelation rel(ClassInfo src, ClassInfo tgt) {
        return new ClassRelation(src, tgt, DependencyType.COMPOSITION, false);
    }

    @Test
    void enableSubPackageGroupingThrowsForNullRootPackage() {
        var layout = new ClassDiagramLayout(20, 40, 20, 20, 60);
        assertThrows(NullPointerException.class,
            () -> layout.enableSubPackageGrouping(null, 30));
    }

    @Test
    void enableSubPackageGroupingThrowsForNegativePackageGap() {
        var layout = new ClassDiagramLayout(20, 40, 20, 20, 60);
        assertThrows(IllegalArgumentException.class,
            () -> layout.enableSubPackageGrouping(ROOT, -1));
    }

    @Test
    void enableSubPackageGroupingReturnsSelfForChaining() {
        var layout = new ClassDiagramLayout(20, 40, 20, 20, 60);
        assertSame(layout, layout.enableSubPackageGrouping(ROOT, 30));
    }

    @Test
    void packageGroupsEmptyWhenOptionNotEnabled() {
        var a = ci(ROOT, "A"); var b = ci(ROOT, "B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);
        assertTrue(result.packageGroups().isEmpty());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -pl . test -Dtest=ClassDiagramLayoutSubPackageTest`
Expected: FAIL with compile error (no `enableSubPackageGrouping` method).

- [ ] **Step 3: Add the setter to `ClassDiagramLayout`**

Edit `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`. Add the two new fields below the existing fields (after `groupGap`):

```java
    private String rootPackageForGrouping = null;
    private int packageGap = 0;
```

Add the setter method directly after the constructor:

```java
    /**
     * サブパッケージグルーピングを有効化する。設定すると {@link #layout} が
     * 各ConnectedComponent内を {@code (groupIndex, packageName)} ごとの水平スロットに分割し、
     * 非ルートスロットを {@link PackageGroupBox} で囲む。未呼出時は既存レイアウトと同一出力。
     *
     * @param rootPackage スキャン対象パッケージ名（相対サブパッケージラベル算出に使用）
     * @param packageGap  サブパッケージスロット間の水平隙間（px、0以上）
     * @return このレイアウト自身（メソッドチェーン用）
     * @throws NullPointerException     rootPackageがnullの場合
     * @throws IllegalArgumentException packageGapが0未満の場合
     */
    public ClassDiagramLayout enableSubPackageGrouping(String rootPackage, int packageGap) {
        Objects.requireNonNull(rootPackage, "rootPackage must not be null");
        if (packageGap < 0) {
            throw new IllegalArgumentException("packageGap must be >= 0: " + packageGap);
        }
        this.rootPackageForGrouping = rootPackage;
        this.packageGap = packageGap;
        return this;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl . test -Dtest=ClassDiagramLayoutSubPackageTest`
Expected: PASS (4 tests; the algorithm branch is still untaken since the option is unused in those tests, and the empty-default test passes because the new `packageGroups` argument is omitted in the existing return statements — `LayoutResult` legacy constructor returns empty list).

- [ ] **Step 5: Run full test suite**

Run: `mvn -q test`
Expected: ALL existing tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutSubPackageTest.java
git commit -m "feat: add enableSubPackageGrouping setter to ClassDiagramLayout"
```

---

## Task 5: Implement single-sub-package grouping (smallest enabled case)

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutSubPackageTest.java`

- [ ] **Step 1: Append failing test**

Append to `ClassDiagramLayoutSubPackageTest`:

```java
    @Test
    void singleSubPackageProducesOnePackageGroupBoxWithRelativeLabel() {
        // Both classes are in com.example.service → one slot, label "service".
        var a = ci(ROOT + ".service", "A");
        var b = ci(ROOT + ".service", "B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(ROOT, 30)
            .layout(layers, rels);

        assertEquals(1, result.packageGroups().size());
        assertEquals("service", result.packageGroups().get(0).label());
    }

    @Test
    void packageGroupBoxEnclosesAllItsMembers() {
        var a = ci(ROOT + ".service", "A");
        var b = ci(ROOT + ".service", "B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(ROOT, 30)
            .layout(layers, rels);

        var pg = result.packageGroups().get(0);
        for (var box : result.boxes()) {
            assertTrue(box.x() >= pg.x(), "box left inside group");
            assertTrue(box.y() >= pg.y(), "box top inside group");
            assertTrue(box.x() + box.width() <= pg.x() + pg.width(), "box right inside group");
            assertTrue(box.y() + box.height() <= pg.y() + pg.height(), "box bottom inside group");
        }
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -pl . test -Dtest=ClassDiagramLayoutSubPackageTest`
Expected: 2 new tests FAIL (`packageGroups` is still empty because the algorithm branch is not implemented).

- [ ] **Step 3: Wire the algorithm branch into `layout()`**

Edit `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`.

First, add these constants near the top of the class (after `MAX_CROSSING_PASSES`):

```java
    private static final int GROUP_PADDING_LEFT = 15;
    private static final int GROUP_PADDING_RIGHT = 15;
    private static final int GROUP_PADDING_TOP = 25;
    private static final int GROUP_PADDING_BOTTOM = 10;
```

Locate the final `return new LayoutResult(...)` statement at the bottom of `layout()` (currently lines 199–204). Replace it with:

```java
        // Step 9: optional sub-package grouping — rebuild positions per (groupIndex, packageName) slot.
        var packageGroups = new ArrayList<PackageGroupBox>();
        if (rootPackageForGrouping != null) {
            packageGroups.addAll(applySubPackageGrouping(orderedLayers, relations, boxMap));
            // Recompute canvas size after re-positioning.
            int maxRight = 0;
            int maxBottom = 0;
            for (var box : boxMap.values()) {
                maxRight = Math.max(maxRight, box.x() + box.width());
                maxBottom = Math.max(maxBottom, box.y() + box.height());
            }
            for (var pg : packageGroups) {
                maxRight = Math.max(maxRight, pg.x() + pg.width());
                maxBottom = Math.max(maxBottom, pg.y() + pg.height());
            }
            canvasWidth = maxRight + canvasPaddingX;
            canvasHeight = maxBottom + canvasPaddingY;
        }

        return new LayoutResult(
            List.copyOf(boxMap.values()),
            List.copyOf(dependencies),
            List.copyOf(packageGroups),
            canvasWidth,
            canvasHeight
        );
```

Add the two new private methods at the end of the class (before the closing brace). The slot ordering is initially alphabetical (root first); Task 9 will replace this with barycenter-based ordering:

```java
    private List<PackageGroupBox> applySubPackageGrouping(
            List<List<ClassInfo>> orderedLayers,
            List<ClassRelation> relations,
            Map<ClassInfo, ClassBox> boxMap) {

        // Build originalLayerIndex per ClassInfo (independent of groupIndex).
        Map<ClassInfo, Integer> originalLayerIndex = new HashMap<>();
        for (int i = 0; i < orderedLayers.size(); i++) {
            for (var info : orderedLayers.get(i)) {
                originalLayerIndex.put(info, i);
            }
        }

        // Group classes by groupIndex (preserve insertion order).
        Map<Integer, List<ClassInfo>> byGroup = new LinkedHashMap<>();
        for (var layer : orderedLayers) {
            for (var info : layer) {
                byGroup.computeIfAbsent(info.groupIndex(), k -> new ArrayList<>()).add(info);
            }
        }

        var result = new ArrayList<PackageGroupBox>();
        int currentGroupX = canvasPaddingX;

        for (var groupEntry : byGroup.entrySet()) {
            var members = groupEntry.getValue();

            // Partition members by slot key ("" for root, relative pkg name otherwise).
            Map<String, List<ClassInfo>> slotMembers = new LinkedHashMap<>();
            for (var info : members) {
                slotMembers.computeIfAbsent(slotKeyFor(info), k -> new ArrayList<>()).add(info);
            }

            // Slot ordering: root first, then alphabetical. (Task 9 will replace this with barycenter.)
            var slotOrder = new ArrayList<String>();
            if (slotMembers.containsKey("")) slotOrder.add("");
            slotMembers.keySet().stream()
                .filter(k -> !k.isEmpty())
                .sorted()
                .forEach(slotOrder::add);

            int slotStartX = currentGroupX;
            for (var key : slotOrder) {
                var slot = slotMembers.get(key);
                var dims = layoutSingleSlot(slot, originalLayerIndex, boxMap, slotStartX, canvasPaddingY);
                if (!key.isEmpty()) {
                    result.add(new PackageGroupBox(
                        key,
                        slotStartX - GROUP_PADDING_LEFT,
                        canvasPaddingY - GROUP_PADDING_TOP,
                        dims[0] + GROUP_PADDING_LEFT + GROUP_PADDING_RIGHT,
                        dims[1] + GROUP_PADDING_TOP + GROUP_PADDING_BOTTOM
                    ));
                }
                slotStartX += dims[0] + (key.isEmpty() ? 0 : GROUP_PADDING_LEFT + GROUP_PADDING_RIGHT) + packageGap;
            }

            currentGroupX = slotStartX - packageGap + groupGap;
        }

        return result;
    }

    private String slotKeyFor(ClassInfo info) {
        if (info.packageName().equals(rootPackageForGrouping)) return "";
        if (info.packageName().startsWith(rootPackageForGrouping + ".")) {
            return info.packageName().substring(rootPackageForGrouping.length() + 1);
        }
        // Defensive: package outside root → use full package name as key.
        return info.packageName();
    }

    /**
     * 1スロットを縦配置し、配置後のスロット幅・高さを返す。
     *
     * @return {{@code width}, {@code height}}
     */
    private int[] layoutSingleSlot(
            List<ClassInfo> members,
            Map<ClassInfo, Integer> originalLayerIndex,
            Map<ClassInfo, ClassBox> boxMap,
            int startX, int startY) {
        // Group by original layer index, sorted ascending.
        var byLayer = new TreeMap<Integer, List<ClassInfo>>();
        for (var info : members) {
            byLayer.computeIfAbsent(originalLayerIndex.get(info), k -> new ArrayList<>()).add(info);
        }

        // Compute each row width and the max width (= slot width).
        var rowWidths = new ArrayList<Integer>();
        var rowMaxHeights = new ArrayList<Integer>();
        int slotWidth = 0;
        for (var rowMembers : byLayer.values()) {
            int w = 0;
            int h = 0;
            for (var info : rowMembers) {
                var b = boxMap.get(info);
                w += b.width();
                h = Math.max(h, b.height());
            }
            if (rowMembers.size() > 1) w += (rowMembers.size() - 1) * horizontalGap;
            rowWidths.add(w);
            rowMaxHeights.add(h);
            slotWidth = Math.max(slotWidth, w);
        }

        // Place each row centered within the slot width.
        int currentY = startY;
        int rowIdx = 0;
        for (var rowMembers : byLayer.values()) {
            int rowStartX = startX + (slotWidth - rowWidths.get(rowIdx)) / 2;
            int x = rowStartX;
            for (var info : rowMembers) {
                var b = boxMap.get(info);
                b.setPosition(x, currentY);
                x += b.width() + horizontalGap;
            }
            currentY += rowMaxHeights.get(rowIdx) + verticalGap;
            rowIdx++;
        }

        int slotHeight = (currentY - startY) - (byLayer.isEmpty() ? 0 : verticalGap);
        return new int[] { slotWidth, slotHeight };
    }
```

Add the required import at the top of `ClassDiagramLayout.java` (if not already present):

```java
import java.util.TreeMap;
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl . test -Dtest=ClassDiagramLayoutSubPackageTest`
Expected: PASS (6 tests).

- [ ] **Step 5: Run full test suite to confirm no regression**

Run: `mvn -q test`
Expected: ALL tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutSubPackageTest.java
git commit -m "feat: implement single sub-package slot layout with bounding rect"
```

---

## Task 6: Root + sub-package mixed — root slot stays at left

**Files:**
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutSubPackageTest.java`

- [ ] **Step 1: Append failing test**

```java
    @Test
    void rootPackageSlotIsPlacedLeftOfSubPackageSlots() {
        // R (root) ← S (com.example.service). One connected component (REALIZATION).
        var rRoot = ci(ROOT, "R");
        var sSvc  = ci(ROOT + ".service", "S");
        var rels = List.of(
            new ClassRelation(sSvc, rRoot, DependencyType.REALIZATION, false));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(ROOT, 30)
            .layout(layers, rels);

        var boxR = result.boxes().stream().filter(b -> b.name().equals("R")).findFirst().orElseThrow();
        var boxS = result.boxes().stream().filter(b -> b.name().equals("S")).findFirst().orElseThrow();
        assertTrue(boxR.x() < boxS.x(), "root-package class must be left of sub-package class");

        // Only the sub-package "service" is enclosed; root class is not.
        assertEquals(1, result.packageGroups().size());
        assertEquals("service", result.packageGroups().get(0).label());
        // The "service" PackageGroupBox encloses S but NOT R.
        var pg = result.packageGroups().get(0);
        assertTrue(boxR.x() + boxR.width() <= pg.x(), "R is left of and outside the service group");
    }
```

- [ ] **Step 2: Run test to verify it passes (already supported by Task 5 implementation)**

Run: `mvn -q -pl . test -Dtest=ClassDiagramLayoutSubPackageTest#rootPackageSlotIsPlacedLeftOfSubPackageSlots`
Expected: PASS (root is already placed first in `slotOrder` from Task 5).

If it fails, fix the algorithm before committing.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutSubPackageTest.java
git commit -m "test: cover root-package slot placement on left"
```

---

## Task 7: Multi-level sub-package label (e.g. `service.impl`)

**Files:**
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutSubPackageTest.java`

- [ ] **Step 1: Append failing test**

```java
    @Test
    void multiLevelSubPackageLabelUsesFullRelativeName() {
        var iface = ci(ROOT + ".service", "Svc");
        var impl  = ci(ROOT + ".service.impl", "SvcImpl");
        var rels = List.of(
            new ClassRelation(impl, iface, DependencyType.REALIZATION, false));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(ROOT, 30)
            .layout(layers, rels);

        var labels = result.packageGroups().stream().map(PackageGroupBox::label).toList();
        assertTrue(labels.contains("service"));
        assertTrue(labels.contains("service.impl"));
    }
```

- [ ] **Step 2: Run test to verify it passes**

Run: `mvn -q -pl . test -Dtest=ClassDiagramLayoutSubPackageTest#multiLevelSubPackageLabelUsesFullRelativeName`
Expected: PASS (`slotKeyFor` already strips `rootPackage + "."`, leaving full relative name).

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutSubPackageTest.java
git commit -m "test: cover multi-level sub-package labels"
```

---

## Task 8: Non-contiguous Sugiyama layers — same sub-package classes stay in one rectangle

**Files:**
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutSubPackageTest.java`

- [ ] **Step 1: Append failing test**

This is the regression case that broke Approach 2 in the spec.

```java
    @Test
    void sameSubPackageClassesInNonContiguousLayersAreEnclosedTogether() {
        // service.A → other.M → service.B
        // Original Sugiyama layers: [A], [M], [B] (3 layers vertically)
        // With sub-package slots: A and B (both "service") stack in one slot vertically;
        // M lives in its own "other" slot horizontally separated.
        var a = ci(ROOT + ".service", "A");
        var m = ci(ROOT + ".other",   "M");
        var b = ci(ROOT + ".service", "B");
        var rels = List.of(rel(a, m), rel(m, b));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(ROOT, 30)
            .layout(layers, rels);

        var boxA = result.boxes().stream().filter(x -> x.name().equals("A")).findFirst().orElseThrow();
        var boxB = result.boxes().stream().filter(x -> x.name().equals("B")).findFirst().orElseThrow();
        var boxM = result.boxes().stream().filter(x -> x.name().equals("M")).findFirst().orElseThrow();

        var serviceGroup = result.packageGroups().stream()
            .filter(pg -> pg.label().equals("service")).findFirst().orElseThrow();

        // service rectangle encloses A and B…
        assertTrue(boxA.x() >= serviceGroup.x() && boxA.x() + boxA.width() <= serviceGroup.x() + serviceGroup.width());
        assertTrue(boxB.x() >= serviceGroup.x() && boxB.x() + boxB.width() <= serviceGroup.x() + serviceGroup.width());
        assertTrue(boxA.y() >= serviceGroup.y() && boxA.y() + boxA.height() <= serviceGroup.y() + serviceGroup.height());
        assertTrue(boxB.y() >= serviceGroup.y() && boxB.y() + boxB.height() <= serviceGroup.y() + serviceGroup.height());

        // …but NOT M (M sits in a different horizontal slot).
        boolean mIsOutsideHorizontally =
            (boxM.x() + boxM.width() <= serviceGroup.x()) || (boxM.x() >= serviceGroup.x() + serviceGroup.width());
        assertTrue(mIsOutsideHorizontally, "M must be horizontally outside the service group rectangle");
    }
```

- [ ] **Step 2: Run test to verify it passes**

Run: `mvn -q -pl . test -Dtest=ClassDiagramLayoutSubPackageTest#sameSubPackageClassesInNonContiguousLayersAreEnclosedTogether`
Expected: PASS — Task 5's algorithm partitions by `(groupIndex, packageName)` so A and B share one slot regardless of the Sugiyama-layer gap between them.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutSubPackageTest.java
git commit -m "test: cover non-contiguous Sugiyama layers regression case"
```

---

## Task 9: Barycenter ordering for non-root sub-package slots

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutSubPackageTest.java`

- [ ] **Step 1: Append failing test**

Scenario (hand-computed for single-pass barycenter):
- Three non-root sub-packages: `alpha`, `beta`, `gamma`. No root-package class.
- Relations: `beta.B → alpha.A` (REALIZATION) and `gamma.C → alpha.A` (REALIZATION).
- All three in one ConnectedComponent (linked through `alpha.A`).
- Alphabetical initial indices: `alpha=1, beta=2, gamma=3`.
- Single-pass barycenter:
  - `alpha`: linked to `beta` (idx 2) and `gamma` (idx 3) → bary = 2.5
  - `beta`: linked to `alpha` (idx 1) → bary = 1.0
  - `gamma`: linked to `alpha` (idx 1) → bary = 1.0
- Sort asc: `beta` (1.0), `gamma` (1.0), `alpha` (2.5). Final order: **beta, gamma, alpha**.
- Alphabetical would have been: alpha, beta, gamma — barycenter clearly differs.

```java
    @Test
    void barycenterOrderingDiffersFromAlphabetical() {
        // beta.B and gamma.C both REALIZE alpha.A → alpha is pulled to the right by barycenter.
        var alphaA = ci(ROOT + ".alpha", "A");
        var betaB  = ci(ROOT + ".beta",  "B");
        var gammaC = ci(ROOT + ".gamma", "C");
        var rels = List.of(
            new ClassRelation(betaB,  alphaA, DependencyType.REALIZATION, false),
            new ClassRelation(gammaC, alphaA, DependencyType.REALIZATION, false)
        );
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(ROOT, 30)
            .layout(layers, rels);

        int alphaX = result.packageGroups().stream()
            .filter(p -> p.label().equals("alpha")).findFirst().orElseThrow().x();
        int betaX  = result.packageGroups().stream()
            .filter(p -> p.label().equals("beta")).findFirst().orElseThrow().x();
        int gammaX = result.packageGroups().stream()
            .filter(p -> p.label().equals("gamma")).findFirst().orElseThrow().x();

        // Barycenter expected order: beta < gamma < alpha (alpha is rightmost).
        assertTrue(betaX < alphaX,  "barycenter places beta left of alpha");
        assertTrue(gammaX < alphaX, "barycenter places gamma left of alpha");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl . test -Dtest=ClassDiagramLayoutSubPackageTest#barycenterOrderingDiffersFromAlphabetical`
Expected: FAIL — current ordering is alphabetical (`alpha < beta < gamma`), so `betaX < alphaX` is false.

- [ ] **Step 3: Replace alphabetical ordering with barycenter in `applySubPackageGrouping`**

Edit `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`. In `applySubPackageGrouping`, replace this block:

```java
            // Slot ordering: root first, then alphabetical. (Task 9 will replace this with barycenter.)
            var slotOrder = new ArrayList<String>();
            if (slotMembers.containsKey("")) slotOrder.add("");
            slotMembers.keySet().stream()
                .filter(k -> !k.isEmpty())
                .sorted()
                .forEach(slotOrder::add);
```

with:

```java
            var slotOrder = orderSlotsByBarycenter(slotMembers, relations);
```

Add the new helper method after `applySubPackageGrouping`:

```java
    /**
     * スロット順序を決定: ルートを左端固定、残りを単一パス重心法で並べる。
     *
     * <p>初期インデックスはアルファベット順。各非ルートスロットの重心 = そのスロットメンバーが
     * source または target に含まれる relation のうち、相手側クラスが別スロットに属するものについて、
     * 相手側スロットの初期インデックスを平均した値。relation が 0 件のスロットは初期インデックスを
     * そのまま重心とする。単一パスのため発散しない。
     */
    private List<String> orderSlotsByBarycenter(
            Map<String, List<ClassInfo>> slotMembers,
            List<ClassRelation> relations) {

        // Per-class → slot key lookup.
        Map<ClassInfo, String> classToSlot = new HashMap<>();
        for (var entry : slotMembers.entrySet()) {
            for (var info : entry.getValue()) {
                classToSlot.put(info, entry.getKey());
            }
        }

        boolean hasRoot = slotMembers.containsKey("");
        var nonRoot = slotMembers.keySet().stream()
            .filter(k -> !k.isEmpty())
            .sorted()
            .collect(Collectors.toCollection(ArrayList::new));

        // Initial index map: root = 0 (if present), then non-root in alphabetical order.
        Map<String, Integer> idx = new HashMap<>();
        int next = 0;
        if (hasRoot) idx.put("", next++);
        for (var k : nonRoot) idx.put(k, next++);

        // Single-pass barycenter.
        Map<String, Double> bary = new HashMap<>();
        for (var key : nonRoot) {
            double sum = 0;
            int count = 0;
            for (var rel : relations) {
                var srcSlot = classToSlot.get(rel.sourceClassInfo());
                var tgtSlot = classToSlot.get(rel.targetClassInfo());
                if (key.equals(srcSlot) && tgtSlot != null && !key.equals(tgtSlot)) {
                    sum += idx.get(tgtSlot);
                    count++;
                } else if (key.equals(tgtSlot) && srcSlot != null && !key.equals(srcSlot)) {
                    sum += idx.get(srcSlot);
                    count++;
                }
            }
            bary.put(key, count > 0 ? sum / count : (double) idx.get(key));
        }

        // Stable sort non-root by barycenter; ties preserve alphabetical (initial) order.
        nonRoot.sort((a, b) -> {
            int cmp = Double.compare(bary.get(a), bary.get(b));
            if (cmp != 0) return cmp;
            return Integer.compare(idx.get(a), idx.get(b));
        });

        var result = new ArrayList<String>();
        if (hasRoot) result.add("");
        result.addAll(nonRoot);
        return result;
    }
```

Add imports at the top of `ClassDiagramLayout.java` if missing:

```java
import java.util.stream.Collectors;
```

(`java.util.HashMap` and `java.util.ArrayList` are already imported.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl . test -Dtest=ClassDiagramLayoutSubPackageTest`
Expected: PASS (all sub-package tests including the new barycenter test).

- [ ] **Step 5: Run full test suite**

Run: `mvn -q test`
Expected: ALL tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutSubPackageTest.java
git commit -m "feat: order non-root sub-package slots by barycenter"
```

---

## Task 10: Add `enableSubPackageGrouping` setter to `ClassDiagramGenerator`

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramGenerator.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassDiagramGeneratorTest.java`

- [ ] **Step 1: Append failing tests**

Edit `src/test/java/com/sosuisha/classdiagram/ClassDiagramGeneratorTest.java` (add to existing class):

```java
    @Test
    void enableSubPackageGroupingThrowsForNegativeGap() {
        assertThrows(IllegalArgumentException.class,
            () -> new ClassDiagramGenerator(20, 40, 20, 20, 60).enableSubPackageGrouping(-1));
    }

    @Test
    void enableSubPackageGroupingReturnsSelf() {
        var gen = new ClassDiagramGenerator(20, 40, 20, 20, 60);
        assertSame(gen, gen.enableSubPackageGrouping(30));
    }
```

If `ClassDiagramGeneratorTest` does not already import `assertSame`, ensure the `static org.junit.jupiter.api.Assertions.*` import is present.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -pl . test -Dtest=ClassDiagramGeneratorTest`
Expected: FAIL with compile error (no `enableSubPackageGrouping` on generator).

- [ ] **Step 3: Add the setter and wire it through `generate()`**

Edit `src/main/java/com/sosuisha/classdiagram/ClassDiagramGenerator.java`. Add a new field below `fontFamily`:

```java
    private int packageGapForGrouping = -1; // -1 = disabled
```

Add the setter directly after `fontFamily(...)`:

```java
    /**
     * サブパッケージグルーピングを有効化する。
     *
     * @param packageGap サブパッケージスロット間の水平隙間（px、0以上）
     * @return このジェネレーター自身（メソッドチェーン用）
     * @throws IllegalArgumentException packageGapが0未満の場合
     */
    public ClassDiagramGenerator enableSubPackageGrouping(int packageGap) {
        if (packageGap < 0) {
            throw new IllegalArgumentException("packageGap must be >= 0: " + packageGap);
        }
        this.packageGapForGrouping = packageGap;
        return this;
    }
```

In `generate()`, replace:

```java
        var result = new ClassDiagramLayout(horizontalGap, verticalGap, canvasPaddingX, canvasPaddingY, groupGap)
                         .layout(layers, relations);
```

with:

```java
        var layoutEngine = new ClassDiagramLayout(horizontalGap, verticalGap, canvasPaddingX, canvasPaddingY, groupGap);
        if (packageGapForGrouping >= 0) {
            layoutEngine.enableSubPackageGrouping(packageName, packageGapForGrouping);
        }
        var result = layoutEngine.layout(layers, relations);
```

And replace:

```java
        result.boxes().forEach(builder::add);
        result.dependencies().forEach(builder::add);
```

with (package groups must render first so boxes/arrows overlay them):

```java
        result.packageGroups().forEach(builder::add);
        result.boxes().forEach(builder::add);
        result.dependencies().forEach(builder::add);
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl . test -Dtest=ClassDiagramGeneratorTest`
Expected: PASS.

- [ ] **Step 5: Run full test suite**

Run: `mvn -q test`
Expected: ALL tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramGenerator.java src/test/java/com/sosuisha/classdiagram/ClassDiagramGeneratorTest.java
git commit -m "feat: expose enableSubPackageGrouping on ClassDiagramGenerator"
```

---

## Task 11: End-to-end SVG smoke test

**Files:**
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassDiagramGeneratorTest.java`

- [ ] **Step 1: Append failing test**

This test confirms `PackageGroupBox` SVG actually appears in the final document when the option is enabled. It uses the existing compiled fixtures under `com.sosuisha.classdiagram.analyzer.fixture` (which already includes a `sub` sub-package via `FixtureSubOrder`).

```java
    @Test
    void generateEmitsPackageGroupSvgWhenSubPackageGroupingEnabled() throws Exception {
        var classRoot = java.nio.file.Path.of("target/test-classes");
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(30)
            .generate(classRoot, "com.sosuisha.classdiagram.analyzer.fixture");
        assertTrue(svg.contains("data-diagram-draw=\"package-group\""),
            "SVG should contain at least one package-group element");
        assertTrue(svg.contains("data-diagram-draw-name=\"sub\""),
            "SVG should label the 'sub' sub-package");
    }

    @Test
    void generateDoesNotEmitPackageGroupSvgByDefault() throws Exception {
        var classRoot = java.nio.file.Path.of("target/test-classes");
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .generate(classRoot, "com.sosuisha.classdiagram.analyzer.fixture");
        assertFalse(svg.contains("data-diagram-draw=\"package-group\""),
            "SVG should NOT contain package-group when option is disabled");
    }
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `mvn -q test -Dtest=ClassDiagramGeneratorTest`
Expected: PASS.

If the `sub` label is missing, inspect the SVG — it may be that `FixtureSubOrder` lives in a different group, or that the relation set does not pull it in. Adjust the fixture-using assertion (still requires both `package-group` presence in one test and absence in the other).

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramGeneratorTest.java
git commit -m "test: end-to-end SVG smoke test for sub-package grouping"
```

---

## Task 12: Final regression sweep and cleanup

**Files:** none (verification only)

- [ ] **Step 1: Run the full test suite**

Run: `mvn -q test`
Expected: ALL tests pass.

- [ ] **Step 2: Inspect for warnings**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS with no compilation warnings.

- [ ] **Step 3: Verify no unused imports remain in modified files**

Open each modified `*.java` and confirm no dead imports. If any are dead, remove them.

Files to inspect:
- `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`
- `src/main/java/com/sosuisha/classdiagram/ClassDiagramGenerator.java`
- `src/main/java/com/sosuisha/classdiagram/LayoutResult.java`
- `src/main/java/com/sosuisha/classdiagram/PackageGroupBox.java`

- [ ] **Step 4: Commit any cleanup**

If cleanup was needed:

```bash
git add -u
git commit -m "chore: remove unused imports after sub-package grouping feature"
```

If nothing needed cleanup, skip.

---

## Self-Review Notes

- **Spec coverage:** Tasks 1–2 cover `PackageGroupBox` (spec §コンポーネント). Task 3 covers `LayoutResult` extension. Task 4 covers the API setter and disabled-by-default behavior (spec §オプション API + 非機能要件 #1/#2). Task 5 covers Steps A, C, D, E (single slot, vertical stacking by original layer, slot bounding rect). Task 6 covers root-package fixed-left placement (spec §要件 5 + Step B root固定). Task 7 covers multi-level labels (spec §要件 2). Task 8 covers the non-contiguous-layer regression (spec §要件 7). Task 9 covers barycenter ordering (spec Step B). Task 10 covers the generator API + SVG render order (spec §SVGBuilder 描画順). Task 11 covers end-to-end smoke test. Task 12 covers regression sweep.
- **Step F (ConnectedComponent 間配置):** Reused unchanged from the existing implementation — already handled by the legacy `groupGap`-based loop. The new algorithm chains `currentGroupX` similarly.
- **Step G (Dependency 生成):** Reused unchanged — existing `boxMap`-based loop runs before the new branch.
- **Placeholders:** None remain. All code is fully written.
- **Type consistency:** `enableSubPackageGrouping(String, int)` on layout, `enableSubPackageGrouping(int)` on generator. `LayoutResult.packageGroups()` accessor. `PackageGroupBox(label, x, y, width, height)`. Consistent across tasks.
