package com.example.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Thrown whenever encryption or decryption cannot be completed. Callers must never fall back to plaintext. */
class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Pure-JVM cryptographic building blocks (AES-256-GCM, PBKDF2-HMAC-SHA256).
 * PBKDF2 is implemented on top of HmacSHA256 so it behaves identically on every API level
 * (PBKDF2WithHmacSHA256 is missing from the platform on API 24/25).
 */
object CryptoPrimitives {
    const val GCM_IV_BYTES = 12
    const val GCM_TAG_BITS = 128
    const val KEY_BYTES = 32
    private val random = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    fun aesKey(raw: ByteArray): SecretKey {
        require(raw.size == KEY_BYTES) { "AES-256 key must be 32 bytes" }
        return SecretKeySpec(raw, "AES")
    }

    /** Returns iv || ciphertext+tag. */
    fun gcmEncrypt(key: SecretKey, plain: ByteArray, aad: ByteArray? = null): ByteArray {
        try {
            val iv = randomBytes(GCM_IV_BYTES)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            if (aad != null) cipher.updateAAD(aad)
            val ct = cipher.doFinal(plain)
            return iv + ct
        } catch (e: Exception) {
            throw CryptoException("Encryption failed", e)
        }
    }

    fun gcmDecrypt(key: SecretKey, ivAndCipher: ByteArray, aad: ByteArray? = null): ByteArray {
        if (ivAndCipher.size <= GCM_IV_BYTES + GCM_TAG_BITS / 8 - 1) throw CryptoException("Ciphertext too short")
        try {
            val iv = ivAndCipher.copyOfRange(0, GCM_IV_BYTES)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            if (aad != null) cipher.updateAAD(aad)
            return cipher.doFinal(ivAndCipher, GCM_IV_BYTES, ivAndCipher.size - GCM_IV_BYTES)
        } catch (e: Exception) {
            throw CryptoException("Decryption failed (wrong key or tampered data)", e)
        }
    }

    /** RFC 8018 PBKDF2 with HMAC-SHA256. */
    fun pbkdf2Sha256(password: CharArray, salt: ByteArray, iterations: Int, keyLen: Int = KEY_BYTES): ByteArray {
        require(iterations >= 1)
        val passBytes = String(password).toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        // HMAC requires a non-empty key spec; an empty password is rejected by callers anyway.
        mac.init(SecretKeySpec(if (passBytes.isEmpty()) ByteArray(1) else passBytes, "HmacSHA256"))
        val hLen = mac.macLength
        val blocks = (keyLen + hLen - 1) / hLen
        val out = ByteArray(blocks * hLen)
        for (i in 1..blocks) {
            mac.update(salt)
            mac.update(byteArrayOf((i ushr 24).toByte(), (i ushr 16).toByte(), (i ushr 8).toByte(), i.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            for (j in 1 until iterations) {
                u = mac.doFinal(u)
                for (k in t.indices) t[k] = (t[k].toInt() xor u[k].toInt()).toByte()
            }
            System.arraycopy(t, 0, out, (i - 1) * hLen, hLen)
        }
        passBytes.fill(0)
        return out.copyOf(keyLen)
    }

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    fun sha256Hex(data: ByteArray): String = sha256(data).toHex()

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
