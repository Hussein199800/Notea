package com.example.data.importer

import com.example.data.repository.ChecklistDraft
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Charset detection for exported text: BOM, strict UTF-8, then Windows-1256 (Arabic legacy). */
object TextDecoder {
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            val cp1256 = runCatching { Charset.forName("windows-1256") }.getOrNull()
            if (cp1256 != null) String(bytes, cp1256) else String(bytes, Charsets.ISO_8859_1)
        }
    }

    fun normalizeNewlines(s: String) = s.replace("\r\n", "\n").replace('\r', '\n')
}

/** Splits text into (title, body) the way people expect: a short first line becomes the title. */
object TitleHeuristics {
    fun split(text: String, fallbackTitle: String): Pair<String, String> {
        val t = TextDecoder.normalizeNewlines(text).trim('\n', ' ', '\uFEFF')
        if (t.isEmpty()) return fallbackTitle to ""
        val first = t.substringBefore('\n').trim()
        val rest = t.substringAfter('\n', "").trimStart('\n')
        return if (first.length in 1..80 && rest.isNotBlank()) first.removePrefix("#").trim() to rest
        else fallbackTitle to t
    }

    fun fileTitle(name: String) = name.substringAfterLast('/').substringBeforeLast('.').replace('_', ' ').trim().ifBlank { name }
}

/** Pulls `- [ ]` / `- [x]` / `☐` / `☑` lines out of a body into checklist items. */
object ChecklistExtractor {
    private val pattern = Regex("^\\s*(?:[-*+]\\s*\\[( |x|X)]|(☐|☑|✓|✔|□|■))\\s+(.+)$")

    fun extract(body: String): Pair<String, List<ChecklistDraft>> {
        val items = mutableListOf<ChecklistDraft>()
        val kept = StringBuilder()
        for (line in body.lines()) {
            val m = pattern.find(line)
            if (m != null) {
                val checked = m.groupValues[1].equals("x", true) || m.groupValues[2] in setOf("☑", "✓", "✔", "■")
                items += ChecklistDraft(text = m.groupValues[3].trim(), checked = checked)
            } else {
                kept.append(line).append('\n')
            }
        }
        return kept.toString().trimEnd() to items
    }
}

/**
 * Small tolerant HTML -> Notea markdown converter (no external parser). Handles the subset produced
 * by note apps: headings, bold/italic/underline, lists, links, line breaks, checkboxes, images.
 */
object HtmlConverter {
    data class Result(
        val title: String?,
        val markdown: String,
        val checklist: List<ChecklistDraft>,
        val imageSources: List<String>,
        val lostFormatting: Boolean,
        val createdAt: Long?
    )

    private val tagRegex = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>", RegexOption.DOT_MATCHES_ALL)

    fun convert(html: String): Result {
        val cleaned = html
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<(script|style|head)[^>]*>.*?</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), " ")
        val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(html)?.groupValues?.get(1)?.let { decodeEntities(it).trim() }?.takeIf { it.isNotBlank() }
        val created = Regex("<meta[^>]+name=\"(?:created|creation[-_]?date|date)\"[^>]+content=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let { DateParsing.parse(it) }

        val out = StringBuilder()
        val images = mutableListOf<String>()
        val checklist = mutableListOf<ChecklistDraft>()
        var lost = false
        var linkHref: String? = null
        var linkStart = -1
        val listStack = ArrayDeque<String>()
        val olCounters = ArrayDeque<Int>()
        var pendingCheckbox: Boolean? = null
        var checkboxLineStart = -1

        fun newline() { if (out.isNotEmpty() && out.last() != '\n') out.append('\n') }
        fun blankLine() { newline(); if (out.length >= 2 && !out.endsWith("\n\n")) out.append('\n') }

        var idx = 0
        for (m in tagRegex.findAll(cleaned)) {
            appendText(out, cleaned.substring(idx, m.range.first))
            idx = m.range.last + 1
            val closing = m.groupValues[1] == "/"
            val tag = m.groupValues[2].lowercase()
            val attrs = m.groupValues[3]
            when (tag) {
                "br" -> {
                    finishCheckbox(out, checkboxLineStart, pendingCheckbox, checklist)?.let { out.setLength(it) }
                    pendingCheckbox = null; out.append('\n')
                }
                "p", "div", "section", "article", "tr" -> {
                    finishCheckbox(out, checkboxLineStart, pendingCheckbox, checklist)?.let { out.setLength(it) }
                    pendingCheckbox = null
                    if (closing) newline() else newline()
                }
                "h1", "h2", "h3", "h4", "h5", "h6" -> if (closing) blankLine() else {
                    blankLine(); out.append("#".repeat(minOf(3, tag[1].digitToInt()))).append(' ')
                }
                "b", "strong" -> out.append("**")
                "i", "em" -> out.append("*")
                "u", "ins" -> out.append("++")
                "s", "strike", "del" -> out.append("~~")
                "ul" -> if (closing) { listStack.removeLastOrNull(); newline() } else { listStack.addLast("ul"); newline() }
                "ol" -> if (closing) { listStack.removeLastOrNull(); olCounters.removeLastOrNull(); newline() } else {
                    listStack.addLast("ol"); olCounters.addLast(0); newline()
                }
                "li" -> if (!closing) {
                    finishCheckbox(out, checkboxLineStart, pendingCheckbox, checklist)?.let { out.setLength(it) }
                    pendingCheckbox = null
                    newline()
                    val indent = "  ".repeat((listStack.size - 1).coerceAtLeast(0))
                    if (listStack.lastOrNull() == "ol") {
                        val n = (olCounters.removeLastOrNull() ?: 0) + 1
                        olCounters.addLast(n); out.append(indent).append("$n. ")
                    } else out.append(indent).append("- ")
                } else {
                    finishCheckbox(out, checkboxLineStart, pendingCheckbox, checklist)?.let { out.setLength(it) }
                    pendingCheckbox = null
                }
                "a" -> if (!closing) {
                    linkHref = attr(attrs, "href"); linkStart = out.length
                } else {
                    val href = linkHref
                    if (href != null && linkStart in 0..out.length && !href.startsWith("#")) {
                        val text = out.substring(linkStart)
                        out.setLength(linkStart)
                        out.append(if (text.isBlank() || text == href) href else "[$text]($href)")
                    }
                    linkHref = null
                }
                "img" -> attr(attrs, "src")?.let { src ->
                    images += src
                }
                "input" -> if (attr(attrs, "type")?.lowercase() == "checkbox") {
                    // Checkbox line: the rest of the line becomes a checklist item.
                    newline()
                    checkboxLineStart = out.length
                    pendingCheckbox = Regex("\\bchecked\\b", RegexOption.IGNORE_CASE).containsMatchIn(attrs)
                }
                "table", "font", "span", "svg", "canvas" -> if (tag == "table" || tag == "svg" || tag == "canvas" || attrs.contains("style", true)) lost = true
                "blockquote" -> if (!closing) { newline(); out.append("> ") } else newline()
                "hr" -> { blankLine(); out.append("---\n") }
            }
        }
        appendText(out, cleaned.substring(idx))
        finishCheckbox(out, checkboxLineStart, pendingCheckbox, checklist)?.let { out.setLength(it) }

        var md = out.toString()
            .replace(Regex("[ \\t]+\n"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .replace("****", "").replace("**  **", " ")
            .trim()
        val (stripped, extra) = ChecklistExtractor.extract(md)
        md = stripped
        return Result(title, md, checklist + extra, images, lost, created)
    }

    private fun finishCheckbox(out: StringBuilder, start: Int, checked: Boolean?, list: MutableList<ChecklistDraft>): Int? {
        if (checked == null || start < 0 || start > out.length) return null
        val text = out.substring(start).trim()
        if (text.isNotEmpty()) list += ChecklistDraft(text = text, checked = checked)
        return start
    }

    private fun appendText(out: StringBuilder, raw: String) {
        if (raw.isEmpty()) return
        val text = decodeEntities(raw.replace(Regex("\\s+"), " "))
        if (text.isBlank()) { if (out.isNotEmpty() && out.last() != ' ' && out.last() != '\n') out.append(' '); return }
        if (out.isNotEmpty() && out.last() == '\n') out.append(text.trimStart()) else out.append(text)
    }

    private fun attr(attrs: String, name: String): String? =
        Regex("\\b$name\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", RegexOption.IGNORE_CASE).find(attrs)?.let {
            decodeEntities(it.groupValues[2].ifEmpty { it.groupValues[3] }.ifEmpty { it.groupValues[4] })
        }

    fun decodeEntities(s: String): String {
        if (!s.contains('&')) return s
        return Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);").replace(s) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                else -> when (e.lowercase()) {
                    "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "
                    "rlm", "lrm" -> ""; "hellip" -> "…"; "mdash" -> "—"; "ndash" -> "–"
                    else -> m.value
                }
            }
        }
    }
}

object DateParsing {
    private val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm", "yyyy-MM-dd", "yyyy/MM/dd"
    )

    fun parse(s: String): Long? {
        val t = s.trim()
        t.toLongOrNull()?.let { n -> return when { n > 1e15 -> n / 1000; n > 1e11 -> n; n > 1e8 -> n * 1000; else -> null } }
        for (p in patterns) {
            try {
                val f = java.text.SimpleDateFormat(p, java.util.Locale.US)
                if (p.endsWith("'Z'")) f.timeZone = java.util.TimeZone.getTimeZone("UTC")
                f.isLenient = false
                return f.parse(t)?.time
            } catch (_: Exception) {
            }
        }
        return null
    }
}
