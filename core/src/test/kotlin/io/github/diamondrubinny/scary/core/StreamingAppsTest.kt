// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.links.KnownPackages
import io.github.diamondrubinny.scary.core.links.StreamingApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamingAppsTest {
    private fun key(pkg: String, label: String) = StreamingApps.match(pkg, label)?.key

    @Test
    fun knownPackagesMatchWhateverTheirName() {
        assertEquals("netflix", key("com.netflix.ninja", "Netflix"))
        assertEquals("prime", key("com.amazon.amazonvideo.livingroom", "Prime Video"))
        assertEquals("youtube", key(KnownPackages.YOUTUBE_TV, "YouTube"))
    }

    @Test
    fun unknownPackagesMatchByTheirNameInAnyLanguage() {
        assertEquals("cellcom", key("il.example.tv", "Cellcom tv"))
        assertEquals("cellcom", key("il.example.tv", "סלקום TV"))
        assertEquals("hot", key("il.example.hot", "HOT"))
        assertEquals("yes", key("il.example.yes", "yes+"))
        assertEquals("partner", key("il.example.partner", "Partner TV"))
        assertEquals("apple_tv", key("com.example.atv", "Apple TV"))
    }

    @Test
    fun shortCommonWordsDoNotMatchOtherApps() {
        assertNull(key("com.mattel.hotwheels", "Hot Wheels Unlimited"))
        assertNull(key("com.example.hotel", "Hotels"))
        assertNull(key("com.example.partner", "Partner Portal"))
        assertNull(key("com.example.plexus", "Plexus"))
        assertNull(key("com.example.weather", "Weather"))
    }

    @Test
    fun stremioTheStoreSelfAndKidsAppsAreNeverOffered() {
        assertNull(key(KnownPackages.STREMIO, "Stremio"))
        assertNull(key(KnownPackages.PLAY_STORE, "Google Play Store"))
        assertNull(key("io.github.diamondrubinny.scary", "SCARY"))
        assertNull(key("com.google.android.youtube.tvkids", "YouTube Kids"))
    }

    @Test
    fun queriesAreTheTitleAndForYouTubeTheYear() {
        assertEquals("The Shining", StreamingApps.query("  The Shining "))
        assertEquals("The Shining 1980", StreamingApps.youTubeQuery("The Shining", 1980))
        assertEquals(80, StreamingApps.query("x".repeat(200)).length)
    }
}
