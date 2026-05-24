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
    void drawIncludesSixPathEdgesForTabbedShape() {
        var box = new PackageGroupBox("p", 10, 20, 100, 50);
        var svg = box.draw();
        // Tabbed shape outline has 6 segments: tab top, tab right, main top (right of tab),
        // main right, main bottom, full-height left.
        int pathCount = svg.split("<path", -1).length - 1;
        assertEquals(6, pathCount);
    }

    @Test
    void drawAppliesTranslationWhenPositioned() {
        var box = new PackageGroupBox("p", 10, 20, 100, 50);
        assertTrue(box.draw().contains("translate(10,20)"));
    }

    @Test
    void drawIncludesFillRectWhenFillColorSet() {
        var box = new PackageGroupBox("p", 0, 0, 100, 50, "#abcdef");
        var svg = box.draw();
        assertTrue(svg.contains("fill=\"#abcdef\""),
            "fill color must appear in SVG output");
    }

    @Test
    void drawOmitsFillRectWhenFillColorNull() {
        var box = new PackageGroupBox("p", 0, 0, 100, 50);
        var svg = box.draw();
        assertFalse(svg.contains("fill=\"#"),
            "no colored fill rect when fillColor is null (white label background is the only fill)");
    }

    @Test
    void drawOmitsCornerShadowByDefault() {
        var box = new PackageGroupBox("p", 0, 0, 100, 50, "#abcdef");
        var svg = box.draw();
        assertFalse(svg.contains("data-diagram-draw=\"package-shadow\""));
    }

    @Test
    void drawIncludesFiveCornerShadowLinesWhenPicturesque() {
        var box = new PackageGroupBox("p", 0, 0, 100, 50, "#abcdef", true);
        var svg = box.draw();
        assertEquals(5, svg.split("data-diagram-draw=\"package-shadow-solid\"", -1).length - 1);
        assertEquals(4, svg.split("data-diagram-draw=\"package-shadow-dashed\"", -1).length - 1);
        assertTrue(svg.contains("M 82,50 L 92.8,39.2"));
        assertTrue(svg.contains("M 92.8,39.2 L 100,32"));
        assertTrue(svg.contains("M 94,50 L 99.4,44.6"));
        assertTrue(svg.contains("M 99.4,44.6 L 100,44"));
        assertTrue(svg.contains("M 98,50 L 100,48"));
        assertTrue(svg.contains("stroke-dasharray=\"2,2\""));
    }
}
