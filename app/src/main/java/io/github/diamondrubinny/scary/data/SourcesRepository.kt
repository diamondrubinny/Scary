// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.os.SystemClock
import android.util.Log
import io.github.diamondrubinny.scary.core.sources.LanHttp
import io.github.diamondrubinny.scary.core.sources.Releases
import io.github.diamondrubinny.scary.core.sources.Socks5
import io.github.diamondrubinny.scary.core.sources.StreamAddon
import io.github.diamondrubinny.scary.core.sources.Torznab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * The viewer's own sources of releases, only once they give their addresses (docs/TORRENT_SCRAPER_PLAN.md): Torznab
 * indexers (Prowlarr, Jackett) with their keys, and stream add-ons that speak Stremio's add-on protocol (StreamAddon in
 * core); up to [MAX_SOURCES], asked together. SCARY ships no address of any indexer, add-on or site, and recommends none.
 *
 * The addresses and the keys are stored encrypted (SecretStore), are not backed up, and are sent to those addresses
 * only: over HTTPS, or over plain HTTP when the address is a private home-network one (core LanHttp), where the key
 * then travels unencrypted inside that network. The key travels in the request's address, as Torznab asks, so that
 * address is never logged. A search sends the viewer's words, the key and the TV's IP address to the source; nothing
 * about the viewer's account.
 *
 * With "search through Tor" on (docs/TOR_PLAN.md), the HTTPS requests leave through the SOCKS port of a Tor app the
 * viewer runs on this device, and the source sees a Tor address instead of the TV's. SCARY carries no Tor code. When
 * that port does not answer, the search fails: it never falls back to a direct connection.
 */
class SourcesRepository(private val secrets: SecretStore) {
    enum class SaveResult { SAVED, BAD_ADDRESS, BAD_KEY, STORAGE, FULL }

    sealed interface Search {
        data class Ready(val candidates: List<Releases.Candidate>) : Search
        data object NotConfigured : Search
        data object Unreachable : Search

        /** The indexer said no: a wrong key, or its limit reached. */
        data object Refused : Search
        data object Unreadable : Search

        /** The source is a stream add-on, which answers by film only: there is no film to ask it for. */
        data object NeedsFilm : Search

        /** "Search through Tor" is on, and no Tor app answers on this device. */
        data object TorOff : Search
    }

    /** Where the viewer's sources come from: a Torznab indexer (address and key), or a stream add-on (address only). */
    private sealed interface Config {
        val address: String

        data class Indexer(override val address: String, val apiKey: String) : Config
        data class Addon(override val address: String) : Config
    }

    @Volatile
    private var configs: List<Config> = emptyList()

    @Volatile
    private var throughTor = false

    /** The VPN's kill switch (VpnRepository): while it says so, no search leaves, as if the source were unreachable. */
    @Volatile
    var blocked: () -> Boolean = { false }
    private var cachedQuery: String? = null
    private var cached: List<Releases.Candidate>? = null
    private var cachedAt = 0L

    /**
     * The sources, to show which are set, in the order they were added. An indexer by its address without the key; an
     * add-on by its host only, since the rest of its address can hold the viewer's settings.
     */
    private val _labels = MutableStateFlow<List<String>>(emptyList())
    val labels: StateFlow<List<String>> = _labels.asStateFlow()

    /** The first source, or null when none is set. */
    private val _host = MutableStateFlow<String?>(null)
    val host: StateFlow<String?> = _host.asStateFlow()

    private val _tor = MutableStateFlow(false)

    /** Whether searches go through Tor. Off unless the viewer turns it on. */
    val tor: StateFlow<Boolean> = _tor.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val root = secrets.read(SECRET_NAME)?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        // Before several sources: the one source was the object itself.
        val stored = (root?.get("sources") as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(::read) }
            ?: listOfNotNull(root?.let(::read))
        use(stored.take(MAX_SOURCES), (root?.get("tor") as? JsonPrimitive)?.booleanOrNull == true)
    }

    private fun read(o: JsonObject): Config? = runCatching {
        val url = (o["url"] as? JsonPrimitive)?.contentOrNull ?: return null
        if ((o["kind"] as? JsonPrimitive)?.contentOrNull == KIND_ADDON) {
            // Kept as the base address; checked again as the manifest's address it came from.
            StreamAddon.base("$url/manifest.json")?.let { Config.Addon(it) }
        } else {
            val endpoint = Torznab.endpoint(url)
            val key = (o["key"] as? JsonPrimitive)?.contentOrNull?.let(Torznab::apiKey)
            if (endpoint != null && key != null) Config.Indexer(endpoint, key) else null
        }
    }.getOrNull()

    private fun store(next: List<Config>, tor: Boolean): Boolean {
        if (next.isEmpty() && !tor) {
            secrets.remove(SECRET_NAME)
            return true
        }
        val text = buildJsonObject {
            put("tor", tor)
            put(
                "sources",
                buildJsonArray {
                    next.forEach { config ->
                        add(
                            buildJsonObject {
                                when (config) {
                                    is Config.Addon -> {
                                        put("kind", KIND_ADDON)
                                        put("url", config.address)
                                    }
                                    is Config.Indexer -> {
                                        put("url", config.address)
                                        put("key", config.apiKey)
                                    }
                                }
                            },
                        )
                    }
                },
            )
        }.toString()
        return secrets.write(SECRET_NAME, text)
    }

    /**
     * Adds a source of the viewer's, or replaces the one at the same address. An address that ends with /manifest.json
     * is a stream add-on's and takes no key; any other is a Torznab indexer's and needs one.
     */
    suspend fun save(address: String, key: String): SaveResult = withContext(Dispatchers.IO) {
        val added: Config = if (StreamAddon.isManifest(address)) {
            Config.Addon(StreamAddon.base(address) ?: return@withContext SaveResult.BAD_ADDRESS)
        } else {
            val endpoint = Torznab.endpoint(address) ?: return@withContext SaveResult.BAD_ADDRESS
            Config.Indexer(endpoint, Torznab.apiKey(key) ?: return@withContext SaveResult.BAD_KEY)
        }
        val current = configs
        val at = current.indexOfFirst { it.address == added.address }
        val next = when {
            at >= 0 -> current.toMutableList().also { it[at] = added }
            current.size >= MAX_SOURCES -> return@withContext SaveResult.FULL
            else -> current + added
        }
        if (!store(next, throughTor)) return@withContext SaveResult.STORAGE
        use(next, throughTor)
        SaveResult.SAVED
    }

    /** Removes the source at [index] of [labels]; with null, all of them. */
    suspend fun remove(index: Int? = null) = withContext(Dispatchers.IO) {
        val next = if (index == null) emptyList() else configs.filterIndexed { i, _ -> i != index }
        store(next, throughTor)
        use(next, throughTor)
    }

    suspend fun setTor(on: Boolean) = withContext(Dispatchers.IO) {
        if (store(configs, on)) use(configs, on)
    }

    private fun use(next: List<Config>, tor: Boolean) {
        configs = next
        throughTor = tor
        cached = null
        val labels = next.map { config ->
            when (config) {
                is Config.Indexer -> config.address.substringAfter("://")
                is Config.Addon -> Regex("^https?://([^/:]+)").find(config.address)?.groupValues?.get(1).orEmpty()
            }
        }
        _labels.value = labels
        _host.value = labels.firstOrNull()
        _tor.value = tor
    }

    /**
     * Asks all of the viewer's sources together, and puts their answers in one list. An indexer is asked for [query];
     * with a film's [imdbId], for that film first, and for the words only when that found nothing (not every indexer
     * searches by number). A stream add-on answers by film only: it is asked for [filmImdbId], and without one says
     * [Search.NeedsFilm]. The last answer is kept for [CACHE_MS], so filtering and sorting ask nothing.
     */
    suspend fun search(query: String, imdbId: String? = null, filmImdbId: String? = imdbId): Search = withContext(Dispatchers.IO) {
        val all = configs
        if (all.isEmpty()) return@withContext Search.NotConfigured
        if (blocked()) return@withContext Search.Unreachable
        val words = Torznab.cleanQuery(query)
        val key = "${imdbId.orEmpty()}|${filmImdbId.orEmpty()}|${words.orEmpty()}"
        cached?.takeIf { cachedQuery == key && SystemClock.elapsedRealtime() - cachedAt < CACHE_MS }?.let { return@withContext Search.Ready(it) }
        val answers = coroutineScope { all.map { async { askOne(it, words, imdbId, filmImdbId) } }.awaitAll() }
        val result = merge(answers)
        (result as? Search.Ready)?.let {
            cachedQuery = key
            cached = it.candidates
            cachedAt = SystemClock.elapsedRealtime()
        }
        result
    }

    private fun askOne(config: Config, words: String?, imdbId: String?, filmImdbId: String?): Search = when (config) {
        is Config.Addon -> filmImdbId?.let { StreamAddon.movieUrl(config.address, it) }?.let(::askAddon) ?: Search.NeedsFilm
        is Config.Indexer -> {
            var result: Search = imdbId?.let { Torznab.movieUrl(config.address, config.apiKey, it) }?.let(::ask) ?: Search.Ready(emptyList())
            if ((result as? Search.Ready)?.candidates?.isEmpty() != false && words != null) {
                Torznab.searchUrl(config.address, config.apiKey, words)?.let { result = ask(it) }
            }
            result
        }
    }

    /**
     * One answer from several: every release found, if any source found one. Otherwise what went wrong, the most
     * useful to know first; then an empty list if any source answered at all.
     */
    private fun merge(answers: List<Search>): Search {
        val found = answers.filterIsInstance<Search.Ready>().flatMap { it.candidates }
        if (found.isNotEmpty()) return Search.Ready(found)
        for (failure in listOf(Search.TorOff, Search.Refused, Search.Unreachable, Search.Unreadable)) if (failure in answers) return failure
        return if (answers.any { it is Search.Ready }) Search.Ready(emptyList()) else Search.NeedsFilm
    }

    /** A stream add-on's answer for one film; the address is never logged (it can hold the viewer's settings). */
    private fun askAddon(url: String): Search = fetch(url, "application/json") { body ->
        StreamAddon.parse(body)?.let { Search.Ready(it) } ?: Search.Unreadable
    }

    private fun ask(url: String): Search = fetch(url, "application/rss+xml, application/xml", ::readFeed)

    /** One request, by the way its address asks for: at home, through Tor when the viewer turned that on, or direct. */
    private fun fetch(url: String, accept: String, read: (String) -> Search): Search {
        // A home-network address is not reachable from Tor, and has nothing to hide from: it is asked directly.
        LanHttp.target(url)?.let { return askAtHome(it, read) }
        return if (throughTor) getThroughTor(url, accept, read) else get(url, accept, read)
    }

    private fun readFeed(body: String): Search = when (val answer = Torznab.parse(body)) {
        is Torznab.Answer.Results -> Search.Ready(answer.candidates)
        is Torznab.Answer.Refused -> Search.Refused
        Torznab.Answer.Unreadable -> Search.Unreadable
    }

    enum class Health { OK, NOT_CONFIGURED, UNREACHABLE, REFUSED, UNREADABLE, TOR_OFF }

    /** Whether one of the viewer's sources ([label], as in [labels]) answers, and how long it took. */
    data class Check(val health: Health, val millis: Long, val label: String = "")

    /**
     * Asks each of the viewer's sources the smallest question it has (an indexer: what it can do; an add-on: its
     * manifest), at their word, to show whether the address and the key work. Nothing is searched for.
     */
    suspend fun check(): List<Check> = withContext(Dispatchers.IO) {
        val all = configs
        val names = _labels.value
        if (blocked()) return@withContext all.indices.map { Check(Health.UNREACHABLE, 0, names.getOrElse(it) { "" }) }
        coroutineScope {
            all.mapIndexed { index, config ->
                async {
                    val started = SystemClock.elapsedRealtime()
                    val health = when (checkOne(config)) {
                        is Search.Ready -> Health.OK
                        Search.Refused -> Health.REFUSED
                        Search.Unreadable -> Health.UNREADABLE
                        Search.TorOff -> Health.TOR_OFF
                        else -> Health.UNREACHABLE
                    }
                    Check(health, SystemClock.elapsedRealtime() - started, names.getOrElse(index) { "" })
                }
            }.awaitAll()
        }
    }

    private fun checkOne(config: Config): Search = when (config) {
        is Config.Addon -> fetch(StreamAddon.manifestUrl(config.address), "application/json") { body ->
            if (StreamAddon.isManifestAnswer(body)) Search.Ready(emptyList()) else Search.Unreadable
        }
        is Config.Indexer -> fetch(Torznab.capsUrl(config.address, config.apiKey), "application/xml") { body ->
            when (Torznab.capsAnswer(body)) {
                is Torznab.Answer.Results -> Search.Ready(emptyList())
                is Torznab.Answer.Refused -> Search.Refused
                Torznab.Answer.Unreadable -> Search.Unreadable
            }
        }
    }

    private fun answered(code: Int, body: String?, read: (String) -> Search): Search = when {
        code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN || code == HTTP_TOO_MANY_REQUESTS -> Search.Refused
        // A redirect is not followed: the key must not go to another address.
        code != HttpURLConnection.HTTP_OK -> Search.Unreachable
        else -> body?.let(read) ?: Search.Unreadable
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
            answered(response.code, response.body, read)
        }
    } catch (e: IOException) {
        // Only the kind of failure: the address holds the key.
        Log.w(TAG, "Home indexer request failed: ${e.javaClass.simpleName}")
        Search.Unreachable
    }

    /**
     * One HTTPS GET through the Tor app on this device (docs/TOR_PLAN.md): a socket to its SOCKS port on 127.0.0.1,
     * the source's host given to it by name (core Socks5) so the lookup happens inside Tor, then TLS to the source
     * itself over that socket, with its certificate and name checked as on any HTTPS connection. The exit sees the
     * source's host and nothing of the request. No Tor app listening is [Search.TorOff]: never a direct connection.
     */
    private fun getThroughTor(url: String, accept: String, read: (String) -> Search): Search {
        val target = Socks5.target(url) ?: return Search.Unreachable
        val proxy = Socket()
        try {
            proxy.soTimeout = TOR_TIMEOUT_MS
            try {
                proxy.connect(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), TOR_SOCKS_PORT), TOR_CONNECT_MS)
            } catch (e: IOException) {
                return Search.TorOff
            }
            val input = proxy.getInputStream()
            val output = proxy.getOutputStream()
            output.write(Socks5.GREETING)
            output.flush()
            // Something else listens on that port: not a Tor app.
            if (!Socks5.greeted(input)) return Search.TorOff
            output.write(Socks5.connect(target.host, target.port))
            output.flush()
            if (!Socks5.connected(input)) return Search.Unreachable
            val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(proxy, target.host, target.port, true) as SSLSocket
            tls.use {
                it.sslParameters = it.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                it.startHandshake()
                if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(target.host, it.session)) return Search.Unreachable
                it.outputStream.apply {
                    write(LanHttp.request(target.host, target.port, target.pathAndQuery, defaultPort = 443, accept = accept))
                    flush()
                }
                val response = LanHttp.read(it.inputStream.buffered(), Torznab.MAX_ANSWER_BYTES)
                return answered(response.code, response.body, read)
            }
        } catch (e: ConnectException) {
            return Search.TorOff
        } catch (e: IOException) {
            // Only the kind of failure: the address holds the key or the viewer's settings.
            Log.w(TAG, "Source request through Tor failed: ${e.javaClass.simpleName}")
            return Search.Unreachable
        } finally {
            runCatching { proxy.close() }
        }
    }

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
            answered(code, if (code == HttpURLConnection.HTTP_OK) readLimited(connection) else null, read)
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

    companion object {
        /** The most sources asked together: each search goes to all of them. */
        const val MAX_SOURCES = 4

        private const val TAG = "ScarySources"
        private const val SECRET_NAME = "torznab_indexer"
        private const val KIND_ADDON = "addon"
        private const val TIMEOUT_MS = 20_000
        private const val LAN_CONNECT_MS = 8_000
        private const val CACHE_MS = 10 * 60_000L
        private const val HTTP_TOO_MANY_REQUESTS = 429

        /** Where a Tor app on the device listens for SOCKS (Orbot's and Tor's own default port). */
        private const val TOR_SOCKS_PORT = 9050
        private const val TOR_CONNECT_MS = 3_000

        /** Tor is slow: a circuit and an answer through three relays take longer than a direct one. */
        private const val TOR_TIMEOUT_MS = 45_000
    }
}
