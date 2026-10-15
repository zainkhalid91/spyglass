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
    |  spyglass mcp [--project <dir>]            Run as an MCP server on stdio, for AI agents
    |                                            (tools: render_previews, compare_with_design, diff_images)
    |      --project <dir>      Default project for tools (default: the server's working directory)
    |
    |  spyglass version
""".trimMargin()

fun main(args: Array<String>) {
    val code = try {
        run(args.toList())
    } catch (e: CliError) {
        System.err.println("spyglass: ${e.message}")
        2
    }
    exitProcess(code)
}

private fun run(args: List<String>): Int = when (args.firstOrNull()) {
    "render" -> RenderCommand.run(args.drop(1))
    "diff" -> DiffCommand.run(args.drop(1))
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
