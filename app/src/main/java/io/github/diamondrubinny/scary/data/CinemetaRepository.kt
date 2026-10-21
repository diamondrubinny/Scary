package io.github.diamondrubinny.scary.data

import android.content.Context
import android.util.Log
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaCache
import io.github.diamondrubinny.scary.core.cinemeta.OnlineRow
import io.github.diamondrubinny.scary.core.model.Catalog
import io.github.diamondrubinny.scary.core.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Fetches artwork and popular rows from Cinemeta and keeps a copy on disk. */
class CinemetaRepository(context: Context) {
    private val cacheFile = File(context.filesDir, "cinemeta_cache.json")

    private data class RowSpec(
        val id: String,
        val genre: String,
        val titleHe: String,
        val titleEn: String,
        val titlePt: String,
        val titleRu: String,
    )

    private val rowSpecs = listOf(
        RowSpec("horror_popular", "Horror", "פופולרי עכשיו באימה", "Popular horror right now", "Terror em alta agora", "Популярные ужасы сейчас"),
        RowSpec("thriller_popular", "Thriller", "מותחנים פופולריים", "Popular thrillers", "Suspenses populares", "Популярные триллеры"),
        RowSpec("mystery_popular", "Mystery", "תעלומות שכולם רואים", "Mysteries everyone is watching", "Mistérios que todos estão vendo", "Загадки, которые смотрят все"),
    )

    suspend fun cached(): CinemetaCache? = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) null else runCatching { CinemetaCache.decode(cacheFile.readText()) }.getOrNull()
    }

    /** Returns fresh data, or null when the TV is offline or the service is down. Never throws. */
    suspend fun refresh(imdbIds: List<String>): CinemetaCache? = withContext(Dispatchers.IO) {
        // A shared pool instead of fixed batches: one slow movie no longer holds up the next ones,
        // and the three popular rows queue first so whole rows of posters appear early.
        val pool = Semaphore(PARALLEL_REQUESTS)
        val (metaList, rows) = coroutineScope {
            val rows = rowSpecs.map { spec ->
                async {
                    pool.withPermit { fetch(Cinemeta.catalogUrl("top", spec.genre)) }
                        ?.let(Cinemeta::parseCatalog)
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { OnlineRow(spec.id, spec.titleHe, spec.titleEn, it, mapOf("pt" to spec.titlePt, "ru" to spec.titleRu)) }
                }
            }
            val metas = imdbIds.distinct().map { id ->
                async { pool.withPermit { fetch(Cinemeta.metaUrl(id))?.let(Cinemeta::parseMeta) } }
            }
            metas.awaitAll() to rows.awaitAll().filterNotNull()
        }
        val metas = metaList.filterNotNull().associateBy { it.imdbId }
        if (metas.isEmpty() && rows.isEmpty()) return@withContext null
        CinemetaCache(System.currentTimeMillis(), metas, rows).also(::save)
    }

    /**
     * Web search in Cinemeta for the search screen. Only the query text and the TV's IP reach Cinemeta. Null when the
     * query is invalid, the TV is offline or the service is down; never throws.
     */
    suspend fun search(query: String, known: Catalog): List<Movie>? = withContext(Dispatchers.IO) {
        val url = Cinemeta.searchUrl(query) ?: return@withContext null
        fetch(url)?.let { Cinemeta.searchResults(it, known) }
    }

    private fun save(cache: CinemetaCache) {
        try {
            val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            tmp.writeText(cache.encode())
            if (!tmp.renameTo(cacheFile)) tmp.delete()
        } catch (e: IOException) {
            Log.w(TAG, "Could not save Cinemeta cache: ${e.javaClass.simpleName}")
        }
    }

    private fun fetch(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as? HttpsURLConnection) ?: return null
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "SCARY-launcher")
            // Cinemeta redirects catalogs to another strem.io host; the redirect must stay on HTTPS.
            if (connection.responseCode != HttpURLConnection.HTTP_OK || connection.url.protocol != "https") return null
            connection.inputStream.use { input ->
                val bytes = input.readNBytesCompat(MAX_BODY_BYTES + 1)
                if (bytes.size > MAX_BODY_BYTES) null else String(bytes, Charsets.UTF_8)
            }
        } catch (e: IOException) {
            Log.i(TAG, "Cinemeta request failed: ${e.javaClass.simpleName}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private companion object {
        const val TAG = "Cinemeta"
        const val TIMEOUT_MS = 10_000
        const val PARALLEL_REQUESTS = 6
        const val MAX_BODY_BYTES = 2 * 1024 * 1024
    }
}
