// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.stability.KeyBacklog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyBacklogTest {
    private val down = 20

    @Test
    fun pressesOnTimeAreAlwaysDelivered() {
        val backlog = KeyBacklog()
        // Fast presses and a held key, all arriving on time.
        (0 until 10).forEach { i ->
            val t = 1_000L + i * 50
            assertTrue(backlog.deliver(down, down = true, repeatCount = i, madeAt = t, now = t + 20))
        }
        assertTrue(backlog.deliver(down, down = false, repeatCount = 0, madeAt = 1_600, now = 1_610))
    }

    @Test
    fun afterAStallOnlyTheFirstPressOfTheBacklogIsDelivered() {
        val backlog = KeyBacklog()
        // Five presses made during a two-second stall arrive together when it ends.
        val arrived = (0 until 5).map { i ->
            val made = 10_000L + i * 300
            val now = 12_000L + i
            backlog.deliver(down, down = true, repeatCount = 0, madeAt = made, now = now) to
                backlog.deliver(down, down = false, repeatCount = 0, madeAt = made + 80, now = now)
        }
        assertEquals(listOf(true to true, false to false, false to false, false to false, false to false), arrived)
        // The next press, on time, moves the focus as usual.
        assertTrue(backlog.deliver(down, down = true, repeatCount = 0, madeAt = 12_500, now = 12_510))
    }

    @Test
    fun lateRepeatsOfAHeldKeyAreDropped() {
        val backlog = KeyBacklog()
        assertTrue(backlog.deliver(down, down = true, repeatCount = 0, madeAt = 0, now = 10, pressedAt = 0))
        assertFalse(backlog.deliver(down, down = true, repeatCount = 3, madeAt = 100, now = 2_000, pressedAt = 0))
        // Its release still arrives: the press itself was delivered.
        assertTrue(backlog.deliver(down, down = false, repeatCount = 0, madeAt = 150, now = 2_001))
    }

    @Test
    fun aSingleLatePressAfterAQuietMomentIsDelivered() {
        val backlog = KeyBacklog()
        assertTrue(backlog.deliver(down, down = true, repeatCount = 0, madeAt = 0, now = 10))
        assertTrue(backlog.deliver(down, down = false, repeatCount = 0, madeAt = 50, now = 60))
        // One press during a stall, seconds later: the viewer meant it.
        assertTrue(backlog.deliver(down, down = true, repeatCount = 0, madeAt = 5_000, now = 6_500))
    }

    @Test
    fun repeatsStampedWithThePressTimeAreAlwaysDelivered() {
        val backlog = KeyBacklog()
        assertTrue(backlog.deliver(down, down = true, repeatCount = 0, madeAt = 0, now = 10, pressedAt = 0))
        // A remote that gives every repeat the press's own time: holding the key must keep scrolling.
        (1..20).forEach { i ->
            assertTrue(backlog.deliver(down, down = true, repeatCount = i, madeAt = 0, now = 400L + i * 50, pressedAt = 0))
        }
    }

    @Test
    fun aClockThatWentBackwardsDoesNotDropPresses() {
        val backlog = KeyBacklog()
        assertTrue(backlog.deliver(down, down = true, repeatCount = 0, madeAt = 0, now = 5_000))
        assertTrue(backlog.deliver(down, down = true, repeatCount = 0, madeAt = 0, now = 4_000))
    }
}
