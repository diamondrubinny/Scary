// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.CatalogParser
import io.github.diamondrubinny.scary.core.catalog.Decades
import io.github.diamondrubinny.scary.core.catalog.Mood
import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.model.MovieTag
import io.github.diamondrubinny.scary.core.model.Subgenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The new moods and decades, and the bundled library behind every row and mood. */
class LibraryTest {
    private val catalog = CatalogParser.parse(File("../app/src/main/assets/catalog.json").readText())

    private fun movie(id: String, year: Int, runtime: Int, slowBurn: Boolean = false, curated: Boolean = true) = Movie(
        id = id, imdbId = "tt$id", title = id, year = year, runtimeMin = runtime, genre = Genre.HORROR,
        subgenres = listOf(Subgenre.GHOST), fear = 3, curated = curated,
        tags = if (slowBurn) listOf(MovieTag.SLOW_BURN) else emptyList(),
    )

    private val small = listOf(
        movie("quick", 1981, 85),
        movie("long_slow", 1984, 130, slowBurn = true),
        movie("quicker", 2015, 78, slowBurn = true),
        movie("nineties", 1996, 95),
        movie("online_short", 1983, 80, curated = false),
    )

    private fun ids(movies: List<Movie>) = movies.map { it.id }

    @Test
    fun shortAndSharpIsNinetyMinutesOrLessShortestFirst() {
        assertEquals(listOf("quicker", "quick"), ids(Mood.SHORT.pick(small)))
    }

    @Test
    fun slowBurnFollowsTheEditorialTag() {
        assertEquals(listOf("long_slow", "quicker"), ids(Mood.SLOW_BURN.pick(small)))
    }

    @Test
    fun decadesAloneOrWithAMood() {
        assertEquals(1980, Decades.of(small[0]))
        // Only decades with enough curated movies become chips.
        assertEquals(listOf(1980), Decades.available(small, minMovies = 2))
        assertEquals(listOf("quick", "long_slow"), ids(Decades.pick(small, null, 1980)))
        assertEquals(listOf("quick"), ids(Decades.pick(small, Mood.SHORT, 1980)))
        assertEquals(listOf("quicker", "quick"), ids(Decades.pick(small, Mood.SHORT, null)))
        assertTrue(Decades.pick(small, null, null).isEmpty())
    }

    @Test
    fun theLibraryIsBigAndEveryRowAndMoodHasEnoughToShow() {
        assertTrue("library has ${catalog.movies.size} movies", catalog.movies.size >= 150)
        catalog.collections.forEach { collection ->
            assertTrue("${collection.id} is too short", collection.movieIds.size >= 5)
        }
        Mood.entries.forEach { mood ->
            assertTrue("$mood has only ${mood.pick(catalog.movies).size}", mood.pick(catalog.movies).size >= 8)
        }
        // Family night shows only fear 1-2: there must be plenty of those.
        assertTrue(catalog.movies.count { (it.fear ?: 5) <= 2 } >= 20)
        Subgenre.entries.forEach { sub ->
            assertTrue("no movie is $sub", catalog.movies.any { sub in it.subgenres })
        }
        assertTrue(Decades.available(catalog.movies).containsAll(listOf(1960, 1970, 1980, 1990, 2000, 2010, 2020)))
    }

    @Test
    fun newRowsAreThere() {
        val ids = catalog.collections.map { it.id }
        listOf("found_footage", "folk_horror", "asian_horror", "zombies", "vampires", "body_horror", "haunted_houses", "creatures")
            .forEach { assertTrue("missing row $it", it in ids) }
    }
}
