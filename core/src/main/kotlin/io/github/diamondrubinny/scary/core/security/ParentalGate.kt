package io.github.diamondrubinny.scary.core.security

import io.github.diamondrubinny.scary.core.model.Movie

/**
 * When the parental PIN is asked for. Settings (no movie) always need it while the lock is on; a movie needs it at or
 * above the lock level, and movies outside the curated library (no fear level) count as the scariest. A correct PIN
 * unlocks for [UNLOCK_WINDOW_MS], or until SCARY leaves the screen.
 */
object ParentalGate {
    const val UNLOCK_WINDOW_MS = 30 * 60_000L

    fun needsPin(lockEnabled: Boolean, unlocked: Boolean, lockFromFear: Int, movie: Movie?): Boolean =
        lockEnabled && !unlocked && (movie == null || movie.fearForLock >= lockFromFear)

    /** Whether an unlock at [unlockedAtMillis] still holds at [nowMillis]. A clock moved backwards ends it too. */
    fun stillUnlocked(unlockedAtMillis: Long, nowMillis: Long): Boolean =
        nowMillis >= unlockedAtMillis && nowMillis - unlockedAtMillis <= UNLOCK_WINDOW_MS
}
