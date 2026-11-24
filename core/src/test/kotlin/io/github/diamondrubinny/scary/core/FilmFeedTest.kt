// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.CatalogException
import io.github.diamondrubinny.scary.core.catalog.FilmFeed
import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.Subgenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FilmFeedTest {
    private fun id(n: Int) = "tt" + (1_000_000 + n)

    private fun film(n: Int, extra: String = "") = """{"imdb":"${id(n)}","t":{"en":"Film $n"},"y":1990,"p":10$extra}"""

    /** A feed of [count] plain films, with [first] in front and [rows] as its rows. */
    private fun feed(first: List<String> = emptyList(), rows: String = "", count: Int = 1_100, version: Int = 1, generated: String = "2026-11-24") =
        """{"version":$version,"generated":"$generated","films":[${(first + (1..count).map { film(it) }).joinToString(",")}],"rows":[$rows]}"""

    private fun row(id: String, films: List<String>) = """{"id":"$id","films":[${films.joinToString(",") { "\"$it\"" }}]}"""

    private fun refused(text: String) = runCatching { FilmFeed.parse(text, thisYear = 2026) }.exceptionOrNull() is CatalogException

    @Test
    fun `the feed gives films and rows, in its order`() {
        val special = """{"imdb":"tt0078748","t":{"en":"Alien","he":"הנוסע השמיני"},"y":1979,"p":90,"d":"Ridley Scott","s":["creature","sci_fi","not_known_yet"],"g":["Sci-Fi"],"r":8.5,"rel":"1979-05-25","future":1}"""
        val result = FilmFeed.parse(feed(listOf(special), row("creatures", listOf("tt0078748") + (1..20).map(::id)) + "," + row("top_rated", (30..60).map(::id))), thisYear = 2026)
        assertEquals(1_101, result.movies.size)
        assertEquals(0, result.droppedFilms)
        assertEquals("2026-11-24", result.generated)
        val alien = result.movies.first()
        assertEquals("cm_tt0078748", alien.id)
        assertEquals("Alien", alien.title)
        assertEquals("הנוסע השמיני", alien.titleHe)
        assertEquals("Ridley Scott", alien.director)
        assertEquals(8.5, alien.imdbRating!!, 0.0)
        // A subgenre this version does not know is skipped; the film stays. No fear level is ever made up.
        assertEquals(listOf(Subgenre.CREATURE, Subgenre.SCI_FI), alien.subgenres)
        assertEquals(listOf("Horror", "Sci-Fi"), alien.genres)
        assertNull(alien.fear)
        assertTrue(!alien.curated)
        assertEquals(listOf("creatures", "top_rated"), result.rows.map { it.id })
        assertEquals("cm_tt0078748", result.rows[0].movieIds.first())
        assertEquals(21, result.rows[0].movieIds.size)
    }

    @Test
    fun `a thriller and a series are what the feed says they are`() {
        val result = FilmFeed.parse(
            feed(
                listOf(
                    """{"imdb":"tt0114369","t":{"en":"Se7en"},"y":1995,"p":80,"k":"thriller","g":["Crime","Drama"],"r":8.6}""",
                    """{"imdb":"tt9813792","t":{"en":"From"},"y":2022,"p":20,"k":"series","on":true}""",
                    """{"imdb":"tt0460681","t":{"en":"Supernatural"},"y":2005,"p":60,"k":"series","e":2020}""",
                    """{"imdb":"tt3000001","t":{"en":"A kind from the future"},"y":2020,"p":1,"k":"podcast"}""",
                    """{"imdb":"tt3000002","t":{"en":"A film with an end year"},"y":2020,"p":1,"e":2022}""",
                ),
            ),
            thisYear = 2026,
        )
        val (seven, from, supernatural) = result.movies.take(3)
        assertEquals(Genre.THRILLER, seven.genre)
        assertEquals(listOf("Thriller", "Crime", "Drama"), seven.genres)
        assertTrue(!seven.isSeries)
        assertTrue(from.isSeries && from.ongoing && from.endYear == null && from.genre == Genre.HORROR)
        assertTrue(supernatural.isSeries && !supernatural.ongoing && supernatural.endYear == 2020)
        assertEquals(2, result.droppedFilms)
    }

    @Test
    fun `a film that breaks a rule is dropped, and the rest of the feed stands`() {
        val bad = listOf(
            """{"imdb":"nm0000001","t":{"en":"Not a film id"},"y":1990,"p":1}""",
            """{"imdb":"tt2000001","t":{"he":"בלי כותרת באנגלית"},"y":1990,"p":1}""",
            """{"imdb":"tt2000002","t":{"en":"From the future"},"y":2031,"p":1}""",
            """{"imdb":"tt2000003","t":{"en":"Bad‮title"},"y":1990,"p":1}""",
            """{"imdb":"tt2000004","t":{"en":"Bad rating"},"y":1990,"p":1,"r":11.0}""",
            """{"imdb":"tt2000005","t":{"en":"Bad genre"},"y":1990,"p":1,"g":["<script>"]}""",
            """{"imdb":"tt2000006","t":{"en":"Unknown language","xx":"?"},"y":1990,"p":1}""",
            film(1),
        )
        val result = FilmFeed.parse(feed(bad), thisYear = 2026)
        // The eighth is film 1 again: the first copy stays, the second is dropped.
        assertEquals(1_100, result.movies.size)
        assertEquals(8, result.droppedFilms)
        assertTrue(result.movies.none { it.imdbId.startsWith("tt20000") })
    }

    @Test
    fun `a row keeps only films the feed has, and is dropped when too few are left`() {
        val rows = listOf(
            row("slashers", (1..10).map(::id) + listOf("tt9999999", "../etc", id(3))),
            row("too_small", (1..5).map(::id)),
            row("Bad Id", (1..20).map(::id)),
            row("slashers", (20..40).map(::id)),
            row("long", (1..200).map(::id)),
        ).joinToString(",")
        val result = FilmFeed.parse(feed(rows = rows), thisYear = 2026)
        assertEquals(listOf("slashers", "long"), result.rows.map { it.id })
        assertEquals((1..10).map { "cm_" + id(it) }, result.rows[0].movieIds)
        assertEquals(FilmFeed.MAX_ROW_FILMS, result.rows[1].movieIds.size)
        assertEquals(3, result.droppedRows)
    }

    @Test
    fun `a file that is not a usable feed is refused whole`() {
        assertTrue(refused("not json"))
        assertTrue(refused("""{"version":1,"generated":"2026-11-24"}"""))
        assertTrue("another version", refused(feed(version = 2)))
        assertTrue("no date", refused(feed(generated = "yesterday")))
        assertTrue("too few films", refused(feed(count = 200)))
        assertTrue("too many rows", refused(feed(rows = (1..41).joinToString(",") { row("row_" + "a".repeat(it % 20 + 1) + ('a' + it % 26), (1..10).map(::id)) })))
        assertTrue("too large", refused(feed() + " ".repeat(FilmFeed.MAX_CHARS)))
    }

    @Test
    fun `the bundled feed, when there is one, is whole and its rows are full`() {
        val file = File("../app/src/main/assets/feed.json")
        if (!file.exists()) return
        val result = FilmFeed.parse(file.readText())
        assertEquals("No film breaks a rule", 0, result.droppedFilms)
        assertEquals("No row breaks a rule", 0, result.droppedRows)
        assertTrue("Thousands of films: ${result.movies.size}", result.movies.size >= 3_500)
        assertTrue("New films are there", result.rows.any { it.id == "new" })
        assertTrue("Thrillers and series have their rows", result.rows.map { it.id }.containsAll(listOf("thrillers", "series")))
        assertTrue("A horror row holds no thriller and no series", result.rows.filter { it.id !in setOf("thrillers", "series") }.all { row ->
            row.movieIds.all { id -> result.movies.first { it.id == id }.let { !it.isSeries && it.genre == Genre.HORROR } }
        })
        assertTrue("Every row has a dozen films", result.rows.all { it.movieIds.size >= 12 })
        // The best-known horror films, which the hand-picked library used to hold, are in the feed.
        for (imdb in listOf("tt0054215", "tt0081505", "tt0078748", "tt0077651")) assertTrue(imdb, result.movies.any { it.imdbId == imdb })
    }
}
