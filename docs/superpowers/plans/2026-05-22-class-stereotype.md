# Class Stereotype Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a general `ClassStereotype` mechanism to the pipeline so that interface classes are rendered with `«interface»` above the class name in the SVG output.

**Architecture:** New `ClassStereotype` enum flows through `ClassInfo` → `ClassBox`. `ClassRelationScanner` does a two-pass scan (first collect stereotypes, then build relations) so every `ClassInfo` carries the correct stereotype. `ClassDiagramLayout` passes the stereotype when constructing `ClassBox`. All existing constructors are kept with backward-compatible defaults.

**Tech Stack:** Java 25, JUnit 5, `java.lang.classfile` (Class-File API), `java.lang.reflect.AccessFlag`

**Spec:** `docs/superpowers/specs/2026-05-22-class-stereotype-design.md`

**Key constants** (from `ClassBox.java`):
- `FONT_SIZE = 14`, `ASCENT = 11`, `LINE_GAP = 4`, `PADDING_Y = 4`, `PADDING_X = 8`
- `CHAR_WIDTH = 8`, `MIN_WIDTH = 100`
- `compartmentHeight(n) = n * FONT_SIZE + (n-1) * LINE_GAP + PADDING_Y * 2`
  - n=1 → 22, n=2 → 40 (difference = 18)

---

### Task 1: Create `ClassStereotype` enum

**Files:**
- Create: `src/main/java/com/sosuisha/classdiagram/ClassStereotype.java`

- [ ] **Step 1: Create the enum**

```java
package com.sosuisha.classdiagram;

public enum ClassStereotype {
    NONE(""),
    INTERFACE("«interface»");

    private final String label;

    ClassStereotype(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/sosuisha/classdiagram/ClassStereotype.java
git commit -m "feat: add ClassStereotype enum (NONE, INTERFACE)"
```

---

### Task 2: Add `stereotype` field to `ClassInfo`

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/analyzer/ClassInfo.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/analyzer/ClassInfoTest.java`

- [ ] **Step 1: Write failing tests in `ClassInfoTest.java`**

Add these five tests at the end of the class (after the existing `fromFullyQualifiedNameThrowsForLeadingDot` test):

```java
@Test
void twoArgConstructorDefaultsToNoneStereotype() {
    var info = new ClassInfo("com.example", "Foo");
    assertEquals(ClassStereotype.NONE, info.stereotype());
}

@Test
void threeArgConstructorSetsStereotype() {
    var info = new ClassInfo("com.example", "IFoo", ClassStereotype.INTERFACE);
    assertEquals(ClassStereotype.INTERFACE, info.stereotype());
}

@Test
void fromFullyQualifiedNameDefaultsToNoneStereotype() {
    var info = ClassInfo.fromFullyQualifiedName("com.example.Foo");
    assertEquals(ClassStereotype.NONE, info.stereotype());
}

@Test
void fromFullyQualifiedNameWithStereotypeSetsStereotype() {
    var info = ClassInfo.fromFullyQualifiedName("com.example.IFoo", ClassStereotype.INTERFACE);
    assertEquals(ClassStereotype.INTERFACE, info.stereotype());
}

@Test
void threeArgConstructorThrowsForNullStereotype() {
    assertThrows(NullPointerException.class,
        () -> new ClassInfo("com.example", "Foo", null));
}
```

Also add `import com.sosuisha.classdiagram.ClassStereotype;` to the imports.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=ClassInfoTest -q`
Expected: `TESTS FAILED` — `stereotype()` method does not exist yet

- [ ] **Step 3: Rewrite `ClassInfo.java`**

Replace the entire file content with:

```java
package com.sosuisha.classdiagram.analyzer;

import com.sosuisha.classdiagram.ClassStereotype;
import java.util.Objects;

/**
 * クラスのパッケージ名・単純名・ステレオタイプを保持する識別子。
 *
 * @param packageName パッケージ名（例: {@code "com.sosuisha.classdiagram"}）
 * @param simpleName  単純名（パッケージを除く。例: {@code "Order"}）
 * @param stereotype  ステレオタイプ（例: {@code ClassStereotype.INTERFACE}）
 */
public record ClassInfo(String packageName, String simpleName, ClassStereotype stereotype) {

    /**
     * コンポーネントnullチェックを行うコンパクトコンストラクタ。
     *
     * @throws NullPointerException packageName、simpleName、またはstereotypeがnullの場合
     */
    public ClassInfo {
        Objects.requireNonNull(packageName, "packageName must not be null");
        Objects.requireNonNull(simpleName, "simpleName must not be null");
        Objects.requireNonNull(stereotype, "stereotype must not be null");
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
}
```

- [ ] **Step 4: Run all tests**

Run: `mvn test -q`
Expected: `BUILD SUCCESS` — existing tests compile because `new ClassInfo(pkg, name)` still works

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sosuisha/classdiagram/analyzer/ClassInfo.java
git add src/test/java/com/sosuisha/classdiagram/analyzer/ClassInfoTest.java
git commit -m "feat: add stereotype field to ClassInfo with backward-compatible 2-arg constructor"
```

---

### Task 3: Update `ClassBox` to render stereotype

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassBox.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassBoxTest.java`

- [ ] **Step 1: Write failing tests in `ClassBoxTest.java`**

Add these six tests at the end of the class (after the existing `sameClassBoxProducesSameOutput` test). Also add `import com.sosuisha.classdiagram.ClassStereotype;` to the imports.

```java
@Test
void defaultBoxHasNoneStereotype() {
    assertEquals(ClassStereotype.NONE, new ClassBox("MyClass").stereotype());
}

@Test
void interfaceBoxHasInterfaceStereotype() {
    var box = new ClassBox("IFoo", ClassStereotype.INTERFACE);
    assertEquals(ClassStereotype.INTERFACE, box.stereotype());
}

@Test
void interfaceBoxDrawContainsStereotypeLabel() {
    var result = new ClassBox("IFoo", ClassStereotype.INTERFACE).draw();
    assertTrue(result.contains("«interface»"));
}

@Test
void noneBoxDrawDoesNotContainStereotypeLabel() {
    assertFalse(new ClassBox("MyClass").draw().contains("«interface»"));
}

@Test
void interfaceBoxNameCompartmentIsTwoLinesTaller() {
    // NONE: compartmentHeight(1) = 22; INTERFACE: compartmentHeight(2) = 40; diff = 18
    int noneHeight = new ClassBox("MyClass").height();
    int ifaceHeight = new ClassBox("MyClass", ClassStereotype.INTERFACE).height();
    assertEquals(noneHeight + 18, ifaceHeight);
}

@Test
void interfaceBoxWidthAccountsForStereotypeLabel() {
    // «interface» = 11 chars: 11 * CHAR_WIDTH(8) + PADDING_X*2(16) = 104 > MIN_WIDTH(100)
    // "X" alone: 1 * 8 + 16 = 24 → clamped to MIN_WIDTH 100
    assertEquals(104, new ClassBox("X", ClassStereotype.INTERFACE).width());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=ClassBoxTest -q`
Expected: `TESTS FAILED` — `stereotype()` method does not exist yet

- [ ] **Step 3: Rewrite `ClassBox.java`**

Replace the entire file content with:

```java
package com.sosuisha.classdiagram;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * UMLクラスボックスを表すオブジェクト。
 *
 * <p>名前・フィールド・メソッドの3コンパートメントを持つ。
 * 内容（ステレオタイプ・名前・フィールド・メソッド）は構築後イミュータブル。
 * 描画位置は {@link #setPosition(int, int)} で設定する。
 * 幅・高さはコンテンツから自動計算される。
 * 輪郭線・区切り線はコンテンツのハッシュを種としたゆらぎを持つ。
 */
public final class ClassBox implements SvgElement {

    private static final int FONT_SIZE  = 14;
    private static final int ASCENT     = FONT_SIZE * 4 / 5;
    private static final int LINE_GAP   = 4;
    private static final int PADDING_X  = 8;
    private static final int PADDING_Y  = 4;
    private static final int CHAR_WIDTH = FONT_SIZE / 2 + 1;
    private static final int MIN_WIDTH  = 100;
    private static final double SKETCH_MAX_NONE = 1.0;
    private static final double SKETCH_MAX_SOME = 1.5;
    private static final double SKETCH_MAX_FULL = 2.0;

    private final ClassStereotype stereotype;
    private final String name;
    private final List<String> fields;
    private final List<String> methods;
    private int x = 0;
    private int y = 0;

    /**
     * フィールドとメソッドを指定せずにClassBoxを生成する（stereotype = NONE）。
     *
     * @param name クラス名
     * @throws NullPointerException nameがnullの場合
     */
    public ClassBox(String name) {
        this(name, ClassStereotype.NONE, List.of(), List.of());
    }

    /**
     * ステレオタイプを指定してClassBoxを生成する（フィールド・メソッドなし）。
     *
     * @param name       クラス名
     * @param stereotype ステレオタイプ
     * @throws NullPointerException nameまたはstereotypeがnullの場合
     */
    public ClassBox(String name, ClassStereotype stereotype) {
        this(name, stereotype, List.of(), List.of());
    }

    /**
     * フィールドとメソッドを指定してClassBoxを生成する（stereotype = NONE）。
     *
     * @param name クラス名
     * @param fields フィールド一覧
     * @param methods メソッド一覧
     * @throws NullPointerException name、fields、またはmethodsがnullの場合
     */
    public ClassBox(String name, List<String> fields, List<String> methods) {
        this(name, ClassStereotype.NONE, fields, methods);
    }

    /**
     * ClassBoxを生成する。
     *
     * @param name       クラス名
     * @param stereotype ステレオタイプ
     * @param fields     フィールド一覧
     * @param methods    メソッド一覧
     * @throws NullPointerException name、stereotype、fields、またはmethodsがnullの場合
     */
    public ClassBox(String name, ClassStereotype stereotype, List<String> fields, List<String> methods) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(stereotype, "stereotype must not be null");
        Objects.requireNonNull(fields, "fields must not be null");
        Objects.requireNonNull(methods, "methods must not be null");
        this.name = name;
        this.stereotype = stereotype;
        this.fields = List.copyOf(fields);
        this.methods = List.copyOf(methods);
    }

    /** @return ステレオタイプ */
    public ClassStereotype stereotype() { return stereotype; }

    /** @return クラス名 */
    public String name() { return name; }

    /** @return フィールド一覧（イミュータブル） */
    public List<String> fields() { return fields; }

    /** @return メソッド一覧（イミュータブル） */
    public List<String> methods() { return methods; }

    /** @return 描画位置のX座標 */
    public int x() { return x; }

    /** @return 描画位置のY座標 */
    public int y() { return y; }

    /**
     * 描画位置を設定する。
     *
     * @param x X座標
     * @param y Y座標
     */
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * コンテンツから自動計算した幅を返す。
     *
     * @return 幅（px）
     */
    public int width() {
        int maxLen = name.length();
        if (stereotype != ClassStereotype.NONE) {
            maxLen = Math.max(maxLen, stereotype.label().length());
        }
        for (var f : fields) {
            maxLen = Math.max(maxLen, f.length());
        }
        for (var m : methods) {
            maxLen = Math.max(maxLen, m.length());
        }
        return Math.max(MIN_WIDTH, maxLen * CHAR_WIDTH + PADDING_X * 2);
    }

    /**
     * コンテンツから自動計算した高さを返す。
     *
     * @return 高さ（px）
     */
    public int height() {
        int nameLines = stereotype == ClassStereotype.NONE ? 1 : 2;
        return compartmentHeight(nameLines)
             + compartmentHeight(fields.size())
             + compartmentHeight(methods.size());
    }

    /**
     * ClassBoxのSVG表現を返す。
     *
     * @return SVGのgタグ文字列
     */
    @Override
    public String draw() {
        int w = width();
        int h = height();
        var rng = createRandom();
        double sketchMax = sketchMax();
        var content = new StringBuilder();

        int ch = 0;
        ch += appendNameCompartment(content, w, ch);
        content.append(sketchyLine(0, ch, w, ch, rng, sketchMax));
        ch += appendTextCompartment(content, fields, w, ch);
        content.append(sketchyLine(0, ch, w, ch, rng, sketchMax));
        appendTextCompartment(content, methods, w, ch);

        var sb = new StringBuilder();
        if (x != 0 || y != 0) {
            sb.append("<g data-diagram-draw=\"box\" data-diagram-draw-type=\"class\" data-diagram-draw-name=\"%s\" transform=\"translate(%d,%d)\">".formatted(name, x, y));
        } else {
            sb.append("<g data-diagram-draw=\"box\" data-diagram-draw-type=\"class\" data-diagram-draw-name=\"%s\">".formatted(name));
        }
        sb.append(sketchyLine(0, 0, w, 0, rng, sketchMax));
        sb.append(sketchyLine(w, 0, w, h, rng, sketchMax));
        sb.append(sketchyLine(w, h, 0, h, rng, sketchMax));
        sb.append(sketchyLine(0, h, 0, 0, rng, sketchMax));
        sb.append(content);
        sb.append("</g>");
        return sb.toString();
    }

    private double sketchMax() {
        if (!fields.isEmpty() && !methods.isEmpty()) {
            return SKETCH_MAX_FULL;
        }
        if (!fields.isEmpty() || !methods.isEmpty()) {
            return SKETCH_MAX_SOME;
        }
        return SKETCH_MAX_NONE;
    }

    private Random createRandom() {
        return new Random(Objects.hash(name, fields, methods));
    }

    private static String sketchyLine(int x1, int y1, int x2, int y2, Random rng, double sketchMax) {
        double wobble = rng.nextDouble() * sketchMax * 2 - sketchMax;
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
        return "<path d=\"M %d,%d Q %.1f,%.1f %d,%d Q %.1f,%.1f %d,%d\" fill=\"none\" stroke=\"black\"/>".formatted(
            x1, y1, cp1x, cp1y, mx, my, cp2x, cp2y, x2, y2);
    }

    private static int compartmentHeight(int lineCount) {
        if (lineCount == 0) {
            return PADDING_Y * 2;
        }
        return lineCount * FONT_SIZE + (lineCount - 1) * LINE_GAP + PADDING_Y * 2;
    }

    private int appendNameCompartment(StringBuilder sb, int width, int startY) {
        if (stereotype == ClassStereotype.NONE) {
            int textY = startY + PADDING_Y + ASCENT;
            sb.append("<text x=\"%d\" y=\"%d\" font-size=\"%d\" text-anchor=\"middle\">%s</text>".formatted(
                width / 2, textY, FONT_SIZE, name));
            return compartmentHeight(1);
        }
        int stereoY = startY + PADDING_Y + ASCENT;
        int nameY = startY + PADDING_Y + ASCENT + FONT_SIZE + LINE_GAP;
        sb.append("<text x=\"%d\" y=\"%d\" font-size=\"%d\" text-anchor=\"middle\">%s</text>".formatted(
            width / 2, stereoY, FONT_SIZE - 2, stereotype.label()));
        sb.append("<text x=\"%d\" y=\"%d\" font-size=\"%d\" text-anchor=\"middle\">%s</text>".formatted(
            width / 2, nameY, FONT_SIZE, name));
        return compartmentHeight(2);
    }

    private int appendTextCompartment(StringBuilder sb, List<String> lines, int width, int startY) {
        for (int i = 0; i < lines.size(); i++) {
            int baseline = startY + PADDING_Y + ASCENT + i * (FONT_SIZE + LINE_GAP);
            sb.append("<text x=\"%d\" y=\"%d\" font-size=\"%d\">%s</text>".formatted(PADDING_X, baseline, FONT_SIZE, lines.get(i)));
        }
        return compartmentHeight(lines.size());
    }
}
```

- [ ] **Step 4: Run all tests**

Run: `mvn test -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sosuisha/classdiagram/ClassBox.java
git add src/test/java/com/sosuisha/classdiagram/ClassBoxTest.java
git commit -m "feat: ClassBox renders stereotype label above class name"
```

---

### Task 4: Pass stereotype from `ClassInfo` to `ClassBox` in `ClassDiagramLayout`

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java`

- [ ] **Step 1: Write failing test in `ClassDiagramLayoutTest.java`**

Add this import at the top of the file:
```java
import com.sosuisha.classdiagram.ClassStereotype;
```

Add this test after the existing `coImplementationsSameInterfaceLandAtSameLayer` test:

```java
@Test
void layoutPassesStereotypeToClassBox() {
    var iface = new ClassInfo(PKG, "IFoo", ClassStereotype.INTERFACE);
    var impl = new ClassInfo(PKG, "FooImpl"); // NONE
    var rel = new ClassRelation(impl, iface, DependencyType.REALIZATION, false);
    var layers = new ClassRelationSorter().sort(List.of(rel));
    var result = new ClassDiagramLayout(20, 40, 20, 20).layout(layers, List.of(rel));

    var ifaceBox = result.boxes().stream()
        .filter(b -> b.name().equals("IFoo")).findFirst().orElseThrow();
    var implBox = result.boxes().stream()
        .filter(b -> b.name().equals("FooImpl")).findFirst().orElseThrow();
    assertEquals(ClassStereotype.INTERFACE, ifaceBox.stereotype());
    assertEquals(ClassStereotype.NONE, implBox.stereotype());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ClassDiagramLayoutTest#layoutPassesStereotypeToClassBox -q`
Expected: `TESTS FAILED` — `ClassBox` is still created with `ClassStereotype.NONE` for all classes

- [ ] **Step 3: Update `ClassDiagramLayout.java` — Step 1 in `layout()`**

In `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java`, find line 60:

```java
                boxMap.put(info, new ClassBox(info.simpleName()));
```

Replace it with:

```java
                boxMap.put(info, new ClassBox(info.simpleName(), info.stereotype()));
```

- [ ] **Step 4: Run all tests**

Run: `mvn test -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java
git add src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java
git commit -m "feat: ClassDiagramLayout passes stereotype from ClassInfo to ClassBox"
```

---

### Task 5: Two-pass stereotype detection in `ClassRelationScanner`

**Files:**
- Modify: `src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java`
- Modify: `src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java`

- [ ] **Step 1: Write failing tests in `ClassRelationScannerTest.java`**

Add these two imports:
```java
import com.sosuisha.classdiagram.ClassStereotype;
import com.sosuisha.classdiagram.analyzer.ClassRelation;
```

Add these two tests after the existing `scanDetectsBothRealizationsForMultipleInterfaceImplementation` test:

```java
@Test
void scanAssignsInterfaceStereotypeToInterfaceTarget() {
    var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXTURE_PKG);
    var ifaceInfo = relations.stream()
        .filter(r -> r.type() == DependencyType.REALIZATION
                  && r.targetClassInfo().simpleName().equals("FixtureService"))
        .map(ClassRelation::targetClassInfo)
        .findFirst()
        .orElseThrow();
    assertEquals(ClassStereotype.INTERFACE, ifaceInfo.stereotype());
}

@Test
void scanAssignsNoneStereotypeToConcreteClassSource() {
    var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXTURE_PKG);
    var implInfo = relations.stream()
        .filter(r -> r.type() == DependencyType.REALIZATION
                  && r.sourceClassInfo().simpleName().equals("FixtureServiceImpl"))
        .map(ClassRelation::sourceClassInfo)
        .findFirst()
        .orElseThrow();
    assertEquals(ClassStereotype.NONE, implInfo.stereotype());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=ClassRelationScannerTest#scanAssignsInterfaceStereotypeToInterfaceTarget+scanAssignsNoneStereotypeToConcreteClassSource -q`
Expected: `TESTS FAILED` — all ClassInfo still have `NONE` stereotype

- [ ] **Step 3: Rewrite `ClassRelationScanner.java`**

Replace the entire file content with:

```java
package com.sosuisha.classdiagram.analyzer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Signature;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import com.sosuisha.classdiagram.ClassStereotype;
import com.sosuisha.classdiagram.DependencyType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * コンパイル済みクラスファイルをリフレクションで分析し、コンポジション・集約・実現関係を返すスキャナー。
 *
 * <p>指定パッケージおよびサブパッケージの {@code .class} ファイルを対象とする。
 * 2パス方式: 第1パスでステレオタイプを収集し、第2パスで関係を構築する。
 * {@link #scan(Path, String)} が {@link ClassRelation} のリストを返す。
 */
public class ClassRelationScanner {

    /**
     * 指定パッケージおよびサブパッケージ内のクラスを分析し、コンポジション・集約・実現関係を返す。
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

        var targetClassNames = collectClassNames(classRoot, packageDir);
        if (targetClassNames.isEmpty()) {
            return List.of();
        }

        return analyzeRelations(classRoot, targetClassNames);
    }

    private List<ClassRelation> analyzeRelations(Path classRoot, Set<String> targetClassNames) {
        var stereotypes = collectStereotypes(classRoot, targetClassNames);
        var relations = new ArrayList<ClassRelation>();

        for (var className : targetClassNames) {
            var classFilePath = classRoot.resolve(className.replace('.', '/') + ".class");
            ClassModel model;
            try {
                model = ClassFile.of().parse(classFilePath);
            } catch (IOException e) {
                continue;
            }

            var sourceInfo = ClassInfo.fromFullyQualifiedName(className,
                stereotypes.getOrDefault(className, ClassStereotype.NONE));
            var constructorParamTypeNames = collectConstructorParamTypeNames(model);

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

            for (var iface : model.interfaces()) {
                var ifaceName = internalNameToBinary(iface.asInternalName());
                if (targetClassNames.contains(ifaceName)) {
                    var ifaceInfo = ClassInfo.fromFullyQualifiedName(ifaceName,
                        stereotypes.getOrDefault(ifaceName, ClassStereotype.NONE));
                    relations.add(new ClassRelation(
                        sourceInfo,
                        ifaceInfo,
                        DependencyType.REALIZATION,
                        false
                    ));
                }
            }
        }

        return List.copyOf(relations);
    }

    private Map<String, ClassStereotype> collectStereotypes(Path classRoot, Set<String> targetClassNames) {
        var map = new HashMap<String, ClassStereotype>();
        for (var className : targetClassNames) {
            var classFilePath = classRoot.resolve(className.replace('.', '/') + ".class");
            try {
                var model = ClassFile.of().parse(classFilePath);
                map.put(className, model.flags().has(AccessFlag.INTERFACE)
                    ? ClassStereotype.INTERFACE
                    : ClassStereotype.NONE);
            } catch (IOException e) {
                map.put(className, ClassStereotype.NONE);
            }
        }
        return map;
    }

    private record FieldTarget(String targetClassName, boolean isMany) {}

    private static final Set<String> COLLECTION_TYPES = Set.of(
        "java.util.Collection",
        "java.util.List",
        "java.util.Set",
        "java.util.Queue",
        "java.util.Deque",
        "java.util.ArrayList",
        "java.util.LinkedList",
        "java.util.HashSet",
        "java.util.LinkedHashSet",
        "java.util.TreeSet",
        "java.util.ArrayDeque"
    );

    private static FieldTarget resolveFieldTarget(FieldModel field, Set<String> targetClassNames) {
        var fieldTypeName = classDescToBinaryName(field.fieldTypeSymbol());
        if (targetClassNames.contains(fieldTypeName)) {
            return new FieldTarget(fieldTypeName, false);
        }
        if (COLLECTION_TYPES.contains(fieldTypeName)) {
            var sigAttr = field.findAttribute(Attributes.signature());
            if (sigAttr.isPresent()) {
                var sig = sigAttr.get().asTypeSignature();
                if (sig instanceof Signature.ClassTypeSig classSig
                    && classSig.typeArgs().size() == 1
                    && classSig.typeArgs().get(0) instanceof Signature.TypeArg.Bounded bounded
                    && bounded.wildcardIndicator() == Signature.TypeArg.Bounded.WildcardIndicator.NONE
                    && bounded.boundType() instanceof Signature.ClassTypeSig argSig) {
                    var argName = internalNameToBinary(argSig.className());
                    if (targetClassNames.contains(argName)) {
                        return new FieldTarget(argName, true);
                    }
                }
            }
        }
        return null;
    }

    private static Set<String> collectConstructorParamTypeNames(ClassModel model) {
        var names = new HashSet<String>();
        for (var method : model.methods()) {
            if (!method.methodName().stringValue().equals("<init>")) continue;
            var desc = method.methodTypeSymbol();
            for (int i = 0; i < desc.parameterCount(); i++) {
                names.add(classDescToBinaryName(desc.parameterType(i)));
            }
        }
        return names;
    }

    private static String classDescToBinaryName(ClassDesc desc) {
        if (desc.isPrimitive() || desc.isArray()) {
            return desc.descriptorString();
        }
        var pkg = desc.packageName();
        return pkg.isEmpty() ? desc.displayName() : pkg + "." + desc.displayName();
    }

    private static String internalNameToBinary(String internalName) {
        return internalName.replace('/', '.');
    }

    private static Set<String> collectClassNames(Path classRoot, Path packageDir) {
        var names = new HashSet<String>();
        try (var stream = Files.walk(packageDir)) {
            stream
                .filter(p -> p.toString().endsWith(".class"))
                .filter(p -> !p.getFileName().toString().contains("$"))
                .map(p -> toFqn(classRoot.relativize(p)))
                .forEach(names::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return names;
    }

    private static String toFqn(Path relativePath) {
        var sb = new StringBuilder();
        for (var element : relativePath) {
            if (sb.length() > 0) sb.append('.');
            sb.append(element.toString());
        }
        var s = sb.toString();
        return s.endsWith(".class") ? s.substring(0, s.length() - 6) : s;
    }
}
```

- [ ] **Step 4: Run all tests**

Run: `mvn test -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sosuisha/classdiagram/analyzer/ClassRelationScanner.java
git add src/test/java/com/sosuisha/classdiagram/analyzer/ClassRelationScannerTest.java
git commit -m "feat: ClassRelationScanner detects interface stereotype via two-pass scan"
```

---

### Task 6: Update `docs/spec.md` and final verification

**Files:**
- Modify: `docs/spec.md`

- [ ] **Step 1: Run full test suite to confirm green**

Run: `mvn test -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 2: Update `docs/spec.md`**

In the **パッケージ構成** section, add `ClassStereotype.java` to the listing:

```
com.sosuisha.classdiagram
├── SvgElement.java              インターフェース: SVG描画要素
├── ClassStereotype.java         ステレオタイプ列挙型（NONE, INTERFACE）
├── ClassBox.java                クラスボックス（SVG要素）
...
```

In the **`DependencyType` 列挙型** section just after the intro paragraph, add a new **`ClassStereotype` 列挙型** section before the `ClassBox` section:

```markdown
### `ClassStereotype` 列挙型

クラスのステレオタイプ。

| 値 | 意味 | SVG表現 |
|----|------|---------|
| `NONE` | 通常クラス（ステレオタイプなし） | クラス名のみ表示 |
| `INTERFACE` | インタフェース | `«interface»`（font-size 12）をクラス名の上に表示 |

将来の拡張: `ABSTRACT`（抽象クラス）、`ENUM`（列挙型）など。
```

In the **`ClassBox` クラス** section, update the API block to include the new constructors:

```java
new ClassBox("MyClass")
new ClassBox("MyClass", ClassStereotype.INTERFACE)
new ClassBox("MyClass", List.of("id: Long"), List.of("getId(): Long"))
new ClassBox("MyClass", ClassStereotype.INTERFACE, List.of(), List.of())

ClassStereotype s = box.stereotype();
int w = box.width();
int h = box.height();
box.setPosition(50, 60);
String name = box.name();
```

In the **`ClassInfo` record** section, update the record definition:

```java
public record ClassInfo(String packageName, String simpleName, ClassStereotype stereotype)

// 完全修飾名から生成（stereotype = NONE）
ClassInfo.fromFullyQualifiedName("com.example.Order")
// → ClassInfo("com.example", "Order", ClassStereotype.NONE)

// 完全修飾名とステレオタイプから生成
ClassInfo.fromFullyQualifiedName("com.example.IService", ClassStereotype.INTERFACE)
// → ClassInfo("com.example", "IService", ClassStereotype.INTERFACE)

// 後方互換2引数コンストラクタ（stereotype = NONE）
new ClassInfo("com.example", "Order")
```

- [ ] **Step 3: Commit spec update**

```bash
git add docs/spec.md
git commit -m "docs: update spec.md for ClassStereotype and updated ClassInfo/ClassBox APIs"
```
