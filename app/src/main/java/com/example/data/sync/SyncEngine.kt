package com.example.data.sync

import android.util.Log
import com.example.core.security.KeyVault
import com.example.core.security.VaultEnvelope
import com.example.data.legacy.LegacyNoteParser
import com.example.data.local.NoteEntity
import com.example.data.local.NoteaDatabase
import com.example.data.local.RevisionReason
import com.example.data.local.SyncState
import com.example.data.repository.NoteRepository
import com.example.data.repository.toDrafts
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Two-way sync between Room (source of truth) and Firestore.
 *
 * Push: dirty rows are uploaded by [SyncWorker] (WorkManager, network constraint, exponential
 * backoff). Pull: a realtime listener applies remote changes while the app is open.
 *
 * Conflicts (both sides changed since the last common version) never lose data: the newer edit
 * wins and the other version is stored in the note's revision history with reason "conflict".
 */
class SyncEngine(
    private val firestore: FirebaseFirestore,
    private val db: NoteaDatabase,
    private val repository: NoteRepository,
    private val keyVault: KeyVault,
    private val deviceId: String
) {
    private val mutex = Mutex()
    private var noteListener: ListenerRegistration? = null
    private var folderListener: ListenerRegistration? = null
    private var listeningUid: String? = null

    private fun userDoc(uid: String) = firestore.collection("users").document(uid)
    private fun notesCol(uid: String) = userDoc(uid).collection("notes")
    private fun foldersCol(uid: String) = userDoc(uid).collection("folders")
    private fun vaultDoc(uid: String) = userDoc(uid).collection("vault").document("key")

    // ------------------------------------------------------------------ push
    /** Uploads every dirty row. Returns true when everything was pushed. */
    suspend fun pushAll(uid: String): Boolean = mutex.withLock {
        var ok = true
        for (f in db.folderDao().getDirty(uid)) {
            try {
                withTimeout(TIMEOUT) {
                    val doc = foldersCol(uid).document(f.id)
                    if (f.deleted) doc.delete().await()
                    else if (doc.get().await().exists()) doc.set(FirestoreMapper.folderToMap(f) - "createdAt", SetOptions.merge()).await()
                    else doc.set(FirestoreMapper.folderToMap(f)).await()
                }
                if (f.deleted) db.folderDao().hardDelete(f.id) else db.folderDao().markClean(f.id, f.updatedAt)
            } catch (e: Exception) {
                Log.w(TAG, "folder push failed ${f.id}", e); ok = false
            }
        }
        for (n in db.noteDao().getDirty(uid)) {
            try {
                pushNote(uid, n)
            } catch (e: Exception) {
                Log.w(TAG, "note push failed ${n.id}", e); ok = false
            }
        }
        ok
    }

    private suspend fun pushNote(uid: String, n: NoteEntity) {
        val ref = notesCol(uid).document(n.id)
        if (n.deleted) {
            withTimeout(TIMEOUT) { ref.delete().await() }
            db.noteDao().hardDelete(n.id)
            return
        }
        // Conflict check against the server copy.
        val remoteSnap = withTimeout(TIMEOUT) { ref.get(com.google.firebase.firestore.Source.SERVER).await() }
        val remote = if (remoteSnap.exists()) FirestoreMapper.noteFromSnapshot(remoteSnap, uid) else null
        if (remote != null && remote.clientUpdatedAt > n.baseRemoteUpdatedAt && remote.clientUpdatedAt != n.updatedAt) {
            if (remote.clientUpdatedAt > n.updatedAt) {
                // Remote is newer: keep it, preserve our local edit as a revision.
                keepAsRevision(n)
                applyRemote(uid, remote)
                return
            } else {
                // Ours is newer: preserve the remote edit as a revision, then upload ours.
                repository.addRevision(n.id, remote.note.title, remote.note.body, remote.checklist, RevisionReason.CONFLICT, remote.clientUpdatedAt)
            }
        }
        val d = db.noteDao().getNoteWithDetails(n.id) ?: return
        val folderKey = n.folderId?.takeIf { it.startsWith("legacy-") }?.removePrefix("legacy-") ?: "personal"
        val map = FirestoreMapper.noteToMap(
            n, d.checklist.toDrafts(), d.tags.map { it.name }, d.activeReminder?.triggerAt, folderKey, deviceId
        )
        withTimeout(TIMEOUT) {
            // createdAt is immutable server-side and v1 docs hold microsecond server timestamps:
            // never resend it on update (merge keeps the stored value).
            if (remote == null) ref.set(map).await() else ref.set(map - "createdAt", SetOptions.merge()).await()
        }
        db.noteDao().markClean(n.id, n.updatedAt, n.updatedAt)
    }

    private suspend fun keepAsRevision(local: NoteEntity) {
        val d = db.noteDao().getNoteWithDetails(local.id) ?: return
        repository.addRevision(local.id, local.title, local.body, d.checklist.toDrafts(), RevisionReason.CONFLICT, local.updatedAt)
    }

    // ------------------------------------------------------------------ pull
    private suspend fun applyRemote(uid: String, remote: RemoteNote) {
        remote.note.folderId?.takeIf { it.startsWith("legacy-") }?.let { id ->
            val key = id.removePrefix("legacy-")
            LegacyNoteParser.legacyFolderNames[key]?.let { repository.ensureFolder(uid, id, it.first) }
        }
        repository.applyStored(uid, remote.note, remote.checklist, remote.tags, remote.reminderAt)
    }

    suspend fun handleRemoteNote(uid: String, remote: RemoteNote) = mutex.withLock {
        val local = db.noteDao().getNote(remote.note.id)
        when {
            local == null -> applyRemote(uid, remote)
            local.deleted -> Unit // our delete wins; pushed by the worker
            remote.clientUpdatedAt == local.updatedAt -> db.noteDao().markClean(local.id, local.updatedAt, remote.clientUpdatedAt)
            local.syncState == SyncState.CLEAN -> if (remote.clientUpdatedAt != local.baseRemoteUpdatedAt) applyRemote(uid, remote)
            remote.clientUpdatedAt > local.baseRemoteUpdatedAt -> {
                // Both changed: lossless resolution.
                if (remote.clientUpdatedAt > local.updatedAt) {
                    keepAsRevision(local)
                    applyRemote(uid, remote)
                } else {
                    repository.addRevision(local.id, remote.note.title, remote.note.body, remote.checklist, RevisionReason.CONFLICT, remote.clientUpdatedAt)
                    db.noteDao().upsert(local.copy(baseRemoteUpdatedAt = remote.clientUpdatedAt))
                }
            }
            else -> Unit // local dirty, remote unchanged since base: our push will win
        }
    }

    private suspend fun handleRemoteRemoved(id: String) = mutex.withLock {
        val local = db.noteDao().getNote(id) ?: return@withLock
        if (local.syncState == SyncState.CLEAN) repository.removeLocalCopy(local.userId, id)
    }

    /** Starts realtime listeners for [uid] (idempotent). */
    fun startListening(uid: String, scope: CoroutineScope) {
        if (listeningUid == uid) return
        stopListening()
        listeningUid = uid
        var firstServerSnapshot = true
        noteListener = notesCol(uid).whereEqualTo("userId", uid)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snap, err ->
                if (err != null || snap == null) {
                    Log.w(TAG, "notes listener error", err); return@addSnapshotListener
                }
                val fromServer = !snap.metadata.isFromCache
                scope.launch {
                    if (firstServerSnapshot && fromServer) {
                        firstServerSnapshot = false
                        // Notes deleted remotely while this device was offline.
                        val remoteIds = snap.documents.map { it.id }.toSet()
                        db.noteDao().getAllIncludingDeleted(uid)
                            .filter { it.syncState == SyncState.CLEAN && it.baseRemoteUpdatedAt > 0 && it.id !in remoteIds }
                            .forEach { handleRemoteRemoved(it.id) }
                    }
                    for (change in snap.documentChanges) {
                        val doc = change.document
                        if (doc.metadata.hasPendingWrites()) continue
                        try {
                            when (change.type) {
                                DocumentChange.Type.REMOVED -> if (fromServer) handleRemoteRemoved(doc.id)
                                else -> FirestoreMapper.noteFromSnapshot(doc, uid)?.let { handleRemoteNote(uid, it) }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "apply remote failed ${doc.id}", e)
                        }
                    }
                }
            }
        folderListener = foldersCol(uid).addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            scope.launch {
                for (change in snap.documentChanges) {
                    val doc = change.document
                    if (doc.metadata.hasPendingWrites()) continue
                    val local = db.folderDao().get(doc.id)
                    if (change.type == DocumentChange.Type.REMOVED) {
                        if (local != null && local.syncState == SyncState.CLEAN) db.folderDao().hardDelete(doc.id)
                        continue
                    }
                    val remote = FirestoreMapper.folderFromSnapshot(doc, uid) ?: continue
                    if (local == null || local.syncState == SyncState.CLEAN || remote.updatedAt > local.updatedAt) {
                        db.folderDao().upsert(remote)
                    }
                }
            }
        }
    }

    fun stopListening() {
        noteListener?.remove(); folderListener?.remove()
        noteListener = null; folderListener = null; listeningUid = null
    }

    // ------------------------------------------------------------------ vault envelope
    suspend fun fetchVaultEnvelope(uid: String): VaultEnvelope? = try {
        val snap = withTimeout(TIMEOUT) { vaultDoc(uid).get().await() }
        snap.data?.let { VaultEnvelope.fromMap(it) }?.also {
            if (keyVault.localEnvelope(uid) == null) keyVault.rememberEnvelope(uid, it)
        }
    } catch (e: Exception) {
        Log.w(TAG, "vault fetch failed", e); null
    }

    sealed interface RemoteVault {
        data class Exists(val envelope: VaultEnvelope) : RemoteVault
        data object Missing : RemoteVault
        data object Unknown : RemoteVault
    }

    /** Server-confirmed vault lookup (cache is not trusted for this decision). */
    suspend fun checkRemoteVault(uid: String): RemoteVault = try {
        val snap = withTimeout(15_000) { vaultDoc(uid).get(com.google.firebase.firestore.Source.SERVER).await() }
        snap.data?.let { VaultEnvelope.fromMap(it) }?.let { RemoteVault.Exists(it) } ?: RemoteVault.Missing
    } catch (e: Exception) {
        RemoteVault.Unknown
    }

    suspend fun uploadVaultEnvelope(uid: String, env: VaultEnvelope): Boolean = try {
        withTimeout(TIMEOUT) { vaultDoc(uid).set(env.toMap() + ("userId" to uid)).await() }
        true
    } catch (e: Exception) {
        Log.w(TAG, "vault upload failed", e); false
    }

    // ------------------------------------------------------------------ settings
    suspend fun pushSettings(uid: String, appLockEnabled: Boolean, appPinHash: String, languageCode: String) {
        try {
            withTimeout(TIMEOUT) {
                userDoc(uid).set(
                    mapOf(
                        "userId" to uid, "appLockEnabled" to appLockEnabled, "appPinHash" to appPinHash.take(128),
                        "languageCode" to if (languageCode == "en") "en" else "ar",
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "settings push failed", e)
        }
    }

    suspend fun fetchSettings(uid: String): Map<String, Any>? = try {
        withTimeout(TIMEOUT) { userDoc(uid).get().await() }.data
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val TAG = "NoteaSync"
        private const val TIMEOUT = 30_000L
    }
}
