// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.content.Context
import android.util.Log
import io.github.diamondrubinny.scary.core.catalog.OriginFilter
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.realdebrid.AltTitles
import io.github.diamondrubinny.scary.core.tmdb.Tmdb
import io.github.diamondrubinny.scary.core.tmdb.TmdbAuth
import io.github.diamondrubinny.scary.core.tmdb.TmdbCache
import io.github.diamondrubinny.scary.core.tmdb.TmdbCastMember
import io.github.diamondrubinny.scary.core.tmdb.TmdbCategory
import io.github.diamondrubinny.scary.core.tmdb.TmdbResult
import io.github.diamondrubinny.scary.core.tmdb.TmdbRow
import io.github.diamondrubinny.scary.core.tmdb.RegionOffers
import io.github.diamondrubinny.scary.core.tmdb.WatchProviders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
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
        if (auth != null) loadAltTitles()
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
        // The films' other titles came with the key and leave with it.
        synchronized(altLock) {
            altLoaded = true
            altFile.delete()
            _altTitles.value = AltTitles.NONE
        }
        synchronized(casts) { casts.clear() }
    }

    /** The cache only counts when it was fetched in the current UI language. */
    suspend fun cached(language: Language): TmdbCache? = withContext(Dispatchers.IO) {
        if (auth == null || !cacheFile.exists()) return@withContext null
        // A cache written before the rows carried their language (version 1) may hold Arab and Indian films: not shown.
        runCatching { TmdbCache.decode(cacheFile.readText()) }.getOrNull()
            ?.takeIf { it.language == language.code && it.rowsVersion >= TmdbCache.ROWS_VERSION }
    }

    /**
     * The library's films' text in the viewer's language ([imdbIds], one request each) plus the discover rows (TmdbRow,
     * [TmdbCache.ROW_PAGES] pages each). Null without a key or when nothing could be read.
     *
     * While [previous] is still the day's (same language), only the rows are read again and the text of films it lacks:
     * that is the round made every few hours, a few dozen requests once the films' IMDb ids are known. A row whose
     * pages failed keeps its previous films.
     */
    suspend fun refresh(imdbIds: List<String>, language: Language, previous: TmdbCache? = null): TmdbCache? = withContext(Dispatchers.IO) {
        val key = auth ?: return@withContext null
        val now = System.currentTimeMillis()
        val kept = previous?.takeIf { it.language == language.code && !it.isStale(now) }
        val wanted = imdbIds.distinct().let { all -> if (kept != null) all.filter { it !in kept.info } else all }
        val pool = Semaphore(PARALLEL_REQUESTS)
        val fresh = coroutineScope {
            wanted.map { id -> async { pool.withPermit { body(Tmdb.findUrl(id, language), key) }?.let { Tmdb.parseFind(it, language) }?.let { id to it } } }.awaitAll()
        }.filterNotNull().toMap()
        val info = kept?.info.orEmpty() + fresh
        val today = LocalDate.now()
        val results = coroutineScope {
            TmdbRow.entries.map { row ->
                async {
                    val pages = (1..TmdbCache.ROW_PAGES).map { page ->
                        async { pool.withPermit { body(Tmdb.discoverUrl(row, today, language, page), key) }?.let(Tmdb::parseDiscover) }
                    }.awaitAll()
                    // Films in Arabic or Indian languages stay off the shelves (core OriginFilter), before their ids are asked for.
                    val shown = pages.filterNotNull().flatten().distinctBy { it.tmdbId }.filterNot { OriginFilter.hidesLanguage(it.originalLanguage) }
                    val metas = withImdbIds(shown, key, language, pool).distinctBy { it.imdbId }
                    Triple(row.id, metas.takeIf { it.isNotEmpty() }?.let { Tmdb.rowOf(row, it) }, pages.any { it == null })
                }
            }.awaitAll()
        }
        val rows = Cinemeta.keepPrevious(
            order = results.map { it.first },
            fresh = results.mapNotNull { it.second },
            previous = previous?.takeIf { it.language == language.code && it.rowsVersion == TmdbCache.ROWS_VERSION }?.rows,
            failed = results.filter { it.third }.mapTo(HashSet()) { it.first },
        )
        if (info.isEmpty() && rows.isEmpty()) return@withContext null
        // Every page failed (offline): the copy on disk stays as it was, and the next try waits.
        if (results.all { it.second == null && it.third } && fresh.isEmpty()) return@withContext null
        TmdbCache(kept?.fetchedAtMillis ?: now, language.code, info, rows, TmdbCache.ROWS_VERSION, rowsFetchedAtMillis = now).also { cache ->
            runCatching { cacheFile.writeText(cache.encode()) }
                .onFailure { Log.w(TAG, "Could not save TMDB cache: ${it.javaClass.simpleName}") }
        }
    }

    /** Where a TMDB row goes on after the pages read at a refresh; null for rows that are not TMDB's. */
    fun endlessStart(rowId: String): Int? = TmdbRow.entries.firstOrNull { it.id == rowId }?.let { TmdbCache.ROW_PAGES + 1 }

    /**
     * Page [page] of the TMDB row [rowId], as Cinemeta-style entries with their IMDb ids, loaded while the viewer
     * scrolls (memory only). Its `next` is null at TMDB's last page or page 500. Null without a key or when offline.
     */
    suspend fun loadPage(rowId: String, page: Int, language: Language): OnlinePage? = withContext(Dispatchers.IO) {
        val key = auth ?: return@withContext null
        val row = TmdbRow.entries.firstOrNull { it.id == rowId } ?: return@withContext null
        if (page !in 1..Tmdb.MAX_PAGE) return@withContext OnlinePage(emptyList(), null)
        val results = body(Tmdb.discoverUrl(row, LocalDate.now(), language, page), key)?.let(Tmdb::parseDiscover)
            ?: return@withContext null
        // A page is counted by what TMDB sent (its end is TMDB's), but only films that may be shown get their IMDb id.
        val metas = withImdbIds(results.filterNot { OriginFilter.hidesLanguage(it.originalLanguage) }, key, language)
        OnlinePage(metas, if (results.isEmpty() || page >= Tmdb.MAX_PAGE) null else page + 1)
    }

    /**
     * Page [page] of the library row [collectionId]'s TMDB results (TmdbCategory), as Cinemeta-style entries with
     * their IMDb ids, loaded while the viewer scrolls (memory only). Null without a key, for rows TMDB has no filter
     * for, or when TMDB does not answer: the row then goes on from Cinemeta.
     */
    suspend fun loadCategoryPage(collectionId: String, page: Int, language: Language): OnlinePage? = withContext(Dispatchers.IO) {
        val key = auth ?: return@withContext null
        val category = TmdbCategory.of(collectionId) ?: return@withContext null
        if (page !in 1..Tmdb.MAX_PAGE) return@withContext OnlinePage(emptyList(), null)
        val results = body(Tmdb.discoverUrl(category, LocalDate.now(), language, page), key)?.let(Tmdb::parseDiscover)
            ?: return@withContext null
        OnlinePage(
            withImdbIds(results.filterNot { OriginFilter.hidesLanguage(it.originalLanguage) }, key, language),
            if (results.isEmpty() || page >= Tmdb.MAX_PAGE) null else page + 1,
        )
    }

    /** Discover results with their IMDb ids (one request each, a few at a time); those without one are left out. */
    /**
     * The results as Cinemeta-style entries with their IMDb ids: from the ids already known, else one request each,
     * never more than [pool] allows at a time (TMDB answers "too many requests" to a burst, and those films would be
     * missing from the row).
     */
    private suspend fun withImdbIds(
        results: List<TmdbResult>,
        key: TmdbAuth,
        language: Language,
        pool: Semaphore = Semaphore(PARALLEL_REQUESTS),
    ): List<CinemetaMeta> = coroutineScope {
        results.map { r ->
            async {
                val known = synchronized(tmdbIds) { imdbIds[r.tmdbId] }
                val imdbId = known ?: pool.withPermit { body(Tmdb.externalIdsUrl(r.tmdbId), key) }?.let(Tmdb::parseImdbId)?.also { remember(it, r.tmdbId, save = false) }
                imdbId?.let { Tmdb.toMeta(r, it, language) }
            }
        }.awaitAll().filterNotNull().also { saveIds() }
    }

    /** IMDb id to TMDB id, so a movie page opened again needs one request, not two (and the account's lists). */
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
        val tmdbId = tmdbIdOf(imdbId) ?: return@withContext null
        val now = System.currentTimeMillis()
        synchronized(providers) { providers[tmdbId]?.takeIf { now - it.first < PROVIDERS_MAX_AGE_MS }?.second }
            ?.let { return@withContext it }
        val offers = body(WatchProviders.url(tmdbId), key)?.let(WatchProviders::parse) ?: return@withContext null
        synchronized(providers) { providers[tmdbId] = now to offers }
        offers
    }

    /** A film's series (TMDB "collection") by the film's TMDB id; a film that stands alone is kept as null. In memory only. */
    private val franchises = object : LinkedHashMap<Long, OnlineFranchise?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, OnlineFranchise?>) = size > FRANCHISES_KEPT
    }

    /** A series of films, each with its IMDb id, in the order they came out. */
    data class OnlineFranchise(val name: String, val language: Language, val films: List<CinemetaMeta>)

    /**
     * The whole series a film belongs to ("Halloween", all thirteen, in order), in [language]. Asked for with the
     * viewer's own key: the film's TMDB id, the series' id, and the IMDb id of each of its films TMDB has not told
     * this TV before. Nothing about the viewer is sent. Null without a key, when the film stands alone, or when TMDB
     * cannot be reached. Never throws.
     */
    suspend fun franchise(imdbId: String, language: Language): OnlineFranchise? = withContext(Dispatchers.IO) {
        val key = auth ?: return@withContext null
        val tmdbId = tmdbIdOf(imdbId) ?: return@withContext null
        synchronized(franchises) {
            if (franchises.containsKey(tmdbId)) {
                val kept = franchises[tmdbId]
                if (kept == null || kept.language == language) return@withContext kept
            }
        }
        val film = body(Tmdb.movieUrl(tmdbId), key) ?: return@withContext null
        val collectionId = Tmdb.parseCollectionId(film)
        val found = collectionId?.let { id -> body(Tmdb.collectionUrl(id, language), key)?.let(Tmdb::parseCollection) }
        // The series' id was read but the series itself did not arrive: not remembered, so the next visit asks again.
        if (collectionId != null && found == null) return@withContext null
        val result = found?.let { OnlineFranchise(it.name, language, withImdbIds(it.parts, key, language)) }
        // A film whose IMDb id did not arrive is missing from the series: what there is is shown, but not remembered,
        // so the next visit asks for the rest. A film that stands alone is remembered as such.
        if (found == null || result?.films?.size == found.parts.size) synchronized(franchises) { franchises[tmdbId] = result?.takeIf { it.films.size >= 2 } }
        result?.takeIf { it.films.size >= 2 }
    }

    /** An actor's films by their name, lower case; a name TMDB does not know is kept as null. In memory only. */
    private val people = object : LinkedHashMap<String, OnlinePerson?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OnlinePerson?>) = size > PEOPLE_KEPT
    }

    /** An actor's horror, thrillers and mysteries, each with its IMDb id, the newest first. */
    data class OnlinePerson(val language: Language, val films: List<CinemetaMeta>)

    /**
     * The films [name] acted in that belong on SCARY's shelves, in [language] (docs/CAST_PLAN.md). Asked for with the
     * viewer's own key: the name (it is printed on the film's page), then the person's id, and the IMDb id of each
     * film this TV has not asked about before. Nothing about the viewer is sent. Null without a key, when TMDB knows
     * nobody by exactly that name, or when it cannot be reached. Never throws.
     */
    suspend fun personFilms(name: String, language: Language): OnlinePerson? = withContext(Dispatchers.IO) {
        val key = auth ?: return@withContext null
        val cacheKey = name.trim().lowercase()
        synchronized(people) {
            if (people.containsKey(cacheKey)) {
                val kept = people[cacheKey]
                if (kept == null || kept.language == language) return@withContext kept
            }
        }
        val search = Tmdb.personSearchUrl(name, language) ?: return@withContext null
        val found = body(search, key) ?: return@withContext null
        val personId = Tmdb.parsePersonId(found, name)
        if (personId == null) {
            synchronized(people) { people[cacheKey] = null }
            return@withContext null
        }
        filmsOf(personId, cacheKey, language, key)
    }

    /**
     * As [personFilms], for a person TMDB named itself (an actor of a film's cast, [cast]): asked for by TMDB's id of
     * them, so nobody of the same name is taken for them. Null without a key or when TMDB cannot be reached.
     */
    suspend fun personFilmsById(personId: Long, language: Language): OnlinePerson? = withContext(Dispatchers.IO) {
        val key = auth ?: return@withContext null
        if (personId <= 0) return@withContext null
        val cacheKey = "#$personId"
        synchronized(people) { people[cacheKey]?.takeIf { it.language == language } }?.let { return@withContext it }
        filmsOf(personId, cacheKey, language, key)
    }

    private suspend fun filmsOf(personId: Long, cacheKey: String, language: Language, key: TmdbAuth): OnlinePerson? {
        val credits = body(Tmdb.personCreditsUrl(personId, language), key)?.let(Tmdb::parsePersonCredits) ?: return null
        // The films kept off every shelf stay off this one too (core OriginFilter).
        val shown = credits.filterNot { OriginFilter.hidesLanguage(it.originalLanguage) }
        val result = OnlinePerson(language, withImdbIds(shown, key, language))
        // A film whose IMDb id did not arrive is missing: shown as it is, asked for again on the next visit.
        if (result.films.size == shown.size) synchronized(people) { people[cacheKey] = result }
        return result
    }

    /** A film's cast by its TMDB id and the language of the roles' names. In memory only. */
    private val casts = object : LinkedHashMap<Pair<Long, Language>, List<TmdbCastMember>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Long, Language>, List<TmdbCastMember>>) = size > CASTS_KEPT
    }

    /**
     * A film's actors with their photos and roles, in [language] (docs/UPGRADE_0.7_PLAN.md). Asked for with the
     * viewer's own key: the film's id only. Null without a key, when TMDB does not know the film, or when it cannot
     * be reached; an empty list when TMDB lists nobody. Never throws.
     */
    suspend fun cast(imdbId: String, language: Language): List<TmdbCastMember>? = withContext(Dispatchers.IO) {
        val key = auth ?: return@withContext null
        val tmdbId = tmdbIdOf(imdbId) ?: return@withContext null
        synchronized(casts) { casts[tmdbId to language] }?.let { return@withContext it }
        val found = body(Tmdb.creditsUrl(tmdbId, language), key)?.let(Tmdb::parseCast) ?: return@withContext null
        synchronized(casts) { casts[tmdbId to language] = found }
        found
    }

    private val altFile = File(context.filesDir, "alt_titles.json")
    private val altLock = Any()
    private var altLoaded = false
    private val _altTitles = MutableStateFlow(AltTitles.NONE)

    /**
     * Other titles of the films whose pages were opened, kept on the TV (up to [AltTitles.MAX_FILMS] films), for
     * recognising the viewer's own files. Empty without a key.
     */
    val altTitles: StateFlow<AltTitles> = _altTitles.asStateFlow()

    private fun loadAltTitles() {
        synchronized(altLock) {
            if (altLoaded) return
            altLoaded = true
            _altTitles.value = runCatching { if (altFile.exists()) AltTitles.decode(altFile.readText()) else AltTitles.NONE }
                .getOrDefault(AltTitles.NONE)
        }
    }

    /**
     * Asks TMDB once for a film's other titles and keeps them. True when titles were added now, so the viewer's files
     * are worth looking through again; false when they were known already, there are none, there is no key, or TMDB
     * cannot be reached (then it is asked again next time). The request carries the film's id only. Never throws.
     */
    suspend fun loadAlternativeTitles(imdbId: String): Boolean = withContext(Dispatchers.IO) {
        val key = auth ?: return@withContext false
        loadAltTitles()
        if (_altTitles.value.knows(imdbId)) return@withContext false
        val tmdbId = tmdbIdOf(imdbId) ?: return@withContext false
        val titles = body(Tmdb.alternativeTitlesUrl(tmdbId), key)?.let(Tmdb::parseAlternativeTitles) ?: return@withContext false
        synchronized(altLock) {
            // The key may have been removed while TMDB answered: nothing is kept then.
            if (auth == null) return@withContext false
            val next = _altTitles.value.with(imdbId, titles)
            _altTitles.value = next
            runCatching { altFile.writeText(next.encode()) }
                .onFailure { Log.w(TAG, "Could not save other titles: ${it.javaClass.simpleName}") }
        }
        titles.isNotEmpty()
    }

    private val languagesFile = File(context.filesDir, "origin_languages.json")
    private val languages = LinkedHashMap<Long, String>()
    private var languagesLoaded = false

    /**
     * The original language of films by TMDB id ("is" for Lamb), for the rows about a place (core Origin): an American
     * film with a European producer is told from a European one by it. Only the ids of films whose origin is unclear are
     * asked about, a few at a time, and each answer is kept on the TV for good (a film's language does not change; a
     * film TMDB does not know is kept as unknown). Empty without a key. Never throws.
     */
    suspend fun originalLanguages(ids: List<Long>): Map<Long, String> = withContext(Dispatchers.IO) {
        val wanted = ids.filter { it > 0 }.distinct()
        synchronized(languages) { loadLanguages() }
        val key = auth
        val missing = if (key == null) emptyList() else synchronized(languages) { wanted.filter { it !in languages } }.take(MAX_LANGUAGE_LOOKUPS)
        if (key != null && missing.isNotEmpty()) {
            missing.chunked(PARALLEL_REQUESTS).forEach { chunk ->
                coroutineScope { chunk.map { id -> async { id to fetchLanguage(id, key) } }.awaitAll() }.forEach { (id, language) ->
                    if (language != null) synchronized(languages) { languages[id] = language }
                }
            }
            synchronized(languages) {
                while (languages.size > MAX_LANGUAGES) languages.remove(languages.keys.first())
                saveLanguages()
            }
        }
        synchronized(languages) { wanted.mapNotNull { id -> languages[id]?.takeIf { it != NO_LANGUAGE }?.let { id to it } }.toMap() }
    }

    /** The film's language; [NO_LANGUAGE] when TMDB does not know the film (kept); null when it could not be asked (tried again later). */
    private fun fetchLanguage(tmdbId: Long, key: TmdbAuth): String? {
        val answer = get(Tmdb.movieUrl(tmdbId), key) ?: return null
        return when {
            answer.ok -> answer.body?.let(Tmdb::parseOriginalLanguage) ?: NO_LANGUAGE
            answer.code == HttpURLConnection.HTTP_NOT_FOUND -> NO_LANGUAGE
            else -> null
        }
    }

    private fun loadLanguages() {
        if (languagesLoaded) return
        languagesLoaded = true
        runCatching {
            Json.parseToJsonElement(languagesFile.takeIf { it.exists() }?.readText() ?: return).jsonObject.forEach { (id, value) ->
                val language = (value as? JsonPrimitive)?.contentOrNull
                if (id.toLongOrNull()?.takeIf { it > 0 } != null && language != null && (language == NO_LANGUAGE || language.length in 2..3)) {
                    languages[id.toLong()] = language
                }
            }
        }
    }

    private fun saveLanguages() {
        runCatching {
            val text = buildJsonObject { languages.forEach { (id, language) -> put(id.toString(), language) } }.toString()
            val tmp = File(languagesFile.parentFile, languagesFile.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(languagesFile)) tmp.delete()
        }.onFailure { Log.w(TAG, "Could not save film languages: ${it.javaClass.simpleName}") }
    }

    /** An answer from TMDB: the status code, and the body of a successful (2xx) answer. */
    data class Response(val code: Int, val body: String?) {
        val ok: Boolean get() = code in 200..299
    }

    /** Whether the viewer has added a key: everything else TMDB offers needs one. */
    val hasKey: Boolean get() = auth != null

    /**
     * A request to TMDB with the viewer's key, for their account (TmdbAccountRepository): [method] GET, POST or
     * DELETE, and a JSON body. Null without a key or when TMDB cannot be reached. Never logs the URL or the body.
     */
    suspend fun call(method: String, url: String, jsonBody: String? = null): Response? = withContext(Dispatchers.IO) {
        val key = auth ?: return@withContext null
        request(method, url, key, jsonBody)
    }

    /** IMDb id to TMDB id ("find"), remembered on the TV. Null when TMDB does not know the film or cannot be reached. */
    suspend fun tmdbIdOf(imdbId: String): Long? = withContext(Dispatchers.IO) {
        synchronized(tmdbIds) { tmdbIds[imdbId] }?.let { return@withContext it }
        val key = auth ?: return@withContext null
        val url = runCatching { Tmdb.findUrl(imdbId, Language.EN) }.getOrNull() ?: return@withContext null
        val found = body(url, key)?.let { Tmdb.parseFind(it, Language.EN) }?.tmdbId ?: return@withContext null
        remember(imdbId, found)
        found
    }

    /** TMDB id to IMDb id ("external_ids"), remembered on the TV. */
    suspend fun imdbIdOf(tmdbId: Long): String? = withContext(Dispatchers.IO) {
        synchronized(tmdbIds) { imdbIds[tmdbId] }?.let { return@withContext it }
        val key = auth ?: return@withContext null
        val found = body(Tmdb.externalIdsUrl(tmdbId), key)?.let(Tmdb::parseImdbId) ?: return@withContext null
        remember(found, tmdbId)
        found
    }

    private val imdbIds = HashMap<Long, String>()
    private val idsFile = File(context.filesDir, "tmdb_ids.json")
    private var idsLoaded = false

    /** Both ways, and on disk: syncing a list of hundreds of films looks each one up once, not every time. */
    /** [save] false when many are remembered in a row (a row's page): the caller writes them once, with [saveIds]. */
    private fun remember(imdbId: String, tmdbId: Long, save: Boolean = true) {
        synchronized(tmdbIds) {
            loadIds()
            if (tmdbIds[imdbId] == tmdbId && imdbIds[tmdbId] == imdbId) return
            if (tmdbIds.size >= MAX_IDS) {
                tmdbIds.clear()
                imdbIds.clear()
            }
            tmdbIds[imdbId] = tmdbId
            imdbIds[tmdbId] = imdbId
            idsUnsaved = true
        }
        if (save) saveIds()
    }

    private var idsUnsaved = false

    /** Writes the ids to the TV when any were remembered since the last write. */
    private fun saveIds() {
        synchronized(tmdbIds) {
            if (!idsUnsaved) return
            idsUnsaved = false
            runCatching { idsFile.writeText(Tmdb.encodeIds(tmdbIds)) }
                .onFailure { Log.w(TAG, "Could not save TMDB ids: ${it.javaClass.simpleName}") }
        }
    }

    private fun loadIds() {
        if (idsLoaded) return
        idsLoaded = true
        val saved = runCatching { if (idsFile.exists()) Tmdb.decodeIds(idsFile.readText()) else emptyMap() }.getOrDefault(emptyMap())
        saved.forEach { (imdb, tmdbId) ->
            tmdbIds.putIfAbsent(imdb, tmdbId)
            imdbIds.putIfAbsent(tmdbId, imdb)
        }
    }

    private fun body(url: String, auth: TmdbAuth): String? = get(url, auth)?.takeIf { it.code == HttpURLConnection.HTTP_OK }?.body

    private fun get(url: String, auth: TmdbAuth): Response? = request("GET", url, auth, null)

    private fun request(method: String, url: String, auth: TmdbAuth, jsonBody: String?): Response? {
        var connection: HttpsURLConnection? = null
        return try {
            val target = URL(Tmdb.authorize(url, auth))
            if (target.host != Tmdb.API_HOST || method !in METHODS) return null
            connection = target.openConnection() as? HttpsURLConnection ?: return null
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            if (auth is TmdbAuth.Bearer) connection.setRequestProperty("Authorization", "Bearer ${auth.token}")
            if (jsonBody != null) {
                val bytes = jsonBody.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json;charset=utf-8")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val code = connection.responseCode
            val text = if (code in 200..299) connection.inputStream.use { it.readLimited() } else null
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
        const val MAX_BODY_BYTES = 2 * 1024 * 1024
        const val PROVIDERS_KEPT = 40
        const val FRANCHISES_KEPT = 40
        const val PEOPLE_KEPT = 40
        const val CASTS_KEPT = 40
        const val PROVIDERS_MAX_AGE_MS = 24L * 60 * 60 * 1000
        const val MAX_IDS = 20_000
        const val MAX_LANGUAGE_LOOKUPS = 300
        const val MAX_LANGUAGES = 6_000
        const val NO_LANGUAGE = "-"
        val METHODS = setOf("GET", "POST", "DELETE")
    }
}
