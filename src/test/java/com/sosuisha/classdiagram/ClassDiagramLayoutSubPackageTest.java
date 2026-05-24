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

    @Test
    void singleSubPackageProducesOnePackageGroupBoxWithRelativeLabel() {
        // Both classes are in com.example.service → one slot, label "service".
        var a = ci(ROOT + ".service", "A");
        var b = ci(ROOT + ".service", "B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(ROOT, 30)
            .layout(layers, rels);

        assertEquals(1, result.packageGroups().size());
        assertEquals("service", result.packageGroups().get(0).label());
    }

    @Test
    void packageGroupBoxEnclosesAllItsMembers() {
        var a = ci(ROOT + ".service", "A");
        var b = ci(ROOT + ".service", "B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(ROOT, 30)
            .layout(layers, rels);

        var pg = result.packageGroups().get(0);
        for (var box : result.boxes()) {
            assertTrue(box.x() >= pg.x(), "box left inside group");
            assertTrue(box.y() >= pg.y(), "box top inside group");
            assertTrue(box.x() + box.width() <= pg.x() + pg.width(), "box right inside group");
            assertTrue(box.y() + box.height() <= pg.y() + pg.height(), "box bottom inside group");
        }
    }

    @Test
    void rootPackageSlotIsPlacedLeftOfSubPackageSlots() {
        // R (root) ← S (com.example.service). One connected component (REALIZATION).
        var rRoot = ci(ROOT, "R");
        var sSvc  = ci(ROOT + ".service", "S");
        var rels = List.of(
            new ClassRelation(sSvc, rRoot, DependencyType.REALIZATION, false));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(ROOT, 30)
            .layout(layers, rels);

        var boxR = result.boxes().stream().filter(b -> b.name().equals("R")).findFirst().orElseThrow();
        var boxS = result.boxes().stream().filter(b -> b.name().equals("S")).findFirst().orElseThrow();
        assertTrue(boxR.x() < boxS.x(), "root-package class must be left of sub-package class");

        assertEquals(1, result.packageGroups().size());
        assertEquals("service", result.packageGroups().get(0).label());
        var pg = result.packageGroups().get(0);
        assertTrue(boxR.x() + boxR.width() <= pg.x(), "R is left of and outside the service group");
    }

    @Test
    void multiLevelSubPackageLabelUsesFullRelativeName() {
        var iface = ci(ROOT + ".service", "Svc");
        var impl  = ci(ROOT + ".service.impl", "SvcImpl");
        var rels = List.of(
            new ClassRelation(impl, iface, DependencyType.REALIZATION, false));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(ROOT, 30)
            .layout(layers, rels);

        var labels = result.packageGroups().stream().map(PackageGroupBox::label).toList();
        assertTrue(labels.contains("service"));
        assertTrue(labels.contains("service.impl"));
    }

    @Test
    void packageGroupBoxStaysWithinCanvasBounds() {
        // Regression: with default canvasPaddingY=20 (< GROUP_PADDING_TOP=25),
        // the algorithm must clamp the package group's top so it is not negative.
        var a = ci(ROOT + ".service", "A");
        var b = ci(ROOT + ".service", "B");
        var rels = List.of(rel(a, b));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(ROOT, 30)
            .layout(layers, rels);
        for (var pg : result.packageGroups()) {
            assertTrue(pg.x() >= 0, "PackageGroupBox x must be >= 0, got " + pg.x());
            assertTrue(pg.y() >= 0, "PackageGroupBox y must be >= 0, got " + pg.y());
        }
    }
}
