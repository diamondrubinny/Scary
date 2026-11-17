// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.stability

import android.content.Context
import android.os.Process
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * What went wrong lately, for Settings → About → Diagnostics, so a viewer can show it (a photo of the screen) when
 * SCARY crashes, freezes or shows no online films. Only where in SCARY's code it happened (class, method, line) and
 * when: never an error's message, a URL, a film or anything about the viewer. Kept on this TV only (not backed up),
 * never sent anywhere.
 */
object HealthLog {
    private const val FILE = "health_log"
    private const val CRASH = "last_crash"
    private const val FREEZE = "last_freeze"
    private const val FREEZES = "freezes"
    private const val ONLINE = "online"
    private const val REFRESH = "refresh"
    private const val QUALITY = "quality"
    private const val STARTS = "starts"
    private const val MAX_STARTS = 5
    private const val OWN_PACKAGE = "io.github.diamondrubinny.scary"
    private const val FRAMES = 4

    data class Report(
        val crash: String?,
        val freeze: String?,
        val freezes: Int,
        val online: String?,
        /** "15.11 10:30 · 28/30 · 70/70": pages of the online rows, then of the categories, loaded of those asked for. */
        val refresh: String? = null,
        /** "16.11 21:10 · 41 · 3": American films kept out of the rows about a place, and those waiting for a language. */
        val quality: String? = null,
        /** "16.11 21:10 · 3.2 s · 3.0 s on average of 5": how long SCARY took, from opening, to show its first rows. */
        val startup: String? = null,
    )

    fun read(context: Context): Report {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return Report(
            prefs.getString(CRASH, null),
            prefs.getString(FREEZE, null),
            prefs.getInt(FREEZES, 0),
            prefs.getString(ONLINE, null),
            prefs.getString(REFRESH, null),
            prefs.getString(QUALITY, null),
            startupLine(prefs.getString(STARTS, null)),
        )
    }

    fun clear(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { clear() }

    /** From the crash handler: the process is about to die, so written at once. */
    fun recordCrash(context: Context, error: Throwable) {
        val root = generateSequence(error) { it.cause.takeIf { cause -> cause !== it } }.take(5).last()
        val line = "${stamp()} · ${root.javaClass.simpleName} ${where(root.stackTrace)}"
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit(commit = true) { putString(CRASH, line) }
    }

    /** The online catalog's last answer: "OK" or the kind of failure (an HTTP code, a network error's type). */
    fun recordOnline(context: Context, ok: Boolean, problem: String?) {
        val line = if (ok) "${stamp()} · OK" else "${stamp()} · ${problem ?: "failed"}"
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putString(ONLINE, line) }
    }

    /** The last refresh of the online rows: how many of their pages loaded, and of the categories' pool (once filled). */
    fun recordRefresh(context: Context, rowsLoaded: Int, rowsTotal: Int, categoriesLoaded: Int?, categoriesTotal: Int?) {
        val categories = if (categoriesLoaded != null && categoriesTotal != null) " · $categoriesLoaded/$categoriesTotal" else ""
        val line = "${stamp()} · $rowsLoaded/$rowsTotal$categories"
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putString(REFRESH, line) }
    }

    /** The origin check of the last refresh (core RowAudit.held): counts only, never a film's name. */
    fun recordQuality(context: Context, keptOut: Int, unclear: Int) {
        val line = "${stamp()} · $keptOut · $unclear"
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putString(QUALITY, line) }
    }

    private val firstRowsRecorded = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Called when the home screen has drawn its first rows: how long since this process started, once per start. The
     * last [MAX_STARTS] are kept so a slow start can be told from one slow morning. Only a time and a duration.
     */
    fun recordFirstRows(context: Context) {
        if (!firstRowsRecorded.compareAndSet(false, true)) return
        val millis = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        if (millis !in 1..MAX_START_MS) return
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val kept = (prefs.getString(STARTS, null)?.split(';').orEmpty().filter { it.isNotBlank() } + "${stamp()}|$millis").takeLast(MAX_STARTS)
        prefs.edit { putString(STARTS, kept.joinToString(";")) }
    }

    private const val MAX_START_MS = 10 * 60_000L

    /** "16.11 21:10 · 3.2 s · 3.0 s on average of 5" from the stored starts; null when there are none. */
    private fun startupLine(stored: String?): String? {
        val starts = stored?.split(';').orEmpty().mapNotNull { entry ->
            val parts = entry.split('|')
            val millis = parts.getOrNull(1)?.toLongOrNull()?.takeIf { it in 1..MAX_START_MS } ?: return@mapNotNull null
            parts[0] to millis
        }
        val last = starts.lastOrNull() ?: return null
        fun seconds(ms: Long) = String.format(Locale.ROOT, "%.1f s", ms / 1000.0)
        val average = starts.sumOf { it.second } / starts.size
        return "${last.first} · ${seconds(last.second)} · ${seconds(average)} / ${starts.size}"
    }

    private fun recordFreeze(context: Context, seconds: Float, frames: Array<StackTraceElement>) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val line = "${stamp()} · ${String.format(Locale.ROOT, "%.1f", seconds)} s ${where(frames)}"
        prefs.edit {
            putString(FREEZE, line)
            putInt(FREEZES, prefs.getInt(FREEZES, 0) + 1)
        }
    }

    /** "at HomeViewModel.kt:512 (buildState) ← …": SCARY's own frames first, else the top of the stack. */
    private fun where(frames: Array<StackTraceElement>): String {
        val own = frames.filter { it.className.startsWith(OWN_PACKAGE) }
        val shown = (own.ifEmpty { frames.toList() }).take(FRAMES)
        return shown.joinToString(" ← ", prefix = "at ") { "${it.fileName ?: it.className.substringAfterLast('.')}:${it.lineNumber} (${it.methodName})" }
    }

    private fun stamp(): String = SimpleDateFormat("dd.MM HH:mm", Locale.ROOT).format(Date())

    /**
     * Watches the main thread from a thread of its own: when it does not answer for [FREEZE_MS], where it is stuck is
     * written down once, with how long it lasted. A quiet thread that wakes once a second; it changes nothing in the app.
     */
    fun startFreezeWatch(context: Context) {
        val appContext = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread({
            while (true) {
                val answered = AtomicBoolean(false)
                val asked = SystemClock.uptimeMillis()
                main.post { answered.set(true) }
                var frames: Array<StackTraceElement>? = null
                while (!answered.get()) {
                    try {
                        Thread.sleep(POLL_MS)
                    } catch (e: InterruptedException) {
                        return@Thread
                    }
                    // Where it is stuck, taken while it still is.
                    if (frames == null && SystemClock.uptimeMillis() - asked >= FREEZE_MS) frames = Looper.getMainLooper().thread.stackTrace
                }
                frames?.let { recordFreeze(appContext, (SystemClock.uptimeMillis() - asked) / 1000f, it) }
                try {
                    Thread.sleep(PERIOD_MS)
                } catch (e: InterruptedException) {
                    return@Thread
                }
            }
        }, "scary-freeze-watch").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }.start()
    }

    private const val FREEZE_MS = 2_000L
    private const val POLL_MS = 250L
    private const val PERIOD_MS = 1_000L
}
