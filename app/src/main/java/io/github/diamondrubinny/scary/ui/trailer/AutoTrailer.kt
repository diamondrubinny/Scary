// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.trailer

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        if (phase == Phase.LOADING) {
            TrailerDiagnostics.note(if (TrailerDiagnostics.wasReady) "YouTube was ready but did not play within 15 s" else "no answer from YouTube within 15 s")
            phase = Phase.DONE
        }
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
                    onMessage = { message ->
                        val event = TrailerEvent.parse(message)
                        // Only the player on screen may move things on; a late event from one already closed may not.
                        if (event != null && (phase == Phase.LOADING || phase == Phase.PLAYING)) {
                            TrailerDiagnostics.event(event, TrailerEvent.errorDetail(message))
                            phase = when (event) {
                                TrailerEvent.READY -> phase
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
    onMessage: (String) -> Unit,
    onPaused: () -> Unit,
    modifier: Modifier,
) {
    val latestOnMessage by rememberUpdatedState(onMessage)
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
            TrailerWebViews.create(context, videoId, muted) { latestOnMessage(it) }?.also { holder[0] = it }
                ?: View(context).also { view -> view.post { latestOnMessage(TrailerEvent.ERROR.wire) } }
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

    // JavaScript: the page is SCARY's own (TrailerEmbed), and the YouTube player needs it. The bridge (below) takes
    // four words and nothing else.
    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "AddJavascriptInterface")
    fun create(context: Context, videoId: String, muted: Boolean, onMessage: (String) -> Unit): WebView? {
        if (!available) return null
        var web: WebView? = null
        return try {
            val view = WebView(context).also { web = it }
            val version = WebViewCompat.getCurrentWebViewPackage(context)?.versionName ?: "?"
            // Only SCARY's own page, in the main frame, may report, and only the four events it knows. Older WebViews
            // (many TVs never update theirs) lack the listener: a plain bridge then carries the same words. Any frame
            // could call it, YouTube's included, but all it accepts is one of those words, which at most closes the
            // player.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.addWebMessageListener(view, TrailerEmbed.CHANNEL, setOf(TrailerEmbed.ORIGIN)) { _, message, _, isMainFrame, _ ->
                    message.data?.takeIf { isMainFrame }?.let(onMessage)
                }
                TrailerDiagnostics.loading("WebView $version")
            } else {
                view.addJavascriptInterface(TrailerBridge(onMessage), TrailerEmbed.CHANNEL)
                TrailerDiagnostics.loading("WebView $version (bridge)")
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
            view.webViewClient = TrailerClient(onMessage)
            val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            view.webChromeClient = object : WebChromeClient() {
                // No grey "play" placeholder while the video loads.
                override fun getDefaultVideoPoster(): Bitmap = createBitmap(1, 1)

                // Debug builds log the page's console, for CI and for a TV on a cable.
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (debuggable) Log.d(TrailerDiagnostics.TAG, "console: ${message.message().take(300)}")
                    return true
                }
            }
            view.loadDataWithBaseURL(TrailerEmbed.BASE_URL, TrailerEmbed.html(videoId, muted), "text/html", "utf-8", null)
            view
        } catch (e: RuntimeException) {
            // No WebView installed, or one that is being updated (MissingWebViewPackageException and friends).
            available = false
            TrailerDiagnostics.note("no usable WebView on this TV (${e.javaClass.simpleName})")
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
private class TrailerClient(private val onMessage: (String) -> Unit) : WebViewClient() {
    // The page never goes anywhere else: no YouTube links opening inside SCARY.
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.isForMainFrame

    // Logged for "trailers don't work" (only the host, never the full address).
    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        Log.w(TrailerDiagnostics.TAG, "load error ${error.errorCode} from ${request.url.host}")
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
        Log.w(TrailerDiagnostics.TAG, "HTTP ${response.statusCode} from ${request.url.host}")
    }

    // A crashed or killed renderer must not take SCARY down with it.
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        TrailerDiagnostics.note("the WebView renderer stopped (crashed: ${detail.didCrash()})")
        onMessage(TrailerEvent.ERROR.wire)
        return true
    }
}

/** For WebViews without WebMessageListener: the page's `scaryTrailer.postMessage(word)`, delivered on the main thread. */
private class TrailerBridge(private val onMessage: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun postMessage(message: String?) {
        val word = message?.take(16) ?: return
        if (TrailerEvent.parse(word) != null) main.post { onMessage(word) }
    }
}

/**
 * What happened to the last trailer, in plain words, shown under the trailer settings and logged (tag ScaryTrailer):
 * it tells a TV's WebView problem apart from YouTube's answer when "trailers don't work". Holds no personal data.
 */
object TrailerDiagnostics {
    const val TAG = "ScaryTrailer"
    private val state = MutableStateFlow<String?>(null)
    val last: StateFlow<String?> = state.asStateFlow()
    private var webView = ""

    /** Whether YouTube's player said it was ready for the trailer now loading. */
    var wasReady = false
        private set

    fun loading(webViewDescription: String) {
        webView = webViewDescription
        wasReady = false
        note("loading")
    }

    fun event(event: TrailerEvent, detail: String?) {
        if (event == TrailerEvent.READY) wasReady = true
        note(
            when (event) {
                TrailerEvent.READY -> "YouTube player ready"
                TrailerEvent.PLAYING -> "playing"
                TrailerEvent.ENDED -> "played to the end"
                TrailerEvent.ERROR -> when (detail) {
                    null -> "error"
                    "api" -> "YouTube's player script could not load"
                    else -> "YouTube error $detail"
                }
            },
        )
    }

    fun note(status: String) {
        val line = if (webView.isEmpty()) status else "$webView: $status"
        state.value = line
        Log.i(TAG, line)
    }
}

/** How long the focus rests on a movie before its trailer starts. */
private const val DWELL_MS = 1_500L

/** How long a trailer may take to start before its window gives up. */
private const val LOAD_TIMEOUT_MS = 15_000L

/** YouTube's minimum size for an embedded player, in pixels on each side. */
private const val MIN_PLAYER_PX = 200f
