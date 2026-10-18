package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.CatalogParser
import io.github.diamondrubinny.scary.core.catalog.NightPick
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.cinemeta.OnlineRow
import io.github.diamondrubinny.scary.core.links.Links
import org.junit.Assert.assertEquals
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
        assertEquals("https://www.youtube.com/watch?v=jAE7dNurHR4", Links.youTubeVideo("jAE7dNurHR4"))
    }
}

class CinemetaCacheTest {
    @Test
    fun `cache round-trips and reports staleness`() {
        val meta = CinemetaMeta("tt0081505", "The Shining", 1980, background = "https://x/b")
        val cache = io.github.diamondrubinny.scary.core.cinemeta.CinemetaCache(
            1_000L, mapOf(meta.imdbId to meta), listOf(OnlineRow("r", "ש", "R", listOf(meta))),
        )
        val back = io.github.diamondrubinny.scary.core.cinemeta.CinemetaCache.decode(cache.encode())!!
        assertEquals(cache, back)
        assertTrue(!back.isStale(1_000L + 60_000))
        assertTrue(back.isStale(1_000L + 25L * 60 * 60 * 1000))
        assertTrue(back.isStale(0L)) // clock went backwards
        assertNull(io.github.diamondrubinny.scary.core.cinemeta.CinemetaCache.decode("{broken"))
    }
}
