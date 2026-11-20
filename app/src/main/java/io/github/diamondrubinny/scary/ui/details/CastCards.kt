// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.tmdb.TmdbCastMember
import io.github.diamondrubinny.scary.ui.components.FocusSurface
import io.github.diamondrubinny.scary.ui.components.POSTER_DIM
import io.github.diamondrubinny.scary.ui.components.rememberArtwork
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.theme.mixedLanguage

/** The cast as TMDB lists it on a film's page (photos and roles), and each actor's card in it. */
const val CAST_CARDS_TAG = "castCards"
const val CAST_CARD_TAG = "castCard"

/**
 * The film's cast with faces and roles, as TMDB lists it (docs/UPGRADE_0.7_PLAN.md): a heading and a row of cards.
 * OK on a card opens that actor's films. Each card is one button, read as "<actor>, as <role>".
 */
@Composable
fun CastCards(cast: List<TmdbCastMember>, onOpen: (TmdbCastMember) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        BasicText(
            text = stringResource(R.string.row_cast),
            style = ScaryType.RowTitle,
            modifier = Modifier.padding(start = SidePadding, end = SidePadding, top = 20.dp).semantics { heading() },
        )
        LazyRow(
            modifier = Modifier.testTag(CAST_CARDS_TAG),
            contentPadding = PaddingValues(horizontal = SidePadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(cast, key = { "cast:" + it.personId }) { member ->
                CastCard(member, onClick = { onOpen(member) })
            }
        }
    }
}

@Composable
private fun CastCard(member: TmdbCastMember, onClick: () -> Unit, width: Dp = 84.dp) {
    val label = if (member.character.isBlank()) member.name else stringResource(R.string.cast_card_label, member.name, member.character)
    Column(Modifier.width(width)) {
        FocusSurface(
            onClick = onClick,
            modifier = Modifier.width(width).aspectRatio(2f / 3f).testTag(CAST_CARD_TAG),
            contentDescription = label,
            focusedScale = 1.06f,
        ) { focused ->
            PersonPhoto(member.name, member.photo, Modifier.fillMaxSize())
            // Noir: like the posters, the card in focus is lit and the others are dimmed (not with a finger).
            val lit = focused || LocalInputModeManager.current.inputMode != InputMode.Keyboard
            if (!lit) Box(Modifier.fillMaxSize().background(ScaryColors.Void.copy(alpha = POSTER_DIM)))
        }
        // The button above already says both; a screen reader does not read them twice.
        BasicText(
            text = member.name,
            style = ScaryType.Caption.copy(color = ScaryColors.Bone).mixedLanguage(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp).clearAndSetSemantics { },
        )
        if (member.character.isNotBlank()) {
            BasicText(
                text = member.character,
                style = ScaryType.Caption.mixedLanguage(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/**
 * An actor's photo from TMDB's image host; their initials on a dark block while it loads, when there is none, or
 * when it cannot be loaded. Decoration for a screen reader: the name is said by whatever holds this.
 */
@Composable
fun PersonPhoto(name: String, photo: String?, modifier: Modifier = Modifier) {
    var shown by remember(photo) { mutableStateOf(false) }
    Box(modifier.background(ScaryColors.Crypt), contentAlignment = Alignment.Center) {
        if (!shown) {
            BasicText(
                text = initialsOf(name),
                style = ScaryType.RowTitle.copy(color = ScaryColors.Ash),
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        rememberArtwork(photo)?.let { request ->
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { shown = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** "JC" for "Jamie Lee Curtis": the first letter of the first and of the last word, as written. */
internal fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return ""
    fun first(word: String) = String(Character.toChars(word.codePointAt(0))).uppercase()
    return if (words.size == 1) first(words[0]) else first(words.first()) + first(words.last())
}
