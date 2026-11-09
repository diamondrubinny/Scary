// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.player

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** A spot the viewer marked in a file, to come back to. */
data class Bookmark(val positionMs: Long, val createdAt: Long)

/**
 * What the viewer watched of one file in their Real-Debrid account: where they stopped, the spots they marked, and
 * when. Keyed by the file's key in the account (never its link, which carries a token); [name] is the file's name as
 * the account lists it, [movieId] the SCARY movie it was played for, if any.
 */
data class WatchEntry(
    val key: String,
    val name: String,
    val movieId: String?,
    /** Where to pick up; 0 when the file was finished or barely started. */
    val positionMs: Long,
    val durationMs: Long,
    val lastWatched: Long,
    /** Oldest first by position. */
    val bookmarks: List<Bookmark> = emptyList(),
) {
    /** How far in, 0..1, for the progress bar on its card. */
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /** Somewhere to come back to: a spot to resume from, or marked spots. */
    val worthContinuing: Boolean get() = positionMs > 0 || bookmarks.isNotEmpty()
}

/**
 * The watch history of the internal player, kept on the TV only: resume positions and bookmarks of the viewer's own
 * files. Pure logic; the app stores [encode]'s text and reads it back with [decode], which treats it as untrusted (an
 * entry that is off in any way is dropped).
 */
object WatchHistory {
    /** Bookmarks kept per file. */
    const val MAX_BOOKMARKS = 20

    /** Files remembered; the least recently watched are forgotten first. */
    const val MAX_ENTRIES = 50

    /** Past this share of the file, it counts as watched to the end, and the next start is from the beginning. */
    const val FINISHED_FRACTION = 0.92

    /** Less than this in, nothing to resume. */
    const val MIN_RESUME_MS = 60_000L

    /** A second bookmark this close to an existing one replaces nothing and adds nothing. */
    const val BOOKMARK_MERGE_MS = 5_000L

    /** Files shown under "Continue watching". */
    const val CONTINUE_LIMIT = 20

    private const val MAX_KEY_CHARS = 80
    private const val MAX_NAME_CHARS = 300
    private const val MAX_MOVIE_ID_CHARS = 80
    private const val MAX_POSITION_MS = 48L * 60 * 60 * 1000
    private val KEY = Regex("^(d:[A-Za-z0-9]{1,64}|t:[A-Za-z0-9]{1,64}:\\d{1,4})$")

    private val json = Json { ignoreUnknownKeys = true }

    /** Whether [key] is a file key as Real-Debrid's lists produce them ("d:<id>" or "t:<id>:<index>"). */
    fun isValidKey(key: String): Boolean = key.length <= MAX_KEY_CHARS && KEY.matches(key)

    /** The position to save: none past the credits ([FINISHED_FRACTION]) or before [MIN_RESUME_MS]. */
    fun resumePosition(positionMs: Long, durationMs: Long): Long = when {
        positionMs < MIN_RESUME_MS -> 0L
        durationMs > 0 && positionMs >= durationMs * FINISHED_FRACTION -> 0L
        else -> positionMs.coerceAtMost(MAX_POSITION_MS)
    }

    /** Records where the viewer is in [key] now; the entry moves to the front. */
    fun progress(
        entries: List<WatchEntry>,
        key: String,
        name: String,
        movieId: String?,
        positionMs: Long,
        durationMs: Long,
        now: Long,
    ): List<WatchEntry> {
        if (!isValidKey(key)) return entries
        val old = entries.firstOrNull { it.key == key }
        val entry = WatchEntry(
            key = key,
            name = cleanName(name).ifEmpty { old?.name.orEmpty() },
            movieId = movieId?.takeIf(::isValidMovieId) ?: old?.movieId,
            positionMs = resumePosition(positionMs, durationMs),
            durationMs = durationMs.coerceIn(0L, MAX_POSITION_MS).takeIf { it > 0 } ?: old?.durationMs ?: 0L,
            lastWatched = now,
            bookmarks = old?.bookmarks.orEmpty(),
        )
        return trim(listOf(entry) + entries.filter { it.key != key })
    }

    /**
     * Marks [positionMs] in [key]. One within [BOOKMARK_MERGE_MS] of an existing bookmark is the same spot; past
     * [MAX_BOOKMARKS], the oldest marked goes. The file needs an entry already (it is playing).
     */
    fun addBookmark(entries: List<WatchEntry>, key: String, positionMs: Long, now: Long): List<WatchEntry> {
        val entry = entries.firstOrNull { it.key == key } ?: return entries
        val at = positionMs.coerceIn(0L, MAX_POSITION_MS)
        if (entry.bookmarks.any { kotlin.math.abs(it.positionMs - at) < BOOKMARK_MERGE_MS }) return entries
        val kept = (entry.bookmarks + Bookmark(at, now))
            .sortedByDescending { it.createdAt }
            .take(MAX_BOOKMARKS)
            .sortedBy { it.positionMs }
        return entries.map { if (it.key == key) it.copy(bookmarks = kept, lastWatched = now) else it }
    }

    fun removeBookmark(entries: List<WatchEntry>, key: String, positionMs: Long): List<WatchEntry> =
        entries.map { entry -> if (entry.key == key) entry.copy(bookmarks = entry.bookmarks.filter { it.positionMs != positionMs }) else entry }

    /** Forgets [key] entirely (a file no longer in the account, or the viewer's choice). */
    fun forget(entries: List<WatchEntry>, key: String): List<WatchEntry> = entries.filter { it.key != key }

    /** "Continue watching": files with a spot to resume or bookmarks, the most recently watched first. */
    fun continueWatching(entries: List<WatchEntry>, limit: Int = CONTINUE_LIMIT): List<WatchEntry> =
        entries.filter { it.worthContinuing && it.name.isNotEmpty() }.sortedByDescending { it.lastWatched }.take(limit)

    /** The latest entry played for [movieId] that has somewhere to continue from. */
    fun forMovie(entries: List<WatchEntry>, movieId: String): WatchEntry? =
        entries.filter { it.movieId == movieId && it.worthContinuing }.maxByOrNull { it.lastWatched }

    /**
     * The file after [key] in the same torrent ("t:<id>:<index>" then "t:<id>:<index + 1>"), among [keys] (the account's
     * files, in any order). Downloads ("d:") have no next file.
     */
    fun nextInTorrent(key: String, keys: Collection<String>): String? {
        val parts = key.split(':')
        if (parts.size != 3 || parts[0] != "t") return null
        val index = parts[2].toIntOrNull() ?: return null
        return keys
            .mapNotNull { other ->
                val p = other.split(':')
                if (p.size == 3 && p[0] == "t" && p[1] == parts[1]) p[2].toIntOrNull()?.let { it to other } else null
            }
            .filter { (i, _) -> i > index }
            .minByOrNull { (i, _) -> i }
            ?.second
    }

    private fun trim(entries: List<WatchEntry>): List<WatchEntry> =
        if (entries.size <= MAX_ENTRIES) entries else entries.sortedByDescending { it.lastWatched }.take(MAX_ENTRIES)

    // Storage --------------------------------------------------------------------------------------------------------

    fun encode(entries: List<WatchEntry>): String = buildJsonArray {
        entries.forEach { entry ->
            add(
                buildJsonObject {
                    put("k", entry.key)
                    put("n", entry.name)
                    entry.movieId?.let { put("m", it) }
                    put("p", entry.positionMs)
                    put("d", entry.durationMs)
                    put("w", entry.lastWatched)
                    put(
                        "b",
                        buildJsonArray {
                            entry.bookmarks.forEach { b -> add(buildJsonObject { put("p", b.positionMs); put("c", b.createdAt) }) }
                        },
                    )
                },
            )
        }
    }.toString()

    /** Reads [encode]'s text, as untrusted: anything malformed is dropped, the limits are applied again. */
    fun decode(text: String?): List<WatchEntry> {
        if (text.isNullOrBlank()) return emptyList()
        val array = try {
            json.parseToJsonElement(text).jsonArray
        } catch (e: IllegalArgumentException) {
            return emptyList()
        }
        val entries = array.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val key = o.string("k")?.takeIf(::isValidKey) ?: return@mapNotNull null
            val bookmarks = (o["b"] as? JsonArray).orEmpty().mapNotNull { b ->
                val bo = b as? JsonObject ?: return@mapNotNull null
                val p = bo.long("p")?.takeIf { it in 0..MAX_POSITION_MS } ?: return@mapNotNull null
                Bookmark(p, bo.long("c")?.coerceAtLeast(0) ?: 0L)
            }.distinctBy { it.positionMs }.sortedByDescending { it.createdAt }.take(MAX_BOOKMARKS).sortedBy { it.positionMs }
            WatchEntry(
                key = key,
                name = cleanName(o.string("n").orEmpty()),
                movieId = o.string("m")?.takeIf(::isValidMovieId),
                positionMs = o.long("p")?.takeIf { it in 0..MAX_POSITION_MS } ?: 0L,
                durationMs = o.long("d")?.takeIf { it in 0..MAX_POSITION_MS } ?: 0L,
                lastWatched = o.long("w")?.coerceAtLeast(0) ?: 0L,
                bookmarks = bookmarks,
            )
        }.distinctBy { it.key }
        return trim(entries)
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

    private fun isValidMovieId(id: String): Boolean = id.isNotEmpty() && id.length <= MAX_MOVIE_ID_CHARS && id.all { it.isLetterOrDigit() || it == '_' || it == '-' }

    /** A file name as shown: no control or direction characters, not too long. */
    private fun cleanName(name: String): String =
        name.filter { !it.isISOControl() }
            .replace(Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069]"), "")
            .trim()
            .take(MAX_NAME_CHARS)
}
