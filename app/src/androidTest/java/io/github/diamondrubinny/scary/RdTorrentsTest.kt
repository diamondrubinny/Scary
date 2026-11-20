// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary

import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.diamondrubinny.scary.core.realdebrid.RealDebrid
import io.github.diamondrubinny.scary.data.RealDebridRepository
import io.github.diamondrubinny.scary.ui.home.RdFilesState
import io.github.diamondrubinny.scary.ui.home.RdTorrentsState
import io.github.diamondrubinny.scary.ui.realdebrid.RD_TORRENTS_CONFIRM_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.RD_TORRENTS_DELETE_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.RD_TORRENTS_FIELD_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.RD_TORRENTS_PULL_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.REAL_DEBRID_TORRENTS_BUTTON_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.RdTorrentsActions
import io.github.diamondrubinny.scary.ui.realdebrid.RdTorrentsScreen
import io.github.diamondrubinny.scary.ui.realdebrid.RealDebridActions
import io.github.diamondrubinny.scary.ui.realdebrid.RealDebridScreen
import io.github.diamondrubinny.scary.ui.realdebrid.torrentTag
import io.github.diamondrubinny.scary.ui.theme.ScaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Add a torrent" (the sources feature), composed on its own with fake data: the link the viewer typed is passed on,
 * and deleting a torrent takes a second "yes". A build without the feature shows no way in.
 */
@RunWith(AndroidJUnit4::class)
class RdTorrentsTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun theLinkIsPassedOnAndDeletingAsksTwice() {
        val pulled = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val torrent = RealDebrid.TorrentStatus("A1", "Night.of.the.Living.Dead.1968", RealDebrid.TorrentState.DOWNLOADING, 42, 1_000_000_000)
        rule.runOnUiThread { rule.activity.setContent {
            ScaryTheme {
                RdTorrentsScreen(
                    state = RdTorrentsState(torrents = listOf(torrent), active = RealDebrid.ActiveCount(1, 25)),
                    actions = RdTorrentsActions(onPull = { pulled += it }, onRefresh = {}, onPlay = {}, onDelete = { deleted += it }),
                )
            }
        } }
        // Nothing typed: the button does nothing.
        rule.onNode(hasTestTag(RD_TORRENTS_PULL_TAG)).performClick()
        assertTrue(pulled.isEmpty())
        val link = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"
        rule.onNode(hasTestTag(RD_TORRENTS_FIELD_TAG)).performTextInput(link)
        rule.onNode(hasTestTag(RD_TORRENTS_PULL_TAG)).performClick()
        assertEquals(listOf(link), pulled)

        // One OK on "Delete" deletes nothing; the second, on "Delete" again, does.
        rule.onNode(hasTestTag(torrentTag("A1"))).performClick()
        rule.onNode(hasTestTag(RD_TORRENTS_DELETE_TAG)).performClick()
        assertTrue(deleted.isEmpty())
        rule.onNode(hasTestTag(RD_TORRENTS_CONFIRM_TAG)).performClick()
        assertEquals(listOf("A1"), deleted)
    }

    @Test
    fun aBuildWithoutTheFeatureShowsNoWayIn() {
        rule.runOnUiThread { rule.activity.setContent {
            ScaryTheme {
                RealDebridScreen(
                    state = RealDebridRepository.State.SignedIn("viewer", true, "2027-01-01"),
                    files = RdFilesState.Ready(emptyList()),
                    actions = RealDebridActions(onSignIn = {}, onCancel = {}, onSignOut = {}, onRefresh = {}, onPlay = {}),
                )
            }
        } }
        rule.onNode(hasTestTag(REAL_DEBRID_TORRENTS_BUTTON_TAG)).assertDoesNotExist()
    }
}
