package com.sosuisha.classdiagram.analyzer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassRelationTest {

    @Test
    void storesSourceClass() {
        var r = new ClassRelation("com.example.Order", "com.example.Item", RelationType.COMPOSITION, true);
        assertEquals("com.example.Order", r.sourceClass());
    }

    @Test
    void storesTargetClass() {
        var r = new ClassRelation("com.example.Order", "com.example.Item", RelationType.COMPOSITION, true);
        assertEquals("com.example.Item", r.targetClass());
    }

    @Test
    void storesType() {
        var r = new ClassRelation("com.example.Order", "com.example.Item", RelationType.COMPOSITION, true);
        assertEquals(RelationType.COMPOSITION, r.type());
    }

    @Test
    void storesIsMany() {
        var r = new ClassRelation("com.example.Order", "com.example.Item", RelationType.COMPOSITION, true);
        assertTrue(r.isMany());
    }

    @Test
    void isManyFalseWhenSingleReference() {
        var r = new ClassRelation("com.example.Order", "com.example.Customer", RelationType.AGGREGATION, false);
        assertFalse(r.isMany());
    }
}
