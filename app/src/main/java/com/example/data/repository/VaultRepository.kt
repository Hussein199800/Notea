package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.local.ArabicNotesDatabase
import com.example.data.local.NoteDao
import com.example.data.local.NoteEntity
import com.example.data.local.SyncConflictResolver
import com.example.data.model.Note
import com.example.data.model.UserSettings
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await

class VaultRepository(
    private val db: FirebaseFirestore,
    private val noteDao: NoteDao? = null
) {

    // CRITICAL: Always initialize with R.string.firestore_database_id
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        ArabicNotesDatabase.getInstance(context.applicationContext).noteDao()
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeNotes(userId: String): Flow<List<Note>> {
        val path = "users/$userId/notes"
        return db.collection("users").document(userId).collection("notes")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                val remoteNotes = snapshot.toObjects(
                    Note::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                remoteNotes.sortedWith(
                    compareByDescending<Note> { it.isPinned }
                        .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                )
            }
            .onEach { remoteNotes ->
                val dao = noteDao ?: return@onEach
                try {
                    val localEntities = dao.getNotesForUser(userId)
                    val remoteEntities = remoteNotes.map { NoteEntity.fromDomainNote(it, pendingSync = false) }
                    val merged = SyncConflictResolver.mergeLists(localEntities, remoteEntities)
                    dao.upsertNotes(merged)
                } catch (_: Exception) {
                    // Best-effort local Room mirror sync
                }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun getCachedLocalNotes(userId: String): List<Note> {
        val dao = noteDao ?: return emptyList()
        return try {
            dao.getNotesForUser(userId).map { it.toDomainNote() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun observeUserSettings(userId: String): Flow<UserSettings?> {
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserSettings::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.GET, path)
                }
                throw error
            }
    }

    suspend fun getNoteById(ownerUserId: String, noteId: String): Result<Note?> {
        val docRef = db.collection("users").document(ownerUserId).collection("notes").document(noteId)
        return try {
            val snap = docRef.get().await()
            Result.success(
                snap.toObject(Note::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createNote(
        title: String,
        content: String,
        category: String = "personal",
        colorIndex: Int = 0,
        isPinned: Boolean = false,
        isLocked: Boolean = false,
        notePinHash: String = "",
        lockHint: String = "",
        isArchived: Boolean = false
    ): Result<String> {
        val uid = requireUserId()
        val notesCol = db.collection("users").document(uid).collection("notes")
        val docRef = notesCol.document()
        val safeTitle = title.trim().ifBlank { "Untitled Note" }.take(200)
        val safeContent = content.take(20000)
        val safeCategory = category.trim().ifBlank { "personal" }.take(50)
        val safeColor = colorIndex.coerceIn(0, 5)
        val safePinHash = notePinHash.take(128)
        val safeHint = lockHint.trim().take(100)

        // Optimistic offline-first Room write
        val nowMs = System.currentTimeMillis()
        noteDao?.let { dao ->
            try {
                dao.upsertNote(
                    NoteEntity(
                        id = docRef.id,
                        userId = uid,
                        title = safeTitle,
                        content = safeContent,
                        category = safeCategory,
                        colorIndex = safeColor,
                        isPinned = isPinned,
                        isLocked = isLocked,
                        notePinHash = safePinHash,
                        lockHint = safeHint,
                        isArchived = isArchived,
                        createdAtMillis = nowMs,
                        updatedAtMillis = nowMs,
                        pendingSync = true
                    )
                )
            } catch (_: Exception) {}
        }

        val payload = mapOf(
            "userId" to uid,
            "title" to safeTitle,
            "content" to safeContent,
            "category" to safeCategory,
            "colorIndex" to safeColor,
            "isPinned" to isPinned,
            "isLocked" to isLocked,
            "notePinHash" to safePinHash,
            "lockHint" to safeHint,
            "isArchived" to isArchived,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateNote(note: Note): Result<Unit> {
        val uid = requireUserId()
        require(note.id.isNotBlank()) { "Note ID must not be blank for update" }
        val docRef = db.collection("users").document(uid).collection("notes").document(note.id)

        val safeTitle = note.title.trim().ifBlank { "Untitled Note" }.take(200)
        val safeContent = note.content.take(20000)
        val safeCategory = note.category.trim().ifBlank { "personal" }.take(50)
        val safeColor = note.colorIndex.toInt().coerceIn(0, 5)
        val safePinHash = note.notePinHash.take(128)
        val safeHint = note.lockHint.trim().take(100)

        noteDao?.let { dao ->
            try {
                dao.upsertNote(
                    NoteEntity.fromDomainNote(
                        note.copy(
                            title = safeTitle,
                            content = safeContent,
                            category = safeCategory,
                            colorIndex = safeColor.toLong(),
                            notePinHash = safePinHash,
                            lockHint = safeHint
                        ),
                        pendingSync = true
                    )
                )
            } catch (_: Exception) {}
        }

        val updates = mapOf(
            "title" to safeTitle,
            "content" to safeContent,
            "category" to safeCategory,
            "colorIndex" to safeColor,
            "isPinned" to note.isPinned,
            "isLocked" to note.isLocked,
            "notePinHash" to safePinHash,
            "lockHint" to safeHint,
            "isArchived" to note.isArchived,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(noteId: String): Result<Unit> {
        val uid = requireUserId()
        require(noteId.isNotBlank()) { "Note ID must not be blank for delete" }
        val docRef = db.collection("users").document(uid).collection("notes").document(noteId)
        noteDao?.let { dao ->
            try {
                dao.deleteNoteById(noteId)
            } catch (_: Exception) {}
        }
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveUserSettings(
        appLockEnabled: Boolean,
        appPinHash: String,
        languageCode: String
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val safeLang = if (languageCode == "ar" || languageCode == "en") languageCode else "ar"
        val payload = mapOf(
            "userId" to uid,
            "appLockEnabled" to appLockEnabled,
            "appPinHash" to appPinHash.take(128),
            "languageCode" to safeLang,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }
}
