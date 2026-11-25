package com.example.data.importer

import android.util.Xml
import com.example.data.local.AttachmentKind
import com.example.data.repository.AttachmentStore
import com.example.data.repository.ChecklistDraft
import com.example.data.repository.Ids
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

/** Shared state for one import session: temp storage for extracted binaries. */
class ImportContext(val tempDir: File) {
    init { tempDir.mkdirs() }

    fun stash(name: String, bytes: ByteArray): File =
        File(tempDir, Ids.newId() + "_" + name.substringAfterLast('/').takeLast(60).replace(Regex("[^\\w.\\-]"), "_"))
            .also { it.writeBytes(bytes) }

    fun stash(name: String, input: ImportInput): File {
        input.stagedFile?.let { return it }
        if (input.sizeBytes > ImportManager.MAX_ZIP_BYTES) throw ImportLimitExceeded()
        return input.open().use { stream -> stash(name, stream, ImportManager.MAX_ZIP_BYTES) }
    }

    internal fun stash(name: String, stream: InputStream, limit: Long): File {
        val file = File(tempDir, Ids.newId() + "_" + name.substringAfterLast('/').takeLast(60).replace(Regex("[^\\w.\\-]"), "_"))
        try {
            file.outputStream().use { stream.copyWithinLimit(it, limit) }
            return file
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }
}

data class ParseOutput(
    val candidates: List<ImportCandidate>,
    val issues: List<ImportIssue> = emptyList(),
    val hasKeepNote: Boolean = false
)

interface ImportHandler {
    fun canHandle(input: ImportInput): Boolean
    fun parse(input: ImportInput, ctx: ImportContext): ParseOutput
}

private const val MAX_TEXT_BYTES = 8L * 1024 * 1024

internal class ImportLimitExceeded : java.io.IOException("Import size limit reached")

/** Counts skipped entries too. A null destination consumes data without keeping it in memory. */
internal fun InputStream.copyWithinLimit(out: OutputStream?, limit: Long): Long {
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size.toLong(), limit - total + 1).toInt())
        if (count < 0) return total
        total += count
        if (total > limit) throw ImportLimitExceeded()
        out?.write(buffer, 0, count)
    }
}

private fun InputStream.readBytesLimited(limit: Long): ByteArray {
    val out = ByteArrayOutputStream()
    copyWithinLimit(out, limit)
    return out.toByteArray()
}

private fun ImportInput.readBytesLimited(limit: Long = MAX_TEXT_BYTES): ByteArray {
    if (sizeBytes > limit) throw ImportLimitExceeded()
    return open().use { it.readBytesLimited(limit) }
}

object PlainTextHandler : ImportHandler {
    override fun canHandle(input: ImportInput) = input.extension == "txt" || (input.mime == "text/plain" && input.extension !in setOf("md", "markdown"))
    override fun parse(input: ImportInput, ctx: ImportContext): ParseOutput {
        val text = TextDecoder.normalizeNewlines(TextDecoder.decode(input.readBytesLimited()))
        val (title, body) = TitleHeuristics.split(text, TitleHeuristics.fileTitle(input.name))
        val (clean, checklist) = ChecklistExtractor.extract(body)
        return ParseOutput(listOf(ImportCandidate(Ids.newId(), input.name, title, clean, checklist, createdAt = input.lastModified, updatedAt = input.lastModified)))
    }
}

object MarkdownHandler : ImportHandler {
    override fun canHandle(input: ImportInput) = input.extension in setOf("md", "markdown") || input.mime == "text/markdown"
    override fun parse(input: ImportInput, ctx: ImportContext): ParseOutput {
        var text = TextDecoder.normalizeNewlines(TextDecoder.decode(input.readBytesLimited()))
        var tags = emptyList<String>()
        var created: Long? = null
        // YAML front matter (title / tags / created) used by many exporters.
        var fmTitle: String? = null
        if (text.startsWith("---\n")) {
            val end = FRONT_MATTER_END.find(text, 4)
            if (end != null) {
                val fm = text.substring(4, end.range.first)
                text = text.substring(end.range.last + 1).trimStart('\n')
                fm.lines().forEach { l ->
                    val k = l.substringBefore(':').trim().lowercase(); val v = l.substringAfter(':', "").trim()
                    when (k) {
                        "title" -> fmTitle = v.trim('"', '\'')
                        "tags" -> tags = v.trim('[', ']').split(',').map { it.trim().trim('"', '\'') }.filter { it.isNotBlank() }
                        "created", "date" -> created = DateParsing.parse(v.trim('"', '\''))
                    }
                }
            }
        }
        val title: String
        val body: String
        val first = text.substringBefore('\n')
        if (fmTitle != null) { title = fmTitle!!; body = text }
        else if (first.startsWith("# ")) { title = first.removePrefix("# ").trim(); body = text.substringAfter('\n', "").trimStart('\n') }
        else { title = TitleHeuristics.fileTitle(input.name); body = text }
        val (clean, checklist) = ChecklistExtractor.extract(body)
        return ParseOutput(listOf(ImportCandidate(Ids.newId(), input.name, title, clean, checklist, tags,
            createdAt = created ?: input.lastModified, updatedAt = input.lastModified)))
    }

    private val FRONT_MATTER_END = Regex("(?m)^---[ \\t]*$")
}

/** HTML (Huawei Notepad, Keep/Takeout, generic). [resolver] finds images inside an archive. */
class HtmlHandler(private val resolver: ((String) -> ImportInput?)? = null) : ImportHandler {
    override fun canHandle(input: ImportInput) = input.extension in setOf("html", "htm", "xhtml") || input.mime == "text/html"
    override fun parse(input: ImportInput, ctx: ImportContext): ParseOutput {
        val html = TextDecoder.decode(input.readBytesLimited())
        val r = HtmlConverter.convert(html)
        val warnings = mutableSetOf<ImportWarning>()
        if (r.lostFormatting) warnings += ImportWarning.FORMATTING_LOST
        val atts = mutableListOf<ImportedAttachment>()
        for (src in r.imageSources) {
            if (src.startsWith("data:image")) {
                val b64 = src.substringAfter("base64,", "")
                val mime = src.substringAfter("data:").substringBefore(';')
                runCatching { android.util.Base64.decode(b64, android.util.Base64.DEFAULT) }.getOrNull()?.let {
                    atts += ImportedAttachment(AttachmentKind.IMAGE, "image." + mime.substringAfter('/'), mime, ctx.stash("image", it))
                } ?: run { warnings += ImportWarning.MISSING_RESOURCE }
                continue
            }
            val target = resolver?.invoke(src)
            if (target != null) {
                atts += ImportedAttachment(AttachmentKind.IMAGE, target.name.substringAfterLast('/'), target.mime, ctx.stash(target.name, target))
            } else if (!src.startsWith("http")) {
                warnings += ImportWarning.MISSING_RESOURCE
            }
        }
        var title = r.title
        var body = r.markdown
        if (title == null) {
            val s = TitleHeuristics.split(body, TitleHeuristics.fileTitle(input.name)); title = s.first; body = s.second
        }
        return ParseOutput(listOf(ImportCandidate(Ids.newId(), input.name, title, body, r.checklist,
            createdAt = r.createdAt ?: input.lastModified, updatedAt = input.lastModified, attachments = atts, warnings = warnings)))
    }
}

/** DOCX (Samsung Notes / Word export): paragraphs, runs, headings, lists, images, core dates. */
object DocxHandler : ImportHandler {
    private const val MAX_DOCX_BYTES = 200L * 1024 * 1024

    override fun canHandle(input: ImportInput) = input.extension == "docx" ||
        input.mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    override fun parse(input: ImportInput, ctx: ImportContext): ParseOutput {
        var documentXml: ByteArray? = null
        var coreXml: ByteArray? = null
        val media = linkedMapOf<String, File>()
        var truncated = false
        ZipInputStream(input.open()).use { zip ->
            var remaining = MAX_DOCX_BYTES
            var entries = 0
            while (true) {
                val e = zip.nextEntry ?: break
                if (++entries > ImportManager.MAX_ZIP_ENTRIES || e.size > remaining) {
                    truncated = true
                    break
                }
                try {
                    remaining -= when {
                        e.name == "word/document.xml" -> zip.readBytesLimited(minOf(MAX_TEXT_BYTES, remaining)).also { documentXml = it }.size.toLong()
                        e.name == "docProps/core.xml" -> zip.readBytesLimited(minOf(MAX_TEXT_BYTES, remaining)).also { coreXml = it }.size.toLong()
                        !e.isDirectory && e.name.startsWith("word/media/") && e.name !in media ->
                            ctx.stash(e.name, zip, remaining).also { media[e.name] = it }.length()
                        else -> zip.copyWithinLimit(null, remaining)
                    }
                } catch (_: ImportLimitExceeded) {
                    truncated = true
                    break
                }
            }
        }
        val xml = documentXml ?: return ParseOutput(emptyList(), listOf(
            ImportIssue(input.name, if (truncated) ImportWarning.TRUNCATED else ImportWarning.UNREADABLE)
        ))
        val warnings = mutableSetOf<ImportWarning>()
        if (truncated) warnings += ImportWarning.TRUNCATED
        val md = StringBuilder()
        val checklist = mutableListOf<ChecklistDraft>()
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(ByteArrayInputStream(xml), "UTF-8")
        val para = StringBuilder()
        var prefix = ""
        var bold = false; var italic = false; var underline = false
        var inRunProps = false
        var runText = StringBuilder()
        fun flushRun() {
            if (runText.isEmpty()) return
            var t = runText.toString()
            if (t.isNotBlank()) {
                if (underline) t = "++$t++"
                if (italic) t = "*$t*"
                if (bold) t = "**$t**"
            }
            para.append(t); runText = StringBuilder()
        }
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val name = p.name
            when (event) {
                XmlPullParser.START_TAG -> when (name) {
                    "w:p" -> { para.setLength(0); prefix = "" }
                    "w:pStyle" -> {
                        val v = p.getAttributeValue(null, "w:val")?.lowercase().orEmpty()
                        prefix = when {
                            v == "title" || v.endsWith("heading1") || v == "heading1" || v == "1" -> "# "
                            v.contains("heading2") || v == "2" -> "## "
                            v.contains("heading") -> "### "
                            v.contains("list") -> "- "
                            else -> prefix
                        }
                    }
                    "w:numPr" -> if (prefix.isEmpty()) prefix = "- "
                    "w:r" -> { bold = false; italic = false; underline = false }
                    "w:rPr" -> inRunProps = true
                    "w:b" -> if (inRunProps) bold = p.getAttributeValue(null, "w:val") !in setOf("0", "false")
                    "w:i" -> if (inRunProps) italic = p.getAttributeValue(null, "w:val") !in setOf("0", "false")
                    "w:u" -> if (inRunProps) underline = p.getAttributeValue(null, "w:val") != "none"
                    "w:t" -> runText.append(p.nextText())
                    "w:tab" -> runText.append('\t')
                    "w:br", "w:cr" -> runText.append('\n')
                    "w:tbl", "w:drawing", "w:pict", "mc:AlternateContent", "w:object" -> if (name != "w:drawing") warnings += ImportWarning.FORMATTING_LOST
                    "w14:checkbox" -> prefix = "- [ ] "
                    "w14:checked" -> if (p.getAttributeValue(null, "w14:val") == "1") prefix = "- [x] "
                }
                XmlPullParser.END_TAG -> when (name) {
                    "w:rPr" -> inRunProps = false
                    "w:r" -> flushRun()
                    "w:p" -> {
                        flushRun()
                        val line = para.toString()
                        if (line.isNotBlank()) md.append(prefix).append(line.trim()).append('\n') else md.append('\n')
                    }
                }
            }
            event = p.next()
        }
        val atts = media.map { (path, file) ->
            val n = path.substringAfterLast('/')
            val mime = AttachmentStore.guessMime(n)
            ImportedAttachment(if (mime.startsWith("image/")) AttachmentKind.IMAGE else AttachmentKind.FILE, n, mime, file)
        }
        var created: Long? = null
        var modified: Long? = null
        var docTitle: String? = null
        coreXml?.let { core ->
            val s = String(core, Charsets.UTF_8)
            created = Regex("<dcterms:created[^>]*>([^<]+)<").find(s)?.groupValues?.get(1)?.let { DateParsing.parse(it) }
            modified = Regex("<dcterms:modified[^>]*>([^<]+)<").find(s)?.groupValues?.get(1)?.let { DateParsing.parse(it) }
            docTitle = Regex("<dc:title>([^<]+)<").find(s)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        }
        val text = md.toString().replace(Regex("\n{3,}"), "\n\n").trim()
        val (clean, checks) = ChecklistExtractor.extract(text)
        checklist += checks
        val (title, body) = if (docTitle != null) docTitle!! to clean
        else if (clean.startsWith("# ")) clean.substringBefore('\n').removePrefix("# ").trim() to clean.substringAfter('\n', "").trim()
        else TitleHeuristics.split(clean, TitleHeuristics.fileTitle(input.name))
        return ParseOutput(listOf(ImportCandidate(Ids.newId(), input.name, title, body, checklist,
            createdAt = created ?: input.lastModified, updatedAt = modified ?: input.lastModified, attachments = atts, warnings = warnings)))
    }
}

/** PDFs (Samsung/Huawei exports) are kept as the original file; OCR extracts printed text afterwards. */
object PdfHandler : ImportHandler {
    override fun canHandle(input: ImportInput) = input.extension == "pdf" || input.mime == "application/pdf"
    override fun parse(input: ImportInput, ctx: ImportContext): ParseOutput {
        val f = ctx.stash(input.name, input)
        val title = TitleHeuristics.fileTitle(input.name)
        return ParseOutput(listOf(ImportCandidate(Ids.newId(), input.name, title, "",
            createdAt = input.lastModified, updatedAt = input.lastModified,
            // Text is read later by on-device OCR (printed text; handwriting stays as the original PDF).
            attachments = listOf(ImportedAttachment(AttachmentKind.PDF, input.name, "application/pdf", f)))))
    }
}

object ImageHandler : ImportHandler {
    private val exts = setOf("png", "jpg", "jpeg", "webp", "gif", "heic", "heif", "avif", "bmp", "tif", "tiff", "svg")
    override fun canHandle(input: ImportInput) = input.extension in exts || input.mime.startsWith("image/")
    override fun parse(input: ImportInput, ctx: ImportContext): ParseOutput {
        val f = ctx.stash(input.name, input)
        val mime = input.mime.takeIf { it.startsWith("image/") } ?: com.example.data.repository.AttachmentStore.guessMime(input.name)
        val p = com.example.core.media.ImageNormalizer.prepare(f, input.name, mime)
        val att = ImportedAttachment(if (p.isImage) AttachmentKind.IMAGE else AttachmentKind.FILE, p.name, p.mime, p.file)
        return ParseOutput(listOf(ImportCandidate(Ids.newId(), input.name, TitleHeuristics.fileTitle(input.name), "",
            createdAt = input.lastModified, updatedAt = input.lastModified,
            attachments = listOf(att),
            warnings = if (p.unsupported) setOf(ImportWarning.IMAGE_KEPT_AS_FILE) else emptySet())))
    }
}

/**
 * JSON: Notea v1 export (array), Google Keep Takeout notes, and generic `{title, content|text|body}`
 * arrays or `{notes: [...]}` objects (common in third-party backup tools).
 */
class JsonHandler(private val resolver: ((String) -> ImportInput?)? = null) : ImportHandler {
    override fun canHandle(input: ImportInput) = input.extension == "json" || input.mime == "application/json"

    override fun parse(input: ImportInput, ctx: ImportContext): ParseOutput {
        val text = TextDecoder.decode(input.readBytesLimited(64L * 1024 * 1024)).trim()
        val items: List<JSONObject> = when {
            text.startsWith("[") -> JSONArray(text).let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }
            text.startsWith("{") -> {
                val o = JSONObject(text)
                val arr = o.optJSONArray("notes") ?: o.optJSONArray("items") ?: o.optJSONArray("data")
                if (arr != null) (0 until arr.length()).mapNotNull { arr.optJSONObject(it) } else listOf(o)
            }
            else -> emptyList()
        }
        val issues = mutableListOf<ImportIssue>()
        val out = mutableListOf<ImportCandidate>()
        var hasKeepNote = false
        for (o in items) {
            try {
                val keep = isKeepNote(o)
                val candidate = if (keep) keepNote(o, input, ctx) else fromObject(o, input)
                if (!candidate.isEmpty) {
                    out += candidate
                    hasKeepNote = hasKeepNote || keep
                }
            } catch (e: Exception) {
                issues += e.toImportIssue(input.name)
            }
        }
        if (out.isEmpty() && issues.isEmpty()) issues += ImportIssue(input.name, ImportWarning.UNSUPPORTED)
        return ParseOutput(out, issues, hasKeepNote)
    }

    private fun usec(o: JSONObject, key: String): Long? = o.optLong(key, 0L).takeIf { it > 0 }?.let { it / 1000 }

    private fun isKeepNote(o: JSONObject): Boolean = o.has("textContent") || o.has("listContent") ||
        o.has("userEditedTimestampUsec") && (o.has("isArchived") || o.has("attachments"))

    private fun keepNote(o: JSONObject, input: ImportInput, ctx: ImportContext): ImportCandidate {
        val checklist = o.optJSONArray("listContent")?.let { list ->
            (0 until list.length()).mapNotNull { index ->
                list.optJSONObject(index)?.let { item ->
                    ChecklistDraft(text = item.text("text"), checked = item.optBoolean("isChecked"))
                }
            }
        }.orEmpty()
        val labels = o.optJSONArray("labels")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.text("name")?.takeIf(String::isNotBlank) }
        }.orEmpty()
        val warnings = mutableSetOf<ImportWarning>()
        val attachments = o.optJSONArray("attachments")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val asset = a.optJSONObject(i)
                val path = asset?.text("filePath").orEmpty()
                val resource = path.takeIf(String::isNotBlank)?.let { resolver?.invoke(it) }
                if (resource == null) {
                    warnings += ImportWarning.MISSING_RESOURCE
                    return@mapNotNull null
                }
                val mime = asset?.text("mimetype").orEmpty().trim().lowercase().ifBlank { resource.mime }
                val kind = when {
                    mime.startsWith("image/") -> AttachmentKind.IMAGE
                    mime.startsWith("audio/") -> AttachmentKind.AUDIO
                    mime == "application/pdf" -> AttachmentKind.PDF
                    else -> AttachmentKind.FILE
                }
                try {
                    ImportedAttachment(kind, resource.name.substringAfterLast('/'), mime, ctx.stash(resource.name, resource))
                } catch (e: Exception) {
                    warnings += e.toImportIssue(resource.name).warning
                    null
                }
            }
        }.orEmpty()
        return ImportCandidate(
            Ids.newId(), input.name, o.text("title").ifBlank { TitleHeuristics.fileTitle(input.name) },
            TextDecoder.normalizeNewlines(o.text("textContent")), checklist, labels,
            createdAt = usec(o, "createdTimestampUsec"), updatedAt = usec(o, "userEditedTimestampUsec"),
            isPinned = o.optBoolean("isPinned"), isArchived = o.optBoolean("isArchived"),
            trashedAt = if (o.optBoolean("isTrashed")) System.currentTimeMillis() else null,
            attachments = attachments, warnings = warnings
        )
    }

    private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key)

    private fun fromObject(o: JSONObject, input: ImportInput): ImportCandidate {
        // Notea v1 export (content may contain the legacy metadata block + category flags)
        if (o.has("content") && o.has("category")) {
            val p = com.example.data.legacy.LegacyNoteParser.parse(o.optString("content"), o.optString("category"), o.optString("lockHint"))
            val locked = o.optBoolean("isLocked")
            val warnings = if (locked && com.example.core.security.NoteCipher.isEncrypted(o.optString("content"))) setOf(ImportWarning.LOCKED_CONTENT) else emptySet()
            return ImportCandidate(
                Ids.newId(), input.name, o.optString("title", "").ifBlank { TitleHeuristics.fileTitle(input.name) },
                if (locked && com.example.core.security.NoteCipher.isEncrypted(o.optString("content"))) o.optString("content") else p.body,
                p.checklist.map { ChecklistDraft(text = it.text, checked = it.checked) }, p.tags,
                folderName = com.example.data.legacy.LegacyNoteParser.legacyFolderNames[p.baseCategory]?.first,
                createdAt = o.optLong("createdAtMillis", 0L).takeIf { it > 0 },
                updatedAt = o.optLong("updatedAtMillis", 0L).takeIf { it > 0 },
                isPinned = o.optBoolean("isPinned"), isFavorite = p.isFavorite, isArchived = o.optBoolean("isArchived"),
                colorIndex = o.optInt("colorIndex", 0), warnings = warnings, requiresEncryption = locked,
                lockPinHash = o.text("notePinHash"), lockHint = p.lockHint
            )
        }
        // Generic
        val title = o.text("title").ifBlank { o.text("name") }
        val body = listOf("content", "text", "body", "note", "description").firstNotNullOfOrNull { k -> o.text(k).takeIf { it.isNotBlank() } }.orEmpty()
        val tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        val created = listOf("created", "createdAt", "created_at", "creationDate", "date").firstNotNullOfOrNull { k -> o.opt(k)?.toString()?.let { DateParsing.parse(it) } }
        val updated = listOf("updated", "updatedAt", "updated_at", "modified", "modifiedDate").firstNotNullOfOrNull { k -> o.opt(k)?.toString()?.let { DateParsing.parse(it) } }
        val (t, b) = if (title.isBlank() && body.isNotBlank()) TitleHeuristics.split(body, TitleHeuristics.fileTitle(input.name)) else title to body
        val (clean, checklist) = ChecklistExtractor.extract(b)
        return ImportCandidate(Ids.newId(), input.name, t, clean, checklist, tags, createdAt = created, updatedAt = updated)
    }
}
