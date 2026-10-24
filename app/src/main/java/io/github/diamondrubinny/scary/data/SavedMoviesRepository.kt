// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.content.Context
import android.util.Log
import io.github.diamondrubinny.scary.core.catalog.KeptList
import io.github.diamondrubinny.scary.core.catalog.MyList
import io.github.diamondrubinny.scary.core.catalog.RecentList
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * Movies found online (popular rows or web search) that the viewer kept, by adding them to My list or watching
 * them. Stored on this TV only, so they stay in My list and Recently opened after they leave the online rows.
 */
class SavedMoviesRepository(context: Context) {
    private val file = File(context.filesDir, "saved_movies.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(CinemetaMeta.serializer())
    private val mutex = Mutex()
    private var loaded = false

    private val _items = MutableStateFlow<List<CinemetaMeta>>(emptyList())

    /** Newest first. */
    val items: StateFlow<List<CinemetaMeta>> = _items.asStateFlow()

    suspend fun load() = mutex.withLock { loadLocked() }

    /**
     * Keeps an online movie; curated movies are always in the bundled library and are not stored. Past [MAX_SIZE] the
     * oldest go first, but never one in [keep] (IMDb ids of My list and Recently opened): without its details a movie
     * would vanish from My list and could not even be opened to take it off.
     */
    suspend fun save(movie: Movie, language: Language, keep: Set<String> = emptySet()) {
        if (movie.curated) return
        mutex.withLock {
            loadLocked()
            val meta = Cinemeta.fromMovie(movie, language.code)
            val next = trim(listOf(meta) + _items.value.filter { it.imdbId != meta.imdbId }, keep + meta.imdbId)
            if (next == _items.value) return@withLock
            _items.value = next
            withContext(Dispatchers.IO) {
                try {
                    val tmp = File(file.parentFile, file.name + ".tmp")
                    tmp.writeText(json.encodeToString(serializer, next))
                    if (!tmp.renameTo(file)) tmp.delete()
                } catch (e: IOException) {
                    Log.w(TAG, "Could not save movies: ${e.javaClass.simpleName}")
                }
            }
        }
    }

    // My list (100) and Recent (12) fit well inside MAX_SIZE, so the cap still holds in practice.
    private fun trim(items: List<CinemetaMeta>, keep: Set<String>): List<CinemetaMeta> =
        KeptList.trim(items, MAX_SIZE) { it.imdbId in keep }

    private suspend fun loadLocked() {
        if (loaded) return
        loaded = true
        _items.value = withContext(Dispatchers.IO) {
            if (!file.exists()) {
                emptyList()
            } else {
                runCatching { json.decodeFromString(serializer, file.readText()) }
                    .onFailure { Log.w(TAG, "Saved movies unreadable: ${it.javaClass.simpleName}") }
                    .getOrDefault(emptyList())
                    // The file is ours, but it is still re-validated as untrusted input.
                    .map { it.copy(poster = Cinemeta.safeImage(it.poster), background = Cinemeta.safeImage(it.background), logo = Cinemeta.safeImage(it.logo)) }
                    .filter { Cinemeta.toMovie(it) != null }
                    // Bounded by save(); a little over MAX_SIZE is the movies it had to keep.
                    .take(MAX_SIZE + MAX_KEPT)
            }
        }
    }

    private companion object {
        const val TAG = "SavedMovies"
        const val MAX_SIZE = 150
        const val MAX_KEPT = MyList.MAX_SIZE + RecentList.MAX_SIZE
    }
}
