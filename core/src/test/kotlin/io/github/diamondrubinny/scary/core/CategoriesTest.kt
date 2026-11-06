// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.CatalogParser
import io.github.diamondrubinny.scary.core.catalog.Categories
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The library's categories filled from Cinemeta: right films in, the near misses found while tuning kept out. */
class CategoriesTest {
    private fun film(
        name: String,
        description: String,
        vararg genres: String,
        year: Int = 2005,
        rating: Double? = 6.5,
        country: String = "United States",
        type: String = CinemetaMeta.TYPE_MOVIE,
    ) = CinemetaMeta(
        imdbId = "tt" + (1_000_000 + name.hashCode().mod(8_999_999)).toString(),
        name = name,
        year = year,
        description = description,
        genres = genres.toList(),
        imdbRating = rating,
        country = country,
        type = type,
    )

    @Test
    fun `the clear cases land in their category`() {
        assertTrue(Categories.matches("vampires", film("Near Dark", "A young man falls in with a family of nomadic vampires.", "Horror")))
        assertTrue(Categories.matches("zombies", film("Juan of the Dead", "Havana is overrun by zombies.", "Comedy", "Horror")))
        assertTrue(Categories.matches("haunted_houses", film("Ghost House", "A couple moves into a haunted house in Korea.", "Horror")))
        assertTrue(Categories.matches("slashers", film("The Burning", "A summer camp caretaker returns to take revenge.", "Horror")))
        assertTrue(Categories.matches("asian_horror", film("Kwaidan", "Four ghost stories.", "Fantasy", "Horror", country = "Japan")))
        assertTrue(Categories.matches("classics", film("Black Sunday", "A vengeful witch returns.", "Horror", year = 1960, rating = 7.4)))
        assertTrue(Categories.matches("crime_thriller", film("Kiss the Girls", "A detective hunts a serial killer.", "Crime", "Mystery", "Thriller")))
    }

    @Test
    fun `the near misses found while tuning stay out`() {
        // A werewolf film is not a slasher, even with a killer in its plot.
        assertFalse(Categories.matches("slashers", film("The Howling", "A killer werewolf stalks a reporter.", "Horror")))
        // "The dead brought back" by a scientist is not a zombie film.
        assertFalse(Categories.matches("zombies", film("Frankenstein", "A scientist reanimates a corpse.", "Horror", "Sci-Fi")))
        // A co-production with China, made in the United Kingdom, is not Asian horror.
        assertFalse(Categories.matches("asian_horror", film("Last Night in Soho", "A dream of the 1960s.", "Horror", country = "United Kingdom, China")))
        // An action film with a secret is not a psychological thriller.
        assertFalse(Categories.matches("psychological_thriller", film("GoldenEye", "An agent uncovers a plot and its deception.", "Action", "Thriller")))
        // Ghostface is a killer, not a ghost; and "undead" vampires are not zombies.
        val scream = film("Scream", "A masked killer known as Ghostface stalks teenagers.", "Horror", "Mystery")
        assertFalse(Categories.matches("supernatural", scream))
        assertTrue(Categories.matches("slashers", scream))
        assertFalse(Categories.matches("zombies", film("Fright Night", "A teenager learns his neighbour is an undead vampire.", "Horror")))
        // A paranormal investigation is not found footage.
        assertFalse(Categories.matches("found_footage", film("The Conjuring", "Paranormal investigators help a family.", "Horror")))
        // Off the shelves: a documentary or a film for children, whatever its words.
        assertFalse(Categories.matches("vampires", film("Vampire Lore", "All about vampires.", "Documentary", "Horror")))
        assertFalse(Categories.matches("creatures", film("Monster Pals", "Friendly monsters.", "Animation", "Family", "Horror")))
    }

    @Test
    fun `series go only to series rows, films only to film rows`() {
        val show = film("Midnight Mass", "A priest brings miracles, and a demon, to an island.", "Drama", "Horror", type = CinemetaMeta.TYPE_SERIES)
        assertTrue(Categories.matches("series_ghosts", show))
        assertFalse(Categories.matches("supernatural", show))
        assertFalse(Categories.matches("series_ghosts", film("The Rite", "A priest faces a demon.", "Horror")))
    }

    @Test
    fun `sorting keeps the pool's order, skips known films and caps each category`() {
        val pool = (1..5).map { film("Vampire night $it", "Vampires attack.", "Horror") }
        val sorted = Categories.sort(pool, known = mapOf("vampires" to setOf(pool[0].imdbId)), max = 3)
        assertEquals(pool.drop(1).take(3).map { it.imdbId }, sorted["vampires"])
        assertFalse("no empty category", sorted.values.any { it.isEmpty() })
    }

    @Test
    fun `every rule is for a category the library really has`() {
        val films = CatalogParser.parse(File("../app/src/main/assets/catalog.json").readText()).collections.map { it.id }
        val series = CatalogParser.parse(File("../app/src/main/assets/series.json").readText(), MediaKind.SERIES).collections.map { it.id }
        assertTrue(Categories.ids.toString(), (films + series).containsAll(Categories.ids))
    }
}
