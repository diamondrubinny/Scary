package io.github.diamondrubinny.scary.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.ui.home.DocumentKind
import io.github.diamondrubinny.scary.ui.home.SidePadding
import io.github.diamondrubinny.scary.ui.theme.ScaryColors
import io.github.diamondrubinny.scary.ui.theme.ScaryType

/**
 * The privacy notice or the accessibility statement. Each paragraph takes focus, so the D-pad scrolls
 * through the text and TalkBack reads it one paragraph at a time. Nothing here is clickable.
 */
@Composable
fun DocumentScreen(kind: DocumentKind) {
    val title = stringResource(
        when (kind) {
            DocumentKind.Privacy -> R.string.settings_privacy
            DocumentKind.Accessibility -> R.string.settings_accessibility_statement
        },
    )
    val paragraphs = stringArrayResource(
        when (kind) {
            DocumentKind.Privacy -> R.array.privacy_paragraphs
            DocumentKind.Accessibility -> R.array.accessibility_paragraphs
        },
    )
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(kind) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SidePadding, vertical = 28.dp)
            .fillMaxWidth(0.7f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(
            title,
            style = ScaryType.Display.copy(fontSize = ScaryType.Display.fontSize * 0.7f),
            modifier = Modifier.semantics { heading() },
        )
        BasicText(stringResource(R.string.document_hint), style = ScaryType.Caption, modifier = Modifier.padding(bottom = 8.dp))
        paragraphs.forEachIndexed { index, text ->
            Paragraph(text, if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
        }
    }
}

@Composable
private fun Paragraph(text: String, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) ScaryColors.Crypt else Color.Transparent)
            .border(2.dp, if (focused) ScaryColors.Candle.copy(alpha = 0.6f) else Color.Transparent, shape)
            .focusable(interactionSource = interactionSource)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        BasicText(text, style = ScaryType.Body.copy(fontSize = 17.sp, lineHeight = 26.sp, color = ScaryColors.Bone))
    }
}
