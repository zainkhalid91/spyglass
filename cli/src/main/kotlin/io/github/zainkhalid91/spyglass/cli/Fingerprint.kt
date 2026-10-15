package io.github.zainkhalid91.spyglass.cli

import java.awt.Color
import java.awt.image.BufferedImage
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.sqrt

/** What Spyglass remembers about an image: exact identity, visual similarity and main colours. */
internal class Fingerprint(
    /** SHA-256 of the pixels (not the file), so re-encoding the same picture gives the same value. */
    val sha: String,
    /** 64-bit difference hash: images that look alike differ in few bits. */
    val dhash: Long,
    /** Up to six main colours as #RRGGBB, most used first. */
    val palette: List<String>,
    val width: Int,
    val height: Int,
) {
    companion object {
        fun of(image: BufferedImage): Fingerprint {
            val w = image.width
            val h = image.height
            val pixels = image.getRGB(0, 0, w, h, null, 0, w)
            return Fingerprint(sha(pixels, w, h), dhash(image), palette(pixels), w, h)
        }

        /** How many of the 64 bits differ: 0 is identical, under about 10 looks alike. */
        fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

        private fun sha(pixels: IntArray, w: Int, h: Int): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = java.nio.ByteBuffer.allocate(8 + pixels.size * 4)
            buffer.putInt(w).putInt(h)
            pixels.forEach(buffer::putInt)
            return digest.digest(buffer.array()).joinToString("") { "%02x".format(it) }
        }

        /** Gradients between neighbouring cells of a 9x8 greyscale thumbnail. */
        private fun dhash(image: BufferedImage): Long {
            val small = BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY)
            val g = small.createGraphics()
            try {
                g.color = Color.WHITE
                g.fillRect(0, 0, 9, 8)
                g.drawImage(image.getScaledInstance(9, 8, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null)
            } finally {
                g.dispose()
            }
            var hash = 0L
            for (y in 0 until 8) for (x in 0 until 8) {
                val left = small.raster.getSample(x, y, 0)
                val right = small.raster.getSample(x + 1, y, 0)
                hash = (hash shl 1) or (if (left > right) 1L else 0L)
            }
            return hash
        }

        /**
         * Main colours: pixels are grouped into 4-bit-per-channel buckets, buckets under 2% of the
         * image are dropped, and buckets that look the same are merged.
         */
        private fun palette(pixels: IntArray): List<String> {
            val step = max(1, pixels.size / 200_000) // sample big images
            val counts = IntArray(4096)
            val sums = Array(4096) { LongArray(3) }
            var sampled = 0
            for (i in pixels.indices step step) {
                val p = pixels[i]
                if ((p ushr 24) < 128) continue // mostly transparent
                val r = p shr 16 and 0xFF
                val g = p shr 8 and 0xFF
                val b = p and 0xFF
                val bucket = (r shr 4 shl 8) or (g shr 4 shl 4) or (b shr 4)
                counts[bucket]++
                sums[bucket][0] += r.toLong(); sums[bucket][1] += g.toLong(); sums[bucket][2] += b.toLong()
                sampled++
            }
            if (sampled == 0) return emptyList()
            val chosen = mutableListOf<IntArray>()
            for (bucket in counts.indices.sortedByDescending { counts[it] }) {
                if (counts[bucket] < sampled * 0.02 || chosen.size == 6) break
                val n = counts[bucket]
                val color = IntArray(3) { (sums[bucket][it] / n).toInt() }
                if (chosen.none { colorDistance(it, color) < 28 }) chosen += color
            }
            return chosen.map { "#%02X%02X%02X".format(it[0], it[1], it[2]) }
        }

        private fun colorDistance(a: IntArray, b: IntArray): Double =
            sqrt(((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2])).toDouble())
    }
}
