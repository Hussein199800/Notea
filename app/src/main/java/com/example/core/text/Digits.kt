package com.example.core.text

/** Numeric input accepts Arabic-Indic (٠-٩) and Persian (۰-۹) digits and the Arabic decimal mark «٫». */
object Digits {
    fun latin(input: String): String {
        if (input.none { it.code in 0x0660..0x0669 || it.code in 0x06F0..0x06F9 || it == '٫' }) return input
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val c = ch.code
            when {
                c in 0x0660..0x0669 -> sb.append('0' + (c - 0x0660))
                c in 0x06F0..0x06F9 -> sb.append('0' + (c - 0x06F0))
                ch == '٫' -> sb.append('.')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** PIN filter: Latin digits only after conversion, at most [max]. Returns null to reject the keystroke. */
    fun pin(input: String, max: Int = 8): String? = latin(input).takeIf { v -> v.length <= max && v.all { it in '0'..'9' } }
}
