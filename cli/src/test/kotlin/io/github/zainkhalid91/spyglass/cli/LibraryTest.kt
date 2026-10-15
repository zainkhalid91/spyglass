package io.github.zainkhalid91.spyglass.cli

import java.awt.Color
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryTest {
    private val project = createTempDirectory("spyglass-library").toFile()

    @AfterTest
    fun cleanUp() {
        project.deleteRecursively()
    }

    private fun img(name: String, rects: List<IntArray> = emptyList(), color: Color = Color.RED, w: Int = 200, h: Int = 200) =
        testImage(File(project, "in/$name"), w, h, rects, color)

    @Test
    fun `the same pixels are one reference, seen again`() {
        val library = Library(project)
        val a = library.record(img("a.png", listOf(intArrayOf(10, 10, 50, 50))), Library.KIND_RENDER, preview = "CardsKt.Card")
        val again = library.record(img("copy.png", listOf(intArrayOf(10, 10, 50, 50))), Library.KIND_RENDER)
        assertEquals(a.id, again.id)
        assertEquals(2, again.seen)
        assertEquals("CardsKt.Card", again.preview)
        assertEquals(1, library.size)
        assertTrue(library.imageFile(a.id).isFile)
    }

    @Test
    fun `fingerprints separate different images and group similar ones`() {
        val base = Fingerprint.of(ImageIO.read(img("base.png", listOf(intArrayOf(20, 20, 80, 80)))))
        val nudged = Fingerprint.of(ImageIO.read(img("nudged.png", listOf(intArrayOf(22, 20, 80, 80)))))
        val other = Fingerprint.of(ImageIO.read(img("other.png", listOf(intArrayOf(120, 120, 60, 60)), Color.BLUE)))
        assertNotEquals(base.sha, nudged.sha)
        assertTrue(Fingerprint.distance(base.dhash, nudged.dhash) < Fingerprint.distance(base.dhash, other.dhash))
        assertEquals(listOf("#FFFFFF", "#FF0000"), base.palette)
    }

    @Test
    fun `designs are found by name or id prefix, and similar images are ranked`() {
        val library = Library(project)
        val design = library.record(img("mock.jpg.png", listOf(intArrayOf(20, 20, 80, 80))), Library.KIND_DESIGN, name = "plan-card")
        library.record(img("far.png", listOf(intArrayOf(120, 0, 80, 200)), Color.BLUE), Library.KIND_RENDER)
        assertEquals(design.id, library.find("Plan-Card")?.id)
        assertEquals(design.id, library.find(design.id.take(6))?.id)
        assertNull(library.find("nothing-like-this"))

        val closest = library.similar(img("near.png", listOf(intArrayOf(21, 20, 80, 80))))
        assertEquals(design.id, closest.first().first.id)
    }

    @Test
    fun `the index survives a restart, and a damaged one is set aside`() {
        Library(project).apply {
            record(img("a.png"), Library.KIND_DESIGN, name = "blank").note = "an empty screen"
            save()
        }
        val reloaded = Library(project).find("blank")
        assertNotNull(reloaded)
        assertEquals("an empty screen", reloaded.note)

        File(project, ".spyglass/library/refs.json").writeText("{ not json")
        assertEquals(0, Library(project).size)
        assertTrue(File(project, ".spyglass/library/refs.json.bak").isFile)
    }

    @Test
    fun `old renders are evicted, designs and approved renders are kept`() {
        val library = Library(project, maxRenders = 2)
        val design = library.record(img("design.png", listOf(intArrayOf(0, 0, 10, 10))), Library.KIND_DESIGN)
        val approved = img("approved.png", listOf(intArrayOf(0, 0, 20, 20)))
        library.approve(approved, "approved.png", "CardsKt.Approved")
        repeat(4) { i -> library.record(img("r$i.png", listOf(intArrayOf(i * 30, 100, 20, 20))), Library.KIND_RENDER) }
        assertNotNull(library.find(design.id))
        assertTrue(library.list().any { "approved" in it.tags })
        assertEquals(2, library.list(Library.KIND_RENDER).count { "approved" !in it.tags })
        assertTrue(File(project, ".spyglass/baselines/approved.png").isFile)
    }

    @Test
    fun `a render saved as a design becomes a full-size design`() {
        // Regression: the design kept its render kind and thumbnail, so it couldn't be found by name.
        val library = Library(project)
        val big = img("big.png", listOf(intArrayOf(100, 100, 300, 300)), w = 900, h = 900)
        library.record(big, Library.KIND_RENDER, preview = "CardsKt.Big")
        assertEquals(480, ImageIO.read(library.imageFile(library.list().single().id)).width)

        val design = library.record(big, Library.KIND_DESIGN, name = "big-card")
        assertEquals(Library.KIND_DESIGN, design.kind)
        assertEquals(design.id, library.list(Library.KIND_DESIGN).single().id)
        assertEquals(900, ImageIO.read(library.imageFile(design.id)).width)
    }

    @Test
    fun `baseline keys keep modules apart`() {
        val root = File("/work/app")
        assertEquals("CardsKt.Card.png", Library.baselineKey(File(root, "build/spyglass/previews/CardsKt.Card.png"), root))
        assertEquals("shared/CardsKt.Card.png", Library.baselineKey(File(root, "shared/build/spyglass/previews/CardsKt.Card.png"), root))
    }
}
