// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.diamondrubinny.scary.ui.theme.LocalReduceMotion
import io.github.diamondrubinny.scary.ui.theme.ScaryColors

/**
 * Base for everything the remote can land on: springs forward, gets a candlelight border and a warm glow when
 * focused, and dips slightly while OK is held. D-pad center and Enter trigger [onClick]; holding them triggers
 * [onLongClick] when given.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
    focusedScale: Float = 1.08f,
    containerColor: Color = ScaryColors.Crypt,
    focusedContainerColor: Color = containerColor,
    borderColor: Color = Color.Transparent,
    focusedBorderColor: Color = ScaryColors.Candle,
    contentDescription: String? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val latestOnFocusChanged by rememberUpdatedState(onFocusChanged)
    LaunchedEffect(focused) { latestOnFocusChanged(focused) }

    val reduceMotion = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 1f + (focusedScale - 1f) * PRESSED_FRACTION
            focused -> focusedScale
            else -> 1f
        },
        // A small overshoot makes focus feel physical; it settles in about a quarter second.
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = SPRING_DAMPING, stiffness = Spring.StiffnessMedium),
        label = "focusScale",
    )
    val glow by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(DURATION_MS),
        label = "focusGlow",
    )
    val container by animateColorAsState(
        targetValue = if (focused) focusedContainerColor else containerColor,
        animationSpec = if (reduceMotion) snap() else tween(DURATION_MS),
        label = "focusContainer",
    )

    Box(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                if (glow > 0f) {
                    val spread = 16.dp.toPx()
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(ScaryColors.Candle.copy(alpha = 0.32f * glow), Color.Transparent),
                            center = center,
                            radius = maxOf(size.width, size.height) * 0.8f,
                        ),
                        topLeft = Offset(-spread, -spread),
                        size = Size(size.width + spread * 2, size.height + spread * 2),
                        cornerRadius = CornerRadius(spread),
                    )
                }
            }
            .clip(shape)
            .background(container)
            .border(2.dp, if (focused) focusedBorderColor else borderColor, shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
    ) {
        content(focused)
    }
}

private const val DURATION_MS = 160
private const val SPRING_DAMPING = 0.62f
/** While pressed, the card keeps this share of its focus growth. */
private const val PRESSED_FRACTION = 0.35f
