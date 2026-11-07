// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.CategoryPaging
import io.github.diamondrubinny.scary.core.catalog.Personalize
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.cinemeta.EndlessCursor
import io.github.diamondrubinny.scary.core.cinemeta.OnlineRow
import io.github.diamondrubinny.scary.core.model.Catalog
import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.MediaKind
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.model.Subgenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The home rows: the right kind in each tab, each film once, endless categories, taste inside a row. */
class RowsTest {
    private fun meta(imdb: String, name: String, type: String = CinemetaMeta.TYPE_MOVIE, years: Boolean = false) =
        CinemetaMeta(imdbId = imdb, name = name, year = 2025, genres = listOf("Horror"), type = type, runsOn = years)

    @Test
    fun `a title Cinemeta lists as a film and as a series never puts a series row on the films tab`() {
        val base = Catalog(1, emptyList(), emptyList())
        // Haunted Hotel: tt32499579 in the horror films' catalog ("2025–") and in the series' one.
        val films = OnlineRow("horror_popular", "", "Popular horror", (1..4).map { meta("tt100000$it", "Film $it") } + meta("tt32499579", "Haunted Hotel", years = true))
        val series = OnlineRow("series_horror", "", "Popular horror series", (5..8).map { meta("tt100000$it", "Show $it", CinemetaMeta.TYPE_SERIES) } + meta("tt32499579", "Haunted Hotel", CinemetaMeta.TYPE_SERIES))
        val merged = Cinemeta.merge(base, emptyMap(), listOf(films, series))
        val filmTab = merged.ofKind(MediaKind.MOVIE).collections.map { it.id }
        assertEquals(listOf(Cinemeta.onlineCollectionId("horror_popular")), filmTab)
        // Its years run on: it is a series, kept only in the series row.
        assertEquals(MediaKind.SERIES, merged.movies.first { it.imdbId == "tt32499579" }.kind)
        // And the same the other way round, when the film row comes second.
        val reversed = Cinemeta.merge(base, emptyMap(), listOf(series, films.copy(metas = films.metas.map { it.copy(runsOn = false) })))
        assertEquals(listOf(Cinemeta.onlineCollectionId("horror_popular")), reversed.ofKind(MediaKind.MOVIE).collections.map { it.id })
        assertTrue(reversed.ofKind(MediaKind.MOVIE).movies.none { it.imdbId == "tt32499579" })
    }

    private fun film(id: String, curated: Boolean = false, vararg subs: Subgenre) =
        Movie(id = id, imdbId = "tt0000001", title = id, year = 2000, genre = Genre.HORROR, subgenres = subs.toList(), curated = curated)

    @Test
    fun `each film from the web shows once down the screen, library films stay in their categories`() {
        val shared = film("web_shared")
        val library = film("library", curated = true)
        val rows = listOf(
            "popular" to listOf(shared, film("web_a")),
            "zombies" to listOf(library, shared, film("web_b")),
            "classics" to listOf(library, film("web_c")),
        )
        val result = Personalize.withoutRepeats(rows) { key, movie -> movie.curated && key != "popular" }
        assertEquals(listOf("web_shared", "web_a"), result[0].second.map { it.id })
        assertEquals(listOf("library", "web_b"), result[1].second.map { it.id })
        assertEquals(listOf("library", "web_c"), result[2].second.map { it.id })
    }

    @Test
    fun `inside a row the films that fit the taste come first, in blocks, after the library's`() {
        val library = film("library", curated = true)
        val ghosts = (1..3).map { film("ghost$it", false, Subgenre.GHOST) }
        val slashers = (1..3).map { film("slasher$it", false, Subgenre.SLASHER) }
        val byId = (ghosts + slashers).associateBy { it.id }
        val taste = Personalize.taste(byId::get, listOf("slasher1", "slasher2", "slasher3"), emptyList(), emptyList())
        val row = listOf(library) + ghosts + slashers
        val sorted = Personalize.byTasteInRow(row, taste, block = 6)
        assertEquals("library", sorted.first().id)
        assertEquals(setOf("slasher1", "slasher2", "slasher3"), sorted.subList(1, 4).map { it.id }.toSet())
        // In blocks: a film never moves into an earlier block.
        val blocks = Personalize.byTasteInRow(row, taste, block = 3)
        assertEquals(ghosts.map { it.id }.toSet(), blocks.subList(1, 4).map { it.id }.toSet())
        // No taste yet: nothing moves.
        assertEquals(row, Personalize.byTasteInRow(row, Personalize.Taste.NONE))
    }

    @Test
    fun `a category goes on past the daily pool, then back through the years, and ends`() {
        val year = 2026
        val first = CategoryPaging.page(MediaKind.MOVIE, 0, year)!!
        assertEquals(CategoryPaging.tops(MediaKind.MOVIE).first().first, first.genre)
        assertEquals(CategoryPaging.tops(MediaKind.MOVIE).first().second, first.skip)
        // A full page goes on in the same catalog; an empty one moves to the next.
        assertEquals(Cinemeta.PAGE_SIZE, CategoryPaging.after(MediaKind.MOVIE, 0, 50, year))
        val stages = CategoryPaging.tops(MediaKind.MOVIE).size
        var cursor = 0
        repeat(stages) { cursor = CategoryPaging.after(MediaKind.MOVIE, cursor, 0, year)!! }
        val years = CategoryPaging.page(MediaKind.MOVIE, cursor, year)!!
        assertNull(years.genre)
        assertEquals(year, years.year)
        assertEquals(0, years.skip)
        // Year after year, back to the first, then the end.
        var c = cursor
        var guard = 0
        while (true) {
            val next = CategoryPaging.after(MediaKind.MOVIE, c, 0, year) ?: break
            assertTrue(CategoryPaging.page(MediaKind.MOVIE, next, year)!!.year!! < CategoryPaging.page(MediaKind.MOVIE, c, year)!!.year!!)
            c = next
            assertTrue(guard++ < 200)
        }
        assertEquals(EndlessCursor.FIRST_YEAR, CategoryPaging.page(MediaKind.MOVIE, c, year)!!.year)
        assertFalse(CategoryPaging.POOL.isEmpty())
    }
}
