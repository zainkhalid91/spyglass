package io.github.zainkhalid91.spyglass.cli

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

    class Outcome(
        val task: String,
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
        val build = GradleBuild.find(start)
        val resolvedModule = module ?: inferModule(build.settingsDir, start)
        val task = if (resolvedModule == null) "spyglassRender" else "${resolvedModule.trimEnd(':')}:spyglassRender"

        val gradleArgs = buildList {
            add("-I"); add(BundledPlugin.initScript.path)
            add("-Dspyglass.pluginJar=${BundledPlugin.pluginJar.path}")
            add(task)
            add("--rerun") // Always render, so the manifest reflects this run.
            add("--console=plain")
            if (quiet) add("-q")
            for ((name, value) in options) {
                val property = propertyNames[name] ?: throw CliError("unknown render option '$name'")
                add("-P$property=$value")
            }
        }

        val startedAt = System.currentTimeMillis()
        val gradle = build.run(gradleArgs)
        if (gradle.exitCode != 0) return Outcome(task, gradle, emptyList(), emptyList())

        // File timestamps can be coarse, so allow a little slack.
        val manifests = Manifests.find(build.settingsDir, since = startedAt - 2_000)
        return Outcome(task, gradle, manifests, manifests.flatMap(Manifests::read))
    }

    private fun inferModule(settingsDir: File, start: File): String? {
        if (start == settingsDir) return null
        val isProject = File(start, "build.gradle.kts").isFile || File(start, "build.gradle").isFile
        if (!isProject) return null
        return ":" + start.relativeTo(settingsDir).invariantSeparatorsPath.replace('/', ':')
    }

    fun toJson(outcome: Outcome): JsonObject = buildJsonObject {
        put("tool", "spyglass")
        put("version", VERSION)
        put("rendered", outcome.previews.count { it.error == null })
        put("failed", outcome.previews.count { it.error != null })
        putJsonArray("manifests") { outcome.manifests.forEach { add(JsonPrimitive(it.path)) } }
        putJsonArray("previews") {
            for (p in outcome.previews) addJsonObject {
                put("function", p.function)
                put("name", p.name)
                put("status", if (p.error == null) "ok" else "error")
                put("image", p.image?.path?.let(::JsonPrimitive) ?: JsonNull)
                put("widthPx", p.widthPx)
                put("heightPx", p.heightPx)
                put("density", p.density)
                put("error", p.error?.let(::JsonPrimitive) ?: JsonNull)
            }
        }
    }

    /** A short readable label such as `CardsKt.WeatherCardPreview [Dark]`. */
    fun label(p: PreviewEntry): String {
        val owner = p.function.substringBeforeLast('.').substringAfterLast('.')
        return "$owner.${p.function.substringAfterLast('.')}" + if (p.name.isEmpty()) "" else " [${p.name}]"
    }
}
