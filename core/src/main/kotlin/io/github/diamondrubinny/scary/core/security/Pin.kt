package io.github.diamondrubinny.scary.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** A stored PIN: never the digits themselves, only a salted PBKDF2 hash. */
data class PinHash(val hash: String, val salt: String, val iterations: Int) {
    fun encode(): String = "$iterations:$salt:$hash"

    companion object {
        fun decode(raw: String?): PinHash? {
            val parts = raw?.split(':') ?: return null
            if (parts.size != 3) return null
            val iterations = parts[0].toIntOrNull()?.takeIf { it > 0 } ?: return null
            return PinHash(hash = parts[2], salt = parts[1], iterations = iterations)
        }
    }
}

object PinHasher {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    /** Tuned so a check takes well under a second on a low-end TV box while slowing offline guessing. */
    const val DEFAULT_ITERATIONS = 60_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    private val random = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    fun isValidFormat(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun hash(pin: String, iterations: Int = DEFAULT_ITERATIONS): PinHash {
        require(isValidFormat(pin)) { "PIN must be $MIN_LENGTH-$MAX_LENGTH digits" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return PinHash(encoder.encodeToString(derive(pin, salt, iterations)), encoder.encodeToString(salt), iterations)
    }

    fun verify(pin: String, stored: PinHash): Boolean {
        if (!isValidFormat(pin)) return false
        val salt = runCatching { decoder.decode(stored.salt) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(stored.hash) }.getOrNull() ?: return false
        // Constant-time comparison.
        return MessageDigest.isEqual(derive(pin, salt, stored.iterations), expected)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

/** Failed-attempt state, persisted so restarting the app does not reset the wait. */
data class LockoutState(val failedAttempts: Int = 0, val lockedUntilMillis: Long = 0L)

object PinLockout {
    const val FREE_ATTEMPTS = 3
    private const val BASE_WAIT_MS = 30_000L
    private const val MAX_WAIT_MS = 15 * 60_000L

    fun remainingWaitMillis(state: LockoutState, nowMillis: Long): Long =
        (state.lockedUntilMillis - nowMillis).coerceAtLeast(0L)

    /** After three free attempts, each failure waits twice as long: 30 s, 1 min, 2 min ... up to 15 min. */
    fun onFailure(state: LockoutState, nowMillis: Long): LockoutState {
        val attempts = state.failedAttempts + 1
        if (attempts < FREE_ATTEMPTS) return LockoutState(attempts, 0L)
        val doublings = (attempts - FREE_ATTEMPTS).coerceAtMost(10)
        val wait = (BASE_WAIT_MS shl doublings).coerceAtMost(MAX_WAIT_MS)
        return LockoutState(attempts, nowMillis + wait)
    }

    fun onSuccess(): LockoutState = LockoutState()
}
