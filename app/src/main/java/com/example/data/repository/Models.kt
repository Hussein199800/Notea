package com.example.data.repository

import com.example.data.local.ChecklistItemEntity
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

object Ids {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val rnd = SecureRandom()

    /** 20-char id compatible with Firestore document ids and the `isValidId` rule. */
    fun newId(): String = buildString(20) { repeat(20) { append(ALPHABET[rnd.nextInt(ALPHABET.length)]) } }
}

data class ChecklistDraft(val id: String = Ids.newId(), val text: String, val checked: Boolean = false)

/** Editable, always-plaintext view of a note. Encryption happens inside the repository. */
data class NoteDraft(
    val id: String,
    val title: String = "",
    val body: String = "",
    val colorIndex: Int = 0,
    val folderId: String? = null,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val trashedAt: Long? = null,
    val isLocked: Boolean = false,
    val lockPinHash: String = "",
    val lockHint: String = "",
    val tags: List<String> = emptyList(),
    val checklist: List<ChecklistDraft> = emptyList(),
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val importSource: String = ""
) {
    val isBlank: Boolean get() = title.isBlank() && body.isBlank() && checklist.all { it.text.isBlank() }
}

fun List<ChecklistItemEntity>.toDrafts() = sortedBy { it.position }.map { ChecklistDraft(it.id, it.text, it.checked) }

fun List<ChecklistDraft>.toJson(): String = JSONArray().also { arr ->
    forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text).put("checked", it.checked)) }
}.toString()

fun checklistFromJson(json: String): List<ChecklistDraft> = try {
    val arr = JSONArray(json.ifBlank { "[]" })
    (0 until arr.length()).map {
        val o = arr.getJSONObject(it)
        ChecklistDraft(o.optString("id").ifBlank { Ids.newId() }, o.optString("text"), o.optBoolean("checked"))
    }
} catch (_: Exception) {
    emptyList()
}

/** Inner plaintext of a locked note before encryption (body + checklist travel together). */
object LockedPayload {
    fun encode(body: String, checklist: List<ChecklistDraft>): String =
        JSONObject().put("v", 1).put("body", body).put("checklist", JSONArray(checklist.toJson())).toString()

    fun decode(plain: String): Pair<String, List<ChecklistDraft>> = try {
        val o = JSONObject(plain)
        if (o.optInt("v", 0) != 1) plain to emptyList()
        else o.optString("body") to checklistFromJson(o.optJSONArray("checklist")?.toString() ?: "[]")
    } catch (_: Exception) {
        // Legacy locked notes stored the raw body.
        plain to emptyList()
    }
}

sealed interface DecryptResult {
    data class Plain(val body: String, val checklist: List<ChecklistDraft>) : DecryptResult
    /** The vault key is not available on this device yet (needs passphrase). */
    data object VaultLocked : DecryptResult
    /** Legacy v1 payload created on another device; cannot be opened here. */
    data object ForeignLegacy : DecryptResult
    data object Failed : DecryptResult
}

class VaultLockedException : Exception("Vault master key is not available on this device")
