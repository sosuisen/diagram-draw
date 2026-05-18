package com.sosuisha;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoxTest {

    @Test
    void boxCanBeCreatedWithCoordinatesAndSize() {
        var box = new Box(10, 20, 100, 50);
        assertNotNull(box);
    }

    @Test
    void xReturnsX() {
        var box = new Box(10, 20, 100, 50);
        assertEquals(10, box.x());
    }

    @Test
    void yReturnsY() {
        var box = new Box(10, 20, 100, 50);
        assertEquals(20, box.y());
    }

    @Test
    void widthReturnsWidth() {
        var box = new Box(10, 20, 100, 50);
        assertEquals(100, box.width());
    }

    @Test
    void heightReturnsHeight() {
        var box = new Box(10, 20, 100, 50);
        assertEquals(50, box.height());
    }

    @Test
    void drawReturnsSvgRectTag() {
        var box = new Box(10, 20, 100, 50);
        var result = box.draw();
        assertTrue(result.contains("<rect"));
        assertTrue(result.contains("/>"));
    }

    @Test
    void drawContainsCorrectX() {
        var box = new Box(10, 20, 100, 50);
        assertTrue(box.draw().contains("x=\"10\""));
    }

    @Test
    void drawContainsCorrectY() {
        var box = new Box(10, 20, 100, 50);
        assertTrue(box.draw().contains("y=\"20\""));
    }

    @Test
    void drawContainsCorrectWidth() {
        var box = new Box(10, 20, 100, 50);
        assertTrue(box.draw().contains("width=\"100\""));
    }

    @Test
    void drawContainsCorrectHeight() {
        var box = new Box(10, 20, 100, 50);
        assertTrue(box.draw().contains("height=\"50\""));
    }

    @Test
    void drawContainsFillNone() {
        var box = new Box(10, 20, 100, 50);
        assertTrue(box.draw().contains("fill=\"none\""));
    }

    @Test
    void drawContainsStrokeBlack() {
        var box = new Box(10, 20, 100, 50);
        assertTrue(box.draw().contains("stroke=\"black\""));
    }

    @Test
    void throwsWhenWidthIsZero() {
        assertThrows(IllegalArgumentException.class, () -> new Box(10, 20, 0, 50));
    }

    @Test
    void throwsWhenWidthIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new Box(10, 20, -1, 50));
    }

    @Test
    void throwsWhenHeightIsZero() {
        assertThrows(IllegalArgumentException.class, () -> new Box(10, 20, 100, 0));
    }

    @Test
    void throwsWhenHeightIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new Box(10, 20, 100, -1));
    }
}
