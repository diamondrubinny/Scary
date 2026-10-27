// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.trailer

import java.security.MessageDigest
import java.util.Base64

/**
 * What the trailer page reports back to the app. Anything else it might send is ignored. An error may carry YouTube's
 * error code ("error:150") or "api" when YouTube's script could not load, for the diagnostics in settings.
 */
enum class TrailerEvent(val wire: String) {
    READY("ready"),
    PLAYING("playing"),
    ENDED("ended"),
    ERROR("error");

    companion object {
        private val errorPattern = Regex("^error(?::(\\d{1,4}|api))?$")

        fun parse(message: String?): TrailerEvent? =
            if (message != null && errorPattern.matches(message)) ERROR else entries.firstOrNull { it.wire == message }

        /** "150" for "error:150", "api" for "error:api"; null for anything else. */
        fun errorDetail(message: String?): String? = message?.let { errorPattern.matchEntire(it) }?.groupValues?.get(1)?.ifEmpty { null }
    }
}

/** Where the player comes from: privacy-enhanced mode first; the regular player only as a last try. */
enum class TrailerHost(val origin: String) {
    NOCOOKIE("https://www.youtube-nocookie.com"),
    YOUTUBE("https://www.youtube.com"),
}

/**
 * The page that plays a trailer inside SCARY through YouTube's IFrame Player API, in privacy-enhanced mode
 * (youtube-nocookie.com).
 *
 * YouTube's Required Minimum Functionality asks embedded players in apps to identify themselves in the HTTP Referer,
 * as an https URL whose host is the app ID: the page is loaded with [BASE_URL] as its base URL, which sets it. Only
 * documented player parameters are used, and the player plays once (no loop).
 */
object TrailerEmbed {
    const val APP_ID = "io.github.diamondrubinny.scary"

    /** Base URL of the page: YouTube reads the app's identity from it. Nothing is ever loaded from this host. */
    const val BASE_URL = "https://$APP_ID/"

    /** The only origin whose messages the app accepts. */
    const val ORIGIN = "https://$APP_ID"

    /** Name of the message channel the page posts [TrailerEvent]s to. */
    const val CHANNEL = "scaryTrailer"

    private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

    fun isValidVideoId(videoId: String?): Boolean = videoId != null && videoIdPattern.matches(videoId)

    // The script reads the video id and the sound setting from the page, so it never changes and its hash can be
    // allowed by the page's Content-Security-Policy instead of all inline scripts.
    private val script = """
        (function () {
          var holder = document.getElementById('player');
          var videoId = holder.getAttribute('data-video');
          var muted = holder.getAttribute('data-muted') === '1';
          var host = holder.getAttribute('data-host');
          var reported = {};
          function report(event) {
            var kind = event.split(':')[0];
            if (reported[kind] || !window.$CHANNEL) return;
            reported[kind] = true;
            window.$CHANNEL.postMessage(event);
          }
          window.onYouTubeIframeAPIReady = function () {
            new YT.Player('player', {
              host: host,
              videoId: videoId,
              width: '100%',
              height: '100%',
              playerVars: {
                autoplay: 1, mute: muted ? 1 : 0, controls: 0, rel: 0, playsinline: 1, fs: 0,
                disablekb: 1, iv_load_policy: 3
              },
              events: {
                onReady: function (e) {
                  if (muted) { e.target.mute(); } else { e.target.unMute(); }
                  report('ready');
                  e.target.playVideo();
                },
                onStateChange: function (e) {
                  if (e.data === YT.PlayerState.PLAYING) report('playing');
                  if (e.data === YT.PlayerState.ENDED) report('ended');
                },
                onError: function (e) { report('error:' + e.data); }
              }
            });
          };
          var api = document.createElement('script');
          api.src = 'https://www.youtube.com/iframe_api';
          api.onerror = function () { report('error:api'); };
          document.head.appendChild(api);
        })();
    """.trimIndent()

    private val scriptHash: String = Base64.getEncoder().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(script.toByteArray(Charsets.UTF_8)),
    )

    /** Only YouTube's API script and the script above may run; frames only from YouTube; no other requests. */
    val contentSecurityPolicy: String =
        "default-src 'none'; script-src 'sha256-$scriptHash' https://www.youtube.com https://s.ytimg.com; " +
            "frame-src https://www.youtube-nocookie.com https://www.youtube.com; style-src 'unsafe-inline'; " +
            "img-src 'none'; connect-src 'none'; base-uri 'none'; form-action 'none'"

    /** The page for [videoId]; throws for anything but an 11-character YouTube id, so nothing else reaches the HTML. */
    fun html(videoId: String, muted: Boolean, host: TrailerHost = TrailerHost.NOCOOKIE): String {
        require(isValidVideoId(videoId)) { "invalid YouTube id" }
        return listOf(
            "<!doctype html>",
            "<html>",
            "<head>",
            "<meta charset=\"utf-8\">",
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">",
            "<meta name=\"referrer\" content=\"strict-origin-when-cross-origin\">",
            "<meta http-equiv=\"Content-Security-Policy\" content=\"$contentSecurityPolicy\">",
            "<style>html, body, #player { margin: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }</style>",
            "</head>",
            "<body>",
            "<div id=\"player\" data-video=\"$videoId\" data-muted=\"${if (muted) "1" else "0"}\" data-host=\"${host.origin}\"></div>",
            "<script>$script</script>",
            "</body>",
            "</html>",
        ).joinToString("\n")
    }
}
