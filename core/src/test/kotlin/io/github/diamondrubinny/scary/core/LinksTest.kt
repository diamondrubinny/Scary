package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.links.Links
import org.junit.Assert.assertEquals
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
}
