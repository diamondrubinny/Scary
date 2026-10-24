// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.AutoWatched
import io.github.diamondrubinny.scary.core.catalog.NightPick
import io.github.diamondrubinny.scary.core.catalog.PendingWatch
import io.github.diamondrubinny.scary.core.catalog.WatchedList
import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.Movie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WatchedTest {
    private val minute = 60_000L

    private fun testMovie(id: String, fear: Int) =
        Movie(id = id, imdbId = "tt0000001", title = id, year = 2000, genre = Genre.HORROR, fear = fear, curated = true)

    @Test
    fun `the list keeps many movies, newest first, each once`() {
        var list = emptyList<String>()
        for (id in listOf("a", "b", "c", "b")) list = WatchedList.add(list, id)
        assertEquals(listOf("b", "c", "a"), list)
        assertEquals(listOf("b", "a"), WatchedList.remove(list, "c"))
        assertEquals(3, WatchedList.add(listOf("x", "y", "z"), "w", max = 3).size)
    }

    @Test
    fun `most of the movie away counts as watched`() {
        // A 100-minute movie needs 60 minutes away.
        assertFalse(AutoWatched.counts(59 * minute, runtimeMin = 100))
        assertTrue(AutoWatched.counts(60 * minute, runtimeMin = 100))
        assertTrue(AutoWatched.counts(130 * minute, runtimeMin = 100))
    }

    @Test
    fun `a quick look never counts, even for a short film`() {
        assertFalse(AutoWatched.counts(15 * minute, runtimeMin = 20))
        assertTrue(AutoWatched.counts(20 * minute, runtimeMin = 20))
    }

    @Test
    fun `without a runtime, 45 minutes counts`() {
        assertFalse(AutoWatched.counts(44 * minute, runtimeMin = 0))
        assertTrue(AutoWatched.counts(45 * minute, runtimeMin = 0))
    }

    @Test
    fun `a TV left on all night does not count`() {
        assertFalse(AutoWatched.counts(7 * 60 * minute, runtimeMin = 100))
        assertFalse(AutoWatched.counts(-5 * minute, runtimeMin = 100))
    }

    @Test
    fun `surprise me prefers a movie not seen yet, and still answers when all were seen`() {
        val movies = (1..5).map { testMovie("m$it", fear = 3) }
        val watched = setOf("m1", "m2", "m3", "m4")
        repeat(20) { seed ->
            assertEquals("m5", NightPick.surprise(movies, maxFear = 5, random = Random(seed), watched = watched)?.id)
        }
        assertTrue(NightPick.surprise(movies, maxFear = 5, random = Random(1), watched = movies.map { it.id }.toSet()) != null)
    }

    @Test
    fun `a pending watch survives being stored, and junk is ignored`() {
        val pending = PendingWatch("cm_tt0081505", 1_700_000_000_000)
        assertEquals(pending, PendingWatch.decode(pending.encode()))
        listOf(null, "", "id", "id|", "|123", "id|abc", "id|0", "a|b|c").forEach { assertNull(it, PendingWatch.decode(it)) }
    }
}
