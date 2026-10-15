package io.github.zainkhalid91.spyglass.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.util.Locale

private val renderFlags = mapOf("--filter" to "filter", "--target" to "target")

private fun renderFrom(args: Args, quiet: Boolean): Rendering.Outcome {
    val outcome = Rendering.render(
        start = File(args["--project"] ?: ".").canonicalFile,
        module = args["--module"],
        options = renderFlags.mapNotNull { (flag, name) -> args[flag]?.let { name to it } }.toMap(),
        quiet = quiet,
    )
    if (!outcome.succeeded) throw CliError("Gradle failed (exit ${outcome.gradle.exitCode}) while running ${outcome.task}")
    return outcome
}

/** `spyglass approve`: makes the current renders the baselines that `spyglass check` compares against. */
internal object ApproveCommand {
    fun run(raw: List<String>): Int {
        val args = Args.parse(raw, options = renderFlags.keys + setOf("--project", "--module"), switches = emptySet())
        val outcome = renderFrom(args, quiet = false)
        val library = Library(outcome.root)
        var count = 0
        for (p in outcome.previews) {
            val image = p.image ?: continue
            library.approve(image, Library.baselineKey(image, outcome.root), Rendering.label(p))
            println("  approved  ${Rendering.label(p)}")
            count++
        }
        library.save()
        Telemetry.count("cli.approve")
        println("spyglass: $count baseline(s) in ${library.baselinesDir}")
        return 0
    }
}

/** One preview's result in `spyglass check`. */
internal class CheckResult(
    val preview: String,
    /** same, changed, new (no baseline yet) or error (did not render). */
    val status: String,
    val similarity: Double? = null,
    val regions: List<DiffRegion> = emptyList(),
    val heatmap: File? = null,
    val error: String? = null,
    /** True when a failure here means something that used to work broke. */
    val regression: Boolean = false,
)

/**
 * `spyglass check`: renders every preview and compares it with its approved baseline. Exit code 1
 * when a preview changed beyond `--fail-under`, or a preview that had a baseline no longer renders.
 * New previews and previews that never rendered are reported, and fail only with `--strict`.
 */
internal object CheckCommand {
    fun run(raw: List<String>): Int {
        val args = Args.parse(
            raw,
            options = renderFlags.keys + setOf("--project", "--module", "--threshold", "--fail-under"),
            switches = setOf("--json", "--strict"),
        )
        val threshold = args.double("--threshold") ?: 0.1
        val failUnder = args.double("--fail-under") ?: 0.999
        if (threshold !in 0.0..1.0 || failUnder !in 0.0..1.0) throw CliError("--threshold and --fail-under must be between 0 and 1")
        val json = args.has("--json")

        val outcome = renderFrom(args, quiet = json)
        val results = evaluate(outcome.previews, outcome.root, Library(outcome.root), threshold, failUnder)
        val failed = results.filter { it.regression || (args.has("--strict") && it.status in setOf("new", "error")) }
        Telemetry.count("cli.check")
        Telemetry.count("check.changed", results.count { it.status == "changed" })

        if (json) printJson(results, failed.isEmpty()) else printHuman(results, failed.size)
        return if (failed.isEmpty()) 0 else 1
    }

    fun evaluate(previews: List<PreviewEntry>, root: File, library: Library, threshold: Double, failUnder: Double): List<CheckResult> =
        previews.map { p ->
            val label = Rendering.label(p)
            val image = p.image
            if (image == null) {
                // A preview that failed is only a regression if it has a baseline, i.e. it used to render.
                val hadBaseline = library.baselinesDir.walkTopDown().any { it.isFile && it.nameWithoutExtension.startsWith(stemOf(p)) }
                return@map CheckResult(label, "error", error = p.error, regression = hadBaseline)
            }
            val key = Library.baselineKey(image, root)
            val baseline = library.baseline(key) ?: return@map CheckResult(label, "new")
            val heatmap = File(image.parentFile, image.nameWithoutExtension + ".check.png")
            val diff = ImageDiff.compare(image, baseline, threshold, heatmap)
            if (diff.similarity >= failUnder) {
                heatmap.delete()
                CheckResult(label, "same", diff.similarity)
            } else {
                CheckResult(label, "changed", diff.similarity, diff.regions, heatmap, regression = true)
            }
        }

    /** The file stem the renderer would give this preview's image, used to find its baseline. */
    private fun stemOf(p: PreviewEntry): String {
        val cls = p.function.substringBeforeLast('.').substringAfterLast('.')
        val base = "$cls.${p.function.substringAfterLast('.')}"
        return (if (p.name.isEmpty()) base else "${base}_${p.name}").replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun printHuman(results: List<CheckResult>, failures: Int) {
        for (r in results) {
            val score = r.similarity?.let { String.format(Locale.ROOT, "%.2f%%", it * 100) } ?: ""
            when (r.status) {
                "same" -> println("  same     ${r.preview}  $score")
                "new" -> println("  new      ${r.preview}  (no baseline; run spyglass approve)")
                "error" -> println("  error    ${r.preview}  ${r.error}")
                else -> {
                    println("  CHANGED  ${r.preview}  $score  heatmap: ${r.heatmap}")
                    r.regions.take(3).forEach { println("             ${it.where} ${it.width}x${it.height}px at ${it.x},${it.y}") }
                }
            }
        }
        val counts = results.groupingBy { it.status }.eachCount()
        println("spyglass check: ${counts["same"] ?: 0} same, ${counts["changed"] ?: 0} changed, ${counts["new"] ?: 0} new, ${counts["error"] ?: 0} errors")
        if (failures > 0) println("spyglass check: FAILED ($failures)") else println("spyglass check: passed")
    }

    private fun printJson(results: List<CheckResult>, passed: Boolean) {
        val json = buildJsonObject {
            put("passed", passed)
            putJsonArray("previews") {
                for (r in results) addJsonObject {
                    put("preview", r.preview)
                    put("status", r.status)
                    r.similarity?.let { put("similarity", (it * 10_000).toInt() / 10_000.0) }
                    r.heatmap?.let { put("heatmap", it.path) }
                    r.error?.let { put("error", it) }
                    if (r.regions.isNotEmpty()) put("regions", JsonArray(r.regions.map { JsonPrimitive("${it.where} ${it.width}x${it.height}px") }))
                }
            }
        }
        println(Json.encodeToString(JsonElement.serializer(), json))
    }
}

/** `spyglass refs`: lists the project's UI memory. */
internal object RefsCommand {
    fun run(raw: List<String>): Int {
        val args = Args.parse(raw, options = setOf("--project", "--kind", "--filter"), switches = emptySet())
        val start = File(args["--project"] ?: ".").canonicalFile
        val root = runCatching { GradleBuild.find(start).settingsDir }.getOrDefault(start)
        val refs = Library(root).list(args["--kind"], args["--filter"])
        if (refs.isEmpty()) {
            println("spyglass: no references in ${File(root, ".spyglass/library")} yet. Render or compare previews to add some.")
            return 0
        }
        for (r in refs) {
            val sim = r.similarity?.let { String.format(Locale.ROOT, " vs design %.2f%%", it * 100) } ?: ""
            val note = r.note?.let { "  note: $it" } ?: ""
            println("${r.id}  ${r.kind.padEnd(7)} ${r.label}  ${r.width}x${r.height}  seen ${r.seen}x  ${r.palette.take(4).joinToString(",")}$sim$note")
        }
        println("spyglass: ${refs.size} reference(s)")
        return 0
    }
}

/** `spyglass telemetry`: turns opt-in usage sharing on or off and shows what would be sent. */
internal object TelemetryCommand {
    fun run(raw: List<String>): Int {
        when (raw.firstOrNull() ?: "status") {
            "on" -> {
                println(Telemetry.CONSENT_TEXT)
                Telemetry.setEnabled(true)
                println("\nUsage sharing is ON. Turn it off any time with `spyglass telemetry off`.")
            }
            "off" -> {
                Telemetry.setEnabled(false)
                println("Usage sharing is OFF, and the numbers collected on this machine were deleted.")
            }
            "show" -> println(if (Telemetry.counters().isEmpty()) "Nothing collected yet." else Telemetry.report())
            "send" -> println(
                when {
                    !Telemetry.enabled -> "Usage sharing is off. `spyglass telemetry on` turns it on."
                    Telemetry.counters().isEmpty() -> "Nothing to send yet."
                    Telemetry.send() -> "Sent. Thank you!"
                    else -> "Could not send. It needs the GitHub CLI (gh), logged in. The numbers are kept for next time."
                }
            )
            "status" -> {
                println("Usage sharing is ${if (Telemetry.enabled) "ON" else "OFF"}.")
                println("Last sent: ${Telemetry.lastSent() ?: "never"}. Counters waiting: ${Telemetry.counters().size}.")
                println("Commands: spyglass telemetry on | off | show | send")
            }
            else -> throw CliError("usage: spyglass telemetry [status | on | off | show | send]")
        }
        return 0
    }
}
