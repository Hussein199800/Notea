package com.example.data.legacy

/**
 * Parses the v1 storage format, where checklists/tags/attachments were hidden inside the note
 * body (`<!--AN_META_START ... AN_META_END-->`) and favourite/trash flags were encoded in the
 * `category` string (`trash:<millis>|work|fav`). Used for the Room migration and for v1 documents
 * still present in Firestore.
 */
object LegacyNoteParser {
    private const val META_START = "<!--AN_META_START"
    private const val META_END = "AN_META_END-->"

    data class ChecklistLine(val id: String, val text: String, val checked: Boolean)

    data class Parsed(
        val body: String,
        val checklist: List<ChecklistLine>,
        val tags: List<String>,
        val reminderLabel: String,
        val imageUri: String,
        val baseCategory: String,
        val isFavorite: Boolean,
        val trashedAt: Long?,
        val lockHint: String
    )

    fun hasMetadataBlock(content: String) = content.startsWith(META_START) && content.contains(META_END)

    fun parse(content: String, category: String, lockHint: String): Parsed {
        var body = content
        val checklist = mutableListOf<ChecklistLine>()
        var tags = emptyList<String>()
        var reminder = ""
        var image = ""
        if (hasMetadataBlock(content)) {
            val endIdx = content.indexOf(META_END)
            val header = content.substring(META_START.length, endIdx).trim()
            body = content.substring(endIdx + META_END.length).trimStart('\r', '\n')
            header.lines().forEach { line ->
                when {
                    line.startsWith("TAGS:") -> tags = line.removePrefix("TAGS:").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    line.startsWith("REMINDER:") -> reminder = line.removePrefix("REMINDER:").trim()
                    line.startsWith("IMAGE:") -> image = line.removePrefix("IMAGE:").trim()
                    line.startsWith("CHK:") -> {
                        val parts = line.removePrefix("CHK:").split(":", limit = 3)
                        if (parts.size == 3) checklist += ChecklistLine(parts[1], parts[2], parts[0] == "1")
                    }
                }
            }
        }
        val trashedAt = if (category.startsWith("trash:")) {
            category.substringAfter("trash:").substringBefore("|").toLongOrNull() ?: System.currentTimeMillis()
        } else if (category == "trash") System.currentTimeMillis() else null
        val raw = if (category.startsWith("trash:")) category.substringAfter("|", "personal") else category
        val base = raw.replace("|fav", "").ifBlank { "personal" }.let { if (it == "trash") "personal" else it }
        val fav = category.contains("|fav") || lockHint.endsWith("★")
        return Parsed(
            body = body,
            checklist = checklist,
            tags = tags,
            reminderLabel = reminder,
            imageUri = image,
            baseCategory = base,
            isFavorite = fav,
            trashedAt = trashedAt,
            lockHint = lockHint.removeSuffix("★").trim()
        )
    }

    /** Builds a v1-compatible category string so older clients still see fav/trash state. */
    fun buildCategory(baseCategory: String, isFavorite: Boolean, trashedAt: Long?): String {
        val base = baseCategory.ifBlank { "personal" }.take(20)
        val fav = if (isFavorite) "|fav" else ""
        return if (trashedAt != null) "trash:$trashedAt|$base$fav".take(50) else "$base$fav".take(50)
    }

    /** Legacy categories become folders (except the default "personal"). */
    val legacyFolderNames = mapOf(
        "work" to ("العمل" to "Work"),
        "ideas" to ("أفكار" to "Ideas"),
        "journal" to ("يوميات" to "Journal"),
        "secret" to ("خاص" to "Private")
    )

    fun legacyFolderId(category: String) = "legacy-$category"
}
