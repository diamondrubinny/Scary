// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.sources.Releases
import io.github.diamondrubinny.scary.core.sources.Releases.Codec
import io.github.diamondrubinny.scary.core.sources.Releases.Filter
import io.github.diamondrubinny.scary.core.sources.Releases.Origin
import io.github.diamondrubinny.scary.core.sources.Releases.Resolution
import io.github.diamondrubinny.scary.core.sources.Releases.Sort
import io.github.diamondrubinny.scary.core.sources.Torznab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The viewer's indexer, as the Torznab specification gives it (read 20 Nov 2026), and hostile answers. */
class TorznabTest {
    private val a = "a".repeat(40)
    private val b = "b".repeat(40)
    private val c = "c".repeat(40)

    private fun feed(items: String) =
        """<?xml version="1.0" encoding="UTF-8"?>
           <rss version="2.0" xmlns:torznab="http://torznab.com/schemas/2015/feed"><channel><title>Indexer</title>$items</channel></rss>"""

    @Test
    fun `the address must be https, or plain http at a home address`() {
        assertEquals("https://indexer.example/1/api", Torznab.endpoint(" https://indexer.example/1/api/ "))
        assertEquals("http://192.168.1.5:9696/1/api", Torznab.endpoint("http://192.168.1.5:9696/1/api"))
        assertEquals("http://10.0.0.7/api", Torznab.endpoint("http://10.0.0.7/api/"))
        assertEquals("http://172.20.3.4:9117/api/v2.0/indexers/all/results/torznab/api", Torznab.endpoint("http://172.20.3.4:9117/api/v2.0/indexers/all/results/torznab/api"))
        // Plain HTTP anywhere else is refused: a public address, a name, or numbers that only look private.
        assertNull(Torznab.endpoint("http://indexer.example/1/api"))
        assertNull(Torznab.endpoint("http://8.8.8.8:9696/1/api"))
        assertNull(Torznab.endpoint("http://172.32.0.1/api"))
        assertNull(Torznab.endpoint("http://192.168.1.5.evil.example/api"))
        assertNull(Torznab.endpoint("http://nas.local:9696/1/api"))
        assertNull(Torznab.endpoint("http://user:pass@192.168.1.5/api"))
        assertNull(Torznab.endpoint("http://192.168.1.999/api"))
        assertNull(Torznab.endpoint("https://user:pass@indexer.example/api"))
        assertNull(Torznab.endpoint("https://indexer.example/api?apikey=x"))
        assertNull(Torznab.endpoint("https://"))
        assertNull(Torznab.endpoint("javascript:alert(1)"))
        assertEquals("0123456789abcdef", Torznab.apiKey(" 0123456789abcdef "))
        assertNull(Torznab.apiKey("short"))
        assertNull(Torznab.apiKey("key&t=caps"))
    }

    @Test
    fun `the search follows the specification`() {
        assertEquals(
            "https://indexer.example/api?t=search&cat=2000,5000&extended=1&limit=100&q=night+of+the+living+dead+1968&apikey=0123456789abcdef",
            Torznab.searchUrl("https://indexer.example/api", "0123456789abcdef", "  night of the\n living   dead 1968 "),
        )
        assertEquals("a%26t%3Dcaps", Torznab.searchUrl("https://i.example/api", "0123456789abcdef", "a&t=caps")!!.substringAfter("q=").substringBefore("&"))
        assertNull(Torznab.searchUrl("https://i.example/api", "0123456789abcdef", " "))
        assertEquals(
            "https://indexer.example/api?t=movie&cat=2000&extended=1&limit=100&imdbid=tt0063350&apikey=0123456789abcdef",
            Torznab.movieUrl("https://indexer.example/api", "0123456789abcdef", "tt0063350"),
        )
        assertNull(Torznab.movieUrl("https://indexer.example/api", "0123456789abcdef", "tt1&t=caps"))
        assertNull(Torznab.movieUrl("https://indexer.example/api", "0123456789abcdef", ""))
    }

    @Test
    fun `items with a magnet or a hash are kept, and a torrent file address is not`() {
        val answer = Torznab.parse(
            feed(
                """<item><title>Night.of.the.Living.Dead.1968.1080p.BluRay.x264</title><guid>https://indexer.example/t/1</guid>
                     <link>https://indexer.example/dl/1.torrent</link><size>8000000000</size><jackettindexer id="x">Site One</jackettindexer>
                     <enclosure url="magnet:?xt=urn:btih:$a&amp;dn=Night" length="0" type="application/x-bittorrent;x-scheme-handler/magnet"/>
                     <torznab:attr name="seeders" value="120"/><torznab:attr name="peers" value="130"/></item>
                   <item><title>Carnival.of.Souls.1962.720p.WEB-DL.HEVC</title><link>https://indexer.example/dl/2.torrent</link>
                     <enclosure url="https://indexer.example/dl/2.torrent" length="1500000000" type="application/x-bittorrent"/>
                     <torznab:attr name="infohash" value="${b.uppercase()}"/><torznab:attr name="seeders" value="7"/><torznab:attr name="size" value="1600000000"/></item>
                   <item><title>Only.A.Torrent.File.1080p</title><link>https://indexer.example/dl/3.torrent</link>
                     <enclosure url="https://indexer.example/dl/3.torrent" length="5" type="application/x-bittorrent"/></item>
                   <item><link>magnet:?xt=urn:btih:$c</link></item>""",
            ),
        ) as Torznab.Answer.Results
        assertEquals(listOf(a, b), answer.candidates.map { it.magnet.infoHash })
        val first = answer.candidates[0]
        assertEquals("Site One", first.source)
        assertEquals(8_000_000_000, first.sizeBytes)
        assertEquals(120, first.seeders)
        assertEquals(Releases.Tags(Resolution.FULL_HD, Origin.BLURAY, Codec.AVC), first.tags)
        val second = answer.candidates[1]
        assertEquals(1_600_000_000, second.sizeBytes)
        assertEquals("Carnival.of.Souls.1962.720p.WEB-DL.HEVC", second.magnet.name)
        assertEquals(Releases.Tags(Resolution.HD, Origin.WEB, Codec.HEVC), second.tags)
    }

    @Test
    fun `hostile and broken answers are refused whole`() {
        val bomb = """<?xml version="1.0"?><!DOCTYPE rss [<!ENTITY x SYSTEM "file:///etc/passwd">]><rss><channel><item><title>&x;</title></item></channel></rss>"""
        assertEquals(Torznab.Answer.Unreadable, Torznab.parse(bomb))
        assertEquals(Torznab.Answer.Unreadable, Torznab.parse("<html><body>Sign in</body></html>"))
        assertEquals(Torznab.Answer.Unreadable, Torznab.parse("<rss><channel><item>"))
        assertEquals(Torznab.Answer.Unreadable, Torznab.parse(""))
        assertEquals(Torznab.Answer.Unreadable, Torznab.parse("x".repeat(Torznab.MAX_ANSWER_BYTES + 1)))
        assertEquals(Torznab.Answer.Refused(100), Torznab.parse("""<?xml version="1.0"?><error code="100" description="Invalid API Key"/>"""))
        assertEquals(Torznab.Answer.Refused(0), Torznab.parse("<error/>"))
        assertTrue((Torznab.parse(feed("")) as Torznab.Answer.Results).candidates.isEmpty())
        // A feed with far too many items is cut, not trusted.
        val many = (1..260).joinToString("") { "<item><title>R$it</title><link>magnet:?xt=urn:btih:${"%040x".format(it)}</link></item>" }
        assertEquals(200, (Torznab.parse(feed(many)) as Torznab.Answer.Results).candidates.size)
    }

    @Test
    fun `a release's name gives its picture`() {
        assertEquals(Releases.Tags(Resolution.UHD, Origin.REMUX, Codec.HEVC), Releases.tags("Film.1979.2160p.UHD.BluRay.REMUX.HDR.HEVC-GRP"))
        assertEquals(Releases.Tags(Resolution.UHD, Origin.WEB, Codec.OTHER), Releases.tags("Film 2020 4K WEBRip"))
        assertEquals(Origin.CAM, Releases.tags("Film.2026.1080p.HDCAM.x264").origin)
        assertEquals(Origin.CAM, Releases.tags("Film.2026.TS.XviD").origin)
        // Letters inside a word are not a tag: "Monsters" holds "ts", "Scream" holds "cam".
        assertEquals(Releases.Tags(Resolution.SD, Origin.UNKNOWN, Codec.OTHER), Releases.tags("Monsters.Scream.1996"))
        assertEquals(Resolution.SD, Releases.tags("Film.1968.DVDRip.XviD").resolution)
        assertEquals(Origin.DVD, Releases.tags("Film.1968.DVDRip.XviD").origin)
    }

    private fun candidate(title: String, hash: String, seeders: Int, size: Long = 1) =
        Releases.Candidate(title, io.github.diamondrubinny.scary.core.realdebrid.Magnets.Magnet(hash), "", size, seeders, Releases.tags(title))

    @Test
    fun `the smart order puts a shared good picture first and a cinema recording last`() {
        val bluRay = candidate("Film.1080p.BluRay", a, 80, 8)
        val uhdDead = candidate("Film.2160p.WEB-DL", b, 0, 20)
        val hdBusy = candidate("Film.720p.WEB-DL", c, 5000, 2)
        val camBusy = candidate("Film.1080p.HDCAM", "d".repeat(40), 9000, 3)
        val all = listOf(camBusy, hdBusy, uhdDead, bluRay)
        assertEquals(listOf(bluRay, hdBusy, uhdDead, camBusy), Releases.arrange(all, Filter.ALL, Sort.SMART))
        assertEquals(bluRay, Releases.best(all))
        assertEquals(listOf(camBusy, hdBusy, bluRay, uhdDead), Releases.arrange(all, Filter.ALL, Sort.SEEDERS))
        assertEquals(listOf(uhdDead, bluRay, camBusy, hdBusy), Releases.arrange(all, Filter.ALL, Sort.SIZE))
        assertEquals(listOf(bluRay, camBusy), Releases.arrange(all, Filter.FULL_HD, Sort.SMART))
        assertNull(Releases.best(listOf(camBusy)))
    }

    @Test
    fun `the same release from two sites is listed once`() {
        val one = candidate("Film.1080p.BluRay", a, 10)
        val two = candidate("Film.1080p.BluRay", a, 90)
        assertEquals(listOf(two), Releases.arrange(listOf(one, two), Filter.ALL, Sort.SMART))
    }

    @Test
    fun `the check asks the indexer what it can do, and reads only that`() {
        assertEquals("https://indexer.example/api?t=caps&apikey=k%2B1", Torznab.capsUrl("https://indexer.example/api", "k+1"))
        assertEquals(Torznab.Answer.Results(emptyList()), Torznab.capsAnswer("""<?xml version="1.0"?><caps><server title="x"/><searching><search available="yes"/></searching></caps>"""))
        assertEquals(Torznab.Answer.Refused(100), Torznab.capsAnswer("""<error code="100" description="Invalid API Key"/>"""))
        assertEquals(Torznab.Answer.Unreadable, Torznab.capsAnswer("<html><body>Login</body></html>"))
        assertEquals(Torznab.Answer.Unreadable, Torznab.capsAnswer("not xml"))
        assertEquals(Torznab.Answer.Unreadable, Torznab.capsAnswer("""<!DOCTYPE caps [<!ENTITY x "y">]><caps/>"""))
    }
}
