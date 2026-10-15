package io.github.zainkhalid91.spyglass.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Locale

/**
 * The tools the MCP server offers. Everything here is written to be cheap in tokens: short schemas
 * (they are loaded into every session), compact JSON, images scaled down before they are sent, and
 * images the agent has just seen not sent again. Problems an agent can fix come back as `isError`.
 */
internal object McpTools {
    // 640 px keeps 14sp text legible on a phone-sized preview at about 450 tokens per image.
    private const val DEFAULT_IMAGE_PX = 640
    private const val DEFAULT_MAX_IMAGES = 6

    /**
     * Images count as "already seen" for this long. Some clients keep one server running across
     * conversations, so the memory is short; `force` or `references show` always resends.
     */
    private val SEEN_FOR: Duration = Duration.ofMinutes(20)

    /** The project tools render when a call doesn't pass `projectDir`; set by `spyglass mcp --project`. */
    var defaultProject: File = File(".")

    /** Reference ids of images sent to the agent, and when. */
    private val sent = mutableMapOf<String, Instant>()

    /** Results of recent comparisons, keyed by render, design and threshold. */
    private val comparisons = mutableMapOf<String, Pair<Instant, String>>()

    private val projectProps = arrayOf(
        "projectDir" to prop("string", "Project or module dir. Default: the server's project."),
        "module" to prop("string", "Gradle module, e.g. :shared. Default: inferred, or all."),
    )
    private val imageProp = "imageMaxPx" to prop("integer", "Longest side of returned images. Default $DEFAULT_IMAGE_PX.")
    private val thresholdProp = "threshold" to prop("number", "Colour difference (0-1) counted as a change. Default 0.1.")

    val definitions: JsonArray = buildJsonArray {
        add(
            tool(
                name = "render_previews",
                title = "Render Compose previews",
                description = "Render Compose @Preview functions to PNG without an IDE and return the images. " +
                    "Use after changing UI code, with filter set to what you changed. Images you saw recently and " +
                    "that haven't changed are marked unchanged instead of resent.",
                required = emptyList(),
                *projectProps,
                "filter" to prop("string", "Only previews whose function or @Preview name contains this."),
                "includeImages" to prop("boolean", "Return images. Default true."),
                "maxImages" to prop("integer", "Most images returned. Default $DEFAULT_MAX_IMAGES."),
                "force" to prop("boolean", "Resend images even if unchanged."),
                imageProp,
            )
        )
        add(
            tool(
                name = "compare_with_design",
                title = "Compare a preview with a design",
                description = "Render one @Preview and compare it with a design. Returns similarity, the regions " +
                    "that differ as [x,y,w,h] dp, and one image: render | design | heatmap (changes in red). " +
                    "Loop: fix the largest regions, compare again.",
                required = listOf("preview", "designImage"),
                "preview" to prop("string", "Preview function name or @Preview name."),
                "variant" to prop("string", "@Preview name, if the function has several."),
                "designImage" to prop("string", "Path to the design (PNG/JPEG), or the name or ref of a saved design."),
                "designName" to prop("string", "Save the design under this name for later calls."),
                *projectProps,
                thresholdProp,
                imageProp,
            )
        )
        add(
            tool(
                name = "diff_images",
                title = "Compare two images",
                description = "Compare two images: similarity, regions that differ, and actual | expected | heatmap as one image.",
                required = listOf("actual", "expected"),
                "actual" to prop("string", "Path to the image being checked."),
                "expected" to prop("string", "Path to the reference; scaled to actual's size if needed."),
                "density" to prop("number", "Pixels per dp for region sizes. Default 2."),
                thresholdProp,
                imageProp,
            )
        )
        add(
            tool(
                name = "references",
                title = "UI memory",
                description = "The project's remembered UI: each unique render and design seen, with names, notes and " +
                    "main colours. list and similar are text only, so cheap; check them before building UI that may " +
                    "exist. show sends one image. save names a design or adds a note. approve stores renders as " +
                    "baselines for `spyglass check`.",
                required = listOf("action"),
                "action" to prop("string", "list | similar | show | save | approve"),
                "query" to prop("string", "Ref id, name, preview or tag. approve: preview filter."),
                "kind" to prop("string", "render | design"),
                "image" to prop("string", "Image path, for similar or save."),
                "name" to prop("string", "save: name to give it."),
                "note" to prop("string", "save: note to attach, e.g. what was fixed."),
                "tags" to prop("string", "save: comma-separated tags."),
                "limit" to prop("integer", "Most results. Default 20."),
                *projectProps,
                imageProp,
            )
        )
    }

    private val toolNames = setOf("render_previews", "compare_with_design", "diff_images", "references")
    private val referenceActions = setOf("list", "similar", "show", "save", "approve")

    fun call(params: JsonObject): JsonObject {
        val name = params["name"]?.jsonPrimitive()?.contentOrNull ?: throw McpInvalidParams("tools/call needs a tool name")
        val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        return try {
            // Only known names are counted: text chosen by the client never reaches usage reports.
            if (name in toolNames) Telemetry.count("mcp.$name")
            when (name) {
                "render_previews" -> renderPreviews(args)
                "compare_with_design" -> compareWithDesign(args)
                "diff_images" -> diffImages(args)
                "references" -> references(args)
                else -> throw McpInvalidParams("Unknown tool: $name")
            }
        } catch (e: CliError) {
            toolError(e.message ?: "Spyglass failed")
        } finally {
            Telemetry.flush()
        }
    }

    // ------------------------------------------------------------------ render_previews

    private fun renderPreviews(args: JsonObject): JsonObject {
        val limit = args.int("maxImages") ?: DEFAULT_MAX_IMAGES
        val maxPx = args.imageMaxPx()
        val force = args.bool("force") == true
        val includeImages = args.bool("includeImages") != false
        val outcome = render(args, filter = args.string("filter"))
        if (!outcome.succeeded) return toolError(gradleFailure(outcome))

        val rendered = outcome.previews.filter { it.image != null }
        val unchanged = if (force) emptySet() else rendered.mapNotNull { outcome.refs[it.image] }.filter(::seenRecently).toSet()
        val content = mutableListOf(text(compact(Rendering.toJson(outcome, unchanged))))
        if (outcome.previews.isEmpty()) {
            content += text("No previews rendered. Check the filter, and that the module has a JVM (desktop) target.")
        }
        if (includeImages) {
            var shown = 0
            var notShown = 0
            for (p in rendered) {
                val ref = outcome.refs[p.image]
                if (ref != null && ref in unchanged) continue
                if (shown == limit) {
                    notShown++
                    continue
                }
                content += text(Rendering.label(p) + (ref?.let { " (ref $it)" } ?: ""))
                content += image(Images.fit(p.image!!, maxPx))
                ref?.let(::markSent)
                shown++
            }
            if (unchanged.isNotEmpty()) {
                content += text("${unchanged.size} unchanged since you saw them, not resent (\"unchanged\" above). force=true resends.")
            }
            if (notShown > 0) content += text("$notShown more not shown; narrow with filter.")
            Telemetry.count("mcp.imagesSent", shown)
            Telemetry.count("mcp.imagesUnchanged", unchanged.size)
        }
        return result(content)
    }

    // ------------------------------------------------------------------ compare_with_design

    private fun compareWithDesign(args: JsonObject): JsonObject {
        val query = args.requireString("preview")
        val variant = args.string("variant")
        val threshold = args.threshold()
        val maxPx = args.imageMaxPx()
        val root = projectRoot(args)
        val design = resolveDesign(args.requireString("designImage"), Library(root))

        val outcome = render(args, filter = query)
        if (!outcome.succeeded) return toolError(gradleFailure(outcome))
        val preview = pick(outcome.previews, query, variant)
        val render = preview.image ?: return toolError("${Rendering.label(preview)} failed to render: ${preview.error}")

        val library = Library(outcome.root)
        val designRef = library.record(design, Library.KIND_DESIGN, name = args.string("designName"))
        // The render was just learned by Rendering; look it up rather than counting it as seen twice.
        val renderRef = outcome.refs[render]?.let(library::find)
            ?: library.record(render, Library.KIND_RENDER, preview = Rendering.label(preview))
        val key = "${renderRef.id}|${designRef.id}|$threshold"
        comparisons[key]?.takeIf { (at, _) -> Duration.between(at, Instant.now()) < SEEN_FOR }?.let { (_, summary) ->
            library.save()
            Telemetry.count("mcp.compareCacheHits")
            return result(listOf(text("Same render and design as your last comparison, so no new image:\n$summary")))
        }

        val heatmap = File(render.parentFile, render.nameWithoutExtension + ".vs-design.png")
        val diff = ImageDiff.compare(render, design, threshold, heatmap)
        renderRef.design = designRef.id
        renderRef.similarity = diff.similarity
        library.save()
        Telemetry.similarity(diff.similarity)

        val summaryJson = buildJsonObject {
            put("preview", Rendering.label(preview))
            put("render", renderRef.id)
            put("design", designRef.name ?: designRef.id)
            for ((k, v) in DiffCommand.toJson(diff, preview.density)) put(k, v)
        }
        val summary = compact(summaryJson)
        comparisons[key] = Instant.now() to summary

        // A design the agent saw recently is left out of the strip: render and heatmap are what changed.
        val panels = buildList {
            add("Render" to render)
            if (!seenRecently(designRef.id)) add("Design" to design)
            add("Diff" to heatmap)
        }
        markSent(designRef.id)
        markSent(renderRef.id)
        return result(listOf(text(summary), image(Images.strip(panels, maxWidth = maxPx * 3 / 2, maxHeight = maxPx))))
    }

    /** A design given as a path, or as the name or ref id of one saved in the library. */
    private fun resolveDesign(value: String, library: Library): File {
        path(value).takeIf { it.isFile }?.let { return it }
        val ref = library.find(value)?.takeIf { it.kind == Library.KIND_DESIGN }
            ?: throw CliError("designImage not found: $value (no such file, and no saved design with that name or ref)")
        return library.imageFile(ref.id).takeIf { it.isFile } ?: throw CliError("the saved design ${ref.label} has no image")
    }

    // ------------------------------------------------------------------ diff_images

    private fun diffImages(args: JsonObject): JsonObject {
        val actual = path(args.requireString("actual"))
        val expected = path(args.requireString("expected"))
        val threshold = args.threshold()
        val density = args.double("density") ?: 2.0
        if (density <= 0) throw CliError("'density' must be positive")
        val maxPx = args.imageMaxPx()
        val heatmap = File(actual.parentFile, actual.nameWithoutExtension + ".diff.png")
        val diff = ImageDiff.compare(actual, expected, threshold, heatmap)
        Telemetry.similarity(diff.similarity)
        return result(
            listOf(
                text(compact(DiffCommand.toJson(diff, density))),
                image(Images.strip(listOf("Actual" to actual, "Expected" to expected, "Diff" to heatmap), maxWidth = maxPx * 3 / 2, maxHeight = maxPx)),
            )
        )
    }

    // ------------------------------------------------------------------ references

    private fun references(args: JsonObject): JsonObject {
        val action = args.requireString("action").lowercase()
        if (action !in referenceActions) throw CliError("unknown action '$action': use ${referenceActions.joinToString()}")
        val limit = (args.int("limit") ?: 20).coerceIn(1, 200)
        Telemetry.count("mcp.references.$action")
        if (action == "approve") return approve(args)

        val library = Library(projectRoot(args))
        return when (action) {
            "list" -> {
                val refs = library.list(args.string("kind"), args.string("query"))
                if (refs.isEmpty()) return result(listOf(text("No references yet. Render or compare previews and they are remembered.")))
                val lines = refs.take(limit).joinToString("\n", transform = ::describe)
                result(listOf(text("${refs.size} references, newest first (showing ${minOf(limit, refs.size)}):\n$lines")))
            }
            "similar" -> {
                val (target, exclude) = args.string("image")?.let { path(it) to null }
                    ?: args.string("query")?.let { q -> library.find(q)?.let { library.imageFile(it.id) to it.id } }
                    ?: throw CliError("similar needs image (a path) or query (a ref id or name)")
                if (!target.isFile) throw CliError("image not found: $target")
                val matches = library.similar(target, minOf(limit, 10), exclude)
                if (matches.isEmpty()) return result(listOf(text("Nothing similar in the library.")))
                val lines = matches.joinToString("\n") { (ref, distance) -> "distance $distance  ${describe(ref)}" }
                result(listOf(text("Closest first (0 = identical, under 10 = looks alike):\n$lines")))
            }
            "show" -> {
                val ref = library.find(args.requireString("query")) ?: throw CliError("no reference matches '${args.string("query")}'")
                markSent(ref.id)
                result(listOf(text(describe(ref)), image(Images.fit(library.imageFile(ref.id), args.imageMaxPx()))))
            }
            "save" -> {
                val ref = args.string("image")?.let { library.record(path(it), args.string("kind") ?: Library.KIND_DESIGN, name = args.string("name")) }
                    ?: args.string("query")?.let(library::find)
                    ?: throw CliError("save needs image (a path) or query (an existing ref id or name)")
                args.string("name")?.let { ref.name = it }
                args.string("note")?.let { ref.note = it }
                args.string("tags")?.let { tags -> ref.tags = (ref.tags + tags.split(',').map(String::trim).filter(String::isNotEmpty)).distinct() }
                library.save()
                result(listOf(text("Saved: ${describe(ref)}")))
            }
            else -> throw CliError("unknown action '$action': use list, similar, show, save or approve")
        }
    }

    private fun approve(args: JsonObject): JsonObject {
        val query = args.requireString("query")
        val outcome = render(args, filter = query)
        if (!outcome.succeeded) return toolError(gradleFailure(outcome))
        val library = Library(outcome.root)
        val approved = outcome.previews.filter { it.image != null }.map { p ->
            library.approve(p.image!!, Library.baselineKey(p.image, outcome.root), Rendering.label(p))
            Rendering.label(p)
        }
        library.save()
        if (approved.isEmpty()) return toolError("No rendered preview matches '$query' to approve.")
        return result(listOf(text("Approved ${approved.size} baseline(s) in .spyglass/baselines/: ${approved.joinToString()}")))
    }

    /** One compact line per reference. */
    private fun describe(r: Library.Ref): String = buildString {
        append(r.id).append("  ").append(r.kind).append("  ").append(r.label)
        append("  ").append(r.width).append('x').append(r.height)
        append("  seen ").append(r.seen).append("x, last ").append(r.last.take(10))
        if (r.palette.isNotEmpty()) append("  colours ").append(r.palette.take(4).joinToString(","))
        r.similarity?.let { append("  vs design ").append(String.format(Locale.ROOT, "%.2f%%", it * 100)) }
        if (r.tags.isNotEmpty()) append("  [").append(r.tags.joinToString(",")).append(']')
        r.note?.let { append("  note: ").append(it) }
    }

    // ------------------------------------------------------------------ helpers

    private fun seenRecently(id: String) = sent[id]?.let { Duration.between(it, Instant.now()) < SEEN_FOR } == true

    private fun markSent(id: String) {
        sent[id] = Instant.now()
    }

    /** Forgets what this server has sent; for tests and new sessions. */
    internal fun resetSession() {
        sent.clear()
        comparisons.clear()
    }

    private fun render(args: JsonObject, filter: String?): Rendering.Outcome {
        val options = buildMap { filter?.let { put("filter", it) } }
        return Rendering.render(start(args), args.string("module"), options, quiet = true)
    }

    private fun start(args: JsonObject) = args.string("projectDir")?.let(::path) ?: defaultProject.absoluteFile.normalize()

    /** The Gradle build root that owns the library, or the directory itself outside a build. */
    private fun projectRoot(args: JsonObject): File = start(args).let { dir ->
        runCatching { GradleBuild.find(dir).settingsDir }.getOrDefault(dir)
    }

    /**
     * A path from a tool call. Relative paths are taken from the server's project, not its working
     * directory, which for clients like Claude Desktop is unrelated to the project.
     */
    internal fun path(value: String): File {
        val file = File(value)
        return (if (file.isAbsolute) file else File(defaultProject, value)).absoluteFile.normalize()
    }

    /** One preview from a filtered render: an exact function or @Preview name match wins over a partial one. */
    private fun pick(previews: List<PreviewEntry>, query: String, variant: String?): PreviewEntry {
        val candidates = previews.filter { variant == null || it.name.equals(variant, ignoreCase = true) }
        if (candidates.isEmpty()) throw CliError("No @Preview matches '$query'${variant?.let { " with variant '$it'" } ?: ""}.")
        if (candidates.size == 1) return candidates.single()
        val exact = candidates.filter {
            it.function.substringAfterLast('.').equals(query, true) || it.function.equals(query, true) || it.name.equals(query, true)
        }
        if (exact.size == 1) return exact.single()
        val options = (exact.ifEmpty { candidates }).joinToString("; ") { p ->
            "preview=\"${p.function.substringAfterLast('.')}\"" + if (p.name.isEmpty()) "" else " variant=\"${p.name}\""
        }
        throw CliError("'$query' matches several previews. Pass one of: $options")
    }

    /** Gradle's last lines hold the compile error the agent needs; its generic advice after "* Try:" does not. */
    private fun gradleFailure(outcome: Rendering.Outcome): String {
        val lines = outcome.gradle.lastLines.filter { it.isNotBlank() }
        val useful = lines.takeWhile { !it.startsWith("* Try:") }.ifEmpty { lines }
        return "Gradle failed (exit ${outcome.gradle.exitCode}) running ${outcome.task}:\n" + useful.takeLast(30).joinToString("\n")
    }

    private fun compact(json: JsonObject) = Json.encodeToString(JsonElement.serializer(), json)

    private fun image(png: ByteArray) = buildJsonObject {
        put("type", "image")
        put("data", Base64.getEncoder().encodeToString(png))
        put("mimeType", "image/png")
    }

    private fun result(content: List<JsonObject>, isError: Boolean = false) = buildJsonObject {
        put("content", JsonArray(content))
        put("isError", isError)
    }

    private fun text(s: String) = buildJsonObject {
        put("type", "text")
        put("text", s)
    }

    private fun toolError(message: String) = result(listOf(text(message)), isError = true)

    private fun tool(
        name: String,
        title: String,
        description: String,
        required: List<String>,
        vararg properties: Pair<String, JsonObject>,
    ) = buildJsonObject {
        put("name", name)
        put("title", title)
        put("description", description)
        putJsonObject("inputSchema") {
            put("type", "object")
            put("properties", JsonObject(properties.toMap()))
            if (required.isNotEmpty()) put("required", JsonArray(required.map(::JsonPrimitive)))
        }
    }

    private fun prop(type: String, description: String) = buildJsonObject {
        put("type", type)
        put("description", description)
    }

    // Argument readers. A wrong type is the agent's to fix, so it becomes a tool error, not a protocol error.
    private fun JsonElement.jsonPrimitive(): JsonPrimitive? = this as? JsonPrimitive

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive()?.takeIf { it.isString }?.contentOrNull?.ifBlank { null }

    private fun JsonObject.requireString(key: String): String =
        string(key) ?: throw CliError("'$key' is required and must be a non-empty string")

    private fun JsonObject.double(key: String): Double? = this[key]?.let {
        it.jsonPrimitive()?.doubleOrNull ?: throw CliError("'$key' must be a number")
    }

    private fun JsonObject.int(key: String): Int? = this[key]?.let {
        it.jsonPrimitive()?.intOrNull ?: throw CliError("'$key' must be an integer")
    }

    private fun JsonObject.bool(key: String): Boolean? = this[key]?.let {
        it.jsonPrimitive()?.booleanOrNull ?: throw CliError("'$key' must be true or false")
    }

    private fun JsonObject.threshold(): Double =
        (double("threshold") ?: 0.1).also { if (it !in 0.0..1.0) throw CliError("'threshold' must be between 0 and 1") }

    private fun JsonObject.imageMaxPx(): Int =
        (int("imageMaxPx") ?: DEFAULT_IMAGE_PX).also { if (it !in 64..4096) throw CliError("'imageMaxPx' must be between 64 and 4096") }
}
