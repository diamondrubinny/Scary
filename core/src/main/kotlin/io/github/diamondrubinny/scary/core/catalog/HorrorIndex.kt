// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.model.Subgenre
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Year

/**
 * The full horror index (index.json): every well-known horror film on Wikidata (CC0) that IMDb also calls horror,
 * built by tools/catalog/build_index.py. Facts only: titles, year, director, subgenres, IMDb's other genres. No fear
 * level and no synopsis, which are never made up; the synopsis and poster come from Cinemeta when a film is opened.
 *
 * The file ships inside the APK, but it is still read as untrusted: an entry that breaks a rule is dropped (and
 * counted), never shown half-checked. Its films are "cm_" + IMDb id, like Cinemeta's, so the same film from an online
 * row is the same movie, and none of them is in the hand-picked library.
 */
object HorrorIndex {
    const val SUPPORTED_VERSION = 1
    private const val MAX_TITLE = 120
    private const val MAX_NAME = 80
    private const val MAX_GENRES = 6
    private const val FIRST_FILM_YEAR = 1895
    private val imdbPattern = Regex("^tt\\d{7,8}$")
    private val genrePattern = Regex("^[A-Za-z][A-Za-z -]{1,23}$")
    private val languages = Language.entries.map { it.code }.toSet()
    private val subgenres = Subgenre.entries.associateBy { it.serialName }
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class IndexFile(val version: Int, val films: List<Entry>)

    @Serializable
    private data class Entry(
        val imdb: String = "",
        @SerialName("t") val titles: Map<String, String> = emptyMap(),
        @SerialName("y") val year: Int = 0,
        @SerialName("d") val director: String? = null,
        @SerialName("s") val subgenres: List<String> = emptyList(),
        @SerialName("g") val genres: List<String> = emptyList(),
    )

    /** The films, most widely known first, and how many entries were dropped for breaking a rule. */
    data class Result(val movies: List<Movie>, val dropped: Int)

    /**
     * Parses [text]; films already in the hand-picked library ([curatedImdbIds]) are left out. Throws
     * [CatalogException] only when the file as a whole cannot be read.
     */
    fun parse(text: String, curatedImdbIds: Set<String> = emptySet(), thisYear: Int = Year.now().value): Result {
        val file = try {
            json.decodeFromString(IndexFile.serializer(), text)
        } catch (e: IllegalArgumentException) {
            throw CatalogException(listOf("malformed index json: ${e.message}"))
        }
        if (file.version != SUPPORTED_VERSION) throw CatalogException(listOf("unsupported index version ${file.version}"))
        val seen = HashSet<String>()
        var dropped = 0
        val movies = file.films.mapNotNull { entry ->
            val movie = toMovie(entry, thisYear)
            when {
                movie == null || !seen.add(entry.imdb) -> {
                    dropped++
                    null
                }
                entry.imdb in curatedImdbIds -> null
                else -> movie
            }
        }
        return Result(movies, dropped)
    }

    private fun toMovie(entry: Entry, thisYear: Int): Movie? {
        if (!imdbPattern.matches(entry.imdb) || entry.year !in FIRST_FILM_YEAR..thisYear) return null
        if (entry.titles.keys.any { it !in languages }) return null
        val titles = entry.titles.mapValues { (_, title) -> clean(title, MAX_TITLE) ?: return null }
        val english = titles[Language.EN.code] ?: return null
        val director = entry.director?.let { clean(it, MAX_NAME) ?: return null }.orEmpty()
        if (entry.genres.size > MAX_GENRES || entry.genres.any { !genrePattern.matches(it) }) return null
        return Movie(
            id = Cinemeta.ID_PREFIX + entry.imdb,
            imdbId = entry.imdb,
            title = english,
            titleHe = titles[Language.HE.code].orEmpty(),
            titles = titles - Language.EN.code,
            year = entry.year,
            director = director,
            genre = Genre.HORROR,
            // Unknown names are skipped, not refused: a newer index may know subgenres this version does not.
            subgenres = entry.subgenres.mapNotNull(subgenres::get).distinct(),
            hue = Math.floorMod(entry.imdb.hashCode(), 360),
            curated = false,
            genres = listOf("Horror") + entry.genres,
        )
    }

    /** One line of plain text, or null: no control or bidirectional-override characters, within [max]. */
    private fun clean(text: String, max: Int): String? {
        if (text.any { it.isISOControl() || it in BIDI_CONTROLS }) return null
        val trimmed = text.trim()
        return trimmed.takeIf { it.isNotEmpty() && it.length <= max }
    }

    private val BIDI_CONTROLS = setOf('‪', '‫', '‬', '‭', '‮', '⁦', '⁧', '⁨', '⁩')

    private val Subgenre.serialName: String
        get() = Subgenre.serializer().descriptor.getElementName(ordinal)
}
