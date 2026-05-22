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

    @Test
    void groupIndexDefaultsToZero() {
        var info = new ClassInfo("com.example", "Foo");
        assertEquals(0, info.groupIndex());
    }

    @Test
    void setGroupIndexUpdatesValue() {
        var info = new ClassInfo("com.example", "Foo");
        info.setGroupIndex(2);
        assertEquals(2, info.groupIndex());
    }

    @Test
    void equalsIgnoresGroupIndex() {
        var a = new ClassInfo("com.example", "Foo");
        var b = new ClassInfo("com.example", "Foo");
        b.setGroupIndex(99);
        assertEquals(a, b);
    }

    @Test
    void hashCodeIgnoresGroupIndex() {
        var a = new ClassInfo("com.example", "Foo");
        var b = new ClassInfo("com.example", "Foo");
        b.setGroupIndex(99);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void setGroupIndexThrowsForNegativeValue() {
        var info = new ClassInfo("com.example", "Foo");
        assertThrows(IllegalArgumentException.class, () -> info.setGroupIndex(-1));
    }
}
