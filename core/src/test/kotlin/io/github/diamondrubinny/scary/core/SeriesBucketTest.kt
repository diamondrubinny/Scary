// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.AutoWatched
import io.github.diamondrubinny.scary.core.catalog.BucketList
import io.github.diamondrubinny.scary.core.catalog.BucketReturn
import io.github.diamondrubinny.scary.core.catalog.CatalogException
import io.github.diamondrubinny.scary.core.catalog.CatalogParser
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.links.Links
import io.github.diamondrubinny.scary.core.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The series library, series from Cinemeta, and the bucket list. */
class SeriesBucketTest {
    private val moviesText = File("../app/src/main/assets/catalog.json").readText()
    private val seriesText = File("../app/src/main/assets/series.json").readText()

    @Test
    fun `the bundled series library is valid, all series, and shares no IMDb id with the films`() {
        val series = CatalogParser.parse(seriesText, MediaKind.SERIES)
        assertTrue(series.movies.size >= 30)
        assertTrue(series.movies.all { it.isSeries && it.curated })
        val films = CatalogParser.parse(moviesText)
        assertTrue(films.movies.none { it.isSeries })
        assertTrue(series.movies.map { it.imdbId }.intersect(films.movies.map { it.imdbId }.toSet()).isEmpty())
        assertTrue(series.movies.map { it.id }.intersect(films.movies.map { it.id }.toSet()).isEmpty())
        assertTrue(series.collections.map { it.id }.intersect(films.collections.map { it.id }.toSet()).isEmpty())
    }

    @Test
    fun `a film in the series library, or a series among the films, is refused`() {
        assertThrows(CatalogException::class.java) { CatalogParser.parse(seriesText, MediaKind.MOVIE) }
        assertThrows(CatalogException::class.java) { CatalogParser.parse(moviesText, MediaKind.SERIES) }
    }

    @Test
    fun `a series reads its years as a span`() {
        val series = CatalogParser.parse(seriesText, MediaKind.SERIES)
        assertEquals("2016–2025", series.movie("stranger_things")!!.yearsLabel)
        assertEquals("2022–", series.movie("from")!!.yearsLabel)
        assertEquals("2018", series.movie("hill_house")!!.yearsLabel)
        assertEquals("1960", CatalogParser.parse(moviesText).movie("psycho")!!.yearsLabel)
    }

    @Test
    fun `Cinemeta series come with their kind and years, and their addresses ask for series`() {
        val catalog = Cinemeta.parseCatalog(
            """{"metas":[
                {"id":"tt4574334","type":"series","name":"Stranger Things","releaseInfo":"2016–2025","genres":["Horror"]},
                {"id":"tt9813792","type":"series","name":"From","releaseInfo":"2022-","genres":["Horror"]},
                {"id":"tt6763664","type":"series","name":"The Haunting of Hill House","releaseInfo":"2018","genres":["Horror"]},
                {"id":"tt0054215","type":"movie","name":"Psycho","releaseInfo":"1960","genres":["Horror"]},
                {"id":"tt0000002","type":"series","name":"Odd","releaseInfo":"2020–1999","genres":["Horror"]}
            ]}""",
        )
        val (ended, running, oneSeason, film, odd) = catalog.map { Cinemeta.toMovie(it)!! }
        assertEquals(MediaKind.SERIES, ended.kind)
        assertEquals(2016, ended.year)
        assertEquals(2025, ended.endYear)
        assertFalse(ended.ongoing)
        assertTrue(running.ongoing)
        assertNull(running.endYear)
        assertNull(oneSeason.endYear)
        assertFalse(oneSeason.ongoing)
        assertEquals(MediaKind.MOVIE, film.kind)
        assertNull(film.endYear)
        assertNull(odd.endYear)
        assertFalse(odd.ongoing)
        // A blank "releaseInfo" leaves the year to the "year" field.
        val blank = Cinemeta.parseCatalog("""{"metas":[{"id":"tt0000003","type":"series","name":"B","releaseInfo":"","year":"2019","genres":["Horror"]}]}""")
        assertEquals(2019, blank.single().year)
        // Saved and rebuilt, a series stays one.
        assertEquals(ended, Cinemeta.toMovie(Cinemeta.fromMovie(ended)))
        assertEquals("https://v3-cinemeta.strem.io/meta/series/tt4574334.json", Cinemeta.metaUrl("tt4574334", MediaKind.SERIES))
        assertEquals(
            "https://v3-cinemeta.strem.io/catalog/series/top/genre=Horror.json",
            Cinemeta.catalogUrl("top", "Horror", kind = MediaKind.SERIES),
        )
        assertEquals("https://v3-cinemeta.strem.io/catalog/series/top/search=dark.json", Cinemeta.searchUrl("dark", MediaKind.SERIES))
        assertEquals(CinemetaMeta.TYPE_MOVIE, Cinemeta.fromMovie(film).type)
    }

    @Test
    fun `online data updates a running series' years, never a film's`() {
        val series = CatalogParser.parse(seriesText, MediaKind.SERIES).movie("from")!!
        val ended = Cinemeta.enrich(series, CinemetaMeta("tt9813792", "From", 2022, type = CinemetaMeta.TYPE_SERIES, endYear = 2027))
        assertEquals("2022–2027", ended.yearsLabel)
        val film = CatalogParser.parse(moviesText).movie("psycho")!!
        assertEquals(film.endYear, Cinemeta.enrich(film, CinemetaMeta("tt0054215", "Psycho", 1960, endYear = 1999)).endYear)
    }

    @Test
    fun `Stremio opens a series on its page`() {
        assertEquals("stremio:///detail/series/tt4574334", Links.stremioSeries("tt4574334"))
        assertThrows(IllegalArgumentException::class.java) { Links.stremioSeries("tt4574334/../x") }
    }

    @Test
    fun `the bucket list adds, removes, reorders and moves on`() {
        var list = emptyList<String>()
        list = BucketList.toggle(list, "a")
        list = BucketList.toggle(list, "b")
        list = BucketList.toggle(list, "c")
        assertEquals(listOf("a", "b", "c"), list)
        assertEquals(listOf("a", "c"), BucketList.toggle(list, "b"))
        assertEquals(listOf("a", "c", "b"), BucketList.move(list, "b", 1))
        assertEquals(listOf("b", "a", "c"), BucketList.move(list, "b", -1))
        assertEquals(list, BucketList.move(list, "a", -1))
        assertEquals(list, BucketList.move(list, "c", 1))
        assertEquals(list, BucketList.move(list, "x", 1))
        assertEquals("b", BucketList.after(list, "a"))
        assertNull(BucketList.after(list, "c"))
        assertEquals("a", BucketList.after(list, "gone"))
        assertNull(BucketList.after(emptyList(), null))
        val full = (1..BucketList.MAX_SIZE).map { "m$it" }
        assertEquals(full, BucketList.toggle(full, "extra"))
    }

    @Test
    fun `back after most of the film counts as its end, even after a very long time away`() {
        val minute = 60_000L
        assertFalse(BucketReturn.finished(5 * minute, 100))
        assertFalse(BucketReturn.finished(50 * minute, 100))
        assertTrue(BucketReturn.finished(61 * minute, 100))
        assertTrue(BucketReturn.finished(AutoWatched.MAX_AWAY_MS * 2, 100))
        assertTrue(BucketReturn.finished(AutoWatched.UNKNOWN_RUNTIME_AWAY_MS, 0))
    }

    @Test
    fun `an endless row goes on to its catalog's end, and a row through the years carries on with the year before`() {
        val step = io.github.diamondrubinny.scary.core.cinemeta.EndlessCursor
        // A plain row: the next skip, until a page comes back empty or the catalog's furthest page.
        assertEquals(150, step.after(100, 50, yearChain = false))
        assertNull(step.after(100, 0, yearChain = false))
        assertNull(step.after(Cinemeta.MAX_SKIP, 50, yearChain = false))
        // Through the years: an empty page moves to the year before, from its first page.
        val start = 100
        assertEquals(2024, step.year(2024, start))
        val nextYear = step.after(start, 0, yearChain = true)!!
        assertEquals(2023, step.year(2024, nextYear))
        assertEquals(0, step.skip(nextYear))
        assertEquals(nextYear + 50, step.after(nextYear, 50, yearChain = true))
        assertEquals(2023, step.year(2024, nextYear + 50))
        // Never past the first year.
        val past = (2024 - step.FIRST_YEAR + 1) * step.YEAR_STEP
        assertNull(step.year(2024, past))
    }

    @Test
    fun `TMDB rows can be asked for any page up to its last`() {
        val url = io.github.diamondrubinny.scary.core.tmdb.Tmdb.discoverUrl(
            io.github.diamondrubinny.scary.core.tmdb.TmdbRow.TOP_HORROR, java.time.LocalDate.of(2026, 10, 30),
            io.github.diamondrubinny.scary.core.model.Language.EN, page = 7,
        )
        assertTrue("page=7" in url)
        assertThrows(IllegalArgumentException::class.java) {
            io.github.diamondrubinny.scary.core.tmdb.Tmdb.discoverUrl(
                io.github.diamondrubinny.scary.core.tmdb.TmdbRow.TOP_HORROR, java.time.LocalDate.of(2026, 10, 30),
                io.github.diamondrubinny.scary.core.model.Language.EN, page = 501,
            )
        }
    }
}
