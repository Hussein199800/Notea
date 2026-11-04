package com.example.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em

/**
 * Notea stores note bodies as lightweight Markdown (portable, diff-friendly, exportable):
 * `**bold**`, `*italic*`, `++underline++`, `~~strike~~`, `# / ## / ###` headings, `- ` / `1. `
 * lists, `> ` quotes and `[text](url)` links. The editor shows the styling live while keeping
 * the markers visible but faded, so offsets never shift (identity mapping = fast + correct).
 */
object MarkdownEngine {
    private val bold = Regex("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*")
    private val italic = Regex("(?<![*\\w])\\*(?=[^\\s*])(.+?)(?<=[^\\s*])\\*(?![*\\w])")
    private val underline = Regex("\\+\\+(?=\\S)(.+?)(?<=\\S)\\+\\+")
    private val strike = Regex("~~(?=\\S)(.+?)(?<=\\S)~~")
    private val link = Regex("\\[([^\\]\\n]+)]\\((https?://[^)\\s]+)\\)")
    private val bareUrl = Regex("(?<![(\\[])\\bhttps?://[^\\s)]+")
    private val heading = Regex("^(#{1,3}) ")
    private val bullet = Regex("^(\\s*)([-*•]|\\d+[.)]) ")
    private val quote = Regex("^> ")

    data class Palette(val marker: Color, val link: Color, val quote: Color, val accent: Color)

    fun style(text: String, p: Palette, headingSizes: List<TextUnit> = listOf(1.45.em, 1.25.em, 1.1.em)): AnnotatedString {
        val b = AnnotatedString.Builder(text)
        val markerStyle = SpanStyle(color = p.marker)
        var lineStart = 0
        for (line in text.split('\n')) {
            val end = lineStart + line.length
            heading.find(line)?.let { m ->
                val level = m.groupValues[1].length
                b.addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = headingSizes[level - 1]), lineStart, end)
                b.addStyle(markerStyle, lineStart, lineStart + m.value.length)
            }
            bullet.find(line)?.let { m ->
                val s = lineStart + m.groups[2]!!.range.first
                b.addStyle(SpanStyle(color = p.accent, fontWeight = FontWeight.Bold), s, lineStart + m.groups[2]!!.range.last + 1)
            }
            if (quote.containsMatchIn(line)) {
                b.addStyle(markerStyle, lineStart, lineStart + 2)
                b.addStyle(SpanStyle(color = p.quote, fontStyle = FontStyle.Italic), lineStart + 2, end)
            }
            lineStart = end + 1
        }
        fun inline(r: Regex, open: Int, close: Int, st: SpanStyle) {
            for (m in r.findAll(text)) {
                b.addStyle(st, m.range.first, m.range.last + 1)
                b.addStyle(markerStyle, m.range.first, m.range.first + open)
                b.addStyle(markerStyle, m.range.last + 1 - close, m.range.last + 1)
            }
        }
        inline(bold, 2, 2, SpanStyle(fontWeight = FontWeight.Bold))
        inline(italic, 1, 1, SpanStyle(fontStyle = FontStyle.Italic))
        inline(underline, 2, 2, SpanStyle(textDecoration = TextDecoration.Underline))
        inline(strike, 2, 2, SpanStyle(textDecoration = TextDecoration.LineThrough))
        for (m in link.findAll(text)) {
            b.addStyle(markerStyle, m.range.first, m.range.last + 1)
            val g = m.groups[1]!!.range
            b.addStyle(SpanStyle(color = p.link, textDecoration = TextDecoration.Underline), g.first, g.last + 1)
            b.addStringAnnotation("url", m.groupValues[2], m.range.first, m.range.last + 1)
        }
        for (m in bareUrl.findAll(text)) {
            b.addStyle(SpanStyle(color = p.link, textDecoration = TextDecoration.Underline), m.range.first, m.range.last + 1)
            b.addStringAnnotation("url", m.value, m.range.first, m.range.last + 1)
        }
        return b.toAnnotatedString()
    }

    class Transformation(private val palette: Palette) : VisualTransformation {
        private var cacheKey: String? = null
        private var cache: AnnotatedString? = null
        override fun filter(text: AnnotatedString): TransformedText {
            val raw = text.text
            val styled = if (raw == cacheKey && cache != null) cache!! else style(raw, palette).also { cacheKey = raw; cache = it }
            return TransformedText(styled, OffsetMapping.Identity)
        }
    }

    /** URL under the cursor, if any (to offer "open link"). */
    fun urlAt(text: String, offset: Int): String? {
        link.findAll(text).firstOrNull { offset in it.range.first..it.range.last + 1 }?.let { return it.groupValues[2] }
        return bareUrl.findAll(text).firstOrNull { offset in it.range.first..it.range.last + 1 }?.value
    }

    /** Plain preview for cards/widgets/search snippets: markers removed. */
    fun plain(text: String): String = text
        .replace(link) { it.groupValues[1] }
        .replace(bold) { it.groupValues[1] }
        .replace(underline) { it.groupValues[1] }
        .replace(strike) { it.groupValues[1] }
        .replace(italic) { it.groupValues[1] }
        .lines().joinToString("\n") { l -> l.replace(heading, "").replace(quote, "").replace(Regex("^(\\s*)[-*] "), "$1• ") }

    // ------------------------------------------------------------------ editing commands
    fun toggleInline(v: TextFieldValue, marker: String): TextFieldValue {
        val s = v.selection.min
        val e = v.selection.max
        val t = v.text
        // Unwrap if already wrapped
        if (s >= marker.length && e + marker.length <= t.length &&
            t.substring(s - marker.length, s) == marker && t.substring(e, e + marker.length) == marker
        ) {
            val nt = t.removeRange(e, e + marker.length).removeRange(s - marker.length, s)
            return TextFieldValue(nt, TextRange(s - marker.length, e - marker.length))
        }
        return if (s == e) {
            val nt = t.substring(0, s) + marker + marker + t.substring(e)
            TextFieldValue(nt, TextRange(s + marker.length))
        } else {
            val nt = t.substring(0, s) + marker + t.substring(s, e) + marker + t.substring(e)
            TextFieldValue(nt, TextRange(s + marker.length, e + marker.length))
        }
    }

    /** Toggles a prefix (`# `, `- `, `1. `, `> `) on every line touched by the selection. */
    fun toggleLinePrefix(v: TextFieldValue, prefix: String): TextFieldValue {
        val t = v.text
        val start = t.lastIndexOf('\n', (v.selection.min - 1).coerceAtLeast(0)).let { if (v.selection.min == 0) 0 else if (it < 0) 0 else it + 1 }
        val endIdx = t.indexOf('\n', v.selection.max).let { if (it < 0) t.length else it }
        val block = t.substring(start, endIdx)
        val lines = block.split('\n')
        val anyPrefixRegex = Regex("^(#{1,3} |[-*•] |\\d+[.)] |> )")
        val allHave = lines.all { it.startsWith(prefix) || (prefix == "1. " && Regex("^\\d+[.)] ").containsMatchIn(it)) }
        var counter = 0
        val newLines = lines.map { l ->
            if (allHave) l.replaceFirst(anyPrefixRegex, "")
            else {
                counter++
                val stripped = l.replaceFirst(anyPrefixRegex, "")
                (if (prefix == "1. ") "$counter. " else prefix) + stripped
            }
        }
        val newBlock = newLines.joinToString("\n")
        val nt = t.substring(0, start) + newBlock + t.substring(endIdx)
        val delta = newBlock.length - block.length
        val cursor = (v.selection.max + delta).coerceIn(start, start + newBlock.length)
        return TextFieldValue(nt, TextRange(if (v.selection.collapsed) cursor else start, cursor))
    }

    fun insertLink(v: TextFieldValue, url: String, label: String?): TextFieldValue {
        val s = v.selection.min; val e = v.selection.max
        val text = label?.takeIf { it.isNotBlank() } ?: v.text.substring(s, e).ifBlank { url }
        val ins = "[$text]($url)"
        return TextFieldValue(v.text.substring(0, s) + ins + v.text.substring(e), TextRange(s + ins.length))
    }

    /**
     * Smart Enter: continues a list ("- ", "1. ") on the next line, or ends it when the item is empty.
     * Returns null when no special handling applies.
     */
    fun continueList(old: TextFieldValue, new: TextFieldValue): TextFieldValue? {
        if (new.text.length != old.text.length + 1 || !new.selection.collapsed) return null
        val pos = new.selection.start
        if (pos == 0 || new.text[pos - 1] != '\n') return null
        val lineStart = new.text.lastIndexOf('\n', pos - 2).let { if (it < 0) 0 else it + 1 }
        val prevLine = new.text.substring(lineStart, pos - 1)
        val m = bullet.find(prevLine) ?: return null
        val marker = m.value
        if (prevLine.length == marker.length) {
            // Empty item: remove the marker and stop the list.
            val nt = new.text.substring(0, lineStart) + new.text.substring(pos)
            return TextFieldValue(nt, TextRange(lineStart))
        }
        val nextMarker = Regex("(\\d+)([.)])").find(marker)?.let { d ->
            marker.replaceFirst(d.value, "${d.groupValues[1].toInt() + 1}${d.groupValues[2]}")
        } ?: marker
        val nt = new.text.substring(0, pos) + nextMarker + new.text.substring(pos)
        return TextFieldValue(nt, TextRange(pos + nextMarker.length))
    }
}

/** Snapshot-based undo/redo for the whole editor state; snapshots are coalesced while typing. */
class EditHistory<T>(initial: T, private val limit: Int = 200) {
    private val undo = ArrayDeque<T>()
    private val redo = ArrayDeque<T>()
    var current: T = initial
        private set
    private var lastPushAt = 0L

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    /** Records [next]; edits within [coalesceMs] of the previous one are merged into one step. */
    fun record(next: T, coalesceMs: Long = 900L, now: Long = System.currentTimeMillis()) {
        if (next == current) return
        if (now - lastPushAt > coalesceMs || undo.isEmpty()) {
            undo.addLast(current)
            if (undo.size > limit) undo.removeFirst()
        }
        lastPushAt = now
        current = next
        redo.clear()
    }

    fun undo(): T? {
        val prev = undo.removeLastOrNull() ?: return null
        redo.addLast(current); current = prev; lastPushAt = 0
        return prev
    }

    fun redo(): T? {
        val n = redo.removeLastOrNull() ?: return null
        undo.addLast(current); current = n; lastPushAt = 0
        return n
    }

    fun reset(value: T) { undo.clear(); redo.clear(); current = value; lastPushAt = 0 }
}
