package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.example.core.security.NoteCipher
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/**
 * Stores attachment binaries in app-private storage: `files/attachments/<uid>/<id>`.
 * Attachments of locked notes are encrypted with the vault master key.
 */
class AttachmentStore(private val context: Context) {

    fun userDir(uid: String): File {
        require(isPathSegment(uid))
        return File(context.filesDir, "attachments/$uid").apply { mkdirs() }
    }

    fun file(uid: String, relativePath: String): File {
        require(isPathSegment(relativePath))
        return File(userDir(uid), relativePath)
    }

    data class Stored(val relativePath: String, val size: Long)

    fun save(uid: String, id: String, input: InputStream, encryptWith: ByteArray?): Stored {
        val target = file(uid, id)
        val tmp = File.createTempFile("attachment-", ".tmp", target.parentFile)
        try {
            tmp.outputStream().use { out ->
                if (encryptWith != null) NoteCipher.encryptStream(encryptWith, input, out) else input.copyTo(out)
            }
            if (!tmp.renameTo(target)) throw IOException("Could not move attachment into place")
        } finally {
            tmp.delete()
        }
        return Stored(id, target.length())
    }

    fun open(uid: String, relativePath: String, decryptWith: ByteArray?): InputStream {
        val f = file(uid, relativePath)
        val raw = f.inputStream().buffered()
        try {
            return if (NoteCipher.isEncryptedStream(raw)) {
                NoteCipher.decryptStream(decryptWith ?: throw VaultLockedException(), raw)
            } else raw
        } catch (e: Exception) {
            raw.close()
            throw e
        }
    }

    /** Re-encrypts (or decrypts) an existing file in place, e.g. when a note gets locked/unlocked. */
    fun transform(uid: String, relativePath: String, from: ByteArray?, to: ByteArray?) {
        val f = file(uid, relativePath)
        if (!f.exists()) return
        val tmp = File.createTempFile("attachment-", ".tmp", f.parentFile)
        try {
            open(uid, relativePath, from).use { input ->
                tmp.outputStream().use { out ->
                    if (to != null) NoteCipher.encryptStream(to, input, out) else input.copyTo(out)
                }
            }
            if (!tmp.renameTo(f)) throw IOException("Could not replace attachment")
            cacheDirectory(uid, relativePath).deleteRecursively()
        } finally {
            tmp.delete()
        }
    }

    /** Plain file usable by viewers (decrypted into cache when needed). */
    fun viewableFile(uid: String, relativePath: String, extension: String, decryptWith: ByteArray?): File {
        val f = file(uid, relativePath)
        if (!NoteCipher.isEncryptedFile(f)) return f
        val key = decryptWith ?: throw VaultLockedException()
        val dir = cacheDirectory(uid, relativePath).apply { mkdirs() }
        val out = File(dir, "preview.${extension.takeIf { it.matches(SAFE_EXTENSION) } ?: "bin"}")
        val tmp = File.createTempFile("preview-", ".tmp", dir)
        try {
            open(uid, relativePath, key).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(out)) throw IOException("Could not prepare attachment preview")
            return out
        } finally {
            tmp.delete()
        }
    }

    fun delete(uid: String, relativePath: String) {
        if (isPathSegment(relativePath)) {
            file(uid, relativePath).delete()
            cacheDirectory(uid, relativePath).deleteRecursively()
        }
    }

    private fun cacheDirectory(uid: String, relativePath: String) = File(context.cacheDir, "decrypted/$uid/$relativePath")

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
        val mime = mimeFor(name, runCatching { cr.getType(uri) }.getOrNull())
        return UriInfo(name, mime, size, modified)
    }

    companion object {
        private val SAFE_EXTENSION = Regex("[a-z0-9]{1,16}")
        private val GENERIC_MIME = setOf("*/*", "application/octet-stream", "application/binary", "binary/octet-stream", "application/unknown")

        internal fun isPathSegment(value: String): Boolean =
            value.isNotBlank() && value != "." && value != ".." && value.none { it == '/' || it == '\\' || it == '\u0000' }

        fun mimeFor(name: String, reported: String?): String {
            val mime = reported.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
            return mime.takeIf { '/' in it && '*' !in it && it !in GENERIC_MIME } ?: guessMime(name)
        }

        fun guessMime(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
                "md", "markdown" -> "text/markdown"
                "heic" -> "image/heic"; "heif" -> "image/heif"; "avif" -> "image/avif"
                "tif", "tiff" -> "image/tiff"; "svg" -> "image/svg+xml"; "webp" -> "image/webp"
                "notea" -> "application/x-notea-backup"
                else -> "application/octet-stream"
            }
        }

        fun extensionFor(name: String, mime: String): String =
            name.substringAfterLast('.', "").lowercase(Locale.ROOT).takeIf { it.matches(SAFE_EXTENSION) } ?: run {
                MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: ""
            }
    }
}
