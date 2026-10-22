// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.audio.DroneSynth
import io.github.diamondrubinny.scary.core.security.LockoutState
import io.github.diamondrubinny.scary.core.security.PinHash
import io.github.diamondrubinny.scary.core.security.PinHasher
import io.github.diamondrubinny.scary.core.security.PinLockout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SecurityAudioTest {

    @Test
    fun `pin hash verifies only the right pin and survives encoding`() {
        val stored = PinHasher.hash("4821", iterations = 1_000)
        val decoded = PinHash.decode(stored.encode())!!
        assertTrue(PinHasher.verify("4821", decoded))
        assertFalse(PinHasher.verify("4822", decoded))
        assertFalse(PinHasher.verify("48a1", decoded))
        assertFalse(stored.encode().contains("4821"))
    }

    @Test
    fun `same pin gets a different salt each time`() {
        assertNotEquals(PinHasher.hash("1234", 1_000).salt, PinHasher.hash("1234", 1_000).salt)
    }

    @Test
    fun `pin format and decode are strict`() {
        assertFalse(PinHasher.isValidFormat("123"))
        assertFalse(PinHasher.isValidFormat("123456789"))
        assertFalse(PinHasher.isValidFormat("12 4"))
        assertTrue(PinHasher.isValidFormat("000000"))
        assertNull(PinHash.decode(null))
        assertNull(PinHash.decode("x:y"))
        assertNull(PinHash.decode("-1:a:b"))
        assertFalse(PinHasher.verify("1234", PinHash("%%%", "%%%", 1000)))
    }

    @Test
    fun `lockout starts after three failures and doubles up to fifteen minutes`() {
        var state = LockoutState()
        val now = 1_000_000L
        repeat(2) { state = PinLockout.onFailure(state, now) }
        assertEquals(0L, PinLockout.remainingWaitMillis(state, now))
        state = PinLockout.onFailure(state, now)
        assertEquals(30_000L, PinLockout.remainingWaitMillis(state, now))
        state = PinLockout.onFailure(state, now)
        assertEquals(60_000L, PinLockout.remainingWaitMillis(state, now))
        repeat(20) { state = PinLockout.onFailure(state, now) }
        assertEquals(15 * 60_000L, PinLockout.remainingWaitMillis(state, now))
        assertEquals(LockoutState(), PinLockout.onSuccess())
    }

    @Test
    fun `drone stays quiet, finite and not silent`() {
        val synth = DroneSynth()
        val buffer = ShortArray(22_050 * 3)
        synth.render(buffer)
        val peak = buffer.maxOf { abs(it.toInt()) }
        assertTrue("peak $peak", peak in 500..(Short.MAX_VALUE / 4))
    }
}
