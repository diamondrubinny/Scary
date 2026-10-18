package io.github.diamondrubinny.scary.data

import android.content.Context
import io.github.diamondrubinny.scary.core.catalog.CatalogParser
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.model.Catalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
) {
    private val mutex = Mutex()
    private var bundled: Catalog? = null

    /** Throws CatalogException or IOException when the bundled catalog is broken. */
    suspend fun loadBundled(): Catalog = mutex.withLock {
        bundled ?: withContext(Dispatchers.IO) {
            context.assets.open(ASSET_PATH).bufferedReader().use { CatalogParser.parse(it.readText()) }
        }.also { bundled = it }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun catalog(onlineEnabled: Flow<Boolean>): Flow<Catalog> = onlineEnabled.distinctUntilChanged().flatMapLatest { online ->
        flow {
            val base = loadBundled()
            emit(base)
            if (!online) return@flow
            val cache = cinemeta.cached()
            if (cache != null) emit(Cinemeta.merge(base, cache.metas, cache.rows))
            if (cache == null || cache.isStale(System.currentTimeMillis())) {
                cinemeta.refresh(base.movies.map { it.imdbId })?.let { emit(Cinemeta.merge(base, it.metas, it.rows)) }
            }
        }
    }

    private companion object {
        const val ASSET_PATH = "catalog.json"
    }
}
