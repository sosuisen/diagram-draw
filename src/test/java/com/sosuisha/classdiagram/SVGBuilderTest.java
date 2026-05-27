package com.sosuisha.classdiagram;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SVGBuilderTest {

    @Test
    void buildReturnsSvgTag() {
        var result = new SVGBuilder(800, 600).build();
        assertTrue(result.contains("<svg"));
        assertTrue(result.contains("</svg>"));
    }

    @Test
    void buildContainsRectAfterAddingBox() {
        var builder = new SVGBuilder(800, 600);
        builder.add(new ClassBox("MyClass"));
        assertTrue(builder.build().contains("<rect"));
    }

    @Test
    void buildContainsTwoClassBoxesAfterAddingTwo() {
        var builder = new SVGBuilder(800, 600);
        builder.add(new ClassBox("MyClass"));
        builder.add(new ClassBox("OtherClass"));
        var result = builder.build();
        assertEquals(2, result.split("data-diagram-draw=\"box\"", -1).length - 1);
    }

    @Test
    void addReturnsSvgBuilder() {
        var builder = new SVGBuilder(800, 600);
        var result = builder.add(new ClassBox("MyClass"));
        assertSame(builder, result);
    }

    @Test
    void buildContainsXmlns() {
        var result = new SVGBuilder(800, 600).build();
        assertTrue(result.contains("xmlns=\"http://www.w3.org/2000/svg\""));
    }

    @Test
    void buildContainsWidth() {
        var result = new SVGBuilder(800, 600).build();
        assertTrue(result.contains("width=\"800\""));
    }

    @Test
    void buildContainsHeight() {
        var result = new SVGBuilder(800, 600).build();
        assertTrue(result.contains("height=\"600\""));
    }

    @Test
    void addThrowsWhenElementIsNull() {
        assertThrows(NullPointerException.class, () -> new SVGBuilder(800, 600).add(null));
    }

    @Test
    void throwsWhenWidthIsZero() {
        assertThrows(IllegalArgumentException.class, () -> new SVGBuilder(0, 600));
    }

    @Test
    void throwsWhenWidthIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new SVGBuilder(-1, 600));
    }

    @Test
    void throwsWhenHeightIsZero() {
        assertThrows(IllegalArgumentException.class, () -> new SVGBuilder(800, 0));
    }

    @Test
    void throwsWhenHeightIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new SVGBuilder(800, -1));
    }

    @Test
    void backgroundRectHasFillWhite() {
        var result = new SVGBuilder(800, 600).build();
        assertTrue(result.contains("fill=\"white\""));
    }

    @Test
    void backgroundRectHasDataAttribute() {
        var result = new SVGBuilder(800, 600).build();
        assertTrue(result.contains("data-diagram-draw=\"background\""));
    }

    @Test
    void backgroundRectHasCorrectWidth() {
        var result = new SVGBuilder(800, 600).build();
        // width="800" appears on both <svg> and background <rect>
        assertEquals(2, result.split("width=\"800\"", -1).length - 1);
    }

    @Test
    void backgroundRectHasCorrectHeight() {
        var result = new SVGBuilder(800, 600).build();
        // height="600" appears on both <svg> and background <rect>
        assertEquals(2, result.split("height=\"600\"", -1).length - 1);
    }

    @Test
    void fontFamilyReturnsSvgBuilder() {
        var builder = new SVGBuilder(800, 600);
        assertSame(builder, builder.fontFamily("HackGen"));
    }

    @Test
    void fontFamilyThrowsForNull() {
        assertThrows(NullPointerException.class,
                () -> new SVGBuilder(800, 600).fontFamily(null));
    }

    @Test
    void buildIncludesFontFamilyStyleWhenSet() {
        var result = new SVGBuilder(800, 600).fontFamily("HackGen").build();
        assertTrue(result.contains("font-family"));
        assertTrue(result.contains("HackGen"));
    }

    @Test
    void buildExcludesFontFamilyStyleByDefault() {
        var result = new SVGBuilder(800, 600).build();
        assertFalse(result.contains("font-family"));
    }
}
