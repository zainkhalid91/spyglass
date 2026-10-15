package io.github.zainkhalid91.spyglass.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

internal val prettyJson = Json { prettyPrint = true }

internal class PreviewEntry(
    val function: String,
    val name: String,
    val image: File?,
    val widthPx: Int,
    val heightPx: Int,
    val density: Double,
    val error: String?,
)

/** Reads the `previews.json` files the renderer writes under `build/spyglass/previews/`. */
internal object Manifests {
    private const val RELATIVE = "spyglass/previews/previews.json"
    private val skipped = setOf(".git", ".gradle", ".idea", ".kotlin", "node_modules")

    /** Manifests under [root] written at or after [since], without walking into build outputs. */
    fun find(root: File, since: Long): List<File> {
        val found = mutableListOf<File>()
        Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val name = dir.fileName?.toString() ?: return FileVisitResult.CONTINUE
                if (name in skipped) return FileVisitResult.SKIP_SUBTREE
                if (name == "build") {
                    val manifest = dir.resolve(RELATIVE).toFile()
                    if (manifest.isFile && manifest.lastModified() >= since) found += manifest
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException) = FileVisitResult.CONTINUE
        })
        return found.sortedBy { it.path }
    }

    fun read(manifest: File): List<PreviewEntry> {
        val root = Json.parseToJsonElement(manifest.readText()).jsonObject
        val density = root["density"]?.jsonPrimitive?.doubleOrNull ?: 2.0
        return root["previews"]?.jsonArray.orEmpty().map { element ->
            val p = element.jsonObject
            PreviewEntry(
                function = p.string("function").orEmpty(),
                name = p.string("name").orEmpty(),
                image = p.string("image")?.let { File(manifest.parentFile, it) },
                widthPx = p["widthPx"]?.jsonPrimitive?.intOrNull ?: 0,
                heightPx = p["heightPx"]?.jsonPrimitive?.intOrNull ?: 0,
                density = density,
                error = p.string("error"),
            )
        }
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
