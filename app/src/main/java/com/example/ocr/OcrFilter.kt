package com.example.ocr

/**
 * Pure (no Android) post-processing of OCR output: keeps only words the engine is confident about
 * and rejects the whole result when it looks like noise, so the user never sees random symbols
 * presented as "text from the image".
 */
object OcrFilter {
    data class Word(val text: String, val confidence: Float)
    data class Result(val text: String, val keptWords: Int, val meanConfidence: Float, val accepted: Boolean)

    const val MIN_WORD_CONF = 60f
    const val MIN_MEAN_CONF = 62f
    const val MIN_WORDS = 3

    private val arabic = Regex("[\\u0600-\\u06FF]")
    private val latin = Regex("[A-Za-z]")

    /** A word that mixes scripts, or is mostly punctuation/digits soup, is noise. */
    fun looksLikeNoise(w: String): Boolean {
        val letters = w.count { it.isLetter() }
        if (letters == 0) return !(w.any { it.isDigit() } && w.length in 1..12 && w.all { it.isDigit() || it in "/.:-٫,٬" })
        if (arabic.containsMatchIn(w) && latin.containsMatchIn(w)) return true
        val symbols = w.count { !it.isLetterOrDigit() }
        return symbols * 2 > w.length
    }

    fun filter(lines: List<List<Word>>): Result {
        val all = lines.flatten()
        if (all.isEmpty()) return Result("", 0, 0f, false)
        val out = StringBuilder()
        var kept = 0; var confSum = 0f
        for (line in lines) {
            val good = line.filter { it.confidence >= MIN_WORD_CONF && !looksLikeNoise(it.text) }
            // Keep a line only when most of it is readable; otherwise it's probably noise.
            if (good.isEmpty() || good.size * 10 < line.size * 6) continue
            out.append(good.joinToString(" ") { it.text.trim() }).append('\n')
            kept += good.size; confSum += good.sumOf { it.confidence.toDouble() }.toFloat()
        }
        val mean = if (kept == 0) 0f else confSum / kept
        val accepted = kept >= MIN_WORDS && mean >= MIN_MEAN_CONF
        return Result(if (accepted) out.toString().trim() else "", kept, mean, accepted)
    }
}
