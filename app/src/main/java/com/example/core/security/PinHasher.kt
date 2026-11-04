package com.example.core.security

import android.util.Base64

internal object B64 {
    fun enc(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    fun dec(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
}

/**
 * Salted, slow PIN hashing. A PIN only gates the UI (app lock / note lock); it is never used as an
 * encryption key. Format: `p2$<iterations>$<salt b64>$<hash b64>` (max ~80 chars).
 * Legacy hashes (unsalted SHA-256 hex, 64 chars) are still verified so existing users keep access,
 * and [needsUpgrade] tells callers to re-hash after a successful verification.
 */
object PinHasher {
    private const val PREFIX = "p2"
    const val DEFAULT_ITERATIONS = 60_000

    fun hash(pin: String, iterations: Int = DEFAULT_ITERATIONS): String {
        val trimmed = pin.trim()
        require(trimmed.isNotEmpty()) { "PIN must not be empty" }
        val salt = CryptoPrimitives.randomBytes(16)
        val h = CryptoPrimitives.pbkdf2Sha256(trimmed.toCharArray(), salt, iterations)
        return "$PREFIX\$$iterations\$${B64.enc(salt)}\$${B64.enc(h)}"
    }

    fun verify(pin: String, stored: String): Boolean {
        if (stored.isBlank()) return false
        val trimmed = pin.trim()
        if (trimmed.isEmpty()) return false
        if (isLegacy(stored)) {
            val legacy = CryptoPrimitives.sha256Hex(trimmed.toByteArray(Charsets.UTF_8))
            return CryptoPrimitives.constantTimeEquals(legacy.toByteArray(), stored.lowercase().toByteArray())
        }
        val parts = stored.split("$")
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        if (iterations !in 1..5_000_000) return false
        return try {
            val salt = B64.dec(parts[2])
            val expected = B64.dec(parts[3])
            val actual = CryptoPrimitives.pbkdf2Sha256(trimmed.toCharArray(), salt, iterations, expected.size)
            CryptoPrimitives.constantTimeEquals(actual, expected)
        } catch (_: Exception) {
            false
        }
    }

    fun isLegacy(stored: String): Boolean = stored.length == 64 && stored.all { it in "0123456789abcdefABCDEF" }

    fun needsUpgrade(stored: String): Boolean = stored.isNotBlank() && isLegacy(stored)
}
