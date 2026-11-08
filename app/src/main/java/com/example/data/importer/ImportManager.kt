package com.example.data.importer

import android.content.Context
import android.net.Uri
import com.example.core.security.LegacyCryptoV1
import com.example.core.security.NoteCipher
import com.example.core.text.ArabicNormalizer
import com.example.data.local.SyncState
import com.example.data.repository.Ids
import com.example.data.repository.NoteDraft
import com.example.data.repository.NoteRepository
import com.example.data.repository.VaultLockedException
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Orchestrates imports: reads files (SAF / share intents), dispatches each to a format handler,
 * detects duplicates against existing notes, then commits the user's choices.
 * Only uses documented inputs (files the user picked or shared) — no private databases.
 */
class ImportManager(private val context: Context, private val repository: NoteRepository) {

    private fun newSessionDir(): File = File(context.cacheDir, "import/" + Ids.newId()).apply { mkdirs() }

    fun clearTemp() { File(context.cacheDir, "import").deleteRecursively() }

    fun inputFromUri(uri: Uri): ImportInput {
        val info = repository.attachments.describe(uri)
        return ImportInput(info.name, info.mime, info.lastModified, info.size) {
            context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Cannot open $uri")
        }
    }

    /** Parses [inputs] into a preview. Never writes to the database. */
    suspend fun preview(uid: String, source: ImportSource, inputs: List<ImportInput>): ImportPreview {
        val ctx = ImportContext(newSessionDir())
        val candidates = mutableListOf<ImportCandidate>()
        val issues = mutableListOf<ImportIssue>()
        for (input in inputs) {
            try {
                val out = parseOne(input, ctx)
                candidates += out.candidates
                issues += out.issues
            } catch (e: Exception) {
                issues += ImportIssue(input.name, ImportWarning.UNREADABLE, e.message.orEmpty())
            }
        }
        candidates.forEach { c -> c.warnings.forEach { w -> issues += ImportIssue(c.sourceName, w) } }
        return ImportPreview(source, markDuplicates(uid, candidates.filter { !it.isEmpty }), issues.distinct())
    }

    fun previewSharedText(subject: String?, text: String): ImportCandidate {
        val (title, body) = if (!subject.isNullOrBlank()) subject.trim() to text.trim()
        else TitleHeuristics.split(text, "")
        val (clean, checklist) = ChecklistExtractor.extract(body)
        return ImportCandidate(Ids.newId(), "share", title, clean, checklist)
    }

    private fun handlers(resolver: ((String) -> ImportInput?)? = null): List<ImportHandler> = listOf(
        JsonHandler, MarkdownHandler, HtmlHandler(resolver), DocxHandler, PdfHandler, ImageHandler, PlainTextHandler
    )

    internal fun parseOne(input: ImportInput, ctx: ImportContext): ParseOutput {
        if (input.extension == "zip" || input.mime == "application/zip" || input.mime == "application/x-zip-compressed") return parseZip(input, ctx)
        val h = handlers().firstOrNull { it.canHandle(input) }
            ?: return ParseOutput(emptyList(), listOf(ImportIssue(input.name, ImportWarning.UNSUPPORTED)))
        return h.parse(input, ctx)
    }

    /**
     * ZIP archives (Huawei/Keep exports, folders of notes). Entries are extracted with zip-slip and
     * size guards, then each file is parsed; images referenced from HTML become attachments of that
     * note instead of separate notes.
     */
    internal fun parseZip(input: ImportInput, ctx: ImportContext): ParseOutput {
        val dir = File(ctx.tempDir, "zip_" + Ids.newId()).apply { mkdirs() }
        val files = linkedMapOf<String, File>()
        val times = hashMapOf<String, Long>()
        val issues = mutableListOf<ImportIssue>()
        var total = 0L
        ZipInputStream(input.open()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val name = e.name.replace('\\', '/')
                if (name.startsWith("__MACOSX/") || name.substringAfterLast('/').startsWith(".")) continue
                val target = File(dir, name).canonicalFile
                if (!target.path.startsWith(dir.canonicalPath + File.separator)) continue // zip-slip
                if (files.size >= MAX_ZIP_ENTRIES || total > MAX_ZIP_BYTES) { issues += ImportIssue(input.name, ImportWarning.TRUNCATED); break }
                target.parentFile?.mkdirs()
                target.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val r = zip.read(buf); if (r < 0) break
                        total += r
                        if (total > MAX_ZIP_BYTES) break
                        out.write(buf, 0, r)
                    }
                }
                files[name] = target
                if (e.time > 0) times[name] = e.time
            }
        }
        fun entryInput(name: String, f: File) = ImportInput(name, com.example.data.repository.AttachmentStore.guessMime(name), times[name], f.length()) { f.inputStream() }

        val consumed = mutableSetOf<String>()
        val resolverFor = { base: String ->
            { src: String ->
                val baseDir = base.substringBeforeLast('/', "")
                val candidatesPaths = listOf(if (baseDir.isEmpty()) src else "$baseDir/$src", src, src.substringAfterLast('/'))
                    .map { java.io.File("/" + it).normalize().path.removePrefix("/") }
                val hit = candidatesPaths.firstOrNull { files.containsKey(it) }
                    ?: files.keys.firstOrNull { it.endsWith("/" + src.substringAfterLast('/')) }
                hit?.let { consumed += it; entryInput(it, files.getValue(it)) }
            }
        }
        val out = mutableListOf<ImportCandidate>()
        // Keep Takeout ships both .json and .html for a note: prefer JSON (structured) when both exist.
        val jsonBases = files.keys.filter { it.endsWith(".json") }.map { it.removeSuffix(".json") }.toSet()
        val ordered = files.keys.sortedBy { if (it.endsWith(".html") || it.endsWith(".htm")) 0 else 1 }
        for (name in ordered) {
            if (name in consumed) continue
            val lower = name.lowercase()
            if ((lower.endsWith(".html") || lower.endsWith(".htm")) && name.substringBeforeLast('.') in jsonBases) continue
            if (lower.endsWith(".css") || lower.endsWith(".js") || lower.endsWith("labels.txt")) continue
            val inp = entryInput(name, files.getValue(name))
            try {
                val h = handlers(resolverFor(name)).firstOrNull { it.canHandle(inp) }
                if (h == null) { issues += ImportIssue(name, ImportWarning.UNSUPPORTED); continue }
                if (h is ImageHandler && name in consumed) continue
                val r = h.parse(inp, ctx)
                out += r.candidates; issues += r.issues
            } catch (e: Exception) {
                issues += ImportIssue(name, ImportWarning.UNREADABLE, e.message.orEmpty())
            }
        }
        // Images that a later HTML entry referenced were already parsed as standalone notes: drop them.
        val filtered = out.filterNot { c -> c.attachments.size == 1 && c.body.isBlank() && c.sourceName in consumed }
        return ParseOutput(filtered, issues)
    }

    /** Marks EXACT (same normalized content) and SIMILAR (same title, different content) duplicates. */
    suspend fun markDuplicates(uid: String, candidates: List<ImportCandidate>): List<ImportCandidate> {
        val existing = repository.existingHashes(uid)
        val byHash = existing.filter { it.contentHash.isNotBlank() }.associateBy { it.contentHash }
        val byTitle = existing.groupBy { ArabicNormalizer.normalize(it.title) }
        val seenInBatch = hashSetOf<String>()
        return candidates.map { c ->
            val hash = NoteRepository.contentHash(c.title, c.body, c.checklist)
            val exact = byHash[hash]
            when {
                c.storedLocked != null && existing.any { it.id == c.storedLocked.id } ->
                    c.copy(duplicate = DuplicateKind.EXACT, duplicateOfId = c.storedLocked.id, action = ImportAction.SKIP)
                exact != null -> c.copy(duplicate = DuplicateKind.EXACT, duplicateOfId = exact.id, action = ImportAction.SKIP)
                !seenInBatch.add(hash) -> c.copy(duplicate = DuplicateKind.EXACT, action = ImportAction.SKIP)
                c.title.isNotBlank() && byTitle[ArabicNormalizer.normalize(c.title)] != null ->
                    c.copy(duplicate = DuplicateKind.SIMILAR, duplicateOfId = byTitle.getValue(ArabicNormalizer.normalize(c.title)).first().id, action = ImportAction.KEEP_BOTH)
                else -> c
            }
        }
    }

    /** Writes the chosen candidates. Each note is saved independently so one failure does not abort the rest. */
    suspend fun commit(uid: String, source: ImportSource, candidates: List<ImportCandidate>, defaultFolderId: String? = null): ImportResult {
        var created = 0; var merged = 0; var skipped = 0; var attachments = 0
        val failed = mutableListOf<ImportIssue>()
        val ids = linkedMapOf<String, String>()
        for (c in candidates) {
            try {
                when (c.action) {
                    ImportAction.SKIP -> skipped++
                    ImportAction.MERGE -> {
                        val target = c.duplicateOfId?.let { repository.getNote(it) }
                        val draft = target?.let { repository.toDraft(uid, it) }
                        if (draft == null) { createFrom(uid, source, c, defaultFolderId, ids); created++ } else {
                            repository.snapshot(draft.id, com.example.data.local.RevisionReason.IMPORT_MERGE)
                            val mergedBody = listOf(draft.body.trimEnd(), c.body.trim()).filter { it.isNotBlank() }.joinToString("\n\n")
                            val existingTexts = draft.checklist.map { ArabicNormalizer.normalize(it.text) }.toSet()
                            repository.save(uid, draft.copy(
                                body = mergedBody,
                                checklist = draft.checklist + c.checklist.filter { ArabicNormalizer.normalize(it.text) !in existingTexts },
                                tags = (draft.tags + c.tags).distinct(), updatedAt = null
                            ))
                            attachments += addAttachments(uid, draft.id, c)
                            merged++
                        }
                    }
                    ImportAction.IMPORT, ImportAction.KEEP_BOTH -> {
                        attachments += createFrom(uid, source, c, defaultFolderId, ids); created++
                    }
                }
            } catch (e: Exception) {
                failed += ImportIssue(c.sourceName, ImportWarning.UNREADABLE, e.message.orEmpty())
            }
        }
        // Sub-notes: link each restored note to its restored parent (or an existing note with that id).
        for (c in candidates) {
            val child = ids[c.key] ?: continue
            val parent = c.parentKey?.let { ids[it] ?: it.takeIf { id -> repository.getNote(id) != null } }
            if (parent != null || c.icon.isNotBlank()) runCatching { repository.restoreMeta(uid, child, parent, c.icon) }
        }
        return ImportResult(created, merged, skipped, attachments, failed, ids)
    }

    private suspend fun createFrom(uid: String, source: ImportSource, c: ImportCandidate, defaultFolderId: String?, ids: MutableMap<String, String>): Int {
        val folderId = c.folderName?.let { name -> (repository.findFolderByName(uid, name) ?: repository.createFolder(uid, name)).id } ?: defaultFolderId
        val stored = c.storedLocked
        // Keep the original id (backups) unless it already exists here ("keep both" gets a new id).
        val newId = if (c.action == ImportAction.IMPORT && repository.getNote(c.key) == null && c.key.matches(Regex("[A-Za-z0-9_\\-]{1,128}"))) c.key else Ids.newId()
        ids[c.key] = newId
        if (stored != null) {
            // Locked note from a backup: keep the ciphertext exactly as it was.
            repository.applyStored(uid, stored.copy(id = newId, folderId = folderId, syncState = SyncState.DIRTY, baseRemoteUpdatedAt = 0L), emptyList(), c.tags, c.reminderAt)
            repository.markDirty(uid, newId)
            return addAttachments(uid, newId, c)
        }
        var body = c.body
        var locked = false
        if (LegacyCryptoV1.isLegacyPayload(body)) {
            val plain = LegacyCryptoV1.tryDecrypt(body)
            if (plain == null || !repository.vaultReady(uid)) {
                // Keep the original encrypted payload (still openable on the device that made it).
                val ts = System.currentTimeMillis()
                repository.applyStored(uid, com.example.data.local.NoteEntity(
                    id = newId, userId = uid, title = c.title.ifBlank { "🔒" }, body = body, colorIndex = c.colorIndex, folderId = folderId,
                    isLocked = true, createdAt = c.createdAt ?: ts, updatedAt = c.updatedAt ?: ts, importSource = source.key
                ), emptyList(), c.tags, null)
                repository.markDirty(uid, newId)
                return 0
            }
            body = plain; locked = true
        }
        val draft = NoteDraft(
            id = newId, title = c.title.take(NoteRepository.MAX_TITLE), body = body, colorIndex = c.colorIndex, folderId = folderId,
            isPinned = c.isPinned, isFavorite = c.isFavorite, isArchived = c.isArchived, trashedAt = c.trashedAt,
            isLocked = locked, tags = c.tags, checklist = c.checklist,
            createdAt = c.createdAt, updatedAt = c.updatedAt ?: c.createdAt, importSource = source.key
        )
        try {
            repository.save(uid, draft)
        } catch (e: VaultLockedException) {
            repository.save(uid, draft.copy(isLocked = false))
        }
        val idMap = addAttachmentsMapped(uid, newId, c)
        val n = idMap.size
        // Restore the block layout (attachment references remapped when ids had to change).
        if (c.blocks != null) repository.getNote(newId)?.let { d ->
            repository.toDraft(uid, d)?.let { cur ->
                val remapped = c.blocks.map { b -> if (b.attachmentId != null) b.copy(attachmentId = idMap[b.attachmentId] ?: b.attachmentId) else b }
                repository.save(uid, cur.copy(blocks = com.example.data.repository.Blocks.reconcile(remapped, cur.body, cur.checklist, d.attachments.map { it.id }), updatedAt = null))
            }
        }
        if (c.reminderAt != null && c.reminderAt > System.currentTimeMillis()) repository.setReminder(uid, newId, c.reminderAt)
        // Attachments bump updatedAt; restore the original modification date.
        if (draft.updatedAt != null) repository.restoreTimestamps(newId, draft.createdAt, draft.updatedAt)
        return n
    }

    private suspend fun addAttachments(uid: String, noteId: String, c: ImportCandidate): Int = addAttachmentsMapped(uid, noteId, c).size

    /** Adds the candidate's attachments; returns source id -> stored id. */
    private suspend fun addAttachmentsMapped(uid: String, noteId: String, c: ImportCandidate): Map<String, String> {
        val ids = linkedMapOf<String, String>()
        for (a in c.attachments) {
            val key = a.originalId ?: com.example.data.repository.Ids.newId()
            if (a.encrypted && a.file.exists() && a.file.length() > 0) {
                ids[key] = repository.addRawAttachment(uid, noteId, a)
            } else {
                var att = a
                if (a.kind == com.example.data.local.AttachmentKind.IMAGE && a.file.exists() && a.file.length() > 0) {
                    val p = com.example.core.media.ImageNormalizer.prepare(a.file, a.name, a.mime)
                    att = a.copy(kind = if (p.isImage) a.kind else com.example.data.local.AttachmentKind.FILE, name = p.name, mime = p.mime, file = p.file)
                }
                ids[key] = repository.addAttachment(uid, noteId, att.kind, att.name, att.mime, if (att.file.exists()) att.open() else null,
                    att.extractedText, att.durationMs, att.url, att.linkTitle, att.linkDescription, att.createdAt, preferredId = a.originalId).id
            }
        }
        return ids
    }

    companion object {
        const val MAX_ZIP_ENTRIES = 5000
        const val MAX_ZIP_BYTES = 1024L * 1024 * 1024
        val SUPPORTED_MIME = arrayOf(
            "text/plain", "text/markdown", "text/html", "application/json", "application/zip", "application/x-zip-compressed",
            "application/pdf", "image/*", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/octet-stream"
        )
    }
}

/** True when a body string is any kind of encrypted payload. */
internal fun String.isEncryptedPayload() = NoteCipher.isEncrypted(this)
