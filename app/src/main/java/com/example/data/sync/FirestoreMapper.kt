package com.example.data.sync

import com.example.data.legacy.LegacyNoteParser
import com.example.data.local.FolderEntity
import com.example.data.local.NoteEntity
import com.example.data.local.SyncState
import com.example.data.repository.ChecklistDraft
import com.example.data.repository.Ids
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import java.util.Date

/** A note as it exists in Firestore, normalized across schema v1 and v2 documents. */
data class RemoteNote(
    val note: NoteEntity,
    val checklist: List<ChecklistDraft>,
    val tags: List<String>,
    val reminderAt: Long?,
    val clientUpdatedAt: Long
)

object FirestoreMapper {
    const val SCHEMA_VERSION = 2L

    fun noteToMap(
        n: NoteEntity, checklist: List<ChecklistDraft>, tags: List<String>, reminderAt: Long?,
        folderLegacyKey: String, deviceId: String
    ): Map<String, Any> = mapOf(
        "userId" to n.userId,
        "title" to n.title.ifBlank { "-" }.take(200),
        "content" to n.body,
        // v1-compatible encoding so older app versions keep seeing favourite/trash state.
        "category" to LegacyNoteParser.buildCategory(folderLegacyKey, n.isFavorite, n.trashedAt),
        "colorIndex" to n.colorIndex.toLong(),
        "isPinned" to n.isPinned,
        "isLocked" to n.isLocked,
        "notePinHash" to n.lockPinHash.take(128),
        "lockHint" to n.lockHint.take(100),
        "isArchived" to n.isArchived,
        "createdAt" to Timestamp(Date(n.createdAt)),
        "updatedAt" to FieldValue.serverTimestamp(),
        "schemaVersion" to SCHEMA_VERSION,
        "isFavorite" to n.isFavorite,
        "trashedAt" to (n.trashedAt ?: 0L),
        "folderId" to (n.folderId ?: ""),
        "tags" to tags.take(50),
        "checklist" to checklist.take(500).map { mapOf("id" to it.id, "text" to it.text.take(1000), "checked" to it.checked) },
        "reminderAt" to (reminderAt ?: 0L),
        "clientUpdatedAt" to n.updatedAt,
        "deviceId" to deviceId
    )

    fun noteFromSnapshot(doc: DocumentSnapshot, uid: String): RemoteNote? {
        val data = doc.data ?: return null
        val title = data["title"] as? String ?: ""
        val content = data["content"] as? String ?: ""
        val category = data["category"] as? String ?: "personal"
        val lockHint = data["lockHint"] as? String ?: ""
        val isLocked = data["isLocked"] as? Boolean ?: false
        val createdAt = (data["createdAt"] as? Timestamp)?.toDate()?.time
        val serverUpdated = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
        val schema = (data["schemaVersion"] as? Number)?.toLong() ?: 1L
        val clientUpdatedAt = (data["clientUpdatedAt"] as? Number)?.toLong() ?: serverUpdated ?: createdAt ?: 0L

        return if (schema >= 2L) {
            @Suppress("UNCHECKED_CAST")
            val checklist = (data["checklist"] as? List<Map<String, Any?>>).orEmpty().map {
                ChecklistDraft((it["id"] as? String).orEmpty().ifBlank { Ids.newId() }, it["text"] as? String ?: "", it["checked"] as? Boolean ?: false)
            }
            @Suppress("UNCHECKED_CAST")
            val tags = (data["tags"] as? List<String>).orEmpty()
            val trashedAt = (data["trashedAt"] as? Number)?.toLong()?.takeIf { it > 0 }
            RemoteNote(
                note = NoteEntity(
                    id = doc.id, userId = uid, title = title, body = content,
                    colorIndex = ((data["colorIndex"] as? Number)?.toInt() ?: 0),
                    folderId = (data["folderId"] as? String)?.takeIf { it.isNotBlank() },
                    isPinned = data["isPinned"] as? Boolean ?: false,
                    isFavorite = data["isFavorite"] as? Boolean ?: false,
                    isArchived = data["isArchived"] as? Boolean ?: false,
                    trashedAt = trashedAt,
                    isLocked = isLocked,
                    lockPinHash = data["notePinHash"] as? String ?: "",
                    lockHint = lockHint,
                    createdAt = createdAt ?: clientUpdatedAt,
                    updatedAt = clientUpdatedAt,
                    syncState = SyncState.CLEAN,
                    baseRemoteUpdatedAt = clientUpdatedAt,
                    legacyCategory = category
                ),
                checklist = checklist,
                tags = tags,
                reminderAt = (data["reminderAt"] as? Number)?.toLong()?.takeIf { it > 0 },
                clientUpdatedAt = clientUpdatedAt
            )
        } else {
            val p = LegacyNoteParser.parse(if (isLocked) "" else content, category, lockHint)
            val folderId = LegacyNoteParser.legacyFolderNames[p.baseCategory]?.let { LegacyNoteParser.legacyFolderId(p.baseCategory) }
            RemoteNote(
                note = NoteEntity(
                    id = doc.id, userId = uid, title = title, body = if (isLocked) content else p.body,
                    colorIndex = ((data["colorIndex"] as? Number)?.toInt() ?: 0),
                    folderId = folderId,
                    isPinned = data["isPinned"] as? Boolean ?: false,
                    isFavorite = p.isFavorite,
                    isArchived = data["isArchived"] as? Boolean ?: false,
                    trashedAt = p.trashedAt,
                    isLocked = isLocked,
                    lockPinHash = data["notePinHash"] as? String ?: "",
                    lockHint = p.lockHint,
                    createdAt = createdAt ?: clientUpdatedAt,
                    updatedAt = clientUpdatedAt,
                    syncState = SyncState.CLEAN,
                    baseRemoteUpdatedAt = clientUpdatedAt,
                    legacyCategory = category
                ),
                checklist = p.checklist.map { ChecklistDraft(it.id, it.text, it.checked) },
                tags = p.tags,
                reminderAt = null,
                clientUpdatedAt = clientUpdatedAt
            )
        }
    }

    fun folderToMap(f: FolderEntity): Map<String, Any> = mapOf(
        "userId" to f.userId,
        "name" to f.name.take(60).ifBlank { "-" },
        "colorIndex" to f.colorIndex.toLong(),
        "position" to f.position.toLong(),
        "createdAt" to Timestamp(Date(f.createdAt)),
        "clientUpdatedAt" to f.updatedAt,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun folderFromSnapshot(doc: DocumentSnapshot, uid: String): FolderEntity? {
        val d = doc.data ?: return null
        val updated = (d["clientUpdatedAt"] as? Number)?.toLong() ?: 0L
        return FolderEntity(
            id = doc.id, userId = uid, name = d["name"] as? String ?: return null,
            colorIndex = (d["colorIndex"] as? Number)?.toInt() ?: 0,
            position = (d["position"] as? Number)?.toInt() ?: 0,
            createdAt = (d["createdAt"] as? Timestamp)?.toDate()?.time ?: updated,
            updatedAt = updated, syncState = SyncState.CLEAN
        )
    }
}
