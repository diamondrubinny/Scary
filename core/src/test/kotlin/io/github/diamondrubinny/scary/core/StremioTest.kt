// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.stremio.Stremio
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StremioTest {
    // Shaped like Stremio's own answers (link.stremio.com and api.strem.io, as checked on 13.11.2026).
    private val linkCreated = """{"result":{"success":true,"code":"W88S","link":"https://link.stremio.com/W88S","qrcode":"https://link.stremio.com/qr?data=x"}}"""

    private fun item(id: String, type: String = "movie", removed: Boolean = false, temp: Boolean = false, offset: Long = 0, duration: Long = 0, times: Int = 0, flagged: Int = 0, mtime: String = "2026-11-01T10:00:00.000Z", extra: String = "") =
        """{"_id":"$id","name":"Film $id","type":"$type","poster":null,"posterShape":"poster","removed":$removed,"temp":$temp,
            "_ctime":"2026-10-01T10:00:00.000Z","_mtime":"$mtime",
            "state":{"lastWatched":"2026-11-02T20:00:00.000Z","timeWatched":0,"timeOffset":$offset,"overallTimeWatched":0,"timesWatched":$times,"flaggedWatched":$flagged,"duration":$duration,"video_id":"$id","watched":null,"noNotif":false},
            "behaviorHints":{"defaultVideoId":null}$extra}"""

    @Test
    fun `the link code and the key are read, anything else is refused`() {
        val code = Stremio.parseLinkCode(linkCreated)
        assertEquals(Stremio.LinkCode("W88S", "https://link.stremio.com/W88S"), code)
        assertEquals("https://link.stremio.com/api/v2/read?type=Read&code=W88S", Stremio.linkReadUrl("W88S"))
        assertNull(Stremio.linkReadUrl("W8 8S&x=1"))
        // A link that leads anywhere else is not shown as the QR code: Stremio's own page is.
        val elsewhere = Stremio.parseLinkCode("""{"result":{"code":"W88S","link":"https://evil.example/W88S"}}""")
        assertEquals("https://link.stremio.com/W88S", elsewhere?.link)
        assertNull(Stremio.parseLinkCode("""{"result":{"code":"../x"}}"""))
        assertNull(Stremio.parseLinkCode("not json"))
        // Waiting: Stremio answers with an error until the viewer approves.
        val waiting = """{"error":{"code":101,"message":"Invalid or expired token"}}"""
        assertNull(Stremio.parseLinkKey(waiting))
        assertEquals(101, Stremio.errorCode(waiting))
        assertEquals("abcDEF123456", Stremio.parseLinkKey("""{"result":{"success":true,"authKey":"abcDEF123456"}}"""))
        assertNull(Stremio.parseLinkKey("""{"result":{"authKey":"bad key with spaces"}}"""))
        assertEquals(Stremio.ERROR_SESSION, Stremio.errorCode("""{"error":{"code":1,"message":"Session does not exist"}}"""))
    }

    @Test
    fun `the library keeps films and series by IMDb id, and reads where the viewer stopped`() {
        val text = """{"result":[
            ${item("tt0081505", offset = 3_600_000, duration = 8_760_000)},
            ${item("tt0078748", flagged = 1)},
            ${item("tt0087800", removed = true)},
            ${item("tt0108778", type = "series", offset = 1_000, duration = 2_000)},
            ${item("tt1234567", temp = true, removed = true, offset = 600_000, duration = 6_000_000)},
            {"_id":"yt_id:UC123","name":"Channel","type":"channel","removed":false,"temp":false,"_mtime":"2026-11-01T10:00:00.000Z","state":{}},
            {"_id":"tt0081505","name":"Duplicate","type":"movie","removed":false,"temp":false,"_mtime":"2026-11-01T10:00:00.000Z","state":{}},
            {"_id":"tt0000001","name":"Bad","type":"movie","removed":"yes","temp":false,"_mtime":"x","state":{"timeOffset":-5,"duration":"long"}},
            "nonsense"
        ]}"""
        val items = Stremio.parseLibrary(text)!!
        assertEquals(listOf("tt0081505", "tt0078748", "tt0087800", "tt0108778", "tt1234567", "tt0000001"), items.map { it.imdbId })
        val shining = items.first()
        assertEquals(3_600_000L, shining.offsetMs)
        assertEquals(8_760_000L, shining.durationMs)
        assertTrue(shining.continuing)
        val bad = items.last()
        assertFalse(bad.removed)
        assertEquals(0L, bad.offsetMs)
        assertEquals(0L, bad.modified)
        assertEquals(setOf("tt0081505", "tt0078748", "tt0000001"), Stremio.libraryFilms(items))
        assertEquals(setOf("tt0078748"), Stremio.watchedFilms(items))
        // Continue: stopped in the middle, the library's and the merely played; never what was taken out.
        assertEquals(setOf("tt0081505", "tt0108778", "tt1234567"), Stremio.continueWatching(items).map { it.imdbId }.toSet())
        assertNull(Stremio.parseLibrary("""{"error":{"code":1}}"""))
    }

    @Test
    fun `changes are made on a copy of the item, keeping what SCARY does not know`() {
        val now = Instant.parse("2026-11-13T12:00:00Z")
        val items = Stremio.parseLibrary("""{"result":[${item("tt0081505", removed = true, extra = ""","futureField":42""")}]}""")!!
        val back = Stremio.added(items.single(), "tt0081505", "The Shining", null, now)!!
        assertEquals("false", back["removed"]!!.jsonPrimitive.content)
        assertEquals("2026-11-13T12:00:00Z", back["_mtime"]!!.jsonPrimitive.content)
        assertEquals(42L, back["futureField"]!!.jsonPrimitive.long)
        val out = Stremio.removed(items.single(), now)
        assertEquals("true", out["removed"]!!.jsonPrimitive.content)
        val watched = Stremio.markedWatched(items.single(), now)
        assertEquals(1L, watched["state"]!!.jsonObject["flaggedWatched"]!!.jsonPrimitive.long)
        assertEquals(1L, watched["state"]!!.jsonObject["timesWatched"]!!.jsonPrimitive.long)
        // A new item has everything Stremio's own items have; an odd id is not sent at all.
        val fresh = Stremio.added(null, "tt0084787", "The Thing", "https://images.metahub.space/poster/medium/tt0084787/img", now)!!
        assertNotNull(fresh["state"])
        assertEquals("movie", fresh["type"]!!.jsonPrimitive.content)
        assertNull(Stremio.added(null, "../tt1", "x", null, now))
        // The request carries exactly the changes.
        val body = Json.parseToJsonElement(Stremio.libraryPutBody("KEY12345", listOf(fresh))) as JsonObject
        assertEquals("libraryItem", body["collection"]!!.jsonPrimitive.content)
        assertTrue(Stremio.libraryGetBody("KEY12345").contains("\"all\":true"))
    }
}
