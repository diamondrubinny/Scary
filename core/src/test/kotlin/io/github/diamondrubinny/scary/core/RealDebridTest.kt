// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.realdebrid.FileMatch
import io.github.diamondrubinny.scary.core.realdebrid.RealDebrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real-Debrid's API as documented (api.real-debrid.com, 9 Nov 2026), and hostile answers. */
class RealDebridTest {
    private fun movie(title: String, year: Int, he: String = "") =
        Movie(id = title.lowercase(), imdbId = "tt0000001", title = title, titleHe = he, year = year, genre = Genre.HORROR)

    @Test
    fun `requests follow the documented device flow`() {
        assertEquals("https://api.real-debrid.com/oauth/v2/device/code?client_id=X245A4XAIBGVM&new_credentials=yes", RealDebrid.deviceCodeUrl())
        assertEquals(
            "https://api.real-debrid.com/oauth/v2/device/credentials?client_id=X245A4XAIBGVM&code=ABCDEFGHIJ0123",
            RealDebrid.credentialsUrl("ABCDEFGHIJ0123"),
        )
        assertEquals(
            "client_id=ID1234&client_secret=SECRET99&code=CODE1234&grant_type=http%3A%2F%2Foauth.net%2Fgrant_type%2Fdevice%2F1.0",
            RealDebrid.tokenForm("ID1234", "SECRET99", "CODE1234"),
        )
        assertEquals("https://api.real-debrid.com/rest/1.0/torrents/info/ABC123", RealDebrid.torrentInfoUrl("ABC123"))
        assertThrows { RealDebrid.torrentInfoUrl("../user") }
        assertThrows { RealDebrid.credentialsUrl("a&b=c") }
        assertThrows { RealDebrid.unrestrictForm("http://plain.example/file") }
    }

    @Test
    fun `sign-in answers are read strictly`() {
        val code = RealDebrid.parseDeviceCode(
            """{"device_code":"ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789","user_code":"ABCDEF0123456","interval":5,"expires_in":1800,"verification_url":"https:\/\/real-debrid.com\/device"}""",
        )!!
        assertEquals("ABCDEF0123456", code.userCode)
        assertEquals(5, code.intervalSeconds)
        // A verification page anywhere but Real-Debrid is refused: the QR code must never send the viewer elsewhere.
        assertNull(RealDebrid.parseDeviceCode("""{"device_code":"ABCDEFGH1234","user_code":"ABCDEF01","verification_url":"https://evil.example/device"}"""))
        assertNull(RealDebrid.parseCredentials("""{"error":"authorization_pending","error_code":0}"""))
        assertEquals(RealDebrid.Credentials("NEWID123", "NEWSECRET123"), RealDebrid.parseCredentials("""{"client_id":"NEWID123","client_secret":"NEWSECRET123"}"""))
        val token = RealDebrid.parseToken("""{"access_token":"ACCESS1234","expires_in":3600,"token_type":"Bearer","refresh_token":"REFRESH1234"}""")!!
        assertEquals("REFRESH1234", token.refreshToken)
        assertNull(RealDebrid.parseToken("""{"access_token":"ACCESS1234","token_type":"Mac","refresh_token":"REFRESH1234"}"""))
        assertNull(RealDebrid.parseToken("not json"))
    }

    @Test
    fun `downloads keep videos on Real-Debrid hosts only`() {
        val files = RealDebrid.parseDownloads(
            """[
              {"id":"AAA111","filename":"The.Shining.1980.1080p.BluRay.mkv","mimeType":"video/x-matroska","filesize":8000000000,
               "link":"https://real-debrid.com/d/AAA111","download":"https://lax1.download.real-debrid.com/d/AAA111/The.Shining.mkv"},
              {"id":"BBB222","filename":"notes.txt","mimeType":"text/plain","filesize":10,"link":"https://real-debrid.com/d/BBB222","download":"https://lax1.download.real-debrid.com/d/BBB222/notes.txt"},
              {"id":"CCC333","filename":"Evil.mp4","link":"https://real-debrid.com/d/CCC333","download":"https://download.evil.example/x.mp4"},
              {"id":"../x","filename":"Bad.mkv","link":"https://real-debrid.com/d/x"},
              {"id":"DDD444","filename":"Line\nbreak.mkv","link":"https://real-debrid.com/d/DDD444"}
            ]""",
        )
        assertEquals(listOf("d:AAA111", "d:CCC333"), files.map { it.key })
        assertEquals("https://lax1.download.real-debrid.com/d/AAA111/The.Shining.mkv", files[0].direct)
        // A direct link on another host is not used; the file is unrestricted through the API instead.
        assertNull(files[1].direct)
        assertTrue(RealDebrid.parseDownloads("{}").isEmpty())
    }

    @Test
    fun `torrents map their selected files to their links`() {
        val torrent = RealDebrid.parseTorrents(
            """[{"id":"TOR1","filename":"Some.Pack","bytes":100,"status":"downloaded","links":["https://real-debrid.com/d/L1","https://real-debrid.com/d/L2"]},
               {"id":"TOR2","filename":"Still.Going","status":"downloading","links":[]}]""",
        ).single()
        val files = RealDebrid.torrentFiles(
            torrent,
            """{"files":[{"id":1,"path":"/Pack/a.nfo","bytes":1,"selected":0},{"id":2,"path":"/Pack/Halloween.1978.mkv","bytes":50,"selected":1},
               {"id":3,"path":"/Pack/sample.txt","bytes":2,"selected":1}]}""",
        )
        assertEquals(listOf("Halloween.1978.mkv"), files.map { it.name })
        assertEquals("https://real-debrid.com/d/L1", files[0].link)
        // Info that does not line up with the links is not guessed at.
        assertTrue(RealDebrid.torrentFiles(torrent, """{"files":[]}""").isEmpty())
    }

    @Test
    fun `unrestricted and transcoded links must be playable`() {
        assertEquals(
            "https://lax1.download.real-debrid.com/d/X1/f.mkv",
            RealDebrid.parseUnrestricted("""{"id":"X1","filename":"f.mkv","mimeType":"video/x-matroska","download":"https://lax1.download.real-debrid.com/d/X1/f.mkv"}""")?.url,
        )
        assertNull(RealDebrid.parseUnrestricted("""{"id":"X1","download":"http://lax1.download.real-debrid.com/d/X1/f.mkv"}"""))
        assertNull(RealDebrid.parseUnrestricted("""{"id":"X1","download":"https://user:pw@lax1.download.real-debrid.com/f.mkv"}"""))
        assertEquals(
            "https://lax1.download.real-debrid.com/t/X1/1080.m3u8",
            RealDebrid.parseTranscode("""{"apple":{"480":"https://lax1.download.real-debrid.com/t/X1/480.m3u8","1080":"https://lax1.download.real-debrid.com/t/X1/1080.m3u8"},"dash":{}}"""),
        )
        assertFalse(RealDebrid.isPlayableUrl("https://real-debrid.com.evil.example/x"))
        assertTrue(RealDebrid.isPlayableUrl("https://real-debrid.com/x"))
    }

    @Test
    fun `file names are matched to films word for word`() {
        val shining = movie("The Shining", 1980)
        assertTrue(FileMatch.matches(shining, FileMatch.parse("The.Shining.1980.2160p.UHD.BluRay.x265.mkv")))
        assertTrue(FileMatch.matches(shining, FileMatch.parse("The Shining (1980) [1080p].mp4")))
        assertFalse("wrong year", FileMatch.matches(shining, FileMatch.parse("The.Shining.1997.mkv")))
        assertFalse("another film", FileMatch.matches(shining, FileMatch.parse("The.Shining.Girls.2022.mkv")))
        assertFalse("an episode", FileMatch.matches(shining, FileMatch.parse("The.Shining.S01E02.1980.mkv")))
        assertTrue(FileMatch.matches(movie("Rosemary's Baby", 1968), FileMatch.parse("Rosemarys.Baby.1968.mkv")))
        assertTrue(FileMatch.matches(movie("2001: A Space Odyssey", 1968), FileMatch.parse("2001.A.Space.Odyssey.1968.mkv")))
        // Short titles need a year: "It" must not claim any file named it.
        assertFalse(FileMatch.matches(movie("It", 2017), FileMatch.parse("It.mkv")))
        assertTrue(FileMatch.matches(movie("It", 2017), FileMatch.parse("It.2017.1080p.mkv")))
        // A Hebrew title in the file name works too.
        assertTrue(FileMatch.matches(movie("Rabies", 2010, he = "כלבת"), FileMatch.parse("כלבת.2010.mkv")))
        val files = listOf(
            RealDebrid.RdFile("d:1", "The.Shining.1980.720p.mkv", 2_000, "https://real-debrid.com/d/1"),
            RealDebrid.RdFile("d:2", "The.Shining.1980.2160p.mkv", 9_000, "https://real-debrid.com/d/2"),
            RealDebrid.RdFile("d:3", "Halloween.1978.mkv", 5_000, "https://real-debrid.com/d/3"),
        )
        assertEquals(listOf("d:2", "d:1"), FileMatch.forMovie(shining, files).map { it.key })
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            return
        }
        throw AssertionError("expected IllegalArgumentException")
    }
}
