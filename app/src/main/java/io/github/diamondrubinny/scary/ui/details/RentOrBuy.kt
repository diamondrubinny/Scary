// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.brand.Donation
import io.github.diamondrubinny.scary.core.tmdb.OfferKind
import io.github.diamondrubinny.scary.ui.components.FocusSurface
import io.github.diamondrubinny.scary.ui.components.QrImage
import io.github.diamondrubinny.scary.ui.home.OfferAction
import io.github.diamondrubinny.scary.ui.home.ProviderOffer
import io.github.diamondrubinny.scary.ui.home.RentOrBuyState
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.theme.mixedLanguage

/**
 * "Didn't find it? Rent or buy": the stores and services that offer the movie in the viewer's country, from TMDB
 * (data by JustWatch). A store whose app is here searches in it, a known one opens its store page, and the QR code
 * leads to every store's link on a phone. SCARY sells nothing, and the note says so. "Search the whole TV" is already
 * offered just above, so it is not repeated here.
 */
@Composable
fun RentOrBuy(
    state: RentOrBuyState,
    countryName: (String) -> String,
    onOffer: (ProviderOffer) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == RentOrBuyState.Hidden) return
    Column(modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val country = when (state) {
            is RentOrBuyState.Loading -> countryName(state.country)
            is RentOrBuyState.Ready -> countryName(state.country)
            else -> null
        }
        BasicText(
            text = if (country != null) stringResource(R.string.rent_buy_title, country) else stringResource(R.string.rent_buy_title_plain),
            style = ScaryType.Eyebrow,
            modifier = Modifier.semantics { heading() },
        )
        when (state) {
            RentOrBuyState.NeedsKey -> BasicText(stringResource(R.string.rent_buy_needs_key), style = ScaryType.Caption)
            RentOrBuyState.NoCountry -> BasicText(stringResource(R.string.rent_buy_no_country), style = ScaryType.Caption)
            is RentOrBuyState.Loading -> BasicText(stringResource(R.string.rent_buy_loading, country.orEmpty()), style = ScaryType.Caption)
            is RentOrBuyState.Ready -> Ready(state, country.orEmpty(), onOffer)
            RentOrBuyState.Hidden -> Unit
        }
    }
}

@Composable
private fun Ready(state: RentOrBuyState.Ready, country: String, onOffer: (ProviderOffer) -> Unit) {
    if (state.rentOrBuy.isEmpty() && state.included.isEmpty()) {
        BasicText(stringResource(R.string.rent_buy_none, country), style = ScaryType.Caption)
        return
    }
    if (state.rentOrBuy.isNotEmpty()) OfferRow(state.rentOrBuy, onOffer)
    if (state.included.isNotEmpty()) {
        BasicText(stringResource(R.string.rent_buy_included), style = ScaryType.Caption.copy(color = ScaryColors.Bone))
        OfferRow(state.included, onOffer)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        state.link?.let { link ->
            QrImage(
                code = remember(link) { Donation.qr(link) },
                size = 96.dp,
                description = stringResource(R.string.rent_buy_qr_description, country),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.widthIn(max = 520.dp)) {
            if (state.link != null) BasicText(stringResource(R.string.rent_buy_qr), style = ScaryType.Caption.copy(color = ScaryColors.Bone))
            // TMDB asks for JustWatch to be credited wherever its availability data is shown.
            BasicText(stringResource(R.string.rent_buy_credit), style = ScaryType.Caption.mixedLanguage())
            BasicText(stringResource(R.string.rent_buy_note), style = ScaryType.Caption)
        }
    }
}

@Composable
private fun OfferRow(offers: List<ProviderOffer>, onOffer: (ProviderOffer) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        // Room for the focused card to grow.
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
    ) {
        items(offers, key = { it.provider.id }) { offer -> OfferCard(offer, onOffer) }
    }
}

@Composable
private fun OfferCard(offer: ProviderOffer, onOffer: (ProviderOffer) -> Unit) {
    val kinds = kindsLabel(offer.provider.kinds)
    val name = offer.provider.name
    val description = when (offer.action) {
        is OfferAction.Search -> stringResource(R.string.offer_search_cd, name, kinds)
        is OfferAction.Store -> stringResource(R.string.offer_store_cd, name, kinds)
        OfferAction.None -> stringResource(R.string.offer_info_cd, name, kinds)
    }
    val content: @Composable (Boolean) -> Unit = { focused ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(ScaryColors.Crypt)) {
                offer.provider.logoUrl?.let { logo ->
                    // The name is always written beside it; the logo only helps recognise the store.
                    AsyncImage(model = logo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(32.dp))
                }
            }
            Column {
                val textColor = if (focused) ScaryColors.Void else ScaryColors.Bone
                BasicText(
                    name,
                    style = ScaryType.Label.copy(color = textColor).mixedLanguage(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 200.dp),
                )
                BasicText(kinds, style = ScaryType.Caption.copy(color = if (focused) ScaryColors.Void else ScaryColors.Ash), maxLines = 1)
            }
        }
    }
    if (offer.action == OfferAction.None) {
        // Nothing to open on this TV: not a button, so the remote never lands on a press that does nothing.
        Box(
            Modifier
                .background(ScaryColors.Void.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .semantics(mergeDescendants = true) { contentDescription = description },
        ) { content(false) }
    } else {
        FocusSurface(
            onClick = { onOffer(offer) },
            shape = RoundedCornerShape(6.dp),
            focusedScale = 1.06f,
            containerColor = ScaryColors.Void.copy(alpha = 0.6f),
            focusedContainerColor = ScaryColors.Bone,
            borderColor = ScaryColors.Bone.copy(alpha = 0.25f),
            focusedBorderColor = ScaryColors.Bone,
            contentDescription = description,
        ) { focused -> content(focused) }
    }
}

@Composable
private fun kindsLabel(kinds: Set<OfferKind>): String = when {
    OfferKind.RENT in kinds && OfferKind.BUY in kinds -> stringResource(R.string.offer_rent_buy)
    OfferKind.RENT in kinds -> stringResource(R.string.offer_rent)
    OfferKind.BUY in kinds -> stringResource(R.string.offer_buy)
    else -> stringResource(R.string.offer_included)
}
