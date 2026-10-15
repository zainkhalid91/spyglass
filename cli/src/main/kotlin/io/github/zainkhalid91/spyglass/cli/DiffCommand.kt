package io.github.zainkhalid91.spyglass.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

internal object DiffCommand {
    fun run(raw: List<String>): Int {
        val args = Args.parse(
            raw,
            options = setOf("--out", "--threshold", "--density", "--fail-under"),
            switches = setOf("--json"),
        )
        if (args.positionals.size != 2) {
            throw CliError("usage: spyglass diff <actual.png> <expected.png> [--out heatmap.png] [--threshold 0.1] [--json]")
        }
        val actual = File(args.positionals[0]).absoluteFile
        val expected = File(args.positionals[1]).absoluteFile
        val threshold = args.double("--threshold") ?: 0.1
        if (threshold !in 0.0..1.0) throw CliError("--threshold must be between 0 and 1")
        val density = args.double("--density") ?: 2.0
        if (density <= 0) throw CliError("--density must be a positive number")
        val heatmap = args["--out"]?.let { File(it).absoluteFile }
            ?: File(actual.parentFile, actual.nameWithoutExtension + ".diff.png")

        val result = ImageDiff.compare(actual, expected, threshold, heatmap)
        if (args.has("--json")) {
            println(Json.encodeToString(JsonElement.serializer(), toJson(result, density)))
        } else {
            printHuman(result, density)
        }

        val failUnder = args.double("--fail-under")
        return if (failUnder != null && result.similarity < failUnder) 1 else 0
    }

    private fun printHuman(r: DiffResult, density: Double) {
        println("spyglass diff: ${percent(r.similarity)} similar (${r.diffPixels} of ${r.width * r.height} px differ)")
        if (r.resized) println("  expected image was ${r.expectedWidth}x${r.expectedHeight}, scaled to ${r.width}x${r.height} to compare")
        if (r.stretched) println("  warning: ${STRETCHED}")
        println("  heatmap: ${r.heatmap}")
        if (r.regions.isEmpty()) {
            println("  no regions differ")
            return
        }
        println("  regions that differ, largest first (px, then dp at density $density):")
        r.regions.forEachIndexed { i, g ->
            println(
                "    ${i + 1}. ${g.where.padEnd(13)} x=${g.x} y=${g.y} ${g.width}x${g.height}px" +
                    "  (${dp(g.x, density)},${dp(g.y, density)} ${dp(g.width, density)}x${dp(g.height, density)}dp)" +
                    "  ${percent(g.mismatch)} of it changed"
            )
        }
    }

    private const val STRETCHED =
        "the design's aspect ratio differs from the render's, so it was stretched to compare. Export it at the preview's size for a fair result."

    /**
     * Compact on purpose, as agents read it: regions are `[x, y, width, height]` in dp, the unit
     * Compose code is written in, and `changed` is the share of each region's pixels that differ.
     */
    fun toJson(r: DiffResult, density: Double): JsonObject =
        buildJsonObject {
            put("similarity", round4(r.similarity))
            put("changedPx", r.diffPixels)
            put("size", "${r.width}x${r.height}")
            if (r.resized) put("designScaledFrom", "${r.expectedWidth}x${r.expectedHeight}")
            if (r.stretched) put("warning", STRETCHED)
            put("heatmap", r.heatmap.path)
            put("density", density)
            putJsonArray("regions") {
                for (g in r.regions) addJsonObject {
                    put("at", g.where)
                    put("dp", JsonArray(listOf(g.x, g.y, g.width, g.height).map { JsonPrimitive(dp(it, density)) }))
                    put("changed", round2(g.mismatch))
                }
            }
        }

    private fun dp(px: Int, density: Double) = (px / density).roundToInt()
    private fun round4(v: Double) = (v * 10_000).roundToInt() / 10_000.0
    private fun round2(v: Double) = (v * 100).roundToInt() / 100.0
    fun percent(v: Double): String = String.format(Locale.ROOT, "%.2f%%", v * 100)
}
