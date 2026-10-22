package io.github.diamondrubinny.scary.stability

import android.content.Context
import android.util.Log
import io.github.diamondrubinny.scary.core.stability.CrashLoop

/**
 * Notices when SCARY keeps crashing, so the next start can switch to safe mode instead of leaving the TV without a
 * home screen. Only crash times are written, to this TV only; nothing about the crash is stored or sent anywhere.
 */
object CrashGuard {
    private const val TAG = "CrashGuard"
    private const val FILE = "crash_guard"
    private const val KEY = "crash_times"

    /** Records each uncaught crash, then lets the previous handler (the system's) end the process as usual. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val prefs = appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                val times = CrashLoop.record(CrashLoop.parse(prefs.getString(KEY, null)), System.currentTimeMillis())
                // commit, not apply: the process is about to die.
                prefs.edit().putString(KEY, CrashLoop.format(times)).commit()
            } catch (t: Throwable) {
                Log.w(TAG, "Could not record crash: ${t.javaClass.simpleName}")
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** True once after a crash loop; the record is cleared so safe mode is entered only once per loop. */
    fun consumeCrashLoop(context: Context): Boolean {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val loop = CrashLoop.isLoop(CrashLoop.parse(prefs.getString(KEY, null)), System.currentTimeMillis())
        if (loop) prefs.edit().remove(KEY).apply()
        return loop
    }
}
