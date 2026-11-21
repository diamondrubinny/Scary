// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.content.Context
import androidx.core.content.edit
import io.github.diamondrubinny.scary.core.player.LanguagePrefs
import io.github.diamondrubinny.scary.core.player.Languages
import io.github.diamondrubinny.scary.core.player.TrackChoice
import io.github.diamondrubinny.scary.core.player.TrackMemory
import io.github.diamondrubinny.scary.core.player.WatchEntry
import io.github.diamondrubinny.scary.core.player.WatchHistory
import io.github.diamondrubinny.scary.core.realdebrid.RdMatches
import io.github.diamondrubinny.scary.core.stremio.ContinueDismissals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The internal player's watch history (core [WatchHistory]): where the viewer stopped in each of their Real-Debrid
 * files and the spots they marked. Kept on this TV only (excluded from backups like everything else), by the file's
 * key in the account, never its link; nothing of it leaves the TV. Also the player's own settings (subtitle size,
 * picture, night sound, the languages to choose), which outlast a film, and the tracks chosen for each file.
 */
class WatchStore(context: Context) {
    private val prefs = context.getSharedPreferences("player_history", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("player_positions", Context.MODE_PRIVATE)

    /** What goes after the keys that belong to the viewer on screen (core Profiles): nothing for the first profile. */
    private var suffix = ""
    private val historyKey get() = HISTORY + suffix
    private val dismissedKey get() = STREMIO_DISMISSED + suffix

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<WatchEntry>> = _entries.asStateFlow()

    fun entry(key: String): WatchEntry? = _entries.value.firstOrNull { it.key == key }

    @Synchronized
    fun update(change: (List<WatchEntry>) -> List<WatchEntry>) {
        val before = _entries.value
        val after = change(before)
        if (after == before) return
        _entries.value = after
        prefs.edit { putString(historyKey, WatchHistory.encode(after)) }
    }

    /**
     * Another viewer is on screen: "Continue watching" and what they took out of the Stremio row are theirs. The
     * player's settings, the tracks chosen per file and which film a file is belong to the TV and stay.
     */
    @Synchronized
    fun useProfile(profileSuffix: String) {
        if (profileSuffix == suffix) return
        suffix = profileSuffix
        _entries.value = load()
        _stremioDismissed.value = ContinueDismissals.decode(prefs.getString(dismissedKey, null))
    }

    /** A removed profile's history goes with it; never the first profile's. */
    @Synchronized
    fun removeProfile(profileSuffix: String) {
        if (profileSuffix.isEmpty()) return
        prefs.edit {
            remove(HISTORY + profileSuffix)
            remove(STREMIO_DISMISSED + profileSuffix)
        }
    }

    /** "Clear watch history", and signing out of Real-Debrid: the tracks chosen per file go with it. */
    @Synchronized
    fun clear() {
        _entries.value = emptyList()
        memories.clear()
        _fileFilms.value = emptyMap()
        prefs.edit {
            remove(historyKey)
            remove(TRACK_MEMORY)
            remove(FILE_FILMS)
        }
    }

    private val _languages = MutableStateFlow(readLanguages())

    /** The languages the player chooses by itself in every file (core LanguagePrefs): English unless changed. */
    val languages: StateFlow<LanguagePrefs> = _languages.asStateFlow()

    fun setLanguages(value: LanguagePrefs) {
        _languages.value = value
        prefs.edit {
            // "" keeps the file's default audio, or subtitles off.
            putString(AUDIO_LANGUAGE, value.audio.orEmpty())
            putString(SUBTITLE_LANGUAGE, value.subtitles.orEmpty())
        }
    }

    private fun readLanguages(): LanguagePrefs {
        fun read(key: String): String? = if (prefs.contains(key)) Languages.normalize(prefs.getString(key, null)) else LanguagePrefs.DEFAULT_LANGUAGE
        return LanguagePrefs(audio = read(AUDIO_LANGUAGE), subtitles = read(SUBTITLE_LANGUAGE))
    }

    private val _onlineSubtitles = MutableStateFlow(prefs.getBoolean(ONLINE_SUBTITLES, true))

    /**
     * Whether the player may look for subtitles online by the film's name (on unless the viewer turns it off). Off sends
     * nothing: no search, no film id.
     */
    val onlineSubtitles: StateFlow<Boolean> = _onlineSubtitles.asStateFlow()

    fun setOnlineSubtitles(value: Boolean) {
        _onlineSubtitles.value = value
        prefs.edit { putBoolean(ONLINE_SUBTITLES, value) }
    }

    private val _fileFilms = MutableStateFlow<Map<String, String>>(RdMatches.decode(prefs.getString(FILE_FILMS, null)))

    /**
     * Which film the viewer says each of their Real-Debrid files is (file key to film id, "" for "not a film in the
     * library"), when SCARY's guess from the name was wrong. On this TV only, not backed up.
     */
    val fileFilms: StateFlow<Map<String, String>> = _fileFilms.asStateFlow()

    /** Sets (or, with null, clears) the film of one file. */
    @Synchronized
    fun setFileFilm(fileKey: String, movieId: String?) {
        val next = RdMatches.with(_fileFilms.value, fileKey, movieId)
        _fileFilms.value = next
        prefs.edit { putString(FILE_FILMS, RdMatches.encode(next)) }
    }

    private val _stremioDismissed = MutableStateFlow<Map<String, Long>>(ContinueDismissals.decode(prefs.getString(STREMIO_DISMISSED, null)))

    /**
     * What the viewer took out of the "Continue in Stremio" row (core ContinueDismissals): IMDb id to when. On this TV
     * only; nothing is changed in the Stremio account.
     */
    val stremioDismissed: StateFlow<Map<String, Long>> = _stremioDismissed.asStateFlow()

    @Synchronized
    fun dismissStremio(imdbId: String, nowMillis: Long) {
        val next = ContinueDismissals.with(_stremioDismissed.value, imdbId, nowMillis)
        _stremioDismissed.value = next
        prefs.edit { putString(dismissedKey, ContinueDismissals.encode(next)) }
    }

    private val memories: LinkedHashMap<String, TrackMemory> by lazy { TrackChoice.decodeAll(prefs.getString(TRACK_MEMORY, null)) }

    /** The tracks the viewer chose for one file, if they changed any. */
    @Synchronized
    fun trackMemory(fileKey: String): TrackMemory? = memories[fileKey]

    @Synchronized
    fun rememberTracks(fileKey: String, memory: TrackMemory) {
        if (!WatchHistory.isValidKey(fileKey)) return
        memories.remove(fileKey)
        memories[fileKey] = memory
        while (memories.size > TrackChoice.MAX_FILES) memories.remove(memories.keys.first())
        prefs.edit { putString(TRACK_MEMORY, TrackChoice.encodeAll(memories)) }
    }

    var subtitleSize: Int
        get() = prefs.getInt(SUBTITLE_SIZE, 1).coerceIn(0, 2)
        set(value) = prefs.edit { putInt(SUBTITLE_SIZE, value.coerceIn(0, 2)) }

    /** How subtitles look in every film: 0 white, 1 yellow. */
    var subtitleColor: Int
        get() = prefs.getInt(SUBTITLE_COLOR, 0).coerceIn(0, 1)
        set(value) = prefs.edit { putInt(SUBTITLE_COLOR, value.coerceIn(0, 1)) }

    /** A dark box behind the subtitles (on unless turned off). */
    var subtitleBackground: Boolean
        get() = prefs.getBoolean(SUBTITLE_BACKGROUND, true)
        set(value) = prefs.edit { putBoolean(SUBTITLE_BACKGROUND, value) }

    /** Where the subtitles sit: 0 at the bottom, 1 higher (clear of a film's own burnt-in text or a TV's cut edge). */
    var subtitlePosition: Int
        get() = prefs.getInt(SUBTITLE_POSITION, 0).coerceIn(0, 1)
        set(value) = prefs.edit { putInt(SUBTITLE_POSITION, value.coerceIn(0, 1)) }

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
        val stored = WatchHistory.decode(prefs.getString(historyKey, null))
        // What SCARY 0.5 kept was the TV's own, so it is carried into the first profile only.
        if (suffix.isNotEmpty()) return stored
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
        const val SUBTITLE_COLOR = "subtitle_color"
        const val SUBTITLE_BACKGROUND = "subtitle_background"
        const val SUBTITLE_POSITION = "subtitle_position"
        const val PICTURE = "picture"
        const val NIGHT_SOUND = "night_sound"
        const val AUDIO_LANGUAGE = "audio_language"
        const val SUBTITLE_LANGUAGE = "subtitle_language"
        const val TRACK_MEMORY = "track_memory"
        const val ONLINE_SUBTITLES = "online_subtitles"
        const val FILE_FILMS = "file_films"
        const val STREMIO_DISMISSED = "stremio_dismissed"
        const val LEGACY_STAMP = "at:"
    }
}
