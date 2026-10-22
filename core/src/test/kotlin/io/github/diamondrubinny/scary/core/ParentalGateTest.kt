// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.security.ParentalGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentalGateTest {
    private fun movie(fear: Int?) = Movie(id = "m", imdbId = "tt1", title = "M", year = 2000, genre = Genre.HORROR, fear = fear)

    @Test
    fun noLockNeverAsks() {
        assertFalse(ParentalGate.needsPin(lockEnabled = false, unlocked = false, lockFromFear = 1, movie = movie(5)))
        assertFalse(ParentalGate.needsPin(lockEnabled = false, unlocked = false, lockFromFear = 1, movie = null))
    }

    @Test
    fun settingsAlwaysAskWhileLocked() {
        assertTrue(ParentalGate.needsPin(lockEnabled = true, unlocked = false, lockFromFear = 5, movie = null))
    }

    @Test
    fun moviesAskFromTheLockLevelUp() {
        assertFalse(ParentalGate.needsPin(lockEnabled = true, unlocked = false, lockFromFear = 4, movie = movie(3)))
        assertTrue(ParentalGate.needsPin(lockEnabled = true, unlocked = false, lockFromFear = 4, movie = movie(4)))
        // No fear level (online movies) counts as the scariest.
        assertTrue(ParentalGate.needsPin(lockEnabled = true, unlocked = false, lockFromFear = 5, movie = movie(null)))
    }

    @Test
    fun anUnlockSkipsThePin() {
        assertFalse(ParentalGate.needsPin(lockEnabled = true, unlocked = true, lockFromFear = 1, movie = movie(5)))
        assertFalse(ParentalGate.needsPin(lockEnabled = true, unlocked = true, lockFromFear = 1, movie = null))
    }

    @Test
    fun theUnlockLastsThirtyMinutesAndEndsIfTheClockGoesBack() {
        val at = 1_000_000L
        assertTrue(ParentalGate.stillUnlocked(at, at))
        assertTrue(ParentalGate.stillUnlocked(at, at + ParentalGate.UNLOCK_WINDOW_MS))
        assertFalse(ParentalGate.stillUnlocked(at, at + ParentalGate.UNLOCK_WINDOW_MS + 1))
        assertFalse(ParentalGate.stillUnlocked(at, at - 1))
    }
}
