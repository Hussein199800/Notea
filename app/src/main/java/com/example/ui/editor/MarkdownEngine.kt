package com.example.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
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
    private val boldItalic = Regex("\\*\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*\\*")
    private val bold = Regex("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*(?!\\*)")
    private val italic = Regex("(?<![*\\w])\\*(?=[^\\s*])(.+?)(?<=[^\\s*])\\*(?![*\\w])")
    private val underline = Regex("\\+\\+(?=\\S)(.+?)(?<=\\S)\\+\\+")
    private val strike = Regex("~~(?=\\S)(.+?)(?<=\\S)~~")
    private val link = Regex("\\[([^\\]\\n]+)]\\((https?://[^()\\s]++(?:\\([^()\\s]*+\\)[^()\\s]*+)*+)\\)", RegexOption.IGNORE_CASE)
    private val bareUrl = Regex("\\bhttps?://(?:\\[[0-9a-f:.]+])?[^\\s<>\\[\\]{}\"]*", RegexOption.IGNORE_CASE)
    private val heading = Regex("^(#{1,6}) ")
    private val bullet = Regex("^(\\s*)([-+*•]|\\d+[.)]) ")
    private val quote = Regex("^> ")
    private val fence = Regex("^ {0,3}(`{3,}|~{3,})(.*)$")
    private val code = Regex("(?<!`)(`+)(.+?)(?<!`)\\1(?!`)")
    private val escaped = Regex("""\\([\\\[\]`*+~#>!_.()\-])""")
    private val sentencePunctuation = setOf('.', ',', '!', '?', ';', ':', '،', '؛', '؟', '…')

    private enum class Inline(val pattern: Regex) {
        ESCAPE(escaped), CODE(code), LINK(link), BOLD_ITALIC(boldItalic), BOLD(bold),
        ITALIC(italic), UNDERLINE(underline), STRIKE(strike), URL(bareUrl)
    }

    data class Palette(val marker: Color, val link: Color, val quote: Color, val accent: Color)

    fun style(text: String, p: Palette, headingSizes: List<TextUnit> = listOf(1.45.em, 1.25.em, 1.1.em)): AnnotatedString {
        val b = AnnotatedString.Builder(text)
        val markerStyle = SpanStyle(color = p.marker)
        var lineStart = 0
        for (line in text.split('\n')) {
            val end = lineStart + line.length
            heading.find(line)?.let { m ->
                val level = m.groupValues[1].length
                b.addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = headingSizes.getOrElse(level - 1) { headingSizes.lastOrNull() ?: 1.1.em }), lineStart, end)
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
        inline(boldItalic, 3, 3, SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
        inline(bold, 2, 2, SpanStyle(fontWeight = FontWeight.Bold))
        inline(italic, 1, 1, SpanStyle(fontStyle = FontStyle.Italic))
        inline(underline, 2, 2, SpanStyle(textDecoration = TextDecoration.Underline))
        inline(strike, 2, 2, SpanStyle(textDecoration = TextDecoration.LineThrough))
        val links = link.findAll(text).toList()
        for (m in links) {
            b.addStyle(markerStyle, m.range.first, m.range.last + 1)
            val g = m.groups[1]!!.range
            b.addStyle(SpanStyle(color = p.link, textDecoration = TextDecoration.Underline), g.first, g.last + 1)
            b.addStringAnnotation("url", m.groupValues[2], m.range.first, m.range.last + 1)
        }
        var nextLink = 0
        for (m in bareUrl.findAll(text)) {
            while (nextLink < links.size && links[nextLink].range.last < m.range.first) nextLink++
            if (links.getOrNull(nextLink)?.let { m.range.first in it.range } == true) continue
            val url = trimUrl(m.value)
            if (url.substringAfter("://").isEmpty()) continue
            val end = m.range.first + url.length
            b.addStyle(SpanStyle(color = p.link, textDecoration = TextDecoration.Underline), m.range.first, end)
            b.addStringAnnotation("url", url, m.range.first, end)
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
        return bareUrl.findAll(text).firstNotNullOfOrNull { m ->
            val url = trimUrl(m.value)
            url.takeIf { it.substringAfter("://").isNotEmpty() && offset in m.range.first..m.range.first + it.length }
        }
    }

    /** Plain preview for cards/widgets/search snippets: markers removed. */
    fun plain(text: String): String = render(text, null).text

    /** Reader rendering shares the stored Markdown syntax without exposing the edit markers. */
    fun render(text: String, palette: Palette?, onLinkClick: ((String) -> Unit)? = null): AnnotatedString {
        val out = AnnotatedString.Builder()
        var codeFence: String? = null
        var firstLine = true
        for (line in text.lineSequence()) {
            val currentFence = codeFence
            val fenceMatch = fence.matchEntire(line)
            if (fenceMatch != null) {
                val (marker, info) = fenceMatch.destructured
                if (currentFence == null && (marker.first() != '`' || '`' !in info)) {
                    codeFence = marker
                    continue
                }
                if (currentFence != null && marker.first() == currentFence.first() && marker.length >= currentFence.length && info.isBlank()) {
                    codeFence = null
                    continue
                }
            }
            if (!firstLine) out.append('\n')
            firstLine = false
            val start = out.length
            if (currentFence != null) {
                out.append(line)
                if (palette != null && out.length > start) out.addStyle(SpanStyle(fontFamily = FontFamily.Monospace), start, out.length)
                continue
            }
            val title = heading.find(line)
            val quotation = quote.find(line)
            val listItem = bullet.find(line)
            val contentStart = when {
                title != null -> title.value.length
                quotation != null -> quotation.value.length
                listItem != null -> {
                    val (indent, marker) = listItem.destructured
                    out.append(indent)
                    val markerStart = out.length
                    out.append(if (marker.length == 1) "•" else marker)
                    if (palette != null) out.addStyle(SpanStyle(color = palette.accent, fontWeight = FontWeight.Bold), markerStart, out.length)
                    out.append(' ')
                    listItem.value.length
                }
                else -> 0
            }
            appendInline(out, line.substring(contentStart), palette, onLinkClick)
            if (palette != null && out.length > start) {
                if (title != null) {
                    val fontSize = when (title.groupValues[1].length) {
                        1 -> 1.45.em
                        2 -> 1.25.em
                        else -> 1.1.em
                    }
                    out.addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = fontSize), start, out.length)
                } else if (quotation != null) {
                    out.addStyle(SpanStyle(color = palette.quote, fontStyle = FontStyle.Italic), start, out.length)
                }
            }
        }
        return out.toAnnotatedString()
    }

    private fun appendInline(
        out: AnnotatedString.Builder,
        line: String,
        palette: Palette?,
        onLinkClick: ((String) -> Unit)?,
        allowLinks: Boolean = true
    ) {
        val matches = Inline.entries.asSequence()
            .filter { allowLinks || (it != Inline.LINK && it != Inline.URL) }
            .flatMap { token -> token.pattern.findAll(line).map { token to it } }
            .sortedBy { (_, match) -> match.range.first }
        var cursor = 0
        for ((token, match) in matches) {
            if (match.range.first < cursor) continue
            out.append(line, cursor, match.range.first)
            val start = out.length
            var next = match.range.last + 1
            when (token) {
                Inline.ESCAPE -> out.append(match.groupValues[1])
                Inline.CODE -> {
                    out.append(match.groupValues[2])
                    if (palette != null) out.addStyle(SpanStyle(fontFamily = FontFamily.Monospace), start, out.length)
                }
                Inline.LINK, Inline.URL -> {
                    val url = if (token == Inline.LINK) match.groupValues[2] else trimUrl(match.value)
                    if (token == Inline.LINK) appendInline(out, match.groupValues[1], palette, onLinkClick, allowLinks = false)
                    else {
                        out.append(url)
                        next = match.range.first + url.length
                    }
                    if (palette != null && url.substringAfter("://").isNotEmpty()) {
                        val styles = TextLinkStyles(style = SpanStyle(color = palette.link, textDecoration = TextDecoration.Underline))
                        val listener = onLinkClick?.let { open -> LinkInteractionListener { open(url) } }
                        out.addLink(LinkAnnotation.Url(url, styles, listener), start, out.length)
                    }
                }
                else -> {
                    appendInline(out, match.groupValues[1], palette, onLinkClick, allowLinks)
                    if (palette != null) {
                        val style = when (token) {
                            Inline.BOLD_ITALIC -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                            Inline.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                            Inline.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                            Inline.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                            else -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                        }
                        out.addStyle(style, start, out.length)
                    }
                }
            }
            cursor = next
        }
        out.append(line, cursor, line.length)
    }

    private fun trimUrl(url: String): String {
        var unmatchedClosing = url.count { it == ')' } - url.count { it == '(' }
        var end = url.length
        while (end > 0) {
            val c = url[end - 1]
            if (c == ')' && unmatchedClosing > 0) unmatchedClosing--
            else if (c !in sentencePunctuation) break
            end--
        }
        return url.substring(0, end)
    }

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
        val anyPrefixRegex = Regex("^(#{1,6} |[-+*•] |\\d+[.)] |> )")
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
        if (pos !in 1..new.text.length || new.text[pos - 1] != '\n' || new.text.removeRange(pos - 1, pos) != old.text) return null
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
            val number = d.groupValues[1].toIntOrNull()?.takeIf { it < Int.MAX_VALUE } ?: return null
            marker.replaceFirst(d.value, "${number + 1}${d.groupValues[2]}")
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
