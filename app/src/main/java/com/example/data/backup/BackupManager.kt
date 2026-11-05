package com.example.data.backup

import android.content.Context
import com.example.core.security.B64
import com.example.core.security.CryptoException
import com.example.core.security.CryptoPrimitives
import com.example.core.security.KeyVault
import com.example.core.security.NoteCipher
import com.example.core.security.VaultEnvelope
import com.example.data.importer.DuplicateKind
import com.example.data.importer.ImportAction
import com.example.data.importer.ImportCandidate
import com.example.data.importer.ImportContext
import com.example.data.importer.ImportIssue
import com.example.data.importer.ImportPreview
import com.example.data.importer.ImportSource
import com.example.data.importer.ImportWarning
import com.example.data.importer.ImportedAttachment
import com.example.data.local.NoteEntity
import com.example.data.local.NoteaDatabase
import com.example.data.local.ReminderStatus
import com.example.data.local.SyncState
import com.example.data.repository.Ids
import com.example.data.repository.LockedPayload
import com.example.data.repository.NoteRepository
import com.example.data.repository.checklistFromJson
import com.example.data.repository.toDrafts
import com.example.data.repository.toJson
import com.example.data.repository.blocksToJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec

data class BackupManifest(
    val formatVersion: Int,
    val schemaVersion: Int,
    val appVersion: String,
    val createdAt: Long,
    val encrypted: Boolean,
    val counts: Map<String, Int>,
    val checksums: Map<String, String>,
    val vaultEnvelope: VaultEnvelope?
) {
    fun toJson(): JSONObject = JSONObject()
        .put("format", FORMAT_ID)
        .put("formatVersion", formatVersion)
        .put("schemaVersion", schemaVersion)
        .put("appVersion", appVersion)
        .put("createdAt", createdAt)
        .put("encrypted", encrypted)
        .put("counts", JSONObject(counts as Map<*, *>))
        .put("checksums", JSONObject(checksums as Map<*, *>))
        .put("vault", vaultEnvelope?.toJson() ?: JSONObject.NULL)

    companion object {
        const val FORMAT_ID = "notea-backup"
        fun fromJson(o: JSONObject): BackupManifest {
            require(o.optString("format") == FORMAT_ID) { "Not a Notea backup" }
            val counts = o.optJSONObject("counts")?.let { c -> c.keys().asSequence().associateWith { c.optInt(it) } }.orEmpty()
            val sums = o.optJSONObject("checksums")?.let { c -> c.keys().asSequence().associateWith { c.optString(it) } }.orEmpty()
            return BackupManifest(
                o.getInt("formatVersion"), o.optInt("schemaVersion", 2), o.optString("appVersion"), o.optLong("createdAt"),
                o.optBoolean("encrypted"), counts, sums,
                o.optJSONObject("vault")?.let { runCatching { VaultEnvelope.fromJson(it) }.getOrNull() }
            )
        }
    }
}

class BackupException(val reason: Reason, message: String = reason.name) : Exception(message) {
    enum class Reason { NOT_A_BACKUP, PASSWORD_REQUIRED, WRONG_PASSWORD, CORRUPTED, NEWER_VERSION }
}

/**
 * `.notea` backups.
 *
 * Plain: a ZIP with `manifest.json` (format/schema versions, counts, SHA-256 of every entry,
 * vault envelope), `data.json` and `attachments/<id>`.
 * Encrypted: `NOTEA-ENC1\n<header json>\n` followed by AES-256-GCM(zip) with a key derived from
 * the backup password (PBKDF2-HMAC-SHA256); the header is authenticated as AAD.
 *
 * Locked notes are always stored as vault ciphertext, even in a plain backup.
 * New format versions must stay readable: readers ignore unknown fields and reject only
 * `formatVersion > CURRENT_FORMAT`.
 */
class BackupManager(
    private val context: Context,
    private val db: NoteaDatabase,
    private val repository: NoteRepository,
    private val keyVault: KeyVault,
    private val appVersion: String = "2.0"
) {
    // ------------------------------------------------------------------ export
    suspend fun export(uid: String, out: OutputStream, password: CharArray?): BackupManifest {
        val stage = File(context.cacheDir, "backup_stage_${Ids.newId()}.zip")
        try {
            val manifest = writeZip(uid, stage, encrypted = password != null)
            if (password == null) {
                stage.inputStream().use { it.copyTo(out) }
            } else {
                val salt = CryptoPrimitives.randomBytes(16)
                val header = JSONObject().put("formatVersion", CURRENT_FORMAT).put("kdf", "pbkdf2-hmac-sha256")
                    .put("iter", PASSWORD_ITERATIONS).put("salt", B64.enc(salt)).put("createdAt", manifest.createdAt).toString()
                val key = CryptoPrimitives.aesKey(CryptoPrimitives.pbkdf2Sha256(password, salt, PASSWORD_ITERATIONS))
                val iv = CryptoPrimitives.randomBytes(CryptoPrimitives.GCM_IV_BYTES)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(CryptoPrimitives.GCM_TAG_BITS, iv))
                val headerBytes = (ENC_MAGIC + header + "\n").toByteArray(Charsets.UTF_8)
                cipher.updateAAD(headerBytes)
                out.write(headerBytes)
                out.write(iv)
                val cos = CipherOutputStream(object : OutputStream() {
                    override fun write(b: Int) = out.write(b)
                    override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
                    override fun close() = out.flush()
                }, cipher)
                stage.inputStream().use { it.copyTo(cos) }
                cos.close()
            }
            out.flush()
            return manifest
        } finally {
            stage.delete()
        }
    }

    private suspend fun writeZip(uid: String, target: File, encrypted: Boolean): BackupManifest {
        val all = db.noteDao().getAllWithDetails(uid)
        val folders = repository.allFolders(uid)
        val templates = db.templateDao().all(uid)
        val checksums = linkedMapOf<String, String>()
        var attachmentCount = 0
        var revisionCount = 0
        var reminderCount = 0
        val notesJson = JSONArray()
        for (d in all) {
            val n = d.note
            val revs = db.revisionDao().forNote(n.id)
            revisionCount += revs.size
            reminderCount += d.reminders.count { it.status != ReminderStatus.DONE }
            notesJson.put(JSONObject().apply {
                put("id", n.id); put("title", n.title); put("body", n.body); put("colorIndex", n.colorIndex)
                put("folderId", n.folderId ?: JSONObject.NULL); put("isPinned", n.isPinned); put("isFavorite", n.isFavorite)
                put("isArchived", n.isArchived); put("trashedAt", n.trashedAt ?: JSONObject.NULL); put("isLocked", n.isLocked)
                put("lockPinHash", n.lockPinHash); put("lockHint", n.lockHint); put("createdAt", n.createdAt); put("updatedAt", n.updatedAt)
                put("importSource", n.importSource)
                put("tags", JSONArray(d.tags.map { it.name }))
                put("checklist", JSONArray(d.checklist.toDrafts().toJson()))
                if (d.blocks.isNotEmpty()) put("blocks", JSONArray(d.blocks.sortedBy { it.position }
                    .map { com.example.data.repository.BlockDraft(it.id, it.type, it.text, it.attachmentId) }.blocksToJson()))
                put("reminders", JSONArray().also { a -> d.reminders.filter { it.status != ReminderStatus.DONE }.forEach { r ->
                    a.put(JSONObject().put("triggerAt", r.triggerAt).put("status", r.status)) } })
                put("revisions", JSONArray().also { a -> revs.forEach { r ->
                    a.put(JSONObject().put("title", r.title).put("body", r.body).put("checklist", JSONArray(r.checklistJson.ifBlank { "[]" }))
                        .put("createdAt", r.createdAt).put("reason", r.reason).put("label", r.label).put("blocks", r.blocksJson)) } })
                put("attachments", JSONArray().also { a -> d.attachments.forEach { at ->
                    a.put(JSONObject().put("id", at.id).put("kind", at.kind).put("mime", at.mimeType).put("name", at.displayName)
                        .put("path", if (at.localPath.isNotBlank()) "attachments/${at.id}" else "")
                        .put("size", at.sizeBytes).put("durationMs", at.durationMs).put("url", at.url).put("linkTitle", at.linkTitle)
                        .put("linkDescription", at.linkDescription).put("extractedText", at.extractedText)
                        .put("encrypted", at.encrypted).put("createdAt", at.createdAt))
                } })
            })
            attachmentCount += d.attachments.size
        }
        val data = JSONObject()
            .put("notes", notesJson)
            .put("folders", JSONArray().also { a -> folders.forEach { f ->
                a.put(JSONObject().put("id", f.id).put("name", f.name).put("colorIndex", f.colorIndex).put("position", f.position).put("createdAt", f.createdAt)) } })
            .put("templates", JSONArray().also { a -> templates.forEach { t ->
                a.put(JSONObject().put("name", t.name).put("title", t.title).put("body", t.body).put("checklist", JSONArray(t.checklistJson.ifBlank { "[]" }))) } })
            .toString().toByteArray(Charsets.UTF_8)

        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("data.json")); zip.write(data); zip.closeEntry()
            checksums["data.json"] = CryptoPrimitives.sha256Hex(data)
            for (d in all) for (at in d.attachments) {
                if (at.localPath.isBlank()) continue
                val f = repository.attachments.file(uid, at.localPath)
                if (!f.exists()) continue
                zip.putNextEntry(ZipEntry("attachments/${at.id}"))
                val md = MessageDigest.getInstance("SHA-256")
                DigestInputStream(f.inputStream(), md).use { it.copyTo(zip) }
                zip.closeEntry()
                checksums["attachments/${at.id}"] = md.digest().joinToString("") { "%02x".format(it) }
            }
            val manifest = BackupManifest(
                CURRENT_FORMAT, NoteaDatabase.VERSION, appVersion, System.currentTimeMillis(), encrypted,
                mapOf("notes" to all.size, "folders" to folders.size, "tags" to all.flatMap { it.tags }.distinctBy { it.id }.size,
                    "attachments" to attachmentCount, "reminders" to reminderCount, "revisions" to revisionCount, "templates" to templates.size),
                checksums, keyVault.localEnvelope(uid)
            )
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toJson().toString(2).toByteArray()); zip.closeEntry()
            return manifest
        }
    }

    // ------------------------------------------------------------------ inspect / read
    fun isEncrypted(input: InputStream): Boolean {
        val b = BufferedInputStream(input)
        val head = ByteArray(ENC_MAGIC.length)
        val r = b.read(head)
        return r == head.size && String(head, Charsets.UTF_8) == ENC_MAGIC
    }

    /**
     * Decrypts (if needed) and verifies a backup into a temp folder, then converts it into an import
     * preview. Nothing is written to the database here. [vaultPassphrase] lets locked notes from a
     * backup made with another vault key be re-encrypted with this device's key.
     */
    suspend fun read(uid: String, open: () -> InputStream, password: CharArray?, vaultPassphrase: CharArray? = null): ImportPreview {
        val dir = File(context.cacheDir, "import/restore_${Ids.newId()}").apply { mkdirs() }
        val zipFile = File(dir, "inner.zip")
        open().use { raw ->
            val input = BufferedInputStream(raw)
            input.mark(64)
            val head = ByteArray(ENC_MAGIC.length)
            val n = input.read(head)
            input.reset()
            if (n == head.size && String(head, Charsets.UTF_8) == ENC_MAGIC) {
                if (password == null) throw BackupException(BackupException.Reason.PASSWORD_REQUIRED)
                decryptTo(input, password, zipFile)
            } else if (n >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
                zipFile.outputStream().use { input.copyTo(it) }
            } else throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        }
        // Extract + verify
        val entries = hashMapOf<String, File>()
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name
                if (name.contains("..") || name.startsWith("/")) continue
                val f = File(dir, "e_" + name.replace('/', '_'))
                f.outputStream().use { zip.copyTo(it) }
                entries[name] = f
            }
        }
        zipFile.delete()
        val manifestFile = entries["manifest.json"] ?: throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        val manifest = try { BackupManifest.fromJson(JSONObject(manifestFile.readText())) } catch (e: Exception) {
            throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        }
        if (manifest.formatVersion > CURRENT_FORMAT) throw BackupException(BackupException.Reason.NEWER_VERSION)
        val issues = mutableListOf<ImportIssue>()
        for ((name, sum) in manifest.checksums) {
            val f = entries[name]
            val actual = f?.let { file -> MessageDigest.getInstance("SHA-256").let { md -> file.inputStream().use { DigestInputStream(it, md).readBytes() }; md.digest().joinToString("") { "%02x".format(it) } } }
            if (actual != sum) {
                if (name == "data.json") throw BackupException(BackupException.Reason.CORRUPTED, "data.json checksum mismatch")
                issues += ImportIssue(name, ImportWarning.MISSING_RESOURCE, "checksum")
                entries.remove(name)
            }
        }
        val data = JSONObject(entries.getValue("data.json").readText())

        // Vault handling for locked notes
        val backupEnv = manifest.vaultEnvelope
        if (backupEnv != null && keyVault.localEnvelope(uid) == null) keyVault.rememberEnvelope(uid, backupEnv)
        val currentKeyId = keyVault.currentKeyId(uid)
        val backupMk: ByteArray? = if (backupEnv != null && vaultPassphrase != null) keyVault.openEnvelope(uid, backupEnv, vaultPassphrase) else null
        val currentMk = keyVault.masterKey(uid)

        val folderNames = data.optJSONArray("folders")?.let { a -> (0 until a.length()).associate { i ->
            a.getJSONObject(i).let { it.getString("id") to it.getString("name") } } }.orEmpty()
        val ctx = ImportContext(dir)
        val candidates = mutableListOf<ImportCandidate>()
        val notes = data.getJSONArray("notes")
        for (i in 0 until notes.length()) {
            val o = notes.getJSONObject(i)
            try {
                candidates += noteCandidate(uid, o, entries, folderNames, currentKeyId, backupMk, currentMk, ctx, issues)
            } catch (e: Exception) {
                issues += ImportIssue(o.optString("title"), ImportWarning.UNREADABLE, e.message.orEmpty())
            }
        }
        data.optJSONArray("templates")?.let { a ->
            for (i in 0 until a.length()) {
                val t = a.getJSONObject(i)
                pendingTemplates += Triple(t.optString("name"), t.optString("title") to t.optString("body"), t.optJSONArray("checklist")?.toString() ?: "[]")
            }
        }
        val marked = markExistingIds(uid, candidates)
        return ImportPreview(ImportSource.BACKUP, marked, issues.distinct(), manifest)
    }

    private val pendingTemplates = mutableListOf<Triple<String, Pair<String, String>, String>>()

    /** Restores templates found in the last read backup (skips ones with the same name). */
    suspend fun commitTemplates(uid: String) {
        val existing = db.templateDao().all(uid).map { it.name }.toSet()
        pendingTemplates.filter { it.first !in existing }.forEach { (name, tb, checklist) ->
            repository.saveTemplate(uid, name, com.example.data.repository.NoteDraft(id = "", title = tb.first, body = tb.second, checklist = checklistFromJson(checklist)))
        }
        pendingTemplates.clear()
    }

    private suspend fun markExistingIds(uid: String, list: List<ImportCandidate>): List<ImportCandidate> {
        val withDup = repository.let { com.example.data.importer.ImportManager(context, it).markDuplicates(uid, list) }
        // Same note id already on this device: by default skip; if content differs offer "keep both".
        val ids = db.noteDao().getAllIncludingDeleted(uid).associateBy { it.id }
        return withDup.map { c ->
            val originalId = c.key
            val local = ids[originalId]
            when {
                local == null -> c.copy(action = if (c.duplicate == DuplicateKind.EXACT) ImportAction.SKIP else c.action)
                local.updatedAt == (c.updatedAt ?: -1L) -> c.copy(duplicate = DuplicateKind.EXACT, duplicateOfId = originalId, action = ImportAction.SKIP)
                else -> c.copy(duplicate = DuplicateKind.SIMILAR, duplicateOfId = originalId, action = ImportAction.KEEP_BOTH)
            }
        }
    }

    private fun noteCandidate(
        uid: String, o: JSONObject, entries: Map<String, File>, folderNames: Map<String, String>,
        currentKeyId: String?, backupMk: ByteArray?, currentMk: ByteArray?, ctx: ImportContext, issues: MutableList<ImportIssue>
    ): ImportCandidate {
        val id = o.getString("id")
        val title = o.optString("title")
        var body = o.optString("body")
        var checklist = checklistFromJson(o.optJSONArray("checklist")?.toString() ?: "[]")
        val locked = o.optBoolean("isLocked")
        val warnings = mutableSetOf<ImportWarning>()
        var storedLocked: NoteEntity? = null
        var lockedPlainAvailable = false
        var lockedPlainBlocks: List<com.example.data.repository.BlockDraft>? = null
        val atts = mutableListOf<ImportedAttachment>()
        var reencryptAttachmentsFrom: ByteArray? = null

        if (locked && NoteCipher.isV2(body)) {
            val keyId = NoteCipher.keyIdOf(body)
            when {
                keyId != null && keyId == currentKeyId -> Unit // same vault: keep ciphertext as is
                backupMk != null && keyId == KeyVault.keyId(backupMk) && currentMk != null -> {
                    // Different vault: decrypt with the backup key, re-encrypt with ours on save.
                    val content = LockedPayload.decodeContent(NoteCipher.decryptText(backupMk, body))
                    body = content.body; checklist = content.checklist; lockedPlainAvailable = true
                    lockedPlainBlocks = content.blocks
                    reencryptAttachmentsFrom = backupMk
                }
                else -> warnings += ImportWarning.LOCKED_CONTENT
            }
            if (!lockedPlainAvailable) {
                storedLocked = NoteEntity(
                    id = id, userId = uid, title = title, body = body, colorIndex = o.optInt("colorIndex"),
                    isPinned = o.optBoolean("isPinned"), isFavorite = o.optBoolean("isFavorite"), isArchived = o.optBoolean("isArchived"),
                    trashedAt = if (o.isNull("trashedAt")) null else o.optLong("trashedAt"), isLocked = true,
                    lockPinHash = o.optString("lockPinHash"), lockHint = o.optString("lockHint"),
                    createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"), syncState = SyncState.DIRTY,
                    importSource = o.optString("importSource")
                )
            }
        } else if (locked && NoteCipher.isEncrypted(body)) {
            warnings += ImportWarning.LOCKED_CONTENT // legacy v1 payload kept as is
        }

        o.optJSONArray("attachments")?.let { a ->
            for (i in 0 until a.length()) {
                val at = a.getJSONObject(i)
                val path = at.optString("path")
                val f = if (path.isNotBlank()) entries[path] else null
                if (path.isNotBlank() && f == null) { warnings += ImportWarning.MISSING_RESOURCE; continue }
                var file = f ?: File(ctx.tempDir, "empty_${Ids.newId()}").apply { createNewFile() }
                var encrypted = at.optBoolean("encrypted")
                if (encrypted && reencryptAttachmentsFrom != null && f != null) {
                    val plain = File(ctx.tempDir, "dec_${Ids.newId()}")
                    NoteCipher.decryptStream(reencryptAttachmentsFrom!!, f.inputStream()).use { s -> plain.outputStream().use { s.copyTo(it) } }
                    file = plain; encrypted = false
                }
                atts += ImportedAttachment(
                    kind = at.optString("kind"), name = at.optString("name"), mime = at.optString("mime"), file = file,
                    extractedText = at.optString("extractedText"), encrypted = encrypted && storedLocked != null,
                    durationMs = at.optLong("durationMs"), url = at.optString("url"), linkTitle = at.optString("linkTitle"),
                    linkDescription = at.optString("linkDescription"), createdAt = at.optLong("createdAt").takeIf { it > 0 },
                    originalId = at.optString("id").takeIf { it.isNotBlank() }
                ).let { if (path.isBlank()) it.copy(file = File("")) else it }
            }
        }
        val reminderAt = o.optJSONArray("reminders")?.let { r -> (0 until r.length()).map { r.getJSONObject(it).optLong("triggerAt") }.filter { it > System.currentTimeMillis() }.minOrNull() }
        pendingRevisions[id] = o.optJSONArray("revisions")?.toString() ?: "[]"
        return ImportCandidate(
            key = id, sourceName = title.ifBlank { id }, title = title, body = if (storedLocked != null) "" else body,
            checklist = if (storedLocked != null) emptyList() else checklist,
            tags = o.optJSONArray("tags")?.let { t -> (0 until t.length()).map { t.getString(it) } }.orEmpty(),
            folderName = o.optString("folderId").takeIf { it.isNotBlank() && it != "null" }?.let { folderNames[it] },
            createdAt = o.optLong("createdAt").takeIf { it > 0 }, updatedAt = o.optLong("updatedAt").takeIf { it > 0 },
            isPinned = o.optBoolean("isPinned"), isFavorite = o.optBoolean("isFavorite"), isArchived = o.optBoolean("isArchived"),
            trashedAt = if (o.isNull("trashedAt")) null else o.optLong("trashedAt"), colorIndex = o.optInt("colorIndex"),
            attachments = atts, warnings = warnings, storedLocked = storedLocked, reminderAt = reminderAt,
            blocks = if (storedLocked != null) null else (o.optJSONArray("blocks")?.let { com.example.data.repository.blocksFromJson(it.toString()) }
                ?: lockedPlainBlocks)
        ).also {
            if (lockedPlainAvailable) lockedPlainIds += id
        }
    }

    /** Notes (by backup id) that were locked in the backup and must be re-locked when restored. */
    val lockedPlainIds = mutableSetOf<String>()
    private val pendingRevisions = hashMapOf<String, String>()

    private fun decryptTo(input: InputStream, password: CharArray, target: File) {
        val headerBuf = java.io.ByteArrayOutputStream()
        var newlines = 0
        while (newlines < 2) {
            val b = input.read()
            if (b < 0) throw BackupException(BackupException.Reason.CORRUPTED)
            headerBuf.write(b)
            if (b == '\n'.code) newlines++
            if (headerBuf.size() > 4096) throw BackupException(BackupException.Reason.CORRUPTED)
        }
        val headerBytes = headerBuf.toByteArray()
        val header = JSONObject(String(headerBytes, Charsets.UTF_8).removePrefix(ENC_MAGIC).trim())
        if (header.optInt("formatVersion") > CURRENT_FORMAT) throw BackupException(BackupException.Reason.NEWER_VERSION)
        val salt = B64.dec(header.getString("salt"))
        val key = CryptoPrimitives.aesKey(CryptoPrimitives.pbkdf2Sha256(password, salt, header.getInt("iter")))
        val iv = ByteArray(CryptoPrimitives.GCM_IV_BYTES)
        var off = 0
        while (off < iv.size) { val r = input.read(iv, off, iv.size - off); if (r < 0) throw BackupException(BackupException.Reason.CORRUPTED); off += r }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(CryptoPrimitives.GCM_TAG_BITS, iv))
        cipher.updateAAD(headerBytes)
        try {
            CipherInputStream(input, cipher).use { cis -> target.outputStream().use { cis.copyTo(it) } }
        } catch (e: Exception) {
            target.delete()
            throw BackupException(BackupException.Reason.WRONG_PASSWORD)
        }
        if (target.length() == 0L) throw BackupException(BackupException.Reason.WRONG_PASSWORD)
    }

    /** After committing a restore: re-lock notes that were locked in the backup and restore their revision history. */
    suspend fun finishRestore(uid: String, createdIds: Map<String, String>) {
        for ((backupId, newId) in createdIds) {
            if (backupId in lockedPlainIds) {
                val d = db.noteDao().getNoteWithDetails(newId) ?: continue
                repository.toDraft(uid, d)?.let { draft -> runCatching { repository.save(uid, draft.copy(isLocked = true, updatedAt = d.note.updatedAt)) } }
            }
            val revs = pendingRevisions[backupId] ?: continue
            val arr = JSONArray(revs)
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                repository.addRevision(newId, r.optString("title"), r.optString("body"),
                    checklistFromJson(r.optJSONArray("checklist")?.toString() ?: "[]"), r.optString("reason"), r.optLong("createdAt"),
                    r.optString("label"), r.optString("blocks"))
            }
        }
        lockedPlainIds.clear(); pendingRevisions.clear()
        commitTemplates(uid)
    }

    // ------------------------------------------------------------------ local history
    fun historyDir(uid: String) = File(context.filesDir, "backups/$uid").apply { mkdirs() }

    fun history(uid: String): List<File> = historyDir(uid).listFiles { f -> f.name.endsWith(".notea") }?.sortedByDescending { it.lastModified() }.orEmpty()

    suspend fun createSnapshot(uid: String, keep: Int = HISTORY_KEEP): File {
        val f = File(historyDir(uid), "notea-${System.currentTimeMillis()}.notea")
        val tmp = File(f.path + ".tmp")
        tmp.outputStream().use { export(uid, it, null) }
        if (!tmp.renameTo(f)) { tmp.delete(); throw IllegalStateException("snapshot failed") }
        history(uid).drop(keep).forEach { it.delete() }
        return f
    }

    companion object {
        const val CURRENT_FORMAT = 1
        const val ENC_MAGIC = "NOTEA-ENC1\n"
        const val PASSWORD_ITERATIONS = 210_000
        const val HISTORY_KEEP = 7
        const val MIME = "application/octet-stream"
    }
}
