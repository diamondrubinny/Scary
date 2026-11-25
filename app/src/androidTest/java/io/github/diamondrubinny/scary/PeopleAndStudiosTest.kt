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
import io.github.diamondrubinny.scary.core.tmdb.CastMember
import io.github.diamondrubinny.scary.core.tmdb.ProductionCompany
import io.github.diamondrubinny.scary.ui.details.CAST_CARD_TAG
import io.github.diamondrubinny.scary.ui.details.CAST_INITIAL_TAG
import io.github.diamondrubinny.scary.ui.details.CAST_PHOTO_TAG
import io.github.diamondrubinny.scary.ui.details.COMPANIES_ROW_TAG
import io.github.diamondrubinny.scary.ui.details.CastCards
import io.github.diamondrubinny.scary.ui.details.CompaniesRow
import io.github.diamondrubinny.scary.ui.home.PersonUi
import io.github.diamondrubinny.scary.ui.person.PERSON_PHOTO_TAG
import io.github.diamondrubinny.scary.ui.person.PERSON_STATUS_TAG
import io.github.diamondrubinny.scary.ui.person.PersonScreen
import io.github.diamondrubinny.scary.ui.person.PersonWords
import io.github.diamondrubinny.scary.ui.theme.ScaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Actors with their photos and the companies of a film (docs/TMDB_PEOPLE_STUDIOS_PLAN.md), on their own: nothing is
 * asked of TMDB here, only what the rows show and open.
 */
@RunWith(AndroidJUnit4::class)
class PeopleAndStudiosTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    @Test
    fun actorsAreCardsReadOutWithTheirRoleAndCompaniesOpenTheirFilms() {
        val withPhoto = CastMember(819, "Edward Norton", "The Narrator", "/8nytsqL59SFJTVYVrN72k6qkGgJ.jpg")
        val without = CastMember(2, "Meat Loaf", "", null)
        val opened = mutableListOf<String>()
        rule.runOnUiThread { rule.activity.setContent {
            ScaryTheme {
                Column {
                    CastCards(listOf(withPhoto, without), onOpen = { opened += it.name })
                    CompaniesRow(listOf(ProductionCompany(3172, "Blumhouse Productions")), onOpen = { opened += "company:${it.id}" })
                }
            }
        } }
        rule.onAllNodes(hasTestTag(CAST_CARD_TAG)).assertCountEquals(2)
        // The card says who plays whom; the picture itself is decoration.
        rule.onNode(hasContentDescription(text(R.string.cast_plays, "Edward Norton", "The Narrator"))).assertExists()
        rule.onAllNodes(hasTestTag(CAST_PHOTO_TAG), useUnmergedTree = true).assertCountEquals(1)
        // No photo: the name's first letter stands in, and the card is named by the actor alone.
        rule.onAllNodes(hasTestTag(CAST_INITIAL_TAG), useUnmergedTree = true).assertCountEquals(1)
        rule.onNode(hasContentDescription("Meat Loaf")).performClick()
        rule.onNode(hasTestTag(COMPANIES_ROW_TAG)).assertExists()
        rule.onNode(hasText("Blumhouse Productions")).performClick()
        assertEquals(listOf("Meat Loaf", "company:3172"), opened)
    }

    @Test
    fun aCompanyScreenSaysWhoseFilmsTheseAre() {
        val film = Movie(id = "cm_tt1179904", imdbId = "tt1179904", title = "Paranormal Activity", year = 2007, genre = Genre.HORROR)
        val asked = mutableListOf<String>()
        rule.runOnUiThread { rule.activity.setContent {
            ScaryTheme {
                PersonScreen(
                    name = "3172",
                    person = PersonUi("3172", listOf(film), loading = false, fromTmdb = true),
                    onLoad = { asked += it },
                    onOpenMovie = {},
                    words = PersonWords(
                        title = text(R.string.company_title, "Blumhouse Productions"),
                        loading = text(R.string.company_loading),
                        note = { count -> androidx.compose.ui.res.stringResource(R.string.company_note, count.toString()) },
                        emptyTitle = text(R.string.company_empty_title),
                        emptyBody = text(R.string.company_empty_body),
                    ),
                )
            }
        } }
        rule.onNode(hasText(text(R.string.company_title, "Blumhouse Productions"))).assertExists()
        rule.onNode(hasTestTag(PERSON_STATUS_TAG) and hasText(text(R.string.company_note, "1"))).assertExists()
        // An actor's photo belongs to an actor's screen only.
        rule.onNode(hasTestTag(PERSON_PHOTO_TAG)).assertDoesNotExist()
        assertEquals(listOf("3172"), asked)
    }
}
