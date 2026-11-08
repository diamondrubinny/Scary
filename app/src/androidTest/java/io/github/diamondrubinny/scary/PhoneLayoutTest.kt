// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.diamondrubinny.scary.ui.components.TOUCH_TARGET
import io.github.diamondrubinny.scary.ui.home.HOME_ROWS_TAG
import io.github.diamondrubinny.scary.ui.home.HomeViewModel
import io.github.diamondrubinny.scary.ui.settings.LANGUAGE_ROW_TAG
import io.github.diamondrubinny.scary.ui.settings.PHONE_REMOTE_ROW_TAG
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SCARY on a phone or a tablet (not a TV): every screen fits the width, buttons are big enough for a finger, the
 * TV-only options are gone, and the app has an icon in the app drawer. Skipped on a TV (CI runs it on a phone
 * emulator, upright and as a tablet).
 */
@RunWith(AndroidJUnit4::class)
class PhoneLayoutTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun notATv() {
        val uiMode = rule.activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        assumeFalse("A TV keeps the TV layout", uiMode == Configuration.UI_MODE_TYPE_TELEVISION)
    }

    private fun text(id: Int): String = rule.activity.getString(id)

    private fun waitFor(matcher: SemanticsMatcher) {
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }

    /**
     * Nothing sticks out past either side of the screen. Rows that scroll sideways (posters, the top bar's icons) may
     * hold more than fits, so only the row itself is measured, not what is inside it.
     */
    private fun assertFitsTheWidth(screen: String) {
        rule.waitForIdle()
        val root = rule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val width = root.size.width.toFloat()
        val outside = mutableListOf<String>()
        fun visit(node: SemanticsNode) {
            val bounds = node.boundsInRoot
            if (bounds.width > 0f && (bounds.left < -SLACK_PX || bounds.right > width + SLACK_PX)) {
                outside += "${node.config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: node.config.getOrNull(SemanticsProperties.TestTag) ?: node.id}" +
                    " [${bounds.left.toInt()}..${bounds.right.toInt()} of ${width.toInt()}]"
            }
            if (SemanticsProperties.HorizontalScrollAxisRange !in node.config) node.children.forEach(::visit)
        }
        visit(root)
        assertTrue("$screen: these stick out past the screen's sides: ${outside.take(MAX_LISTED)}", outside.isEmpty())
    }

    @Test
    fun theHomeScreenFitsAndItsButtonsTakeAFinger() {
        waitFor(hasTestTag(HOME_ROWS_TAG))
        assertFitsTheWidth("Home")
        rule.onNodeWithContentDescription(text(R.string.settings_title))
            .assertHeightIsAtLeast(TOUCH_TARGET)
            .assertWidthIsAtLeast(TOUCH_TARGET)
        rule.onNodeWithContentDescription(text(R.string.search_title)).assertHeightIsAtLeast(TOUCH_TARGET)
    }

    @Test
    fun aMoviePageFitsWithItsButtonsBelowThePoster() {
        waitFor(hasTestTag(HOME_ROWS_TAG))
        val container = (rule.activity.application as ScaryApplication).container
        val viewModel = ViewModelProvider(rule.activity, HomeViewModel.factory(container))[HomeViewModel::class.java]
        rule.runOnUiThread { viewModel.openDetails("hereditary") }
        waitFor(hasText(text(R.string.action_watch_stremio)))
        assertFitsTheWidth("Movie page")
        val watch = rule.onNode(hasText(text(R.string.action_watch_stremio)), useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("Watch is reachable without scrolling sideways", watch.boundsInRoot.right <= rule.onRoot().fetchSemanticsNode().size.width)
        rule.onNode(hasText(text(R.string.action_watch_stremio))).assertHeightIsAtLeast(TOUCH_TARGET)
    }

    @Test
    fun settingsFitAndOfferNoTvOnlyOptions() {
        waitFor(hasTestTag(HOME_ROWS_TAG))
        rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        waitFor(hasTestTag(LANGUAGE_ROW_TAG))
        assertFitsTheWidth("Settings")
        assertTrue("No 'set as home screen' on a phone", rule.onAllNodes(hasText(text(R.string.settings_default_home), substring = true)).fetchSemanticsNodes().isEmpty())
        assertTrue("No phone remote on a phone", rule.onAllNodes(hasTestTag(PHONE_REMOTE_ROW_TAG)).fetchSemanticsNodes().isEmpty())
    }

    /** A finger scrolling the home screen, even one that starts on an app tile, scrolls and opens nothing. */
    @Test
    fun scrollingHomeOpensNothing() {
        waitFor(hasTestTag(HOME_ROWS_TAG))
        waitFor(hasText(text(R.string.row_apps)))
        val rows = rule.onNode(hasTestTag(HOME_ROWS_TAG))
        val apps = rule.onNode(hasText(text(R.string.row_apps))).fetchSemanticsNode().boundsInRoot
        val top = rows.fetchSemanticsNode().boundsInRoot.top
        rows.performTouchInput {
            // From just below the "My apps" title (on its tiles) up towards the top.
            val startY = (apps.bottom - top + TILE_OFFSET_PX).coerceIn(0f, height - 1f)
            swipe(Offset(centerX, startY), Offset(centerX, startY / 4f), durationMillis = 400)
        }
        rule.waitForIdle()
        Thread.sleep(SETTLE_MS)
        assertTrue(
            "A scroll opened something: SCARY is ${rule.activity.lifecycle.currentState}",
            rule.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
        )
        assertTrue("Still on the home screen", rule.onAllNodes(hasTestTag(HOME_ROWS_TAG)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun theAppHasAnIconInTheAppDrawer() {
        val drawer = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(rule.activity.packageName)
        val found = rule.activity.packageManager.queryIntentActivities(drawer, 0)
        assertTrue("SCARY is listed in the app drawer", found.isNotEmpty())
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
        const val SLACK_PX = 1f
        const val MAX_LISTED = 8
        const val TILE_OFFSET_PX = 60f
        const val SETTLE_MS = 1_500L
    }
}
