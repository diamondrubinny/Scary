// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.tmdb

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Signing in to the viewer's TMDB account, and its lists (TMDB API v3, developer.themoviedb.org, checked 5 Nov 2026):
 * a request token, approved by the viewer on themoviedb.org (from their phone, by a QR code), becomes a session id;
 * with it SCARY reads and changes the account's watchlist and favourites and reads its rated films. The session id is
 * a password: it is stored encrypted on the TV and sent only to api.themoviedb.org.
 */
object TmdbAccount {
    private const val API = Tmdb.API_BASE
    private val json = Json { ignoreUnknownKeys = true }
    private val token = Regex("^[0-9a-fA-F]{20,64}$")

    /** The lists SCARY keeps in step with: watchlist and My list, favourites and "Loved it", rated films and Watched. */
    enum class AccountList(val path: String) { WATCHLIST("watchlist/movies"), FAVORITES("favorite/movies"), RATED("rated/movies") }

    /** GET: "This token will auto expire after 60 minutes if it's not used." */
    fun requestTokenUrl(): String = "$API/authentication/token/new"

    /** Where the viewer approves the token, signed in to TMDB. */
    fun approveUrl(requestToken: String): String {
        require(token.matches(requestToken)) { "invalid request token" }
        return "https://www.themoviedb.org/authenticate/$requestToken"
    }

    /** POST, body [sessionBody]: a session for an approved token. */
    fun createSessionUrl(): String = "$API/authentication/session/new"

    fun sessionBody(requestToken: String): String = buildJsonObject { put("request_token", requestToken) }.toString()

    /** DELETE, body [deleteSessionBody]: signs the TV out of the account. */
    fun deleteSessionUrl(): String = "$API/authentication/session"

    fun deleteSessionBody(sessionId: String): String = buildJsonObject { put("session_id", sessionId) }.toString()

    /**
     * The account's details. The documented path is /3/account/{account_id}, and the id is what they give: so the
     * first call, without an id yet, is to /3/account, with [accountUrl] (id 0) to try next.
     */
    fun accountUrl(sessionId: String, accountId: Long? = null): String =
        if (accountId == null) "$API/account?session_id=${enc(sessionId)}" else "$API/account/$accountId?session_id=${enc(sessionId)}"

    /** GET a page of one of the account's film lists, oldest first so new pages only add at the end. */
    fun listUrl(accountId: Long, sessionId: String, list: AccountList, page: Int): String {
        require(page in 1..MAX_PAGE) { "invalid page" }
        return "$API/account/$accountId/${list.path}?session_id=${enc(sessionId)}&page=$page&sort_by=created_at.asc"
    }

    /** POST, body [watchlistBody] or [favoriteBody]. */
    fun watchlistUrl(accountId: Long, sessionId: String): String = "$API/account/$accountId/watchlist?session_id=${enc(sessionId)}"

    fun favoriteUrl(accountId: Long, sessionId: String): String = "$API/account/$accountId/favorite?session_id=${enc(sessionId)}"

    fun watchlistBody(tmdbId: Long, add: Boolean): String = listBody(tmdbId, "watchlist", add)

    fun favoriteBody(tmdbId: Long, add: Boolean): String = listBody(tmdbId, "favorite", add)

    private fun listBody(tmdbId: Long, field: String, add: Boolean): String {
        require(tmdbId > 0) { "invalid TMDB id" }
        return buildJsonObject {
            put("media_type", "movie")
            put("media_id", tmdbId)
            put(field, add)
        }.toString()
    }

    /** A new request token, or null when TMDB says no. */
    fun parseRequestToken(text: String): String? =
        root(text)?.takeIf { it.bool("success") != false }?.string("request_token")?.takeIf(token::matches)

    fun parseSession(text: String): String? =
        root(text)?.takeIf { it.bool("success") == true }?.string("session_id")?.takeIf(token::matches)

    data class Account(val id: Long, val username: String)

    fun parseAccount(text: String): Account? {
        val o = root(text) ?: return null
        val id = (o["id"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 } ?: return null
        return Account(id, o.string("username").orEmpty().take(MAX_USERNAME))
    }

    /** One page of a list: TMDB ids, and the page count. */
    data class ListPage(val ids: List<Long>, val films: List<TmdbResult>, val totalPages: Int)

    fun parseListPage(text: String): ListPage? {
        val o = root(text) ?: return null
        val films = Tmdb.parseDiscover(text)
        val pages = (o["total_pages"] as? JsonPrimitive)?.intOrNull?.coerceIn(0, MAX_PAGE) ?: return null
        return ListPage(films.map { it.tmdbId }, films, pages)
    }

    /** A change to a list worked: TMDB answers status_code 1 (added), 12 (updated) or 13 (removed). */
    fun parseChanged(text: String): Boolean =
        (root(text)?.get("status_code") as? JsonPrimitive)?.intOrNull in setOf(1, 12, 13)

    private fun root(text: String): JsonObject? =
        try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    /** A session id goes into URLs as it is: so it must be one, hex like TMDB's, and nothing that could bend the URL. */
    private fun enc(sessionId: String): String {
        require(token.matches(sessionId)) { "invalid session id" }
        return sessionId
    }

    /** Pages read per list and sync: 20 films a page, so up to 500 films each. */
    const val MAX_PAGE = 25
    private const val MAX_USERNAME = 64
}

/**
 * Keeps a list on the TV and the same list on TMDB in step, both ways, by comparing each with how they stood after
 * the last sync ([lastSynced]): what one side added or removed since then is done on the other. Before the first
 * sync nothing is removed anywhere: both lists end up holding everything from both. Ids are IMDb ids.
 */
object ListSync {
    data class Plan(
        /** The TV's list after the sync, newest first. */
        val local: List<String>,
        /** To add on TMDB, and to remove there. */
        val addRemote: Set<String>,
        val removeRemote: Set<String>,
        /** Both sides agree on this once every change on TMDB is made: the [lastSynced] of the next sync. */
        val synced: Set<String>,
    ) {
        /**
         * What both sides agree on when some changes on TMDB failed: an add that failed is left out, so the next sync
         * tries it again instead of reading it as removed on TMDB; a removal that failed stays in, to be tried again.
         */
        fun syncedAfter(failedAdds: Set<String>, failedRemoves: Set<String>): Set<String> = synced - failedAdds + failedRemoves
    }

    /**
     * The TV's list (SCARY's ids, newest first) after a sync agreed on [agreed] (IMDb ids): the list as it was, less
     * the films removed on TMDB, with the films added there on top. Entries the sync does not cover ([imdbOf] null:
     * series, or ids that do not map) keep their places; films added from TMDB need a SCARY id ([scaryIdOf]).
     */
    fun applyTo(
        original: List<String>,
        synced: Set<String>,
        agreed: List<String>,
        imdbOf: (String) -> String?,
        scaryIdOf: (String) -> String?,
    ): List<String> {
        val removed = synced - agreed.toSet()
        val added = agreed.filterNot(synced::contains).mapNotNull(scaryIdOf)
        return (added + original.filterNot { id -> imdbOf(id)?.let(removed::contains) == true }).distinct()
    }

    /** [local] newest first; [remote] as TMDB has it now; [addOnly] for a list SCARY only reads (rated films). */
    fun plan(local: List<String>, remote: Set<String>, lastSynced: Set<String>?, addOnly: Boolean = false): Plan {
        val localSet = local.toSet()
        if (addOnly) {
            val added = remote.filterNot(localSet::contains)
            return Plan(added + local, emptySet(), emptySet(), remote)
        }
        if (lastSynced == null) {
            val fromRemote = remote.filterNot(localSet::contains)
            return Plan(fromRemote + local, localSet - remote, emptySet(), localSet + remote)
        }
        val removedRemotely = lastSynced - remote
        val addedRemotely = remote - lastSynced
        val removedLocally = lastSynced - localSet
        val addedLocally = localSet - lastSynced
        val kept = local.filterNot(removedRemotely::contains)
        val newLocal = addedRemotely.filterNot { it in localSet || it in removedLocally } + kept
        return Plan(
            local = newLocal,
            addRemote = addedLocally - remote,
            removeRemote = removedLocally intersect remote - addedRemotely,
            synced = newLocal.toSet(),
        )
    }
}
