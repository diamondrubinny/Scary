// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.model.Catalog
import io.github.diamondrubinny.scary.core.model.MediaKind
import io.github.diamondrubinny.scary.core.stremio.Stremio
import io.github.diamondrubinny.scary.core.stremio.StremioItem
import io.github.diamondrubinny.scary.core.tmdb.ListSync
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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Instant

/**
 * The viewer's own Stremio account, only if they connect it (docs/STREMIO_ACCOUNT_PLAN.md): "Continue in Stremio" on
 * the home screen, films watched there marked watched here, and My list kept in step with the Stremio library both
 * ways. Connecting is Stremio's own link: the TV shows a code and a QR code, the viewer approves on their phone, the TV
 * gets a key. The key is a password: stored encrypted (SecretStore, Android Keystore), sent only to api.strem.io,
 * never logged. SCARY never reads the account's add-ons or asks for streams: watching stays in Stremio.
 */
class StremioRepository(
    context: Context,
    private val secrets: SecretStore,
    private val settings: SettingsRepository,
) {
    sealed interface State {
        data object SignedOut : State

        /** Waiting for the viewer to approve [code] at [link] (the QR code). */
        data class Waiting(val code: String, val link: String) : State

        data class SignedIn(val lastSyncMillis: Long?, val syncing: Boolean, val lastResult: SyncResult?) : State

        data class Failed(val reason: Reason) : State
    }

    enum class Reason { UNREACHABLE, EXPIRED, STORAGE, SESSION_ENDED }

    /** What a sync changed on the TV and in Stremio, and whether all of it worked. */
    data class SyncResult(val addedToMyList: Int, val addedToWatched: Int, val sentToStremio: Int, val complete: Boolean)

    /** How My list and Watched stood after the last sync, by IMDb id ([ListSync]'s lastSynced). */
    private data class SyncState(val myList: List<String>? = null, val watched: List<String>? = null)

    private class Answer(val code: Int, val body: String?) {
        val ok get() = code in 200..299
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateFile = File(context.filesDir, "stremio_sync.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val syncMutex = Mutex()
    private val requestLock = Mutex()

    private val _state = MutableStateFlow<State>(State.SignedOut)
    val state: StateFlow<State> = _state.asStateFlow()

    /** The library as last read: "Continue in Stremio" and the movie page come from it. Empty when signed out. */
    private val _library = MutableStateFlow<List<StremioItem>>(emptyList())
    val library: StateFlow<List<StremioItem>> = _library.asStateFlow()

    @Volatile
    private var authKey: String? = null
    private var loginJob: Job? = null
    private var lastSyncElapsed = 0L
    private var lastRequestAt = 0L

    val signedIn: Boolean get() = authKey != null

    /** Reads the stored key once; called off the main thread at start-up. */
    suspend fun load() = withContext(Dispatchers.IO) {
        authKey = secrets.read(SECRET_NAME)
        _state.value = if (authKey != null) State.SignedIn(null, false, null) else State.SignedOut
    }

    /**
     * Starts connecting: a code from Stremio, shown with its page and QR code. Then the TV asks every [POLL_MS] whether
     * the viewer has approved, until they have or [CODE_LIFETIME_MS] has passed. [onSignedIn] runs once connected.
     */
    fun startSignIn(onSignedIn: () -> Unit) {
        loginJob?.cancel()
        loginJob = scope.launch {
            val created = send("GET", Stremio.LINK_CREATE_URL, null)
            val code = created?.body?.takeIf { created.ok }?.let(Stremio::parseLinkCode)
            if (code == null) {
                _state.value = State.Failed(Reason.UNREACHABLE)
                return@launch
            }
            _state.value = State.Waiting(code.code, code.link)
            val readUrl = Stremio.linkReadUrl(code.code) ?: return@launch
            val deadline = SystemClock.elapsedRealtime() + CODE_LIFETIME_MS
            while (isActive && SystemClock.elapsedRealtime() < deadline) {
                delay(POLL_MS)
                // Until the viewer approves, Stremio answers with an error: keep asking.
                val answer = send("GET", readUrl, null) ?: continue
                val key = answer.body?.takeIf { answer.ok }?.let(Stremio::parseLinkKey) ?: continue
                // A key that does not open the account is not kept (Stremio answers errors with an "error" object).
                val user = send("POST", Stremio.GET_USER_URL, Stremio.authBody(key))
                if (user == null || !user.ok || user.body == null || Stremio.errorCode(user.body) != null) {
                    _state.value = State.Failed(Reason.UNREACHABLE)
                    return@launch
                }
                if (!secrets.write(SECRET_NAME, key)) {
                    _state.value = State.Failed(Reason.STORAGE)
                    return@launch
                }
                authKey = key
                stateFile.delete()
                _state.value = State.SignedIn(null, false, null)
                onSignedIn()
                return@launch
            }
            if (isActive) _state.value = State.Failed(Reason.EXPIRED)
        }
    }

    fun cancelSignIn() {
        loginJob?.cancel()
        loginJob = null
        _state.value = if (authKey != null) State.SignedIn(null, false, null) else State.SignedOut
    }

    /**
     * Disconnects: the key is ended at Stremio (if it can be reached) and forgotten on the TV, with what the sync
     * remembered. My list and Watched on the TV stay as they are; nothing changes in the Stremio library.
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        loginJob?.cancel()
        val key = authKey
        forget(State.SignedOut)
        if (key != null) send("POST", Stremio.LOGOUT_URL, Stremio.authBody(key))
    }

    private fun forget(next: State) {
        authKey = null
        secrets.remove(SECRET_NAME)
        stateFile.delete()
        _library.value = emptyList()
        _state.value = next
    }

    /**
     * Reads the library and keeps My list and Watched in step with it, unless a sync ran less than [minGapMs] ago.
     * [catalog] is the full catalog: only films SCARY knows take part (a horror launcher's lists; the rest of the
     * library is left alone, on both sides). Null when not signed in, or too soon.
     */
    suspend fun sync(catalog: Catalog, minGapMs: Long = 0): SyncResult? = syncMutex.withLock {
        val key = authKey ?: return@withLock null
        if (minGapMs > 0 && lastSyncElapsed != 0L && SystemClock.elapsedRealtime() - lastSyncElapsed < minGapMs) return@withLock null
        lastSyncElapsed = SystemClock.elapsedRealtime()
        _state.value = State.SignedIn((state.value as? State.SignedIn)?.lastSyncMillis, true, null)
        val result = withContext(Dispatchers.IO) { runSync(key, catalog) }
        if (authKey == null) return@withLock null
        if (result == null) {
            if (sessionEnded) {
                // Signed out of Stremio elsewhere (or the key was revoked): the TV forgets it and says so.
                Log.i(TAG, "Stremio session ended")
                forget(State.Failed(Reason.SESSION_ENDED))
            } else {
                _state.value = State.SignedIn((state.value as? State.SignedIn)?.lastSyncMillis, false, SyncResult(0, 0, 0, complete = false))
            }
            return@withLock null
        }
        _state.value = State.SignedIn(System.currentTimeMillis(), false, result)
        result
    }

    @Volatile
    private var sessionEnded = false

    /** Null when the library could not be read ([sessionEnded] when Stremio refused the key). */
    private suspend fun runSync(key: String, catalog: Catalog): SyncResult? {
        sessionEnded = false
        val answer = send("POST", Stremio.DATASTORE_GET_URL, Stremio.libraryGetBody(key)) ?: return null
        val body = answer.body ?: return null
        if (Stremio.errorCode(body) == Stremio.ERROR_SESSION) {
            sessionEnded = true
            return null
        }
        val items = (if (answer.ok) Stremio.parseLibrary(body) else null) ?: return null
        _library.value = items

        val prefs = settings.prefs.first()
        val films = catalog.movies.filter { it.kind == MediaKind.MOVIE }
        val byImdb = films.associateBy { it.imdbId }
        val catalogImdb = catalog.movies.associate { it.id to it.imdbId }
        val series = catalog.movies.filter { it.kind == MediaKind.SERIES }.mapTo(HashSet()) { it.id }
        fun imdbOf(id: String): String? =
            if (id in series) null else catalogImdb[id] ?: id.removePrefix(Cinemeta.ID_PREFIX).takeIf { it != id && it.startsWith("tt") }
        fun scaryId(imdb: String): String? = byImdb[imdb]?.id

        val localList = prefs.myListIds.mapNotNull(::imdbOf).distinct()
        val localWatched = prefs.watchedIds.mapNotNull(::imdbOf).toSet()
        // Only films SCARY knows (or already holds): a comedy in the viewer's Stremio library stays out of My list.
        val known = byImdb.keys + localList
        val itemsById = items.associateBy { it.imdbId }
        val remoteList = Stremio.libraryFilms(items).filterTo(LinkedHashSet()) { it in known }
        val remoteWatched = Stremio.watchedFilms(items).filterTo(LinkedHashSet()) { it in known }
        val synced = readState()

        val plan = ListSync.plan(localList, remoteList, synced.myList?.toSet())
        val now = Instant.now()
        val changes = ArrayList<JsonObject>()
        for (imdb in plan.addRemote) {
            val movie = byImdb[imdb]
            Stremio.added(itemsById[imdb], imdb, movie?.title ?: itemsById[imdb]?.name.orEmpty(), movie?.posterUrl, now)?.let(changes::add)
        }
        for (imdb in plan.removeRemote) itemsById[imdb]?.let { changes += Stremio.removed(it, now) }
        // Watched here since the last sync, and in the Stremio library: marked there too.
        val newlyWatched = localWatched - synced.watched.orEmpty().toSet() - remoteWatched
        for (imdb in newlyWatched) {
            val item = itemsById[imdb]?.takeIf { it.isMovie && it.inLibrary } ?: continue
            if (imdb in plan.removeRemote || imdb in plan.addRemote) continue
            changes += Stremio.markedWatched(item, now)
        }
        val sentOk = changes.isEmpty() || run {
            val put = send("POST", Stremio.DATASTORE_PUT_URL, Stremio.libraryPutBody(key, changes))
            val error = put?.body?.let(Stremio::errorCode)
            if (error == Stremio.ERROR_SESSION) {
                sessionEnded = true
                return null
            }
            // Stremio answers 200 with an "error" object when it refuses: that is not sent.
            put != null && put.ok && error == null
        }

        val before = prefs.myListIds.size to prefs.watchedIds.size
        val merged = ListSync.applyTo(prefs.myListIds, localList.toSet(), plan.local, ::imdbOf, ::scaryId)
        val stored = settings.applySync(
            myList = merged,
            loved = null,
            watchedAdded = (remoteWatched - localWatched).mapNotNull(::scaryId),
        )
        val storedList = stored.myList.mapNotNull(::imdbOf).toSet()
        val storedWatched = stored.watched.mapNotNull(::imdbOf).toSet()
        val agreedList = (if (sentOk) plan.synced else plan.syncedAfter(plan.addRemote, plan.removeRemote))
            .filter { it in storedList || (!sentOk && it in plan.removeRemote) }
        writeState(SyncState(myList = agreedList, watched = if (sentOk) (storedWatched + remoteWatched).toList() else synced.watched))
        return SyncResult(
            addedToMyList = (stored.myList.size - before.first).coerceAtLeast(0),
            addedToWatched = (stored.watched.size - before.second).coerceAtLeast(0),
            sentToStremio = if (sentOk) changes.size else 0,
            complete = sentOk,
        )
    }

    /** One request to Stremio's own hosts over HTTPS, or null when the network failed. Spaced out a little. */
    private suspend fun send(method: String, url: String, body: String?): Answer? = requestLock.withLock {
        if (!allowed(url)) return null
        val gap = MIN_GAP_MS - (SystemClock.elapsedRealtime() - lastRequestAt)
        if (gap > 0) delay(gap)
        lastRequestAt = SystemClock.elapsedRealtime()
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    instanceFollowRedirects = false
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "SCARY-launcher")
                    if (body != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        outputStream.use { it.write(body.toByteArray()) }
                    }
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                Answer(code, stream?.let(::readLimited))
            } catch (e: IOException) {
                // Only the kind of failure: never the body (it can hold the key).
                Log.w(TAG, "Stremio request failed: ${e.javaClass.simpleName}")
                null
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun allowed(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.rawUserInfo == null && uri.host?.lowercase() in setOf(Stremio.API_HOST, Stremio.LINK_HOST)
    }

    private fun readLimited(stream: InputStream): String? = stream.use { input ->
        val bytes = input.readNBytesCompat(MAX_ANSWER_BYTES + 1)
        if (bytes.size > MAX_ANSWER_BYTES) null else String(bytes, Charsets.UTF_8)
    }

    private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun readState(): SyncState = runCatching {
        if (!stateFile.exists()) return SyncState()
        val root = json.parseToJsonElement(stateFile.readText()) as? JsonObject ?: return SyncState()
        fun list(name: String): List<String>? = (root[name] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf { id -> id.startsWith("tt") } }
        SyncState(list("myList"), list("watched"))
    }.getOrDefault(SyncState())

    private fun writeState(state: SyncState) {
        runCatching {
            stateFile.writeText(
                buildJsonObject {
                    state.myList?.let { put("myList", buildJsonArray { it.forEach { id -> add(JsonPrimitive(id)) } }) }
                    state.watched?.let { put("watched", buildJsonArray { it.forEach { id -> add(JsonPrimitive(id)) } }) }
                }.toString(),
            )
        }
    }

    private companion object {
        const val TAG = "ScaryStremio"
        const val SECRET_NAME = "stremio_auth_key"
        const val TIMEOUT_MS = 20_000
        const val POLL_MS = 3_000L
        const val CODE_LIFETIME_MS = 5 * 60_000L
        const val MIN_GAP_MS = 250L
        const val MAX_ANSWER_BYTES = 8 * 1024 * 1024
    }
}
