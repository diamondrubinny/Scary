package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.CatalogParser
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.tmdb.Tmdb
import io.github.diamondrubinny.scary.core.tmdb.TmdbAuth
import io.github.diamondrubinny.scary.core.tmdb.TmdbCache
import io.github.diamondrubinny.scary.core.tmdb.TmdbRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class TmdbTest {
    private val catalog = CatalogParser.parse(File("../app/src/main/assets/catalog.json").readText())

    private val findJson = """
        {"movie_results":[{"id":694,"title":"הניצוץ","original_title":"The Shining",
          "overview":"ג'ק טורנס מקבל עבודה כשומר חורף במלון מבודד.","poster_path":"/abcDEF123.jpg",
          "backdrop_path":"/back_drop-9.jpg","release_date":"1980-05-23","vote_average":8.2,"genre_ids":[27,53]}],
         "tv_results":[]}
    """.trimIndent()

    @Test
    fun `accepts v3 keys and v4 tokens, rejects anything else`() {
        assertEquals(TmdbAuth.ApiKey("0123456789abcdef0123456789abcdef"), Tmdb.parseAuth("  0123456789abcdef0123456789abcdef \n"))
        val token = "eyJhbGciOiJIUzI1NiJ9." + "a".repeat(40) + ".sig_nature-1"
        assertEquals(TmdbAuth.Bearer(token), Tmdb.parseAuth(token))
        assertNull(Tmdb.parseAuth(""))
        assertNull(Tmdb.parseAuth("0123456789abcdef0123456789abcdeg"))
        assertNull(Tmdb.parseAuth("key with spaces 0123456789abcdef0123"))
        assertNull(Tmdb.parseAuth("eyJ.not a token"))
        assertEquals("…cdef", Tmdb.mask("0123456789abcdef0123456789abcdef"))
    }

    @Test
    fun `v3 key goes in the query string, v4 token does not`() {
        val url = Tmdb.findUrl("tt0081505", Language.HE)
        assertTrue(Tmdb.authorize(url, TmdbAuth.ApiKey("k".repeat(32))).endsWith("&api_key=" + "k".repeat(32)))
        assertEquals(url, Tmdb.authorize(url, TmdbAuth.Bearer("eyJ.x.y")))
        assertTrue(Tmdb.authorize(Tmdb.configurationUrl(), TmdbAuth.ApiKey("a".repeat(32))).contains("?api_key="))
    }

    @Test
    fun `parses find with hebrew text and safe image urls`() {
        val info = Tmdb.parseFind(findJson, Language.HE)!!
        assertEquals(694L, info.tmdbId)
        assertEquals("he", info.language)
        assertEquals("הניצוץ", info.title)
        assertTrue(info.overview.startsWith("ג'ק"))
        assertEquals("https://image.tmdb.org/t/p/w500/abcDEF123.jpg", info.poster)
        assertEquals("https://image.tmdb.org/t/p/w1280/back_drop-9.jpg", info.backdrop)
    }

    @Test
    fun `english-only results do not pretend to be hebrew, bad paths are dropped`() {
        val info = Tmdb.parseFind(
            """{"movie_results":[{"id":5,"title":"Alien","original_title":"Alien","overview":"In space","poster_path":"../../etc/passwd","backdrop_path":"/x.svg"}]}""",
            Language.RU,
        )!!
        assertEquals("", info.title)
        assertEquals("", info.overview)
        assertNull(info.poster)
        assertNull(info.backdrop)
        assertNull(Tmdb.parseFind("""{"movie_results":[]}""", Language.HE))
        assertNull(Tmdb.parseFind("<html>", Language.HE))
    }

    @Test
    fun `enrich adds hebrew title and keeps curated hebrew synopsis`() {
        val shining = catalog.movie("the_shining")!!
        val enriched = Tmdb.enrich(shining, Tmdb.parseFind(findJson, Language.HE)!!)
        assertEquals("הניצוץ", enriched.displayTitle(Language.HE))
        assertEquals("The Shining", enriched.displayTitle(Language.EN))
        assertEquals(shining.synopsisHe, enriched.synopsisHe)
        assertEquals("https://image.tmdb.org/t/p/w1280/back_drop-9.jpg", enriched.backgroundUrl)
    }

    @Test
    fun `discover results become online movies with hebrew text`() {
        val results = Tmdb.parseDiscover("""{"page":1,"results":[{"id":1,"title":"הנזירה","original_title":"The Nun","overview":"נזירה צעירה...","release_date":"2018-09-05","genre_ids":[27]},{"id":0,"title":"bad"}]}""")
        assertEquals(1, results.size)
        val movie = Cinemeta.toMovie(Tmdb.toMeta(results.single(), "tt5814060", Language.HE))!!
        assertEquals("The Nun", movie.title)
        assertEquals("הנזירה", movie.displayTitle(Language.HE))
        assertEquals("The Nun", movie.displayTitle(Language.RU))
        assertTrue(movie.synopsis(Language.HE).startsWith("נזירה"))
        assertFalse(movie.curated)
    }

    @Test
    fun `discover urls use fixed parameters`() {
        val url = Tmdb.discoverUrl(TmdbRow.NEW_HORROR, LocalDate.of(2026, 10, 18), Language.HE)
        assertTrue(url.startsWith("https://api.themoviedb.org/3/discover/movie?with_genres=27"))
        assertTrue("primary_release_date.gte=2025-10-18" in url)
        assertTrue("language=he-IL" in url)
        assertTrue("with_genres=53%2C9648" in Tmdb.discoverUrl(TmdbRow.PSYCH_THRILLER, LocalDate.of(2026, 1, 1), Language.RU))
        assertEquals("tt0081505", Tmdb.parseImdbId("""{"id":694,"imdb_id":"tt0081505"}"""))
        assertNull(Tmdb.parseImdbId("""{"id":694,"imdb_id":null}"""))
    }

    @Test
    fun `russian and portuguese text is used only when really translated`() {
        val ru = Tmdb.parseFind(
            """{"movie_results":[{"id":694,"title":"Сияние","original_title":"The Shining","overview":"Джек Торранс..."}]}""",
            Language.RU,
        )!!
        val shining = catalog.movie("the_shining")!!
        val enriched = Tmdb.enrich(shining, ru)
        assertEquals("Сияние", enriched.displayTitle(Language.RU))
        // The curated Russian synopsis wins over TMDB's.
        assertEquals(shining.synopses["ru"], enriched.synopsis(Language.RU))
        val pt = Tmdb.parseFind(
            """{"movie_results":[{"id":694,"title":"The Shining","original_title":"The Shining","overview":"Jack Torrance..."}]}""",
            Language.PT,
        )!!
        assertEquals("", pt.title)
        assertEquals("Jack Torrance...", pt.overview)
    }

    @Test
    fun `languages cycle and map from android codes`() {
        assertEquals(listOf(Language.HE, Language.EN, Language.PT, Language.RU), Language.entries.toList())
        assertEquals(Language.HE, Language.RU.next())
        assertEquals(Language.PT, Language.fromCode("pt"))
        assertEquals(Language.RU, Language.fromCode("ru"))
        assertEquals(Language.HE, Language.fromCode("iw"))
        assertEquals(Language.EN, Language.fromCode("fr"))
    }

    @Test
    fun `curated catalog reads in all four languages`() {
        val exorcist = catalog.movie("the_exorcist")!!
        Language.entries.forEach { assertTrue(exorcist.synopsis(it).isNotBlank()) }
        assertTrue(exorcist.synopsis(Language.RU).contains("священник"))
        assertEquals("Classics you can't skip", catalog.collections.first().title(Language.EN))
        assertEquals("Clássicos imperdíveis", catalog.collections.first().title(Language.PT))
    }

    @Test
    fun `cache round trip never contains the key`() {
        val cache = TmdbCache(1L, "he", mapOf("tt0081505" to Tmdb.parseFind(findJson, Language.HE)!!), emptyList())
        val text = cache.encode()
        assertEquals(cache, TmdbCache.decode(text))
        assertFalse("api_key" in text)
    }
}
