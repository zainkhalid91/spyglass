package io.github.zainkhalid91.spyglass.renderer

import java.io.File

/** Writes `previews.json`, the machine-readable index of a render run (read by agents and CI). */
internal object Manifest {
    fun write(file: File, results: List<RenderResult>, options: Options) {
        val entries = results.joinToString(",\n") { r ->
            val fields = buildList {
                add("function" to quote(r.spec.functionName))
                add("name" to quote(r.spec.name))
                add("group" to quote(r.spec.group))
                add("status" to quote(if (r.error == null) "ok" else "error"))
                add("image" to (r.file?.let { quote(it.relativeTo(file.parentFile).invariantSeparatorsPath) } ?: "null"))
                add("widthPx" to r.widthPx.toString())
                add("heightPx" to r.heightPx.toString())
                add("durationMs" to r.durationMs.toString())
                add("error" to (r.error?.let(::quote) ?: "null"))
            }
            fields.joinToString(",\n", prefix = "    {\n", postfix = "\n    }") { (k, v) -> "      \"$k\": $v" }
        }
        file.parentFile?.mkdirs()
        file.writeText(
            """
            |{
            |  "tool": "spyglass",
            |  "version": ${quote(VERSION)},
            |  "density": ${options.density},
            |  "previews": [
            |$entries
            |  ]
            |}
            |""".trimMargin()
        )
    }

    private fun quote(s: String) = buildString {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
        append('"')
    }
}
