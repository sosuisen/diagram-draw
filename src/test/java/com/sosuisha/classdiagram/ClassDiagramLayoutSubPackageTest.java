package com.sosuisha.classdiagram;

import com.sosuisha.classdiagram.analyzer.ClassInfo;
import com.sosuisha.classdiagram.analyzer.ClassRelation;
import com.sosuisha.classdiagram.analyzer.ClassRelationSorter;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ClassDiagramLayoutSubPackageTest {

    private static final String ROOT = "com.example";

    private static ClassInfo ci(String pkg, String name) {
        return new ClassInfo(pkg, name);
    }

    private static ClassRelation rel(ClassInfo src, ClassInfo tgt) {
        return new ClassRelation(src, tgt, DependencyType.COMPOSITION, false);
    }

    @Test
    void enableSubPackageGroupingThrowsForNullRootPackage() {
        var layout = new ClassDiagramLayout(20, 40, 20, 20, 60);
        assertThrows(NullPointerException.class,
            () -> layout.enableSubPackageGrouping(null, 30));
    }

    @Test
    void enableSubPackageGroupingThrowsForNegativePackageGap() {
        var layout = new ClassDiagramLayout(20, 40, 20, 20, 60);
        assertThrows(IllegalArgumentException.class,
            () -> layout.enableSubPackageGrouping(ROOT, -1));
    }

    @Test
    void enableSubPackageGroupingReturnsSelfForChaining() {
        var layout = new ClassDiagramLayout(20, 40, 20, 20, 60);
        assertSame(layout, layout.enableSubPackageGrouping(ROOT, 30));
    }

    @Test
    void packageGroupsEmptyWhenOptionNotEnabled() {
        var a = ci(ROOT, "A"); var b = ci(ROOT, "B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60).layout(layers, rels);
        assertTrue(result.packageGroups().isEmpty());
    }
}
