package io.github.diamondrubinny.scary.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.diamondrubinny.scary.R

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

object ScaryFonts {
    val Display = FontFamily(Font(R.font.karantina_bold, FontWeight.Bold))
    val Body = FontFamily(
        Font(R.font.assistant_regular, FontWeight.Normal),
        Font(R.font.assistant_bold, FontWeight.Bold),
    )
    val Logo = FontFamily(Font(R.font.rubik_wet_paint, FontWeight.Normal))
}

/** Sizes target a 960x540dp TV canvas viewed from about three meters. */
object ScaryType {
    val Logo = TextStyle(
        fontFamily = ScaryFonts.Logo,
        fontSize = 34.sp,
        color = ScaryColors.Bone,
        shadow = Shadow(ScaryColors.Blood, Offset(0f, 5f), 0f),
    )
    val Display = TextStyle(fontFamily = ScaryFonts.Display, fontWeight = FontWeight.Bold, fontSize = 60.sp, lineHeight = 54.sp, color = ScaryColors.Bone)
    val Title = TextStyle(fontFamily = ScaryFonts.Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 26.sp, color = ScaryColors.Bone)
    val CardTitle = TextStyle(fontFamily = ScaryFonts.Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 18.sp, color = ScaryColors.Bone)
    val Clock = TextStyle(fontFamily = ScaryFonts.Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = ScaryColors.Bone)
    val Body = TextStyle(fontFamily = ScaryFonts.Body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, color = ScaryColors.BoneDim)
    val BodyStrong = Body.copy(fontWeight = FontWeight.Bold, color = ScaryColors.Bone)
    val Label = TextStyle(fontFamily = ScaryFonts.Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ScaryColors.Bone)
    val Caption = TextStyle(fontFamily = ScaryFonts.Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, color = ScaryColors.Ash)
    val Eyebrow = TextStyle(fontFamily = ScaryFonts.Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.2.em, color = ScaryColors.Candle)
    val RowTitle = TextStyle(fontFamily = ScaryFonts.Body, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ScaryColors.Bone)
}

/** True when decorative motion should stop: the user turned effects off, or the system removed animations. */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun ScaryTheme(reduceMotion: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion, content = content)
}
