package io.github.zainkhalid91.spyglass.renderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewSpecTest {
    private fun spec(name: String = "", uiMode: Int = 0, parameterIndex: Int? = null) = PreviewSpec(
        className = "com.example.ui.CardsKt",
        methodName = "CardPreview",
        name = name,
        group = "",
        widthDp = -1,
        heightDp = -1,
        showBackground = false,
        backgroundColor = 0L,
        fontScale = 1f,
        uiMode = uiMode,
        device = "",
        showSystemUi = false,
        parameterIndex = parameterIndex,
    )

    @Test
    fun `file names use the class, function, preview name and parameter index`() {
        assertEquals("CardsKt.CardPreview", spec().fileStem)
        assertEquals("CardsKt.CardPreview_Large_text", spec(name = "Large text").fileStem)
        assertEquals("CardsKt.CardPreview_Dark_2", spec(name = "Dark", parameterIndex = 2).fileStem)
        assertEquals("CardsKt.CardPreview_85_", spec(name = "85%").fileStem)
    }

    @Test
    fun `dark comes from the night bits of uiMode`() {
        assertTrue(spec(uiMode = 0x21).dark) // UI_MODE_NIGHT_YES | UI_MODE_TYPE_NORMAL
        assertFalse(spec(uiMode = 0x11).dark) // UI_MODE_NIGHT_NO | UI_MODE_TYPE_NORMAL
        assertFalse(spec(uiMode = 0).dark)
    }

    @Test
    fun `filters match the function or the preview name, ignoring case`() {
        assertTrue(spec().matches("cardpreview"))
        assertTrue(spec(name = "Big text").matches("BIG"))
        assertFalse(spec(name = "Big text").matches("Dark"))
    }
}
