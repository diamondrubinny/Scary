// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.sources.Relevance
import io.github.diamondrubinny.scary.core.sources.Relevance.Wanted
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A search for a film shows that film's releases, and not the films that only share a word with it. */
class RelevanceTest {
    private val shining = Wanted("The Shining", 1980)

    @Test
    fun `the film's own releases are kept, however they are written`() {
        for (release in listOf(
            "The.Shining.1980.1080p.BluRay.x264-GROUP",
            "The Shining (1980) [2160p] [4K] [HDR]",
            "Shining 1980 720p WEB-DL",
            "the_shining_1980_remastered_bdrip",
            "The.Shining.EXTENDED.1980.1080p.BluRay",
            "The.Shining.1980.Directors.Cut.2160p.UHD.BluRay",
            "Stanley Kubrick's The Shining 1980 1080p",
            // A film's year differs by a year from country to country.
            "The.Shining.1981.DVDRip",
            // No year at all: only when the name is the title and nothing else.
            "The.Shining.1080p.BluRay.x264",
        )) assertTrue(release, Relevance.isWanted(release, shining))
    }

    @Test
    fun `other films that share its words are left out`() {
        for (release in listOf(
            "The.Shining.Hour.1938.DVDRip",
            "The.Shining.1997.Miniseries.DVDRip",
            "Doctor.Sleep.2019.1080p.BluRay",
            "Shining.Girls.S01E01.1080p.WEB",
            "Shining.Through.1992.720p",
            "The.Shining.2.1982.fan.edit",
            "Room.237.2012.The.Shining.Documentary.1080p",
            "Some.New.Film.2026.1080p.WEB",
            "Shining.Vale.1080p.WEB",
        )) assertFalse(release, Relevance.isWanted(release, shining))
    }

    @Test
    fun `sequels and remakes do not pass for the first film`() {
        val scream = Wanted("Scream", 2022)
        assertTrue(Relevance.isWanted("Scream.2022.2160p.WEB-DL", scream))
        assertFalse(Relevance.isWanted("Scream.VI.2023.1080p.WEB", scream))
        assertFalse(Relevance.isWanted("Scream.2.1997.1080p.BluRay", scream))
        assertFalse(Relevance.isWanted("Scream.1996.1080p.BluRay", scream))
        val halloween = Wanted("Halloween", 1978)
        assertTrue(Relevance.isWanted("Halloween.1978.REMASTERED.1080p.BluRay", halloween))
        assertFalse(Relevance.isWanted("Halloween.II.1981.1080p", halloween))
        assertFalse(Relevance.isWanted("Halloween.2018.1080p", halloween))
        assertFalse(Relevance.isWanted("Halloween.Kills.2021.1080p", halloween))
        val alien = Wanted("Alien", 1979)
        assertFalse(Relevance.isWanted("Aliens.1986.1080p", alien))
        assertFalse(Relevance.isWanted("Alien.Covenant.2017.1080p", alien))
        assertTrue(Relevance.isWanted("Alien.1979.Directors.Cut.1080p", alien))
    }

    @Test
    fun `names with numbers, accents and signs are read as names`() {
        assertTrue(Relevance.isWanted("2001.A.Space.Odyssey.1968.2160p.UHD", Wanted("2001: A Space Odyssey", 1968)))
        assertTrue(Relevance.isWanted("Se7en.1995.1080p.BluRay", Wanted("Se7en", 1995)))
        assertTrue(Relevance.isWanted("28.Days.Later.2002.1080p", Wanted("28 Days Later", 2002)))
        assertFalse(Relevance.isWanted("28.Weeks.Later.2007.1080p", Wanted("28 Days Later", 2002)))
        assertTrue(Relevance.isWanted("Les.Yeux.Sans.Visage.1960.1080p", Wanted("Les yeux sans visage", 1960)))
        assertTrue(Relevance.isWanted("REC.2007.1080p.BluRay", Wanted("[REC]", 2007)))
        assertTrue(Relevance.isWanted("Rosemarys.Baby.1968.1080p", Wanted("Rosemary's Baby", 1968)))
        assertTrue(Relevance.isWanted("Fast.and.Furious.2009.720p", Wanted("Fast & Furious", 2009)))
        assertTrue(Relevance.isWanted("Pontypool.2008.720p", Wanted("Pontypool", 2008)))
        assertFalse(Relevance.isWanted("anything", Wanted("  ", 2000)))
    }

    @Test
    fun `a series is found by its name, whatever the season`() {
        val series = Wanted("The Haunting of Hill House", 2018, series = true)
        assertTrue(Relevance.isWanted("The.Haunting.of.Hill.House.S01E02.1080p.WEB", series))
        assertTrue(Relevance.isWanted("The Haunting of Hill House Season 1 Complete 1080p", series))
        assertTrue(Relevance.isWanted("The.Haunting.of.Hill.House.2018.S01.1080p", series))
        assertFalse(Relevance.isWanted("The.Haunting.of.Bly.Manor.S01E01.1080p", series))
        assertFalse(Relevance.isWanted("The.Haunting.1963.1080p", series))
    }

    @Test
    fun `words the viewer typed must all be there`() {
        assertTrue(Relevance.holdsWords("Night.of.the.Living.Dead.1968.1080p.BluRay", "night living dead 1968"))
        assertTrue(Relevance.holdsWords("Night.of.the.Living.Dead.1968.1080p.BluRay", "The Living Dead"))
        assertFalse(Relevance.holdsWords("Dawn.of.the.Dead.1978.1080p", "night living dead"))
        assertFalse(Relevance.holdsWords("Night.of.the.Living.Dead.1990.1080p", "night living dead 1968"))
        assertTrue(Relevance.holdsWords("anything at all", "  "))
    }

    @Test
    fun `a search for Postmortem 2026 shows that film and none of the others the indexer gave`() {
        // What an indexer answered for it (a viewer's screen, 26 Nov 2026).
        val wanted = Wanted("Postmortem", 2026)
        assertFalse(Relevance.isWanted("Chicago.Fire.S13E13.Post.Mortem.720p.AMZN.WEB-DL.DDP5.1.H.264", wanted))
        assertFalse(Relevance.isWanted("Sarah Silverman PostMortem 2025 720p NF WEB-DL DDP5 1 H 264-FLUX", wanted))
        assertFalse(Relevance.isWanted("Romance.Post.Mortem.2023.1080P.BLURAY.X264-WATCHABLE", wanted))
        // The film itself, with its name in one word or two.
        assertTrue(Relevance.isWanted("Postmortem.2026.1080p.WEB-DL.x264", wanted))
        assertTrue(Relevance.isWanted("Post.Mortem.2026.2160p.WEB.H265", wanted))
        assertTrue(Relevance.isWanted("Post Mortem (2025) 1080p WEBRip", wanted))
        // And the other way: a title in two words, a release in one.
        assertTrue(Relevance.isWanted("Postmortem.2026.720p", Wanted("Post Mortem", 2026)))
        assertFalse(Relevance.isWanted("Post.Mortems.2026.720p", wanted))
    }
}
