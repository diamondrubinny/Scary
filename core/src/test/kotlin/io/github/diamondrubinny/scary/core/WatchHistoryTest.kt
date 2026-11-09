// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.player.Bookmark
import io.github.diamondrubinny.scary.core.player.WatchEntry
import io.github.diamondrubinny.scary.core.player.WatchHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchHistoryTest {
    private val hour = 60 * 60 * 1000L

    private fun played(key: String = "d:ABC123", at: Long = 30 * 60_000L, now: Long = 1_000L, entries: List<WatchEntry> = emptyList()) =
        WatchHistory.progress(entries, key, "The.Thing.1982.1080p.mkv", "the_thing", at, 2 * hour, now)

    @Test
    fun `progress keeps where the viewer stopped, moves the file first and forgets the oldest`() {
        val one = played("d:A", now = 1)
        val two = played("d:B", now = 2, entries = one)
        assertEquals(listOf("d:B", "d:A"), two.map { it.key })
        assertEquals(30 * 60_000L, two.first().positionMs)
        assertEquals(listOf("d:A", "d:B"), played("d:A", now = 3, entries = two).map { it.key })
        var many = emptyList<WatchEntry>()
        repeat(WatchHistory.MAX_ENTRIES + 5) { i -> many = played("d:F$i", now = i.toLong(), entries = many) }
        assertEquals(WatchHistory.MAX_ENTRIES, many.size)
        assertFalse(many.any { it.key == "d:F0" })
    }

    @Test
    fun `nothing to resume at the very start or past the credits`() {
        assertEquals(0L, WatchHistory.resumePosition(20_000, 2 * hour))
        assertEquals(0L, WatchHistory.resumePosition((2 * hour * 0.95).toLong(), 2 * hour))
        assertEquals(hour, WatchHistory.resumePosition(hour, 2 * hour))
        // A finished film with bookmarks still waits under "Continue watching".
        val finished = WatchHistory.addBookmark(played(at = (2 * hour * 0.99).toLong()), "d:ABC123", hour, 5)
        assertEquals(0L, finished.single().positionMs)
        assertTrue(finished.single().worthContinuing)
    }

    @Test
    fun `bookmarks are kept in order, a spot close to another is the same one, and at most twenty`() {
        var h = played()
        h = WatchHistory.addBookmark(h, "d:ABC123", hour, 10)
        h = WatchHistory.addBookmark(h, "d:ABC123", 10 * 60_000L, 11)
        h = WatchHistory.addBookmark(h, "d:ABC123", hour + 3_000, 12)
        assertEquals(listOf(10 * 60_000L, hour), h.single().bookmarks.map { it.positionMs })
        repeat(30) { i -> h = WatchHistory.addBookmark(h, "d:ABC123", 2 * hour + i * 60_000L, 100L + i) }
        val marks = h.single().bookmarks
        assertEquals(WatchHistory.MAX_BOOKMARKS, marks.size)
        // The oldest marked went; the newest stayed.
        assertTrue(marks.any { it.positionMs == 2 * hour + 29 * 60_000L })
        assertFalse(marks.any { it.positionMs == 10 * 60_000L })
        assertEquals(marks.sortedBy { it.positionMs }, marks)
        // No entry, no bookmark: the file is playing when it is marked.
        assertEquals(h, WatchHistory.addBookmark(h, "d:OTHER", hour, 1))
        val removed = WatchHistory.removeBookmark(h, "d:ABC123", marks.first().positionMs)
        assertEquals(WatchHistory.MAX_BOOKMARKS - 1, removed.single().bookmarks.size)
    }

    @Test
    fun `progress keeps the bookmarks and the name`() {
        var h = WatchHistory.addBookmark(played(), "d:ABC123", hour, 10)
        h = WatchHistory.progress(h, "d:ABC123", "", null, 40 * 60_000L, 0, 20)
        val entry = h.single()
        assertEquals(1, entry.bookmarks.size)
        assertEquals("The.Thing.1982.1080p.mkv", entry.name)
        assertEquals("the_thing", entry.movieId)
        assertEquals(2 * hour, entry.durationMs)
    }

    @Test
    fun `continue watching lists files with somewhere to go back to, latest first`() {
        var h = played("d:A", now = 1)
        h = played("d:B", at = 0, now = 2, entries = h)
        h = played("d:C", now = 3, entries = h)
        h = WatchHistory.addBookmark(h, "d:B", hour, 4)
        assertEquals(listOf("d:B", "d:C", "d:A"), WatchHistory.continueWatching(h).map { it.key })
        assertEquals("d:B", WatchHistory.forMovie(h, "the_thing")?.key)
        assertNull(WatchHistory.forMovie(h, "halloween"))
        assertEquals(listOf("d:B", "d:A"), WatchHistory.continueWatching(WatchHistory.forget(h, "d:C")).map { it.key })
    }

    @Test
    fun `the next file is the next one in the same torrent`() {
        val keys = listOf("t:T1:0", "t:T1:2", "t:T1:5", "t:T2:3", "d:D1")
        assertEquals("t:T1:2", WatchHistory.nextInTorrent("t:T1:0", keys))
        assertEquals("t:T1:5", WatchHistory.nextInTorrent("t:T1:2", keys))
        assertNull(WatchHistory.nextInTorrent("t:T1:5", keys))
        assertNull(WatchHistory.nextInTorrent("d:D1", keys))
    }

    @Test
    fun `stored history round-trips, and hostile text is dropped`() {
        var h = WatchHistory.addBookmark(played(), "d:ABC123", hour, 10)
        h = played("t:T1:3", now = 2, entries = h)
        assertEquals(h, WatchHistory.decode(WatchHistory.encode(h)))
        assertTrue(WatchHistory.decode("not json").isEmpty())
        assertTrue(WatchHistory.decode(null).isEmpty())
        val hostile = """[
            {"k":"https://evil.example/x","n":"x","p":1,"d":2,"w":3},
            {"k":"d:OK1","n":"Fine\u202Ename\u0007","m":"../../x","p":-5,"d":"long","w":4,"b":[{"p":-1},{"p":120000,"c":1},"x"]},
            {"k":"d:OK1","n":"duplicate","p":1,"d":1,"w":1}
        ]"""
        val read = WatchHistory.decode(hostile)
        assertEquals(1, read.size)
        val entry = read.single()
        assertEquals("d:OK1", entry.key)
        assertEquals("Finename", entry.name)
        assertNull(entry.movieId)
        assertEquals(0L, entry.positionMs)
        assertEquals(0L, entry.durationMs)
        assertEquals(listOf(Bookmark(120_000, 1)), entry.bookmarks)
    }

    @Test
    fun `only keys as Real-Debrid lists make them are accepted`() {
        assertTrue(WatchHistory.isValidKey("d:ABCDEF123"))
        assertTrue(WatchHistory.isValidKey("t:XYZ:12"))
        assertFalse(WatchHistory.isValidKey("d:"))
        assertFalse(WatchHistory.isValidKey("t:XYZ"))
        assertFalse(WatchHistory.isValidKey("d:abc/../x"))
        assertTrue(WatchHistory.progress(emptyList(), "https://x", "n", null, hour, 2 * hour, 1).isEmpty())
    }
}
