// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import io.github.diamondrubinny.scary.core.links.Region
import io.github.diamondrubinny.scary.core.ai.AiProvider
import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.diamondrubinny.scary.core.apps.PinnedList
import io.github.diamondrubinny.scary.core.catalog.MyList
import io.github.diamondrubinny.scary.core.catalog.PendingWatch
import io.github.diamondrubinny.scary.core.catalog.BucketList
import io.github.diamondrubinny.scary.core.catalog.RecentList
import io.github.diamondrubinny.scary.core.catalog.WatchedList
import io.github.diamondrubinny.scary.core.links.KnownPackages
import io.github.diamondrubinny.scary.core.security.LockoutState
import io.github.diamondrubinny.scary.core.security.PinHash
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class LauncherPrefs(
    val effectsEnabled: Boolean,
    val recentIds: List<String>,
    /** Pinned packages in display order. */
    val pinned: List<String>,
    /** Packages hidden from the home row. They stay in the all-apps drawer. */
    val hidden: Set<String>,
    /** Highest fear level tonight's pick may have, 1..5. */
    val nightMaxFear: Int,
    /** Salted hash of the parental PIN; null when the lock is off. */
    val pinHash: PinHash?,
    /** Movies at or above this fear level need the PIN. */
    val lockFromFear: Int,
    val lockout: LockoutState,
    val soundsEnabled: Boolean,
    /** Artwork and popular rows from Cinemeta. On by default; off keeps SCARY fully offline. */
    val onlineEnabled: Boolean,
    /** The viewer's watchlist, newest first. */
    val myListIds: List<String> = emptyList(),
    /** Only the gentlest curated movies (fear 1-2) appear anywhere, and no web search. */
    val familyNight: Boolean = false,
    /** Halloween and Friday the 13th rows around their dates. On by default. */
    val seasonalRows: Boolean = true,
    /** Movies the viewer has seen, newest first: marked by hand or after a viewing. */
    val watchedIds: List<String> = emptyList(),
    /** Mark a movie as watched after time away that looks like a viewing ([io.github.diamondrubinny.scary.core.catalog.AutoWatched]). */
    val autoWatched: Boolean = true,
    /** Watched films leave the home rows (a "Watched" row keeps them) instead of going to the end of each row. */
    val hideWatched: Boolean = false,
    /** The viewer said "Not now" to picking films they love, on the home screen. */
    val tasteInviteDismissed: Boolean = false,
    /** Look for a new version of SCARY by itself (official builds only). */
    val autoUpdateCheck: Boolean = true,
    /** The version the viewer chose to skip, the end of "Later", and the last check. */
    val updateSkippedVersion: Int? = null,
    val updateLaterUntil: Long? = null,
    val updateLastCheck: Long? = null,
    /** AI: on at all; which service; the model chosen per service; services the viewer agreed to send to. */
    val aiEnabled: Boolean = true,
    val aiProvider: String = AiProvider.DEFAULT.key,
    val aiModels: Map<String, String> = emptyMap(),
    val aiConsented: Set<String> = emptySet(),
    /** A short taste summary may go with AI requests (separate consent, off by default). */
    val aiShareTaste: Boolean = false,
    val aiCustomUrl: String = "",
    /** "Ask SCARY" quick chips on the home screen. */
    val aiHomeRow: Boolean = true,
    /** ISO country chosen for rent and buy offers; null follows the device ([io.github.diamondrubinny.scary.core.links.Region]). */
    val rentBuyCountry: String? = null,
    /** A trailer plays by itself next to a movie the focus rests on. On by default, after [trailerIntroSeen]. */
    val trailerAutoplay: Boolean = true,
    /** Trailers that play by themselves start muted unless this is on. */
    val trailerSound: Boolean = false,
    /** The viewer has read what playing trailers here means (YouTube) and chosen to keep them or turn them off. */
    val trailerIntroSeen: Boolean = false,
    /** Films the viewer loved, newest first: "Because you loved X". */
    val lovedIds: List<String> = emptyList(),
    /** Films marked "not for me", newest first: never suggested again. */
    val dislikedIds: List<String> = emptyList(),
    /** When each film was marked as watched (epoch day), for "your year in fear"; films marked before this was kept have none. */
    val watchedDays: Map<String, Long> = emptyMap(),
    /** When the sleep timer ends, in wall-clock milliseconds; null when it is off. */
    val sleepAtMillis: Long? = null,
    /** The bucket list: films to watch one after another, in order. */
    val bucketIds: List<String> = emptyList(),
    /** The bucket-list film handed to Stremio, until SCARY is back on screen; null otherwise. */
    val bucketPlaying: String? = null,
) {
    val parentalEnabled: Boolean get() = pinHash != null
}

/** How the viewer rated a film: one or the other, never both. */
enum class Rating { LOVED, NOT_FOR_ME }

/** Local-only preferences. No account, no sync, no analytics. */
class SettingsRepository(private val context: Context) {

    val prefs: Flow<LauncherPrefs> = context.settingsStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            LauncherPrefs(
                effectsEnabled = p[EFFECTS] ?: systemAnimationsEnabled(),
                recentIds = RecentList.decode(p[RECENT]),
                pinned = p[PINNED]?.let(RecentList::decode) ?: KnownPackages.PINNED,
                hidden = p[HIDDEN] ?: emptySet(),
                nightMaxFear = (p[NIGHT_MAX_FEAR] ?: MAX_FEAR).coerceIn(1, MAX_FEAR),
                pinHash = PinHash.decode(p[PIN_HASH]),
                lockFromFear = (p[LOCK_FROM_FEAR] ?: DEFAULT_LOCK_FROM_FEAR).coerceIn(1, MAX_FEAR),
                lockout = LockoutState(p[PIN_FAILED] ?: 0, p[PIN_LOCKED_UNTIL] ?: 0L),
                soundsEnabled = p[SOUNDS] ?: false,
                onlineEnabled = p[ONLINE] ?: true,
                myListIds = RecentList.decode(p[MY_LIST]),
                familyNight = p[FAMILY_NIGHT] ?: false,
                seasonalRows = p[SEASONAL_ROWS] ?: true,
                watchedIds = RecentList.decode(p[WATCHED]),
                autoWatched = p[AUTO_WATCHED] ?: true,
                hideWatched = p[HIDE_WATCHED] ?: false,
                tasteInviteDismissed = p[TASTE_INVITE_DISMISSED] ?: false,
                autoUpdateCheck = p[AUTO_UPDATE_CHECK] ?: true,
                updateSkippedVersion = p[UPDATE_SKIPPED],
                updateLaterUntil = p[UPDATE_LATER_UNTIL],
                updateLastCheck = p[UPDATE_LAST_CHECK],
                aiEnabled = p[AI_ENABLED] ?: true,
                aiProvider = p[AI_PROVIDER] ?: AiProvider.DEFAULT.key,
                aiModels = decodeModels(p[AI_MODELS]),
                aiConsented = p[AI_CONSENTED].orEmpty(),
                aiShareTaste = p[AI_SHARE_TASTE] ?: false,
                aiCustomUrl = p[AI_CUSTOM_URL].orEmpty(),
                aiHomeRow = p[AI_HOME_ROW] ?: true,
                rentBuyCountry = Region.normalize(p[RENT_BUY_COUNTRY]),
                trailerAutoplay = p[TRAILER_AUTOPLAY] ?: true,
                trailerSound = p[TRAILER_SOUND] ?: false,
                trailerIntroSeen = p[TRAILER_INTRO_SEEN] ?: false,
                lovedIds = RecentList.decode(p[LOVED]),
                dislikedIds = RecentList.decode(p[DISLIKED]),
                watchedDays = decodeDays(p[WATCHED_DAYS]),
                sleepAtMillis = p[SLEEP_AT],
                bucketIds = RecentList.decode(p[BUCKET]),
                bucketPlaying = p[BUCKET_PLAYING]?.takeIf(String::isNotBlank),
            )
        }
        .distinctUntilChanged()

    suspend fun setEffectsEnabled(enabled: Boolean) = edit { it[EFFECTS] = enabled }

    suspend fun pushRecent(movieId: String) = edit {
        it[RECENT] = RecentList.encode(RecentList.push(RecentList.decode(it[RECENT]), movieId))
    }

    /** Adds the movie to "My list", or removes it if it is already there. Returns true when it was added. */
    suspend fun toggleMyList(movieId: String): Boolean {
        var added = false
        edit {
            val current = RecentList.decode(it[MY_LIST])
            added = movieId !in current
            it[MY_LIST] = RecentList.encode(MyList.toggle(current, movieId))
        }
        return added
    }

    /** Marks the movie as watched, or not. Returns true when this changed anything. */
    suspend fun setWatched(movieId: String, watched: Boolean): Boolean {
        var changed = false
        edit {
            val current = RecentList.decode(it[WATCHED])
            changed = (movieId in current) != watched
            if (changed) {
                it[WATCHED] = RecentList.encode(if (watched) WatchedList.add(current, movieId) else WatchedList.remove(current, movieId))
                val days = decodeDays(it[WATCHED_DAYS]) - movieId
                val updated = if (watched) mapOf(movieId to LocalDate.now().toEpochDay()) + days else days
                it[WATCHED_DAYS] = encodeDays(updated.entries.take(WatchedList.MAX_SIZE).associate { e -> e.key to e.value })
            }
        }
        return changed
    }

    suspend fun setAutoWatched(enabled: Boolean) = edit { it[AUTO_WATCHED] = enabled }

    suspend fun setHideWatched(enabled: Boolean) = edit { it[HIDE_WATCHED] = enabled }

    suspend fun setTasteInviteDismissed(dismissed: Boolean) = edit { it[TASTE_INVITE_DISMISSED] = dismissed }

    suspend fun setAutoUpdateCheck(enabled: Boolean) = edit { it[AUTO_UPDATE_CHECK] = enabled }

    suspend fun setAiEnabled(enabled: Boolean) = edit { it[AI_ENABLED] = enabled }

    suspend fun setAiProvider(provider: AiProvider) = edit { it[AI_PROVIDER] = provider.key }

    suspend fun setAiModel(provider: AiProvider, model: String?) = edit {
        val models = decodeModels(it[AI_MODELS]).toMutableMap()
        if (model == null) models.remove(provider.key) else models[provider.key] = model
        it[AI_MODELS] = models.entries.joinToString("\n") { (k, v) -> "$k=$v" }
    }

    /** The viewer agreed to send requests to [provider] (asked once per service), or took it back. */
    suspend fun setAiConsent(provider: AiProvider, agreed: Boolean) = edit {
        val set = it[AI_CONSENTED].orEmpty()
        it[AI_CONSENTED] = if (agreed) set + provider.key else set - provider.key
    }

    suspend fun setAiShareTaste(enabled: Boolean) = edit { it[AI_SHARE_TASTE] = enabled }

    suspend fun setAiCustomUrl(url: String) = edit { it[AI_CUSTOM_URL] = url }

    suspend fun setAiHomeRow(enabled: Boolean) = edit { it[AI_HOME_ROW] = enabled }

    suspend fun setUpdateLastCheck(millis: Long) = edit { it[UPDATE_LAST_CHECK] = millis }

    suspend fun skipUpdate(versionCode: Int) = edit {
        it[UPDATE_SKIPPED] = versionCode
        it.remove(UPDATE_LATER_UNTIL)
    }

    suspend fun updateLater(untilMillis: Long) = edit { it[UPDATE_LATER_UNTIL] = untilMillis }

    /** Empties "Recently opened". Watched, My list and the ratings stay. */
    suspend fun clearRecent() = edit { it.remove(RECENT) }

    /** "Loved it" or "not for me" (one excludes the other), or neither with null. */
    suspend fun setRating(movieId: String, rating: Rating?) = edit {
        val loved = RecentList.decode(it[LOVED]).filter { id -> id != movieId }
        val disliked = RecentList.decode(it[DISLIKED]).filter { id -> id != movieId }
        it[LOVED] = RecentList.encode((if (rating == Rating.LOVED) listOf(movieId) + loved else loved).take(MAX_RATED))
        it[DISLIKED] = RecentList.encode((if (rating == Rating.NOT_FOR_ME) listOf(movieId) + disliked else disliked).take(MAX_RATED))
    }

    /** The lists as they are stored after [applySync]: the lists have caps, so not everything given may fit. */
    data class StoredLists(val myList: List<String>, val loved: List<String>, val watched: List<String>)

    /**
     * The lists as a sync with the viewer's TMDB account left them (TmdbSync): My list and "Loved it" replaced (null
     * leaves one as it is), films added to Watched. Films loved there are no longer "not for me" here. Films marked
     * watched from TMDB get no date: SCARY does not know when they were seen, and "Your year in fear" counts only
     * the films marked here.
     */
    suspend fun applySync(myList: List<String>?, loved: List<String>?, watchedAdded: List<String>): StoredLists {
        var stored = StoredLists(emptyList(), emptyList(), emptyList())
        edit { p ->
            myList?.let { p[MY_LIST] = RecentList.encode(it.distinct().take(MyList.MAX_SIZE)) }
            loved?.let { list ->
                val kept = list.distinct().take(MAX_RATED)
                p[LOVED] = RecentList.encode(kept)
                p[DISLIKED] = RecentList.encode(RecentList.decode(p[DISLIKED]).filterNot(kept::contains))
            }
            val watched = RecentList.decode(p[WATCHED])
            val added = watchedAdded.distinct().filterNot(watched::contains)
            if (added.isNotEmpty()) p[WATCHED] = RecentList.encode((added + watched).take(WatchedList.MAX_SIZE))
            stored = StoredLists(RecentList.decode(p[MY_LIST]), RecentList.decode(p[LOVED]), RecentList.decode(p[WATCHED]))
        }
        return stored
    }

    suspend fun setSleepAt(millis: Long?) = edit { if (millis == null) it.remove(SLEEP_AT) else it[SLEEP_AT] = millis }

    /** Changes the bucket list with [change] (BucketList's functions), atomically; returns the new list. */
    suspend fun updateBucket(change: (List<String>) -> List<String>): List<String> {
        var updated = emptyList<String>()
        edit {
            updated = change(RecentList.decode(it[BUCKET])).take(BucketList.MAX_SIZE)
            it[BUCKET] = RecentList.encode(updated)
        }
        return updated
    }

    suspend fun setBucketPlaying(movieId: String?) = edit { if (movieId == null) it.remove(BUCKET_PLAYING) else it[BUCKET_PLAYING] = movieId }

    /** Null goes back to the device's own country. */
    suspend fun setRentBuyCountry(code: String?) = edit {
        val country = Region.normalize(code)
        if (country == null) it.remove(RENT_BUY_COUNTRY) else it[RENT_BUY_COUNTRY] = country
    }

    /** Remembers that [pending] was handed to another app; SCARY judges the viewing when it is back on screen. */
    suspend fun setPendingWatch(pending: PendingWatch) = edit { it[PENDING_WATCH] = pending.encode() }

    /**
     * The TV's screen went off at [atMillis]: a movie handed to another app counts as watched up to now, however long
     * the TV then stays off. Nothing happens when no movie is pending.
     */
    suspend fun endPendingWatch(atMillis: Long) = edit {
        val pending = PendingWatch.decode(it[PENDING_WATCH]) ?: return@edit
        it[PENDING_WATCH] = pending.endedAt(atMillis).encode()
    }

    /** The movie handed to another app, if any, forgotten as it is read: each return to SCARY judges it once. */
    suspend fun takePendingWatch(): PendingWatch? {
        var pending: PendingWatch? = null
        edit {
            pending = PendingWatch.decode(it[PENDING_WATCH])
            it.remove(PENDING_WATCH)
        }
        return pending
    }

    suspend fun togglePinned(packageName: String) = editPinned { PinnedList.toggle(it, packageName) }

    suspend fun movePinned(packageName: String, delta: Int) = editPinned { PinnedList.move(it, packageName, delta) }

    suspend fun toggleHidden(packageName: String) = edit {
        val hidden = it[HIDDEN] ?: emptySet()
        it[HIDDEN] = if (packageName in hidden) hidden - packageName else hidden + packageName
    }

    suspend fun setNightMaxFear(level: Int) = edit { it[NIGHT_MAX_FEAR] = level.coerceIn(1, MAX_FEAR) }

    suspend fun setPin(hash: PinHash) = edit {
        it[PIN_HASH] = hash.encode()
        it[PIN_FAILED] = 0
        it[PIN_LOCKED_UNTIL] = 0L
    }

    suspend fun clearPin() = edit {
        it.remove(PIN_HASH)
        it.remove(PIN_FAILED)
        it.remove(PIN_LOCKED_UNTIL)
    }

    suspend fun setLockout(state: LockoutState) = edit {
        it[PIN_FAILED] = state.failedAttempts
        it[PIN_LOCKED_UNTIL] = state.lockedUntilMillis
    }

    suspend fun setLockFromFear(level: Int) = edit { it[LOCK_FROM_FEAR] = level.coerceIn(1, MAX_FEAR) }

    suspend fun setSoundsEnabled(enabled: Boolean) = edit { it[SOUNDS] = enabled }

    suspend fun setOnlineEnabled(enabled: Boolean) = edit { it[ONLINE] = enabled }

    suspend fun setFamilyNight(enabled: Boolean) = edit { it[FAMILY_NIGHT] = enabled }

    suspend fun setSeasonalRows(enabled: Boolean) = edit { it[SEASONAL_ROWS] = enabled }

    suspend fun setTrailerAutoplay(enabled: Boolean) = edit { it[TRAILER_AUTOPLAY] = enabled }

    suspend fun setTrailerSound(enabled: Boolean) = edit { it[TRAILER_SOUND] = enabled }

    /** The answer to the first-time explanation: it is not asked again, and [enabled] becomes the setting. */
    suspend fun answerTrailerIntro(enabled: Boolean) = edit {
        it[TRAILER_INTRO_SEEN] = true
        it[TRAILER_AUTOPLAY] = enabled
    }

    private suspend fun editPinned(change: (List<String>) -> List<String>) = edit {
        val current = it[PINNED]?.let(RecentList::decode) ?: KnownPackages.PINNED
        it[PINNED] = RecentList.encode(change(current))
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.settingsStore.edit { block(it) }
    }

    private fun systemAnimationsEnabled(): Boolean = systemAnimationsEnabled(context)

    /** Whether the TV currently allows animations ("Remove animations" off). */
    fun systemAnimationsOn(): Boolean = systemAnimationsEnabled(context)

    companion object {
        /** False when the TV's accessibility settings removed animations; that always stops decorative motion. */
        fun systemAnimationsEnabled(context: Context): Boolean =
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

        const val MAX_FEAR = 5
        private val EFFECTS = booleanPreferencesKey("effects_enabled")
        private val RECENT = stringPreferencesKey("recent_movie_ids")
        private val MY_LIST = stringPreferencesKey("my_list_movie_ids")
        private val PINNED = stringPreferencesKey("pinned_apps")
        private val HIDDEN = stringSetPreferencesKey("hidden_apps")
        private val NIGHT_MAX_FEAR = intPreferencesKey("night_max_fear")
        private val PIN_HASH = stringPreferencesKey("parental_pin_hash")
        private val PIN_FAILED = intPreferencesKey("parental_pin_failed")
        private val PIN_LOCKED_UNTIL = longPreferencesKey("parental_pin_locked_until")
        private val LOCK_FROM_FEAR = intPreferencesKey("parental_lock_from_fear")
        private val SOUNDS = booleanPreferencesKey("ambient_sounds")
        private val ONLINE = booleanPreferencesKey("online_artwork")
        private val FAMILY_NIGHT = booleanPreferencesKey("family_night")
        private val SEASONAL_ROWS = booleanPreferencesKey("seasonal_rows")
        private val WATCHED = stringPreferencesKey("watched_movie_ids")
        private val AUTO_WATCHED = booleanPreferencesKey("auto_watched")
        private val HIDE_WATCHED = booleanPreferencesKey("hide_watched")
        private val TASTE_INVITE_DISMISSED = booleanPreferencesKey("taste_invite_dismissed")
        private val AUTO_UPDATE_CHECK = booleanPreferencesKey("auto_update_check")
        private val UPDATE_SKIPPED = intPreferencesKey("update_skipped_version")
        private val UPDATE_LATER_UNTIL = longPreferencesKey("update_later_until")
        private val UPDATE_LAST_CHECK = longPreferencesKey("update_last_check")
        private val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        private val AI_PROVIDER = stringPreferencesKey("ai_provider")
        private val AI_MODELS = stringPreferencesKey("ai_models")
        private val AI_CONSENTED = stringSetPreferencesKey("ai_consented")
        private val AI_SHARE_TASTE = booleanPreferencesKey("ai_share_taste")
        private val AI_CUSTOM_URL = stringPreferencesKey("ai_custom_url")
        private val AI_HOME_ROW = booleanPreferencesKey("ai_home_row")

        /** "openai=gpt-x" lines: the model saved for each service. */
        fun decodeModels(raw: String?): Map<String, String> = raw.orEmpty().lines()
            .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() && it[1].isNotBlank() } }
            .associate { it[0] to it[1] }
        private val PENDING_WATCH = stringPreferencesKey("pending_watch")
        private val RENT_BUY_COUNTRY = stringPreferencesKey("rent_buy_country")
        private val TRAILER_AUTOPLAY = booleanPreferencesKey("trailer_autoplay")
        private val TRAILER_SOUND = booleanPreferencesKey("trailer_sound")
        private val TRAILER_INTRO_SEEN = booleanPreferencesKey("trailer_intro_seen")
        private val LOVED = stringPreferencesKey("loved_movie_ids")
        private val DISLIKED = stringPreferencesKey("disliked_movie_ids")
        private val WATCHED_DAYS = stringPreferencesKey("watched_movie_days")
        private val SLEEP_AT = longPreferencesKey("sleep_at_millis")
        private val BUCKET = stringPreferencesKey("bucket_movie_ids")
        private val BUCKET_PLAYING = stringPreferencesKey("bucket_playing")
        private const val MAX_RATED = 500

        /** "id@epochDay,id@epochDay", newest first; anything malformed is skipped. */
        private fun decodeDays(raw: String?): Map<String, Long> =
            RecentList.decode(raw).mapNotNull { entry ->
                val at = entry.lastIndexOf('@')
                val day = entry.substring(at + 1).toLongOrNull()
                if (at <= 0 || day == null) null else entry.substring(0, at) to day
            }.toMap()

        private fun encodeDays(days: Map<String, Long>): String = RecentList.encode(days.map { (id, day) -> "$id@$day" })
        const val DEFAULT_LOCK_FROM_FEAR = 4
    }
}
