// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.cinemeta

import io.github.diamondrubinny.scary.core.model.Catalog
import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.MediaKind
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.model.MovieCollection
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** The fields SCARY uses from a Cinemeta movie, already validated. */
@Serializable
data class CinemetaMeta(
    val imdbId: String,
    val name: String,
    val year: Int? = null,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String = "",
    val director: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val imdbRating: Double? = null,
    val runtimeMin: Int? = null,
    val trailerYoutubeId: String? = null,
    /** More trailers of the same movie, for when the first may not play inside an app. */
    val trailerAlternates: List<String> = emptyList(),
    /** ISO release date, for films not out yet (TMDB's coming-soon row); empty otherwise. */
    val releaseDate: String = "",
    /** Title and description in [localLanguage] (a Language.code), when the row comes from TMDB. */
    val localName: String = "",
    val localDescription: String = "",
    val localLanguage: String = "",
    /** "movie" or "series", as Cinemeta says. */
    val type: String = TYPE_MOVIE,
    /** A series' last year, from "2016–2025"; null when it ended the year it began or is still running. */
    val endYear: Int? = null,
    /** A series still running ("2022–"). */
    val ongoing: Boolean = false,
) {
    val kind: MediaKind get() = if (type == TYPE_SERIES) MediaKind.SERIES else MediaKind.MOVIE

    companion object {
        const val TYPE_MOVIE = "movie"
        const val TYPE_SERIES = "series"

        fun typeOf(kind: MediaKind): String = if (kind == MediaKind.SERIES) TYPE_SERIES else TYPE_MOVIE
    }
}

/** A row pulled from a Cinemeta catalog, such as popular horror. */
@Serializable
data class OnlineRow(
    val id: String,
    val titleHe: String,
    val titleEn: String,
    val metas: List<CinemetaMeta>,
    val titles: Map<String, String> = emptyMap(),
)

/**
 * Cinemeta is Stremio's public metadata service (no API key). Its responses are treated as untrusted:
 * every field is optional, types are checked, and only https image URLs are kept.
 */
object Cinemeta {
    const val BASE_URL = "https://v3-cinemeta.strem.io"

    private val json = Json { ignoreUnknownKeys = true }
    private val imdbPattern = Regex("^tt\\d{7,8}$")

    /**
     * Titles that announce adult content. Cinemeta has no adult flag, and such films are sometimes tagged as thrillers
     * ("Shining Sex", 1977), so they would reach rows and search. A word list catches the obvious ones only; the
     * parental lock (which hides every online movie's art) is the real control.
     */
    private val adultTitle = Regex(
        "(?<![\\p{L}\\p{N}])(sex|sexy|porn|porno|xxx|erotic|erotica|erotik|érotique|hentai)(?![\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE,
    )
    private val youtubePattern = Regex("^[A-Za-z0-9_-]{11}$")
    // Genres are names ("Horror") or, for the year catalog, years ("2026").
    private val genrePattern = Regex("^[A-Za-z0-9-]{1,32}$")
    private val catalogIdPattern = Regex("^[A-Za-z]{1,32}$")
    private const val MAX_URL_LENGTH = 512
    private const val MAX_TEXT_LENGTH = 1_500
    private const val MAX_CAST = 6
    private const val MAX_TRAILERS = 4
    private const val MIN_SEARCH_LENGTH = 3
    private const val MAX_SEARCH_LENGTH = 60
    private const val MAX_SEARCH_RESULTS = 20
    /** The furthest catalog page SCARY asks for. */
    const val MAX_SKIP = 10_000

    /** Movies in one catalog page. */
    const val PAGE_SIZE = 50

    /**
     * A safety bound only: endless rows go on until their catalogs run out (Cinemeta's horror ends near 1,750 films,
     * its thrillers near 4,150), and rows that walk back through the years go on far beyond. Well over what anyone
     * scrolls through on a TV.
     */
    const val MAX_ENDLESS_ROW_MOVIES = 50_000

    /**
     * Movies an online row may hold after the daily refresh: three catalog pages, the most any row loads, so nothing
     * fetched is dropped (endless rows continue right after their last page).
     */
    const val MAX_ROW_MOVIES = 150

    /** Movies from Cinemeta have ids "cm_" + IMDb id, so they never clash with the curated library's. */
    const val ID_PREFIX = "cm_"
    private val THEME_GENRES = listOf("Horror", "Thriller", "Mystery")

    /** Never on SCARY's shelves even when also tagged as a thriller: documentaries and titles for children. */
    private val OFF_THEME_GENRES = listOf("Documentary", "Family", "Animation", "Kids")

    /** Full metas fetched per search to learn the genres of results that came without them. */
    private const val MAX_GENRE_LOOKUPS = 12

    fun metaUrl(imdbId: String, kind: MediaKind = MediaKind.MOVIE): String {
        require(imdbPattern.matches(imdbId)) { "invalid IMDb id" }
        return "$BASE_URL/meta/${CinemetaMeta.typeOf(kind)}/$imdbId.json"
    }

    /** A catalog page; Cinemeta pages hold 50 movies, so [skip] moves in steps of 50. */
    fun catalogUrl(catalogId: String, genre: String, skip: Int = 0, kind: MediaKind = MediaKind.MOVIE): String {
        require(catalogIdPattern.matches(catalogId)) { "invalid catalog id" }
        require(genrePattern.matches(genre)) { "invalid genre" }
        require(skip in 0..MAX_SKIP) { "invalid skip" }
        val extra = if (skip > 0) "genre=$genre&skip=$skip" else "genre=$genre"
        return "$BASE_URL/catalog/${CinemetaMeta.typeOf(kind)}/$catalogId/$extra.json"
    }

    /**
     * Only horror, thriller and mystery, for catalogs that mix every genre (the year catalog). Movies without genres
     * and documentaries are left out.
     */
    fun themed(metas: List<CinemetaMeta>): List<CinemetaMeta> = metas.filter(::onTheme)

    /**
     * Search in Cinemeta's "top" catalog, using the Stremio add-on "extra" path argument. Null when the query is too
     * short or too long; otherwise every character outside [A-Za-z0-9-_.~] is percent-encoded as UTF-8.
     */
    fun searchUrl(query: String, kind: MediaKind = MediaKind.MOVIE): String? {
        val q = query.trim().replace(Regex("\\s+"), " ")
        if (q.length !in MIN_SEARCH_LENGTH..MAX_SEARCH_LENGTH) return null
        val encoded = buildString {
            for (byte in q.toByteArray(Charsets.UTF_8)) {
                val c = byte.toInt() and 0xFF
                if (c.toChar().let { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "-_.~" }) {
                    append(c.toChar())
                } else {
                    append('%').append("%02X".format(c))
                }
            }
        }
        return "$BASE_URL/catalog/${CinemetaMeta.typeOf(kind)}/top/search=$encoded.json"
    }

    /**
     * Web search results as movies: only horror, thriller and mystery, never documentaries or family titles, minus
     * anything already in [known], capped at [limit]. Results without genres are dropped: Cinemeta's search often
     * leaves them out, and kept unfiltered they let in children's shows and unrelated films. The caller looks their
     * genres up first ([needsGenres]).
     */
    fun searchResults(metas: List<CinemetaMeta>, known: Catalog, limit: Int = MAX_SEARCH_RESULTS): List<Movie> {
        val knownImdb = known.movies.mapTo(HashSet()) { it.imdbId }
        return metas.asSequence()
            .filter { it.imdbId !in knownImdb }
            .filter(::onTheme)
            .mapNotNull(::toMovie)
            .distinctBy { it.imdbId }
            .take(limit)
            .toList()
    }

    /** Search results whose genres must be looked up (the full meta) before [searchResults] can judge them. */
    fun needsGenres(metas: List<CinemetaMeta>, known: Catalog, limit: Int = MAX_GENRE_LOOKUPS): List<String> {
        val knownImdb = known.movies.mapTo(HashSet()) { it.imdbId }
        return metas.filter { it.genres.isEmpty() && it.imdbId !in knownImdb }.map { it.imdbId }.distinct().take(limit)
    }

    private fun onTheme(meta: CinemetaMeta): Boolean =
        meta.genres.any { g -> THEME_GENRES.any { it.equals(g, ignoreCase = true) } } &&
            meta.genres.none { g -> OFF_THEME_GENRES.any { it.equals(g, ignoreCase = true) } }

    /** The reverse of [toMovie], so a movie found online can be saved and rebuilt later. */
    fun fromMovie(movie: Movie, language: String = ""): CinemetaMeta = CinemetaMeta(
        imdbId = movie.imdbId,
        name = movie.title,
        year = movie.year,
        poster = movie.posterUrl,
        background = movie.backgroundUrl,
        logo = movie.logoUrl,
        description = movie.synopsisEn,
        director = movie.director.split(',').map(String::trim).filter(String::isNotEmpty),
        cast = movie.cast,
        genres = listOf(if (movie.genre == Genre.HORROR) "Horror" else "Thriller"),
        imdbRating = movie.imdbRating,
        runtimeMin = movie.runtimeMin.takeIf { it > 0 },
        trailerYoutubeId = movie.trailerYoutubeId,
        trailerAlternates = movie.trailerAlternates,
        localName = movie.titles[language].orEmpty(),
        localDescription = movie.synopses[language].orEmpty(),
        localLanguage = language.takeIf { movie.titles.containsKey(it) || movie.synopses.containsKey(it) }.orEmpty(),
        type = CinemetaMeta.typeOf(movie.kind),
        endYear = movie.endYear,
        ongoing = movie.ongoing,
    )

    fun parseMeta(text: String): CinemetaMeta? =
        root(text)?.get("meta")?.let { it as? JsonObject }?.let(::toMeta)

    fun parseCatalog(text: String): List<CinemetaMeta> =
        (root(text)?.get("metas") as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(::toMeta) }.orEmpty()

    private fun root(text: String): JsonObject? =
        try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun toMeta(o: JsonObject): CinemetaMeta? {
        val imdbId = (o.str("imdb_id") ?: o.str("id"))?.takeIf(imdbPattern::matches) ?: return null
        val name = o.str("name")?.trim()?.take(200)?.takeIf(String::isNotEmpty) ?: return null
        if (adultTitle.containsMatchIn(name)) return null
        val years = o.str("releaseInfo")?.takeIf(String::isNotBlank) ?: o.str("year")
        val year = years?.take(4)?.toIntOrNull()?.takeIf { it in 1895..2100 }
        val type = if (o.str("type") == CinemetaMeta.TYPE_SERIES) CinemetaMeta.TYPE_SERIES else CinemetaMeta.TYPE_MOVIE
        // A series' years: "2016–2025" (ended), "2022–" (running), "2018" (one season); either dash.
        val span = years?.trim()?.let(seriesYears::matchEntire)
        val endYear = span?.groupValues?.get(2)?.toIntOrNull()?.takeIf { year != null && it in year..2100 }
        val ongoing = type == CinemetaMeta.TYPE_SERIES && span != null && span.groupValues[1].isNotEmpty() && span.groupValues[2].isEmpty()
        val trailers = (
            (o["trailerStreams"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.str("ytId") }.orEmpty() +
                (o["trailers"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.str("source") }.orEmpty()
            ).filter(youtubePattern::matches).distinct().take(MAX_TRAILERS)
        return CinemetaMeta(
            imdbId = imdbId,
            name = name,
            year = year,
            poster = safeImage(o.str("poster"))?.replace("/poster/small/", "/poster/medium/"),
            background = safeImage(o.str("background")),
            logo = safeImage(o.str("logo")),
            description = o.str("description")?.trim()?.take(MAX_TEXT_LENGTH).orEmpty(),
            director = o.strList("director").take(3),
            cast = o.strList("cast").take(MAX_CAST),
            genres = (o.strList("genres").ifEmpty { o.strList("genre") }).take(8),
            imdbRating = o.str("imdbRating")?.toDoubleOrNull()?.takeIf { it in 0.0..10.0 },
            runtimeMin = o.str("runtime")?.substringBefore(' ')?.toIntOrNull()?.takeIf { it in 1..600 },
            trailerYoutubeId = trailers.firstOrNull(),
            trailerAlternates = trailers.drop(1),
            type = type,
            endYear = endYear.takeIf { type == CinemetaMeta.TYPE_SERIES },
            ongoing = ongoing,
        )
    }

    private val seriesYears = Regex("^\\d{4}([–-])?(\\d{4})?$")

    /** Only https URLs of sane length; anything else (http, data:, file:, javascript:) is dropped. */
    fun safeImage(url: String?): String? =
        url?.trim()?.takeIf { it.startsWith("https://") && it.length <= MAX_URL_LENGTH && it.none(Char::isWhitespace) }

    /** Adds artwork and extras to a curated movie without overwriting its editorial fields. */
    fun enrich(movie: Movie, meta: CinemetaMeta): Movie = movie.copy(
        posterUrl = meta.poster ?: movie.posterUrl,
        backgroundUrl = meta.background ?: movie.backgroundUrl,
        logoUrl = meta.logo ?: movie.logoUrl,
        imdbRating = meta.imdbRating ?: movie.imdbRating,
        cast = meta.cast.ifEmpty { movie.cast },
        trailerYoutubeId = movie.trailerYoutubeId ?: meta.trailerYoutubeId,
        trailerAlternates = if (movie.trailerYoutubeId != null) movie.trailerAlternates else meta.trailerAlternates,
        synopsisEn = movie.synopsisEn.ifBlank { meta.description },
        // A running series ends, or a new season starts: Cinemeta knows before the bundled library does.
        endYear = if (movie.isSeries && meta.kind == movie.kind && (meta.endYear != null || meta.ongoing)) meta.endYear else movie.endYear,
        ongoing = if (movie.isSeries && meta.kind == movie.kind && (meta.endYear != null || meta.ongoing)) meta.ongoing else movie.ongoing,
    )

    /** A movie that is only known from Cinemeta. Fear is unknown, so it stays null. */
    fun toMovie(meta: CinemetaMeta): Movie? {
        val year = meta.year ?: return null
        return Movie(
            id = ID_PREFIX + meta.imdbId,
            imdbId = meta.imdbId,
            title = meta.name,
            titles = localized(meta.localLanguage, meta.localName),
            year = year,
            director = meta.director.joinToString(", "),
            runtimeMin = meta.runtimeMin ?: 0,
            genre = if (meta.genres.any { it.equals("Horror", ignoreCase = true) }) Genre.HORROR else Genre.THRILLER,
            hue = Math.floorMod(meta.imdbId.hashCode(), 360),
            synopsisEn = meta.description,
            synopses = localized(meta.localLanguage, meta.localDescription),
            posterUrl = meta.poster,
            backgroundUrl = meta.background,
            logoUrl = meta.logo,
            imdbRating = meta.imdbRating,
            cast = meta.cast,
            trailerYoutubeId = meta.trailerYoutubeId,
            trailerAlternates = meta.trailerAlternates,
            releaseDate = meta.releaseDate.ifBlank { null },
            curated = false,
            kind = meta.kind,
            endYear = meta.endYear,
            ongoing = meta.ongoing,
        )
    }

    /**
     * Combines the bundled catalog with online data: curated movies get artwork, and each online row becomes
     * a collection placed before the curated ones. Movies already in the library are reused, not duplicated.
     */
    /**
     * Adds a further page of movies to the end of the online row [collectionId] (an endless row). Movies already in the
     * catalog are reused rather than duplicated; the row keeps its order and stops at [maxRow].
     */
    fun extend(base: Catalog, collectionId: String, metas: List<CinemetaMeta>, maxRow: Int = MAX_ENDLESS_ROW_MOVIES): Catalog {
        val index = base.collections.indexOfFirst { it.id == collectionId }
        if (index < 0 || metas.isEmpty()) return base
        val collection = base.collections[index]
        val byImdb = base.movies.associateByTo(HashMap()) { it.imdbId }
        val added = mutableListOf<Movie>()
        val newIds = metas.mapNotNull { meta ->
            byImdb[meta.imdbId]?.id ?: toMovie(meta)?.also { byImdb[it.imdbId] = it; added += it }?.id
        }
        val ids = (collection.movieIds + newIds).distinct().take(maxRow)
        val kept = ids.toHashSet()
        val collections = base.collections.toMutableList().also { it[index] = collection.copy(movieIds = ids) }
        return base.copy(movies = base.movies + added.filter { it.id in kept }, collections = collections)
    }

    /** The collection id an online row gets in the merged catalog. */
    fun onlineCollectionId(rowId: String): String = "online_$rowId"

    fun merge(base: Catalog, metas: Map<String, CinemetaMeta>, rows: List<OnlineRow>, perRow: Int = MAX_ROW_MOVIES): Catalog {
        val curated = base.movies.map { m -> metas[m.imdbId]?.let { enrich(m, it) } ?: m }
        val byImdb = curated.associateBy { it.imdbId }.toMutableMap()
        val added = mutableListOf<Movie>()
        val onlineCollections = rows.mapNotNull { row ->
            val ids = row.metas.asSequence()
                .mapNotNull { meta ->
                    byImdb[meta.imdbId]?.id ?: toMovie(meta)?.also { byImdb[it.imdbId] = it; added += it }?.id
                }
                .distinct()
                .take(perRow)
                .toList()
            if (ids.size < MIN_ROW_SIZE) null else MovieCollection(onlineCollectionId(row.id), row.titleHe, row.titleEn, ids, row.titles)
        }
        return base.copy(movies = curated + added, collections = onlineCollections + base.collections)
    }

    private const val MIN_ROW_SIZE = 4

    private fun localized(language: String, text: String): Map<String, String> =
        if (language.isBlank() || text.isBlank()) emptyMap() else mapOf(language to text)

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonObject.strList(key: String): List<String> = when (val v: JsonElement? = this[key]) {
        is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull?.trim() }
            .filter(String::isNotEmpty)
        is JsonPrimitive -> v.contentOrNull?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
        else -> emptyList()
    }
}

/** What SCARY keeps on disk between launches so artwork shows instantly and works offline. */
@Serializable
data class CinemetaCache(
    val fetchedAtMillis: Long,
    val metas: Map<String, CinemetaMeta>,
    val rows: List<OnlineRow>,
    /** Which set of online rows this cache holds; caches from older sets refresh at once. */
    val rowsVersion: Int = 1,
) {
    fun isStale(nowMillis: Long, maxAgeMillis: Long = DEFAULT_MAX_AGE_MS): Boolean =
        rowsVersion < ROWS_VERSION || nowMillis - fetchedAtMillis !in 0..maxAgeMillis

    fun encode(): String = cacheJson.encodeToString(serializer(), this)

    companion object {
        const val DEFAULT_MAX_AGE_MS = 24L * 60 * 60 * 1000
        /**
         * Bumped whenever the cached data changes shape (3: popular rows one page, deeper rows right after them;
         * 4: every trailer of a movie, not only the first; 5: series and their rows; 6: endless popular rows and rows
         * back through the years).
         */
        const val ROWS_VERSION = 6
        private val cacheJson = Json { ignoreUnknownKeys = true }

        fun decode(text: String): CinemetaCache? =
            try {
                cacheJson.decodeFromString(serializer(), text)
            } catch (e: IllegalArgumentException) {
                null
            }
    }
}

/**
 * Where an endless row continues, as one number. A plain row's cursor is its next catalog skip. A row that walks back
 * through the years (the "year" catalog, from its first year down to [FIRST_YEAR]) packs how many years back it is and
 * the skip within that year: when a year runs out, the row carries on with the year before.
 */
object EndlessCursor {
    /** Bigger than any skip within one year (Cinemeta.MAX_SKIP). */
    const val YEAR_STEP = 100_000
    const val FIRST_YEAR = 1920

    fun yearsBack(cursor: Int): Int = cursor / YEAR_STEP

    fun skip(cursor: Int): Int = cursor % YEAR_STEP

    /** The year [cursor] reads from, for a row starting at [startYear]; null once it is past [FIRST_YEAR]. */
    fun year(startYear: Int, cursor: Int): Int? = (startYear - yearsBack(cursor)).takeIf { it >= FIRST_YEAR }

    /** The start of the year before. */
    fun nextYear(cursor: Int): Int = (yearsBack(cursor) + 1) * YEAR_STEP

    /** The next page after one that came back with [pageSize] films: the year before when this year ran out. */
    fun after(cursor: Int, pageSize: Int, yearChain: Boolean): Int? = when {
        !yearChain -> if (pageSize == 0 || cursor + Cinemeta.PAGE_SIZE > Cinemeta.MAX_SKIP) null else cursor + Cinemeta.PAGE_SIZE
        pageSize == 0 || skip(cursor) + Cinemeta.PAGE_SIZE > Cinemeta.MAX_SKIP -> nextYear(cursor)
        else -> cursor + Cinemeta.PAGE_SIZE
    }
}
