// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.player

import java.util.Locale

/** An audio track or a subtitle track of the playing file, or a subtitle file next to it. */
enum class TrackKind { AUDIO, TEXT }

/**
 * One track as the player describes it, in plain values (no Media3 types), so choosing and naming can be tested here.
 * Names and labels come from the file and are untrusted: [TrackInfo.of] cleans them.
 */
data class TrackInfo(
    val kind: TrackKind,
    /** Unique within the file: "g<group>:<track>" for the file's own tracks, "x<index>" for subtitle files. */
    val id: String,
    /** ISO 639-1 where one exists ("en", "he"), else the code as given; null when unknown. */
    val language: String?,
    /** The title the file gives the track ("Director's commentary"), cleaned; null when none. */
    val label: String?,
    /** The format's MIME type ("audio/eac3", "application/pgs"). */
    val mimeType: String?,
    /** Audio channels; 0 when unknown or for subtitles. */
    val channels: Int = 0,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isCommentary: Boolean = false,
    /** Audio description for the blind. */
    val isDescription: Boolean = false,
    /** Subtitles for the deaf and hard of hearing (SDH): sounds as well as words. */
    val isSdh: Boolean = false,
    /** False when this TV cannot play it (a DTS track on a TV without DTS): shown, but not chosen. */
    val supported: Boolean = true,
    /** A subtitle file from the same torrent, fetched when chosen. */
    val external: Boolean = false,
    /** A subtitle found online by the film's name (core OnlineSubtitles), fetched and added by the player. */
    val online: Boolean = false,
) {
    /**
     * The same track again (after the file reloads, or when it is played another day): what a choice is remembered by.
     * A subtitle file is known by its index among the torrent's ([id]), the file's own tracks by what they are.
     */
    val key: String
        get() = when {
            // An online subtitle is known by its language and release; its place in the player changes with every fetch.
            online -> "${kind.name}|$ONLINE_MARK|${language.orEmpty()}|${label.orEmpty()}"
            external -> "${kind.name}|x|$id"
            else -> listOf(kind.name, language.orEmpty(), label.orEmpty(), mimeType.orEmpty(), channels).joinToString("|")
        }

    val isBitmapSubtitle: Boolean get() = kind == TrackKind.TEXT && TrackNames.isBitmapSubtitle(mimeType)

    companion object {
        private const val MAX_LABEL = 80

        /** A track from the player's raw values, with its language normalized and its label cleaned. */
        fun of(
            kind: TrackKind,
            id: String,
            language: String?,
            label: String?,
            mimeType: String?,
            channels: Int = 0,
            isDefault: Boolean = false,
            isForced: Boolean = false,
            isCommentary: Boolean = false,
            isDescription: Boolean = false,
            isSdh: Boolean = false,
            supported: Boolean = true,
            external: Boolean = false,
            online: Boolean = false,
        ): TrackInfo = TrackInfo(
            kind = kind,
            id = id,
            language = Languages.normalize(language),
            label = cleanLabel(label),
            mimeType = mimeType?.trim()?.lowercase()?.takeIf { it.length in 1..64 },
            channels = channels.coerceIn(0, 32),
            isDefault = isDefault,
            isForced = isForced || label.orEmpty().contains("forced", ignoreCase = true),
            isCommentary = isCommentary || label.orEmpty().contains("commentary", ignoreCase = true),
            isDescription = isDescription,
            isSdh = isSdh || Regex("\\b(sdh|cc|hearing impaired)\\b", RegexOption.IGNORE_CASE).containsMatchIn(label.orEmpty()),
            supported = supported,
            external = external,
            online = online,
        )

        private const val ONLINE_MARK = "o"

        /** The language of a remembered online subtitle ([key] of a track found online), else null. */
        fun onlineLanguageOf(key: String?): String? {
            val parts = key?.split('|') ?: return null
            return if (parts.size >= 3 && parts[0] == TrackKind.TEXT.name && parts[1] == ONLINE_MARK) parts[2].takeIf { it.isNotEmpty() } else null
        }

        /** Control characters out, whitespace folded, at most [MAX_LABEL] characters; nothing left is no label. */
        fun cleanLabel(label: String?): String? =
            label?.filterNot { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }
                ?.replace(Regex("\\s+"), " ")?.trim()?.take(MAX_LABEL)?.takeIf { it.isNotEmpty() }
    }
}

/** Plain names for formats and channel layouts, as people know them from their amplifier or the box of the film. */
object TrackNames {
    private val AUDIO = mapOf(
        "audio/ac3" to "Dolby Digital",
        "audio/eac3" to "Dolby Digital Plus",
        "audio/eac3-joc" to "Dolby Atmos",
        "audio/ac4" to "Dolby AC-4",
        "audio/true-hd" to "Dolby TrueHD",
        "audio/vnd.dts" to "DTS",
        "audio/vnd.dts.hd" to "DTS-HD",
        "audio/vnd.dts.hd;profile=lbr" to "DTS Express",
        "audio/vnd.dts.uhd;profile=p2" to "DTS:X",
        "audio/mp4a-latm" to "AAC",
        "audio/opus" to "Opus",
        "audio/flac" to "FLAC",
        "audio/mpeg" to "MP3",
        "audio/mpeg-l2" to "MP2",
        "audio/vorbis" to "Vorbis",
        "audio/raw" to "PCM",
        "audio/alac" to "ALAC",
    )
    private val TEXT = mapOf(
        "application/x-subrip" to "SRT",
        "text/x-ssa" to "ASS",
        "text/vtt" to "WebVTT",
        "application/ttml+xml" to "TTML",
        "application/pgs" to "PGS",
        "application/vobsub" to "VobSub",
        "application/dvbsubs" to "DVB",
        "application/cea-608" to "CC",
        "application/cea-708" to "CC",
        "application/x-media3-cues" to null,
    )
    private val BITMAP = setOf("application/pgs", "application/vobsub", "application/dvbsubs")

    /** "Dolby Digital Plus", "PGS"; null for formats without a name worth showing. */
    fun format(mimeType: String?): String? {
        val mime = mimeType?.lowercase() ?: return null
        return AUDIO[mime] ?: TEXT[mime]
    }

    /** "5.1", "7.1"… for three channels or more; mono and stereo are words, in the viewer's language. */
    fun surround(channels: Int): String? = when (channels) {
        in Int.MIN_VALUE..2 -> null
        3 -> "2.1"
        6 -> "5.1"
        7 -> "6.1"
        8 -> "7.1"
        else -> "$channels ch"
    }

    /** Subtitles drawn as pictures (Blu-ray, DVD, broadcast) rather than text. */
    fun isBitmapSubtitle(mimeType: String?): Boolean = mimeType?.lowercase() in BITMAP
}

/** Language codes from files and from Media3, made comparable: "eng", "en-US" and "en" are all English. */
object Languages {
    /** ISO 639-2/B codes that differ from the terminology codes Java knows. */
    private val BIBLIOGRAPHIC = mapOf(
        "alb" to "sq", "arm" to "hy", "baq" to "eu", "bur" to "my", "chi" to "zh", "cze" to "cs", "dut" to "nl",
        "fre" to "fr", "geo" to "ka", "ger" to "de", "gre" to "el", "ice" to "is", "mac" to "mk", "mao" to "mi",
        "may" to "ms", "per" to "fa", "rum" to "ro", "slo" to "sk", "tib" to "bo", "wel" to "cy",
    )

    /** Names used in subtitle file names ("Movie.Hebrew.srt"), lowercase. */
    private val NAMES = mapOf(
        "english" to "en", "hebrew" to "he", "arabic" to "ar", "russian" to "ru", "portuguese" to "pt",
        "brazilian" to "pt", "spanish" to "es", "french" to "fr", "german" to "de", "italian" to "it",
        "japanese" to "ja", "korean" to "ko", "chinese" to "zh", "dutch" to "nl", "polish" to "pl", "turkish" to "tr",
        "greek" to "el", "swedish" to "sv", "danish" to "da", "norwegian" to "no", "finnish" to "fi",
        "czech" to "cs", "hungarian" to "hu", "romanian" to "ro", "ukrainian" to "uk", "persian" to "fa",
        "thai" to "th", "vietnamese" to "vi", "indonesian" to "id", "hindi" to "hi",
    )

    private val THREE_TO_TWO: Map<String, String> by lazy {
        Locale.getISOLanguages().mapNotNull { two -> runCatching { Locale.forLanguageTag(two).isO3Language }.getOrNull()?.let { it to two } }.toMap()
    }

    /** Old codes Java (and some Android versions) still use: Hebrew, Indonesian, Yiddish. */
    private val LEGACY = mapOf("iw" to "he", "in" to "id", "ji" to "yi")

    private val TWO = Locale.getISOLanguages().mapTo(HashSet()) { LEGACY[it] ?: it }

    /** "en", "he"…; "iw" is Hebrew's old code. Null for unknown ("und"), empty or malformed codes. */
    fun normalize(code: String?): String? {
        val base = code?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_') ?: return null
        if (base.isEmpty() || base == "und" || base == "unk" || base == "mis" || base == "zxx" || !base.all { it in 'a'..'z' }) return null
        val known = when (base.length) {
            2 -> LEGACY[base] ?: base.takeIf { it in TWO }
            3 -> BIBLIOGRAPHIC[base] ?: THREE_TO_TWO[base] ?: base
            else -> null
        }
        return known?.let { LEGACY[it] ?: it }
    }

    /**
     * The language a subtitle file's name gives, from its last words: "Movie.2019.heb.srt", "Movie.he.forced.srt",
     * "Movie_Hebrew.srt". Null when none is recognized.
     */
    fun fromFileName(name: String): String? {
        val words = name.substringBeforeLast('.').lowercase().split('.', '_', '-', ' ', '[', ']', '(', ')').filter { it.isNotEmpty() }
        for (word in words.asReversed().take(4)) {
            NAMES[word]?.let { return it }
            if (word.length in 2..3 && word !in NOT_LANGUAGES) normalize(word)?.takeIf { it.length == 2 }?.let { return it }
        }
        return null
    }

    /** Short words in file names that look like language codes but are not. */
    private val NOT_LANGUAGES = setOf("sdh", "cc", "hi", "web", "dl", "rip", "hd", "sd", "bd", "x26", "avc", "dts", "aac", "ac3", "mkv", "srt", "sub", "the", "and", "for", "one", "two")
}
