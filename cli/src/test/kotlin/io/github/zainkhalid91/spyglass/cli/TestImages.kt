package io.github.zainkhalid91.spyglass.cli

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Writes a white PNG with optional filled rectangles (x, y, w, h). */
internal fun testImage(file: File, w: Int, h: Int, rects: List<IntArray> = emptyList(), color: Color = Color.RED): File {
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.color = Color.WHITE
    g.fillRect(0, 0, w, h)
    g.color = color
    rects.forEach { (x, y, rw, rh) -> g.fillRect(x, y, rw, rh) }
    g.dispose()
    file.parentFile?.mkdirs()
    ImageIO.write(img, "png", file)
    return file
}
