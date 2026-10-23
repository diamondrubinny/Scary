// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.Mood
import io.github.diamondrubinny.scary.core.catalog.Season
import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.model.Subgenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MoodsTest {
    private fun movie(
        id: String,
        fear: Int?,
        year: Int = 2010,
        vararg subgenres: Subgenre,
        curated: Boolean = true,
    ) = Movie(
        id = id, imdbId = "tt$id", title = id, year = year, genre = Genre.HORROR,
        subgenres = subgenres.toList(), fear = fear, curated = curated,
    )

    private val library = listOf(
        movie("calm", 1, 2015, Subgenre.MYSTERY),
        movie("ghost", 3, 2012, Subgenre.GHOST),
        movie("slasher", 4, 1978, Subgenre.SLASHER),
        movie("mild_creature", 2, 2001, Subgenre.CREATURE),
        movie("worst", 5, 2018, Subgenre.POSSESSION, Subgenre.PSYCHOLOGICAL),
        movie("online", null, 1980, Subgenre.SLASHER, curated = false),
    )

    private fun ids(movies: List<Movie>) = movies.map { it.id }

    @Test
    fun moodsPickFromTheCuratedLibraryOnly() {
        assertEquals(listOf("calm", "mild_creature"), ids(Mood.GENTLE.pick(library)))
        assertEquals(listOf("calm", "worst"), ids(Mood.MIND_GAMES.pick(library)))
        assertEquals(listOf("ghost", "worst"), ids(Mood.GHOSTS.pick(library)))
        // A creature movie with fear 2 is not "blood and screams".
        assertEquals(listOf("slasher"), ids(Mood.BLOOD.pick(library)))
        assertEquals(listOf("slasher"), ids(Mood.CLASSICS.pick(library)))
        assertEquals(listOf("worst", "slasher"), ids(Mood.RELENTLESS.pick(library)))
    }

    @Test
    fun moodKeysRoundTrip() {
        Mood.entries.forEach { assertEquals(it, Mood.fromKey(it.key)) }
        assertNull(Mood.fromKey("nope"))
        assertNull(Mood.fromKey(null))
    }

    @Test
    fun halloweenIsAllOfOctober() {
        assertTrue(Season.HALLOWEEN in Season.activeOn(LocalDate.of(2026, 10, 1)))
        assertTrue(Season.HALLOWEEN in Season.activeOn(LocalDate.of(2026, 10, 31)))
        assertTrue(Season.activeOn(LocalDate.of(2026, 11, 1)).isEmpty())
        assertTrue(Season.activeOn(LocalDate.of(2026, 9, 30)).isEmpty())
    }

    @Test
    fun friday13thIsTheWeekBeforeAndTheDay() {
        // November 13, 2026 is a Friday.
        assertEquals(listOf(Season.FRIDAY_13TH), Season.activeOn(LocalDate.of(2026, 11, 13)))
        assertEquals(listOf(Season.FRIDAY_13TH), Season.activeOn(LocalDate.of(2026, 11, 7)))
        assertTrue(Season.activeOn(LocalDate.of(2026, 11, 6)).isEmpty())
        assertTrue(Season.activeOn(LocalDate.of(2026, 11, 14)).isEmpty())
        // December 13, 2026 is a Sunday.
        assertTrue(Season.activeOn(LocalDate.of(2026, 12, 13)).isEmpty())
    }

    @Test
    fun aFriday13thInOctoberShowsBothRowsMostSpecificFirst() {
        // October 13, 2028 is a Friday.
        assertEquals(listOf(Season.FRIDAY_13TH, Season.HALLOWEEN), Season.activeOn(LocalDate.of(2028, 10, 10)))
    }

    @Test
    fun seasonalRowsAreScariestFirst() {
        assertEquals(listOf("worst", "slasher", "ghost", "mild_creature"), ids(Season.HALLOWEEN.pick(library)))
        assertEquals(listOf("slasher", "mild_creature"), ids(Season.FRIDAY_13TH.pick(library)))
    }
}
