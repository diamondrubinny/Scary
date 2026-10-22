package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.stability.CrashLoop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLoopTest {
    @Test
    fun threeCrashesInsideTheWindowAreALoop() {
        var times = emptyList<Long>()
        times = CrashLoop.record(times, 1_000)
        times = CrashLoop.record(times, 20_000)
        assertFalse(CrashLoop.isLoop(times, 21_000))
        times = CrashLoop.record(times, 60_000)
        assertTrue(CrashLoop.isLoop(times, 61_000))
    }

    @Test
    fun crashesSpreadOverTimeAreNotALoop() {
        var times = emptyList<Long>()
        for (t in listOf(0L, 100_000L, 200_000L, 300_000L)) times = CrashLoop.record(times, t)
        assertFalse(CrashLoop.isLoop(times, 301_000))
        assertEquals(listOf(200_000L, 300_000L), times)
    }

    @Test
    fun aLoopExpiresAfterTheWindow() {
        val times = listOf(1_000L, 2_000L, 3_000L)
        assertTrue(CrashLoop.isLoop(times, 4_000))
        assertFalse(CrashLoop.isLoop(times, 3_000 + CrashLoop.WINDOW_MS + 1))
    }

    @Test
    fun keepsOnlyTheLastFewTimes() {
        var times = emptyList<Long>()
        for (t in 1L..10L) times = CrashLoop.record(times, t * 1_000)
        assertEquals(CrashLoop.THRESHOLD, times.size)
        assertEquals(10_000L, times.last())
    }

    @Test
    fun futureTimesFromAClockChangeDoNotCount() {
        assertFalse(CrashLoop.isLoop(listOf(500_000L, 600_000L, 700_000L), 1_000))
    }

    @Test
    fun parseIgnoresMalformedInput() {
        assertEquals(listOf(5L, 7L), CrashLoop.parse("5, x,,7,-3"))
        assertEquals(emptyList<Long>(), CrashLoop.parse(null))
        assertEquals("5,7", CrashLoop.format(listOf(5L, 7L)))
    }
}
