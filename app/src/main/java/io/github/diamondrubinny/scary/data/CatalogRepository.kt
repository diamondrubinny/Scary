package io.github.diamondrubinny.scary.data

import android.content.Context
import io.github.diamondrubinny.scary.core.catalog.CatalogParser
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.model.Catalog
import io.github.diamondrubinny.scary.core.tmdb.Tmdb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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

    /** Throws CatalogException or IOException when the bundled catalog is broken. */
    suspend fun loadBundled(): Catalog = mutex.withLock {
        bundled ?: withContext(Dispatchers.IO) {
            context.assets.open(ASSET_PATH).bufferedReader().use { CatalogParser.parse(it.readText()) }
        }.also { bundled = it }
    }

    /**
     * Layers, each optional: bundled catalog, then Cinemeta artwork and rows, then TMDB Hebrew text and rows when
     * the user has added a TMDB key. Re-runs when the online switch or the key changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun catalog(onlineEnabled: Flow<Boolean>, tmdbKeySet: Flow<Boolean>): Flow<Catalog> =
        combine(onlineEnabled, tmdbKeySet) { online, hasKey -> online to hasKey }
            .distinctUntilChanged()
            .flatMapLatest { (online, hasKey) ->
                flow {
                    val base = loadBundled()
                    emit(base)
                    if (!online) return@flow

                    val now = System.currentTimeMillis()
                    var cinemetaCache = cinemeta.cached()
                    var tmdbCache = if (hasKey) tmdb.cached() else null
                    fun build(): Catalog {
                        val withCinemeta = cinemetaCache?.let { Cinemeta.merge(base, it.metas, it.rows) } ?: base
                        val cache = tmdbCache ?: return withCinemeta
                        val withRows = Cinemeta.merge(withCinemeta, emptyMap(), cache.rows)
                        return withRows.copy(movies = withRows.movies.map { m -> cache.info[m.imdbId]?.let { Tmdb.enrich(m, it) } ?: m })
                    }
                    if (cinemetaCache != null || tmdbCache != null) emit(build())

                    val knownCinemeta = cinemetaCache
                    if (knownCinemeta == null || knownCinemeta.isStale(now)) {
                        cinemeta.refresh(base.movies.map { it.imdbId })?.let { cinemetaCache = it; emit(build()) }
                    }
                    val knownTmdb = tmdbCache
                    if (hasKey && (knownTmdb == null || knownTmdb.isStale(now))) {
                        val ids = build().movies.map { it.imdbId }
                        tmdb.refresh(ids)?.let { tmdbCache = it; emit(build()) }
                    }
                }
            }

    private companion object {
        const val ASSET_PATH = "catalog.json"
    }
}
