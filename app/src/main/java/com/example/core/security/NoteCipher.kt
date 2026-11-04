package com.example.core.security

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Content encryption with the vault master key.
 * Text payload format: `ENCv2:<keyId>:<base64(iv || ciphertext || tag)>`.
 * All failures throw [CryptoException]; there is no plaintext fallback.
 */
object NoteCipher {
    const val PREFIX_V2 = "ENCv2:"
    private val TEXT_AAD = "notea:text:v2".toByteArray()
    private val FILE_MAGIC = "NENC2".toByteArray()

    fun isEncrypted(text: String): Boolean = text.startsWith(PREFIX_V2) || LegacyCryptoV1.isLegacyPayload(text)

    fun isV2(text: String): Boolean = text.startsWith(PREFIX_V2)

    fun keyIdOf(payload: String): String? =
        if (payload.startsWith(PREFIX_V2)) payload.removePrefix(PREFIX_V2).substringBefore(":", "") else null

    fun encryptText(mk: ByteArray, plain: String): String {
        val ct = CryptoPrimitives.gcmEncrypt(CryptoPrimitives.aesKey(mk), plain.toByteArray(Charsets.UTF_8), TEXT_AAD)
        val out = PREFIX_V2 + KeyVault.keyId(mk) + ":" + B64.enc(ct)
        // Round-trip check: never hand back a payload we could not decrypt ourselves.
        if (decryptText(mk, out) != plain) throw CryptoException("Encryption self-check failed")
        return out
    }

    fun decryptText(mk: ByteArray, payload: String): String {
        if (!payload.startsWith(PREFIX_V2)) throw CryptoException("Not a v2 payload")
        val body = payload.removePrefix(PREFIX_V2)
        val keyId = body.substringBefore(":")
        if (keyId != KeyVault.keyId(mk)) throw CryptoException("Payload was encrypted with a different vault key")
        val bytes = try { B64.dec(body.substringAfter(":")) } catch (e: Exception) { throw CryptoException("Bad encoding", e) }
        return String(CryptoPrimitives.gcmDecrypt(CryptoPrimitives.aesKey(mk), bytes, TEXT_AAD), Charsets.UTF_8)
    }

    /** Streams [input] into [output] encrypted (header MAGIC || iv || GCM stream). */
    fun encryptStream(mk: ByteArray, input: InputStream, output: OutputStream) {
        try {
            val iv = CryptoPrimitives.randomBytes(CryptoPrimitives.GCM_IV_BYTES)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, CryptoPrimitives.aesKey(mk), GCMParameterSpec(CryptoPrimitives.GCM_TAG_BITS, iv))
            cipher.updateAAD(FILE_MAGIC)
            output.write(FILE_MAGIC)
            output.write(iv)
            CipherOutputStream(NonClosingOutputStream(output), cipher).use { input.copyTo(it) }
        } catch (e: CryptoException) {
            throw e
        } catch (e: Exception) {
            throw CryptoException("File encryption failed", e)
        }
    }

    fun decryptStream(mk: ByteArray, input: InputStream): InputStream {
        try {
            val magic = ByteArray(FILE_MAGIC.size).also { readFully(input, it) }
            if (!magic.contentEquals(FILE_MAGIC)) throw CryptoException("Not an encrypted Notea file")
            val iv = ByteArray(CryptoPrimitives.GCM_IV_BYTES).also { readFully(input, it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, CryptoPrimitives.aesKey(mk), GCMParameterSpec(CryptoPrimitives.GCM_TAG_BITS, iv))
            cipher.updateAAD(FILE_MAGIC)
            return CipherInputStream(input, cipher)
        } catch (e: CryptoException) {
            throw e
        } catch (e: Exception) {
            throw CryptoException("File decryption failed", e)
        }
    }

    fun isEncryptedFile(file: File): Boolean = file.inputStream().use { s ->
        val b = ByteArray(FILE_MAGIC.size)
        s.read(b) == b.size && b.contentEquals(FILE_MAGIC)
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val r = input.read(buf, off, buf.size - off)
            if (r < 0) throw CryptoException("Truncated encrypted data")
            off += r
        }
    }

    private class NonClosingOutputStream(private val out: OutputStream) : OutputStream() {
        override fun write(b: Int) = out.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun flush() = out.flush()
        override fun close() = out.flush()
    }
}

/**
 * Read-only support for the original `ENCv1:` payloads written by the first app version
 * (Keystore key `arabic_notes_master_aes_key`, or the SHA-256 fallback key used when the
 * Keystore was unavailable). Used only to migrate old notes to v2; never used to encrypt.
 */
object LegacyCryptoV1 {
    private const val PREFIX = "ENCv1:"
    private const val LEGACY_ALIAS = "arabic_notes_master_aes_key"

    fun isLegacyPayload(text: String) = text.startsWith(PREFIX)

    /** Returns the plaintext, or null if this device cannot decrypt it. */
    fun tryDecrypt(payload: String): String? {
        if (!isLegacyPayload(payload)) return null
        val combined = try { B64.dec(payload.removePrefix(PREFIX)) } catch (_: Exception) { return null }
        if (combined.size <= CryptoPrimitives.GCM_IV_BYTES) return null
        for (key in candidateKeys()) {
            try {
                val iv = combined.copyOfRange(0, CryptoPrimitives.GCM_IV_BYTES)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(CryptoPrimitives.GCM_TAG_BITS, iv))
                val plain = cipher.doFinal(combined, iv.size, combined.size - iv.size)
                return String(plain, Charsets.UTF_8)
            } catch (_: Exception) {
                // try next candidate
            }
        }
        return null
    }

    private fun candidateKeys(): List<SecretKey> {
        val keys = mutableListOf<SecretKey>()
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getEntry(LEGACY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { keys += it }
        } catch (_: Exception) {
        }
        val digest = MessageDigest.getInstance("SHA-256")
        keys += SecretKeySpec(digest.digest("ArabicNotesKey:arabic_notes_vault".toByteArray(Charsets.UTF_8)), "AES")
        return keys
    }
}
