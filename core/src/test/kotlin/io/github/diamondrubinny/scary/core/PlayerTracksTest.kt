// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.player.LanguagePrefs
import io.github.diamondrubinny.scary.core.player.Languages
import io.github.diamondrubinny.scary.core.player.SubtitleFiles
import io.github.diamondrubinny.scary.core.player.TrackChoice
import io.github.diamondrubinny.scary.core.player.TrackInfo
import io.github.diamondrubinny.scary.core.player.TrackKind
import io.github.diamondrubinny.scary.core.player.TrackMemory
import io.github.diamondrubinny.scary.core.player.TrackNames
import io.github.diamondrubinny.scary.core.realdebrid.RealDebrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class PlayerTracksTest {
    private fun audio(id: String, language: String?, channels: Int = 2, label: String? = null, default: Boolean = false, supported: Boolean = true, mime: String = "audio/ac3") =
        TrackInfo.of(TrackKind.AUDIO, id, language, label, mime, channels = channels, isDefault = default, supported = supported)

    private fun text(id: String, language: String?, label: String? = null, forced: Boolean = false, default: Boolean = false, sdh: Boolean = false, external: Boolean = false, mime: String = "application/x-subrip") =
        TrackInfo.of(TrackKind.TEXT, id, language, label, mime, isForced = forced, isDefault = default, isSdh = sdh, external = external)

    @Test
    fun `formats and channels get the names people know`() {
        assertEquals("Dolby Digital Plus", TrackNames.format("audio/eac3"))
        assertEquals("DTS-HD", TrackNames.format("AUDIO/VND.DTS.HD"))
        assertEquals("Dolby TrueHD", TrackNames.format("audio/true-hd"))
        assertEquals("PGS", TrackNames.format("application/pgs"))
        assertNull(TrackNames.format("audio/unknown"))
        assertEquals("5.1", TrackNames.surround(6))
        assertEquals("7.1", TrackNames.surround(8))
        assertNull(TrackNames.surround(2))
        assertTrue(TrackNames.isBitmapSubtitle("application/pgs"))
        assertFalse(TrackNames.isBitmapSubtitle("application/x-subrip"))
    }

    @Test
    fun `language codes from files and players are made comparable`() {
        assertEquals("en", Languages.normalize("eng"))
        assertEquals("en", Languages.normalize("en-US"))
        assertEquals("he", Languages.normalize("heb"))
        assertEquals("he", Languages.normalize("iw"))
        assertEquals("fr", Languages.normalize("fre"))
        assertEquals("de", Languages.normalize("ger"))
        assertNull(Languages.normalize("und"))
        assertNull(Languages.normalize(""))
        assertNull(Languages.normalize("e1"))
        assertEquals("he", Languages.fromFileName("The.Shining.1980.1080p.heb.srt"))
        assertEquals("he", Languages.fromFileName("The Shining (1980) Hebrew.srt"))
        assertEquals("en", Languages.fromFileName("Movie.en.forced.srt"))
        assertEquals("en", Languages.fromFileName("Movie.English.SDH.srt"))
        assertNull(Languages.fromFileName("Movie.2019.1080p.srt"))
        // "hi" in a subtitle name means hearing impaired, not Hindi.
        assertEquals("en", Languages.fromFileName("Movie.en.hi.srt"))
    }

    @Test
    fun `labels from the file are cleaned and flags are read from them too`() {
        val track = TrackInfo.of(TrackKind.TEXT, "g0:0", "eng", "  English\u0007  (Forced)‮ ", "application/x-subrip")
        assertEquals("English (Forced)", track.label)
        assertTrue(track.isForced)
        assertTrue(TrackInfo.of(TrackKind.AUDIO, "g1:0", "en", "Director's Commentary", "audio/aac").isCommentary)
        assertTrue(TrackInfo.of(TrackKind.TEXT, "g2:0", "en", "English SDH", null).isSdh)
        assertNull(TrackInfo.cleanLabel("   "))
        assertEquals(80, TrackInfo.cleanLabel("x".repeat(500))!!.length)
    }

    @Test
    fun `audio follows the preferred language, then the file's default, and skips commentary`() {
        val tracks = listOf(
            audio("g0:0", "he", default = true),
            audio("g1:0", "en", channels = 2),
            audio("g2:0", "en", channels = 6),
            audio("g3:0", "en", label = "Commentary", channels = 6),
        )
        assertEquals("g2:0", TrackChoice.pickAudio(tracks, LanguagePrefs())?.id)
        assertEquals("g0:0", TrackChoice.pickAudio(tracks, LanguagePrefs(audio = "he"))?.id)
        assertEquals("g0:0", TrackChoice.pickAudio(tracks, LanguagePrefs(audio = null))?.id)
        // A language the file lacks: its default.
        assertEquals("g0:0", TrackChoice.pickAudio(tracks, LanguagePrefs(audio = "ja"))?.id)
        // A remembered choice wins, commentary included.
        assertEquals("g3:0", TrackChoice.pickAudio(tracks, LanguagePrefs(), TrackMemory(audio = tracks[3].key))?.id)
    }

    @Test
    fun `audio this TV cannot play is never chosen, and is noticed when it is all there is`() {
        val tracks = listOf(audio("g0:0", "en", channels = 6, supported = false, mime = "audio/vnd.dts"), audio("g1:0", "en", channels = 2, mime = "audio/aac"))
        assertEquals("g1:0", TrackChoice.pickAudio(tracks, LanguagePrefs())?.id)
        assertFalse(TrackChoice.noPlayableAudio(tracks))
        val onlyDts = listOf(tracks[0])
        assertNull(TrackChoice.pickAudio(onlyDts, LanguagePrefs()))
        assertTrue(TrackChoice.noPlayableAudio(onlyDts))
        assertFalse(TrackChoice.noPlayableAudio(emptyList()))
        // The window lists playable tracks first.
        assertEquals(listOf("g1:0", "g0:0"), TrackChoice.ordered(tracks, TrackKind.AUDIO).map { it.id })
    }

    @Test
    fun `subtitles follow the preferred language, plain before forced and SDH, else forced ones for the audio`() {
        val tracks = listOf(
            text("g4:0", "en", forced = true),
            text("g5:0", "en", sdh = true),
            text("g6:0", "en"),
            text("g7:0", "he", default = true),
            text("x0", "he", external = true),
        )
        assertEquals("g6:0", TrackChoice.pickSubtitle(tracks, LanguagePrefs(), audioLanguage = "en")?.id)
        // The file's own Hebrew track before a subtitle file.
        assertEquals("g7:0", TrackChoice.pickSubtitle(tracks, LanguagePrefs(subtitles = "he"), "en")?.id)
        // Off: only the forced subtitles of the language heard.
        assertEquals("g4:0", TrackChoice.pickSubtitle(tracks, LanguagePrefs(subtitles = null), "en")?.id)
        assertNull(TrackChoice.pickSubtitle(tracks, LanguagePrefs(subtitles = null), "he"))
        // A language with no subtitles: forced ones for the audio, if any.
        assertEquals("g4:0", TrackChoice.pickSubtitle(tracks, LanguagePrefs(subtitles = "ja"), "en")?.id)
        // Remembered: a chosen track, or "off" for this file.
        assertEquals("x0", TrackChoice.pickSubtitle(tracks, LanguagePrefs(), "en", TrackMemory(subtitle = tracks[4].key))?.id)
        assertEquals("g4:0", TrackChoice.pickSubtitle(tracks, LanguagePrefs(), "en", TrackMemory(subtitlesOff = true))?.id)
    }

    @Test
    fun `a remembered choice survives storage, and malformed storage is no memory`() {
        val memory = TrackMemory(audio = "AUDIO|en|Commentary|", subtitle = "TEXT|he||x", subtitlesOff = false)
        assertEquals(memory, TrackChoice.decode(TrackChoice.encode(memory)))
        assertEquals(TrackMemory(subtitlesOff = true), TrackChoice.decode(TrackChoice.encode(TrackMemory(subtitlesOff = true))))
        assertNull(TrackChoice.decode(TrackChoice.encode(TrackMemory())))
        assertNull(TrackChoice.decode("{broken"))
        assertNull(TrackChoice.decode("""{"a":5,"s":["x"]}"""))
        assertNull(TrackChoice.decode("x".repeat(5_000)))
        // Every file's choices: invalid file keys and broken values are dropped, the newest kept.
        val all = linkedMapOf("t:ABC:0" to memory, "d:XYZ" to TrackMemory(subtitlesOff = true))
        assertEquals(all, TrackChoice.decodeAll(TrackChoice.encodeAll(all)))
        assertEquals(mapOf("t:ABC:0" to memory), TrackChoice.decodeAll("""{"t:ABC:0":${kotlinx.serialization.json.JsonPrimitive(TrackChoice.encode(memory))},"../x":"{}","d:Q":"{broken"}"""))
        val many = (1..TrackChoice.MAX_FILES + 5).associate { "d:F$it" to memory }
        assertEquals("d:F6", TrackChoice.decodeAll(TrackChoice.encodeAll(many)).keys.first())
        assertTrue(TrackChoice.decodeAll("[1,2]").isEmpty())
    }

    @Test
    fun `a track is known again by what it is, a subtitle file by its place in the torrent`() {
        val stereo = audio("g1:0", "en", channels = 2, mime = "audio/aac")
        val surround = audio("g2:0", "en", channels = 6, mime = "audio/eac3")
        assertFalse(stereo.key == surround.key)
        // The same track after the file reloads, under another id.
        assertEquals(surround.key, audio("g5:0", "eng", channels = 6, mime = "audio/eac3").key)
        assertEquals("TEXT|x|x2", text("x2", "he", external = true).key)
    }

    @Test
    fun `subtitle files go with the video they are named after, or their episode`() {
        val names = listOf("Show.S01E01.mkv", "Show.S01E02.mkv", "Show.S01E01.heb.srt", "Show.S01E02.eng.srt", "Show.1x02.he.srt", "notes.txt", "Other.Film.srt")
        assertEquals(listOf(2), SubtitleFiles.forVideo("Show.S01E01.mkv", names, videosInTorrent = 2))
        assertEquals(listOf(3, 4), SubtitleFiles.forVideo("Show.S01E02.mkv", names, videosInTorrent = 2))
        // A film alone in its torrent takes every subtitle file in it; those named after it first.
        val film = listOf("The.Thing.1982.mkv", "Subs/English.srt", "The.Thing.1982.heb.srt", "cover.jpg")
        assertEquals(listOf(2, 1), SubtitleFiles.forVideo("The.Thing.1982.mkv", film, videosInTorrent = 1))
        assertEquals("1x2", SubtitleFiles.episodeOf("show.s01.e02.srt".replace(".e", "e")))
        assertNull(SubtitleFiles.episodeOf("The.Thing.1982.srt"))
        assertTrue(SubtitleFiles.isSubtitle("a.ASS"))
        assertEquals("text/vtt", SubtitleFiles.mimeType("a.vtt"))
    }

    private val srt = "1\n00:00:01,000 --> 00:00:03,000\n%s\n"

    @Test
    fun `hebrew subtitles in windows-1255 are read as hebrew, not gibberish`() {
        val line = srt.format("שלום, מה שלומך?")
        val bytes = line.toByteArray(Charset.forName("windows-1255"))
        assertEquals(line, SubtitleFiles.decode(bytes, "application/x-subrip", languageHint = "he"))
        // Without a hint, from the letters.
        assertEquals(line, SubtitleFiles.decode(bytes, "application/x-subrip"))
        // UTF-8, with or without a byte-order mark.
        assertEquals(line, SubtitleFiles.decode(line.toByteArray(Charsets.UTF_8), "application/x-subrip"))
        assertEquals(line, SubtitleFiles.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + line.toByteArray(), "application/x-subrip"))
        assertEquals(line, SubtitleFiles.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + line.toByteArray(Charsets.UTF_16LE), "application/x-subrip"))
    }

    @Test
    fun `russian and arabic code pages are told apart, and non-subtitles are refused`() {
        val russian = srt.format("Привет, Как дела? Что случилось")
        assertEquals(russian, SubtitleFiles.decode(russian.toByteArray(Charset.forName("windows-1251")), "application/x-subrip"))
        val arabic = srt.format("مرحبا بك")
        assertEquals(arabic, SubtitleFiles.decode(arabic.toByteArray(Charset.forName("windows-1256")), "application/x-subrip", languageHint = "ar"))
        val windowsLineEnds = srt.format("Hi").replace("\n", "\r\n")
        assertEquals(srt.format("Hi"), SubtitleFiles.decode(windowsLineEnds.toByteArray(), "application/x-subrip"))
        assertNull(SubtitleFiles.decode("<html>not subtitles</html>".toByteArray(), "application/x-subrip"))
        assertNull(SubtitleFiles.decode(ByteArray(0), "application/x-subrip"))
        assertNull(SubtitleFiles.decode(ByteArray(SubtitleFiles.MAX_BYTES + 1) { 'a'.code.toByte() }, "application/x-subrip"))
        assertTrue(SubtitleFiles.decode("[Script Info]\nTitle: x\n".toByteArray(), "text/x-ssa") != null)
    }

    @Test
    fun `a torrent's subtitle files come with its videos`() {
        val torrent = RealDebrid.parseTorrents(
            """[{"id":"TOR1","filename":"The.Thing","bytes":100,"status":"downloaded","links":["https://real-debrid.com/d/L1","https://real-debrid.com/d/L2","https://real-debrid.com/d/L3"]}]""",
        ).single()
        val files = RealDebrid.torrentFiles(
            torrent,
            """{"files":[{"id":1,"path":"/T/The.Thing.1982.mkv","bytes":5000000,"selected":1},
               {"id":2,"path":"/T/The.Thing.1982.heb.srt","bytes":60000,"selected":1},
               {"id":3,"path":"/T/huge.srt","bytes":${SubtitleFiles.MAX_BYTES + 1},"selected":1}]}""",
        )
        val film = files.single()
        assertEquals(listOf("The.Thing.1982.heb.srt"), film.subtitles.map { it.name })
        assertEquals("https://real-debrid.com/d/L2", film.subtitles.single().link)
    }
}
