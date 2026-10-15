package io.github.zainkhalid91.spyglass.cli

import java.io.File
import kotlin.system.exitProcess

internal const val VERSION = "0.1.0"

/** A problem with how Spyglass was called; printed without a stack trace. */
internal class CliError(message: String) : Exception(message)

private val HELP = """
    |Spyglass $VERSION - see your Compose UI without an IDE.
    |
    |Usage:
    |  spyglass render [options]                 Render every @Preview to PNG
    |      --project <dir>      Project or module directory (default: current directory)
    |      --module <path>      Gradle module to render, e.g. :shared (default: inferred, or all)
    |      --filter <text>      Only previews whose function or name contains <text>
    |      --density <n>        Pixels per dp (default 2)
    |      --max-width-dp <n>   Canvas limit for previews without widthDp (default 1024)
    |      --max-height-dp <n>  Canvas limit for previews without heightDp (default 1024)
    |      --target <name>      Kotlin Multiplatform JVM target (default: detected)
    |      --fail-on-error      Exit with 1 if any preview fails
    |      --json               Print compact JSON to stdout (Gradle output goes to stderr)
    |
    |  spyglass diff <actual.png> <expected.png> [options]
    |                                            Compare a render with a design image
    |      --out <file>         Heatmap path (default: <actual>.diff.png)
    |      --threshold <0-1>    Colour difference that counts as a changed pixel (default 0.1)
    |      --density <n>        Pixels per dp, to report regions in dp (default 2)
    |      --fail-under <0-1>   Exit with 1 if similarity is below this
    |      --json               Print a JSON result to stdout
    |
    |  spyglass approve [--project, --module, --filter]
    |                                            Store the current renders as baselines (.spyglass/baselines)
    |  spyglass check [options]                  Compare every preview with its baseline (for CI)
    |      --fail-under <0-1>   Similarity below this counts as changed (default 0.999)
    |      --threshold <0-1>    Colour difference that counts as a changed pixel (default 0.1)
    |      --strict             Also fail on previews with no baseline or that never rendered
    |      --json               Print compact JSON to stdout
    |
    |  spyglass refs [--kind render|design] [--filter <text>]
    |                                            List the project's UI memory (.spyglass/library)
    |
    |  spyglass mcp [--project <dir>]            Run as an MCP server on stdio, for AI agents
    |                                            (tools: render_previews, compare_with_design, diff_images, references)
    |      --project <dir>      Default project for tools (default: the server's working directory)
    |
    |  spyglass telemetry [status|on|off|show|send]
    |                                            Opt-in anonymous usage numbers (off unless you turn it on)
    |  spyglass version
""".trimMargin()

fun main(args: Array<String>) {
    val command = args.firstOrNull()
    val code = try {
        // Interactive commands may ask once about usage sharing; agents (mcp) and CI never are.
        if (command in setOf("render", "diff", "check", "approve")) Telemetry.askOnce()
        run(args.toList())
    } catch (e: CliError) {
        System.err.println("spyglass: ${e.message}")
        2
    }
    if (command != "mcp" && command != "telemetry") {
        runCatching {
            Telemetry.flush()
            Telemetry.sendIfDue()
        }
    }
    exitProcess(code)
}

private fun run(args: List<String>): Int = when (args.firstOrNull()) {
    "render" -> RenderCommand.run(args.drop(1)).also { Telemetry.count("cli.render") }
    "diff" -> DiffCommand.run(args.drop(1)).also { Telemetry.count("cli.diff") }
    "approve" -> ApproveCommand.run(args.drop(1))
    "check" -> CheckCommand.run(args.drop(1))
    "refs" -> RefsCommand.run(args.drop(1))
    "telemetry" -> TelemetryCommand.run(args.drop(1))
    "mcp" -> {
        val options = Args.parse(args.drop(1), options = setOf("--project"), switches = emptySet())
        options["--project"]?.let { McpTools.defaultProject = File(it).absoluteFile }
        McpServer.run()
    }
    "version", "--version" -> {
        println("spyglass $VERSION")
        0
    }
    null, "help", "--help", "-h" -> {
        println(HELP)
        0
    }
    else -> throw CliError("unknown command '${args[0]}'. Run 'spyglass help'.")
}
