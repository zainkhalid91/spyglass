package io.github.zainkhalid91.spyglass.cli

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

/** The tools the MCP server offers. Problems an agent can fix come back as `isError` results. */
internal object McpTools {
    /** Images larger than this are referenced by path instead of sent inline. */
    private const val MAX_INLINE_BYTES = 2_000_000

    private val projectProps = arrayOf(
        "projectDir" to prop("string", "Gradle project or module directory. Defaults to the server's working directory."),
        "module" to prop("string", "Gradle module path such as :shared. Defaults to the module containing projectDir, or every module."),
    )

    val definitions: JsonArray = buildJsonArray {
        add(
            tool(
                name = "render_previews",
                title = "Render Compose previews",
                description = "Render the project's Compose @Preview functions to PNG (headless, no IDE) and return them. " +
                    "Call after changing Compose UI code to see the result. Returns a JSON summary (function, status, " +
                    "image path, size, error) followed by the images.",
                required = emptyList(),
                *projectProps,
                "filter" to prop("string", "Only previews whose function name or @Preview name contains this text (case-insensitive)."),
                "density" to prop("number", "Pixels per dp. Default 2."),
                "includeImages" to prop("boolean", "Return the images inline. Default true."),
                "maxImages" to prop("integer", "Most images to return inline. Default 6; the rest are listed by path."),
            )
        )
        add(
            tool(
                name = "compare_with_design",
                title = "Compare a preview with a design",
                description = "Render one @Preview and compare it with a design image (mock, screenshot or export). " +
                    "Returns the similarity score, the regions that differ (px and dp, with a rough position), the render, " +
                    "the design, and a heatmap with changed pixels in red. Use it in a loop: change the code, compare, " +
                    "fix the largest regions, repeat.",
                required = listOf("preview", "designImage"),
                "preview" to prop("string", "The preview's function name (e.g. LoginScreenPreview) or its @Preview name."),
                "variant" to prop("string", "The @Preview name, when one function has several @Preview annotations."),
                "designImage" to prop("string", "Path to the design image (PNG or JPEG)."),
                *projectProps,
                "threshold" to prop("number", "Colour difference (0-1) that counts as a changed pixel. Default 0.1."),
                "density" to prop("number", "Pixels per dp. Default 2."),
            )
        )
        add(
            tool(
                name = "diff_images",
                title = "Compare two images",
                description = "Compare two existing images and return the similarity score, the regions that differ, " +
                    "and a heatmap with changed pixels in red.",
                required = listOf("actual", "expected"),
                "actual" to prop("string", "Path to the image being checked (e.g. a rendered preview)."),
                "expected" to prop("string", "Path to the reference image. It is scaled to the actual image's size if they differ."),
                "heatmap" to prop("string", "Where to write the heatmap. Default: next to actual, as <name>.diff.png."),
                "threshold" to prop("number", "Colour difference (0-1) that counts as a changed pixel. Default 0.1."),
                "density" to prop("number", "Pixels per dp, used to report regions in dp. Default 2."),
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
        val outcome = render(args, filter = args.string("filter"))
        if (!outcome.succeeded) return toolError(gradleFailure(outcome))

        val content = mutableListOf(text(prettyJson.encodeToString(JsonElement.serializer(), Rendering.toJson(outcome))))
        if (outcome.previews.isEmpty()) {
            content += text("No @Preview functions were rendered. Check the filter, and that the module has a JVM (desktop) target.")
        }
        if (args.bool("includeImages") != false) {
            val rendered = outcome.previews.filter { it.image != null }
            val limit = args.int("maxImages") ?: 6
            for (p in rendered.take(limit)) {
                content += text(Rendering.label(p))
                content += imageOrPath(p.image!!)
            }
            if (rendered.size > limit) {
                content += text("${rendered.size - limit} more images are not shown; their paths are in the summary. Use `filter` to see them.")
            }
        }
        return result(content)
    }

    private fun compareWithDesign(args: JsonObject): JsonObject {
        val query = args.requireString("preview")
        val variant = args.string("variant")
        val design = File(args.requireString("designImage")).absoluteFile
        if (!design.isFile) throw CliError("designImage not found: $design")

        val outcome = render(args, filter = query)
        if (!outcome.succeeded) return toolError(gradleFailure(outcome))
        val preview = pick(outcome.previews, query, variant)
        val image = preview.image ?: return toolError("${Rendering.label(preview)} failed to render: ${preview.error}")

        val density = args.double("density") ?: preview.density
        val heatmap = File(image.parentFile, image.nameWithoutExtension + ".vs-design.png")
        val diff = ImageDiff.compare(image, design, args.threshold(), heatmap)
        val summary = buildJsonObject {
            put("preview", Rendering.label(preview))
            put("function", preview.function)
            for ((key, value) in DiffCommand.toJson(diff, image, design, density)) put(key, value)
        }
        return result(
            listOf(
                text(prettyJson.encodeToString(JsonElement.serializer(), summary)),
                text("Render (${DiffCommand.percent(diff.similarity)} similar to the design):"),
                imageOrPath(image),
                text("Design:"),
                imageOrPath(design),
                text("Heatmap, changed pixels in red:"),
                imageOrPath(heatmap),
            )
        )
    }

    private fun diffImages(args: JsonObject): JsonObject {
        val actual = File(args.requireString("actual")).absoluteFile
        val expected = File(args.requireString("expected")).absoluteFile
        val heatmap = args.string("heatmap")?.let { File(it).absoluteFile }
            ?: File(actual.parentFile, actual.nameWithoutExtension + ".diff.png")
        val diff = ImageDiff.compare(actual, expected, args.threshold(), heatmap)
        val summary = DiffCommand.toJson(diff, actual, expected, args.double("density") ?: 2.0)
        return result(
            listOf(
                text(prettyJson.encodeToString(JsonElement.serializer(), summary)),
                text("Heatmap, changed pixels in red:"),
                imageOrPath(heatmap),
            )
        )
    }

    private fun render(args: JsonObject, filter: String?): Rendering.Outcome {
        val options = buildMap {
            filter?.let { put("filter", it) }
            args.double("density")?.let { put("density", it.toString()) }
        }
        val start = File(args.string("projectDir") ?: ".").canonicalFile
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

    private fun gradleFailure(outcome: Rendering.Outcome) =
        "Gradle failed (exit ${outcome.gradle.exitCode}) running ${outcome.task}. Last output:\n" +
            outcome.gradle.lastLines.joinToString("\n")

    private fun imageOrPath(file: File): JsonObject {
        val mime = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> null
        }
        if (mime == null || !file.isFile || file.length() > MAX_INLINE_BYTES) return text("(image not inlined: $file)")
        return buildJsonObject {
            put("type", "image")
            put("data", Base64.getEncoder().encodeToString(file.readBytes()))
            put("mimeType", mime)
        }
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
}
