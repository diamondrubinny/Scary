// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.system.SleepTimer
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.home.PlayerSession
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Test tag of the player. */
const val PLAYER_TAG = "player"

/**
 * The internal player (Media3 ExoPlayer) for a file from the viewer's own Real-Debrid account. The remote: OK plays or
 * pauses and shows the controls, left and right jump 10 seconds, the media keys work, Back leaves. It starts where the
 * viewer stopped last time, keeps the screen on while playing, shows the file's subtitles when chosen, and when the TV
 * cannot play the file as it is, tries Real-Debrid's converted (HLS) stream once before saying so.
 */
@OptIn(UnstableApi::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(session: PlayerSession, onProgress: (Long, Long) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember(session) { ExoPlayer.Builder(context).setSeekBackIncrementMs(SEEK_MS).setSeekForwardIncrementMs(SEEK_MS).build() }
    val latestProgress by rememberUpdatedState(onProgress)
    var videoAspect by remember { mutableFloatStateOf(16f / 9f) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var cues by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var triedConverted by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var audioIndex by remember { mutableIntStateOf(-1) }
    var subtitleIndex by remember { mutableIntStateOf(-1) }
    val playPauseFocus = remember { FocusRequester() }
    // The player itself holds the focus while the controls are hidden, so the remote's keys always reach it.
    val rootFocus = remember { FocusRequester() }

    fun poke() {
        controlsVisible = true
        lastInteraction = System.currentTimeMillis()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(0L)
                if (state == Player.STATE_ENDED) {
                    latestProgress(player.duration, player.duration)
                    poke()
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onCues(cueGroup: CueGroup) {
                cues = cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString("\n")
            }

            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }

            override fun onPlayerError(error: PlaybackException) {
                // The TV cannot decode this file (often MKV with DTS or HEVC 10-bit): Real-Debrid's HLS stream, once.
                if (!triedConverted) {
                    triedConverted = true
                    val resumeAt = player.currentPosition
                    scope.launch {
                        val converted = session.playable.transcoded()
                        if (converted == null) {
                            failed = true
                        } else {
                            player.setMediaItem(MediaItem.Builder().setUri(converted).setMimeType(MimeTypes.APPLICATION_M3U8).build(), resumeAt)
                            player.prepare()
                            player.play()
                        }
                    }
                } else {
                    failed = true
                }
            }
        }
        player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(session.playable.url), session.startAtMs)
        player.prepare()
        player.playWhenReady = true
        onDispose {
            latestProgress(player.currentPosition, player.duration.coerceAtLeast(0L))
            player.removeListener(listener)
            player.release()
        }
    }

    // The screen stays on while the film plays.
    val view = LocalView.current
    DisposableEffect(playing) {
        view.keepScreenOn = playing
        onDispose { view.keepScreenOn = false }
    }

    // Position: shown every half second, saved every 15 seconds; the controls hide after a few quiet seconds of play.
    LaunchedEffect(player) {
        var sinceSave = 0
        while (true) {
            delay(TICK_MS)
            position = player.currentPosition
            if (player.duration > 0) duration = player.duration
            if (++sinceSave * TICK_MS >= SAVE_EVERY_MS) {
                sinceSave = 0
                latestProgress(position, duration)
            }
            if (playing && System.currentTimeMillis() - lastInteraction > HIDE_AFTER_MS) controlsVisible = false
        }
    }
    LaunchedEffect(controlsVisible, failed) {
        withFrameNanos { }
        runCatching { if (controlsVisible || failed) playPauseFocus.requestFocus() else rootFocus.requestFocus() }
    }
    // Back always leaves the player, wherever the focus is.
    BackHandler { onClose() }
    // The sleep timer pauses the film when it ends.
    LaunchedEffect(player) {
        SleepTimer.ended.collect {
            player.pause()
            controlsVisible = true
        }
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
        poke()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag(PLAYER_TAG)
            .onPreviewKeyEvent { event ->
                if (event.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_BACK) {
                    if (event.type == KeyEventType.KeyUp) onClose()
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKeyEvent.KEYCODE_SPACE -> { togglePlay(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); poke(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); poke(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { player.seekForward(); poke(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> { player.seekBack(); poke(); true }
                    // While the controls are hidden, left and right jump; with them shown, they move the focus.
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> if (!controlsVisible) { player.seekBack(); poke(); true } else { poke(); false }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> if (!controlsVisible) { player.seekForward(); poke(); true } else { poke(); false }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> if (!controlsVisible) { togglePlay(); true } else { poke(); false }
                    else -> { poke(); false }
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        // The video surface never takes the focus: keys sent into it would not reach the player's controls.
        PlayerSurface(
            player = player,
            modifier = Modifier.fillMaxHeight().aspectRatio(videoAspect, matchHeightConstraintsFirst = true).focusProperties { canFocus = false },
        )
        if (cues.isNotBlank()) {
            BasicText(
                cues,
                style = ScaryType.Body.copy(color = Color.White, fontSize = 28.sp, textAlign = TextAlign.Center, textDirection = TextDirection.Content),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (controlsVisible) 150.dp else 48.dp, start = 48.dp, end = 48.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        if (buffering && !failed) {
            BasicText(stringResource(R.string.player_loading), style = ScaryType.BodyStrong.copy(color = Color.White), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (failed) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(48.dp)) {
                BasicText(
                    stringResource(R.string.player_error),
                    style = ScaryType.Title.copy(color = Color.White, textAlign = TextAlign.Center),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
                ScaryButton(stringResource(R.string.player_close), onClick = onClose, primary = true, focusRequester = playPauseFocus)
            }
        }
        AnimatedVisibility(visible = controlsVisible && !failed, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicText(
                    session.playable.title,
                    style = ScaryType.BodyStrong.copy(color = Color.White, textDirection = TextDirection.Content),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ProgressLine(position, duration)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    ScaryButton(stringResource(R.string.player_back30), onClick = { player.seekTo((player.currentPosition - 3 * SEEK_MS).coerceAtLeast(0)); poke() })
                    ScaryButton(stringResource(R.string.player_back10), onClick = { player.seekBack(); poke() })
                    ScaryButton(
                        stringResource(if (playing) R.string.player_pause else R.string.player_play),
                        onClick = ::togglePlay,
                        primary = true,
                        focusRequester = playPauseFocus,
                    )
                    ScaryButton(stringResource(R.string.player_fwd10), onClick = { player.seekForward(); poke() })
                    ScaryButton(stringResource(R.string.player_fwd30), onClick = { player.seekTo(player.currentPosition + 3 * SEEK_MS); poke() })
                    val audio = trackGroups(tracks, C.TRACK_TYPE_AUDIO)
                    if (audio.size > 1) {
                        ScaryButton(stringResource(R.string.player_audio, trackLabel(audio.getOrNull(audioIndex), stringResource(R.string.player_track_default))), onClick = {
                            audioIndex = (audioIndex + 1) % audio.size
                            select(player, audio[audioIndex])
                            poke()
                        })
                    }
                    val text = trackGroups(tracks, C.TRACK_TYPE_TEXT)
                    if (text.isNotEmpty()) {
                        val off = stringResource(R.string.player_subtitles_off)
                        ScaryButton(stringResource(R.string.player_subtitles, if (subtitleIndex < 0) off else trackLabel(text.getOrNull(subtitleIndex), off)), onClick = {
                            subtitleIndex = if (subtitleIndex + 1 >= text.size) -1 else subtitleIndex + 1
                            if (subtitleIndex < 0) {
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                            } else {
                                select(player, text[subtitleIndex])
                            }
                            poke()
                        })
                    }
                    ScaryButton(stringResource(R.string.player_close), onClick = onClose)
                }
            }
        }
    }
}

@Composable
private fun ProgressLine(position: Long, duration: Long) {
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(clock(position), style = ScaryType.Caption.copy(color = Color.White, textDirection = TextDirection.Ltr))
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) },
        ) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(ScaryColors.Blood, RoundedCornerShape(3.dp)))
        }
        BasicText(clock(duration), style = ScaryType.Caption.copy(color = Color.White, textDirection = TextDirection.Ltr))
    }
}

/** "1:42:07" or "42:07". */
private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}

private fun trackGroups(tracks: Tracks, type: Int): List<Tracks.Group> = tracks.groups.filter { it.type == type && it.isSupported }

/** A track's language (in the TV's language) or label, as the file names it. */
private fun trackLabel(group: Tracks.Group?, fallback: String): String {
    val format = group?.getTrackFormat(0) ?: return fallback
    val language = format.language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }?.let { Locale.forLanguageTag(it).displayLanguage }
    return format.label?.takeIf(String::isNotBlank) ?: language ?: fallback
}

private fun select(player: Player, group: Tracks.Group) {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(group.type, false)
        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
        .build()
}

private const val SEEK_MS = 10_000L
private const val TICK_MS = 500L
private const val SAVE_EVERY_MS = 15_000L
private const val HIDE_AFTER_MS = 5_000L
