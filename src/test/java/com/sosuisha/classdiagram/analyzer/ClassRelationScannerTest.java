package com.sosuisha.classdiagram.analyzer;

import com.sosuisha.classdiagram.ClassStereotype;
import com.sosuisha.classdiagram.DependencyType;
import com.sosuisha.classdiagram.analyzer.ClassRelation;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ClassRelationScannerTest {

    private static final Path CLASS_ROOT = Path.of("target/test-classes");
    private static final String FIXTURE_PKG = "com.sosuisha.classdiagram.analyzer.fixture";

    @Test
    void scanThrowsForNullClassRoot() {
        assertThrows(NullPointerException.class,
            () -> new ClassRelationScanner().scan(null, "com.example"));
    }

    @Test
    void scanThrowsForNullPackageName() {
        assertThrows(NullPointerException.class,
            () -> new ClassRelationScanner().scan(CLASS_ROOT, null));
    }

    @Test
    void scanReturnsEmptyListForNonExistentPackage() {
        var result = new ClassRelationScanner().scan(CLASS_ROOT, "com.does.not.exist");
        assertTrue(result.isEmpty());
    }

    @Test
    void scanDetectsAggregationForConstructorInjectedField() {
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXTURE_PKG);
        assertTrue(relations.stream().anyMatch(r ->
            r.sourceClassInfo().packageName().equals(FIXTURE_PKG) &&
            r.sourceClassInfo().simpleName().equals("FixtureOrder") &&
            r.targetClassInfo().packageName().equals(FIXTURE_PKG) &&
            r.targetClassInfo().simpleName().equals("FixtureCustomer") &&
            r.type() == DependencyType.AGGREGATION &&
            !r.isMany()
        ));
    }

    @Test
    void scanReturnsNoRelationsForPojoWithoutSamePackageFields() {
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXTURE_PKG);
        assertTrue(relations.stream().noneMatch(r ->
            r.sourceClassInfo().packageName().equals(FIXTURE_PKG) &&
            r.sourceClassInfo().simpleName().equals("FixtureItem")
        ));
    }

    @Test
    void scanDetectsCompositionWithCollectionField() {
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXTURE_PKG);
        assertTrue(relations.stream().anyMatch(r ->
            r.sourceClassInfo().simpleName().equals("FixtureOrder") &&
            r.targetClassInfo().simpleName().equals("FixtureItem") &&
            r.type() == DependencyType.COMPOSITION &&
            r.isMany()
        ));
    }

    @Test
    void scanDetectsRelationFromSubpackage() {
        var subPkg = FIXTURE_PKG + ".sub";
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXTURE_PKG);
        assertTrue(relations.stream().anyMatch(r ->
            r.sourceClassInfo().packageName().equals(subPkg) &&
            r.sourceClassInfo().simpleName().equals("FixtureSubOrder") &&
            r.targetClassInfo().packageName().equals(FIXTURE_PKG) &&
            r.targetClassInfo().simpleName().equals("FixtureItem") &&
            r.type() == DependencyType.COMPOSITION &&
            r.isMany()
        ));
    }

    @Test
    void scanDetectsRealizationForImplementsInterface() {
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXTURE_PKG);
        assertTrue(relations.stream().anyMatch(r ->
            r.sourceClassInfo().simpleName().equals("FixtureServiceImpl") &&
            r.targetClassInfo().simpleName().equals("FixtureService") &&
            r.type() == DependencyType.REALIZATION &&
            !r.isMany()
        ));
    }

    @Test
    void scanDetectsBothRealizationsForMultipleInterfaceImplementation() {
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXTURE_PKG);
        assertTrue(relations.stream().anyMatch(r ->
            r.sourceClassInfo().simpleName().equals("FixtureMultiImpl") &&
            r.targetClassInfo().simpleName().equals("FixtureService") &&
            r.type() == DependencyType.REALIZATION
        ));
        assertTrue(relations.stream().anyMatch(r ->
            r.sourceClassInfo().simpleName().equals("FixtureMultiImpl") &&
            r.targetClassInfo().simpleName().equals("FixtureAnotherService") &&
            r.type() == DependencyType.REALIZATION
        ));
    }

    @Test
    void scanAssignsInterfaceStereotypeToInterfaceTarget() {
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXTURE_PKG);
        var ifaceInfo = relations.stream()
            .filter(r -> r.type() == DependencyType.REALIZATION
                      && r.targetClassInfo().simpleName().equals("FixtureService"))
            .map(ClassRelation::targetClassInfo)
            .findFirst()
            .orElseThrow();
        assertEquals(ClassStereotype.INTERFACE, ifaceInfo.stereotype());
    }

    @Test
    void scanAssignsNoneStereotypeToConcreteClassSource() {
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXTURE_PKG);
        var implInfo = relations.stream()
            .filter(r -> r.type() == DependencyType.REALIZATION
                      && r.sourceClassInfo().simpleName().equals("FixtureServiceImpl"))
            .map(ClassRelation::sourceClassInfo)
            .findFirst()
            .orElseThrow();
        assertEquals(ClassStereotype.NONE, implInfo.stereotype());
    }

    private static final String FIXDEP_PKG = "com.sosuisha.classdiagram.analyzer.fixdep";

    @Test
    void scanDetectsDependencyFqnForLocalVariable() {
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXDEP_PKG);
        var src = relations.stream()
            .filter(r -> r.sourceClassInfo().simpleName().equals("FixtureDepSource"))
            .map(ClassRelation::sourceClassInfo)
            .findFirst()
            .orElseThrow();
        assertTrue(src.dependencyTargetFqns().contains(FIXDEP_PKG + ".FixtureDepTarget"),
            "FixtureDepTarget must be in dependencyTargetFqns (local var in process())");
    }

    @Test
    void scanDetectsDependencyFqnForMethodParam() {
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXDEP_PKG);
        var src = relations.stream()
            .filter(r -> r.sourceClassInfo().simpleName().equals("FixtureDepSource"))
            .map(ClassRelation::sourceClassInfo)
            .findFirst()
            .orElseThrow();
        assertTrue(src.dependencyTargetFqns().contains(FIXDEP_PKG + ".FixtureDepTargetPart"),
            "FixtureDepTargetPart must be in dependencyTargetFqns (param in check())");
    }

    @Test
    void scanExcludesFieldTypeFromDependencyFqns() {
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXDEP_PKG);
        var src = relations.stream()
            .filter(r -> r.sourceClassInfo().simpleName().equals("FixtureDepSource"))
            .map(ClassRelation::sourceClassInfo)
            .findFirst()
            .orElseThrow();
        assertFalse(src.dependencyTargetFqns().contains(FIXDEP_PKG + ".FixtureDepSourcePart"),
            "FixtureDepSourcePart is a stored field — must NOT be in dependencyTargetFqns");
    }

    @Test
    void scanDoesNotIncludeSelfInDependencyFqns() {
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXDEP_PKG);
        var src = relations.stream()
            .filter(r -> r.sourceClassInfo().simpleName().equals("FixtureDepSource"))
            .map(ClassRelation::sourceClassInfo)
            .findFirst()
            .orElseThrow();
        assertFalse(src.dependencyTargetFqns().contains(FIXDEP_PKG + ".FixtureDepSource"),
            "A class must not depend on itself");
    }
}
