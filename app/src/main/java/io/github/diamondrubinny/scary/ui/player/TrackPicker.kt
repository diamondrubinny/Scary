// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.player.LanguagePrefs
import io.github.diamondrubinny.scary.core.player.TrackInfo
import io.github.diamondrubinny.scary.core.player.TrackKind
import io.github.diamondrubinny.scary.core.player.TrackNames
import io.github.diamondrubinny.scary.ui.components.FocusSurface
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import java.util.Locale

/** Test tags of the track window. */
const val PLAYER_TRACKS_TAG = "playerTracks"
const val PLAYER_TRACKS_BUTTON_TAG = "playerTracksButton"
const val PLAYER_SUBTITLES_OFF_TAG = "playerSubtitlesOff"
const val PREF_AUDIO_TAG = "prefAudioLanguage"
const val PREF_SUBTITLES_TAG = "prefSubtitleLanguage"

/** A track row's test tag: "track:<id>". */
fun trackTag(id: String) = "track:$id"

/** What the track window shows and changes. */
class TrackPickerState(
    val audio: List<TrackInfo>,
    val text: List<TrackInfo>,
    /** The track playing now, by [TrackInfo.id]. */
    val selectedAudio: String?,
    val selectedText: String?,
    /** A subtitle file being fetched. */
    val loadingText: String?,
    val prefs: LanguagePrefs,
)

class TrackPickerActions(
    val onAudio: (TrackInfo) -> Unit,
    /** A subtitle track, or null for "Off". */
    val onText: (TrackInfo?) -> Unit,
    val onPrefs: (LanguagePrefs) -> Unit,
    val onClose: () -> Unit,
)

/**
 * "Audio and subtitles": every audio and subtitle track of the file (and the subtitle files of its torrent), each with
 * its language, title, format, channels and marks; tracks this TV cannot play are shown dimmed, with why. Below, the
 * languages chosen by themselves in every film. Arrows and OK only.
 */
@Composable
fun TrackPicker(state: TrackPickerState, actions: TrackPickerActions, focusRequester: FocusRequester, modifier: Modifier = Modifier) {
    Column(
        modifier
            .widthIn(max = 560.dp)
            .background(ScaryColors.Crypt.copy(alpha = 0.96f), RoundedCornerShape(12.dp))
            .border(1.dp, ScaryColors.Candle.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
            .testTag(PLAYER_TRACKS_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(stringResource(R.string.player_tracks), style = ScaryType.Title, modifier = Modifier.semantics { heading() })
        // The first row takes the focus: the audio playing now, else the first audio track, else "Off".
        val firstAudio = state.audio.firstOrNull { it.id == state.selectedAudio } ?: state.audio.firstOrNull()
        if (state.audio.isNotEmpty()) {
            Heading(stringResource(R.string.tracks_audio))
            state.audio.forEachIndexed { index, track ->
                TrackRow(
                    title = trackTitle(track, index),
                    details = trackDetails(track, loading = false),
                    selected = track.id == state.selectedAudio,
                    enabled = track.supported,
                    onClick = { actions.onAudio(track) },
                    modifier = (if (track == firstAudio) Modifier.focusRequester(focusRequester) else Modifier).testTag(trackTag(track.id)),
                )
            }
        }
        Heading(stringResource(R.string.tracks_subtitles))
        TrackRow(
            title = stringResource(R.string.player_subtitles_off),
            details = "",
            selected = state.selectedText == null,
            enabled = true,
            onClick = { actions.onText(null) },
            modifier = (if (firstAudio == null) Modifier.focusRequester(focusRequester) else Modifier).testTag(PLAYER_SUBTITLES_OFF_TAG),
        )
        state.text.forEachIndexed { index, track ->
            TrackRow(
                title = trackTitle(track, index),
                details = trackDetails(track, loading = track.id == state.loadingText),
                selected = track.id == state.selectedText,
                enabled = track.supported,
                onClick = { actions.onText(track) },
                modifier = Modifier.testTag(trackTag(track.id)),
            )
        }
        Heading(stringResource(R.string.tracks_every_film))
        LanguagePrefButtons(state.prefs, actions.onPrefs)
        ScaryButton(stringResource(R.string.player_close), onClick = actions.onClose)
    }
}

/**
 * The languages the player chooses by itself in every film: audio (or the file's own default) and subtitles (or off).
 * Each press moves to the next language. In the track window and on the Real-Debrid screen.
 */
@Composable
fun LanguagePrefButtons(prefs: LanguagePrefs, onPrefs: (LanguagePrefs) -> Unit) {
    val options = listOf<String?>(null) + LanguagePrefs.OFFERED
    ScaryButton(
        stringResource(R.string.tracks_pref_audio, prefs.audio?.let { languageName(it) } ?: stringResource(R.string.tracks_as_in_file)),
        onClick = { onPrefs(prefs.copy(audio = options.after(prefs.audio))) },
        modifier = Modifier.testTag(PREF_AUDIO_TAG),
    )
    ScaryButton(
        stringResource(R.string.tracks_pref_subtitles, prefs.subtitles?.let { languageName(it) } ?: stringResource(R.string.player_subtitles_off)),
        onClick = { onPrefs(prefs.copy(subtitles = options.after(prefs.subtitles))) },
        modifier = Modifier.testTag(PREF_SUBTITLES_TAG),
    )
}

/** The next option after [current], round again after the last. */
private fun List<String?>.after(current: String?): String? = this[(indexOf(current) + 1).mod(size)]

@Composable
private fun Heading(text: String) {
    BasicText(text, style = ScaryType.RowTitle, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

@Composable
private fun TrackRow(title: String, details: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val mark = if (selected) "● " else "○ "
    val description = listOf(title, details).filter { it.isNotBlank() }.joinToString(", ")
    FocusSurface(
        // A track this TV cannot play stays reachable, so its reason can be read, but does nothing.
        onClick = { if (enabled) onClick() },
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(8.dp),
        focusedScale = 1.02f,
        containerColor = if (selected) ScaryColors.Void else ScaryColors.Void.copy(alpha = 0.5f),
        focusedContainerColor = ScaryColors.Bone,
        contentDescription = description,
    ) { focused ->
        val color = if (focused) ScaryColors.Void else ScaryColors.Bone
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(mark + title, style = ScaryType.BodyStrong.copy(color = color, textDirection = TextDirection.Content), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (details.isNotBlank()) {
                    BasicText(details, style = ScaryType.Caption.copy(color = color, textDirection = TextDirection.Content), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A language's name in the viewer's language, capitalized ("English", "עברית"). */
@Composable
fun languageName(code: String): String {
    val viewer = LocalLocale.current.platformLocale
    val name = Locale.forLanguageTag(code).getDisplayLanguage(viewer)
    return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(viewer) else it.toString() }.ifBlank { code }
}

/** The track's language, else its title, else "Track 2". */
@Composable
fun trackTitle(track: TrackInfo, index: Int): String =
    track.language?.let { languageName(it) } ?: track.label ?: stringResource(R.string.track_number, index + 1)

/** Its title from the file (when the language is the name), format, channels, marks, and why it cannot play. */
@Composable
fun trackDetails(track: TrackInfo, loading: Boolean): String {
    val parts = mutableListOf<String>()
    if (track.language != null) track.label?.let(parts::add)
    TrackNames.format(track.mimeType)?.let(parts::add)
    if (track.kind == TrackKind.AUDIO) {
        when (track.channels) {
            1 -> parts += stringResource(R.string.track_mono)
            2 -> parts += stringResource(R.string.track_stereo)
            else -> TrackNames.surround(track.channels)?.let(parts::add)
        }
    }
    if (track.isDefault) parts += stringResource(R.string.track_default)
    if (track.isForced) parts += stringResource(R.string.track_forced)
    if (track.isSdh) parts += stringResource(R.string.track_sdh)
    if (track.isCommentary) parts += stringResource(R.string.track_commentary)
    if (track.isDescription) parts += stringResource(R.string.track_description)
    if (track.external) parts += stringResource(R.string.track_external)
    if (!track.supported) parts += stringResource(R.string.track_unsupported)
    if (loading) parts += stringResource(R.string.player_subtitle_loading)
    return parts.distinct().joinToString(" · ")
}

/** "English · 5.1 · Dolby Digital Plus": the audio playing now, for the info line and the window's button. */
@Composable
fun audioSummary(track: TrackInfo?, index: Int): String? {
    track ?: return null
    val parts = mutableListOf(trackTitle(track, index))
    when (track.channels) {
        1 -> parts += stringResource(R.string.track_mono)
        2 -> parts += stringResource(R.string.track_stereo)
        else -> TrackNames.surround(track.channels)?.let(parts::add)
    }
    TrackNames.format(track.mimeType)?.let(parts::add)
    return parts.joinToString(" · ")
}
