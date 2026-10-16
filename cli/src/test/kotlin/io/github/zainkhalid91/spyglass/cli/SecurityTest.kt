package io.github.zainkhalid91.spyglass.cli

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Each test here pins down a fix from the security review. */
class SecurityTest {
    private val dir = createTempDirectory("spyglass-security").toFile()
    private val realHome = Telemetry.home

    @AfterTest
    fun cleanUp() {
        McpTools.defaultProject = File(".")
        McpTools.allowAnyProject = false
        Telemetry.home = realHome
        dir.deleteRecursively()
    }

    @Test
    fun `a tampered library index cannot point outside the library`() {
        val victim = testImage(File(dir, "victim.png"), 10, 10)
        val index = File(dir, "project/.spyglass/library/refs.json")
        index.parentFile.mkdirs()
        index.writeText(
            """{"version":1,"refs":[
              {"id":"../../../victim","kind":"render","size":"10x10","dhash":"0","first":"a","last":"a","seen":1},
              {"id":"0123456789ab","kind":"render","size":"10x10","dhash":"0","first":"a","last":"a","seen":1}]}"""
        )
        val library = Library(File(dir, "project"), maxRenders = 0)
        assertEquals(1, library.size, "only the well-formed id is loaded")
        // Eviction deletes stored images; it must never reach the file the bad id pointed at.
        library.record(testImage(File(dir, "new.png"), 10, 10, listOf(intArrayOf(0, 0, 5, 5))), Library.KIND_RENDER)
        assertTrue(victim.isFile)
    }

    @Test
    fun `baselines cannot be written outside the baselines folder`() {
        val library = Library(File(dir, "project"))
        val render = testImage(File(dir, "r.png"), 10, 10)
        assertFailsWith<CliError> { library.approve(render, "../../escaped.png", "X") }
        assertTrue(!File(dir, "escaped.png").exists())
    }

    @Test
    fun `an agent cannot render outside the server's project unless allowed`() {
        Telemetry.home = File(dir, "home")
        McpTools.defaultProject = File(dir, "project").apply { mkdirs() }
        val reply = McpServer.handle(
            """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"render_previews","arguments":{"projectDir":"${File(dir, "elsewhere").invariantSeparatorsPath}"}}}"""
        )!!.getValue("result").jsonObject
        assertTrue(reply.getValue("isError").jsonPrimitive.boolean)
        val text = reply.getValue("content").jsonArray.first().jsonObject.getValue("text").jsonPrimitive.content
        assertTrue("--allow-any-project" in text)
    }

    @Test
    fun `huge images are refused from their header, before decoding`() {
        val huge = File(dir, "huge.png").apply { writeBytes(pngHeader(50_000, 50_000)) }
        val error = assertFailsWith<CliError> { Images.readChecked(huge) }
        assertTrue("too large" in error.message!!)
    }

    @Test
    fun `values that cmd_exe would execute are refused on the script fallback`() {
        assumeTrue(GradleBuild.isWindows, "the cmd.exe fallback only exists on Windows")
        val project = File(dir, "nowrapper").apply { mkdirs() }
        File(project, "settings.gradle.kts").writeText("")
        File(project, "gradlew.bat").writeText("@echo off")
        val build = GradleBuild.find(project)
        assertFailsWith<CliError> { build.run(listOf("spyglassRender", "-Pspyglass.filter=x&calc")) }
    }

    @Test
    fun `programs are found only through absolute PATH entries, never the current directory`() {
        // Regression: a bare "gh" on Windows ran gh.exe from the current directory (a cloned project) first.
        val planted = File(dir, "project").apply { mkdirs() }
        File(planted, "gh.exe").writeText("planted")
        val bin = File(dir, "bin").apply { mkdirs() }
        File(bin, "gh.exe").writeText("real")
        val path = listOf(".", "", "relative\\bin", bin.path).joinToString(File.pathSeparator)
        assertEquals(File(bin, "gh.exe").path, Executables.onPath("gh", path, windows = true))
        assertEquals(null, Executables.onPath("gh", ".${File.pathSeparator}relative", windows = true))
        if (GradleBuild.isWindows) assertTrue(File(Executables.cmd).isAbsolute) // cmd.exe is only used on Windows
    }

    /** A PNG signature and IHDR chunk claiming [w] x [h], with no pixel data at all. */
    private fun pngHeader(w: Int, h: Int): ByteArray {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        val chunk = ByteArrayOutputStream().also { c ->
            DataOutputStream(c).apply {
                write("IHDR".toByteArray())
                writeInt(w); writeInt(h)
                write(byteArrayOf(8, 2, 0, 0, 0))
            }
        }.toByteArray()
        out.writeInt(13)
        out.write(chunk)
        out.writeInt(CRC32().apply { update(chunk) }.value.toInt())
        return bytes.toByteArray()
    }
}
