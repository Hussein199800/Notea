package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.security.NoteCipher
import com.example.core.testVault
import com.example.data.backup.BackupException
import com.example.data.backup.BackupManager
import com.example.data.importer.DuplicateKind
import com.example.data.importer.ImportAction
import com.example.data.importer.ImportInput
import com.example.data.importer.ImportManager
import com.example.data.importer.ImportSource
import com.example.data.importer.ImportWarning
import com.example.data.local.AttachmentKind
import com.example.data.repository.AttachmentStore
import com.example.data.repository.ChecklistDraft
import com.example.data.repository.NoteDraft
import com.example.data.repository.NoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private fun input(name: String, bytes: ByteArray, modified: Long? = null) =
    ImportInput(name, AttachmentStore.guessMime(name), modified, bytes.size.toLong()) { bytes.inputStream() }

private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
    return out.toByteArray()
}

private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)

@RunWith(RobolectricTestRunner::class)
class ImportTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val db = inMemoryDb()
    private val repo = NoteRepository(db, testVault(), AttachmentStore(ctx))
    private val im = ImportManager(ctx, repo)

    @Test fun txt_utf8_and_windows1256() = runBlocking {
        val p = im.preview("u", ImportSource.XIAOMI, listOf(
            input("a.txt", "قائمة اليوم\nشراء الخبز\n- [x] تم\n- [ ] لم يتم".toByteArray(), 1234L),
            input("b.txt", "مرحبا بالعالم".toByteArray(charset("windows-1256")))
        ))
        val a = p.candidates.first { it.sourceName == "a.txt" }
        assertEquals("قائمة اليوم", a.title)
        assertEquals("شراء الخبز", a.body)
        assertEquals(listOf(true, false), a.checklist.map { it.checked })
        assertEquals(1234L, a.createdAt)
        assertEquals("مرحبا بالعالم", p.candidates.first { it.sourceName == "b.txt" }.body)
    }

    @Test fun markdown_frontMatter() = runBlocking {
        val md = "---\ntitle: خطة\ntags: [عمل, مشروع]\ncreated: 2023-05-01\n---\n## هدف\n**مهم** جداً"
        val c = im.preview("u", ImportSource.FILES, listOf(input("p.md", md.toByteArray()))).candidates.single()
        assertEquals("خطة", c.title); assertEquals(listOf("عمل", "مشروع"), c.tags)
        assertTrue(c.body.contains("**مهم**")); assertTrue(c.createdAt!! < 1700000000000L)
    }

    @Test fun html_huaweiStyle_withImagesInZip() = runBlocking {
        val html = """<html><head><title>رحلة</title><meta name="created" content="2022-03-04 10:00:00"></head><body>
            <h1>العنوان</h1><p>نص <b>عريض</b> و <i>مائل</i> و <u>مسطر</u></p>
            <ul><li>أول</li><li>ثاني</li></ul><div><input type="checkbox" checked> مهمة منجزة</div>
            <a href="https://example.com">رابط</a><img src="images/p1.png"><table><tr><td>x</td></tr></table></body></html>"""
        val p = im.preview("u", ImportSource.HUAWEI, listOf(input("notes.zip", zip("note1.html" to html.toByteArray(), "images/p1.png" to PNG))))
        val c = p.candidates.single() // the image is attached, not a separate note
        assertEquals("رحلة", c.title)
        assertTrue(c.body.contains("**عريض**")); assertTrue(c.body.contains("*مائل*")); assertTrue(c.body.contains("++مسطر++"))
        assertTrue(c.body.contains("- أول")); assertTrue(c.body.contains("[رابط](https://example.com)"))
        assertEquals("مهمة منجزة", c.checklist.single().text); assertTrue(c.checklist.single().checked)
        assertEquals(AttachmentKind.IMAGE, c.attachments.single().kind)
        assertTrue(ImportWarning.FORMATTING_LOST in c.warnings)
        assertTrue(c.createdAt != null)
    }

    @Test fun docx_samsungStyle() = runBlocking {
        val doc = """<?xml version="1.0"?><w:document xmlns:w="w"><w:body>
            <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>محضر</w:t></w:r></w:p>
            <w:p><w:r><w:rPr><w:b/></w:rPr><w:t>قرار</w:t></w:r><w:r><w:t xml:space="preserve"> مهم</w:t></w:r></w:p>
            <w:p><w:pPr><w:numPr/></w:pPr><w:r><w:t>بند</w:t></w:r></w:p></w:body></w:document>"""
        val core = """<cp:coreProperties xmlns:cp="c" xmlns:dcterms="d"><dcterms:created>2021-01-02T03:04:05Z</dcterms:created><dcterms:modified>2021-02-02T03:04:05Z</dcterms:modified></cp:coreProperties>"""
        val bytes = zip("word/document.xml" to doc.toByteArray(), "docProps/core.xml" to core.toByteArray(), "word/media/image1.png" to PNG)
        val c = im.preview("u", ImportSource.SAMSUNG, listOf(input("s.docx", bytes))).candidates.single()
        assertEquals("محضر", c.title)
        assertTrue(c.body.contains("**قرار** مهم")); assertTrue(c.body.contains("- بند"))
        assertEquals(1609556645000L, c.createdAt); assertEquals(1612235045000L, c.updatedAt)
        assertEquals(1, c.attachments.size)
    }

    @Test fun pdf_and_image_keptAsOriginal() = runBlocking {
        val p = im.preview("u", ImportSource.SAMSUNG, listOf(input("handwriting.pdf", "%PDF-1.4 x".toByteArray()), input("scan.jpg", PNG)))
        assertEquals(2, p.candidates.size)
        assertTrue(p.candidates.all { it.warnings.isEmpty() && it.attachments.size == 1 })
        assertEquals(setOf(AttachmentKind.PDF, AttachmentKind.IMAGE), p.candidates.map { it.attachments.single().kind }.toSet())
    }

    @Test fun json_keepAndNoteaV1_andUnsupported() = runBlocking {
        val keep = """{"title":"Keep","textContent":"","listContent":[{"text":"a","isChecked":true}],"labels":[{"name":"x"}],"isPinned":true,"createdTimestampUsec":1600000000000000}"""
        val v1 = """[{"id":"1","title":"قديمة","content":"<!--AN_META_START\nTAGS:t\nAN_META_END-->\nنص","category":"journal|fav","colorIndex":3,"isPinned":false,"isLocked":false,"notePinHash":"","lockHint":"","isArchived":false,"createdAtMillis":5,"updatedAtMillis":6}]"""
        val p = im.preview("u", ImportSource.FILES, listOf(input("k.json", keep.toByteArray()), input("n.json", v1.toByteArray()), input("x.exe", byteArrayOf(1))))
        val k = p.candidates.first { it.title == "Keep" }
        assertTrue(k.isPinned); assertEquals(listOf("x"), k.tags); assertEquals(1600000000000L, k.createdAt)
        val o = p.candidates.first { it.title == "قديمة" }
        assertEquals("نص", o.body); assertTrue(o.isFavorite); assertEquals("يوميات", o.folderName); assertEquals(5L, o.createdAt)
        assertTrue(p.issues.any { it.sourceName == "x.exe" && it.warning == ImportWarning.UNSUPPORTED })
    }

    @Test fun duplicates_detected_and_commit_respectsChoices() = runBlocking {
        repo.save("u", NoteDraft(id = "e1", title = "تسوق", body = "خبز"))
        val p = im.preview("u", ImportSource.FILES, listOf(
            input("1.txt", "تسوق\nخبز".toByteArray()),         // exact (normalized)
            input("2.txt", "تسوق\nحليب".toByteArray()),        // same title, different body
            input("3.txt", "جديد\nمحتوى".toByteArray(), 42L)
        ))
        val byName = p.candidates.associateBy { it.sourceName }
        assertEquals(DuplicateKind.EXACT, byName["1.txt"]!!.duplicate); assertEquals(ImportAction.SKIP, byName["1.txt"]!!.action)
        assertEquals(DuplicateKind.SIMILAR, byName["2.txt"]!!.duplicate)
        val chosen = p.candidates.map { if (it.sourceName == "2.txt") it.copy(action = ImportAction.MERGE) else it }
        val r = im.commit("u", ImportSource.FILES, chosen)
        assertEquals(1, r.created); assertEquals(1, r.merged); assertEquals(1, r.skipped)
        assertEquals("خبز\n\nحليب", db.noteDao().getNote("e1")!!.body)
        val created = db.noteDao().getAllWithDetails("u").first { it.note.title == "جديد" }.note
        assertEquals(42L, created.createdAt); assertEquals("files", created.importSource)
        assertEquals(1, db.revisionDao().forNote("e1").size) // pre-merge snapshot
    }
}

@RunWith(RobolectricTestRunner::class)
class BackupTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    private fun device(): Triple<com.example.data.local.NoteaDatabase, NoteRepository, BackupManager> {
        val db = inMemoryDb(); val v = testVault()
        val repo = NoteRepository(db, v, AttachmentStore(ctx))
        return Triple(db, repo, BackupManager(ctx, db, repo, v))
    }

    private fun seed(repo: NoteRepository) = runBlocking {
        val f = repo.createFolder("u", "مشاريع")
        repo.save("u", NoteDraft(id = "p1", title = "خطة", body = "**نص**", folderId = f.id, tags = listOf("عمل"),
            checklist = listOf(ChecklistDraft(text = "مهمة", checked = true)), createdAt = 100L, isFavorite = true))
        repo.addAttachment("u", "p1", AttachmentKind.IMAGE, "a.png", "image/png", PNG.inputStream())
        repo.setReminder("u", "p1", System.currentTimeMillis() + 3_600_000)
        repo.restoreTimestamps("p1", 100L, 200L)
        repo.snapshot("p1")
        repo.save("u", NoteDraft(id = "p2", title = "أخرى", body = "x"))
    }

    @Test fun plainBackup_roundTrip_toNewDevice_preservesEverything() = runBlocking {
        val (_, repoA, bmA) = device(); seed(repoA)
        val out = ByteArrayOutputStream(); val manifest = bmA.export("u", out, null)
        assertEquals(2, manifest.counts["notes"]); assertEquals(1, manifest.counts["attachments"])

        val (dbB, repoB, bmB) = device()
        val preview = bmB.read("u", { out.toByteArray().inputStream() }, null)
        assertEquals(2, preview.noteCount); assertEquals(1, preview.attachmentCount)
        // selective: skip p2
        val chosen = preview.candidates.map { if (it.key == "p2") it.copy(action = ImportAction.SKIP) else it }
        val r = ImportManager(ctx, repoB).commit("u", ImportSource.BACKUP, chosen)
        bmB.finishRestore("u", r.createdIds)
        val n = dbB.noteDao().getNoteWithDetails("p1")!!
        assertEquals("**نص**", n.note.body); assertEquals(100L, n.note.createdAt); assertEquals(200L, n.note.updatedAt)
        assertTrue(n.note.isFavorite); assertEquals("مشاريع", repoB.allFolders("u").single().name)
        assertEquals("عمل", n.tags.single().name); assertTrue(n.checklist.single().checked)
        assertEquals(1, n.attachments.size); assertTrue(n.activeReminder != null)
        assertTrue(dbB.revisionDao().forNote("p1").isNotEmpty())
        assertTrue(dbB.noteDao().getNote("p2") == null)

        // Restoring the same backup again does not duplicate anything.
        val again = bmB.read("u", { out.toByteArray().inputStream() }, null)
        assertEquals(ImportAction.SKIP, again.candidates.first { it.key == "p1" }.action)
    }

    @Test fun encryptedBackup_passwordRequired_wrongPasswordRejected() = runBlocking {
        val (_, repoA, bmA) = device(); seed(repoA)
        val out = ByteArrayOutputStream(); bmA.export("u", out, "strong-pass".toCharArray())
        val bytes = out.toByteArray()
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("خطة"))
        val (_, _, bmB) = device()
        try { bmB.read("u", { bytes.inputStream() }, null); fail() } catch (e: BackupException) { assertEquals(BackupException.Reason.PASSWORD_REQUIRED, e.reason) }
        try { bmB.read("u", { bytes.inputStream() }, "nope-nope".toCharArray()); fail() } catch (e: BackupException) { assertEquals(BackupException.Reason.WRONG_PASSWORD, e.reason) }
        assertEquals(2, bmB.read("u", { bytes.inputStream() }, "strong-pass".toCharArray()).noteCount)
    }

    @Test fun tamperedBackup_isRejected() = runBlocking {
        val (_, repoA, bmA) = device(); seed(repoA)
        val out = ByteArrayOutputStream(); bmA.export("u", out, null)
        // Rewrite data.json while keeping the old manifest checksum.
        val entries = linkedMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(out.toByteArray().inputStream()).use { z -> while (true) { val e = z.nextEntry ?: break; entries[e.name] = z.readBytes() } }
        entries["data.json"] = String(entries["data.json"]!!).replace("خطة", "مزورة").toByteArray()
        val bad = zip(*entries.map { it.key to it.value }.toTypedArray())
        val (_, _, bmB) = device()
        try { bmB.read("u", { bad.inputStream() }, null); fail() } catch (e: BackupException) { assertEquals(BackupException.Reason.CORRUPTED, e.reason) }
        try { bmB.read("u", { "hello".toByteArray().inputStream() }, null); fail() } catch (e: BackupException) { assertEquals(BackupException.Reason.NOT_A_BACKUP, e.reason) }
    }

    @Test fun lockedNotes_stayEncrypted_andOpenOnNewDeviceWithPassphrase() = runBlocking {
        val dbA = inMemoryDb(); val vA = testVault(); val repoA = NoteRepository(dbA, vA, AttachmentStore(ctx))
        vA.setUp("u", "vault-pass".toCharArray())
        repoA.save("u", NoteDraft(id = "s1", title = "سر", body = "PLAINTEXT-SECRET", isLocked = true))
        val out = ByteArrayOutputStream(); BackupManager(ctx, dbA, repoA, vA).export("u", out, null)
        assertFalse(String(out.toByteArray(), Charsets.ISO_8859_1).contains("PLAINTEXT-SECRET"))

        val dbB = inMemoryDb(); val vB = testVault(); val repoB = NoteRepository(dbB, vB, AttachmentStore(ctx))
        val bmB = BackupManager(ctx, dbB, repoB, vB)
        val p = bmB.read("u", { out.toByteArray().inputStream() }, null)
        val r = ImportManager(ctx, repoB).commit("u", ImportSource.BACKUP, p.candidates); bmB.finishRestore("u", r.createdIds)
        val stored = dbB.noteDao().getNote("s1")!!
        assertTrue(NoteCipher.isV2(stored.body))
        // The vault envelope came with the backup: the passphrase opens it on the new device.
        assertTrue(vB.unlockWithPassphrase("u", "vault-pass".toCharArray()))
        val dec = repoB.decrypt("u", stored) as com.example.data.repository.DecryptResult.Plain
        assertEquals("PLAINTEXT-SECRET", dec.body)
    }
}
