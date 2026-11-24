// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.CatalogException
import io.github.diamondrubinny.scary.core.catalog.LegacyLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LegacyLibraryTest {
    @Test
    fun `an old id becomes the film's new one, and any other id stays`() {
        val legacy = LegacyLibrary.parse("""{"version":1,"films":{"psycho":{"imdb":"tt0054215","fear":4},"dark":{"imdb":"tt5753856"}}}""")
        assertEquals("cm_tt0054215", legacy.newId("psycho"))
        assertEquals("cm_tt5753856", legacy.newId("dark"))
        assertEquals("cm_tt0078748", legacy.newId("cm_tt0078748"))
        assertEquals("not_a_film_here", legacy.newId("not_a_film_here"))
        assertEquals(4, legacy.fear("tt0054215"))
        // No fear level is "unknown", never a guess.
        assertNull(legacy.fear("tt5753856"))
        assertNull(legacy.fear("tt0078748"))
    }

    @Test
    fun `an entry that breaks a rule is dropped, and a broken file is refused`() {
        val legacy = LegacyLibrary.parse(
            """{"version":1,"films":{"good":{"imdb":"tt0054215","fear":9},"Bad Id":{"imdb":"tt0000001"},"bad_imdb":{"imdb":"../x"},"cm_tt0000002":{"imdb":"tt0000002"}}}""",
        )
        assertEquals(1, legacy.size)
        assertNull("a fear level out of range is no fear level", legacy.fear("tt0054215"))
        assertEquals("Bad Id", legacy.newId("Bad Id"))
        assertTrue(runCatching { LegacyLibrary.parse("nope") }.exceptionOrNull() is CatalogException)
        assertTrue(runCatching { LegacyLibrary.parse("""{"version":2,"films":{}}""") }.exceptionOrNull() is CatalogException)
        assertEquals("x", LegacyLibrary.EMPTY.newId("x"))
    }

    @Test
    fun `the bundled file holds every id and fear level of the library`() {
        val legacy = LegacyLibrary.parse(File("../app/src/main/assets/legacy.json").readText())
        assertEquals(493, legacy.size)
        assertEquals("cm_tt0054215", legacy.newId("psycho"))
        assertEquals("cm_tt0063522", legacy.newId("rosemarys_baby"))
        assertTrue((legacy.fear("tt0054215") ?: 0) in 1..5)
    }
}
