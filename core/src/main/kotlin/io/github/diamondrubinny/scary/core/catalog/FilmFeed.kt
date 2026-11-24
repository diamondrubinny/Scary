// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.MediaKind
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.model.Subgenre
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Year

/**
 * The film feed (docs/CATALOG_REBUILD_PLAN.md): every film and every home row, built each week from public data by
 * tools/catalog/build_feed.py and downloaded by the TV, so the library changes without an app update. Facts only:
 * titles, year, director, subgenres, IMDb's rating and other genres, and which films each row holds, in order. Beside
 * the horror films it holds thrillers and horror series, each marked as what it is. No synopsis and no fear level,
 * which are never made up.
 *
 * The file comes from the network, so it is read as untrusted, whole: a file too large, of another version or with too
 * few films is refused ([CatalogException]) and the copy the TV already has stays. Inside a good file, a film that
 * breaks a rule is dropped (and counted), and a row is kept only with the films that are left, if enough are.
 * Its films are "cm_" + IMDb id, like Cinemeta's and the index's, so the same film from anywhere is the same movie.
 */
object FilmFeed {
    const val SUPPORTED_VERSION = 1

    /** The largest file read: three times today's, so growth is not refused and a runaway file is. */
    const val MAX_CHARS = 3_000_000
    const val MIN_FILMS = 1_000
    const val MAX_FILMS = 12_000
    const val MAX_ROWS = 40
    const val MAX_ROW_FILMS = 80

    /** A row with fewer films than this after checking is not worth a place on the home screen. */
    const val MIN_ROW_FILMS = 8
    private const val KIND_THRILLER = "thriller"
    private const val KIND_SERIES = "series"
    private const val MAX_TITLE = 120
    private const val MAX_NAME = 80
    private const val MAX_GENRES = 6
    private const val FIRST_FILM_YEAR = 1895
    private val imdbPattern = Regex("^tt\\d{7,8}$")
    private val genrePattern = Regex("^[A-Za-z][A-Za-z -]{1,23}$")
    private val rowIdPattern = Regex("^[a-z][a-z_]{1,31}$")
    private val datePattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val languages = Language.entries.map { it.code }.toSet()
    private val subgenres = Subgenre.entries.associateBy { it.serialName }
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class FeedFile(val version: Int, val generated: String = "", val films: List<Entry> = emptyList(), val rows: List<RowEntry> = emptyList())

    @Serializable
    private data class Entry(
        val imdb: String = "",
        @SerialName("t") val titles: Map<String, String> = emptyMap(),
        @SerialName("y") val year: Int = 0,
        @SerialName("d") val director: String? = null,
        @SerialName("s") val subgenres: List<String> = emptyList(),
        @SerialName("g") val genres: List<String> = emptyList(),
        @SerialName("r") val rating: Double? = null,
        /** Absent for a horror film; "thriller" for a thriller, "series" for a horror series. */
        @SerialName("k") val kind: String? = null,
        @SerialName("e") val endYear: Int? = null,
        @SerialName("on") val ongoing: Boolean = false,
    )

    @Serializable
    private data class RowEntry(val id: String = "", val films: List<String> = emptyList())

    /** A home row: its id (the app holds its name, in every language) and its films' ids, in order. */
    data class Row(val id: String, val movieIds: List<String>)

    /**
     * The films, best known first; the rows, in home-screen order; the day the feed was built (ISO); and how many
     * films and rows were dropped for breaking a rule.
     */
    data class Result(val movies: List<Movie>, val rows: List<Row>, val generated: String, val droppedFilms: Int, val droppedRows: Int)

    /** Parses [text]. Throws [CatalogException] when the file as a whole is not a feed this version can use. */
    fun parse(text: String, thisYear: Int = Year.now().value): Result {
        if (text.length > MAX_CHARS) throw CatalogException(listOf("feed too large: ${text.length} characters"))
        val file = try {
            json.decodeFromString(FeedFile.serializer(), text)
        } catch (e: IllegalArgumentException) {
            throw CatalogException(listOf("malformed feed json: ${e.message}"))
        }
        if (file.version != SUPPORTED_VERSION) throw CatalogException(listOf("unsupported feed version ${file.version}"))
        if (!datePattern.matches(file.generated)) throw CatalogException(listOf("feed without a date"))
        if (file.films.size > MAX_FILMS || file.rows.size > MAX_ROWS) throw CatalogException(listOf("feed with too many films or rows"))
        val seen = HashSet<String>()
        var droppedFilms = 0
        val movies = file.films.mapNotNull { entry ->
            toMovie(entry, thisYear)?.takeIf { seen.add(entry.imdb) } ?: run {
                droppedFilms++
                null
            }
        }
        if (movies.size < MIN_FILMS) throw CatalogException(listOf("feed with too few films: ${movies.size}"))
        val known = movies.mapTo(HashSet()) { it.id }
        val rowIds = HashSet<String>()
        var droppedRows = 0
        val rows = file.rows.mapNotNull { row ->
            val ids = row.films.asSequence()
                .filter(imdbPattern::matches).map { Cinemeta.ID_PREFIX + it }.filter { it in known }
                .distinct().take(MAX_ROW_FILMS).toList()
            if (rowIdPattern.matches(row.id) && ids.size >= MIN_ROW_FILMS && rowIds.add(row.id)) {
                Row(row.id, ids)
            } else {
                droppedRows++
                null
            }
        }
        return Result(movies, rows, file.generated, droppedFilms, droppedRows)
    }

    private fun toMovie(entry: Entry, thisYear: Int): Movie? {
        if (!imdbPattern.matches(entry.imdb) || entry.year !in FIRST_FILM_YEAR..thisYear) return null
        if (entry.titles.keys.any { it !in languages }) return null
        val titles = entry.titles.mapValues { (_, title) -> clean(title, MAX_TITLE) ?: return null }
        val english = titles[Language.EN.code] ?: return null
        val director = entry.director?.let { clean(it, MAX_NAME) ?: return null }.orEmpty()
        if (entry.genres.size > MAX_GENRES || entry.genres.any { !genrePattern.matches(it) }) return null
        if (entry.rating != null && (entry.rating.isNaN() || entry.rating <= 0.0 || entry.rating > 10.0)) return null
        // A kind this version does not know is not shown as something it is not.
        if (entry.kind != null && entry.kind != KIND_THRILLER && entry.kind != KIND_SERIES) return null
        val series = entry.kind == KIND_SERIES
        val thriller = entry.kind == KIND_THRILLER
        if (entry.endYear != null && (!series || entry.endYear !in entry.year..2100)) return null
        return Movie(
            id = Cinemeta.ID_PREFIX + entry.imdb,
            imdbId = entry.imdb,
            title = english,
            titleHe = titles[Language.HE.code].orEmpty(),
            titles = titles - Language.EN.code,
            year = entry.year,
            director = director,
            genre = if (thriller) Genre.THRILLER else Genre.HORROR,
            kind = if (series) MediaKind.SERIES else MediaKind.MOVIE,
            endYear = entry.endYear,
            ongoing = series && entry.ongoing && entry.endYear == null,
            // Unknown names are skipped, not refused: a newer feed may know subgenres this version does not.
            subgenres = entry.subgenres.mapNotNull(subgenres::get).distinct(),
            hue = Math.floorMod(entry.imdb.hashCode(), 360),
            imdbRating = entry.rating,
            curated = false,
            genres = listOf(if (thriller) "Thriller" else "Horror") + entry.genres,
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
