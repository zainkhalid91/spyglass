package io.github.zainkhalid91.spyglass.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Adds {@code spyglassRender}, which renders every {@code @Preview} composable in the module's JVM
 * (desktop) target to PNG files plus a {@code previews.json} manifest, without an IDE.
 *
 * <p>The plugin avoids Kotlin Gradle Plugin types on purpose: it finds the JVM target through
 * configuration attributes, so it also works when applied from an init script.
 */
public final class SpyglassPlugin implements Plugin<Project> {
    public static final String RENDER_TASK = "spyglassRender";
    static final String RENDERER_RESOURCE = "/io/github/zainkhalid91/spyglass/renderer.jar";
    private static final String RENDERER_MAIN = "io.github.zainkhalid91.spyglass.renderer.MainKt";
    private static final String PLATFORM_ATTRIBUTE = "org.jetbrains.kotlin.platform.type";
    private static final String RUNTIME_SUFFIX = "RuntimeClasspath";

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("org.jetbrains.kotlin.multiplatform", p -> register(project, true));
        project.getPluginManager().withPlugin("org.jetbrains.kotlin.jvm", p -> register(project, false));
    }

    private void register(Project project, boolean multiplatform) {
        TaskContainer tasks = project.getTasks();
        if (tasks.getNames().contains(RENDER_TASK)) return;

        Provider<RegularFile> rendererJar = project.getLayout().getBuildDirectory().file("spyglass/renderer.jar");
        TaskProvider<ExtractRenderer> extract = tasks.register("spyglassExtractRenderer", ExtractRenderer.class, task -> {
            task.setDescription("Unpacks the Spyglass renderer used by " + RENDER_TASK + ".");
            task.getRendererChecksum().set(rendererChecksum());
            task.getRendererJar().set(rendererJar);
        });

        tasks.register(RENDER_TASK, JavaExec.class, task -> {
            task.setGroup("spyglass");
            task.setDescription("Renders every @Preview composable to PNG, without an IDE.");
            task.getMainClass().set(RENDERER_MAIN);

            String target = multiplatform ? findJvmTarget(project) : "";
            if (target == null) {
                // A build-wide `spyglassRender` must not fail because one module (say, Android-only) has nothing to render.
                project.getLogger().lifecycle("spyglass: skipping {} (no JVM target; add jvm(\"desktop\") to render its previews)", project.getPath());
                task.setEnabled(false);
                return;
            }
            String jarTaskName = target.isEmpty() ? "jar" : target + "Jar";
            String runtimeName = target.isEmpty() ? "runtimeClasspath" : target + RUNTIME_SUFFIX;
            TaskProvider<Jar> jar = tasks.named(jarTaskName, Jar.class);
            Provider<RegularFile> classesJar = jar.flatMap(Jar::getArchiveFile);
            Directory outDir = project.getLayout().getBuildDirectory().dir("spyglass/previews").get();

            task.dependsOn(extract, jar);
            task.classpath(rendererJar, classesJar, project.getConfigurations().getByName(runtimeName));
            task.jvmArgs("-Djava.awt.headless=true");

            List<String> args = new ArrayList<>(List.of(
                    "--scan", classesJar.get().getAsFile().getAbsolutePath(),
                    "--out", outDir.getAsFile().getAbsolutePath()));
            addOption(project, args, "spyglass.filter", "--filter");
            addOption(project, args, "spyglass.density", "--density");
            addOption(project, args, "spyglass.maxWidthDp", "--max-width-dp");
            addOption(project, args, "spyglass.maxHeightDp", "--max-height-dp");
            addOption(project, args, "spyglass.report", "--report");
            addOption(project, args, "spyglass.timeout", "--timeout");
            if (Boolean.parseBoolean(String.valueOf(project.findProperty("spyglass.failOnError")))) {
                args.add("--fail-on-error");
            }
            task.args(args);
            task.getOutputs().dir(outDir);
        });
    }

    /**
     * Picks the Kotlin Multiplatform JVM target, e.g. {@code desktop} for {@code jvm("desktop")}, or
     * returns null when the module has none. With several, prefers "desktop", then "jvm", then by name.
     */
    private static String findJvmTarget(Project project) {
        List<String> candidates = new ArrayList<>();
        for (Configuration configuration : project.getConfigurations()) {
            String name = configuration.getName();
            if (!name.endsWith(RUNTIME_SUFFIX) || name.contains("Test")) continue;
            String prefix = name.substring(0, name.length() - RUNTIME_SUFFIX.length());
            if (prefix.isEmpty() || !isJvmPlatform(configuration.getAttributes())) continue;
            if (project.getTasks().getNames().contains(prefix + "Jar")) candidates.add(prefix);
        }

        Object override = project.findProperty("spyglass.target");
        if (override != null && !override.toString().isBlank()) {
            String target = override.toString();
            if (!candidates.contains(target)) {
                throw new GradleException("spyglass.target=" + target + " is not a JVM target of " + project.getPath()
                        + ". JVM targets found: " + (candidates.isEmpty() ? "none" : String.join(", ", candidates)) + ".");
            }
            return target;
        }
        candidates.sort((a, b) -> rank(a) != rank(b) ? Integer.compare(rank(a), rank(b)) : a.compareTo(b));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static int rank(String target) {
        return target.equals("desktop") ? 0 : target.equals("jvm") ? 1 : 2;
    }

    private static boolean isJvmPlatform(AttributeContainer attributes) {
        for (Attribute<?> key : attributes.keySet()) {
            if (key.getName().equals(PLATFORM_ATTRIBUTE)) {
                return "jvm".equals(String.valueOf(attributes.getAttribute(key)));
            }
        }
        return false;
    }

    private static void addOption(Project project, List<String> args, String property, String flag) {
        Object value = project.findProperty(property);
        if (value != null && !value.toString().isBlank()) {
            args.add(flag);
            args.add(value.toString());
        }
    }

    /** Lets Gradle re-extract the renderer whenever the plugin ships a different one. */
    private static String rendererChecksum() {
        try (InputStream in = SpyglassPlugin.class.getResourceAsStream(RENDERER_RESOURCE)) {
            if (in == null) throw new GradleException("renderer.jar is missing from the Spyglass plugin.");
            CRC32 crc = new CRC32();
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = in.read(buffer)) != -1; ) crc.update(buffer, 0, n);
            return Long.toHexString(crc.getValue());
        } catch (IOException e) {
            throw new GradleException("Could not read the Spyglass renderer.", e);
        }
    }
}
