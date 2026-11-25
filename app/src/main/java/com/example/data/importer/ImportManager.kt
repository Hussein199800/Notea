package com.example.data.importer

import android.content.Context
import android.net.Uri
import com.example.core.security.LegacyCryptoV1
import com.example.core.security.NoteCipher
import com.example.core.text.ArabicNormalizer
import com.example.data.local.AttachmentKind
import com.example.data.local.BlockType
import com.example.data.local.RevisionReason
import com.example.data.local.SyncState
import com.example.data.repository.AttachmentStore
import com.example.data.repository.BlockDraft
import com.example.data.repository.Blocks
import com.example.data.repository.Ids
import com.example.data.repository.NoteDraft
import com.example.data.repository.NoteRepository
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
        val parsed = parseFiles(inputs, ctx, allowZip = true)
        val candidates = parsed.candidates.filter { !it.isEmpty }
        val issues = parsed.issues + candidates.flatMap { c -> c.warnings.map { ImportIssue(c.sourceName, it) } }
        return ImportPreview(source, markDuplicates(uid, candidates), issues.distinct())
    }

    fun previewSharedText(subject: String?, text: String): ImportCandidate {
        val (title, body) = if (!subject.isNullOrBlank()) subject.trim() to text.trim()
        else TitleHeuristics.split(text, "")
        val (clean, checklist) = ChecklistExtractor.extract(body)
        return ImportCandidate(Ids.newId(), "share", title, clean, checklist)
    }

    private fun parseFiles(inputs: List<ImportInput>, ctx: ImportContext, allowZip: Boolean): ParseOutput {
        val byPath = inputs.groupBy { archivePath(it.name) }
        val consumed = mutableSetOf<ImportInput>()
        val keepBases = mutableSetOf<String>()
        val candidates = mutableListOf<ImportCandidate>()
        val issues = mutableListOf<ImportIssue>()
        val ordered = inputs.sortedBy { input ->
            when {
                JsonHandler().canHandle(input) -> 0
                HtmlHandler().canHandle(input) -> 1
                else -> 2
            }
        }
        for (input in ordered) {
            if (input in consumed) continue
            val html = HtmlHandler().canHandle(input)
            val base = input.name.substringBeforeLast('.')
            if (html && base in keepBases) continue
            if (!allowZip && (input.extension in setOf("css", "js") || input.name.substringAfterLast('/').equals("labels.txt", true))) continue

            val referenced = mutableSetOf<ImportInput>()
            val resolver: (String) -> ImportInput? = { ref ->
                val parent = input.name.replace('\\', '/').substringBeforeLast('/', "")
                val target = resourcePaths(ref, parent).firstNotNullOfOrNull { path -> byPath[path]?.singleOrNull() }
                target?.takeUnless { it === input }?.also { referenced += it }
            }
            try {
                val out = if (allowZip && input.isZip()) parseZip(input, ctx) else {
                    val handlers = listOf(SamsungNotesHandler, JsonHandler(resolver), MarkdownHandler, HtmlHandler(resolver), DocxHandler,
                        PdfHandler, ImageHandler, PlainTextHandler)
                    handlers.firstOrNull { it.canHandle(input) }?.parse(input, ctx)
                        ?: ParseOutput(emptyList(), listOf(ImportIssue(input.name, ImportWarning.UNSUPPORTED)))
                }
                candidates += out.candidates
                issues += out.issues
                if (out.hasKeepNote) keepBases += base
                if (out.candidates.any { it.attachments.isNotEmpty() }) consumed += referenced
            } catch (e: Exception) {
                issues += e.toImportIssue(input.name)
            }
        }
        return ParseOutput(candidates, issues)
    }

    private fun ImportInput.isZip(): Boolean =
        extension == "zip" || mime == "application/zip" || mime == "application/x-zip-compressed"

    /** Resolve paths only against the selected files or the current archive. */
    private fun resourcePaths(reference: String, parent: String): List<String> {
        val withoutSuffix = reference.substringBefore('#').substringBefore('?')
        val decoded = runCatching { java.net.URLDecoder.decode(withoutSuffix.replace("+", "%2B"), "UTF-8") }.getOrNull()
        return listOfNotNull(reference, withoutSuffix, decoded).distinct().map { it.replace('\\', '/') }
            .filter { !it.startsWith('/') && ':' !in it && '\u0000' !in it }.flatMap { path ->
            listOfNotNull(
                if (parent.isEmpty()) null else archivePath("$parent/$path", allowParents = true),
                archivePath(path, allowParents = true)
            )
        }.distinct()
    }

    private fun archivePath(name: String, allowParents: Boolean = false): String? {
        val slashed = name.replace('\\', '/')
        if (slashed.startsWith('/') || ':' in slashed || '\u0000' in slashed) return null
        val parts = mutableListOf<String>()
        for (part in slashed.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (allowParents && parts.isNotEmpty()) parts.removeAt(parts.lastIndex) else return null
                else -> parts += part
            }
        }
        return parts.joinToString("/").takeIf(String::isNotEmpty)
    }

    /**
     * Stages complete ZIP entries under generated filenames. Archive paths only identify entries
     * and resolve references. Skipped entries count toward both limits.
     */
    internal fun parseZip(input: ImportInput, ctx: ImportContext, byteLimit: Long = MAX_ZIP_BYTES): ParseOutput {
        val files = linkedMapOf<String, ImportInput>()
        val issues = mutableListOf<ImportIssue>()
        var remaining = byteLimit
        var entries = 0
        try {
            ZipInputStream(input.open()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++entries > MAX_ZIP_ENTRIES || entry.size > remaining) throw ImportLimitExceeded()
                    val name = archivePath(entry.name)
                    if (entry.isDirectory || name == null || name in files ||
                        name.startsWith("__MACOSX/") || name.substringAfterLast('/').startsWith(".")) {
                        remaining -= zip.copyWithinLimit(null, remaining)
                        continue
                    }
                    val file = ctx.stash(name, zip, remaining)
                    remaining -= file.length()
                    files[name] = ImportInput(name, AttachmentStore.guessMime(name), entry.time.takeIf { it > 0 }, file.length(), file) {
                        file.inputStream()
                    }
                }
            }
        } catch (e: Exception) {
            issues += e.toImportIssue(input.name)
        }
        val parsed = parseFiles(files.values.toList(), ctx, allowZip = false)
        return parsed.copy(issues = issues + parsed.issues)
    }

    /** Marks EXACT (same normalized content) and SIMILAR (same title, different content) duplicates. */
    suspend fun markDuplicates(uid: String, candidates: List<ImportCandidate>): List<ImportCandidate> {
        val existing = repository.existingHashes(uid)
        val byHash = existing.filter { it.contentHash.isNotBlank() }.groupBy { it.contentHash }
        val byTitle = existing.groupBy { ArabicNormalizer.normalize(it.title) }
        val seenInBatch = hashSetOf<Pair<String, List<Boolean>>>()
        return candidates.map { c ->
            val hash = NoteRepository.contentHash(c.title, c.body, c.checklist)
            val checks = c.checklist.map { it.checked }
            val comparable = !c.isLocked && c.attachments.isEmpty()
            val exact = if (comparable) byHash[hash].orEmpty().firstOrNull { row ->
                val note = repository.getNote(row.id)
                note != null && note.attachments.isEmpty() && note.checklist.sortedBy { it.position }.map { it.checked } == checks
            } else null
            val similar = if (c.title.isNotBlank()) byTitle[ArabicNormalizer.normalize(c.title)]?.firstOrNull() else null
            when {
                c.storedLocked != null && existing.any { it.id == c.storedLocked.id } ->
                    c.copy(duplicate = DuplicateKind.EXACT, duplicateOfId = c.storedLocked.id, action = ImportAction.SKIP)
                exact != null -> c.copy(duplicate = DuplicateKind.EXACT, duplicateOfId = exact.id, action = ImportAction.SKIP)
                comparable && !seenInBatch.add(hash to checks) -> c.copy(duplicate = DuplicateKind.EXACT, action = ImportAction.SKIP)
                similar != null -> c.copy(duplicate = DuplicateKind.SIMILAR,
                    duplicateOfId = similar.id.takeIf { similar.contentHash.isNotEmpty() }, action = ImportAction.KEEP_BOTH)
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
                        require(c.canMerge)
                        val target = c.duplicateOfId?.let { repository.getNote(it) }
                        if (target == null || target.note.deleted || target.note.userId != uid) {
                            attachments += createFrom(uid, source, c, defaultFolderId, ids, failed)
                            created++
                        } else {
                            require(!target.note.isLocked)
                            val draft = requireNotNull(repository.toDraft(uid, target))
                            mergeInto(uid, draft, c)
                            attachments += addAttachments(uid, draft.id, c, failed).size
                            merged++
                        }
                    }
                    ImportAction.IMPORT, ImportAction.KEEP_BOTH -> {
                        attachments += createFrom(uid, source, c, defaultFolderId, ids, failed)
                        created++
                    }
                }
            } catch (e: Exception) {
                failed += e.toImportIssue(c.sourceName)
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

    private suspend fun mergeInto(uid: String, draft: NoteDraft, candidate: ImportCandidate) {
        repository.snapshot(draft.id, RevisionReason.IMPORT_MERGE)
        val existingTexts = draft.checklist.mapTo(hashSetOf()) { ArabicNormalizer.normalize(it.text) }
        val newItems = candidate.checklist.filter { existingTexts.add(ArabicNormalizer.normalize(it.text)) }
        val checklistBlock = if (draft.blocks != null && newItems.isNotEmpty()) BlockDraft(type = BlockType.CHECKLIST) else null
        val layout = draft.blocks?.let { blocks ->
            buildList {
                addAll(blocks)
                if (candidate.body.isNotBlank()) add(BlockDraft(type = BlockType.TEXT, text = candidate.body))
                checklistBlock?.let { add(it) }
            }
        }
        repository.save(uid, draft.copy(
            body = listOf(draft.body, candidate.body).filter(String::isNotBlank).joinToString(Blocks.SEPARATOR),
            blocks = layout,
            checklist = draft.checklist + newItems.map { it.copy(id = Ids.newId(), blockId = checklistBlock?.id) },
            tags = (draft.tags + candidate.tags).distinct(),
            updatedAt = null
        ))
    }

    private suspend fun createFrom(
        uid: String,
        source: ImportSource,
        c: ImportCandidate,
        defaultFolderId: String?,
        ids: MutableMap<String, String>,
        failed: MutableList<ImportIssue>
    ): Int {
        val folderId = c.folderName?.let { name -> (repository.findFolderByName(uid, name) ?: repository.createFolder(uid, name)).id } ?: defaultFolderId
        val stored = c.storedLocked
        // Keep the original id (backups) unless it already exists here ("keep both" gets a new id).
        val newId = if (c.action == ImportAction.IMPORT && repository.getNote(c.key) == null && c.key.matches(Regex("[A-Za-z0-9_\\-]{1,128}"))) c.key else Ids.newId()
        if (stored != null) {
            // Locked note from a backup: keep the ciphertext exactly as it was.
            repository.applyStored(uid, stored.copy(id = newId, folderId = folderId, syncState = SyncState.DIRTY, baseRemoteUpdatedAt = 0L), emptyList(), c.tags, c.reminderAt)
            ids[c.key] = newId
            repository.markDirty(uid, newId)
            return addAttachments(uid, newId, c, failed).size
        }
        var body = c.body
        var locked = c.requiresEncryption
        if (body.isEncryptedPayload()) {
            val plain = LegacyCryptoV1.tryDecrypt(body)
            if (plain == null || !repository.vaultReady(uid)) {
                // Preserve the original encrypted payload when it cannot be re-encrypted here.
                val ts = System.currentTimeMillis()
                repository.applyStored(uid, com.example.data.local.NoteEntity(
                    id = newId, userId = uid, title = c.title.ifBlank { "🔒" }, body = body, colorIndex = c.colorIndex, folderId = folderId,
                    isPinned = c.isPinned, isFavorite = c.isFavorite, isArchived = c.isArchived, trashedAt = c.trashedAt,
                    isLocked = true, lockPinHash = c.lockPinHash, lockHint = c.lockHint,
                    createdAt = c.createdAt ?: ts, updatedAt = c.updatedAt ?: ts, importSource = source.key
                ), emptyList(), c.tags, null)
                ids[c.key] = newId
                repository.markDirty(uid, newId)
                return 0
            }
            body = plain; locked = true
        }
        // Checklist and block IDs are global Room keys, including the default checklist block ID.
        val sourceLayout = c.blocks?.let { Blocks.reconcile(it, body, c.checklist, c.attachments.mapNotNull { a -> a.originalId }) }
        val blockIds = sourceLayout?.associate { it.id to Ids.newId() }.orEmpty()
        val checklist = c.checklist.map { item ->
            item.copy(id = Ids.newId(), blockId = blockIds[item.blockId ?: Blocks.DEFAULT_CHECKLIST])
        }
        val draft = NoteDraft(
            id = newId, title = c.title.take(NoteRepository.MAX_TITLE), body = body, colorIndex = c.colorIndex, folderId = folderId,
            isPinned = c.isPinned, isFavorite = c.isFavorite, isArchived = c.isArchived, trashedAt = c.trashedAt,
            isLocked = locked, lockPinHash = c.lockPinHash, lockHint = c.lockHint, tags = c.tags, checklist = checklist,
            createdAt = c.createdAt, updatedAt = c.updatedAt ?: c.createdAt, importSource = source.key
        )
        repository.save(uid, draft)
        ids[c.key] = newId
        val idMap = addAttachments(uid, newId, c, failed)
        val n = idMap.size
        // Restore the block layout (attachment references remapped when ids had to change).
        if (sourceLayout != null) repository.getNote(newId)?.let { d ->
            repository.toDraft(uid, d)?.let { cur ->
                val remapped = sourceLayout.map { block ->
                    block.copy(id = blockIds.getValue(block.id), attachmentId = block.attachmentId?.let(idMap::get))
                }
                repository.save(uid, cur.copy(blocks = Blocks.reconcile(remapped, cur.body, cur.checklist, d.attachments.map { it.id }), updatedAt = null))
            }
        }
        if (c.reminderAt != null && c.reminderAt > System.currentTimeMillis()) repository.setReminder(uid, newId, c.reminderAt)
        // Attachments bump updatedAt; restore the original modification date.
        if (draft.updatedAt != null) repository.restoreTimestamps(newId, draft.createdAt, draft.updatedAt)
        return n
    }

    /** Adds the candidate's attachments; returns source id -> stored id. */
    private suspend fun addAttachments(uid: String, noteId: String, c: ImportCandidate, failed: MutableList<ImportIssue>): Map<String, String> {
        val ids = linkedMapOf<String, String>()
        for (a in c.attachments) {
            val key = a.originalId ?: Ids.newId()
            try {
                ids[key] = if (a.encrypted) {
                    repository.addRawAttachment(uid, noteId, a)
                } else {
                    val att = if (a.kind == AttachmentKind.IMAGE) {
                        val p = com.example.core.media.ImageNormalizer.prepare(a.file, a.name, a.mime)
                        a.copy(kind = if (p.isImage) a.kind else AttachmentKind.FILE, name = p.name, mime = p.mime, file = p.file)
                    } else a
                    repository.addAttachment(uid, noteId, att.kind, att.name, att.mime,
                        if (att.kind == AttachmentKind.LINK || att.file.name.isEmpty()) null else att.open(),
                        att.extractedText, att.durationMs, att.url, att.linkTitle, att.linkDescription, att.createdAt, preferredId = a.originalId).id
                }
            } catch (e: Exception) {
                failed += e.toImportIssue(a.name)
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
