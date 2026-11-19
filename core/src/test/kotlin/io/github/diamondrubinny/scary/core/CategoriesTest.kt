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
        poster = "https://images.metahub.space/poster/small/tt0000001/img",
        // Never empty: a film with no synopsis is left out by the quality check before any category is asked.
        description = description.ifEmpty { "." },
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

/** The twelve rows that had no rule: each now goes on, by writer, country, years, genres, rating or words. */
class NewCategoriesTest {
    private fun film(
        name: String,
        description: String = "",
        vararg genres: String,
        year: Int = 2005,
        rating: Double? = 6.5,
        country: String = "United States",
        writer: List<String> = emptyList(),
    ) = CinemetaMeta(
        imdbId = "tt" + (1_000_000 + name.hashCode().mod(8_999_999)).toString(),
        name = name,
        year = year,
        poster = "https://images.metahub.space/poster/small/tt0000001/img",
        // Never empty: a film with no synopsis is left out by the quality check before any category is asked.
        description = description.ifEmpty { "." },
        genres = genres.toList(),
        imdbRating = rating,
        country = country,
        writer = writer,
    )

    @Test
    fun `new nightmares are this year's and last year's horror only`() {
        assertTrue(Categories.matches("new_nightmares", film("Fresh Fear", "", "Horror", year = 2026), thisYear = 2026))
        assertTrue(Categories.matches("new_nightmares", film("Last Year", "", "Horror", year = 2025), thisYear = 2026))
        assertFalse(Categories.matches("new_nightmares", film("Old One", "", "Horror", year = 2023), thisYear = 2026))
        assertFalse(Categories.matches("new_nightmares", film("A Drama", "", "Drama", year = 2026), thisYear = 2026))
        assertTrue("new_nightmares" in Categories.replacesCurated)
    }

    @Test
    fun `writer, country and years say the rows that words could not`() {
        assertTrue(Categories.matches("stephen_king", film("Carrie", "", "Horror", writer = listOf("Stephen King", "Lawrence D. Cohen"))))
        assertFalse(Categories.matches("stephen_king", film("Not King", "", "Horror", writer = listOf("Stephen Kingsley"))))
        assertTrue(Categories.matches("giallo", film("Deep Red", "", "Horror", "Mystery", year = 1975, country = "Italy")))
        assertFalse(Categories.matches("giallo", film("New Italian", "", "Horror", year = 2015, country = "Italy")))
        assertTrue(Categories.matches("classic_monsters", film("Frankenstein", "", "Horror", year = 1931)))
        assertFalse(Categories.matches("classic_monsters", film("Modern", "", "Horror", year = 1999)))
        assertTrue(Categories.matches("horror_comedy", film("Shaun of the Dead", "", "Comedy", "Horror")))
        assertFalse(Categories.matches("horror_comedy", film("Just Funny", "", "Comedy")))
        assertTrue(Categories.matches("spanish_horror", film("REC", "", "Horror", country = "Spain")))
        assertTrue(Categories.matches("spanish_horror", film("Tigers Are Not Afraid", "", "Horror", country = "Mexico")))
        assertTrue(Categories.matches("european_horror", film("Martyrs", "", "Horror", country = "France, Canada")))
        assertFalse(Categories.matches("european_horror", film("British", "", "Horror", country = "United Kingdom")))
        assertTrue(Categories.matches("max_fear", film("Top Rated", "", "Horror", rating = 8.1)))
        assertFalse(Categories.matches("max_fear", film("Average", "", "Horror", rating = 6.0)))
    }

    @Test
    fun `witches, cosmic horror and nature attacks go by words, and keep their near misses out`() {
        assertTrue(Categories.matches("witches", film("The Witch", "A family in 1630s New England is torn apart by black magic.", "Horror")))
        assertTrue(Categories.matches("witches", film("Suspiria", "A dance academy run by a coven.", "Horror")))
        assertTrue(Categories.matches("cosmic_horror", film("The Void", "A cult serving ancient beings from beyond.", "Horror", "Sci-Fi")))
        assertFalse(Categories.matches("witches", film("Alien: Covenant", "The crew of a colony ship, the Covenant, finds a paradise.", "Horror", "Sci-Fi")))
        assertFalse(Categories.matches("cosmic_horror", film("Dark Signal", "An alien force from beyond the stars.", "Horror", "Sci-Fi")))
        assertTrue(Categories.matches("cosmic_horror", film("Color Out of Space", "A Lovecraft tale of a meteorite.", "Horror")))
        assertTrue(Categories.matches("animal_attacks", film("Crawl", "A daughter faces alligators in a hurricane.", "Horror", "Action")))
        assertTrue(Categories.matches("animal_attacks", film("The Shallows", "A surfer is stalked by a great white shark.", "Horror", "Drama")))
        assertFalse(Categories.matches("animal_attacks", film("Werewolves", "A pack of wolves turns out to be werewolves.", "Horror")))
        assertFalse(Categories.matches("witches", film("Hocus Pocus 3", "Three witches return.", "Comedy", "Family")))
    }

    @Test
    fun `every new category is one the library has, and series stay out of them`() {
        val films = CatalogParser.parse(File("../app/src/main/assets/catalog.json").readText()).collections.map { it.id }.toSet()
        val added = listOf(
            "new_nightmares", "stephen_king", "giallo", "classic_monsters", "horror_comedy", "spanish_horror",
            "european_horror", "max_fear", "witches", "cosmic_horror", "animal_attacks",
        )
        assertTrue(Categories.ids.containsAll(added))
        assertTrue(films.containsAll(added))
        val series = film("A Series", "", "Horror", year = 2026).copy(type = CinemetaMeta.TYPE_SERIES)
        assertFalse(Categories.matches("new_nightmares", series, thisYear = 2026))
    }
}

class TmdbCategoryTest {
    private val today = java.time.LocalDate.of(2026, 11, 16)

    @Test
    fun `every TMDB category is a library row, and its discover address holds only fixed parameters`() {
        val films = CatalogParser.parse(File("../app/src/main/assets/catalog.json").readText()).collections.map { it.id }.toSet()
        io.github.diamondrubinny.scary.core.tmdb.TmdbCategory.entries.forEach { category ->
            assertTrue(category.collectionId, category.collectionId in films)
            val url = io.github.diamondrubinny.scary.core.tmdb.Tmdb.discoverUrl(category, today, io.github.diamondrubinny.scary.core.model.Language.HE, page = 3)
            assertTrue(url, url.startsWith("https://api.themoviedb.org/3/discover/movie?"))
            assertTrue(url, "page=3" in url && "include_adult=false" in url && "api_key" !in url)
        }
        val newest = io.github.diamondrubinny.scary.core.tmdb.Tmdb.discoverUrl(io.github.diamondrubinny.scary.core.tmdb.TmdbCategory.NEW_NIGHTMARES, today, io.github.diamondrubinny.scary.core.model.Language.EN)
        assertTrue(newest, "primary_release_date.gte=2025-11-16" in newest && "primary_release_date.lte=2026-11-16" in newest)
        val comedy = io.github.diamondrubinny.scary.core.tmdb.Tmdb.discoverUrl(io.github.diamondrubinny.scary.core.tmdb.TmdbCategory.HORROR_COMEDY, today, io.github.diamondrubinny.scary.core.model.Language.EN)
        assertTrue(comedy, "with_genres=27%2C35" in comedy)
        assertEquals(io.github.diamondrubinny.scary.core.tmdb.TmdbCategory.GIALLO, io.github.diamondrubinny.scary.core.tmdb.TmdbCategory.of("giallo"))
        assertEquals(null, io.github.diamondrubinny.scary.core.tmdb.TmdbCategory.of("zombies"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pages past TMDB's last are refused`() {
        io.github.diamondrubinny.scary.core.tmdb.Tmdb.discoverUrl(io.github.diamondrubinny.scary.core.tmdb.TmdbCategory.MAX_FEAR, today, io.github.diamondrubinny.scary.core.model.Language.EN, page = 501)
    }
}
