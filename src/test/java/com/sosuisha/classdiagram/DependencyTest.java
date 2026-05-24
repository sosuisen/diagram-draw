package com.sosuisha.classdiagram;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DependencyTest {

    private static Dependency dep(DependencyType type) {
        var source = new ClassBox("Order");
        source.setPosition(0, 0);
        var target = new ClassBox("Item");
        target.setPosition(200, 0);
        return new Dependency(source, target, type);
    }

    @Test
    void dependencyStoresSourceAndTarget() {
        var source = new ClassBox("Order");
        var target = new ClassBox("Item");
        var dep = new Dependency(source, target, DependencyType.COMPOSITION);
        assertSame(source, dep.source());
        assertSame(target, dep.target());
    }

    @Test
    void dependencyStoresType() {
        var dep = new Dependency(new ClassBox("A"), new ClassBox("B"), DependencyType.AGGREGATION);
        assertEquals(DependencyType.AGGREGATION, dep.type());
    }

    @Test
    void throwsWhenSourceIsNull() {
        assertThrows(NullPointerException.class,
            () -> new Dependency(null, new ClassBox("B"), DependencyType.COMPOSITION));
    }

    @Test
    void throwsWhenTargetIsNull() {
        assertThrows(NullPointerException.class,
            () -> new Dependency(new ClassBox("A"), null, DependencyType.COMPOSITION));
    }

    @Test
    void throwsWhenTypeIsNull() {
        assertThrows(NullPointerException.class,
            () -> new Dependency(new ClassBox("A"), new ClassBox("B"), null));
    }

    @Test
    void drawContainsCurvePath() {
        var svg = dep(DependencyType.COMPOSITION).draw();
        assertTrue(svg.contains("<path "), "edge should be drawn as <path>");
        assertTrue(svg.contains(" Q "), "path should use a quadratic Bezier (Q) curve command");
    }

    @Test
    void drawContainsDiamond() {
        assertTrue(dep(DependencyType.COMPOSITION).draw().contains("<polygon"));
    }

    @Test
    void drawHasDataAttribute() {
        assertTrue(dep(DependencyType.COMPOSITION).draw().contains("data-diagram-draw=\"dependency\""));
    }

    @Test
    void compositionDiamondIsFilled() {
        assertTrue(dep(DependencyType.COMPOSITION).draw().contains("fill=\"black\""));
    }

    @Test
    void aggregationDiamondIsNotFilled() {
        var result = dep(DependencyType.AGGREGATION).draw();
        assertTrue(result.contains("fill=\"none\""));
        assertFalse(result.contains("fill=\"black\""));
    }

    @Test
    void drawCurveDoesNotStartAtSourceCenter() {
        // ソース中心X = 50。曲線パスは "M sx,sy ..." の形なので "M 50.0," は含まれないはず
        var result = dep(DependencyType.COMPOSITION).draw();
        assertFalse(result.contains("M 50.0,"));
    }

    @Test
    void drawCurveDoesNotEndAtTargetCenter() {
        // ターゲット中心X = 250。曲線の終点は ".. 250.0,..." 形式では現れないはず（辺上の点）
        var result = dep(DependencyType.COMPOSITION).draw();
        // 曲線パスの末尾は "Q cpx,cpy ex,ey"。ex=250.0 ならテスト対象が辺上にないことになる。
        assertFalse(result.matches(".* Q [^ ]+ 250\\.0,.*"),
            "curve should not end at target center X (250.0)");
    }

    private static Dependency realizationDep() {
        var source = new ClassBox("FooImpl");
        source.setPosition(50, 200);
        var target = new ClassBox("IFoo");
        target.setPosition(50, 0);
        return new Dependency(source, target, DependencyType.REALIZATION);
    }

    @Test
    void drawRealizationHasDataAttribute() {
        assertTrue(realizationDep().draw().contains("data-diagram-draw-type=\"realization\""));
    }

    @Test
    void drawRealizationHasDashedLine() {
        assertTrue(realizationDep().draw().contains("stroke-dasharray"));
    }

    @Test
    void drawRealizationHasHollowTriangle() {
        var svg = realizationDep().draw();
        assertTrue(svg.contains("<polygon"));
        assertTrue(svg.contains("fill=\"white\""));
    }

    private static Dependency dependencyDep() {
        var source = new ClassBox("OrderService");
        source.setPosition(50, 200);
        var target = new ClassBox("InventoryRepo");
        target.setPosition(50, 0);
        return new Dependency(source, target, DependencyType.DEPENDENCY);
    }

    @Test
    void drawDependencyHasDataAttribute() {
        assertTrue(dependencyDep().draw().contains("data-diagram-draw-type=\"dependency\""));
    }

    @Test
    void drawDependencyHasDashedLine() {
        assertTrue(dependencyDep().draw().contains("stroke-dasharray"));
    }

    @Test
    void drawDependencyHasOpenArrowhead() {
        var svg = dependencyDep().draw();
        assertTrue(svg.contains("<polyline"));
        assertTrue(svg.contains("fill=\"none\""));
    }

    @Test
    void drawDependencyHasNoFilledDiamond() {
        assertFalse(dependencyDep().draw().contains("fill=\"black\""));
    }
}
