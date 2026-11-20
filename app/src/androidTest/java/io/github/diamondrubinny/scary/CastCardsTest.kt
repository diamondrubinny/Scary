// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.tmdb.TmdbCastMember
import io.github.diamondrubinny.scary.ui.details.CAST_CARDS_TAG
import io.github.diamondrubinny.scary.ui.details.CAST_CARD_TAG
import io.github.diamondrubinny.scary.ui.details.CastCards
import io.github.diamondrubinny.scary.ui.home.PersonUi
import io.github.diamondrubinny.scary.ui.person.PERSON_PHOTO_TAG
import io.github.diamondrubinny.scary.ui.person.PERSON_SCREEN_TAG
import io.github.diamondrubinny.scary.ui.person.PersonScreen
import io.github.diamondrubinny.scary.ui.theme.ScaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The cast as TMDB lists it (docs/UPGRADE_0.7_PLAN.md), composed on its own with made-up data (no TMDB key in a
 * test): each actor is one button that says their name and role, and OK passes TMDB's id of them on; the actor's
 * screen shows their photo's place beside the heading.
 */
@RunWith(AndroidJUnit4::class)
class CastCardsTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val cast = listOf(
        TmdbCastMember(9221, "Donald Pleasence", "Dr. Sam Loomis", "https://image.tmdb.org/t/p/w185/aBcDe12345.jpg"),
        TmdbCastMember(8944, "Jamie Lee Curtis", "Laurie Strode", null),
        TmdbCastMember(7, "Nick Castle", "", null),
    )

    @Test
    fun eachActorIsOneButtonWithTheirRoleAndOkPassesTheirIdOn() {
        val opened = mutableListOf<Long>()
        val withRole = rule.activity.getString(R.string.cast_card_label, "Jamie Lee Curtis", "Laurie Strode")
        // In place of the app's own screen: the rule's setContent refuses an activity that already has content.
        rule.runOnUiThread { rule.activity.setContent {
            ScaryTheme {
                Column { CastCards(cast, onOpen = { opened += it.personId }) }
            }
        } }
        rule.waitUntil(WAIT_MS) { rule.onAllNodes(hasTestTag(CAST_CARD_TAG)).fetchSemanticsNodes().size == 3 }
        rule.onNode(hasTestTag(CAST_CARDS_TAG)).assertExists()
        // One button says both the name and the role; an actor with no role listed is just their name.
        rule.onNode(hasTestTag(CAST_CARD_TAG) and hasContentDescription(withRole)).assertExists()
        rule.onNode(hasTestTag(CAST_CARD_TAG) and hasContentDescription("Nick Castle")).assertExists()
        // The name and the role under the photo are not read a second time.
        rule.onAllNodes(hasText("Laurie Strode")).assertCountEquals(0)
        rule.onNode(hasTestTag(CAST_CARD_TAG) and hasContentDescription(withRole)).performClick()
        rule.onNode(hasTestTag(CAST_CARD_TAG) and hasContentDescription("Nick Castle")).performClick()
        rule.runOnIdle { assertEquals(listOf(8944L, 7L), opened) }
    }

    @Test
    fun anActorsScreenShowsTheirPhotoOnlyWhenTmdbGaveOne() {
        val film = Movie(id = "halloween", imdbId = "tt0077651", title = "Halloween", year = 1978, genre = Genre.HORROR)
        val asked = mutableListOf<String>()
        var photo: String? = "https://image.tmdb.org/t/p/w185/aBcDe12345.jpg"
        fun show() = rule.runOnUiThread { rule.activity.setContent {
            ScaryTheme {
                PersonScreen(
                    name = "Donald Pleasence",
                    person = PersonUi("Donald Pleasence", listOf(film), loading = false, fromTmdb = true, personId = 9221),
                    onLoad = { asked += it },
                    onOpenMovie = {},
                    photo = photo,
                )
            }
        } }
        show()
        rule.waitUntil(WAIT_MS) { rule.onAllNodes(hasTestTag(PERSON_SCREEN_TAG)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasTestTag(PERSON_PHOTO_TAG)).assertExists()
        rule.onNode(hasContentDescription("Halloween", substring = true)).assertExists()
        rule.runOnIdle { assertEquals(listOf("Donald Pleasence"), asked) }
        // Opened from a plain name (no TMDB cast): no photo, and nothing stands in its place.
        photo = null
        show()
        rule.waitUntil(WAIT_MS) { rule.onAllNodes(hasTestTag(PERSON_PHOTO_TAG)).fetchSemanticsNodes().isEmpty() }
        rule.onNode(hasTestTag(PERSON_SCREEN_TAG)).assertExists()
    }

    private companion object {
        const val WAIT_MS = 10_000L
    }
}
