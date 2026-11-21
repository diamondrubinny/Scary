// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.os.SystemClock
import android.util.Log
import io.github.diamondrubinny.scary.core.sources.LanHttp
import io.github.diamondrubinny.scary.core.sources.Releases
import io.github.diamondrubinny.scary.core.sources.StreamAddon
import io.github.diamondrubinny.scary.core.sources.Torznab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/**
 * The viewer's own source of releases, only once they give its address (docs/TORRENT_SCRAPER_PLAN.md): a Torznab
 * indexer (Prowlarr, Jackett) with its key, or a stream add-on that speaks Stremio's add-on protocol (StreamAddon in
 * core). SCARY ships no address of any indexer, add-on or site, and recommends none.
 *
 * The address and the key are stored encrypted (SecretStore), are not backed up, and are sent to that address only:
 * over HTTPS, or over plain HTTP when the address is a private home-network one (core LanHttp), where the key then
 * travels unencrypted inside that network. The key travels in the request's address, as Torznab asks, so that address is never logged. A search
 * sends the viewer's words, the key and the TV's IP address to the indexer; nothing about the viewer's account.
 */
class SourcesRepository(private val secrets: SecretStore) {
    enum class SaveResult { SAVED, BAD_ADDRESS, BAD_KEY, STORAGE }

    sealed interface Search {
        data class Ready(val candidates: List<Releases.Candidate>) : Search
        data object NotConfigured : Search
        data object Unreachable : Search

        /** The indexer said no: a wrong key, or its limit reached. */
        data object Refused : Search
        data object Unreadable : Search

        /** The source is a stream add-on, which answers by film only: there is no film to ask it for. */
        data object NeedsFilm : Search
    }

    /** Where the viewer's sources come from: a Torznab indexer (address and key), or a stream add-on (address only). */
    private sealed interface Config {
        val address: String

        data class Indexer(override val address: String, val apiKey: String) : Config
        data class Addon(override val address: String) : Config
    }

    private var config: Config? = null

    /** The VPN's kill switch (VpnRepository): while it says so, no search leaves, as if the source were unreachable. */
    @Volatile
    var blocked: () -> Boolean = { false }
    private var cachedQuery: String? = null
    private var cached: List<Releases.Candidate>? = null
    private var cachedAt = 0L

    /** The source's host, to show which one is set; null when none is. Never the key or the rest of the address. */
    private val _host = MutableStateFlow<String?>(null)
    val host: StateFlow<String?> = _host.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val stored = secrets.read(SECRET_NAME)?.let { text ->
            runCatching {
                val o = Json.parseToJsonElement(text).jsonObject
                val url = (o["url"] as? JsonPrimitive)?.contentOrNull ?: return@runCatching null
                if ((o["kind"] as? JsonPrimitive)?.contentOrNull == KIND_ADDON) {
                    // Kept as the base address; checked again as the manifest's address it came from.
                    StreamAddon.base("$url/manifest.json")?.let { Config.Addon(it) }
                } else {
                    val endpoint = Torznab.endpoint(url)
                    val key = (o["key"] as? JsonPrimitive)?.contentOrNull?.let(Torznab::apiKey)
                    if (endpoint != null && key != null) Config.Indexer(endpoint, key) else null
                }
            }.getOrNull()
        }
        use(stored)
    }

    /**
     * Saves the viewer's source. An address that ends with /manifest.json is a stream add-on's and takes no key;
     * any other is a Torznab indexer's and needs one.
     */
    suspend fun save(address: String, key: String): SaveResult = withContext(Dispatchers.IO) {
        val next: Config
        val text: String
        if (StreamAddon.isManifest(address)) {
            val base = StreamAddon.base(address) ?: return@withContext SaveResult.BAD_ADDRESS
            next = Config.Addon(base)
            text = buildJsonObject {
                put("kind", KIND_ADDON)
                put("url", base)
            }.toString()
        } else {
            val endpoint = Torznab.endpoint(address) ?: return@withContext SaveResult.BAD_ADDRESS
            val apiKey = Torznab.apiKey(key) ?: return@withContext SaveResult.BAD_KEY
            next = Config.Indexer(endpoint, apiKey)
            text = buildJsonObject {
                put("url", endpoint)
                put("key", apiKey)
            }.toString()
        }
        if (!secrets.write(SECRET_NAME, text)) return@withContext SaveResult.STORAGE
        use(next)
        SaveResult.SAVED
    }

    suspend fun remove() = withContext(Dispatchers.IO) {
        secrets.remove(SECRET_NAME)
        use(null)
    }

    private fun use(next: Config?) {
        config = next
        cached = null
        _host.value = next?.let { Regex("^https?://([^/:]+)").find(it.address)?.groupValues?.get(1) }
    }

    /**
     * Asks the viewer's source. An indexer is asked for [query]; with a film's [imdbId], for that film first, and for
     * the words only when that found nothing (not every indexer searches by number). A stream add-on answers by film
     * only: it is asked for [filmImdbId], and without one says [Search.NeedsFilm]. The last answer is kept for
     * [CACHE_MS], so filtering and sorting ask nothing.
     */
    suspend fun search(query: String, imdbId: String? = null, filmImdbId: String? = imdbId): Search = withContext(Dispatchers.IO) {
        if (config != null && blocked()) return@withContext Search.Unreachable
        when (val current = config) {
            null -> Search.NotConfigured
            is Config.Addon -> {
                val url = filmImdbId?.let { StreamAddon.movieUrl(current.address, it) } ?: return@withContext Search.NeedsFilm
                remembered("addon|$filmImdbId") { askAddon(url) }
            }
            is Config.Indexer -> {
                val words = Torznab.cleanQuery(query)
                val byNumber = imdbId?.let { Torznab.movieUrl(current.address, current.apiKey, it) }
                if (words == null && byNumber == null) return@withContext Search.Ready(emptyList())
                remembered("${if (byNumber != null) imdbId else ""}|${words.orEmpty()}") {
                    var result: Search = byNumber?.let(::ask) ?: Search.Ready(emptyList())
                    if ((result as? Search.Ready)?.candidates?.isEmpty() != false && words != null) {
                        Torznab.searchUrl(current.address, current.apiKey, words)?.let { result = ask(it) }
                    }
                    result
                }
            }
        }
    }

    private inline fun remembered(key: String, ask: () -> Search): Search {
        cached?.takeIf { cachedQuery == key && SystemClock.elapsedRealtime() - cachedAt < CACHE_MS }?.let { return Search.Ready(it) }
        val result = ask()
        (result as? Search.Ready)?.let {
            cachedQuery = key
            cached = it.candidates
            cachedAt = SystemClock.elapsedRealtime()
        }
        return result
    }

    /** A stream add-on's answer for one film, over HTTPS; the address is never logged (it can hold the viewer's settings). */
    private fun askAddon(url: String): Search = get(url, "application/json") { body ->
        StreamAddon.parse(body)?.let { Search.Ready(it) } ?: Search.Unreadable
    }

    private fun ask(url: String): Search = LanHttp.target(url)?.let { askAtHome(it, ::readFeed) } ?: askSecurely(url)

    private fun readFeed(body: String): Search = when (val answer = Torznab.parse(body)) {
        is Torznab.Answer.Results -> Search.Ready(answer.candidates)
        is Torznab.Answer.Refused -> Search.Refused
        Torznab.Answer.Unreadable -> Search.Unreadable
    }

    enum class Health { OK, NOT_CONFIGURED, UNREACHABLE, REFUSED, UNREADABLE }

    /** Whether the viewer's source answers, and how long it took. */
    data class Check(val health: Health, val millis: Long)

    /**
     * Asks the viewer's source the smallest question it has (an indexer: what it can do; an add-on: its manifest), at
     * their word, to show whether the address and the key work. Nothing is searched for.
     */
    suspend fun check(): Check = withContext(Dispatchers.IO) {
        val current = config ?: return@withContext Check(Health.NOT_CONFIGURED, 0)
        if (blocked()) return@withContext Check(Health.UNREACHABLE, 0)
        val started = SystemClock.elapsedRealtime()
        val answer = when (current) {
            is Config.Addon -> get(StreamAddon.manifestUrl(current.address), "application/json") { body ->
                if (StreamAddon.isManifestAnswer(body)) Search.Ready(emptyList()) else Search.Unreadable
            }
            is Config.Indexer -> {
                val read = { body: String ->
                    when (Torznab.capsAnswer(body)) {
                        is Torznab.Answer.Results -> Search.Ready(emptyList())
                        is Torznab.Answer.Refused -> Search.Refused
                        Torznab.Answer.Unreadable -> Search.Unreadable
                    }
                }
                val url = Torznab.capsUrl(current.address, current.apiKey)
                LanHttp.target(url)?.let { askAtHome(it, read) } ?: get(url, "application/xml", read)
            }
        }
        val health = when (answer) {
            is Search.Ready -> Health.OK
            Search.Refused -> Health.REFUSED
            Search.Unreadable -> Health.UNREADABLE
            else -> Health.UNREACHABLE
        }
        Check(health, SystemClock.elapsedRealtime() - started)
    }

    /**
     * The viewer's indexer at a private home address, over plain HTTP (core LanHttp): a socket of SCARY's own, since
     * the app's cleartext rule stays closed for everything else. The address was checked when it was saved, and the
     * socket is checked again once connected: it must have reached a private address.
     */
    private fun askAtHome(target: LanHttp.Target, read: (String) -> Search): Search = try {
        Socket().use { socket ->
            socket.soTimeout = TIMEOUT_MS
            socket.connect(InetSocketAddress(InetAddress.getByName(target.host), target.port), LAN_CONNECT_MS)
            if (!socket.inetAddress.isSiteLocalAddress) return Search.Unreachable
            socket.getOutputStream().apply {
                write(LanHttp.request(target))
                flush()
            }
            val response = LanHttp.read(socket.getInputStream().buffered(), Torznab.MAX_ANSWER_BYTES)
            when {
                response.code == HttpURLConnection.HTTP_UNAUTHORIZED || response.code == HttpURLConnection.HTTP_FORBIDDEN || response.code == HTTP_TOO_MANY_REQUESTS -> Search.Refused
                // A redirect is not followed: the key must not go to another address.
                response.code != HttpURLConnection.HTTP_OK -> Search.Unreachable
                else -> response.body?.let(read) ?: Search.Unreadable
            }
        }
    } catch (e: IOException) {
        // Only the kind of failure: the address holds the key.
        Log.w(TAG, "Home indexer request failed: ${e.javaClass.simpleName}")
        Search.Unreachable
    }

    private fun askSecurely(url: String): Search = get(url, "application/rss+xml, application/xml", ::readFeed)

    /** One HTTPS GET, read up to the size limit and handed to [read]; redirects are not followed. */
    private fun get(url: String, accept: String, read: (String) -> Search): Search {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                // The key, or the settings in the address, must not follow a redirect to another host.
                instanceFollowRedirects = false
                setRequestProperty("Accept", accept)
                setRequestProperty("User-Agent", "SCARY-launcher")
            }
            val code = connection.responseCode
            when {
                code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN || code == HTTP_TOO_MANY_REQUESTS -> Search.Refused
                code != HttpURLConnection.HTTP_OK -> Search.Unreachable
                else -> readLimited(connection)?.let(read) ?: Search.Unreadable
            }
        } catch (e: IOException) {
            // Only the kind of failure: the address holds the key or the viewer's settings.
            Log.w(TAG, "Source request failed: ${e.javaClass.simpleName}")
            Search.Unreachable
        } finally {
            connection?.disconnect()
        }
    }

    private fun readLimited(connection: HttpURLConnection): String? = connection.inputStream.use { stream ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > Torznab.MAX_ANSWER_BYTES) return null
        }
        out.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val TAG = "ScarySources"
        const val SECRET_NAME = "torznab_indexer"
        const val KIND_ADDON = "addon"
        const val TIMEOUT_MS = 20_000
        const val LAN_CONNECT_MS = 8_000
        const val CACHE_MS = 10 * 60_000L
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
