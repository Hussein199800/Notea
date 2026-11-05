package com.example.data.importer

import android.util.Xml
import com.example.data.local.AttachmentKind
import com.example.data.repository.ChecklistDraft
import com.example.data.repository.Ids
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/** Shared state for one import session: temp storage for extracted binaries. */
class ImportContext(val tempDir: File) {
    init { tempDir.mkdirs() }

    fun stash(name: String, bytes: ByteArray): File =
        File(tempDir, Ids.newId() + "_" + name.substringAfterLast('/').takeLast(60).replace(Regex("[^\\w.\\-]"), "_"))
            .also { it.writeBytes(bytes) }

    fun stash(name: String, input: ImportInput): File =
        File(tempDir, Ids.newId() + "_" + name.substringAfterLast('/').takeLast(60).replace(Regex("[^\\w.\\-]"), "_"))
            .also { f -> input.open().use { i -> f.outputStream().use { o -> i.copyTo(o) } } }
}

data class ParseOutput(val candidates: List<ImportCandidate>, val issues: List<ImportIssue> = emptyList())

interface ImportHandler {
    fun canHandle(input: ImportInput): Boolean
    fun parse(input: ImportInput, ctx: ImportContext): ParseOutput
}

private const val MAX_TEXT_BYTES = 8L * 1024 * 1024

private fun ImportInput.readBytesLimited(limit: Long = MAX_TEXT_BYTES): ByteArray = open().use { s ->
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        val r = s.read(buf)
        if (r < 0) break
        total += r
        if (total > limit) throw IllegalArgumentException("File too large to parse as text")
        out.write(buf, 0, r)
    }
    out.toByteArray()
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
            val end = text.indexOf("\n---", 4)
            if (end > 0) {
                val fm = text.substring(4, end)
                text = text.substring(end + 4).trimStart('\n')
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
        else { val s = TitleHeuristics.split(text, TitleHeuristics.fileTitle(input.name)); title = s.first; body = s.second }
        val (clean, checklist) = ChecklistExtractor.extract(body)
        return ParseOutput(listOf(ImportCandidate(Ids.newId(), input.name, title, clean, checklist, tags,
            createdAt = created ?: input.lastModified, updatedAt = input.lastModified)))
    }
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
            val target = resolver?.invoke(java.net.URLDecoder.decode(src, "UTF-8"))
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
    override fun canHandle(input: ImportInput) = input.extension == "docx" ||
        input.mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    override fun parse(input: ImportInput, ctx: ImportContext): ParseOutput {
        var documentXml: ByteArray? = null
        var coreXml: ByteArray? = null
        val media = linkedMapOf<String, ByteArray>()
        ZipInputStream(input.open()).use { zip ->
            var total = 0L
            while (true) {
                val e = zip.nextEntry ?: break
                val bytes = zip.readBytes(); total += bytes.size
                if (total > 200L * 1024 * 1024) break
                when {
                    e.name == "word/document.xml" -> documentXml = bytes
                    e.name == "docProps/core.xml" -> coreXml = bytes
                    e.name.startsWith("word/media/") -> media[e.name] = bytes
                }
            }
        }
        val xml = documentXml ?: return ParseOutput(emptyList(), listOf(ImportIssue(input.name, ImportWarning.UNREADABLE)))
        val warnings = mutableSetOf<ImportWarning>()
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
        val atts = media.map { (path, bytes) ->
            val n = path.substringAfterLast('/')
            ImportedAttachment(AttachmentKind.IMAGE, n, com.example.data.repository.AttachmentStore.guessMime(n), ctx.stash(n, bytes))
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
object JsonHandler : ImportHandler {
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
        val out = items.mapNotNull { o -> runCatching { fromObject(o, input) }.getOrElse { issues += ImportIssue(input.name, ImportWarning.UNREADABLE); null } }
            .filter { !it.isEmpty }
        if (items.isEmpty()) issues += ImportIssue(input.name, ImportWarning.UNSUPPORTED)
        return ParseOutput(out, issues)
    }

    private fun usec(o: JSONObject, key: String): Long? = o.optLong(key, 0L).takeIf { it > 0 }?.let { it / 1000 }

    fun fromObject(o: JSONObject, input: ImportInput): ImportCandidate {
        // Google Keep Takeout
        if (o.has("textContent") || o.has("listContent")) {
            val list = o.optJSONArray("listContent")
            val checklist = if (list != null) (0 until list.length()).map {
                val i = list.getJSONObject(it); ChecklistDraft(text = i.optString("text"), checked = i.optBoolean("isChecked"))
            } else emptyList()
            val labels = o.optJSONArray("labels")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("name") } }.orEmpty()
            return ImportCandidate(
                Ids.newId(), input.name, o.optString("title").ifBlank { TitleHeuristics.fileTitle(input.name) },
                o.optString("textContent"), checklist, labels,
                createdAt = usec(o, "createdTimestampUsec"), updatedAt = usec(o, "userEditedTimestampUsec"),
                isPinned = o.optBoolean("isPinned"), isArchived = o.optBoolean("isArchived"),
                trashedAt = if (o.optBoolean("isTrashed")) System.currentTimeMillis() else null
            )
        }
        // Notea v1 export (content may contain the legacy metadata block + category flags)
        if (o.has("content") && o.has("category")) {
            val p = com.example.data.legacy.LegacyNoteParser.parse(o.optString("content"), o.optString("category"), o.optString("lockHint"))
            val locked = o.optBoolean("isLocked")
            val warnings = if (locked && com.example.core.security.NoteCipher.isEncrypted(o.optString("content"))) setOf(ImportWarning.LOCKED_CONTENT) else emptySet()
            return ImportCandidate(
                Ids.newId(), input.name, o.optString("title", "").ifBlank { TitleHeuristics.fileTitle(input.name) },
                if (locked) o.optString("content") else p.body,
                p.checklist.map { ChecklistDraft(text = it.text, checked = it.checked) }, p.tags,
                folderName = com.example.data.legacy.LegacyNoteParser.legacyFolderNames[p.baseCategory]?.first,
                createdAt = o.optLong("createdAtMillis", 0L).takeIf { it > 0 },
                updatedAt = o.optLong("updatedAtMillis", 0L).takeIf { it > 0 },
                isPinned = o.optBoolean("isPinned"), isFavorite = p.isFavorite, isArchived = o.optBoolean("isArchived"),
                colorIndex = o.optInt("colorIndex", 0), warnings = warnings
            )
        }
        // Generic
        val title = o.optString("title").ifBlank { o.optString("name") }
        val body = listOf("content", "text", "body", "note", "description").firstNotNullOfOrNull { k -> o.optString(k).takeIf { it.isNotBlank() } } ?: ""
        val tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        val created = listOf("created", "createdAt", "created_at", "creationDate", "date").firstNotNullOfOrNull { k -> o.opt(k)?.toString()?.let { DateParsing.parse(it) } }
        val updated = listOf("updated", "updatedAt", "updated_at", "modified", "modifiedDate").firstNotNullOfOrNull { k -> o.opt(k)?.toString()?.let { DateParsing.parse(it) } }
        val (t, b) = if (title.isBlank()) TitleHeuristics.split(body, TitleHeuristics.fileTitle(input.name)) else title to body
        val (clean, checklist) = ChecklistExtractor.extract(b)
        return ImportCandidate(Ids.newId(), input.name, t, clean, checklist, tags, createdAt = created, updatedAt = updated)
    }
}
