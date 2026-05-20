package com.sosuisha.classdiagram.analyzer;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ClassRelationSorterTest {

    private static final String PKG = "com.example";
    private final ClassRelationSorter sorter = new ClassRelationSorter();

    private static ClassInfo ci(String name) {
        return new ClassInfo(PKG, name);
    }

    private static ClassRelation rel(String src, String tgt) {
        return new ClassRelation(ci(src), ci(tgt), RelationType.COMPOSITION, false);
    }

    @Test
    void sortThrowsForNullInput() {
        assertThrows(NullPointerException.class, () -> sorter.sort(null));
    }

    @Test
    void sortReturnsEmptyForEmptyInput() {
        assertEquals(List.of(), sorter.sort(List.of()));
    }

    @Test
    void sortLinearChain() {
        // A → B → C  =>  [[A], [B], [C]]
        var result = sorter.sort(List.of(rel("A", "B"), rel("B", "C")));
        assertEquals(3, result.size());
        assertEquals(List.of(ci("A")), result.get(0));
        assertEquals(List.of(ci("B")), result.get(1));
        assertEquals(List.of(ci("C")), result.get(2));
    }

    @Test
    void sortDiamond() {
        // A->B, A->C, B->D, C->D  =>  [[A], [B, C], [D]]
        var result = sorter.sort(List.of(
            rel("A", "B"), rel("A", "C"), rel("B", "D"), rel("C", "D")
        ));
        assertEquals(3, result.size());
        assertEquals(List.of(ci("A")), result.get(0));
        assertEquals(List.of(ci("B"), ci("C")), result.get(1));
        assertEquals(List.of(ci("D")), result.get(2));
    }

    @Test
    void sortThrowsCircularRelationException() {
        // A -> B -> A
        var ex = assertThrows(CircularRelationException.class,
            () -> sorter.sort(List.of(rel("A", "B"), rel("B", "A"))));
        assertTrue(ex.getMessage().contains("A"));
        assertTrue(ex.getMessage().contains("B"));
    }
}
