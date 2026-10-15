package io.github.zainkhalid91.spyglass.cli

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageDiffTest {
    private val dir = createTempDirectory("spyglass-diff").toFile()

    /** A white image, optionally with filled rectangles (x, y, w, h) in the given colour. */
    private fun image(name: String, w: Int, h: Int, rects: List<IntArray> = emptyList(), color: Color = Color.RED): File {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, w, h)
        g.color = color
        rects.forEach { (x, y, rw, rh) -> g.fillRect(x, y, rw, rh) }
        g.dispose()
        return File(dir, name).also { ImageIO.write(img, "png", it) }
    }

    private fun compare(actual: File, expected: File) = ImageDiff.compare(actual, expected, 0.1, File(dir, "heat.png"))

    @Test
    fun `identical images are fully similar with no regions`() {
        val a = image("a.png", 200, 200, rects = listOf(intArrayOf(20, 20, 40, 40)))
        val r = compare(a, a)
        assertEquals(1.0, r.similarity)
        assertEquals(0, r.diffPixels)
        assertTrue(r.regions.isEmpty())
        assertTrue(File(dir, "heat.png").isFile)
    }

    @Test
    fun `a changed area becomes one region in the right place`() {
        val plain = image("plain.png", 200, 200)
        val marked = image("marked.png", 200, 200, rects = listOf(intArrayOf(140, 20, 30, 30)))
        val r = compare(marked, plain)
        assertEquals(900, r.diffPixels)
        assertEquals(1, r.regions.size)
        val region = r.regions.single()
        assertEquals("top-right", region.where)
        assertTrue(region.x <= 140 && region.y <= 20 && region.x + region.width >= 170 && region.y + region.height >= 50)
    }

    @Test
    fun `nearby changes merge, distant ones stay apart`() {
        val plain = image("plain2.png", 400, 400)
        val near = image("near.png", 400, 400, rects = listOf(intArrayOf(20, 20, 20, 20), intArrayOf(48, 20, 20, 20)))
        assertEquals(1, compare(near, plain).regions.size)
        val far = image("far.png", 400, 400, rects = listOf(intArrayOf(20, 20, 20, 20), intArrayOf(340, 340, 20, 20)))
        assertEquals(2, compare(far, plain).regions.size)
    }

    @Test
    fun `colours below the threshold do not count`() {
        val white = image("white.png", 100, 100)
        val offWhite = image("offwhite.png", 100, 100, listOf(intArrayOf(0, 0, 100, 100)), Color(250, 250, 250))
        assertEquals(0, compare(offWhite, white).diffPixels)
    }

    @Test
    fun `a design of another size is scaled, and a different shape is flagged`() {
        val actual = image("actual.png", 200, 100)
        val sameShape = compare(actual, image("double.png", 400, 200))
        assertTrue(sameShape.resized)
        assertFalse(sameShape.stretched)
        val square = compare(actual, image("square.png", 200, 200))
        assertTrue(square.stretched)
    }

    @Test
    fun `missing or unreadable images are reported, not thrown raw`() {
        val a = image("ok.png", 10, 10)
        assertFailsWith<CliError> { compare(a, File(dir, "missing.png")) }
        val text = File(dir, "notes.png").also { it.writeText("not an image") }
        assertFailsWith<CliError> { compare(a, text) }
    }
}
