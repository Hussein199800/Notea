package com.example.features

import androidx.test.core.app.ApplicationProvider
import com.example.core.text.NoteLinks
import com.example.data.importer.ImportContext
import com.example.data.importer.ImportInput
import com.example.data.importer.ImportWarning
import com.example.data.importer.SamsungNotesHandler
import com.example.data.local.NoteEntity
import com.example.data.local.NoteWithDetails
import com.example.ocr.OcrFilter
import com.example.ocr.OcrFilter.Word
import com.example.ui.viewmodel.buildGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class NotionAndImportTest {
    private fun note(id: String, title: String, body: String = "", parent: String? = null, icon: String = "", locked: Boolean = false, trashed: Boolean = false) =
        NoteWithDetails(NoteEntity(id = id, userId = "u", title = title, body = body, createdAt = 1, updatedAt = 1, parentId = parent, icon = icon,
            isLocked = locked, trashedAt = if (trashed) 5L else null), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    @Test fun links_parseNormalizeAndDedupe() {
        assertEquals(listOf("خطة العمل", "Budget"), NoteLinks.titles("راجع [[خطة العمل]] و [[Budget]] ثم [[خطه العمل]]"))
        assertTrue(NoteLinks.linksTo("انظر [[أحمد]]", "احمد"))
        assertFalse(NoteLinks.linksTo("[[أحمد]", "احمد"))
        assertTrue(NoteLinks.titles("[[\n]] [[]]").isEmpty())
    }

    @Test fun graph_parentChildrenLinksBacklinks_lockedNeverRead() {
        val notes = listOf(
            note("p", "المشروع", icon = "📁"),
            note("me", "المهام", "تفاصيل في [[المواعيد]] و [[غير موجودة]]", parent = "p"),
            note("c1", "اليوم الأول", parent = "me"),
            note("c2", "محذوفة", parent = "me", trashed = true),
            note("d", "المواعيد", "عودة إلى [[المهام]]"),
            note("secret", "سري", "[[المهام]]", locked = true)
        )
        val g = buildGraph("me", "المهام", "تفاصيل في [[المواعيد]] و [[غير موجودة]]", notes)
        assertEquals("p", g.parent!!.id); assertEquals("📁", g.parent!!.icon)
        assertEquals(listOf("c1"), g.children.map { it.id })
        assertEquals(listOf("d"), g.links.map { it.id })
        assertEquals(listOf("غير موجودة"), g.missingLinks)
        assertEquals(listOf("d"), g.backlinks.map { it.id }) // the locked note is not read
        assertNull(buildGraph("p", "المشروع", "", notes).parent)
    }

    @Test fun ocrFilter_acceptsShortClearText_likeAPassword() {
        val r = OcrFilter.filter(listOf(listOf(Word("7u+rh3T\$f&X5bh/", 88f)), listOf(Word("Kaza", 93f), Word("wallet", 95f))))
        assertTrue(r.accepted); assertTrue(r.text.contains("Kaza wallet")); assertTrue(r.text.contains("7u+rh3T"))
        // Still rejects low-confidence noise.
        assertFalse(OcrFilter.filter(listOf(listOf(Word("Wa", 31f), Word("50l", 35f)))).accepted)
    }

    private fun utf16(s: String) = s.toByteArray(Charsets.UTF_16LE)

    @Test fun samsungSdocx_textAndImagesImported_noiseDropped() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val noteBin = ByteArrayOutputStream().apply {
            write(byteArrayOf(1, 0, 0, 0, 7, 9)); write(utf16("SamsungOneUI"))
            write(byteArrayOf(0, 0, 3, 0)); write(utf16("قائمة الأعمال"))
            write(byteArrayOf(0, 0, 0x12, 0x7F)); write(utf16("مراجعة ملف القضية 245 مع النقابة"))
            write(byteArrayOf(0, 0)); write(utf16("a1b2c3d4e5f6a7b8c9")); write(byteArrayOf(0, 0)); write(utf16("Call the client tomorrow"))
        }.toByteArray()
        val img = ByteArray(5000) { (it % 251).toByte() }
        val zip = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { z ->
                z.putNextEntry(ZipEntry("note.note")); z.write(noteBin); z.closeEntry()
                z.putNextEntry(ZipEntry("media/photo1.jpg")); z.write(img); z.closeEntry()
                z.putNextEntry(ZipEntry("media/thumbnail.jpg")); z.write(img); z.closeEntry()
            }
        }.toByteArray()
        val input = ImportInput("ملاحظتي.sdocx", "application/octet-stream", 1000L, zip.size.toLong()) { zip.inputStream() }
        assertTrue(SamsungNotesHandler.canHandle(input))
        val out = SamsungNotesHandler.parse(input, ImportContext(File(ctx.cacheDir, "sdocx-test")))
        val c = out.candidates.single()
        assertEquals("قائمة الأعمال", c.title)
        assertTrue(c.body.contains("مراجعة ملف القضية 245 مع النقابة"))
        assertTrue(c.body.contains("Call the client tomorrow"))
        assertFalse(c.body.contains("Samsung")); assertFalse(c.body.contains("a1b2c3"))
        assertEquals(1, c.attachments.size) // the thumbnail is skipped
        assertTrue(ImportWarning.FORMATTING_LOST in c.warnings)
    }
}
