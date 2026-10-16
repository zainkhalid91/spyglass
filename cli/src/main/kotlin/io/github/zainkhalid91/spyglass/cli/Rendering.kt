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
        "timeout" to "spyglass.timeout",
    )
    private val numericOptions = setOf("density", "maxWidthDp", "maxHeightDp", "timeout")

    class Outcome(
        val task: String,
        /** The Gradle build's root directory; image paths in results are relative to it. */
        val root: File,
        val gradle: GradleBuild.Result,
        val manifests: List<File>,
        val previews: List<PreviewEntry>,
        /** Library reference id of each rendered image. */
        val refs: Map<File, String> = emptyMap(),
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

        val started = System.nanoTime()
        val gradle = build.run(gradleArgs)
        Telemetry.count("render.runs")
        if (gradle.exitCode != 0) {
            Telemetry.count("render.gradleFailures")
            return Outcome(task, build.settingsDir, gradle, emptyList(), emptyList())
        }

        val manifests = if (report.isFile) report.readLines().filter { it.isNotBlank() }.distinct().map(::File).filter { it.isFile } else emptyList()
        val previews = manifests.flatMap(Manifests::read)
        Telemetry.count("render.previews", previews.size)
        previews.mapNotNull { it.error }.forEach { Telemetry.count("render.error." + Telemetry.errorCategory(it)) }
        Telemetry.count("render.seconds." + bucket((System.nanoTime() - started) / 1_000_000_000))
        return Outcome(task, build.settingsDir, gradle, manifests, previews, learn(build.settingsDir, previews))
    }

    /** Every render is remembered in the project's library, so later turns can refer to it cheaply. */
    private fun learn(root: File, previews: List<PreviewEntry>): Map<File, String> {
        val rendered = previews.filter { it.image != null }
        if (rendered.isEmpty()) return emptyMap()
        return try {
            val library = Library(root)
            val before = library.size
            val refs = rendered.associate { p -> p.image!! to library.record(p.image, Library.KIND_RENDER, preview = label(p)).id }
            library.save()
            Telemetry.count("library.newRefs", library.size - before)
            refs
        } catch (e: Exception) {
            // The library is a convenience; a problem with it must never break rendering.
            System.err.println("spyglass: could not update the UI library: ${e.message}")
            emptyMap()
        }
    }

    private fun bucket(seconds: Long) = when {
        seconds < 10 -> "under-10"
        seconds < 30 -> "10-30"
        seconds < 120 -> "30-120"
        else -> "over-120"
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
    fun toJson(outcome: Outcome, unchanged: Set<String> = emptySet()): JsonObject = buildJsonObject {
        put("root", outcome.root.path)
        put("rendered", outcome.previews.count { it.error == null })
        put("failed", outcome.previews.count { it.error != null })
        putJsonArray("previews") {
            for (p in outcome.previews) addJsonObject {
                put("preview", label(p))
                if (p.image != null) {
                    put("image", p.image.relativeToOrSelf(outcome.root).invariantSeparatorsPath)
                    put("px", "${p.widthPx}x${p.heightPx}")
                    outcome.refs[p.image]?.let { ref ->
                        put("ref", ref)
                        if (ref in unchanged) put("unchanged", true)
                    }
                }
                p.error?.let { put("error", it) }
                p.warning?.let { put("warning", it) }
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
