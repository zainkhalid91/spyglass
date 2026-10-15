package io.github.zainkhalid91.spyglass.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import javax.imageio.ImageIO

/**
 * The project's UI memory, kept in `<project>/.spyglass/`:
 * - `library/` remembers every unique render and design Spyglass has handled (fingerprint, thumbnail,
 *   palette, preview name, last comparison), so later turns can find, name and reuse them as text
 *   instead of images.
 * - `baselines/` holds approved full-size renders that `spyglass check` compares against.
 */
internal class Library(projectRoot: File, private val maxRenders: Int = MAX_RENDERS) {
    val dir = File(projectRoot, ".spyglass")
    val baselinesDir = File(dir, "baselines")
    private val libraryDir = File(dir, "library")
    private val imagesDir = File(libraryDir, "images")
    private val indexFile = File(libraryDir, "refs.json")
    private val refs: MutableMap<String, Ref> = load()

    class Ref(
        /** The first 12 hex digits of the pixel SHA-256. */
        val id: String,
        /** `render` or `design`. A render saved as a design becomes a design. */
        var kind: String,
        var preview: String?,
        var name: String?,
        var tags: List<String>,
        var note: String?,
        val width: Int,
        val height: Int,
        val dhash: Long,
        val palette: List<String>,
        val first: String,
        var last: String,
        var seen: Int,
        /** The design this render was last compared with, and how similar it was. */
        var design: String?,
        var similarity: Double?,
    ) {
        val label: String get() = name ?: preview ?: id
    }

    val size: Int get() = refs.size

    /**
     * Remembers [file]. An image already in the library (same pixels) is only marked as seen again.
     * Renders are kept as small thumbnails; designs at full size, since they are compared against.
     */
    fun record(file: File, kind: String, preview: String? = null, name: String? = null): Ref {
        val image = ImageIO.read(file) ?: throw CliError("not a readable image: $file")
        val print = Fingerprint.of(image)
        val id = print.sha.take(12)
        val now = Instant.now().toString()
        refs[id]?.let { existing ->
            existing.last = now
            existing.seen++
            if (existing.preview == null) existing.preview = preview
            if (name != null) existing.name = name
            // The same pixels saved as a design: keep it as a design, with the full image instead of a thumbnail.
            if (kind == KIND_DESIGN && existing.kind != KIND_DESIGN) {
                existing.kind = KIND_DESIGN
                imagesDir.mkdirs()
                ImageIO.write(image, "png", imageFile(id))
            }
            return existing
        }
        imagesDir.mkdirs()
        val stored = imageFile(id)
        if (kind == KIND_RENDER) stored.writeBytes(Images.fit(file, THUMBNAIL_PX)) else ImageIO.write(image, "png", stored)
        val ref = Ref(id, kind, preview, name, emptyList(), null, print.width, print.height, print.dhash, print.palette, now, now, 1, null, null)
        refs[id] = ref
        evict()
        return ref
    }

    /** The stored copy of a reference: a thumbnail for renders, the full image for designs. */
    fun imageFile(id: String) = File(imagesDir, "$id.png")

    /** By id (or a prefix of at least 4 characters), else by name, most recent first. */
    fun find(query: String): Ref? {
        val q = query.trim()
        refs[q]?.let { return it }
        if (q.length >= 4) refs.values.filter { it.id.startsWith(q) }.singleOrNull()?.let { return it }
        return refs.values.filter { it.name.equals(q, ignoreCase = true) }.maxByOrNull { it.last }
    }

    /** References that look like [image], closest first, with their distance (0 = identical). */
    fun similar(image: File, limit: Int = 5, exclude: String? = null): List<Pair<Ref, Int>> {
        val target = Fingerprint.of(ImageIO.read(image) ?: throw CliError("not a readable image: $image")).dhash
        return refs.values.filter { it.id != exclude }
            .map { it to Fingerprint.distance(it.dhash, target) }
            .filter { it.second <= SIMILAR_BITS }
            .sortedWith(compareBy({ it.second }, { -it.first.seen }))
            .take(limit)
    }

    fun list(kind: String? = null, filter: String? = null): List<Ref> = refs.values
        .filter { kind == null || it.kind == kind }
        .filter { r -> filter == null || listOfNotNull(r.preview, r.name, r.note).any { it.contains(filter, true) } || r.tags.any { it.equals(filter, true) } }
        .sortedByDescending { it.last }

    /**
     * Makes [render] the approved baseline for its preview. [key] is the image's path relative to the
     * project with `build/spyglass/previews/` removed, so modules keep separate baselines.
     */
    fun approve(render: File, key: String, preview: String): Ref {
        val target = File(baselinesDir, key)
        target.parentFile.mkdirs()
        Files.copy(render.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return record(render, KIND_RENDER, preview).also { it.tags = (it.tags + "approved").distinct() }
    }

    fun baseline(key: String): File? = File(baselinesDir, key).takeIf { it.isFile }

    /** Writes the index atomically, so a crash never leaves a half-written file. */
    fun save() {
        libraryDir.mkdirs()
        val json = buildJsonObject {
            put("version", 1)
            put("refs", JsonArray(refs.values.sortedBy { it.first }.map { it.toJson() }))
        }
        val temp = File(libraryDir, "refs.json.tmp")
        temp.writeText(Json.encodeToString(JsonElement.serializer(), json))
        try {
            Files.move(temp.toPath(), indexFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), indexFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Old renders go first once there are too many; designs and approved renders are always kept. */
    private fun evict() {
        val renders = refs.values.filter { it.kind == KIND_RENDER && "approved" !in it.tags }
        if (renders.size <= maxRenders) return
        renders.sortedBy { it.last }.take(renders.size - maxRenders).forEach { old ->
            refs.remove(old.id)
            imageFile(old.id).delete()
        }
    }

    private fun load(): MutableMap<String, Ref> {
        if (!indexFile.isFile) return linkedMapOf()
        return try {
            Json.parseToJsonElement(indexFile.readText()).jsonObject.getValue("refs").jsonArray
                .map { it.jsonObject.toRef() }.associateByTo(linkedMapOf()) { it.id }
        } catch (e: Exception) {
            // Keep the damaged file for inspection and start a fresh library rather than failing.
            indexFile.renameTo(File(libraryDir, "refs.json.bak"))
            linkedMapOf()
        }
    }

    private fun Ref.toJson() = buildJsonObject {
        put("id", id)
        put("kind", kind)
        preview?.let { put("preview", it) }
        name?.let { put("name", it) }
        if (tags.isNotEmpty()) put("tags", JsonArray(tags.map(::JsonPrimitive)))
        note?.let { put("note", it) }
        put("size", "${width}x$height")
        put("dhash", java.lang.Long.toHexString(dhash))
        put("palette", JsonArray(palette.map(::JsonPrimitive)))
        put("first", first)
        put("last", last)
        put("seen", seen)
        design?.let { put("design", it) }
        similarity?.let { put("similarity", it) }
    }

    private fun JsonObject.toRef(): Ref {
        fun s(key: String) = this[key]?.jsonPrimitive?.contentOrNull
        val (w, h) = s("size").orEmpty().split('x').map { it.toIntOrNull() ?: 0 }.let { it.getOrElse(0) { 0 } to it.getOrElse(1) { 0 } }
        return Ref(
            id = s("id") ?: error("ref without id"),
            kind = s("kind") ?: KIND_RENDER,
            preview = s("preview"),
            name = s("name"),
            tags = this["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
            note = s("note"),
            width = w,
            height = h,
            dhash = java.lang.Long.parseUnsignedLong(s("dhash") ?: "0", 16),
            palette = this["palette"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
            first = s("first").orEmpty(),
            last = s("last").orEmpty(),
            seen = this["seen"]?.jsonPrimitive?.intOrNull ?: 1,
            design = s("design"),
            similarity = this["similarity"]?.jsonPrimitive?.doubleOrNull,
        )
    }

    companion object {
        const val KIND_RENDER = "render"
        const val KIND_DESIGN = "design"
        private const val THUMBNAIL_PX = 480
        private const val MAX_RENDERS = 300
        private const val SIMILAR_BITS = 12

        /** `app/build/spyglass/previews/X.png` under [root] becomes `app/X.png`. */
        fun baselineKey(image: File, root: File): String =
            image.relativeToOrSelf(root).invariantSeparatorsPath.replace("build/spyglass/previews/", "")
    }
}
