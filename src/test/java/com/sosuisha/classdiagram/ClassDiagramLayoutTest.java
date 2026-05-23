package com.sosuisha.classdiagram;

import com.sosuisha.classdiagram.analyzer.ClassInfo;
import com.sosuisha.classdiagram.analyzer.ClassRelation;
import com.sosuisha.classdiagram.analyzer.ClassRelationSorter;
import com.sosuisha.classdiagram.ClassStereotype;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
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
            () -> new ClassDiagramLayout(20, 40, 20, 20, 60).layout(null, List.of()));
    }

    @Test
    void layoutThrowsForNullRelations() {
        assertThrows(NullPointerException.class,
            () -> new ClassDiagramLayout(20, 40, 20, 20, 60).layout(List.of(), null));
    }

    @Test
    void layoutPlacesSiblingChildrenAtSameLayer() {
        // A→B→D, A→C: Kahn produces [[A],[B,C],[D]]
        // B and C are both direct children of A → same layer
        // D is child of B → one layer below B
        var a = ci("A"); var b = ci("B"); var c = ci("C"); var d = ci("D");
        var rels = List.of(rel(a, b), rel(b, d), rel(a, c));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);

        var boxA = result.boxes().stream().filter(bx -> bx.name().equals("A")).findFirst().orElseThrow();
        var boxB = result.boxes().stream().filter(bx -> bx.name().equals("B")).findFirst().orElseThrow();
        var boxC = result.boxes().stream().filter(bx -> bx.name().equals("C")).findFirst().orElseThrow();
        var boxD = result.boxes().stream().filter(bx -> bx.name().equals("D")).findFirst().orElseThrow();

        // B and C are siblings (both direct children of A) → same layer
        assertEquals(boxB.y(), boxC.y());
        // Layers flow top to bottom: A < B/C < D
        assertTrue(boxA.y() < boxB.y());
        assertTrue(boxB.y() < boxD.y());
    }

    @Test
    void layoutPositionsTopLayer() {
        var a = ci("A"); var b = ci("B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);

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
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);

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
        var result = new ClassDiagramLayout(20, 40, padding, padding, 60).layout(layers, rels);

        assertTrue(result.canvasWidth() >= 2 * padding);
        assertTrue(result.canvasHeight() >= 2 * padding);
    }

    @Test
    void layoutCreatesDependencies() {
        var a = ci("A"); var b = ci("B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);

        assertEquals(1, result.dependencies().size());
        assertEquals(DependencyType.COMPOSITION, result.dependencies().get(0).type());
    }

    @Test
    void layoutPlacesInterfaceAboveImplementationForRealization() {
        var iface = ci("IFoo");
        var impl = ci("FooImpl");
        var rel = new ClassRelation(impl, iface, DependencyType.REALIZATION, false);
        var layers = new ClassRelationSorter().sort(List.of(rel));
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, List.of(rel));

        var ifaceBox = result.boxes().stream()
            .filter(b -> b.name().equals("IFoo")).findFirst().orElseThrow();
        var implBox = result.boxes().stream()
            .filter(b -> b.name().equals("FooImpl")).findFirst().orElseThrow();
        assertTrue(ifaceBox.y() < implBox.y(),
            "Interface y=%d must be less than impl y=%d".formatted(ifaceBox.y(), implBox.y()));
    }

    @Test
    void coImplementationsSameInterfaceLandAtSameLayer() {
        // IFoo (interface), FooImplA (implements IFoo + owns LeafA), FooImplB (implements IFoo only)
        // Kahn: [[IFoo], [FooImplA, FooImplB], [LeafA]] — both impls are direct children of IFoo
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
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);

        var boxImplA = result.boxes().stream().filter(b -> b.name().equals("FooImplA")).findFirst().orElseThrow();
        var boxImplB = result.boxes().stream().filter(b -> b.name().equals("FooImplB")).findFirst().orElseThrow();
        assertEquals(boxImplA.y(), boxImplB.y(),
            "Co-implementations of the same interface must be at the same layer");
    }

    @Test
    void layoutPassesStereotypeToClassBox() {
        var iface = new ClassInfo(PKG, "IFoo", ClassStereotype.INTERFACE);
        var impl = new ClassInfo(PKG, "FooImpl"); // NONE
        var rel = new ClassRelation(impl, iface, DependencyType.REALIZATION, false);
        var layers = new ClassRelationSorter().sort(List.of(rel));
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, List.of(rel));

        var ifaceBox = result.boxes().stream()
            .filter(b -> b.name().equals("IFoo")).findFirst().orElseThrow();
        var implBox = result.boxes().stream()
            .filter(b -> b.name().equals("FooImpl")).findFirst().orElseThrow();
        assertEquals(ClassStereotype.INTERFACE, ifaceBox.stereotype());
        assertEquals(ClassStereotype.NONE, implBox.stereotype());
    }

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
        // groupGap is unused when there is only 1 group
        var result0   = new ClassDiagramLayout(20, 40, 20, 20, 0).layout(layers, rels);
        var result100 = new ClassDiagramLayout(20, 40, 20, 20, 100).layout(layers, rels);
        assertEquals(result0.canvasWidth(),  result100.canvasWidth());
        assertEquals(result0.canvasHeight(), result100.canvasHeight());
    }

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
    void layoutSuppressesImplementerDependencyArrowWhenInterfaceHasSame() {
        var iface = new ClassInfo(PKG, "IFoo", ClassStereotype.INTERFACE);
        var impl = new ClassInfo(PKG, "FooImpl");
        var target = ci("Target");
        target.setGroupIndex(1);

        // Both interface and impl declare dependency on Target (cross-group)
        iface.addDependencyTargetFqn(PKG + ".Target");
        impl.addDependencyTargetFqn(PKG + ".Target");

        var rels = List.of(new ClassRelation(impl, iface, DependencyType.REALIZATION, false));
        var layers = new ArrayList<>(new ClassRelationSorter().sort(rels));
        layers.add(List.of(target));

        var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);

        assertTrue(result.dependencies().stream().anyMatch(d ->
            d.source().name().equals("IFoo") && d.target().name().equals("Target")
            && d.type() == DependencyType.DEPENDENCY),
            "IFoo → Target DEPENDENCY arrow must be drawn");
        assertFalse(result.dependencies().stream().anyMatch(d ->
            d.source().name().equals("FooImpl") && d.target().name().equals("Target")
            && d.type() == DependencyType.DEPENDENCY),
            "FooImpl → Target must be suppressed because IFoo already has the same DEPENDENCY");
    }

    @Test
    void layoutKeepsImplementerDependencyArrowWhenInterfaceLacksIt() {
        var iface = new ClassInfo(PKG, "IFoo", ClassStereotype.INTERFACE);
        var impl = new ClassInfo(PKG, "FooImpl");
        var target = ci("Target");
        target.setGroupIndex(1);

        // Only impl depends on Target; interface does NOT
        impl.addDependencyTargetFqn(PKG + ".Target");

        var rels = List.of(new ClassRelation(impl, iface, DependencyType.REALIZATION, false));
        var layers = new ArrayList<>(new ClassRelationSorter().sort(rels));
        layers.add(List.of(target));

        var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);

        assertTrue(result.dependencies().stream().anyMatch(d ->
            d.source().name().equals("FooImpl") && d.target().name().equals("Target")
            && d.type() == DependencyType.DEPENDENCY),
            "FooImpl → Target must be kept when IFoo does not have the same DEPENDENCY");
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

    @Test
    void minimizeCrossingsReducesCrossings() {
        // Layer 0: [A, B]  A at index 0, B at index 1
        // Layer 1: [C, D]  A→D and B→C → crossing before minimization
        // After minimization: bary(D)=0 (parent A at 0), bary(C)=1 (parent B at 1)
        // → D must be left of C
        var a = ci("A"); var b = ci("B"); var c = ci("C"); var d = ci("D");
        var layers = List.of(List.of(a, b), List.of(c, d));
        var rels = List.of(
            new ClassRelation(a, d, DependencyType.COMPOSITION, false),
            new ClassRelation(b, c, DependencyType.COMPOSITION, false)
        );
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);

        var boxC = result.boxes().stream().filter(bx -> bx.name().equals("C")).findFirst().orElseThrow();
        var boxD = result.boxes().stream().filter(bx -> bx.name().equals("D")).findFirst().orElseThrow();
        assertTrue(boxD.x() < boxC.x(),
            "D (parent=A at idx 0) must be left of C (parent=B at idx 1) after crossing minimization");
    }

    @Test
    void minimizeCrossingsNoNeighborNodeKeepsRelativeOrder() {
        // Layer 0: [B, A]  B at index 0, A at index 1
        // Layer 1: [C, D, E]  COMP(A,C) → bary(C)=1, COMP(B,D) → bary(D)=0, E has no parent → bary(E)=2 (current idx)
        // After minimization: [D, C, E]
        var a = ci("A"); var b = ci("B"); var c = ci("C"); var d = ci("D"); var e = ci("E");
        var layers = List.of(List.of(b, a), List.of(c, d, e));
        var rels = List.of(
            new ClassRelation(a, c, DependencyType.COMPOSITION, false),
            new ClassRelation(b, d, DependencyType.COMPOSITION, false)
        );
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);

        var boxC = result.boxes().stream().filter(bx -> bx.name().equals("C")).findFirst().orElseThrow();
        var boxD = result.boxes().stream().filter(bx -> bx.name().equals("D")).findFirst().orElseThrow();
        var boxE = result.boxes().stream().filter(bx -> bx.name().equals("E")).findFirst().orElseThrow();
        assertTrue(boxD.x() < boxC.x(), "D (bary=0) must be left of C (bary=1)");
        assertTrue(boxC.x() < boxE.x(), "E (no neighbor, bary=current idx=2) must be rightmost");
    }

    @Test
    void minimizeCrossingsRealizationReducesCrossings() {
        // Layer 0: [IA, IB]  IA at index 0, IB at index 1 (interfaces)
        // Layer 1: [ImplB, ImplA]  ImplA implements IA, ImplB implements IB → crossing!
        // After minimization: bary(ImplA)=0, bary(ImplB)=1 → [ImplA, ImplB]
        var ia = new ClassInfo(PKG, "IA", ClassStereotype.INTERFACE);
        var ib = new ClassInfo(PKG, "IB", ClassStereotype.INTERFACE);
        var implA = ci("ImplA");
        var implB = ci("ImplB");
        var layers = List.of(List.of(ia, ib), List.of(implB, implA));
        var rels = List.of(
            new ClassRelation(implA, ia, DependencyType.REALIZATION, false),
            new ClassRelation(implB, ib, DependencyType.REALIZATION, false)
        );
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);

        var boxImplA = result.boxes().stream().filter(bx -> bx.name().equals("ImplA")).findFirst().orElseThrow();
        var boxImplB = result.boxes().stream().filter(bx -> bx.name().equals("ImplB")).findFirst().orElseThrow();
        assertTrue(boxImplA.x() < boxImplB.x(),
            "ImplA (parent=IA at idx 0) must be left of ImplB (parent=IB at idx 1)");
    }

    @Test
    void coImplementorsStayAdjacentWhenSameBarycenter() {
        // Layer 0: [A, IFoo, B]  A at 0, IFoo at 1, B at 2
        // Layer 1: [ImplFoo1, X, ImplFoo2]
        //   ImplFoo1 → IFoo (REALIZATION)         bary = 1
        //   COMP(A, X) and COMP(B, X)             bary(X) = (0+2)/2 = 1
        //   ImplFoo2 → IFoo (REALIZATION)         bary = 1
        // Without grouping: all bary = 1, stable sort keeps initial order
        //   → [ImplFoo1, X, ImplFoo2]  (co-implementors NOT adjacent)
        // With grouping: IFoo-group [ImplFoo1, ImplFoo2] and X-group [X]
        //   → [ImplFoo1, ImplFoo2, X]  (co-implementors adjacent)
        var a = ci("A"); var b = ci("B"); var x = ci("X");
        var iFoo = new ClassInfo(PKG, "IFoo", ClassStereotype.INTERFACE);
        var implFoo1 = ci("ImplFoo1"); var implFoo2 = ci("ImplFoo2");

        var layers = List.of(List.of(a, iFoo, b), List.of(implFoo1, x, implFoo2));
        var rels = List.of(
            new ClassRelation(implFoo1, iFoo, DependencyType.REALIZATION, false),
            new ClassRelation(implFoo2, iFoo, DependencyType.REALIZATION, false),
            new ClassRelation(a, x, DependencyType.COMPOSITION, false),
            new ClassRelation(b, x, DependencyType.COMPOSITION, false)
        );
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);

        var box1 = result.boxes().stream().filter(bx -> bx.name().equals("ImplFoo1")).findFirst().orElseThrow();
        var box2 = result.boxes().stream().filter(bx -> bx.name().equals("ImplFoo2")).findFirst().orElseThrow();

        // ImplFoo1 and ImplFoo2 have identical name length → identical widths.
        // Adjacent ⇔ |x1 - x2| == width + horizontalGap(=20).
        int dist = Math.abs(box1.x() - box2.x());
        int adjacentDist = box1.width() + 20;
        assertEquals(adjacentDist, dist,
            "ImplFoo1 and ImplFoo2 (co-implementors of IFoo) must be adjacent in the same layer");
    }
}
