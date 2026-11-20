// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.realdebrid.AltTitles
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

    private fun series(title: String, year: Int, endYear: Int? = null, ongoing: Boolean = false, he: String = "") =
        movie(title, year, he).copy(kind = io.github.diamondrubinny.scary.core.model.MediaKind.SERIES, endYear = endYear, ongoing = ongoing)

    @Test
    fun `episode files are matched to their series, by season and episode`() {
        val dark = series("Dark", 2017, endYear = 2020)
        val files = listOf(
            RealDebrid.RdFile("d:1", "Dark.S01E02.1080p.WEB.x264.mkv", 3_000, "https://real-debrid.com/d/1"),
            RealDebrid.RdFile("d:2", "Dark.S01E01.720p.WEB.mkv", 1_000, "https://real-debrid.com/d/2"),
            RealDebrid.RdFile("d:3", "Dark S02E01 2160p.mkv", 5_000, "https://real-debrid.com/d/3"),
            RealDebrid.RdFile("d:4", "Dark.S01E01.2160p.WEB.mkv", 4_000, "https://real-debrid.com/d/4"),
            RealDebrid.RdFile("d:5", "Dark.Waters.2019.mkv", 9_000, "https://real-debrid.com/d/5"),
            RealDebrid.RdFile("d:6", "Dark.Matter.S01E01.mkv", 2_000, "https://real-debrid.com/d/6"),
            RealDebrid.RdFile("d:7", "Dark.1x03.mkv", 2_000, "https://real-debrid.com/d/7"),
        )
        val episodes = FileMatch.forSeries(dark, files)
        // By season and episode; an episode with two files has the bigger one first.
        assertEquals(listOf("d:4", "d:2", "d:1", "d:7", "d:3"), episodes.map { it.file.key })
        assertEquals(listOf(1 to 1, 1 to 1, 1 to 2, 1 to 3, 2 to 1), episodes.map { it.season to it.episode })
        // A film is not an episode, and a series' files are never a film's.
        assertFalse(FileMatch.matches(dark, FileMatch.parse("Dark.Waters.2019.mkv")))
        assertFalse(FileMatch.matches(movie("Dark", 2017), FileMatch.parse("Dark.S01E01.mkv")))
        // A year in the name is the show's: another show of the same name from another era is not it.
        assertTrue(FileMatch.matches(dark, FileMatch.parse("Dark.2017.S01E01.mkv")))
        assertFalse(FileMatch.matches(dark, FileMatch.parse("Dark.1999.S01E01.mkv")))
        // A show still running, and one with a year inside its run.
        assertTrue(FileMatch.matches(series("The Bear", 2022, ongoing = true), FileMatch.parse("The.Bear.2022.S03E05.mkv")))
        assertTrue(FileMatch.matches(series("Dark", 2017, endYear = 2020), FileMatch.parse("Dark.2019.S03E01.mkv")))
        // A Hebrew title.
        assertTrue(FileMatch.matches(series("Dark", 2017, he = "אפל"), FileMatch.parse("אפל.S01E01.mkv")))
        // Two letters are not a title.
        assertFalse(FileMatch.matches(series("Us", 2020), FileMatch.parse("Us.S01E01.mkv")))
    }

    @Test
    fun `spelling variants of a title are the same title`() {
        assertTrue(FileMatch.matches(movie("Alien 3", 1992), FileMatch.parse("Alien³.1992.mkv")))
        assertTrue(FileMatch.matches(movie("Alien³", 1992), FileMatch.parse("Alien.3.1992.1080p.mkv")))
        assertTrue(FileMatch.matches(movie("Scream VI", 2023), FileMatch.parse("Scream.6.2023.mkv")))
        assertTrue(FileMatch.matches(movie("Friday the 13th Part 2", 1981), FileMatch.parse("Friday.the.13th.Part.II.1981.mkv")))
        assertTrue(FileMatch.matches(movie("Friday the 13th: Part 2", 1981), FileMatch.parse("Friday.the.13th.2.1981.mkv")))
        assertTrue(FileMatch.matches(movie("The Conjuring", 2013), FileMatch.parse("Conjuring.2013.mkv")))
        assertTrue(FileMatch.matches(movie("Conjuring", 2013), FileMatch.parse("The.Conjuring.2013.mkv")))
        assertTrue(FileMatch.matches(movie("Tom & Jerry", 2021), FileMatch.parse("Tom.and.Jerry.2021.mkv")))
        // Different films stay different.
        assertFalse(FileMatch.matches(movie("Alien 3", 1992), FileMatch.parse("Alien.2.1992.mkv")))
        assertFalse(FileMatch.matches(movie("Scream", 1996), FileMatch.parse("Scream.2.1996.mkv")))
        assertFalse(FileMatch.matches(movie("The Conjuring", 2013), FileMatch.parse("The.Conjuring.2.2016.mkv")))
    }

    @Test
    fun `an index finds the film of a file without trying every film`() {
        val films = listOf(
            movie("The Shining", 1980), movie("The Shining", 1997).copy(id = "shining-1997"), movie("Alien 3", 1992),
            movie("Rosemary's Baby", 1968), series("Dark", 2017, endYear = 2020),
        )
        val index = FileMatch.Index(films)
        assertEquals(listOf("the shining"), index.find("The.Shining.1980.1080p.mkv").map { it.id })
        assertEquals(listOf("shining-1997"), index.find("The.Shining.1997.mkv").map { it.id })
        assertEquals(listOf("alien 3"), index.find("Alien³.1992.mkv").map { it.id })
        assertEquals(listOf("dark"), index.find("Dark.S02E03.2160p.mkv").map { it.id })
        assertTrue(index.find("Unknown.Film.2001.mkv").isEmpty())
        // Without a year, both films with that title fit: the nearest year first does not apply, so both are offered.
        assertEquals(setOf("the shining", "shining-1997"), index.find("The.Shining.1080p.BluRay.mkv").map { it.id }.toSet())
        // A loose list to correct a guess from: titles that hold the file's title or are held by it.
        assertEquals(setOf("the shining", "shining-1997"), index.similar("Shining.Redrum.2020.mkv").map { it.id }.toSet())
        assertTrue(index.similar("Something.Else.mkv").isEmpty())
        assertEquals(setOf("the shining", "shining-1997"), index.similar("Shining.mkv").map { it.id }.toSet())
        assertTrue(index.similar("ab.mkv").isEmpty())
    }

    @Test
    fun `the viewer's corrections are kept, bounded and read back safely`() {
        var map = io.github.diamondrubinny.scary.core.realdebrid.RdMatches.with(emptyMap(), "d:ABC", "cm_tt0081505")
        map = io.github.diamondrubinny.scary.core.realdebrid.RdMatches.with(map, "t:XYZ:1", "")
        assertEquals("cm_tt0081505", io.github.diamondrubinny.scary.core.realdebrid.RdMatches.film(map, "d:ABC"))
        assertEquals("", io.github.diamondrubinny.scary.core.realdebrid.RdMatches.film(map, "t:XYZ:1"))
        assertNull(io.github.diamondrubinny.scary.core.realdebrid.RdMatches.film(map, "d:other"))
        assertEquals(map, io.github.diamondrubinny.scary.core.realdebrid.RdMatches.decode(io.github.diamondrubinny.scary.core.realdebrid.RdMatches.encode(map)))
        assertNull(io.github.diamondrubinny.scary.core.realdebrid.RdMatches.film(io.github.diamondrubinny.scary.core.realdebrid.RdMatches.with(map, "d:ABC", null), "d:ABC"))
        // Hostile or damaged storage.
        assertTrue(io.github.diamondrubinny.scary.core.realdebrid.RdMatches.decode("{broken").isEmpty())
        assertTrue(io.github.diamondrubinny.scary.core.realdebrid.RdMatches.decode("""{"d:A":5,"bad key!":"x","d:B":"ok"}""").keys == setOf("d:B"))
        var many = emptyMap<String, String>()
        repeat(400) { many = io.github.diamondrubinny.scary.core.realdebrid.RdMatches.with(many, "d:K$it", "id$it") }
        assertEquals(300, many.size)
        assertFalse("d:K0" in many)
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            return
        }
        throw AssertionError("expected IllegalArgumentException")
    }

    @Test
    fun `a file named by another title of a film is that film, and a film's own title wins`() {
        val eyes = movie("Eyes Without a Face", 1960).copy(imdbId = "tt0053459")
        val alt = AltTitles.NONE.with("tt0053459", listOf("Les yeux sans visage", "The Horror Chamber of Dr. Faustus", "It"))
        val french = FileMatch.parse("Les.Yeux.Sans.Visage.1960.1080p.BluRay.mkv")
        assertFalse("not without the other titles", FileMatch.matches(eyes, french))
        assertTrue(FileMatch.matches(eyes, french, alt))
        assertTrue(FileMatch.matches(eyes, FileMatch.parse("The Horror Chamber of Dr Faustus (1960).mp4"), alt))
        // The same rules as for a film's own titles: the year, and no short title without a year.
        assertFalse("wrong year", FileMatch.matches(eyes, FileMatch.parse("Les.Yeux.Sans.Visage.1988.mkv"), alt))
        assertFalse("an episode", FileMatch.matches(eyes, FileMatch.parse("Les.Yeux.Sans.Visage.S01E02.mkv"), alt))
        // Another title of two letters tells nothing apart: left out even with the right year.
        assertFalse(FileMatch.matches(eyes, FileMatch.parse("It.1960.mkv"), alt))
        val files = listOf(
            RealDebrid.RdFile("d:1", "Les.Yeux.Sans.Visage.1960.mkv", 2_000, "https://real-debrid.com/d/1"),
            RealDebrid.RdFile("d:2", "Eyes.Without.a.Face.1960.2160p.mkv", 9_000, "https://real-debrid.com/d/2"),
            RealDebrid.RdFile("d:3", "Halloween.1978.mkv", 5_000, "https://real-debrid.com/d/3"),
        )
        assertEquals(listOf("d:2"), FileMatch.forMovie(eyes, files).map { it.key })
        assertEquals(listOf("d:2", "d:1"), FileMatch.forMovie(eyes, files, alt).map { it.key })

        // In the index: found by another title; a film whose own title the file carries comes first and wins.
        val night = movie("Night of the Demon", 1957).copy(imdbId = "tt0050766")
        val curse = movie("Curse of the Demon", 1957).copy(imdbId = "tt9999991")
        val both = AltTitles.NONE.with("tt0050766", listOf("Curse of the Demon")).with("tt0053459", listOf("Les yeux sans visage"))
        val index = FileMatch.Index(listOf(eyes, night, curse, movie("Dark", 2017).copy(imdbId = "tt5753856")), both)
        assertEquals(listOf(eyes.id), index.find("Les.Yeux.Sans.Visage.1960.mkv").map { it.id })
        assertEquals(eyes.id, index.one("Les.Yeux.Sans.Visage.1960.mkv")?.id)
        assertEquals(listOf(curse.id, night.id), index.find("Curse.of.the.Demon.1957.mkv").map { it.id })
        assertEquals("its own title wins", curse.id, index.one("Curse.of.the.Demon.1957.mkv")?.id)
        assertEquals(listOf(curse.id), index.ownTitle("Curse.of.the.Demon.1957.mkv").map { it.id })
        assertNull(index.one("Unknown.Film.2001.mkv"))
        // Without the other titles nothing changes.
        assertNull(FileMatch.Index(listOf(eyes, night)).one("Les.Yeux.Sans.Visage.1960.mkv"))
        // Two films that fit only by other titles are none: the viewer decides.
        val twice = both.with("tt9999991", listOf("Les yeux sans visage"))
        assertNull(FileMatch.Index(listOf(eyes, night, curse), twice).one("Les.Yeux.Sans.Visage.mkv"))
    }

    @Test
    fun `other titles are kept within limits and read back as they were written`() {
        var kept = AltTitles.NONE.with("tt0053459", listOf(" Les yeux sans visage ", "les YEUX sans visage", "x", "Ok\u0007ay", "B".repeat(AltTitles.MAX_TITLE + 1)))
        assertEquals(listOf("Les yeux sans visage", "Okay"), kept.of("tt0053459"))
        // A film asked about that has none is remembered as such.
        kept = kept.with("tt0050766", emptyList())
        assertTrue(kept.knows("tt0050766"))
        assertTrue(kept.of("tt0050766").isEmpty())
        assertFalse(kept.knows("tt0000009"))
        assertEquals("an invalid id changes nothing", kept, kept.with("../x", listOf("Title")))
        assertEquals(kept, AltTitles.decode(kept.encode()))
        assertEquals(2, AltTitles.decode(kept.encode()).size)
        // Damaged, hostile or foreign files give nothing.
        assertEquals(AltTitles.NONE, AltTitles.decode("not json"))
        assertEquals(AltTitles.NONE, AltTitles.decode("""{"version":"1","films":{"tt0053459":["A title"]}}"""))
        assertEquals(AltTitles.NONE, AltTitles.decode("""{"version":2,"films":{"tt0053459":["A title"]}}"""))
        assertEquals(AltTitles.NONE, AltTitles.decode("""{"version":1,"films":[]}"""))
        val hostile = AltTitles.decode("""{"version":1,"films":{"../../etc":["A title"],"tt0053459":["Good title",7,null,{"a":1}],"tt0050766":"x"}}""")
        assertEquals(1, hostile.size)
        assertEquals(listOf("Good title"), hostile.of("tt0053459"))
        // The oldest films leave first.
        var many = AltTitles.NONE
        for (i in 1..AltTitles.MAX_FILMS + 5) many = many.with("tt%07d".format(i), listOf("Title $i"))
        assertEquals(AltTitles.MAX_FILMS, many.size)
        assertFalse(many.knows("tt0000005"))
        assertTrue(many.knows("tt0000006"))
        // A film asked about again moves to the newest end.
        many = many.with("tt0000006", listOf("Again"))
        many = many.with("tt9000000", listOf("New"))
        assertTrue(many.knows("tt0000006"))
        assertFalse(many.knows("tt0000007"))
    }
}
