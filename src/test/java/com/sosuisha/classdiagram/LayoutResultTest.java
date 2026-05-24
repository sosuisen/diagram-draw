package com.sosuisha.classdiagram;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LayoutResultTest {

    @Test
    void constructorThrowsForNullBoxes() {
        assertThrows(NullPointerException.class,
            () -> new LayoutResult(null, List.of(), List.of(), 100, 100));
    }

    @Test
    void constructorThrowsForNullDependencies() {
        assertThrows(NullPointerException.class,
            () -> new LayoutResult(List.of(), null, List.of(), 100, 100));
    }

    @Test
    void boxesIsUnmodifiable() {
        var mutable = new ArrayList<ClassBox>();
        mutable.add(new ClassBox("A"));
        var result = new LayoutResult(mutable, List.of(), List.of(), 100, 100);
        assertThrows(UnsupportedOperationException.class,
            () -> result.boxes().add(new ClassBox("B")));
    }

    @Test
    void dependenciesIsUnmodifiable() {
        var boxA = new ClassBox("A");
        var boxB = new ClassBox("B");
        var mutable = new ArrayList<Dependency>();
        mutable.add(new Dependency(boxA, boxB, DependencyType.COMPOSITION));
        var result = new LayoutResult(List.of(), mutable, List.of(), 100, 100);
        assertThrows(UnsupportedOperationException.class,
            () -> result.dependencies().add(new Dependency(boxA, boxB, DependencyType.COMPOSITION)));
    }

    @Test
    void constructorThrowsForNullPackageGroups() {
        assertThrows(NullPointerException.class,
            () -> new LayoutResult(List.of(), List.of(), null, 100, 100));
    }

    @Test
    void packageGroupsIsUnmodifiable() {
        var mutable = new ArrayList<PackageGroupBox>();
        mutable.add(new PackageGroupBox("p", 0, 0, 10, 10));
        var result = new LayoutResult(List.of(), List.of(), mutable, 100, 100);
        assertThrows(UnsupportedOperationException.class,
            () -> result.packageGroups().add(new PackageGroupBox("q", 0, 0, 10, 10)));
    }

    @Test
    void packageGroupsDefaultsToEmptyListInLegacyConstructor() {
        var result = new LayoutResult(List.of(), List.of(), 100, 100);
        assertTrue(result.packageGroups().isEmpty());
    }
}
