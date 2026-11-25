package com.example.core.memory

import com.example.core.text.ArabicNormalizer
import com.example.data.local.NoteWithDetails
import com.example.ui.editor.MarkdownEngine
import java.util.Calendar
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Notea Memory — entirely on device, no AI, no tokens:
 * - related notes: TF-IDF cosine similarity over Arabic-normalized words (light prefix stemming);
 * - one quiet "from your memory" suggestion per day (on this day, past decisions, possibly outdated
 *   notes, forgotten important notes).
 * Locked and trashed notes are never analysed.
 */
object NoteMemory {
    data class Related(val id: String, val title: String, val score: Double, val sharedWords: List<String>)

    enum class Reason { ON_THIS_DAY, DECISION, MAYBE_OUTDATED, FORGOTTEN }
    data class Resurface(val noteId: String, val title: String, val preview: String, val reason: Reason, val yearsAgo: Int = 0)

    /** Below this many notes the feature stays silent (it would only be noise). */
    const val MIN_NOTES = 15

    private val stop = setOf(
        "في", "من", "على", "الى", "علي", "عن", "مع", "هذا", "هذه", "ذلك", "تلك", "التي", "الذي", "الذين", "كان", "كانت", "يكون",
        "ان", "او", "ثم", "قد", "لا", "ما", "لم", "لن", "هو", "هي", "هم", "انا", "نحن", "انت", "كل", "بعد", "قبل", "عند", "حتي",
        "اذا", "لكن", "يا", "ايضا", "جدا", "بين", "غير", "حيث", "لقد", "عليه", "فيه", "منه", "به", "له", "لها", "اي", "انه", "التي",
        "the", "and", "for", "with", "that", "this", "from", "are", "was", "were", "have", "has", "you", "your", "not", "but",
        "our", "all", "can", "will", "about", "into", "then", "than", "they", "them", "there", "what", "when", "which"
    )
    private val prefixes = listOf("وال", "بال", "كال", "فال", "لل", "ال")

    fun stem(token: String): String {
        var t = token
        for (p in prefixes) if (t.startsWith(p) && t.length - p.length >= 3) { t = t.substring(p.length); break }
        if (t.length > 4 && (t.startsWith("و") || t.startsWith("ف"))) t = t.substring(1).let { r -> prefixes.firstOrNull { r.startsWith(it) && r.length - it.length >= 3 }?.let { r.substring(it.length) } ?: r }
        return t
    }

    fun terms(text: String): Map<String, Int> =
        ArabicNormalizer.tokens(text).asSequence()
            .map { stem(it) }
            .filter { it.length >= 3 && it !in stop && !it.all(Char::isDigit) }
            .groupingBy { it }.eachCount()

    private fun docText(d: NoteWithDetails): String = buildString {
        val n = d.note
        append(n.title).append(' ').append(n.title).append(' ') // title counts double
        append(MarkdownEngine.plain(n.body)).append(' ')
        d.checklist.forEach { append(it.text).append(' ') }
        d.tags.forEach { append(it.name).append(' ').append(it.name).append(' ') }
        d.attachments.forEach { append(it.extractedText.take(4000)).append(' ').append(it.linkTitle).append(' ') }
    }

    class Index internal constructor(
        private val vectors: Map<String, Map<String, Double>>,
        private val titles: Map<String, String>
    ) {
        /** Notes that best match a free-text question (on-device retrieval for the library assistant). */
        fun search(query: String, k: Int = 5): List<String> {
            val q = terms(query).keys
            if (q.isEmpty()) return emptyList()
            val qn = ArabicNormalizer.normalize(query)
            return vectors.asSequence().map { (id, w) ->
                var score = q.sumOf { t -> w[t] ?: 0.0 }
                val title = ArabicNormalizer.normalize(titles[id].orEmpty())
                if (title.isNotBlank() && q.any { title.contains(it) }) score += 0.25
                if (title.isNotBlank() && qn.contains(title)) score += 0.5
                id to score
            }.filter { it.second > 0.0 }.sortedByDescending { it.second }.take(k).map { it.first }.toList()
        }

        fun related(noteId: String, k: Int = 5, minScore: Double = 0.08): List<Related> {
            val v = vectors[noteId] ?: return emptyList()
            return vectors.asSequence().filter { it.key != noteId }.map { (id, w) ->
                var dot = 0.0
                val shared = mutableListOf<Pair<String, Double>>()
                val (small, big) = if (v.size < w.size) v to w else w to v
                for ((t, x) in small) { val y = big[t] ?: continue; dot += x * y; shared += t to x * y }
                Related(id, titles[id].orEmpty(), dot, shared.sortedByDescending { it.second }.take(4).map { it.first })
            }.filter { it.score >= minScore }.sortedByDescending { it.score }.take(k).toList()
        }
    }

    fun buildIndex(notes: List<NoteWithDetails>): Index {
        val docs = notes.filter { !it.note.isLocked && it.note.trashedAt == null && !it.note.deleted }
            .associate { it.note.id to terms(docText(it)) }
        val df = HashMap<String, Int>()
        docs.values.forEach { t -> t.keys.forEach { df[it] = (df[it] ?: 0) + 1 } }
        val n = docs.size.coerceAtLeast(1).toDouble()
        val vectors = docs.mapValues { (_, tf) ->
            val raw = tf.mapValues { (t, c) -> (1 + ln(c.toDouble())) * ln(1 + n / (df[t] ?: 1)) }
                // terms present in (almost) every note carry no signal
                .filter { (t, _) -> (df[t] ?: 0) < n * 0.5 || n < 4 }
            val norm = sqrt(raw.values.sumOf { it * it }).takeIf { it > 0 } ?: 1.0
            raw.mapValues { it.value / norm }
        }
        return Index(vectors, notes.associate { it.note.id to it.note.title })
    }

    private val dateRegex = Regex("\\b(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})\\b|\\b(\\d{1,2})[-/.](\\d{1,2})[-/.](20\\d{2})\\b")

    /** Latest date written in the text (yyyy-mm-dd or dd/mm/yyyy), if any. */
    fun latestDateIn(text: String): Long? = dateRegex.findAll(ArabicNormalizer.normalize(text)).mapNotNull { m ->
        val (y, mo, d) = if (m.groupValues[1].isNotEmpty()) Triple(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        else Triple(m.groupValues[6], m.groupValues[5], m.groupValues[4])
        runCatching { Calendar.getInstance().apply { set(y.toInt(), mo.toInt() - 1, d.toInt(), 23, 59, 0) }.timeInMillis }.getOrNull()
    }.maxOrNull()

    /**
     * One suggestion for today (stable during the day), or null. [dismissed] are note ids hidden today.
     */
    fun resurface(notes: List<NoteWithDetails>, now: Long, dismissed: Set<String>): Resurface? {
        val pool = notes.filter { !it.note.isLocked && it.note.trashedAt == null && !it.note.isArchived && it.note.id !in dismissed }
        if (pool.size < MIN_NOTES) return null
        val today = Calendar.getInstance().apply { timeInMillis = now }
        fun cal(t: Long) = Calendar.getInstance().apply { timeInMillis = t }
        fun preview(d: NoteWithDetails) = MarkdownEngine.plain(d.note.body).take(160)
        val day = 86_400_000L
        val dayIndex = (now / day).toInt()
        fun pick(list: List<NoteWithDetails>) = list.sortedBy { it.note.id }.let { if (it.isEmpty()) null else it[Math.floorMod(dayIndex, it.size)] }

        // Same calendar date (month + day, so leap years don't shift it) in an earlier year.
        pool.filter { val c = cal(it.note.createdAt); c.get(Calendar.MONTH) == today.get(Calendar.MONTH) && c.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH) && c.get(Calendar.YEAR) < today.get(Calendar.YEAR) }
            .let(::pick)?.let { return Resurface(it.note.id, it.note.title, preview(it), Reason.ON_THIS_DAY, today.get(Calendar.YEAR) - cal(it.note.createdAt).get(Calendar.YEAR)) }

        val decisionWords = setOf("قرار", "قررت", "decision", "decided")
        pool.filter { d ->
            now - d.note.updatedAt > 30 * day &&
                (d.tags.any { ArabicNormalizer.normalize(it.name) in decisionWords } || decisionWords.any { w -> ArabicNormalizer.normalize(d.note.title).contains(w) })
        }.let(::pick)?.let { return Resurface(it.note.id, it.note.title, preview(it), Reason.DECISION) }

        pool.filter { d ->
            d.checklist.any { !it.checked } && (latestDateIn(d.note.body + " " + d.note.title)?.let { it < now - day } == true)
        }.let(::pick)?.let { return Resurface(it.note.id, it.note.title, preview(it), Reason.MAYBE_OUTDATED) }

        pool.filter { (it.note.isPinned || it.note.isFavorite) && now - it.note.updatedAt > 180 * day }
            .let(::pick)?.let { return Resurface(it.note.id, it.note.title, preview(it), Reason.FORGOTTEN) }
        return null
    }
}
