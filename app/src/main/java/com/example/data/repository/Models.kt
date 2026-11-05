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

data class ChecklistDraft(val id: String = Ids.newId(), val text: String, val checked: Boolean = false, val blockId: String? = null)

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
    val importSource: String = "",
    /** Ordered layout (v3). Null = classic layout (text, checklist, attachments). */
    val blocks: List<BlockDraft>? = null
) {
    val isBlank: Boolean get() = title.isBlank() && body.isBlank() && checklist.all { it.text.isBlank() }
}

data class BlockDraft(val id: String = Ids.newId(), val type: String, val text: String = "", val attachmentId: String? = null)

/** Block layout rules shared by editor, repository, sync and backups. */
object Blocks {
    const val SEPARATOR = "\n\n"

    /** Canonical note body = text blocks joined by a blank line (used for search, previews, sync). */
    fun joinText(blocks: List<BlockDraft>): String =
        blocks.filter { it.type == com.example.data.local.BlockType.TEXT }.joinToString(SEPARATOR) { it.text }

    fun defaultLayout(body: String, checklist: List<ChecklistDraft>, attachmentIds: List<String>): List<BlockDraft> = buildList {
        add(BlockDraft(type = com.example.data.local.BlockType.TEXT, text = body))
        if (checklist.isNotEmpty()) add(BlockDraft(id = DEFAULT_CHECKLIST, type = com.example.data.local.BlockType.CHECKLIST))
        attachmentIds.forEach { add(BlockDraft(type = com.example.data.local.BlockType.ATTACHMENT, attachmentId = it)) }
    }

    /**
     * Makes a stored layout consistent with the actual data: unknown attachments are dropped,
     * attachments missing from the layout are appended, checklist items get a checklist block.
     */
    fun reconcile(blocks: List<BlockDraft>?, body: String, checklist: List<ChecklistDraft>, attachmentIds: List<String>): List<BlockDraft> {
        if (blocks.isNullOrEmpty()) return defaultLayout(body, checklist, attachmentIds)
        val ids = attachmentIds.toSet()
        val out = blocks.filter { it.type != com.example.data.local.BlockType.ATTACHMENT || it.attachmentId in ids }.toMutableList()
        val placed = out.mapNotNull { it.attachmentId }.toSet()
        val checklistBlocks = out.filter { it.type == com.example.data.local.BlockType.CHECKLIST }.map { it.id }.toSet()
        if (checklist.any { (it.blockId ?: DEFAULT_CHECKLIST) !in checklistBlocks }) {
            checklist.mapNotNull { it.blockId ?: DEFAULT_CHECKLIST }.filter { it !in checklistBlocks }.distinct()
                .forEach { out += BlockDraft(id = it, type = com.example.data.local.BlockType.CHECKLIST) }
        }
        attachmentIds.filter { it !in placed }.forEach { out += BlockDraft(type = com.example.data.local.BlockType.ATTACHMENT, attachmentId = it) }
        if (out.none { it.type == com.example.data.local.BlockType.TEXT }) out.add(0, BlockDraft(type = com.example.data.local.BlockType.TEXT))
        return out
    }

    /** Merges adjacent text blocks and drops empty ones (except a single/last text block). */
    fun normalize(blocks: List<BlockDraft>): List<BlockDraft> {
        val merged = mutableListOf<BlockDraft>()
        for (b in blocks) {
            val last = merged.lastOrNull()
            if (b.type == com.example.data.local.BlockType.TEXT && last?.type == com.example.data.local.BlockType.TEXT) {
                merged[merged.lastIndex] = last.copy(text = listOf(last.text, b.text).filter { it.isNotEmpty() }.joinToString(SEPARATOR))
            } else merged += b
        }
        val result = merged.filterIndexed { i, b -> b.type != com.example.data.local.BlockType.TEXT || b.text.isNotEmpty() || i == merged.lastIndex }
        return result.ifEmpty { listOf(BlockDraft(type = com.example.data.local.BlockType.TEXT)) }
    }

    /** Rebuilds text blocks from [body] using per-block lengths (sync stores layout without duplicating text). */
    fun fromLayout(layout: List<Triple<String, String, Any?>>, body: String): List<BlockDraft>? {
        var pos = 0
        val out = mutableListOf<BlockDraft>()
        var firstText = true
        for ((id, type, extra) in layout) {
            when (type) {
                com.example.data.local.BlockType.TEXT -> {
                    if (!firstText) { if (!body.startsWith(SEPARATOR, pos)) return null; pos += SEPARATOR.length }
                    val len = (extra as? Number)?.toInt() ?: return null
                    if (pos + len > body.length) return null
                    out += BlockDraft(id, type, body.substring(pos, pos + len)); pos += len; firstText = false
                }
                else -> out += BlockDraft(id, type, attachmentId = extra as? String)
            }
        }
        return if (pos == body.length) out else null
    }

    const val DEFAULT_CHECKLIST = "checklist-default"
}

fun List<BlockDraft>.blocksToJson(): String = JSONArray().also { arr ->
    forEach { b -> arr.put(JSONObject().put("id", b.id).put("type", b.type).put("text", b.text).put("attachmentId", b.attachmentId ?: JSONObject.NULL)) }
}.toString()

fun blocksFromJson(json: String): List<BlockDraft>? = try {
    if (json.isBlank()) null else JSONArray(json).let { a ->
        (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
            BlockDraft(o.getString("id"), o.getString("type"), o.optString("text"), o.optString("attachmentId").takeIf { !o.isNull("attachmentId") && it.isNotBlank() })
        } }
    }
} catch (_: Exception) { null }

fun List<ChecklistItemEntity>.toDrafts() = sortedBy { it.position }.map { ChecklistDraft(it.id, it.text, it.checked, it.blockId) }

fun List<ChecklistDraft>.toJson(): String = JSONArray().also { arr ->
    forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text).put("checked", it.checked).put("blockId", it.blockId ?: JSONObject.NULL)) }
}.toString()

fun checklistFromJson(json: String): List<ChecklistDraft> = try {
    val arr = JSONArray(json.ifBlank { "[]" })
    (0 until arr.length()).map {
        val o = arr.getJSONObject(it)
        ChecklistDraft(o.optString("id").ifBlank { Ids.newId() }, o.optString("text"), o.optBoolean("checked"),
            o.optString("blockId").takeIf { !o.isNull("blockId") && it.isNotBlank() })
    }
} catch (_: Exception) {
    emptyList()
}

/** Inner plaintext of a locked note before encryption (body + checklist travel together). */
object LockedPayload {
    data class Content(val body: String, val checklist: List<ChecklistDraft>, val blocks: List<BlockDraft>?)

    fun encode(body: String, checklist: List<ChecklistDraft>, blocks: List<BlockDraft>? = null): String =
        JSONObject().put("v", if (blocks != null) 2 else 1).put("body", body).put("checklist", JSONArray(checklist.toJson()))
            .apply { if (blocks != null) put("blocks", JSONArray(blocks.blocksToJson())) }.toString()

    fun decodeContent(plain: String): Content = try {
        val o = JSONObject(plain)
        if (o.optInt("v", 0) !in 1..2) Content(plain, emptyList(), null)
        else Content(o.optString("body"), checklistFromJson(o.optJSONArray("checklist")?.toString() ?: "[]"),
            o.optJSONArray("blocks")?.let { blocksFromJson(it.toString()) })
    } catch (_: Exception) {
        // Legacy locked notes stored the raw body.
        Content(plain, emptyList(), null)
    }

    fun decode(plain: String): Pair<String, List<ChecklistDraft>> = decodeContent(plain).let { it.body to it.checklist }
}

sealed interface DecryptResult {
    data class Plain(val body: String, val checklist: List<ChecklistDraft>, val blocks: List<BlockDraft>? = null) : DecryptResult
    /** The vault key is not available on this device yet (needs passphrase). */
    data object VaultLocked : DecryptResult
    /** Legacy v1 payload created on another device; cannot be opened here. */
    data object ForeignLegacy : DecryptResult
    data object Failed : DecryptResult
}

class VaultLockedException : Exception("Vault master key is not available on this device")
