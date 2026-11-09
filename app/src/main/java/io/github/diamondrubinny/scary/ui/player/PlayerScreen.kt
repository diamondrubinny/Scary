// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.player

import android.media.AudioManager
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
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
import io.github.diamondrubinny.scary.core.player.Bookmark
import io.github.diamondrubinny.scary.data.WatchStore
import io.github.diamondrubinny.scary.system.SleepTimer
import io.github.diamondrubinny.scary.ui.components.FocusSurface
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.home.PlayerSession
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Test tags of the player. */
const val PLAYER_TAG = "player"
const val PLAYER_MARK_TAG = "playerMark"
const val PLAYER_BOOKMARKS_TAG = "playerBookmarks"
const val PLAYER_BOOKMARK_LIST_TAG = "playerBookmarkList"
const val PLAYER_SEEKBAR_TAG = "playerSeekBar"
const val PLAYER_SPEED_TAG = "playerSpeed"
const val PLAYER_TOAST_TAG = "playerToast"

class PlayerActions(
    /** Where the film is (and how long it is): saved now and then, and when the player closes. */
    val onProgress: (positionMs: Long, durationMs: Long) -> Unit,
    val onClose: () -> Unit,
    /** "Mark this spot"; false when it could not be kept. */
    val onBookmark: (positionMs: Long) -> Boolean,
    val onRemoveBookmark: (positionMs: Long) -> Unit,
    /** The next file in the same torrent. */
    val onPlayNext: () -> Unit,
)

/**
 * The internal player (Media3 ExoPlayer) for a file from the viewer's own Real-Debrid account.
 *
 * The remote, with the controls hidden: OK plays or pauses, holding OK marks the spot, left and right jump 10 seconds,
 * the media keys work, Back leaves. With the controls shown: a progress bar to move along (left and right, faster
 * while held; OK jumps there), jumps, play and pause, "Mark this spot" and the marked spots, audio and subtitle tracks,
 * subtitle size and delay, speed, picture, night sound, the next file of the torrent, and Close.
 *
 * It starts where it was asked to (a saved spot, a bookmark, or the start), keeps the screen on while playing, pauses
 * when the sleep timer ends, and when the TV cannot play the file as it is, tries Real-Debrid's converted (HLS) stream
 * once before saying so.
 */
@OptIn(UnstableApi::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    session: PlayerSession,
    bookmarks: List<Bookmark>,
    actions: PlayerActions,
    settings: WatchStore? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioSession = remember(session) { context.getSystemService(AudioManager::class.java)?.generateAudioSessionId() ?: 0 }
    val player = remember(session) {
        ExoPlayer.Builder(context).setSeekBackIncrementMs(SEEK_MS).setSeekForwardIncrementMs(SEEK_MS).build().also { exo ->
            if (audioSession > 0) exo.audioSessionId = audioSession
        }
    }
    val latestActions by rememberUpdatedState(actions)
    var videoAspect by remember { mutableFloatStateOf(16f / 9f) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
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
    var speedIndex by remember { mutableIntStateOf(SPEEDS.indexOf(1f)) }
    var subtitleSize by remember { mutableIntStateOf(settings?.subtitleSize ?: 1) }
    var subtitleDelayMs by remember { mutableLongStateOf(0L) }
    var picture by remember { mutableIntStateOf(settings?.pictureMode ?: PICTURE_FIT) }
    var nightOn by remember { mutableStateOf(settings?.nightSound == true && NightSound.supported) }
    var showBookmarks by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var toastId by remember { mutableIntStateOf(0) }
    // Delayed subtitles: each cue waits until the film reaches its time plus the delay.
    val pendingCues = remember { ArrayDeque<Pair<Long, String>>() }
    var nextCountdown by remember { mutableIntStateOf(-1) }
    val playPauseFocus = remember { FocusRequester() }
    val bookmarkListFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }
    // The player itself holds the focus while the controls are hidden, so the remote's keys always reach it.
    val rootFocus = remember { FocusRequester() }
    val markedText = stringResource(R.string.player_marked, "%s")
    val markFailedText = stringResource(R.string.player_mark_failed)

    fun poke() {
        controlsVisible = true
        lastInteraction = System.currentTimeMillis()
    }

    fun say(text: String) {
        toast = text
        toastId++
    }

    fun mark() {
        val at = player.currentPosition
        if (latestActions.onBookmark(at)) say(markedText.replace("%s", clock(at))) else say(markFailedText)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                ended = state == Player.STATE_ENDED
                if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(0L)
                if (state == Player.STATE_ENDED) {
                    latestActions.onProgress(player.duration, player.duration)
                    poke()
                    // Next file of the torrent: in a few seconds, unless the viewer says no.
                    if (session.next != null) nextCountdown = NEXT_COUNTDOWN_S
                }
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                // A jump: delayed subtitles waiting for the old place no longer belong.
                pendingCues.clear()
                if (subtitleDelayMs > 0) cues = ""
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onCues(cueGroup: CueGroup) {
                val text = cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString("\n")
                if (subtitleDelayMs <= 0) {
                    cues = text
                } else {
                    pendingCues.addLast(player.currentPosition + subtitleDelayMs to text)
                    while (pendingCues.size > MAX_PENDING_CUES) pendingCues.removeFirst()
                }
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
            latestActions.onProgress(player.currentPosition, player.duration.coerceAtLeast(0L))
            player.removeListener(listener)
            player.release()
        }
    }

    // Night sound: on the player's own audio session, while chosen.
    DisposableEffect(player, nightOn) {
        val effect = if (nightOn) NightSound.start(audioSession) else null
        onDispose { effect?.release() }
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
                latestActions.onProgress(position, duration)
            }
            if (playing && !showBookmarks && System.currentTimeMillis() - lastInteraction > HIDE_AFTER_MS) controlsVisible = false
        }
    }
    // Delayed subtitles come up when the film reaches their time.
    LaunchedEffect(player, subtitleDelayMs) {
        if (subtitleDelayMs <= 0) return@LaunchedEffect
        while (true) {
            delay(CUE_TICK_MS)
            val now = player.currentPosition
            var shown: String? = null
            while (pendingCues.isNotEmpty() && pendingCues.first().first <= now) shown = pendingCues.removeFirst().second
            if (shown != null) cues = shown
        }
    }
    // The next file's countdown.
    LaunchedEffect(nextCountdown) {
        if (nextCountdown < 0) return@LaunchedEffect
        if (nextCountdown == 0) {
            nextCountdown = -1
            latestActions.onPlayNext()
            return@LaunchedEffect
        }
        delay(1_000)
        if (nextCountdown > 0) nextCountdown--
    }
    // A short message (a spot marked), gone after a moment.
    LaunchedEffect(toastId) {
        if (toast == null) return@LaunchedEffect
        delay(TOAST_MS)
        toast = null
    }
    LaunchedEffect(controlsVisible, failed, showBookmarks, nextCountdown >= 0) {
        withFrameNanos { }
        runCatching {
            when {
                nextCountdown >= 0 -> nextFocus.requestFocus()
                showBookmarks -> bookmarkListFocus.requestFocus()
                controlsVisible || failed -> playPauseFocus.requestFocus()
                else -> rootFocus.requestFocus()
            }
        }
    }

    // Back closes what is open on top of the film first, then leaves the player.
    fun back() {
        when {
            nextCountdown >= 0 -> nextCountdown = -1
            showBookmarks -> {
                showBookmarks = false
                poke()
            }
            else -> latestActions.onClose()
        }
    }
    BackHandler { back() }
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

    // OK held with the controls hidden marks the spot; a short press plays or pauses.
    var okDown by remember { mutableStateOf(false) }
    var okHeld by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag(PLAYER_TAG)
            .onPreviewKeyEvent { event ->
                val code = event.key.nativeKeyCode
                if (code == AndroidKeyEvent.KEYCODE_BACK) {
                    if (event.type == KeyEventType.KeyUp) back()
                    return@onPreviewKeyEvent true
                }
                val hidden = !controlsVisible && !showBookmarks && nextCountdown < 0
                if (hidden && (code == AndroidKeyEvent.KEYCODE_DPAD_CENTER || code == AndroidKeyEvent.KEYCODE_ENTER)) {
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (event.nativeKeyEvent.repeatCount == 0) {
                                okDown = true
                                okHeld = false
                            } else if (okDown && !okHeld && event.nativeKeyEvent.repeatCount >= HOLD_REPEATS) {
                                okHeld = true
                                mark()
                            }
                        }
                        KeyEventType.KeyUp -> {
                            if (okDown && !okHeld) togglePlay()
                            okDown = false
                            okHeld = false
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (code) {
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKeyEvent.KEYCODE_SPACE -> { togglePlay(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); poke(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); poke(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { player.seekForward(); poke(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> { player.seekBack(); poke(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> if (session.next != null) { latestActions.onPlayNext(); true } else false
                    // While the controls are hidden, left and right jump; with them shown, they move the focus.
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> if (hidden) { player.seekBack(); poke(); true } else { lastInteraction = System.currentTimeMillis(); false }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> if (hidden) { player.seekForward(); poke(); true } else { lastInteraction = System.currentTimeMillis(); false }
                    else -> { poke(); false }
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Picture(player, videoAspect, picture)
        if (cues.isNotBlank()) {
            BasicText(
                cues,
                style = ScaryType.Body.copy(
                    color = Color.White,
                    fontSize = SUBTITLE_SIZES_SP[subtitleSize].sp,
                    lineHeight = (SUBTITLE_SIZES_SP[subtitleSize] * 1.25f).sp,
                    textAlign = TextAlign.Center,
                    textDirection = TextDirection.Content,
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (controlsVisible) 230.dp else 48.dp, start = 48.dp, end = 48.dp)
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
                ScaryButton(stringResource(R.string.player_close), onClick = latestActions.onClose, primary = true, focusRequester = playPauseFocus)
            }
        }
        toast?.let { message ->
            BasicText(
                message,
                style = ScaryType.BodyStrong.copy(color = ScaryColors.Void),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
                    .background(ScaryColors.Candle, RoundedCornerShape(8.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .testTag(PLAYER_TOAST_TAG)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (nextCountdown >= 0) {
            session.next?.let { next ->
                NextFileCard(
                    name = next.name,
                    seconds = nextCountdown,
                    focusRequester = nextFocus,
                    onPlayNow = {
                        nextCountdown = -1
                        latestActions.onPlayNext()
                    },
                    onCancel = { nextCountdown = -1 },
                    modifier = Modifier.align(Alignment.TopEnd).padding(32.dp),
                )
            }
        }
        if (showBookmarks) {
            BookmarkPanel(
                bookmarks = bookmarks,
                focusRequester = bookmarkListFocus,
                onJump = { at ->
                    player.seekTo(at)
                    showBookmarks = false
                    poke()
                },
                onRemove = latestActions.onRemoveBookmark,
                onClose = {
                    showBookmarks = false
                    poke()
                },
                modifier = Modifier.align(Alignment.CenterEnd).padding(32.dp),
            )
        }
        AnimatedVisibility(visible = controlsVisible && !failed && !showBookmarks, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BasicText(
                        session.playable.title,
                        style = ScaryType.BodyStrong.copy(color = Color.White, textDirection = TextDirection.Content),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    InfoLine(position, duration, SPEEDS[speedIndex])
                }
                // Time runs left to right on the bar in every language, as left and right jump back and forward.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    SeekBar(
                        position = position,
                        duration = duration,
                        bookmarks = bookmarks,
                        onSeek = { at ->
                            player.seekTo(at)
                            poke()
                        },
                        onActivity = { lastInteraction = System.currentTimeMillis() },
                    )
                }
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
                    ScaryButton(stringResource(R.string.player_mark), onClick = { mark(); poke() }, modifier = Modifier.testTag(PLAYER_MARK_TAG))
                    if (bookmarks.isNotEmpty()) {
                        ScaryButton(
                            stringResource(R.string.player_bookmarks, bookmarks.size),
                            onClick = { showBookmarks = true },
                            modifier = Modifier.testTag(PLAYER_BOOKMARKS_TAG),
                        )
                    }
                    if (session.next != null) {
                        ScaryButton(stringResource(R.string.player_next), onClick = latestActions.onPlayNext)
                    }
                    ScaryButton(stringResource(R.string.player_close), onClick = latestActions.onClose)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
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
                        ScaryButton(
                            stringResource(R.string.player_subtitle_size, stringResource(SUBTITLE_SIZE_LABELS[subtitleSize])),
                            onClick = {
                                subtitleSize = (subtitleSize + 1) % SUBTITLE_SIZES_SP.size
                                settings?.subtitleSize = subtitleSize
                                poke()
                            },
                        )
                        ScaryButton(
                            stringResource(R.string.player_subtitle_delay, String.format(Locale.ROOT, "%.1f", subtitleDelayMs / 1000f)),
                            onClick = {
                                subtitleDelayMs = if (subtitleDelayMs >= MAX_SUBTITLE_DELAY_MS) 0L else subtitleDelayMs + SUBTITLE_DELAY_STEP_MS
                                pendingCues.clear()
                                poke()
                            },
                        )
                    }
                    ScaryButton(
                        stringResource(R.string.player_speed, SPEED_LABELS[speedIndex]),
                        onClick = {
                            speedIndex = (speedIndex + 1) % SPEEDS.size
                            player.setPlaybackSpeed(SPEEDS[speedIndex])
                            poke()
                        },
                        modifier = Modifier.testTag(PLAYER_SPEED_TAG),
                    )
                    ScaryButton(
                        stringResource(R.string.player_picture, stringResource(PICTURE_LABELS[picture])),
                        onClick = {
                            picture = (picture + 1) % PICTURE_LABELS.size
                            settings?.pictureMode = picture
                            poke()
                        },
                    )
                    if (NightSound.supported) {
                        ScaryButton(
                            stringResource(R.string.player_night_sound, stringResource(if (nightOn) R.string.value_on else R.string.value_off)),
                            onClick = {
                                nightOn = !nightOn
                                settings?.nightSound = nightOn
                                poke()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The video: fit inside the screen, fill it (cropping the edges), or stretch to it. */
@OptIn(UnstableApi::class)
@Composable
private fun Picture(player: ExoPlayer, aspect: Float, mode: Int) {
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val screenAspect = if (maxHeight.value > 0) maxWidth.value / maxHeight.value else aspect
        // The video surface never takes the focus: keys sent into it would not reach the player's controls.
        val surface = Modifier.focusProperties { canFocus = false }
        val size = when (mode) {
            PICTURE_STRETCH -> Modifier.requiredSize(maxWidth, maxHeight)
            PICTURE_FILL -> if (aspect > screenAspect) Modifier.requiredSize(maxHeight * aspect, maxHeight) else Modifier.requiredSize(maxWidth, maxWidth / aspect)
            else -> if (aspect > screenAspect) Modifier.requiredSize(maxWidth, maxWidth / aspect) else Modifier.requiredSize(maxHeight * aspect, maxHeight)
        }
        PlayerSurface(player = player, modifier = size.then(surface))
    }
}

/** "42:10 left · ends at 23:40", by the TV's clock and the playback speed. */
@Composable
private fun InfoLine(position: Long, duration: Long, speed: Float) {
    if (duration <= 0) return
    val left = ((duration - position).coerceAtLeast(0) / speed).toLong()
    val locale = LocalLocale.current.platformLocale
    val endsAt = DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(System.currentTimeMillis() + left))
    BasicText(
        stringResource(R.string.player_info, clock(left), endsAt),
        style = ScaryType.Caption.copy(color = Color.White),
    )
}

/**
 * The progress bar, with the spots marked on it. Focused: left and right move a target along it (10 seconds a press,
 * then 30 and a minute while held), OK jumps there, and moving away drops the target. Read out as "1:12:40 of 2:05:00".
 */
@Composable
private fun SeekBar(position: Long, duration: Long, bookmarks: List<Bookmark>, onSeek: (Long) -> Unit, onActivity: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var target by remember { mutableStateOf<Long?>(null) }
    // Moving away from the bar drops the target.
    LaunchedEffect(focused) { if (!focused) target = null }
    val shown = target ?: position
    val fraction = if (duration > 0) (shown.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val of = stringResource(R.string.player_position, clock(shown), clock(duration))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(clock(shown), style = ScaryType.Caption.copy(color = if (target != null) ScaryColors.Candle else Color.White, textDirection = TextDirection.Ltr))
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 24.dp)
                .testTag(PLAYER_SEEKBAR_TAG)
                .semantics {
                    contentDescription = of
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || duration <= 0) return@onKeyEvent false
                    val repeat = event.nativeKeyEvent.repeatCount
                    val step = when {
                        repeat < 5 -> 10_000L
                        repeat < 15 -> 30_000L
                        else -> 60_000L
                    }
                    when (event.key.nativeKeyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { target = ((target ?: position) - step).coerceAtLeast(0); onActivity(); true }
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { target = ((target ?: position) + step).coerceAtMost(duration); onActivity(); true }
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> {
                            target?.let(onSeek)
                            target = null
                            true
                        }
                        else -> false
                    }
                }
                .focusable(interactionSource = interaction),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (focused) 10.dp else 6.dp)
                    .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(5.dp))
                    .then(if (focused) Modifier.border(2.dp, ScaryColors.Candle, RoundedCornerShape(5.dp)) else Modifier),
            ) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(ScaryColors.Blood, RoundedCornerShape(5.dp)))
            }
            // The marked spots, as small ticks along the bar.
            if (duration > 0) {
                BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
                    val width = maxWidth
                    bookmarks.forEach { mark ->
                        val x = width * (mark.positionMs.toFloat() / duration).coerceIn(0f, 1f)
                        Box(Modifier.padding(start = x).size(width = 3.dp, height = 18.dp).background(ScaryColors.Candle))
                    }
                }
            }
        }
        BasicText(clock(duration), style = ScaryType.Caption.copy(color = Color.White, textDirection = TextDirection.Ltr))
    }
}

/** The marked spots: OK jumps to one, "Delete" next to it removes it. */
@Composable
private fun BookmarkPanel(
    bookmarks: List<Bookmark>,
    focusRequester: FocusRequester,
    onJump: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .widthIn(max = 460.dp)
            .background(ScaryColors.Crypt.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
            .border(1.dp, ScaryColors.Candle.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
            .testTag(PLAYER_BOOKMARK_LIST_TAG),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(stringResource(R.string.player_bookmarks_title), style = ScaryType.Title, modifier = Modifier.semantics { heading() })
        if (bookmarks.isEmpty()) BasicText(stringResource(R.string.player_bookmarks_empty), style = ScaryType.Body)
        bookmarks.forEachIndexed { index, mark ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val label = stringResource(R.string.player_bookmark_at, clock(mark.positionMs))
                FocusSurface(
                    onClick = { onJump(mark.positionMs) },
                    modifier = (if (index == 0) Modifier.focusRequester(focusRequester) else Modifier).weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    focusedScale = 1.03f,
                    containerColor = ScaryColors.Void,
                    focusedContainerColor = ScaryColors.Bone,
                    contentDescription = label,
                ) { focused ->
                    BasicText(
                        label,
                        style = ScaryType.BodyStrong.copy(color = if (focused) ScaryColors.Void else ScaryColors.Bone),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                ScaryButton(stringResource(R.string.player_bookmark_delete), onClick = { onRemove(mark.positionMs) })
            }
        }
        ScaryButton(
            stringResource(R.string.player_close),
            onClick = onClose,
            focusRequester = if (bookmarks.isEmpty()) focusRequester else null,
        )
    }
}

/** "Next: <file> in 7", with "Play now" and "Cancel". */
@Composable
private fun NextFileCard(
    name: String,
    seconds: Int,
    focusRequester: FocusRequester,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .widthIn(max = 520.dp)
            .background(ScaryColors.Crypt.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
            .border(1.dp, ScaryColors.Candle.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(
            stringResource(R.string.player_next_in, seconds),
            style = ScaryType.BodyStrong,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        BasicText(name, style = ScaryType.Body.copy(textDirection = TextDirection.Content), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScaryButton(stringResource(R.string.player_next_now), onClick = onPlayNow, primary = true, focusRequester = focusRequester)
            ScaryButton(stringResource(R.string.player_next_cancel), onClick = onCancel)
        }
    }
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

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f)
private val SPEED_LABELS = listOf("0.75×", "1×", "1.25×", "1.5×")
private val SUBTITLE_SIZES_SP = listOf(22f, 28f, 38f)
private val SUBTITLE_SIZE_LABELS = listOf(R.string.player_size_small, R.string.player_size_medium, R.string.player_size_large)
private const val PICTURE_FIT = 0
private const val PICTURE_FILL = 1
private const val PICTURE_STRETCH = 2
private val PICTURE_LABELS = listOf(R.string.player_picture_fit, R.string.player_picture_fill, R.string.player_picture_stretch)
private const val SEEK_MS = 10_000L
private const val TICK_MS = 500L
private const val CUE_TICK_MS = 100L
private const val SAVE_EVERY_MS = 15_000L
private const val HIDE_AFTER_MS = 5_000L
private const val TOAST_MS = 2_500L
private const val NEXT_COUNTDOWN_S = 10
private const val SUBTITLE_DELAY_STEP_MS = 500L
private const val MAX_SUBTITLE_DELAY_MS = 5_000L
private const val MAX_PENDING_CUES = 200

/** Key repeats before holding OK counts as holding (about half a second on most remotes). */
private const val HOLD_REPEATS = 5
