// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary

import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.diamondrubinny.scary.core.realdebrid.Magnets
import io.github.diamondrubinny.scary.core.sources.Releases
import io.github.diamondrubinny.scary.data.SourcesRepository.SaveResult
import io.github.diamondrubinny.scary.ui.home.RdPullState
import io.github.diamondrubinny.scary.ui.home.RdSearchState
import io.github.diamondrubinny.scary.data.SourcesRepository
import io.github.diamondrubinny.scary.ui.realdebrid.RD_SOURCES_CHECK_RESULT_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.RD_SOURCES_CHECK_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.RD_SOURCES_BEST_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.RD_SOURCES_CONFIRM_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.RD_SOURCES_FIELD_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.RD_SOURCES_INDEXER_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.RD_SOURCES_PULL_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.RD_SOURCES_SEARCH_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.RdSourcesActions
import io.github.diamondrubinny.scary.ui.realdebrid.RdSourcesScreen
import io.github.diamondrubinny.scary.ui.realdebrid.filterTag
import io.github.diamondrubinny.scary.ui.realdebrid.releaseTag
import io.github.diamondrubinny.scary.ui.theme.ScaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Search your indexer" (the sources feature), composed on its own with fake answers: without an indexer there is only
 * the way to set one up; with one, the words are passed on, the filter narrows the list, and nothing is added until
 * the viewer says yes to one release, "Suggest the best" included.
 */
@RunWith(AndroidJUnit4::class)
class RdSourcesTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun release(title: String, hash: String, seeders: Int) =
        Releases.Candidate(title, Magnets.Magnet(hash), "Site", 4_000_000_000, seeders, Releases.tags(title))

    private val bluRay = release("Night.of.the.Living.Dead.1968.1080p.BluRay", "a".repeat(40), 50)
    private val small = release("Night.of.the.Living.Dead.1968.720p.WEB-DL", "b".repeat(40), 900)

    private fun show(host: String?, search: RdSearchState, searched: MutableList<String>, pulled: MutableList<Releases.Candidate>) {
        rule.runOnUiThread { rule.activity.setContent {
            ScaryTheme {
                RdSourcesScreen(
                    search = search,
                    pull = RdPullState.Idle,
                    indexerHost = host,
                    actions = RdSourcesActions(onSearch = { searched += it }, onPull = { pulled += it }, onSaveIndexer = { _, _ -> SaveResult.SAVED }, onRemoveIndexer = {}),
                )
            }
        } }
    }

    @Test
    fun checkingTheSourceSaysWhetherItAnswersAndHowFast() {
        var asked = 0
        rule.runOnUiThread { rule.activity.setContent {
            ScaryTheme {
                RdSourcesScreen(
                    search = RdSearchState.Idle,
                    pull = RdPullState.Idle,
                    indexerHost = "indexer.example",
                    actions = RdSourcesActions(
                        onSearch = {}, onPull = {}, onSaveIndexer = { _, _ -> SaveResult.SAVED }, onRemoveIndexer = {},
                        onCheckSource = { asked++; SourcesRepository.Check(SourcesRepository.Health.OK, 240) },
                    ),
                )
            }
        } }
        rule.onNode(hasTestTag(RD_SOURCES_INDEXER_TAG)).performClick()
        // Nothing is asked until the viewer presses.
        assertEquals(0, asked)
        rule.onNode(hasTestTag(RD_SOURCES_CHECK_RESULT_TAG)).assertDoesNotExist()
        rule.onNode(hasTestTag(RD_SOURCES_CHECK_TAG)).performClick()
        rule.waitForIdle()
        assertEquals(1, asked)
        rule.onNode(hasText(rule.activity.getString(R.string.rd_sources_check_ok, "240"))).assertExists()
    }

    @Test
    fun withoutAnIndexerThereIsOnlyTheWayToSetOneUp() {
        show(null, RdSearchState.Idle, mutableListOf(), mutableListOf())
        rule.onNode(hasTestTag(RD_SOURCES_INDEXER_TAG)).assertExists()
        rule.onNode(hasTestTag(RD_SOURCES_FIELD_TAG)).assertDoesNotExist()
        rule.onNode(hasTestTag(RD_SOURCES_SEARCH_TAG)).assertDoesNotExist()
    }

    @Test
    fun nothingIsAddedUntilTheViewerSaysYes() {
        val searched = mutableListOf<String>()
        val pulled = mutableListOf<Releases.Candidate>()
        show("indexer.example", RdSearchState.Ready(listOf(small, bluRay)), searched, pulled)
        rule.onNode(hasTestTag(RD_SOURCES_FIELD_TAG)).performTextInput("night of the living dead")
        rule.onNode(hasTestTag(RD_SOURCES_SEARCH_TAG)).performClick()
        assertEquals(listOf("night of the living dead"), searched)

        // The filter narrows the list without asking the indexer again.
        rule.onNode(hasTestTag(filterTag(Releases.Filter.HD))).performClick()
        rule.onNode(hasTestTag(releaseTag(small.magnet.infoHash))).assertExists()
        rule.onNode(hasTestTag(releaseTag(bluRay.magnet.infoHash))).assertDoesNotExist()
        assertEquals(1, searched.size)

        // "Suggest the best" only offers: the window opens, and nothing is added before "Add and play".
        rule.onNode(hasTestTag(RD_SOURCES_BEST_TAG)).performClick()
        rule.onNode(hasTestTag(RD_SOURCES_CONFIRM_TAG)).assertExists()
        assertTrue(pulled.isEmpty())
        rule.onNode(hasTestTag(RD_SOURCES_PULL_TAG)).performClick()
        assertEquals(listOf(bluRay), pulled)
    }
}
