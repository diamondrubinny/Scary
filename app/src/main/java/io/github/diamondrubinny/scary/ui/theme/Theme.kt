package io.github.diamondrubinny.scary.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.model.Language

/** Single committed dark palette: bone on void, dried blood for primary actions, candlelight for focus. */
object ScaryColors {
    val Void = Color(0xFF0A0B0B)
    val Crypt = Color(0xFF151615)
    val Bone = Color(0xFFE9E1CF)
    val BoneDim = Color(0xFFCFC8B8)
    val Ash = Color(0xFFA39E92)
    val Blood = Color(0xFF8E1B1B)
    val BloodDim = Color(0xFF3A2222)
    val Candle = Color(0xFFE0A94A)
}

/**
 * Karantina and Assistant have no Cyrillic, so Russian switches to Oswald (condensed headings) and Rubik (text).
 * Set once per Activity before setContent; a language change recreates the Activity.
 */
object ScaryFonts {
    private val karantina = FontFamily(Font(R.font.karantina_bold, FontWeight.Bold))
    private val assistant = FontFamily(
        Font(R.font.assistant_regular, FontWeight.Normal),
        Font(R.font.assistant_bold, FontWeight.Bold),
    )
    private val oswald = FontFamily(Font(R.font.oswald_semibold, FontWeight.Bold))
    private val rubik = FontFamily(
        Font(R.font.rubik_regular, FontWeight.Normal),
        Font(R.font.rubik_bold, FontWeight.Bold),
    )

    var Display: FontFamily = karantina
        private set
    var Body: FontFamily = assistant
        private set
    val Logo = FontFamily(Font(R.font.rubik_wet_paint, FontWeight.Normal))

    fun useFor(language: Language) {
        val cyrillic = language == Language.RU
        Display = if (cyrillic) oswald else karantina
        Body = if (cyrillic) rubik else assistant
    }
}

/** Sizes target a 960x540dp TV canvas viewed from about three meters. */
object ScaryType {
    val Logo: TextStyle get() = TextStyle(
        fontFamily = ScaryFonts.Logo,
        fontSize = 34.sp,
        color = ScaryColors.Bone,
        shadow = Shadow(ScaryColors.Blood, Offset(0f, 5f), 0f),
    )
    val Display: TextStyle get() = TextStyle(fontFamily = ScaryFonts.Display, fontWeight = FontWeight.Bold, fontSize = 60.sp, lineHeight = 54.sp, color = ScaryColors.Bone)
    val Title: TextStyle get() = TextStyle(fontFamily = ScaryFonts.Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 26.sp, color = ScaryColors.Bone)
    val CardTitle: TextStyle get() = TextStyle(fontFamily = ScaryFonts.Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 18.sp, color = ScaryColors.Bone)
    val Clock: TextStyle get() = TextStyle(fontFamily = ScaryFonts.Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = ScaryColors.Bone)
    val Body: TextStyle get() = TextStyle(fontFamily = ScaryFonts.Body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, color = ScaryColors.BoneDim)
    val BodyStrong: TextStyle get() = Body.copy(fontWeight = FontWeight.Bold, color = ScaryColors.Bone)
    val Label: TextStyle get() = TextStyle(fontFamily = ScaryFonts.Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ScaryColors.Bone)
    val Caption: TextStyle get() = TextStyle(fontFamily = ScaryFonts.Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, color = ScaryColors.Ash)
    val Eyebrow: TextStyle get() = TextStyle(fontFamily = ScaryFonts.Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.2.em, color = ScaryColors.Candle)
    val RowTitle: TextStyle get() = TextStyle(fontFamily = ScaryFonts.Body, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ScaryColors.Bone)
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
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion, content = content)
}
