// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.Categories
import io.github.diamondrubinny.scary.core.catalog.Origin
import io.github.diamondrubinny.scary.core.catalog.RowAudit
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaCache
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.cinemeta.OnlineRow
import io.github.diamondrubinny.scary.core.tmdb.Tmdb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The European row held many American films: Cinemeta lists "Germany, United States" for Constantine, The Cell and Blade II.
 * These films, their countries as Cinemeta lists them and their original languages as TMDB gives them, are real.
 */
class OriginQualityTest {
    private var next = 0

    private fun film(name: String, country: String, language: String = "", year: Int = 2005, tmdbId: Long = 0) = CinemetaMeta(
        imdbId = "tt%07d".format(++next),
        name = name,
        year = year,
        // A whole film: the checks on the film itself (core FilmQuality) are not what these tests are about.
        poster = "https://images.metahub.space/poster/small/tt0000001/img",
        description = "A film.",
        imdbRating = 6.5,
        genres = listOf("Horror"),
        country = country,
        originalLanguage = language,
        tmdbId = tmdbId,
    )

    // Americans with a European country first (language: English).
    private val constantine = film("Constantine", "Germany, United States", "en")
    private val cell = film("The Cell", "Germany, United States", "en", 2000)
    private val blade = film("Blade II", "Germany, United States", "en", 2002)
    private val jeepers = film("Jeepers Creepers", "Germany, United States", "en", 2001)
    private val rejects = film("The Devil's Rejects", "Germany, United States", "en")
    private val land = film("Land of the Dead", "France, Canada, United States", "en")
    private val mindhunters = film("Mindhunters", "Netherlands, United Kingdom, Finland, United States", "en")
    private val resident = film("Resident Evil", "Germany, United States", "en", 2026)
    private val suspiria = film("Suspiria", "Italy, United States", "en", 2018)
    private val silentHill = film("Silent Hill", "France, Japan, United States", "en", 2006)
    private val quarantine = film("Quarantine", "Spain, United States", "en", 2008)
    private val americans = listOf(constantine, cell, blade, jeepers, rejects, land, mindhunters, resident, suspiria, silentHill, quarantine)

    // European films that list the United States too (language: theirs).
    private val lamb = film("Lamb", "Iceland, Sweden, Poland, United States", "is", 2021)
    private val infested = film("Infested", "France, United States, Germany", "fr", 2024)
    private val deadSnow = film("Dead Snow 2: Red vs. Dead", "Norway, Iceland, United States, United Kingdom", "no", 2014)
    private val trip = film("The Trip", "Norway, United States, Finland", "no", 2021)

    // European films with no American producer.
    private val martyrs = film("Martyrs", "France, Canada", year = 2008)
    private val raw = film("Raw", "France, Belgium", year = 2017)

    @Test
    fun `no American film with a European country first is in the European row`() {
        americans.forEach { assertFalse(it.name, Categories.matches("european_horror", it)) }
        // The same films with their language not known yet: held back, never guessed at.
        americans.forEach { assertEquals(it.name, Origin.Verdict.UNKNOWN, Origin.verdict(Origin.Region.EUROPE, it.copy(originalLanguage = ""))) }
    }

    @Test
    fun `European films stay, whether or not an American producer is listed, once their language says so`() {
        listOf(lamb, infested, deadSnow, trip, martyrs, raw).forEach { assertTrue(it.name, Categories.matches("european_horror", it)) }
        // Unknown language: the ones that list the United States wait, the others do not need one.
        assertFalse(Categories.matches("european_horror", lamb.copy(originalLanguage = "")))
        assertTrue(Categories.matches("european_horror", martyrs))
    }

    @Test
    fun `the other rows about a place work the same way`() {
        assertFalse(Categories.matches("spanish_horror", quarantine))
        assertTrue(Categories.matches("spanish_horror", film("Marrowbone", "Spain, United States", "es", 2018)))
        assertFalse(Categories.matches("spanish_horror", film("Marrowbone", "Spain, United States", "en", 2018)))
        // Ringu is Japanese and lists the United States: its language puts it in Asian horror.
        val ringu = film("Ringu", "Japan, United States", "ja", 1998)
        assertTrue(Categories.matches("asian_horror", ringu))
        assertFalse(Categories.matches("asian_horror", ringu.copy(originalLanguage = "")))
        assertFalse(Categories.matches("asian_horror", film("The Grudge", "Japan, United States", "en", 2004)))
        assertTrue(Categories.matches("giallo", film("Deep Red", "Italy", year = 1975)))
        assertFalse(Categories.matches("giallo", film("Two Evil Eyes", "Italy, United States", "en", 1990)))
    }

    @Test
    fun `the countries alone propose, only a language placed ahead of the Americans decides`() {
        assertTrue(Origin.proposedFor(Origin.Region.EUROPE, constantine))
        assertFalse(Origin.proposedFor(Origin.Region.EUROPE, film("Hereditary", "United States", "en")))
        assertEquals(Origin.Verdict.OUT, Origin.verdict(Origin.Region.EUROPE, film("Hereditary", "United States", "en")))
        assertEquals(Origin.Verdict.OUT, Origin.verdict(Origin.Region.EUROPE, film("Heel", "United Kingdom, Poland")))
        // Case and spaces in Cinemeta's text and in the language do not matter.
        assertEquals(Origin.Verdict.IN, Origin.verdict(Origin.Region.EUROPE, film("Lamb", " iceland ,United States", " IS ")))
    }

    @Test
    fun `only films that need an answer are looked up`() {
        assertTrue(Origin.needsLanguage(constantine.copy(originalLanguage = "")))
        assertFalse(Origin.needsLanguage(constantine))
        assertFalse(Origin.needsLanguage(martyrs))
        assertFalse(Origin.needsLanguage(film("Hereditary", "United States")))
        assertFalse(Origin.needsLanguage(film("Heel", "United Kingdom, United States")))
    }

    @Test
    fun `a row lists the films its countries say before those only their language says`() {
        val pool = listOf(lamb, martyrs, infested, raw, constantine)
        val sorted = Categories.sort(pool, thisYear = 2026)["european_horror"]!!
        assertEquals(listOf(martyrs, raw, lamb, infested).map { it.imdbId }, sorted)
    }

    @Test
    fun `the audit finds each kind of problem, and nothing in a clean row`() {
        val bad = film("A Documentary", "France").copy(genres = listOf("Documentary", "Horror"))
        val indian = film("Raaz", "India", year = 2002)
        val dup = martyrs
        val rows = mapOf(
            "european_horror" to listOf(martyrs, constantine, lamb.copy(originalLanguage = ""), film("Hereditary", "United States", "en"), bad, indian, dup),
            "zombies" to listOf(film("28 Days Later", "United Kingdom", "en")),
        )
        val problems = RowAudit.audit(rows).associate { it.name to it.problem }
        assertEquals(RowAudit.Problem.AMERICAN_LED, problems["Constantine"])
        assertEquals(RowAudit.Problem.ORIGIN_UNCLEAR, problems["Lamb"])
        assertEquals(RowAudit.Problem.OFF_REGION, problems["Hereditary"])
        assertEquals(RowAudit.Problem.OFF_THEME, problems["A Documentary"])
        assertEquals(RowAudit.Problem.HIDDEN_ORIGIN, problems["Raaz"])
        assertEquals(RowAudit.Problem.DUPLICATE, problems["Martyrs"])
        assertNull(problems["28 Days Later"])
        assertTrue(RowAudit.audit(mapOf("european_horror" to listOf(martyrs, raw, lamb, infested))).isEmpty())
    }

    @Test
    fun `clean takes the offenders out of the lists and counts them, and keeps films it knows nothing about`() {
        val metas = (americans + lamb + martyrs).associateBy { it.imdbId }
        val categories = mapOf(
            "european_horror" to (americans.map { it.imdbId } + listOf(lamb.imdbId, martyrs.imdbId, "tt9999999")),
            "zombies" to listOf(constantine.imdbId),
        )
        val (cleaned, removed) = RowAudit.clean(categories, metas)
        assertEquals(listOf(lamb.imdbId, martyrs.imdbId, "tt9999999"), cleaned["european_horror"])
        assertEquals(americans.size, removed)
        // Constantine is no zombie row's problem: the place check is for place rows only.
        assertEquals(listOf(constantine.imdbId), cleaned["zombies"])
    }

    @Test
    fun `a cache written before the check no longer shows American films in the European row`() {
        val metas = (americans + lamb + martyrs).associateBy { it.imdbId }
        val old = CinemetaCache(
            fetchedAtMillis = 1L,
            metas = metas,
            rows = listOf(OnlineRow("r", "ש", "R", listOf(martyrs))),
            categories = mapOf("european_horror" to (americans + lamb + martyrs).map { it.imdbId }),
            rowsVersion = 9,
        )
        assertTrue(old.isStale(1L))
        assertEquals(listOf(lamb.imdbId, martyrs.imdbId), old.withoutHidden().categories["european_horror"])
    }

    @Test
    fun `held counts what the check keeps out and what waits for a language`() {
        val pool = listOf(constantine, cell, lamb.copy(originalLanguage = ""), martyrs, film("Hereditary", "United States", "en"))
        assertEquals(RowAudit.Held(keptOut = 2, unclear = 1), RowAudit.held(pool))
    }

    @Test
    fun `TMDB ids come from Cinemeta's catalogs, and a film's language from its details`() {
        val metas = Cinemeta.parseCatalog(
            """{"metas":[{"id":"tt0360486","type":"movie","name":"Constantine","genres":["Horror"],"country":"Germany, United States","moviedb_id":561,"releaseInfo":"2005"},
               {"id":"tt0000002","type":"movie","name":"No Id","genres":["Horror"]},
               {"id":"tt0000003","type":"movie","name":"Bad Id","genres":["Horror"],"moviedb_id":"x"}]}""",
        )
        assertEquals(listOf(561L, 0L, 0L), metas.map { it.tmdbId })
        assertEquals("https://api.themoviedb.org/3/movie/561", Tmdb.movieUrl(561))
        assertEquals("en", Tmdb.parseOriginalLanguage("""{"id":561,"original_language":"en","title":"Constantine"}"""))
        assertEquals("is", Tmdb.parseOriginalLanguage("""{"original_language":" IS "}"""))
        assertNull(Tmdb.parseOriginalLanguage("""{"original_language":"<script>"}"""))
        assertNull(Tmdb.parseOriginalLanguage("""{"id":1}"""))
        assertNull(Tmdb.parseOriginalLanguage("{broken"))
    }
}

/** The hand-picked rows: what the quality audit (tools/catalog/audit_rows.py) found once stays fixed. */
class CuratedRowsQualityTest {
    private val catalog = io.github.diamondrubinny.scary.core.catalog.CatalogParser.parse(
        java.io.File("src/test/fixtures/catalog.json").readText(),
    )

    private fun row(id: String) = catalog.collections.single { it.id == id }.movieIds

    @Test
    fun `an American remake is not in Asian horror, and the original is`() {
        assertFalse("the_grudge_2004" in row("asian_horror"))
        assertTrue("ju_on" in row("asian_horror"))
        // The film itself is still in the library, in the row where it fits.
        assertTrue("the_grudge_2004" in row("haunted_houses"))
    }

    @Test
    fun `no row lists a film twice, and every film of a row exists`() {
        val ids = catalog.movies.map { it.id }.toSet()
        catalog.collections.forEach { collection ->
            assertEquals(collection.id, collection.movieIds.distinct(), collection.movieIds)
            assertTrue(collection.id, ids.containsAll(collection.movieIds))
        }
    }
}
