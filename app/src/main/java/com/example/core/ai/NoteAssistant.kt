package com.example.core.ai

import org.json.JSONObject

/**
 * The three assistant actions that are actually worth their (small) cost, plus an on-demand
 * comparison of two notes. Each call sends ONE note (or two), capped at [MAX_INPUT_CHARS].
 * Whole-library analysis is deliberately not offered (too many tokens).
 */
class NoteAssistant(private val client: GeminiClient, private val keys: AiKeyStore) {
    enum class Action { SUMMARY, TASKS, PLAN }

    data class Summary(val summary: String, val points: List<String>)
    data class Task(val text: String, val due: String?)
    data class Plan(val goal: String, val steps: List<String>)
    data class Comparison(val contradictions: List<String>, val outdated: List<String>, val note: String)

    private fun model(key: String): String = keys.model ?: client.pickModel(key).also { keys.model = it }

    private fun call(system: String, user: String, maxOut: Int): JSONObject {
        val key = keys.key() ?: throw AiError.InvalidKey()
        val raw = client.generate(key, model(key), system, user, maxOut, jsonOutput = true)
        return JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
    }

    private fun lang(text: String) = if (text.count { it in '\u0600'..'\u06FF' } > text.length / 10) "Arabic" else "the same language as the note"

    fun prepare(title: String, body: String): Pair<String, Boolean> {
        val full = (if (title.isNotBlank()) "# $title\n\n" else "") + body
        return if (full.length > MAX_INPUT_CHARS) full.take(MAX_INPUT_CHARS) to true else full to false
    }

    fun summarize(note: String): Summary {
        val o = call(
            "You summarize personal notes. Reply ONLY with JSON {\"summary\": string, \"points\": [string]}. " +
                "Write in ${lang(note)}. summary: 2-4 sentences. points: at most 6 short key points. Do not invent facts.",
            note, 700
        )
        return Summary(o.optString("summary"), o.optJSONArray("points").strings())
    }

    fun extractTasks(note: String): List<Task> {
        val o = call(
            "Extract actionable tasks from the note. Reply ONLY with JSON {\"tasks\": [{\"text\": string, \"due\": string|null}]}. " +
                "Write tasks in ${lang(note)}, short imperative phrases, max 15. due: ISO date (YYYY-MM-DD) only if the note states one, else null.",
            note, 700
        )
        val arr = o.optJSONArray("tasks") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { t -> t.optString("text").takeIf { it.isNotBlank() }?.let { Task(it, t.optString("due").takeIf { d -> d.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }) } }
        }
    }

    fun plan(note: String): Plan {
        val o = call(
            "Turn the note into a clear, practical action plan. Reply ONLY with JSON {\"goal\": string, \"steps\": [string]}. " +
                "Write in ${lang(note)}. 3-10 ordered steps, each one concrete sentence. Use only information from the note.",
            note, 800
        )
        return Plan(o.optString("goal"), o.optJSONArray("steps").strings())
    }

    /**
     * Accurate reading of ONE image (user-initiated, after consent). The image is downscaled to
     * at most [IMAGE_MAX_SIDE] px JPEG first to keep the cost low. Returns plain text ("" if none).
     */
    fun readImage(file: java.io.File): String {
        val key = keys.key() ?: throw AiError.InvalidKey()
        val jpeg = ImageForAi.prepare(file) ?: throw AiError.Other("unreadable image")
        val text = client.generateWithImage(
            key, model(key),
            "You are an exact OCR engine. Transcribe ALL text in the image exactly as written, in its original language " +
                "(Arabic stays Arabic, digits as printed). Keep the reading order of the page (right-to-left for Arabic). " +
                "Tables: one row per line, cells separated by \" | \", keeping the printed order of the columns. " +
                "Do not translate, summarize, explain or add anything. If there is no readable text, reply with nothing.",
            "Transcribe this image.", jpeg, "image/jpeg", 4000
        )
        return text.trim().removePrefix("```").removeSuffix("```").trim()
    }

    // ------------------------------------------------------------------ v1.4
    data class Turn(val question: String, val answer: String)

    /** Free question about ONE note. Recent turns are included (capped) so follow-ups work. */
    fun ask(note: String, history: List<Turn>, question: String): String {
        val key = keys.key() ?: throw AiError.InvalidKey()
        val past = history.takeLast(4).joinToString("\n\n") { "Q: ${it.question.take(500)}\nA: ${it.answer.take(1200)}" }
        val user = "NOTE:\n$note\n\n" + (if (past.isNotBlank()) "EARLIER:\n$past\n\n" else "") + "QUESTION: ${question.take(1000)}"
        return client.generate(key, model(key),
            "You answer questions about the user's note. Use only the note (and earlier answers). If the note doesn't contain the answer, say so briefly. " +
                "Reply in ${lang(question)}, short and direct, plain text (simple '-' lists allowed).",
            user, 900, jsonOutput = false).trim()
    }

    enum class Rewrite { IMPROVE, FIX, SHORTEN, EXPAND, TRANSLATE, FORMAL, LEGAL }

    /** Writing tools on a selection or a paragraph. Returns only the rewritten text. */
    fun rewrite(text: String, mode: Rewrite): String {
        val key = keys.key() ?: throw AiError.InvalidKey()
        val input = text.take(MAX_INPUT_CHARS)
        val arabic = lang(input) == "Arabic"
        val task = when (mode) {
            Rewrite.IMPROVE -> "Improve clarity and flow. Keep the meaning, language and length roughly the same."
            Rewrite.FIX -> "Fix spelling, grammar and punctuation only. Change nothing else."
            Rewrite.SHORTEN -> "Make it about half as long, keeping every important fact."
            Rewrite.EXPAND -> "Expand it with more detail and clearer explanation, without inventing facts."
            Rewrite.TRANSLATE -> if (arabic) "Translate it into English." else "Translate it into Modern Standard Arabic."
            Rewrite.FORMAL -> "Rewrite it in a formal, polite register suitable for official correspondence."
            Rewrite.LEGAL -> "Rewrite it in precise legal language (as a lawyer would draft it), keeping all facts, names, numbers and dates exactly."
        }
        val out = client.generate(key, model(key),
            "You are a writing tool. $task Keep Markdown markers (**, ##, -, >) if present. Reply with ONLY the resulting text, no preface or quotes." +
                (if (mode != Rewrite.TRANSLATE) " Keep the original language." else ""),
            input, (input.length * 2 / 3 + 300).coerceIn(300, 4000), jsonOutput = false)
        return out.trim().removePrefix("```").removeSuffix("```").trim()
    }

    data class Suggestion(val title: String, val tags: List<String>, val folder: String?)

    /** Title, tags and the best existing folder for one note. */
    fun suggestMeta(note: String, folders: List<String>, existingTags: List<String>): Suggestion {
        val o = call(
            "Suggest metadata for a personal note. Reply ONLY with JSON {\"title\": string, \"tags\": [string], \"folder\": string|null}. " +
                "Write in ${lang(note)}. title: at most 8 words. tags: 1-4 short tags (prefer these existing ones when they fit: ${existingTags.take(40).joinToString(", ")}). " +
                "folder: exactly one of [${folders.take(40).joinToString(", ")}] or null if none fits.",
            note, 300
        )
        val folder = o.optString("folder").takeIf { f -> f.isNotBlank() && f != "null" && folders.any { it == f } }
        return Suggestion(o.optString("title").trim(), o.optJSONArray("tags").strings().map { it.removePrefix("#").trim() }.filter { it.isNotBlank() }.take(4), folder)
    }

    data class LibraryAnswer(val answer: String, val sources: List<Int>)

    /** Answers from the few notes retrieved ON THE DEVICE (never the whole library). [docs] = (title, text). */
    fun askLibrary(question: String, docs: List<Pair<String, String>>): LibraryAnswer {
        val per = (MAX_INPUT_CHARS / docs.size.coerceAtLeast(1)).coerceAtMost(3000)
        val user = docs.mapIndexed { i, (t, b) -> "[${i + 1}] $t\n${b.take(per)}" }.joinToString("\n\n") + "\n\nQUESTION: ${question.take(1000)}"
        val o = call(
            "Answer the user's question using ONLY the numbered notes. Reply ONLY with JSON {\"answer\": string, \"sources\": [int]}. " +
                "Write in ${lang(question)}, short and direct. sources: the numbers of the notes you used. " +
                "If the notes don't contain the answer, say so in answer and return an empty sources array.",
            user, 900
        )
        val src = o.optJSONArray("sources")?.let { a -> (0 until a.length()).mapNotNull { a.optInt(it, -1).takeIf { n -> n in 1..docs.size } } }.orEmpty()
        return LibraryAnswer(o.optString("answer").trim(), src.distinct())
    }

    data class Transcript(val text: String, val summary: String)

    /** Transcribes ONE recording (≤ [MAX_AUDIO_BYTES]) and adds a short summary. */
    fun transcribe(audio: java.io.File, mime: String): Transcript {
        val key = keys.key() ?: throw AiError.InvalidKey()
        if (audio.length() > MAX_AUDIO_BYTES) throw AiError.Other("too long")
        val raw = client.generateWithMedia(key, model(key),
            "Transcribe the recording exactly, in its spoken language (Arabic stays Arabic; keep dialect words). Then summarize it. " +
                "Reply ONLY with JSON {\"transcript\": string, \"summary\": string}. summary: 1-3 sentences in the recording's language.",
            "Transcribe this recording.", audio.readBytes(), mime, 8000, jsonOutput = true)
        val o = org.json.JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        return Transcript(o.optString("transcript").trim(), o.optString("summary").trim())
    }

    fun compare(a: String, b: String): Comparison {
        val o = call(
            "Compare two personal notes. Reply ONLY with JSON {\"contradictions\": [string], \"outdated\": [string], \"note\": string}. " +
                "Write in ${lang(a + b)}. contradictions: statements that conflict between the notes (max 5). " +
                "outdated: information in one note that the other seems to update (max 5). note: one sentence. Empty arrays if none.",
            "NOTE A:\n$a\n\nNOTE B:\n$b", 600
        )
        return Comparison(o.optJSONArray("contradictions").strings(), o.optJSONArray("outdated").strings(), o.optString("note"))
    }

    private fun org.json.JSONArray?.strings(): List<String> = this?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()

    companion object {
        /** ≈ 3-4k tokens of Arabic text: one note costs a fraction of a cent on paid plans. */
        const val MAX_INPUT_CHARS = 12_000
        const val IMAGE_MAX_SIDE = 2000
        /** ~15 minutes of the app's AAC recordings; inline requests must stay below 20 MB. */
        const val MAX_AUDIO_BYTES = 14L * 1024 * 1024
        /** Notes sent for one library question (picked on the device). */
        const val LIBRARY_NOTES = 5
        /** Shorter notes don't benefit from the assistant, so it isn't offered. */
        const val MIN_NOTE_CHARS = 280
    }
}

/** Downscales an image to a JPEG small enough for one AI request. */
object ImageForAi {
    fun prepare(file: java.io.File, maxSide: Int = NoteAssistant.IMAGE_MAX_SIDE): ByteArray? {
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.path, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        val out = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        return java.io.ByteArrayOutputStream().use { s -> out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, s); s.toByteArray() }
            .also { if (out !== bmp) out.recycle(); bmp.recycle() }
    }
}
