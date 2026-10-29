package com.sosuisha.classdiagram.maven;

import com.sosuisha.classdiagram.ClassDiagramGenerator;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Generates a hand-drawn style UML class diagram as SVG from compiled classes.
 *
 * <p>The goal reads the {@code .class} files of one package and its subpackages.
 * It detects composition, aggregation, realization and dependency relations.
 * Run {@code compile} before this goal.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true)
public class GenerateMojo extends AbstractMojo {

    // Fields are package-private so that tests can set them directly.
    // Maven injects them by reflection, so visibility does not matter.

    /**
     * Root directory of the compiled classes.
     */
    @Parameter(property = "classdiagram.classesDirectory",
            defaultValue = "${project.build.outputDirectory}", required = true)
    File classesDirectory;

    /**
     * Package to draw, such as {@code com.example}. Subpackages are included.
     */
    @Parameter(property = "classdiagram.package", required = true)
    String packageName;

    /**
     * Output SVG file.
     */
    @Parameter(property = "classdiagram.output",
            defaultValue = "${project.build.directory}/classdiagram.svg", required = true)
    File outputFile;

    /**
     * File that holds layout hints in the intention DSL.
     * Write one statement per line. Lines that start with {@code #} are comments.
     * {@code place A below B} puts class A below class B.
     * You can also use {@code above}, {@code right of} and {@code left of}.
     * {@code arrow A B from right to top} sets the sides where the arrow from A to B starts and ends.
     * The sides are {@code top}, {@code bottom}, {@code left} and {@code right}.
     * The {@code to} part is optional.
     */
    @Parameter(property = "classdiagram.intentionFile")
    File intentionFile;

    /**
     * Horizontal gap between class boxes in the same layer, in pixels.
     */
    @Parameter(property = "classdiagram.horizontalGap", defaultValue = "30")
    int horizontalGap = 30;

    /**
     * Vertical gap between layers, in pixels.
     */
    @Parameter(property = "classdiagram.verticalGap", defaultValue = "50")
    int verticalGap = 50;

    /**
     * Left and right padding of the canvas, in pixels.
     */
    @Parameter(property = "classdiagram.canvasPaddingX", defaultValue = "30")
    int canvasPaddingX = 30;

    /**
     * Top and bottom padding of the canvas, in pixels.
     */
    @Parameter(property = "classdiagram.canvasPaddingY", defaultValue = "30")
    int canvasPaddingY = 30;

    /**
     * Horizontal gap between groups of classes that have no relation to each other, in pixels.
     */
    @Parameter(property = "classdiagram.groupGap", defaultValue = "60")
    int groupGap = 60;

    /**
     * Gap between subpackage frames, in pixels.
     * A negative value turns off subpackage frames.
     */
    @Parameter(property = "classdiagram.packageGap", defaultValue = "40")
    int packageGap = 40;

    /**
     * Font family of the text, such as {@code HackGen}.
     */
    @Parameter(property = "classdiagram.fontFamily")
    String fontFamily;

    /**
     * Fill color of class boxes, such as {@code #ecffc4}.
     */
    @Parameter(property = "classdiagram.classFillColor")
    String classFillColor;

    /**
     * Fill color of interface boxes, such as {@code #c4ffee}.
     */
    @Parameter(property = "classdiagram.interfaceFillColor")
    String interfaceFillColor;

    /**
     * Fill color of subpackage frames.
     */
    @Parameter(property = "classdiagram.packageFillColor")
    String packageFillColor;

    /**
     * Line color of subpackage frames.
     */
    @Parameter(property = "classdiagram.packageStrokeColor")
    String packageStrokeColor;

    /**
     * Line color of class boxes.
     */
    @Parameter(property = "classdiagram.classBoxStrokeColor")
    String classBoxStrokeColor;

    /**
     * Color of the relation lines.
     */
    @Parameter(property = "classdiagram.edgeColor")
    String edgeColor;

    /**
     * Shows fields and methods in class boxes. By default, only class names are shown.
     */
    @Parameter(property = "classdiagram.showDetails", defaultValue = "false")
    boolean showDetails;

    /**
     * Draws double lines and shadows for a more hand-drawn look.
     */
    @Parameter(property = "classdiagram.picturesque", defaultValue = "false")
    boolean picturesque;

    // Read-only: the wobble is always derived from the project coordinates.
    @Parameter(defaultValue = "${project.groupId}", readonly = true, required = true)
    String groupId;

    @Parameter(defaultValue = "${project.artifactId}", readonly = true, required = true)
    String artifactId;

    /**
     * Skips this goal.
     */
    @Parameter(property = "classdiagram.skip", defaultValue = "false")
    boolean skip;

    /**
     * Generates the class diagram and writes it to {@link #outputFile}.
     *
     * @throws MojoFailureException if the package directory does not exist,
     *                              or if the diagram cannot be generated, for example because of an invalid intention file
     * @throws MojoExecutionException if the SVG file cannot be written
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping class diagram generation");
            return;
        }
        verifyPackageDirectoryExists();
        writeSvg(generateSvg());
        getLog().info("Class diagram written to " + outputFile);
    }

    private void verifyPackageDirectoryExists() throws MojoFailureException {
        var packageDir = classesDirectory.toPath().resolve(packageName.replace('.', '/'));
        // Without this check, running before compile would silently write an empty SVG.
        if (!Files.isDirectory(packageDir)) {
            throw new MojoFailureException("Package directory not found: " + packageDir
                    + ". Run 'mvn compile' first, or check 'classdiagram.package'.");
        }
    }

    private String generateSvg() throws MojoFailureException {
        try {
            return createGenerator().generate(classesDirectory.toPath(), packageName);
        } catch (UncheckedIOException e) {
            throw new MojoFailureException("Failed to read intention file: " + intentionFile, e);
        } catch (RuntimeException e) {
            throw new MojoFailureException("Failed to generate class diagram: " + e.getMessage(), e);
        }
    }

    private ClassDiagramGenerator createGenerator() {
        var generator = new ClassDiagramGenerator(
                horizontalGap, verticalGap, canvasPaddingX, canvasPaddingY, groupGap)
                .picturesque(picturesque)
                // String.hashCode() is defined by the Java spec, so every JDK and OS
                // gives the same wobble for the same project.
                .seed((groupId + ":" + artifactId).hashCode());
        if (packageGap >= 0) {
            generator.enableSubPackageGrouping(packageGap);
        }
        if (showDetails) {
            generator.showDetails();
        }
        if (fontFamily != null) {
            generator.fontFamily(fontFamily);
        }
        if (classFillColor != null) {
            generator.classFillColor(classFillColor);
        }
        if (interfaceFillColor != null) {
            generator.interfaceFillColor(interfaceFillColor);
        }
        if (packageFillColor != null) {
            generator.packageFillColor(packageFillColor);
        }
        if (packageStrokeColor != null) {
            generator.packageStrokeColor(packageStrokeColor);
        }
        if (classBoxStrokeColor != null) {
            generator.classBoxStrokeColor(classBoxStrokeColor);
        }
        if (edgeColor != null) {
            generator.edgeColor(edgeColor);
        }
        if (intentionFile != null) {
            generator.intentionFile(intentionFile.toPath());
        }
        return generator;
    }

    private void writeSvg(String svg) throws MojoExecutionException {
        Path output = outputFile.toPath().toAbsolutePath();
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, svg);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write " + output, e);
        }
    }
}
