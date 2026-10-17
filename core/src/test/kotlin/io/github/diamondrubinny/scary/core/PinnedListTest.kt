package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.apps.PinnedList
import org.junit.Assert.assertEquals
import org.junit.Test

class PinnedListTest {
    private val list = listOf("a", "b", "c")

    @Test
    fun `toggle adds to the end and removes`() {
        assertEquals(listOf("a", "b", "c", "d"), PinnedList.toggle(list, "d"))
        assertEquals(listOf("a", "c"), PinnedList.toggle(list, "b"))
    }

    @Test
    fun `move shifts and clamps`() {
        assertEquals(listOf("b", "a", "c"), PinnedList.move(list, "b", -1))
        assertEquals(listOf("a", "c", "b"), PinnedList.move(list, "b", 1))
        assertEquals(listOf("b", "c", "a"), PinnedList.move(list, "a", 10))
        assertEquals(list, PinnedList.move(list, "a", -1))
        assertEquals(list, PinnedList.move(list, "zzz", 1))
    }
}
