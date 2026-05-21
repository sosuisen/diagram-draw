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
    void layoutPlacesSiblingChildrenAtSameLayer() {
        // A→B→D, A→C: Kahn produces [[A],[B,C],[D]]
        // B and C are both direct children of A → same layer
        // D is child of B → one layer below B
        var a = ci("A"); var b = ci("B"); var c = ci("C"); var d = ci("D");
        var rels = List.of(rel(a, b), rel(b, d), rel(a, c));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20).layout(layers, rels);

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

    @Test
    void layoutPlacesInterfaceAboveImplementationForRealization() {
        var iface = ci("IFoo");
        var impl = ci("FooImpl");
        var rel = new ClassRelation(impl, iface, DependencyType.REALIZATION, false);
        var layers = new ClassRelationSorter().sort(List.of(rel));
        var result = new ClassDiagramLayout(20, 40, 20, 20).layout(layers, List.of(rel));

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
        var result = new ClassDiagramLayout(20, 40, 20, 20).layout(layers, rels);

        var boxImplA = result.boxes().stream().filter(b -> b.name().equals("FooImplA")).findFirst().orElseThrow();
        var boxImplB = result.boxes().stream().filter(b -> b.name().equals("FooImplB")).findFirst().orElseThrow();
        assertEquals(boxImplA.y(), boxImplB.y(),
            "Co-implementations of the same interface must be at the same layer");
    }
}
