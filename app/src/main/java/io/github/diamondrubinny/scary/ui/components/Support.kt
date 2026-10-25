// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.brand.Donation
import io.github.diamondrubinny.scary.core.brand.QrCode
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.theme.mixedLanguage

/**
 * Where the movie information and pictures come from: TMDB's own logo (unaltered, and much smaller than SCARY's, as
 * TMDB's API terms ask), the sources by name, and TMDB's required notice.
 */
@Composable
fun DataCredit(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(
                painter = painterResource(R.drawable.tmdb_logo),
                contentDescription = null,
                // TMDB's own proportions: their rules forbid stretching the logo.
                modifier = Modifier.height(12.dp).aspectRatio(TMDB_LOGO_RATIO),
            )
            BasicText(stringResource(R.string.data_credit), style = ScaryType.Caption.mixedLanguage())
        }
        // TMDB's wording, in English everywhere; keep its punctuation in place inside a right-to-left layout.
        BasicText(stringResource(R.string.tmdb_attribution), style = ScaryType.Caption.mixedLanguage())
    }
}

/** The donation code with a line on what it is: scanned with a phone, it opens PayPal there, never on the TV. */
@Composable
fun SupportScary(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        QrImage(
            code = remember { Donation.qr() },
            size = 150.dp,
            description = stringResource(R.string.donate_qr_description),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(stringResource(R.string.donate_title), style = ScaryType.RowTitle)
            BasicText(stringResource(R.string.donate_body), style = ScaryType.Body)
            BasicText(Donation.SHORT_URL, style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle).mixedLanguage())
        }
    }
}

/**
 * The small donation code that sits under the logo wherever it is shown. Small enough not to crowd the screen; a
 * phone reads it from about an arm's length, and the large code is in Settings, under About.
 */
@Composable
fun DonateMini(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QrImage(
            code = remember { Donation.qr() },
            size = MINI_QR_SIZE,
            description = stringResource(R.string.donate_qr_description),
        )
        BasicText(stringResource(R.string.donate_mini), style = ScaryType.Caption)
    }
}

private val MINI_QR_SIZE = 64.dp

/**
 * Dark modules on white with the quiet zone around them: phone cameras read that reliably, where a light code on
 * SCARY's dark background would fail on many of them.
 */
@Composable
fun QrImage(code: QrCode, size: Dp, description: String, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .size(size)
            .background(Color.White, RoundedCornerShape(6.dp))
            .semantics { contentDescription = description },
    ) {
        val modules = code.size + 2 * QrCode.QUIET_ZONE
        val cell = this.size.minDimension / modules
        // A hair of overlap, so no seams show between neighbouring modules.
        val module = Size(cell + 0.5f, cell + 0.5f)
        for (y in 0 until code.size) {
            for (x in 0 until code.size) {
                if (code.isDark(x, y)) {
                    drawRect(Color.Black, Offset((x + QrCode.QUIET_ZONE) * cell, (y + QrCode.QUIET_ZONE) * cell), module)
                }
            }
        }
    }
}

/** The viewport of tmdb_logo.xml (273.42 x 35.52), TMDB's own SVG. */
private const val TMDB_LOGO_RATIO = 273.42f / 35.52f
