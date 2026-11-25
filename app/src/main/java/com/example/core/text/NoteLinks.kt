package com.example.core.text

/** Pure helpers for [[note links]] (NoteLinksTest). */
object NoteLinks {
    private val link = Regex("\\[\\[([^\\[\\]\\n]{1,120})]]")

    /** Titles linked with [[…]] in [text], in order, without duplicates. */
    fun titles(text: String): List<String> = link.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }
        .distinctBy { key(it) }.toList()

    /** Comparison key: Arabic-normalized, case-insensitive. */
    fun key(title: String): String = ArabicNormalizer.normalize(title.trim()).lowercase()

    /** True when [text] links to a note titled [title]. */
    fun linksTo(text: String, title: String): Boolean {
        if (title.isBlank() || !text.contains("[[")) return false
        val k = key(title)
        return titles(text).any { key(it) == k }
    }
}
