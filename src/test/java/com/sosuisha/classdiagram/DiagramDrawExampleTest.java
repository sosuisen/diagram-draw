package com.sosuisha.classdiagram;

import com.sosuisha.classdiagram.analyzer.ClassInfo;
import com.sosuisha.classdiagram.analyzer.ClassRelation;
import com.sosuisha.classdiagram.analyzer.ClassRelationSorter;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagramDrawExampleTest {

    @Test
    void outputExampleSvgFile() throws IOException {
        var box1 = new ClassBox("ClassA");
        box1.setPosition(50, 60);
        var box2 = new ClassBox("ClassB");
        box2.setPosition(300, 60);
        var svg = new SVGBuilder(500, 200)
                .add(box1)
                .add(box2)
                .build();

        var outputDir = Path.of("target/svg-output");
        Files.createDirectories(outputDir);
        var outputFile = outputDir.resolve("example.svg");
        Files.writeString(outputFile, svg);

        assertTrue(Files.exists(outputFile));
    }

    @Test
    void outputCompositionExampleSvgFile() throws IOException {
        var order = new ClassBox("Order",
                List.of("id: Long"),
                List.of("getId(): Long"));
        order.setPosition(15, 20);

        var item = new ClassBox("Item",
                List.of("name: String"),
                List.of("getName(): String"));
        item.setPosition(200, 20);

        var dependency = new Dependency(order, item, DependencyType.COMPOSITION);

        var svg = new SVGBuilder(400, 150)
                .add(order)
                .add(item)
                .add(dependency)
                .build();

        var outputDir = Path.of("target/svg-output");
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("composition-example.svg"), svg);
        assertTrue(Files.exists(outputDir.resolve("composition-example.svg")));
    }

    @Test
    void outputClassBoxNoFieldsExampleSvgFile() throws IOException {
        var box = new ClassBox("MyClass");
        box.setPosition(15, 20);
        var svg = new SVGBuilder(300, 200)
                .add(box)
                .build();

        var outputDir = Path.of("target/svg-output");
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("classbox-no-fields-example.svg"), svg);
        assertTrue(Files.exists(outputDir.resolve("classbox-no-fields-example.svg")));
    }

    @Test
    void outputThreeClassBoxExampleSvgFile() throws IOException {
        var order = new ClassBox("Order",
                List.of("id: Long"),
                List.of("getId(): Long"));
        order.setPosition(15, 75);

        var customer = new ClassBox("Customer");
        customer.setPosition(350, 20);

        var orderItem = new ClassBox("OrderItem");
        orderItem.setPosition(350, 130);

        var svg = new SVGBuilder(520, 220)
                .add(order)
                .add(customer)
                .add(orderItem)
                .add(new Dependency(order, customer, DependencyType.AGGREGATION))
                .add(new Dependency(order, orderItem, DependencyType.COMPOSITION))
                .build();

        var outputDir = Path.of("target/svg-output");
        Files.createDirectories(outputDir);
        var outputFile = outputDir.resolve("three-classbox-example.svg");
        Files.writeString(outputFile, svg);
        assertTrue(Files.exists(outputFile));
    }

    @Test
    void outputClassBoxExampleSvgFile() throws IOException {
        var box = new ClassBox("Order",
                List.of("id: Long", "status: String"),
                List.of("getId(): Long", "getStatus(): String"));
        box.setPosition(15, 20);
        var svg = new SVGBuilder(500, 400)
                .add(box)
                .build();

        var outputDir = Path.of("target/svg-output");
        Files.createDirectories(outputDir);
        var outputFile = outputDir.resolve("classbox-example.svg");
        Files.writeString(outputFile, svg);

        assertTrue(Files.exists(outputFile));
    }

    @Test
    void outputSamplesComExampleClassDiagramSvgFile() throws IOException {
        var svg = new ClassDiagramGenerator(30, 50, 30, 30, 60)
                .fontFamily("HackGen")
                .generate(Path.of("samples/sns-client"), "com.example");

        var outputDir = Path.of("target/svg-output");
        Files.createDirectories(outputDir);
        var outputFile = outputDir.resolve("samples-sns-client.svg");
        Files.writeString(outputFile, svg);

        assertTrue(Files.exists(outputFile));
    }

    @Test
    void outputSamplesComExampleClassDiagramSvgFileWithSubPackageGrouping() throws IOException {
        var svg = new ClassDiagramGenerator(30, 50, 30, 30, 60)
                .fontFamily("HackGen")
                .enableSubPackageGrouping(40)
                .generate(Path.of("samples/sns-client"), "com.example");

        var outputDir = Path.of("target/svg-output");
        Files.createDirectories(outputDir);
        var outputFile = outputDir.resolve("samples-sns-client-subpkg.svg");
        Files.writeString(outputFile, svg);

        assertTrue(Files.exists(outputFile));
    }

    @Test
    void outputSampleSnsClientPicturesqueSvgFile() throws IOException {
        var svg = new ClassDiagramGenerator(30, 50, 30, 30, 60)
                .fontFamily("HackGen")
                .enableSubPackageGrouping(40)
                .picturesque(true)
                .packageStrokeColor("#79754d")
                .packageFillColor("#ebdfc7")
                .edgeColor("#661d00")
                .interfaceFillColor("#c4ffee")
                .classFillColor("#ecffc4")
                .generate(Path.of("samples/sns-client"), "com.example");

        var outputDir = Path.of("target/svg-output");
        Files.createDirectories(outputDir);
        var outputFile = outputDir.resolve("sample-sns-client-picturesque.svg");
        Files.writeString(outputFile, svg);

        assertTrue(Files.exists(outputFile));
        assertTrue(svg.contains("data-diagram-draw=\"package-shadow-solid\""));
        assertTrue(svg.contains("data-diagram-draw=\"package-shadow-dashed\""));
    }

    @Test
    void outputSampleSnsClientPicturesqueArrangedSvgFile() throws IOException {
        var svg = new ClassDiagramGenerator(30, 50, 30, 30, 60)
                .fontFamily("HackGen")
                .enableSubPackageGrouping(40)
                .picturesque(true)
                .packageStrokeColor("#79754d")
                .packageFillColor("#ebdfc7")
                .edgeColor("#661d00")
                .interfaceFillColor("#c4ffee")
                .classFillColor("#ecffc4")
                .intention("""
                        place TimelineServiceFake right of TimelineServiceImpl
                        arrow FormViewModel UserService from right to bottom
                        arrow FormViewModel LoginService from right to bottom
                        """)
                .generate(Path.of("samples/sns-client"), "com.example");

        var outputDir = Path.of("target/svg-output");
        Files.createDirectories(outputDir);
        var outputFile = outputDir.resolve("sample-sns-client-picturesque-arranged.svg");
        Files.writeString(outputFile, svg);

        assertTrue(Files.exists(outputFile));
    }

    @Test
    void outputSampleSnsAdminClientPicturesqueArrangedSvgFile() throws IOException {
        var svg = new ClassDiagramGenerator(30, 50, 30, 30, 60)
                .fontFamily("HackGen")
                .enableSubPackageGrouping(40)
                .picturesque(true)
                .packageStrokeColor("#79754d")
                .packageFillColor("#ebdfc7")
                .edgeColor("#661d00")
                .interfaceFillColor("#c4ffee")
                .classFillColor("#ecffc4")
                .generate(Path.of("samples/sns-admin-client"), "com.example");

        var outputDir = Path.of("target/svg-output");
        Files.createDirectories(outputDir);
        var outputFile = outputDir.resolve("sample-sns-admin-client-picturesque-arranged.svg");
        Files.writeString(outputFile, svg);

        assertTrue(Files.exists(outputFile));
    }

    @Test
    void outputLongestPathReassignmentExampleSvgFile() throws IOException {
        // Controller→Service→Repository, Controller→Logger
        // Kahn: [[Controller], [Service, Logger], [Repository]]
        // Longest-path: [[Controller], [Service], [Logger, Repository]] ← Logger moves
        // down
        var pkg = "com.example";
        var controller = new ClassInfo(pkg, "Controller");
        var service = new ClassInfo(pkg, "Service");
        var repository = new ClassInfo(pkg, "Repository");
        var logger = new ClassInfo(pkg, "Logger");

        var relations = List.of(
                new ClassRelation(controller, service, DependencyType.COMPOSITION, false),
                new ClassRelation(service, repository, DependencyType.COMPOSITION, false),
                new ClassRelation(controller, logger, DependencyType.AGGREGATION, false));

        var layers = new ClassRelationSorter().sort(relations);
        var result = new ClassDiagramLayout(30, 50, 30, 30, 60).layout(layers, relations);
        var builder = new SVGBuilder(result.canvasWidth(), result.canvasHeight()).fontFamily("HackGen");
        result.boxes().forEach(builder::add);
        result.dependencies().forEach(builder::add);
        var svg = builder.build();

        var outputDir = Path.of("target/svg-output");
        Files.createDirectories(outputDir);
        var outputFile = outputDir.resolve("longest-path-example.svg");
        Files.writeString(outputFile, svg);

        assertTrue(Files.exists(outputFile));
    }

    @Test
    void outputGeneratedClassDiagramSvgFile() throws IOException {
        var svg = new ClassDiagramGenerator(30, 50, 30, 30, 60)
                .fontFamily("HackGen")
                .generate(Path.of("target/test-classes"),
                        "com.sosuisha.classdiagram.analyzer.fixture");

        var outputDir = Path.of("target/svg-output");
        Files.createDirectories(outputDir);
        var outputFile = outputDir.resolve("generated-class-diagram.svg");
        Files.writeString(outputFile, svg);

        assertTrue(Files.exists(outputFile));
    }
}
