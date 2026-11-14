package edu.msrit.canteen.backend.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Versioned PBKDF2-HMAC-SHA256 format. A fresh 256-bit salt is used for every password. */
object PasswordHasher {
    private const val ITERATIONS = 600_000
    private const val KEY_BITS = 256
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun hash(password: String): String {
        val salt = ByteArray(32).also(random::nextBytes)
        val derived = derive(password, salt, ITERATIONS)
        return "pbkdf2-sha256\$$ITERATIONS\$${encoder.encodeToString(salt)}\$${encoder.encodeToString(derived)}"
    }

    fun verify(password: String, encoded: String): Boolean {
        val parts = encoded.split('$')
        if (parts.size != 4 || parts[0] != "pbkdf2-sha256") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        if (iterations !in 100_000..2_000_000) return false
        val salt = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(parts[3]) }.getOrNull() ?: return false
        if (salt.size != 32 || expected.size != 32) return false
        return MessageDigest.isEqual(derive(password, salt, iterations), expected)
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}
