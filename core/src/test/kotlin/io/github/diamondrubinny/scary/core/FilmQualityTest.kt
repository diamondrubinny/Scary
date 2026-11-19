// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.Categories
import io.github.diamondrubinny.scary.core.catalog.FilmQuality
import io.github.diamondrubinny.scary.core.catalog.FilmQuality.Problem
import io.github.diamondrubinny.scary.core.catalog.RowAudit
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaCache
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.cinemeta.OnlineRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The checks on the film itself. The films are real ones, as Cinemeta's catalogs gave them on 19.11.2026 (name, year,
 * rating, length and genres): each was on the screen that day.
 */
class FilmQualityTest {
    private val thisYear = 2026
    private var next = 0

    private fun film(
        name: String,
        year: Int,
        rating: Double?,
        minutes: Int?,
        vararg genres: String,
        poster: String? = "https://images.metahub.space/poster/small/tt0000001/img",
        description: String = "A synopsis.",
        type: String = CinemetaMeta.TYPE_MOVIE,
    ) = CinemetaMeta(
        imdbId = "tt%07d".format(++next), name = name, year = year, poster = poster, description = description,
        genres = genres.toList(), imdbRating = rating, runtimeMin = minutes, type = type,
    )

    private fun problem(meta: CinemetaMeta, newRow: Boolean = false) = FilmQuality.problem(meta, newRow, thisYear)

    @Test
    fun `the films worth offering pass`() {
        assertNull(problem(film("Hereditary", 2018, 7.3, 127, "Drama", "Horror", "Mystery")))
        assertNull(problem(film("The Silence of the Lambs", 1991, 8.6, 118, "Crime", "Drama", "Thriller")))
        assertNull(problem(film("Se7en", 1995, 8.6, 127, "Crime", "Drama", "Mystery")))
        // A horror comedy and a horror romance are horror: fear is in them.
        assertNull(problem(film("Shaun of the Dead", 2004, 7.9, 99, "Comedy", "Horror")))
        assertNull(problem(film("Bram Stoker's Dracula", 1992, 7.4, 128, "Drama", "Horror", "Romance")))
        // "Host" is 57 minutes and a real feature; a film whose length is not known is not judged by it.
        assertNull(problem(film("Host", 2020, 6.5, 57, "Horror", "Mystery")))
        assertNull(problem(film("Unknown Length", 2015, 6.0, null, "Horror")))
        // Exactly on the lines.
        assertNull(problem(film("On the line", 2015, FilmQuality.MIN_RATING, FilmQuality.MIN_MINUTES, "Horror")))
    }

    @Test
    fun `a short is not a film for the evening`() {
        assertEquals(Problem.TOO_SHORT, problem(film("The Death of David Cronenberg", 2023, 6.1, 1, "Drama", "Horror")))
        assertEquals(Problem.TOO_SHORT, problem(film("Backstroke", 2017, 6.3, 11, "Horror", "Thriller")))
        assertEquals(Problem.TOO_SHORT, problem(film("The Blair Thumb", 2002, 6.6, 28, "Comedy", "Horror", "Mystery")))
        // A series' length is one episode's: it is never "too short".
        assertNull(problem(film("From", 2022, 7.7, 51, "Drama", "Horror", "Mystery", type = CinemetaMeta.TYPE_SERIES)))
    }

    @Test
    fun `a film with no rating belongs only where new is the point, and only when it is new`() {
        val notOutYet = film("Resident Evil", 2026, null, 94, "Action", "Horror", "Sci-Fi")
        assertEquals(Problem.UNRATED, problem(notOutYet))
        assertNull(problem(notOutYet, newRow = true))
        assertNull(problem(film("Next year's", 2027, null, 100, "Horror"), newRow = true))
        // An old film nobody rated is not "new", wherever it shows up.
        assertEquals(Problem.UNRATED, problem(film("Forgotten", 2019, null, 90, "Horror"), newRow = true))
        assertEquals(Problem.UNRATED, problem(film("No year", 0, null, 90, "Horror").copy(year = null), newRow = true))
        assertTrue(FilmQuality.isNewRow("horror_new") && FilmQuality.isNewRow("series_new") && FilmQuality.isNewRow("new_nightmares"))
        assertTrue(!FilmQuality.isNewRow("horror_popular") && !FilmQuality.isNewRow("horror_featured") && !FilmQuality.isNewRow("horror_last_year"))
    }

    @Test
    fun `a film rated under the floor is not offered, in a new row either`() {
        assertEquals(Problem.LOW_RATED, problem(film("V/H/S/Mixtape", 2026, 4.2, 98, "Horror")))
        assertEquals(Problem.LOW_RATED, problem(film("V/H/S/Mixtape", 2026, 4.2, 98, "Horror"), newRow = true))
        assertEquals(Problem.LOW_RATED, problem(film("Just under", 2010, 4.9, 95, "Horror")))
    }

    @Test
    fun `a thriller or a mystery about something other than fear is left out`() {
        assertEquals(Problem.NO_FEAR, problem(film("Apocalypse Now", 1979, 8.4, 147, "Drama", "Mystery", "War")))
        assertEquals(Problem.NO_FEAR, problem(film("Clue", 1985, 7.3, 94, "Comedy", "Crime", "Mystery")))
        assertEquals(Problem.NO_FEAR, problem(film("Mr. Monk's Last Case: A Monk Movie", 2023, 6.7, 97, "Comedy", "Crime", "Mystery")))
        assertEquals(Problem.NO_FEAR, problem(film("Smooth Talk", 1985, 6.5, 92, "Drama", "Romance", "Thriller")))
        assertEquals(Problem.NO_FEAR, problem(film("Pressure", 2026, 7.3, 101, "Drama", "History", "Thriller")))
        // Genre names are read whatever their letter case.
        assertEquals(Problem.NO_FEAR, problem(film("Lower case", 2000, 7.0, 100, "thriller", "WAR")))
    }

    @Test
    fun `a film with no poster or no synopsis is incomplete`() {
        assertEquals(Problem.INCOMPLETE, problem(film("No poster", 2015, 7.0, 100, "Horror", poster = null)))
        assertEquals(Problem.INCOMPLETE, problem(film("Blank poster", 2015, 7.0, 100, "Horror", poster = " ")))
        assertEquals(Problem.INCOMPLETE, problem(film("L'Avocat", 2027, null, 121, "Thriller", description = "")))
        // A synopsis only in the viewer's language (a row from TMDB) is a synopsis.
        assertNull(problem(film("Local", 2015, 7.0, 100, "Horror", description = "").copy(localDescription = "תקציר")))
    }

    @Test
    fun `a row keeps its good films in order, and each film taken out is counted`() {
        val good = film("Hereditary", 2018, 7.3, 127, "Horror")
        val alsoGood = film("Alien", 1979, 8.5, 117, "Horror", "Sci-Fi")
        val row = listOf(
            good, film("Backstroke", 2017, 6.3, 11, "Horror"), film("Resident Evil", 2026, null, 94, "Horror"),
            alsoGood, film("Clue", 1985, 7.3, 94, "Comedy", "Mystery"), film("Low", 2024, 4.4, 90, "Horror"),
        )
        val removed = ArrayList<Problem>()
        assertEquals(listOf(good, alsoGood), FilmQuality.accepted(row, "horror_popular", thisYear, removed::add))
        assertEquals(listOf(Problem.TOO_SHORT, Problem.UNRATED, Problem.NO_FEAR, Problem.LOW_RATED), removed)
        // The same films in this year's row: the one not out yet stays.
        assertEquals(3, FilmQuality.accepted(row, "horror_new", thisYear).size)
    }

    @Test
    fun `the categories, the audit and an older cache all use the same check`() {
        val short = film("Zombie Short", 2015, 7.0, 12, "Horror", description = "Zombies rise from their graves.")
        val full = short.copy(imdbId = "tt7654321", runtimeMin = 95)
        assertTrue(Categories.matches("zombies", full, thisYear))
        assertTrue(!Categories.matches("zombies", short, thisYear))
        assertEquals(RowAudit.Problem.TOO_SHORT, RowAudit.check("zombies", short, thisYear))
        assertEquals(RowAudit.Problem.UNRATED, RowAudit.check("horror_popular", full.copy(imdbRating = null), thisYear))
        assertEquals(RowAudit.Problem.LOW_RATED, RowAudit.check("horror_popular", full.copy(imdbRating = 3.0), thisYear))
        assertEquals(RowAudit.Problem.NO_FEAR, RowAudit.check("thriller_popular", full.copy(genres = listOf("Thriller", "War")), thisYear))
        assertEquals(RowAudit.Problem.INCOMPLETE, RowAudit.check("horror_popular", full.copy(poster = null), thisYear))
        assertNull(RowAudit.check("horror_popular", full, thisYear))

        // A cache written before the rules: its rows and its categories lose the films the rules leave out.
        val cache = CinemetaCache(
            fetchedAtMillis = 1,
            metas = mapOf(short.imdbId to short, full.imdbId to full),
            rows = listOf(OnlineRow("horror_popular", "", "", listOf(short, full)), OnlineRow("only_shorts", "", "", listOf(short))),
            categories = mapOf("zombies" to listOf(short.imdbId, full.imdbId)),
        ).withoutHidden(thisYear)
        assertEquals(listOf("horror_popular"), cache.rows.map { it.id })
        assertEquals(listOf(full), cache.rows.single().metas)
        assertEquals(mapOf("zombies" to listOf(full.imdbId)), cache.categories)
    }
}
