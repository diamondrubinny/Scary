// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.realdebrid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.Movie
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.player.LanguagePrefs
import io.github.diamondrubinny.scary.ui.player.LanguagePrefButtons
import io.github.diamondrubinny.scary.ui.player.OnlineSubtitlesToggle
import io.github.diamondrubinny.scary.ui.player.continueDetail
import io.github.diamondrubinny.scary.core.player.WatchEntry
import io.github.diamondrubinny.scary.core.brand.Donation
import io.github.diamondrubinny.scary.core.realdebrid.RealDebrid
import io.github.diamondrubinny.scary.data.RealDebridRepository.Reason
import io.github.diamondrubinny.scary.data.RealDebridRepository.State
import io.github.diamondrubinny.scary.ui.components.FocusSurface
import io.github.diamondrubinny.scary.ui.components.QrImage
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.home.RdFilesState
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.theme.scaryLayout
import java.util.Locale

/** Test tags of the Real-Debrid screen. */
const val REAL_DEBRID_TAG = "realDebrid"
const val REAL_DEBRID_CODE_TAG = "realDebridCode"
const val REAL_DEBRID_FILES_TAG = "realDebridFiles"
const val REAL_DEBRID_HISTORY_TAG = "realDebridHistory"
const val REAL_DEBRID_TORRENTS_BUTTON_TAG = "realDebridTorrentsButton"

/** Files listed under "Continue watching" here; the home row has them all. */
private const val HISTORY_SHOWN = 5

class RealDebridActions(
    val onSignIn: () -> Unit,
    val onCancel: () -> Unit,
    val onSignOut: () -> Unit,
    val onRefresh: () -> Unit,
    val onPlay: (RealDebrid.RdFile) -> Unit,
    val onContinue: (WatchEntry) -> Unit = {},
    val onClearHistory: () -> Unit = {},
    /** The languages the player chooses by itself in every film. */
    val onLanguages: (LanguagePrefs) -> Unit = {},
    /** Whether the player may look for subtitles online by the film's name. */
    val onOnlineSubtitles: (Boolean) -> Unit = {},
    /** The films a file may be, to choose from when correcting SCARY's guess. */
    val onCandidates: suspend (RealDebrid.RdFile) -> List<Movie> = { emptyList() },
    /** The viewer's word on a file's film: a film id, "" for "not a film in the library", null to let SCARY guess. */
    val onSetFilm: (RealDebrid.RdFile, String?) -> Unit = { _, _ -> },
    /** Deletes a file from the account; null in a build without the sources feature, which then offers no deleting. */
    val onDeleteFile: ((RealDebrid.RdFile) -> Unit)? = null,
    /** Opens "Add a torrent"; null in a build without the sources feature, which then shows no way in. */
    val onTorrents: (() -> Unit)? = null,
)

/** Test tags of the file correction window. */
const val REAL_DEBRID_FILM_TAG = "realDebridFilm"
const val REAL_DEBRID_NOT_A_FILM_TAG = "realDebridNotAFilm"
const val REAL_DEBRID_GUESS_AGAIN_TAG = "realDebridGuessAgain"
const val REAL_DEBRID_DELETE_FILE_TAG = "realDebridDeleteFile"
const val REAL_DEBRID_DELETE_FILE_CONFIRM_TAG = "realDebridDeleteFileConfirm"

/** A file's film row on the Real-Debrid screen: the film's id for the test tag. */
fun filmTag(movieId: String) = "film:$movieId"

/**
 * The viewer's Real-Debrid account: what connecting does (only their own files are played; SCARY looks for nothing
 * else), the code to enter at real-debrid.com/device while the TV waits, and once connected, the account and its
 * videos. Choosing one plays it in the internal player.
 */
@Composable
fun RealDebridScreen(
    state: State,
    files: RdFilesState,
    actions: RealDebridActions,
    history: List<WatchEntry> = emptyList(),
    languages: LanguagePrefs = LanguagePrefs(),
    onlineSubtitles: Boolean = true,
    /** The film or series each file is (by file key), by SCARY's guess or the viewer's word; files that are none are absent. */
    fileFilms: Map<String, Movie> = emptyMap(),
) {
    var correcting by remember { mutableStateOf<RealDebrid.RdFile?>(null) }
    correcting?.let { file ->
        FilmChooser(file, fileFilms[file.key], actions, onDone = { correcting = null })
    }
    val firstFocus = remember { FocusRequester() }
    // Leaving while waiting stops asking Real-Debrid: approving later would not reach the TV anyway.
    val waiting by rememberUpdatedState(state is State.Waiting)
    val cancel by rememberUpdatedState(actions.onCancel)
    DisposableEffect(Unit) {
        onDispose { if (waiting) cancel() }
    }
    LaunchedEffect(state::class) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    val narrow = scaryLayout.narrow
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SidePadding, vertical = 28.dp)
            .testTag(REAL_DEBRID_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(
            stringResource(R.string.rd_title),
            style = ScaryType.Display.copy(fontSize = ScaryType.Display.fontSize * 0.7f),
            modifier = Modifier.semantics { heading() },
        )
        BasicText(stringResource(if (actions.onTorrents == null) R.string.rd_what else R.string.rd_what_sources), style = ScaryType.Body)
        BasicText(
            text = statusText(state),
            style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state) {
                State.SignedOut, is State.Failed ->
                    ScaryButton(stringResource(R.string.rd_sign_in), onClick = actions.onSignIn, primary = true, focusRequester = firstFocus)
                is State.Waiting -> ScaryButton(stringResource(R.string.rd_cancel), onClick = actions.onCancel, focusRequester = firstFocus)
                is State.SignedIn -> {
                    ScaryButton(stringResource(R.string.rd_refresh), onClick = actions.onRefresh, focusRequester = firstFocus)
                    actions.onTorrents?.let { ScaryButton(stringResource(R.string.rd_torrent_open), onClick = it, modifier = Modifier.testTag(REAL_DEBRID_TORRENTS_BUTTON_TAG)) }
                    ScaryButton(stringResource(R.string.rd_sign_out), onClick = actions.onSignOut)
                    if (history.isNotEmpty()) ScaryButton(stringResource(R.string.rd_clear_history), onClick = actions.onClearHistory)
                }
            }
        }
        if (state is State.Waiting) {
            WaitingForCode(state, narrow)
        }
        if (state is State.SignedIn) {
            // The player's languages, for every film: also in the player's "Audio and subtitles" window.
            BasicText(stringResource(R.string.tracks_every_film), style = ScaryType.RowTitle, modifier = Modifier.semantics { heading() })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LanguagePrefButtons(languages, actions.onLanguages)
                OnlineSubtitlesToggle(onlineSubtitles, actions.onOnlineSubtitles)
            }
            // What leaves the TV when it is on, in plain words.
            BasicText(stringResource(R.string.tracks_online_privacy), style = ScaryType.Caption.copy(color = ScaryColors.Ash))
        }
        if (state is State.SignedIn && history.isNotEmpty()) {
            // Where the viewer stopped, and the spots they marked: on this TV only.
            BasicText(stringResource(R.string.row_continue), style = ScaryType.RowTitle, modifier = Modifier.semantics { heading() })
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag(REAL_DEBRID_HISTORY_TAG)) {
                history.take(HISTORY_SHOWN).forEach { entry -> HistoryRow(entry) { actions.onContinue(entry) } }
            }
        }
        if (state is State.SignedIn) {
            BasicText(stringResource(R.string.rd_files_title), style = ScaryType.RowTitle, modifier = Modifier.semantics { heading() })
            // How to correct a guess, in plain words (holding OK is the remote's long press).
            if (fileFilms.isNotEmpty() || (files as? RdFilesState.Ready)?.files?.isNotEmpty() == true) {
                BasicText(
                    stringResource(if (actions.onDeleteFile == null) R.string.rd_film_hint else R.string.rd_film_hint_delete),
                    style = ScaryType.Caption.copy(color = ScaryColors.Ash),
                )
            }
            when (files) {
                RdFilesState.Idle, RdFilesState.Loading -> BasicText(stringResource(R.string.rd_files_loading), style = ScaryType.Caption)
                RdFilesState.Unreachable -> BasicText(stringResource(R.string.rd_unreachable), style = ScaryType.Caption)
                is RdFilesState.Ready -> if (files.files.isEmpty()) {
                    BasicText(stringResource(R.string.rd_files_empty), style = ScaryType.Caption)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag(REAL_DEBRID_FILES_TAG),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(files.files, key = { it.key }) { file -> FileRow(file, fileFilms[file.key], onLongClick = { correcting = file }) { actions.onPlay(file) } }
                    }
                }
            }
        }
        BasicText(stringResource(R.string.rd_privacy), style = ScaryType.Caption)
    }
}

@Composable
private fun WaitingForCode(state: State.Waiting, narrow: Boolean) {
    val qr = remember(state.verificationUrl) { Donation.qr(state.verificationUrl) }
    val steps: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(stringResource(R.string.rd_step_open, state.verificationUrl.removePrefix("https://")), style = ScaryType.Body.copy(textDirection = TextDirection.Content))
            BasicText(stringResource(R.string.rd_step_code), style = ScaryType.Body)
            // The code is Latin letters and digits: shown big, left to right, and read out letter by letter.
            BasicText(
                state.userCode,
                style = ScaryType.Display.copy(color = ScaryColors.Candle, textDirection = TextDirection.Ltr),
                modifier = Modifier.testTag(REAL_DEBRID_CODE_TAG).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    if (narrow) {
        steps()
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
            QrImage(code = qr, size = QR_SIZE, description = stringResource(R.string.rd_qr_description))
            steps()
        }
    }
}

@Composable
private fun FileRow(file: RealDebrid.RdFile, film: Movie?, onLongClick: () -> Unit, onClick: () -> Unit) {
    val language = Language.fromCode(LocalConfiguration.current.locales[0].language)
    val filmLabel = film?.let { "${it.displayTitle(language)} (${it.yearsLabel})" }
    FocusSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.fillMaxWidth().then(if (film != null) Modifier.testTag(filmTag(film.id)) else Modifier),
        shape = RoundedCornerShape(4.dp),
        focusedScale = 1.02f,
        containerColor = ScaryColors.Crypt,
        focusedContainerColor = ScaryColors.Bone,
        contentDescription = listOfNotNull(stringResource(R.string.rd_play_file, file.name), filmLabel?.let { stringResource(R.string.rd_file_is, it) }).joinToString(", "),
    ) { focused ->
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(
                    file.name,
                    style = ScaryType.Body.copy(color = if (focused) ScaryColors.Void else ScaryColors.Bone, textDirection = TextDirection.Content),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (filmLabel != null) {
                    BasicText(
                        filmLabel,
                        style = ScaryType.Caption.copy(color = if (focused) ScaryColors.Void else ScaryColors.Candle, textDirection = TextDirection.Content),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            BasicText(
                size(file.sizeBytes),
                style = ScaryType.Caption.copy(color = if (focused) ScaryColors.Void else ScaryColors.Ash),
                modifier = Modifier.padding(start = 12.dp).width(80.dp),
            )
        }
    }
}

/**
 * "Which film is this file?": the films it may be (nearest titles first), "Not a film from the library", and "Let
 * SCARY guess again". The viewer's word is kept on this TV, and decides which film the file's subtitles and "Continue
 * watching" belong to.
 */
@Composable
private fun FilmChooser(file: RealDebrid.RdFile, current: Movie?, actions: RealDebridActions, onDone: () -> Unit) {
    val language = Language.fromCode(LocalConfiguration.current.locales[0].language)
    var candidates by remember(file.key) { mutableStateOf<List<Movie>?>(null) }
    LaunchedEffect(file.key) { candidates = actions.onCandidates(file) }
    val first = remember { FocusRequester() }
    // Deleting asks once more, in the same window, before anything leaves the account.
    var deleting by remember(file.key) { mutableStateOf(false) }
    val onDelete = actions.onDeleteFile
    if (deleting && onDelete != null) {
        val keep = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { keep.requestFocus() }
        }
        androidx.compose.ui.window.Dialog(onDismissRequest = onDone) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .background(ScaryColors.Crypt, RoundedCornerShape(4.dp))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicText(stringResource(R.string.rd_file_delete), style = ScaryType.Title, modifier = Modifier.semantics { heading() })
                BasicText(file.name, style = ScaryType.Caption.copy(color = ScaryColors.Ash, textDirection = TextDirection.Content), maxLines = 2, overflow = TextOverflow.Ellipsis)
                BasicText(stringResource(R.string.rd_file_delete_confirm), style = ScaryType.Body, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                // "Keep it" first: a second OK by habit must not delete.
                ScaryButton(stringResource(R.string.rd_torrent_delete_no), onClick = onDone, focusRequester = keep)
                ScaryButton(
                    stringResource(R.string.rd_torrent_delete_yes),
                    onClick = { onDelete(file); onDone() },
                    modifier = Modifier.testTag(REAL_DEBRID_DELETE_FILE_CONFIRM_TAG),
                )
            }
        }
        return
    }
    LaunchedEffect(candidates == null) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDone) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .background(ScaryColors.Crypt, RoundedCornerShape(4.dp))
                .padding(22.dp)
                .testTag(REAL_DEBRID_FILM_TAG),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(stringResource(R.string.rd_film_title), style = ScaryType.Title, modifier = Modifier.semantics { heading() })
            BasicText(file.name, style = ScaryType.Caption.copy(color = ScaryColors.Ash, textDirection = TextDirection.Content), maxLines = 2, overflow = TextOverflow.Ellipsis)
            val shown = candidates
            if (shown == null) {
                BasicText(stringResource(R.string.rd_files_loading), style = ScaryType.Caption, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            shown?.forEachIndexed { i, movie ->
                ScaryButton(
                    "${movie.displayTitle(language)} (${movie.yearsLabel})" + if (movie.id == current?.id) " ✓" else "",
                    onClick = { actions.onSetFilm(file, movie.id); onDone() },
                    focusRequester = if (i == 0) first else null,
                    modifier = Modifier.testTag(filmTag(movie.id)),
                )
            }
            ScaryButton(
                stringResource(R.string.rd_film_none),
                onClick = { actions.onSetFilm(file, ""); onDone() },
                focusRequester = if (shown.isNullOrEmpty()) first else null,
                modifier = Modifier.testTag(REAL_DEBRID_NOT_A_FILM_TAG),
            )
            ScaryButton(
                stringResource(R.string.rd_film_guess),
                onClick = { actions.onSetFilm(file, null); onDone() },
                modifier = Modifier.testTag(REAL_DEBRID_GUESS_AGAIN_TAG),
            )
            if (onDelete != null) {
                ScaryButton(
                    stringResource(R.string.rd_file_delete),
                    onClick = { deleting = true },
                    quiet = true,
                    modifier = Modifier.testTag(REAL_DEBRID_DELETE_FILE_TAG),
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: WatchEntry, onClick: () -> Unit) {
    val detail = continueDetail(entry)
    FocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        focusedScale = 1.02f,
        containerColor = ScaryColors.Crypt,
        focusedContainerColor = ScaryColors.Bone,
        contentDescription = "${entry.name}, $detail",
    ) { focused ->
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                entry.name,
                style = ScaryType.Body.copy(color = if (focused) ScaryColors.Void else ScaryColors.Bone, textDirection = TextDirection.Content),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            BasicText(
                detail,
                style = ScaryType.Caption.copy(color = if (focused) ScaryColors.Void else ScaryColors.Candle),
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** "4.2 GB", "700 MB"; empty when Real-Debrid did not say. */
fun size(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes >= 1L shl 30 -> String.format(Locale.ROOT, "%.1f GB", bytes / (1L shl 30).toDouble())
    else -> String.format(Locale.ROOT, "%d MB", bytes / (1L shl 20))
}

@Composable
private fun statusText(state: State): String = when (state) {
    State.SignedOut -> stringResource(R.string.rd_signed_out)
    is State.Waiting -> stringResource(R.string.rd_waiting)
    is State.SignedIn -> when {
        state.username.isBlank() -> stringResource(R.string.rd_connected)
        state.premium && state.expiration.isNotBlank() -> stringResource(R.string.rd_signed_in_premium, state.username, state.expiration.take(10))
        state.premium -> stringResource(R.string.rd_signed_in, state.username)
        else -> stringResource(R.string.rd_signed_in_free, state.username)
    }
    is State.Failed -> stringResource(
        when (state.reason) {
            Reason.UNREACHABLE -> R.string.rd_unreachable
            Reason.EXPIRED -> R.string.rd_expired
            Reason.STORAGE -> R.string.rd_storage
            Reason.SESSION_ENDED -> R.string.rd_session_ended
            Reason.BUSY -> R.string.rd_busy
        },
    )
}

/** Big enough to scan from the sofa. */
private val QR_SIZE = 220.dp
