// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.realdebrid

import io.github.diamondrubinny.scary.data.DebridAccounts.Service
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.realdebrid.Magnets
import io.github.diamondrubinny.scary.core.realdebrid.RealDebrid.TorrentState
import io.github.diamondrubinny.scary.core.realdebrid.RealDebrid.TorrentStatus
import io.github.diamondrubinny.scary.data.RealDebridRepository.PullError
import io.github.diamondrubinny.scary.ui.components.FocusSurface
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.components.ScaryTextField
import io.github.diamondrubinny.scary.ui.home.RdPullState
import io.github.diamondrubinny.scary.ui.home.RdTorrentsState
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType

/** Test tags of the torrents screen. */
const val RD_TORRENTS_TAG = "rdTorrents"
const val RD_TORRENTS_FIELD_TAG = "rdTorrentsField"
const val RD_TORRENTS_PULL_TAG = "rdTorrentsPull"
const val RD_TORRENTS_STATUS_TAG = "rdTorrentsStatus"
const val RD_TORRENTS_LIST_TAG = "rdTorrentsList"
const val RD_TORRENTS_DIALOG_TAG = "rdTorrentsDialog"
const val RD_TORRENTS_DELETE_TAG = "rdTorrentsDelete"
const val RD_TORRENTS_CONFIRM_TAG = "rdTorrentsConfirm"
const val RD_TORRENTS_SEARCH_TAG = "rdTorrentsSearch"
const val RD_TORRENTS_PULL_TORBOX_TAG = "rdTorrentsPullTorBox"

/** A torrent's row in the list: its id for the test tag. */
fun torrentTag(id: String) = "torrent:$id"

class RdTorrentsActions(
    val onPull: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onPlay: (String) -> Unit,
    val onDelete: (String) -> Unit,
    /** Opens "Search your indexer". */
    val onSearch: () -> Unit = {},
    /** Adds the link to the account the viewer chose, when both are connected. */
    val onPullTo: (String, Service) -> Unit = { text, _ -> onPull(text) },
)

/**
 * "Add a torrent" (the sources feature, docs/TORRENT_SCRAPER_PLAN.md): the viewer's own magnet link goes to their own
 * Real-Debrid account, and its video plays in SCARY's player as soon as Real-Debrid has it. Under it, the account's
 * newest torrents: a finished one plays, any one can be deleted (after a second "yes").
 */
@Composable
fun RdTorrentsScreen(
    state: RdTorrentsState,
    actions: RdTorrentsActions,
    /** The connected accounts. With both, the viewer says where each link goes: there is one button for each. */
    services: List<Service> = listOf(Service.REAL_DEBRID),
) {
    var input by rememberSaveable { mutableStateOf("") }
    var chosen by remember { mutableStateOf<TorrentStatus?>(null) }
    chosen?.let { torrent -> TorrentDialog(torrent, actions, onDone = { chosen = null }) }
    val fieldFocus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val working = state.pull == RdPullState.Working
    val label = stringResource(R.string.rd_torrent_magnet_label)
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { fieldFocus.requestFocus() }
    }
    // The link is done with once Real-Debrid took it.
    LaunchedEffect(state.pull) {
        if (state.pull is RdPullState.Downloading) input = ""
    }
    val choose = services.size > 1
    fun pull() {
        // With two accounts the keyboard's "Done" adds nothing: the viewer presses the account's own button.
        if (!working && input.isNotBlank() && !choose) actions.onPull(input)
    }
    fun pullTo(service: Service) {
        if (!working && input.isNotBlank()) actions.onPullTo(input, service)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SidePadding, vertical = 28.dp)
            .testTag(RD_TORRENTS_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(
            stringResource(R.string.rd_torrent_title),
            style = ScaryType.Display.copy(fontSize = ScaryType.Display.fontSize * 0.7f),
            modifier = Modifier.semantics { heading() },
        )
        BasicText(stringResource(R.string.rd_torrent_what), style = ScaryType.Body)
        BasicText(label, style = ScaryType.RowTitle, modifier = Modifier.semantics { heading() })
        Box(
            Modifier
                .fillMaxWidth()
                .background(ScaryColors.Void, RoundedCornerShape(4.dp))
                .border(2.dp, if (focused) ScaryColors.Candle else ScaryColors.Bone.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (input.isEmpty()) BasicText(stringResource(R.string.rd_torrent_magnet_hint), style = ScaryType.Body.copy(color = ScaryColors.Ash, textDirection = TextDirection.Ltr))
            ScaryTextField(
                value = input,
                onValueChange = { input = it.trim().take(Magnets.MAX_LENGTH) },
                // A magnet link is Latin letters and digits: left to right in every language.
                textStyle = ScaryType.BodyStrong.copy(textDirection = TextDirection.Ltr),
                cursorBrush = SolidColor(ScaryColors.Candle),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { pull() }),
                interactionSource = interaction,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldFocus)
                    .testTag(RD_TORRENTS_FIELD_TAG)
                    .semantics { contentDescription = label },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (choose) {
                ScaryButton(stringResource(R.string.debrid_add_to_rd), onClick = { pullTo(Service.REAL_DEBRID) }, primary = true, modifier = Modifier.testTag(RD_TORRENTS_PULL_TAG))
                ScaryButton(stringResource(R.string.debrid_add_to_torbox), onClick = { pullTo(Service.TORBOX) }, primary = true, modifier = Modifier.testTag(RD_TORRENTS_PULL_TORBOX_TAG))
            } else {
                ScaryButton(stringResource(R.string.rd_torrent_pull_action), onClick = ::pull, primary = true, modifier = Modifier.testTag(RD_TORRENTS_PULL_TAG))
            }
            ScaryButton(stringResource(R.string.rd_sources_open), onClick = actions.onSearch, modifier = Modifier.testTag(RD_TORRENTS_SEARCH_TAG))
            ScaryButton(stringResource(R.string.rd_torrent_refresh), onClick = actions.onRefresh)
            state.active?.let { BasicText(stringResource(R.string.rd_torrent_active_slots, it.active, it.limit), style = ScaryType.Caption.copy(color = ScaryColors.Ash)) }
        }
        // What became of the link, said aloud as it changes; the words carry it, not a colour.
        BasicText(
            text = pullText(state.pull),
            style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle),
            modifier = Modifier.testTag(RD_TORRENTS_STATUS_TAG).semantics { liveRegion = LiveRegionMode.Polite },
        )
        BasicText(stringResource(R.string.rd_torrent_recent_title), style = ScaryType.RowTitle, modifier = Modifier.semantics { heading() })
        val torrents = state.torrents
        when {
            torrents == null -> BasicText(stringResource(R.string.rd_files_loading), style = ScaryType.Caption)
            state.unreachable && torrents.isEmpty() -> BasicText(stringResource(R.string.rd_unreachable), style = ScaryType.Caption)
            torrents.isEmpty() -> BasicText(stringResource(R.string.rd_torrent_recent_empty), style = ScaryType.Caption)
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag(RD_TORRENTS_LIST_TAG),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(torrents, key = { it.id }) { torrent -> TorrentRow(torrent) { chosen = torrent } }
            }
        }
    }
}

@Composable
internal fun pullText(pull: RdPullState): String = when (pull) {
    RdPullState.Idle -> stringResource(R.string.rd_auto_play_when_ready)
    RdPullState.Working -> stringResource(R.string.rd_torrent_working)
    RdPullState.Invalid -> stringResource(R.string.rd_torrent_invalid)
    is RdPullState.Downloading -> stringResource(R.string.rd_torrent_downloading, pull.progress)
    is RdPullState.Failed -> stringResource(
        when (pull.reason) {
            PullError.UNREACHABLE -> R.string.rd_unreachable
            PullError.SLOTS_FULL -> R.string.rd_torrent_error_slots
            PullError.REFUSED -> R.string.rd_torrent_error_refused
            PullError.TOO_BIG -> R.string.rd_torrent_error_too_big
            PullError.NO_VIDEO -> R.string.rd_torrent_error_no_video
            PullError.DEAD -> R.string.rd_torrent_error_dead
            PullError.NOT_ALLOWED -> R.string.rd_torrent_error_not_allowed
            PullError.LIMIT -> R.string.rd_torrent_error_limit
        },
    )
}

@Composable
private fun stateText(torrent: TorrentStatus): String = when (torrent.state) {
    TorrentState.READY -> stringResource(R.string.rd_torrent_state_ready)
    TorrentState.CONVERTING, TorrentState.CHOOSE_FILES, TorrentState.QUEUED -> stringResource(R.string.rd_torrent_state_waiting)
    TorrentState.DOWNLOADING, TorrentState.FINISHING -> stringResource(R.string.rd_torrent_state_downloading, torrent.progress)
    TorrentState.FAILED -> stringResource(R.string.rd_torrent_state_failed)
}

@Composable
private fun TorrentRow(torrent: TorrentStatus, onClick: () -> Unit) {
    val detail = listOf(stateText(torrent), size(torrent.sizeBytes)).filter { it.isNotEmpty() }.joinToString(" · ")
    FocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(torrentTag(torrent.id)),
        shape = RoundedCornerShape(4.dp),
        focusedScale = 1.02f,
        containerColor = ScaryColors.Crypt,
        focusedContainerColor = ScaryColors.Bone,
        contentDescription = "${torrent.name}, $detail",
    ) { focused ->
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                torrent.name,
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

/** One torrent: play it when it is finished, or delete it from the account, which asks once more first. */
@Composable
private fun TorrentDialog(torrent: TorrentStatus, actions: RdTorrentsActions, onDone: () -> Unit) {
    var confirming by remember(torrent.id) { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(confirming) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Dialog(onDismissRequest = onDone) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .background(ScaryColors.Crypt, RoundedCornerShape(4.dp))
                .padding(22.dp)
                .testTag(RD_TORRENTS_DIALOG_TAG),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(
                torrent.name,
                style = ScaryType.Title.copy(textDirection = TextDirection.Content),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (confirming) {
                BasicText(stringResource(R.string.rd_torrent_delete_confirm), style = ScaryType.Body, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                // "No" first: a second OK by habit must not delete.
                ScaryButton(stringResource(R.string.rd_torrent_delete_no), onClick = { confirming = false }, focusRequester = first)
                ScaryButton(
                    stringResource(R.string.rd_torrent_delete_yes),
                    onClick = { actions.onDelete(torrent.id); onDone() },
                    modifier = Modifier.testTag(RD_TORRENTS_CONFIRM_TAG),
                )
            } else {
                BasicText(stateText(torrent), style = ScaryType.Caption.copy(color = ScaryColors.Ash))
                val ready = torrent.state == TorrentState.READY
                if (ready) {
                    ScaryButton(stringResource(R.string.rd_torrent_play), onClick = { actions.onPlay(torrent.id); onDone() }, primary = true, focusRequester = first)
                }
                ScaryButton(
                    stringResource(R.string.rd_torrent_delete),
                    onClick = { confirming = true },
                    focusRequester = if (ready) null else first,
                    modifier = Modifier.testTag(RD_TORRENTS_DELETE_TAG),
                )
                ScaryButton(stringResource(R.string.rd_torrent_back), onClick = onDone)
            }
        }
    }
}
