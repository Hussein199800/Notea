package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.security.MessageDigest

@IgnoreExtraProperties
data class UserSettings(
    val userId: String = "",
    val appLockEnabled: Boolean = false,
    val appPinHash: String = "",
    val languageCode: String = "ar",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Note(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = "personal",
    val colorIndex: Long = 0L,
    @get:PropertyName("isPinned") @set:PropertyName("isPinned")
    var isPinned: Boolean = false,
    @get:PropertyName("isLocked") @set:PropertyName("isLocked")
    var isLocked: Boolean = false,
    val notePinHash: String = "",
    val lockHint: String = "",
    @get:PropertyName("isArchived") @set:PropertyName("isArchived")
    var isArchived: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isTrashed: Boolean
        get() = category.startsWith("trash:") || category == "trash"

    val trashedAtMillis: Long?
        get() = if (category.startsWith("trash:")) {
            category.substringAfter("trash:").substringBefore("|").toLongOrNull()
        } else null

    val isFavorite: Boolean
        get() = category.contains("|fav") || lockHint.endsWith("★")

    val baseCategory: String
        get() {
            val raw = if (category.startsWith("trash:")) {
                category.substringAfter("|", "personal")
            } else {
                category
            }
            return raw.replace("|fav", "").ifBlank { "personal" }
        }

    fun cleanLockHint(): String = lockHint.removeSuffix("★").trim()

    fun extractMetadata(): NoteRichMetadata = NoteRichMetadata.parse(content)

    fun cleanBodyText(): String = NoteRichMetadata.parse(content).bodyText

    companion object {
        const val TRASH_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1000L // 30 days

        fun buildCategoryString(
            baseCategory: String,
            isFavorite: Boolean,
            isTrashed: Boolean,
            trashedAtMillis: Long? = null
        ): String {
            val cleanBase = baseCategory
                .removePrefix("trash:")
                .replace("|fav", "")
                .substringAfter("|")
                .ifBlank { "personal" }
                .take(20)
            val favSuffix = if (isFavorite) "|fav" else ""
            return if (isTrashed) {
                val ts = trashedAtMillis ?: System.currentTimeMillis()
                "trash:$ts|$cleanBase$favSuffix".take(50)
            } else {
                "$cleanBase$favSuffix".take(50)
            }
        }
    }
}

data class ChecklistItem(
    val id: String,
    val text: String,
    val isChecked: Boolean
)

data class NoteRichMetadata(
    val bodyText: String,
    val checklistItems: List<ChecklistItem> = emptyList(),
    val reminderLabel: String = "",
    val imageUri: String = "",
    val voiceMemoLabel: String = "",
    val tags: List<String> = emptyList()
) {
    fun serialize(): String {
        val hasExtras = checklistItems.isNotEmpty() ||
            reminderLabel.isNotBlank() ||
            imageUri.isNotBlank() ||
            voiceMemoLabel.isNotBlank() ||
            tags.isNotEmpty()
        if (!hasExtras) return bodyText.take(20000)

        val sb = StringBuilder()
        sb.appendLine("<!--AN_META_START")
        if (tags.isNotEmpty()) {
            sb.appendLine("TAGS:${tags.joinToString(",")}")
        }
        if (reminderLabel.isNotBlank()) {
            sb.appendLine("REMINDER:$reminderLabel")
        }
        if (imageUri.isNotBlank()) {
            sb.appendLine("IMAGE:$imageUri")
        }
        if (voiceMemoLabel.isNotBlank()) {
            sb.appendLine("VOICE:$voiceMemoLabel")
        }
        checklistItems.forEach { item ->
            val mark = if (item.isChecked) "1" else "0"
            sb.appendLine("CHK:$mark:${item.id}:${item.text.replace("\n", " ")}")
        }
        sb.appendLine("AN_META_END-->")
        sb.append(bodyText)
        return sb.toString().take(20000)
    }

    companion object {
        fun parse(rawContent: String): NoteRichMetadata {
            if (!rawContent.startsWith("<!--AN_META_START")) {
                return NoteRichMetadata(bodyText = rawContent)
            }
            val endIdx = rawContent.indexOf("AN_META_END-->")
            if (endIdx == -1) {
                return NoteRichMetadata(bodyText = rawContent)
            }
            val headerBlock = rawContent.substring("<!--AN_META_START".length, endIdx).trim()
            val body = rawContent.substring(endIdx + "AN_META_END-->".length).trimStart('\r', '\n')

            val checklist = mutableListOf<ChecklistItem>()
            var reminder = ""
            var image = ""
            var voice = ""
            var tags = emptyList<String>()

            headerBlock.lines().forEach { line ->
                when {
                    line.startsWith("TAGS:") -> {
                        tags = line.removePrefix("TAGS:")
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                    }
                    line.startsWith("REMINDER:") -> {
                        reminder = line.removePrefix("REMINDER:").trim()
                    }
                    line.startsWith("IMAGE:") -> {
                        image = line.removePrefix("IMAGE:").trim()
                    }
                    line.startsWith("VOICE:") -> {
                        voice = line.removePrefix("VOICE:").trim()
                    }
                    line.startsWith("CHK:") -> {
                        val parts = line.removePrefix("CHK:").split(":", limit = 3)
                        if (parts.size == 3) {
                            checklist.add(
                                ChecklistItem(
                                    id = parts[1],
                                    text = parts[2],
                                    isChecked = parts[0] == "1"
                                )
                            )
                        }
                    }
                }
            }
            return NoteRichMetadata(
                bodyText = body,
                checklistItems = checklist,
                reminderLabel = reminder,
                imageUri = image,
                voiceMemoLabel = voice,
                tags = tags
            )
        }
    }
}

enum class NoteCategory(val key: String) {
    ALL("all"),
    PERSONAL("personal"),
    WORK("work"),
    IDEAS("ideas"),
    JOURNAL("journal"),
    SECRET("secret")
}

enum class NoteSortOption {
    MODIFIED_DESC,
    CREATED_DESC,
    TITLE_ASC,
    COLOR,
    PINNED
}

enum class SyncStatus {
    SYNCED,
    SYNCING,
    OFFLINE,
    ERROR
}

enum class AutoLockTimeout(val minutes: Int) {
    IMMEDIATELY(0),
    ONE_MINUTE(1),
    FIVE_MINUTES(5),
    FIFTEEN_MINUTES(15)
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class LanguagePreference(val code: String) {
    ARABIC("ar"),
    ENGLISH("en"),
    SYSTEM("system")
}

object PinSecurity {
    fun hashPin(pin: String): String {
        val trimmed = pin.trim()
        if (trimmed.isEmpty()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(trimmed.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(inputPin: String, expectedHash: String): Boolean {
        if (expectedHash.isBlank()) return false
        return hashPin(inputPin) == expectedHash
    }
}
