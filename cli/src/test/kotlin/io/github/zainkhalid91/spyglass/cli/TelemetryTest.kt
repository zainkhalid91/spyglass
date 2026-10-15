package io.github.zainkhalid91.spyglass.cli

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelemetryTest {
    private val realHome = Telemetry.home
    private val home = createTempDirectory("spyglass-home").toFile()

    @BeforeTest
    fun useTempHome() {
        Telemetry.home = home
    }

    @AfterTest
    fun restore() {
        Telemetry.home = realHome
        home.deleteRecursively()
    }

    @Test
    fun `nothing is counted until the developer opts in`() {
        assertFalse(Telemetry.enabled)
        Telemetry.count("render.runs")
        Telemetry.flush()
        assertTrue(Telemetry.counters().isEmpty())
        assertFalse(File(home, "usage.json").exists())
    }

    @Test
    fun `counters add up across flushes once enabled, and turning off deletes them`() {
        Telemetry.setEnabled(true)
        Telemetry.count("render.runs")
        Telemetry.flush()
        Telemetry.count("render.runs", 2)
        Telemetry.similarity(0.97)
        Telemetry.flush()
        assertEquals(mapOf("diff.similarity.95-99" to 1, "render.runs" to 3), Telemetry.counters())

        Telemetry.setEnabled(false)
        assertTrue(Telemetry.counters().isEmpty())
        assertFalse(File(home, "usage.json").exists())
    }

    @Test
    fun `the report holds only counters and versions`() {
        Telemetry.setEnabled(true)
        Telemetry.count("cli.render")
        Telemetry.flush()
        val report = Telemetry.report()
        assertTrue("\"cli.render\":1" in report)
        assertFalse(System.getProperty("user.home") in report)
        assertFalse(System.getProperty("user.name") in report)
    }

    @Test
    fun `error messages are reduced to categories with no user text`() {
        assertEquals("IllegalStateException", Telemetry.errorCategory("IllegalStateException: Secret project path C:/work (at x.Kt(A.kt:3))"))
        assertEquals("other", Telemetry.errorCategory("PaymentGatewayException: card 4242 declined"))
        assertEquals("drew-nothing", Telemetry.errorCategory("sample.CardsKt.Empty drew nothing (measured 0x0 px)."))
        assertEquals("unsupported-parameters", Telemetry.errorCategory("sample.CardsKt.Row has parameters. Annotate one"))
    }
}
