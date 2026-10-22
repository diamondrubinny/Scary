// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

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
import io.github.diamondrubinny.scary.core.catalog.RecentList
import io.github.diamondrubinny.scary.core.links.KnownPackages
import io.github.diamondrubinny.scary.core.security.LockoutState
import io.github.diamondrubinny.scary.core.security.PinHash
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

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
) {
    val parentalEnabled: Boolean get() = pinHash != null
}

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

    private suspend fun editPinned(change: (List<String>) -> List<String>) = edit {
        val current = it[PINNED]?.let(RecentList::decode) ?: KnownPackages.PINNED
        it[PINNED] = RecentList.encode(change(current))
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.settingsStore.edit { block(it) }
    }

    private fun systemAnimationsEnabled(): Boolean = systemAnimationsEnabled(context)

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
        const val DEFAULT_LOCK_FROM_FEAR = 4
    }
}
