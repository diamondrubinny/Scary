// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.content.Context
import android.util.Log
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.tmdb.Tmdb
import io.github.diamondrubinny.scary.core.tmdb.TmdbAuth
import io.github.diamondrubinny.scary.core.tmdb.TmdbCache
import io.github.diamondrubinny.scary.core.tmdb.TmdbRow
import io.github.diamondrubinny.scary.core.tmdb.RegionOffers
import io.github.diamondrubinny.scary.core.tmdb.WatchProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import javax.net.ssl.HttpsURLConnection

enum class TmdbKeyResult { SAVED, INVALID_FORMAT, REJECTED, UNREACHABLE, STORAGE_FAILED }

/**
 * Optional TMDB layer with the user's own key: Hebrew titles and synopses, sharper art and extra rows.
 * The key is stored encrypted, sent only to api.themoviedb.org over HTTPS, and never logged or cached.
 */
class TmdbRepository(context: Context, private val secrets: SecretStore) {
    private val cacheFile = File(context.filesDir, "tmdb_cache.json")

    private val _keyMask = MutableStateFlow<String?>(null)

    /** "…a1b2" when a key is set, null otherwise. The UI never sees the key itself. */
    val keyMask: StateFlow<String?> = _keyMask.asStateFlow()

    private var auth: TmdbAuth? = null

    /** Reads the stored key once; called off the main thread at start-up. */
    suspend fun load() = withContext(Dispatchers.IO) {
        auth = secrets.read(SECRET_NAME)?.let(Tmdb::parseAuth)
        _keyMask.value = auth?.let { Tmdb.mask(it.value()) }
    }

    suspend fun setKey(input: String): TmdbKeyResult = withContext(Dispatchers.IO) {
        val candidate = Tmdb.parseAuth(input) ?: return@withContext TmdbKeyResult.INVALID_FORMAT
        val response = get(Tmdb.configurationUrl(), candidate)
        when {
            response == null -> TmdbKeyResult.UNREACHABLE
            response.code == HttpURLConnection.HTTP_UNAUTHORIZED || response.code == HttpURLConnection.HTTP_FORBIDDEN -> TmdbKeyResult.REJECTED
            response.code != HttpURLConnection.HTTP_OK -> TmdbKeyResult.UNREACHABLE
            !secrets.write(SECRET_NAME, candidate.value()) -> TmdbKeyResult.STORAGE_FAILED
            else -> {
                auth = candidate
                cacheFile.delete()
                _keyMask.value = Tmdb.mask(candidate.value())
                TmdbKeyResult.SAVED
            }
        }
    }

    suspend fun clearKey() = withContext(Dispatchers.IO) {
        secrets.remove(SECRET_NAME)
        cacheFile.delete()
        auth = null
        _keyMask.value = null
    }

    /** The cache only counts when it was fetched in the current UI language. */
    suspend fun cached(language: Language): TmdbCache? = withContext(Dispatchers.IO) {
        if (auth == null || !cacheFile.exists()) return@withContext null
        runCatching { TmdbCache.decode(cacheFile.readText()) }.getOrNull()?.takeIf { it.language == language.code }
    }

    /** Hebrew info for the given movies plus the discover rows. Null without a key or when offline. */
    suspend fun refresh(imdbIds: List<String>, language: Language): TmdbCache? = withContext(Dispatchers.IO) {
        val key = auth ?: return@withContext null
        val info = coroutineScope {
            imdbIds.distinct().chunked(PARALLEL_REQUESTS).flatMap { chunk ->
                chunk.map { id -> async { body(Tmdb.findUrl(id, language), key)?.let { Tmdb.parseFind(it, language) }?.let { id to it } } }.awaitAll()
            }
        }.filterNotNull().toMap()
        val today = LocalDate.now()
        val rows = TmdbRow.entries.mapNotNull { row ->
            val results = body(Tmdb.discoverUrl(row, today, language), key)?.let(Tmdb::parseDiscover).orEmpty().take(ROW_SIZE)
            val metas = coroutineScope {
                results.chunked(PARALLEL_REQUESTS).flatMap { chunk ->
                    chunk.map { r ->
                        async { body(Tmdb.externalIdsUrl(r.tmdbId), key)?.let(Tmdb::parseImdbId)?.let { Tmdb.toMeta(r, it, language) } }
                    }.awaitAll()
                }
            }.filterNotNull()
            metas.takeIf { it.isNotEmpty() }?.let { Tmdb.rowOf(row, it) }
        }
        if (info.isEmpty() && rows.isEmpty()) return@withContext null
        TmdbCache(System.currentTimeMillis(), language.code, info, rows).also { cache ->
            runCatching { cacheFile.writeText(cache.encode()) }
                .onFailure { Log.w(TAG, "Could not save TMDB cache: ${it.javaClass.simpleName}") }
        }
    }

    /** Where a TMDB row goes on after the daily refresh (its first page); null for rows that are not TMDB's. */
    fun endlessStart(rowId: String): Int? = TmdbRow.entries.firstOrNull { it.id == rowId }?.let { 2 }

    /**
     * Page [page] of the TMDB row [rowId], as Cinemeta-style entries with their IMDb ids, loaded while the viewer
     * scrolls (memory only). Its `next` is null at TMDB's last page or page 500. Null without a key or when offline.
     */
    suspend fun loadPage(rowId: String, page: Int, language: Language): OnlinePage? = withContext(Dispatchers.IO) {
        val key = auth ?: return@withContext null
        val row = TmdbRow.entries.firstOrNull { it.id == rowId } ?: return@withContext null
        if (page !in 1..Tmdb.MAX_PAGE) return@withContext OnlinePage(emptyList(), null)
        val results = body(Tmdb.discoverUrl(row, LocalDate.now(), language, page), key)?.let(Tmdb::parseDiscover)
            ?: return@withContext null
        val metas = coroutineScope {
            results.chunked(PARALLEL_REQUESTS).flatMap { chunk ->
                chunk.map { r -> async { body(Tmdb.externalIdsUrl(r.tmdbId), key)?.let(Tmdb::parseImdbId)?.let { Tmdb.toMeta(r, it, language) } } }
                    .awaitAll()
            }
        }.filterNotNull()
        OnlinePage(metas, if (results.isEmpty() || page >= Tmdb.MAX_PAGE) null else page + 1)
    }

    /** IMDb id to TMDB id, so a movie page opened again needs one request, not two (and the account's lists). */
    private val tmdbIds = HashMap<String, Long>()

    /** Watch providers by TMDB id, every country, newest last. In memory only, for [PROVIDERS_MAX_AGE_MS]. */
    private val providers = object : LinkedHashMap<Long, Pair<Long, Map<String, RegionOffers>>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Pair<Long, Map<String, RegionOffers>>>) =
            size > PROVIDERS_KEPT
    }

    /**
     * Where the movie can be rented, bought or streamed, in every country (data by JustWatch, via TMDB). The request
     * carries only the movie's id: the viewer's country is picked on the device. Null without a key or when offline.
     */
    suspend fun watchProviders(imdbId: String): Map<String, RegionOffers>? = withContext(Dispatchers.IO) {
        val key = auth ?: return@withContext null
        val tmdbId = tmdbIdOf(imdbId) ?: return@withContext null
        val now = System.currentTimeMillis()
        synchronized(providers) { providers[tmdbId]?.takeIf { now - it.first < PROVIDERS_MAX_AGE_MS }?.second }
            ?.let { return@withContext it }
        val offers = body(WatchProviders.url(tmdbId), key)?.let(WatchProviders::parse) ?: return@withContext null
        synchronized(providers) { providers[tmdbId] = now to offers }
        offers
    }

    /** An answer from TMDB: the status code, and the body of a successful (2xx) answer. */
    data class Response(val code: Int, val body: String?) {
        val ok: Boolean get() = code in 200..299
    }

    /** Whether the viewer has added a key: everything else TMDB offers needs one. */
    val hasKey: Boolean get() = auth != null

    /**
     * A request to TMDB with the viewer's key, for their account (TmdbAccountRepository): [method] GET, POST or
     * DELETE, and a JSON body. Null without a key or when TMDB cannot be reached. Never logs the URL or the body.
     */
    suspend fun call(method: String, url: String, jsonBody: String? = null): Response? = withContext(Dispatchers.IO) {
        val key = auth ?: return@withContext null
        request(method, url, key, jsonBody)
    }

    /** IMDb id to TMDB id ("find"), remembered on the TV. Null when TMDB does not know the film or cannot be reached. */
    suspend fun tmdbIdOf(imdbId: String): Long? = withContext(Dispatchers.IO) {
        synchronized(tmdbIds) { tmdbIds[imdbId] }?.let { return@withContext it }
        val key = auth ?: return@withContext null
        val url = runCatching { Tmdb.findUrl(imdbId, Language.EN) }.getOrNull() ?: return@withContext null
        val found = body(url, key)?.let { Tmdb.parseFind(it, Language.EN) }?.tmdbId ?: return@withContext null
        remember(imdbId, found)
        found
    }

    /** TMDB id to IMDb id ("external_ids"), remembered on the TV. */
    suspend fun imdbIdOf(tmdbId: Long): String? = withContext(Dispatchers.IO) {
        synchronized(tmdbIds) { imdbIds[tmdbId] }?.let { return@withContext it }
        val key = auth ?: return@withContext null
        val found = body(Tmdb.externalIdsUrl(tmdbId), key)?.let(Tmdb::parseImdbId) ?: return@withContext null
        remember(found, tmdbId)
        found
    }

    private val imdbIds = HashMap<Long, String>()
    private val idsFile = File(context.filesDir, "tmdb_ids.json")
    private var idsLoaded = false

    /** Both ways, and on disk: syncing a list of hundreds of films looks each one up once, not every time. */
    private fun remember(imdbId: String, tmdbId: Long) {
        synchronized(tmdbIds) {
            loadIds()
            if (tmdbIds[imdbId] == tmdbId && imdbIds[tmdbId] == imdbId) return
            if (tmdbIds.size >= MAX_IDS) {
                tmdbIds.clear()
                imdbIds.clear()
            }
            tmdbIds[imdbId] = tmdbId
            imdbIds[tmdbId] = imdbId
            runCatching { idsFile.writeText(Tmdb.encodeIds(tmdbIds)) }
                .onFailure { Log.w(TAG, "Could not save TMDB ids: ${it.javaClass.simpleName}") }
        }
    }

    private fun loadIds() {
        if (idsLoaded) return
        idsLoaded = true
        val saved = runCatching { if (idsFile.exists()) Tmdb.decodeIds(idsFile.readText()) else emptyMap() }.getOrDefault(emptyMap())
        saved.forEach { (imdb, tmdbId) ->
            tmdbIds.putIfAbsent(imdb, tmdbId)
            imdbIds.putIfAbsent(tmdbId, imdb)
        }
    }

    private fun body(url: String, auth: TmdbAuth): String? = get(url, auth)?.takeIf { it.code == HttpURLConnection.HTTP_OK }?.body

    private fun get(url: String, auth: TmdbAuth): Response? = request("GET", url, auth, null)

    private fun request(method: String, url: String, auth: TmdbAuth, jsonBody: String?): Response? {
        var connection: HttpsURLConnection? = null
        return try {
            val target = URL(Tmdb.authorize(url, auth))
            if (target.host != Tmdb.API_HOST || method !in METHODS) return null
            connection = target.openConnection() as? HttpsURLConnection ?: return null
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            if (auth is TmdbAuth.Bearer) connection.setRequestProperty("Authorization", "Bearer ${auth.token}")
            if (jsonBody != null) {
                val bytes = jsonBody.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json;charset=utf-8")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val code = connection.responseCode
            val text = if (code in 200..299) connection.inputStream.use { it.readLimited() } else null
            Response(code, text)
        } catch (e: IOException) {
            // Only the exception type: the URL can contain the key.
            Log.i(TAG, "TMDB request failed: ${e.javaClass.simpleName}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun java.io.InputStream.readLimited(): String? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > MAX_BODY_BYTES) return null
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun TmdbAuth.value(): String = when (this) {
        is TmdbAuth.ApiKey -> key
        is TmdbAuth.Bearer -> token
    }

    private companion object {
        const val TAG = "Tmdb"
        const val SECRET_NAME = "tmdb_key"
        const val TIMEOUT_MS = 10_000
        const val PARALLEL_REQUESTS = 4
        const val ROW_SIZE = 20
        const val MAX_BODY_BYTES = 2 * 1024 * 1024
        const val PROVIDERS_KEPT = 40
        const val PROVIDERS_MAX_AGE_MS = 24L * 60 * 60 * 1000
        const val MAX_IDS = 5_000
        val METHODS = setOf("GET", "POST", "DELETE")
    }
}
