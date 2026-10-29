package com.sosuisha.classdiagram.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class GenerateMojoTest {

    @TempDir
    Path tempDir;

    private GenerateMojo createMojo(String packageName) {
        var mojo = new GenerateMojo();
        mojo.classesDirectory = new File("samples/sns-client");
        mojo.packageName = packageName;
        mojo.outputFile = tempDir.resolve("out/diagram.svg").toFile();
        return mojo;
    }

    @Test
    void executeWritesSvgFile() throws Exception {
        var mojo = createMojo("com.example");
        mojo.fontFamily = "HackGen";
        mojo.picturesque = true;

        mojo.execute();

        var svg = Files.readString(mojo.outputFile.toPath());
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.contains("HackGen"));
        assertTrue(svg.contains("data-diagram-draw=\"box\""));
    }

    @Test
    void executeAppliesIntentionFile() throws Exception {
        var intention = tempDir.resolve("diagram.intention");
        Files.writeString(intention, "place TimelineServiceFake right of TimelineServiceImpl\n");
        var mojo = createMojo("com.example");
        mojo.intentionFile = intention.toFile();

        mojo.execute();

        assertTrue(Files.exists(mojo.outputFile.toPath()));
    }

    @Test
    void executeFailsForInvalidIntention() throws Exception {
        var intention = tempDir.resolve("diagram.intention");
        Files.writeString(intention, "connect A B\n");
        var mojo = createMojo("com.example");
        mojo.intentionFile = intention.toFile();

        assertThrows(MojoFailureException.class, mojo::execute);
    }

    @Test
    void executeFailsForMissingPackageDirectory() {
        var mojo = createMojo("com.does.not.exist");

        assertThrows(MojoFailureException.class, mojo::execute);
        assertFalse(Files.exists(mojo.outputFile.toPath()));
    }

    @Test
    void executeDoesNothingWhenSkipped() throws Exception {
        var mojo = createMojo("com.does.not.exist");
        mojo.skip = true;

        mojo.execute();

        assertFalse(Files.exists(mojo.outputFile.toPath()));
    }
}
