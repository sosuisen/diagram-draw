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
}
