// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.remote

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.remote.RemoteProtocol
import io.github.diamondrubinny.scary.core.remote.RemoteProtocol.Command
import io.github.diamondrubinny.scary.core.remote.RemoteProtocol.Verdict
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
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger

/**
 * The phone remote: a small web server on the home network, on only while the viewer turns it on (docs/
 * PHONE_REMOTE_PLAN.md). It serves the remote page (assets/remote) to anyone, since it holds no secret, and takes only
 * commands signed with the secret from the QR code (RemoteProtocol). It stops after [IDLE_MS] without a command, on
 * "Disconnect", or after [MAX_BAD_SIGNATURES] forged commands. Nothing is stored; nothing leaves the home network.
 */
class PhoneRemoteServer(private val context: Context) {

    sealed interface State {
        data object Off : State

        /** On at [url] (the QR code's address, secret included); [connected] once a phone has sent a signed command. */
        data class On(val url: String, val connected: Boolean) : State

        /** Turned itself off: idle, forged commands, or no network to listen on. */
        data class Stopped(val reason: StopReason) : State
    }

    enum class StopReason { IDLE, FORGED, NO_NETWORK }

    /** One search result for the phone: a movie's id, title in the TV's language, and years. */
    data class Result(val id: String, val title: String, val years: String)

    /** The answer to a command: a status word the page knows (remote.js STATUS_WORDS), and search results. */
    data class Reply(val status: String, val results: List<Result> = emptyList())

    /** What SCARY's screen does with a command; set by MainActivity while it exists. */
    fun interface Handler {
        suspend fun handle(request: RemoteProtocol.Request): Reply
    }

    @Volatile
    var handler: Handler? = null

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var socket: ServerSocket? = null
    // Set under the lock, read by the connections' threads.
    @Volatile
    private var session: RemoteProtocol.Session? = null
    private val badSignatures = AtomicInteger()
    private val openConnections = AtomicInteger()

    @Volatile
    private var lastCommandAt = 0L

    // A simple rate limit: commands in the current second.
    private var windowStart = 0L
    private var windowCount = 0

    /**
     * Turns the remote on (or keeps it on) and returns the QR code's address, or null when the TV has no home network
     * address to listen on. A new secret each time it starts: an old QR code stops working.
     */
    @Synchronized
    fun start(language: Language): String? {
        (state.value as? State.On)?.let { return it.url }
        val address = homeNetworkAddress() ?: run {
            _state.value = State.Stopped(StopReason.NO_NETWORK)
            return null
        }
        val secret = ByteArray(RemoteProtocol.SECRET_BYTES).also(SecureRandom()::nextBytes)
        val server = try {
            ServerSocket(0, BACKLOG)
        } catch (e: IOException) {
            Log.w(TAG, "Could not listen: ${e.javaClass.simpleName}")
            _state.value = State.Stopped(StopReason.NO_NETWORK)
            return null
        }
        socket = server
        session = RemoteProtocol.Session(secret)
        badSignatures.set(0)
        lastCommandAt = SystemClock.elapsedRealtime()
        val url = "http://$address:${server.localPort}/#s=${RemoteProtocol.encodeSecret(secret)}&l=${language.code}"
        _state.value = State.On(url, connected = false)
        job = scope.launch {
            launch { watchIdle() }
            while (isActive) {
                val client = try {
                    server.accept()
                } catch (e: IOException) {
                    break
                }
                if (openConnections.incrementAndGet() > MAX_CONNECTIONS) {
                    openConnections.decrementAndGet()
                    runCatching { client.close() }
                    continue
                }
                launch {
                    try {
                        serve(client)
                    } finally {
                        openConnections.decrementAndGet()
                    }
                }
            }
        }
        return url
    }

    /** "Disconnect": the server stops and the secret is forgotten. */
    fun stop() = stop(null)

    @Synchronized
    private fun stop(reason: StopReason?) {
        job?.cancel()
        job = null
        runCatching { socket?.close() }
        socket = null
        session = null
        _state.value = if (reason == null) State.Off else State.Stopped(reason)
    }

    private suspend fun watchIdle() {
        while (true) {
            delay(IDLE_CHECK_MS)
            if (SystemClock.elapsedRealtime() - lastCommandAt > IDLE_MS) {
                stop(StopReason.IDLE)
                return
            }
        }
    }

    private suspend fun serve(client: Socket) {
        client.use { connection ->
            try {
                connection.soTimeout = SOCKET_TIMEOUT_MS
                val input = BufferedInputStream(connection.getInputStream())
                val head = readHead(input) ?: return
                val lines = head.split("\r\n")
                val (method, path) = lines.first().split(' ').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
                val headers = lines.drop(1).mapNotNull { line ->
                    line.indexOf(':').takeIf { it > 0 }?.let { line.substring(0, it).trim().lowercase() to line.substring(it + 1).trim() }
                }.toMap()
                val output = connection.getOutputStream()
                when {
                    method == "GET" && path.substringBefore('?') in PAGES -> {
                        val file = PAGES.getValue(path.substringBefore('?'))
                        val bytes = context.assets.open("remote/${file.first}").use { it.readBytes() }
                        respond(output, 200, file.second, bytes)
                    }
                    method == "POST" && path == "/api" -> {
                        val length = headers["content-length"]?.toIntOrNull()
                        if (length == null || length !in 1..RemoteProtocol.MAX_BODY) {
                            respondJson(output, 413, Reply("malformed"))
                            return
                        }
                        val body = String(readExactly(input, length) ?: return, Charsets.UTF_8)
                        val (code, reply) = command(body)
                        respondJson(output, code, reply)
                    }
                    else -> respond(output, 404, "text/plain; charset=utf-8", "Not found".toByteArray())
                }
            } catch (e: IOException) {
                // A phone that went away mid-request: nothing to do.
            }
        }
    }

    private suspend fun command(body: String): Pair<Int, Reply> {
        if (!allowedNow()) return 429 to Reply("busy")
        val current = session ?: return 410 to Reply("off")
        return when (val verdict = current.open(body)) {
            is Verdict.Accepted -> {
                val request = verdict.request
                // A ping only says the page is open; it does not keep the remote on.
                if (request.command != Command.PING) lastCommandAt = SystemClock.elapsedRealtime()
                (state.value as? State.On)?.takeIf { !it.connected }?.let { _state.value = it.copy(connected = true) }
                200 to handle(request)
            }
            Verdict.BadSignature -> {
                if (badSignatures.incrementAndGet() >= MAX_BAD_SIGNATURES) stop(StopReason.FORGED)
                401 to Reply("bad_signature")
            }
            Verdict.Replayed -> 409 to Reply("replay")
            Verdict.Malformed -> 400 to Reply("malformed")
        }
    }

    /** Play/Pause and the volume work whatever app is in front; the rest is SCARY's screen's to do. */
    private suspend fun handle(request: RemoteProtocol.Request): Reply {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        when (request.command) {
            Command.PLAY_PAUSE -> audio?.let {
                it.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                it.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            }
            Command.VOLUME_UP -> audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            Command.VOLUME_DOWN -> audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            Command.MUTE -> audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            Command.PING -> Unit
            else -> return handler?.handle(request) ?: Reply("not_front")
        }
        return Reply("ok")
    }

    @Synchronized
    private fun allowedNow(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - windowStart >= 1_000) {
            windowStart = now
            windowCount = 0
        }
        return ++windowCount <= MAX_COMMANDS_PER_SECOND
    }

    private fun readHead(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        var matched = 0
        while (out.size() < MAX_HEAD) {
            val b = input.read()
            if (b < 0) return null
            out.write(b)
            matched = when {
                b == END[matched].code -> matched + 1
                b == '\r'.code -> 1
                else -> 0
            }
            if (matched == END.length) return out.toString(Charsets.ISO_8859_1.name()).removeSuffix(END)
        }
        return null
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray? {
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(bytes, read, length - read)
            if (n < 0) return null
            read += n
        }
        return bytes
    }

    private fun respondJson(output: java.io.OutputStream, code: Int, reply: Reply) {
        val json = buildJsonObject {
            put("s", reply.status)
            if (reply.results.isNotEmpty()) {
                putJsonArray("r") {
                    reply.results.forEach { result ->
                        addJsonObject {
                            put("i", result.id)
                            put("t", result.title)
                            put("y", result.years)
                        }
                    }
                }
            }
        }
        respond(output, code, "application/json; charset=utf-8", json.toString().toByteArray(Charsets.UTF_8))
    }

    private fun respond(output: java.io.OutputStream, code: Int, type: String, body: ByteArray) {
        val reason = when (code) {
            200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 404 -> "Not Found"; 409 -> "Conflict"
            410 -> "Gone"; 413 -> "Payload Too Large"; 429 -> "Too Many Requests"; else -> "Error"
        }
        val head = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            append("Content-Type: $type\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("Referrer-Policy: no-referrer\r\n")
            append("X-Frame-Options: DENY\r\n")
            append("Content-Security-Policy: $CSP\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(head.toByteArray(Charsets.ISO_8859_1))
        output.write(body)
        output.flush()
    }

    /** The TV's address on the home network (Wi-Fi or Ethernet): a private IPv4 address; null when there is none. */
    private fun homeNetworkAddress(): String? = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    private companion object {
        const val TAG = "ScaryRemote"
        const val BACKLOG = 8
        const val MAX_CONNECTIONS = 4
        const val SOCKET_TIMEOUT_MS = 5_000
        const val MAX_HEAD = 8 * 1024
        const val MAX_COMMANDS_PER_SECOND = 30
        const val MAX_BAD_SIGNATURES = 20
        const val IDLE_MS = 30 * 60_000L
        const val IDLE_CHECK_MS = 30_000L
        const val END = "\r\n\r\n"
        const val CSP = "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self'; " +
            "base-uri 'none'; form-action 'none'; frame-ancestors 'none'"
        val PAGES = mapOf(
            "/" to ("index.html" to "text/html; charset=utf-8"),
            "/index.html" to ("index.html" to "text/html; charset=utf-8"),
            "/remote.js" to ("remote.js" to "text/javascript; charset=utf-8"),
            "/remote.css" to ("remote.css" to "text/css; charset=utf-8"),
        )
    }
}
