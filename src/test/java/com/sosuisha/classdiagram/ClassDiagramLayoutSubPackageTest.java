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
    void rootPackageSlotIsPlacedAboveSubPackageSlots() {
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
        assertTrue(boxR.y() < boxS.y(), "root-package class must be above sub-package class");

        assertEquals(1, result.packageGroups().size());
        assertEquals("service", result.packageGroups().get(0).label());
        var pg = result.packageGroups().get(0);
        assertTrue(boxR.y() + boxR.height() <= pg.y(), "R is above and outside the service group");
    }

    @Test
    void nestedSubPackageProducesNestedRectanglesWithLocalLabels() {
        // service.impl.SvcImpl REALIZES service.Svc.
        // Tree: root → service (with Svc) → impl (with SvcImpl).
        // Two PackageGroupBoxes: outer "service" and inner "impl" (local segments, not full path).
        var iface = ci(ROOT + ".service", "Svc");
        var impl  = ci(ROOT + ".service.impl", "SvcImpl");
        var rels = List.of(
            new ClassRelation(impl, iface, DependencyType.REALIZATION, false));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(ROOT, 30)
            .layout(layers, rels);

        var labels = result.packageGroups().stream().map(PackageGroupBox::label).toList();
        assertTrue(labels.contains("service"), "outer 'service' rectangle expected");
        assertTrue(labels.contains("impl"), "inner 'impl' rectangle expected (local segment, not full path)");

        var service = result.packageGroups().stream()
            .filter(p -> p.label().equals("service")).findFirst().orElseThrow();
        var implBox = result.packageGroups().stream()
            .filter(p -> p.label().equals("impl")).findFirst().orElseThrow();
        // impl rectangle must be visually nested inside service rectangle.
        assertTrue(implBox.x() >= service.x() && implBox.y() >= service.y(),
            "impl top-left must be inside service");
        assertTrue(implBox.x() + implBox.width() <= service.x() + service.width()
            && implBox.y() + implBox.height() <= service.y() + service.height(),
            "impl bottom-right must be inside service");
    }

    @Test
    void sameSubPackageClassesInNonContiguousLayersAreEnclosedTogether() {
        // service.A → other.M → service.B
        // Original Sugiyama layers: [A], [M], [B] (3 layers vertically)
        // With sub-package slots: A and B (both "service") stack in one slot vertically;
        // M lives in its own "other" slot horizontally separated.
        var a = ci(ROOT + ".service", "A");
        var m = ci(ROOT + ".other",   "M");
        var b = ci(ROOT + ".service", "B");
        var rels = List.of(rel(a, m), rel(m, b));
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(ROOT, 30)
            .layout(layers, rels);

        var boxA = result.boxes().stream().filter(x -> x.name().equals("A")).findFirst().orElseThrow();
        var boxB = result.boxes().stream().filter(x -> x.name().equals("B")).findFirst().orElseThrow();
        var boxM = result.boxes().stream().filter(x -> x.name().equals("M")).findFirst().orElseThrow();

        var serviceGroup = result.packageGroups().stream()
            .filter(pg -> pg.label().equals("service")).findFirst().orElseThrow();

        assertTrue(boxA.x() >= serviceGroup.x() && boxA.x() + boxA.width() <= serviceGroup.x() + serviceGroup.width());
        assertTrue(boxB.x() >= serviceGroup.x() && boxB.x() + boxB.width() <= serviceGroup.x() + serviceGroup.width());
        assertTrue(boxA.y() >= serviceGroup.y() && boxA.y() + boxA.height() <= serviceGroup.y() + serviceGroup.height());
        assertTrue(boxB.y() >= serviceGroup.y() && boxB.y() + boxB.height() <= serviceGroup.y() + serviceGroup.height());

        boolean mIsOutsideVertically =
            (boxM.y() + boxM.height() <= serviceGroup.y()) || (boxM.y() >= serviceGroup.y() + serviceGroup.height());
        assertTrue(mIsOutsideVertically, "M must be vertically outside the service group rectangle");
    }

    @Test
    void classLevelBarycenterReordersRowMembersToFollowExternalConnections() {
        // service has 3 classes in the same Sugiyama row: A, B, C.
        // outer.X depends on service.C only.
        // Without class-level barycenter, the row order follows minimizeCrossings
        // output (alphabetical-like: A, B, C → C is rightmost).
        // outer.X is at some position; for service.C's connection to outer.X to be straightest,
        // C should be on the side of X. The barycenter pull should keep C next to outer
        // (it already is on the right, so this verifies the algorithm doesn't break ordering).
        // More interesting: make outer.X depend on service.A. Without reorder: A is leftmost.
        // After barycenter: A should be pulled to the side of outer (let's see geometry).
        // Actually for a clean assertion we test that after barycenter,
        // among A/B/C, the one connected to outer ends up closest in X to outer.
        var svcA = ci(ROOT + ".service", "A");
        var svcB = ci(ROOT + ".service", "B");
        var svcC = ci(ROOT + ".service", "C");
        var outerX = ci(ROOT + ".outer", "X");
        // A connects within row; B connects to outer.X; C connects within row.
        // So B should move toward outer.X side after the class-level barycenter pass.
        var rels = List.of(
            rel(svcA, svcB),
            rel(svcB, outerX),
            rel(svcB, svcC)
        );
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(ROOT, 30)
            .layout(layers, rels);

        var boxA = result.boxes().stream().filter(b -> b.name().equals("A")).findFirst().orElseThrow();
        var boxB = result.boxes().stream().filter(b -> b.name().equals("B")).findFirst().orElseThrow();
        var boxC = result.boxes().stream().filter(b -> b.name().equals("C")).findFirst().orElseThrow();
        var boxX = result.boxes().stream().filter(b -> b.name().equals("X")).findFirst().orElseThrow();

        // Only A, B, C live in the same row (same package, same Sugiyama layer).
        // The class with the external connection to outer.X (= B) should end up closest in X
        // to outer.X's center among the three.
        int xCenter = boxX.x() + boxX.width() / 2;
        int distA = Math.abs((boxA.x() + boxA.width() / 2) - xCenter);
        int distB = Math.abs((boxB.x() + boxB.width() / 2) - xCenter);
        int distC = Math.abs((boxC.x() + boxC.width() / 2) - xCenter);
        assertTrue(distB <= distA, "B (externally connected) should be at least as close to X as A");
        assertTrue(distB <= distC, "B (externally connected) should be at least as close to X as C");
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

    @Test
    void barycenterOrderingDiffersFromAlphabetical() {
        // beta.B and gamma.C both REALIZE alpha.A → alpha is pulled to the bottom by barycenter.
        var alphaA = ci(ROOT + ".alpha", "A");
        var betaB  = ci(ROOT + ".beta",  "B");
        var gammaC = ci(ROOT + ".gamma", "C");
        var rels = List.of(
            new ClassRelation(betaB,  alphaA, DependencyType.REALIZATION, false),
            new ClassRelation(gammaC, alphaA, DependencyType.REALIZATION, false)
        );
        var layers = new ClassRelationSorter().sort(rels);
        var result = new ClassDiagramLayout(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(ROOT, 30)
            .layout(layers, rels);

        int alphaY = result.packageGroups().stream()
            .filter(p -> p.label().equals("alpha")).findFirst().orElseThrow().y();
        int betaY  = result.packageGroups().stream()
            .filter(p -> p.label().equals("beta")).findFirst().orElseThrow().y();
        int gammaY = result.packageGroups().stream()
            .filter(p -> p.label().equals("gamma")).findFirst().orElseThrow().y();

        // Barycenter expected order: beta above gamma above alpha (alpha at bottom).
        assertTrue(betaY < alphaY,  "barycenter places beta above alpha");
        assertTrue(gammaY < alphaY, "barycenter places gamma above alpha");
    }
}
