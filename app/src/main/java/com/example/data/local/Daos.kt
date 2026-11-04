package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Transaction
    @Query("SELECT * FROM notes WHERE userId = :userId AND deleted = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun observeNotes(userId: String): Flow<List<NoteWithDetails>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeNote(id: String): Flow<NoteWithDetails?>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteWithDetails(id: String): NoteWithDetails?

    @Transaction
    @Query("SELECT * FROM notes WHERE userId = :userId AND deleted = 0")
    suspend fun getAllWithDetails(userId: String): List<NoteWithDetails>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNote(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE userId = :userId")
    suspend fun getAllIncludingDeleted(userId: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE userId = :userId AND syncState = 1")
    suspend fun getDirty(userId: String): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes WHERE userId = :userId AND syncState = 1")
    fun observeDirtyCount(userId: String): Flow<Int>

    @Query("SELECT * FROM notes WHERE legacyPending = 1")
    suspend fun getLegacyPending(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE userId = :userId AND isLocked = 1 AND deleted = 0")
    suspend fun getLocked(userId: String): List<NoteEntity>

    @Query("SELECT id, contentHash, title FROM notes WHERE userId = :userId AND deleted = 0")
    suspend fun getHashes(userId: String): List<NoteHashRow>

    @Query("SELECT * FROM notes WHERE userId = :userId AND trashedAt IS NOT NULL AND trashedAt < :before AND deleted = 0")
    suspend fun getExpiredTrash(userId: String, before: Long): List<NoteEntity>

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Upsert
    suspend fun upsertAll(notes: List<NoteEntity>)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("UPDATE notes SET syncState = 0, baseRemoteUpdatedAt = :remoteUpdatedAt WHERE id = :id AND updatedAt = :expectedUpdatedAt")
    suspend fun markClean(id: String, expectedUpdatedAt: Long, remoteUpdatedAt: Long): Int

    @Query("SELECT DISTINCT userId FROM notes")
    suspend fun knownUserIds(): List<String>
}

data class NoteHashRow(val id: String, val contentHash: String, val title: String)

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklist_items WHERE noteId = :noteId ORDER BY position")
    suspend fun forNote(noteId: String): List<ChecklistItemEntity>

    @Query("DELETE FROM checklist_items WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChecklistItemEntity>)

    @Transaction
    suspend fun replaceForNote(noteId: String, items: List<ChecklistItemEntity>) {
        deleteForNote(noteId)
        if (items.isNotEmpty()) insertAll(items)
    }
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags WHERE userId = :userId ORDER BY name COLLATE NOCASE")
    fun observeTags(userId: String): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE userId = :userId")
    suspend fun allTags(userId: String): List<TagEntity>

    @Query("SELECT * FROM tags WHERE userId = :userId AND normalized = :normalized LIMIT 1")
    suspend fun findByNormalized(userId: String, normalized: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun clearNoteTags(noteId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRefs(refs: List<NoteTagCrossRef>)

    @Query("SELECT tagId, COUNT(*) AS count FROM note_tags nt JOIN notes n ON n.id = nt.noteId WHERE n.userId = :userId AND n.deleted = 0 AND n.trashedAt IS NULL GROUP BY tagId")
    fun observeTagUsage(userId: String): Flow<List<TagUsage>>

    @Query("DELETE FROM tags WHERE userId = :userId AND id NOT IN (SELECT tagId FROM note_tags)")
    suspend fun deleteUnused(userId: String)

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun delete(tagId: String)
}

data class TagUsage(val tagId: String, val count: Int)

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE userId = :userId AND deleted = 0 ORDER BY position, name COLLATE NOCASE")
    fun observeFolders(userId: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE userId = :userId")
    suspend fun allIncludingDeleted(userId: String): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun get(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE userId = :userId AND syncState = 1")
    suspend fun getDirty(userId: String): List<FolderEntity>

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("UPDATE folders SET syncState = 0 WHERE id = :id AND updatedAt = :expectedUpdatedAt")
    suspend fun markClean(id: String, expectedUpdatedAt: Long)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY position, createdAt")
    suspend fun forNote(noteId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun get(id: String): AttachmentEntity?

    @Query("SELECT * FROM attachments WHERE userId = :userId")
    suspend fun allForUser(userId: String): List<AttachmentEntity>

    @Upsert
    suspend fun upsert(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE status != 2")
    suspend fun allActive(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE userId = :userId AND status != 2 ORDER BY triggerAt")
    fun observeActive(userId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE noteId = :noteId")
    suspend fun forNote(noteId: String): List<ReminderEntity>

    @Upsert
    suspend fun upsert(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM reminders WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: String)
}

@Dao
interface RevisionDao {
    @Query("SELECT * FROM note_revisions WHERE noteId = :noteId ORDER BY createdAt DESC")
    fun observeForNote(noteId: String): Flow<List<RevisionEntity>>

    @Query("SELECT * FROM note_revisions WHERE noteId = :noteId ORDER BY createdAt DESC")
    suspend fun forNote(noteId: String): List<RevisionEntity>

    @Query("SELECT * FROM note_revisions WHERE noteId = :noteId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(noteId: String): RevisionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rev: RevisionEntity)

    @Query("DELETE FROM note_revisions WHERE noteId = :noteId AND id NOT IN (SELECT id FROM note_revisions WHERE noteId = :noteId ORDER BY createdAt DESC LIMIT :keep)")
    suspend fun trim(noteId: String, keep: Int)
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates WHERE userId = :userId ORDER BY createdAt DESC")
    fun observe(userId: String): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE userId = :userId")
    suspend fun all(userId: String): List<TemplateEntity>

    @Upsert
    suspend fun upsert(t: TemplateEntity)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface SearchDao {
    @Query("DELETE FROM notes_fts WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: String)

    @Insert
    suspend fun insert(row: NoteFtsEntity)

    @Transaction
    suspend fun replace(row: NoteFtsEntity) {
        deleteForNote(row.noteId)
        insert(row)
    }

    @Query("SELECT noteId FROM notes_fts WHERE userId = :userId AND notes_fts MATCH :match")
    suspend fun search(userId: String, match: String): List<String>

    @Query("SELECT COUNT(*) FROM notes_fts")
    suspend fun count(): Int
}
