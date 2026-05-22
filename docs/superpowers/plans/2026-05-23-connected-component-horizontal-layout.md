# Connected Component Horizontal Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 互いに依存のない複数の連結成分（グループ）を検出し、横並びにレイアウトする。

**Architecture:** `ClassInfo` に `groupIndex`（mutable int）を追加し、新クラス `ConnectedComponentSplitter` が Union-Find でグループ番号を計算して各インスタンスに書き込む。`ClassDiagramLayout` は `groupIndex` を使ってグループごとに独立した垂直スタックを構築し、`groupGap` ピクセルの間隔で横並びに配置する。パイプラインは scan → split → sort → layout → SVG の順。

**Tech Stack:** Java 25+, JUnit 5, Maven

---

## File Map

| 操作 | ファイル |
|------|---------|
| 変更 | `src/main/java/com/sosuisha/classdiagram/analyzer/ClassInfo.java` |
| 変更 | `src/test/java/com/sosuisha/classdiagram/analyzer/ClassInfoTest.java` |
| 新規 | `src/main/java/com/sosuisha/classdiagram/analyzer/ConnectedComponentSplitter.java` |
| 新規 | `src/test/java/com/sosuisha/classdiagram/analyzer/ConnectedComponentSplitterTest.java` |
| 変更 | `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java` |
| 変更 | `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java` |
| 変更 | `src/main/java/com/sosuisha/classdiagram/ClassDiagramGenerator.java` |
| 変更 | `src/test/java/com/sosuisha/classdiagram/ClassDiagramGeneratorTest.java` |
| 変更 | `src/test/java/com/sosuisha/classdiagram/DiagramDrawExampleTest.java` |
| 変更 | `docs/spec.md` |

---

## Task 1: Convert ClassInfo from record to class, add groupIndex

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/analyzer/ClassInfo.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/analyzer/ClassInfoTest.java`

- [ ] **Step 1: Write failing tests for groupIndex**

`ClassInfoTest` の末尾に追加（クラスの閉じ括弧の前）:

```java
@Test
void groupIndexDefaultsToZero() {
    var info = new ClassInfo("com.example", "Foo");
    assertEquals(0, info.groupIndex());
}

@Test
void setGroupIndexUpdatesValue() {
    var info = new ClassInfo("com.example", "Foo");
    info.setGroupIndex(2);
    assertEquals(2, info.groupIndex());
}

@Test
void equalsIgnoresGroupIndex() {
    var a = new ClassInfo("com.example", "Foo");
    var b = new ClassInfo("com.example", "Foo");
    b.setGroupIndex(99);
    assertEquals(a, b);
}

@Test
void hashCodeIgnoresGroupIndex() {
    var a = new ClassInfo("com.example", "Foo");
    var b = new ClassInfo("com.example", "Foo");
    b.setGroupIndex(99);
    assertEquals(a.hashCode(), b.hashCode());
}
```

- [ ] **Step 2: Run failing tests to confirm they fail**

```
mvn test -pl . -Dtest=ClassInfoTest -q
```

Expected: `groupIndexDefaultsToZero`, `setGroupIndexUpdatesValue`, `equalsIgnoresGroupIndex`, `hashCodeIgnoresGroupIndex` の4つが FAIL（`groupIndex()`, `setGroupIndex()` メソッドが存在しないためコンパイルエラー）。

- [ ] **Step 3: Replace ClassInfo.java (record → final class)**

`src/main/java/com/sosuisha/classdiagram/analyzer/ClassInfo.java` を以下で完全置換:

```java
package com.sosuisha.classdiagram.analyzer;

import com.sosuisha.classdiagram.ClassStereotype;
import java.util.Objects;

/**
 * クラスのパッケージ名・単純名・ステレオタイプを保持する識別子。
 *
 * <p>同一性は packageName + simpleName + stereotype で決まる。
 * groupIndex はレイアウト用メタデータであり同一性に含まれない。
 */
public final class ClassInfo {

    private final String packageName;
    private final String simpleName;
    private final ClassStereotype stereotype;
    private int groupIndex;

    /**
     * ClassInfoを生成する。
     *
     * @param packageName パッケージ名
     * @param simpleName  単純名
     * @param stereotype  ステレオタイプ
     * @throws NullPointerException packageName、simpleName、またはstereotypeがnullの場合
     */
    public ClassInfo(String packageName, String simpleName, ClassStereotype stereotype) {
        this.packageName = Objects.requireNonNull(packageName, "packageName must not be null");
        this.simpleName = Objects.requireNonNull(simpleName, "simpleName must not be null");
        this.stereotype = Objects.requireNonNull(stereotype, "stereotype must not be null");
        this.groupIndex = 0;
    }

    /**
     * ステレオタイプを {@code NONE} としてClassInfoを生成する後方互換コンストラクタ。
     *
     * @param packageName パッケージ名
     * @param simpleName  単純名
     * @throws NullPointerException packageNameまたはsimpleNameがnullの場合
     */
    public ClassInfo(String packageName, String simpleName) {
        this(packageName, simpleName, ClassStereotype.NONE);
    }

    /** @return パッケージ名 */
    public String packageName() { return packageName; }

    /** @return 単純名 */
    public String simpleName() { return simpleName; }

    /** @return ステレオタイプ */
    public ClassStereotype stereotype() { return stereotype; }

    /** @return グループインデックス（デフォルト0） */
    public int groupIndex() { return groupIndex; }

    /**
     * グループインデックスを設定する。{@link ConnectedComponentSplitter} が呼び出す。
     *
     * @param groupIndex グループインデックス（0以上）
     */
    public void setGroupIndex(int groupIndex) {
        this.groupIndex = groupIndex;
    }

    /**
     * 完全修飾名からClassInfoを生成する（stereotype = NONE）。
     *
     * @param fqn 完全修飾名（例: {@code "com.sosuisha.classdiagram.Order"}）
     * @return ClassInfoインスタンス（stereotype = NONE）
     * @throws NullPointerException fqnがnullの場合
     * @throws IllegalArgumentException fqnが完全修飾名でない場合（ドットを含まない）
     */
    public static ClassInfo fromFullyQualifiedName(String fqn) {
        return fromFullyQualifiedName(fqn, ClassStereotype.NONE);
    }

    /**
     * 完全修飾名とステレオタイプからClassInfoを生成する。
     *
     * @param fqn        完全修飾名（例: {@code "com.sosuisha.classdiagram.IService"}）
     * @param stereotype ステレオタイプ
     * @return ClassInfoインスタンス
     * @throws NullPointerException fqnまたはstereotypeがnullの場合
     * @throws IllegalArgumentException fqnが完全修飾名でない場合（ドットを含まない）
     */
    public static ClassInfo fromFullyQualifiedName(String fqn, ClassStereotype stereotype) {
        Objects.requireNonNull(fqn, "fqn must not be null");
        Objects.requireNonNull(stereotype, "stereotype must not be null");
        int dot = fqn.lastIndexOf('.');
        if (dot <= 0 || dot == fqn.length() - 1) {
            throw new IllegalArgumentException(
                "fqn must be a fully qualified name containing at least one '.': " + fqn);
        }
        return new ClassInfo(fqn.substring(0, dot), fqn.substring(dot + 1), stereotype);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClassInfo other)) return false;
        return packageName.equals(other.packageName)
            && simpleName.equals(other.simpleName)
            && stereotype == other.stereotype;
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName, simpleName, stereotype);
    }

    @Override
    public String toString() {
        return packageName + "." + simpleName + "[" + stereotype + ",g=" + groupIndex + "]";
    }
}
```

- [ ] **Step 4: Run all tests to verify existing + new tests pass**

```
mvn test -q
```

Expected: BUILD SUCCESS（全テスト PASS）。

- [ ] **Step 5: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/analyzer/ClassInfo.java
git add src/test/java/com/sosuisha/classdiagram/analyzer/ClassInfoTest.java
git commit -m "feat: convert ClassInfo to final class with mutable groupIndex field"
```

---

## Task 2: Create ConnectedComponentSplitter

**Files:**
- Create: `src/main/java/com/sosuisha/classdiagram/analyzer/ConnectedComponentSplitter.java`
- Create: `src/test/java/com/sosuisha/classdiagram/analyzer/ConnectedComponentSplitterTest.java`

- [ ] **Step 1: Write failing tests**

新規ファイル `src/test/java/com/sosuisha/classdiagram/analyzer/ConnectedComponentSplitterTest.java`:

```java
package com.sosuisha.classdiagram.analyzer;

import com.sosuisha.classdiagram.DependencyType;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ConnectedComponentSplitterTest {

    private final ConnectedComponentSplitter splitter = new ConnectedComponentSplitter();

    private static ClassRelation comp(ClassInfo src, ClassInfo tgt) {
        return new ClassRelation(src, tgt, DependencyType.COMPOSITION, false);
    }

    @Test
    void splitThrowsForNullInput() {
        assertThrows(NullPointerException.class, () -> splitter.split(null));
    }

    @Test
    void splitReturnsEmptyForEmptyInput() {
        var result = splitter.split(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void splitSingleComponentAllGetSameGroupIndex() {
        var a = new ClassInfo("p", "A");
        var b = new ClassInfo("p", "B");
        var c = new ClassInfo("p", "C");
        var relations = new ArrayList<>(List.of(comp(a, b), comp(b, c)));
        splitter.split(relations);
        assertEquals(a.groupIndex(), b.groupIndex());
        assertEquals(b.groupIndex(), c.groupIndex());
    }

    @Test
    void splitFirstComponentGetsGroupIndex0() {
        var a = new ClassInfo("p", "A"); var b = new ClassInfo("p", "B");
        var x = new ClassInfo("p", "X"); var y = new ClassInfo("p", "Y");
        var relations = new ArrayList<>(List.of(comp(a, b), comp(x, y)));
        splitter.split(relations);
        assertEquals(0, a.groupIndex());
        assertEquals(0, b.groupIndex());
        assertEquals(1, x.groupIndex());
        assertEquals(1, y.groupIndex());
    }

    @Test
    void splitTwoComponentsGetDistinctGroupIndices() {
        var a = new ClassInfo("p", "A"); var b = new ClassInfo("p", "B");
        var x = new ClassInfo("p", "X"); var y = new ClassInfo("p", "Y");
        var relations = new ArrayList<>(List.of(comp(a, b), comp(x, y)));
        splitter.split(relations);
        assertNotEquals(a.groupIndex(), x.groupIndex());
    }

    @Test
    void splitThreeComponentsGetDistinctGroupIndices() {
        var a = new ClassInfo("p", "A"); var b = new ClassInfo("p", "B");
        var x = new ClassInfo("p", "X"); var y = new ClassInfo("p", "Y");
        var p = new ClassInfo("p", "P"); var q = new ClassInfo("p", "Q");
        var relations = new ArrayList<>(List.of(comp(a, b), comp(x, y), comp(p, q)));
        splitter.split(relations);
        var groups = Set.of(a.groupIndex(), x.groupIndex(), p.groupIndex());
        assertEquals(3, groups.size());
    }

    @Test
    void splitReturnsSameListInstance() {
        var a = new ClassInfo("p", "A"); var b = new ClassInfo("p", "B");
        var relations = new ArrayList<>(List.of(comp(a, b)));
        var result = splitter.split(relations);
        assertSame(relations, result);
    }

    @Test
    void splitHandlesDuplicateClassInfoInstancesForSameClass() {
        // sourceClassInfo と targetClassInfo に同一論理クラスが別インスタンスで現れる場合
        var a1 = new ClassInfo("p", "A");
        var a2 = new ClassInfo("p", "A"); // a1 と equals だが別インスタンス
        var b  = new ClassInfo("p", "B");
        var c  = new ClassInfo("p", "C");
        // A→B, A→C (A が2回登場、別インスタンス)
        var relations = new ArrayList<>(List.of(comp(a1, b), comp(a2, c)));
        splitter.split(relations);
        // 全て同一グループ
        assertEquals(a1.groupIndex(), b.groupIndex());
        assertEquals(a2.groupIndex(), c.groupIndex());
        assertEquals(a1.groupIndex(), a2.groupIndex());
    }
}
```

- [ ] **Step 2: Run to confirm tests fail**

```
mvn test -pl . -Dtest=ConnectedComponentSplitterTest -q
```

Expected: コンパイルエラー（`ConnectedComponentSplitter` が存在しない）。

- [ ] **Step 3: Create ConnectedComponentSplitter.java**

新規ファイル `src/main/java/com/sosuisha/classdiagram/analyzer/ConnectedComponentSplitter.java`:

```java
package com.sosuisha.classdiagram.analyzer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@link ClassRelation} のリストから連結成分を検出し、
 * 各 {@link ClassInfo} に {@code groupIndex} を設定する。
 *
 * <p>辺の方向を無視した無向グラフとして Union-Find を実行する。
 * relations リスト先頭から最初に出現した成分が groupIndex=0 となる。
 */
public class ConnectedComponentSplitter {

    /**
     * relations 内の全 ClassInfo に groupIndex を設定し、relations をそのまま返す。
     *
     * @param relations 関係リスト
     * @return 同一の relations インスタンス（ClassInfo の groupIndex が書き換わっている）
     * @throws NullPointerException relations が null の場合
     */
    public List<ClassRelation> split(List<ClassRelation> relations) {
        Objects.requireNonNull(relations, "relations must not be null");
        if (relations.isEmpty()) return relations;

        // Union-Find (ClassInfo の equality ベース)
        Map<ClassInfo, ClassInfo> parent = new LinkedHashMap<>();
        for (var rel : relations) {
            parent.putIfAbsent(rel.sourceClassInfo(), rel.sourceClassInfo());
            parent.putIfAbsent(rel.targetClassInfo(), rel.targetClassInfo());
        }
        for (var rel : relations) {
            union(parent, rel.sourceClassInfo(), rel.targetClassInfo());
        }

        // 根ノードに groupIndex を割り当て（出現順）
        Map<ClassInfo, Integer> rootToGroup = new LinkedHashMap<>();
        int nextGroup = 0;
        for (var node : List.copyOf(parent.keySet())) {
            var root = find(parent, node);
            if (!rootToGroup.containsKey(root)) {
                rootToGroup.put(root, nextGroup++);
            }
        }

        // 全 ClassInfo インスタンス（重複含む）に groupIndex を設定
        Set<ClassInfo> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var rel : relations) {
            for (var info : List.of(rel.sourceClassInfo(), rel.targetClassInfo())) {
                if (visited.add(info)) {
                    var root = find(parent, info);
                    info.setGroupIndex(rootToGroup.get(root));
                }
            }
        }

        return relations;
    }

    private ClassInfo find(Map<ClassInfo, ClassInfo> parent, ClassInfo node) {
        var p = parent.getOrDefault(node, node);
        if (p.equals(node)) return node;
        var root = find(parent, p);
        parent.put(node, root);
        return root;
    }

    private void union(Map<ClassInfo, ClassInfo> parent, ClassInfo a, ClassInfo b) {
        var rootA = find(parent, a);
        var rootB = find(parent, b);
        if (!rootA.equals(rootB)) {
            parent.put(rootA, rootB);
        }
    }
}
```

- [ ] **Step 4: Run ConnectedComponentSplitter tests to verify pass**

```
mvn test -pl . -Dtest=ConnectedComponentSplitterTest -q
```

Expected: BUILD SUCCESS（全テスト PASS）。

- [ ] **Step 5: Run all tests to verify nothing broken**

```
mvn test -q
```

Expected: BUILD SUCCESS。

- [ ] **Step 6: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/analyzer/ConnectedComponentSplitter.java
git add src/test/java/com/sosuisha/classdiagram/analyzer/ConnectedComponentSplitterTest.java
git commit -m "feat: add ConnectedComponentSplitter to assign groupIndex via Union-Find"
```

---

## Task 3: Update ClassDiagramLayout for multi-group horizontal layout

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java`

- [ ] **Step 1: Write failing tests for 2-group layout**

`ClassDiagramLayoutTest` の末尾（クラスの閉じ括弧の前）に追加:

```java
@Test
void layoutArrangesTwoGroupsHorizontally() {
    var a = ci("A"); var b = ci("B");   // group 0 (default)
    var x = ci("X"); var y = ci("Y");   // group 1
    x.setGroupIndex(1); y.setGroupIndex(1);

    var rels = List.of(rel(a, b), rel(x, y));
    var layers = new ClassRelationSorter().sort(rels);
    var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);

    var boxA = result.boxes().stream().filter(bx -> bx.name().equals("A")).findFirst().orElseThrow();
    var boxX = result.boxes().stream().filter(bx -> bx.name().equals("X")).findFirst().orElseThrow();
    assertTrue(boxX.x() > boxA.x() + boxA.width(),
        "Group 1 must start to the right of group 0");
}

@Test
void layoutCanvasWidthGrowsWithLargerGroupGap() {
    var a = ci("A"); var b = ci("B");
    var x = ci("X"); var y = ci("Y");
    x.setGroupIndex(1); y.setGroupIndex(1);

    var rels = List.of(rel(a, b), rel(x, y));
    var layers = new ClassRelationSorter().sort(rels);
    var result60  = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);
    var result120 = new ClassDiagramLayout(20, 40, 20, 20, 120).layout(layers, rels);
    assertTrue(result120.canvasWidth() > result60.canvasWidth(),
        "Larger groupGap must produce wider canvas");
}

@Test
void layoutSingleGroupBehaviorUnchangedWithGroupGap() {
    var a = ci("A"); var b = ci("B");
    var rels = List.of(rel(a, b));
    var layers = new ClassRelationSorter().sort(rels);
    // groupGap は 1グループのときキャンバス幅に影響しない
    var result0   = new ClassDiagramLayout(20, 40, 20, 20, 0).layout(layers, rels);
    var result100 = new ClassDiagramLayout(20, 40, 20, 20, 100).layout(layers, rels);
    assertEquals(result0.canvasWidth(),  result100.canvasWidth());
    assertEquals(result0.canvasHeight(), result100.canvasHeight());
}
```

- [ ] **Step 2: Run to confirm new tests fail**

```
mvn test -pl . -Dtest=ClassDiagramLayoutTest -q
```

Expected: `layoutArrangesTwoGroupsHorizontally` 等がコンパイルエラー（5引数コンストラクタ未存在）。

- [ ] **Step 3: Replace ClassDiagramLayout.java**

`src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java` を以下で完全置換:

```java
package com.sosuisha.classdiagram;

import com.sosuisha.classdiagram.analyzer.ClassInfo;
import com.sosuisha.classdiagram.analyzer.ClassRelation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * クラス関係情報からレイアウト位置を計算するエンジン。
 *
 * <p>ClassRelationSorterのKahn法レイヤーを使用し、groupIndex が同じノードを
 * 1つのグループとして垂直スタックにまとめ、グループ間は groupGap ピクセルで
 * 水平方向に並べる。各レイヤーはグループ幅基準で中央揃えされる。
 */
public class ClassDiagramLayout {

    private final int horizontalGap;
    private final int verticalGap;
    private final int canvasPaddingX;
    private final int canvasPaddingY;
    private final int groupGap;

    /**
     * ClassDiagramLayoutを生成する。
     *
     * @param horizontalGap  同一レイヤー内のボックス間水平隙間（px）
     * @param verticalGap    レイヤー間の垂直隙間（px）
     * @param canvasPaddingX キャンバス左右の余白（px）
     * @param canvasPaddingY キャンバス上下の余白（px）
     * @param groupGap       連結成分グループ間の水平隙間（px）
     */
    public ClassDiagramLayout(int horizontalGap, int verticalGap,
                               int canvasPaddingX, int canvasPaddingY, int groupGap) {
        this.horizontalGap = horizontalGap;
        this.verticalGap = verticalGap;
        this.canvasPaddingX = canvasPaddingX;
        this.canvasPaddingY = canvasPaddingY;
        this.groupGap = groupGap;
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

        // Step 1: ClassInfo → ClassBox マップ作成（挿入順保持）
        Map<ClassInfo, ClassBox> boxMap = new LinkedHashMap<>();
        for (var layer : layers) {
            for (var info : layer) {
                boxMap.put(info, new ClassBox(info.simpleName(), info.stereotype()));
            }
        }

        // Step 2: groupIndex ごとにサブレイヤーを構築（空レイヤーは除去）
        var groupIndices = layers.stream()
            .flatMap(List::stream)
            .mapToInt(ClassInfo::groupIndex)
            .distinct()
            .sorted()
            .toArray();
        int numGroups = groupIndices.length;

        var groupSubLayers = new ArrayList<List<List<ClassInfo>>>(numGroups);
        for (int gi : groupIndices) {
            var subLayers = new ArrayList<List<ClassInfo>>();
            for (var layer : layers) {
                var filtered = layer.stream()
                    .filter(info -> info.groupIndex() == gi)
                    .collect(Collectors.toCollection(ArrayList::new));
                if (!filtered.isEmpty()) subLayers.add(filtered);
            }
            groupSubLayers.add(subLayers);
        }

        // Step 3: グループごとのコンテンツ幅・レイヤー幅・最大ボックス高さを計算
        int[] groupContentWidth = new int[numGroups];
        var groupLayerWidths = new ArrayList<int[]>(numGroups);
        var groupMaxBoxHeights = new ArrayList<int[]>(numGroups);

        for (int g = 0; g < numGroups; g++) {
            var subLayers = groupSubLayers.get(g);
            int numSub = subLayers.size();
            int[] layerWidths = new int[numSub];
            int[] maxBoxHeights = new int[numSub];

            for (int i = 0; i < numSub; i++) {
                var sub = subLayers.get(i);
                int w = 0, maxH = 0;
                for (var info : sub) {
                    var box = boxMap.get(info);
                    w += box.width();
                    maxH = Math.max(maxH, box.height());
                }
                if (sub.size() > 1) w += (sub.size() - 1) * horizontalGap;
                layerWidths[i] = w;
                maxBoxHeights[i] = maxH;
            }

            int contentW = 0;
            for (int w : layerWidths) contentW = Math.max(contentW, w);
            groupContentWidth[g] = contentW;
            groupLayerWidths.add(layerWidths);
            groupMaxBoxHeights.add(maxBoxHeights);
        }

        // Step 4: グループ開始X座標を決定
        int[] groupStartX = new int[numGroups];
        groupStartX[0] = canvasPaddingX;
        for (int g = 1; g < numGroups; g++) {
            groupStartX[g] = groupStartX[g - 1] + groupContentWidth[g - 1] + groupGap;
        }

        // Step 5: 各ボックスに座標を設定（グループ幅基準で中央揃え、上揃え）
        for (int g = 0; g < numGroups; g++) {
            var subLayers = groupSubLayers.get(g);
            int[] layerWidths = groupLayerWidths.get(g);
            int[] maxBoxHeights = groupMaxBoxHeights.get(g);
            int currentY = canvasPaddingY;

            for (int i = 0; i < subLayers.size(); i++) {
                var sub = subLayers.get(i);
                int startX = groupStartX[g] + (groupContentWidth[g] - layerWidths[i]) / 2;
                int x = startX;
                for (var info : sub) {
                    var box = boxMap.get(info);
                    box.setPosition(x, currentY);
                    x += box.width() + horizontalGap;
                }
                currentY += maxBoxHeights[i] + verticalGap;
            }
        }

        // Step 6: キャンバスサイズ計算
        int canvasWidth = groupStartX[numGroups - 1] + groupContentWidth[numGroups - 1] + canvasPaddingX;
        int maxGroupHeight = 0;
        for (int g = 0; g < numGroups; g++) {
            int[] maxBoxHeights = groupMaxBoxHeights.get(g);
            int h = 0;
            for (int mh : maxBoxHeights) h += mh;
            if (maxBoxHeights.length > 1) h += (maxBoxHeights.length - 1) * verticalGap;
            maxGroupHeight = Math.max(maxGroupHeight, h);
        }
        int canvasHeight = maxGroupHeight + 2 * canvasPaddingY;

        // Step 7: Dependency 生成
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
}
```

- [ ] **Step 4: Update all existing ClassDiagramLayoutTest constructor calls (4-arg → 5-arg)**

`ClassDiagramLayoutTest.java` 内の `new ClassDiagramLayout(20, 40, 20, 20)` を全て `new ClassDiagramLayout(20, 40, 20, 20, 60)` に置換する。

該当箇所（行番号は参考）:
- `layoutThrowsForNullLayers` の `new ClassDiagramLayout(20, 40, 20, 20)`
- `layoutThrowsForNullRelations` の `new ClassDiagramLayout(20, 40, 20, 20)`
- `layoutPlacesSiblingChildrenAtSameLayer` の `new ClassDiagramLayout(20, 40, 20, 20)`
- `layoutPositionsTopLayer` の `new ClassDiagramLayout(20, 40, 20, 20)`
- `layoutCentersLayer` の `new ClassDiagramLayout(20, 40, 20, 20)`
- `layoutCanvasSize` の `new ClassDiagramLayout(20, 40, 20, 20)`
- `layoutCreatesDependencies` の `new ClassDiagramLayout(20, 40, 20, 20)`
- `layoutPlacesInterfaceAboveImplementationForRealization` の `new ClassDiagramLayout(20, 40, 20, 20)`
- `coImplementationsSameInterfaceLandAtSameLayer` の `new ClassDiagramLayout(20, 40, 20, 20)`
- `layoutPassesStereotypeToClassBox` の `new ClassDiagramLayout(20, 40, 20, 20)`

全て `new ClassDiagramLayout(20, 40, 20, 20, 60)` に変更する。

- [ ] **Step 5: Run ClassDiagramLayoutTest to verify all pass**

```
mvn test -pl . -Dtest=ClassDiagramLayoutTest -q
```

Expected: BUILD SUCCESS（全テスト PASS）。

- [ ] **Step 6: Run all tests**

```
mvn test -q
```

Expected: BUILD SUCCESS。

- [ ] **Step 7: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java
git commit -m "feat: ClassDiagramLayout arranges independent groups horizontally with groupGap"
```

---

## Task 4: Update ClassDiagramGenerator and fix remaining broken constructors

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramGenerator.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassDiagramGeneratorTest.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/DiagramDrawExampleTest.java`

- [ ] **Step 1: Replace ClassDiagramGenerator.java**

`src/main/java/com/sosuisha/classdiagram/ClassDiagramGenerator.java` を以下で完全置換:

```java
package com.sosuisha.classdiagram;

import com.sosuisha.classdiagram.analyzer.ConnectedComponentSplitter;
import com.sosuisha.classdiagram.analyzer.ClassRelationScanner;
import com.sosuisha.classdiagram.analyzer.ClassRelationSorter;
import java.nio.file.Path;
import java.util.Objects;

/**
 * クラス図SVGを生成するファサード。
 *
 * <p>ClassRelationScanner → ConnectedComponentSplitter → ClassRelationSorter
 * → ClassDiagramLayout → SVGBuilder のパイプラインを一括実行し、SVG文字列を返す。
 */
public class ClassDiagramGenerator {

    private final int horizontalGap;
    private final int verticalGap;
    private final int canvasPaddingX;
    private final int canvasPaddingY;
    private final int groupGap;
    private String fontFamily = null;

    /**
     * ClassDiagramGeneratorを生成する。
     *
     * @param horizontalGap  同一レイヤー内のボックス間水平隙間（px）
     * @param verticalGap    レイヤー間の垂直隙間（px）
     * @param canvasPaddingX キャンバス左右の余白（px）
     * @param canvasPaddingY キャンバス上下の余白（px）
     * @param groupGap       連結成分グループ間の水平隙間（px）
     */
    public ClassDiagramGenerator(int horizontalGap, int verticalGap,
                                  int canvasPaddingX, int canvasPaddingY, int groupGap) {
        this.horizontalGap = horizontalGap;
        this.verticalGap = verticalGap;
        this.canvasPaddingX = canvasPaddingX;
        this.canvasPaddingY = canvasPaddingY;
        this.groupGap = groupGap;
    }

    /**
     * テキストに適用するフォントファミリーを設定する。
     *
     * @param fontFamily フォントファミリー名（例: "HackGen"）
     * @return このジェネレーター自身（メソッドチェーン用）
     * @throws NullPointerException fontFamilyがnullの場合
     */
    public ClassDiagramGenerator fontFamily(String fontFamily) {
        Objects.requireNonNull(fontFamily, "fontFamily must not be null");
        this.fontFamily = fontFamily;
        return this;
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
            var builder = new SVGBuilder(canvasPaddingX * 2, canvasPaddingY * 2);
            if (fontFamily != null) builder.fontFamily(fontFamily);
            return builder.build();
        }

        new ConnectedComponentSplitter().split(relations);
        var layers = new ClassRelationSorter().sort(relations);
        var result = new ClassDiagramLayout(horizontalGap, verticalGap, canvasPaddingX, canvasPaddingY, groupGap)
                         .layout(layers, relations);
        var builder = new SVGBuilder(result.canvasWidth(), result.canvasHeight());
        if (fontFamily != null) builder.fontFamily(fontFamily);
        result.boxes().forEach(builder::add);
        result.dependencies().forEach(builder::add);
        return builder.build();
    }
}
```

- [ ] **Step 2: Update ClassDiagramGeneratorTest.java — fix constructor calls**

`ClassDiagramGeneratorTest.java` 内の全 `new ClassDiagramGenerator(20, 40, 20, 20)` を `new ClassDiagramGenerator(20, 40, 20, 20, 60)` に置換する。

該当箇所:
- `generateThrowsForNullClassRoot`
- `generateThrowsForNullPackageName`
- `generateReturnsEmptySvgForNonExistentPackage`
- `fontFamilyThrowsForNull`
- `generateIncludesFontFamilyWhenSet`
- `generateProducesFullSvgForFixturePackage`
- `generateIncludesStereotypeLabelForInterfaces`

- [ ] **Step 3: Update DiagramDrawExampleTest.java — fix constructor calls**

`DiagramDrawExampleTest.java` 内の以下を置換する:

```java
// 変更前
new ClassDiagramGenerator(30, 50, 30, 30)
// 変更後
new ClassDiagramGenerator(30, 50, 30, 30, 60)
```

`outputSamplesComExampleClassDiagramSvgFile` と `outputGeneratedClassDiagramSvgFile` の2箇所。

また、`outputLongestPathReassignmentExampleSvgFile` 内の:
```java
// 変更前
var result = new ClassDiagramLayout(30, 50, 30, 30).layout(layers, relations);
// 変更後
var result = new ClassDiagramLayout(30, 50, 30, 30, 60).layout(layers, relations);
```

- [ ] **Step 4: Run all tests to verify everything passes**

```
mvn test -q
```

Expected: BUILD SUCCESS（全テスト PASS）。

- [ ] **Step 5: Commit**

```
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramGenerator.java
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramGeneratorTest.java
git add src/test/java/com/sosuisha/classdiagram/DiagramDrawExampleTest.java
git commit -m "feat: wire ConnectedComponentSplitter into ClassDiagramGenerator pipeline"
```

---

## Task 5: Update spec.md

**Files:**
- Modify: `docs/spec.md`

- [ ] **Step 1: Update ClassInfo section in spec.md**

`docs/spec.md` の `### ClassInfo record` セクションのヘッダを `### ClassInfo クラス` に変更し、recordからclassになったことを反映する。APIブロックを以下に更新:

```java
public ClassInfo(String packageName, String simpleName)
public ClassInfo(String packageName, String simpleName, ClassStereotype stereotype)

// 完全修飾名から生成（stereotype = NONE, groupIndex = 0）
ClassInfo.fromFullyQualifiedName("com.example.Order")
ClassInfo.fromFullyQualifiedName("com.example.IService", ClassStereotype.INTERFACE)

int idx = info.groupIndex();      // デフォルト 0
info.setGroupIndex(1);            // ConnectedComponentSplitter が呼び出す
```

また、`equals`/`hashCode` は `packageName + simpleName + stereotype` のみ（`groupIndex` 除外）という説明を追加する。

- [ ] **Step 2: Add ConnectedComponentSplitter section**

`### ClassRelationSorter クラス` セクションの前に以下を追加:

```markdown
### `ConnectedComponentSplitter` クラス

`ClassRelation` のリストを走査して無向グラフの連結成分を検出し、
各 `ClassInfo` の `groupIndex` を設定する。

```java
List<ClassRelation> split(List<ClassRelation> relations)
```

- relations をそのまま返す（ClassInfo の groupIndex が書き換わっている）
- 先頭から最初に出現した成分が groupIndex=0
- `@throws NullPointerException` relations が null の場合
```

- [ ] **Step 3: Update ClassDiagramLayout section**

コンストラクタを5引数に更新し、レイアウトアルゴリズムのセクションを更新:

```java
public ClassDiagramLayout(int horizontalGap, int verticalGap,
                           int canvasPaddingX, int canvasPaddingY, int groupGap)

LayoutResult layout(List<List<ClassInfo>> layers, List<ClassRelation> relations)
```

レイアウトアルゴリズムの説明を以下に更新:
1. `groupIndex` ごとにサブレイヤーを構築
2. グループごとにコンテンツ幅・高さを計算
3. グループを `groupGap` ピクセルの間隔で横並び配置（左→右）
4. 各グループ内でレイヤーを中央揃え
5. 全グループを上揃え
6. キャンバス幅 = 全グループ幅 + `groupGap × (グループ数-1)` + `canvasPaddingX × 2`
7. キャンバス高さ = 最も高いグループ + `canvasPaddingY × 2`

- [ ] **Step 4: Update ClassDiagramGenerator section**

コンストラクタを5引数に更新し、内部パイプラインに `ConnectedComponentSplitter.split()` を追加:

```
ClassRelationScanner.scan()
        ↓ List<ClassRelation>
ConnectedComponentSplitter.split()
        ↓ List<ClassRelation>  (groupIndex 設定済み)
ClassRelationSorter.sort()
        ↓ List<List<ClassInfo>>
ClassDiagramLayout.layout()
        ↓ LayoutResult
SVGBuilder.build()
        ↓ String (SVG)
```

また、「今後の予定」の「複数のグラフの分離」を削除する（実装済みのため）。

- [ ] **Step 5: Update usage examples in spec.md**

ファサード使用例と手動パイプライン例のコンストラクタ引数を5引数に更新し、手動パイプラインに `ConnectedComponentSplitter.split()` を追加する。

- [ ] **Step 6: Commit**

```
git add docs/spec.md
git commit -m "docs: update spec.md for groupIndex, ConnectedComponentSplitter, 5-arg constructors"
```
