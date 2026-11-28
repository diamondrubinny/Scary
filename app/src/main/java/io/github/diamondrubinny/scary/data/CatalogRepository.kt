// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import io.github.diamondrubinny.scary.core.catalog.FilmFeed
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import android.content.Context
import android.util.Log
import io.github.diamondrubinny.scary.core.catalog.CatalogException
import io.github.diamondrubinny.scary.core.catalog.Categories
import io.github.diamondrubinny.scary.core.catalog.FeedCatalog
import io.github.diamondrubinny.scary.core.catalog.LegacyLibrary
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.model.Catalog
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.catalog.OriginFilter
import io.github.diamondrubinny.scary.core.sync.RefreshPolicy
import io.github.diamondrubinny.scary.core.sync.RefreshState
import io.github.diamondrubinny.scary.core.tmdb.Tmdb
import java.io.IOException
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
 * The film feed first (docs/CATALOG_REBUILD_PLAN.md: packed in the app, instant and offline), then the same library
 * enriched with artwork and popular rows from Cinemeta: from the disk cache right away, and from the network when the
 * cache is old.
 */
class CatalogRepository(
    private val context: Context,
    private val cinemeta: CinemetaRepository,
    private val tmdb: TmdbRepository,
    private val feed: FeedRepository,
) {
    private val mutex = Mutex()
    private var bundled: Catalog? = null
    private var everything: List<Movie> = emptyList()

    /**
     * Called once with what outlives the hand-picked library (core LegacyLibrary), before the first library is given
     * out: the app's container turns the ids in viewers' lists into the ones the films have now.
     */
    @Volatile
    var onLegacy: suspend (LegacyLibrary) -> Unit = {}

    /**
     * The home rows of the feed, and the first films of each: the ones whose artwork and synopsis are asked for from
     * Cinemeta ahead of time. The rest of the feed comes through [loadIndex], as films whose details load when opened.
     * Throws CatalogException when no feed on this TV can be used.
     */
    suspend fun loadBundled(): Catalog = mutex.withLock {
        bundled ?: withContext(Dispatchers.IO) {
            val legacy = try {
                context.assets.open(LEGACY_PATH).bufferedReader().use { LegacyLibrary.parse(it.readText()) }
            } catch (e: CatalogException) {
                Log.e(TAG, "The legacy file is invalid: ${e.problems.take(2)}")
                LegacyLibrary.EMPTY
            } catch (e: IOException) {
                Log.e(TAG, "The legacy file could not be read", e)
                LegacyLibrary.EMPTY
            }
            onLegacy(legacy)
            this@CatalogRepository.legacy = legacy
            library(feed.load() ?: throw CatalogException(listOf("no usable film feed")), legacy)
        }.also { bundled = it }
    }

    private var legacy: LegacyLibrary = LegacyLibrary.EMPTY

    /** The library made of [result]: its rows with their first films, and all of its films for [films]. */
    private fun library(result: FilmFeed.Result, legacy: LegacyLibrary): Catalog {
        // Each row begins where today turns it to (core DailyRotation): the day is kept, to turn again when it changes.
        val day = java.time.LocalDate.now().toEpochDay()
        libraryDay = day
        val all = FeedCatalog.catalog(result, legacy, day)
        everything = all.movies
        _films.value = all.movies
        val heads = all.collections.flatMapTo(HashSet()) { it.movieIds.take(ROW_HEAD) }
        return all.copy(movies = all.movies.filter { it.id in heads })
    }

    /** The day the library's rows were turned for (days since 1970, in the TV's own time); the UI tests read it. */
    @Volatile
    var libraryDay: Long = 0
        private set

    private val _films = MutableStateFlow<List<Movie>>(emptyList())

    /** Every film of the feed in use; it changes when a newer feed is taken up while the app runs. */
    val films: StateFlow<List<Movie>> = _films.asStateFlow()

    /**
     * Takes up a newer feed downloaded since the last one, or a new day: the library is made again, and given back.
     * Null when there is neither. Called between refreshes of the home screen, never while one runs.
     */
    private suspend fun takeNewerFeed(): Catalog? = mutex.withLock {
        // A new day turns the rows again, with the feed in use or a newer one. SCARY stays up for weeks: without this
        // the rows would begin for ever with the films of the day it was started.
        val newDay = !feed.frozen && bundled != null && java.time.LocalDate.now().toEpochDay() != libraryDay
        val fresh = feed.takePending() ?: (if (newDay) feed.load() else null) ?: return@withLock null
        withContext(Dispatchers.IO) { library(fresh, legacy) }.also { bundled = it }
    }

    /**
     * Every film of the feed: read with the library, in the background. Empty only if the feed cannot be read, in
     * which case [loadBundled] has already thrown.
     */
    suspend fun loadIndex(): List<Movie> {
        loadBundled()
        return everything
    }

    /**
     * Layers, each optional: bundled catalog, then Cinemeta artwork and rows, then TMDB Hebrew text and rows when
     * the user has added a TMDB key. Re-runs when the online switch or the key changes. SCARY is the home screen and
     * stays up for days, so each signal on [refresh] (back on screen, network back, a timer) fetches again whatever is
     * missing or old ([RefreshPolicy]); a failed fetch is retried later, not given up on.
     */
    private val freshAllAsked = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * The rule for hidden films changed (Settings): what is held is filtered again at once, and everything online is
     * read anew at the next signal on the catalog's refresh flow, rows and categories alike.
     */
    fun askForFreshAll() = freshAllAsked.set(true)

    private val freshRowsAsked = java.util.concurrent.atomic.AtomicBoolean(false)

    /** "Refresh films now": the next signal on the catalog's refresh flow reads the online rows again, due or not. */
    fun askForFreshRows() = freshRowsAsked.set(true)

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
                    var base = loadBundled()
                    emit(base)
                    if (!online) return@flow

                    var cinemetaCache = cinemeta.cached()
                    var tmdbCache = if (hasKey) tmdb.cached(lang) else null
                    fun build(): Catalog {
                        val withCinemeta = cinemetaCache?.let { cache ->
                            // The library's categories go on with films from Cinemeta (core Categories), after their own.
                            cache.categories.entries.fold(Cinemeta.merge(base, cache.metas, cache.rows)) { acc, (collectionId, ids) ->
                                val curated = acc.collections.firstOrNull { it.id == collectionId }?.movieIds?.size ?: 0
                                // "New nightmares" is this year's and last year's films only, not the library's past picks.
                                val replace = collectionId in Categories.replacesCurated
                                val max = (if (replace) 0 else curated) + Categories.MAX_PER_CATEGORY
                                Cinemeta.extend(acc, collectionId, ids.mapNotNull(cache.metas::get), maxRow = max, replace = replace)
                            }
                        } ?: base
                        val cache = tmdbCache ?: return withCinemeta
                        val withRows = Cinemeta.merge(withCinemeta, emptyMap(), cache.rows)
                        return withRows.copy(movies = withRows.movies.map { m -> cache.info[m.imdbId]?.let { Tmdb.enrich(m, it) } ?: m })
                    }
                    if (cinemetaCache != null || tmdbCache != null) emit(build())

                    var cinemetaState = RefreshState()
                    var tmdbState = RefreshState()
                    var categoriesState = RefreshState()
                    suspend fun fetchWhatIsDue() {
                        // A feed downloaded at an earlier refresh becomes the library now, between two refreshes: SCARY
                        // stays up for weeks, so the app's next start is too long to wait for the week's films.
                        takeNewerFeed()?.let {
                            base = it
                            emit(build())
                        }
                        // The week's new feed, at most one try a day; it is taken up at the next refresh.
                        feed.refresh()
                        val now = System.currentTimeMillis()
                        // The hidden-films rule changed: hide at once what is held (works offline too), then read all
                        // of it again as if nothing were kept, so films now allowed come in.
                        val everything = freshAllAsked.getAndSet(false)
                        if (everything) {
                            cinemetaCache = cinemetaCache?.withoutHidden()
                            tmdbCache = tmdbCache?.let { it.copy(rows = it.rows.mapNotNull(OriginFilter::visible)) }
                            emit(build())
                        }
                        val knownCinemeta = if (everything) null else cinemetaCache
                        // Rows with pages missing (a weak network) count as a failure: tried again with backoff until whole.
                        // Asked for by the viewer: read now, whatever the schedule and the backoff say.
                        val asked = freshRowsAsked.getAndSet(false)
                        if (asked || RefreshPolicy.due(knownCinemeta == null || knownCinemeta.needsRows(now), cinemetaState, now)) {
                            val fresh = cinemeta.refresh(base.movies.map { it.imdbId to it.kind }, knownCinemeta)
                            cinemetaState = RefreshPolicy.after(cinemetaState, fresh?.complete == true, System.currentTimeMillis())
                            if (fresh != null) {
                                cinemetaCache = fresh
                                emit(build())
                                // Then the categories, in the background of rows already on screen: with each day's rows,
                                // or while they are missing or short. A retry of a few rows does not fetch them all again.
                                if (fresh.fetchedAtMillis != knownCinemeta?.fetchedAtMillis || fresh.needsCategories()) {
                                    val filled = cinemeta.refreshCategories(fresh)
                                    categoriesState = RefreshPolicy.after(categoriesState, filled?.categoriesComplete == true, System.currentTimeMillis())
                                    if (filled != null) {
                                        cinemetaCache = filled
                                        emit(build())
                                    }
                                }
                            }
                        }
                        // Categories that failed to fill, or filled in part (the network dropped right then), are tried
                        // again, with backoff.
                        val withRows = cinemetaCache
                        if (withRows != null && RefreshPolicy.due(withRows.needsCategories(), categoriesState, System.currentTimeMillis())) {
                            val filled = cinemeta.refreshCategories(withRows)
                            categoriesState = RefreshPolicy.after(categoriesState, filled?.categoriesComplete == true, System.currentTimeMillis())
                            if (filled != null) {
                                cinemetaCache = filled
                                emit(build())
                            }
                        }
                        // Only after a refresh this round: the copy read from disk at start says nothing new.
                        val noted = cinemetaCache
                        if (noted != null && noted !== knownCinemeta) cinemeta.noteRefresh(noted)
                        val knownTmdb = if (everything) null else tmdbCache
                        // TMDB's rows, like Cinemeta's, are read again every few hours (and when the viewer asks); the
                        // library's translations once a day.
                        if (hasKey && (asked || RefreshPolicy.due(knownTmdb == null || knownTmdb.needsRows(now), tmdbState, now))) {
                            // TMDB's Hebrew titles and synopses are for films; series keep Cinemeta's and the library's.
                            val ids = build().movies.filterNot { it.isSeries }.map { it.imdbId }
                            val fresh = tmdb.refresh(ids, lang, knownTmdb)
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
        const val LEGACY_PATH = "legacy.json"

        /** How many films from the front of each row get their details ahead of time: what a row shows without scrolling, and a few more. */
        const val ROW_HEAD = 12
        const val TAG = "CatalogRepository"
    }
}
