package com.sosuisha.classdiagram.analyzer;

import com.sosuisha.classdiagram.DependencyType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassRelationTest {

    @Test
    void storesSourceClass() {
        var r = new ClassRelation(
            ClassInfo.fromFullyQualifiedName("com.example.Order"),
            ClassInfo.fromFullyQualifiedName("com.example.Item"),
            DependencyType.COMPOSITION,
            true
        );
        assertEquals("com.example", r.sourceClassInfo().packageName());
        assertEquals("Order", r.sourceClassInfo().simpleName());
    }

    @Test
    void storesTargetClass() {
        var r = new ClassRelation(
            ClassInfo.fromFullyQualifiedName("com.example.Order"),
            ClassInfo.fromFullyQualifiedName("com.example.Item"),
            DependencyType.COMPOSITION,
            true
        );
        assertEquals("com.example", r.targetClassInfo().packageName());
        assertEquals("Item", r.targetClassInfo().simpleName());
    }

    @Test
    void storesType() {
        var r = new ClassRelation(
            ClassInfo.fromFullyQualifiedName("com.example.Order"),
            ClassInfo.fromFullyQualifiedName("com.example.Item"),
            DependencyType.COMPOSITION,
            true
        );
        assertEquals(DependencyType.COMPOSITION, r.type());
    }

    @Test
    void storesIsMany() {
        var r = new ClassRelation(
            ClassInfo.fromFullyQualifiedName("com.example.Order"),
            ClassInfo.fromFullyQualifiedName("com.example.Item"),
            DependencyType.COMPOSITION,
            true
        );
        assertTrue(r.isMany());
    }

    @Test
    void isManyFalseWhenSingleReference() {
        var r = new ClassRelation(
            ClassInfo.fromFullyQualifiedName("com.example.Order"),
            ClassInfo.fromFullyQualifiedName("com.example.Customer"),
            DependencyType.AGGREGATION,
            false
        );
        assertFalse(r.isMany());
    }

    @Test
    void constructorThrowsForNullSourceClassInfo() {
        assertThrows(NullPointerException.class, () ->
            new ClassRelation(null, ClassInfo.fromFullyQualifiedName("com.example.B"),
                DependencyType.COMPOSITION, false));
    }

    @Test
    void constructorThrowsForNullTargetClassInfo() {
        assertThrows(NullPointerException.class, () ->
            new ClassRelation(ClassInfo.fromFullyQualifiedName("com.example.A"), null,
                DependencyType.COMPOSITION, false));
    }

    @Test
    void constructorThrowsForNullType() {
        assertThrows(NullPointerException.class, () ->
            new ClassRelation(
                ClassInfo.fromFullyQualifiedName("com.example.A"),
                ClassInfo.fromFullyQualifiedName("com.example.B"),
                null, false));
    }
}
