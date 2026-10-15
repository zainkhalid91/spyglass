package io.github.zainkhalid91.spyglass.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpServerTest {
    private val modernMeta = """"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28"}"""

    private fun call(json: String): JsonObject = McpServer.handle(json) ?: error("expected a reply")
    private fun JsonObject.result() = getValue("result").jsonObject
    private fun JsonObject.errorCode() = getValue("error").jsonObject.getValue("code").jsonPrimitive.int
    private fun JsonObject.text() = result().getValue("content").jsonArray.first().jsonObject.getValue("text").jsonPrimitive.content

    @AfterTest
    fun resetProject() {
        McpTools.defaultProject = File(".")
    }

    @Test
    fun `legacy initialize echoes a known version and falls back for unknown ones`() {
        val known = call("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}""")
        assertEquals("2025-06-18", known.result().getValue("protocolVersion").jsonPrimitive.content)
        val unknown = call("""{"jsonrpc":"2.0","id":2,"method":"initialize","params":{"protocolVersion":"1999-01-01"}}""")
        assertEquals("2025-11-25", unknown.result().getValue("protocolVersion").jsonPrimitive.content)
    }

    @Test
    fun `modern requests get resultType and server info`() {
        val reply = call("""{"jsonrpc":"2.0","id":"d","method":"server/discover","params":{$modernMeta}}""")
        val result = reply.result()
        assertEquals("complete", result.getValue("resultType").jsonPrimitive.content)
        assertTrue("io.modelcontextprotocol/serverInfo" in result.getValue("_meta").jsonObject)
        assertEquals("d", reply.getValue("id").jsonPrimitive.content)
    }

    @Test
    fun `unsupported modern versions get the dedicated error`() {
        val reply = call("""{"jsonrpc":"2.0","id":3,"method":"tools/list","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"1900-01-01"}}}""")
        assertEquals(-32022, reply.errorCode())
    }

    @Test
    fun `notifications get no reply and bad input gets JSON-RPC errors`() {
        assertNull(McpServer.handle("""{"jsonrpc":"2.0","method":"notifications/initialized"}"""))
        assertEquals(-32700, call("not json").errorCode())
        assertEquals(-32601, call("""{"jsonrpc":"2.0","id":4,"method":"nope"}""").errorCode())
        assertEquals(-32602, call("""{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"nope"}}""").errorCode())
    }

    @Test
    fun `lists the three tools`() {
        val tools = call("""{"jsonrpc":"2.0","id":6,"method":"tools/list"}""").result().getValue("tools").jsonArray
        assertEquals(listOf("render_previews", "compare_with_design", "diff_images"), tools.map { it.jsonObject.getValue("name").jsonPrimitive.content })
    }

    @Test
    fun `mistakes an agent can fix come back as tool errors, before any Gradle run`() {
        val badType = call("""{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"render_previews","arguments":{"maxImages":"lots"}}}""")
        assertTrue(badType.result().getValue("isError").jsonPrimitive.boolean)
        assertEquals("'maxImages' must be an integer", badType.text())

        val noDesign = call("""{"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"compare_with_design","arguments":{"preview":"X","designImage":"missing-design.png"}}}""")
        assertTrue(noDesign.result().getValue("isError").jsonPrimitive.boolean)
        assertTrue(noDesign.text().startsWith("designImage not found"))
    }

    @Test
    fun `relative paths resolve against the server's project`() {
        val project = File(System.getProperty("java.io.tmpdir"), "spyglass-project")
        McpTools.defaultProject = project
        assertEquals(File(project, "design/mock.png").absoluteFile.normalize(), McpTools.path("design/mock.png"))
        val absolute = File(System.getProperty("java.io.tmpdir"), "elsewhere.png").absoluteFile
        assertEquals(absolute.normalize(), McpTools.path(absolute.path))
    }

    @Test
    fun `replies are single-line JSON`() {
        val reply = McpServer.handle("""{"jsonrpc":"2.0","id":9,"method":"ping"}""")!!
        assertTrue('\n' !in Json.encodeToString(JsonObject.serializer(), reply))
    }
}
