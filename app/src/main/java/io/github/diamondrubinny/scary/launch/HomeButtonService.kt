// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.launch

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import io.github.diamondrubinny.scary.MainActivity

/**
 * Turns the remote's Home button into "open SCARY" on TVs that will not let another app be the home screen.
 * It sees key events only (no window content), reacts to KEYCODE_HOME alone, and steps aside when
 * SCARY already is the default home screen.
 */
class HomeButtonService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Services start at boot: show SCARY instead of the stock home screen once the TV is up.
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS && !HomeRole.isDefaultHome(this)) openScary()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME || HomeRole.isDefaultHome(this)) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) openScary()
        return true
    }

    private fun openScary() {
        val intent = Intent(this, MainActivity::class.java)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Could not open SCARY")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private companion object {
        const val TAG = "HomeButtonService"
        const val BOOT_WINDOW_MS = 3 * 60_000L
    }
}
