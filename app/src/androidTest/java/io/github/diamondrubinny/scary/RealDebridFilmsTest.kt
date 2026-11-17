// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.realdebrid.RealDebrid
import io.github.diamondrubinny.scary.data.RealDebridRepository
import io.github.diamondrubinny.scary.ui.home.RdFilesState
import io.github.diamondrubinny.scary.ui.realdebrid.REAL_DEBRID_FILM_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.REAL_DEBRID_GUESS_AGAIN_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.REAL_DEBRID_NOT_A_FILM_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.RealDebridActions
import io.github.diamondrubinny.scary.ui.realdebrid.RealDebridScreen
import io.github.diamondrubinny.scary.ui.realdebrid.filmTag
import io.github.diamondrubinny.scary.ui.theme.ScaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Real-Debrid screen's file list, composed on its own with fake data: each file shows the film SCARY took it for,
 * and holding OK on a file opens "Which film is this file?", where the viewer's word is passed on.
 */
@RunWith(AndroidJUnit4::class)
class RealDebridFilmsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun film(id: String, title: String, year: Int) =
        Movie(id = id, imdbId = "tt0000001", title = title, year = year, genre = Genre.HORROR)

    @Test
    fun aFileShowsItsFilmAndTheViewerCanCorrectIt() {
        val shining = film("shining", "The Shining", 1980)
        val other = film("shining97", "The Shining", 1997)
        val file = RealDebrid.RdFile("d:1", "The.Shining.mkv", 4_000_000_000, "https://real-debrid.com/d/1")
        val said = mutableListOf<Pair<String, String?>>()
        rule.setContent {
            ScaryTheme {
                RealDebridScreen(
                    state = RealDebridRepository.State.SignedIn("viewer", true, "2027-01-01"),
                    files = RdFilesState.Ready(listOf(file)),
                    actions = RealDebridActions(
                        onSignIn = {},
                        onCancel = {},
                        onSignOut = {},
                        onRefresh = {},
                        onPlay = {},
                        onCandidates = { listOf(shining, other) },
                        onSetFilm = { f, id -> said += f.key to id },
                    ),
                    fileFilms = mapOf(file.key to shining),
                )
            }
        }
        // The film SCARY took the file for, under its name.
        rule.onNode(hasTestTag(filmTag("shining"))).assertExists()
        rule.onNode(hasText("The Shining (1980)")).assertExists()

        // Holding OK on the file: the window, with the films it may be.
        rule.onNode(hasTestTag(filmTag("shining"))).performTouchInput { longClick() }
        rule.waitUntil(WAIT_MS) { rule.onAllNodes(hasTestTag(REAL_DEBRID_FILM_TAG)).fetchSemanticsNodes().isNotEmpty() }
        val inWindow = hasAnyAncestor(hasTestTag(REAL_DEBRID_FILM_TAG))
        rule.waitUntil(WAIT_MS) { rule.onAllNodes(hasTestTag(filmTag("shining97")) and inWindow).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasTestTag(filmTag("shining97")) and inWindow).performClick()
        assertEquals(listOf("d:1" to "shining97"), said)

        // "Not a film from the library", and "Let SCARY guess again".
        rule.onNode(hasTestTag(filmTag("shining"))).performTouchInput { longClick() }
        rule.waitUntil(WAIT_MS) { rule.onAllNodes(hasTestTag(REAL_DEBRID_NOT_A_FILM_TAG)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasTestTag(REAL_DEBRID_NOT_A_FILM_TAG)).performClick()
        rule.onNode(hasTestTag(filmTag("shining"))).performTouchInput { longClick() }
        rule.waitUntil(WAIT_MS) { rule.onAllNodes(hasTestTag(REAL_DEBRID_GUESS_AGAIN_TAG)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasTestTag(REAL_DEBRID_GUESS_AGAIN_TAG)).performClick()
        assertEquals(listOf<Pair<String, String?>>("d:1" to "shining97", "d:1" to "", "d:1" to null), said)
    }

    private companion object {
        const val WAIT_MS = 10_000L
    }
}
