package io.github.zainkhalid91.spyglass.renderer

import java.io.File
import kotlin.system.exitProcess

internal data class Options(
    val scanRoots: List<File>,
    val outDir: File,
    val density: Float,
    val maxWidthDp: Int,
    val maxHeightDp: Int,
    val filter: String?,
    val failOnError: Boolean,
    /** A file the renderer appends its manifest path to, so callers find it without searching. */
    val report: File?,
    /** Seconds one preview may take before it is reported as stuck. */
    val timeoutSec: Int = 30,
) {
    companion object {
        fun parse(args: Array<String>): Options {
            val scanRoots = mutableListOf<File>()
            var outDir: File? = null
            var density = 2f
            var maxWidthDp = 1024
            var maxHeightDp = 1024
            var filter: String? = null
            var failOnError = false
            var report: File? = null
            var timeoutSec = 30

            var i = 0
            fun value(): String = args.getOrNull(++i) ?: usage("Missing value for ${args[i - 1]}")
            fun positive(flag: String, raw: String): Float =
                raw.toFloatOrNull()?.takeIf { it > 0f } ?: usage("$flag must be a positive number, got '$raw'")
            while (i < args.size) {
                when (val arg = args[i]) {
                    "--scan" -> scanRoots += File(value())
                    "--out" -> outDir = File(value())
                    "--density" -> density = positive(arg, value())
                    "--max-width-dp" -> maxWidthDp = positive(arg, value()).toInt()
                    "--max-height-dp" -> maxHeightDp = positive(arg, value()).toInt()
                    "--filter" -> filter = value().ifBlank { null }
                    "--report" -> report = File(value())
                "--timeout" -> timeoutSec = positive(arg, value()).toInt().coerceAtLeast(1)
                    "--fail-on-error" -> failOnError = true
                    else -> usage("Unknown argument: $arg")
                }
                i++
            }

            if (scanRoots.isEmpty()) usage("At least one --scan <jar or classes dir> is required")
            return Options(
                scanRoots = scanRoots,
                outDir = outDir ?: usage("--out <dir> is required"),
                density = density,
                maxWidthDp = maxWidthDp,
                maxHeightDp = maxHeightDp,
                filter = filter,
                failOnError = failOnError,
                report = report,
                timeoutSec = timeoutSec,
            )
        }

        private fun usage(problem: String): Nothing {
            System.err.println("spyglass: $problem")
            System.err.println(
                "usage: --scan <path> [--scan <path>...] --out <dir> [--density 2] [--max-width-dp 1024] " +
                    "[--max-height-dp 1024] [--filter <text>] [--report <file>] [--timeout 30] [--fail-on-error]"
            )
            exitProcess(2)
        }
    }
}
