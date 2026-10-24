package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.Note
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

@Entity(tableName = "local_notes")
data class NoteEntity(
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
) {
    fun toDomainNote(): Note = Note(
        id = id,
        userId = userId,
        title = title,
        content = content,
        category = category,
        colorIndex = colorIndex.toLong(),
        isPinned = isPinned,
        isLocked = isLocked,
        notePinHash = notePinHash,
        lockHint = lockHint,
        isArchived = isArchived,
        createdAt = Timestamp(Date(createdAtMillis)),
        updatedAt = Timestamp(Date(updatedAtMillis))
    )

    companion object {
        fun fromDomainNote(note: Note, pendingSync: Boolean = false): NoteEntity {
            val now = System.currentTimeMillis()
            val createdMs = note.createdAt?.toDate()?.time ?: now
            val updatedMs = note.updatedAt?.toDate()?.time ?: createdMs
            return NoteEntity(
                id = note.id,
                userId = note.userId,
                title = note.title,
                content = note.content,
                category = note.category,
                colorIndex = note.colorIndex.toInt(),
                isPinned = note.isPinned,
                isLocked = note.isLocked,
                notePinHash = note.notePinHash,
                lockHint = note.lockHint,
                isArchived = note.isArchived,
                createdAtMillis = createdMs,
                updatedAtMillis = updatedMs,
                pendingSync = pendingSync
            )
        }
    }
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM local_notes WHERE userId = :userId ORDER BY isPinned DESC, updatedAtMillis DESC")
    fun observeNotesForUser(userId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM local_notes WHERE userId = :userId")
    suspend fun getNotesForUser(userId: String): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotes(notes: List<NoteEntity>)

    @Query("DELETE FROM local_notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: String)

    @Query("DELETE FROM local_notes WHERE userId = :userId")
    suspend fun clearNotesForUser(userId: String)
}

@Database(entities = [NoteEntity::class], version = 1, exportSchema = false)
abstract class ArabicNotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: ArabicNotesDatabase? = null

        fun getInstance(context: Context): ArabicNotesDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ArabicNotesDatabase::class.java,
                    "arabic_notes_offline.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

/**
 * Two-way sync conflict resolution based on last-modified timestamp and unique note UUID.
 */
object SyncConflictResolver {
    fun resolveWinner(local: NoteEntity, remote: NoteEntity): NoteEntity {
        require(local.id == remote.id) { "Cannot resolve conflict between different note UUIDs" }
        return if (local.updatedAtMillis > remote.updatedAtMillis) {
            local
        } else {
            remote.copy(pendingSync = false)
        }
    }

    fun mergeLists(localList: List<NoteEntity>, remoteList: List<NoteEntity>): List<NoteEntity> {
        val mergedMap = LinkedHashMap<String, NoteEntity>()
        for (local in localList) {
            mergedMap[local.id] = local
        }
        for (remote in remoteList) {
            val existingLocal = mergedMap[remote.id]
            mergedMap[remote.id] = if (existingLocal != null) {
                resolveWinner(existingLocal, remote)
            } else {
                remote
            }
        }
        return mergedMap.values.sortedWith(
            compareByDescending<NoteEntity> { it.isPinned }
                .thenByDescending { it.updatedAtMillis }
        )
    }
}

/**
 * Export and import notes as JSON or formatted plain-text backup.
 */
object NoteBackupSerializer {
    fun exportToJson(notes: List<Note>): String {
        val array = JSONArray()
        for (note in notes) {
            val obj = JSONObject().apply {
                put("id", note.id)
                put("title", note.title)
                put("content", note.content)
                put("category", note.category)
                put("colorIndex", note.colorIndex)
                put("isPinned", note.isPinned)
                put("isLocked", note.isLocked)
                put("notePinHash", note.notePinHash)
                put("lockHint", note.lockHint)
                put("isArchived", note.isArchived)
                put("createdAtMillis", note.createdAt?.toDate()?.time ?: System.currentTimeMillis())
                put("updatedAtMillis", note.updatedAt?.toDate()?.time ?: System.currentTimeMillis())
            }
            array.put(obj)
        }
        return array.toString(2)
    }

    fun exportToPlainText(notes: List<Note>, isArabic: Boolean): String {
        val header = if (isArabic) "=== نسخة احتياطية - ملاحظاتي العربية ===" else "=== Arabic Notes Backup ==="
        return buildString {
            appendLine(header)
            appendLine()
            notes.forEachIndexed { idx, note ->
                appendLine("${idx + 1}. ${note.title} [${note.category}]")
                if (note.isLocked) {
                    appendLine(if (isArabic) "(ملاحظة مقفلة ومشفرة)" else "(Locked & Encrypted Note)")
                } else {
                    appendLine(note.cleanBodyText())
                }
                appendLine("----------------------------------------")
            }
        }
    }

    fun importFromJson(jsonString: String, currentUserId: String): List<Note> {
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) return emptyList()
        val array = JSONArray(trimmed)
        val imported = mutableListOf<Note>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val createdMs = obj.optLong("createdAtMillis", System.currentTimeMillis())
            val updatedMs = obj.optLong("updatedAtMillis", createdMs)
            imported.add(
                Note(
                    id = obj.optString("id", ""),
                    userId = currentUserId,
                    title = obj.optString("title", "Imported Note").take(200),
                    content = obj.optString("content", "").take(20000),
                    category = obj.optString("category", "personal").take(50),
                    colorIndex = obj.optLong("colorIndex", 0L).coerceIn(0L, 5L),
                    isPinned = obj.optBoolean("isPinned", false),
                    isLocked = obj.optBoolean("isLocked", false),
                    notePinHash = obj.optString("notePinHash", "").take(128),
                    lockHint = obj.optString("lockHint", "").take(100),
                    isArchived = obj.optBoolean("isArchived", false),
                    createdAt = Timestamp(Date(createdMs)),
                    updatedAt = Timestamp(Date(updatedMs))
                )
            )
        }
        return imported
    }
}
