// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.CatalogParser
import io.github.diamondrubinny.scary.core.catalog.FearStats
import io.github.diamondrubinny.scary.core.catalog.Recommender
import io.github.diamondrubinny.scary.core.catalog.TimeBudget
import io.github.diamondrubinny.scary.core.catalog.TimePlan
import io.github.diamondrubinny.scary.core.links.Links
import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.model.Subgenre
import io.github.diamondrubinny.scary.core.tmdb.Tmdb
import io.github.diamondrubinny.scary.core.tmdb.TmdbRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** "How much time do you have?", "Because you loved", fear stats, coming soon and "open on your phone". */
class HandyFeaturesTest {
    private val catalog = CatalogParser.parse(File("../app/src/main/assets/catalog.json").readText())
    private val today = LocalDate.of(2026, 10, 28)

    private fun movie(id: String, minutes: Int, fear: Int, vararg subs: Subgenre, director: String = "D", year: Int = 2000) =
        Movie(id = id, imdbId = "tt0000001", title = id, year = year, director = director, runtimeMin = minutes,
            genre = Genre.HORROR, subgenres = subs.toList(), fear = fear)

    @Test
    fun `every budget finds films in the real library that fit it`() {
        for (budget in TimeBudget.entries) {
            val plan = TimePlan.plan(catalog.movies, budget, today)
            assertTrue("$budget is empty", plan.movies.isNotEmpty())
            when (budget) {
                TimeBudget.QUICK -> assertTrue(plan.movies.all { it.runtimeMin <= 90 })
                TimeBudget.EVENING -> assertTrue(plan.movies.all { it.runtimeMin in 91..120 })
                TimeBudget.DOUBLE -> {
                    assertEquals(2, plan.movies.size)
                    assertTrue(plan.totalMinutes in 150..210)
                }
                TimeBudget.MARATHON -> {
                    assertEquals(3, plan.movies.size)
                    assertTrue(plan.totalMinutes in 240..300)
                }
            }
        }
    }

    @Test
    fun `a lineup warms up from the gentlest film to the scariest`() {
        val plan = TimePlan.plan(catalog.movies, TimeBudget.MARATHON, today)
        assertEquals(plan.movies.sortedBy { it.fear }.map { it.fear }, plan.movies.map { it.fear })
    }

    @Test
    fun `the plan holds within a day and changes from day to day`() {
        val a = TimePlan.plan(catalog.movies, TimeBudget.DOUBLE, today).movies
        assertEquals(a, TimePlan.plan(catalog.movies, TimeBudget.DOUBLE, today).movies)
        val week = (1..7L).map { TimePlan.plan(catalog.movies, TimeBudget.DOUBLE, today.plusDays(it)).movies }
        assertTrue(week.any { it != a })
    }

    @Test
    fun `films marked not for me, locked or watched stay out while others are left`() {
        val quick = TimePlan.plan(catalog.movies, TimeBudget.QUICK, today).movies
        val notForMe = quick.first().id
        val locked = quick[1].id
        val again = TimePlan.plan(catalog.movies, TimeBudget.QUICK, today, exclude = setOf(notForMe), watched = setOf(quick[2].id),
            isLocked = { it.id == locked }).movies
        assertTrue(again.none { it.id == notForMe || it.id == locked || it.id == quick[2].id })
    }

    @Test
    fun `because you loved finds similar films and skips loved, watched and disliked ones`() {
        val loved = movie("loved", 100, 4, Subgenre.GHOST, Subgenre.HAUNTED_HOUSE, director = "Wan")
        val twin = movie("twin", 100, 4, Subgenre.GHOST, Subgenre.HAUNTED_HOUSE, director = "Wan")
        val near = movie("near", 100, 3, Subgenre.GHOST)
        val also = movie("also", 100, 4, Subgenre.HAUNTED_HOUSE, director = "Wan")
        val seen = movie("seen", 100, 4, Subgenre.GHOST, Subgenre.HAUNTED_HOUSE)
        val unlike = movie("unlike", 100, 1, Subgenre.SLASHER, director = "X", year = 1970)
        val rec = Recommender.becauseYouLoved(listOf(loved, twin, near, also, seen, unlike), listOf("loved"), exclude = setOf("seen"))!!
        assertEquals("loved", rec.because.id)
        assertEquals(listOf("twin", "also", "near"), rec.movies.map { it.id })
        assertNull(Recommender.becauseYouLoved(listOf(loved, unlike), listOf("loved")))
    }

    @Test
    fun `the real library has recommendations for a loved classic`() {
        val rec = Recommender.becauseYouLoved(catalog.movies, listOf("the_shining"))!!
        assertTrue(rec.movies.size >= 5)
        assertTrue(rec.movies.none { it.id == "the_shining" })
    }

    @Test
    fun `fear stats sum up the watched list`() {
        val a = movie("a", 100, 5, Subgenre.GHOST, year = 1987)
        val b = movie("b", 90, 3, Subgenre.GHOST, Subgenre.SLASHER, year = 1981)
        val c = movie("c", 110, 5, Subgenre.SLASHER, year = 2019)
        val stats = FearStats.of(listOf(a, b, c), lovedCount = 2)
        assertEquals(3, stats.watchedCount)
        assertEquals(300, stats.totalMinutes)
        assertEquals(13.0 / 3, stats.averageFear!!, 0.001)
        assertEquals("a", stats.scariest!!.id)
        assertEquals(Subgenre.GHOST, stats.favoriteSubgenre)
        assertEquals(1980, stats.favoriteDecade)
        assertEquals(2, stats.lovedCount)
        assertTrue(FearStats.of(emptyList(), 0).isEmpty)
    }

    @Test
    fun `coming soon asks TMDB for horror out in the next six months`() {
        val url = Tmdb.discoverUrl(TmdbRow.COMING_SOON, today, Language.EN)
        assertTrue("with_genres=27" in url)
        assertTrue("primary_release_date.gte=2026-10-29" in url)
        assertTrue("primary_release_date.lte=2027-04-28" in url)
        val result = Tmdb.parseDiscover("""{"results":[{"id":5,"title":"Soon","release_date":"2026-11-14","genre_ids":[27]},
            {"id":6,"title":"Bad date","release_date":"14/11/2026","genre_ids":[27]}]}""")
        assertEquals("2026-11-14", result[0].releaseDate)
        assertNull(result[1].releaseDate)
        val movie = io.github.diamondrubinny.scary.core.cinemeta.Cinemeta.toMovie(Tmdb.toMeta(result[0].copy(year = 2026), "tt1234567", Language.EN))!!
        assertEquals(LocalDate.of(2026, 11, 14), movie.comingOn(today))
        assertNull(movie.comingOn(LocalDate.of(2026, 11, 14)))
        assertNotEquals(TmdbRow.COMING_SOON.titleHe, "")
    }

    @Test
    fun `open on your phone leads to the IMDb page, and only for a real IMDb id`() {
        assertEquals("https://www.imdb.com/title/tt0081505/", Links.imdbTitle("tt0081505"))
        // A short, plain link: the smallest code that holds it (no character-set marker), easy to scan from the sofa.
        assertEquals(29, io.github.diamondrubinny.scary.core.brand.Donation.qr(Links.imdbTitle("tt0081505")).size)
        assertThrows(IllegalArgumentException::class.java) { Links.imdbTitle("tt0081505/../x") }
    }
}
