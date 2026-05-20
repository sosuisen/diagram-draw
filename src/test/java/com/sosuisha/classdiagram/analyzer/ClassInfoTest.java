package com.sosuisha.classdiagram.analyzer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassInfoTest {

    @Test
    void fromFullyQualifiedNameSplitsCorrectly() {
        var info = ClassInfo.fromFullyQualifiedName("com.sosuisha.classdiagram.Order");
        assertEquals("com.sosuisha.classdiagram", info.packageName());
        assertEquals("Order", info.simpleName());
    }

    @Test
    void fromFullyQualifiedNameThrowsForNull() {
        assertThrows(NullPointerException.class,
            () -> ClassInfo.fromFullyQualifiedName(null));
    }

    @Test
    void fromFullyQualifiedNameThrowsForUnqualifiedName() {
        assertThrows(IllegalArgumentException.class,
            () -> ClassInfo.fromFullyQualifiedName("Order"));
    }

    @Test
    void fromFullyQualifiedNameThrowsForEmptyString() {
        assertThrows(IllegalArgumentException.class,
            () -> ClassInfo.fromFullyQualifiedName(""));
    }
}
