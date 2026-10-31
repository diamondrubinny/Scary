// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import io.github.diamondrubinny.scary.ScaryApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The sleep timer: when it runs out, whatever plays is paused (Stremio, Netflix: any player with a media session), so
 * the TV goes idle and its own screensaver and standby take over. Switching the TV off outright needs system rights a
 * launcher does not have.
 *
 * The alarm wakes SCARY even when another app is in front or SCARY's process is gone. It is exact where the system
 * allows it, and otherwise a windowed alarm, which the system may run a few minutes late.
 */
object SleepTimer {
    /** The choices offered in settings, in minutes; 0 is off. */
    val CHOICES = listOf(0, 30, 60, 90, 120)

    /**
     * The choice after the running one, which is the smallest that still covers [minutesLeft] (0 when off): with 25
     * minutes left, the 30-minute timer runs, so the next is 60; after 120 comes off.
     */
    fun next(minutesLeft: Int): Int {
        val current = if (minutesLeft <= 0) 0 else CHOICES.firstOrNull { it >= minutesLeft } ?: CHOICES.last()
        return CHOICES[(CHOICES.indexOf(current) + 1) % CHOICES.size]
    }

    /**
     * Whether SCARY's alarm is still set. A restart of the TV clears alarms, so a timer saved from before it is no
     * longer running.
     */
    fun isSet(context: Context): Boolean = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, SleepTimerReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
    ) != null

    /**
     * Starts (or restarts) the timer; returns when it ends, in wall-clock milliseconds, for the countdown. On time where
     * exact alarms are allowed (always before Android 12; on 12 and 13 by default); otherwise the system may run it up
     * to 10 minutes late.
     */
    fun start(context: Context, minutes: Int): Long {
        val delay = minutes * 60_000L
        val at = SystemClock.elapsedRealtime() + delay
        val alarms = alarms(context)
        val pending = pendingIntent(context)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (exact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            } else {
                alarms.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, WINDOW_MS, pending)
            }
        } catch (e: SecurityException) {
            // The exact-alarm permission was taken back in between.
            alarms.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, WINDOW_MS, pending)
        }
        return System.currentTimeMillis() + delay
    }

    fun cancel(context: Context) {
        val pending = pendingIntent(context)
        alarms(context).cancel(pending)
        pending.cancel()
    }

    /** Pauses the player that has the media session: a pause key, pressed and released, as the remote would. */
    fun pauseMedia(context: Context) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
    }

    private fun alarms(context: Context): AlarmManager = context.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, SleepTimerReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private const val WINDOW_MS = 60_000L
}

/** Runs when the sleep timer ends. Not exported: only SCARY's own alarm reaches it. */
class SleepTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("ScarySleep", "Sleep timer ended: pausing playback")
        SleepTimer.pauseMedia(context)
        val pending = goAsync()
        val settings = (context.applicationContext as ScaryApplication).container.settingsRepository
        CoroutineScope(Dispatchers.IO).launch {
            try {
                settings.setSleepAt(null)
            } finally {
                pending.finish()
            }
        }
    }
}
