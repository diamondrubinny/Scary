// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.realdebrid

import io.github.diamondrubinny.scary.data.DebridAccounts.Service
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.core.sources.Releases
import io.github.diamondrubinny.scary.core.sources.Torznab
import io.github.diamondrubinny.scary.data.SourcesRepository
import io.github.diamondrubinny.scary.data.SourcesRepository.SaveResult
import io.github.diamondrubinny.scary.ui.components.FocusSurface
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.components.ScaryTextField
import io.github.diamondrubinny.scary.ui.home.RdPullState
import io.github.diamondrubinny.scary.ui.home.RdSearchState
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import kotlinx.coroutines.launch

/** Test tags of the indexer search screen. */
const val RD_SOURCES_TAG = "rdSources"
const val RD_SOURCES_FIELD_TAG = "rdSourcesField"
const val RD_SOURCES_SEARCH_TAG = "rdSourcesSearch"
const val RD_SOURCES_BEST_TAG = "rdSourcesBest"
const val RD_SOURCES_SORT_TAG = "rdSourcesSort"
const val RD_SOURCES_STATUS_TAG = "rdSourcesStatus"
const val RD_SOURCES_LIST_TAG = "rdSourcesList"
const val RD_SOURCES_INDEXER_TAG = "rdSourcesIndexer"
const val RD_SOURCES_ADDRESS_TAG = "rdSourcesAddress"
const val RD_SOURCES_KEY_TAG = "rdSourcesKey"
const val RD_SOURCES_SAVE_TAG = "rdSourcesSave"
const val RD_SOURCES_CHECK_TAG = "rdSourcesCheck"
const val RD_SOURCES_CHECK_RESULT_TAG = "rdSourcesCheckResult"
const val RD_SOURCES_SAVED_TAG = "rdSourcesSaved"
const val RD_SOURCES_TOR_TAG = "rdSourcesTor"

/** The Tor app whose SOCKS port a search through Tor uses, when the viewer installed it; SCARY only opens it. */
private const val ORBOT_PACKAGE = "org.torproject.android"

/** The "Remove" button of the source at this place in the list. */
fun sourceRemoveTag(index: Int) = "rdSourcesRemove:$index"
const val RD_SOURCES_CONFIRM_TAG = "rdSourcesConfirm"
const val RD_SOURCES_PULL_TAG = "rdSourcesPull"
const val RD_SOURCES_PULL_TORBOX_TAG = "rdSourcesPullTorBox"

/** A release's row in the results: its info-hash for the test tag. */
fun releaseTag(infoHash: String) = "release:$infoHash"

/** A filter's button: its name for the test tag. */
fun filterTag(filter: Releases.Filter) = "filter:${filter.name}"

class RdSourcesActions(
    val onSearch: (String) -> Unit,
    val onPull: (Releases.Candidate) -> Unit,
    /** Adds the release to the account the viewer chose, when both are connected. */
    val onPullTo: (Releases.Candidate, Service) -> Unit = { release, _ -> onPull(release) },
    val onSaveIndexer: suspend (address: String, key: String) -> SaveResult,
    val onRemoveIndexer: () -> Unit,
    /** Asks each source whether it answers, and how fast; only at the viewer's press. */
    val onCheckSource: suspend () -> List<SourcesRepository.Check> = { emptyList() },
    /** Removes the source at this place in the list. */
    val onRemoveSource: (Int) -> Unit = {},
    /** Turns "search through Tor" on or off. */
    val onSetTor: (Boolean) -> Unit = {},
)

/**
 * "Search your indexer" (the sources feature, docs/TORRENT_SCRAPER_PLAN.md): the viewer's words go to the Torznab
 * indexer they set up themselves, and its releases are listed best first. Nothing is added to the account until the
 * viewer says yes to one release, "Suggest the best" included; then it plays in SCARY's player.
 */
@Composable
fun RdSourcesScreen(
    search: RdSearchState,
    pull: RdPullState,
    indexerHost: String?,
    actions: RdSourcesActions,
    /** All of the viewer's sources, as they are shown; the first is [indexerHost]. */
    sources: List<String> = listOfNotNull(indexerHost),
    /** Whether searches go through the Tor app on this TV. */
    throughTor: Boolean = false,
    /** The film this search was opened for, already in the line (and already asked for). */
    initialQuery: String = "",
    /** The connected accounts. With both, the viewer says where a release goes. */
    services: List<Service> = listOf(Service.REAL_DEBRID),
) {
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    var filter by rememberSaveable { mutableStateOf(Releases.Filter.ALL) }
    var sort by rememberSaveable { mutableStateOf(Releases.Sort.SMART) }
    var settingUp by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<Releases.Candidate?>(null) }
    if (settingUp) IndexerDialog(sources, throughTor, actions, onDone = { settingUp = false })
    chosen?.let { release ->
        ReleaseDialog(
            release,
            choose = services.size > 1,
            onPull = { service -> if (service == null) actions.onPull(release) else actions.onPullTo(release, service) },
            onDone = { chosen = null },
        )
    }

    val firstFocus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(indexerHost == null) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    val all = (search as? RdSearchState.Ready)?.candidates.orEmpty()
    val shown = remember(all, filter, sort) { Releases.arrange(all, filter, sort) }
    val label = stringResource(R.string.rd_scraper_hint)
    fun run() {
        if (query.isNotBlank()) actions.onSearch(query)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SidePadding, vertical = 28.dp)
            .testTag(RD_SOURCES_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(
            stringResource(R.string.rd_scraper_title),
            style = ScaryType.Display.copy(fontSize = ScaryType.Display.fontSize * 0.7f),
            modifier = Modifier.semantics { heading() },
        )
        BasicText(stringResource(R.string.rd_sources_what), style = ScaryType.Body)
        if (indexerHost == null) {
            BasicText(stringResource(R.string.rd_sources_not_configured), style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle))
            ScaryButton(
                stringResource(R.string.rd_sources_indexer),
                onClick = { settingUp = true },
                primary = true,
                focusRequester = firstFocus,
                modifier = Modifier.testTag(RD_SOURCES_INDEXER_TAG),
            )
            return@Column
        }
        Box(
            Modifier
                .fillMaxWidth()
                .background(ScaryColors.Void, RoundedCornerShape(4.dp))
                .border(2.dp, if (focused) ScaryColors.Candle else ScaryColors.Bone.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (query.isEmpty()) BasicText(label, style = ScaryType.Body.copy(color = ScaryColors.Ash))
            ScaryTextField(
                value = query,
                onValueChange = { query = it.take(Torznab.MAX_QUERY) },
                textStyle = ScaryType.BodyStrong.copy(textDirection = TextDirection.Content),
                cursorBrush = SolidColor(ScaryColors.Candle),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { run() }),
                interactionSource = interaction,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(firstFocus)
                    .testTag(RD_SOURCES_FIELD_TAG)
                    .semantics { contentDescription = label },
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ScaryButton(stringResource(R.string.rd_scraper_action), onClick = ::run, primary = true, modifier = Modifier.testTag(RD_SOURCES_SEARCH_TAG))
            if (all.isNotEmpty()) {
                // Offers one release; the viewer still says yes before anything is added.
                ScaryButton(
                    stringResource(R.string.rd_scraper_auto_best),
                    onClick = { chosen = Releases.best(all) },
                    modifier = Modifier.testTag(RD_SOURCES_BEST_TAG),
                )
            }
            ScaryButton(
                stringResource(R.string.rd_scraper_sort_label, sortName(sort)),
                onClick = { sort = Releases.Sort.entries[(sort.ordinal + 1) % Releases.Sort.entries.size] },
                modifier = Modifier.testTag(RD_SOURCES_SORT_TAG),
            )
            ScaryButton(stringResource(R.string.rd_sources_indexer), onClick = { settingUp = true }, modifier = Modifier.testTag(RD_SOURCES_INDEXER_TAG))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Releases.Filter.entries.forEach { option ->
                val name = option.resolution?.label ?: stringResource(R.string.rd_sources_filter_all)
                // The chosen one carries a tick, not only a colour.
                ScaryButton(if (option == filter) "$name ✓" else name, onClick = { filter = option }, quiet = option != filter, modifier = Modifier.testTag(filterTag(option)))
            }
        }
        BasicText(
            text = statusText(search, pull, all.size, shown.size),
            style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle),
            modifier = Modifier.testTag(RD_SOURCES_STATUS_TAG).semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (shown.isNotEmpty()) {
            BasicText(stringResource(R.string.rd_scraper_results_title), style = ScaryType.RowTitle, modifier = Modifier.semantics { heading() })
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag(RD_SOURCES_LIST_TAG),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.magnet.infoHash }) { release -> ReleaseRow(release) { chosen = release } }
            }
        }
    }
}

@Composable
private fun sortName(sort: Releases.Sort): String = stringResource(
    when (sort) {
        Releases.Sort.SMART -> R.string.rd_sources_sort_smart
        Releases.Sort.SEEDERS -> R.string.rd_sources_sort_seeders
        Releases.Sort.SIZE -> R.string.rd_sources_sort_size
    },
)

/** The pull's news while there is some; otherwise the search's. */
@Composable
private fun statusText(search: RdSearchState, pull: RdPullState, found: Int, shown: Int): String = when {
    pull != RdPullState.Idle -> pullText(pull)
    search == RdSearchState.Idle -> stringResource(R.string.rd_sources_idle)
    search == RdSearchState.Loading -> stringResource(R.string.rd_sources_loading)
    search == RdSearchState.Unreachable -> stringResource(R.string.rd_sources_unreachable)
    search == RdSearchState.Refused -> stringResource(R.string.rd_sources_refused)
    search == RdSearchState.Unreadable -> stringResource(R.string.rd_sources_unreadable)
    search == RdSearchState.NeedsFilm -> stringResource(R.string.rd_sources_needs_film)
    search == RdSearchState.TorOff -> stringResource(R.string.rd_tor_off)
    found == 0 -> stringResource(R.string.rd_sources_none)
    shown == 0 -> stringResource(R.string.rd_sources_none_filtered)
    else -> stringResource(R.string.rd_sources_found, shown)
}

/** "1080p · BluRay · HEVC · 8.0 GB · Seeders: 120 · Site". */
@Composable
private fun detail(release: Releases.Candidate): String = listOfNotNull(
    release.tags.resolution.label,
    release.tags.origin.label.ifEmpty { null },
    release.tags.codec.label.ifEmpty { null },
    size(release.sizeBytes).ifEmpty { null },
    if (release.seeders >= 0) stringResource(R.string.rd_sources_seeders, release.seeders) else null,
    release.source.ifEmpty { null },
).joinToString(" · ")

@Composable
private fun ReleaseRow(release: Releases.Candidate, onClick: () -> Unit) {
    val detail = detail(release)
    FocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(releaseTag(release.magnet.infoHash)),
        shape = RoundedCornerShape(4.dp),
        focusedScale = 1.02f,
        containerColor = ScaryColors.Crypt,
        focusedContainerColor = ScaryColors.Bone,
        contentDescription = "${release.title}, $detail",
    ) { focused ->
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            BasicText(
                release.title,
                style = ScaryType.Body.copy(color = if (focused) ScaryColors.Void else ScaryColors.Bone, textDirection = TextDirection.Content),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                detail,
                style = ScaryType.Caption.copy(color = if (focused) ScaryColors.Void else ScaryColors.Candle, textDirection = TextDirection.Content),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One release, before anything is added: the viewer says yes, or goes back. */
@Composable
private fun ReleaseDialog(release: Releases.Candidate, choose: Boolean, onPull: (Service?) -> Unit, onDone: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Dialog(onDismissRequest = onDone) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .background(ScaryColors.Crypt, RoundedCornerShape(4.dp))
                .padding(22.dp)
                .testTag(RD_SOURCES_CONFIRM_TAG),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(
                release.title,
                style = ScaryType.Title.copy(textDirection = TextDirection.Content),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            BasicText(detail(release), style = ScaryType.Caption.copy(color = ScaryColors.Candle, textDirection = TextDirection.Content))
            BasicText(stringResource(if (choose) R.string.debrid_pull_confirm else R.string.rd_sources_pull_confirm), style = ScaryType.Body)
            if (choose) {
                // Both accounts are connected: the viewer says where this release goes, each time.
                ScaryButton(
                    stringResource(R.string.debrid_add_to_rd),
                    onClick = { onPull(Service.REAL_DEBRID); onDone() },
                    primary = true,
                    focusRequester = first,
                    modifier = Modifier.testTag(RD_SOURCES_PULL_TAG),
                )
                ScaryButton(
                    stringResource(R.string.debrid_add_to_torbox),
                    onClick = { onPull(Service.TORBOX); onDone() },
                    primary = true,
                    modifier = Modifier.testTag(RD_SOURCES_PULL_TORBOX_TAG),
                )
            } else {
                ScaryButton(
                    stringResource(R.string.rd_sources_pull_yes),
                    onClick = { onPull(null); onDone() },
                    primary = true,
                    focusRequester = first,
                    modifier = Modifier.testTag(RD_SOURCES_PULL_TAG),
                )
            }
            ScaryButton(stringResource(R.string.rd_torrent_back), onClick = onDone)
        }
    }
}

/** The viewer's sources: their addresses and keys, saved encrypted on this TV, checked or removed; and the Tor switch. */
@Composable
private fun IndexerDialog(sources: List<String>, throughTor: Boolean, actions: RdSourcesActions, onDone: () -> Unit) {
    var address by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<SaveResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    fun save() {
        if (busy) return
        busy = true
        scope.launch {
            val saved = actions.onSaveIndexer(address, key)
            busy = false
            result = saved
            // Saved: the new source is in the list above, and the fields are ready for another.
            if (saved == SaveResult.SAVED) {
                address = ""
                key = ""
            }
        }
    }
    Dialog(onDismissRequest = onDone) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .background(ScaryColors.Crypt, RoundedCornerShape(4.dp))
                .padding(22.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(stringResource(R.string.rd_sources_indexer), style = ScaryType.Title, modifier = Modifier.semantics { heading() })
            BasicText(stringResource(R.string.rd_sources_indexer_what), style = ScaryType.Body)
            if (sources.isNotEmpty()) {
                // Each source, with its own way out; "Remove" names it for a screen reader.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag(RD_SOURCES_SAVED_TAG)) {
                    sources.forEachIndexed { index, label ->
                        BasicText(
                            stringResource(R.string.rd_sources_connected, label),
                            style = ScaryType.Caption.copy(color = ScaryColors.Candle, textDirection = TextDirection.Content),
                        )
                        ScaryButton(
                            stringResource(R.string.rd_sources_remove_one, label),
                            onClick = { actions.onRemoveSource(index) },
                            quiet = true,
                            modifier = Modifier.testTag(sourceRemoveTag(index)),
                        )
                    }
                }
                // Do the saved sources answer, and how fast: asked only at this press.
                var checks by remember { mutableStateOf<List<SourcesRepository.Check>?>(null) }
                var checking by remember { mutableStateOf(false) }
                ScaryButton(
                    stringResource(R.string.rd_sources_check),
                    onClick = {
                        if (!checking) {
                            checking = true
                            scope.launch {
                                checks = actions.onCheckSource()
                                checking = false
                            }
                        }
                    },
                    modifier = Modifier.testTag(RD_SOURCES_CHECK_TAG),
                )
                val lines = when {
                    checking -> listOf(stringResource(R.string.rd_sources_checking))
                    else -> checks.orEmpty().map { check ->
                        val words = when (check.health) {
                            SourcesRepository.Health.OK -> stringResource(R.string.rd_sources_check_ok, check.millis.toString())
                            SourcesRepository.Health.REFUSED -> stringResource(R.string.rd_sources_check_refused)
                            SourcesRepository.Health.UNREADABLE -> stringResource(R.string.rd_sources_check_unreadable)
                            SourcesRepository.Health.TOR_OFF -> stringResource(R.string.rd_tor_off)
                            SourcesRepository.Health.UNREACHABLE, SourcesRepository.Health.NOT_CONFIGURED -> stringResource(R.string.rd_sources_check_unreachable)
                        }
                        if (check.label.isEmpty()) words else "${check.label}: $words"
                    }
                }
                if (lines.isNotEmpty()) {
                    Column(modifier = Modifier.testTag(RD_SOURCES_CHECK_RESULT_TAG).semantics { liveRegion = LiveRegionMode.Polite }) {
                        lines.forEach { BasicText(it, style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle, textDirection = TextDirection.Content)) }
                    }
                }
            }
            DialogField(
                label = stringResource(R.string.rd_sources_address_label),
                hint = "https://",
                value = address,
                onValueChange = { address = it.trim().take(1500) },
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
                onDone = {},
                modifier = Modifier.focusRequester(first).testTag(RD_SOURCES_ADDRESS_TAG),
            )
            DialogField(
                label = stringResource(R.string.rd_sources_key_label),
                hint = "",
                value = key,
                onValueChange = { key = it.trim().take(128) },
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                onDone = ::save,
                secret = true,
                modifier = Modifier.testTag(RD_SOURCES_KEY_TAG),
            )
            val message = when (result) {
                SaveResult.BAD_ADDRESS -> stringResource(R.string.rd_sources_bad_address)
                SaveResult.BAD_KEY -> stringResource(R.string.rd_sources_bad_key)
                SaveResult.STORAGE -> stringResource(R.string.rd_storage)
                SaveResult.FULL -> stringResource(R.string.rd_sources_full, SourcesRepository.MAX_SOURCES.toString())
                SaveResult.SAVED -> stringResource(R.string.rd_sources_saved)
                null -> null
            }
            if (message != null) {
                BasicText(message, style = ScaryType.BodyStrong.copy(color = ScaryColors.Candle), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            ScaryButton(stringResource(R.string.rd_sources_save), onClick = ::save, primary = true, modifier = Modifier.testTag(RD_SOURCES_SAVE_TAG))
            // Search through the Tor app the viewer runs on this TV (docs/TOR_PLAN.md). SCARY carries no Tor. Shown with
            // or without a source, so the switch is found before one is added.
            ScaryButton(
                stringResource(R.string.rd_tor_toggle, stringResource(if (throughTor) R.string.vpn_on else R.string.vpn_off)),
                onClick = { actions.onSetTor(!throughTor) },
                modifier = Modifier.testTag(RD_SOURCES_TOR_TAG),
            )
            BasicText(stringResource(R.string.rd_tor_note), style = ScaryType.Caption)
            val context = LocalContext.current
            val torApp = remember { runCatching { context.packageManager.getLaunchIntentForPackage(ORBOT_PACKAGE) }.getOrNull() }
            if (torApp != null) {
                ScaryButton(stringResource(R.string.rd_tor_open_app), onClick = { runCatching { context.startActivity(torApp) } }, quiet = true)
            }
            ScaryButton(stringResource(R.string.rd_torrent_back), onClick = onDone)
        }
    }
}

@Composable
private fun DialogField(
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicText(label, style = ScaryType.Caption)
    Box(
        Modifier
            .fillMaxWidth()
            .background(ScaryColors.Void, RoundedCornerShape(4.dp))
            .border(2.dp, if (focused) ScaryColors.Candle else ScaryColors.Bone.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty() && hint.isNotEmpty()) BasicText(hint, style = ScaryType.Body.copy(color = ScaryColors.Ash, textDirection = TextDirection.Ltr))
        ScaryTextField(
            value = value,
            onValueChange = onValueChange,
            // Addresses and keys are Latin letters and digits: left to right in every language.
            textStyle = ScaryType.BodyStrong.copy(textDirection = TextDirection.Ltr),
            cursorBrush = SolidColor(ScaryColors.Candle),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            interactionSource = interaction,
            modifier = modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}
