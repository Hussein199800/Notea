package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts and decrypts locked note payloads using AES-256-GCM.
 * Uses AndroidKeyStore on physical/emulator devices, with a deterministic
 * key derivation fallback for local JVM unit tests where AndroidKeyStore is absent.
 */
object CryptoManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "arabic_notes_master_aes_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val ENCRYPTED_PREFIX = "ENCv1:"

    fun isEncryptedPayload(text: String): Boolean = text.startsWith(ENCRYPTED_PREFIX)

    fun encrypt(plainText: String, passphraseSalt: String = "arabic_notes_vault"): String {
        if (plainText.isEmpty()) return ""
        if (isEncryptedPayload(plainText)) return plainText
        return try {
            val secretKey = getOrCreateSecretKey(passphraseSalt)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = try {
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                cipher.iv
            } catch (e: Exception) {
                val randomIv = ByteArray(GCM_IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, randomIv))
                randomIv
            }
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
            ENCRYPTED_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText
        }
    }

    fun decrypt(payload: String, passphraseSalt: String = "arabic_notes_vault"): String {
        if (!isEncryptedPayload(payload)) return payload
        return try {
            val rawBase64 = payload.removePrefix(ENCRYPTED_PREFIX)
            val combined = Base64.decode(rawBase64, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH_BYTES) return payload
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val cipherBytes = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)
            val secretKey = getOrCreateSecretKey(passphraseSalt)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            payload
        }
    }

    private fun getOrCreateSecretKey(passphraseSalt: String): SecretKey {
        if (passphraseSalt == "arabic_notes_vault") {
            try {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (existingKey != null) {
                    return existingKey.secretKey
                }
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                return keyGenerator.generateKey()
            } catch (_: Exception) {
                // Fallback for JVM unit tests or environments where AndroidKeyStore provider is unavailable
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(("ArabicNotesKey:" + passphraseSalt).toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }
}
