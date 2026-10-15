package io.github.zainkhalid91.spyglass.renderer

import java.io.File
import kotlin.system.exitProcess

internal const val VERSION = "0.1.0"

fun main(args: Array<String>) {
    System.setProperty("java.awt.headless", "true")
    val options = Options.parse(args)

    // Everything else on the classpath is searched only for multi-preview annotations (e.g. @PreviewLightDark).
    val scanned = options.scanRoots.map { it.canonicalFile }.toSet()
    val libraries = System.getProperty("java.class.path").orEmpty().split(File.pathSeparator)
        .filter { it.isNotBlank() }.map(::File).filter { it.exists() && it.canonicalFile !in scanned }
    val previews = PreviewScanner.scan(options.scanRoots, libraries)
        .filter { spec -> options.filter?.let(spec::matches) ?: true }

    // On a full run, remove images an earlier run wrote for previews that no longer exist. Only files
    // listed in the index are touched, so diff heatmaps and other files in the folder survive, and a
    // filtered run leaves the other previews' images alone.
    options.outDir.mkdirs()
    val index = File(options.outDir, ".spyglass-images")
    if (options.filter == null && index.isFile) {
        index.readLines().filter { it.isNotBlank() }.forEach { File(options.outDir, it).delete() }
    }

    val usedStems = mutableSetOf<String>()
    val results = previews.flatMap { spec ->
        val jobs = try {
            PreviewRenderer.jobs(spec)
        } catch (t: Throwable) {
            return@flatMap listOf(PreviewRenderer.failure(spec, t).also(::report))
        }
        jobs.map { job ->
            var stem = job.spec.fileStem
            var n = 2
            while (!usedStems.add(stem)) stem = "${job.spec.fileStem}_${n++}"
            PreviewRenderer.render(job, options, File(options.outDir, "$stem.png")).also(::report)
        }
    }

    val written = results.mapNotNull { it.file?.name }
    val indexed = if (options.filter == null || !index.isFile) written else (index.readLines() + written).distinct()
    index.writeText(indexed.filter { it.isNotBlank() }.joinToString("\n"))

    val manifest = File(options.outDir, "previews.json")
    Manifest.write(manifest, results, options)
    // Several modules may render in one Gradle run; each adds its own line.
    options.report?.let { report ->
        report.absoluteFile.parentFile?.mkdirs()
        report.appendText(manifest.absolutePath + "\n")
    }

    val failed = results.count { it.error != null }
    println()
    println("spyglass: rendered ${results.size - failed}/${results.size} previews -> ${options.outDir.absolutePath}")
    println("spyglass: manifest ${manifest.absolutePath}")
    if (previews.isEmpty()) println("spyglass: no @Preview functions found in ${options.scanRoots.joinToString()}")
    exitProcess(if (failed > 0 && options.failOnError) 1 else 0)
}

private fun report(r: RenderResult) {
    val label = r.spec.functionName + (if (r.spec.name.isEmpty()) "" else " [${r.spec.name}]") +
        (r.parameter?.let { " ($it)" } ?: "")
    if (r.error == null) {
        println("  [ok]    $label  ${r.widthPx}x${r.heightPx}px  ${r.durationMs}ms")
    } else {
        println("  [error] $label  ${r.error}")
    }
}
