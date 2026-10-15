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
import java.util.Base64

/**
 * The tools the MCP server offers. Everything here is written to be cheap in tokens: short schemas
 * (they are loaded into every session), compact JSON, and images scaled down before they are sent.
 * Problems an agent can fix come back as `isError` results.
 */
internal object McpTools {
    // 640 px keeps 14sp text legible on a phone-sized preview at about 450 tokens per image.
    private const val DEFAULT_IMAGE_PX = 640
    private const val DEFAULT_MAX_IMAGES = 6

    /** The project tools render when a call doesn't pass `projectDir`; set by `spyglass mcp --project`. */
    var defaultProject: File = File(".")

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
                    "Use after changing UI code, with filter set to what you changed.",
                required = emptyList(),
                *projectProps,
                "filter" to prop("string", "Only previews whose function or @Preview name contains this."),
                "includeImages" to prop("boolean", "Return images. Default true."),
                "maxImages" to prop("integer", "Most images returned. Default $DEFAULT_MAX_IMAGES."),
                imageProp,
            )
        )
        add(
            tool(
                name = "compare_with_design",
                title = "Compare a preview with a design",
                description = "Render one @Preview and compare it with a design image. Returns similarity, the regions " +
                    "that differ as [x,y,w,h] dp, and one image: render | design | heatmap (changes in red). " +
                    "Loop: fix the largest regions, compare again.",
                required = listOf("preview", "designImage"),
                "preview" to prop("string", "Preview function name or @Preview name."),
                "variant" to prop("string", "@Preview name, if the function has several."),
                "designImage" to prop("string", "Path to the design (PNG or JPEG)."),
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
    }

    fun call(params: JsonObject): JsonObject {
        val name = params["name"]?.jsonPrimitive()?.contentOrNull ?: throw McpInvalidParams("tools/call needs a tool name")
        val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        return try {
            when (name) {
                "render_previews" -> renderPreviews(args)
                "compare_with_design" -> compareWithDesign(args)
                "diff_images" -> diffImages(args)
                else -> throw McpInvalidParams("Unknown tool: $name")
            }
        } catch (e: CliError) {
            toolError(e.message ?: "Spyglass failed")
        }
    }

    private fun renderPreviews(args: JsonObject): JsonObject {
        val limit = args.int("maxImages") ?: DEFAULT_MAX_IMAGES
        val maxPx = args.imageMaxPx()
        val outcome = render(args, filter = args.string("filter"))
        if (!outcome.succeeded) return toolError(gradleFailure(outcome))

        val content = mutableListOf(text(compact(Rendering.toJson(outcome))))
        if (outcome.previews.isEmpty()) {
            content += text("No previews rendered. Check the filter, and that the module has a JVM (desktop) target.")
        }
        if (args.bool("includeImages") != false) {
            val rendered = outcome.previews.filter { it.image != null }
            for (p in rendered.take(limit)) {
                content += text(Rendering.label(p))
                content += image(Images.fit(p.image!!, maxPx))
            }
            if (rendered.size > limit) content += text("${rendered.size - limit} more not shown; narrow with filter.")
        }
        return result(content)
    }

    private fun compareWithDesign(args: JsonObject): JsonObject {
        val query = args.requireString("preview")
        val variant = args.string("variant")
        val design = File(args.requireString("designImage")).absoluteFile
        if (!design.isFile) throw CliError("designImage not found: $design")
        val threshold = args.threshold()
        val maxPx = args.imageMaxPx()

        val outcome = render(args, filter = query)
        if (!outcome.succeeded) return toolError(gradleFailure(outcome))
        val preview = pick(outcome.previews, query, variant)
        val render = preview.image ?: return toolError("${Rendering.label(preview)} failed to render: ${preview.error}")

        val heatmap = File(render.parentFile, render.nameWithoutExtension + ".vs-design.png")
        val diff = ImageDiff.compare(render, design, threshold, heatmap)
        val summary = JsonObject(mapOf("preview" to JsonPrimitive(Rendering.label(preview))) + DiffCommand.toJson(diff, preview.density))
        return result(
            listOf(
                text(compact(summary)),
                image(Images.strip(listOf("Render" to render, "Design" to design, "Diff" to heatmap), maxWidth = maxPx * 3 / 2, maxHeight = maxPx)),
            )
        )
    }

    private fun diffImages(args: JsonObject): JsonObject {
        val actual = File(args.requireString("actual")).absoluteFile
        val expected = File(args.requireString("expected")).absoluteFile
        val threshold = args.threshold()
        val density = args.double("density") ?: 2.0
        if (density <= 0) throw CliError("'density' must be positive")
        val maxPx = args.imageMaxPx()
        val heatmap = File(actual.parentFile, actual.nameWithoutExtension + ".diff.png")
        val diff = ImageDiff.compare(actual, expected, threshold, heatmap)
        return result(
            listOf(
                text(compact(DiffCommand.toJson(diff, density))),
                image(Images.strip(listOf("Actual" to actual, "Expected" to expected, "Diff" to heatmap), maxWidth = maxPx * 3 / 2, maxHeight = maxPx)),
            )
        )
    }

    private fun render(args: JsonObject, filter: String?): Rendering.Outcome {
        val options = buildMap { filter?.let { put("filter", it) } }
        val start = (args.string("projectDir")?.let(::File) ?: defaultProject).absoluteFile.normalize()
        return Rendering.render(start, args.string("module"), options, quiet = true)
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
