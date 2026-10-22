package io.github.diamondrubinny.scary.core.tmdb

import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.cinemeta.OnlineRow
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.Movie
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

/** What TMDB adds to a movie: text in the UI language and higher-resolution art. */
@Serializable
data class TmdbInfo(
    val tmdbId: Long,
    /** Language.code of [title] and [overview]. */
    val language: String,
    val title: String = "",
    val overview: String = "",
    val poster: String? = null,
    val backdrop: String? = null,
    val rating: Double? = null,
)

/** A TMDB search result before its IMDb id is known. */
data class TmdbResult(
    val tmdbId: Long,
    val title: String,
    val originalTitle: String,
    val overview: String,
    val year: Int?,
    val poster: String?,
    val backdrop: String?,
    val rating: Double?,
    val genreIds: List<Int>,
)

/** How a key is sent: a v4 read-access token goes in a header, a v3 key in the query string. */
sealed interface TmdbAuth {
    data class Bearer(val token: String) : TmdbAuth
    data class ApiKey(val key: String) : TmdbAuth
}

/** Rows SCARY builds from TMDB's discover endpoint. Parameters are fixed here, never taken from input. */
enum class TmdbRow(val id: String, val titleHe: String, val titleEn: String, val titlePt: String, val titleRu: String) {
    NEW_HORROR("tmdb_new_horror", "אימה חדשה", "New horror", "Terror recente", "Новые ужасы"),
    TOP_HORROR("tmdb_top_horror", "הכי מדורגים באימה", "Top rated horror", "Terror mais bem avaliado", "Лучшие ужасы по оценкам"),
    PSYCH_THRILLER("tmdb_psych_thriller", "מתח ותעלומה", "Thrillers and mysteries", "Suspense e mistério", "Триллеры и загадки"),
}

/**
 * TMDB helpers. Responses are untrusted: everything is optional, image paths must look like TMDB file names,
 * and ids are numeric. The key itself never appears in anything this object returns except the auth value.
 */
object Tmdb {
    const val API_HOST = "api.themoviedb.org"
    private const val API = "https://$API_HOST/3"
    private const val IMAGES = "https://image.tmdb.org/t/p"
    const val HORROR = 27
    const val THRILLER = 53
    const val MYSTERY = 9648

    private val json = Json { ignoreUnknownKeys = true }
    private val v3Key = Regex("^[0-9a-fA-F]{32}$")
    private val v4Token = Regex("^eyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+$")
    private val imagePath = Regex("^/[A-Za-z0-9_\\-]{5,64}\\.(jpg|jpeg|png|webp)$")
    private val imdbPattern = Regex("^tt\\d{7,8}$")
    private val hebrew = Regex("[\\u0590-\\u05FF]")
    private val cyrillic = Regex("[\\u0400-\\u04FF]")
    private val arabic = Regex("[\\u0600-\\u06FF]")

    /**
     * TMDB falls back to the original title when it has no translation, so text only counts as localized when
     * it is in the right script (Hebrew, Russian, Arabic) or differs from the original title (Portuguese).
     */
    private fun isLocalized(language: Language, text: String, original: String = ""): Boolean = when (language) {
        Language.HE -> hebrew.containsMatchIn(text)
        Language.RU -> cyrillic.containsMatchIn(text)
        Language.AR -> arabic.containsMatchIn(text)
        Language.PT -> text.isNotBlank() && !text.equals(original, ignoreCase = true)
        Language.EN -> false
    }

    /** Accepts a v3 API key (32 hex) or a v4 read access token (JWT). Whitespace around it is ignored. */
    fun parseAuth(input: String): TmdbAuth? {
        val value = input.trim()
        return when {
            v3Key.matches(value) -> TmdbAuth.ApiKey(value)
            value.length in 40..1_000 && v4Token.matches(value) -> TmdbAuth.Bearer(value)
            else -> null
        }
    }

    /** "…a1b2": enough to recognise the key without showing it. */
    fun mask(key: String): String = "…" + key.trim().takeLast(4)

    fun configurationUrl(): String = "$API/configuration"

    fun findUrl(imdbId: String, language: Language): String {
        require(imdbPattern.matches(imdbId)) { "invalid IMDb id" }
        return "$API/find/$imdbId?external_source=imdb_id&language=${language.tmdbTag}"
    }

    fun externalIdsUrl(tmdbId: Long): String {
        require(tmdbId > 0) { "invalid TMDB id" }
        return "$API/movie/$tmdbId/external_ids"
    }

    fun discoverUrl(row: TmdbRow, today: LocalDate, language: Language): String {
        val params = when (row) {
            TmdbRow.NEW_HORROR -> listOf(
                "with_genres" to "$HORROR",
                "sort_by" to "popularity.desc",
                "primary_release_date.gte" to today.minusYears(1).toString(),
                "primary_release_date.lte" to today.toString(),
                "vote_count.gte" to "40",
            )
            TmdbRow.TOP_HORROR -> listOf(
                "with_genres" to "$HORROR",
                "sort_by" to "vote_average.desc",
                "vote_count.gte" to "3000",
            )
            TmdbRow.PSYCH_THRILLER -> listOf(
                "with_genres" to "$THRILLER,$MYSTERY",
                "sort_by" to "popularity.desc",
                "vote_count.gte" to "500",
            )
        } + listOf("include_adult" to "false", "language" to language.tmdbTag, "page" to "1")
        return "$API/discover/movie?" + params.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, StandardCharsets.UTF_8) }
    }

    /** Adds the v3 key to a URL; v4 tokens go in the Authorization header instead. */
    fun authorize(url: String, auth: TmdbAuth): String = when (auth) {
        is TmdbAuth.Bearer -> url
        is TmdbAuth.ApiKey -> url + (if ('?' in url) "&" else "?") + "api_key=" + auth.key
    }

    fun imageUrl(path: String?, size: String): String? =
        path?.takeIf(imagePath::matches)?.let { "$IMAGES/$size$it" }

    fun parseFind(text: String, language: Language): TmdbInfo? {
        val first = (root(text)?.get("movie_results") as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val result = toResult(first) ?: return null
        return TmdbInfo(
            tmdbId = result.tmdbId,
            language = language.code,
            // English is TMDB's base language: its title and overview are always usable as English text.
            title = result.title.takeIf { language == Language.EN || isLocalized(language, it, result.originalTitle) }.orEmpty(),
            overview = result.overview.takeIf {
                it.isNotBlank() && (language == Language.EN || language == Language.PT || isLocalized(language, it))
            }.orEmpty(),
            poster = imageUrl(result.poster, "w500"),
            backdrop = imageUrl(result.backdrop, "w1280"),
            rating = result.rating,
        )
    }

    fun parseDiscover(text: String): List<TmdbResult> =
        (root(text)?.get("results") as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(::toResult) }.orEmpty()

    fun parseImdbId(text: String): String? =
        (root(text)?.get("imdb_id") as? JsonPrimitive)?.contentOrNull?.takeIf(imdbPattern::matches)

    /** Turns a discover result plus its IMDb id into the same shape as a Cinemeta row entry. */
    fun toMeta(result: TmdbResult, imdbId: String, language: Language): CinemetaMeta {
        val localTitle = result.title.takeIf { isLocalized(language, it, result.originalTitle) }.orEmpty()
        val localOverview = result.overview.takeIf { language != Language.EN && it.isNotBlank() && (language == Language.PT || isLocalized(language, it)) }.orEmpty()
        return CinemetaMeta(
        imdbId = imdbId,
        name = if (language == Language.EN) result.title else result.originalTitle.ifBlank { result.title },
        year = result.year,
        poster = imageUrl(result.poster, "w500"),
        background = imageUrl(result.backdrop, "w1280"),
        // Without a translation, keep TMDB's overview as the English synopsis rather than showing none.
        description = if (language == Language.EN || localOverview.isBlank()) result.overview else "",
        localName = localTitle,
        localDescription = localOverview,
        localLanguage = if (localTitle.isBlank() && localOverview.isBlank()) "" else language.code,
        genres = buildList {
            if (HORROR in result.genreIds) add("Horror")
            if (THRILLER in result.genreIds) add("Thriller")
            if (MYSTERY in result.genreIds) add("Mystery")
        },
        )
    }

    /** Localized text and sharper art from TMDB. Curated translations and editorial fields always win. */
    fun enrich(movie: Movie, info: TmdbInfo): Movie {
        val lang = info.language
        if (lang == Language.EN.code) {
            // English lives in the base fields; fill only what is missing, curated text always wins.
            return movie.copy(
                synopsisEn = movie.synopsisEn.ifBlank { info.overview },
                posterUrl = info.poster ?: movie.posterUrl,
                backgroundUrl = info.backdrop ?: movie.backgroundUrl,
            )
        }
        val hasTitle = movie.titles[lang]?.isNotBlank() == true || (lang == Language.HE.code && movie.titleHe.isNotBlank())
        val hasSynopsis = movie.synopses[lang]?.isNotBlank() == true || (lang == Language.HE.code && movie.synopsisHe.isNotBlank())
        return movie.copy(
            titles = if (hasTitle || info.title.isBlank()) movie.titles else movie.titles + (lang to info.title),
            synopses = if (hasSynopsis || info.overview.isBlank()) movie.synopses else movie.synopses + (lang to info.overview),
            posterUrl = info.poster ?: movie.posterUrl,
            backgroundUrl = info.backdrop ?: movie.backgroundUrl,
        )
    }

    fun rowOf(row: TmdbRow, metas: List<CinemetaMeta>): OnlineRow =
        OnlineRow(row.id, row.titleHe, row.titleEn, metas, mapOf("pt" to row.titlePt, "ru" to row.titleRu))

    private fun root(text: String): JsonObject? =
        try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun toResult(o: JsonObject): TmdbResult? {
        val id = (o["id"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 } ?: return null
        val title = o.text("title").ifBlank { return null }
        return TmdbResult(
            tmdbId = id,
            title = title,
            originalTitle = o.text("original_title"),
            overview = o.text("overview").take(1_500),
            year = o.text("release_date").take(4).toIntOrNull()?.takeIf { it in 1895..2100 },
            poster = (o["poster_path"] as? JsonPrimitive)?.contentOrNull,
            backdrop = (o["backdrop_path"] as? JsonPrimitive)?.contentOrNull,
            rating = (o["vote_average"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it in 0.0..10.0 },
            genreIds = (o["genre_ids"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }.orEmpty(),
        )
    }

    private fun JsonObject.text(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.take(300).orEmpty()
}

/** Disk cache for TMDB data, like the Cinemeta one. It holds movie data only, never the key. */
@Serializable
data class TmdbCache(
    val fetchedAtMillis: Long,
    /** Language.code the text was fetched in; a different UI language means a refresh. */
    val language: String = "",
    val info: Map<String, TmdbInfo>,
    val rows: List<OnlineRow>,
) {
    fun isStale(nowMillis: Long, maxAgeMillis: Long = 24L * 60 * 60 * 1000): Boolean =
        nowMillis - fetchedAtMillis !in 0..maxAgeMillis

    fun encode(): String = cacheJson.encodeToString(serializer(), this)

    companion object {
        private val cacheJson = Json { ignoreUnknownKeys = true }

        fun decode(text: String): TmdbCache? =
            try {
                cacheJson.decodeFromString(serializer(), text)
            } catch (e: IllegalArgumentException) {
                null
            }
    }
}
