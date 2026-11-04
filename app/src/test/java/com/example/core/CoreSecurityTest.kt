package com.example.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.security.CryptoException
import com.example.core.security.CryptoPrimitives
import com.example.core.security.DeviceKeyWrapper
import com.example.core.security.KeyVault
import com.example.core.security.NoteCipher
import com.example.core.security.PinHasher
import com.example.core.security.VaultState
import com.example.core.security.toHex
import com.example.core.text.ArabicNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** In-memory stand-in for Android Keystore (JVM tests have no Keystore provider). */
class FakeWrapper : DeviceKeyWrapper {
    private val key = SecretKeySpec(ByteArray(32) { 7 }, "AES")
    override fun wrap(plain: ByteArray, aad: ByteArray) = CryptoPrimitives.gcmEncrypt(key, plain, aad)
    override fun unwrap(wrapped: ByteArray, aad: ByteArray) = CryptoPrimitives.gcmDecrypt(key, wrapped, aad)
}

fun testVault(name: String = "vault_" + System.nanoTime()): KeyVault {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    return KeyVault(ctx.getSharedPreferences(name, Context.MODE_PRIVATE), FakeWrapper(), iterations = 1_000)
}

@RunWith(RobolectricTestRunner::class)
class CoreSecurityTest {

    @Test fun pbkdf2_matchesRfc7914Vector() {
        val out = CryptoPrimitives.pbkdf2Sha256("passwd".toCharArray(), "salt".toByteArray(), 1, 64)
        assertEquals(
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783",
            out.toHex()
        )
    }

    @Test fun noteCipher_roundTrip_andFailuresThrow() {
        val mk = CryptoPrimitives.randomBytes(32)
        val plain = "سرّي جداً – top secret ✅"
        val enc = NoteCipher.encryptText(mk, plain)
        assertTrue(enc.startsWith(NoteCipher.PREFIX_V2))
        assertFalse(enc.contains("سرّي"))
        assertEquals(plain, NoteCipher.decryptText(mk, enc))
        // Wrong key -> exception, never the ciphertext/plaintext silently
        try { NoteCipher.decryptText(CryptoPrimitives.randomBytes(32), enc); fail() } catch (_: CryptoException) {}
        // Tampering -> exception
        val tampered = enc.dropLast(4) + "AAAA"
        try { NoteCipher.decryptText(mk, tampered); fail() } catch (_: CryptoException) {}
    }

    @Test fun fileCipher_roundTrip() {
        val mk = CryptoPrimitives.randomBytes(32)
        val data = ByteArray(300_000) { (it % 251).toByte() }
        val out = java.io.ByteArrayOutputStream()
        NoteCipher.encryptStream(mk, data.inputStream(), out)
        val enc = out.toByteArray()
        assertFalse(enc.copyOfRange(17, 60).contentEquals(data.copyOfRange(0, 43)))
        assertTrue(NoteCipher.decryptStream(mk, enc.inputStream()).readBytes().contentEquals(data))
    }

    @Test fun keyVault_setupOnDeviceA_unlockOnDeviceB_withPassphrase() {
        val a = testVault()
        assertEquals(VaultState.NOT_SET_UP, a.state("u1"))
        val env = a.setUp("u1", "correct horse".toCharArray())
        assertEquals(VaultState.READY, a.state("u1"))
        val payload = NoteCipher.encryptText(a.masterKey("u1")!!, "hello")

        val b = testVault() // another device: different keystore storage
        b.rememberEnvelope("u1", env)
        assertEquals(VaultState.NEEDS_UNLOCK, b.state("u1"))
        assertFalse(b.unlockWithPassphrase("u1", "wrong pass".toCharArray()))
        assertNull(b.masterKey("u1"))
        assertTrue(b.unlockWithPassphrase("u1", "correct horse".toCharArray()))
        assertEquals("hello", NoteCipher.decryptText(b.masterKey("u1")!!, payload))

        // Passphrase change keeps the same master key (old notes stay readable)
        val env2 = b.changePassphrase("u1", "new passphrase".toCharArray())
        assertEquals(env.keyId, env2.keyId)
        assertNotEquals(env.wrappedKey, env2.wrappedKey)
        val c = testVault(); c.rememberEnvelope("u1", env2)
        assertTrue(c.unlockWithPassphrase("u1", "new passphrase".toCharArray()))
        assertEquals("hello", NoteCipher.decryptText(c.masterKey("u1")!!, payload))
    }

    @Test fun keyVault_isPerUser_andCannotBeOverwritten() {
        val v = testVault()
        v.setUp("alice", "alice-pass".toCharArray())
        assertEquals(VaultState.NOT_SET_UP, v.state("bob"))
        try { v.setUp("alice", "another-pass".toCharArray()); fail() } catch (_: IllegalStateException) {}
        try { v.setUp("bob", "short".toCharArray()); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test fun pinHasher_saltedAndLegacyCompatible() {
        val h1 = PinHasher.hash("1234", 1000)
        val h2 = PinHasher.hash("1234", 1000)
        assertNotEquals(h1, h2) // salted
        assertTrue(h1.length <= 128)
        assertTrue(PinHasher.verify("1234", h1))
        assertFalse(PinHasher.verify("1235", h1))
        // v1 stored plain SHA-256 hex
        val legacy = CryptoPrimitives.sha256Hex("1234".toByteArray())
        assertTrue(PinHasher.verify("1234", legacy))
        assertTrue(PinHasher.needsUpgrade(legacy))
        assertFalse(PinHasher.needsUpgrade(h1))
    }

    @Test fun legacyV1Payload_fallbackKey_isReadable() {
        // v1 wrote ENCv1 with SHA-256("ArabicNotesKey:arabic_notes_vault") when Keystore was unavailable.
        val key = SecretKeySpec(CryptoPrimitives.sha256("ArabicNotesKey:arabic_notes_vault".toByteArray()), "AES")
        val iv = ByteArray(12) { 1 }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        val payload = "ENCv1:" + android.util.Base64.encodeToString(iv + c.doFinal("قديم".toByteArray()), android.util.Base64.NO_WRAP)
        assertEquals("قديم", com.example.core.security.LegacyCryptoV1.tryDecrypt(payload))
        assertNull(com.example.core.security.LegacyCryptoV1.tryDecrypt("ENCv1:AAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
    }

    @Test fun arabicNormalizer_ignoresDiacriticsAndLetterForms() {
        assertEquals(ArabicNormalizer.normalize("أَحْمَد"), ArabicNormalizer.normalize("احمد"))
        assertEquals(ArabicNormalizer.normalize("مدرسة"), ArabicNormalizer.normalize("مدرسه"))
        assertEquals(ArabicNormalizer.normalize("مستشفى"), ArabicNormalizer.normalize("مستشفي"))
        assertEquals("2024", ArabicNormalizer.normalize("٢٠٢٤"))
        assertEquals("كتاب", ArabicNormalizer.normalize("كتـــاب"))
        assertEquals("hello world", ArabicNormalizer.normalize("Hello   World"))
        assertEquals("احمد* 2024*", ArabicNormalizer.toFtsQuery("أحمد ٢٠٢٤"))
        assertNull(ArabicNormalizer.toFtsQuery("  ...  "))
        assertNotNull(ArabicNormalizer.toFtsQuery("\"quote\""))
    }
}
