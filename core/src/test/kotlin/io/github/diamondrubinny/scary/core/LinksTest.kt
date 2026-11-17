// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.links.Links
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class LinksTest {
    @Test
    fun `stremio link uses imdb id`() {
        assertEquals("stremio:///detail/movie/tt0081505/tt0081505", Links.stremioMovie("tt0081505"))
    }

    @Test
    fun `rejects ids that could inject into the uri`() {
        assertThrows(IllegalArgumentException::class.java) { Links.stremioMovie("tt0081505/../x") }
        assertThrows(IllegalArgumentException::class.java) { Links.playStoreMarket("com.x&id=evil") }
        assertThrows(IllegalArgumentException::class.java) { Links.playStoreWeb("nodots") }
    }

    @Test
    fun `play store links`() {
        assertEquals("market://details?id=com.stremio.one", Links.playStoreMarket("com.stremio.one"))
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.stremio.one",
            Links.playStoreWeb("com.stremio.one"),
        )
    }

    @Test
    fun `trailer search encodes the query`() {
        assertEquals(
            "https://www.youtube.com/results?search_query=Rosemary%27s+Baby+1968+trailer",
            Links.youTubeTrailerSearch("Rosemary's Baby", 1968),
        )
    }

    @Test
    fun `an episode link names the series and Stremio's own episode id`() {
        assertEquals("stremio:///detail/series/tt0944947/tt0944947:1:2", Links.stremioEpisode("tt0944947:1:2"))
        listOf("tt0944947", "tt0944947:1", "tt0944947:1:2/../x", "x:1:2", "tt0944947:100:2", "tt0944947:1:2:3", "").forEach {
            val rejected = try { Links.stremioEpisode(it); false } catch (e: IllegalArgumentException) { true }
            assertTrue("rejects $it", rejected)
        }
    }
}
