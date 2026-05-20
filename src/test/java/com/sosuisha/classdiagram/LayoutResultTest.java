package com.sosuisha.classdiagram;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LayoutResultTest {

    @Test
    void constructorThrowsForNullBoxes() {
        assertThrows(NullPointerException.class,
            () -> new LayoutResult(null, List.of(), 100, 100));
    }

    @Test
    void constructorThrowsForNullDependencies() {
        assertThrows(NullPointerException.class,
            () -> new LayoutResult(List.of(), null, 100, 100));
    }

    @Test
    void boxesIsUnmodifiable() {
        var mutable = new ArrayList<ClassBox>();
        mutable.add(new ClassBox("A"));
        var result = new LayoutResult(mutable, List.of(), 100, 100);
        assertThrows(UnsupportedOperationException.class,
            () -> result.boxes().add(new ClassBox("B")));
    }

    @Test
    void dependenciesIsUnmodifiable() {
        var boxA = new ClassBox("A");
        var boxB = new ClassBox("B");
        var mutable = new ArrayList<Dependency>();
        mutable.add(new Dependency(boxA, boxB, DependencyType.COMPOSITION));
        var result = new LayoutResult(List.of(), mutable, 100, 100);
        assertThrows(UnsupportedOperationException.class,
            () -> result.dependencies().add(new Dependency(boxA, boxB, DependencyType.COMPOSITION)));
    }
}
