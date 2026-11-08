package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.security.CryptoException
import com.example.core.security.CryptoPrimitives
import com.example.core.security.KeyVault
import com.example.core.security.LegacyCryptoV1
import com.example.core.security.NoteCipher
import com.example.core.text.ArabicNormalizer
import com.example.data.local.AttachmentEntity
import com.example.data.local.AttachmentKind
import com.example.data.local.ChecklistItemEntity
import com.example.data.local.FolderEntity
import com.example.data.local.NoteEntity
import com.example.data.local.NoteBlockEntity
import com.example.data.local.NoteFtsEntity
import com.example.data.local.NoteTagCrossRef
import com.example.data.local.NoteWithDetails
import com.example.data.local.NoteaDatabase
import com.example.data.local.ReminderEntity
import com.example.data.local.ReminderStatus
import com.example.data.local.RevisionEntity
import com.example.data.local.RevisionReason
import com.example.data.local.SyncState
import com.example.data.local.TagEntity
import com.example.data.local.TemplateEntity
import com.example.data.legacy.LegacyNoteParser
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/** Hooks the repository calls after local writes (sync queue, alarms). */
interface ChangeListener {
    fun onNotesChanged(uid: String) {}
    fun onReminderChanged(reminder: ReminderEntity, noteTitle: String, locked: Boolean) {}
    fun onReminderCancelled(reminderId: String) {}
    /** An image or PDF was stored (OCR runs in the background). */
    fun onScannableAttachment(attachment: AttachmentEntity) {}
}

/**
 * Local-first source of truth. Every write goes to Room first (works fully offline), marks the
 * row dirty and notifies the sync queue. Nothing here waits for the network.
 */
class NoteRepository(
    private val db: NoteaDatabase,
    private val keyVault: KeyVault,
    val attachments: AttachmentStore,
    private val listener: ChangeListener = object : ChangeListener {},
    private val now: () -> Long = System::currentTimeMillis
) {
    private val notes = db.noteDao()
    private val checklists = db.checklistDao()
    private val tags = db.tagDao()
    private val folders = db.folderDao()
    private val attachmentDao = db.attachmentDao()
    private val reminders = db.reminderDao()
    private val revisions = db.revisionDao()
    private val templates = db.templateDao()
    private val search = db.searchDao()
    private val blockDao = db.blockDao()

    // ---------------------------------------------------------------- observe
    fun observeNotes(uid: String): Flow<List<NoteWithDetails>> = notes.observeNotes(uid)
    fun observeNote(id: String): Flow<NoteWithDetails?> = notes.observeNote(id)
    fun observeFolders(uid: String) = folders.observeFolders(uid)
    fun observeTags(uid: String) = tags.observeTags(uid)
    fun observeTagUsage(uid: String) = tags.observeTagUsage(uid)
    fun observeRevisions(noteId: String) = revisions.observeForNote(noteId)
    fun observeTemplates(uid: String) = templates.observe(uid)
    fun observePendingSyncCount(uid: String) = notes.observeDirtyCount(uid)

    suspend fun getNote(id: String) = notes.getNoteWithDetails(id)

    // ---------------------------------------------------------------- encryption helpers
    fun decrypt(uid: String, note: NoteEntity, checklist: List<ChecklistItemEntity> = emptyList(), blocks: List<NoteBlockEntity> = emptyList()): DecryptResult {
        val storedBlocks = blocks.sortedBy { it.position }.map { BlockDraft(it.id, it.type, it.text, it.attachmentId) }.ifEmpty { null }
        if (!note.isLocked && !NoteCipher.isEncrypted(note.body)) return DecryptResult.Plain(note.body, checklist.toDrafts(), storedBlocks)
        val body = note.body
        return when {
            NoteCipher.isV2(body) -> {
                val mk = keyVault.masterKey(uid) ?: return DecryptResult.VaultLocked
                try {
                    val content = LockedPayload.decodeContent(NoteCipher.decryptText(mk, body))
                    DecryptResult.Plain(content.body, content.checklist.ifEmpty { checklist.toDrafts() }, content.blocks)
                } catch (_: CryptoException) {
                    DecryptResult.Failed
                }
            }
            LegacyCryptoV1.isLegacyPayload(body) -> {
                val plain = LegacyCryptoV1.tryDecrypt(body) ?: return DecryptResult.ForeignLegacy
                val parsed = LegacyNoteParser.parse(plain, "", "")
                DecryptResult.Plain(parsed.body, checklist.toDrafts() + parsed.checklist.map { ChecklistDraft(it.id, it.text, it.checked) })
            }
            // Locked flag but plaintext body (v1 encryption failure): readable, re-encrypted on next save.
            else -> DecryptResult.Plain(body, checklist.toDrafts(), storedBlocks)
        }
    }

    fun toDraft(uid: String, d: NoteWithDetails): NoteDraft? {
        val n = d.note
        val dec = decrypt(uid, n, d.checklist, d.blocks) as? DecryptResult.Plain ?: return null
        return NoteDraft(
            id = n.id, title = n.title, body = dec.body, colorIndex = n.colorIndex, folderId = n.folderId,
            isPinned = n.isPinned, isFavorite = n.isFavorite, isArchived = n.isArchived, trashedAt = n.trashedAt,
            isLocked = n.isLocked, lockPinHash = n.lockPinHash, lockHint = n.lockHint,
            tags = d.tags.map { it.name }, checklist = dec.checklist, createdAt = n.createdAt, updatedAt = n.updatedAt,
            importSource = n.importSource, blocks = dec.blocks, parentId = n.parentId, icon = n.icon
        )
    }

    // ---------------------------------------------------------------- writes
    /**
     * Persists [draft]. Locked notes are encrypted with the vault master key; if the key is not
     * available a [VaultLockedException] is thrown and NOTHING is written (never plaintext).
     */
    suspend fun save(uid: String, input: NoteDraft, markDirty: Boolean = true): NoteEntity {
        // With a block layout, the canonical body is derived from the text blocks.
        val layout = input.blocks?.let { Blocks.normalize(it) }
        val draft = if (layout != null) input.copy(blocks = layout, body = Blocks.joinText(layout)) else input
        val existing = notes.getNote(draft.id)
        val ts = now()
        val storedBody: String
        val storedChecklist: List<ChecklistDraft>
        if (draft.isLocked) {
            val mk = keyVault.masterKey(uid) ?: throw VaultLockedException()
            storedBody = NoteCipher.encryptText(mk, LockedPayload.encode(draft.body, draft.checklist, layout))
            storedChecklist = emptyList()
        } else {
            storedBody = draft.body
            storedChecklist = draft.checklist
        }
        val entity = NoteEntity(
            id = draft.id,
            userId = uid,
            title = draft.title.take(MAX_TITLE),
            body = storedBody,
            colorIndex = draft.colorIndex.coerceIn(0, MAX_COLOR),
            folderId = draft.folderId,
            isPinned = draft.isPinned,
            isFavorite = draft.isFavorite,
            isArchived = draft.isArchived,
            trashedAt = draft.trashedAt,
            isLocked = draft.isLocked,
            lockPinHash = draft.lockPinHash,
            lockHint = draft.lockHint.take(100),
            createdAt = draft.createdAt ?: existing?.createdAt ?: ts,
            updatedAt = draft.updatedAt ?: ts,
            syncState = if (markDirty) SyncState.DIRTY else (existing?.syncState ?: SyncState.CLEAN),
            deleted = false,
            baseRemoteUpdatedAt = existing?.baseRemoteUpdatedAt ?: 0L,
            legacyCategory = existing?.legacyCategory ?: "",
            legacyPending = 0,
            importSource = draft.importSource.ifBlank { existing?.importSource ?: "" },
            contentHash = if (draft.isLocked) "" else contentHash(draft.title, draft.body, draft.checklist),
            parentId = (draft.parentId ?: existing?.parentId)?.takeIf { it != draft.id },
            icon = (draft.icon ?: existing?.icon ?: "").take(16)
        )
        val wasLocked = existing?.isLocked == true
        db.withTransaction {
            notes.upsert(entity)
            checklists.replaceForNote(entity.id, storedChecklist.mapIndexed { i, c ->
                ChecklistItemEntity(c.id, entity.id, c.text.take(1000), c.checked, i, c.blockId)
            })
            // Locked notes keep their layout inside the encrypted payload only.
            blockDao.replaceForNote(entity.id, if (draft.isLocked || layout == null) emptyList() else layout.mapIndexed { i, b ->
                NoteBlockEntity(b.id, entity.id, i, b.type, b.text, b.attachmentId)
            })
            setTags(uid, entity.id, draft.tags)
            reindex(uid, entity, draft.body, draft.checklist, draft.tags)
        }
        if (wasLocked != draft.isLocked) reencryptAttachments(uid, entity.id, toLocked = draft.isLocked)
        if (markDirty) listener.onNotesChanged(uid)
        return entity
    }

    suspend fun newNoteId(): String = Ids.newId()

    private suspend fun setTags(uid: String, noteId: String, names: List<String>) {
        tags.clearNoteTags(noteId)
        val refs = names.map { it.trim().removePrefix("#").take(40) }.filter { it.isNotBlank() }
            .distinctBy { ArabicNormalizer.normalize(it) }
            .map { name ->
                val norm = ArabicNormalizer.normalize(name)
                val tag = tags.findByNormalized(uid, norm) ?: TagEntity(Ids.newId(), uid, name, norm).also { tags.insert(it) }
                NoteTagCrossRef(noteId, (tags.findByNormalized(uid, norm) ?: tag).id)
            }
        if (refs.isNotEmpty()) tags.insertRefs(refs)
        tags.deleteUnused(uid)
    }

    private suspend fun reindex(uid: String, n: NoteEntity, plainBody: String, checklist: List<ChecklistDraft>, tagNames: List<String>) {
        val atts = attachmentDao.forNote(n.id)
        val folderName = n.folderId?.let { folders.get(it)?.name }.orEmpty()
        val bodyText = if (n.isLocked) "" else plainBody + "\n" + checklist.joinToString("\n") { it.text }
        val extra = if (n.isLocked) folderName else
            (atts.map { it.displayName + " " + it.extractedText + " " + it.linkTitle + " " + it.url } + folderName).joinToString(" ")
        search.replace(
            NoteFtsEntity(
                noteId = n.id, userId = uid,
                title = ArabicNormalizer.normalize(n.title),
                body = ArabicNormalizer.normalize(bodyText),
                tags = ArabicNormalizer.normalize(tagNames.joinToString(" ")),
                extra = ArabicNormalizer.normalize(extra)
            )
        )
    }

    /** Rebuilds the search index entry for one note from what is stored. */
    suspend fun reindexNote(uid: String, noteId: String) {
        val d = notes.getNoteWithDetails(noteId) ?: return
        if (d.note.deleted) { search.deleteForNote(noteId); return }
        val plain = (decrypt(uid, d.note, d.checklist) as? DecryptResult.Plain)
        reindex(uid, d.note, plain?.body.orEmpty(), plain?.checklist.orEmpty(), d.tags.map { it.name })
    }

    suspend fun reindexAll(uid: String) {
        notes.getAllWithDetails(uid).forEach { reindexNote(uid, it.note.id) }
    }

    private suspend fun update(uid: String, id: String, change: (NoteEntity) -> NoteEntity) {
        val n = notes.getNote(id) ?: return
        notes.upsert(change(n).copy(updatedAt = now(), syncState = SyncState.DIRTY))
        listener.onNotesChanged(uid)
    }

    suspend fun setPinned(uid: String, id: String, pinned: Boolean) = update(uid, id) { it.copy(isPinned = pinned) }
    suspend fun setFavorite(uid: String, id: String, fav: Boolean) = update(uid, id) { it.copy(isFavorite = fav) }
    suspend fun setArchived(uid: String, id: String, archived: Boolean) =
        update(uid, id) { it.copy(isArchived = archived, isPinned = if (archived) false else it.isPinned) }
    suspend fun setColor(uid: String, id: String, color: Int) = update(uid, id) { it.copy(colorIndex = color.coerceIn(0, MAX_COLOR)) }
    suspend fun moveToFolder(uid: String, id: String, folderId: String?) = update(uid, id) { it.copy(folderId = folderId) }
    suspend fun moveToTrash(uid: String, id: String) = update(uid, id) { it.copy(trashedAt = now(), isPinned = false) }
    suspend fun restoreFromTrash(uid: String, id: String) = update(uid, id) { it.copy(trashedAt = null) }
    /** Restores sub-note/icon metadata from a backup without touching the note's dates. */
    suspend fun restoreMeta(uid: String, id: String, parentId: String?, icon: String) {
        val n = notes.getNote(id) ?: return
        if (parentId == id) return
        notes.upsert(n.copy(parentId = parentId ?: n.parentId, icon = icon.ifBlank { n.icon }.take(16), syncState = SyncState.DIRTY))
        listener.onNotesChanged(uid)
    }
    suspend fun setIcon(uid: String, id: String, icon: String) = update(uid, id) { it.copy(icon = icon.take(16)) }

    /** Moves [id] under [parentId] (null = top level). Refuses cycles (a note can't go under its own descendant). */
    suspend fun setParent(uid: String, id: String, parentId: String?): Boolean {
        if (parentId != null) {
            var cur: String? = parentId
            var guard = 0
            while (cur != null && guard++ < 64) {
                if (cur == id) return false
                cur = notes.getNote(cur)?.parentId
            }
        }
        update(uid, id) { it.copy(parentId = parentId) }
        return true
    }

    /** Permanent delete. Synced notes become tombstones until the remote delete is confirmed. */
    suspend fun deletePermanently(uid: String, id: String) {
        val n = notes.getNote(id) ?: return
        attachmentDao.forNote(id).forEach { attachments.delete(uid, it.localPath) }
        reminders.forNote(id).forEach { listener.onReminderCancelled(it.id) }
        db.withTransaction {
            search.deleteForNote(id)
            if (n.baseRemoteUpdatedAt > 0L || n.legacyCategory.isNotEmpty()) {
                checklists.deleteForNote(id)
                tags.clearNoteTags(id)
                reminders.deleteForNote(id)
                notes.upsert(n.copy(deleted = true, body = "", title = "", syncState = SyncState.DIRTY, updatedAt = now()))
            } else {
                notes.hardDelete(id)
            }
            tags.deleteUnused(uid)
        }
        listener.onNotesChanged(uid)
    }

    suspend fun emptyTrash(uid: String) {
        notes.getAllWithDetails(uid).filter { it.note.trashedAt != null }.forEach { deletePermanently(uid, it.note.id) }
    }

    suspend fun purgeExpiredTrash(uid: String) {
        notes.getExpiredTrash(uid, now() - TRASH_RETENTION_MS).forEach { deletePermanently(uid, it.id) }
    }

    // ---------------------------------------------------------------- sync support
    /** Writes a note exactly as stored remotely (ciphertext stays ciphertext); does not mark dirty. */
    suspend fun applyStored(uid: String, n: NoteEntity, checklist: List<ChecklistDraft>, tagNames: List<String>, reminderAt: Long?, blocks: List<BlockDraft>? = null) {
        val existing = notes.getNote(n.id)
        val entity = n.copy(
            userId = uid,
            colorIndex = n.colorIndex.coerceIn(0, MAX_COLOR),
            syncState = SyncState.CLEAN,
            legacyPending = 0,
            importSource = existing?.importSource ?: "",
            contentHash = if (n.isLocked) "" else contentHash(n.title, n.body, checklist)
        )
        db.withTransaction {
            notes.upsert(entity)
            checklists.replaceForNote(n.id, checklist.mapIndexed { i, c -> ChecklistItemEntity(c.id, n.id, c.text, c.checked, i, c.blockId) })
            // Keep this device's layout when the remote copy has none (e.g. written by an older app version).
            if (n.isLocked) blockDao.deleteForNote(n.id)
            else if (blocks != null) blockDao.replaceForNote(n.id, blocks.mapIndexed { i, b -> NoteBlockEntity(b.id, n.id, i, b.type, b.text, b.attachmentId) })
            else {
                val local = blockDao.forNote(n.id).map { BlockDraft(it.id, it.type, it.text, it.attachmentId) }
                if (local.isNotEmpty() && Blocks.joinText(local) != n.body) blockDao.deleteForNote(n.id)
            }
            setTags(uid, n.id, tagNames)
        }
        reindexNote(uid, n.id)
        val current = reminders.forNote(n.id).filter { it.status != ReminderStatus.DONE }.minByOrNull { it.triggerAt }
        if (reminderAt != current?.triggerAt) {
            reminders.forNote(n.id).forEach { listener.onReminderCancelled(it.id); reminders.delete(it.id) }
            if (reminderAt != null && reminderAt > now()) {
                val r = ReminderEntity(Ids.newId(), n.id, uid, reminderAt, ReminderStatus.SCHEDULED, now())
                reminders.upsert(r)
                listener.onReminderChanged(r, n.title, n.isLocked)
            }
        }
    }

    /** Removes the local copy of a note that was deleted on another device. */
    suspend fun removeLocalCopy(uid: String, id: String) {
        attachmentDao.forNote(id).forEach { attachments.delete(uid, it.localPath) }
        reminders.forNote(id).forEach { listener.onReminderCancelled(it.id) }
        db.withTransaction {
            search.deleteForNote(id)
            notes.hardDelete(id)
            tags.deleteUnused(uid)
        }
    }

    suspend fun markDirty(uid: String, id: String) {
        val n = notes.getNote(id) ?: return
        notes.upsert(n.copy(syncState = SyncState.DIRTY))
        listener.onNotesChanged(uid)
    }

    suspend fun restoreTimestamps(id: String, createdAt: Long?, updatedAt: Long?) {
        val n = notes.getNote(id) ?: return
        notes.upsert(n.copy(createdAt = createdAt ?: n.createdAt, updatedAt = updatedAt ?: n.updatedAt))
    }

    suspend fun existingHashes(uid: String) = notes.getHashes(uid)

    fun vaultReady(uid: String) = keyVault.masterKey(uid) != null

    /** Copies an already-encrypted attachment (from a backup) without decrypting it. */
    suspend fun addRawAttachment(uid: String, noteId: String, a: com.example.data.importer.ImportedAttachment): String {
        val id = a.originalId?.takeIf { attachmentDao.get(it) == null } ?: Ids.newId()
        val stored = a.open().use { attachments.save(uid, id, it, null) }
        attachmentDao.upsert(
            AttachmentEntity(
                id = id, noteId = noteId, userId = uid, kind = a.kind, mimeType = a.mime, displayName = a.name,
                localPath = stored.relativePath, sizeBytes = stored.size, durationMs = a.durationMs, url = a.url,
                linkTitle = a.linkTitle, linkDescription = a.linkDescription, extractedText = a.extractedText,
                encrypted = true, createdAt = a.createdAt ?: now(), position = attachmentDao.forNote(noteId).size
            )
        )
        return id
    }

    // ---------------------------------------------------------------- folders
    suspend fun createFolder(uid: String, name: String, colorIndex: Int = 0): FolderEntity {
        val ts = now()
        val f = FolderEntity(Ids.newId(), uid, name.trim().take(60), colorIndex, 0, ts, ts)
        folders.upsert(f)
        listener.onNotesChanged(uid)
        return f
    }

    suspend fun renameFolder(uid: String, id: String, name: String) {
        val f = folders.get(id) ?: return
        folders.upsert(f.copy(name = name.trim().take(60), updatedAt = now(), syncState = SyncState.DIRTY))
        listener.onNotesChanged(uid)
    }

    /** Deleting a folder never deletes notes: they move back to "no folder". */
    suspend fun deleteFolder(uid: String, id: String) {
        val f = folders.get(id) ?: return
        notes.getAllWithDetails(uid).filter { it.note.folderId == id }.forEach { moveToFolder(uid, it.note.id, null) }
        folders.upsert(f.copy(deleted = true, updatedAt = now(), syncState = SyncState.DIRTY))
        listener.onNotesChanged(uid)
    }

    suspend fun ensureFolder(uid: String, id: String, name: String) {
        if (folders.get(id) == null) {
            val ts = now()
            folders.upsert(FolderEntity(id, uid, name, 0, 0, ts, ts))
        }
    }

    suspend fun findFolderByName(uid: String, name: String): FolderEntity? {
        val norm = ArabicNormalizer.normalize(name)
        return folders.allIncludingDeleted(uid).firstOrNull { !it.deleted && ArabicNormalizer.normalize(it.name) == norm }
    }

    suspend fun allFolders(uid: String) = folders.allIncludingDeleted(uid).filter { !it.deleted }

    // ---------------------------------------------------------------- tags
    suspend fun renameTag(uid: String, tagId: String, newName: String) {
        val all = notes.getAllWithDetails(uid).filter { d -> d.tags.any { it.id == tagId } }
        all.forEach { d ->
            val names = d.tags.map { if (it.id == tagId) newName else it.name }
            db.withTransaction { setTags(uid, d.note.id, names) }
            update(uid, d.note.id) { it }
            reindexNote(uid, d.note.id)
        }
    }

    // ---------------------------------------------------------------- attachments
    suspend fun addAttachment(
        uid: String, noteId: String, kind: String, displayName: String, mime: String,
        input: InputStream?, extractedText: String = "", durationMs: Long = 0L,
        url: String = "", linkTitle: String = "", linkDescription: String = "", createdAt: Long? = null,
        preferredId: String? = null
    ): AttachmentEntity {
        val note = notes.getNote(noteId)
        val locked = note?.isLocked == true
        val key = if (locked) keyVault.masterKey(uid) ?: throw VaultLockedException() else null
        val id = preferredId?.takeIf { attachmentDao.get(it) == null } ?: Ids.newId()
        val stored = input?.use { attachments.save(uid, id, it, key) }
        val entity = AttachmentEntity(
            id = id, noteId = noteId, userId = uid, kind = kind, mimeType = mime,
            displayName = displayName.take(200), localPath = stored?.relativePath ?: "", sizeBytes = stored?.size ?: 0L,
            durationMs = durationMs, url = url, linkTitle = linkTitle.take(300), linkDescription = linkDescription.take(600),
            extractedText = extractedText.take(200_000), encrypted = key != null,
            createdAt = createdAt ?: now(), position = attachmentDao.forNote(noteId).size
        )
        attachmentDao.upsert(entity)
        reindexNote(uid, noteId)
        update(uid, noteId) { it }
        if ((kind == AttachmentKind.IMAGE || kind == AttachmentKind.PDF) && !entity.encrypted && extractedText.isBlank()) listener.onScannableAttachment(entity)
        return entity
    }

    /** Stores OCR text (never for locked notes) and makes it searchable. */
    suspend fun setExtractedText(attachmentId: String, text: String) {
        val a = attachmentDao.get(attachmentId) ?: return
        val note = notes.getNote(a.noteId) ?: return
        if (note.isLocked) return
        attachmentDao.upsert(a.copy(extractedText = text.take(200_000)))
        reindexNote(a.userId, a.noteId)
    }

    fun observeAttachments(noteId: String) = attachmentDao.observeForNote(noteId)

    suspend fun removeAttachment(uid: String, attachmentId: String) {
        val a = attachmentDao.get(attachmentId) ?: return
        attachments.delete(uid, a.localPath)
        attachmentDao.delete(attachmentId)
        reindexNote(uid, a.noteId)
        update(uid, a.noteId) { it }
    }

    suspend fun attachmentsFor(noteId: String) = attachmentDao.forNote(noteId)

    fun openAttachment(uid: String, a: AttachmentEntity): InputStream =
        attachments.open(uid, a.localPath, if (a.encrypted) keyVault.masterKey(uid) ?: throw VaultLockedException() else null)

    private suspend fun reencryptAttachments(uid: String, noteId: String, toLocked: Boolean) {
        val mk = keyVault.masterKey(uid) ?: return
        attachmentDao.forNote(noteId).forEach { a ->
            var updated = a
            if (a.localPath.isNotBlank() && a.encrypted != toLocked) {
                attachments.transform(uid, a.localPath, if (a.encrypted) mk else null, if (toLocked) mk else null)
                updated = updated.copy(encrypted = toLocked)
            }
            // OCR text is stored unencrypted: drop it when locking, extract again after unlocking.
            if (toLocked) updated = updated.copy(extractedText = "")
            if (updated != a) attachmentDao.upsert(updated)
            if (!toLocked && (a.kind == AttachmentKind.IMAGE || a.kind == AttachmentKind.PDF)) listener.onScannableAttachment(updated)
        }
        reindexNote(uid, noteId)
    }

    // ---------------------------------------------------------------- reminders
    suspend fun setReminder(uid: String, noteId: String, triggerAt: Long?) {
        reminders.forNote(noteId).forEach {
            listener.onReminderCancelled(it.id)
            reminders.delete(it.id)
        }
        val note = notes.getNote(noteId) ?: return
        if (triggerAt != null) {
            val r = ReminderEntity(Ids.newId(), noteId, uid, triggerAt, ReminderStatus.SCHEDULED, now())
            reminders.upsert(r)
            listener.onReminderChanged(r, note.title, note.isLocked)
        }
        update(uid, noteId) { it }
    }

    suspend fun snoozeReminder(reminderId: String, until: Long) {
        val r = reminders.get(reminderId) ?: return
        val note = notes.getNote(r.noteId) ?: return
        val updated = r.copy(triggerAt = until, status = ReminderStatus.SCHEDULED)
        reminders.upsert(updated)
        listener.onReminderChanged(updated, note.title, note.isLocked)
    }

    suspend fun markReminder(reminderId: String, status: Int) {
        val r = reminders.get(reminderId) ?: return
        reminders.upsert(r.copy(status = status))
    }

    suspend fun activeReminders() = reminders.allActive()
    fun observeReminders(uid: String) = reminders.observeActive(uid)

    // ---------------------------------------------------------------- revisions
    /** Stores a snapshot of the current stored state (ciphertext for locked notes). */
    suspend fun snapshot(noteId: String, reason: String = RevisionReason.AUTO, label: String = "") {
        val d = notes.getNoteWithDetails(noteId) ?: return
        val latest = revisions.latest(noteId)
        val checklistJson = d.checklist.toDrafts().toJson()
        val blocksJson = d.blocks.sortedBy { it.position }.map { BlockDraft(it.id, it.type, it.text, it.attachmentId) }.ifEmpty { null }?.blocksToJson().orEmpty()
        if (label.isBlank() && latest != null && latest.title == d.note.title && latest.body == d.note.body && latest.checklistJson == checklistJson) return
        revisions.insert(RevisionEntity(Ids.newId(), noteId, d.note.title, d.note.body, checklistJson, now(), reason, label.trim().take(80), blocksJson))
        revisions.trim(noteId, MAX_REVISIONS)
    }

    suspend fun renameRevision(id: String, label: String) = revisions.setLabel(id, label.trim().take(80))

    suspend fun addRevision(noteId: String, title: String, storedBody: String, checklist: List<ChecklistDraft>, reason: String, at: Long = now(),
                            label: String = "", blocksJson: String = "") {
        revisions.insert(RevisionEntity(Ids.newId(), noteId, title, storedBody, checklist.toJson(), at, reason, label, blocksJson))
        revisions.trim(noteId, MAX_REVISIONS)
    }

    fun decryptRevision(uid: String, rev: RevisionEntity): DecryptResult {
        val fake = NoteEntity(id = rev.noteId, userId = uid, title = rev.title, body = rev.body,
            isLocked = NoteCipher.isEncrypted(rev.body), createdAt = rev.createdAt, updatedAt = rev.createdAt)
        val dec = decrypt(uid, fake)
        if (dec !is DecryptResult.Plain) return dec
        return dec.copy(
            checklist = dec.checklist.ifEmpty { checklistFromJson(rev.checklistJson) },
            blocks = dec.blocks ?: blocksFromJson(rev.blocksJson)
        )
    }

    suspend fun restoreRevision(uid: String, rev: RevisionEntity) {
        val d = notes.getNoteWithDetails(rev.noteId) ?: return
        val current = toDraft(uid, d) ?: throw VaultLockedException()
        val dec = decryptRevision(uid, rev) as? DecryptResult.Plain ?: throw VaultLockedException()
        snapshot(rev.noteId, RevisionReason.BEFORE_RESTORE)
        save(uid, current.copy(title = rev.title, body = dec.body, checklist = dec.checklist, updatedAt = null,
            blocks = dec.blocks?.let { b -> Blocks.reconcile(b, dec.body, dec.checklist, d.attachments.map { it.id }) }))
    }

    // ---------------------------------------------------------------- templates
    suspend fun saveTemplate(uid: String, name: String, draft: NoteDraft) {
        templates.upsert(TemplateEntity(Ids.newId(), uid, name.take(60), draft.title, draft.body, draft.checklist.toJson(), now()))
    }

    suspend fun deleteTemplate(id: String) = templates.delete(id)

    // ---------------------------------------------------------------- vault migration
    /**
     * Once the vault is ready, re-encrypts every locked note that is still plaintext (v1 failure
     * fallback) or uses a v1 device-bound key. Returns the number of notes upgraded and the number
     * that could not be read on this device (left untouched).
     */
    suspend fun upgradeLockedNotes(uid: String): Pair<Int, Int> {
        keyVault.masterKey(uid) ?: return 0 to 0
        var upgraded = 0
        var foreign = 0
        notes.getLocked(uid).filter { !NoteCipher.isV2(it.body) }.forEach { n ->
            val d = notes.getNoteWithDetails(n.id) ?: return@forEach
            when (val dec = decrypt(uid, n, d.checklist)) {
                is DecryptResult.Plain -> {
                    val draft = toDraft(uid, d) ?: return@forEach
                    save(uid, draft.copy(body = dec.body, checklist = dec.checklist, updatedAt = null))
                    upgraded++
                }
                DecryptResult.ForeignLegacy -> foreign++
                else -> Unit
            }
        }
        return upgraded to foreign
    }

    suspend fun countUnprotectedLocked(uid: String): Int = notes.getLocked(uid).count { !NoteCipher.isV2(it.body) }

    companion object {
        const val MAX_TITLE = 200
        const val MAX_COLOR = 7
        const val MAX_REVISIONS = 30
        const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000

        fun contentHash(title: String, body: String, checklist: List<ChecklistDraft>): String {
            val canonical = ArabicNormalizer.normalize(title) + "\u0000" + ArabicNormalizer.normalize(body) + "\u0000" +
                checklist.joinToString("\u0001") { ArabicNormalizer.normalize(it.text) }
            return CryptoPrimitives.sha256Hex(canonical.toByteArray()).take(32)
        }
    }
}
