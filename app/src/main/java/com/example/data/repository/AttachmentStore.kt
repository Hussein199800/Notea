package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.example.core.security.NoteCipher
import java.io.File
import java.io.InputStream

/**
 * Stores attachment binaries in app-private storage: `files/attachments/<uid>/<id>`.
 * Attachments of locked notes are encrypted with the vault master key.
 */
class AttachmentStore(private val context: Context) {

    fun userDir(uid: String): File = File(context.filesDir, "attachments/$uid").apply { mkdirs() }

    fun file(uid: String, relativePath: String): File = File(userDir(uid), relativePath)

    data class Stored(val relativePath: String, val size: Long)

    fun save(uid: String, id: String, input: InputStream, encryptWith: ByteArray?): Stored {
        val target = File(userDir(uid), id)
        val tmp = File(userDir(uid), "$id.tmp")
        try {
            tmp.outputStream().use { out ->
                if (encryptWith != null) NoteCipher.encryptStream(encryptWith, input, out) else input.copyTo(out)
            }
            if (!tmp.renameTo(target)) throw IllegalStateException("Could not move attachment into place")
        } finally {
            tmp.delete()
        }
        return Stored(id, target.length())
    }

    fun open(uid: String, relativePath: String, decryptWith: ByteArray?): InputStream {
        val f = file(uid, relativePath)
        val raw = f.inputStream()
        return if (decryptWith != null && NoteCipher.isEncryptedFile(f)) NoteCipher.decryptStream(decryptWith, raw) else raw
    }

    /** Re-encrypts (or decrypts) an existing file in place, e.g. when a note gets locked/unlocked. */
    fun transform(uid: String, relativePath: String, from: ByteArray?, to: ByteArray?) {
        val f = file(uid, relativePath)
        if (!f.exists()) return
        val tmp = File(f.parentFile, f.name + ".x")
        open(uid, relativePath, from).use { input ->
            tmp.outputStream().use { out ->
                if (to != null) NoteCipher.encryptStream(to, input, out) else input.copyTo(out)
            }
        }
        if (!tmp.renameTo(f)) {
            tmp.delete(); throw IllegalStateException("Could not replace attachment")
        }
    }

    /** Plain file usable by viewers (decrypted into cache when needed). */
    fun viewableFile(uid: String, relativePath: String, extension: String, decryptWith: ByteArray?): File {
        val f = file(uid, relativePath)
        if (!NoteCipher.isEncryptedFile(f)) return f
        val key = decryptWith ?: throw VaultLockedException()
        val out = File(context.cacheDir, "decrypted/${relativePath}.${extension.ifBlank { "bin" }}")
        out.parentFile?.mkdirs()
        open(uid, relativePath, key).use { input -> out.outputStream().use { input.copyTo(it) } }
        out.deleteOnExit()
        return out
    }

    fun delete(uid: String, relativePath: String) {
        if (relativePath.isNotBlank()) file(uid, relativePath).delete()
    }

    fun clearDecryptedCache() {
        File(context.cacheDir, "decrypted").deleteRecursively()
    }

    data class UriInfo(val name: String, val mime: String, val size: Long, val lastModified: Long?)

    fun describe(uri: Uri): UriInfo {
        val cr = context.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        var size = -1L
        var modified: Long? = null
        try {
            cr.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { c.getString(it)?.let { n -> name = n } }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { if (!c.isNull(it)) size = c.getLong(it) }
                    c.getColumnIndex("last_modified").takeIf { it >= 0 }?.let { if (!c.isNull(it)) modified = c.getLong(it).takeIf { v -> v > 0 } }
                }
            }
        } catch (_: Exception) {
        }
        val mime = cr.getType(uri) ?: guessMime(name)
        return UriInfo(name, mime, size, modified)
    }

    companion object {
        fun guessMime(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
                "md", "markdown" -> "text/markdown"
                "heic" -> "image/heic"; "heif" -> "image/heif"; "avif" -> "image/avif"
                "tif", "tiff" -> "image/tiff"; "svg" -> "image/svg+xml"; "webp" -> "image/webp"
                "notea" -> "application/x-notea-backup"
                else -> "application/octet-stream"
            }
        }

        fun extensionFor(name: String, mime: String): String =
            name.substringAfterLast('.', "").lowercase().ifBlank {
                MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: ""
            }
    }
}
