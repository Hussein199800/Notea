package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/**
 * On-device OCR with Tesseract (Arabic + English, "fast" models bundled in assets/tessdata).
 * Nothing leaves the device. Quality: good for printed text, weak for handwriting.
 */
class OcrEngine(private val context: Context) {
    private val dataDir: File get() = File(context.filesDir, "tesseract")

    /** Copies the bundled models once (Tesseract needs real files). */
    private fun ensureModels() {
        val dir = File(dataDir, "tessdata").apply { mkdirs() }
        for (lang in LANGS) {
            val f = File(dir, "$lang.traineddata")
            val asset = "tessdata/$lang.traineddata"
            val size = context.assets.openFd(asset).use { it.length }
            if (!f.exists() || f.length() != size) {
                context.assets.open(asset).use { i -> f.outputStream().use { i.copyTo(it) } }
            }
        }
    }

    /** OCR of one bitmap. Returns cleaned text (may be empty). */
    fun recognize(bitmap: Bitmap): String {
        ensureModels()
        val api = TessBaseAPI()
        return try {
            if (!api.init(dataDir.absolutePath, LANGS.joinToString("+"))) return ""
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            api.setImage(bitmap)
            clean(api.utF8Text.orEmpty())
        } finally {
            api.recycle()
        }
    }

    fun recognizeImageFile(file: File): String {
        val bmp = decodeSampled(file) ?: return ""
        return try { recognize(bmp) } finally { bmp.recycle() }
    }

    /** Renders and OCRs up to [maxPages] pages of a PDF (scans and printed PDFs). */
    fun recognizePdf(file: File, maxPages: Int = 25): String {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfRenderer(pfd).use { r ->
            (0 until minOf(r.pageCount, maxPages)).joinToString("\n\n") { i ->
                r.openPage(i).use { page ->
                    val scale = 2000f / maxOf(page.width, page.height)
                    val bmp = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    try { recognize(bmp) } finally { bmp.recycle() }
                }
            }.trim()
        }
    }

    private fun decodeSampled(file: File, maxSide: Int = 2600): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, opts)
        if (opts.outWidth <= 0) return null
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > maxSide) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        val LANGS = listOf("ara", "eng")

        fun clean(raw: String): String = raw.lines()
            .map { it.trim() }
            .filter { line -> line.count { it.isLetterOrDigit() } >= 2 } // drop noise lines
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
