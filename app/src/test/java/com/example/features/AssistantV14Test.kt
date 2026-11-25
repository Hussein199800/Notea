package com.example.features

import androidx.test.core.app.ApplicationProvider
import com.example.core.ai.AiKeyStore
import com.example.core.ai.GeminiClient
import com.example.core.ai.NoteAssistant
import com.example.core.memory.NoteMemory
import com.example.data.local.AttachmentEntity
import com.example.data.local.NoteEntity
import com.example.data.local.NoteWithDetails
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetSocketAddress

@RunWith(RobolectricTestRunner::class)
class AssistantV14Test {
    private fun note(id: String, title: String, body: String, locked: Boolean = false, atts: List<AttachmentEntity> = emptyList()) =
        NoteWithDetails(NoteEntity(id = id, userId = "u", title = title, body = body, createdAt = 1, updatedAt = 1, isLocked = locked),
            emptyList(), emptyList(), atts, emptyList(), emptyList())

    @Test fun librarySearch_findsClosestNotes_onDevice_neverLocked() {
        val idx = NoteMemory.buildIndex(listOf(
            note("a", "قضية رقم 245", "الجلسة القادمة في محكمة البداية يوم الأحد"),
            note("b", "قائمة تسوق", "خبز وحليب"),
            note("c", "مواعيد", "موعد الجلسة مع الموكل في المكتب"),
            note("s", "سري", "الجلسة السرية", locked = true)
        ))
        val r = idx.search("متى الجلسة القادمة للقضية 245؟")
        assertEquals("a", r.first()); assertTrue("c" in r); assertFalse("b" in r); assertFalse("s" in r)
        assertTrue(idx.search("؟").isEmpty())
    }

    private fun server(handler: (String) -> String): HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { ex ->
            val body = ex.requestBody.readBytes().decodeToString()
            val resp = handler(body).toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong()); ex.responseBody.use { it.write(resp) }
        }
        start()
    }

    private fun assistant(port: Int): NoteAssistant {
        val keys = AiKeyStore(ApplicationProvider.getApplicationContext(), com.example.core.FakeWrapper())
        keys.setKey("test-key"); keys.model = "models/m"
        return NoteAssistant(GeminiClient("http://127.0.0.1:$port/v1beta"), keys)
    }

    private fun reply(text: String) = org.json.JSONObject().put("candidates", org.json.JSONArray().put(org.json.JSONObject().put("content",
        org.json.JSONObject().put("parts", org.json.JSONArray().put(org.json.JSONObject().put("text", text)))))).toString()

    @Test fun libraryAnswer_keepsOnlyValidSources_andSendsOnlyGivenNotes() {
        var sent = ""
        val srv = server { b -> sent = b; reply("""{"answer":"يوم الأحد","sources":[1,7,1]}""") }
        try {
            val a = assistant(srv.address.port).askLibrary("متى الجلسة؟", listOf("قضية 245" to "الجلسة يوم الأحد", "مواعيد" to "مكتب"))
            assertEquals("يوم الأحد", a.answer); assertEquals(listOf(1), a.sources)
            assertTrue(sent.contains("[1] قضية 245")); assertTrue(sent.contains("[2] مواعيد"))
        } finally { srv.stop(0) }
    }

    @Test fun rewrite_suggest_ask_parseCleanly() {
        val answers = ArrayDeque(listOf(
            reply("```\nنص محسّن\n```"),
            reply("""{"title":"قضية الإيجار","tags":["#عمل","قضايا"],"folder":"غير موجود"}"""),
            reply("الجواب: الأحد")
        ))
        val srv = server { answers.removeFirst() }
        try {
            val ai = assistant(srv.address.port)
            assertEquals("نص محسّن", ai.rewrite("نص", NoteAssistant.Rewrite.IMPROVE))
            val sug = ai.suggestMeta("ملاحظة عن قضية إيجار", listOf("العمل"), listOf("عمل"))
            assertEquals("قضية الإيجار", sug.title); assertEquals(listOf("عمل", "قضايا"), sug.tags)
            assertEquals(null, sug.folder) // only existing folders are accepted
            assertEquals("الجواب: الأحد", ai.ask("ملاحظة", listOf(NoteAssistant.Turn("س", "ج")), "متى؟"))
        } finally { srv.stop(0) }
    }

    @Test fun libraryDocText_skipsEncryptedAttachmentText() {
        val att = { id: String, enc: Boolean, text: String -> AttachmentEntity(id = id, noteId = "n", userId = "u", kind = "image", displayName = "x", mimeType = "image/jpeg",
            localPath = "p", sizeBytes = 1, createdAt = 1, encrypted = enc, extractedText = text) }
        val d = note("n", "t", "**نص**", atts = listOf(att("1", false, "مقروء"), att("2", true, "سري")))
        val text = com.example.ui.screens.libraryDocText(d)
        assertTrue(text.contains("نص")); assertFalse(text.contains("**")); assertTrue(text.contains("مقروء")); assertFalse(text.contains("سري"))
    }
}
