package com.sosuisha.classdiagram;

import org.junit.jupiter.api.Test;
import com.sosuisha.classdiagram.intention.IntentionParseException;
import java.nio.file.Path;
import java.util.regex.Pattern;
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

    @Test
    void generateSucceedsForClassWithStaticFieldOfOwnType() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .generate(Path.of("target/test-classes"),
                      "com.sosuisha.classdiagram.analyzer.fixself");
        assertTrue(svg.contains("FixtureSelfSettings"));
    }

    private static final String FIXISO_PKG = "com.sosuisha.classdiagram.analyzer.fixiso";

    @Test
    void generateDrawsClassesWithoutRelations() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .generate(Path.of("target/test-classes"), FIXISO_PKG);
        assertTrue(svg.contains("data-diagram-draw-name=\"FixtureIsoModel\""));
        assertTrue(svg.contains("data-diagram-draw-name=\"FixtureIsoHelper\""));
    }

    @Test
    void generateDrawsDependencyArrowsToAndAmongClassesWithoutRelations() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .generate(Path.of("target/test-classes"), FIXISO_PKG);
        // Repository -> IsoModel, Repository -> IsoItem, HelperUser -> Helper
        assertEquals(3, countOccurrences(svg, "data-diagram-draw-type=\"dependency\""));
    }

    @Test
    void generateDrawsClassesWithoutRelationsWithSubPackageGrouping() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(20)
            .generate(Path.of("target/test-classes"), FIXISO_PKG);
        assertTrue(svg.contains("data-diagram-draw-name=\"FixtureIsoModel\""));
    }

    @Test
    void generateDrawsClassesWhenPackageHasNoRelationAtAll() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .generate(Path.of("target/test-classes"), "com.sosuisha.classdiagram.analyzer.fixnorel");
        assertTrue(svg.contains("data-diagram-draw-name=\"FixtureNoRelA\""));
        assertTrue(svg.contains("data-diagram-draw-name=\"FixtureNoRelB\""));
    }

    private static int countOccurrences(String text, String pattern) {
        int count = 0;
        for (int i = text.indexOf(pattern); i >= 0; i = text.indexOf(pattern, i + 1)) {
            count++;
        }
        return count;
    }

    @Test
    void generateDrawsOneFrameForPackageSharedByConnectedComponents() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(20)
            .generate(Path.of("target/test-classes"), "com.sosuisha.classdiagram.analyzer.fixmerge");
        assertEquals(1, countOccurrences(svg,
            "data-diagram-draw=\"package-group\" data-diagram-draw-name=\"model\""));
        assertEquals(1, countOccurrences(svg,
            "data-diagram-draw=\"package-group\" data-diagram-draw-name=\"service\""));
    }

    @Test
    void generatePutsClassWithoutRelationsInFrameOfItsPackage() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(20)
            .generate(Path.of("target/test-classes"), "com.sosuisha.classdiagram.analyzer.fixmerge");
        int[] frame = translateOf(svg, "model");
        int[] user = translateOf(svg, "FixtureMergeUser");
        assertTrue(user[0] > frame[0] && user[1] > frame[1],
            "FixtureMergeUser must be inside the model frame");
    }

    private static int[] translateOf(String svg, String name) {
        var matcher = Pattern.compile(
            "data-diagram-draw-name=\"" + Pattern.quote(name) + "\" transform=\"translate\\((\\d+),(\\d+)\\)\"")
            .matcher(svg);
        assertTrue(matcher.find(), "element not found: " + name);
        return new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
    }

    @Test
    void omitArrowsRemovesAllLinesOfTheClass() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .intention("omit arrows of FixtureIsoRepository")
            .generate(Path.of("target/test-classes"), FIXISO_PKG);
        // Only HelperUser -> Helper remains. Repository's composition and two dependencies are gone.
        assertEquals(1, countOccurrences(svg, "data-diagram-draw=\"dependency\""));
        assertTrue(svg.contains("data-diagram-draw-name=\"FixtureIsoStore\""),
            "omitting arrows must not remove the boxes at the other end");
    }

    @Test
    void omitArrowsMarksTheClassBox() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .intention("omit arrows of FixtureIsoRepository")
            .generate(Path.of("target/test-classes"), FIXISO_PKG);
        var box = svg.substring(svg.indexOf("data-diagram-draw-name=\"FixtureIsoRepository\""));
        assertTrue(box.substring(0, box.indexOf("</g>")).contains("{arrows omitted}"));
    }

    @Test
    void omitArrowsOfUnknownClassThrows() {
        var generator = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .intention("omit arrows of NoSuchClass");
        assertThrows(IntentionParseException.class,
            () -> generator.generate(Path.of("target/test-classes"), FIXISO_PKG));
    }

    @Test
    void arrowConstraintOnOmittedClassThrows() {
        var generator = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .intention("""
                omit arrows of FixtureIsoHelper
                arrow FixtureIsoHelperUser FixtureIsoHelper from bottom
                """);
        var ex = assertThrows(IntentionParseException.class,
            () -> generator.generate(Path.of("target/test-classes"), FIXISO_PKG));
        assertEquals(2, ex.lineNumber());
    }

    @Test
    void generatePutsPackageReferencedBySiblingsInRightColumn() {
        var svg = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .enableSubPackageGrouping(20)
            .generate(Path.of("target/test-classes"), "com.sosuisha.classdiagram.analyzer.fixcol");
        int coreX = translateOf(svg, "core")[0];
        int uiRight = translateOf(svg, "FixtureColView")[0] + new ClassBox("FixtureColView").width();
        int infraRight = translateOf(svg, "FixtureColRepositoryImpl")[0]
            + new ClassBox("FixtureColRepositoryImpl").width();
        assertTrue(coreX > uiRight, "core must be right of ui");
        assertTrue(coreX > infraRight, "core must be right of infra");
    }

    @Test
    void seedChangesWobbleOfBoxesAndPackageFrames() {
        var a = new ClassDiagramGenerator(20, 40, 20, 20, 60).enableSubPackageGrouping(20).seed(1)
            .generate(Path.of("target/test-classes"), "com.sosuisha.classdiagram.analyzer.fixmerge");
        var b = new ClassDiagramGenerator(20, 40, 20, 20, 60).enableSubPackageGrouping(20).seed(2)
            .generate(Path.of("target/test-classes"), "com.sosuisha.classdiagram.analyzer.fixmerge");
        assertNotEquals(boxPart(a, "FixtureMergeUser"), boxPart(b, "FixtureMergeUser"));
        assertNotEquals(packagePart(a, "model"), packagePart(b, "model"));
    }

    @Test
    void seedZeroIsTheDefault() {
        var plain = new ClassDiagramGenerator(20, 40, 20, 20, 60)
            .generate(Path.of("target/test-classes"), FIXISO_PKG);
        var seeded = new ClassDiagramGenerator(20, 40, 20, 20, 60).seed(0)
            .generate(Path.of("target/test-classes"), FIXISO_PKG);
        assertEquals(plain, seeded);
    }

    private static String boxPart(String svg, String name) {
        var start = svg.indexOf("data-diagram-draw=\"box\" data-diagram-draw-type=\"class\" data-diagram-draw-name=\"" + name + "\"");
        return svg.substring(start, svg.indexOf("</g>", start));
    }

    private static String packagePart(String svg, String label) {
        var start = svg.indexOf("data-diagram-draw=\"package-group\" data-diagram-draw-name=\"" + label + "\"");
        return svg.substring(start, svg.indexOf("</g>", start));
    }
}
