package com.example.core.media

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Makes every image displayable and searchable on every supported Android version:
 * - JPEG, PNG, WebP (incl. animated), GIF (incl. animated), BMP, SVG: kept as is.
 * - HEIC/HEIF (Android 9+) and AVIF (Android 12+): converted to high-quality JPEG.
 * - TIFF (scanners): decoded by [TiffDecoder] to PNG (scans) / JPEG (photos).
 * When conversion is impossible the original is kept as a file attachment (never dropped).
 */
object ImageNormalizer {
    enum class Format { JPEG, PNG, WEBP, GIF, BMP, SVG, HEIF, AVIF, TIFF, UNKNOWN }

    data class Prepared(val file: File, val name: String, val mime: String, val isImage: Boolean, val converted: Boolean, val unsupported: Boolean)

    fun detect(head: ByteArray, name: String, mime: String): Format {
        fun at(i: Int, s: String) = head.size >= i + s.length && String(head, i, s.length, Charsets.ISO_8859_1) == s
        return when {
            head.size >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() -> Format.JPEG
            at(1, "PNG") -> Format.PNG
            at(0, "GIF8") -> Format.GIF
            at(0, "RIFF") && at(8, "WEBP") -> Format.WEBP
            at(0, "BM") -> Format.BMP
            at(0, "II*\u0000") || at(0, "MM\u0000*") -> Format.TIFF
            at(4, "ftyp") && (at(8, "avif") || at(8, "avis")) -> Format.AVIF
            at(4, "ftyp") && listOf("heic", "heix", "hevc", "heim", "heis", "mif1", "msf1").any { at(8, it) } -> Format.HEIF
            mime == "image/svg+xml" || name.endsWith(".svg", true) || String(head, Charsets.UTF_8).contains("<svg") -> Format.SVG
            else -> Format.UNKNOWN
        }
    }

    fun mimeOf(f: Format) = when (f) {
        Format.JPEG -> "image/jpeg"; Format.PNG -> "image/png"; Format.WEBP -> "image/webp"; Format.GIF -> "image/gif"
        Format.BMP -> "image/bmp"; Format.SVG -> "image/svg+xml"; Format.HEIF -> "image/heif"; Format.AVIF -> "image/avif"
        Format.TIFF -> "image/tiff"; Format.UNKNOWN -> "application/octet-stream"
    }

    fun isImageName(name: String, mime: String) = mime.startsWith("image/") ||
        name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "heic", "heif", "avif", "tif", "tiff")

    /** [src] is a temp file owned by the caller; the result may be [src] itself or a new file next to it. */
    fun prepare(src: File, name: String, mime: String): Prepared {
        val head = src.inputStream().use { s -> ByteArray(64).let { b -> val n = s.read(b); if (n <= 0) ByteArray(0) else b.copyOf(n) } }
        val fmt = detect(head, name, mime)
        val base = name.substringBeforeLast('.').ifBlank { "image" }
        return when (fmt) {
            Format.JPEG, Format.PNG, Format.WEBP, Format.GIF, Format.BMP, Format.SVG ->
                Prepared(src, name, mimeOf(fmt), isImage = true, converted = false, unsupported = false)
            Format.HEIF, Format.AVIF -> {
                val supported = Build.VERSION.SDK_INT >= (if (fmt == Format.AVIF) 31 else 28)
                val bmp = if (supported) runCatching {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(src)) { d, _, _ -> d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
                }.getOrNull() else null
                if (bmp == null) Prepared(src, name, mimeOf(fmt), isImage = false, converted = false, unsupported = true)
                else Prepared(write(src, bmp, Bitmap.CompressFormat.JPEG, 92), "$base.jpg", "image/jpeg", true, true, false)
            }
            Format.TIFF -> {
                val bmp = TiffDecoder.decode(src.readBytes())
                if (bmp == null) Prepared(src, name, "image/tiff", isImage = false, converted = false, unsupported = true)
                else {
                    val photo = !isMostlyGray(bmp)
                    val fmtOut = if (photo) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
                    Prepared(write(src, bmp, fmtOut, 92), "$base.${if (photo) "jpg" else "png"}", if (photo) "image/jpeg" else "image/png", true, true, false)
                }
            }
            Format.UNKNOWN -> Prepared(src, name, mime, isImage = false, converted = false, unsupported = isImageName(name, mime))
        }
    }

    private fun write(src: File, bmp: Bitmap, fmt: Bitmap.CompressFormat, q: Int): File {
        val out = File(src.parentFile, src.name + if (fmt == Bitmap.CompressFormat.PNG) ".png" else ".jpg")
        out.outputStream().use { bmp.compress(fmt, q, it) }
        bmp.recycle()
        return out
    }

    private fun isMostlyGray(b: Bitmap): Boolean {
        val stepX = maxOf(1, b.width / 40); val stepY = maxOf(1, b.height / 40)
        var colored = 0; var total = 0
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                val p = b.getPixel(x, y)
                val r = (p shr 16) and 255; val g = (p shr 8) and 255; val bl = p and 255
                if (maxOf(r, g, bl) - minOf(r, g, bl) > 24) colored++
                total++; x += stepX
            }
            y += stepY
        }
        return colored * 20 < total
    }

    fun toBytes(b: Bitmap): ByteArray = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}
