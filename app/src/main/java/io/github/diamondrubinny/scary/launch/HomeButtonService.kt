// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.launch

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import io.github.diamondrubinny.scary.MainActivity

/**
 * Turns the remote's Home button into "open SCARY" on TVs that will not let another app be the home screen.
 * It sees key events only (no window content), reacts to KEYCODE_HOME alone, and steps aside when
 * SCARY already is the default home screen.
 *
 * It also presses the phone remote's keys while another app is in front (Stremio, Netflix…), which an app cannot do
 * by itself: Back and Home on every Android version, the arrows and OK from Android 13 ([press]). It still reads
 * nothing on the screen.
 */
class HomeButtonService : AccessibilityService() {

    /** The keys the phone remote can press in other apps. */
    enum class Key { UP, DOWN, LEFT, RIGHT, OK, BACK }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // Services start at boot: show SCARY instead of the stock home screen once the TV is up.
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS && !HomeRole.isDefaultHome(this)) openScary()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Presses [key] in whatever window has the focus; false when this Android cannot (the arrows before 13). */
    fun press(key: Key): Boolean {
        if (key == Key.BACK) return performGlobalAction(GLOBAL_ACTION_BACK)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return performGlobalAction(
            when (key) {
                Key.UP -> GLOBAL_ACTION_DPAD_UP
                Key.DOWN -> GLOBAL_ACTION_DPAD_DOWN
                Key.LEFT -> GLOBAL_ACTION_DPAD_LEFT
                Key.RIGHT -> GLOBAL_ACTION_DPAD_RIGHT
                Key.OK -> GLOBAL_ACTION_DPAD_CENTER
                Key.BACK -> GLOBAL_ACTION_BACK
            },
        )
    }

    /** Brings SCARY to the front from another app, for the phone remote's Home, search and TMDB key. */
    fun bringScaryToFront(): Boolean = openScary()

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME || HomeRole.isDefaultHome(this)) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) openScary()
        return true
    }

    private fun openScary(): Boolean {
        val intent = Intent(this, MainActivity::class.java)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Could not open SCARY")
            false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "HomeButtonService"
        private const val BOOT_WINDOW_MS = 3 * 60_000L

        /** The running service, while the viewer has it turned on in the TV's accessibility settings. */
        @Volatile
        var instance: HomeButtonService? = null
            private set

        /** Whether the arrows and OK can reach other apps on this TV (Android 13 and later). */
        val arrowsEverywhere: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
}
