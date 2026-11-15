// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.KeyEvent
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** The remote's arrows and OK. */
private val REMOTE_KEYS = setOf(
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_CENTER,
)

/**
 * The remote's arrows and OK go to the screen whenever no on-screen keyboard is showing. Android hands keys to the
 * keyboard before the app, and a keyboard hidden while a text line was focused can stay connected for a while after:
 * it then took the arrows, and OK on whatever was focused next (Ask SCARY's "Chat" did not open). Here such keys are
 * taken before the keyboard and handed to the activity as usual (so its own filter, core KeyBacklog, still applies).
 * While the keyboard shows, they stay its own, to move between its keys.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.remoteKeysPastHiddenKeyboard(): Modifier {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    return onInterceptKeyBeforeSoftKeyboard { event ->
        val native = event.nativeKeyEvent
        if (activity == null || native.keyCode !in REMOTE_KEYS || keyboardShown(view)) return@onInterceptKeyBeforeSoftKeyboard false
        activity.dispatchKeyEvent(native)
        true
    }
}

/** Whether the on-screen keyboard is showing now, from the window itself (not every screen follows the insets). */
internal fun keyboardShown(view: View): Boolean =
    ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
