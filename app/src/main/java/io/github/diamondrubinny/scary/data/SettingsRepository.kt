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
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.diamondrubinny.scary.core.apps.PinnedList
import io.github.diamondrubinny.scary.core.catalog.RecentList
import io.github.diamondrubinny.scary.core.links.KnownPackages
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
)

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
            )
        }
        .distinctUntilChanged()

    suspend fun setEffectsEnabled(enabled: Boolean) = edit { it[EFFECTS] = enabled }

    suspend fun pushRecent(movieId: String) = edit {
        it[RECENT] = RecentList.encode(RecentList.push(RecentList.decode(it[RECENT]), movieId))
    }

    suspend fun togglePinned(packageName: String) = editPinned { PinnedList.toggle(it, packageName) }

    suspend fun movePinned(packageName: String, delta: Int) = editPinned { PinnedList.move(it, packageName, delta) }

    suspend fun toggleHidden(packageName: String) = edit {
        val hidden = it[HIDDEN] ?: emptySet()
        it[HIDDEN] = if (packageName in hidden) hidden - packageName else hidden + packageName
    }

    suspend fun setNightMaxFear(level: Int) = edit { it[NIGHT_MAX_FEAR] = level.coerceIn(1, MAX_FEAR) }

    private suspend fun editPinned(change: (List<String>) -> List<String>) = edit {
        val current = it[PINNED]?.let(RecentList::decode) ?: KnownPackages.PINNED
        it[PINNED] = RecentList.encode(change(current))
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.settingsStore.edit { block(it) }
    }

    private fun systemAnimationsEnabled(): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

    companion object {
        const val MAX_FEAR = 5
        private val EFFECTS = booleanPreferencesKey("effects_enabled")
        private val RECENT = stringPreferencesKey("recent_movie_ids")
        private val PINNED = stringPreferencesKey("pinned_apps")
        private val HIDDEN = stringSetPreferencesKey("hidden_apps")
        private val NIGHT_MAX_FEAR = intPreferencesKey("night_max_fear")
    }
}
