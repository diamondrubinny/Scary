// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.sources.Releases.Origin
import io.github.diamondrubinny.scary.core.sources.Releases.Resolution
import io.github.diamondrubinny.scary.core.sources.StreamAddon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A stream add-on at the viewer's own address, as Stremio's add-on SDK documents it (read 21 Nov 2026), and hostile answers. */
class StreamAddonTest {
    private val a = "a".repeat(40)
    private val b = "b".repeat(40)

    @Test
    fun `the address is a manifest's, over https`() {
        assertEquals("https://addon.example", StreamAddon.base(" https://addon.example/manifest.json "))
        assertEquals("https://addon.example/sort=size|limit=5", StreamAddon.base("https://addon.example/sort=size|limit=5/manifest.json"))
        assertTrue(StreamAddon.isManifest("https://addon.example/manifest.json"))
        assertFalse(StreamAddon.isManifest("https://indexer.example/1/api"))
        assertNull(StreamAddon.base("http://addon.example/x/y/z/manifest.json"))
        assertNull(StreamAddon.base("http://192.168.1.5:7000/manifest.json"))
        assertNull(StreamAddon.base("https://user:pass@addon.example/manifest.json"))
        assertNull(StreamAddon.base("https://addon.example/manifest.json?x=1"))
        assertNull(StreamAddon.base("https://addon.example/two words/manifest.json"))
        assertNull(StreamAddon.base("https://addon.example/stream/movie/tt1.json"))
    }

    @Test
    fun `a film is asked for by its imdb number`() {
        assertEquals("https://addon.example/cfg/stream/movie/tt0063350.json", StreamAddon.movieUrl("https://addon.example/cfg", "tt0063350"))
        assertNull(StreamAddon.movieUrl("https://addon.example", "tt1/../../x"))
        assertNull(StreamAddon.movieUrl("https://addon.example", ""))
    }

    @Test
    fun `only streams that name a torrent are kept`() {
        val list = StreamAddon.parse(
            """{"streams":[
              {"name":"Addon\n1080p","title":"Night.of.the.Living.Dead.1968.1080p.BluRay.x264\n👤 120 💾 8.5 GB ⚙️ Site","infoHash":"${a.uppercase()}","fileIdx":1,
               "sources":["tracker:udp://tracker.example:1337/announce","dht:$a","tracker:javascript:alert(1)"],"behaviorHints":{"bingeGroup":"x"}},
              {"name":"Addon 4K","description":"Some release","infoHash":"$b","behaviorHints":{"filename":"Carnival.of.Souls.1962.2160p.WEB-DL.mkv","videoSize":21000000000}},
              {"name":"Direct","url":"https://files.example/film.mp4"},
              {"name":"Tube","ytId":"abc"},
              {"name":"Bad","infoHash":"nothex"},
              {"infoHash":"${"c".repeat(40)}"},
              "junk"],
             "cacheMaxAge":3600}""",
        )!!
        assertEquals(listOf(a, b), list.map { it.magnet.infoHash })
        val first = list[0]
        assertEquals("Night.of.the.Living.Dead.1968.1080p.BluRay.x264", first.title)
        assertEquals(120, first.seeders)
        assertEquals((8.5 * (1L shl 30)).toLong(), first.sizeBytes)
        assertEquals("Addon", first.source)
        assertEquals(Resolution.FULL_HD, first.tags.resolution)
        assertEquals(Origin.BLURAY, first.tags.origin)
        // The tracker that is an address is kept; the DHT node and the script are not.
        assertEquals(listOf("udp://tracker.example:1337/announce"), first.magnet.trackers)
        val second = list[1]
        assertEquals("Carnival.of.Souls.1962.2160p.WEB-DL.mkv", second.title)
        assertEquals(21_000_000_000, second.sizeBytes)
        assertEquals(-1, second.seeders)
        assertEquals(Resolution.UHD, second.tags.resolution)
    }

    @Test
    fun `what is not the protocol's answer is refused`() {
        assertNull(StreamAddon.parse(""))
        assertNull(StreamAddon.parse("<html>Sign in</html>"))
        assertNull(StreamAddon.parse("[]"))
        assertNull(StreamAddon.parse("""{"streams":"none"}"""))
        assertNull(StreamAddon.parse("""{"err":"not found"}"""))
        assertNull(StreamAddon.parse("x".repeat(StreamAddon.MAX_ANSWER_BYTES + 1)))
        assertTrue(StreamAddon.parse("""{"streams":[]}""")!!.isEmpty())
        // Far too many streams are cut, not trusted.
        val many = (1..400).joinToString(",") { """{"name":"n","title":"R$it","infoHash":"${"%040x".format(it)}"}""" }
        assertEquals(300, StreamAddon.parse("""{"streams":[$many]}""")!!.size)
    }
}
