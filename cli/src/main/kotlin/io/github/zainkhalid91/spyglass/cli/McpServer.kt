package io.github.zainkhalid91.spyglass.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.concurrent.thread

/** A malformed request; answered with a JSON-RPC "invalid params" error. */
internal class McpInvalidParams(message: String) : Exception(message)

/**
 * A Model Context Protocol server over stdio (one JSON-RPC message per line).
 *
 * It is "dual-era": clients on the stateless 2026-07-28 revision send the protocol version in each
 * request's `_meta`, while older clients open with `initialize`. Both are served.
 */
internal object McpServer {
    private const val MODERN = "2026-07-28"
    private val LEGACY = listOf("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05")
    private const val VERSION_META = "io.modelcontextprotocol/protocolVersion"
    private const val SERVER_INFO_META = "io.modelcontextprotocol/serverInfo"

    private val serverInfo = buildJsonObject {
        put("name", "spyglass")
        put("title", "Spyglass")
        put("version", VERSION)
    }

    private val capabilities = buildJsonObject { putJsonObject("tools") {} }

    // Sent to the agent once per session, so it is kept short.
    private val instructions = """
        Spyglass shows you Compose UI. After editing UI code, call render_previews with filter and look at the result before calling it done.
        Given a design image, loop compare_with_design: fix the largest regions first. Pixel-exact targets reach 0.98+; mocks from other tools rarely pass 0.9, so judge the image too.
        Previews need @Preview on a @Composable in a module with a JVM (desktop) target. Errors come back as text: fix and retry.
    """.trimIndent()

    fun run(): Int {
        // Only protocol messages may reach stdout; anything else printed is sent to stderr.
        val protocol = PrintStream(FileOutputStream(FileDescriptor.out), true, Charsets.UTF_8)
        System.setOut(System.err)
        // Opt-in usage reports go out in the background, so they never delay a tool call.
        thread(isDaemon = true, name = "spyglass-usage") { runCatching { Telemetry.sendIfDue() } }
        val input = System.`in`.bufferedReader(Charsets.UTF_8)
        while (true) {
            val line = input.readLine() ?: break
            if (line.isBlank()) continue
            val reply = runCatching { handle(line) }.getOrElse { error(JsonNull, -32600, "Invalid Request: $it") } ?: continue
            protocol.println(Json.encodeToString(JsonElement.serializer(), reply))
        }
        return 0
    }

    fun handle(line: String): JsonObject? {
        val message = runCatching { Json.parseToJsonElement(line) as? JsonObject }.getOrNull()
            ?: return error(JsonNull, -32700, "Parse error")
        val method = message["method"]?.jsonPrimitive?.contentOrNull ?: return null // a response, not a request
        val id = message["id"] ?: return null // notifications get no reply
        val params = message["params"] as? JsonObject ?: JsonObject(emptyMap())

        val requested = (params["_meta"] as? JsonObject)?.get(VERSION_META)?.jsonPrimitive?.contentOrNull
        val modern = requested != null
        if (requested != null && requested != MODERN) return unsupportedVersion(id, requested)

        return try {
            val result = when (method) {
                "initialize" -> initialize(params)
                "server/discover" -> discover()
                "ping" -> JsonObject(emptyMap())
                "tools/list" -> buildJsonObject { put("tools", McpTools.definitions) }
                "tools/call" -> McpTools.call(params)
                else -> return error(id, -32601, "Method not found: $method")
            }
            success(id, if (modern) withModernFields(result) else result)
        } catch (e: McpInvalidParams) {
            error(id, -32602, e.message ?: "Invalid params")
        } catch (e: Exception) {
            error(id, -32603, "Internal error: $e")
        }
    }

    /** Legacy handshake: answer with the client's version if we know it, else our newest legacy one. */
    private fun initialize(params: JsonObject): JsonObject {
        val requested = params["protocolVersion"]?.jsonPrimitive?.contentOrNull
        return buildJsonObject {
            put("protocolVersion", if (requested in LEGACY) requested else LEGACY.first())
            put("capabilities", capabilities)
            put("serverInfo", serverInfo)
            put("instructions", instructions)
        }
    }

    private fun discover() = buildJsonObject {
        put("supportedVersions", JsonArray(listOf(JsonPrimitive(MODERN))))
        put("capabilities", capabilities)
        put("instructions", instructions)
    }

    private fun withModernFields(result: JsonObject): JsonObject {
        val meta = (result["_meta"] as? JsonObject).orEmpty() + (SERVER_INFO_META to serverInfo)
        return JsonObject(result + mapOf("resultType" to JsonPrimitive("complete"), "_meta" to JsonObject(meta)))
    }

    private fun unsupportedVersion(id: JsonElement, requested: String) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        putJsonObject("error") {
            put("code", -32022)
            put("message", "Unsupported protocol version")
            putJsonObject("data") {
                put("supported", JsonArray((listOf(MODERN) + LEGACY).map(::JsonPrimitive)))
                put("requested", requested)
            }
        }
    }

    private fun success(id: JsonElement, result: JsonObject) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
    }

    private fun error(id: JsonElement, code: Int, message: String) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
        }
    }
}
