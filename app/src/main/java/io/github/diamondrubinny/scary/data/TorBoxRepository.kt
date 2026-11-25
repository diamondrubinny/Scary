// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.os.SystemClock
import android.util.Log
import io.github.diamondrubinny.scary.core.player.Languages
import io.github.diamondrubinny.scary.core.player.SubtitleFiles
import io.github.diamondrubinny.scary.core.realdebrid.Magnets
import io.github.diamondrubinny.scary.core.realdebrid.RealDebrid
import io.github.diamondrubinny.scary.core.torbox.TorBox
import io.github.diamondrubinny.scary.data.RealDebridRepository.Files
import io.github.diamondrubinny.scary.data.RealDebridRepository.Playable
import io.github.diamondrubinny.scary.data.RealDebridRepository.Pull
import io.github.diamondrubinny.scary.data.RealDebridRepository.PullError
import io.github.diamondrubinny.scary.data.RealDebridRepository.Reason
import io.github.diamondrubinny.scary.data.RealDebridRepository.State
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
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/**
 * The viewer's TorBox account, beside Real-Debrid (docs/TORBOX_PLAN.md, core TorBox): signing in, the videos of their
 * own torrents, adding a magnet link they chose, and deleting a torrent, each as the Real-Debrid account does it and
 * with the same answers, so the screens treat both alike.
 *
 * Signing in is TorBox's device flow: the TV shows TorBox's page and a code, the viewer approves on their phone, and
 * the TV receives the account's token. That token is the account's API key: it gives full access and does not expire.
 * It is stored encrypted (SecretStore, Android Keystore), sent only to api.torbox.app, never logged, and never put in
 * an address a player opens: TorBox takes it in the address of a link request, so SCARY makes that request itself and
 * hands on only the file's address that comes back. Requests stay well under TorBox's limit of 300 a minute.
 */
class TorBoxRepository(
    private val secrets: SecretStore,
    /** Where subtitle files from the viewer's torrents are kept as UTF-8 text while they are in use; none in tests. */
    subtitlesDir: File? = null,
) {
    private val subtitleCache = subtitlesDir?.let(::SubtitleCache)

    /** The VPN's kill switch (VpnRepository): while it says so, no request leaves, as if TorBox were unreachable. */
    @Volatile
    var blocked: () -> Boolean = { false }

    private class Answer(val code: Int, val body: String?) {
        val ok: Boolean get() = code in 200..299 && TorBox.problem(body) == null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestLock = Mutex()
    private val random = SecureRandom()
    private var lastRequestAt = 0L
    private var blockedUntil = 0L

    private val _state = MutableStateFlow<State>(State.SignedOut)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    private var token: String? = null
    private var loginJob: Job? = null
    private var cachedFiles: List<RealDebrid.RdFile>? = null
    private var cachedAt = 0L

    val signedIn: Boolean get() = token != null

    /** Reads the stored account once, off the main thread, at start-up. */
    suspend fun load() = withContext(Dispatchers.IO) {
        token = secrets.read(SECRET_NAME)?.takeIf(TorBox::isToken)
        if (token != null) _state.value = State.SignedIn("", premium = true, expiration = "")
    }

    /**
     * Starts signing in: a device code from TorBox, shown with its page; then the TV asks every few seconds whether
     * the viewer has approved, until they have or the code runs out.
     */
    fun startSignIn() {
        loginJob?.cancel()
        loginJob = scope.launch {
            val code = send("GET", TorBox.DEVICE_START_URL)?.takeIf { it.ok }?.body?.let(TorBox::parseDeviceCode)
            if (code == null) {
                _state.value = State.Failed(Reason.UNREACHABLE)
                return@launch
            }
            _state.value = State.Waiting(code.verificationUrl, code.userCode)
            val deadline = SystemClock.elapsedRealtime() + code.expiresInSeconds * 1000L
            var fresh: String? = null
            while (isActive && fresh == null && SystemClock.elapsedRealtime() < deadline) {
                delay(code.intervalSeconds * 1000L)
                val answer = send("POST", TorBox.DEVICE_TOKEN_URL, TorBox.deviceTokenBody(code.deviceCode), JSON) ?: continue
                if (answer.ok) {
                    fresh = answer.body?.let(TorBox::parseToken)
                } else if (TorBox.errorName(answer.body) != TorBox.NOT_APPROVED_YET && answer.code != HTTP_TOO_MANY_REQUESTS) {
                    // The code was refused, used or forgotten: asking again cannot help.
                    break
                }
            }
            if (fresh == null) {
                if (isActive) _state.value = State.Failed(Reason.EXPIRED)
                return@launch
            }
            if (!secrets.write(SECRET_NAME, fresh)) {
                _state.value = State.Failed(Reason.STORAGE)
                return@launch
            }
            token = fresh
            cachedFiles = null
            val user = call("GET", TorBox.USER_URL)?.takeIf { it.ok }?.body?.let(TorBox::parseUser)
            _state.value = State.SignedIn(user?.email.orEmpty(), user?.paid ?: true, user?.expiration.orEmpty())
        }
    }

    fun cancelSignIn() {
        loginJob?.cancel()
        if (state.value is State.Waiting) _state.value = if (token != null) State.SignedIn("", premium = true, expiration = "") else State.SignedOut
    }

    /**
     * Forgets the account on this TV. The token itself stays good at TorBox until the viewer resets their API key
     * there; the settings screen says so.
     */
    fun signOut() {
        loginJob?.cancel()
        secrets.remove(SECRET_NAME)
        token = null
        cachedFiles = null
        _state.value = State.SignedOut
    }

    /** Asks for the account's name and plan again (for the settings screen). */
    fun refreshAccount() {
        if (token == null) return
        scope.launch {
            val user = call("GET", TorBox.USER_URL)?.takeIf { it.ok }?.body?.let(TorBox::parseUser) ?: return@launch
            _state.value = State.SignedIn(user.email, user.paid, user.expiration)
        }
    }

    private fun sessionEnded() {
        secrets.remove(SECRET_NAME)
        token = null
        cachedFiles = null
        _state.value = State.Failed(Reason.SESSION_ENDED)
    }

    /** The videos of the account's finished torrents. Kept for [CACHE_MS], as Real-Debrid's are. */
    suspend fun files(force: Boolean = false): Files = withContext(Dispatchers.IO) {
        if (token == null) return@withContext Files.SignedOut
        cachedFiles?.takeIf { !force && SystemClock.elapsedRealtime() - cachedAt < CACHE_MS }?.let { return@withContext Files.Ready(it) }
        val torrents = torrents(fresh = force) ?: return@withContext if (token == null) Files.SignedOut else Files.Unreachable
        val all = torrents.flatMap { it.videos() }
        cachedFiles = all
        cachedAt = SystemClock.elapsedRealtime()
        Files.Ready(all)
    }

    private suspend fun torrents(fresh: Boolean): List<TorBox.Torrent>? {
        val all = mutableListOf<TorBox.Torrent>()
        for (page in 0 until MAX_PAGES) {
            val answer = call("GET", TorBox.torrentsUrl(page * TorBox.PAGE_SIZE, fresh)) ?: return null
            // An account with no torrent answers "not found": an empty list, not a failure.
            if (!answer.ok) return if (TorBox.errorName(answer.body) == NOTHING_FOUND) all else null
            val listed = TorBox.parseTorrents(answer.body.orEmpty())
            all += listed
            if (listed.size < TorBox.PAGE_SIZE) break
        }
        return all
    }

    private suspend fun torrent(id: Long): TorBox.Torrent? =
        call("GET", TorBox.torrentUrl(id))?.takeIf { it.ok }?.body?.let(TorBox::parseTorrents)?.firstOrNull { it.id == id }

    /**
     * Adds [magnet] to the viewer's account and waits a short while: a torrent TorBox already holds is ready within
     * seconds. TorBox chooses the files itself, so nothing is left waiting for a choice. One that fails is deleted
     * again so it does not keep one of the account's active places.
     */
    suspend fun pull(magnet: Magnets.Magnet): Pull = withContext(Dispatchers.IO) {
        val boundary = boundary()
        val added = call("POST", TorBox.CREATE_URL, TorBox.createBody(magnet, boundary), TorBox.multipartType(boundary))
            ?: return@withContext Pull.Failed(PullError.UNREACHABLE)
        val id = added.body?.takeIf { added.ok }?.let(TorBox::parseCreated)
            ?: return@withContext Pull.Failed(pullError(added), TorBox.errorName(added.body)?.takeIf { Regex("^[A-Z0-9_]{1,48}$").matches(it) })
        cachedFiles = null
        val started = SystemClock.elapsedRealtime()
        var progress = 0
        while (SystemClock.elapsedRealtime() - started < PULL_WAIT_MS) {
            val now = torrent(id)
            when (now?.state) {
                null, RealDebrid.TorrentState.CONVERTING, RealDebrid.TorrentState.CHOOSE_FILES -> Unit
                RealDebrid.TorrentState.QUEUED, RealDebrid.TorrentState.DOWNLOADING, RealDebrid.TorrentState.FINISHING -> {
                    progress = now.progress
                    // Not one TorBox already holds: it stays in the account and shows in the list when done.
                    if (SystemClock.elapsedRealtime() - started > CACHED_WAIT_MS) return@withContext Pull.Downloading(progress, now.status().id)
                }
                RealDebrid.TorrentState.READY -> {
                    val files = now.videos()
                    return@withContext if (files.isEmpty()) Pull.Failed(PullError.NO_VIDEO) else Pull.Ready(files)
                }
                RealDebrid.TorrentState.FAILED -> {
                    delete(id)
                    return@withContext Pull.Failed(PullError.DEAD)
                }
            }
            delay(PULL_POLL_MS)
        }
        Pull.Downloading(progress, TorBox.statusId(id))
    }

    private fun pullError(answer: Answer): PullError = when (TorBox.problem(answer.body)) {
        TorBox.Problem.SLOTS_FULL -> PullError.SLOTS_FULL
        TorBox.Problem.LIMIT -> PullError.LIMIT
        TorBox.Problem.TOO_BIG -> PullError.TOO_BIG
        TorBox.Problem.NOT_ALLOWED -> PullError.NOT_ALLOWED
        TorBox.Problem.INVALID -> PullError.DEAD
        else -> PullError.UNREACHABLE
    }

    /** The account's torrents in any state, newest first as the API lists them, or null when they could not be listed. */
    suspend fun recentTorrents(): List<RealDebrid.TorrentStatus>? = withContext(Dispatchers.IO) {
        if (token == null) return@withContext emptyList()
        torrents(fresh = true)?.map { it.status() }
    }

    /** Where one torrent of the account stands now ([id] as [recentTorrents] gives it); null when it could not be asked. */
    suspend fun torrentStatus(id: String): RealDebrid.TorrentStatus? = withContext(Dispatchers.IO) {
        TorBox.parseStatusId(id)?.let { torrent(it) }?.status()
    }

    /** The videos of one finished torrent; empty when it has none, null when it could not be asked. */
    suspend fun torrentVideos(id: String): List<RealDebrid.RdFile>? = withContext(Dispatchers.IO) {
        TorBox.parseStatusId(id)?.let { torrent(it) }?.videos()
    }

    suspend fun deleteTorrent(id: String): Boolean = withContext(Dispatchers.IO) {
        TorBox.parseStatusId(id)?.let { delete(it) } ?: false
    }

    /** Deletes the torrent [file] came from, at the viewer's word: TorBox has no way to delete one file of a torrent. */
    suspend fun deleteFile(file: RealDebrid.RdFile): Boolean = withContext(Dispatchers.IO) {
        TorBox.parseFileRef(file.key)?.let { (torrentId, _) -> delete(torrentId) } ?: false
    }

    /** Deletes every torrent [files] came from. How many could not be deleted. */
    suspend fun deleteFiles(files: List<RealDebrid.RdFile>): Int = withContext(Dispatchers.IO) {
        var failed = 0
        for (torrentId in files.mapNotNull { TorBox.parseFileRef(it.key)?.first }.distinct()) {
            if (!delete(torrentId)) failed++
            delay(DELETE_PAUSE_MS)
        }
        cachedFiles = null
        failed
    }

    private suspend fun delete(torrentId: Long): Boolean {
        val answer = call("POST", TorBox.CONTROL_URL, TorBox.deleteBody(torrentId), JSON) ?: return false
        if (answer.ok) cachedFiles = null
        return answer.ok || answer.code == HttpURLConnection.HTTP_NOT_FOUND
    }

    /** True for a file of this account ([RealDebrid.RdFile.key] says whose a file is). */
    fun owns(file: RealDebrid.RdFile): Boolean = TorBox.parseFileRef(file.key) != null

    /** A link to play [file]. TorBox offers no converted stream, so there is none to fall back to. */
    suspend fun playable(file: RealDebrid.RdFile): Playable? = withContext(Dispatchers.IO) {
        link(file.link)?.let { url -> Playable(url = url, title = file.name) { null } }
    }

    /** The address of the file [ref] names, asked for by SCARY itself: the request's own address holds the token. */
    private suspend fun link(ref: String): String? {
        val (torrentId, fileId) = TorBox.parseFileRef(ref) ?: return null
        val held = token ?: return null
        val answer = send("GET", TorBox.linkUrl(held, torrentId, fileId)) ?: return null
        if (answer.code == HttpURLConnection.HTTP_UNAUTHORIZED) sessionEnded()
        return answer.body?.takeIf { answer.ok }?.let { TorBox.parseLink(it, held) }
    }

    /** A subtitle file of the viewer's torrent as UTF-8 text on the TV, as Real-Debrid's are kept. Never run, only read. */
    suspend fun subtitleFile(subtitle: RealDebrid.RdSubtitle): File? = withContext(Dispatchers.IO) {
        val cache = subtitleCache ?: return@withContext null
        val mimeType = SubtitleFiles.mimeType(subtitle.name) ?: return@withContext null
        val file = cache.fileFor(subtitle.link, subtitle.name.substringAfterLast('.').lowercase())
        cache.kept(file)?.let { return@withContext it }
        val url = link(subtitle.link) ?: return@withContext null
        val bytes = download(url) ?: return@withContext null
        val text = SubtitleFiles.decode(bytes, mimeType, Languages.fromFileName(subtitle.name)) ?: return@withContext null
        cache.keep(file, text)
    }

    /** A link's content, up to SubtitleFiles.MAX_BYTES; null for anything else. Redirects are not followed. */
    private fun download(url: String): ByteArray? {
        if (blocked() || !url.startsWith("https://")) return null
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = false
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.use { stream -> readLimited(stream, SubtitleFiles.MAX_BYTES) }
        } catch (e: IOException) {
            Log.w(TAG, "Subtitle download failed: ${e.javaClass.simpleName}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun boundary(): String {
        val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return "scary" + (1..BOUNDARY_LETTERS).map { letters[random.nextInt(letters.length)] }.joinToString("")
    }

    /** One API call with the account's token; null when the network failed. A refused token ends the session. */
    private suspend fun call(method: String, url: String, body: String? = null, contentType: String? = null): Answer? {
        val held = token ?: return null
        val answer = send(method, url, body, contentType, held) ?: return null
        if (answer.code == HttpURLConnection.HTTP_UNAUTHORIZED) sessionEnded()
        return answer
    }

    private suspend fun send(method: String, url: String, body: String? = null, contentType: String? = null, bearer: String? = null): Answer? = requestLock.withLock {
        // Spaced out under TorBox's limit; after a 429, quiet for as long as it asked (within reason).
        if (blocked()) return null
        val now = SystemClock.elapsedRealtime()
        if (now < blockedUntil) return null
        val gap = 60_000L / TorBox.MAX_REQUESTS_PER_MINUTE - (now - lastRequestAt)
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
                    if (body != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", contentType ?: JSON)
                        outputStream.use { it.write(body.toByteArray()) }
                    }
                }
                val code = connection.responseCode
                if (code == HTTP_TOO_MANY_REQUESTS) {
                    val wait = connection.getHeaderField("Retry-After")?.toLongOrNull()?.times(1000) ?: BACKOFF_MS
                    blockedUntil = SystemClock.elapsedRealtime() + wait.coerceIn(MIN_BACKOFF_MS, MAX_BACKOFF_MS)
                }
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                Answer(code, stream?.use { readLimited(it, MAX_BODY_BYTES)?.toString(Charsets.UTF_8) })
            } catch (e: IOException) {
                // Only the kind of failure, never the address (it can hold the token) or the answer.
                Log.w(TAG, "TorBox request failed: ${e.javaClass.simpleName}")
                null
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun readLimited(stream: InputStream, limit: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > limit) return null
        }
        return out.toByteArray()
    }

    private companion object {
        const val TAG = "TorBox"
        const val SECRET_NAME = "torbox_account"
        const val JSON = "application/json"
        const val TIMEOUT_MS = 15_000
        const val MAX_BODY_BYTES = 8 * 1024 * 1024
        const val MAX_PAGES = 4
        const val CACHE_MS = 5 * 60_000L
        const val BACKOFF_MS = 2 * 60_000L
        const val MIN_BACKOFF_MS = 5_000L
        const val MAX_BACKOFF_MS = 60 * 60_000L
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val BOUNDARY_LETTERS = 24

        /** What the list answers for an account with no torrent. */
        const val NOTHING_FOUND = "ITEM_NOT_FOUND"

        /** How long a pull is watched before it is left to finish in the account. */
        const val PULL_WAIT_MS = 60_000L
        const val PULL_POLL_MS = 3_000L

        /** A torrent TorBox already holds is ready well within this. */
        const val CACHED_WAIT_MS = 15_000L
        const val DELETE_PAUSE_MS = 300L
    }
}
