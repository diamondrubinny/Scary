// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.tmdb.CastMember
import io.github.diamondrubinny.scary.core.tmdb.ProductionCompany
import io.github.diamondrubinny.scary.ui.components.FocusSurface
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType

/** The cast row with photos, each actor's card in it, and the companies row (docs/TMDB_PEOPLE_STUDIOS_PLAN.md). */
const val CAST_CARD_TAG = "castCard"
const val CAST_PHOTO_TAG = "castPhoto"
const val CAST_INITIAL_TAG = "castInitial"
const val COMPANIES_ROW_TAG = "companiesRow"
const val COMPANY_NAME_TAG = "companyName"

private val PhotoSize = 72.dp
private val CardWidth = 112.dp

/**
 * A film's actors as cards: a round photo, the name, and who they play. OK on a card opens that actor's films. An
 * actor without a photo gets the first letter of their name in its place, so nothing rests on the picture; the photo
 * is decoration and is not read out, the card is ("name, as character").
 */
@Composable
fun CastCards(cast: List<CastMember>, onOpen: (CastMember) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier = modifier.testTag(CAST_ROW_TAG), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(cast, key = { "actor:${it.id}" }) { actor ->
            FocusSurface(
                onClick = { onOpen(actor) },
                modifier = Modifier.width(CardWidth).testTag(CAST_CARD_TAG),
                shape = RoundedCornerShape(4.dp),
                containerColor = ScaryColors.Crypt,
                focusedContainerColor = ScaryColors.Bone,
                contentDescription = if (actor.character.isBlank()) actor.name else stringResource(R.string.cast_plays, actor.name, actor.character),
            ) { focused ->
                Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp).width(CardWidth - 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val photo = actor.photoUrl
                    if (photo != null) {
                        AsyncImage(
                            model = photo,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(PhotoSize).clip(CircleShape).background(ScaryColors.Void).testTag(CAST_PHOTO_TAG),
                        )
                    } else {
                        Box(Modifier.size(PhotoSize).clip(CircleShape).background(ScaryColors.Void).testTag(CAST_INITIAL_TAG), contentAlignment = Alignment.Center) {
                            BasicText(actor.name.trim().take(1).uppercase(), style = ScaryType.Title.copy(color = ScaryColors.Candle))
                        }
                    }
                    BasicText(
                        actor.name,
                        style = ScaryType.Caption.copy(color = if (focused) ScaryColors.Void else ScaryColors.Bone, textAlign = TextAlign.Center, textDirection = TextDirection.Content),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (actor.character.isNotBlank()) {
                        BasicText(
                            actor.character,
                            style = ScaryType.Caption.copy(color = if (focused) ScaryColors.Void else ScaryColors.Ash, textAlign = TextAlign.Center, textDirection = TextDirection.Content),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** The companies that made the film: OK on one opens its horror films. */
@Composable
fun CompaniesRow(companies: List<ProductionCompany>, onOpen: (ProductionCompany) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.testTag(COMPANIES_ROW_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "label") { BasicText(stringResource(R.string.companies_label), style = ScaryType.Caption) }
        items(companies, key = { "company:${it.id}" }) { company ->
            ScaryButton(
                text = company.name,
                onClick = { onOpen(company) },
                quiet = true,
                modifier = Modifier.testTag(COMPANY_NAME_TAG),
            )
        }
    }
}
