package com.example.data.importer

import com.example.data.local.NoteEntity
import com.example.data.repository.ChecklistDraft
import java.io.File
import java.io.InputStream

/** Where the files come from. Parsing is format based; the source only adds guidance and a label. */
enum class ImportSource(val key: String) { SAMSUNG("samsung"), XIAOMI("xiaomi"), HUAWEI("huawei"), FILES("files"), SHARE("share"), BACKUP("notea") }

enum class ImportWarning {
    /** Some formatting (tables, fonts, colours, drawings) could not be converted. */
    FORMATTING_LOST,
    /** Text could not be extracted (handwriting, scanned PDF, image); the original file is attached. */
    TEXT_NOT_EXTRACTED,
    /** An image or file referenced by the note was not found. */
    MISSING_RESOURCE,
    /** File type is not supported; it was skipped. */
    UNSUPPORTED,
    /** The file is damaged or could not be read. */
    UNREADABLE,
    /** Locked note from a backup: needs the vault passphrase of the original account to open. */
    LOCKED_CONTENT,
    /** Archive limits reached (too many entries / too large); remaining items skipped. */
    TRUNCATED,
    /** Image format can't be decoded on this device; the original file is kept as an attachment. */
    IMAGE_KEPT_AS_FILE
}

data class ImportedAttachment(
    val kind: String,
    val name: String,
    val mime: String,
    val file: File,
    val extractedText: String = "",
    val encrypted: Boolean = false,
    val durationMs: Long = 0L,
    val url: String = "",
    val linkTitle: String = "",
    val linkDescription: String = "",
    val createdAt: Long? = null,
    /** Id in the source (backups): kept when free so block layouts stay valid. */
    val originalId: String? = null
) {
    fun open(): InputStream = file.inputStream()
}

enum class DuplicateKind { NONE, EXACT, SIMILAR }

enum class ImportAction { IMPORT, SKIP, MERGE, KEEP_BOTH }

data class ImportCandidate(
    val key: String,
    val sourceName: String,
    val title: String,
    val body: String,
    val checklist: List<ChecklistDraft> = emptyList(),
    val tags: List<String> = emptyList(),
    val folderName: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val trashedAt: Long? = null,
    val colorIndex: Int = 0,
    val attachments: List<ImportedAttachment> = emptyList(),
    val warnings: Set<ImportWarning> = emptySet(),
    /** Present for locked notes restored from a .notea backup: the stored (encrypted) form. */
    val storedLocked: NoteEntity? = null,
    val reminderAt: Long? = null,
    /** Ordered layout from a .notea backup (attachment ids refer to [ImportedAttachment.originalId]). */
    val blocks: List<com.example.data.repository.BlockDraft>? = null,
    val duplicate: DuplicateKind = DuplicateKind.NONE,
    val duplicateOfId: String? = null,
    val action: ImportAction = ImportAction.IMPORT
) {
    val isEmpty get() = title.isBlank() && body.isBlank() && checklist.isEmpty() && attachments.isEmpty() && storedLocked == null
}

/** A file to import (from SAF, a share intent or an archive entry). */
class ImportInput(
    val name: String,
    val mime: String,
    val lastModified: Long?,
    val sizeBytes: Long,
    private val opener: () -> InputStream
) {
    fun open(): InputStream = opener()
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
}

data class ImportIssue(val sourceName: String, val warning: ImportWarning, val detail: String = "")

data class ImportPreview(
    val source: ImportSource,
    val candidates: List<ImportCandidate>,
    val issues: List<ImportIssue>,
    /** For encrypted backups: info needed before restoring. */
    val backupInfo: com.example.data.backup.BackupManifest? = null
) {
    val noteCount get() = candidates.count { it.action != ImportAction.SKIP }
    val attachmentCount get() = candidates.filter { it.action != ImportAction.SKIP }.sumOf { it.attachments.size }
    val duplicateCount get() = candidates.count { it.duplicate != DuplicateKind.NONE }
}

data class ImportResult(
    val created: Int, val merged: Int, val skipped: Int, val attachments: Int, val failed: List<ImportIssue>,
    /** candidate key -> created note id */
    val createdIds: Map<String, String> = emptyMap()
)
