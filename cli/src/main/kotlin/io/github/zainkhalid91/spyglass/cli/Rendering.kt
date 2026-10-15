package io.github.zainkhalid91.spyglass.cli

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File

/** Renders a project's previews through its own Gradle wrapper. Shared by the CLI and the MCP server. */
internal object Rendering {
    /** Gradle properties the renderer understands, keyed by their friendly name. */
    val propertyNames = mapOf(
        "filter" to "spyglass.filter",
        "density" to "spyglass.density",
        "maxWidthDp" to "spyglass.maxWidthDp",
        "maxHeightDp" to "spyglass.maxHeightDp",
        "target" to "spyglass.target",
    )
    private val numericOptions = setOf("density", "maxWidthDp", "maxHeightDp")

    class Outcome(
        val task: String,
        /** The Gradle build's root directory; image paths in results are relative to it. */
        val root: File,
        val gradle: GradleBuild.Result,
        val manifests: List<File>,
        val previews: List<PreviewEntry>,
    ) {
        val succeeded get() = gradle.exitCode == 0
    }

    /**
     * @param start project or module directory; running from a module's directory renders that module.
     * @param options friendly-name options from [propertyNames], e.g. `filter` to `Card`.
     */
    fun render(start: File, module: String?, options: Map<String, String>, quiet: Boolean): Outcome {
        if (!start.isDirectory) throw CliError("project directory not found: $start")
        val build = GradleBuild.find(start)
        val resolvedModule = module ?: inferModule(build.settingsDir, start)
        val task = if (resolvedModule == null) "spyglassRender" else ":${resolvedModule.trim(':')}:spyglassRender"

        // Each renderer appends its manifest path here. The path is fixed per build, so Gradle's
        // configuration cache can be reused from one run to the next.
        val report = File(build.settingsDir, ".gradle/spyglass/rendered.txt")
        report.delete()

        val gradleArgs = buildList {
            add("-I"); add(BundledPlugin.initScript.path)
            add("-Dspyglass.pluginJar=${BundledPlugin.pluginJar.path}")
            add(task)
            add("--rerun") // Always render, so the manifest reflects this run.
            add("--console=plain")
            if (quiet) add("-q")
            add("-Pspyglass.report=${report.path}")
            for ((name, value) in options) {
                val property = propertyNames[name] ?: throw CliError("unknown render option '$name'")
                // Checked here so a typo fails in milliseconds instead of after a Gradle run.
                if (name in numericOptions && value.toDoubleOrNull()?.let { it > 0 } != true) {
                    throw CliError("$name must be a positive number, got '$value'")
                }
                add("-P$property=$value")
            }
        }

        val gradle = build.run(gradleArgs)
        if (gradle.exitCode != 0) return Outcome(task, build.settingsDir, gradle, emptyList(), emptyList())

        val manifests = if (report.isFile) report.readLines().filter { it.isNotBlank() }.distinct().map(::File).filter { it.isFile } else emptyList()
        return Outcome(task, build.settingsDir, gradle, manifests, manifests.flatMap(Manifests::read))
    }

    private fun inferModule(settingsDir: File, start: File): String? {
        if (start == settingsDir) return null
        val isProject = File(start, "build.gradle.kts").isFile || File(start, "build.gradle").isFile
        if (!isProject) return null
        return ":" + start.relativeTo(settingsDir).invariantSeparatorsPath.replace('/', ':')
    }

    /**
     * A compact result for agents and scripts: one entry per image, paths relative to `root`, and no
     * empty fields. Every field costs tokens when an agent reads it.
     */
    fun toJson(outcome: Outcome): JsonObject = buildJsonObject {
        put("root", outcome.root.path)
        put("rendered", outcome.previews.count { it.error == null })
        put("failed", outcome.previews.count { it.error != null })
        putJsonArray("previews") {
            for (p in outcome.previews) addJsonObject {
                put("preview", label(p))
                if (p.image != null) {
                    put("image", p.image.relativeToOrSelf(outcome.root).invariantSeparatorsPath)
                    put("px", "${p.widthPx}x${p.heightPx}")
                }
                p.error?.let { put("error", it) }
            }
        }
    }

    /** A short readable label such as `CardsKt.WeatherCardPreview [Dark]` or `AppKt.Row (Ann)`. */
    fun label(p: PreviewEntry): String {
        val owner = p.function.substringBeforeLast('.').substringAfterLast('.')
        return "$owner.${p.function.substringAfterLast('.')}" +
            (if (p.name.isEmpty()) "" else " [${p.name}]") +
            (p.parameter?.let { " ($it)" } ?: "")
    }
}
