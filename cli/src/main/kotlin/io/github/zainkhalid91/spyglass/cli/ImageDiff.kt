package io.github.zainkhalid91.spyglass.cli

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

internal class DiffRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    /** Share of the region's pixels that differ, 0..1. */
    val mismatch: Double,
    val diffPixels: Int,
    /** Rough position in the image, e.g. "top-left", for humans and agents. */
    val where: String,
)

internal class DiffResult(
    val similarity: Double,
    val diffPixels: Int,
    val width: Int,
    val height: Int,
    val expectedWidth: Int,
    val expectedHeight: Int,
    val heatmap: File,
    val regions: List<DiffRegion>,
) {
    val resized get() = expectedWidth != width || expectedHeight != height
}

/**
 * Perceptual pixel comparison. The colour distance is the YIQ metric used by pixelmatch, so a
 * threshold of 0.1 means the same thing it does there. The expected image is scaled to the actual
 * image's size when they differ, so a design mock exported at another resolution still compares.
 */
internal object ImageDiff {
    private const val MAX_YIQ_DELTA = 35215.0
    private const val MAX_REGIONS = 8

    fun compare(actualFile: File, expectedFile: File, threshold: Double, heatmapFile: File): DiffResult {
        val actualRaw = read(actualFile)
        val expectedRaw = read(expectedFile)
        val w = actualRaw.width
        val h = actualRaw.height
        val actual = pixelsOverWhite(actualRaw, w, h)
        val expected = pixelsOverWhite(expectedRaw, w, h)

        val maxDelta = MAX_YIQ_DELTA * threshold * threshold
        val diff = BooleanArray(w * h)
        val heat = IntArray(w * h)
        var diffCount = 0
        for (i in actual.indices) {
            if (colorDelta(actual[i], expected[i]) > maxDelta) {
                diff[i] = true
                diffCount++
                heat[i] = DIFF_COLOR
            } else {
                heat[i] = faded(actual[i])
            }
        }

        heatmapFile.absoluteFile.parentFile?.mkdirs()
        val heatImage = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        heatImage.setRGB(0, 0, w, h, heat, 0, w)
        ImageIO.write(heatImage, "png", heatmapFile)

        return DiffResult(
            similarity = 1.0 - diffCount.toDouble() / (w * h),
            diffPixels = diffCount,
            width = w,
            height = h,
            expectedWidth = expectedRaw.width,
            expectedHeight = expectedRaw.height,
            heatmap = heatmapFile,
            regions = regions(diff, w, h),
        )
    }

    private fun read(file: File): BufferedImage {
        if (!file.isFile) throw CliError("no such image: $file")
        return ImageIO.read(file) ?: throw CliError("not a readable image: $file")
    }

    /** RGB pixels at [w]x[h], with transparency flattened onto white like a preview background. */
    private fun pixelsOverWhite(image: BufferedImage, w: Int, h: Int): IntArray {
        val canvas = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = canvas.createGraphics()
        try {
            g.color = Color.WHITE
            g.fillRect(0, 0, w, h)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.drawImage(image, 0, 0, w, h, null)
        } finally {
            g.dispose()
        }
        return canvas.getRGB(0, 0, w, h, null, 0, w)
    }

    private fun colorDelta(a: Int, b: Int): Double {
        val r1 = (a shr 16 and 0xFF).toDouble(); val g1 = (a shr 8 and 0xFF).toDouble(); val b1 = (a and 0xFF).toDouble()
        val r2 = (b shr 16 and 0xFF).toDouble(); val g2 = (b shr 8 and 0xFF).toDouble(); val b2 = (b and 0xFF).toDouble()
        val y = (r1 * 0.29889531 + g1 * 0.58662247 + b1 * 0.11448223) - (r2 * 0.29889531 + g2 * 0.58662247 + b2 * 0.11448223)
        val i = (r1 * 0.59597799 - g1 * 0.27417610 - b1 * 0.32180189) - (r2 * 0.59597799 - g2 * 0.27417610 - b2 * 0.32180189)
        val q = (r1 * 0.21147017 - g1 * 0.52261711 + b1 * 0.31114694) - (r2 * 0.21147017 - g2 * 0.52261711 + b2 * 0.31114694)
        return 0.5053 * y * y + 0.299 * i * i + 0.1957 * q * q
    }

    private const val DIFF_COLOR = 0xFF2D55

    /** Unchanged pixels become a light grey copy of the actual image, so red differences stand out. */
    private fun faded(rgb: Int): Int {
        val luma = (rgb shr 16 and 0xFF) * 0.299 + (rgb shr 8 and 0xFF) * 0.587 + (rgb and 0xFF) * 0.114
        val v = (255 + (luma - 255) * 0.3).toInt().coerceIn(0, 255)
        return (v shl 16) or (v shl 8) or v
    }

    /** Groups changed pixels into rectangles: mark grid cells with changes, then join touching cells. */
    private fun regions(diff: BooleanArray, w: Int, h: Int): List<DiffRegion> {
        val cell = max(8, min(w, h) / 32)
        val cols = ceil(w / cell.toDouble()).toInt()
        val rows = ceil(h / cell.toDouble()).toInt()
        val counts = IntArray(cols * rows)
        for (y in 0 until h) for (x in 0 until w) {
            if (diff[y * w + x]) counts[(y / cell) * cols + x / cell]++
        }
        // Ignore cells where only a stray pixel or two changed (anti-aliasing noise).
        val marked = BooleanArray(counts.size) { counts[it] >= max(2, cell * cell / 100) }

        val seen = BooleanArray(counts.size)
        val boxes = mutableListOf<Box>()
        for (start in counts.indices) {
            if (!marked[start] || seen[start]) continue
            var minC = cols; var minR = rows; var maxC = -1; var maxR = -1
            val stack = ArrayDeque(listOf(start))
            seen[start] = true
            while (stack.isNotEmpty()) {
                val c = stack.removeLast()
                val col = c % cols
                val row = c / cols
                minC = min(minC, col); maxC = max(maxC, col); minR = min(minR, row); maxR = max(maxR, row)
                for (dr in -1..1) for (dc in -1..1) {
                    val nr = row + dr
                    val nc = col + dc
                    if (nr !in 0 until rows || nc !in 0 until cols) continue
                    val n = nr * cols + nc
                    if (marked[n] && !seen[n]) {
                        seen[n] = true
                        stack.addLast(n)
                    }
                }
            }
            boxes += Box(minC * cell, minR * cell, min(w, (maxC + 1) * cell), min(h, (maxR + 1) * cell))
        }

        return mergeNearby(boxes, gap = cell)
            .map { b ->
                var changed = 0
                for (py in b.top until b.bottom) for (px in b.left until b.right) if (diff[py * w + px]) changed++
                val area = (b.right - b.left) * (b.bottom - b.top)
                DiffRegion(
                    b.left, b.top, b.right - b.left, b.bottom - b.top,
                    changed.toDouble() / area, changed,
                    where((b.left + b.right) / 2, (b.top + b.bottom) / 2, w, h),
                )
            }
            .sortedByDescending { it.diffPixels }
            .take(MAX_REGIONS)
    }

    private data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun near(o: Box, gap: Int) =
            left <= o.right + gap && o.left <= right + gap && top <= o.bottom + gap && o.top <= bottom + gap

        fun union(o: Box) = Box(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom))
    }

    /** Joins boxes less than [gap] apart, so one changed word or number reads as one region. */
    private fun mergeNearby(boxes: List<Box>, gap: Int): List<Box> {
        val result = boxes.toMutableList()
        var merged = true
        while (merged) {
            merged = false
            loop@ for (i in result.indices) for (j in i + 1 until result.size) {
                if (result[i].near(result[j], gap)) {
                    result[i] = result[i].union(result[j])
                    result.removeAt(j)
                    merged = true
                    break@loop
                }
            }
        }
        return result
    }

    private fun where(cx: Int, cy: Int, w: Int, h: Int): String {
        val vertical = listOf("top", "middle", "bottom")[min(2, cy * 3 / h)]
        val horizontal = listOf("left", "center", "right")[min(2, cx * 3 / w)]
        return if (vertical == "middle" && horizontal == "center") "center" else "$vertical-$horizontal"
    }
}
