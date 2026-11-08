// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * How much room the screen gives, read once at the top: TVs (and wide, tall screens) keep SCARY's TV layout exactly;
 * tablets in portrait and phones on their side get one column with tighter margins; phones upright get the narrow
 * layout (the top bar in two rows, the hero above its buttons, posters on top).
 */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

@Immutable
data class ScaryLayout(
    val widthClass: WidthClass,
    /** A TV (Android's TV UI mode): the remote, the home-screen options, autoplaying trailers. */
    val tv: Boolean,
    /** The margin on both sides of every screen. */
    val side: Dp,
) {
    /** A phone upright. */
    val compact: Boolean get() = widthClass == WidthClass.COMPACT

    /** Not the TV layout: one column, no side-by-side panes. */
    val narrow: Boolean get() = widthClass != WidthClass.EXPANDED

    companion object {
        /** The TV layout, as SCARY has always drawn it. */
        val TV = ScaryLayout(WidthClass.EXPANDED, tv = true, side = 48.dp)

        fun of(widthDp: Int, heightDp: Int, tv: Boolean): ScaryLayout {
            val widthClass = when {
                tv -> WidthClass.EXPANDED
                // Wide but short (a phone on its side): not enough height for the TV layout's hero and rows.
                widthDp >= EXPANDED_WIDTH && heightDp >= EXPANDED_HEIGHT -> WidthClass.EXPANDED
                widthDp >= MEDIUM_WIDTH -> WidthClass.MEDIUM
                else -> WidthClass.COMPACT
            }
            val side = when (widthClass) {
                WidthClass.EXPANDED -> 48.dp
                WidthClass.MEDIUM -> 32.dp
                WidthClass.COMPACT -> 16.dp
            }
            return ScaryLayout(widthClass, tv, side)
        }

        private const val EXPANDED_WIDTH = 840
        private const val EXPANDED_HEIGHT = 500
        private const val MEDIUM_WIDTH = 600
    }
}

val LocalScaryLayout = staticCompositionLocalOf { ScaryLayout.TV }

/** The layout for this screen, as it is now (it changes when a phone or tablet turns). */
@Composable
fun rememberScaryLayout(): ScaryLayout {
    val config = LocalConfiguration.current
    val tv = (config.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    // The window's own size (it is smaller than the screen in split screen or a desktop window).
    // Before the first layout it is not known yet: the display's size stands in for that one frame.
    val window = LocalWindowInfo.current.containerSize
    val metrics = LocalResources.current.displayMetrics
    val size = if (window.width > 0 && window.height > 0) window else IntSize(metrics.widthPixels, metrics.heightPixels)
    val density = LocalDensity.current.density
    return remember(size, density, tv) { ScaryLayout.of((size.width / density).toInt(), (size.height / density).toInt(), tv) }
}

/** The current screen's layout. */
val scaryLayout: ScaryLayout
    @Composable @ReadOnlyComposable get() = LocalScaryLayout.current

/** A pane that takes [fraction] of a TV's width (for comfortable lines), and all of a tablet's or phone's. */
fun androidx.compose.ui.Modifier.paneWidth(layout: ScaryLayout, fraction: Float): androidx.compose.ui.Modifier =
    if (layout.narrow) fillMaxWidth() else fillMaxWidth(fraction)

/** A dialog [max] wide where there is room, and as wide as the screen allows on a phone. */
fun androidx.compose.ui.Modifier.dialogWidth(max: Dp): androidx.compose.ui.Modifier = widthIn(max = max).fillMaxWidth()
