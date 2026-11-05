// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.CatalogParser
import io.github.diamondrubinny.scary.core.catalog.Personalize
import io.github.diamondrubinny.scary.core.catalog.Recommender
import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.model.Subgenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Sorting by what the viewer watches: watched films last, rows by taste, "Because you watched". */
class PersonalizeTest {
    private val catalog = CatalogParser.parse(File("../app/src/main/assets/catalog.json").readText())

    private fun movie(id: String, vararg subs: Subgenre, director: String = "D", year: Int = 2000, genre: Genre = Genre.HORROR) =
        Movie(id = id, imdbId = "tt0000001", title = id, year = year, director = director, genre = genre, subgenres = subs.toList(), fear = 3)

    @Test
    fun `watched films go to the end of a row, each group keeping its order`() {
        val row = listOf("a", "b", "c", "d", "e")
        assertEquals(listOf("a", "c", "e", "b", "d"), Personalize.watchedLast(row) { it in setOf("b", "d") })
        assertEquals(row, Personalize.watchedLast(row) { false })
        assertEquals(row, Personalize.watchedLast(row) { true })
    }

    @Test
    fun `slashers watched put the slasher row first, and nothing moves without enough history`() {
        val slashers = (1..6).map { movie("slasher$it", Subgenre.SLASHER, year = 1980 + it) }
        val ghosts = (1..6).map { movie("ghost$it", Subgenre.GHOST, Subgenre.HAUNTED_HOUSE) }
        val byId = (slashers + ghosts).associateBy { it.id }
        val rows = listOf("ghosts" to ghosts, "slashers" to slashers)

        val oneFilm = Personalize.taste(byId::get, watchedIds = listOf("slasher1"), lovedIds = emptyList(), dislikedIds = emptyList())
        assertFalse(oneFilm.known)
        assertEquals(rows, Personalize.byTaste(rows, oneFilm) { Personalize.rowScore(it.second, oneFilm, emptySet()) })

        val watched = listOf("slasher1", "slasher2", "slasher3")
        val taste = Personalize.taste(byId::get, watched, emptyList(), emptyList())
        assertTrue(taste.known)
        assertTrue(taste.score(slashers[5]) > taste.score(ghosts[0]))
        val sorted = Personalize.byTaste(rows, taste) { Personalize.rowScore(it.second, taste, watched.toSet()) }
        assertEquals(listOf("slashers", "ghosts"), sorted.map { it.first })
    }

    @Test
    fun `loved films outweigh watched ones and not for me counts against`() {
        val slasher = movie("slasher", Subgenre.SLASHER)
        val ghost = movie("ghost", Subgenre.GHOST)
        val films = listOf(slasher, ghost) + (1..4).map { movie("s$it", Subgenre.SLASHER) } + (1..4).map { movie("g$it", Subgenre.GHOST) }
        val byId = films.associateBy { it.id }
        // Two slashers watched, one ghost story loved: the ghost wins.
        val taste = Personalize.taste(byId::get, listOf("s1", "s2"), listOf("g1"), emptyList())
        assertTrue(taste.score(ghost) > taste.score(slasher))
        // A ghost story marked "not for me" pulls ghosts down.
        val against = Personalize.taste(byId::get, listOf("g1", "s1", "s2"), emptyList(), listOf("g2", "g3"))
        assertTrue(against.score(slasher) > against.score(ghost))
    }

    @Test
    fun `rows score the same keep their order`() {
        val films = (1..4).map { movie("f$it", Subgenre.CREATURE) }
        val byId = films.associateBy { it.id }
        val taste = Personalize.taste(byId::get, films.map { it.id }, emptyList(), emptyList())
        val rows = listOf("one" to films.take(2), "two" to films.drop(2))
        assertEquals(rows, Personalize.byTaste(rows, taste) { Personalize.rowScore(it.second, taste, emptySet()) })
    }

    @Test
    fun `because you watched recommends from the latest watched film, never a watched one`() {
        val seed = movie("seed", Subgenre.GHOST, Subgenre.HAUNTED_HOUSE, director = "Wan")
        val twin = movie("twin", Subgenre.GHOST, Subgenre.HAUNTED_HOUSE, director = "Wan")
        val also = movie("also", Subgenre.HAUNTED_HOUSE, director = "Wan")
        val near = movie("near", Subgenre.GHOST, Subgenre.HAUNTED_HOUSE)
        val seen = movie("seen", Subgenre.GHOST, Subgenre.HAUNTED_HOUSE, director = "Wan")
        val films = listOf(seed, twin, also, near, seen)
        val rec = Recommender.becauseYouWatched(films, watchedIds = listOf("seed", "seen"))!!
        assertEquals("seed", rec.because.id)
        assertTrue(rec.movies.none { it.id == "seen" || it.id == "seed" })
        // A seed already behind "Because you loved" is skipped.
        assertNull(Recommender.becauseYouWatched(films, watchedIds = listOf("seed"), skipSeeds = setOf("seed")))
    }

    @Test
    fun `on the real library a few watched classics reorder the rows`() {
        val watched = listOf("the_shining", "hereditary", "the_conjuring", "the_exorcist")
        val taste = Personalize.taste(catalog::movie, watched, emptyList(), emptyList())
        assertTrue(taste.known)
        val rows = catalog.collections.map { it to catalog.moviesOf(it) }
        val sorted = Personalize.byTaste(rows, taste) { Personalize.rowScore(it.second, taste, watched.toSet()) }
        assertEquals(rows.toSet(), sorted.toSet())
        assertTrue("some row moved", sorted != rows)
        val rec = Recommender.becauseYouWatched(catalog.movies, watched)!!
        assertTrue(rec.movies.size >= 3 && rec.movies.none { it.id in watched })
    }
}
