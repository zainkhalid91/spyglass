package io.github.zainkhalid91.spyglass.cli

import kotlinx.serialization.json.JsonElement
import java.io.File

internal object RenderCommand {
    private val flags = mapOf(
        "--filter" to "filter",
        "--density" to "density",
        "--max-width-dp" to "maxWidthDp",
        "--max-height-dp" to "maxHeightDp",
        "--target" to "target",
    )

    fun run(raw: List<String>): Int {
        val args = Args.parse(
            raw,
            options = flags.keys + setOf("--project", "--module"),
            switches = setOf("--json", "--fail-on-error"),
        )
        if (args.positionals.isNotEmpty()) throw CliError("render takes no positional arguments: ${args.positionals}")

        val json = args.has("--json")
        val outcome = Rendering.render(
            start = File(args["--project"] ?: ".").canonicalFile,
            module = args["--module"],
            options = flags.mapNotNull { (flag, name) -> args[flag]?.let { name to it } }.toMap(),
            quiet = json,
        )
        if (!outcome.succeeded) {
            System.err.println("spyglass: Gradle failed (exit ${outcome.gradle.exitCode}) while running ${outcome.task}")
            return outcome.gradle.exitCode
        }

        if (json) {
            println(prettyJson.encodeToString(JsonElement.serializer(), Rendering.toJson(outcome)))
        } else {
            printHuman(outcome)
        }
        return if (args.has("--fail-on-error") && outcome.previews.any { it.error != null }) 1 else 0
    }

    private fun printHuman(outcome: Rendering.Outcome) {
        val previews = outcome.previews
        val failed = previews.count { it.error != null }
        println("spyglass: rendered ${previews.size - failed}/${previews.size} previews")
        for (p in previews) {
            if (p.error == null) {
                println("  ok     ${Rendering.label(p)}  ${p.widthPx}x${p.heightPx}px  ${p.image}")
            } else {
                println("  error  ${Rendering.label(p)}  ${p.error}")
            }
        }
        outcome.manifests.forEach { println("manifest: $it") }
        if (outcome.manifests.isEmpty()) {
            println("spyglass: no manifest was written. Does the module have a JVM (desktop) target?")
        }
    }
}
