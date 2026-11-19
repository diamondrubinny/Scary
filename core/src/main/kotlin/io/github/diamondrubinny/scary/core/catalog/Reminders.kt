// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

/**
 * "Remind me": films not out yet that the viewer asked about, each with the day it comes out. Once that day comes the
 * film shows in the home screen's "Now out" row, for [SHOW_DAYS]. Kept on the TV only; nothing is scheduled and
 * nothing is sent: the row is worked out when the home screen is built.
 */
object Reminders {
    /** As many reminders as are kept; the oldest ask goes first when one more is added. */
    const val MAX = 40

    /** How long a film stays in "Now out" after its day. */
    const val SHOW_DAYS = 45L

    /** [day]: when the film comes out, in days since 1970. */
    data class Reminder(val id: String, val day: Long)

    private val ID = Regex("[A-Za-z0-9:_.-]{1,80}")
    // 2000-01-01 to 2100-01-01: a day outside is not a release date.
    private val DAYS = 10_957L..47_482L

    /** Adds a reminder for [id] (newest first), or removes it if there is one. */
    fun toggle(current: List<Reminder>, id: String, day: Long): List<Reminder> =
        if (current.any { it.id == id }) current.filter { it.id != id } else clean(listOf(Reminder(id, day)) + current)

    /** The films whose day has come and that still belong in "Now out", the latest out first. */
    fun due(current: List<Reminder>, today: Long): List<String> =
        current.filter { it.day <= today && today - it.day <= SHOW_DAYS }.sortedByDescending { it.day }.map { it.id }

    /** Without the films whose time in "Now out" has passed. */
    fun pruned(current: List<Reminder>, today: Long): List<Reminder> = current.filter { today - it.day <= SHOW_DAYS }

    fun encode(reminders: List<Reminder>): String = clean(reminders).joinToString(",") { "${it.id}@${it.day}" }

    /** Stored text is not trusted: an entry that does not fit is dropped. */
    fun decode(raw: String?): List<Reminder> = clean(
        raw.orEmpty().split(',').mapNotNull { entry ->
            val id = entry.substringBeforeLast('@', "")
            val day = entry.substringAfterLast('@', "").toLongOrNull()
            if (day == null) null else Reminder(id, day)
        },
    )

    private fun clean(reminders: List<Reminder>): List<Reminder> =
        reminders.filter { ID.matches(it.id) && '@' !in it.id && it.day in DAYS }.distinctBy { it.id }.take(MAX)
}
