package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.core.testVault
import com.example.core.security.NoteCipher
import com.example.data.legacy.LegacyUpgrader
import com.example.data.local.MIGRATION_1_2
import com.example.data.local.NoteaDatabase
import com.example.data.local.SyncState
import com.example.data.repository.AttachmentStore
import com.example.data.repository.ChecklistDraft
import com.example.data.repository.DecryptResult
import com.example.data.repository.NoteDraft
import com.example.data.repository.NoteRepository
import com.example.data.repository.VaultLockedException
import com.example.core.text.ArabicNormalizer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

fun inMemoryDb(): NoteaDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NoteaDatabase::class.java)
    .allowMainThreadQueries().build()

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NoteaDatabase::class.java)

    @Test
    fun v1ToV2_keepsEveryNote_andExpandsLegacyMetadata() = runBlocking {
        helper.createDatabase(dbName, 1).apply {
            val meta = "<!--AN_META_START\nTAGS:عمل,مهم\nREMINDER:غداً\nCHK:1:a1:شراء الخبز\nCHK:0:a2:الاتصال بأحمد\nAN_META_END-->\nنص الملاحظة"
            execSQL("INSERT INTO local_notes VALUES ('n1','alice','قائمة',?,'work|fav',2,1,0,'','',0,1000,2000,0)", arrayOf(meta))
            execSQL("INSERT INTO local_notes VALUES ('n2','alice','محذوفة','نص','trash:1700000000000|ideas',0,0,0,'','',0,1000,3000,1)")
            execSQL("INSERT INTO local_notes VALUES ('n3','bob','Locked','ENCv1:abc','secret',0,0,1,'hash','hint★',0,1000,4000,0)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_1_2, com.example.data.local.MIGRATION_2_3).close()

        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(ctx, NoteaDatabase::class.java, dbName).addMigrations(*NoteaDatabase.ALL_MIGRATIONS).allowMainThreadQueries().build()
        val repo = NoteRepository(db, testVault(), AttachmentStore(ctx))
        assertEquals(3, LegacyUpgrader(db, repo).run())
        assertEquals(0, LegacyUpgrader(db, repo).run()) // idempotent

        val n1 = db.noteDao().getNoteWithDetails("n1")!!
        assertEquals("نص الملاحظة\n\n⏰ غداً", n1.note.body)
        assertTrue(n1.note.isFavorite)
        assertEquals("legacy-work", n1.note.folderId)
        assertEquals(listOf("شراء الخبز", "الاتصال بأحمد"), n1.sortedChecklist.map { it.text })
        assertTrue(n1.sortedChecklist[0].checked)
        assertEquals(setOf("عمل", "مهم"), n1.tags.map { it.name }.toSet())
        assertEquals(1000L, n1.note.createdAt); assertEquals(2000L, n1.note.updatedAt)
        assertEquals(SyncState.CLEAN, n1.note.syncState)

        val n2 = db.noteDao().getNote("n2")!!
        assertEquals(1700000000000L, n2.trashedAt)
        assertEquals(SyncState.DIRTY, n2.syncState) // pendingSync preserved

        val n3 = db.noteDao().getNote("n3")!!
        assertEquals("ENCv1:abc", n3.body) // ciphertext untouched
        assertEquals("hint", n3.lockHint)
        assertEquals("bob", n3.userId)

        // Safety copy of the v1 table is still there.
        assertEquals(3, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM local_notes").use { it.moveToFirst(); it.getInt(0) })
        // Search index built for migrated notes.
        assertTrue(db.searchDao().search("alice", ArabicNormalizer.toFtsQuery("الخبز")!!).contains("n1"))
        db.close()
    }
}

@RunWith(RobolectricTestRunner::class)
class MigrationV3Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NoteaDatabase::class.java)

    @Test
    fun v2ToV3_isAdditive_andOldNotesUseClassicLayout() = runBlocking {
        val name = "v3-test.db"
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO notes (id,userId,title,body,colorIndex,folderId,isPinned,isFavorite,isArchived,trashedAt,isLocked,lockPinHash,lockHint,createdAt,updatedAt,syncState,deleted,baseRemoteUpdatedAt,legacyCategory,legacyPending,importSource,contentHash) VALUES ('n','u','t','نص',0,NULL,0,0,0,NULL,0,'','',1,2,0,0,2,'',0,'','')")
            execSQL("INSERT INTO checklist_items VALUES ('c','n','بند',1,0)")
            execSQL("INSERT INTO note_revisions VALUES ('r','n','t','قديم','[]',1,'auto')")
            close()
        }
        helper.runMigrationsAndValidate(name, 3, true, com.example.data.local.MIGRATION_2_3).close()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(ctx, NoteaDatabase::class.java, name).addMigrations(*NoteaDatabase.ALL_MIGRATIONS).allowMainThreadQueries().build()
        val d = db.noteDao().getNoteWithDetails("n")!!
        assertEquals("نص", d.note.body); assertTrue(d.blocks.isEmpty()); assertNull(d.checklist.single().blockId)
        val rev = db.revisionDao().forNote("n").single(); assertEquals("", rev.label); assertEquals("", rev.blocksJson)
        db.close()
    }
}

@RunWith(RobolectricTestRunner::class)
class MigrationV4Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NoteaDatabase::class.java)

    @Test
    fun v3ToV4_isAdditive_keepsNotes_noParentNoIcon() = runBlocking {
        val name = "v4-test.db"
        helper.createDatabase(name, 3).apply {
            execSQL("INSERT INTO notes (id,userId,title,body,colorIndex,folderId,isPinned,isFavorite,isArchived,trashedAt,isLocked,lockPinHash,lockHint,createdAt,updatedAt,syncState,deleted,baseRemoteUpdatedAt,legacyCategory,legacyPending,importSource,contentHash) VALUES ('n','u','عنوان','نص',2,NULL,1,0,0,NULL,0,'','',1,2,0,0,2,'',0,'','')")
            execSQL("INSERT INTO note_blocks VALUES ('b','n',0,'text','نص',NULL)")
            close()
        }
        helper.runMigrationsAndValidate(name, 4, true, com.example.data.local.MIGRATION_3_4).close()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(ctx, NoteaDatabase::class.java, name).addMigrations(*NoteaDatabase.ALL_MIGRATIONS).allowMainThreadQueries().build()
        val d = db.noteDao().getNoteWithDetails("n")!!
        assertEquals("نص", d.note.body); assertEquals(2, d.note.colorIndex); assertTrue(d.note.isPinned)
        assertNull(d.note.parentId); assertEquals("", d.note.icon); assertEquals(1, d.blocks.size)
        db.close()
    }
}

@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val db = inMemoryDb()
    private val vault = testVault()
    private val repo = NoteRepository(db, vault, AttachmentStore(ctx))

    @After fun close() = db.close()

    @Test fun subNotes_parentKeptOnSave_cyclesRefused_iconStored() = runBlocking {
        repo.save("u", NoteDraft(id = "p", title = "مشروع"))
        repo.save("u", NoteDraft(id = "c", title = "مهمة فرعية", parentId = "p", icon = "📌"))
        repo.save("u", NoteDraft(id = "g", title = "حفيد", parentId = "c"))
        assertEquals("p", db.noteDao().getNote("c")!!.parentId)
        assertEquals("📌", db.noteDao().getNote("c")!!.icon)
        // Editing the child (draft from toDraft) keeps its parent and icon.
        val d = repo.toDraft("u", repo.getNote("c")!!)!!
        repo.save("u", d.copy(body = "تعديل", updatedAt = null))
        assertEquals("p", db.noteDao().getNote("c")!!.parentId); assertEquals("📌", db.noteDao().getNote("c")!!.icon)
        // A draft without parent info (e.g. older code paths) keeps the stored parent.
        repo.save("u", NoteDraft(id = "c", title = "مهمة فرعية", body = "x"))
        assertEquals("p", db.noteDao().getNote("c")!!.parentId)
        // Cycles: p under its grandchild is refused; moving to top level works.
        assertFalse(repo.setParent("u", "p", "g"))
        assertNull(db.noteDao().getNote("p")!!.parentId)
        assertTrue(repo.setParent("u", "g", null)); assertNull(db.noteDao().getNote("g")!!.parentId)
        assertFalse(repo.setParent("u", "c", "c"))
    }

    @Test fun toggleAndCalloutText_isPartOfBody() = runBlocking {
        val blocks = listOf(
            com.example.data.repository.BlockDraft("t", com.example.data.local.BlockType.TEXT, "مقدمة"),
            com.example.data.repository.BlockDraft("g", com.example.data.local.BlockType.TOGGLE, "التفاصيل\nمخفي"),
            com.example.data.repository.BlockDraft("k", com.example.data.local.BlockType.CALLOUT, "مهم جداً")
        )
        repo.save("u", NoteDraft(id = "b", title = "كتل", blocks = blocks))
        val n = db.noteDao().getNoteWithDetails("b")!!
        assertEquals("مقدمة\n\nالتفاصيل\nمخفي\n\nمهم جداً", n.note.body)
        assertEquals(listOf("text", "toggle", "callout"), n.blocks.sortedBy { it.position }.map { it.type })
        // Sync layout round-trip keeps toggle/callout text.
        val layout = blocks.map { Triple(it.id, it.type, it.text.length as Any?) }
        assertEquals(blocks, com.example.data.repository.Blocks.fromLayout(layout, n.note.body))
    }

    @Test fun lockedNote_neverStoredInPlaintext() = runBlocking {
        try {
            repo.save("u", NoteDraft(id = "x", title = "T", body = "SECRET-BODY", isLocked = true))
            fail("must refuse without vault")
        } catch (_: VaultLockedException) {}
        assertNull(db.noteDao().getNote("x")) // nothing written

        vault.setUp("u", "passphrase1".toCharArray())
        repo.save("u", NoteDraft(id = "x", title = "T", body = "SECRET-BODY", isLocked = true,
            checklist = listOf(ChecklistDraft(text = "SECRET-ITEM"))))
        val n = db.noteDao().getNoteWithDetails("x")!!
        assertTrue(NoteCipher.isV2(n.note.body))
        assertTrue(n.checklist.isEmpty())
        // Scan every table for the plaintext
        val sql = db.openHelper.readableDatabase
        listOf("notes", "checklist_items", "notes_fts", "note_revisions").forEach { t ->
            sql.query("SELECT * FROM $t").use { c ->
                while (c.moveToNext()) for (i in 0 until c.columnCount) {
                    val v = runCatching { c.getString(i) }.getOrNull().orEmpty()
                    assertFalse("$t leaks plaintext", v.contains("SECRET"))
                }
            }
        }
        val dec = repo.decrypt("u", n.note, n.checklist) as DecryptResult.Plain
        assertEquals("SECRET-BODY", dec.body)
        assertEquals("SECRET-ITEM", dec.checklist.single().text)
    }

    @Test fun search_arabicNormalized_titleBodyTagsChecklist_excludesLockedContent() = runBlocking {
        vault.setUp("u", "passphrase1".toCharArray())
        repo.save("u", NoteDraft(id = "a", title = "مَدْرَسَةُ أحمد", body = "اجتماع الإثنين", tags = listOf("عمل")))
        repo.save("u", NoteDraft(id = "b", title = "Shopping", body = "", checklist = listOf(ChecklistDraft(text = "Milk"))))
        repo.save("u", NoteDraft(id = "c", title = "Vault", body = "كلمة سرية", isLocked = true))
        repo.save("other", NoteDraft(id = "d", title = "مدرسه احمد"))
        fun q(s: String) = runBlocking { db.searchDao().search("u", ArabicNormalizer.toFtsQuery(s)!!).toSet() }
        assertEquals(setOf("a"), q("مدرسه احمد"))
        assertEquals(setOf("a"), q("اجتما"))
        assertEquals(setOf("a"), q("عمل"))
        assertEquals(setOf("b"), q("milk"))
        assertEquals(emptySet<String>(), q("سرية")) // locked body not indexed
        assertEquals(setOf("c"), q("vault")) // title is
    }

    @Test fun revisions_restore_keepsCurrentVersion() = runBlocking {
        repo.save("u", NoteDraft(id = "r", title = "v1", body = "first"))
        repo.snapshot("r")
        repo.save("u", NoteDraft(id = "r", title = "v2", body = "second"))
        val rev = db.revisionDao().forNote("r").single()
        repo.restoreRevision("u", rev)
        assertEquals("first", db.noteDao().getNote("r")!!.body)
        assertEquals(listOf("first", "second"), db.revisionDao().forNote("r").map { it.body }.sorted())
    }

    @Test fun trash_and_tombstones() = runBlocking {
        repo.save("u", NoteDraft(id = "t1", title = "local only"))
        repo.moveToTrash("u", "t1")
        assertNotNull(db.noteDao().getNote("t1")!!.trashedAt)
        repo.deletePermanently("u", "t1")
        assertNull(db.noteDao().getNote("t1")) // never synced: hard delete

        repo.save("u", NoteDraft(id = "t2", title = "synced"))
        db.noteDao().markClean("t2", db.noteDao().getNote("t2")!!.updatedAt, 123L)
        repo.deletePermanently("u", "t2")
        val tomb = db.noteDao().getNote("t2")!!
        assertTrue(tomb.deleted); assertEquals(SyncState.DIRTY, tomb.syncState) // waits for remote delete
        assertTrue(db.noteDao().observeNotesSnapshot("u").none { it.note.id == "t2" })
    }

    @Test fun folderDelete_keepsNotes() = runBlocking {
        val f = repo.createFolder("u", "العمل")
        repo.save("u", NoteDraft(id = "f1", title = "x", folderId = f.id))
        repo.deleteFolder("u", f.id)
        assertNull(db.noteDao().getNote("f1")!!.folderId)
        assertNotNull(db.noteDao().getNote("f1"))
    }
}

private suspend fun com.example.data.local.NoteDao.observeNotesSnapshot(uid: String) = getAllWithDetails(uid)
