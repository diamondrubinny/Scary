// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.tmdb

import io.github.diamondrubinny.scary.core.catalog.Search
import io.github.diamondrubinny.scary.core.model.Language
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.URLEncoder

/** One actor of a film as TMDB lists them: who they play, and their photo's path when there is one. */
data class CastMember(val id: Long, val name: String, val character: String, val photoPath: String?) {
    /** The photo's address on TMDB's image host, or null: the name's first letter stands in for it then. */
    val photoUrl: String? get() = Tmdb.imageUrl(photoPath, TmdbPeople.PHOTO_SIZE)
}

/** A company that made a film, as TMDB names it. */
data class ProductionCompany(val id: Long, val name: String)

/** A horror studio on the home screen: [label] is shown, [query] is the company's own name, looked up at TMDB. */
data class Studio(val label: String, val query: String)

/**
 * A film's actors with their photos, the companies that made it, and a company's horror films
 * (docs/TMDB_PEOPLE_STUDIOS_PLAN.md), with the viewer's own TMDB key like everything else from TMDB. The requests are
 * the ones TMDB documents (developer.themoviedb.org, read 25 Nov 2026): movie credits, movie details, discover with
 * "with_companies", and the company search. Nothing the viewer typed is sent: a film's id, a company's id, or a
 * studio's name from the fixed list below.
 *
 * Every answer is read as untrusted: an item of the wrong shape is dropped, a name is cut to a sane length, and a
 * photo's path is kept only in the shape TMDB's image host uses, so no address from an answer is ever opened as it is.
 */
object TmdbPeople {
    /** TMDB's profile sizes are w45, w185 and h632 (its configuration); a round card on a TV wants the middle one. */
    const val PHOTO_SIZE = "w185"
    const val MAX_CAST = 12
    const val MAX_COMPANIES = 4
    private const val MAX_NAME = 80
    private const val MAX_ITEMS = 200
    private const val MAX_SEARCH_RESULTS = 40

    /** A studio's id is asked for again after this long: TMDB's terms allow keeping its data six months at most. */
    const val STUDIO_ID_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Studios known for horror. Their TMDB ids are not written here: each is looked up by name once
     * ([companySearchUrl]) and only an exact match is taken, so a wrong id cannot be shipped; one that is not found is
     * simply not shown.
     */
    val STUDIOS: List<Studio> = listOf(
        Studio("Blumhouse", "Blumhouse Productions"),
        Studio("A24", "A24"),
        Studio("Hammer", "Hammer Film Productions"),
        Studio("Universal", "Universal Pictures"),
        Studio("New Line", "New Line Cinema"),
        Studio("Dimension", "Dimension Films"),
        Studio("Ghost House", "Ghost House Pictures"),
        Studio("Atomic Monster", "Atomic Monster"),
        Studio("Platinum Dunes", "Platinum Dunes"),
        Studio("Lionsgate", "Lionsgate"),
    )

    // Requests ----------------------------------------------------------------------------------------------------------

    fun creditsUrl(tmdbId: Long): String {
        require(tmdbId > 0) { "invalid TMDB id" }
        return "${Tmdb.API_BASE}/movie/$tmdbId/credits"
    }

    /** A company's horror films, the best known first; TMDB serves 20 to a page. */
    fun companyFilmsUrl(companyId: Long, language: Language, page: Int = 1): String {
        require(companyId > 0) { "invalid company id" }
        require(page in 1..Tmdb.MAX_PAGE) { "invalid page" }
        return "${Tmdb.API_BASE}/discover/movie?with_companies=$companyId&with_genres=${Tmdb.HORROR}&sort_by=popularity.desc" +
            "&include_adult=false&language=${language.tmdbTag}&page=$page"
    }

    /** The search for a company by its own name; only names from [STUDIOS] are ever sent. */
    fun companySearchUrl(studio: Studio): String = "${Tmdb.API_BASE}/search/company?query=${URLEncoder.encode(studio.query, "UTF-8")}"

    // Answers -----------------------------------------------------------------------------------------------------------

    /**
     * The actors from [creditsUrl]'s answer, in TMDB's own order (the leads first), each once, at most [MAX_CAST].
     * Adult-flagged entries and entries without a name are left out.
     */
    fun parseCast(text: String): List<CastMember> =
        (root(text)?.get("cast") as? JsonArray).orEmpty().asSequence().take(MAX_ITEMS)
            .mapNotNull { it as? JsonObject }
            .filterNot { (it["adult"] as? JsonPrimitive)?.booleanOrNull == true }
            .mapNotNull { o ->
                val id = o.id() ?: return@mapNotNull null
                val name = o.text("name").ifBlank { return@mapNotNull null }
                val order = (o["order"] as? JsonPrimitive)?.longOrNull ?: Long.MAX_VALUE
                // Kept only in the shape TMDB's image host uses; anything else means "no photo".
                val photo = (o["profile_path"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { Tmdb.imageUrl(it, PHOTO_SIZE) != null }
                order to CastMember(id, name, o.text("character"), photo)
            }
            .sortedBy { it.first }
            .map { it.second }
            .distinctBy { it.id }
            .take(MAX_CAST)
            .toList()

    /** The companies from a film's details (Tmdb.movieUrl), in TMDB's order, each once, at most [MAX_COMPANIES]. */
    fun parseCompanies(text: String): List<ProductionCompany> =
        (root(text)?.get("production_companies") as? JsonArray).orEmpty().asSequence().take(MAX_ITEMS)
            .mapNotNull { it as? JsonObject }
            .mapNotNull { o -> o.id()?.let { id -> o.text("name").takeIf { it.isNotBlank() }?.let { ProductionCompany(id, it) } } }
            .distinctBy { it.id }
            .take(MAX_COMPANIES)
            .toList()

    /**
     * The id of [studio] in a company search's answer: only a company whose name is the same (case, accents and
     * spacing aside), never the nearest one; among several of that name, one with a logo first, as the established
     * company has one. Null when there is none: nothing is guessed.
     */
    fun parseCompanyId(text: String, studio: Studio): Long? {
        val wanted = Search.normalize(studio.query)
        if (wanted.isEmpty()) return null
        return (root(text)?.get("results") as? JsonArray).orEmpty().asSequence().take(MAX_SEARCH_RESULTS)
            .mapNotNull { it as? JsonObject }
            .filter { Search.normalize(it.text("name")) == wanted }
            .mapNotNull { o -> o.id()?.let { id -> id to ((o["logo_path"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.isNotBlank() == true) } }
            .sortedByDescending { it.second }
            .firstOrNull()?.first
    }

    // The studios' ids, kept on the TV ----------------------------------------------------------------------------------

    /** A studio's id and when it was found. */
    data class KnownStudio(val id: Long, val foundAtMillis: Long) {
        fun isFresh(nowMillis: Long): Boolean = nowMillis - foundAtMillis in 0 until STUDIO_ID_MAX_AGE_MS
    }

    fun encodeStudios(known: Map<String, KnownStudio>): String = buildJsonObject {
        known.forEach { (query, studio) -> put(query, "${studio.id}:${studio.foundAtMillis}") }
    }.toString()

    /** Only studios still in [STUDIOS] are read back; a damaged entry is dropped. */
    fun decodeStudios(text: String): Map<String, KnownStudio> {
        val names = STUDIOS.mapTo(HashSet()) { it.query }
        return root(text)?.entries.orEmpty().mapNotNull { (query, value) ->
            if (query !in names) return@mapNotNull null
            val parts = (value as? JsonPrimitive)?.contentOrNull?.split(':') ?: return@mapNotNull null
            val id = parts.getOrNull(0)?.toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val at = parts.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            query to KnownStudio(id, at)
        }.toMap()
    }

    // JSON --------------------------------------------------------------------------------------------------------------

    private fun root(text: String): JsonObject? = try {
        json.parseToJsonElement(text).jsonObject
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun JsonObject.id(): Long? = (this["id"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.takeIf { it > 0 }

    private fun JsonObject.text(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.filterNot(Char::isISOControl)?.trim()?.take(MAX_NAME).orEmpty()
}
