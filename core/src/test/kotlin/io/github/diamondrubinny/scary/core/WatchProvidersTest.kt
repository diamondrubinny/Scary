// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.links.Region
import io.github.diamondrubinny.scary.core.links.ResolvedCountry
import io.github.diamondrubinny.scary.core.links.StreamingApps
import io.github.diamondrubinny.scary.core.tmdb.OfferKind
import io.github.diamondrubinny.scary.core.tmdb.WatchProviders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchProvidersTest {
    // The shape of TMDB's /movie/{id}/watch/providers reply, trimmed to two countries.
    private val reply = """
        {"id":694,"results":{
          "IL":{"link":"https://www.themoviedb.org/movie/694-the-shining/watch?locale=IL",
            "rent":[{"logo_path":"/apple123.jpg","provider_id":2,"provider_name":"Apple TV","display_priority":4},
                    {"logo_path":"/gplay45.jpg","provider_id":3,"provider_name":"Google Play Movies","display_priority":7}],
            "buy":[{"logo_path":"/apple123.jpg","provider_id":2,"provider_name":"Apple TV","display_priority":2}],
            "flatrate":[{"logo_path":"/netflix9.jpg","provider_id":8,"provider_name":"Netflix","display_priority":1}]},
          "US":{"link":"https://evil.example/phish",
            "buy":[{"logo_path":"../../etc","provider_id":10,"provider_name":"Amazon Video‮","display_priority":3},
                   {"provider_id":-1,"provider_name":"Broken"},
                   {"provider_id":11,"provider_name":"   "}],
            "ads":[{"provider_id":73,"provider_name":"Tubi TV","display_priority":9}]},
          "europe":{"buy":[{"provider_id":1,"provider_name":"Nope"}]}}}
    """.trimIndent()

    @Test
    fun aCountryMergesEveryWayAStoreOffersTheMovie() {
        val il = WatchProviders.parse(reply)!!.getValue("IL")
        val apple = il.providers.single { it.id == 2L }
        assertEquals(setOf(OfferKind.RENT, OfferKind.BUY), apple.kinds)
        // The best (lowest) priority wins, and the list is sorted by it.
        assertEquals(2, apple.priority)
        assertEquals(listOf("Netflix", "Apple TV", "Google Play Movies"), il.providers.map { it.name })
        assertEquals(listOf("Apple TV", "Google Play Movies"), il.rentOrBuy.map { it.name })
        assertEquals(listOf("Netflix"), il.included.map { it.name })
        assertEquals("https://image.tmdb.org/t/p/w92/apple123.jpg", apple.logoUrl)
        assertEquals("https://www.themoviedb.org/movie/694-the-shining/watch?locale=IL", il.link)
    }

    @Test
    fun untrustedFieldsAreCleanedOrDropped() {
        val all = WatchProviders.parse(reply)!!
        // Only ISO country codes are kept.
        assertEquals(setOf("IL", "US"), all.keys)
        val us = all.getValue("US")
        // A link outside themoviedb.org is dropped, as are a bad logo path, a bad id and a blank name.
        assertNull(us.link)
        val amazon = us.providers.single { it.id == 10L }
        assertNull(amazon.logoUrl)
        assertEquals("Amazon Video", amazon.name)
        assertEquals(listOf(10L, 73L), us.providers.map { it.id })
        // Free with ads counts as included.
        assertEquals(listOf("Tubi TV"), us.included.map { it.name })
    }

    @Test
    fun anythingThatIsNotAWatchProvidersReplyIsNull() {
        assertNull(WatchProviders.parse("not json"))
        assertNull(WatchProviders.parse("""{"status_code":7,"status_message":"Invalid API key"}"""))
        assertTrue(WatchProviders.parse("""{"id":1,"results":{}}""")!!.isEmpty())
    }

    @Test
    fun theUrlHasOnlyTheMovieIdInIt() {
        assertEquals("https://api.themoviedb.org/3/movie/694/watch/providers", WatchProviders.url(694))
    }

    @Test
    fun theCountryIsTheChosenOneThenTheNetworkThenTheSimThenTheDevice() {
        assertEquals(ResolvedCountry("DE", automatic = false), Region.resolve("de", "IL", "IL", "US"))
        assertEquals(ResolvedCountry("IL", automatic = true), Region.resolve(null, " il ", "US", "US"))
        assertEquals(ResolvedCountry("FR", automatic = true), Region.resolve("", "", "fr", "US"))
        assertEquals(ResolvedCountry("US", automatic = true), Region.resolve(null, null, null, "US"))
        // Numeric regions ("419", Latin America) and "unknown" codes are no country.
        assertNull(Region.resolve(null, "", null, "419"))
        assertNull(Region.resolve("ZZ", null, null, null))
    }

    @Test
    fun storesFromTmdbMatchTheAppsTheyLiveIn() {
        fun key(name: String) = StreamingApps.matchProvider(name)?.key
        assertEquals("apple_tv", key("Apple TV"))
        assertEquals("google_tv", key("Google Play Movies"))
        assertEquals("prime", key("Amazon Video"))
        assertEquals("prime", key("Paramount+ Amazon Channel"))
        assertEquals("max", key("Max"))
        assertEquals("netflix", key("Netflix Standard with Ads"))
        assertEquals("rakuten", key("Rakuten TV"))
        assertNull(key("Microsoft Store"))
        assertNull(key("YouTube Kids"))
        assertNull(key(""))
    }
}
