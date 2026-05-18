package com.sosuisha;

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
    void drawContainsLine() {
        assertTrue(dep(DependencyType.COMPOSITION).draw().contains("<line"));
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
    void drawLineDoesNotStartAtSourceCenter() {
        // ソース中心X = 0 + 100/2 = 50。辺上の点なのでx1 != 50 のはず
        var result = dep(DependencyType.COMPOSITION).draw();
        assertFalse(result.contains("x1=\"50.0\""));
    }

    @Test
    void drawLineDoesNotEndAtTargetCenter() {
        // ターゲット中心X = 200 + 100/2 = 250。辺上の点なのでx2 != 250 のはず
        var result = dep(DependencyType.COMPOSITION).draw();
        assertFalse(result.contains("x2=\"250.0\""));
    }
}
