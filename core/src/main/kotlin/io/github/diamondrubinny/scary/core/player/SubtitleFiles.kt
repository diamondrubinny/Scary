// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.player

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Subtitle files that come with a film in the viewer's own Real-Debrid torrent ("Movie.heb.srt" next to "Movie.mkv"):
 * which belong to which video, and reading them as text whatever their encoding. Many Hebrew subtitle files are in
 * Windows-1255 and come out as gibberish when read as UTF-8; Arabic and Russian ones have the same story.
 */
object SubtitleFiles {
    /** Formats the player reads, by extension, with their MIME type. */
    val FORMATS = mapOf("srt" to "application/x-subrip", "ass" to "text/x-ssa", "ssa" to "text/x-ssa", "vtt" to "text/vtt")

    /** Larger files are not subtitles (or not worth the wait): refused. */
    const val MAX_BYTES = 2 * 1024 * 1024

    /** At most this many subtitle files are offered for one video. */
    const val MAX_PER_VIDEO = 20

    fun isSubtitle(name: String): Boolean = extension(name) in FORMATS

    fun mimeType(name: String): String? = FORMATS[extension(name)]

    private fun extension(name: String) = name.substringAfterLast('.', "").lowercase()

    /**
     * The subtitle files ([names]) that go with [video], best first: those named after it ("Movie.mkv" and
     * "Movie.heb.srt"), then those of the same episode (S01E02), then, when the torrent holds only this one video, the
     * rest. Each returned as its index in [names].
     */
    fun forVideo(video: String, names: List<String>, videosInTorrent: Int): List<Int> {
        val stem = normalized(video.substringBeforeLast('.'))
        val episode = episodeOf(video)
        val ranked = names.withIndex().filter { isSubtitle(it.value) }.mapNotNull { (index, name) ->
            val other = normalized(name.substringBeforeLast('.'))
            val rank = when {
                stem.isNotEmpty() && other.startsWith(stem) -> 0
                episode != null && episodeOf(name) == episode -> 1
                videosInTorrent <= 1 && episodeOf(name) == null -> 2
                else -> return@mapNotNull null
            }
            Triple(index, rank, name)
        }
        return ranked.sortedWith(compareBy({ it.second }, { it.third.lowercase() })).take(MAX_PER_VIDEO).map { it.first }
    }

    private fun normalized(name: String) = name.substringAfterLast('/').lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private val EPISODE = Regex("(?i)\\bs(\\d{1,2})[ ._-]?e(\\d{1,3})\\b|\\b(\\d{1,2})x(\\d{2,3})\\b")

    /** "1x2" for S01E02 or 1x02; null when the name gives no episode. */
    fun episodeOf(name: String): String? {
        val m = EPISODE.find(name) ?: return null
        val season = (m.groupValues[1].ifEmpty { m.groupValues[3] }).toIntOrNull() ?: return null
        val episode = (m.groupValues[2].ifEmpty { m.groupValues[4] }).toIntOrNull() ?: return null
        return "${season}x$episode"
    }

    /**
     * A subtitle file's text: its byte-order mark, else UTF-8 when the bytes are valid UTF-8, else the single-byte
     * encoding of its language ([languageHint], else guessed from the letters). Null when it is too large, empty, or
     * does not read as subtitles of its format ([mimeType]).
     */
    fun decode(bytes: ByteArray, mimeType: String?, languageHint: String? = null): String? {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null
        val text = when {
            bytes.startsWith(0xEF, 0xBB, 0xBF) -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            bytes.startsWith(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            bytes.startsWith(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            else -> strictUtf8(bytes) ?: String(bytes, singleByteCharset(bytes, languageHint))
        }.replace("\r\n", "\n").replace('\r', '\n').removePrefix("﻿")
        if (!looksLikeSubtitles(text, mimeType)) return null
        // Many Hebrew and Arabic files are written in visual order, with a sentence's closing mark at the start of its
        // line; the time-coded formats are fixed here, ASS (with its own fields and styles) is left as it is.
        return if (mimeType == "text/x-ssa") text else fixRtlPunctuation(text)
    }

    private val TAGS_AT_START = Regex("^(?:<[^>]*>|\\{[^}]*\\})*")
    private val TAGS_AT_END = Regex("(?:<[^>]*>|\\{[^}]*\\})*$")
    private const val CLOSING_MARKS = ".,!?:;"

    /**
     * A closing mark at the start of a Hebrew or Arabic line (".זה מפחיד אותנו") moves to the end ("זה מפחיד אותנו."),
     * where it belongs in the text's own direction; without this it shows at the wrong side of the line. Only a single
     * mark or a short run of them ("?!") before RTL letters, after any tags such as <i>; an ellipsis ("...") and any
     * line that is not RTL are left alone, and so are time lines and cue numbers.
     */
    fun fixRtlPunctuation(text: String): String {
        if (text.none { it in '\u0590'..'\u06FF' }) return text
        return text.split('\n').joinToString("\n") { line -> fixLine(line) }
    }

    private fun fixLine(line: String): String {
        if (line.length < 2 || line.contains("-->")) return line
        val lead = TAGS_AT_START.find(line)?.value.orEmpty()
        val rest = line.substring(lead.length)
        val marks = rest.takeWhile { it in CLOSING_MARKS }
        if (marks.isEmpty() || marks.length > 2 || marks.count { it == '.' } > 1) return line
        val body = rest.substring(marks.length).trimStart()
        // The first letter decides the line's direction.
        val first = body.firstOrNull { it.isLetter() } ?: return line
        if (first !in '\u0590'..'\u06FF') return line
        val trailing = TAGS_AT_END.find(body)?.value.orEmpty()
        return lead + body.substring(0, body.length - trailing.length).trimEnd() + marks + trailing
    }

    private fun ByteArray.startsWith(vararg prefix: Int) = size >= prefix.size && prefix.indices.all { this[it].toInt() and 0xFF == prefix[it] }

    private fun strictUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        null
    }

    /**
     * The Windows code page of the file's language. Without a hint: Hebrew letters (0xE0–0xFA) far outnumbering other
     * high bytes point to Windows-1255, Cyrillic-heavy text (0xC0–0xFF) with lowercase dominance to Windows-1251, and
     * Western text to Windows-1252.
     */
    fun singleByteCharset(bytes: ByteArray, languageHint: String?): Charset {
        val name = when (languageHint) {
            "he", "yi" -> "windows-1255"
            "ar", "fa", "ur" -> "windows-1256"
            "ru", "uk", "bg", "sr", "mk", "be" -> "windows-1251"
            "el" -> "windows-1253"
            "tr" -> "windows-1254"
            "pl", "cs", "sk", "hu", "ro", "hr", "sl" -> "windows-1250"
            else -> guess(bytes)
        }
        return runCatching { Charset.forName(name) }.getOrDefault(Charsets.ISO_8859_1)
    }

    private fun guess(bytes: ByteArray): String {
        var high = 0
        var hebrew = 0
        var cyrillicLower = 0
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v < 0x80) continue
            high++
            if (v in 0xE0..0xFA) hebrew++
            if (v in 0xE0..0xFF) cyrillicLower++
        }
        if (high == 0) return "windows-1252"
        // Hebrew has no case, and its letters sit in 0xE0–0xFA; Russian text mixes in capitals (0xC0–0xDF) too.
        val capitals = high - cyrillicLower
        return when {
            hebrew * 10 >= high * 8 && capitals * 20 < high -> "windows-1255"
            cyrillicLower * 10 >= high * 6 -> "windows-1251"
            else -> "windows-1252"
        }
    }

    /** SRT and WebVTT have time lines ("00:01:02,500 --> …"), ASS a [Script Info] or Dialogue lines. */
    private fun looksLikeSubtitles(text: String, mimeType: String?): Boolean = when (mimeType) {
        "text/x-ssa" -> text.contains("[Script Info]", ignoreCase = true) || text.contains("Dialogue:")
        else -> text.contains("-->")
    }
}
