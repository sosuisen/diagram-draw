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
