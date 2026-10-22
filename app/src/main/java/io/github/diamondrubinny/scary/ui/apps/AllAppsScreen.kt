package io.github.diamondrubinny.scary.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.data.InstalledApp
import io.github.diamondrubinny.scary.ui.components.AppTileCard
import io.github.diamondrubinny.scary.ui.components.ScaryButton
import io.github.diamondrubinny.scary.ui.home.AppTile
import io.github.diamondrubinny.scary.ui.home.DrawerApp
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType
import io.github.diamondrubinny.scary.ui.theme.mixedLanguage

class AllAppsActions(
    val onLaunch: (InstalledApp) -> Unit,
    val onTogglePinned: (InstalledApp) -> Unit,
    val onMovePinned: (InstalledApp, Int) -> Unit,
    val onToggleHidden: (InstalledApp) -> Unit,
    val onAppInfo: (InstalledApp) -> Unit,
)

/**
 * Every installed TV app. Editing works two ways because some remotes do not report a long press:
 * hold select on an app, or switch on edit mode and select it.
 */
@Composable
fun AllAppsScreen(apps: List<DrawerApp>, actions: AllAppsActions) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var optionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    val firstFocus = remember { FocusRequester() }
    val editFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (runCatching { firstFocus.requestFocus() }.isFailure) runCatching { editFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SidePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                stringResource(R.string.all_apps),
                style = ScaryType.Display.copy(fontSize = ScaryType.Display.fontSize * 0.7f),
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.weight(1f))
            ScaryButton(
                text = stringResource(if (editing) R.string.all_apps_done else R.string.all_apps_edit),
                onClick = { editing = !editing },
                primary = editing,
                focusRequester = editFocus,
            )
        }
        BasicText(
            text = stringResource(if (apps.isEmpty()) R.string.all_apps_empty else R.string.all_apps_edit_hint),
            style = ScaryType.Body.copy(color = if (editing) ScaryColors.Candle else ScaryColors.Ash),
            modifier = Modifier.padding(horizontal = SidePadding, vertical = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 136.dp),
            contentPadding = PaddingValues(horizontal = SidePadding, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(apps, key = { it.app.packageName }) { entry ->
                val isFirst = entry === apps.first()
                AppTileCard(
                    tile = AppTile.Installed(entry.app),
                    onClick = { if (editing) optionsFor = entry.app.packageName else actions.onLaunch(entry.app) },
                    onFocused = {},
                    onLongClick = { optionsFor = entry.app.packageName },
                    focusRequester = firstFocus.takeIf { isFirst },
                    badge = when {
                        entry.hidden -> stringResource(R.string.badge_hidden)
                        entry.pinned -> stringResource(R.string.badge_pinned)
                        else -> null
                    },
                    dimmed = entry.hidden,
                )
            }
        }
    }

    val selected = apps.firstOrNull { it.app.packageName == optionsFor }
    if (selected != null) {
        AppOptionsDialog(
            entry = selected,
            canMove = selected.pinned,
            onDismiss = { optionsFor = null },
            actions = actions,
        )
    }
}

@Composable
private fun AppOptionsDialog(
    entry: DrawerApp,
    canMove: Boolean,
    onDismiss: () -> Unit,
    actions: AllAppsActions,
) {
    val app = entry.app
    val firstOption = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { firstOption.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(360.dp)
                .background(ScaryColors.Crypt, RoundedCornerShape(10.dp))
                .border(1.dp, ScaryColors.Bone.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(app.label, style = ScaryType.Title.mixedLanguage(), modifier = Modifier.semantics { heading() })
            OptionButton(stringResource(R.string.app_option_open), Modifier.focusRequester(firstOption)) {
                onDismiss()
                actions.onLaunch(app)
            }
            OptionButton(stringResource(if (entry.pinned) R.string.app_option_unpin else R.string.app_option_pin)) {
                actions.onTogglePinned(app)
            }
            if (canMove) {
                OptionButton(stringResource(R.string.app_option_move_earlier)) { actions.onMovePinned(app, -1) }
                OptionButton(stringResource(R.string.app_option_move_later)) { actions.onMovePinned(app, 1) }
            }
            OptionButton(stringResource(if (entry.hidden) R.string.app_option_show else R.string.app_option_hide)) {
                actions.onToggleHidden(app)
            }
            OptionButton(stringResource(R.string.app_option_info)) {
                onDismiss()
                actions.onAppInfo(app)
            }
        }
    }
}

@Composable
private fun OptionButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ScaryButton(text = text, onClick = onClick, modifier = modifier.fillMaxWidth())
}
