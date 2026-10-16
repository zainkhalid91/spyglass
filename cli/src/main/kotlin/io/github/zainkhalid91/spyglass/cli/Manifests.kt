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

internal class PreviewEntry(
    val function: String,
    val name: String,
    /** The `@PreviewParameter` value shown, as text, for parameterised previews. */
    val parameter: String?,
    val image: File?,
    val widthPx: Int,
    val heightPx: Int,
    val density: Double,
    val error: String?,
    /** A note about an image that did render, e.g. content cut off at the size limit. */
    val warning: String? = null,
)

/** Reads the `previews.json` files the renderer writes under `build/spyglass/previews/`. */
internal object Manifests {
    fun read(manifest: File): List<PreviewEntry> {
        val root = runCatching { Json.parseToJsonElement(manifest.readText()).jsonObject }
            .getOrElse { throw CliError("could not read $manifest: ${it.message}") }
        val density = root["density"]?.jsonPrimitive?.doubleOrNull ?: 2.0
        return root["previews"]?.jsonArray.orEmpty().map { element ->
            val p = element.jsonObject
            PreviewEntry(
                function = p.string("function").orEmpty(),
                name = p.string("name").orEmpty(),
                parameter = p.string("parameter"),
                image = p.string("image")?.let { File(manifest.parentFile, it) },
                widthPx = p["widthPx"]?.jsonPrimitive?.intOrNull ?: 0,
                heightPx = p["heightPx"]?.jsonPrimitive?.intOrNull ?: 0,
                density = density,
                error = p.string("error"),
                warning = p.string("warning"),
            )
        }
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
