package com.sosuisha;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class ClassBoxTest {

    @Test
    void classBoxCanBeCreatedWithName() {
        var box = new ClassBox("MyClass");
        assertEquals("MyClass", box.name());
    }

    @Test
    void throwsWhenNameIsNull() {
        assertThrows(NullPointerException.class, () -> new ClassBox(null));
    }

    @Test
    void throwsWhenFieldsIsNull() {
        assertThrows(NullPointerException.class, () -> new ClassBox("MyClass", null, List.of()));
    }

    @Test
    void throwsWhenMethodsIsNull() {
        assertThrows(NullPointerException.class, () -> new ClassBox("MyClass", List.of(), null));
    }

    @Test
    void fieldsDefaultsToEmpty() {
        var box = new ClassBox("MyClass");
        assertTrue(box.fields().isEmpty());
    }

    @Test
    void methodsDefaultsToEmpty() {
        var box = new ClassBox("MyClass");
        assertTrue(box.methods().isEmpty());
    }

    @Test
    void classBoxStoresFields() {
        var box = new ClassBox("MyClass", List.of("id: Long"), List.of());
        assertEquals(List.of("id: Long"), box.fields());
    }

    @Test
    void classBoxStoresMethods() {
        var box = new ClassBox("MyClass", List.of(), List.of("getId(): Long"));
        assertEquals(List.of("getId(): Long"), box.methods());
    }

    @Test
    void drawContainsClassName() {
        var result = new ClassBox("MyClass").draw();
        assertTrue(result.contains("MyClass"));
    }

    @Test
    void drawContainsTwoHorizontalLines() {
        var result = new ClassBox("MyClass").draw();
        assertEquals(2, result.split("<line", -1).length - 1);
    }

    @Test
    void drawContainsOuterRect() {
        var result = new ClassBox("MyClass").draw();
        assertTrue(result.contains("<rect"));
    }

    @Test
    void drawHasDataDiagramDrawBox() {
        var result = new ClassBox("MyClass").draw();
        assertTrue(result.contains("data-diagram-draw=\"box\""));
    }

    @Test
    void drawHasDataDiagramDrawTypClass() {
        var result = new ClassBox("MyClass").draw();
        assertTrue(result.contains("data-diagram-draw-type=\"class\""));
    }

    @Test
    void drawHasDataDiagramDrawName() {
        var result = new ClassBox("MyClass").draw();
        assertTrue(result.contains("data-diagram-draw-name=\"MyClass\""));
    }

    @Test
    void drawContainsFieldText() {
        var result = new ClassBox("MyClass", List.of("id: Long"), List.of()).draw();
        assertTrue(result.contains("id: Long"));
    }

    @Test
    void drawContainsMethodText() {
        var result = new ClassBox("MyClass", List.of(), List.of("getId(): Long")).draw();
        assertTrue(result.contains("getId(): Long"));
    }

    @Test
    void drawTextHasFontSize() {
        var result = new ClassBox("MyClass").draw();
        assertTrue(result.contains("font-size="));
    }

    @Test
    void classNameBaselineIsAtPaddingPlusAscent() {
        // ベースラインY = PADDING_Y + ASCENT = 4 + 11 = 15
        var result = new ClassBox("MyClass").draw();
        assertTrue(result.contains("y=\"15\""));
    }

    @Test
    void drawWithPositionHasTranslate() {
        var box = new ClassBox("MyClass");
        box.setPosition(50, 60);
        assertTrue(box.draw().contains("translate(50,60)"));
    }

    @Test
    void drawDefaultPositionHasNoTranslate() {
        var result = new ClassBox("MyClass").draw();
        assertFalse(result.contains("translate"));
    }

    @Test
    void emptyCompartmentHeightIsPaddingOnly() {
        // 空コンパートメント高さ = PADDING_Y * 2 = 8
        // 合計 = nameHeight(22) + fields(8) + methods(8) = 38
        var result = new ClassBox("MyClass").draw();
        assertTrue(result.contains("height=\"38\""));
    }

    @Test
    void secondFieldHasLineGapBetweenLines() {
        // nameHeight     = FONT_SIZE + PADDING_Y*2 = 14 + 8 = 22
        // fields startY  = 22
        // 1行目 baseline = 22 + PADDING_Y + ASCENT = 22 + 4 + 11 = 37
        // 2行目 baseline = 37 + FONT_SIZE + LINE_GAP = 37 + 14 + 4 = 55
        var result = new ClassBox("C", List.of("a", "b"), List.of()).draw();
        assertTrue(result.contains("y=\"55\""));
    }
}
