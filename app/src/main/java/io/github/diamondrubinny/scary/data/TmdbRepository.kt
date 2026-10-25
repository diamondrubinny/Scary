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

    /** IMDb id to TMDB id, so a movie page opened again needs one request, not two. */
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
        val tmdbId = synchronized(tmdbIds) { tmdbIds[imdbId] } ?: run {
            val url = runCatching { Tmdb.findUrl(imdbId, Language.EN) }.getOrNull() ?: return@withContext null
            val found = body(url, key)?.let { Tmdb.parseFind(it, Language.EN) }?.tmdbId ?: return@withContext null
            synchronized(tmdbIds) { tmdbIds[imdbId] = found }
            found
        }
        val now = System.currentTimeMillis()
        synchronized(providers) { providers[tmdbId]?.takeIf { now - it.first < PROVIDERS_MAX_AGE_MS }?.second }
            ?.let { return@withContext it }
        val offers = body(WatchProviders.url(tmdbId), key)?.let(WatchProviders::parse) ?: return@withContext null
        synchronized(providers) { providers[tmdbId] = now to offers }
        offers
    }

    private data class Response(val code: Int, val body: String?)

    private fun body(url: String, auth: TmdbAuth): String? = get(url, auth)?.takeIf { it.code == HttpURLConnection.HTTP_OK }?.body

    private fun get(url: String, auth: TmdbAuth): Response? {
        var connection: HttpsURLConnection? = null
        return try {
            val target = URL(Tmdb.authorize(url, auth))
            if (target.host != Tmdb.API_HOST) return null
            connection = target.openConnection() as? HttpsURLConnection ?: return null
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            if (auth is TmdbAuth.Bearer) connection.setRequestProperty("Authorization", "Bearer ${auth.token}")
            val code = connection.responseCode
            val text = if (code == HttpURLConnection.HTTP_OK) connection.inputStream.use { it.readLimited() } else null
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
    }
}
