package io.github.diamondrubinny.scary.core.stability

/**
 * Detects a crash loop: a launcher that crashes on start leaves the TV with no home screen, so after
 * [THRESHOLD] crashes within [WINDOW_MS] SCARY starts in safe mode (effects and sounds off).
 *
 * Only crash times are kept, never stack traces or anything else about the crash.
 */
object CrashLoop {
    const val THRESHOLD = 3
    const val WINDOW_MS = 120_000L

    /** Crash times to keep after a crash at [now]: only those inside the window, newest last. */
    fun record(previous: List<Long>, now: Long): List<Long> =
        (recent(previous, now) + now).takeLast(THRESHOLD)

    /** True when the recorded crashes, as seen at [now], form a loop. */
    fun isLoop(times: List<Long>, now: Long): Boolean = recent(times, now).size >= THRESHOLD

    /** Parses the stored form, ignoring anything malformed. Times after "now" (a clock change) never count. */
    fun parse(stored: String?): List<Long> =
        stored.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.filter { it > 0 }

    fun format(times: List<Long>): String = times.joinToString(",")

    private fun recent(times: List<Long>, now: Long): List<Long> =
        times.filter { it in (now - WINDOW_MS)..now }
}
