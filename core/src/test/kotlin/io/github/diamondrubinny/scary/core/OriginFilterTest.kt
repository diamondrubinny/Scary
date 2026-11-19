// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.Categories
import io.github.diamondrubinny.scary.core.catalog.OriginFilter
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaCache
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.cinemeta.OnlineRow
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.tmdb.Tmdb
import io.github.diamondrubinny.scary.core.tmdb.TmdbCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Arab and Indian films stay off every shelf; a film led by another country, and every other language, stays. */
class OriginFilterTest {
    private fun film(n: Int, country: String = "", language: String = "", vararg genres: String = arrayOf("Horror")) = CinemetaMeta(
        imdbId = "tt%07d".format(n),
        name = "Film $n",
        poster = "https://images.metahub.space/poster/small/tt0000001/img",
        description = "A film.",
        imdbRating = 6.5,
        genres = genres.toList(),
        country = country,
        originalLanguage = language,
        year = 2024,
    )

    @Test
    fun `the first country decides, for India and the Arab League alike`() {
        listOf("India", "India, United States", "Egypt", "Lebanon, France", "Saudi Arabia", "United Arab Emirates", "Morocco", "Palestine", "Palestinian Territories", "Tunisia", "Iraq", "Syria", "Jordan", "Kuwait", "Qatar")
            .forEach { assertTrue(it, OriginFilter.hides(film(1, country = it))) }
        listOf("United States, India", "France, Egypt", "Israel", "Iran", "Pakistan", "Turkey", "Japan", "Spain", "", "United Kingdom")
            .forEach { assertFalse(it, OriginFilter.hides(film(1, country = it))) }
    }

    @Test
    fun `whole country names, not words inside them`() {
        // "Arab" in a name does not matter, only the country: and nothing here is a substring match.
        assertFalse(OriginFilter.hidesCountry("Indiana"))
        assertFalse(OriginFilter.hidesCountry("Arabia Felix"))
        assertTrue(OriginFilter.hidesCountry("  india  "))
        assertTrue(OriginFilter.hidesCountry("EGYPT, France"))
    }

    @Test
    fun `TMDB's films go by their original language`() {
        listOf("ar", "hi", "ta", "te", "ml", "kn", "bn", "mr", "pa", "gu", "AR", " hi ").forEach { assertTrue(it, OriginFilter.hidesLanguage(it)) }
        listOf("en", "he", "es", "fr", "ja", "ko", "it", "ur", "fa", "tr", "ru", "pt", "").forEach { assertFalse(it, OriginFilter.hidesLanguage(it)) }
        assertTrue(OriginFilter.hides(film(2, language = "hi")))
        assertFalse(OriginFilter.hides(film(3, language = "en")))
    }

    @Test
    fun `lists and rows lose the hidden films, and a row with none left goes`() {
        val metas = listOf(film(1, "India"), film(2, "Japan"), film(3, language = "ta"), film(4, "Egypt"), film(5, "United States"))
        assertEquals(listOf("tt0000002", "tt0000005"), OriginFilter.visible(metas).map { it.imdbId })
        val row = OnlineRow("r", "ש", "R", metas)
        assertEquals(2, OriginFilter.visible(row)!!.metas.size)
        assertNull(OriginFilter.visible(OnlineRow("only", "ש", "Only", listOf(film(1, "India"), film(4, "Egypt")))))
    }

    @Test
    fun `a cache from before the choice shows no hidden film`() {
        val kept = film(1, "Japan")
        val hidden = film(2, "India")
        val cache = CinemetaCache(
            fetchedAtMillis = 1L,
            metas = mapOf(kept.imdbId to kept, hidden.imdbId to hidden),
            rows = listOf(OnlineRow("a", "ש", "A", listOf(kept, hidden)), OnlineRow("b", "ש", "B", listOf(hidden))),
            categories = mapOf("zombies" to listOf(kept.imdbId, hidden.imdbId), "vampires" to listOf(hidden.imdbId), "slashers" to listOf("tt9999999")),
        )
        val clean = cache.withoutHidden()
        assertEquals(listOf("a"), clean.rows.map { it.id })
        assertEquals(listOf(kept.imdbId), clean.rows.single().metas.map { it.imdbId })
        assertEquals(listOf(kept.imdbId), clean.categories["zombies"])
        assertFalse("vampires" in clean.categories)
        // A film whose details are not in the cache stays: nothing says it is hidden.
        assertEquals(listOf("tt9999999"), clean.categories["slashers"])
    }

    @Test
    fun `India no longer counts as Asian horror, since it is hidden`() {
        assertFalse(Categories.matches("asian_horror", film(1, country = "India")))
        assertTrue(Categories.matches("asian_horror", film(2, country = "Japan")))
    }

    @Test
    fun `TMDB results carry their original language, bad codes become none, and old caches are read as stale`() {
        val text = """{"results":[
            {"id":1,"title":"A","original_title":"A","overview":"","genre_ids":[27],"original_language":"hi","release_date":"2024-05-01"},
            {"id":2,"title":"B","original_title":"B","overview":"","genre_ids":[27],"original_language":"EN"},
            {"id":3,"title":"C","original_title":"C","overview":"","genre_ids":[27],"original_language":"<script>"},
            {"id":4,"title":"D","original_title":"D","overview":"","genre_ids":[27]}]}"""
        val results = Tmdb.parseDiscover(text)
        assertEquals(listOf("hi", "en", "", ""), results.map { it.originalLanguage })
        assertEquals("hi", Tmdb.toMeta(results[0], "tt0000001", Language.EN).originalLanguage)
        assertTrue(OriginFilter.hides(Tmdb.toMeta(results[0], "tt0000001", Language.EN)))
        assertFalse(OriginFilter.hides(Tmdb.toMeta(results[1], "tt0000002", Language.EN)))
        // A cache written before rows carried their language (version 1) is stale at once; a current one is not.
        val old = TmdbCache(1_000L, "he", emptyMap(), emptyList())
        assertTrue(old.isStale(1_000L))
        assertFalse(old.copy(rowsVersion = TmdbCache.ROWS_VERSION).isStale(1_000L))
        assertEquals(1, TmdbCache.decode("""{"fetchedAtMillis":1,"info":{},"rows":[]}""")!!.rowsVersion)
    }

    @Test
    fun `with the rule turned off nothing is hidden, and it comes back when turned on`() {
        try {
            OriginFilter.enabled = false
            assertFalse(OriginFilter.hidesCountry("India"))
            assertFalse(OriginFilter.hidesLanguage("ar"))
        } finally {
            OriginFilter.enabled = true
        }
        assertTrue(OriginFilter.hidesCountry("India"))
        assertTrue(OriginFilter.hidesLanguage("ar"))
    }
}
