// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.tmdb.Studio
import io.github.diamondrubinny.scary.core.tmdb.TmdbPeople
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A film's actors and companies as TMDB documents them (developer.themoviedb.org, 25 Nov 2026), and hostile answers. */
class TmdbPeopleTest {
    private val blumhouse = Studio("Blumhouse", "Blumhouse Productions")

    @Test
    fun `requests are the documented ones and carry nothing but ids and a fixed name`() {
        assertEquals("https://api.themoviedb.org/3/movie/550/credits", TmdbPeople.creditsUrl(550))
        assertEquals(
            "https://api.themoviedb.org/3/discover/movie?with_companies=3172&with_genres=27&sort_by=popularity.desc&include_adult=false&language=${Language.EN.tmdbTag}&page=2",
            TmdbPeople.companyFilmsUrl(3172, Language.EN, 2),
        )
        assertEquals("https://api.themoviedb.org/3/search/company?query=Blumhouse+Productions", TmdbPeople.companySearchUrl(blumhouse))
        for (bad in listOf<() -> Unit>({ TmdbPeople.creditsUrl(0) }, { TmdbPeople.companyFilmsUrl(-1, Language.EN) }, { TmdbPeople.companyFilmsUrl(1, Language.EN, 0) })) {
            try {
                bad()
                throw AssertionError("expected a refusal")
            } catch (e: IllegalArgumentException) {
                // refused, as it should be
            }
        }
    }

    @Test
    fun `the cast comes in TMDB's order with photos only in the image host's shape`() {
        val answer = """{"id":550,"cast":[
            {"adult":false,"id":2,"name":"Second Lead","character":"Marla","order":1,"profile_path":"https://evil.example/x.jpg"},
            {"adult":false,"id":819,"known_for_department":"Acting","name":"Edward Norton","character":"The Narrator","order":0,"profile_path":"/8nytsqL59SFJTVYVrN72k6qkGgJ.jpg"},
            {"adult":true,"id":3,"name":"Left Out","character":"x","order":2,"profile_path":null},
            {"adult":false,"id":4,"name":"","character":"x","order":3},
            {"adult":false,"id":"5","name":"Text Id","order":4},
            {"adult":false,"id":819,"name":"Edward Norton","character":"Again","order":9},
            "junk"],"crew":[{"id":7,"name":"A Director","job":"Director"}]}"""
        val cast = TmdbPeople.parseCast(answer)
        assertEquals(listOf("Edward Norton", "Second Lead"), cast.map { it.name })
        assertEquals("The Narrator", cast[0].character)
        assertEquals("https://image.tmdb.org/t/p/w185/8nytsqL59SFJTVYVrN72k6qkGgJ.jpg", cast[0].photoUrl)
        // An address in place of a path is never opened: the actor is shown without a photo.
        assertNull(cast[1].photoUrl)
        assertTrue(TmdbPeople.parseCast("not json").isEmpty())
        assertTrue(TmdbPeople.parseCast("""{"cast":"none"}""").isEmpty())
    }

    @Test
    fun `no more actors than a row holds`() {
        val many = (1..40).joinToString(",") { """{"id":$it,"name":"Actor $it","character":"c","order":$it}""" }
        val cast = TmdbPeople.parseCast("""{"cast":[$many]}""")
        assertEquals(TmdbPeople.MAX_CAST, cast.size)
        assertEquals("Actor 1", cast.first().name)
    }

    @Test
    fun `a film's companies are read from its details`() {
        val answer = """{"id":1,"original_language":"en","production_companies":[
            {"id":3172,"logo_path":"/kDedjRZwO8uyFhuHamomOhN6fzG.png","name":"Blumhouse Productions","origin_country":"US"},
            {"id":3172,"name":"Blumhouse Productions"},
            {"id":0,"name":"No Id"},{"id":9,"name":"  "},
            {"id":33,"name":"Universal Pictures"},{"id":34,"name":"C"},{"id":35,"name":"D"},{"id":36,"name":"E"}]}"""
        val companies = TmdbPeople.parseCompanies(answer)
        assertEquals(listOf(3172L, 33L, 34L, 35L), companies.map { it.id })
        assertEquals("Blumhouse Productions", companies.first().name)
        assertTrue(TmdbPeople.parseCompanies("""{"id":1}""").isEmpty())
    }

    @Test
    fun `a studio is found only by its exact name, never the nearest`() {
        val answer = """{"page":1,"results":[
            {"id":1,"logo_path":null,"name":"Blumhouse Television","origin_country":"US"},
            {"id":2,"logo_path":null,"name":"Blumhouse Productions","origin_country":""},
            {"id":3172,"logo_path":"/kDedjRZwO8uyFhuHamomOhN6fzG.png","name":"BLUMHOUSE  productions","origin_country":"US"}]}"""
        // Among two of the same name, the one with a logo: the established company has one.
        assertEquals(3172L, TmdbPeople.parseCompanyId(answer, blumhouse))
        assertNull(TmdbPeople.parseCompanyId("""{"results":[{"id":1,"name":"Blumhouse Television"}]}""", blumhouse))
        assertNull(TmdbPeople.parseCompanyId("""{"results":[]}""", blumhouse))
        assertNull(TmdbPeople.parseCompanyId("<html>", blumhouse))
    }

    @Test
    fun `studio ids are kept with their date and asked again after a month`() {
        val now = 1_800_000_000_000L
        val known = mapOf("A24" to TmdbPeople.KnownStudio(41077, now), "Blumhouse Productions" to TmdbPeople.KnownStudio(3172, now - TmdbPeople.STUDIO_ID_MAX_AGE_MS))
        val back = TmdbPeople.decodeStudios(TmdbPeople.encodeStudios(known))
        assertEquals(known, back)
        assertTrue(back.getValue("A24").isFresh(now))
        assertTrue(!back.getValue("Blumhouse Productions").isFresh(now))
        // A clock set back does not make an id fresh for ever.
        assertTrue(!TmdbPeople.KnownStudio(1, now + 1000).isFresh(now))
        // Only studios still on the list are read back, and damage is dropped.
        assertTrue(TmdbPeople.decodeStudios("""{"Gone Studio":"5:$now","A24":"x:y","Lionsgate":7}""").isEmpty())
        assertTrue(TmdbPeople.decodeStudios("junk").isEmpty())
    }

    @Test
    fun `the studio list has no doubles`() {
        assertEquals(TmdbPeople.STUDIOS.size, TmdbPeople.STUDIOS.map { it.query }.toSet().size)
        assertEquals(TmdbPeople.STUDIOS.size, TmdbPeople.STUDIOS.map { it.label }.toSet().size)
    }
}
