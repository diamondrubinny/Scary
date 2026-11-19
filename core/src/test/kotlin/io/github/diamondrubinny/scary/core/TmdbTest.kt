// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

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
    fun `arabic counts only when the text is in arabic script`() {
        val arabic = Tmdb.parseFind(
            """{"movie_results":[{"id":7,"title":"البريق","original_title":"The Shining","overview":"كاتب يقبل وظيفة حارس فندق معزول في الشتاء."}]}""",
            Language.AR,
        )!!
        assertEquals("البريق", arabic.title)
        assertEquals("ar", arabic.language)
        assertTrue(arabic.overview.isNotBlank())
        val untranslated = Tmdb.parseFind(
            """{"movie_results":[{"id":7,"title":"The Shining","original_title":"The Shining","overview":"A writer takes a job."}]}""",
            Language.AR,
        )!!
        assertEquals("", untranslated.title)
        assertEquals("", untranslated.overview)
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
    fun `english find fills only a missing english synopsis`() {
        val en = Tmdb.parseFind(
            """{"movie_results":[{"id":5,"title":"Alien","original_title":"Alien","overview":"In space no one can hear you scream."}]}""",
            Language.EN,
        )!!
        assertEquals("Alien", en.title)
        assertEquals("In space no one can hear you scream.", en.overview)
        val curated = catalog.movie("alien")!!
        assertEquals(curated.synopsisEn, Tmdb.enrich(curated, en).synopsisEn)
        val online = curated.copy(synopsisEn = "", curated = false)
        assertEquals("In space no one can hear you scream.", Tmdb.enrich(online, en).synopsisEn)
    }

    @Test
    fun `untranslated discover overview is kept as english`() {
        val result = Tmdb.parseDiscover("""{"results":[{"id":9,"title":"Smile 2","original_title":"Smile 2","overview":"A pop star starts to see things.","release_date":"2024-10-16","genre_ids":[27]}]}""").single()
        val meta = Tmdb.toMeta(result, "tt29268110", Language.RU)
        assertEquals("", meta.localDescription)
        assertEquals("A pop star starts to see things.", meta.description)
        assertTrue(Cinemeta.toMovie(meta)!!.synopsis(Language.RU).isNotBlank())
    }

    @Test
    fun `languages cycle and map from android codes`() {
        assertEquals(listOf(Language.HE, Language.EN, Language.PT, Language.RU, Language.AR), Language.entries.toList())
        assertEquals(Language.AR, Language.RU.next())
        assertEquals(Language.HE, Language.AR.next())
        assertEquals(Language.AR, Language.fromCode("ar"))
        assertTrue(Language.AR.rtl)
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

    @Test
    fun `the new rows ask TMDB only with fixed filters it documents`() {
        val today = LocalDate.of(2026, 11, 18)
        fun url(row: TmdbRow, page: Int = 1) = Tmdb.discoverUrl(row, today, Language.EN, page)
        // Out in the last two months.
        url(TmdbRow.JUST_RELEASED).let {
            assertTrue("with_genres=27" in it)
            assertTrue("primary_release_date.gte=2026-09-19" in it)
            assertTrue("primary_release_date.lte=2026-11-18" in it)
        }
        url(TmdbRow.POPULAR_HORROR).let { assertTrue("sort_by=popularity.desc" in it && "vote_count.gte=200" in it) }
        url(TmdbRow.NEW_THRILLERS).let { assertTrue("with_genres=53" in it && "primary_release_date.gte=2025-11-18" in it) }
        // Well rated, seen by few.
        url(TmdbRow.HIDDEN_GEMS).let {
            assertTrue("sort_by=vote_average.desc" in it && "vote_average.gte=6.8" in it)
            assertTrue("vote_count.gte=150" in it && "vote_count.lte=1500" in it)
        }
        // A decade is its ten years, the best known films first.
        url(TmdbRow.EIGHTIES, page = 3).let {
            assertTrue("primary_release_date.gte=1980-01-01" in it && "primary_release_date.lte=1989-12-31" in it)
            assertTrue("sort_by=vote_count.desc" in it && it.endsWith("page=3"))
        }
        assertTrue("primary_release_date.lte=2019-12-31" in url(TmdbRow.TWENTY_TENS))
        // Every row: no adult films, a title in each language, an id of its own.
        TmdbRow.entries.forEach { row ->
            assertTrue(row.id, "include_adult=false" in url(row))
            assertTrue(row.id, listOf(row.titleHe, row.titleEn, row.titlePt, row.titleRu, row.titleAr).none { it.isBlank() })
        }
        assertEquals(TmdbRow.entries.size, TmdbRow.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `tmdb's rows are read again after a few hours, the translations once a day`() {
        val read = 2_000_000_000L
        val hour = 60L * 60 * 1000
        val cache = io.github.diamondrubinny.scary.core.tmdb.TmdbCache(read, "he", emptyMap(), emptyList(), io.github.diamondrubinny.scary.core.tmdb.TmdbCache.ROWS_VERSION, rowsFetchedAtMillis = read)
        assertFalse(cache.needsRows(read + 7 * hour))
        assertTrue(cache.needsRows(read + 9 * hour))
        assertFalse(cache.isStale(read + 9 * hour))
        assertFalse(cache.copy(rowsFetchedAtMillis = read + 9 * hour).needsRows(read + 10 * hour))
        assertTrue(cache.copy(rowsFetchedAtMillis = read + 20 * hour).needsRows(read + 25 * hour))
        // A cache from before the rows had an age of their own, and one of an older shape.
        assertTrue(cache.copy(rowsFetchedAtMillis = 0).needsRows(read + 9 * hour))
        assertTrue(cache.copy(rowsVersion = 2).needsRows(read))
    }

    @Test
    fun `a film's series is read from TMDB in the order the films came out`() {
        // A film's details: only the series' id is read from "belongs_to_collection".
        val film = """{"id":948,"title":"Halloween","belongs_to_collection":{"id":91361,"name":"Halloween Collection","poster_path":"/p.jpg","backdrop_path":"/b.jpg"}}"""
        assertEquals(91361L, Tmdb.parseCollectionId(film))
        assertEquals(null, Tmdb.parseCollectionId("""{"id":694,"title":"The Shining","belongs_to_collection":null}"""))
        assertEquals(null, Tmdb.parseCollectionId("""{"id":694,"belongs_to_collection":{"id":"x"}}"""))
        assertEquals(null, Tmdb.parseCollectionId("not json"))
        assertEquals("https://api.themoviedb.org/3/collection/91361?language=he-IL", Tmdb.collectionUrl(91361, Language.HE))

        val collection = """{"id":91361,"name":"Halloween Collection","overview":"...","parts":[
            {"adult":false,"id":11281,"title":"Halloween II","original_title":"Halloween II","release_date":"1981-10-30","genre_ids":[27,53],"vote_average":6.4,"poster_path":"/2.jpg","original_language":"en"},
            {"adult":false,"id":948,"title":"Halloween","original_title":"Halloween","release_date":"1978-10-24","genre_ids":[27,53],"vote_average":7.6,"poster_path":"/1.jpg","original_language":"en"},
            {"adult":false,"id":999001,"title":"Halloween: The Next One","original_title":"Halloween: The Next One","release_date":"","genre_ids":[27]},
            {"adult":true,"id":999002,"title":"Not this one","release_date":"1990-01-01","genre_ids":[27]},
            {"adult":false,"id":10676,"name":"Halloween III: Season of the Witch","original_name":"Halloween III","release_date":"1982-10-22","genre_ids":[27]},
            {"id":948,"title":"Halloween again","release_date":"1978-10-24"},
            "junk", {"id":0,"title":"No id"}, {"id":5,"release_date":"2000-01-01"}
        ]}"""
        val franchise = Tmdb.parseCollection(collection)!!
        assertEquals(91361L, franchise.id)
        assertEquals("Halloween Collection", franchise.name)
        // By release date; the film with no date yet comes last; the adult title, the repeat and the junk are gone.
        assertEquals(listOf(948L, 11281L, 10676L, 999001L), franchise.parts.map { it.tmdbId })
        assertEquals("Halloween III: Season of the Witch", franchise.parts[2].title)
        assertEquals(1978, franchise.parts.first().year)
        // One film is not a series; neither is an answer of another shape.
        assertEquals(null, Tmdb.parseCollection("""{"id":1,"name":"Solo","parts":[{"id":2,"title":"Only","release_date":"2001-01-01"}]}"""))
        assertEquals(null, Tmdb.parseCollection("""{"success":false,"status_code":34}"""))
        assertEquals(null, Tmdb.parseCollection("[]"))
        // A very long series has an end.
        val long = (1..100).joinToString(",") { """{"id":$it,"title":"Part $it","release_date":"19${(it % 90 + 10)}-01-01"}""" }
        assertEquals(Tmdb.MAX_FRANCHISE, Tmdb.parseCollection("""{"id":7,"name":"Long","parts":[$long]}""")!!.parts.size)
    }
}
