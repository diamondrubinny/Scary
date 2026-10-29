// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import java.util.Locale

@Composable
fun ScaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    FocusSurface(
        onClick = onClick,
        modifier = if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier,
        shape = RoundedCornerShape(6.dp),
        focusedScale = 1.06f,
        containerColor = if (primary) ScaryColors.Blood else ScaryColors.Void.copy(alpha = 0.6f),
        focusedContainerColor = if (primary) ScaryColors.Candle else ScaryColors.Bone,
        borderColor = if (primary) ScaryColors.Blood else ScaryColors.Bone.copy(alpha = 0.25f),
        focusedBorderColor = if (primary) ScaryColors.Candle else ScaryColors.Bone,
    ) { focused ->
        BasicText(
            text = text,
            style = ScaryType.Label.copy(color = if (focused) ScaryColors.Void else ScaryColors.Bone),
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 20.dp, vertical = 9.dp),
        )
    }
}

/** Five blood drops; the filled ones are the movie's editorial fear level. */
@Composable
fun FearMeter(level: Int, modifier: Modifier = Modifier, dropWidth: Dp = 8.dp) {
    val description = stringResource(R.string.fear_description, level)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(5) { index ->
            Canvas(Modifier.size(dropWidth, dropWidth * 1.6f)) {
                val on = index < level
                val radius = size.width / 2
                drawRoundRect(
                    color = if (on) ScaryColors.Fear else ScaryColors.BloodDim,
                    cornerRadius = CornerRadius(radius, radius),
                )
                drawRect(
                    color = if (on) ScaryColors.Fear else ScaryColors.BloodDim,
                    size = Size(size.width, size.height / 2),
                )
            }
        }
    }
}

/** Year, director, runtime and fear in one line, as in the mockup. */
@Composable
fun MovieMeta(movie: Movie, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(movie.yearsLabel, style = ScaryType.BodyStrong)
        if (movie.director.isNotBlank()) {
            BasicText(movie.director, style = ScaryType.Body.copy(color = ScaryColors.Ash), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (movie.runtimeMin > 0) {
            // A series' runtime is one episode's.
            BasicText(
                stringResource(if (movie.isSeries) R.string.minutes_episode else R.string.minutes, movie.runtimeMin),
                style = ScaryType.Body.copy(color = ScaryColors.Ash),
            )
        }
        movie.imdbRating?.let { rating ->
            BasicText(
                stringResource(R.string.imdb_rating, String.format(Locale.US, "%.1f", rating)),
                style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle),
            )
        }
        movie.fear?.let { fear ->
            FearMeter(fear)
            BasicText(stringResource(R.string.fear_short, fear), style = ScaryType.Body.copy(color = ScaryColors.Ash))
        }
    }
}

/** Round icon button for the top bar. Icons are drawn, so no icon font or vector assets are needed. */
@Composable
fun IconCircleButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    icon: @Composable (Color) -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        focusedScale = 1.12f,
        containerColor = ScaryColors.Bone.copy(alpha = if (dimmed) 0.04f else 0.1f),
        focusedContainerColor = ScaryColors.Bone,
        borderColor = ScaryColors.Bone.copy(alpha = 0.15f),
        contentDescription = contentDescription,
    ) { focused ->
        Box(Modifier.align(Alignment.Center)) {
            icon(if (focused) ScaryColors.Void else ScaryColors.Bone.copy(alpha = if (dimmed) 0.55f else 1f))
        }
    }
}

@Composable
fun FogIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        val w = size.width
        val h = size.height
        listOf(0.3f to 0.75f, 0.55f to 0.95f, 0.8f to 0.55f).forEachIndexed { i, (y, len) ->
            val path = Path().apply {
                val startX = if (i == 1) w * 0.05f else w * 0.15f
                moveTo(startX, h * y)
                cubicTo(startX + w * len * 0.33f, h * y - h * 0.08f, startX + w * len * 0.66f, h * y + h * 0.08f, startX + w * len, h * y)
            }
            drawPath(path, color, style = stroke)
        }
    }
}

@Composable
fun HomeIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val roof = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.95f, h * 0.48f)
            lineTo(w * 0.05f, h * 0.48f)
            close()
        }
        drawPath(roof, color)
        drawRect(color, topLeft = Offset(w * 0.18f, h * 0.46f), size = Size(w * 0.64f, h * 0.46f))
    }
}

@Composable
fun LockIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.14f
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.05f),
            size = Size(w * 0.56f, h * 0.6f),
            style = Stroke(width = stroke),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.1f, h * 0.4f),
            size = Size(w * 0.8f, h * 0.58f),
            cornerRadius = CornerRadius(w * 0.12f),
        )
    }
}

/** A check mark: "watched". */
@Composable
fun CheckIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.12f, h * 0.52f)
            lineTo(w * 0.4f, h * 0.8f)
            lineTo(w * 0.88f, h * 0.22f)
        }
        drawPath(path, color, style = Stroke(width = w * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun SearchIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val stroke = 2.2.dp.toPx()
        val r = size.minDimension * 0.32f
        val c = Offset(size.width * 0.44f, size.height * 0.44f)
        drawCircle(color, radius = r, center = c, style = Stroke(width = stroke))
        drawLine(
            color = color,
            start = Offset(c.x + r * 0.72f, c.y + r * 0.72f),
            end = Offset(size.width * 0.92f, size.height * 0.92f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** A die showing five pips, for "Surprise me". */
@Composable
fun DiceIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2 + size.minDimension * 0.06f
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = CornerRadius(size.minDimension * 0.2f),
            style = Stroke(width = stroke),
        )
        val r = size.minDimension * 0.075f
        listOf(0.32f to 0.32f, 0.68f to 0.32f, 0.5f to 0.5f, 0.32f to 0.68f, 0.68f to 0.68f).forEach { (x, y) ->
            drawCircle(color, radius = r, center = Offset(size.width * x, size.height * y))
        }
    }
}

@Composable
fun MicIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        val w = size.width
        val h = size.height
        // Capsule, cradle arc and stand.
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.36f, h * 0.05f),
            size = Size(w * 0.28f, h * 0.5f),
            cornerRadius = CornerRadius(w * 0.14f),
        )
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.2f),
            size = Size(w * 0.56f, h * 0.5f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(color, Offset(w * 0.5f, h * 0.7f), Offset(w * 0.5f, h * 0.9f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.34f, h * 0.92f), Offset(w * 0.66f, h * 0.92f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** A screen on a stand with a plug: one of the TV's inputs (HDMI, the antenna). */
@Composable
fun InputIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(color, topLeft = Offset(w * 0.04f, h * 0.12f), size = Size(w * 0.92f, h * 0.6f), cornerRadius = CornerRadius(w * 0.08f), style = Stroke(width = w * 0.1f))
        drawRect(color, topLeft = Offset(w * 0.44f, h * 0.72f), size = Size(w * 0.12f, h * 0.14f))
        drawRect(color, topLeft = Offset(w * 0.26f, h * 0.84f), size = Size(w * 0.48f, h * 0.1f))
    }
}

/** A crescent moon: the sleep timer. */
@Composable
fun MoonIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val r = size.minDimension / 2
        val moon = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(center, r * 0.92f))
            op(this, Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(center.x + r * 0.45f, center.y - r * 0.35f), r * 0.8f)) }, androidx.compose.ui.graphics.PathOperation.Difference)
        }
        drawPath(moon, color)
    }
}

@Composable
fun GridIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val cell = size.minDimension * 0.38f
        val gap = size.minDimension - cell * 2
        for (row in 0..1) {
            for (col in 0..1) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(col * (cell + gap), row * (cell + gap)),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(cell * 0.25f),
                )
            }
        }
    }
}

@Composable
fun GearIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val r = size.minDimension / 2
        val tooth = Size(r * 0.42f, r * 0.5f)
        repeat(8) { i ->
            rotate(degrees = i * 45f) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(center.x - tooth.width / 2, center.y - r),
                    size = tooth,
                    cornerRadius = CornerRadius(tooth.width * 0.25f),
                )
            }
        }
        // A thick ring leaves the hub hole transparent without needing a blend mode.
        drawCircle(color, radius = r * 0.5f, style = Stroke(width = r * 0.44f))
    }
}

/**
 * What to show when there is nothing to show: a framed note that says why, and a way forward when there is one.
 * Announced politely by TalkBack when it appears.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    /** For a screen whose only control is this action: it takes the focus. */
    actionFocus: FocusRequester? = null,
) {
    Column(
        modifier = modifier
            .border(1.dp, ScaryColors.Bone.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
            .background(ScaryColors.Void.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(title, style = ScaryType.BodyStrong)
        BasicText(body, style = ScaryType.Body.copy(color = ScaryColors.Ash))
        if (actionLabel != null) {
            ScaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.padding(top = 6.dp), focusRequester = actionFocus)
        }
    }
}

/** The bucket list: a short list, each line with a dot before it. */
@Composable
fun BucketIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val stroke = size.minDimension * 0.11f
        for (i in 0..2) {
            val y = size.height * (0.22f + i * 0.28f)
            drawCircle(color, radius = stroke * 0.95f, center = Offset(size.width * 0.14f, y))
            drawLine(color, Offset(size.width * 0.34f, y), Offset(size.width * 0.92f, y), strokeWidth = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}
