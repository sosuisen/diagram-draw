package com.sosuisha.classdiagram.analyzer;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            r.sourceClass().equals(FIXTURE_PKG + ".FixtureOrder") &&
            r.targetClass().equals(FIXTURE_PKG + ".FixtureCustomer") &&
            r.type() == RelationType.AGGREGATION &&
            !r.isMany()
        ));
    }

    @Test
    void scanReturnsNoRelationsForPojoWithoutSamePackageFields() {
        var relations = new ClassRelationScanner().scan(CLASS_ROOT, FIXTURE_PKG);
        assertTrue(relations.stream().noneMatch(r ->
            r.sourceClass().equals(FIXTURE_PKG + ".FixtureItem")
        ));
    }
}
