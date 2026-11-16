// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.player.FileNames
import io.github.diamondrubinny.scary.core.player.OnlineSubtitles
import io.github.diamondrubinny.scary.core.player.SubtitleFiles
import io.github.diamondrubinny.scary.core.player.TrackInfo
import io.github.diamondrubinny.scary.core.player.TrackKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineSubtitlesTest {
    /** Entries of the add-on's real answer for The Conjuring (tt1457767), 16.11.2026: 12 of its 98. */
    private val answer = """{"subtitles":[
{"id":"5213214","url":"https://subs5.strem.io/en/download/subencoding-stremio-utf8/src-api/file/1953754683","SubEncoding":"UTF-8","lang":"eng","m":"i","g":"1","subtitleFileName":"The.Conjuring.2013.720p.BluRay.x264-ALLiANCE.srt","movieReleaseName":"720p.BluRay.x264-ALLiANCE","releaseGroup":"ALLiANCE","releaseFormat":"BluRay","fpsMilli":23976,"season":0,"episode":0},
{"id":"5213215","url":"https://subs5.strem.io/en/download/subencoding-stremio-utf8/src-api/file/1953754684","SubEncoding":"UTF-8","lang":"eng","subtitleFileName":"The.Conjuring.2013.720p.BluRay.x264-ALLiANCE.srt","movieReleaseName":"720p.BluRay.x264-ALLiANCE (COLOR Hearing Impaired)","releaseFormat":"BluRay","fpsMilli":23976},
{"id":"5213213","url":"https://subs5.strem.io/en/download/subencoding-stremio-utf8/src-api/file/1953754682","SubEncoding":"UTF-8","lang":"eng","subtitleFileName":"The.Conjuring.2013.720p.BluRay.x264-ALLiANCE.srt","movieReleaseName":"720p.BluRay.x264-ALLiANCE","releaseFormat":"BluRay","fpsMilli":23976},
{"id":"5211413","url":"https://subs5.strem.io/en/download/subencoding-stremio-utf8/src-api/file/1953752892","SubEncoding":"GB18030","lang":"eng","subtitleFileName":"The Conjuring.[2013].DVDRIP.DIVX.[Eng]-DUQA®.srt","movieReleaseName":"The Conjuring.[2013].DVDRIP.DIVX.[Eng]-DUQA®","releaseFormat":"DVD","fpsMilli":25000},
{"id":"5213114","url":"https://subs5.strem.io/en/download/subencoding-stremio-utf8/src-api/file/1953754584","SubEncoding":"UTF-8","lang":"eng","subtitleFileName":"The.Conjuring.2013.720p.srt","movieReleaseName":"The.Conjuring.2013.720p","fpsMilli":23976},
{"id":"5211847","url":"https://subs5.strem.io/en/download/subencoding-stremio-utf8/src-api/file/1953753313","SubEncoding":"ASCII","lang":"eng","subtitleFileName":"The.Conjuring.2013.WEBRip.XviD-AQOS.srt","movieReleaseName":"The.Conjuring.2013.WEBRip.XviD-AQOS","releaseFormat":"WEBRip","fpsMilli":25000},
{"id":"5210077","url":"https://subs5.strem.io/en/download/subencoding-stremio-utf8/src-api/file/1953751556","SubEncoding":"CP1255","lang":"heb","subtitleFileName":"The.Conjuring.2012.DVDRip.X264-ALLiANCE.srt","movieReleaseName":"The.Conjuring.2012.DVDRip.X264-ALLiANCE","releaseFormat":"DVD","fpsMilli":25000},
{"id":"5136265","url":"https://subs5.strem.io/en/download/subencoding-stremio-utf8/src-api/file/1953678132","SubEncoding":"CP1255","lang":"heb","subtitleFileName":"The.Conjuring.2013.R6.Webrip.x264.AC3-ANIMAL.srt","movieReleaseName":"The.Conjuring.2013.R6.Webrip.x264.AC3-ANIMAL","releaseFormat":"WEBRip","fpsMilli":25000},
{"id":"5212763","url":"https://subs5.strem.io/en/download/subencoding-stremio-utf8/src-api/file/1953754216","SubEncoding":"CP1252","lang":"pob","subtitleFileName":"The.Conjuring.2013.srt","movieReleaseName":"The.Conjuring.2013.720p.BluRay.x264-ALLiANCE/.1080p/.BRRip","fpsMilli":23976},
{"id":"5215268","url":"https://subs5.strem.io/en/download/subencoding-stremio-utf8/src-api/file/1953756687","SubEncoding":"CP1252","lang":"pob","subtitleFileName":"The.Conjuring.2013.1080p.BluRay.x264.YIFY.srt","movieReleaseName":"The.Conjuring.2013.1080p.BluRay.x264.YIFY","releaseFormat":"BluRay","fpsMilli":23976},
{"id":"5209974","url":"https://subs5.strem.io/en/download/subencoding-stremio-utf8/src-api/file/1953751451","SubEncoding":"CP1256","lang":"ara","subtitleFileName":"The.Conjuring.2013.srt","movieReleaseName":"The.Conjuring.2013","fpsMilli":23976}
]}"""

    private val entries = OnlineSubtitles.parse(answer)

    @Test
    fun `the real answer is read with its languages mapped to the two-letter codes`() {
        assertEquals(11, entries.size)
        assertEquals(setOf("en", "he", "pt", "ar"), entries.map { it.language }.toSet())
        assertEquals(2, entries.count { it.language == "he" })
        // "pob" is Brazilian Portuguese.
        assertEquals(2, entries.count { it.language == "pt" })
        val first = entries.first()
        assertEquals("5213214", first.id)
        assertEquals(23.976, first.fps!!, 0.0001)
        assertEquals("720p.BluRay.x264-ALLiANCE", first.release)
        assertEquals("BluRay", first.format)
        assertEquals(0, first.order)
    }

    @Test
    fun `hearing impaired subtitles are told from plain ones`() {
        assertTrue(entries.first { it.id == "5213215" }.hearingImpaired)
        assertFalse(entries.first { it.id == "5213214" }.hearingImpaired)
    }

    @Test
    fun `languages are listed with the viewer's own first, then by how many`() {
        val counts = OnlineSubtitles.languages(entries, listOf("he", "en"))
        assertEquals(listOf("he", "en", "pt", "ar"), counts.map { it.language })
        assertEquals(listOf(2, 6, 2, 1), counts.map { it.count })
        // A language the film has none in is not offered; without the viewer's, the most subtitles come first.
        assertEquals(listOf("en", "he", "pt", "ar"), OnlineSubtitles.languages(entries, listOf("fr")).map { it.language })
        assertEquals("en", OnlineSubtitles.languages(entries).first().language)
    }

    @Test
    fun `the subtitle made for the same release is chosen`() {
        // The viewer's 720p BluRay file: the subtitle timed for that release, plain rather than for the hearing impaired.
        assertEquals("5213214", OnlineSubtitles.best(entries, "en", "The.Conjuring.2013.720p.BluRay.x264-ALLiANCE.mkv")?.id)
        // A WEBRip file: the WEBRip subtitle, though it comes later in the service's list.
        assertEquals("5211847", OnlineSubtitles.best(entries, "en", "The.Conjuring.2013.WEBRip.XviD-AQOS.mkv")?.id)
        // WEB-DL and WEBRip are the same family of release.
        assertEquals("5136265", OnlineSubtitles.best(entries, "he", "The.Conjuring.2013.1080p.WEB-DL.x264.AC3.mkv")?.id)
        // A 1080p YIFY file: the Portuguese subtitle made for it.
        assertEquals("5215268", OnlineSubtitles.best(entries, "pt", "The.Conjuring.2013.1080p.BluRay.x264.YIFY.mkv")?.id)
    }

    @Test
    fun `frame rate breaks a tie between equal releases, then plain before hearing impaired, then the service's order`() {
        val tied = OnlineSubtitles.parse(
            """{"subtitles":[
{"id":"1","url":"https://subs5.strem.io/a","lang":"eng","movieReleaseName":"Film.BluRay","fpsMilli":25000},
{"id":"2","url":"https://subs5.strem.io/b","lang":"eng","movieReleaseName":"Film.BluRay","fpsMilli":23976},
{"id":"3","url":"https://subs5.strem.io/c","lang":"eng","movieReleaseName":"Film.BluRay SDH","fpsMilli":23976}]}""",
        )
        assertEquals("2", OnlineSubtitles.best(tied, "en", "Film.BluRay.mkv", videoFps = 23.976)?.id)
        assertEquals("1", OnlineSubtitles.best(tied, "en", "Film.BluRay.mkv", videoFps = 25.0)?.id)
        // The frame rate unknown: the plain ones first, in the service's order.
        assertEquals("1", OnlineSubtitles.best(tied, "en", "Film.BluRay.mkv", videoFps = null)?.id)
        assertNull(OnlineSubtitles.best(tied, "fr", "Film.BluRay.mkv"))
    }

    @Test
    fun `an answer is never trusted`() {
        val hostile = """{"subtitles":[
{"id":"1","url":"http://subs5.strem.io/plain-http","lang":"eng"},
{"id":"2","url":"https://evil.example/subs.srt","lang":"eng"},
{"id":"3","url":"https://strem.io.evil.example/subs.srt","lang":"eng"},
{"id":"4","url":"https://user:pass@subs5.strem.io/x","lang":"eng"},
{"id":"5","url":"https://subs5.strem.io:8443/x","lang":"eng"},
{"id":"6","url":"https://subs5.strem.io/ok x","lang":"eng"},
{"id":"7","url":"file:///data/data/secret","lang":"eng"},
{"id":"8","url":"https://subs5.strem.io/x","lang":"zz9"},
{"id":"9 ; rm","url":"https://subs5.strem.io/x","lang":"eng"},
{"id":"10","url":"https://subs5.strem.io/x"},
"just a string",
{"id":"11","url":"https://subs5.strem.io/fine","lang":"eng","movieReleaseName":"Rel\u0007ease‮ <script>alert(1)</script>","fpsMilli":-5},
{"id":"12","url":"https://SUBS5.STREM.IO/fine","lang":"HEB"}
]}"""
        val kept = OnlineSubtitles.parse(hostile)
        assertEquals(listOf("11", "12"), kept.map { it.id })
        // Control and direction characters are taken out of what is shown; nothing else about a name is trusted.
        assertFalse(kept[0].release!!.any { it.isISOControl() || it == '‮' })
        assertNull(kept[0].fps)
        assertEquals("he", kept[1].language)
        assertEquals(emptyList<OnlineSubtitles.Entry>(), OnlineSubtitles.parse("not json"))
        assertEquals(emptyList<OnlineSubtitles.Entry>(), OnlineSubtitles.parse("""{"subtitles":"x"}"""))
        assertEquals(emptyList<OnlineSubtitles.Entry>(), OnlineSubtitles.parse("""[1,2]"""))
        assertEquals(emptyList<OnlineSubtitles.Entry>(), OnlineSubtitles.parse(""))
    }

    @Test
    fun `an answer is cut at three hundred entries`() {
        val many = (1..500).joinToString(",", """{"subtitles":[""", "]}") { """{"id":"$it","url":"https://subs5.strem.io/$it","lang":"eng"}""" }
        assertEquals(300, OnlineSubtitles.parse(many).size)
    }

    @Test
    fun `the search names only a real IMDb id`() {
        assertEquals("https://opensubtitles-v3.strem.io/subtitles/movie/tt1457767.json", OnlineSubtitles.searchUrl("tt1457767"))
        assertEquals("https://opensubtitles-v3.strem.io/subtitles/series/tt0944947:1:2.json", OnlineSubtitles.searchUrl("tt0944947", 1, 2))
        assertNull(OnlineSubtitles.searchUrl("tt1457767/../x"))
        assertNull(OnlineSubtitles.searchUrl("cm_tt1457767"))
        assertNull(OnlineSubtitles.searchUrl("tt1457767", season = 1))
        assertNull(OnlineSubtitles.searchUrl("tt1457767", 1, 5000))
        assertNull(OnlineSubtitles.searchUrl("", null, null))
    }

    @Test
    fun `file names give a title, a year, a season and an episode`() {
        FileNames.guess("The.Shining.1980.1080p.BluRay.x264-GRP.mkv").let {
            assertEquals("The Shining", it.title)
            assertEquals(1980, it.year)
            assertFalse(it.isEpisode)
        }
        FileNames.guess("2001.A.Space.Odyssey.1968.REMASTERED.720p.mkv").let {
            assertEquals("2001 A Space Odyssey", it.title)
            assertEquals(1968, it.year)
        }
        FileNames.guess("The.Haunting.of.Hill.House.S01E02.720p.WEB.x264.mkv").let {
            assertEquals("The Haunting of Hill House", it.title)
            assertNull(it.year)
            assertEquals(1, it.season)
            assertEquals(2, it.episode)
            assertTrue(it.isEpisode)
        }
        FileNames.guess("Dark (2017) 2x05 1080p.mkv").let {
            assertEquals("Dark", it.title)
            assertEquals(2017, it.year)
            assertEquals(2, it.season)
            assertEquals(5, it.episode)
        }
        FileNames.guess("Rosemarys Baby 1080p BluRay.mp4").let {
            assertEquals("Rosemarys Baby", it.title)
            assertNull(it.year)
        }
        // A screen size is not an episode.
        assertFalse(FileNames.guess("Movie.1920x1080.mkv").isEpisode)
        assertEquals("", FileNames.guess("1080p.mkv").title)
    }

    private fun meta(id: String, name: String, year: Int?, type: String = CinemetaMeta.TYPE_MOVIE) = CinemetaMeta(imdbId = id, name = name, year = year, type = type)

    @Test
    fun `a file is a film only when the title and the year both fit`() {
        val results = listOf(
            meta("tt0081505", "The Shining", 1980),
            meta("tt1457767", "The Conjuring", 2013),
            meta("tt0092457", "The Shining", 1987),
        )
        assertEquals("tt0081505", OnlineSubtitles.pick(results, FileNames.guess("The.Shining.1980.1080p.mkv"))?.imdbId)
        assertEquals("tt0092457", OnlineSubtitles.pick(results, FileNames.guess("The.Shining.1987.mkv"))?.imdbId)
        // A year a few years off is another film (or a wrong guess): nothing is fetched.
        assertNull(OnlineSubtitles.pick(results, FileNames.guess("The.Shining.1995.mkv")))
        // A different title, even a close one.
        assertNull(OnlineSubtitles.pick(results, FileNames.guess("The.Conjuring.2.2016.mkv")))
        // No year: only a long enough title, and the top result.
        assertEquals("tt1457767", OnlineSubtitles.pick(results, FileNames.guess("The.Conjuring.1080p.mkv"))?.imdbId)
        assertNull(OnlineSubtitles.pick(listOf(meta("tt0099999", "It", 1990)), FileNames.guess("It.mkv")))
        // A series needs a series (and an episode marker); a film is never taken for one.
        val shows = listOf(meta("tt5555555", "Dark", 2017, CinemetaMeta.TYPE_SERIES), meta("tt6666666", "Dark", 2017))
        assertEquals("tt5555555", OnlineSubtitles.pick(shows, FileNames.guess("Dark.S01E01.mkv"))?.imdbId)
        assertEquals("tt6666666", OnlineSubtitles.pick(shows, FileNames.guess("Dark.2017.mkv"))?.imdbId)
        assertNull(OnlineSubtitles.pick(results, FileNames.guess("")))
    }

    @Test
    fun `an online subtitle is known again by its language, not its place in the player`() {
        val a = TrackInfo.of(TrackKind.TEXT, "o0", "heb", "The.Conjuring.2013.R6.Webrip", "application/x-subrip", online = true)
        val b = a.copy(id = "o3")
        assertEquals(a.key, b.key)
        assertEquals("he", TrackInfo.onlineLanguageOf(a.key))
        assertNull(TrackInfo.onlineLanguageOf(TrackInfo.of(TrackKind.TEXT, "x2", "he", "n.srt", null, external = true).key))
        assertNull(TrackInfo.onlineLanguageOf(null))
    }

    @Test
    fun `a downloaded subtitle is decoded like a torrent's, whatever its encoding`() {
        val srt = "1\n00:00:01,000 --> 00:00:02,500\nשלום עולם\n\n"
        val utf8 = SubtitleFiles.decode(srt.toByteArray(Charsets.UTF_8), "application/x-subrip", "he")
        assertNotNull(utf8)
        assertTrue(utf8!!.contains("שלום עולם"))
        val cp1255 = SubtitleFiles.decode(srt.toByteArray(charset("windows-1255")), "application/x-subrip", "he")
        assertTrue(cp1255!!.contains("שלום עולם"))
        // An error page is not subtitles.
        assertNull(SubtitleFiles.decode("<html>429 Too Many Requests</html>".toByteArray(), "application/x-subrip", "en"))
    }
}
