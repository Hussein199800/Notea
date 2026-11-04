package com.example.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.core.security.NoteCipher
import com.example.core.testVault
import com.example.data.inMemoryDb
import com.example.data.local.RevisionReason
import com.example.data.local.SyncState
import com.example.data.repository.AttachmentStore
import com.example.data.repository.NoteDraft
import com.example.data.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.net.Socket

/**
 * Runs only when the Firestore (8085) and Auth (9099) emulators are up:
 * `firebase emulators:exec --only firestore,auth "./gradlew :app:testDebugUnitTest --tests '*SyncEmulatorTest'"`
 */
class SyncEmulatorTest : FirestoreEmulatorTestBase() {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    private fun up(port: Int) = runCatching { Socket("127.0.0.1", port).close() }.isSuccess

    @Before override fun setUpFirebase() {
        org.robolectric.shadows.ShadowLog.stream = System.out
        assumeTrue("emulators not running", up(8085) && up(9099))
        super.setUpFirebase()
    }

    private inner class Device(name: String) {
        val db = inMemoryDb()
        val vault = testVault("vault_$name")
        val repo = NoteRepository(db, vault, AttachmentStore(ctx))
        val engine = SyncEngine(firestore, db, repo, vault, name)
        suspend fun pull(uid: String, id: String) = withContext(Dispatchers.IO) {
            val snap = firestore.collection("users").document(uid).collection("notes").document(id).get().await()
            engine.handleRemoteNote(uid, FirestoreMapper.noteFromSnapshot(snap, uid)!!)
        }
    }

    @Test fun offlineEdits_push_pull_andLosslessConflict() = runBlocking {
        val uid = signInTestUser("sync-${System.nanoTime()}@example.com")
        val a = Device("A"); val b = Device("B")

        // Created while "offline": only Room is touched, the row waits in the queue.
        a.repo.save(uid, NoteDraft(id = "syncnote1", title = "ملاحظة", body = "نسخة أ", tags = listOf("عمل")))
        assertEquals(SyncState.DIRTY, a.db.noteDao().getNote("syncnote1")!!.syncState)
        assertTrue(withContext(Dispatchers.IO) { a.engine.pushAll(uid) })
        assertEquals(SyncState.CLEAN, a.db.noteDao().getNote("syncnote1")!!.syncState)

        b.pull(uid, "syncnote1")
        val onB = b.db.noteDao().getNoteWithDetails("syncnote1")!!
        assertEquals("نسخة أ", onB.note.body); assertEquals("عمل", onB.tags.single().name)

        // Both devices edit the same note; B edits last.
        a.repo.save(uid, a.repo.toDraft(uid, a.db.noteDao().getNoteWithDetails("syncnote1")!!)!!.copy(body = "تعديل أ", updatedAt = null))
        Thread.sleep(5)
        b.repo.save(uid, b.repo.toDraft(uid, onB)!!.copy(body = "تعديل ب", updatedAt = null))
        assertTrue(withContext(Dispatchers.IO) { a.engine.pushAll(uid) })
        assertTrue(withContext(Dispatchers.IO) { b.engine.pushAll(uid) })
        // B's newer edit wins; A's edit is preserved in history on B.
        assertEquals("تعديل ب", b.db.noteDao().getNote("syncnote1")!!.body)
        val conflict = b.db.revisionDao().forNote("syncnote1").filter { it.reason == RevisionReason.CONFLICT }
        assertTrue(conflict.any { it.body == "تعديل أ" })
        a.pull(uid, "syncnote1")
        assertEquals("تعديل ب", a.db.noteDao().getNote("syncnote1")!!.body)
    }

    @Test fun lockedNote_onlyCiphertextReachesFirestore() = runBlocking {
        val uid = signInTestUser("lock-${System.nanoTime()}@example.com")
        val a = Device("L")
        a.vault.setUp(uid, "vault-passphrase".toCharArray())
        a.repo.save(uid, NoteDraft(id = "locked1", title = "سري", body = "PLAINTEXT-SECRET", isLocked = true))
        assertTrue(withContext(Dispatchers.IO) { a.engine.pushAll(uid) })
        val doc = withContext(Dispatchers.IO) { firestore.collection("users").document(uid).collection("notes").document("locked1").get().await() }
        val content = doc.getString("content")!!
        assertTrue(NoteCipher.isV2(content)); assertFalse(doc.data.toString().contains("PLAINTEXT-SECRET"))
    }
}
