package com.sosuisha.classdiagram;

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
    void drawIncludesFillRectWhenFillColorSet() {
        var box = new ClassBox("MyClass");
        box.setFillColor("#FFFFBB");
        assertTrue(box.draw().contains("fill=\"#FFFFBB\""));
    }

    @Test
    void drawOmitsFillRectWhenFillColorNull() {
        var box = new ClassBox("MyClass");
        assertFalse(box.draw().contains("fill=\"#"));
    }

    @Test
    void drawContainsClassName() {
        var result = new ClassBox("MyClass").draw();
        assertTrue(result.contains("MyClass"));
    }

    @Test
    void drawUsesDefaultClassBoxStrokeColor() {
        assertTrue(new ClassBox("MyClass").draw().contains("stroke=\"#000000\""));
    }

    @Test
    void drawUsesCustomClassBoxStrokeColor() {
        var box = new ClassBox("MyClass");
        box.setStrokeColor("#123456");
        assertTrue(box.draw().contains("stroke=\"#123456\""));
    }

    @Test
    void setStrokeColorThrowsForNull() {
        assertThrows(NullPointerException.class,
                () -> new ClassBox("MyClass").setStrokeColor(null));
    }

    @Test
    void drawContainsFourPathsByDefault() {
        // 4辺（rect代替）。nameOnly がデフォルトなので区切り線は描画しない。
        var result = new ClassBox("MyClass").draw();
        assertEquals(4, result.split("<path", -1).length - 1);
    }

    @Test
    void showDetailsDrawsSixPaths() {
        // 4辺（rect代替） + 2本の区切り線 = 6
        var result = new ClassBox("MyClass").showDetails().draw();
        assertEquals(6, result.split("<path", -1).length - 1);
    }

    @Test
    void drawPathsUseBezierCurves() {
        var result = new ClassBox("MyClass").draw();
        assertTrue(result.contains(" Q "));
    }

    @Test
    void drawPathsHaveDoubleWobble() {
        // 各pathに Q が2つ（S字カーブ）= 4paths × 2 = 8個
        var result = new ClassBox("MyClass").draw();
        assertEquals(8, result.split(" Q ", -1).length - 1);
    }

    @Test
    void picturesqueReturnsSelf() {
        var box = new ClassBox("MyClass");
        assertSame(box, box.picturesque(true));
    }

    @Test
    void drawDoesNotIncludeOnePixelShiftedCompanionLineByDefault() {
        var result = new ClassBox("MyClass").draw();
        assertFalse(result.contains("M 1,1"));
        assertFalse(result.contains("M 101,1"));
        assertFalse(result.contains("M 101,27"));
        assertFalse(result.contains("M 1,27"));
    }

    @Test
    void picturesqueDrawsBoldOutlineWithOnePixelShiftedCompanionLine() {
        var result = new ClassBox("MyClass").picturesque(true).draw();
        assertEquals(8, result.split("<path", -1).length - 1);
        assertTrue(result.contains("M 1,1"));
        assertTrue(result.contains("M 101,1"));
        assertTrue(result.contains("M 101,27"));
        assertTrue(result.contains("M 1,27"));
    }

    @Test
    void drawContainsNoRectElement() {
        var result = new ClassBox("MyClass").draw();
        assertFalse(result.contains("<rect"));
    }

    @Test
    void drawContainsNoLineElement() {
        var result = new ClassBox("MyClass").draw();
        assertFalse(result.contains("<line"));
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
        var result = new ClassBox("MyClass", List.of("id: Long"), List.of()).showDetails().draw();
        assertTrue(result.contains("id: Long"));
    }

    @Test
    void drawContainsMethodText() {
        var result = new ClassBox("MyClass", List.of(), List.of("getId(): Long")).showDetails().draw();
        assertTrue(result.contains("getId(): Long"));
    }

    @Test
    void drawOmitsFieldAndMethodTextByDefault() {
        var result = new ClassBox("MyClass",
                List.of("id: Long"),
                List.of("getId(): Long")).draw();
        assertFalse(result.contains("id: Long"));
        assertFalse(result.contains("getId(): Long"));
    }

    @Test
    void showDetailsReturnsSelf() {
        var box = new ClassBox("MyClass");
        assertSame(box, box.showDetails());
    }

    @Test
    void drawTextHasFontSize() {
        var result = new ClassBox("MyClass").draw();
        assertTrue(result.contains("font-size="));
    }

    @Test
    void classNameBaselineIsAtPaddingPlusAscent() {
        // nameOnly のベースラインY = NAME_ONLY_PADDING_Y + ASCENT = 6 + 11 = 17
        var result = new ClassBox("MyClass").draw();
        assertTrue(result.contains("y=\"17\""));
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
        // nameOnly がデフォルトなので、名前コンパートメントのみ。
        assertEquals(26, new ClassBox("MyClass").height());
    }

    @Test
    void showDetailsKeepsEmptyCompartmentHeight() {
        // 空コンパートメント高さ = PADDING_Y * 2 = 8
        // 合計 = nameHeight(22) + fields(8) + methods(8) = 38
        assertEquals(38, new ClassBox("MyClass").showDetails().height());
    }

    @Test
    void secondFieldHasLineGapBetweenLines() {
        // nameHeight = FONT_SIZE + PADDING_Y*2 = 14 + 8 = 22
        // fields startY = 22
        // 1行目 baseline = 22 + PADDING_Y + ASCENT = 22 + 4 + 11 = 37
        // 2行目 baseline = 37 + FONT_SIZE + LINE_GAP = 37 + 14 + 4 = 55
        var result = new ClassBox("C", List.of("a", "b"), List.of()).showDetails().draw();
        assertTrue(result.contains("y=\"55\""));
    }

    @Test
    void sameClassBoxProducesSameOutput() {
        var result1 = new ClassBox("MyClass").draw();
        var result2 = new ClassBox("MyClass").draw();
        assertEquals(result1, result2);
    }

    @Test
    void defaultBoxHasNoneStereotype() {
        assertEquals(ClassStereotype.NONE, new ClassBox("MyClass").stereotype());
    }

    @Test
    void interfaceBoxHasInterfaceStereotype() {
        var box = new ClassBox("IFoo", ClassStereotype.INTERFACE);
        assertEquals(ClassStereotype.INTERFACE, box.stereotype());
    }

    @Test
    void interfaceBoxDrawContainsStereotypeLabel() {
        var result = new ClassBox("IFoo", ClassStereotype.INTERFACE).showDetails().draw();
        assertTrue(result.contains("«interface»"));
    }

    @Test
    void nameOnlyInterfaceBoxDrawContainsStereotypeLabel() {
        assertTrue(new ClassBox("IFoo", ClassStereotype.INTERFACE).draw().contains("«interface»"));
    }

    @Test
    void nameOnlyInterfaceBoxNameCompartmentIsTwoLinesTaller() {
        // nameOnly class: 14 + 6*2 = 26; nameOnly interface: 2*14 + 4 + 6*2 = 44
        int noneHeight = new ClassBox("MyClass").height();
        int ifaceHeight = new ClassBox("MyClass", ClassStereotype.INTERFACE).height();
        assertEquals(noneHeight + 18, ifaceHeight);
    }

    @Test
    void nameOnlyInterfaceBoxWidthAccountsForStereotypeLabel() {
        // «interface» = 11 chars: 11 * CHAR_WIDTH(8) + PADDING_X*2(16) = 104 >
        // MIN_WIDTH(100)
        assertEquals(104, new ClassBox("X", ClassStereotype.INTERFACE).width());
    }

    @Test
    void showDetailsInterfaceBoxNameCompartmentIsTwoLinesTaller() {
        // NONE: compartmentHeight(1) = 22; INTERFACE: compartmentHeight(2) = 40; diff =
        // 18
        int noneHeight = new ClassBox("MyClass").showDetails().height();
        int ifaceHeight = new ClassBox("MyClass", ClassStereotype.INTERFACE).showDetails().height();
        assertEquals(noneHeight + 18, ifaceHeight);
    }

    @Test
    void showDetailsInterfaceBoxWidthAccountsForStereotypeLabel() {
        // «interface» = 11 chars: 11 * CHAR_WIDTH(8) + PADDING_X*2(16) = 104 >
        // MIN_WIDTH(100)
        // "X" alone: 1 * 8 + 16 = 24 → clamped to MIN_WIDTH 100
        assertEquals(104, new ClassBox("X", ClassStereotype.INTERFACE).showDetails().width());
    }
}
