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
    void generateIncludesStereotypeLabelForInterfacesByDefault() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        assertTrue(svg.contains("«interface»"),
            "nameOnly keeps interface stereotype labels while omitting members and separators");
    }

    @Test
    void showDetailsReturnsSelf() {
        var gen = new ClassDiagramGenerator(20, 40, 20, 20, 60);
        assertSame(gen, gen.showDetails());
    }

    @Test
    void showDetailsIncludesStereotypeLabelForInterfaces() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .showDetails()
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        assertTrue(svg.contains("«interface»"),
            "showDetails must contain «interface» stereotype label for FixtureService");
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
    void picturesqueReturnsSelf() {
        var gen = new ClassDiagramGenerator(20, 40, 20, 20, 60);
        assertSame(gen, gen.picturesque(true));
    }

    @Test
    void strokeColorOptionsReturnSelf() {
        var gen = new ClassDiagramGenerator(20, 40, 20, 20, 60);
        assertSame(gen, gen.packageStrokeColor("#123456"));
        assertSame(gen, gen.classBoxStrokeColor("#234567"));
        assertSame(gen, gen.edgeColor("#345678"));
    }

    @Test
    void strokeColorOptionsThrowForNull() {
        var gen = new ClassDiagramGenerator(20, 40, 20, 20, 60);
        assertThrows(NullPointerException.class, () -> gen.packageStrokeColor(null));
        assertThrows(NullPointerException.class, () -> gen.classBoxStrokeColor(null));
        assertThrows(NullPointerException.class, () -> gen.edgeColor(null));
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
    void generateEmitsPackageShadowOnlyWhenPicturesqueEnabled() {
        var normalSvg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(30)
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        assertFalse(normalSvg.contains("data-diagram-draw=\"package-shadow\""));

        var picturesqueSvg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(30)
            .picturesque(true)
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        assertTrue(picturesqueSvg.contains("data-diagram-draw=\"package-shadow-solid\""));
        assertTrue(picturesqueSvg.contains("data-diagram-draw=\"package-shadow-dashed\""));
    }

    @Test
    void generateUsesConfiguredStrokeColors() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(30)
            .packageStrokeColor("#123456")
            .classBoxStrokeColor("#234567")
            .edgeColor("#345678")
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        assertTrue(svg.contains("stroke=\"#123456\""));
        assertTrue(svg.contains("stroke=\"#234567\""));
        assertTrue(svg.contains("stroke=\"#345678\""));
    }

    @Test
    void generateMakesClassBoxesBoldOnlyWhenPicturesqueEnabled() {
        var normalSvg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        assertFalse(normalSvg.contains("M 1,1"));

        var picturesqueSvg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .picturesque(true)
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        assertTrue(picturesqueSvg.contains("M 1,1"));
    }

    @Test
    void generateDoesNotEmitPackageGroupSvgByDefault() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        assertFalse(svg.contains("data-diagram-draw=\"package-group\""),
            "SVG should NOT contain package-group when option is disabled");
    }

    @Test
    void intentionReturnsSelf() {
        var gen = new ClassDiagramGenerator(20, 40, 20, 20, 60);
        assertSame(gen, gen.intention("place A below B"));
    }

    @Test
    void intentionFileReturnsSelf(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir)
            throws Exception {
        var file = tempDir.resolve("test.intention");
        java.nio.file.Files.writeString(file, "# empty");
        var gen = new ClassDiagramGenerator(20, 40, 20, 20, 60);
        assertSame(gen, gen.intentionFile(file));
    }

    @Test
    void intentionNullThrowsInGenerator() {
        assertThrows(NullPointerException.class,
            () -> new ClassDiagramGenerator(20, 40, 20, 20, 60).intention(null));
    }

    @Test
    void intentionFileNullThrowsInGenerator() {
        assertThrows(NullPointerException.class,
            () -> new ClassDiagramGenerator(20, 40, 20, 20, 60).intentionFile(null));
    }

    @Test
    void intentionIsAppliedDuringGenerate() {
        var svgNormal = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        var svgConstrained = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .intention("place FixtureOrder below FixtureItem")
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        assertTrue(svgNormal.startsWith("<svg"));
        assertTrue(svgConstrained.startsWith("<svg"));
        assertNotEquals(svgNormal, svgConstrained,
            "Constrained SVG must differ from normal SVG");
    }

    @Test
    void intentionFileOverridesDslWhenBothSet(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        var file = tempDir.resolve("test.intention");
        java.nio.file.Files.writeString(file, "place FixtureOrder below FixtureItem");
        var svgDslOnly = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .intention("# no constraint")
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        var svgFileOverride = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .intention("# no constraint")
            .intentionFile(file)
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixture");
        assertNotEquals(svgDslOnly, svgFileOverride,
            "intentionFile must override intention() when both are set");
    }
}
