package io.github.zainkhalid91.spyglass.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Copies the renderer jar bundled inside the plugin to the build directory, so JavaExec can run it. */
public abstract class ExtractRenderer extends DefaultTask {
    @Input
    public abstract Property<String> getRendererChecksum();

    @OutputFile
    public abstract RegularFileProperty getRendererJar();

    @TaskAction
    public void extract() throws IOException {
        File target = getRendererJar().get().getAsFile();
        target.getParentFile().mkdirs();
        try (InputStream in = ExtractRenderer.class.getResourceAsStream(SpyglassPlugin.RENDERER_RESOURCE)) {
            if (in == null) throw new GradleException("renderer.jar is missing from the Spyglass plugin.");
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
