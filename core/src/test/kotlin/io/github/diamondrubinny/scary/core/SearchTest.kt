// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.CatalogParser
import io.github.diamondrubinny.scary.core.catalog.Search
import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.model.Subgenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SearchTest {
    private val catalog = CatalogParser.parse(File("../app/src/main/assets/catalog.json").readText())

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
        assertEquals(setOf("se7en", "zodiac", "fight_club", "gone_girl"), ids("fincher").toSet())
        assertTrue("psycho" in ids("1960"))
        assertTrue("the_shining" in ids("מלון"))
    }

    @Test
    fun `uses localized labels from the ui`() {
        val labels: (Movie) -> List<String> = { m -> if (Subgenre.FOLK in m.subgenres) listOf("אימה פולקלורית") else emptyList() }
        assertEquals(setOf("the_witch", "midsommar"), ids("פולקלור", labels).toSet())
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
}
