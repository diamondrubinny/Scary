// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.content.Context
import android.util.Log
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaCache
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
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
import java.time.LocalDate
import javax.net.ssl.HttpsURLConnection

/** Fetches artwork and popular rows from Cinemeta and keeps a copy on disk. */
class CinemetaRepository(context: Context) {
    private val cacheFile = File(context.filesDir, "cinemeta_cache.json")

    /**
     * One online row: a Cinemeta catalog ([catalog] "top" or "year"), its [genre] (a genre name or a year), and the
     * catalog pages to join ([skips], 50 movies each). Year catalogs mix every genre, so they keep only horror,
     * thriller and mystery ([themedOnly]). An [endless] row keeps loading further pages while the viewer scrolls. A
     * popular row is the first page of its genre and its "deeper" row starts at the next one, so no movie falls in
     * between and no page is loaded twice. Rows keep every movie of their pages (MAX_ROW_MOVIES is three pages).
     */
    private data class RowSpec(
        val id: String,
        val catalog: String,
        val genre: String,
        val skips: List<Int>,
        val titles: Map<String, String>,
        val themedOnly: Boolean = false,
        val endless: Boolean = false,
    )

    private val rowSpecs: List<RowSpec>
        get() {
            val year = LocalDate.now().year
            return listOf(
                RowSpec(
                    "horror_popular", "top", "Horror", listOf(0),
                    titles("פופולרי עכשיו באימה", "Popular horror right now", "Terror em alta agora", "Популярные ужасы сейчас", "رعب رائج الآن"),
                ),
                RowSpec(
                    "horror_new", "year", year.toString(), listOf(0, 50, 100),
                    titles("חדש השנה", "New this year", "Novos deste ano", "Новинки этого года", "جديد هذا العام"),
                    themedOnly = true,
                    endless = true,
                ),
                RowSpec(
                    "thriller_popular", "top", "Thriller", listOf(0),
                    titles("מותחנים פופולריים", "Popular thrillers", "Suspenses populares", "Популярные триллеры", "أفلام إثارة رائجة"),
                ),
                RowSpec(
                    "horror_deep", "top", "Horror", listOf(50, 100),
                    titles("עוד מהמרתף", "Deeper in the cellar", "Mais fundo no porão", "Глубже в подвал", "أعمق في القبو"),
                    endless = true,
                ),
                RowSpec(
                    "mystery_popular", "top", "Mystery", listOf(0, 50),
                    titles("תעלומות שכולם רואים", "Mysteries everyone is watching", "Mistérios que todos estão vendo", "Загадки, которые смотрят все", "ألغاز يشاهدها الجميع"),
                    endless = true,
                ),
                RowSpec(
                    "horror_last_year", "year", (year - 1).toString(), listOf(0, 50, 100),
                    titles("הפחדים של שנה שעברה", "Last year's scares", "Os sustos do ano passado", "Страхи прошлого года", "مخاوف العام الماضي"),
                    themedOnly = true,
                    endless = true,
                ),
                RowSpec(
                    "thriller_deep", "top", "Thriller", listOf(50),
                    titles("עוד מותחנים", "More thrillers", "Mais suspenses", "Ещё триллеры", "مزيد من الإثارة"),
                    endless = true,
                ),
            )
        }

    /** One more page of an endless row: [rawSize] movies came back, [movies] are the ones the row keeps. */
    data class Page(val rawSize: Int, val movies: List<CinemetaMeta>)

    /** Where an endless row continues after the pages loaded with the daily refresh; null for rows that do not. */
    fun endlessStart(rowId: String): Int? =
        rowSpecs.firstOrNull { it.id == rowId && it.endless }?.let { it.skips.last() + Cinemeta.PAGE_SIZE }

    /**
     * The catalog page at [skip] for the endless row [rowId], loaded while the viewer scrolls. Kept in memory only.
     * Null when the TV is offline or the service is down; never throws.
     */
    suspend fun loadPage(rowId: String, skip: Int): Page? = withContext(Dispatchers.IO) {
        val spec = rowSpecs.firstOrNull { it.id == rowId && it.endless } ?: return@withContext null
        val url = runCatching { Cinemeta.catalogUrl(spec.catalog, spec.genre, skip) }.getOrNull() ?: return@withContext null
        val metas = fetch(url)?.let(Cinemeta::parseCatalog) ?: return@withContext null
        Page(metas.size, if (spec.themedOnly) Cinemeta.themed(metas) else metas)
    }

    private fun titles(he: String, en: String, pt: String, ru: String, ar: String) =
        mapOf("he" to he, "en" to en, "pt" to pt, "ru" to ru, "ar" to ar)

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
                    // The pages of one row load side by side; a page that fails just leaves the row shorter.
                    val pages = spec.skips.map { skip ->
                        async { pool.withPermit { fetch(Cinemeta.catalogUrl(spec.catalog, spec.genre, skip)) }?.let(Cinemeta::parseCatalog) }
                    }.awaitAll().filterNotNull()
                    val metas = pages.flatten().distinctBy { it.imdbId }.let { if (spec.themedOnly) Cinemeta.themed(it) else it }
                    metas.takeIf { it.isNotEmpty() }?.let {
                        OnlineRow(spec.id, spec.titles.getValue("he"), spec.titles.getValue("en"), it, spec.titles - "he" - "en")
                    }
                }
            }
            val metas = imdbIds.distinct().map { id ->
                async { pool.withPermit { fetch(Cinemeta.metaUrl(id))?.let(Cinemeta::parseMeta) } }
            }
            metas.awaitAll() to rows.awaitAll().filterNotNull()
        }
        val metas = metaList.filterNotNull().associateBy { it.imdbId }
        if (metas.isEmpty() && rows.isEmpty()) return@withContext null
        CinemetaCache(System.currentTimeMillis(), metas, rows, CinemetaCache.ROWS_VERSION).also(::save)
    }

    /**
     * Web search in Cinemeta for the search screen. Only the query text and the TV's IP reach Cinemeta. Null when the
     * query is invalid, the TV is offline or the service is down; never throws.
     */
    suspend fun search(query: String, known: Catalog): List<Movie>? = withContext(Dispatchers.IO) {
        val url = Cinemeta.searchUrl(query) ?: return@withContext null
        val metas = fetch(url)?.let(Cinemeta::parseCatalog) ?: return@withContext null
        // The top results often come without genres: look them up (side by side), so the theme filter can judge them.
        val missing = Cinemeta.needsGenres(metas, known)
        val full = if (missing.isEmpty()) {
            emptyMap()
        } else {
            val pool = Semaphore(PARALLEL_REQUESTS)
            coroutineScope {
                missing.map { id -> async { pool.withPermit { fetch(Cinemeta.metaUrl(id))?.let(Cinemeta::parseMeta) } } }
                    .awaitAll().filterNotNull().associateBy { it.imdbId }
            }
        }
        Cinemeta.searchResults(metas.map { full[it.imdbId] ?: it }, known)
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
