// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.CatalogParser
import io.github.diamondrubinny.scary.core.catalog.Search
import io.github.diamondrubinny.scary.core.catalog.SearchFilter
import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.MediaKind
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.model.Subgenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SearchTest {
    private val catalog = CatalogParser.parse(File("src/test/fixtures/catalog.json").readText())

    private fun ids(query: String, labels: (Movie) -> List<String> = { emptyList() }) =
        Search.query(catalog.movies, query, labels).map { it.id }

    @Test
    fun `normalize strips accents, niqqud and punctuation`() {
        assertEquals("amenabar", Search.normalize("Amenábar"))
        assertEquals("שלום", Search.normalize("שָׁלוֹם"))
        assertEquals("rec", Search.normalize("[REC]"))
        assertEquals("rosemary s baby", Search.normalize("Rosemary's Baby"))
    }

    @Test
    fun `a prepared index answers like a one-off search, for any number of queries`() {
        val index = Search.Index(catalog.movies)
        assertEquals(catalog.movies.size, index.size)
        for (query in listOf("shin", "rec", "carpenter", "1978", "ghost house", "", "   ")) {
            assertEquals(query, ids(query), index.query(query).map { it.id })
        }
    }

    @Test
    fun `title prefix ranks first`() {
        assertEquals("the_shining", ids("shin").first())
        assertEquals("rec", ids("rec").first())
    }

    @Test
    fun `all words must match`() {
        assertEquals(listOf("silence_of_the_lambs"), ids("silence lambs"))
        assertTrue(ids("shining zzzz").isEmpty())
    }

    @Test
    fun `finds by director, year and hebrew synopsis`() {
        assertEquals(setOf("se7en", "zodiac", "fight_club", "gone_girl", "panic_room"), ids("fincher").toSet())
        assertTrue("psycho" in ids("1960"))
        assertTrue("the_shining" in ids("מלון"))
    }

    @Test
    fun `uses localized labels from the ui`() {
        val labels: (Movie) -> List<String> = { m -> if (Subgenre.FOLK in m.subgenres) listOf("אימה פולקלורית") else emptyList() }
        // Every folk horror movie, found by the Hebrew label alone (no title or synopsis says "folklore").
        val folk = catalog.movies.filter { Subgenre.FOLK in it.subgenres }.map { it.id }.toSet()
        assertTrue(folk.containsAll(setOf("the_witch", "midsommar", "wicker_man")))
        assertEquals(folk, ids("פולקלור", labels).toSet())
    }

    @Test
    fun `blank query returns nothing`() {
        assertTrue(ids("   ").isEmpty())
        assertTrue(Search.query(listOf(sample()), "!!!").isEmpty())
    }

    private fun sample() = Movie(
        id = "x", imdbId = "tt0000001", title = "X", year = 2000, director = "d", runtimeMin = 90,
        genre = Genre.HORROR, subgenres = listOf(Subgenre.GHOST), fear = 1, hue = 0, synopsisHe = "ת", synopsisEn = "s",
    )

    @Test
    fun `a filter narrows a search before the limit is taken`() {
        val index = Search.Index(catalog.movies)
        val all = index.query("the", limit = 500)
        val eighties = SearchFilter(decade = 1980)
        val narrowed = index.query("the", limit = 500) { eighties.matches(it, watched = false) }
        assertTrue(narrowed.isNotEmpty() && narrowed.size < all.size)
        assertTrue(narrowed.all { it.year in 1980..1989 })
        // The same films, in the same order, as filtering every match by hand.
        assertEquals(all.filter { it.year in 1980..1989 }, narrowed)
        // A small limit still returns that many of the decade's films, not the few among the first matches overall.
        assertEquals(narrowed.take(3), index.query("the", limit = 3) { eighties.matches(it, watched = false) })
    }

    @Test
    fun `each part of a filter lets through only what fits`() {
        val shining = catalog.movies.first { it.title == "The Shining" }
        assertTrue(SearchFilter.NONE.matches(shining, watched = true))
        assertTrue(!SearchFilter.NONE.active && SearchFilter(unwatchedOnly = true).active)
        assertTrue(SearchFilter(kind = MediaKind.MOVIE, decade = 1980).matches(shining, watched = false))
        assertTrue(!SearchFilter(kind = MediaKind.SERIES).matches(shining, watched = false))
        assertTrue(!SearchFilter(decade = 1970).matches(shining, watched = false))
        assertTrue(!SearchFilter(decade = SearchFilter.OLDER).matches(shining, watched = false))
        assertTrue(SearchFilter(decade = SearchFilter.OLDER).matches(shining.copy(year = 1968), watched = false))
        assertTrue(!SearchFilter(decade = SearchFilter.OLDER).matches(shining.copy(year = 0), watched = false))
        assertTrue(!SearchFilter(unwatchedOnly = true).matches(shining, watched = true))
        assertTrue(SearchFilter(unwatchedOnly = true).matches(shining, watched = false))
        val band = SearchFilter.FearBand.entries.first { shining.fear!! in it.levels }
        assertTrue(SearchFilter(fear = band).matches(shining, watched = false))
        assertTrue(SearchFilter.FearBand.entries.filter { it != band }.none { SearchFilter(fear = it).matches(shining, watched = false) })
        // A film found on the web has no scare rating: it fits no band, and every other filter.
        val web = shining.copy(fear = null, curated = false)
        assertTrue(SearchFilter.FearBand.entries.none { SearchFilter(fear = it).matches(web, watched = false) })
        assertTrue(SearchFilter(decade = 1980).matches(web, watched = false))
    }

    @Test
    fun `OK on a filter goes through its values and back to any`() {
        assertEquals(listOf(MediaKind.MOVIE, MediaKind.SERIES, null), generateSequence(SearchFilter.NONE.nextKind()) { it.nextKind() }.take(3).map { it.kind }.toList())
        assertEquals(
            listOf(SearchFilter.FearBand.MILD, SearchFilter.FearBand.MEDIUM, SearchFilter.FearBand.INTENSE, null),
            generateSequence(SearchFilter.NONE.nextFear()) { it.nextFear() }.take(4).map { it.fear }.toList(),
        )
        assertEquals(listOf(2020, 2010, 2000, 1990, 1980, 1970, SearchFilter.OLDER), SearchFilter.decades(2026))
        assertEquals(
            listOf(2020, 2010, 2000, 1990, 1980, 1970, SearchFilter.OLDER, null, 2020),
            generateSequence(SearchFilter.NONE.nextDecade(2026)) { it.nextDecade(2026) }.take(9).map { it.decade }.toList(),
        )
        // A new decade begins: the filter offers it, and a decade chosen before still moves on.
        assertEquals(2030, SearchFilter.NONE.nextDecade(2031).decade)
        assertEquals(2010, SearchFilter(decade = 2020).nextDecade(2031).decade)
    }

    @Test
    fun `with no text, a filter alone picks the films`() {
        val index = Search.Index(catalog.movies)
        val intense = SearchFilter(fear = SearchFilter.FearBand.INTENSE, decade = 1970)
        val picked = index.browse { intense.matches(it, watched = false) }
        assertTrue(picked.isNotEmpty())
        assertTrue(picked.all { it.year in 1970..1979 && it.fear!! >= 4 })
        assertTrue(picked.size <= Search.BROWSE_LIMIT)
        assertEquals(picked.take(2), index.browse(limit = 2) { intense.matches(it, watched = false) })
    }
}
