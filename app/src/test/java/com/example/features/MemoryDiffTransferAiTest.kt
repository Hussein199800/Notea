package com.example.features

import com.example.core.ai.AiError
import com.example.core.ai.GeminiClient
import com.example.core.memory.NoteMemory
import com.example.data.local.ChecklistItemEntity
import com.example.data.local.NoteEntity
import com.example.data.local.NoteWithDetails
import com.example.data.local.TagEntity
import com.example.data.transfer.DeviceTransfer
import com.example.ui.editor.TextDiff
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.net.InetSocketAddress
import java.util.Calendar

private fun note(id: String, title: String, body: String, created: Long = 0, updated: Long = 0, tags: List<String> = emptyList(),
                 checklist: List<Pair<String, Boolean>> = emptyList(), locked: Boolean = false, pinned: Boolean = false) =
    NoteWithDetails(
        NoteEntity(id = id, userId = "u", title = title, body = body, createdAt = created, updatedAt = updated, isLocked = locked, isPinned = pinned),
        checklist.mapIndexed { i, (t, c) -> ChecklistItemEntity("$id-$i", id, t, c, i) },
        tags.map { TagEntity("tag-$it", "u", it, it) }, emptyList(), emptyList()
    )

@RunWith(RobolectricTestRunner::class)
class MemoryTest {
    @Test fun related_usesArabicNormalizationAndSkipsLocked() {
        val notes = listOf(
            note("a", "خطة السفر إلى إسطنبول", "حجز الفندق في إسطنبول وتذاكر الطيران والمتاحف"),
            note("b", "ميزانية الرحلة", "تكلفة الفندق والطيران في اسطنبول"),
            note("c", "وصفة كعكة", "طحين وسكر وبيض"),
            note("d", "سري", "اسطنبول فندق طيران", locked = true),
            note("e", "مشتريات", "حليب وخبز"), note("f", "رياضة", "جري ومشي")
        )
        val idx = NoteMemory.buildIndex(notes)
        val rel = idx.related("a")
        assertEquals("b", rel.first().id)
        assertTrue(rel.none { it.id == "d" || it.id == "c" })
        assertTrue(rel.first().sharedWords.any { it.contains("اسطنبول") || it.contains("فندق") || it.contains("طيران") })
    }

    @Test fun resurface_isQuietBelowThreshold_andPicksOnThisDay() {
        val now = System.currentTimeMillis()
        val lastYear = Calendar.getInstance().apply { add(Calendar.YEAR, -2) }.timeInMillis
        val few = (1..5).map { note("n$it", "t$it", "x", now, now) }
        assertNull(NoteMemory.resurface(few, now, emptySet()))
        val many = (1..20).map { note("n$it", "t$it", "x", now - 86_400_000L * 40, now - 86_400_000L * 40) } + note("old", "ذكرى", "يوم جميل", lastYear, lastYear)
        val r = NoteMemory.resurface(many, now, emptySet())
        assertNotNull(r); assertEquals("old", r!!.noteId); assertEquals(NoteMemory.Reason.ON_THIS_DAY, r.reason); assertEquals(2, r.yearsAgo)
        assertTrue(NoteMemory.resurface(many, now, setOf("old"))?.noteId != "old")
        val withDecision = (1..20).map { note("n$it", "t$it", "x", now, now) } + note("dec", "قرار: تغيير العمل", "لأن…", now - 86_400_000L * 60, now - 86_400_000L * 60, tags = listOf("قرار"))
        assertEquals(NoteMemory.Reason.DECISION, NoteMemory.resurface(withDecision, now, emptySet())!!.reason)
        val outdated = (1..20).map { note("n$it", "t$it", "x", now, now) } + note("o", "مهام", "الموعد 2020-01-05", now, now, checklist = listOf("اتصال" to false))
        assertEquals(NoteMemory.Reason.MAYBE_OUTDATED, NoteMemory.resurface(outdated, now, emptySet())!!.reason)
    }
}

class DiffTest {
    @Test fun wordDiff_arabic() {
        val parts = TextDiff.diff("اشتريت خبزاً وحليباً اليوم", "اشتريت خبزاً وجبناً اليوم صباحاً")
        val removed = parts.filter { it.op == TextDiff.Op.REMOVED }.joinToString("") { it.text }.trim()
        val added = parts.filter { it.op == TextDiff.Op.ADDED }.joinToString("") { it.text }.trim()
        assertTrue(removed.contains("وحليباً")); assertTrue(added.contains("وجبناً")); assertTrue(added.contains("صباحاً"))
        assertEquals(TextDiff.Stats(2, 1), TextDiff.stats(parts))
        assertEquals("اشتريت خبزاً وجبناً اليوم صباحاً", parts.filter { it.op != TextDiff.Op.REMOVED }.joinToString("") { it.text })
    }
}

class TransferTest {
    @Test fun loopbackTransfer_rejectsWrongCode_thenDelivers() = runBlocking {
        val src = File.createTempFile("notea", ".bin").apply { writeBytes(ByteArray(300_000) { (it % 251).toByte() }) }
        val code = DeviceTransfer.newCode()
        val server = DeviceTransfer.Server(src, code)
        val serving = async(kotlinx.coroutines.Dispatchers.IO) { server.serve { _, _ -> } }
        delay(100)
        val out = File.createTempFile("recv", ".bin")
        val wrong = DeviceTransfer.receive(DeviceTransfer.Offer("127.0.0.1", server.port, "AAAAAAAAAAAA"), out) { _, _ -> }
        assertTrue(wrong.isFailure)
        val ok = DeviceTransfer.receive(DeviceTransfer.Offer("127.0.0.1", server.port, code), out) { _, _ -> }
        assertTrue(ok.isSuccess)
        assertTrue(serving.await())
        assertTrue(out.readBytes().contentEquals(src.readBytes()))
        val offer = DeviceTransfer.Offer("192.168.1.5", 40123, code)
        assertEquals(offer, DeviceTransfer.parse(offer.toUri()))
        assertEquals(offer, DeviceTransfer.parseManual(" 192.168.1.5:40123 ", offer.prettyCode.lowercase()))
        assertNull(DeviceTransfer.parseManual("bad", code))
    }
}

/** Gemini client against a local fake server (no network, no key). */
@RunWith(RobolectricTestRunner::class)
class GeminiClientTest {
    private fun server(handler: (String, String) -> Pair<Int, String>): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { ex ->
                val body = ex.requestBody.readBytes().decodeToString()
                assertEquals("test-key", ex.requestHeaders.getFirst("x-goog-api-key")) // key in header, never in URL
                val (code, resp) = handler(ex.requestURI.path, body)
                val bytes = resp.toByteArray(); ex.sendResponseHeaders(code, bytes.size.toLong()); ex.responseBody.use { it.write(bytes) }
            }
            start()
        }

    @Test fun picksCheapestModel_andParsesJsonOutput() {
        val srv = server { path, body ->
            if (path.endsWith("/models")) 200 to """{"models":[
                {"name":"models/gemini-2.5-pro","supportedGenerationMethods":["generateContent"]},
                {"name":"models/gemini-2.0-flash-lite","supportedGenerationMethods":["generateContent"]},
                {"name":"models/gemini-2.5-flash-lite","supportedGenerationMethods":["generateContent"]},
                {"name":"models/gemini-2.5-flash-lite-preview","supportedGenerationMethods":["generateContent"]},
                {"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]}]}"""
            else {
                assertTrue(body.contains("\"maxOutputTokens\":700"))
                200 to """{"candidates":[{"content":{"parts":[{"text":"{\"summary\":\"ملخص\",\"points\":[\"أ\",\"ب\"]}"}]}}]}"""
            }
        }
        try {
            val c = GeminiClient("http://127.0.0.1:${srv.address.port}/v1beta")
            assertEquals("models/gemini-2.5-flash-lite", c.pickModel("test-key"))
            val out = c.generate("test-key", "models/gemini-2.5-flash-lite", "sys", "نص", 700)
            assertTrue(out.contains("ملخص"))
        } finally { srv.stop(0) }
    }

    @Test fun mapsErrorsToClearMessages() {
        var response = 400 to """{"error":{"status":"INVALID_ARGUMENT","message":"API key not valid. Please pass a valid API key."}}"""
        val srv = server { _, _ -> response }
        try {
            val c = GeminiClient("http://127.0.0.1:${srv.address.port}/v1beta")
            fun expect(cls: Class<*>) = try { c.generate("test-key", "models/m", "s", "u"); fail() } catch (e: AiError) { assertTrue(cls.isInstance(e)) }
            expect(AiError.InvalidKey::class.java)
            response = 400 to """{"error":{"status":"FAILED_PRECONDITION","message":"User location is not supported for the API use."}}"""
            expect(AiError.RegionUnsupported::class.java)
            response = 429 to """{"error":{"status":"RESOURCE_EXHAUSTED","message":"Quota exceeded"}}"""
            expect(AiError.Quota::class.java)
        } finally { srv.stop(0) }
        try { GeminiClient("http://127.0.0.1:1/v1beta").generate("test-key", "m", "s", "u"); fail() } catch (e: AiError) { assertTrue(e is AiError.Offline) }
    }
}
