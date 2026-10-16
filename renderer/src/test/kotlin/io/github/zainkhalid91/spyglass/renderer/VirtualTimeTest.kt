package io.github.zainkhalid91.spyglass.renderer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VirtualTimeTest {
    @Test
    fun `delays wait for the clock, not the wall, and wake in order`() {
        val clock = VirtualTime()
        val log = mutableListOf<String>()
        val scope = CoroutineScope(clock)
        scope.launch { delay(300); log += "loaded" }
        scope.launch { delay(100); log += "spinner"; delay(100); log += "spinner again" }
        assertEquals(emptyList(), log)
        assertEquals(100_000_000L, clock.nextWakeup())

        clock.advanceTo(150_000_000L)
        assertEquals(listOf("spinner"), log)
        clock.advanceTo(1_000_000_000L)
        assertEquals(listOf("spinner", "spinner again", "loaded"), log)
        assertNull(clock.nextWakeup())
    }

    @Test
    fun `cancelled delays are not waited for`() {
        val clock = VirtualTime()
        val job = CoroutineScope(clock).launch { delay(500) }
        job.cancel()
        assertNull(clock.nextWakeup())
    }
}
