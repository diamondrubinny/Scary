// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.player

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json

/**
 * The languages the player chooses by itself in every file: [audio] null keeps the file's own default track,
 * [subtitles] null keeps subtitles off (forced ones still show). English for both unless the viewer changes it.
 */
data class LanguagePrefs(val audio: String? = DEFAULT_LANGUAGE, val subtitles: String? = DEFAULT_LANGUAGE) {
    companion object {
        const val DEFAULT_LANGUAGE = "en"

        /** The languages offered in Settings → Player, besides "the file's default" and "off". */
        val OFFERED = listOf("en", "he", "ar", "ru", "pt", "es", "fr", "de", "it", "ja", "ko")
    }
}

/** What the viewer chose for one file, kept so it comes back with "Continue watching". */
data class TrackMemory(val audio: String? = null, val subtitle: String? = null, val subtitlesOff: Boolean = false)

/** Which audio and subtitle tracks to play: what the viewer chose for this file, else their languages, else the file's. */
object TrackChoice {
    fun pickAudio(tracks: List<TrackInfo>, prefs: LanguagePrefs, memory: TrackMemory? = null): TrackInfo? {
        val audio = tracks.filter { it.kind == TrackKind.AUDIO && it.supported }
        if (audio.isEmpty()) return null
        memory?.audio?.let { key -> audio.firstOrNull { it.key == key }?.let { return it } }
        val plain = audio.filterNot { it.isCommentary || it.isDescription }.ifEmpty { audio }
        prefs.audio?.let { language -> plain.filter { it.language == language }.bestAudio()?.let { return it } }
        return plain.firstOrNull { it.isDefault } ?: plain.first()
    }

    /**
     * Subtitles: the remembered choice, else the preferred language (plain before forced, before SDH), else the
     * forced subtitles in the language being heard (they translate only the foreign passages). Null: none.
     */
    fun pickSubtitle(tracks: List<TrackInfo>, prefs: LanguagePrefs, audioLanguage: String?, memory: TrackMemory? = null): TrackInfo? {
        val text = tracks.filter { it.kind == TrackKind.TEXT && it.supported }
        if (text.isEmpty()) return null
        if (memory != null && (memory.subtitle != null || memory.subtitlesOff)) {
            memory.subtitle?.let { key -> text.firstOrNull { it.key == key }?.let { return it } }
            if (memory.subtitlesOff) return forcedFor(text, audioLanguage)
        }
        val language = prefs.subtitles ?: return forcedFor(text, audioLanguage)
        val inLanguage = text.filter { it.language == language }
        return inLanguage.sortedWith(compareBy<TrackInfo>({ it.isForced }, { it.isSdh }, { it.external }, { !it.isDefault })).firstOrNull()
            ?: forcedFor(text, audioLanguage)
    }

    private fun forcedFor(text: List<TrackInfo>, audioLanguage: String?): TrackInfo? =
        text.firstOrNull { it.isForced && it.language != null && it.language == audioLanguage }
            ?: text.firstOrNull { it.isForced && it.language == null && it.isDefault }

    /** Default first, then the most channels, then the file's order. */
    private fun List<TrackInfo>.bestAudio(): TrackInfo? =
        withIndex().sortedWith(compareBy({ !it.value.isDefault }, { -it.value.channels }, { it.index })).firstOrNull()?.value

    /** The tracks as the window lists them: the file's order, with tracks this TV cannot play last. */
    fun ordered(tracks: List<TrackInfo>, kind: TrackKind): List<TrackInfo> =
        tracks.filter { it.kind == kind }.withIndex().sortedWith(compareBy({ !it.value.supported }, { it.value.external }, { it.index })).map { it.value }

    /** The file has sound, but none of it plays on this TV (DTS or TrueHD only, on a TV without them). */
    fun noPlayableAudio(tracks: List<TrackInfo>): Boolean {
        val audio = tracks.filter { it.kind == TrackKind.AUDIO }
        return audio.isNotEmpty() && audio.none { it.supported }
    }

    // Storage ----------------------------------------------------------------------------------------------------------

    private const val MAX_KEY = 200
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(memory: TrackMemory): String = buildJsonObject {
        memory.audio?.let { put("a", it) }
        memory.subtitle?.let { put("s", it) }
        if (memory.subtitlesOff) put("off", true)
    }.toString()

    /** Files whose choices are kept; the oldest go first. */
    const val MAX_FILES = 60

    /** Every file's choices, by the file's key (WatchHistory), oldest first. */
    fun encodeAll(memories: Map<String, TrackMemory>): String = buildJsonObject {
        memories.entries.toList().takeLast(MAX_FILES).forEach { (file, memory) -> put(file, encode(memory)) }
    }.toString()

    fun decodeAll(text: String?): LinkedHashMap<String, TrackMemory> {
        val out = LinkedHashMap<String, TrackMemory>()
        if (text.isNullOrBlank() || text.length > MAX_FILES * 5 * MAX_KEY) return out
        val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return out
        o.entries.toList().takeLast(MAX_FILES).forEach { (file, value) ->
            if (!WatchHistory.isValidKey(file)) return@forEach
            decode((value as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull)?.let { out[file] = it }
        }
        return out
    }

    /** Hostile input: anything malformed is no memory. */
    fun decode(text: String?): TrackMemory? {
        if (text.isNullOrBlank() || text.length > 4 * MAX_KEY) return null
        val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        fun key(name: String) = (o[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.length <= MAX_KEY }
        val off = (o["off"] as? JsonPrimitive)?.takeIf { !it.isString }?.contentOrNull == "true"
        return TrackMemory(key("a"), key("s"), off).takeIf { it.audio != null || it.subtitle != null || it.subtitlesOff }
    }
}
