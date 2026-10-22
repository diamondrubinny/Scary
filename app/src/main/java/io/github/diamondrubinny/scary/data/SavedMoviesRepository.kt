// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.content.Context
import android.util.Log
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

    /** Keeps an online movie; curated movies are always in the bundled library and are not stored. */
    suspend fun save(movie: Movie, language: Language) {
        if (movie.curated) return
        mutex.withLock {
            loadLocked()
            val meta = Cinemeta.fromMovie(movie, language.code)
            val next = (listOf(meta) + _items.value.filter { it.imdbId != meta.imdbId }).take(MAX_SIZE)
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
                    .take(MAX_SIZE)
            }
        }
    }

    private companion object {
        const val TAG = "SavedMovies"
        const val MAX_SIZE = 150
    }
}
