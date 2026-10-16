package io.github.zainkhalid91.spyglass.cli

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Images prepared for an agent. An image costs roughly (width x height) / 750 tokens, so images are
 * scaled down before they are sent, and comparisons are sent as one labelled strip instead of three.
 */
internal object Images {
    /** PNG bytes of [file], scaled so its longer side is at most [maxPx]. */
    fun fit(file: File, maxPx: Int): ByteArray {
        val image = read(file)
        val scale = min(1.0, maxPx.toDouble() / max(image.width, image.height))
        return png(if (scale < 1.0) resize(image, (image.width * scale).roundToInt(), (image.height * scale).roundToInt()) else image)
    }

    /** Labelled panels side by side, scaled to one height, at most [maxWidth] wide in total. */
    fun strip(panels: List<Pair<String, File>>, maxWidth: Int, maxHeight: Int): ByteArray {
        val images = panels.map { (label, file) -> label to read(file) }
        val gap = 12
        val labelHeight = 26
        var height = min(maxHeight, images.maxOf { it.second.height })
        var widths = images.map { (_, img) -> img.width * height / img.height }
        val total = widths.sum() + gap * (images.size + 1)
        if (total > maxWidth) {
            val shrink = (maxWidth - gap * (images.size + 1)).toDouble() / widths.sum()
            height = (height * shrink).roundToInt()
            widths = images.map { (_, img) -> img.width * height / img.height }
        }

        val canvas = BufferedImage(widths.sum() + gap * (images.size + 1), height + labelHeight + gap, BufferedImage.TYPE_INT_RGB)
        val g = canvas.createGraphics()
        try {
            g.color = Color(0xF2F4F8)
            g.fillRect(0, 0, canvas.width, canvas.height)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.font = Font(Font.SANS_SERIF, Font.BOLD, 15)
            var x = gap
            images.forEachIndexed { i, (label, img) ->
                g.color = Color(0x14213D)
                g.drawString(label, x, 19)
                g.drawImage(resize(img, widths[i], height), x, labelHeight, null)
                x += widths[i] + gap
            }
        } finally {
            g.dispose()
        }
        return png(canvas)
    }

    private fun read(file: File): BufferedImage = readChecked(file)

    /** Images above this are refused: decoding one would need gigabytes and could take the server down. */
    const val MAX_PIXELS = 40_000_000L

    /**
     * Reads an image after checking its size from the header, so an enormous (or hostile) file is
     * rejected before any pixels are decoded.
     */
    fun readChecked(file: File): BufferedImage {
        if (!file.isFile) throw CliError("no such image: $file")
        val stream = ImageIO.createImageInputStream(file) ?: throw CliError("not a readable image: $file")
        stream.use {
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) throw CliError("not a readable image: $file")
            val reader = readers.next()
            try {
                reader.input = stream
                val w = reader.getWidth(0)
                val h = reader.getHeight(0)
                if (w.toLong() * h > MAX_PIXELS) {
                    throw CliError("image too large: ${w}x$h, over ${MAX_PIXELS / 1_000_000} megapixels: $file")
                }
                return reader.read(0)
            } catch (e: java.io.IOException) {
                throw CliError("not a readable image: $file (${e.message})")
            } finally {
                reader.dispose()
            }
        }
    }

    /** Smooth downscaling: area averaging keeps thin text legible where plain bilinear would alias. */
    private fun resize(image: BufferedImage, width: Int, height: Int): BufferedImage {
        if (width == image.width && height == image.height) return image
        val out = BufferedImage(max(1, width), max(1, height), BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.drawImage(image.getScaledInstance(out.width, out.height, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null)
        } finally {
            g.dispose()
        }
        return out
    }

    private fun png(image: BufferedImage): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
}
