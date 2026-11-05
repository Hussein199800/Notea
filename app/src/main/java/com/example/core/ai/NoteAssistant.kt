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
        /** Shorter notes don't benefit from the assistant, so it isn't offered. */
        const val MIN_NOTE_CHARS = 280
    }
}
