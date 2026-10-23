// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.CatalogParser
import io.github.diamondrubinny.scary.core.catalog.NightPick
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.cinemeta.OnlineRow
import io.github.diamondrubinny.scary.core.links.Links
import io.github.diamondrubinny.scary.core.model.Catalog
import org.junit.Assert.assertEquals
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class CinemetaTest {
    private val shiningJson = javaClass.getResource("/cinemeta_shining.json")!!.readText()
    private val catalog = CatalogParser.parse(File("../app/src/main/assets/catalog.json").readText())

    @Test
    fun `parses a real meta response`() {
        val meta = Cinemeta.parseMeta(shiningJson)!!
        assertEquals("tt0081505", meta.imdbId)
        assertEquals(1980, meta.year)
        assertEquals(8.4, meta.imdbRating!!, 0.001)
        assertEquals(144, meta.runtimeMin)
        assertEquals("jAE7dNurHR4", meta.trailerYoutubeId)
        assertEquals("https://images.metahub.space/poster/medium/tt0081505/img", meta.poster)
        assertEquals(listOf("Stanley Kubrick"), meta.director)
    }

    @Test
    fun `rejects unsafe or malformed input`() {
        assertNull(Cinemeta.parseMeta("not json"))
        assertNull(Cinemeta.parseMeta("""{"meta":{"imdb_id":"nm1","name":"x"}}"""))
        val meta = Cinemeta.parseMeta(
            """{"meta":{"imdb_id":"tt0000001","name":"X","poster":"http://evil/p.png","background":"javascript:alert(1)",
               "imdbRating":"99","director":"A, B","trailerStreams":[{"ytId":"../../x"}]}}""",
        )!!
        assertNull(meta.poster)
        assertNull(meta.background)
        assertNull(meta.imdbRating)
        assertNull(meta.trailerYoutubeId)
        assertEquals(listOf("A", "B"), meta.director)
        assertTrue(Cinemeta.parseCatalog("""{"metas":[1,"x",{"id":"tt1234567","name":"Ok","year":"2020"}]}""").size == 1)
    }

    @Test
    fun `enrich keeps editorial fields and adds artwork`() {
        val shining = catalog.movie("the_shining")!!
        val enriched = Cinemeta.enrich(shining, Cinemeta.parseMeta(shiningJson)!!)
        assertEquals(shining.synopsisHe, enriched.synopsisHe)
        assertEquals(shining.fear, enriched.fear)
        assertEquals(shining.runtimeMin, enriched.runtimeMin)
        assertTrue(enriched.backgroundUrl!!.startsWith("https://"))
        assertEquals(3, enriched.cast.size)
    }

    @Test
    fun `merge adds online rows first, reuses curated movies and skips tiny rows`() {
        val shiningMeta = Cinemeta.parseMeta(shiningJson)!!
        val fresh = (1..5).map { CinemetaMeta("tt99000$it".padEnd(9, '0'), "New $it", 2025, genres = listOf("Horror")) }
        val rows = listOf(
            OnlineRow("popular", "פופולרי", "Popular", listOf(shiningMeta) + fresh),
            OnlineRow("tiny", "קטן", "Tiny", fresh.take(2)),
        )
        val merged = Cinemeta.merge(catalog, mapOf(shiningMeta.imdbId to shiningMeta), rows)
        val first = merged.collections.first()
        assertEquals("online_popular", first.id)
        assertEquals("the_shining", first.movieIds.first())
        assertEquals(catalog.movies.size + 5, merged.movies.size)
        assertTrue(merged.collections.none { it.id == "online_tiny" })
        val online = merged.movie(first.movieIds[1])!!
        assertNull(online.fear)
        assertEquals(5, online.fearForLock)
        // Online movies have no editorial fear, so they never become tonight's pick.
        repeat(30) { assertTrue(NightPick.pick(merged.movies, LocalDate.of(2026, 1, 1).plusDays(it.toLong()))!!.curated) }
    }

    @Test
    fun `urls are validated`() {
        assertEquals("https://v3-cinemeta.strem.io/meta/movie/tt0081505.json", Cinemeta.metaUrl("tt0081505"))
        assertEquals("https://v3-cinemeta.strem.io/catalog/movie/top/genre=Horror.json", Cinemeta.catalogUrl("top", "Horror"))
        assertEquals("https://v3-cinemeta.strem.io/catalog/movie/top/genre=Horror&skip=50.json", Cinemeta.catalogUrl("top", "Horror", 50))
        assertEquals("https://v3-cinemeta.strem.io/catalog/movie/year/genre=2026.json", Cinemeta.catalogUrl("year", "2026"))
        assertThrows(IllegalArgumentException::class.java) { Cinemeta.catalogUrl("top", "Horror&skip=1") }
        assertThrows(IllegalArgumentException::class.java) { Cinemeta.catalogUrl("top", "Horror", -50) }
        assertThrows(IllegalArgumentException::class.java) { Cinemeta.catalogUrl("top/../x", "Horror") }
        assertEquals("https://www.youtube.com/watch?v=jAE7dNurHR4", Links.youTubeVideo("jAE7dNurHR4"))
    }

    @Test
    fun `search url encodes the query and rejects bad lengths`() {
        assertEquals(
            "https://v3-cinemeta.strem.io/catalog/movie/top/search=the%20thing.json",
            Cinemeta.searchUrl("  the   thing "),
        )
        assertEquals(
            "https://v3-cinemeta.strem.io/catalog/movie/top/search=%D7%90%D7%99%D7%9E%D7%94%2F..%3F.json",
            Cinemeta.searchUrl("אימה/..?"),
        )
        assertNull(Cinemeta.searchUrl("ab"))
        assertNull(Cinemeta.searchUrl("x".repeat(61)))
    }

    @Test
    fun `search results keep the theme, skip known movies and cap the count`() {
        val known = catalog.movies.first()
        val text = """{"metas":[
            {"id":"${known.imdbId}","name":"Known","releaseInfo":"1980","genres":["Horror"]},
            {"id":"tt9000001","name":"Ghost House","releaseInfo":"2021","genres":["Horror","Drama"]},
            {"id":"tt9000002","name":"Fast Cars","releaseInfo":"2022","genres":["Action"]},
            {"id":"tt9000003","name":"No Genres","releaseInfo":"2019"},
            {"id":"tt9000004","name":"No Year","genres":["Thriller"]}
        ]}"""
        val results = Cinemeta.searchResults(text, catalog)
        assertEquals(listOf("Ghost House", "No Genres"), results.map { it.title })
        assertTrue(results.none { it.curated })
        assertEquals(1, Cinemeta.searchResults(text, catalog, limit = 1).size)
    }

    @Test
    fun `a movie survives the trip through fromMovie and back`() {
        val meta = Cinemeta.parseMeta(shiningJson)!!
        val movie = Cinemeta.toMovie(meta.copy(localName = "הניצוץ", localLanguage = "he"))!!
        val again = Cinemeta.toMovie(Cinemeta.fromMovie(movie, "he"))!!
        assertEquals(movie.copy(hue = again.hue), again)
    }
}

class CinemetaCacheTest {
    @Test
    fun `cache round-trips and reports staleness`() {
        val meta = CinemetaMeta("tt0081505", "The Shining", 1980, background = "https://x/b")
        val cache = io.github.diamondrubinny.scary.core.cinemeta.CinemetaCache(
            1_000L, mapOf(meta.imdbId to meta), listOf(OnlineRow("r", "ש", "R", listOf(meta))),
            io.github.diamondrubinny.scary.core.cinemeta.CinemetaCache.ROWS_VERSION,
        )
        val back = io.github.diamondrubinny.scary.core.cinemeta.CinemetaCache.decode(cache.encode())!!
        assertEquals(cache, back)
        assertTrue(!back.isStale(1_000L + 60_000))
        assertTrue(back.isStale(1_000L + 25L * 60 * 60 * 1000))
        assertTrue(back.isStale(0L)) // clock went backwards
        assertNull(io.github.diamondrubinny.scary.core.cinemeta.CinemetaCache.decode("{broken"))
    }

    @Test
    fun `themed keeps horror, thriller and mystery but not documentaries`() {
        fun meta(id: String, vararg genres: String) = CinemetaMeta(imdbId = id, name = id, genres = genres.toList())
        val kept = Cinemeta.themed(
            listOf(
                meta("tt0000001", "Horror"),
                meta("tt0000002", "Comedy"),
                meta("tt0000003", "Documentary", "Thriller"),
                meta("tt0000004", "Drama", "mystery"),
                meta("tt0000005"),
            ),
        )
        assertEquals(listOf("tt0000001", "tt0000004"), kept.map { it.imdbId })
    }

    @Test
    fun `a cache from an older set of rows is stale at once`() {
        val now = 1_000_000L
        assertTrue(CinemetaCache(now, emptyMap(), emptyList()).isStale(now))
        assertFalse(CinemetaCache(now, emptyMap(), emptyList(), CinemetaCache.ROWS_VERSION).isStale(now))
        // Decoding an old file without the field gives the old version.
        val old = CinemetaCache.decode("""{"fetchedAtMillis":$now,"metas":{},"rows":[]}""")!!
        assertTrue(old.isStale(now))
    }

    @Test
    fun `extend adds a page to the end of an online row without duplicates`() {
        fun meta(n: Int) = CinemetaMeta(imdbId = "tt%07d".format(n), name = "Movie $n", year = 2020, genres = listOf("Horror"))
        val base = io.github.diamondrubinny.scary.core.cinemeta.Cinemeta.merge(
            Catalog(1, emptyList(), emptyList()), emptyMap(),
            listOf(OnlineRow("deep", "ע", "Deep", (1..5).map(::meta))),
        )
        val id = io.github.diamondrubinny.scary.core.cinemeta.Cinemeta.onlineCollectionId("deep")
        val more = io.github.diamondrubinny.scary.core.cinemeta.Cinemeta.extend(base, id, (4..8).map(::meta))
        val row = more.collections.single { it.id == id }
        assertEquals((1..8).map { "tt%07d".format(it) }, row.movieIds.map { more.movie(it)!!.imdbId })
        assertEquals(8, more.movies.size)
        // Unknown rows and empty pages change nothing; the cap holds.
        assertEquals(base, io.github.diamondrubinny.scary.core.cinemeta.Cinemeta.extend(base, "online_none", (9..10).map(::meta)))
        assertEquals(base, io.github.diamondrubinny.scary.core.cinemeta.Cinemeta.extend(base, id, emptyList()))
        val capped = io.github.diamondrubinny.scary.core.cinemeta.Cinemeta.extend(base, id, (6..20).map(::meta), maxRow = 7)
        assertEquals(7, capped.collections.single { it.id == id }.movieIds.size)
        assertEquals(7, capped.movies.size)
    }
}
