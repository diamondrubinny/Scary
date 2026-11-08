// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.CatalogException
import io.github.diamondrubinny.scary.core.catalog.CatalogParser
import io.github.diamondrubinny.scary.core.catalog.HorrorIndex
import io.github.diamondrubinny.scary.core.model.MediaKind
import io.github.diamondrubinny.scary.core.model.Subgenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HorrorIndexTest {
    private fun entry(fields: String) = """{"version":1,"films":[{$fields}]}"""

    @Test
    fun `the bundled index is whole, checked and apart from the hand-picked library`() {
        val curated = CatalogParser.parse(File("../app/src/main/assets/catalog.json").readText()).movies.map { it.imdbId } +
            CatalogParser.parse(File("../app/src/main/assets/series.json").readText(), MediaKind.SERIES).movies.map { it.imdbId }
        val raw = HorrorIndex.parse(File("../app/src/main/assets/index.json").readText())
        assertEquals("No entry breaks a rule", 0, raw.dropped)
        assertTrue("Thousands of films: ${raw.movies.size}", raw.movies.size >= 3500)
        assertTrue("None is hand-picked already", raw.movies.none { it.imdbId in curated })
        assertEquals("No film twice", raw.movies.size, raw.movies.map { it.id }.toSet().size)
        assertTrue(raw.movies.all { it.id == "cm_" + it.imdbId && !it.curated && it.fear == null && it.title.isNotBlank() })
        // Every decade of horror, from the silent era on.
        val decades = raw.movies.groupingBy { it.year / 10 * 10 }.eachCount()
        for (decade in 1920..2020 step 10) assertTrue("Films from the ${decade}s: ${decades[decade]}", (decades[decade] ?: 0) >= 30)
        // The subgenres asked for have films.
        for (subgenre in listOf(Subgenre.SLASHER, Subgenre.GIALLO, Subgenre.ZOMBIE, Subgenre.VAMPIRE)) {
            assertTrue("$subgenre", raw.movies.count { subgenre in it.subgenres } >= 20)
        }
        assertTrue("Titles in Hebrew", raw.movies.count { it.titleHe.isNotBlank() } >= 500)
    }

    @Test
    fun `an entry is read into a movie with no fear level`() {
        val result = HorrorIndex.parse(
            entry(""""imdb":"tt0076162","t":{"en":"Suspiria","he":"סוספיריה","ru":"Суспирия"},"y":1977,"d":"Dario Argento","s":["giallo","witch","future_one"],"g":["Mystery"]"""),
        )
        val movie = result.movies.single()
        assertEquals("cm_tt0076162", movie.id)
        assertEquals("Suspiria", movie.title)
        assertEquals("סוספיריה", movie.titleHe)
        assertEquals(mapOf("he" to "סוספיריה", "ru" to "Суспирия"), movie.titles)
        assertEquals(listOf(Subgenre.GIALLO, Subgenre.WITCH), movie.subgenres)
        assertEquals(listOf("Horror", "Mystery"), movie.genres)
        assertEquals(null, movie.fear)
        assertEquals(0, result.dropped)
    }

    @Test
    fun `hostile or broken entries are dropped, the rest kept`() {
        val bad = listOf(
            """"imdb":"nm0000123","t":{"en":"Not a film id"},"y":1980""",
            """"imdb":"tt0000001","t":{"he":"No English title"},"y":1980""",
            """"imdb":"tt0000002","t":{"en":"Line\nbreak"},"y":1980""",
            """"imdb":"tt0000003","t":{"en":"‮Reversed"},"y":1980""",
            """"imdb":"tt0000004","t":{"en":"${"x".repeat(121)}"},"y":1980""",
            """"imdb":"tt0000005","t":{"en":"From the future"},"y":2999""",
            """"imdb":"tt0000006","t":{"en":"Unknown language","xx":"?"},"y":1980""",
            """"imdb":"tt0000007","t":{"en":"Odd genre"},"y":1980,"g":["<script>"]""",
            """"imdb":"tt0000008","t":{"en":"Blank"},"y":1980,"d":"  """",
        )
        val good = """{"imdb":"tt0000009","t":{"en":"Fine"},"y":1980}"""
        val text = """{"version":1,"films":[${bad.joinToString(",") { "{$it}" }},$good,$good]}"""
        val result = HorrorIndex.parse(text, thisYear = 2026)
        assertEquals(listOf("tt0000009"), result.movies.map { it.imdbId })
        assertEquals("Each bad entry and the duplicate", bad.size + 1, result.dropped)
    }

    @Test
    fun `hand-picked films are left out, and an unreadable file is refused`() {
        val text = entry(""""imdb":"tt0081505","t":{"en":"The Shining"},"y":1980""")
        assertTrue(HorrorIndex.parse(text, curatedImdbIds = setOf("tt0081505")).movies.isEmpty())
        assertThrows { HorrorIndex.parse("not json") }
        assertThrows { HorrorIndex.parse("""{"version":2,"films":[]}""") }
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (e: CatalogException) {
            return
        }
        throw AssertionError("expected CatalogException")
    }
}
