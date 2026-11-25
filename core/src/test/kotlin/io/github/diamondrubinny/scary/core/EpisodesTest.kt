// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.player.Episodes
import io.github.diamondrubinny.scary.core.player.Episodes.Item
import io.github.diamondrubinny.scary.core.player.Episodes.Number
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which file follows which, and how a series is listed, from file names alone. */
class EpisodesTest {
    @Test
    fun `episodes are read from the usual ways of writing them`() {
        assertEquals(Number(1, 2), Episodes.numberOf("The.Haunting.of.Hill.House.S01E02.1080p.WEB.mkv"))
        assertEquals(Number(3, 10), Episodes.numberOf("show s3 e10.mp4"))
        assertEquals(Number(2, 5), Episodes.numberOf("Show - 2x05 - Title.mkv"))
        assertEquals(Number(1, 4), Episodes.numberOf("Show Episode 4.mkv"))
        assertEquals(Number(1, 7), Episodes.numberOf("Show.E07.720p.mkv"))
        assertEquals(Number(1, 12), Episodes.numberOf("Show - Ep.12.mkv"))
        // A film, a resolution, a year and a codec are not episodes.
        assertNull(Episodes.numberOf("Alien.1979.1080p.BluRay.x264.mkv"))
        assertNull(Episodes.numberOf("Se7en.1995.1920x1080.mkv"))
        assertNull(Episodes.numberOf("The.Eye.2002.mkv"))
        assertNull(Episodes.numberOf("Scream.2022.2160p.HDR10.E-AC3.mkv"))
    }

    @Test
    fun `a series is known by the words before the episode`() {
        assertEquals("the haunting of hill house", Episodes.seriesOf("The.Haunting.of.Hill.House.S01E02.1080p.mkv"))
        assertEquals(Episodes.seriesOf("the haunting of hill house s01e03"), Episodes.seriesOf("The_Haunting_of_Hill_House - S01E02"))
        assertNull(Episodes.seriesOf("Alien.1979.mkv"))
        assertNull(Episodes.seriesOf("S01E02.mkv"))
    }

    @Test
    fun `the next episode is the next number, not the next file`() {
        // Real-Debrid lists a season's files in the torrent's own order: here E10 stands before E2.
        val season = listOf(
            Item("t:T1:0", "Show.S01E01.mkv"),
            Item("t:T1:1", "Show.S01E10.mkv"),
            Item("t:T1:2", "Show.S01E02.mkv"),
            Item("t:T1:3", "Show.S02E01.mkv"),
        )
        assertEquals("t:T1:2", Episodes.next(season[0], season)?.key)
        assertEquals("t:T1:1", Episodes.next(season[2], season)?.key)
        // The season's last episode leads to the next season's first.
        assertEquals("t:T1:3", Episodes.next(season[1], season)?.key)
        assertNull(Episodes.next(season[3], season))
    }

    @Test
    fun `a season added an episode at a time plays on, in either account`() {
        val files = listOf(
            Item("torbox:9:0", "Show.S01E03.720p.mkv"),
            Item("t:AAA:0", "Show.S01E01.1080p.mkv"),
            Item("d:D7", "show s01e02 web.mkv"),
            Item("t:BBB:0", "Other.Show.S01E02.mkv"),
        )
        assertEquals("d:D7", Episodes.next(files[1], files)?.key)
        assertEquals("torbox:9:0", Episodes.next(files[2], files)?.key)
        assertNull(Episodes.next(files[0], files))
        // Another series' episode is never the next one.
        assertNull(Episodes.next(files[3], files))
    }

    @Test
    fun `of two copies of the next episode, the one in the same torrent is played`() {
        val files = listOf(
            Item("t:T1:0", "Show.S01E01.1080p.mkv"),
            Item("t:T2:0", "Show.S01E02.480p.mkv"),
            Item("t:T1:1", "Show.S01E02.1080p.mkv"),
        )
        assertEquals("t:T1:1", Episodes.next(files[0], files)?.key)
    }

    @Test
    fun `a film in parts goes on by name within its torrent, and a film alone has nothing after it`() {
        val files = listOf(
            Item("t:T1:0", "Film.Part.10.mkv"),
            Item("t:T1:1", "Film.Part.2.mkv"),
            Item("t:T1:2", "Film.Part.1.mkv"),
            Item("t:T2:0", "Zebra.mkv"),
            Item("d:D1", "Alone.mkv"),
        )
        assertEquals("t:T1:1", Episodes.next(files[2], files)?.key)
        assertEquals("t:T1:0", Episodes.next(files[1], files)?.key)
        assertNull(Episodes.next(files[0], files))
        assertNull(Episodes.next(files[4], files))
    }

    @Test
    fun `a list shows a series together and in order, and leaves the rest where it was`() {
        val files = listOf(
            Item("d:D1", "Newest.Film.2026.mkv"),
            Item("t:T1:0", "Show.S01E10.mkv"),
            Item("t:T1:1", "Show.S01E02.mkv"),
            Item("t:T1:2", "Show.Extras.mkv"),
            Item("t:F1:0", "A.Film.1980.mkv"),
            Item("torbox:9:0", "show.s01e01.mkv"),
            Item("t:T3:0", "Other.S02E01.mkv"),
            Item("t:T3:1", "Other.S01E05.mkv"),
        )
        assertEquals(
            listOf("d:D1", "torbox:9:0", "t:T1:1", "t:T1:0", "t:T1:2", "t:F1:0", "t:T3:1", "t:T3:0"),
            Episodes.ordered(files) { it }.map { it.key },
        )
        assertTrue(Episodes.ordered(emptyList<Item>()) { it }.isEmpty())
    }

    @Test
    fun `names sort as a person would sort them`() {
        assertEquals(listOf("Part 1", "part 2", "Part 10"), listOf("Part 10", "part 2", "Part 1").sortedWith(Episodes.NATURAL))
    }
}
