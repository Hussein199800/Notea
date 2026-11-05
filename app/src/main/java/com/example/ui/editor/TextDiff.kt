package com.example.ui.editor

/**
 * Word-level diff (LCS) between two versions of a note, used by the version history to show what
 * changed. Tokens keep their trailing whitespace so the result re-joins into readable text.
 * Large inputs fall back to a line-level diff to stay fast.
 */
object TextDiff {
    enum class Op { SAME, ADDED, REMOVED }
    data class Part(val op: Op, val text: String)
    data class Stats(val added: Int, val removed: Int)

    private val tokenRegex = Regex("\\S+\\s*|\\s+")

    fun diff(old: String, new: String): List<Part> {
        val wordsA = tokenRegex.findAll(old).map { it.value }.toList()
        val wordsB = tokenRegex.findAll(new).map { it.value }.toList()
        val (a, b) = if (wordsA.size.toLong() * wordsB.size > MAX_CELLS) {
            old.split('\n').map { it + "\n" } to new.split('\n').map { it + "\n" }
        } else wordsA to wordsB
        if (a.size.toLong() * b.size > MAX_CELLS) return listOf(Part(Op.REMOVED, old), Part(Op.ADDED, new))
        // Trim common prefix/suffix first (most edits are local).
        var start = 0
        while (start < a.size && start < b.size && a[start] == b[start]) start++
        var endA = a.size; var endB = b.size
        while (endA > start && endB > start && a[endA - 1] == b[endB - 1]) { endA--; endB-- }
        val midA = a.subList(start, endA); val midB = b.subList(start, endB)
        val n = midA.size; val m = midB.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
            dp[i][j] = if (key(midA[i]) == key(midB[j])) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val out = mutableListOf<Part>()
        fun add(op: Op, t: String) { if (out.isNotEmpty() && out.last().op == op) out[out.lastIndex] = Part(op, out.last().text + t) else out += Part(op, t) }
        a.subList(0, start).forEach { add(Op.SAME, it) }
        var i = 0; var j = 0
        while (i < n && j < m) {
            when {
                key(midA[i]) == key(midB[j]) -> { add(Op.SAME, midB[j]); i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> { add(Op.REMOVED, midA[i]); i++ }
                else -> { add(Op.ADDED, midB[j]); j++ }
            }
        }
        while (i < n) add(Op.REMOVED, midA[i++])
        while (j < m) add(Op.ADDED, midB[j++])
        a.subList(endA, a.size).forEach { add(Op.SAME, it) }
        return out
    }

    fun stats(parts: List<Part>) = Stats(
        added = parts.filter { it.op == Op.ADDED }.sumOf { words(it.text) },
        removed = parts.filter { it.op == Op.REMOVED }.sumOf { words(it.text) }
    )

    private fun words(t: String) = t.split(Regex("\\s+")).count { it.isNotBlank() }
    private fun key(token: String) = token.trimEnd()
    private const val MAX_CELLS = 4_000_000L
}
