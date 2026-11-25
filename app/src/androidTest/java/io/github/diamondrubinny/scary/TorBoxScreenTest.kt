// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.diamondrubinny.scary.core.realdebrid.RealDebrid
import io.github.diamondrubinny.scary.ui.home.RdFilesState
import io.github.diamondrubinny.scary.ui.realdebrid.REAL_DEBRID_SERVICE_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.RealDebridActions
import io.github.diamondrubinny.scary.ui.realdebrid.RealDebridScreen
import io.github.diamondrubinny.scary.ui.realdebrid.TORBOX_OPEN_TAG
import androidx.compose.ui.test.performTextInput
import io.github.diamondrubinny.scary.core.realdebrid.Magnets
import io.github.diamondrubinny.scary.core.sources.Releases
import io.github.diamondrubinny.scary.data.DebridAccounts.Service
import io.github.diamondrubinny.scary.ui.home.RdPullState
import io.github.diamondrubinny.scary.ui.home.RdSearchState
import io.github.diamondrubinny.scary.ui.home.RdTorrentsState
import io.github.diamondrubinny.scary.ui.realdebrid.RD_SOURCES_PULL_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.RD_SOURCES_PULL_TORBOX_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.RD_TORRENTS_FIELD_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.RD_TORRENTS_PULL_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.RD_TORRENTS_PULL_TORBOX_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.RdSourcesActions
import io.github.diamondrubinny.scary.ui.realdebrid.RdSourcesScreen
import io.github.diamondrubinny.scary.ui.realdebrid.RdTorrentsActions
import io.github.diamondrubinny.scary.ui.realdebrid.RdTorrentsScreen
import io.github.diamondrubinny.scary.ui.realdebrid.releaseTag
import io.github.diamondrubinny.scary.data.RealDebridRepository.Reason
import io.github.diamondrubinny.scary.data.RealDebridRepository.State
import io.github.diamondrubinny.scary.ui.realdebrid.TORBOX_CODE_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.TORBOX_SIGN_IN_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.TORBOX_SIGN_OUT_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.TORBOX_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.TorBoxScreen
import io.github.diamondrubinny.scary.ui.theme.ScaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The TorBox account screen, on its own: nothing is sent to TorBox here, only what the screen says and asks for. */
@RunWith(AndroidJUnit4::class)
class TorBoxScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    @Test
    fun connectingShowsTheCodeAndTheAccountThenDisconnects() {
        val calls = mutableListOf<String>()
        var state by mutableStateOf<State>(State.SignedOut)
        rule.runOnUiThread { rule.activity.setContent {
            ScaryTheme {
                TorBoxScreen(
                    state = state,
                    onSignIn = { calls += "in"; state = State.Waiting("https://torbox.app/oauth/device", "123456") },
                    onCancel = { calls += "cancel"; state = State.SignedOut },
                    onSignOut = { calls += "out"; state = State.SignedOut },
                )
            }
        } }
        rule.onNode(hasTestTag(TORBOX_TAG)).assertExists()
        rule.onNode(hasText(text(R.string.torbox_signed_out))).assertExists()
        // What the connection is, said before connecting: a key to the whole account that does not run out.
        rule.onNode(hasText(text(R.string.torbox_key_note))).assertExists()

        rule.onNode(hasTestTag(TORBOX_SIGN_IN_TAG)).performClick()
        rule.onNode(hasTestTag(TORBOX_CODE_TAG)).assertTextEquals("123456")
        rule.onNode(hasText(text(R.string.torbox_step_open, "torbox.app/oauth/device"))).assertExists()
        rule.onNode(hasTestTag(TORBOX_SIGN_IN_TAG)).assertDoesNotExist()

        rule.runOnUiThread { state = State.SignedIn("viewer@example.org", premium = true, expiration = "2027-01-01T00:00:00Z") }
        rule.onNode(hasText(text(R.string.torbox_signed_in_until, "viewer@example.org", "2027-01-01"))).assertExists()
        rule.onNode(hasTestTag(TORBOX_CODE_TAG)).assertDoesNotExist()

        rule.onNode(hasTestTag(TORBOX_SIGN_OUT_TAG)).performClick()
        rule.onNode(hasText(text(R.string.torbox_signed_out))).assertExists()
        assertEquals(listOf("in", "out"), calls)
    }

    @Test
    fun aFreePlanAndAFailureAreSaidInWords() {
        var state by mutableStateOf<State>(State.SignedIn("viewer@example.org", premium = false, expiration = ""))
        rule.runOnUiThread { rule.activity.setContent {
            ScaryTheme { TorBoxScreen(state = state, onSignIn = {}, onCancel = {}, onSignOut = {}) }
        } }
        rule.onNode(hasText(text(R.string.torbox_signed_in_free, "viewer@example.org"))).assertExists()
        rule.runOnUiThread { state = State.Failed(Reason.EXPIRED) }
        rule.onNode(hasText(text(R.string.torbox_expired))).assertExists()
        rule.onNode(hasTestTag(TORBOX_SIGN_IN_TAG)).assertExists()
        rule.runOnUiThread { state = State.Failed(Reason.SESSION_ENDED) }
        rule.onNode(hasText(text(R.string.torbox_session_ended))).assertExists()
    }

    @Test
    fun withTorBoxAloneItsFilesAreListedPlayedAndNamedByTheirAccount() {
        val mine = RealDebrid.RdFile("d:1", "The.Shining.mkv", 4_000_000_000, "https://real-debrid.com/d/1")
        val theirs = RealDebrid.RdFile("torbox:7:0", "Alien.1979.mkv", 3_000_000_000, "torbox:7:0")
        val played = mutableListOf<String>()
        var state by mutableStateOf<State>(State.SignedOut)
        var files by mutableStateOf(listOf(theirs))
        var torBox by mutableStateOf(true)
        rule.runOnUiThread { rule.activity.setContent {
            ScaryTheme {
                RealDebridScreen(
                    state = state,
                    files = RdFilesState.Ready(files),
                    actions = RealDebridActions(onSignIn = {}, onCancel = {}, onSignOut = {}, onRefresh = {}, onPlay = { played += it.key }, onTorBox = {}),
                    torBoxConnected = torBox,
                )
            }
        } }
        // Real-Debrid is not connected, and the list is there all the same, with the way to connect it still offered.
        rule.onNode(hasText(text(R.string.rd_sign_in))).assertExists()
        rule.onNode(hasTestTag(TORBOX_OPEN_TAG)).assertExists()
        rule.onNode(hasText("Alien.1979.mkv")).performClick()
        assertEquals(listOf("torbox:7:0"), played)
        rule.onNode(hasTestTag(REAL_DEBRID_SERVICE_TAG) and hasText(text(R.string.torbox_title))).assertExists()

        // Both connected: each file says whose it is.
        rule.runOnUiThread {
            state = State.SignedIn("viewer", true, "2027-01-01")
            files = listOf(mine, theirs)
        }
        rule.onNode(hasTestTag(REAL_DEBRID_SERVICE_TAG) and hasText(text(R.string.rd_title))).assertExists()
        rule.onNode(hasTestTag(REAL_DEBRID_SERVICE_TAG) and hasText(text(R.string.torbox_title))).assertExists()

        // Real-Debrid alone: nothing to tell apart, so no label.
        rule.runOnUiThread {
            torBox = false
            files = listOf(mine)
        }
        rule.onNode(hasTestTag(REAL_DEBRID_SERVICE_TAG)).assertDoesNotExist()
    }

    @Test
    fun withBothAccountsTheViewerSaysWhereALinkGoes() {
        val magnet = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"
        val sent = mutableListOf<String>()
        var services by mutableStateOf(listOf(Service.REAL_DEBRID, Service.TORBOX))
        rule.runOnUiThread { rule.activity.setContent {
            ScaryTheme {
                RdTorrentsScreen(
                    state = RdTorrentsState(torrents = emptyList()),
                    actions = RdTorrentsActions(
                        onPull = { sent += "only" },
                        onRefresh = {},
                        onPlay = {},
                        onDelete = {},
                        onPullTo = { _, service -> sent += service.name },
                    ),
                    services = services,
                )
            }
        } }
        rule.onNode(hasTestTag(RD_TORRENTS_FIELD_TAG)).performTextInput(magnet)
        rule.onNode(hasTestTag(RD_TORRENTS_PULL_TORBOX_TAG) and hasText(text(R.string.debrid_add_to_torbox))).performClick()
        rule.onNode(hasTestTag(RD_TORRENTS_PULL_TAG) and hasText(text(R.string.debrid_add_to_rd))).performClick()
        assertEquals(listOf("TORBOX", "REAL_DEBRID"), sent)

        // One account: one button, and nothing to choose.
        rule.runOnUiThread { services = listOf(Service.TORBOX) }
        rule.onNode(hasTestTag(RD_TORRENTS_PULL_TORBOX_TAG)).assertDoesNotExist()
        rule.onNode(hasTestTag(RD_TORRENTS_PULL_TAG)).performClick()
        assertEquals(listOf("TORBOX", "REAL_DEBRID", "only"), sent)
    }

    @Test
    fun aReleaseGoesToTheAccountTheViewerChooses() {
        val release = Releases.Candidate("A.Film.2020.1080p.BluRay", Magnets.Magnet("0123456789abcdef0123456789abcdef01234567"), "Site", 4_000_000_000, 50, Releases.tags("A.Film.2020.1080p.BluRay"))
        val sent = mutableListOf<String>()
        rule.runOnUiThread { rule.activity.setContent {
            ScaryTheme {
                RdSourcesScreen(
                    search = RdSearchState.Ready(listOf(release)),
                    pull = RdPullState.Idle,
                    indexerHost = "indexer.example",
                    actions = RdSourcesActions(
                        onSearch = {},
                        onPull = { sent += "only" },
                        onSaveIndexer = { _, _ -> throw AssertionError("not asked for") },
                        onRemoveIndexer = {},
                        onPullTo = { _, service -> sent += service.name },
                    ),
                    services = listOf(Service.REAL_DEBRID, Service.TORBOX),
                )
            }
        } }
        rule.onNode(hasTestTag(releaseTag(release.magnet.infoHash))).performClick()
        // Nothing is added by opening the window; both accounts are offered, by name.
        rule.onNode(hasText(text(R.string.debrid_pull_confirm))).assertExists()
        rule.onNode(hasTestTag(RD_SOURCES_PULL_TAG) and hasText(text(R.string.debrid_add_to_rd))).assertExists()
        assertEquals(emptyList<String>(), sent)
        rule.onNode(hasTestTag(RD_SOURCES_PULL_TORBOX_TAG)).performClick()
        assertEquals(listOf("TORBOX"), sent)
    }
}
