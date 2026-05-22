package com.sosuisha.classdiagram.analyzer;

import com.sosuisha.classdiagram.DependencyType;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ConnectedComponentSplitterTest {

    private final ConnectedComponentSplitter splitter = new ConnectedComponentSplitter();

    private static ClassRelation comp(ClassInfo src, ClassInfo tgt) {
        return new ClassRelation(src, tgt, DependencyType.COMPOSITION, false);
    }

    @Test
    void splitThrowsForNullInput() {
        assertThrows(NullPointerException.class, () -> splitter.split(null));
    }

    @Test
    void splitReturnsEmptyForEmptyInput() {
        var result = splitter.split(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void splitSingleComponentAllGetSameGroupIndex() {
        var a = new ClassInfo("p", "A");
        var b = new ClassInfo("p", "B");
        var c = new ClassInfo("p", "C");
        var relations = new ArrayList<>(List.of(comp(a, b), comp(b, c)));
        splitter.split(relations);
        assertEquals(a.groupIndex(), b.groupIndex());
        assertEquals(b.groupIndex(), c.groupIndex());
    }

    @Test
    void splitFirstComponentGetsGroupIndex0() {
        var a = new ClassInfo("p", "A"); var b = new ClassInfo("p", "B");
        var x = new ClassInfo("p", "X"); var y = new ClassInfo("p", "Y");
        var relations = new ArrayList<>(List.of(comp(a, b), comp(x, y)));
        splitter.split(relations);
        assertEquals(0, a.groupIndex());
        assertEquals(0, b.groupIndex());
        assertEquals(1, x.groupIndex());
        assertEquals(1, y.groupIndex());
    }

    @Test
    void splitTwoComponentsGetDistinctGroupIndices() {
        var a = new ClassInfo("p", "A"); var b = new ClassInfo("p", "B");
        var x = new ClassInfo("p", "X"); var y = new ClassInfo("p", "Y");
        var relations = new ArrayList<>(List.of(comp(a, b), comp(x, y)));
        splitter.split(relations);
        assertNotEquals(a.groupIndex(), x.groupIndex());
    }

    @Test
    void splitThreeComponentsGetDistinctGroupIndices() {
        var a = new ClassInfo("p", "A"); var b = new ClassInfo("p", "B");
        var x = new ClassInfo("p", "X"); var y = new ClassInfo("p", "Y");
        var p = new ClassInfo("p", "P"); var q = new ClassInfo("p", "Q");
        var relations = new ArrayList<>(List.of(comp(a, b), comp(x, y), comp(p, q)));
        splitter.split(relations);
        var groups = Set.of(a.groupIndex(), x.groupIndex(), p.groupIndex());
        assertEquals(3, groups.size());
    }

    @Test
    void splitReturnsSameListInstance() {
        var a = new ClassInfo("p", "A"); var b = new ClassInfo("p", "B");
        var relations = new ArrayList<>(List.of(comp(a, b)));
        var result = splitter.split(relations);
        assertSame(relations, result);
    }

    @Test
    void splitHandlesDuplicateClassInfoInstancesForSameClass() {
        // sourceClassInfo と targetClassInfo に同一論理クラスが別インスタンスで現れる場合
        var a1 = new ClassInfo("p", "A");
        var a2 = new ClassInfo("p", "A"); // a1 と equals だが別インスタンス
        var b  = new ClassInfo("p", "B");
        var c  = new ClassInfo("p", "C");
        // A→B, A→C (A が2回登場、別インスタンス)
        var relations = new ArrayList<>(List.of(comp(a1, b), comp(a2, c)));
        splitter.split(relations);
        // 全て同一グループ
        assertEquals(a1.groupIndex(), b.groupIndex());
        assertEquals(a2.groupIndex(), c.groupIndex());
        assertEquals(a1.groupIndex(), a2.groupIndex());
    }
}
