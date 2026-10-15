package io.github.zainkhalid91.spyglass.cli

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckCommandTest {
    private val root = createTempDirectory("spyglass-check").toFile()
    private val previews = File(root, "build/spyglass/previews")

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun entry(stem: String, image: File?, error: String? = null) = PreviewEntry(
        function = "sample.CardsKt.$stem", name = "", parameter = null, image = image,
        widthPx = 100, heightPx = 100, density = 2.0, error = error,
    )

    @Test
    fun `sorts previews into same, changed, new and error`() {
        val library = Library(root)
        val same = testImage(File(previews, "CardsKt.Same.png"), 100, 100, listOf(intArrayOf(10, 10, 20, 20)))
        val changed = testImage(File(previews, "CardsKt.Changed.png"), 100, 100, listOf(intArrayOf(10, 10, 20, 20)))
        library.approve(same, "CardsKt.Same.png", "CardsKt.Same")
        library.approve(changed, "CardsKt.Changed.png", "CardsKt.Changed")
        library.approve(testImage(File(root, "old/CardsKt.Broken.png"), 100, 100), "CardsKt.Broken.png", "CardsKt.Broken")
        // The "changed" preview now renders differently.
        testImage(changed, 100, 100, listOf(intArrayOf(60, 60, 30, 30)))
        val fresh = testImage(File(previews, "CardsKt.Fresh.png"), 100, 100)

        val results = CheckCommand.evaluate(
            listOf(
                entry("Same", same),
                entry("Changed", changed),
                entry("Fresh", fresh),
                entry("Broken", null, "IllegalStateException: boom"),
                entry("NeverWorked", null, "IllegalStateException: boom"),
            ),
            root, library, threshold = 0.1, failUnder = 0.999,
        ).associateBy { it.preview }

        assertEquals("same", results.getValue("CardsKt.Same").status)
        assertEquals("changed", results.getValue("CardsKt.Changed").status)
        assertTrue(results.getValue("CardsKt.Changed").regression)
        assertTrue(results.getValue("CardsKt.Changed").heatmap!!.isFile)
        assertEquals("new", results.getValue("CardsKt.Fresh").status)
        assertTrue(results.getValue("CardsKt.Broken").regression, "it had a baseline, so failing now is a regression")
        assertFalse(results.getValue("CardsKt.NeverWorked").regression)
    }
}
