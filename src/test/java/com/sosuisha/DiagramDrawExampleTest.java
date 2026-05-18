package com.sosuisha;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagramDrawExampleTest {

    @Test
    void outputExampleSvgFile() throws IOException {
        var svg = new SVGBuilder(500, 200)
                .add(new Box(50, 60, 150, 80))
                .add(new Box(300, 60, 150, 80))
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
}
