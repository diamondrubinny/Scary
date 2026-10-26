// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.trailer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.trailer.TrailerEmbed
import io.github.diamondrubinny.scary.core.trailer.TrailerEvent
import kotlinx.coroutines.delay

/** What a screen needs in order to play trailers by themselves. Built once per state in ScaryRoot. */
@Immutable
data class TrailerSettings(
    /** The setting is on, and nothing rules it out now: online data, family night, animations, a dialog, the app in front. */
    val enabled: Boolean,
    val muted: Boolean,
    /** False until the viewer has answered the first-time explanation; nothing plays before that. */
    val introSeen: Boolean,
    val onNeedIntro: () -> Unit,
    val onAudible: (Boolean) -> Unit,
) {
    companion object {
        val Off = TrailerSettings(enabled = false, muted = true, introSeen = true, onNeedIntro = {}, onAudible = {})
    }
}

private enum class Phase { IDLE, LOADING, PLAYING, DONE }

/**
 * Whether [AutoTrailer] could play [movie]'s trailer here, so a screen can keep room for it before it starts: the
 * setting allows it now, the movie has a trailer and is not locked, the TV has a usable WebView, and [playerSize] is at
 * least YouTube's minimum of 200 x 200 pixels on this TV's density.
 */
@Composable
fun canAutoTrailer(movie: Movie, locked: Boolean, settings: TrailerSettings, playerSize: DpSize): Boolean {
    val bigEnough = with(LocalDensity.current) {
        playerSize.width.toPx() >= MIN_PLAYER_PX && playerSize.height.toPx() >= MIN_PLAYER_PX
    }
    return settings.enabled && !locked && bigEnough && TrailerEmbed.isValidVideoId(movie.trailerYoutubeId) && TrailerWebViews.available
}

/**
 * The trailer of [movie], played by itself in a [playerSize] window once the focus has rested on the movie for a
 * moment; [idle] shows otherwise. It plays to its end once: when it ends, fails or cannot be embedded, [idle] comes back
 * until another movie is chosen. Anything that stops it before then (a dialog, leaving SCARY, [visible] turning false)
 * starts it again from the beginning once allowed.
 *
 * YouTube's rules for embedded players shape it: the window is never covered or clipped, it plays only while on screen
 * ([visible]), there is one at a time, and it is at least 200 x 200 pixels.
 */
@Composable
fun AutoTrailer(
    movie: Movie,
    locked: Boolean,
    settings: TrailerSettings,
    playerSize: DpSize,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    idle: @Composable () -> Unit = {},
) {
    val videoId = movie.trailerYoutubeId?.takeIf(TrailerEmbed::isValidVideoId)
    val canPlay = visible && canAutoTrailer(movie, locked, settings, playerSize)
    var phase by remember(movie.id) { mutableStateOf(Phase.IDLE) }
    // Bumped when SCARY is paused under a playing trailer, so it starts again once SCARY is back in front.
    var interruptions by remember(movie.id) { mutableIntStateOf(0) }
    val needIntro by rememberUpdatedState(settings.onNeedIntro)

    LaunchedEffect(movie.id, canPlay, settings.introSeen, interruptions) {
        if (phase == Phase.DONE) return@LaunchedEffect
        phase = Phase.IDLE
        if (!canPlay) return@LaunchedEffect
        delay(DWELL_MS)
        if (!settings.introSeen) {
            needIntro()
            return@LaunchedEffect
        }
        phase = Phase.LOADING
        delay(LOAD_TIMEOUT_MS)
        // Still not playing: a slow network or a stuck player. The window goes rather than stay black.
        if (phase == Phase.LOADING) phase = Phase.DONE
    }

    val audible = phase == Phase.PLAYING && !settings.muted
    val onAudible by rememberUpdatedState(settings.onAudible)
    DisposableEffect(audible) {
        if (audible) onAudible(true)
        onDispose { if (audible) onAudible(false) }
    }

    // No animated resizing: it would clip the player while it grows, and nothing may cover any part of it. [idle] keeps
    // to the end side of a wider slot, by the screen's edge.
    Box(modifier, contentAlignment = Alignment.TopEnd) {
        if (videoId != null && (phase == Phase.LOADING || phase == Phase.PLAYING)) {
            key(videoId) {
                TrailerWebView(
                    videoId = videoId,
                    muted = settings.muted,
                    onEvent = { event ->
                        // Only the player on screen may move things on; a late event from one already closed may not.
                        if (phase == Phase.LOADING || phase == Phase.PLAYING) {
                            phase = when (event) {
                                TrailerEvent.PLAYING -> Phase.PLAYING
                                TrailerEvent.ENDED, TrailerEvent.ERROR -> Phase.DONE
                            }
                        }
                    },
                    onPaused = {
                        phase = Phase.IDLE
                        interruptions++
                    },
                    // The player is decoration for the viewer of a screen reader; the movie's page says it all.
                    modifier = Modifier
                        .size(playerSize)
                        .background(androidx.compose.ui.graphics.Color.Black)
                        .focusProperties { canFocus = false }
                        .clearAndSetSemantics { },
                )
            }
        } else {
            idle()
        }
    }
}

/**
 * The WebView itself. When SCARY is paused (another app, standby, the screensaver, a different HDMI input) the page is
 * emptied at once, before Compose gets another frame to remove it, so no trailer ever plays where nobody sees it.
 */
@Composable
private fun TrailerWebView(
    videoId: String,
    muted: Boolean,
    onEvent: (TrailerEvent) -> Unit,
    onPaused: () -> Unit,
    modifier: Modifier,
) {
    val latestOnEvent by rememberUpdatedState(onEvent)
    val latestOnPaused by rememberUpdatedState(onPaused)
    val holder = remember { arrayOfNulls<WebView>(1) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                holder[0]?.let { web ->
                    web.loadUrl("about:blank")
                    web.onPause()
                }
                latestOnPaused()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    AndroidView(
        factory = { context ->
            TrailerWebViews.create(context, videoId, muted) { latestOnEvent(it) }?.also { holder[0] = it }
                ?: View(context).also { view -> view.post { latestOnEvent(TrailerEvent.ERROR) } }
        },
        onRelease = { view ->
            holder[0] = null
            (view as? WebView)?.let(TrailerWebViews::release)
        },
        modifier = modifier,
    )
}

/** Creating, locking down and destroying the one WebView a trailer plays in. */
private object TrailerWebViews {
    /** False once the TV turned out to have no usable WebView; SCARY then stops trying until it restarts. */
    @Volatile
    var available = true
        private set

    @SuppressLint("SetJavaScriptEnabled") // The page is SCARY's own (TrailerEmbed), and the YouTube player needs it.
    fun create(context: Context, videoId: String, muted: Boolean, onEvent: (TrailerEvent) -> Unit): WebView? {
        if (!available) return null
        var web: WebView? = null
        return try {
            val view = WebView(context).also { web = it }
            // Only SCARY's own page, in the main frame, may report; and only the three events it knows. Without the
            // listener there is no "ended" or "error", and a stuck player would stay on screen: then no trailers.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.addWebMessageListener(view, TrailerEmbed.CHANNEL, setOf(TrailerEmbed.ORIGIN)) { _, message, _, isMainFrame, _ ->
                    if (isMainFrame) TrailerEvent.parse(message.data)?.let(onEvent)
                }
            } else {
                available = false
                view.destroy()
                return null
            }
            view.setBackgroundColor(Color.BLACK)
            // Never focused and never read aloud: the remote stays on SCARY's own cards and buttons.
            view.isFocusable = false
            view.isFocusableInTouchMode = false
            view.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            view.settings.apply {
                javaScriptEnabled = true
                // YouTube's player keeps its state in web storage; it is deleted when the player closes.
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = false
                allowContentAccess = false
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                setGeolocationEnabled(false)
                setSupportZoom(false)
            }
            CookieManager.getInstance().apply {
                setAcceptCookie(false)
                setAcceptThirdPartyCookies(view, false)
            }
            view.webViewClient = TrailerClient(onEvent)
            view.webChromeClient = object : WebChromeClient() {
                // No grey "play" placeholder while the video loads.
                override fun getDefaultVideoPoster(): Bitmap = createBitmap(1, 1)
            }
            view.loadDataWithBaseURL(TrailerEmbed.BASE_URL, TrailerEmbed.html(videoId, muted), "text/html", "utf-8", null)
            view
        } catch (e: RuntimeException) {
            // No WebView installed, or one that is being updated (MissingWebViewPackageException and friends).
            available = false
            web?.destroy()
            null
        }
    }

    fun release(web: WebView) {
        web.stopLoading()
        web.webChromeClient = null
        web.destroy()
        WebStorage.getInstance().deleteAllData()
    }
}

// onRenderProcessGone is right below; androidx.webkit's lint check does not see overrides in Kotlin classes.
@SuppressLint("MissingOnRenderProcessGone")
private class TrailerClient(private val onEvent: (TrailerEvent) -> Unit) : WebViewClient() {
    // The page never goes anywhere else: no YouTube links opening inside SCARY.
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.isForMainFrame

    // A crashed or killed renderer must not take SCARY down with it.
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onEvent(TrailerEvent.ERROR)
        return true
    }
}

/** How long the focus rests on a movie before its trailer starts. */
private const val DWELL_MS = 1_500L

/** How long a trailer may take to start before its window gives up. */
private const val LOAD_TIMEOUT_MS = 15_000L

/** YouTube's minimum size for an embedded player, in pixels on each side. */
private const val MIN_PLAYER_PX = 200f
