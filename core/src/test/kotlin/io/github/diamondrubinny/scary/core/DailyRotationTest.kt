// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.DailyRotation
import io.github.diamondrubinny.scary.core.catalog.Decades
import io.github.diamondrubinny.scary.core.catalog.FeedCatalog
import io.github.diamondrubinny.scary.core.catalog.Mood
import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.model.Subgenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A row begins with other films each day, and with the same ones all day. */
class DailyRotationTest {
    private val films = (1..80).map { "f$it" }

    @Test
    fun `a row shows another screenful first each day, and every film in its turn`() {
        val days = (0L until 13L).map { DailyRotation.turned(films, "slashers", 20_000 + it) }
        // Thirteen screenfuls in eighty films: thirteen days, thirteen different beginnings.
        assertEquals(13, days.map { it.take(DailyRotation.STEP) }.toSet().size)
        // Each day's first six are six that stand together in the row, in the row's own order.
        for (row in days) {
            val at = films.indexOf(row.first())
            assertEquals(0, at % DailyRotation.STEP)
            assertEquals(films.subList(at, at + DailyRotation.STEP), row.take(DailyRotation.STEP))
        }
        // Over the round, every film but the row's short tail has stood among the first six.
        assertEquals(78, days.flatMap { it.take(DailyRotation.STEP) }.toSet().size)
        // And the fourteenth day is the first again.
        assertEquals(days[0], DailyRotation.turned(films, "slashers", 20_013))
    }

    @Test
    fun `turning loses no film and repeats none`() {
        for (day in listOf(-5L, 0L, 1L, 20_000L, 20_001L, Long.MAX_VALUE / 2)) {
            val turned = DailyRotation.turned(films, "vampires", day)
            assertEquals(films.size, turned.size)
            assertEquals(films.toSet(), turned.toSet())
        }
    }

    @Test
    fun `the row is the same all day, and rows are out of step with one another`() {
        assertEquals(DailyRotation.turned(films, "zombies", 20_000), DailyRotation.turned(films, "zombies", 20_000))
        assertNotEquals(DailyRotation.turned(films, "zombies", 20_000), DailyRotation.turned(films, "zombies", 20_001))
        val firsts = listOf("slashers", "zombies", "vampires", "creatures", "classics", "thrillers").map { DailyRotation.turned(films, it, 20_000).first() }
        assertTrue("rows in step: $firsts", firsts.toSet().size >= 3)
    }

    @Test
    fun `the newest films keep their order, and a short row is left alone`() {
        assertEquals(films, DailyRotation.turned(films, "new_nightmares", 20_003))
        val short = films.take(DailyRotation.MIN_FILMS - 1)
        assertEquals(short, DailyRotation.turned(short, "psychological_horror", 20_003))
        assertEquals(emptyList<String>(), DailyRotation.turned(emptyList<String>(), "slashers", 20_003))
    }

    @Test
    fun `a film an online row brought keeps what the feed knows of it`() {
        val own = Movie(
            id = "cm_tt0081505", imdbId = "tt0081505", title = "The Shining", year = 1980, genre = Genre.HORROR, fear = 5, curated = true,
            titles = mapOf("he" to "הניצוץ", "ru" to "Сияние"), subgenres = listOf(Subgenre.HAUNTED_HOUSE), runtimeMin = 144, director = "Stanley Kubrick",
        )
        val online = Movie(
            id = "cm_tt0081505", imdbId = "tt0081505", title = "The Shining", year = 1980, genre = Genre.HORROR, curated = false,
            posterUrl = "https://images.example/p.jpg", cast = listOf("Jack Nicholson"), titles = mapOf("ru" to "Сияние (ТВ)", "pt" to ""), imdbRating = 8.4,
        )
        val shown = FeedCatalog.withOwnFields(online, own)
        assertEquals("הניצוץ", shown.displayTitle(Language.HE))
        assertEquals(5, shown.fear)
        assertTrue(shown.curated)
        assertEquals(listOf(Subgenre.HAUNTED_HOUSE), shown.subgenres)
        assertEquals(144, shown.runtimeMin)
        // What the online row gave is kept, its own translation included; an empty one does not wipe the feed's.
        assertEquals("https://images.example/p.jpg", shown.posterUrl)
        assertEquals(listOf("Jack Nicholson"), shown.cast)
        assertEquals("Сияние (ТВ)", shown.titles["ru"])
        assertEquals(8.4, shown.imdbRating!!, 0.0)
        assertEquals("The Shining", shown.displayTitle(Language.PT))
    }

    @Test
    fun `films that tie are ordered by name, whichever stood first in the list`() {
        fun film(id: String, title: String, year: Int, fear: Int) =
            Movie(id = id, imdbId = id, title = title, year = year, genre = Genre.HORROR, fear = fear, curated = true, runtimeMin = 100)
        val a = film("tt1", "Alpha", 1980, 5)
        val b = film("tt2", "Beta", 1980, 5)
        val c = film("tt3", "Gamma", 1981, 5)
        assertEquals(Decades.pick(listOf(a, b, c), null, 1980), Decades.pick(listOf(c, b, a), null, 1980))
        assertEquals(listOf("Alpha", "Beta", "Gamma"), Decades.pick(listOf(c, b, a), null, 1980).map { it.title })
        assertEquals(Mood.RELENTLESS.pick(listOf(a, b, c)), Mood.RELENTLESS.pick(listOf(b, c, a)))
    }
}
