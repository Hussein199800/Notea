package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

object SyncState {
    const val CLEAN = 0
    const val DIRTY = 1
}

@Entity(
    tableName = "notes",
    indices = [Index("userId"), Index("folderId"), Index(value = ["userId", "syncState"])]
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    /** Markdown-flavoured body, or an `ENCv2:`/`ENCv1:` payload when [isLocked]. */
    val body: String,
    val colorIndex: Int = 0,
    val folderId: String? = null,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    /** Non-null when the note is in the trash (epoch millis). */
    val trashedAt: Long? = null,
    val isLocked: Boolean = false,
    val lockPinHash: String = "",
    val lockHint: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: Int = SyncState.DIRTY,
    /** Tombstone: deleted locally, waiting for the remote delete to be confirmed. */
    val deleted: Boolean = false,
    /** Remote `clientUpdatedAt` this local copy was last reconciled with (conflict detection). */
    val baseRemoteUpdatedAt: Long = 0L,
    /** Original v1 `category` string, kept so older app versions keep working with synced docs. */
    val legacyCategory: String = "",
    /** 1 = row came from the v1 schema and still needs [com.example.data.legacy.LegacyUpgrader]. */
    val legacyPending: Int = 0,
    val importSource: String = "",
    val contentHash: String = ""
)

@Entity(
    tableName = "checklist_items",
    foreignKeys = [ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("noteId")]
)
data class ChecklistItemEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val text: String,
    val checked: Boolean,
    val position: Int
)

@Entity(tableName = "tags", indices = [Index(value = ["userId", "normalized"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val normalized: String
)

@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("tagId")]
)
data class NoteTagCrossRef(val noteId: String, val tagId: String)

@Entity(tableName = "folders", indices = [Index("userId")])
data class FolderEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val colorIndex: Int = 0,
    val position: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: Int = SyncState.DIRTY,
    val deleted: Boolean = false
)

object AttachmentKind {
    const val IMAGE = "image"
    const val AUDIO = "audio"
    const val PDF = "pdf"
    const val FILE = "file"
    const val LINK = "link"
}

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("noteId"), Index("userId")]
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val userId: String,
    val kind: String,
    val mimeType: String = "",
    val displayName: String = "",
    /** Path relative to the user's attachment directory; empty for links. */
    val localPath: String = "",
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val url: String = "",
    val linkTitle: String = "",
    val linkDescription: String = "",
    /** Text extracted at import time (searchable). Empty when not extractable. */
    val extractedText: String = "",
    /** File on disk is encrypted with the vault master key (attachments of locked notes). */
    val encrypted: Boolean = false,
    val createdAt: Long,
    val position: Int = 0
)

object ReminderStatus {
    const val SCHEDULED = 0
    const val FIRED = 1
    const val DONE = 2
}

@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("noteId"), Index("userId")]
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val userId: String,
    val triggerAt: Long,
    val status: Int = ReminderStatus.SCHEDULED,
    val createdAt: Long
)

object RevisionReason {
    const val AUTO = "auto"
    const val BEFORE_RESTORE = "before_restore"
    const val CONFLICT = "conflict"
    const val IMPORT_MERGE = "import_merge"
}

@Entity(
    tableName = "note_revisions",
    foreignKeys = [ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("noteId")]
)
data class RevisionEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val title: String,
    val body: String,
    /** JSON array of {text, checked}. */
    val checklistJson: String,
    val createdAt: Long,
    val reason: String
)

@Entity(tableName = "templates", indices = [Index("userId")])
data class TemplateEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val title: String,
    val body: String,
    val checklistJson: String,
    val createdAt: Long
)

/** Full-text index of *normalized* text. Locked notes are indexed by title only. */
@Fts4(notIndexed = ["noteId", "userId"])
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(
    val noteId: String,
    val userId: String,
    val title: String,
    val body: String,
    val tags: String,
    @ColumnInfo(name = "extra") val extra: String
)

data class NoteWithDetails(
    @Embedded val note: NoteEntity,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val checklist: List<ChecklistItemEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(NoteTagCrossRef::class, parentColumn = "noteId", entityColumn = "tagId")
    )
    val tags: List<TagEntity>,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val attachments: List<AttachmentEntity>,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val reminders: List<ReminderEntity>
) {
    val sortedChecklist get() = checklist.sortedBy { it.position }
    val activeReminder get() = reminders.filter { it.status != ReminderStatus.DONE }.minByOrNull { it.triggerAt }
}

/** Legacy v1 table kept read-only after migration as a safety copy. */
@Entity(tableName = "local_notes")
data class LegacyNoteEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val content: String,
    val category: String,
    val colorIndex: Int,
    val isPinned: Boolean,
    val isLocked: Boolean,
    val notePinHash: String,
    val lockHint: String,
    val isArchived: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val pendingSync: Boolean = false
)
