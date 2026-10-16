package io.github.diamondrubinny.scary.data

import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.diamondrubinny.scary.core.catalog.RecentList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Local-only preferences. No account, no sync, no analytics. */
class SettingsRepository(private val context: Context) {

    private val data: Flow<Preferences> = context.settingsStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    /** Defaults to off when the system "remove animations" accessibility setting is on. */
    val effectsEnabled: Flow<Boolean> = data.map { it[EFFECTS] ?: systemAnimationsEnabled() }

    val recentIds: Flow<List<String>> = data.map { RecentList.decode(it[RECENT]) }

    suspend fun setEffectsEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[EFFECTS] = enabled }
    }

    suspend fun pushRecent(movieId: String) {
        context.settingsStore.edit { prefs ->
            prefs[RECENT] = RecentList.encode(RecentList.push(RecentList.decode(prefs[RECENT]), movieId))
        }
    }

    private fun systemAnimationsEnabled(): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

    private companion object {
        val EFFECTS = booleanPreferencesKey("effects_enabled")
        val RECENT = stringPreferencesKey("recent_movie_ids")
    }
}
