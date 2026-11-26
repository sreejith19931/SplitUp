package com.finance.splitup.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN hashing for the app's fallback unlock PIN.
 *
 * History: PINs were stored as an unsalted SHA-256 hex digest. A 4-digit PIN
 * space is only 10,000 values, so an unsalted hash is trivially brute-forceable
 * if the preferences file is ever exposed. New PINs use PBKDF2-HMAC-SHA256 with
 * a random per-PIN salt; legacy hashes are transparently verified and upgraded
 * to the new format on the next successful unlock.
 */
object PinSecurity {

    private const val PREFIX = "pbkdf2"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    /** Hashes a PIN in the current (salted PBKDF2) format. Returns null on crypto failure or blank pin. */
    fun hash(pin: String): String? {
        if (pin.isBlank()) return null
        return try {
            val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
            val digest = pbkdf2(pin, salt, ITERATIONS)
            // Format: pbkdf2$<iterations>$<saltB64>$<hashB64>
            PREFIX + "$" + ITERATIONS + "$" + b64(salt) + "$" + b64(digest)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Verifies a PIN against the stored value. Understands the current PBKDF2
     * format, the legacy unsalted SHA-256 hex format, and (for the oldest
     * installs) a plaintext stored PIN.
     */
    fun verify(pin: String, stored: String?): Boolean {
        if (pin.isBlank() || stored.isNullOrBlank()) return false
        return try {
            when {
                stored.startsWith(PREFIX + "$") -> {
                    val parts = stored.split("$")
                    // Expected: [pbkdf2, iterations, salt, hash]
                    if (parts.size != 4) return false
                    val iterations = parts[1].toInt()
                    val salt = unb64(parts[2])
                    val expected = unb64(parts[3])
                    val actual = pbkdf2(pin, salt, iterations)
                    // Constant-time comparison
                    MessageDigest.isEqual(expected, actual)
                }
                stored == legacyHash(pin) -> true
                else -> pin == stored // oldest format: plaintext
            }
        } catch (_: Exception) {
            false
        }
    }

    /** True when [stored] is a legacy (unsalted SHA-256 or plaintext) value that should be upgraded. */
    fun needsUpgrade(stored: String?): Boolean {
        return stored != null && !stored.startsWith(PREFIX + "$")
    }

    /** Legacy unsalted SHA-256 hex digest — kept only for verifying/migrating old stored PINs. */
    fun legacyHash(pin: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            pin
        }
    }

    private fun pbkdf2(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun b64(bytes: ByteArray): String =
        java.util.Base64.getEncoder().encodeToString(bytes)

    private fun unb64(value: String): ByteArray =
        java.util.Base64.getDecoder().decode(value)
}
