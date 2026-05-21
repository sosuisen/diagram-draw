package com.sosuisha.classdiagram.analyzer;

import com.sosuisha.classdiagram.ClassStereotype;
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

    @Test
    void fromFullyQualifiedNameThrowsForTrailingDot() {
        assertThrows(IllegalArgumentException.class,
            () -> ClassInfo.fromFullyQualifiedName("com.example."));
    }

    @Test
    void fromFullyQualifiedNameThrowsForLeadingDot() {
        assertThrows(IllegalArgumentException.class,
            () -> ClassInfo.fromFullyQualifiedName(".Order"));
    }

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
}
