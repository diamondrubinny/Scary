// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.Categories
import io.github.diamondrubinny.scary.core.catalog.Similar
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.MediaKind
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.model.Subgenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "More like this": alike films first, a different row on each page, nothing off theme. */
class SimilarTest {
    private fun web(id: String, vararg genres: String, year: Int = 2020, director: String = "") = Movie(
        id = "cm_$id", imdbId = id, title = id, year = year, director = director,
        genre = if ("Horror" in genres) Genre.HORROR else Genre.THRILLER, curated = false, genres = genres.toList(),
    )

    @Test
    fun `films from the web with nothing more to go on no longer all get the same row`() {
        // 60 horror films from the web with no subgenres: before, every page showed the same first ids.
        val films = (100..159).map { web("tt0000$it", "Horror") }
        val rows = films.take(10).map { film -> Similar.find(films, film, limit = 10).map { it.id } }
        assertEquals(10, rows.toSet().size)
        // One page keeps its row: the order is stable for a film.
        assertEquals(rows[0], Similar.find(films.shuffled(), films[0], limit = 10).map { it.id })
    }

    @Test
    fun `a drama or a comedy from a general catalog is never like a horror film`() {
        val horror = web("tt1", "Horror", "Mystery")
        val drama = web("tt2", "Drama")
        val comedy = web("tt3", "Comedy", "Drama")
        val alike = web("tt4", "Horror")
        val result = Similar.find(listOf(drama, comedy, alike), horror)
        assertEquals(listOf(alike.id), result.map { it.id })
    }

    @Test
    fun `shared subgenres, a director and genres in common come first, watched films last`() {
        val seed = web("tt1", "Horror", "Comedy", director = "Sam Raimi").copy(subgenres = listOf(Subgenre.POSSESSION))
        val possession = web("tt2", "Horror").copy(subgenres = listOf(Subgenre.POSSESSION))
        val sameDirector = web("tt3", "Horror", director = "Sam Raimi")
        val horrorComedy = web("tt4", "Horror", "Comedy")
        val plain = web("tt5", "Horror")
        val seen = web("tt6", "Horror", "Comedy").copy(subgenres = listOf(Subgenre.POSSESSION))
        val result = Similar.find(listOf(plain, horrorComedy, sameDirector, possession, seen), seed) { it.id == seen.id }.map { it.id }
        assertEquals(possession.id, result.first())
        assertTrue(result.indexOf(sameDirector.id) < result.indexOf(plain.id))
        assertTrue(result.indexOf(horrorComedy.id) < result.indexOf(plain.id))
        assertEquals(seen.id, result.last())
    }

    @Test
    fun `the viewer's taste moves films that are equally alike`() {
        val seed = web("tt1", "Horror")
        val a = web("tt2", "Horror")
        val b = web("tt3", "Horror")
        val liked = Similar.find(listOf(a, b), seed, bonus = { if (it.id == b.id) 1.0 else 0.0 }).map { it.id }
        assertEquals(listOf(b.id, a.id), liked)
        assertNotEquals(liked, Similar.find(listOf(a, b), seed, bonus = { if (it.id == a.id) 1.0 else 0.0 }).map { it.id })
    }

    @Test
    fun `a film from the web gets its subgenres from its genres and plot`() {
        fun meta(plot: String, vararg genres: String) =
            CinemetaMeta(imdbId = "tt9", name = "X", year = 2024, genres = genres.toList(), description = plot)
        assertEquals(
            setOf(Subgenre.VAMPIRE),
            Categories.subgenresOf(meta("A vampire hunts the night streets of an old town.", "Horror")).toSet(),
        )
        val ghosts = Categories.subgenresOf(meta("A family moves into a haunted house where a vengeful spirit waits.", "Horror", "Mystery"))
        assertTrue(Subgenre.HAUNTED_HOUSE in ghosts && Subgenre.GHOST in ghosts && Subgenre.MYSTERY in ghosts)
        // Ghostface is Scream's killer, not a ghost.
        assertFalse(Subgenre.GHOST in Categories.subgenresOf(meta("Ghostface returns to stalk a new group of teens.", "Horror")))
        // Off theme: nothing.
        assertTrue(Categories.subgenresOf(meta("A vampire comedy of manners.", "Comedy", "Romance")).isEmpty())
        // toMovie keeps them, with the genres.
        val movie = Cinemeta.toMovie(meta("Zombies overrun the city.", "Horror", "Action"))!!
        assertEquals(listOf(Subgenre.ZOMBIE), movie.subgenres)
        assertEquals(listOf("Horror", "Action"), movie.genres)
        assertEquals(MediaKind.MOVIE, movie.kind)
    }
}
