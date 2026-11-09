// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.content.Context
import androidx.core.content.edit
import io.github.diamondrubinny.scary.core.player.WatchEntry
import io.github.diamondrubinny.scary.core.player.WatchHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The internal player's watch history (core [WatchHistory]): where the viewer stopped in each of their Real-Debrid
 * files and the spots they marked. Kept on this TV only (excluded from backups like everything else), by the file's
 * key in the account, never its link; nothing of it leaves the TV. Also the player's own settings (subtitle size,
 * picture, night sound), which outlast a film.
 */
class WatchStore(context: Context) {
    private val prefs = context.getSharedPreferences("player_history", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("player_positions", Context.MODE_PRIVATE)

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<WatchEntry>> = _entries.asStateFlow()

    fun entry(key: String): WatchEntry? = _entries.value.firstOrNull { it.key == key }

    @Synchronized
    fun update(change: (List<WatchEntry>) -> List<WatchEntry>) {
        val before = _entries.value
        val after = change(before)
        if (after == before) return
        _entries.value = after
        prefs.edit { putString(HISTORY, WatchHistory.encode(after)) }
    }

    /** "Clear watch history", and signing out of Real-Debrid. */
    @Synchronized
    fun clear() {
        _entries.value = emptyList()
        prefs.edit { remove(HISTORY) }
    }

    var subtitleSize: Int
        get() = prefs.getInt(SUBTITLE_SIZE, 1).coerceIn(0, 2)
        set(value) = prefs.edit { putInt(SUBTITLE_SIZE, value.coerceIn(0, 2)) }

    var pictureMode: Int
        get() = prefs.getInt(PICTURE, 0).coerceIn(0, 2)
        set(value) = prefs.edit { putInt(PICTURE, value.coerceIn(0, 2)) }

    var nightSound: Boolean
        get() = prefs.getBoolean(NIGHT_SOUND, false)
        set(value) = prefs.edit { putBoolean(NIGHT_SOUND, value) }

    /**
     * The history as stored, or, the first time, the resume positions SCARY 0.5 kept (file key → position, and
     * "at:" + key → when), carried over without names: they show under "Continue watching" once played again.
     */
    private fun load(): List<WatchEntry> {
        val stored = WatchHistory.decode(prefs.getString(HISTORY, null))
        val old = legacy.all
        if (old.isEmpty()) return stored
        val carried = old.entries.mapNotNull { (key, value) ->
            val position = (value as? Long)?.takeIf { it > 0 } ?: return@mapNotNull null
            if (key.startsWith(LEGACY_STAMP) || !WatchHistory.isValidKey(key) || stored.any { it.key == key }) return@mapNotNull null
            WatchEntry(key, name = "", movieId = null, positionMs = position, durationMs = 0, lastWatched = (old[LEGACY_STAMP + key] as? Long) ?: 0L)
        }
        val merged = WatchHistory.decode(WatchHistory.encode(stored + carried))
        prefs.edit { putString(HISTORY, WatchHistory.encode(merged)) }
        legacy.edit { clear() }
        return merged
    }

    private companion object {
        const val HISTORY = "history"
        const val SUBTITLE_SIZE = "subtitle_size"
        const val PICTURE = "picture"
        const val NIGHT_SOUND = "night_sound"
        const val LEGACY_STAMP = "at:"
    }
}
