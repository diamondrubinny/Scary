// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary

import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.diamondrubinny.scary.core.profiles.Profiles
import io.github.diamondrubinny.scary.core.profiles.Profiles.Profile
import io.github.diamondrubinny.scary.data.SettingsRepository
import io.github.diamondrubinny.scary.ui.components.PROFILE_NAV_TAG
import io.github.diamondrubinny.scary.ui.home.HOME_ROWS_TAG
import io.github.diamondrubinny.scary.ui.home.ProfileChange
import io.github.diamondrubinny.scary.ui.profiles.PROFILES_ADD_TAG
import io.github.diamondrubinny.scary.ui.profiles.PROFILE_GENTLE_TAG
import io.github.diamondrubinny.scary.ui.profiles.PROFILE_NAME_TAG
import io.github.diamondrubinny.scary.ui.profiles.PROFILE_REMOVE_CONFIRM_TAG
import io.github.diamondrubinny.scary.ui.profiles.PROFILE_REMOVE_TAG
import io.github.diamondrubinny.scary.ui.profiles.PROFILE_SAVE_TAG
import io.github.diamondrubinny.scary.ui.profiles.ProfilesScreen
import io.github.diamondrubinny.scary.ui.profiles.profileTag
import io.github.diamondrubinny.scary.ui.theme.ScaryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** "Who is watching?": the screen on its own, and on a real store that two viewers keep two lists. */
@RunWith(AndroidJUnit4::class)
class ProfilesTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun aProfileIsChosenAddedAndRemovedOnlyAfterASecondYes() {
        val chosen = mutableListOf<String>()
        val changes = mutableListOf<ProfileChange>()
        rule.runOnUiThread { rule.activity.setContent {
            ScaryTheme {
                ProfilesScreen(
                    profiles = listOf(Profile("p1", "Mom"), Profile("p2", "Kid", 1, gentle = true)),
                    activeId = "p1",
                    pinSet = false,
                    onChoose = { chosen += it },
                    onChange = { changes += it },
                )
            }
        } }
        rule.onNode(hasTestTag(profileTag("p2"))).performClick()
        assertEquals(listOf("p2"), chosen)

        // A new profile needs a name; with one, it is passed on as typed, gentle when asked.
        rule.onNode(hasTestTag(PROFILES_ADD_TAG)).performClick()
        rule.onNode(hasTestTag(PROFILE_SAVE_TAG)).performClick()
        assertTrue(changes.isEmpty())
        rule.onNode(hasTestTag(PROFILE_NAME_TAG)).performTextInput("Dana")
        rule.onNode(hasTestTag(PROFILE_GENTLE_TAG)).performClick()
        // No PIN is set: the window says nothing holds anyone in a gentle profile.
        rule.onNode(hasText(rule.activity.getString(R.string.profile_gentle_no_pin))).assertExists()
        rule.onNode(hasTestTag(PROFILE_SAVE_TAG)).performClick()
        assertEquals(ProfileChange.Add("Dana", 2, gentle = true), changes.single())

        // Holding OK on a profile: "Remove" only asks, and the second "Remove" removes.
        rule.onNode(hasTestTag(profileTag("p2"))).performTouchInput { longClick() }
        rule.onNode(hasTestTag(PROFILE_REMOVE_TAG)).performClick()
        assertEquals(1, changes.size)
        rule.onNode(hasTestTag(PROFILE_REMOVE_CONFIRM_TAG)).performClick()
        assertEquals(ProfileChange.Remove("p2"), changes.last())
        // The first profile is the TV's own lists: it cannot be removed.
        rule.onNode(hasTestTag(profileTag("p1"))).performTouchInput { longClick() }
        rule.onNode(hasTestTag(PROFILE_REMOVE_TAG)).assertDoesNotExist()
    }

    @Test
    fun twoViewersKeepTwoListsAndRemovingOneLeavesTheOther() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = SettingsRepository(context)
        runBlocking {
            val before = settings.prefs.first()
            val wasInList = "cm_tt0081505" in before.myListIds
            val second = settings.addProfile("Test viewer", 1, gentle = true)!!
            try {
                // With one viewer the top bar names nobody; with two it names the one on screen.
                rule.waitUntil(20_000) { rule.onAllNodes(hasTestTag(HOME_ROWS_TAG)).fetchSemanticsNodes().isNotEmpty() }
                if (!wasInList) settings.toggleMyList("cm_tt0081505")
                settings.setActiveProfile(second.id)
                val asSecond = settings.prefs.first()
                assertEquals(second.id, asSecond.activeProfileId)
                assertTrue("A new viewer starts with empty lists", asSecond.myListIds.isEmpty())
                assertTrue("A gentle profile is family night for whoever is in it", asSecond.familyNight)
                assertFalse(asSecond.isFirstProfile)
                settings.toggleMyList("cm_tt7784604")
                assertEquals(listOf("cm_tt7784604"), settings.prefs.first().myListIds)
                rule.waitUntil(20_000) { rule.onAllNodes(hasTestTag(PROFILE_NAV_TAG)).fetchSemanticsNodes().isNotEmpty() }

                // Back to the first viewer: their list is as they left it, and the other's film is not in it.
                settings.setActiveProfile(Profiles.FIRST)
                val asFirst = settings.prefs.first()
                assertTrue("cm_tt0081505" in asFirst.myListIds)
                assertFalse("cm_tt7784604" in asFirst.myListIds)
                assertEquals(before.familyNight, asFirst.familyNight)
            } finally {
                settings.setActiveProfile(Profiles.FIRST)
                settings.removeProfile(second.id)
                if (!wasInList && "cm_tt0081505" in settings.prefs.first().myListIds) settings.toggleMyList("cm_tt0081505")
            }
            // Removed with its lists: a profile made again under the same id starts empty.
            val again = settings.addProfile("Test viewer", 1, gentle = false)!!
            try {
                assertEquals(second.id, again.id)
                settings.setActiveProfile(again.id)
                assertTrue(settings.prefs.first().myListIds.isEmpty())
            } finally {
                settings.setActiveProfile(Profiles.FIRST)
                settings.removeProfile(again.id)
            }
            assertEquals(before.profiles, settings.prefs.first().profiles)
        }
    }
}
