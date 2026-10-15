package io.github.zainkhalid91.spyglass.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArgsTest {
    private fun parse(vararg raw: String) =
        Args.parse(raw.toList(), options = setOf("--out", "--threshold"), switches = setOf("--json"))

    @Test
    fun `separates options, switches and positionals`() {
        val args = parse("a.png", "--out", "heat.png", "--json", "b.png")
        assertEquals(listOf("a.png", "b.png"), args.positionals)
        assertEquals("heat.png", args["--out"])
        assertTrue(args.has("--json"))
        assertNull(args["--threshold"])
    }

    @Test
    fun `reads numbers and rejects text`() {
        assertEquals(0.25, parse("--threshold", "0.25").double("--threshold"))
        assertFailsWith<CliError> { parse("--threshold", "abc").double("--threshold") }
    }

    @Test
    fun `rejects unknown options and missing values`() {
        assertFailsWith<CliError> { parse("--nope") }
        assertFailsWith<CliError> { parse("--out") }
        assertFalse(parse().has("--json"))
    }
}
