package com.example.data.legacy

import androidx.room.withTransaction
import com.example.core.text.ArabicNormalizer
import com.example.data.local.ChecklistItemEntity
import com.example.data.local.NoteEntity
import com.example.data.local.NoteTagCrossRef
import com.example.data.local.NoteaDatabase
import com.example.data.local.TagEntity
import com.example.data.repository.Ids
import com.example.data.repository.NoteRepository

/**
 * Expands rows migrated from v1 (`legacyPending = 1`) into the normalized schema:
 * metadata block -> checklist/tags rows, category string -> folder/favourite/trash fields.
 * Idempotent and lossless: free-text extras (old reminder labels, image URIs) are appended to the
 * body instead of being dropped. Sync state is preserved so nothing is re-uploaded unnecessarily.
 */
class LegacyUpgrader(private val db: NoteaDatabase, private val repository: NoteRepository, private val arabicNames: Boolean = true) {

    suspend fun run(): Int {
        val pending = db.noteDao().getLegacyPending()
        pending.forEach { upgrade(it) }
        return pending.size
    }

    private suspend fun upgrade(n: NoteEntity) {
        // Locked v1 notes keep their encrypted body untouched (metadata, if any, lives inside it).
        val parsed = LegacyNoteParser.parse(if (n.isLocked) "" else n.body, n.legacyCategory, n.lockHint)
        var body = if (n.isLocked) n.body else parsed.body
        if (!n.isLocked) {
            val extras = buildList {
                if (parsed.reminderLabel.isNotBlank()) add("⏰ ${parsed.reminderLabel}")
                if (parsed.imageUri.isNotBlank()) add(parsed.imageUri)
            }
            if (extras.isNotEmpty()) body = (body.trimEnd() + "\n\n" + extras.joinToString("\n")).trim()
        }
        val folderId = LegacyNoteParser.legacyFolderNames[parsed.baseCategory]?.let { (ar, en) ->
            val id = LegacyNoteParser.legacyFolderId(parsed.baseCategory)
            repository.ensureFolder(n.userId, id, if (arabicNames) ar else en)
            id
        }
        db.withTransaction {
            val tagDao = db.tagDao()
            val refs = parsed.tags.distinctBy { ArabicNormalizer.normalize(it) }.map { name ->
                val norm = ArabicNormalizer.normalize(name)
                val tag = tagDao.findByNormalized(n.userId, norm) ?: TagEntity(Ids.newId(), n.userId, name, norm).also { tagDao.insert(it) }
                NoteTagCrossRef(n.id, tag.id)
            }
            if (refs.isNotEmpty()) tagDao.insertRefs(refs)
            if (parsed.checklist.isNotEmpty()) {
                db.checklistDao().replaceForNote(n.id, parsed.checklist.mapIndexed { i, c ->
                    ChecklistItemEntity(c.id.ifBlank { Ids.newId() }, n.id, c.text, c.checked, i)
                })
            }
            db.noteDao().upsert(
                n.copy(
                    body = body,
                    folderId = folderId,
                    isFavorite = parsed.isFavorite,
                    trashedAt = parsed.trashedAt,
                    lockHint = parsed.lockHint,
                    colorIndex = n.colorIndex.coerceIn(0, NoteRepository.MAX_COLOR),
                    legacyPending = 0,
                    contentHash = if (n.isLocked) "" else NoteRepository.contentHash(n.title, body, emptyList())
                )
            )
        }
        repository.reindexNote(n.userId, n.id)
    }
}
