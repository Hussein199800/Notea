package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Protects small secrets at rest on this device. Production uses a non-exportable AES key held in
 * Android Keystore; tests can inject an in-memory implementation.
 */
interface DeviceKeyWrapper {
    fun wrap(plain: ByteArray, aad: ByteArray): ByteArray
    fun unwrap(wrapped: ByteArray, aad: ByteArray): ByteArray
}

class AndroidKeystoreWrapper(private val alias: String = "notea_device_kek_v2") : DeviceKeyWrapper {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    override fun wrap(plain: ByteArray, aad: ByteArray): ByteArray {
        try {
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key()) // Keystore generates the IV
            cipher.updateAAD(aad)
            val ct = cipher.doFinal(plain)
            return cipher.iv + ct
        } catch (e: Exception) {
            throw CryptoException("Android Keystore wrap failed", e)
        }
    }

    override fun unwrap(wrapped: ByteArray, aad: ByteArray): ByteArray {
        try {
            val iv = wrapped.copyOfRange(0, CryptoPrimitives.GCM_IV_BYTES)
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                javax.crypto.Cipher.DECRYPT_MODE, key(),
                javax.crypto.spec.GCMParameterSpec(CryptoPrimitives.GCM_TAG_BITS, iv)
            )
            cipher.updateAAD(aad)
            return cipher.doFinal(wrapped, iv.size, wrapped.size - iv.size)
        } catch (e: Exception) {
            throw CryptoException("Android Keystore unwrap failed", e)
        }
    }
}

/**
 * The master key wrapped by a key derived from the user's vault passphrase. Only ciphertext is
 * stored remotely or in backups; the passphrase never leaves the device.
 */
data class VaultEnvelope(
    val version: Int,
    val iterations: Int,
    val salt: String,
    val wrappedKey: String,
    val keyId: String,
    val createdAtMillis: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("v", version)
        .put("kdf", "pbkdf2-hmac-sha256")
        .put("iter", iterations)
        .put("salt", salt)
        .put("wrapped", wrappedKey)
        .put("keyId", keyId)
        .put("createdAt", createdAtMillis)

    fun toMap(): Map<String, Any> = mapOf(
        "v" to version.toLong(),
        "kdf" to "pbkdf2-hmac-sha256",
        "iter" to iterations.toLong(),
        "salt" to salt,
        "wrapped" to wrappedKey,
        "keyId" to keyId,
        "createdAtMillis" to createdAtMillis
    )

    companion object {
        fun fromJson(o: JSONObject): VaultEnvelope = VaultEnvelope(
            version = o.getInt("v"),
            iterations = o.getInt("iter"),
            salt = o.getString("salt"),
            wrappedKey = o.getString("wrapped"),
            keyId = o.getString("keyId"),
            createdAtMillis = o.optLong("createdAt", 0L)
        )

        fun fromMap(m: Map<String, Any?>): VaultEnvelope? = try {
            VaultEnvelope(
                version = (m["v"] as Number).toInt(),
                iterations = (m["iter"] as Number).toInt(),
                salt = m["salt"] as String,
                wrappedKey = m["wrapped"] as String,
                keyId = m["keyId"] as String,
                createdAtMillis = (m["createdAtMillis"] as? Number)?.toLong() ?: 0L
            )
        } catch (_: Exception) {
            null
        }
    }
}

enum class VaultState { NOT_SET_UP, NEEDS_UNLOCK, READY }

/**
 * Per-user master-key management.
 *
 * - A random 256-bit master key (MK) encrypts locked-note content and sensitive attachments.
 * - On device, MK is stored wrapped by Android Keystore (never in plaintext on disk).
 * - For other devices / restore, MK is wrapped by PBKDF2(passphrase) into a [VaultEnvelope]
 *   which is synced to Firestore and embedded in backups. Without the passphrase the envelope
 *   is useless; if the passphrase is forgotten, locked content cannot be recovered (by design).
 */
class KeyVault(
    private val prefs: SharedPreferences,
    private val deviceWrapper: DeviceKeyWrapper,
    private val iterations: Int = DEFAULT_ITERATIONS
) {
    constructor(context: Context) : this(
        context.getSharedPreferences("notea_vault", Context.MODE_PRIVATE),
        AndroidKeystoreWrapper()
    )

    private val cache = HashMap<String, ByteArray>()

    private fun aad(uid: String) = "notea-mk:$uid".toByteArray()

    fun state(uid: String): VaultState = when {
        masterKey(uid) != null -> VaultState.READY
        localEnvelope(uid) != null -> VaultState.NEEDS_UNLOCK
        // A wrapped key exists but this device can no longer unwrap it (e.g. Keystore reset):
        // never offer to create a new vault over it.
        prefs.contains(localKey(uid)) -> VaultState.NEEDS_UNLOCK
        else -> VaultState.NOT_SET_UP
    }

    fun localEnvelope(uid: String): VaultEnvelope? =
        prefs.getString(envelopeKey(uid), null)?.let {
            runCatching { VaultEnvelope.fromJson(JSONObject(it)) }.getOrNull()
        }

    /** Remember an envelope found remotely (or in a backup) so the user can unlock with the passphrase. */
    fun rememberEnvelope(uid: String, envelope: VaultEnvelope) {
        prefs.edit().putString(envelopeKey(uid), envelope.toJson().toString()).apply()
    }

    /** Creates a brand new master key protected by [passphrase]. Returns the envelope to sync. */
    fun setUp(uid: String, passphrase: CharArray): VaultEnvelope {
        require(passphrase.size >= MIN_PASSPHRASE) { "Passphrase too short" }
        check(state(uid) == VaultState.NOT_SET_UP) { "Vault already exists for this account" }
        val mk = CryptoPrimitives.randomBytes(CryptoPrimitives.KEY_BYTES)
        val envelope = buildEnvelope(uid, mk, passphrase)
        storeLocal(uid, mk)
        rememberEnvelope(uid, envelope)
        return envelope
    }

    /** Unlocks the master key on a new device (or after reinstall) using the passphrase. */
    fun unlockWithPassphrase(uid: String, passphrase: CharArray, envelope: VaultEnvelope? = localEnvelope(uid)): Boolean {
        val env = envelope ?: return false
        val mk = openEnvelope(uid, env, passphrase) ?: return false
        storeLocal(uid, mk)
        rememberEnvelope(uid, env)
        return true
    }

    /** Opens an envelope without persisting anything (used to read encrypted backups). */
    fun openEnvelope(uid: String, env: VaultEnvelope, passphrase: CharArray): ByteArray? = try {
        val kek = CryptoPrimitives.pbkdf2Sha256(passphrase, B64.dec(env.salt), env.iterations)
        val mk = CryptoPrimitives.gcmDecrypt(CryptoPrimitives.aesKey(kek), B64.dec(env.wrappedKey), envelopeAad(env.version))
        if (keyId(mk) == env.keyId) mk else null
    } catch (_: Exception) {
        null
    }

    fun changePassphrase(uid: String, newPassphrase: CharArray): VaultEnvelope {
        require(newPassphrase.size >= MIN_PASSPHRASE) { "Passphrase too short" }
        val mk = masterKey(uid) ?: throw CryptoException("Vault is locked")
        val env = buildEnvelope(uid, mk, newPassphrase)
        rememberEnvelope(uid, env)
        return env
    }

    /** Returns the raw master key, or null when the vault is not available on this device. */
    fun masterKey(uid: String): ByteArray? {
        cache[uid]?.let { return it }
        val stored = prefs.getString(localKey(uid), null) ?: return null
        return try {
            deviceWrapper.unwrap(B64.dec(stored), aad(uid)).also { cache[uid] = it }
        } catch (_: Exception) {
            null
        }
    }

    fun currentKeyId(uid: String): String? = masterKey(uid)?.let { keyId(it) }

    /** Installs a master key obtained from a backup envelope (after passphrase check). */
    fun installMasterKey(uid: String, mk: ByteArray, envelope: VaultEnvelope) {
        storeLocal(uid, mk)
        rememberEnvelope(uid, envelope)
    }

    private fun storeLocal(uid: String, mk: ByteArray) {
        val wrapped = deviceWrapper.wrap(mk, aad(uid))
        prefs.edit().putString(localKey(uid), B64.enc(wrapped)).apply()
        cache[uid] = mk
    }

    private fun buildEnvelope(uid: String, mk: ByteArray, passphrase: CharArray): VaultEnvelope {
        val salt = CryptoPrimitives.randomBytes(16)
        val kek = CryptoPrimitives.pbkdf2Sha256(passphrase, salt, iterations)
        val wrapped = CryptoPrimitives.gcmEncrypt(CryptoPrimitives.aesKey(kek), mk, envelopeAad(ENVELOPE_VERSION))
        return VaultEnvelope(ENVELOPE_VERSION, iterations, B64.enc(salt), B64.enc(wrapped), keyId(mk), System.currentTimeMillis())
    }

    private fun envelopeAad(version: Int) = "notea-envelope-v$version".toByteArray()

    private fun localKey(uid: String) = "mk_wrapped_$uid"
    private fun envelopeKey(uid: String) = "envelope_$uid"

    companion object {
        const val ENVELOPE_VERSION = 1
        const val DEFAULT_ITERATIONS = 210_000
        const val MIN_PASSPHRASE = 8

        fun keyId(mk: ByteArray): String = CryptoPrimitives.sha256Hex("notea-key-id".toByteArray() + mk).take(16)
    }
}
