package com.example.data.importer

import com.example.data.local.AttachmentKind
import com.example.data.repository.Ids
import java.util.zip.ZipInputStream

/**
 * Samsung Notes files (.sdocx — what «Save as Samsung Notes file» and Smart Switch produce).
 * The format is a ZIP with a binary note body (UTF-16LE strings) and a media folder.
 * Best effort: the typed text and the images are imported; handwriting/drawings stay as images
 * when Samsung saved a page rendering, and formatting is marked as lost.
 */
object SamsungNotesHandler : ImportHandler {
    override fun canHandle(input: ImportInput) = input.extension in setOf("sdocx", "sdoc")

    private const val MAX_ENTRY = 16L * 1024 * 1024
    private const val MAX_TOTAL = 200L * 1024 * 1024
    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")

    override fun parse(input: ImportInput, ctx: ImportContext): ParseOutput {
        val texts = mutableListOf<String>()
        val atts = mutableListOf<ImportedAttachment>()
        var total = 0L
        var sawNote = false
        input.open().use { raw ->
            ZipInputStream(raw).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    val name = e.name.replace('\\', '/')
                    if (name.contains("..")) continue
                    val bytes = readLimited(zip) ?: continue
                    total += bytes.size
                    if (total > MAX_TOTAL) break
                    val ext = name.substringAfterLast('.', "").lowercase()
                    val lower = name.lowercase()
                    when {
                        ext in imageExt && !lower.contains("thumbnail") && !lower.contains("preview") && bytes.size > 2048 -> {
                            val f = ctx.stash(name, bytes)
                            atts += ImportedAttachment(AttachmentKind.IMAGE, name.substringAfterLast('/'), com.example.data.repository.AttachmentStore.guessMime(name), f)
                        }
                        ext in setOf("note", "page") || lower.endsWith("note.note") -> { sawNote = true; texts += strings(bytes) }
                    }
                }
            }
        }
        if (!sawNote && atts.isEmpty()) return ParseOutput(emptyList(), listOf(ImportIssue(input.name, ImportWarning.UNREADABLE)))
        val body = texts.distinct().joinToString("\n").trim()
        val (title, rest) = TitleHeuristics.split(body, TitleHeuristics.fileTitle(input.name))
        val (clean, checklist) = ChecklistExtractor.extract(rest)
        return ParseOutput(listOf(ImportCandidate(
            Ids.newId(), input.name, title, clean, checklist, createdAt = input.lastModified, updatedAt = input.lastModified,
            attachments = atts,
            warnings = buildSet { add(ImportWarning.FORMATTING_LOST); if (body.isBlank()) add(ImportWarning.TEXT_NOT_EXTRACTED) }
        )))
    }

    private fun readLimited(zip: ZipInputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var n = 0L
        while (true) {
            val r = zip.read(buf); if (r < 0) break
            n += r; if (n > MAX_ENTRY) return null
            out.write(buf, 0, r)
        }
        return out.toByteArray()
    }

    private fun readable(c: Char): Boolean = c in '؀'..'ۿ' || c in 'ﭐ'..'﷿' || c in 'ﹰ'..'﻿' ||
        c in ' '..'~' || c == '\n' || c == '\t' || c in ' '..'ɏ' || c == '‏' || c == '‎' || c in '‐'..'‧' || c == '«' || c == '»'

    /** UTF-16LE text runs that look like human text (not ids, font names or file names). */
    internal fun strings(bytes: ByteArray): List<String> {
        // Strings can start at even or odd offsets: scan both alignments, keep the order of appearance.
        val found = mutableListOf<Pair<Int, String>>()
        for (start in 0..1) {
            val sb = StringBuilder()
            var runStart = start
            var i = start
            fun flush() {
                val t = sb.toString().trim(); sb.setLength(0)
                if (looksLikeText(t)) found += runStart to t
            }
            while (i + 1 < bytes.size) {
                val c = ((bytes[i + 1].toInt() and 0xFF) shl 8 or (bytes[i].toInt() and 0xFF)).toChar()
                if (c != '\u0000' && readable(c)) { if (sb.isEmpty()) runStart = i; sb.append(c) } else flush()
                i += 2
            }
            flush()
        }
        return found.sortedBy { it.first }.map { it.second }.distinct()
    }

    private val noise = Regex("(?i)^(samsung|sec[-_ ]|roboto|noto|droid|font|\\{?[0-9a-f]{8}-|content://|/|[\\w.-]+\\.(png|jpe?g|xml|json|page|note|spi|dat|bin)$)")

    internal fun looksLikeText(t: String): Boolean {
        if (t.length < 2) return false
        val letters = t.count { it.isLetter() }
        if (letters < 2 || letters * 2 < t.length * 0.6) return false
        if (noise.containsMatchIn(t)) return false
        val arabic = t.any { it in '؀'..'ۿ' }
        // Latin-only tokens without spaces that mix digits and letters are ids/hashes.
        if (!arabic && !t.contains(' ') && t.any { it.isDigit() } && t.length > 8) return false
        if (!arabic && !t.contains(' ') && t.length > 24) return false
        return true
    }
}
