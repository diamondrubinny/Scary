package io.github.diamondrubinny.scary.ui.components

import androidx.compose.foundation.Canvas
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
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
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
                    color = if (on) ScaryColors.Blood else ScaryColors.BloodDim,
                    cornerRadius = CornerRadius(radius, radius),
                )
                drawRect(
                    color = if (on) ScaryColors.Blood else ScaryColors.BloodDim,
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
        BasicText(movie.year.toString(), style = ScaryType.BodyStrong)
        BasicText(movie.director, style = ScaryType.Body.copy(color = ScaryColors.Ash), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText(stringResource(R.string.minutes, movie.runtimeMin), style = ScaryType.Body.copy(color = ScaryColors.Ash))
        FearMeter(movie.fear)
        BasicText(stringResource(R.string.fear_short, movie.fear), style = ScaryType.Body.copy(color = ScaryColors.Ash))
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
