package com.example.core.media

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * Minimal baseline TIFF decoder (Android has none): first image, chunky planar layout,
 * 1-bit bilevel or 8-bit gray / RGB / RGBA, compression none / LZW / PackBits, horizontal predictor.
 * Anything else (JPEG-in-TIFF, CCITT fax, tiles, 16-bit) returns null and the caller keeps the
 * original file as an attachment.
 */
object TiffDecoder {
    const val MAX_PIXELS = 40_000_000L

    fun decode(data: ByteArray): Bitmap? = try { decodeOrThrow(data) } catch (_: Exception) { null }

    private class Reader(val d: ByteArray, val le: Boolean) {
        fun u16(o: Int): Int = if (le) (d[o].toInt() and 255) or ((d[o + 1].toInt() and 255) shl 8)
        else ((d[o].toInt() and 255) shl 8) or (d[o + 1].toInt() and 255)
        fun u32(o: Int): Long = if (le) (u16(o).toLong() or (u16(o + 2).toLong() shl 16))
        else ((u16(o).toLong() shl 16) or u16(o + 2).toLong())
    }

    private fun decodeOrThrow(data: ByteArray): Bitmap? {
        if (data.size < 8) return null
        val le = when {
            data[0] == 'I'.code.toByte() && data[1] == 'I'.code.toByte() -> true
            data[0] == 'M'.code.toByte() && data[1] == 'M'.code.toByte() -> false
            else -> return null
        }
        val r = Reader(data, le)
        if (r.u16(2) != 42) return null
        val ifd = r.u32(4).toInt()
        val count = r.u16(ifd)
        val tags = HashMap<Int, LongArray>()
        for (i in 0 until count) {
            val e = ifd + 2 + i * 12
            val tag = r.u16(e); val type = r.u16(e + 2); val n = r.u32(e + 4).toInt()
            val size = when (type) { 1, 2, 6, 7 -> 1; 3, 8 -> 2; 4, 9 -> 4; else -> 8 }
            val valOff = if (size * n <= 4) e + 8 else r.u32(e + 8).toInt()
            tags[tag] = LongArray(n) { k ->
                when (size) { 1 -> (data[valOff + k].toLong() and 255); 2 -> r.u16(valOff + k * 2).toLong(); else -> r.u32(valOff + k * 4) }
            }
        }
        fun one(t: Int, def: Long) = tags[t]?.firstOrNull() ?: def
        val w = one(256, 0).toInt(); val h = one(257, 0).toInt()
        if (w <= 0 || h <= 0 || w.toLong() * h > MAX_PIXELS) return null
        val bps = one(258, 1).toInt()
        val compression = one(259, 1).toInt()
        val photometric = one(262, 1).toInt()
        val spp = one(277, 1).toInt()
        val predictor = one(317, 1).toInt()
        if (one(284, 1).toInt() != 1) return null // planar
        if (bps != 1 && bps != 8) return null
        if (compression !in setOf(1, 5, 32773)) return null
        val offsets = tags[273] ?: return null
        val counts = tags[279] ?: return null
        val rowsPerStrip = one(278, h.toLong()).toInt().coerceAtLeast(1)
        val rowBytes = if (bps == 1) (w + 7) / 8 else w * spp
        val raw = ByteArray(rowBytes * h)
        var dst = 0
        for (s in offsets.indices) {
            val strip = data.copyOfRange(offsets[s].toInt(), (offsets[s] + counts[s]).toInt().coerceAtMost(data.size))
            val rows = minOf(rowsPerStrip, h - s * rowsPerStrip)
            val expected = rows * rowBytes
            val out = when (compression) {
                1 -> strip
                5 -> lzw(strip, expected)
                else -> packBits(strip, expected)
            }
            System.arraycopy(out, 0, raw, dst, minOf(expected, out.size, raw.size - dst))
            dst += expected
            if (dst >= raw.size) break
        }
        if (predictor == 2 && bps == 8) for (y in 0 until h) {
            val base = y * rowBytes
            for (x in spp until rowBytes) raw[base + x] = (raw[base + x] + raw[base + x - spp]).toByte()
        }
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            px[i] = if (bps == 1) {
                val bit = (raw[y * rowBytes + x / 8].toInt() shr (7 - x % 8)) and 1
                val white = if (photometric == 0) bit == 0 else bit == 1
                if (white) -0x1 else -0x1000000
            } else {
                val o = y * rowBytes + x * spp
                when {
                    spp >= 3 -> {
                        val a = if (spp >= 4) raw[o + 3].toInt() and 255 else 255
                        (a shl 24) or ((raw[o].toInt() and 255) shl 16) or ((raw[o + 1].toInt() and 255) shl 8) or (raw[o + 2].toInt() and 255)
                    }
                    else -> {
                        var g = raw[o].toInt() and 255
                        if (photometric == 0) g = 255 - g
                        (255 shl 24) or (g shl 16) or (g shl 8) or g
                    }
                }
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun packBits(src: ByteArray, expected: Int): ByteArray {
        val out = ByteArrayOutputStream(expected)
        var i = 0
        while (i < src.size && out.size() < expected) {
            val n = src[i++].toInt()
            when {
                n in 0..127 -> { val len = minOf(n + 1, src.size - i); out.write(src, i, len); i += len }
                n in -127..-1 -> { if (i < src.size) { val b = src[i++].toInt(); repeat(1 - n) { out.write(b) } } }
            }
        }
        return out.toByteArray()
    }

    /** TIFF LZW (MSB-first, early change). */
    private fun lzw(src: ByteArray, expected: Int): ByteArray {
        val out = ByteArrayOutputStream(expected)
        val dict = ArrayList<ByteArray>(4096)
        fun reset() { dict.clear(); for (k in 0 until 256) dict.add(byteArrayOf(k.toByte())); dict.add(ByteArray(0)); dict.add(ByteArray(0)) }
        reset()
        var bitPos = 0L
        val totalBits = src.size * 8L
        var width = 9
        var prev: ByteArray? = null
        while (bitPos + width <= totalBits && out.size() < expected) {
            var code = 0
            for (b in 0 until width) {
                val p = bitPos + b
                code = (code shl 1) or ((src[(p / 8).toInt()].toInt() shr (7 - (p % 8).toInt())) and 1)
            }
            bitPos += width
            if (code == 257) break
            if (code == 256) { reset(); width = 9; prev = null; continue }
            val entry: ByteArray = when {
                code < dict.size -> dict[code]
                prev != null -> prev + prev[0]
                else -> break
            }
            out.write(entry)
            if (prev != null) dict.add(prev + entry[0])
            prev = entry
            width = when { dict.size + 1 >= 2047 -> 12; dict.size + 1 >= 1023 -> 11; dict.size + 1 >= 511 -> 10; else -> 9 }
        }
        return out.toByteArray()
    }
}
