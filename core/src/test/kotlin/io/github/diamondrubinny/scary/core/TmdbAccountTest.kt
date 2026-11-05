// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.tmdb.ListSync
import io.github.diamondrubinny.scary.core.tmdb.TmdbAccount
import io.github.diamondrubinny.scary.core.tmdb.TmdbAccount.AccountList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The TMDB account: requests as documented (developer.themoviedb.org), answers parsed, lists kept in step. */
class TmdbAccountTest {
    private val requestToken = "ff5c7eeb5a8870efe3cd7fc5c282cffd26800ecd"
    private val session = "79191836ddaa0da3df76a5ffef6f07ad6ab0c641"

    @Test
    fun `sign-in requests are the documented ones`() {
        assertEquals("https://api.themoviedb.org/3/authentication/token/new", TmdbAccount.requestTokenUrl())
        assertEquals("https://www.themoviedb.org/authenticate/$requestToken", TmdbAccount.approveUrl(requestToken))
        assertEquals("https://api.themoviedb.org/3/authentication/session/new", TmdbAccount.createSessionUrl())
        assertEquals("""{"request_token":"$requestToken"}""", TmdbAccount.sessionBody(requestToken))
        assertEquals("""{"session_id":"$session"}""", TmdbAccount.deleteSessionBody(session))
        assertEquals("https://api.themoviedb.org/3/account?session_id=$session", TmdbAccount.accountUrl(session))
        assertEquals("https://api.themoviedb.org/3/account/42?session_id=$session", TmdbAccount.accountUrl(session, 42))
        assertEquals(
            "https://api.themoviedb.org/3/account/42/watchlist/movies?session_id=$session&page=2&sort_by=created_at.asc",
            TmdbAccount.listUrl(42, session, AccountList.WATCHLIST, 2),
        )
        assertEquals("""{"media_type":"movie","media_id":11,"watchlist":true}""", TmdbAccount.watchlistBody(11, add = true))
        assertEquals("""{"media_type":"movie","media_id":11,"favorite":false}""", TmdbAccount.favoriteBody(11, add = false))
    }

    @Test
    fun `a forged token cannot steer the approval link`() {
        listOf("", "abc", "../evil", "ff5c7eeb5a8870efe3cd7fc5c282cffd26800ecd?redirect_to=https://evil.example").forEach {
            assertTrue(runCatching { TmdbAccount.approveUrl(it) }.isFailure)
        }
        assertNull(TmdbAccount.parseRequestToken("""{"success":true,"request_token":"../../x"}"""))
        assertTrue(runCatching { TmdbAccount.accountUrl("abc&page=1") }.isFailure)
        assertTrue(runCatching { TmdbAccount.listUrl(1, "x/../y", AccountList.RATED, 1) }.isFailure)
    }

    @Test
    fun `answers from the docs are read, junk and failures are not`() {
        assertEquals(
            requestToken,
            TmdbAccount.parseRequestToken("""{"success":true,"expires_at":"2016-08-26 17:04:39 UTC","request_token":"$requestToken"}"""),
        )
        assertEquals(session, TmdbAccount.parseSession("""{"success":true,"session_id":"$session"}"""))
        assertNull(TmdbAccount.parseSession("""{"success":false,"status_code":17,"status_message":"Session denied."}"""))
        assertNull(TmdbAccount.parseSession("not json"))
        assertEquals(TmdbAccount.Account(548, "scary_fan"), TmdbAccount.parseAccount("""{"id":548,"username":"scary_fan","name":""}"""))
        assertNull(TmdbAccount.parseAccount("""{"id":0}"""))
        val page = TmdbAccount.parseListPage(
            """{"page":1,"results":[{"id":694,"title":"The Shining","genre_ids":[27,53],"release_date":"1980-05-23","rating":9.0},
               {"id":0,"title":"broken"}],"total_pages":3,"total_results":41}""",
        )!!
        assertEquals(listOf(694L), page.ids)
        assertEquals(3, page.totalPages)
        assertTrue(TmdbAccount.parseChanged("""{"status_code":1,"status_message":"Success."}"""))
        assertTrue(TmdbAccount.parseChanged("""{"status_code":13,"status_message":"The item/record was deleted successfully."}"""))
        assertFalse(TmdbAccount.parseChanged("""{"status_code":3,"status_message":"Authentication failed."}"""))
    }

    @Test
    fun `the first sync joins both lists and removes nothing`() {
        val plan = ListSync.plan(local = listOf("tt1", "tt2"), remote = setOf("tt2", "tt3"), lastSynced = null)
        assertEquals(listOf("tt3", "tt1", "tt2"), plan.local)
        assertEquals(setOf("tt1"), plan.addRemote)
        assertEquals(emptySet<String>(), plan.removeRemote)
        assertEquals(setOf("tt1", "tt2", "tt3"), plan.synced)
    }

    @Test
    fun `later syncs carry adds and removals from each side to the other`() {
        val last = setOf("tt1", "tt2", "tt3")
        // On the TV: tt1 removed, tt4 added. On TMDB: tt2 removed, tt5 added.
        val plan = ListSync.plan(local = listOf("tt4", "tt2", "tt3"), remote = setOf("tt1", "tt3", "tt5"), lastSynced = last)
        assertEquals(listOf("tt5", "tt4", "tt3"), plan.local)
        assertEquals(setOf("tt4"), plan.addRemote)
        assertEquals(setOf("tt1"), plan.removeRemote)
        assertEquals(setOf("tt3", "tt4", "tt5"), plan.synced)
        // Nothing changed anywhere: nothing to do.
        val calm = ListSync.plan(listOf("tt3", "tt4", "tt5"), setOf("tt3", "tt4", "tt5"), plan.synced)
        assertEquals(listOf("tt3", "tt4", "tt5"), calm.local)
        assertTrue(calm.addRemote.isEmpty() && calm.removeRemote.isEmpty())
    }

    @Test
    fun `a change TMDB did not take is tried again, never read as a removal`() {
        val plan = ListSync.plan(local = listOf("tt9", "tt1"), remote = setOf("tt1", "tt2"), lastSynced = setOf("tt1", "tt2"))
        assertEquals(setOf("tt9"), plan.addRemote)
        // tt2 removed on the TV, but TMDB failed to remove it; tt9 failed to be added.
        val local2 = ListSync.plan(local = listOf("tt9", "tt1"), remote = setOf("tt1", "tt2"), lastSynced = setOf("tt1", "tt2"))
        val synced = local2.syncedAfter(failedAdds = setOf("tt9"), failedRemoves = local2.removeRemote)
        val next = ListSync.plan(local = local2.local, remote = setOf("tt1", "tt2"), lastSynced = synced)
        assertTrue("tt9 is still on the TV", "tt9" in next.local)
        assertEquals(setOf("tt9"), next.addRemote)
        assertEquals(local2.removeRemote, next.removeRemote)
    }

    @Test
    fun `applying a sync keeps series and unmapped entries in place, removes and adds only films`() {
        // My list on the TV: a film from TMDB's side, a series (not synced), a curated film, an online film.
        val original = listOf("the_shining", "series_dark", "hereditary", "cm_tt0070047")
        val imdb = mapOf("the_shining" to "tt0081505", "hereditary" to "tt7784604", "cm_tt0070047" to "tt0070047")
        val synced = setOf("tt0081505", "tt7784604", "tt0070047")
        // TMDB removed Hereditary and added a film SCARY knows as "cm_tt1457767".
        val agreed = listOf("tt1457767", "tt0081505", "tt0070047")
        val after = ListSync.applyTo(original, synced, agreed, imdb::get) { if (it == "tt1457767") "cm_tt1457767" else null }
        assertEquals(listOf("cm_tt1457767", "the_shining", "series_dark", "cm_tt0070047"), after)
        // Nothing agreed differently: the list stays exactly as it was.
        assertEquals(original, ListSync.applyTo(original, synced, synced.toList(), imdb::get) { null })
    }

    @Test
    fun `rated films only ever add to Watched`() {
        val plan = ListSync.plan(local = listOf("tt1"), remote = setOf("tt2"), lastSynced = setOf("tt1"), addOnly = true)
        assertEquals(listOf("tt2", "tt1"), plan.local)
        assertTrue(plan.addRemote.isEmpty() && plan.removeRemote.isEmpty())
    }
}
