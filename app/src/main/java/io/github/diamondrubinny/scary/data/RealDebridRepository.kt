// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.os.SystemClock
import android.util.Log
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.realdebrid.FileMatch
import io.github.diamondrubinny.scary.core.realdebrid.RealDebrid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The viewer's own Real-Debrid account, only if they connect it (docs/INTERNAL_PLAYER_PLAN.md): SCARY lists the videos
 * already in it (their downloads, and torrents they added themselves) and plays them. It never searches for sources and
 * never adds anything to the account.
 *
 * Signing in is Real-Debrid's device flow: the TV shows real-debrid.com/device and a code, the viewer approves on their
 * phone, and the TV receives credentials bound to that account. Those and the refresh token are passwords: stored
 * encrypted (SecretStore, Android Keystore), sent only to api.real-debrid.com, never logged. Download links carry access
 * too, so they are never stored or logged either. Requests stay well under Real-Debrid's limit of 250 a minute.
 */
class RealDebridRepository(private val secrets: SecretStore) {
    sealed interface State {
        data object SignedOut : State

        /** Waiting for the viewer to enter [userCode] at [verificationUrl]. */
        data class Waiting(val verificationUrl: String, val userCode: String) : State

        data class SignedIn(val username: String, val premium: Boolean, val expiration: String) : State

        data class Failed(val reason: Reason) : State
    }

    enum class Reason { UNREACHABLE, EXPIRED, STORAGE, SESSION_ENDED, BUSY }

    /** The account's videos, or why they could not be listed. */
    sealed interface Files {
        data class Ready(val files: List<RealDebrid.RdFile>) : Files
        data object SignedOut : Files
        data object Unreachable : Files
    }

    /** What the player opens: a direct link, and how to get the converted stream if the TV cannot play it. */
    class Playable(val url: String, val title: String, private val transcode: suspend () -> String?) {
        suspend fun transcoded(): String? = transcode()
    }

    private data class Stored(val clientId: String, val clientSecret: String, val refreshToken: String, val username: String = "") {
        fun encode(): String = buildJsonObject {
            put("id", clientId)
            put("secret", clientSecret)
            put("refresh", refreshToken)
            put("user", username)
        }.toString()

        companion object {
            fun decode(text: String): Stored? = runCatching {
                val o = Json.parseToJsonElement(text).jsonObject
                fun field(name: String) = (o[name] as? JsonPrimitive)?.contentOrNull
                Stored(field("id") ?: return null, field("secret") ?: return null, field("refresh") ?: return null, field("user").orEmpty())
            }.getOrNull()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestLock = Mutex()
    private val tokenLock = Mutex()
    private var lastRequestAt = 0L
    private var blockedUntil = 0L

    private val _state = MutableStateFlow<State>(State.SignedOut)
    val state: StateFlow<State> = _state.asStateFlow()

    private var stored: Stored? = null
    private var accessToken: String? = null
    private var accessExpiresAt = 0L
    private var loginJob: Job? = null
    private var cachedFiles: List<RealDebrid.RdFile>? = null
    private var cachedAt = 0L

    val signedIn: Boolean get() = stored != null

    /** Reads the stored account once, off the main thread, at start-up. */
    suspend fun load() = withContext(Dispatchers.IO) {
        stored = secrets.read(SECRET_NAME)?.let(Stored::decode)
        stored?.let { _state.value = State.SignedIn(it.username, premium = true, expiration = "") }
    }

    /**
     * Starts signing in: a device code from Real-Debrid, shown with its page; then the TV asks every few seconds
     * (Real-Debrid's interval) whether the viewer has approved, until they have or the code runs out.
     */
    fun startSignIn() {
        loginJob?.cancel()
        loginJob = scope.launch {
            val code = call("GET", RealDebrid.deviceCodeUrl(), auth = false)?.takeIf { it.ok }?.body?.let(RealDebrid::parseDeviceCode)
            if (code == null) {
                _state.value = State.Failed(Reason.UNREACHABLE)
                return@launch
            }
            _state.value = State.Waiting(code.verificationUrl, code.userCode)
            val deadline = SystemClock.elapsedRealtime() + code.expiresInSeconds * 1000L
            var credentials: RealDebrid.Credentials? = null
            while (isActive && credentials == null && SystemClock.elapsedRealtime() < deadline) {
                delay(code.intervalSeconds * 1000L)
                // Until the viewer approves, the endpoint answers with an error: keep asking.
                credentials = call("GET", RealDebrid.credentialsUrl(code.deviceCode), auth = false)?.takeIf { it.ok }?.body?.let(RealDebrid::parseCredentials)
            }
            if (credentials == null) {
                if (isActive) _state.value = State.Failed(Reason.EXPIRED)
                return@launch
            }
            val token = call("POST", RealDebrid.TOKEN_URL, RealDebrid.tokenForm(credentials.clientId, credentials.clientSecret, code.deviceCode), auth = false)
                ?.takeIf { it.ok }?.body?.let(RealDebrid::parseToken)
            if (token == null) {
                _state.value = State.Failed(Reason.UNREACHABLE)
                return@launch
            }
            accessToken = token.accessToken
            accessExpiresAt = SystemClock.elapsedRealtime() + (token.expiresInSeconds - 60) * 1000
            val user = call("GET", RealDebrid.USER_URL)?.takeIf { it.ok }?.body?.let(RealDebrid::parseUser)
            val toStore = Stored(credentials.clientId, credentials.clientSecret, token.refreshToken, user?.username.orEmpty())
            if (!secrets.write(SECRET_NAME, toStore.encode())) {
                accessToken = null
                _state.value = State.Failed(Reason.STORAGE)
                return@launch
            }
            stored = toStore
            cachedFiles = null
            _state.value = State.SignedIn(toStore.username, user?.premium ?: false, user?.expiration.orEmpty())
        }
    }

    fun cancelSignIn() {
        loginJob?.cancel()
        if (state.value is State.Waiting) _state.value = stored?.let { State.SignedIn(it.username, premium = true, expiration = "") } ?: State.SignedOut
    }

    /** Forgets the account on this TV (Real-Debrid keeps its own list of connected apps). */
    fun signOut() {
        loginJob?.cancel()
        secrets.remove(SECRET_NAME)
        stored = null
        accessToken = null
        cachedFiles = null
        _state.value = State.SignedOut
    }

    /** Asks for the account's name and Premium status again (for the settings screen). */
    fun refreshAccount() {
        if (stored == null) return
        scope.launch {
            val user = call("GET", RealDebrid.USER_URL)?.takeIf { it.ok }?.body?.let(RealDebrid::parseUser) ?: return@launch
            _state.value = State.SignedIn(user.username, user.premium, user.expiration)
        }
    }

    /**
     * The account's videos: downloads, then each finished torrent's video files. Kept for [CACHE_MS], so opening films
     * one after another does not ask again each time.
     */
    suspend fun files(force: Boolean = false): Files = withContext(Dispatchers.IO) {
        if (stored == null) return@withContext Files.SignedOut
        val now = SystemClock.elapsedRealtime()
        cachedFiles?.takeIf { !force && now - cachedAt < CACHE_MS }?.let { return@withContext Files.Ready(it) }
        val downloads = mutableListOf<RealDebrid.RdFile>()
        for (page in 1..MAX_PAGES) {
            val answer = call("GET", RealDebrid.downloadsUrl(page)) ?: return@withContext Files.Unreachable
            if (answer.code == HttpURLConnection.HTTP_NO_CONTENT) break
            if (!answer.ok) return@withContext if (stored == null) Files.SignedOut else Files.Unreachable
            val pageFiles = RealDebrid.parseDownloads(answer.body.orEmpty())
            downloads += pageFiles
            if (answer.total != null && page * RealDebrid.PAGE_SIZE >= answer.total) break
            if (pageFiles.isEmpty()) break
        }
        val torrents = mutableListOf<RealDebrid.RdTorrent>()
        for (page in 1..MAX_PAGES) {
            val answer = call("GET", RealDebrid.torrentsUrl(page)) ?: break
            if (!answer.ok || answer.code == HttpURLConnection.HTTP_NO_CONTENT) break
            val pageTorrents = RealDebrid.parseTorrents(answer.body.orEmpty())
            torrents += pageTorrents
            if (answer.total != null && page * RealDebrid.PAGE_SIZE >= answer.total) break
            if (pageTorrents.isEmpty()) break
        }
        val fromTorrents = torrents.flatMap { torrent ->
            RealDebrid.singleFile(torrent)?.let { listOf(it) } ?: if (torrent.links.size > 1) {
                RealDebrid.torrentFiles(torrent, call("GET", RealDebrid.torrentInfoUrl(torrent.id))?.takeIf { it.ok }?.body)
            } else {
                emptyList()
            }
        }
        // The same file can be both a download and a torrent's link: listed once.
        val all = (downloads + fromTorrents).distinctBy { it.link }
        cachedFiles = all
        cachedAt = SystemClock.elapsedRealtime()
        Files.Ready(all)
    }

    /** The viewer's files that are [movie] (FileMatch), or empty when not signed in or not reachable. */
    suspend fun filesFor(movie: Movie): List<RealDebrid.RdFile> =
        (files() as? Files.Ready)?.files?.let { FileMatch.forMovie(movie, it) }.orEmpty()

    /** A link to play [file]: the one Real-Debrid already generated, or a fresh one from its own link. */
    suspend fun playable(file: RealDebrid.RdFile): Playable? = withContext(Dispatchers.IO) {
        val direct = file.direct
        val unrestricted = if (direct == null) {
            call("POST", RealDebrid.UNRESTRICT_URL, RealDebrid.unrestrictForm(file.link))?.takeIf { it.ok }?.body?.let(RealDebrid::parseUnrestricted)
                ?: return@withContext null
        } else {
            null
        }
        val id = file.downloadId ?: unrestricted?.id
        Playable(url = direct ?: unrestricted!!.url, title = file.name) {
            id?.let { call("GET", RealDebrid.transcodeUrl(it))?.takeIf { a -> a.ok }?.body?.let(RealDebrid::parseTranscode) }
        }
    }

    // HTTP --------------------------------------------------------------------------------------------------------------

    private class Answer(val code: Int, val body: String?, val total: Int?) {
        val ok get() = code in 200..299
    }

    /** A fresh access token from the stored refresh token, when the last one ran out. */
    private suspend fun token(): String? = tokenLock.withLock {
        accessToken?.takeIf { SystemClock.elapsedRealtime() < accessExpiresAt }?.let { return it }
        val account = stored ?: return null
        val answer = send("POST", RealDebrid.TOKEN_URL, RealDebrid.tokenForm(account.clientId, account.clientSecret, account.refreshToken), null)
            ?: return null
        val fresh = answer.body?.takeIf { answer.ok }?.let(RealDebrid::parseToken)
        if (fresh == null) {
            // The viewer removed SCARY from their Real-Debrid account (or the credentials were revoked).
            if (answer.code in 400..499) sessionEnded()
            return null
        }
        if (fresh.refreshToken != account.refreshToken) {
            val updated = account.copy(refreshToken = fresh.refreshToken)
            if (secrets.write(SECRET_NAME, updated.encode())) stored = updated
        }
        accessToken = fresh.accessToken
        accessExpiresAt = SystemClock.elapsedRealtime() + (fresh.expiresInSeconds - 60) * 1000
        fresh.accessToken
    }

    private fun sessionEnded() {
        secrets.remove(SECRET_NAME)
        stored = null
        accessToken = null
        cachedFiles = null
        _state.value = State.Failed(Reason.SESSION_ENDED)
    }

    /** One API call with the account's token (refreshed once on a 401), or null when the network failed. */
    private suspend fun call(method: String, url: String, form: String? = null, auth: Boolean = true): Answer? {
        if (!auth) return send(method, url, form, null)
        val first = send(method, url, form, token() ?: return null) ?: return null
        if (first.code != HttpURLConnection.HTTP_UNAUTHORIZED) return first
        accessToken = null
        return send(method, url, form, token() ?: return null)
    }

    private suspend fun send(method: String, url: String, form: String?, bearer: String?): Answer? = requestLock.withLock {
        // Spaced out under Real-Debrid's limit; after a 429, quiet for a while instead of trying again at once.
        val now = SystemClock.elapsedRealtime()
        if (now < blockedUntil) return null
        val gap = 60_000L / RealDebrid.MAX_REQUESTS_PER_MINUTE - (now - lastRequestAt)
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
                    bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
                    if (form != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                        outputStream.use { it.write(form.toByteArray()) }
                    }
                }
                val code = connection.responseCode
                if (code == HTTP_TOO_MANY_REQUESTS) blockedUntil = SystemClock.elapsedRealtime() + BACKOFF_MS
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                Answer(code, stream?.let { readLimited(it) }, connection.getHeaderField("X-Total-Count")?.toIntOrNull())
            } catch (e: IOException) {
                // Only the kind of failure, never the URL (it can hold a code) or the answer.
                Log.w(TAG, "Real-Debrid request failed: ${e.javaClass.simpleName}")
                null
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun readLimited(input: InputStream): String? = input.use { stream ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_BODY_BYTES) return null
        }
        out.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val TAG = "RealDebrid"
        const val SECRET_NAME = "realdebrid_account"
        const val TIMEOUT_MS = 15_000
        const val MAX_BODY_BYTES = 8 * 1024 * 1024
        const val MAX_PAGES = 4
        const val CACHE_MS = 5 * 60_000L
        const val BACKOFF_MS = 2 * 60_000L
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
