package io.github.diamondrubinny.scary.core.cinemeta

import io.github.diamondrubinny.scary.core.model.Catalog
import io.github.diamondrubinny.scary.core.model.Genre
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
    /** Title and description in [localLanguage] (a Language.code), when the row comes from TMDB. */
    val localName: String = "",
    val localDescription: String = "",
    val localLanguage: String = "",
)

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
    private val youtubePattern = Regex("^[A-Za-z0-9_-]{11}$")
    private val genrePattern = Regex("^[A-Za-z-]{1,32}$")
    private val catalogIdPattern = Regex("^[A-Za-z]{1,32}$")
    private const val MAX_URL_LENGTH = 512
    private const val MAX_TEXT_LENGTH = 1_500
    private const val MAX_CAST = 6

    fun metaUrl(imdbId: String): String {
        require(imdbPattern.matches(imdbId)) { "invalid IMDb id" }
        return "$BASE_URL/meta/movie/$imdbId.json"
    }

    fun catalogUrl(catalogId: String, genre: String): String {
        require(catalogIdPattern.matches(catalogId)) { "invalid catalog id" }
        require(genrePattern.matches(genre)) { "invalid genre" }
        return "$BASE_URL/catalog/movie/$catalogId/genre=$genre.json"
    }

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
        val year = (o.str("year") ?: o.str("releaseInfo"))?.take(4)?.toIntOrNull()?.takeIf { it in 1895..2100 }
        val trailer = (o["trailerStreams"] as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonObject)?.str("ytId") }
            ?: (o["trailers"] as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonObject)?.str("source") }
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
            trailerYoutubeId = trailer?.takeIf(youtubePattern::matches),
        )
    }

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
        synopsisEn = movie.synopsisEn.ifBlank { meta.description },
    )

    /** A movie that is only known from Cinemeta. Fear is unknown, so it stays null. */
    fun toMovie(meta: CinemetaMeta): Movie? {
        val year = meta.year ?: return null
        return Movie(
            id = "cm_" + meta.imdbId,
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
            curated = false,
        )
    }

    /**
     * Combines the bundled catalog with online data: curated movies get artwork, and each online row becomes
     * a collection placed before the curated ones. Movies already in the library are reused, not duplicated.
     */
    fun merge(base: Catalog, metas: Map<String, CinemetaMeta>, rows: List<OnlineRow>, perRow: Int = 20): Catalog {
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
            if (ids.size < MIN_ROW_SIZE) null else MovieCollection("online_${row.id}", row.titleHe, row.titleEn, ids, row.titles)
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
) {
    fun isStale(nowMillis: Long, maxAgeMillis: Long = DEFAULT_MAX_AGE_MS): Boolean =
        nowMillis - fetchedAtMillis !in 0..maxAgeMillis

    fun encode(): String = cacheJson.encodeToString(serializer(), this)

    companion object {
        const val DEFAULT_MAX_AGE_MS = 24L * 60 * 60 * 1000
        private val cacheJson = Json { ignoreUnknownKeys = true }

        fun decode(text: String): CinemetaCache? =
            try {
                cacheJson.decodeFromString(serializer(), text)
            } catch (e: IllegalArgumentException) {
                null
            }
    }
}
