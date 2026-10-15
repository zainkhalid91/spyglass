package io.github.zainkhalid91.spyglass.renderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceSpecTest {
    private fun size(device: String) = DeviceSpec.parse(device)?.let { it.widthDp to it.heightDp }

    @Test
    fun `reads a dp spec as written`() {
        assertEquals(411 to 891, size("spec:width=411dp,height=891dp"))
    }

    @Test
    fun `keeps a wide spec wide when no orientation is given`() {
        // Regression: this used to be turned to portrait.
        assertEquals(240 to 140, size("spec:width=240dp,height=140dp"))
    }

    @Test
    fun `turns the screen only for an explicit orientation`() {
        assertEquals(891 to 411, size("spec:width=411dp,height=891dp,orientation=landscape"))
        assertEquals(800 to 1280, size("spec:width=1280dp,height=800dp,dpi=240,orientation=portrait"))
    }

    @Test
    fun `converts pixel specs with their dpi`() {
        assertEquals(393 to 851, size("spec:shape=Normal,width=1080,height=2340,unit=px,dpi=440"))
        assertEquals(540 to 1170, size("spec:width=1080px,height=2340px,dpi=320"))
    }

    @Test
    fun `ignores device ids and malformed specs`() {
        assertNull(size("id:pixel_5"))
        assertNull(size(""))
        assertNull(size("spec:width=abc,height=891dp"))
        assertNull(size("spec:height=891dp"))
    }
}
