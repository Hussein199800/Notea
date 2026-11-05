package com.example.core.text

/**
 * Normalizes text for *search and comparison only*. The original text is never modified;
 * normalized copies are stored in the FTS index and used to build queries.
 *
 * - Removes Arabic diacritics (tashkeel), Quranic marks and tatweel.
 * - Unifies alef/hamza/yaa/taa-marbuta variants.
 * - Maps Arabic-Indic and Persian digits to ASCII digits.
 * - Lower-cases Latin text and collapses whitespace.
 */
object ArabicNormalizer {

    fun normalize(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val c = ch.code
            when {
                // Tashkeel + Quranic annotation marks + superscript alef
                c in 0x064B..0x065F || c == 0x0670 || c in 0x06D6..0x06ED -> Unit
                c == 0x0640 -> Unit // tatweel
                ch == 'أ' || ch == 'إ' || ch == 'آ' || ch == 'ٱ' -> sb.append('ا')
                ch == 'ى' || ch == 'ئ' -> sb.append('ي')
                ch == 'ؤ' -> sb.append('و')
                ch == 'ة' -> sb.append('ه')
                c in 0x0660..0x0669 -> sb.append('0' + (c - 0x0660))
                c in 0x06F0..0x06F9 -> sb.append('0' + (c - 0x06F0))
                ch.isWhitespace() -> {
                    if (sb.isNotEmpty() && sb[sb.length - 1] != ' ') sb.append(' ')
                }
                else -> sb.append(ch.lowercaseChar())
            }
        }
        return sb.toString().trim()
    }

    /** Splits a normalized string into searchable tokens (letters/digits only). */
    fun tokens(input: String): List<String> =
        normalize(input)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }

    /**
     * Builds a safe FTS4 MATCH expression: every token must appear, matched as a prefix.
     * Returns null when the query has no searchable tokens.
     */
    fun toFtsQuery(rawQuery: String): String? {
        val toks = tokens(rawQuery)
        if (toks.isEmpty()) return null
        return toks.joinToString(" ") { "${it.replace("\"", "")}*" }
    }
}
