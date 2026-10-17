package io.github.diamondrubinny.scary.data

import android.content.Context
import io.github.diamondrubinny.scary.core.catalog.CatalogParser
import io.github.diamondrubinny.scary.core.model.Catalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Loads the curated catalog bundled in assets. Phase 3 adds TMDB enrichment on top of it. */
class CatalogRepository(private val context: Context) {
    private val mutex = Mutex()
    private var cached: Catalog? = null

    /** Throws [io.github.diamondrubinny.scary.core.catalog.CatalogException] or IOException on failure. */
    suspend fun load(): Catalog = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            context.assets.open(ASSET_PATH).bufferedReader().use { CatalogParser.parse(it.readText()) }
        }.also { cached = it }
    }

    private companion object {
        const val ASSET_PATH = "catalog.json"
    }
}
