// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.theme

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.model.Language

/**
 * The Noir palette (docs/QUALITY_AND_DESIGN_PLAN.md): bone on pure black, and one red. Light is the only emphasis: what
 * is in focus or matters is bone, the rest is ash, and red is kept for the few marks that should be found at a glance
 * (the current tab, an eyebrow, the fear level).
 */
object ScaryColors {
    val Void = Color(0xFF000000)

    /** A surface that must stand off the black: a dialog, a text field. Rows and panels have none; a hairline parts them. */
    val Crypt = Color(0xFF0F0F0F)
    val Bone = Color(0xFFE9E1CF)
    val BoneDim = Color(0xFFCFC8B8)

    /** Secondary text: 7.6:1 on black. Nothing dimmer than this carries words. */
    val Ash = Color(0xFFA39E92)
    val Blood = Color(0xFF8E1B1B)
    val BloodDim = Color(0xFF3A2222)

    /** What is lit: the focus outline and emphasised words. In Noir it is the bone itself; there is no gold. */
    val Candle = Bone

    /** The one accent: 4.7:1 on black, so it may carry short words. */
    val Accent = Color(0xFFD94040)

    /** A hairline between rows and sections. */
    val Line = Color(0x24E9E1CF)

    /** Filled fear drops: at least 3:1 against the dark background and against the empty drops (WCAG 1.4.11). */
    val Fear = Color(0xFFD94040)
}

/**
 * Titles are set in Rubik, a clean wide sans that covers Hebrew, Latin and Cyrillic; running text in Assistant
 * (Rubik for Russian, which Assistant lacks). Arabic uses Cairo for both. The dripping logo is the only display face.
 * Set once per Activity before setContent; a language change recreates the Activity.
 */
object ScaryFonts {
    private val assistant = FontFamily(
        Font(R.font.assistant_regular, FontWeight.Normal),
        Font(R.font.assistant_bold, FontWeight.Bold),
    )
    private val rubik = FontFamily(
        Font(R.font.rubik_regular, FontWeight.Normal),
        Font(R.font.rubik_bold, FontWeight.Bold),
    )

    private val cairo = FontFamily(
        Font(R.font.cairo_regular, FontWeight.Normal),
        Font(R.font.cairo_bold, FontWeight.Bold),
    )

    var Display: FontFamily = rubik
        private set
    var Body: FontFamily = assistant
        private set
    val Logo = FontFamily(Font(R.font.rubik_wet_paint, FontWeight.Normal))

    /**
     * Whether small labels may be set with wide letter-spacing. Not in Arabic: its letters join, and space between them
     * breaks the words.
     */
    var spacedLabels: Boolean = true
        private set

    fun useFor(language: Language) {
        Display = if (language == Language.AR) cairo else rubik
        spacedLabels = language != Language.AR
        Body = when (language) {
            Language.RU -> rubik
            Language.AR -> cairo
            else -> assistant
        }
    }
}

/** Sizes target a 960x540dp TV canvas viewed from about three meters. */
object ScaryType {
    val Logo: TextStyle get() = TextStyle(
        fontFamily = ScaryFonts.Logo,
        fontSize = 28.sp,
        color = ScaryColors.Bone,
        shadow = Shadow(ScaryColors.Blood, Offset(0f, 4f), 0f),
    )

    /** A film's title, large and light: at this size a regular weight reads from the sofa and stays calm. */
    val Display: TextStyle get() = TextStyle(fontFamily = ScaryFonts.Display, fontWeight = FontWeight.Normal, fontSize = 50.sp, lineHeight = 54.sp, letterSpacing = (-0.01).em, color = ScaryColors.Bone)
    val Title: TextStyle get() = TextStyle(fontFamily = ScaryFonts.Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, color = ScaryColors.Bone)
    val CardTitle: TextStyle get() = TextStyle(fontFamily = ScaryFonts.Display, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 17.sp, color = ScaryColors.Bone)
    val Clock: TextStyle get() = TextStyle(fontFamily = ScaryFonts.Display, fontWeight = FontWeight.Normal, fontSize = 16.sp, color = ScaryColors.Ash)

    /** A word of the top bar: ash until it is the current place or has the focus. */
    val Nav: TextStyle get() = TextStyle(fontFamily = ScaryFonts.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ScaryColors.Ash)

    /** Wide letter-spacing for small labels, where the script allows it (ScaryFonts.spacedLabels). */
    private fun spaced(em: Float) = if (ScaryFonts.spacedLabels) em.em else 0.em
    val Body: TextStyle get() = TextStyle(fontFamily = ScaryFonts.Body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, color = ScaryColors.BoneDim)
    val BodyStrong: TextStyle get() = Body.copy(fontWeight = FontWeight.Bold, color = ScaryColors.Bone)
    val Label: TextStyle get() = TextStyle(fontFamily = ScaryFonts.Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ScaryColors.Bone)
    // 14sp is the smallest size SCARY uses: smaller text is hard to read from a sofa three meters away.
    val Caption: TextStyle get() = TextStyle(fontFamily = ScaryFonts.Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = ScaryColors.Ash)
    val Eyebrow: TextStyle get() = TextStyle(fontFamily = ScaryFonts.Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = spaced(0.22f), color = ScaryColors.Accent)

    /** A row's name: small, spaced out and quiet, so the posters under it lead. */
    val RowTitle: TextStyle get() = TextStyle(fontFamily = ScaryFonts.Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = spaced(0.16f), color = ScaryColors.Ash)

    /** Movie titles in [Display]: a long one ("The Texas Chain Saw Massacre") shrinks to fit before it is cut off. */
    val DisplayFit: TextAutoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 50.sp, stepSize = 2.sp)

    /** [Display] with a line height that follows the size [DisplayFit] picks, for titles on two lines. */
    val DisplayFitting: TextStyle get() = Display.copy(lineHeight = 1.08.em)
}

/**
 * For text that may be in either language (synopses, cast): bidi follows the text itself, while the paragraph
 * stays aligned with the layout's reading side.
 */
@Composable
fun TextStyle.mixedLanguage(): TextStyle = copy(
    textDirection = TextDirection.Content,
    textAlign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left,
)

/** True when decorative motion should stop: the user turned effects off, or the system removed animations. */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun ScaryTheme(reduceMotion: Boolean = false, content: @Composable () -> Unit) {
    // The layout for this screen's size: the TV layout on TVs, one column on tablets and phones (ScaryLayout).
    CompositionLocalProvider(
        LocalReduceMotion provides reduceMotion,
        LocalScaryLayout provides rememberScaryLayout(),
        content = content,
    )
}
