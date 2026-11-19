// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.Reminders
import io.github.diamondrubinny.scary.core.catalog.Reminders.Reminder
import org.junit.Assert.assertEquals
import org.junit.Test

class RemindersTest {
    private val today = 20_776L

    @Test
    fun `a reminder is set with one press and removed with the next`() {
        val one = Reminders.toggle(emptyList(), "cm_tt1", today + 10)
        assertEquals(listOf(Reminder("cm_tt1", today + 10)), one)
        val two = Reminders.toggle(one, "cm_tt2", today + 3)
        assertEquals(listOf("cm_tt2", "cm_tt1"), two.map { it.id })
        assertEquals(listOf(Reminder("cm_tt2", today + 3)), Reminders.toggle(two, "cm_tt1", today + 99))
    }

    @Test
    fun `a film shows once its day comes, and leaves after a while`() {
        val list = listOf(Reminder("later", today + 1), Reminder("today", today), Reminder("lastWeek", today - 7), Reminder("old", today - Reminders.SHOW_DAYS - 1))
        // The latest out first; not yet out and long out are not there.
        assertEquals(listOf("today", "lastWeek"), Reminders.due(list, today))
        assertEquals(listOf("later", "today", "lastWeek"), Reminders.due(list, today + 1))
        assertEquals(listOf("later", "today", "lastWeek"), Reminders.pruned(list, today).map { it.id })
        assertEquals(emptyList<String>(), Reminders.due(emptyList(), today))
    }

    @Test
    fun `stored reminders read back, and damaged ones are dropped`() {
        val list = listOf(Reminder("cm_tt1", today + 10), Reminder("the-film-2027", today))
        assertEquals(list, Reminders.decode(Reminders.encode(list)))
        assertEquals(emptyList<Reminder>(), Reminders.decode(null))
        assertEquals(emptyList<Reminder>(), Reminders.decode(""))
        assertEquals(
            listOf(Reminder("ok", today)),
            Reminders.decode("ok@$today,bad id@$today,noday,@$today,x@abc,far@99999999,ok@${today + 5},${"y".repeat(81)}@$today"),
        )
        // The list has an end: the newest asks are kept.
        val many = (1..60).fold(emptyList<Reminder>()) { acc, i -> Reminders.toggle(acc, "f$i", today + i) }
        assertEquals(Reminders.MAX, many.size)
        assertEquals("f60", many.first().id)
    }
}
