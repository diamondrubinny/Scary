// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.stremio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.format.DateTimeParseException

/** A film or series in the viewer's Stremio library, as SCARY reads it (and the item as stored, to write it back). */
data class StremioItem(
    /** The IMDb id ("tt0081505"); other ids (channels, add-on catalogs) are left out. */
    val imdbId: String,
    val name: String,
    /** "movie" or "series". */
    val type: String,
    /** Taken out of the library (Stremio keeps such items, for their history). */
    val removed: Boolean,
    /** Played without being added to the library. */
    val temp: Boolean,
    /** Where the viewer stopped, and the length, in milliseconds (0 when unknown). */
    val offsetMs: Long,
    val durationMs: Long,
    /** Marked as watched, or played to the end at least once. */
    val watched: Boolean,
    /** Last played, ms since the epoch (0 when never). */
    val lastWatched: Long,
    /** Last changed, ms since the epoch. */
    val modified: Long,
    /** The item exactly as Stremio sent it: changes are made on a copy, so nothing SCARY does not know is lost. */
    val raw: JsonObject,
) {
    val isMovie: Boolean get() = type == Stremio.MOVIE

    /** In the library (not only played, not taken out). */
    val inLibrary: Boolean get() = !removed && !temp

    /** Under Stremio's own "Continue watching": stopped in the middle, in the library or only played. */
    val continuing: Boolean get() = offsetMs > 0 && (!removed || temp)
}

/**
 * The viewer's Stremio account, as Stremio's own apps reach it (stremio-core, MIT): signing in by link (a code shown on
 * the TV, approved on a phone; no password is typed on the TV), and the library (films and series, where the viewer
 * stopped, what was watched). Not a published API: every answer is read as untrusted, and anything off is dropped.
 *
 * SCARY never reads the account's add-ons and never asks them for streams: watching stays in Stremio.
 */
object Stremio {
    const val MOVIE = "movie"
    const val SERIES = "series"

    const val LINK_HOST = "link.stremio.com"
    const val API_HOST = "api.strem.io"
    const val LINK_CREATE_URL = "https://$LINK_HOST/api/v2/create?type=Create"
    private const val API = "https://$API_HOST/api/"
    const val GET_USER_URL = "${API}getUser"
    const val LOGOUT_URL = "${API}logout"
    const val DATASTORE_GET_URL = "${API}datastoreGet"
    const val DATASTORE_PUT_URL = "${API}datastorePut"
    private const val LIBRARY = "libraryItem"

    /** Items read from a library at most. */
    const val MAX_ITEMS = 2_000

    /** "Continue in Stremio" shows at most these. */
    const val CONTINUE_LIMIT = 20

    /** Stremio's answer for a key that is no longer valid (signed out elsewhere, or revoked). */
    const val ERROR_SESSION = 1

    private const val MAX_NAME_CHARS = 200
    private const val MAX_KEY_CHARS = 512
    private const val MAX_DURATION_MS = 48L * 60 * 60 * 1000
    private val IMDB = Regex("^tt\\d{5,10}$")
    private val CODE = Regex("^[A-Za-z0-9]{3,12}$")
    private val KEY = Regex("^[A-Za-z0-9._~+/=-]{8,$MAX_KEY_CHARS}$")

    private val json = Json { ignoreUnknownKeys = true }

    // Signing in by link ----------------------------------------------------------------------------------------------

    /** The address and the code to show: the viewer opens [link] (or scans its QR code) and approves. */
    data class LinkCode(val code: String, val link: String)

    fun linkReadUrl(code: String): String? = code.takeIf(CODE::matches)?.let { "https://$LINK_HOST/api/v2/read?type=Read&code=$it" }

    fun parseLinkCode(text: String): LinkCode? {
        val result = result(text) ?: return null
        val code = result.string("code")?.takeIf(CODE::matches) ?: return null
        val link = result.string("link")?.takeIf { isLinkPage(it) } ?: "https://$LINK_HOST/$code"
        return LinkCode(code, link)
    }

    /** The key once the viewer approved; null while waiting (Stremio answers with an error until then). */
    fun parseLinkKey(text: String): String? = result(text)?.string("authKey")?.takeIf(KEY::matches)

    /** Stremio's error code, if the answer is one. */
    fun errorCode(text: String): Int? =
        (root(text)?.get("error") as? JsonObject)?.let { (it["code"] as? JsonPrimitive)?.intOrNull }

    // Requests --------------------------------------------------------------------------------------------------------

    fun authBody(authKey: String): String = buildJsonObject { put("authKey", authKey) }.toString()

    fun libraryGetBody(authKey: String): String = buildJsonObject {
        put("authKey", authKey)
        put("collection", LIBRARY)
        put("ids", JsonArray(emptyList()))
        put("all", true)
    }.toString()

    fun libraryPutBody(authKey: String, changes: List<JsonObject>): String = buildJsonObject {
        put("authKey", authKey)
        put("collection", LIBRARY)
        put("changes", JsonArray(changes))
    }.toString()

    // The library -----------------------------------------------------------------------------------------------------

    /** The library's films and series by IMDb id; null when the answer is not a library at all. */
    fun parseLibrary(text: String): List<StremioItem>? {
        val root = root(text) ?: return null
        val array = root["result"] as? JsonArray ?: return null
        return array.asSequence().take(MAX_ITEMS * 2).mapNotNull { (it as? JsonObject)?.let(::parseItem) }
            .distinctBy { it.imdbId }
            .take(MAX_ITEMS)
            .toList()
    }

    private fun parseItem(o: JsonObject): StremioItem? {
        val id = o.string("_id")?.takeIf(IMDB::matches) ?: return null
        val type = o.string("type")?.takeIf { it == MOVIE || it == SERIES } ?: return null
        val state = o["state"] as? JsonObject ?: JsonObject(emptyMap())
        val duration = state.long("duration")?.takeIf { it in 0..MAX_DURATION_MS } ?: 0L
        val offset = state.long("timeOffset")?.takeIf { it in 0..MAX_DURATION_MS } ?: 0L
        val timesWatched = state.long("timesWatched")?.coerceAtLeast(0) ?: 0L
        val flagged = state.long("flaggedWatched")?.coerceAtLeast(0) ?: 0L
        return StremioItem(
            imdbId = id,
            name = cleanName(o.string("name").orEmpty()),
            type = type,
            removed = o.bool("removed") ?: false,
            temp = o.bool("temp") ?: false,
            offsetMs = if (duration > 0) offset.coerceAtMost(duration) else offset,
            durationMs = duration,
            watched = flagged > 0 || timesWatched > 0,
            lastWatched = time(state.string("lastWatched")),
            modified = time(o.string("_mtime")),
            raw = o,
        )
    }

    /** "Continue in Stremio": stopped in the middle, the most recently played first. */
    fun continueWatching(items: List<StremioItem>, limit: Int = CONTINUE_LIMIT): List<StremioItem> =
        items.filter { it.continuing }.sortedByDescending { maxOf(it.lastWatched, it.modified) }.take(limit)

    /** Films in the library (series stay out: SCARY's "My list" sync is of films, as with TMDB). */
    fun libraryFilms(items: List<StremioItem>): Set<String> = items.filter { it.isMovie && it.inLibrary }.mapTo(LinkedHashSet()) { it.imdbId }

    /** Films watched in Stremio. */
    fun watchedFilms(items: List<StremioItem>): Set<String> = items.filter { it.isMovie && it.watched }.mapTo(LinkedHashSet()) { it.imdbId }

    // Changes, written back on a copy of the item as Stremio sent it --------------------------------------------------

    /** Adds a film to the library: the item Stremio has, back in; or a new one. */
    fun added(existing: StremioItem?, imdbId: String, name: String, poster: String?, now: Instant): JsonObject? {
        if (!IMDB.matches(imdbId)) return null
        if (existing != null) return existing.raw.with("removed" to JsonPrimitive(false), "temp" to JsonPrimitive(false), "_mtime" to JsonPrimitive(iso(now)))
        return buildJsonObject {
            put("_id", imdbId)
            put("name", cleanName(name))
            put("type", MOVIE)
            if (poster != null && poster.startsWith("https://")) put("poster", poster) else put("poster", JsonNull)
            put("posterShape", "poster")
            put("removed", false)
            put("temp", false)
            put("_ctime", iso(now))
            put("_mtime", iso(now))
            put("state", emptyState())
            put("behaviorHints", buildJsonObject { put("defaultVideoId", imdbId) })
        }
    }

    /** Takes a film out of the library; Stremio keeps the item (and where the viewer stopped). */
    fun removed(existing: StremioItem, now: Instant): JsonObject =
        existing.raw.with("removed" to JsonPrimitive(true), "_mtime" to JsonPrimitive(iso(now)))

    /** Marks a film in the library as watched (as Stremio's own "Mark as watched" does). */
    fun markedWatched(existing: StremioItem, now: Instant): JsonObject {
        val state = (existing.raw["state"] as? JsonObject ?: emptyState())
        val times = (state.long("timesWatched") ?: 0L).coerceAtLeast(1)
        val newState = state.with(
            "flaggedWatched" to JsonPrimitive(1),
            "timesWatched" to JsonPrimitive(times),
            "lastWatched" to JsonPrimitive(iso(now)),
        )
        return existing.raw.with("state" to newState, "_mtime" to JsonPrimitive(iso(now)))
    }

    private fun emptyState(): JsonObject = buildJsonObject {
        put("lastWatched", JsonNull)
        put("timeWatched", 0)
        put("timeOffset", 0)
        put("overallTimeWatched", 0)
        put("timesWatched", 0)
        put("flaggedWatched", 0)
        put("duration", 0)
        put("video_id", JsonNull)
        put("watched", JsonNull)
        put("noNotif", false)
    }

    // Helpers ---------------------------------------------------------------------------------------------------------

    /** Whether [url] is Stremio's link page (shown as a QR code): HTTPS, its own host, no user name, no odd port. */
    fun isLinkPage(url: String): Boolean {
        val uri = try {
            java.net.URI(url)
        } catch (e: java.net.URISyntaxException) {
            return false
        }
        return uri.scheme == "https" && uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443) && uri.host?.lowercase() == LINK_HOST
    }

    fun iso(instant: Instant): String = instant.toString()

    private fun time(text: String?): Long = try {
        text?.let { Instant.parse(it).toEpochMilli() } ?: 0L
    } catch (e: DateTimeParseException) {
        0L
    }

    private fun root(text: String): JsonObject? = try {
        json.parseToJsonElement(text) as? JsonObject
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun result(text: String): JsonObject? = root(text)?.get("result") as? JsonObject

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

    private fun JsonObject.with(vararg changes: Pair<String, JsonElement>): JsonObject = JsonObject(this + changes)

    /** A name as shown: no control or direction characters, not too long. */
    private fun cleanName(name: String): String =
        name.filter { !it.isISOControl() }
            .replace(Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069]"), "")
            .trim()
            .take(MAX_NAME_CHARS)
}
