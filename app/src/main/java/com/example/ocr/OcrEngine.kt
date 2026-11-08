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

    /** Copies the bundled (compressed) models once per model version (Tesseract needs real files). */
    private fun ensureModels() {
        val dir = File(dataDir, "tessdata").apply { mkdirs() }
        val marker = File(dir, "models.version")
        if (marker.exists() && marker.readText() == MODEL_VERSION && LANGS.all { File(dir, "$it.traineddata").length() > 0 }) return
        for (lang in LANGS) {
            val f = File(dir, "$lang.traineddata")
            val tmp = File(dir, "$lang.tmp")
            context.assets.open("tessdata/$lang.traineddata").use { i -> tmp.outputStream().use { i.copyTo(it) } }
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
        marker.writeText(MODEL_VERSION)
    }

    /** OCR of one bitmap: only confident words; empty when the image has no clear text. */
    fun recognize(bitmap: Bitmap): String {
        ensureModels()
        val gray = preprocess(bitmap)
        val api = TessBaseAPI()
        return try {
            if (!api.init(dataDir.absolutePath, LANGS.joinToString("+"), TessBaseAPI.OEM_LSTM_ONLY)) return ""
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            api.setImage(gray)
            api.utF8Text // runs recognition
            val lines = mutableListOf<MutableList<OcrFilter.Word>>()
            val it = api.resultIterator ?: return ""
            try {
                it.begin()
                do {
                    val w = it.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD) ?: continue
                    if (lines.isEmpty() || it.isAtBeginningOf(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)) lines += mutableListOf<OcrFilter.Word>()
                    lines.last() += OcrFilter.Word(w, it.confidence(TessBaseAPI.PageIteratorLevel.RIL_WORD))
                } while (it.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
            } finally { it.delete() }
            OcrFilter.filter(lines).text
        } finally {
            api.recycle()
            if (gray !== bitmap) gray.recycle()
        }
    }

    /** Grayscale + contrast stretch; small images are enlarged so text strokes are thick enough. */
    private fun preprocess(src: Bitmap): Bitmap {
        val scale = if (maxOf(src.width, src.height) < 1600) 2f else 1f
        val w = (src.width * scale).toInt(); val h = (src.height * scale).toInt()
        val scaled = if (scale != 1f) Bitmap.createScaledBitmap(src, w, h, true) else src
        val px = IntArray(w * h)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        var lo = 255; var hi = 0
        val lum = IntArray(px.size) { i ->
            val p = px[i]; val l = ((p shr 16 and 255) * 299 + (p shr 8 and 255) * 587 + (p and 255) * 114) / 1000
            if (l < lo) lo = l; if (l > hi) hi = l; l
        }
        val range = (hi - lo).coerceAtLeast(1)
        for (i in px.indices) { val v = ((lum[i] - lo) * 255 / range).coerceIn(0, 255); px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        if (scaled !== src) scaled.recycle()
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
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
        /** Bump when the bundled models change. */
        const val MODEL_VERSION = "ara-best+eng-fast-2"

        fun clean(raw: String): String = raw.lines()
            .map { it.trim() }
            .filter { line -> line.count { it.isLetterOrDigit() } >= 2 } // drop noise lines
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
