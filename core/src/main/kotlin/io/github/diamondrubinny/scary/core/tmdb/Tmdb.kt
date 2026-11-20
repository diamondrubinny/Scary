// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.tmdb

import io.github.diamondrubinny.scary.core.links.Links
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.cinemeta.OnlineRow
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.Movie
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
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
    /** ISO date (2026-11-14), when TMDB gives a valid one. */
    val releaseDate: String? = null,
    /** ISO 639-1 code of the film's original language ("en", "hi"), when TMDB gives a valid one. */
    val originalLanguage: String = "",
)

/** A series of films as TMDB holds it ("Halloween Collection"), its films in the order they came out. */
data class TmdbFranchise(val id: Long, val name: String, val parts: List<TmdbResult>)

/**
 * One actor of a film as TMDB lists them: TMDB's id for the person, the role they play (may be empty) and the
 * address of their photo (null when TMDB has none).
 */
data class TmdbCastMember(val personId: Long, val name: String, val character: String, val photo: String?)

/** How a key is sent: a v4 read-access token goes in a header, a v3 key in the query string. */
sealed interface TmdbAuth {
    data class Bearer(val token: String) : TmdbAuth
    data class ApiKey(val key: String) : TmdbAuth
}

/** Rows SCARY builds from TMDB's discover endpoint. Parameters are fixed here, never taken from input. */
enum class TmdbRow(
    val id: String,
    val titleHe: String,
    val titleEn: String,
    val titlePt: String,
    val titleRu: String,
    val titleAr: String,
) {
    NEW_HORROR("tmdb_new_horror", "אימה חדשה", "New horror", "Terror recente", "Новые ужасы", "رعب جديد"),
    TOP_HORROR("tmdb_top_horror", "הכי מדורגים באימה", "Top rated horror", "Terror mais bem avaliado", "Лучшие ужасы по оценкам", "أعلى أفلام الرعب تقييمًا"),
    PSYCH_THRILLER("tmdb_psych_thriller", "מתח ותעלומה", "Thrillers and mysteries", "Suspense e mistério", "Триллеры и загадки", "إثارة وغموض"),

    /** Horror films out in the next six months, most anticipated first. */
    COMING_SOON("tmdb_coming_soon", "בקרוב בקולנוע", "Coming soon to cinemas", "Em breve nos cinemas", "Скоро в кино", "قريبًا في السينما"),

    /** Out in the last two months. */
    JUST_RELEASED("tmdb_just_released", "יצאו עכשיו", "Just released", "Acabaram de sair", "Только что вышли", "صدرت للتو"),
    POPULAR_HORROR("tmdb_popular_horror", "האימה הכי פופולרית", "Most popular horror", "Terror mais popular", "Самые популярные ужасы", "أشهر أفلام الرعب"),
    NEW_THRILLERS("tmdb_new_thrillers", "מותחנים חדשים", "New thrillers", "Suspenses recentes", "Новые триллеры", "أفلام إثارة جديدة"),

    /** Well rated by those who saw them, and seen by few. */
    HIDDEN_GEMS("tmdb_hidden_gems", "פנינים נסתרות", "Hidden gems", "Joias escondidas", "Скрытые жемчужины", "جواهر خفية"),
    SEVENTIES("tmdb_70s", "אימת שנות ה־70", "Horror of the 70s", "Terror dos anos 70", "Ужасы 70-х", "رعب السبعينيات"),
    EIGHTIES("tmdb_80s", "אימת שנות ה־80", "Horror of the 80s", "Terror dos anos 80", "Ужасы 80-х", "رعب الثمانينيات"),
    NINETIES("tmdb_90s", "אימת שנות ה־90", "Horror of the 90s", "Terror dos anos 90", "Ужасы 90-х", "رعب التسعينيات"),
    TWO_THOUSANDS("tmdb_2000s", "אימת שנות ה־2000", "Horror of the 2000s", "Terror dos anos 2000", "Ужасы 2000-х", "رعب الألفينات"),
    TWENTY_TENS("tmdb_2010s", "אימת שנות ה־2010", "Horror of the 2010s", "Terror dos anos 2010", "Ужасы 2010-х", "رعب العقد الثاني من الألفية"),
}

/**
 * Library rows that go on, while the viewer scrolls, with TMDB's discover results when they have a TMDB key: the rows
 * whose subject TMDB's own filters say well (genres, original language, release dates, rating). Parameters are fixed
 * here, never taken from input. Rows whose subject needs words (witches, zombies…) go on from Cinemeta's catalogs.
 */
enum class TmdbCategory(val collectionId: String) {
    NEW_NIGHTMARES("new_nightmares"),
    GIALLO("giallo"),
    CLASSIC_MONSTERS("classic_monsters"),
    HORROR_COMEDY("horror_comedy"),
    SPANISH_HORROR("spanish_horror"),
    MAX_FEAR("max_fear"),
    ;

    companion object {
        fun of(collectionId: String): TmdbCategory? = entries.firstOrNull { it.collectionId == collectionId }
    }
}

/**
 * TMDB helpers. Responses are untrusted: everything is optional, image paths must look like TMDB file names,
 * and ids are numeric. The key itself never appears in anything this object returns except the auth value.
 */
object Tmdb {
    const val API_HOST = "api.themoviedb.org"
    const val API_BASE = "https://$API_HOST/3"
    private const val API = API_BASE
    private const val IMAGES = "https://image.tmdb.org/t/p"
    const val HORROR = 27
    const val THRILLER = 53
    const val MYSTERY = 9648
    const val COMEDY = 35

    private val json = Json { ignoreUnknownKeys = true }
    private val v3Key = Regex("^[0-9a-fA-F]{32}$")
    private val v4Token = Regex("^eyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+$")
    private val imagePath = Regex("^/[A-Za-z0-9_\\-]{5,64}\\.(jpg|jpeg|png|webp)$")
    private val imdbPattern = Regex("^tt\\d{7,8}$")
    private val isoDate = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val languageCode = Regex("^[a-z]{2,3}$")
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

    /** A film's details, of which only the original language is read (core Origin). */
    fun movieUrl(tmdbId: Long): String {
        require(tmdbId > 0) { "invalid TMDB id" }
        return "$API/movie/$tmdbId"
    }

    /** "de", "is": the film's original language from [movieUrl]'s answer; null when it is missing or not a language code. */
    fun parseOriginalLanguage(text: String): String? =
        (root(text)?.get("original_language") as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase()?.takeIf(languageCode::matches)

    /**
     * The id of the series of films a film belongs to ("Halloween Collection"), from [movieUrl]'s answer
     * (`belongs_to_collection`); null when the film stands alone.
     */
    fun parseCollectionId(text: String): Long? =
        ((root(text)?.get("belongs_to_collection") as? JsonObject)?.get("id") as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }

    /** A series of films ("collection" in TMDB's words), in [language]. */
    fun collectionUrl(collectionId: Long, language: Language): String {
        require(collectionId > 0) { "invalid collection id" }
        return "$API/collection/$collectionId?language=${language.tmdbTag}"
    }

    /**
     * The films of a series in the order they came out; films without a date (announced only) come last, and adult
     * titles are left out. Null when the answer is not a collection or holds fewer than two films.
     */
    fun parseCollection(text: String): TmdbFranchise? {
        val o = root(text) ?: return null
        val id = (o["id"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 } ?: return null
        val parts = (o["parts"] as? JsonArray).orEmpty().asSequence().take(MAX_FRANCHISE * 2)
            .mapNotNull { it as? JsonObject }
            .filterNot { (it["adult"] as? JsonPrimitive)?.booleanOrNull == true }
            .mapNotNull(::toResult)
            .distinctBy { it.tmdbId }
            .sortedWith(compareBy<TmdbResult> { it.releaseDate == null }.thenBy { it.releaseDate.orEmpty() }.thenBy { it.tmdbId })
            .take(MAX_FRANCHISE)
            .toList()
        if (parts.size < 2) return null
        return TmdbFranchise(id, o.text("name"), parts)
    }

    /**
     * The search for a person by name. Null when [name] is not a name SCARY would send: too short, too long, or with
     * control characters in it. The name is encoded into the address; nothing else of it is used.
     */
    fun personSearchUrl(name: String, language: Language): String? {
        val clean = name.trim()
        if (clean.length !in 2..MAX_PERSON_NAME || clean.any { it.isISOControl() }) return null
        return "$API/search/person?query=${java.net.URLEncoder.encode(clean, "UTF-8")}&include_adult=false&language=${language.tmdbTag}"
    }

    /**
     * The id of the person named [name] in a person search's answer: only a result whose name is the same (case,
     * accents and spacing aside) is taken, never the nearest one; among several, an actor before other crafts, then
     * the best known. Null when there is none: nothing is guessed.
     */
    fun parsePersonId(text: String, name: String): Long? {
        val wanted = personKey(name)
        if (wanted.isEmpty()) return null
        return (root(text)?.get("results") as? JsonArray).orEmpty().asSequence().take(MAX_PERSON_RESULTS)
            .mapNotNull { it as? JsonObject }
            .filterNot { (it["adult"] as? JsonPrimitive)?.booleanOrNull == true }
            .filter { personKey(it.text("name")) == wanted || personKey(it.text("original_name")) == wanted }
            .mapNotNull { o -> (o["id"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }?.let { id -> Triple(id, o.text("known_for_department") == "Acting", (o["popularity"] as? JsonPrimitive)?.doubleOrNull ?: 0.0) } }
            .sortedWith(compareByDescending<Triple<Long, Boolean, Double>> { it.second }.thenByDescending { it.third })
            .firstOrNull()?.first
    }

    private fun personKey(name: String): String = io.github.diamondrubinny.scary.core.catalog.Search.normalize(name)

    /** A person's films, in [language]. */
    fun personCreditsUrl(personId: Long, language: Language): String {
        require(personId > 0) { "invalid person id" }
        return "$API/person/$personId/movie_credits?language=${language.tmdbTag}"
    }

    /**
     * The films a person acted in, from [personCreditsUrl]'s answer: only what SCARY's shelves hold (horror, thriller,
     * mystery), never adult titles or films with no year, each once, the newest first, at most [MAX_PERSON_FILMS].
     */
    fun parsePersonCredits(text: String): List<TmdbResult> =
        (root(text)?.get("cast") as? JsonArray).orEmpty().asSequence().take(MAX_PERSON_CREDITS)
            .mapNotNull { it as? JsonObject }
            .filterNot { (it["adult"] as? JsonPrimitive)?.booleanOrNull == true }
            .mapNotNull(::toResult)
            .filter { it.year != null && isThemed(it) }
            .distinctBy { it.tmdbId }
            .sortedWith(compareByDescending<TmdbResult> { it.releaseDate.orEmpty() }.thenBy { it.tmdbId })
            .take(MAX_PERSON_FILMS)
            .toList()

    /** A film's cast and crew, in [language] (the roles' names). */
    fun creditsUrl(tmdbId: Long, language: Language): String {
        require(tmdbId > 0) { "invalid TMDB id" }
        return "$API/movie/$tmdbId/credits?language=${language.tmdbTag}"
    }

    /**
     * A film's actors from [creditsUrl]'s answer, in TMDB's billing order, each person once, at most [MAX_CAST]. Adult
     * entries and entries without an id or a usable name are left out. The photo is a TMDB file name joined to
     * TMDB's image host, or null.
     */
    fun parseCast(text: String): List<TmdbCastMember> =
        (root(text)?.get("cast") as? JsonArray).orEmpty().asSequence().take(MAX_CAST_READ)
            .mapNotNull { it as? JsonObject }
            .filterNot { (it["adult"] as? JsonPrimitive)?.booleanOrNull == true }
            .mapNotNull { o ->
                val id = (o["id"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 } ?: return@mapNotNull null
                val name = shownText(o.text("name")).takeIf { it.length in 2..MAX_PERSON_NAME } ?: return@mapNotNull null
                val order = (o["order"] as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 } ?: Long.MAX_VALUE
                order to TmdbCastMember(id, name, shownText(o.text("character")).take(MAX_PERSON_NAME), imageUrl(o.text("profile_path"), PROFILE_SIZE))
            }
            .sortedBy { it.first }
            .map { it.second }
            .distinctBy { it.personId }
            .take(MAX_CAST)
            .toList()

    /** A film's other titles (other countries' titles, re-release and working titles). */
    fun alternativeTitlesUrl(tmdbId: Long): String {
        require(tmdbId > 0) { "invalid TMDB id" }
        return "$API/movie/$tmdbId/alternative_titles"
    }

    /**
     * The titles of [alternativeTitlesUrl]'s answer: each once (case aside), 2 to [MAX_ALT_TITLE] characters, at most
     * [MAX_ALT_TITLES]. An answer that is not a film's titles gives none.
     */
    fun parseAlternativeTitles(text: String): List<String> =
        (root(text)?.get("titles") as? JsonArray).orEmpty().asSequence().take(MAX_ALT_TITLES * 4)
            .mapNotNull { (it as? JsonObject)?.text("title")?.let(::shownText) }
            .filter { it.length in 2..MAX_ALT_TITLE }
            .distinctBy { it.lowercase() }
            .take(MAX_ALT_TITLES)
            .toList()

    /** Text from TMDB that will be drawn or kept: no control characters, spaces made single. */
    private fun shownText(text: String): String =
        text.filterNot { it.isISOControl() }.replace(Regex("\\s+"), " ").trim()

    /** The most actors shown on a film's page. */
    const val MAX_CAST = 12
    private const val MAX_CAST_READ = 200

    /** The photo size asked for: the middle one of TMDB's documented profile sizes (w45, w185, h632). */
    private const val PROFILE_SIZE = "w185"

    /** The most other titles kept for one film, and the longest one kept. */
    const val MAX_ALT_TITLES = 40
    const val MAX_ALT_TITLE = 120

    const val MAX_PERSON_FILMS = 60
    private const val MAX_PERSON_NAME = 80
    private const val MAX_PERSON_RESULTS = 40
    private const val MAX_PERSON_CREDITS = 600

    /** The longest series shown: the longest horror series have about thirty films. */
    const val MAX_FRANCHISE = 40

    fun externalIdsUrl(tmdbId: Long): String {
        require(tmdbId > 0) { "invalid TMDB id" }
        return "$API/movie/$tmdbId/external_ids"
    }

    /** TMDB serves discover results 20 to a page, and no further than page 500. */
    const val MAX_PAGE = 500

    fun discoverUrl(row: TmdbRow, today: LocalDate, language: Language, page: Int = 1): String {
        require(page in 1..MAX_PAGE) { "invalid page" }
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
            TmdbRow.COMING_SOON -> listOf(
                "with_genres" to "$HORROR",
                "sort_by" to "popularity.desc",
                "primary_release_date.gte" to today.plusDays(1).toString(),
                "primary_release_date.lte" to today.plusMonths(6).toString(),
            )
            TmdbRow.JUST_RELEASED -> listOf(
                "with_genres" to "$HORROR",
                "sort_by" to "popularity.desc",
                "primary_release_date.gte" to today.minusDays(JUST_RELEASED_DAYS).toString(),
                "primary_release_date.lte" to today.toString(),
            )
            TmdbRow.POPULAR_HORROR -> listOf(
                "with_genres" to "$HORROR",
                "sort_by" to "popularity.desc",
                "vote_count.gte" to "200",
            )
            TmdbRow.NEW_THRILLERS -> listOf(
                "with_genres" to "$THRILLER",
                "sort_by" to "popularity.desc",
                "primary_release_date.gte" to today.minusYears(1).toString(),
                "primary_release_date.lte" to today.toString(),
                "vote_count.gte" to "40",
            )
            // Loved by those who saw them, seen by few: a high average over a modest number of votes.
            TmdbRow.HIDDEN_GEMS -> listOf(
                "with_genres" to "$HORROR",
                "sort_by" to "vote_average.desc",
                "vote_average.gte" to "6.8",
                "vote_count.gte" to "150",
                "vote_count.lte" to "1500",
            )
            TmdbRow.SEVENTIES -> decade(1970)
            TmdbRow.EIGHTIES -> decade(1980)
            TmdbRow.NINETIES -> decade(1990)
            TmdbRow.TWO_THOUSANDS -> decade(2000)
            TmdbRow.TWENTY_TENS -> decade(2010)
        } + listOf("include_adult" to "false", "language" to language.tmdbTag, "page" to page.toString())
        return "$API/discover/movie?" + params.joinToString("&") { (k, v) -> "$k=" + Links.urlEncode(v) }
    }

    private const val JUST_RELEASED_DAYS = 60L

    /** A decade's horror, the films most people know first. */
    private fun decade(from: Int): List<Pair<String, String>> = listOf(
        "with_genres" to "$HORROR",
        "sort_by" to "vote_count.desc",
        "primary_release_date.gte" to "$from-01-01",
        "primary_release_date.lte" to "${from + 9}-12-31",
        "vote_count.gte" to "50",
    )

    /** Page [page] of the discover results that go on the library row [category] (see TmdbCategory). */
    fun discoverUrl(category: TmdbCategory, today: LocalDate, language: Language, page: Int = 1): String {
        require(page in 1..MAX_PAGE) { "invalid page" }
        val params = when (category) {
            // The last twelve months, most talked about first: never a list that stops at last year's films.
            TmdbCategory.NEW_NIGHTMARES -> listOf(
                "with_genres" to "$HORROR",
                "sort_by" to "popularity.desc",
                "primary_release_date.gte" to today.minusYears(1).toString(),
                "primary_release_date.lte" to today.toString(),
                "vote_count.gte" to "10",
            )
            TmdbCategory.GIALLO -> listOf(
                "with_original_language" to "it",
                "with_genres" to "$HORROR|$THRILLER|$MYSTERY",
                "primary_release_date.lte" to "1985-12-31",
                "sort_by" to "vote_count.desc",
            )
            TmdbCategory.CLASSIC_MONSTERS -> listOf(
                "with_genres" to "$HORROR",
                "primary_release_date.lte" to "1965-12-31",
                "sort_by" to "vote_count.desc",
            )
            // Comma: horror and comedy both.
            TmdbCategory.HORROR_COMEDY -> listOf(
                "with_genres" to "$HORROR,$COMEDY",
                "sort_by" to "popularity.desc",
                "vote_count.gte" to "50",
            )
            TmdbCategory.SPANISH_HORROR -> listOf(
                "with_original_language" to "es",
                "with_genres" to "$HORROR",
                "sort_by" to "popularity.desc",
                "vote_count.gte" to "10",
            )
            TmdbCategory.MAX_FEAR -> listOf(
                "with_genres" to "$HORROR",
                "sort_by" to "vote_average.desc",
                "vote_count.gte" to "2000",
            )
        } + listOf("include_adult" to "false", "language" to language.tmdbTag, "page" to page.toString())
        return "$API/discover/movie?" + params.joinToString("&") { (k, v) -> "$k=" + Links.urlEncode(v) }
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
        releaseDate = result.releaseDate.orEmpty(),
        originalLanguage = result.originalLanguage,
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

    /** Horror, thriller or mystery: what SCARY's shelves hold. */
    fun isThemed(result: TmdbResult): Boolean = result.genreIds.any { it == HORROR || it == THRILLER || it == MYSTERY }

    /** IMDb to TMDB ids, as stored on the TV. Only well-formed pairs survive a read. */
    fun encodeIds(ids: Map<String, Long>): String =
        kotlinx.serialization.json.JsonObject(ids.mapValues { JsonPrimitive(it.value) }).toString()

    fun decodeIds(text: String): Map<String, Long> =
        root(text)?.mapNotNull { (imdb, value) ->
            val id = (value as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }
            if (id != null && imdbPattern.matches(imdb)) imdb to id else null
        }?.toMap().orEmpty()

    fun rowOf(row: TmdbRow, metas: List<CinemetaMeta>): OnlineRow =
        OnlineRow(row.id, row.titleHe, row.titleEn, metas, mapOf("pt" to row.titlePt, "ru" to row.titleRu, "ar" to row.titleAr))

    private fun root(text: String): JsonObject? =
        try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun toResult(o: JsonObject): TmdbResult? {
        val id = (o["id"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 } ?: return null
        // A collection's films are described with "name" in TMDB's API description and answer with "title": either is read.
        val title = o.text("title").ifBlank { o.text("name") }.ifBlank { return null }
        return TmdbResult(
            tmdbId = id,
            title = title,
            originalTitle = o.text("original_title").ifBlank { o.text("original_name") },
            overview = o.text("overview").take(1_500),
            year = o.text("release_date").take(4).toIntOrNull()?.takeIf { it in 1895..2100 },
            poster = (o["poster_path"] as? JsonPrimitive)?.contentOrNull,
            backdrop = (o["backdrop_path"] as? JsonPrimitive)?.contentOrNull,
            rating = (o["vote_average"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it in 0.0..10.0 },
            genreIds = (o["genre_ids"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }.orEmpty(),
            releaseDate = o.text("release_date").takeIf(isoDate::matches),
            originalLanguage = o.text("original_language").lowercase().takeIf(languageCode::matches).orEmpty(),
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
    /** Which shape of rows this cache holds; a cache of an older one is read as missing and fetched again at once. */
    val rowsVersion: Int = 1,
    /**
     * When the rows were last read: they are read again every few hours (a few dozen requests once the films' ids are
     * known), the library's translations ([fetchedAtMillis], one request per film) once a day. 0 before this was kept.
     */
    val rowsFetchedAtMillis: Long = 0,
) {
    fun isStale(nowMillis: Long, maxAgeMillis: Long = 24L * 60 * 60 * 1000): Boolean =
        rowsVersion < ROWS_VERSION || nowMillis - fetchedAtMillis !in 0..maxAgeMillis

    /** Whether the rows should be read again: old translations, or rows older than [ROWS_MAX_AGE_MS]. */
    fun needsRows(nowMillis: Long): Boolean =
        isStale(nowMillis) || nowMillis - (if (rowsFetchedAtMillis > 0) rowsFetchedAtMillis else fetchedAtMillis) !in 0..ROWS_MAX_AGE_MS

    fun encode(): String = cacheJson.encodeToString(serializer(), this)

    companion object {
        /**
         * 2: the rows' films carry their original language, and those in Arabic or Indian languages are left out;
         * 3: nine more rows, and three pages of each.
         */
        const val ROWS_VERSION = 3

        /** The rows are read again after this long: three times a day, as Cinemeta's. */
        const val ROWS_MAX_AGE_MS = 8L * 60 * 60 * 1000

        /** Pages of each row read at a refresh (20 films each); the row goes on from the next while the viewer scrolls. */
        const val ROW_PAGES = 3

        private val cacheJson = Json { ignoreUnknownKeys = true }

        fun decode(text: String): TmdbCache? =
            try {
                cacheJson.decodeFromString(serializer(), text)
            } catch (e: IllegalArgumentException) {
                null
            }
    }
}
