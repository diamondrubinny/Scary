// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.CatalogException
import io.github.diamondrubinny.scary.core.catalog.CatalogParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class CatalogParserTest {

    private fun movieJson(id: String, imdb: String = "tt0000001", fear: Int = 3) = """
        {"id":"$id","imdbId":"$imdb","title":"T","year":2000,"director":"D","runtimeMin":90,
         "genre":"horror","subgenres":["ghost"],"fear":$fear,"hue":10,
         "synopsisHe":"ת","synopsisEn":"s","synopses":{"pt":"p","ru":"р"}}
    """.trimIndent()

    private fun catalogJson(movies: String, collections: String = "[]") =
        """{"version":1,"movies":[$movies],"collections":$collections}"""

    private fun problemsOf(text: String): List<String> = try {
        CatalogParser.parse(text)
        emptyList()
    } catch (e: CatalogException) {
        e.problems
    }

    @Test
    fun `bundled app catalog is valid`() {
        val file = File("../app/src/main/assets/catalog.json")
        assertTrue("catalog not found at ${file.absolutePath}", file.exists())
        val catalog = CatalogParser.parse(file.readText())
        assertTrue(catalog.movies.size >= 40)
        assertTrue(catalog.collections.all { catalog.moviesOf(it).size == it.movieIds.size })
    }

    @Test
    fun `parses a minimal catalog`() {
        val catalog = CatalogParser.parse(catalogJson(movieJson("a")))
        assertEquals("a", catalog.movies.single().id)
        assertEquals(55, catalog.movies.single().saturation)
    }

    @Test
    fun `ignores unknown keys`() {
        val text = catalogJson(movieJson("a").replace("\"title\"", "\"extra\":1,\"title\""))
        assertEquals(1, CatalogParser.parse(text).movies.size)
    }

    @Test
    fun `rejects malformed json`() {
        assertTrue(problemsOf("{not json").single().startsWith("malformed"))
    }

    @Test
    fun `rejects bad imdb id, fear and duplicates`() {
        val problems = problemsOf(
            catalogJson(movieJson("a", imdb = "nm123") + "," + movieJson("a", imdb = "tt0000002", fear = 9)),
        )
        assertTrue(problems.any { "invalid imdbId" in it })
        assertTrue(problems.any { "duplicate id" in it })
        assertTrue(problems.any { "fear must be" in it })
    }

    @Test
    fun `rejects collections that reference unknown movies`() {
        val text = catalogJson(
            movieJson("a"),
            """[{"id":"c","titleHe":"ש","titleEn":"c","titles":{"pt":"c","ru":"к"},"movieIds":["a","ghost"]}]""",
        )
        assertTrue(problemsOf(text).any { "unknown movie 'ghost'" in it })
    }

    @Test
    fun `requires portuguese and russian translations for curated entries`() {
        val text = catalogJson(movieJson("a").replace(""","synopses":{"pt":"p","ru":"р"}""", ""))
        val problems = problemsOf(text)
        assertTrue(problems.any { "missing pt synopsis" in it })
        assertTrue(problems.any { "missing ru synopsis" in it })
    }

    @Test
    fun `rejects unsupported version`() {
        val text = catalogJson(movieJson("a")).replace("\"version\":1", "\"version\":2")
        try {
            CatalogParser.parse(text)
            fail("expected CatalogException")
        } catch (e: CatalogException) {
            assertTrue(e.problems.any { "unsupported catalog version" in it })
        }
    }
}
