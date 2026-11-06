// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.model.Catalog
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.MediaKind
import io.github.diamondrubinny.scary.core.tmdb.ListSync
import io.github.diamondrubinny.scary.core.tmdb.Tmdb
import io.github.diamondrubinny.scary.core.tmdb.TmdbAccount
import io.github.diamondrubinny.scary.core.tmdb.TmdbAccount.AccountList
import io.github.diamondrubinny.scary.core.tmdb.TmdbResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * The viewer's own TMDB account, only if they sign in: SCARY keeps My list, "Loved it" and Watched in step with the
 * account's watchlist, favourites and rated films (docs/SMART_SORT_PLAN.md). Signing in is TMDB's own: the TV shows a
 * QR code to TMDB's approval page, the viewer approves on their phone, and the TV gets a session id. The session id is
 * a password: stored encrypted (SecretStore, Android Keystore), sent only to api.themoviedb.org, never logged.
 */
class TmdbAccountRepository(
    context: Context,
    private val secrets: SecretStore,
    private val tmdb: TmdbRepository,
    private val settings: SettingsRepository,
    private val savedMovies: SavedMoviesRepository,
) {
    sealed interface State {
        /** No TMDB key: the account needs one first. */
        data object NoKey : State

        data object SignedOut : State

        /** Waiting for the viewer to approve on TMDB, at [approveUrl] (the QR code). */
        data class Waiting(val approveUrl: String) : State

        data class SignedIn(val username: String, val lastSyncMillis: Long?, val syncing: Boolean, val lastResult: SyncResult?) : State

        data class Failed(val reason: Reason) : State
    }

    enum class Reason { UNREACHABLE, EXPIRED, NO_ACCOUNT, STORAGE, SESSION_ENDED }

    /** What a sync changed on the TV, and whether all of it worked. */
    data class SyncResult(val addedToMyList: Int, val addedToLoved: Int, val addedToWatched: Int, val sentToTmdb: Int, val complete: Boolean)

    private data class Credentials(val sessionId: String, val accountId: Long, val username: String)

    /** How each list stood after the last sync, by IMDb id ([ListSync]'s lastSynced). */
    private data class SyncState(val lists: Map<String, List<String>> = emptyMap())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateFile = File(context.filesDir, "tmdb_account_sync.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val syncMutex = Mutex()

    private val _state = MutableStateFlow<State>(State.SignedOut)
    val state: StateFlow<State> = _state.asStateFlow()

    private var credentials: Credentials? = null
    private var loginJob: Job? = null
    private var lastSyncElapsed = 0L

    /** Reads the stored session once; called off the main thread at start-up, after the key. */
    suspend fun load() = withContext(Dispatchers.IO) {
        credentials = secrets.read(SECRET_NAME)?.let(::decode)
        publishIdle()
    }

    private fun publishIdle() {
        val signedIn = credentials
        _state.value = when {
            !tmdb.hasKey -> State.NoKey
            signedIn != null -> State.SignedIn(signedIn.username, (state.value as? State.SignedIn)?.lastSyncMillis, false, null)
            else -> State.SignedOut
        }
    }

    /** The key changed (added or deleted): the account shows what is possible now. */
    fun onKeyChanged() {
        if (!tmdb.hasKey) loginJob?.cancel()
        if (state.value !is State.Waiting) publishIdle()
    }

    /**
     * Starts signing in: a request token from TMDB, shown as a QR code to its approval page. Then the TV asks every
     * [POLL_MS] whether the viewer has approved, until they have or the token runs out ([TOKEN_LIFETIME_MS]).
     * [onSignedIn] runs once the account is connected (a first sync).
     */
    fun startSignIn(onSignedIn: () -> Unit) {
        loginJob?.cancel()
        loginJob = scope.launch {
            if (!tmdb.hasKey) {
                _state.value = State.NoKey
                return@launch
            }
            val tokenAnswer = tmdb.call("GET", TmdbAccount.requestTokenUrl())
            val token = tokenAnswer?.body?.takeIf { tokenAnswer.ok }?.let(TmdbAccount::parseRequestToken)
            if (token == null) {
                _state.value = State.Failed(Reason.UNREACHABLE)
                return@launch
            }
            _state.value = State.Waiting(TmdbAccount.approveUrl(token))
            val deadline = SystemClock.elapsedRealtime() + TOKEN_LIFETIME_MS
            while (isActive && SystemClock.elapsedRealtime() < deadline) {
                delay(POLL_MS)
                // Before the viewer approves, TMDB answers with an error: keep asking.
                val answer = tmdb.call("POST", TmdbAccount.createSessionUrl(), TmdbAccount.sessionBody(token)) ?: continue
                val sessionId = answer.body?.takeIf { answer.ok }?.let(TmdbAccount::parseSession) ?: continue
                val account = account(sessionId)
                if (account == null) {
                    _state.value = State.Failed(Reason.NO_ACCOUNT)
                    return@launch
                }
                val signedIn = Credentials(sessionId, account.id, account.username)
                if (!secrets.write(SECRET_NAME, encode(signedIn))) {
                    _state.value = State.Failed(Reason.STORAGE)
                    return@launch
                }
                credentials = signedIn
                stateFile.delete()
                _state.value = State.SignedIn(account.username, null, false, null)
                onSignedIn()
                return@launch
            }
            if (isActive) _state.value = State.Failed(Reason.EXPIRED)
        }
    }

    /** The account's id: /3/account first (no id yet), then the documented /3/account/{id} with 0. */
    private suspend fun account(sessionId: String): TmdbAccount.Account? {
        for (url in listOf(TmdbAccount.accountUrl(sessionId), TmdbAccount.accountUrl(sessionId, 0))) {
            val answer = tmdb.call("GET", url) ?: continue
            answer.body?.takeIf { answer.ok }?.let(TmdbAccount::parseAccount)?.let { return it }
        }
        return null
    }

    fun cancelSignIn() {
        loginJob?.cancel()
        loginJob = null
        publishIdle()
    }

    /**
     * Signs the TV out: the session is deleted on TMDB (if it can be reached) and on the TV, with what the sync
     * remembered. The lists on the TV stay as they are.
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        loginJob?.cancel()
        val signedIn = credentials
        credentials = null
        secrets.remove(SECRET_NAME)
        stateFile.delete()
        publishIdle()
        if (signedIn != null) {
            tmdb.call("DELETE", TmdbAccount.deleteSessionUrl(), TmdbAccount.deleteSessionBody(signedIn.sessionId))
        }
    }

    /**
     * Keeps the lists in step with the account, unless one ran less than [minGapMs] ago. [catalog] is the full catalog
     * (no family-night filter), to turn SCARY's ids into IMDb ids and back. Null when not signed in, or too soon.
     */
    suspend fun sync(catalog: Catalog, language: Language, minGapMs: Long = 0): SyncResult? = syncMutex.withLock {
        val signedIn = credentials ?: return@withLock null
        if (minGapMs > 0 && lastSyncElapsed != 0L && SystemClock.elapsedRealtime() - lastSyncElapsed < minGapMs) return@withLock null
        lastSyncElapsed = SystemClock.elapsedRealtime()
        _state.value = State.SignedIn(signedIn.username, (state.value as? State.SignedIn)?.lastSyncMillis, true, null)
        val result = withContext(Dispatchers.IO) { runSync(signedIn, catalog, language) }
        if (credentials == null) return@withLock null
        if (result == null) {
            // Ended on TMDB (signed out there, or revoked): the TV forgets the session and says so.
            Log.i(TAG, "TMDB session ended")
            credentials = null
            secrets.remove(SECRET_NAME)
            stateFile.delete()
            _state.value = State.Failed(Reason.SESSION_ENDED)
        } else {
            _state.value = State.SignedIn(signedIn.username, System.currentTimeMillis(), false, result)
        }
        result
    }

    /** Null when TMDB says the session is no longer valid (signed out on the website, or revoked). */
    private suspend fun runSync(signedIn: Credentials, catalog: Catalog, language: Language): SyncResult? {
        val prefs = settings.prefs.first()
        val byImdb = catalog.movies.filter { it.kind == MediaKind.MOVIE }.associateBy { it.imdbId }
        val catalogImdb = catalog.movies.associate { it.id to it.imdbId }
        val series = catalog.movies.filter { it.kind == MediaKind.SERIES }.mapTo(HashSet()) { it.id }
        // A film from the online rows keeps its IMDb id in its own id, so it maps even when its details are gone from
        // the TV: a film that does not map would look removed here, and be removed from the account. Series are not
        // synced (the lists here are of films) and keep their places.
        fun imdbOf(id: String): String? =
            if (id in series) null else catalogImdb[id] ?: id.removePrefix(Cinemeta.ID_PREFIX).takeIf { it != id && it.startsWith("tt") }
        val synced = readState()
        var complete = true
        var sent = 0
        val newLists = HashMap<AccountList, List<String>>()
        val remoteFilms = HashMap<String, TmdbResult>()
        val plans = HashMap<AccountList, ListSync.Plan>()
        val failedAdds = HashMap<AccountList, MutableSet<String>>()
        val failedRemoves = HashMap<AccountList, MutableSet<String>>()
        val localImdbs = HashMap<AccountList, Set<String>>()

        for (list in AccountList.entries) {
            val remote = remoteList(signedIn, list)
            if (remote == null) {
                // Refused: the session was ended on TMDB. Otherwise only this list could not be read this time.
                if (sessionEnded) return null
                complete = false
                continue
            }
            remote.forEach { (imdb, film) -> remoteFilms.putIfAbsent(imdb, film) }
            val localIds = when (list) {
                AccountList.WATCHLIST -> prefs.myListIds
                AccountList.FAVORITES -> prefs.lovedIds
                AccountList.RATED -> prefs.watchedIds
            }
            val localImdb = localIds.mapNotNull(::imdbOf).distinct()
            val plan = ListSync.plan(localImdb, remote.keys, synced.lists[list.name]?.toSet(), addOnly = list == AccountList.RATED)
            plans[list] = plan
            val adds = HashSet<String>()
            val removes = HashSet<String>()
            for (imdb in plan.addRemote) if (change(signedIn, list, imdb, add = true)) sent++ else adds += imdb
            for (imdb in plan.removeRemote) if (change(signedIn, list, imdb, add = false)) sent++ else removes += imdb
            failedAdds[list] = adds
            failedRemoves[list] = removes
            if (adds.isNotEmpty() || removes.isNotEmpty()) complete = false
            newLists[list] = plan.local
            localImdbs[list] = localImdb.toSet()
        }

        // Films from TMDB that SCARY does not know yet: kept like films from the online rows, so they show.
        val unknown = newLists.values.flatten().distinct().filter { byImdb[it] == null }.mapNotNull { imdb ->
            remoteFilms[imdb]?.takeIf { Tmdb.isThemed(it) }?.let { Tmdb.toMeta(it, imdb, language) }
        }
        savedMovies.saveAll(unknown, keep = unknown.mapTo(HashSet()) { it.imdbId } + prefs.myListIds.mapNotNull(::imdbOf))
        val newIds = unknown.mapNotNull { meta -> Cinemeta.toMovie(meta)?.let { meta.imdbId to it.id } }.toMap()
        val newImdb = newIds.entries.associate { (imdb, id) -> id to imdb }
        fun scaryId(imdb: String): String? = byImdb[imdb]?.id ?: newIds[imdb]

        /**
         * The TV's list after the sync: the list as it was, less the films removed on TMDB, and the films added there
         * on top. Everything else (series, films that do not map) keeps its place.
         */
        fun merged(original: List<String>, list: AccountList): List<String>? {
            val agreed = newLists[list] ?: return null
            return ListSync.applyTo(original, localImdbs.getValue(list), agreed, ::imdbOf, ::scaryId)
        }

        val before = Triple(prefs.myListIds.size, prefs.lovedIds.size, prefs.watchedIds.size)
        val stored = settings.applySync(
            myList = merged(prefs.myListIds, AccountList.WATCHLIST),
            loved = merged(prefs.lovedIds, AccountList.FAVORITES),
            watchedAdded = newLists[AccountList.RATED]?.filterNot { it in localImdbs.getValue(AccountList.RATED) }?.mapNotNull(::scaryId).orEmpty(),
        )
        val storedImdb = mapOf(
            AccountList.WATCHLIST to stored.myList,
            AccountList.FAVORITES to stored.loved,
            AccountList.RATED to stored.watched,
        ).mapValues { (_, ids) -> ids.mapNotNull { id -> imdbOf(id) ?: newImdb[id] }.toSet() }

        // Agreed only on what the TV really holds: a film that did not fit (or has no details) is never read as removed
        // here next time, so it is never removed from the account.
        val next = plans.mapValues { (list, plan) ->
            val removes = failedRemoves[list].orEmpty()
            plan.syncedAfter(failedAdds[list].orEmpty(), removes).filter { it in storedImdb.getValue(list) || it in removes }
        }
        writeState(SyncState(synced.lists + next.mapKeys { it.key.name }.mapValues { it.value.toList() }))
        return SyncResult(
            addedToMyList = (stored.myList.size - before.first).coerceAtLeast(0),
            addedToLoved = (stored.loved.size - before.second).coerceAtLeast(0),
            addedToWatched = (stored.watched.size - before.third).coerceAtLeast(0),
            sentToTmdb = sent,
            complete = complete,
        )
    }

    private var sessionEnded = false

    /**
     * The account's list as IMDb id to film, horror, thriller and mystery only (SCARY's shelves: other films on the
     * account are left alone, on both sides). Null when it could not be read; [sessionEnded] when TMDB refused it.
     */
    private suspend fun remoteList(signedIn: Credentials, list: AccountList): Map<String, TmdbResult>? {
        sessionEnded = false
        val films = ArrayList<TmdbResult>()
        var page = 1
        var pages = 1
        while (page <= pages && page <= TmdbAccount.MAX_PAGE) {
            val answer = tmdb.call("GET", TmdbAccount.listUrl(signedIn.accountId, signedIn.sessionId, list, page)) ?: return null
            if (answer.code == 401) {
                sessionEnded = true
                return null
            }
            val parsed = answer.body?.takeIf { answer.ok }?.let(TmdbAccount::parseListPage) ?: return null
            films += parsed.films
            pages = parsed.totalPages
            page++
        }
        val result = LinkedHashMap<String, TmdbResult>()
        for (film in films.filter(Tmdb::isThemed)) {
            val imdb = tmdb.imdbIdOf(film.tmdbId) ?: continue
            result[imdb] = film
        }
        return result
    }

    /** Adds the film to, or removes it from, the account's watchlist or favourites. True when TMDB took it. */
    private suspend fun change(signedIn: Credentials, list: AccountList, imdbId: String, add: Boolean): Boolean {
        val tmdbId = tmdb.tmdbIdOf(imdbId) ?: return false
        val (url, body) = when (list) {
            AccountList.WATCHLIST -> TmdbAccount.watchlistUrl(signedIn.accountId, signedIn.sessionId) to TmdbAccount.watchlistBody(tmdbId, add)
            AccountList.FAVORITES -> TmdbAccount.favoriteUrl(signedIn.accountId, signedIn.sessionId) to TmdbAccount.favoriteBody(tmdbId, add)
            AccountList.RATED -> return false
        }
        val answer = tmdb.call("POST", url, body) ?: return false
        return answer.ok && answer.body?.let(TmdbAccount::parseChanged) == true
    }

    private fun readState(): SyncState = runCatching {
        if (!stateFile.exists()) return SyncState()
        val root = json.parseToJsonElement(stateFile.readText()).jsonObject
        SyncState(
            root.mapNotNull { (name, ids) ->
                val list = AccountList.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
                list.name to (ids as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            }.toMap(),
        )
    }.getOrDefault(SyncState())

    private fun writeState(state: SyncState) {
        val text = JsonObject(state.lists.mapValues { (_, ids) -> JsonArray(ids.map(::JsonPrimitive)) }).toString()
        runCatching { stateFile.writeText(text) }
            .onFailure { Log.w(TAG, "Could not save the TMDB sync state: ${it.javaClass.simpleName}") }
    }

    private fun encode(credentials: Credentials): String = "${credentials.sessionId}\n${credentials.accountId}\n${credentials.username}"

    private fun decode(stored: String): Credentials? {
        val parts = stored.split('\n')
        if (parts.size != 3) return null
        val id = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
        return Credentials(parts[0].takeIf(String::isNotBlank) ?: return null, id, parts[2])
    }

    private companion object {
        const val TAG = "TmdbAccount"
        const val SECRET_NAME = "tmdb_session"
        const val POLL_MS = 3_000L
        const val TOKEN_LIFETIME_MS = 60 * 60_000L
    }
}
