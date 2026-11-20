// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.os.SystemClock
import android.util.Log
import io.github.diamondrubinny.scary.core.sources.Releases
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
import java.net.URI
import java.net.URL

/**
 * The viewer's own Torznab indexer (Prowlarr, Jackett), only in a build made with the sources feature and only once
 * they give its address and key (docs/TORRENT_SCRAPER_PLAN.md). SCARY ships no address of any indexer or site.
 *
 * The address and the key are stored encrypted (SecretStore), are not backed up, and are sent to that address only,
 * over HTTPS. The key travels in the request's address, as Torznab asks, so that address is never logged. A search
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
    }

    private data class Config(val endpoint: String, val apiKey: String)

    private var config: Config? = null
    private var cachedQuery: String? = null
    private var cached: List<Releases.Candidate>? = null
    private var cachedAt = 0L

    /** The indexer's host, to show which one is set; null when none is. Never the key. */
    private val _host = MutableStateFlow<String?>(null)
    val host: StateFlow<String?> = _host.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val stored = secrets.read(SECRET_NAME)?.let { text ->
            runCatching {
                val o = Json.parseToJsonElement(text).jsonObject
                val endpoint = (o["url"] as? JsonPrimitive)?.contentOrNull?.let(Torznab::endpoint)
                val key = (o["key"] as? JsonPrimitive)?.contentOrNull?.let(Torznab::apiKey)
                if (endpoint != null && key != null) Config(endpoint, key) else null
            }.getOrNull()
        }
        use(stored)
    }

    suspend fun save(address: String, key: String): SaveResult = withContext(Dispatchers.IO) {
        val endpoint = Torznab.endpoint(address) ?: return@withContext SaveResult.BAD_ADDRESS
        val apiKey = Torznab.apiKey(key) ?: return@withContext SaveResult.BAD_KEY
        val text = buildJsonObject {
            put("url", endpoint)
            put("key", apiKey)
        }.toString()
        if (!secrets.write(SECRET_NAME, text)) return@withContext SaveResult.STORAGE
        use(Config(endpoint, apiKey))
        SaveResult.SAVED
    }

    suspend fun remove() = withContext(Dispatchers.IO) {
        secrets.remove(SECRET_NAME)
        use(null)
    }

    private fun use(next: Config?) {
        config = next
        cached = null
        _host.value = next?.let { runCatching { URI(it.endpoint).host }.getOrNull() }
    }

    /**
     * Asks the indexer for [query]; with a film's [imdbId], for that film first, and for the words only when that
     * found nothing (not every indexer searches by number). The last answer is kept for [CACHE_MS], so filtering and
     * sorting ask nothing.
     */
    suspend fun search(query: String, imdbId: String? = null): Search = withContext(Dispatchers.IO) {
        val current = config ?: return@withContext Search.NotConfigured
        val words = Torznab.cleanQuery(query)
        val byNumber = imdbId?.let { Torznab.movieUrl(current.endpoint, current.apiKey, it) }
        if (words == null && byNumber == null) return@withContext Search.Ready(emptyList())
        val cacheKey = "${if (byNumber != null) imdbId else ""}|${words.orEmpty()}"
        cached?.takeIf { cachedQuery == cacheKey && SystemClock.elapsedRealtime() - cachedAt < CACHE_MS }?.let { return@withContext Search.Ready(it) }
        var result: Search = byNumber?.let(::ask) ?: Search.Ready(emptyList())
        if ((result as? Search.Ready)?.candidates?.isEmpty() != false && words != null) {
            Torznab.searchUrl(current.endpoint, current.apiKey, words)?.let { result = ask(it) }
        }
        (result as? Search.Ready)?.let {
            cachedQuery = cacheKey
            cached = it.candidates
            cachedAt = SystemClock.elapsedRealtime()
        }
        result
    }

    private fun ask(url: String): Search {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                // The key must not follow a redirect to another host.
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/rss+xml, application/xml")
                setRequestProperty("User-Agent", "SCARY-launcher")
            }
            val code = connection.responseCode
            when {
                code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN || code == HTTP_TOO_MANY_REQUESTS -> Search.Refused
                code != HttpURLConnection.HTTP_OK -> Search.Unreachable
                else -> when (val answer = readLimited(connection)?.let(Torznab::parse)) {
                    is Torznab.Answer.Results -> Search.Ready(answer.candidates)
                    is Torznab.Answer.Refused -> Search.Refused
                    Torznab.Answer.Unreadable, null -> Search.Unreadable
                }
            }
        } catch (e: IOException) {
            // Only the kind of failure: the address holds the key.
            Log.w(TAG, "Indexer request failed: ${e.javaClass.simpleName}")
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
        const val TIMEOUT_MS = 20_000
        const val CACHE_MS = 10 * 60_000L
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
