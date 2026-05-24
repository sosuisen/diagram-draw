package com.sosuisha.classdiagram;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ClassDiagramGeneratorTest {

    @Test
    void generateThrowsForNullClassRoot() {
        assertThrows(NullPointerException.class,
            () -> new ClassDiagramGenerator(20, 40, 20, 20, 60).generate(null, "com.example"));
    }

    @Test
    void generateThrowsForNullPackageName() {
        assertThrows(NullPointerException.class,
            () -> new ClassDiagramGenerator(20, 40, 20, 20, 60)
                      .generate(Path.of("target/test-classes"), null));
    }

    @Test
    void generateReturnsEmptySvgForNonExistentPackage() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .generate(Path.of("target/test-classes"), "com.does.not.exist");
        assertTrue(svg.startsWith("<svg"));
    }

    @Test
    void fontFamilyThrowsForNull() {
        assertThrows(NullPointerException.class,
            () -> new ClassDiagramGenerator(20, 40, 20, 20, 60).fontFamily(null));
    }

    @Test
    void generateIncludesFontFamilyWhenSet() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .fontFamily("HackGen")
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        assertTrue(svg.contains("HackGen"));
    }

    @Test
    void generateProducesFullSvgForFixturePackage() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.contains("FixtureOrder"));
        assertTrue(svg.contains("FixtureItem"));
        assertTrue(svg.contains("FixtureCustomer"));
    }

    @Test
    void generateIncludesStereotypeLabelForInterfaces() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        assertTrue(svg.contains("«interface»"),
            "SVG must contain «interface» stereotype label for FixtureService");
    }

    @Test
    void generateIncludesCrossGroupDependencyArrowForFixdepPackage() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixdep");
        assertTrue(svg.contains("data-diagram-draw-type=\"dependency\""),
            "SVG must contain a cross-group DEPENDENCY arrow for fixdep package");
        assertTrue(svg.contains("stroke-dasharray"),
            "DEPENDENCY arrow must use a dashed line");
    }

    @Test
    void enableSubPackageGroupingThrowsForNegativeGap() {
        assertThrows(IllegalArgumentException.class,
            () -> new ClassDiagramGenerator(20, 40, 20, 20, 60).enableSubPackageGrouping(-1));
    }

    @Test
    void enableSubPackageGroupingReturnsSelf() {
        var gen = new ClassDiagramGenerator(20, 40, 20, 20, 60);
        assertSame(gen, gen.enableSubPackageGrouping(30));
    }

    @Test
    void generateEmitsPackageGroupSvgWhenSubPackageGroupingEnabled() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(30)
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        assertTrue(svg.contains("data-diagram-draw=\"package-group\""),
            "SVG should contain at least one package-group element");
        assertTrue(svg.contains("data-diagram-draw-name=\"sub\""),
            "SVG should label the 'sub' sub-package");
    }

    @Test
    void generateDoesNotEmitPackageGroupSvgByDefault() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        assertFalse(svg.contains("data-diagram-draw=\"package-group\""),
            "SVG should NOT contain package-group when option is disabled");
    }
}
