package io.github.diamondrubinny.scary.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.ui.home.AppTile
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType

/** Image request with a short crossfade; Coil caches it in memory and on disk. */
@Composable
fun rememberArtwork(url: String?): ImageRequest? {
    val context = LocalContext.current
    return remember(url) { url?.let { ImageRequest.Builder(context).data(it).crossfade(true).build() } }
}

fun movieTint(movie: Movie, lightness: Float): Color =
    Color.hsl(movie.hue.toFloat(), movie.saturation / 100f, lightness)

/** Generated poster art: a tinted gradient with a single light source. No image downloads in phase 1. */
@Composable
fun PosterArt(movie: Movie, modifier: Modifier = Modifier) {
    Box(
        modifier.drawWithCache {
            val base = Brush.linearGradient(
                colors = listOf(movieTint(movie, 0.18f), movieTint(movie, 0.07f), Color(0xFF050505)),
                start = Offset(size.width * 0.3f, 0f),
                end = Offset(size.width * 0.7f, size.height),
            )
            val light = Brush.radialGradient(
                colors = listOf(movieTint(movie, 0.55f).copy(alpha = 0.55f), Color.Transparent),
                center = Offset(size.width * 0.7f, size.height * 0.28f),
                radius = size.width * 0.6f,
            )
            val fade = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                startY = size.height * 0.45f,
                endY = size.height,
            )
            onDrawBehind {
                drawRect(base)
                drawRect(light)
                drawRect(fade)
            }
        },
    )
}

@Composable
fun PosterCard(
    movie: Movie,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 104.dp,
    focusRequester: FocusRequester? = null,
    locked: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val lockedLabel = stringResource(R.string.locked_badge)
    FocusSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = (if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier)
            .width(width)
            .aspectRatio(2f / 3f),
        contentDescription = if (locked) "${movie.title}, ${movie.year}, $lockedLabel" else "${movie.title}, ${movie.year}",
        borderColor = ScaryColors.Bone.copy(alpha = 0.08f),
        onFocusChanged = { if (it) onFocused() },
    ) {
        // The generated art stays underneath: it shows while the poster loads, offline, or if it fails.
        var posterShown by remember(movie.posterUrl) { mutableStateOf(false) }
        PosterArt(movie, Modifier.fillMaxSize())
        rememberArtwork(movie.posterUrl)?.let { request ->
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { posterShown = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!posterShown) {
            BasicText(
                text = movie.year.toString(),
                style = ScaryType.Caption,
                modifier = Modifier.align(Alignment.TopStart).padding(7.dp),
            )
            BasicText(
                text = movie.title,
                style = ScaryType.CardTitle,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp, vertical = 7.dp),
            )
        }
        if (locked) LockBadge(Modifier.align(Alignment.TopEnd))
    }
}

@Composable
fun WideMovieCard(
    movie: Movie,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    locked: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    FocusSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = (if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier)
            .width(176.dp)
            .aspectRatio(16f / 9f),
        contentDescription = "${movie.title}, ${movie.year}",
        borderColor = ScaryColors.Bone.copy(alpha = 0.08f),
        onFocusChanged = { if (it) onFocused() },
    ) {
        PosterArt(movie, Modifier.fillMaxSize())
        rememberArtwork(movie.backgroundUrl)?.let { request ->
            AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
            )
        }
        if (locked) LockBadge(Modifier.align(Alignment.TopEnd))
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 9.dp, vertical = 7.dp)) {
            BasicText(movie.title, style = ScaryType.CardTitle.copy(fontSize = ScaryType.Title.fontSize), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(stringResource(R.string.minutes, movie.runtimeMin), style = ScaryType.Caption)
        }
    }
}

@Composable
private fun LockBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(6.dp)
            .background(ScaryColors.Void.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
            .padding(4.dp),
    ) {
        LockIcon(ScaryColors.Candle)
    }
}

@Composable
fun AppTileCard(
    tile: AppTile,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    badge: String? = null,
    dimmed: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val label = when (tile) {
        is AppTile.Installed -> tile.app.label
        is AppTile.Suggestion -> stringResource(R.string.install_app, tile.label)
        AppTile.AllApps -> stringResource(R.string.all_apps)
        AppTile.SetAsHome -> stringResource(R.string.tile_set_home)
        AppTile.SystemSettings -> stringResource(R.string.system_settings)
    }
    val description = when {
        tile is AppTile.SystemSettings -> stringResource(R.string.system_settings_description)
        badge != null -> "$label, $badge"
        else -> label
    }
    FocusSurface(
        onClick = onClick,
        modifier = (if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier)
            // The setup tile is the first thing a new viewer needs; it is wider so its label is never cut off.
            .width(if (tile is AppTile.SetAsHome) SET_HOME_TILE_WIDTH else TILE_WIDTH)
            .height(TILE_WIDTH * 9 / 16),
        containerColor = ScaryColors.Crypt,
        borderColor = ScaryColors.Bone.copy(alpha = 0.08f),
        contentDescription = description,
        onFocusChanged = { if (it) onFocused() },
        onLongClick = onLongClick,
    ) {
        val banner = (tile as? AppTile.Installed)?.app?.banner
        if (banner != null) {
            Image(
                bitmap = banner,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (tile is AppTile.SetAsHome) {
            // Icon beside the text, so two full lines fit even at the largest system font.
            Row(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HomeIcon(ScaryColors.Candle, Modifier.size(28.dp))
                BasicText(
                    text = label,
                    style = ScaryType.Label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                val icon = (tile as? AppTile.Installed)?.app?.icon
                when {
                    icon != null -> Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(34.dp))
                    tile is AppTile.SystemSettings -> GearIcon(ScaryColors.Bone, Modifier.size(26.dp))
                    tile is AppTile.AllApps -> GridIcon(ScaryColors.Bone, Modifier.size(26.dp))
                    tile is AppTile.SetAsHome -> HomeIcon(ScaryColors.Candle, Modifier.size(26.dp))
                    else -> Box(
                        Modifier
                            .size(30.dp)
                            .background(ScaryColors.Blood, RoundedCornerShape(8.dp)),
                    )
                }
                BasicText(
                    text = label,
                    style = ScaryType.Label.copy(textAlign = TextAlign.Center),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (dimmed) Box(Modifier.fillMaxSize().background(ScaryColors.Void.copy(alpha = 0.55f)))
        if (badge != null) {
            BasicText(
                text = badge,
                style = ScaryType.Caption.copy(color = ScaryColors.Void),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .background(ScaryColors.Candle, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

private val TILE_WIDTH = 136.dp
private val SET_HOME_TILE_WIDTH = 240.dp
