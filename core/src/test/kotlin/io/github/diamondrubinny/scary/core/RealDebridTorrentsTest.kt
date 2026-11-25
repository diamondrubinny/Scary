// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.realdebrid.Magnets
import io.github.diamondrubinny.scary.core.realdebrid.RealDebrid
import io.github.diamondrubinny.scary.core.realdebrid.RealDebrid.TorrentFile
import io.github.diamondrubinny.scary.core.realdebrid.RealDebrid.TorrentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The torrent requests of the sources feature, as documented (api.real-debrid.com, 20 Nov 2026), and hostile input. */
class RealDebridTorrentsTest {
    private val hash = "0123456789abcdef0123456789abcdef01234567"

    @Test
    fun `a magnet is rebuilt from its hash, name and plain trackers only`() {
        val magnet = Magnets.parse(
            "magnet:?xt=urn:btih:${hash.uppercase()}&dn=Night+of+the+Living+Dead+1968&tr=udp%3A%2F%2Ftracker.example%3A1337%2Fannounce" +
                "&tr=javascript%3Aalert(1)&xs=https%3A%2F%2Fevil.example%2Fx.torrent&as=https%3A%2F%2Fevil.example&x.pe=1.2.3.4%3A80",
        )!!
        assertEquals(hash, magnet.infoHash)
        assertEquals("Night of the Living Dead 1968", magnet.name)
        assertEquals(listOf("udp://tracker.example:1337/announce"), magnet.trackers)
        assertEquals("magnet:?xt=urn:btih:$hash&dn=Night+of+the+Living+Dead+1968&tr=udp%3A%2F%2Ftracker.example%3A1337%2Fannounce", magnet.uri)
        assertEquals("magnet=" + java.net.URLEncoder.encode(magnet.uri, "UTF-8"), RealDebrid.addMagnetForm(magnet))
    }

    @Test
    fun `a bare hash and a base32 hash are accepted`() {
        assertEquals(Magnets.Magnet(hash), Magnets.parse("  $hash \n".trim()))
        // RFC 4648's test vector, padded to twenty bytes: "foobar" + 14 zero bytes.
        assertEquals("666f6f6261720000000000000000000000000000", Magnets.parse("magnet:?xt=urn:btih:MZXW6YTBOIAAAAAAAAAAAAAAAAAAAAAA")!!.infoHash)
    }

    @Test
    fun `anything else is refused`() {
        assertNull(Magnets.parse(""))
        assertNull(Magnets.parse("https://example.com/file.torrent"))
        assertNull(Magnets.parse("magnet:?dn=no+hash"))
        assertNull(Magnets.parse("magnet:?xt=urn:btih:xyz"))
        assertNull(Magnets.parse("magnet:?xt=urn:btmh:1220" + "a".repeat(64)))
        assertNull(Magnets.parse("magnet:?xt=urn:btih:$hash&dn=two words"))
        assertNull(Magnets.parse("magnet:?xt=urn:btih:$hash&dn=" + "a".repeat(Magnets.MAX_LENGTH)))
        // A broken escape drops that field, not the link.
        assertEquals(Magnets.Magnet(hash), Magnets.parse("magnet:?xt=urn:btih:$hash&dn=%ZZ"))
    }

    @Test
    fun `requests follow the documentation`() {
        assertEquals("https://api.real-debrid.com/rest/1.0/torrents/addMagnet", RealDebrid.ADD_MAGNET_URL)
        assertEquals("https://api.real-debrid.com/rest/1.0/torrents/selectFiles/ABC123", RealDebrid.selectFilesUrl("ABC123"))
        assertEquals("files=1%2C3", RealDebrid.selectFilesForm(listOf(1, 3)))
        assertEquals("https://api.real-debrid.com/rest/1.0/torrents/delete/ABC123", RealDebrid.deleteTorrentUrl("ABC123"))
        assertEquals("https://api.real-debrid.com/rest/1.0/torrents/activeCount", RealDebrid.ACTIVE_COUNT_URL)
        assertTrue(runCatching { RealDebrid.deleteTorrentUrl("../user") }.isFailure)
        assertTrue(runCatching { RealDebrid.selectFilesForm(emptyList()) }.isFailure)
        assertTrue(runCatching { RealDebrid.selectFilesForm(listOf(-1)) }.isFailure)
    }

    @Test
    fun `deleting a file removes its download, or the torrent it is in`() {
        assertEquals("https://api.real-debrid.com/rest/1.0/downloads/delete/ABC123", RealDebrid.deletion("d:ABC123")!!.url)
        assertEquals(RealDebrid.Deletion.Torrent("XYZ9"), RealDebrid.deletion("t:XYZ9:2"))
        assertEquals("https://api.real-debrid.com/rest/1.0/torrents/delete/XYZ9", RealDebrid.deletion("t:XYZ9:0")!!.url)
        for (key in listOf("", "d:", "d:../user", "t:XYZ9", "t:XYZ9:x", "x:ABC", "d:ABC:1", "t:a/b:0")) assertNull(key, RealDebrid.deletion(key))
    }

    @Test
    fun `deleting every file removes each download and each torrent once`() {
        assertEquals(
            listOf(RealDebrid.Deletion.Torrent("XYZ9"), RealDebrid.Deletion.Download("ABC123"), RealDebrid.Deletion.Torrent("Q7")),
            RealDebrid.deletions(listOf("t:XYZ9:0", "d:ABC123", "t:XYZ9:1", "t:Q7:0", "d:../user", "d:ABC123", "")),
        )
        assertTrue(RealDebrid.deletions(emptyList()).isEmpty())
    }

    @Test
    fun `answers are read strictly`() {
        assertEquals("ABC123", RealDebrid.parseAdded("""{"id":"ABC123","uri":"https://api.real-debrid.com/rest/1.0/torrents/info/ABC123"}"""))
        assertNull(RealDebrid.parseAdded("""{"id":"../x"}"""))
        assertNull(RealDebrid.parseAdded("""{"error":"infringing_file","error_code":35}"""))
        assertEquals(RealDebrid.ActiveCount(2, 25), RealDebrid.parseActiveCount("""{"nb":2,"limit":25}"""))
        assertNull(RealDebrid.parseActiveCount("""{"active":2,"limit":25}"""))
        assertNull(RealDebrid.parseActiveCount("""{"nb":-1,"limit":25}"""))
    }

    @Test
    fun `a torrent's info gives its state, files and links`() {
        val waiting = RealDebrid.parseTorrentStatus(
            """{"id":"ABC123","filename":"Film.1968.1080p","hash":"$hash","bytes":0,"progress":0,"status":"waiting_files_selection",
               "files":[{"id":1,"path":"/Film.1968.1080p/Film.mkv","bytes":4000000000,"selected":0},
                        {"id":2,"path":"/Film.1968.1080p/Sample/sample.mkv","bytes":30000000,"selected":0},
                        {"id":3,"path":"/Film.1968.1080p/Film.en.srt","bytes":90000,"selected":0},
                        {"id":4,"path":"/Film.1968.1080p/readme.exe","bytes":5000,"selected":0},
                        {"id":"x","path":"/bad","bytes":1}],"links":[]}""",
        )!!
        assertEquals(TorrentState.CHOOSE_FILES, waiting.state)
        assertEquals(listOf("Film.mkv", "sample.mkv", "Film.en.srt", "readme.exe"), waiting.files.map { it.name })
        // The film and its subtitles; not the sample, never the program.
        assertEquals(listOf(1, 3), RealDebrid.filesToSelect(waiting.files))
        assertNull(waiting.finished())

        val done = RealDebrid.parseTorrentStatus(
            """{"id":"ABC123","filename":"Film.1968.1080p","bytes":4000090000,"progress":100,"status":"downloaded",
               "links":["https://real-debrid.com/d/AAA","http://plain.example/x","https://real-debrid.com/d/BBB"]}""",
        )!!
        assertEquals(TorrentState.READY, done.state)
        assertEquals(listOf("https://real-debrid.com/d/AAA", "https://real-debrid.com/d/BBB"), done.finished()!!.links)
    }

    @Test
    fun `every documented status has a state and an unknown one fails`() {
        fun state(status: String) = RealDebrid.parseTorrentStatus("""{"id":"A1","filename":"f","status":"$status","progress":250}""")!!
        assertEquals(TorrentState.CONVERTING, state("magnet_conversion").state)
        assertEquals(TorrentState.QUEUED, state("queued").state)
        assertEquals(TorrentState.DOWNLOADING, state("downloading").state)
        assertEquals(TorrentState.FINISHING, state("compressing").state)
        assertEquals(TorrentState.FINISHING, state("uploading").state)
        for (status in listOf("magnet_error", "error", "virus", "dead", "something_new")) assertEquals(TorrentState.FAILED, state(status).state)
        assertEquals(100, state("downloading").progress)
        assertNull(RealDebrid.parseTorrentStatus("""{"id":"A1","status":"downloaded"}"""))
        assertNull(RealDebrid.parseTorrentStatus("[]"))
    }

    @Test
    fun `the list keeps torrents in any state`() {
        val list = RealDebrid.parseTorrentStatuses(
            """[{"id":"A1","filename":"One","status":"downloading","progress":42,"bytes":10},
                {"id":"bad id","filename":"Two","status":"downloaded"},
                {"id":"A3","filename":"Three","status":"downloaded","progress":100,"links":["https://real-debrid.com/d/CCC"]}]""",
        )
        assertEquals(listOf("A1" to 42, "A3" to 100), list.map { it.id to it.progress })
        assertTrue(RealDebrid.parseTorrentStatuses("{}").isEmpty())
    }

    @Test
    fun `no video means nothing is chosen, and several films are all kept`() {
        assertTrue(RealDebrid.filesToSelect(listOf(TorrentFile(1, "setup.exe", 5_000_000, false), TorrentFile(2, "notes.srt", 1000, false))).isEmpty())
        val series = listOf(TorrentFile(1, "S01E01.mkv", 900_000_000, false), TorrentFile(2, "S01E02.mkv", 1_100_000_000, false))
        assertEquals(listOf(1, 2), RealDebrid.filesToSelect(series))
        val files = listOf(RealDebrid.RdFile("t:A:0", "a.mkv", 5, "https://real-debrid.com/d/A"), RealDebrid.RdFile("t:A:1", "b.mkv", 9, "https://real-debrid.com/d/B"))
        assertEquals("t:A:1", RealDebrid.mainFile(files)!!.key)
    }

    @Test
    fun `the account's own words for a refusal are shown as it gave them, and nothing else is`() {
        assertEquals("infringing_file (35)", RealDebrid.errorLabel("""{"error":"infringing_file","error_code":35}"""))
        assertEquals("too_many_active_downloads (21)", RealDebrid.errorLabel("""{"error":"too_many_active_downloads","error_code":21}"""))
        assertEquals("ip_not_allowed", RealDebrid.errorLabel("""{"error":"ip_not_allowed"}"""))
        // Text that is not a plain name is never put on the screen.
        assertNull(RealDebrid.errorLabel("""{"error":"<b>Visit evil.example</b>","error_code":35}"""))
        assertNull(RealDebrid.errorLabel("""{"error_code":35}"""))
        assertNull(RealDebrid.errorLabel("<html>"))
    }
}
