// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.content.Context
import io.github.diamondrubinny.scary.core.catalog.CatalogParser
import io.github.diamondrubinny.scary.core.catalog.Categories
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.model.Catalog
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.MediaKind
import io.github.diamondrubinny.scary.core.sync.RefreshPolicy
import io.github.diamondrubinny.scary.core.sync.RefreshState
import io.github.diamondrubinny.scary.core.tmdb.Tmdb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The bundled, curated catalog first (instant and offline), then the same catalog enriched with artwork and
 * popular rows from Cinemeta: from the disk cache right away, and from the network when the cache is old.
 */
class CatalogRepository(
    private val context: Context,
    private val cinemeta: CinemetaRepository,
    private val tmdb: TmdbRepository,
) {
    private val mutex = Mutex()
    private var bundled: Catalog? = null

    /** The films and the series, in one catalog. Throws CatalogException or IOException when either is broken. */
    suspend fun loadBundled(): Catalog = mutex.withLock {
        bundled ?: withContext(Dispatchers.IO) {
            val films = context.assets.open(ASSET_PATH).bufferedReader().use { CatalogParser.parse(it.readText()) }
            val series = context.assets.open(SERIES_PATH).bufferedReader().use { CatalogParser.parse(it.readText(), MediaKind.SERIES) }
            films.copy(movies = films.movies + series.movies, collections = films.collections + series.collections)
        }.also { bundled = it }
    }

    /**
     * Layers, each optional: bundled catalog, then Cinemeta artwork and rows, then TMDB Hebrew text and rows when
     * the user has added a TMDB key. Re-runs when the online switch or the key changes. SCARY is the home screen and
     * stays up for days, so each signal on [refresh] (back on screen, network back, a timer) fetches again whatever is
     * missing or old ([RefreshPolicy]); a failed fetch is retried later, not given up on.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun catalog(
        onlineEnabled: Flow<Boolean>,
        tmdbKeySet: Flow<Boolean>,
        language: Flow<Language>,
        refresh: Flow<Unit> = emptyFlow(),
    ): Flow<Catalog> =
        combine(onlineEnabled, tmdbKeySet, language) { online, hasKey, lang -> Triple(online, hasKey, lang) }
            .distinctUntilChanged()
            .flatMapLatest { (online, hasKey, lang) ->
                flow {
                    val base = loadBundled()
                    emit(base)
                    if (!online) return@flow

                    var cinemetaCache = cinemeta.cached()
                    var tmdbCache = if (hasKey) tmdb.cached(lang) else null
                    fun build(): Catalog {
                        val withCinemeta = cinemetaCache?.let { cache ->
                            // The library's categories go on with films from Cinemeta (core Categories), after their own.
                            cache.categories.entries.fold(Cinemeta.merge(base, cache.metas, cache.rows)) { acc, (collectionId, ids) ->
                                val curated = acc.collections.firstOrNull { it.id == collectionId }?.movieIds?.size ?: 0
                                Cinemeta.extend(acc, collectionId, ids.mapNotNull(cache.metas::get), maxRow = curated + Categories.MAX_PER_CATEGORY)
                            }
                        } ?: base
                        val cache = tmdbCache ?: return withCinemeta
                        val withRows = Cinemeta.merge(withCinemeta, emptyMap(), cache.rows)
                        return withRows.copy(movies = withRows.movies.map { m -> cache.info[m.imdbId]?.let { Tmdb.enrich(m, it) } ?: m })
                    }
                    if (cinemetaCache != null || tmdbCache != null) emit(build())

                    var cinemetaState = RefreshState()
                    var tmdbState = RefreshState()
                    suspend fun fetchWhatIsDue() {
                        val now = System.currentTimeMillis()
                        val knownCinemeta = cinemetaCache
                        if (RefreshPolicy.due(knownCinemeta == null || knownCinemeta.isStale(now), cinemetaState, now)) {
                            val fresh = cinemeta.refresh(base.movies.map { it.imdbId to it.kind })
                            cinemetaState = RefreshPolicy.after(cinemetaState, fresh != null, System.currentTimeMillis())
                            if (fresh != null) {
                                cinemetaCache = fresh
                                emit(build())
                            }
                        }
                        val knownTmdb = tmdbCache
                        if (hasKey && RefreshPolicy.due(knownTmdb == null || knownTmdb.isStale(now), tmdbState, now)) {
                            // TMDB's Hebrew titles and synopses are for films; series keep Cinemeta's and the library's.
                            val ids = build().movies.filterNot { it.isSeries }.map { it.imdbId }
                            val fresh = tmdb.refresh(ids, lang)
                            tmdbState = RefreshPolicy.after(tmdbState, fresh != null, System.currentTimeMillis())
                            if (fresh != null) {
                                tmdbCache = fresh
                                emit(build())
                            }
                        }
                    }
                    fetchWhatIsDue()
                    refresh.collect { fetchWhatIsDue() }
                }
            }

    private companion object {
        const val ASSET_PATH = "catalog.json"
        const val SERIES_PATH = "series.json"
    }
}
