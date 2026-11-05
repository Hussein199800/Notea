package com.example.features

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.media.ImageNormalizer
import com.example.core.media.TiffDecoder
import com.example.core.security.NoteCipher
import com.example.core.testVault
import com.example.data.backup.BackupManager
import com.example.data.importer.ImportManager
import com.example.data.importer.ImportSource
import com.example.data.inMemoryDb
import com.example.data.local.AttachmentKind
import com.example.data.local.BlockType
import com.example.data.repository.AttachmentStore
import com.example.data.repository.BlockDraft
import com.example.data.repository.Blocks
import com.example.data.repository.ChecklistDraft
import com.example.data.repository.DecryptResult
import com.example.data.repository.NoteDraft
import com.example.data.repository.NoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

private fun res(name: String) = ImageNormalizer::class.java.classLoader!!.getResource("images/$name").readBytes()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MediaTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    private fun pixel(name: String, x: Int, y: Int): Int {
        val b = TiffDecoder.decode(res(name))
        assertNotNull("decode $name", b)
        return b!!.getPixel(x, y)
    }

    @Test fun tiff_uncompressed_lzw_predictor_packbits_bilevel() {
        // Reference pixel from the generator: ((x*7)%256, (y*11)%256, 128 if x<18 else 30)
        fun expect(x: Int, y: Int) = (0xFF shl 24) or (((x * 7) % 256) shl 16) or (((y * 11) % 256) shl 8) or (if (x < 18) 128 else 30)
        for (f in listOf("rgb_none.tif", "rgb_lzw.tif", "rgb_lzw_pred.tif")) {
            for ((x, y) in listOf(0 to 0, 5 to 3, 20 to 10, 36 to 22)) assertEquals("$f@$x,$y", expect(x, y), pixel(f, x, y))
        }
        val gray = pixel("gray_packbits.tif", 10, 10) and 0xFF
        assertTrue(gray in 0..255)
        assertEquals(-0x1000000, pixel("bilevel.tif", 0, 5)) // black dot
        assertEquals(-0x1, pixel("bilevel.tif", 1, 5)) // white
        val big = TiffDecoder.decode(res("big_lzw.tif"))!!
        assertEquals(300, big.width); assertEquals(200, big.height)
        assertEquals(((10 * 20) % 256), (big.getPixel(10, 20) shr 8) and 0xFF)
    }

    @Test fun normalizer_detects_and_converts() {
        val dir = File(ctx.cacheDir, "norm").apply { mkdirs() }
        fun prep(name: String, bytes: ByteArray, mime: String = "application/octet-stream"): ImageNormalizer.Prepared {
            val f = File(dir, name).apply { writeBytes(bytes) }
            return ImageNormalizer.prepare(f, name, mime)
        }
        val tiff = prep("scan.tif", res("rgb_lzw.tif"))
        assertTrue(tiff.isImage); assertTrue(tiff.converted); assertTrue(tiff.mime == "image/jpeg" || tiff.mime == "image/png")
        val jpg = prep("photo.jpg", res("photo.jpg"))
        assertTrue(jpg.isImage); assertFalse(jpg.converted); assertEquals("image/jpeg", jpg.mime)
        val svg = prep("logo.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".toByteArray())
        assertTrue(svg.isImage); assertEquals("image/svg+xml", svg.mime)
        val broken = prep("x.heic", ByteArray(32) { 1 })
        assertFalse(broken.isImage); assertTrue(broken.unsupported) // kept as a file, never dropped
        assertEquals(ImageNormalizer.Format.HEIF, ImageNormalizer.detect("\u0000\u0000\u0000\u0018ftypheic".toByteArray(Charsets.ISO_8859_1), "a", ""))
        assertEquals(ImageNormalizer.Format.AVIF, ImageNormalizer.detect("\u0000\u0000\u0000\u0018ftypavif".toByteArray(Charsets.ISO_8859_1), "a", ""))
    }
}

@RunWith(RobolectricTestRunner::class)
class BlocksTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test fun normalize_reconcile_layoutRoundTrip() {
        val blocks = listOf(
            BlockDraft("t1", BlockType.TEXT, "أول"), BlockDraft("t2", BlockType.TEXT, "ثاني"),
            BlockDraft("a", BlockType.ATTACHMENT, attachmentId = "img"), BlockDraft("t3", BlockType.TEXT, ""),
            BlockDraft("c", BlockType.CHECKLIST), BlockDraft("t4", BlockType.TEXT, "أخير")
        )
        val n = Blocks.normalize(blocks)
        assertEquals(listOf("t1", "a", "c", "t4"), n.map { it.id }) // merged t1+t2, dropped empty t3
        assertEquals("أول\n\nثاني", n[0].text)
        val body = Blocks.joinText(n)
        val layout = n.map { Triple(it.id, it.type, if (it.type == BlockType.TEXT) it.text.length else it.attachmentId) }
        assertEquals(n, Blocks.fromLayout(layout, body))
        assertNull(Blocks.fromLayout(layout, body + "x")) // mismatch -> fall back to classic layout
        // reconcile: missing attachment dropped, unknown attachment appended, checklist block created for items
        val r = Blocks.reconcile(n, body, listOf(ChecklistDraft(text = "x", blockId = "other")), listOf("new"))
        assertTrue(r.none { it.attachmentId == "img" })
        assertEquals("new", r.last().attachmentId)
        assertTrue(r.any { it.id == "other" && it.type == BlockType.CHECKLIST })
    }

    @Test fun repository_savesLayout_lockedLayoutOnlyInsideCiphertext() = runBlocking {
        val db = inMemoryDb(); val vault = testVault(); val repo = NoteRepository(db, vault, AttachmentStore(ctx))
        val layout = listOf(BlockDraft("t1", BlockType.TEXT, "مقدمة"), BlockDraft("c1", BlockType.CHECKLIST), BlockDraft("t2", BlockType.TEXT, "خاتمة"))
        repo.save("u", NoteDraft(id = "n", title = "T", blocks = layout, checklist = listOf(ChecklistDraft(text = "بند", blockId = "c1"))))
        val d = db.noteDao().getNoteWithDetails("n")!!
        assertEquals("مقدمة\n\nخاتمة", d.note.body)
        assertEquals(listOf("t1", "c1", "t2"), d.blocks.sortedBy { it.position }.map { it.id })
        assertEquals("c1", d.checklist.single().blockId)

        vault.setUp("u", "passphrase1".toCharArray())
        repo.save("u", repo.toDraft("u", d)!!.copy(isLocked = true))
        val locked = db.noteDao().getNoteWithDetails("n")!!
        assertTrue(locked.blocks.isEmpty()) // no plaintext layout rows
        assertTrue(NoteCipher.isV2(locked.note.body))
        val dec = repo.decrypt("u", locked.note, locked.checklist, locked.blocks) as DecryptResult.Plain
        assertEquals(listOf("t1", "c1", "t2"), dec.blocks!!.map { it.id })
        assertEquals("مقدمة", dec.blocks!![0].text)
    }

    @Test fun backup_restoresBlockOrderAndAttachmentReferences() = runBlocking {
        val dbA = inMemoryDb(); val repoA = NoteRepository(dbA, testVault(), AttachmentStore(ctx))
        repoA.save("u", NoteDraft(id = "n1", title = "رحلة", body = "قبل الصورة"))
        val img = repoA.addAttachment("u", "n1", AttachmentKind.IMAGE, "a.png", "image/png", byteArrayOf(1, 2, 3).inputStream())
        repoA.save("u", repoA.toDraft("u", dbA.noteDao().getNoteWithDetails("n1")!!)!!.copy(blocks = listOf(
            BlockDraft("t1", BlockType.TEXT, "قبل الصورة"), BlockDraft("a1", BlockType.ATTACHMENT, attachmentId = img.id), BlockDraft("t2", BlockType.TEXT, "بعد الصورة")
        )))
        val out = java.io.ByteArrayOutputStream()
        BackupManager(ctx, dbA, repoA, testVault()).export("u", out, null)

        val dbB = inMemoryDb(); val repoB = NoteRepository(dbB, testVault(), AttachmentStore(ctx))
        val bmB = BackupManager(ctx, dbB, repoB, testVault())
        val p = bmB.read("u", { out.toByteArray().inputStream() }, null)
        val r = ImportManager(ctx, repoB).commit("u", ImportSource.BACKUP, p.candidates); bmB.finishRestore("u", r.createdIds)
        val restored = dbB.noteDao().getNoteWithDetails("n1")!!
        val order = restored.blocks.sortedBy { it.position }
        assertEquals(listOf(BlockType.TEXT, BlockType.ATTACHMENT, BlockType.TEXT), order.map { it.type })
        assertEquals(restored.attachments.single().id, order[1].attachmentId)
        assertEquals("قبل الصورة\n\nبعد الصورة", restored.note.body)
    }
}
