// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.profiles.Profiles
import io.github.diamondrubinny.scary.core.profiles.Profiles.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Viewer profiles: the first is the TV's own lists, the rest keep theirs under a suffix. */
class ProfilesTest {
    @Test
    fun `a TV that never had profiles has the first one, with its lists where they always were`() {
        assertEquals(listOf(Profile("p1")), Profiles.decode(null))
        assertEquals(listOf(Profile("p1")), Profiles.decode("not json"))
        assertEquals(listOf(Profile("p1")), Profiles.decode("{}"))
        assertEquals("", Profiles.suffix("p1"))
        assertEquals("", Profiles.suffix(null))
        assertEquals("@p2", Profiles.suffix("p2"))
        // An id that is not a profile's never reaches a key.
        assertEquals("", Profiles.suffix("p9"))
        assertEquals("", Profiles.suffix("../x"))
    }

    @Test
    fun `profiles are added up to five, each with the first free id`() {
        var list = Profiles.decode(null)
        val (withDana, dana) = Profiles.add(list, "  Dana  ", 2, gentle = false)!!
        assertEquals(Profile("p2", "Dana", 2), dana)
        list = withDana
        list = Profiles.add(list, "Kid", 9, gentle = true)!!.first
        assertEquals(Profile("p3", "Kid", Profiles.COLOURS - 1, gentle = true), list[2])
        list = Profiles.remove(list, "p2")!!
        // The freed id is used again.
        assertEquals("p2", Profiles.add(list, "Noa", 0, false)!!.second.id)
        list = Profiles.add(list, "Noa", 0, false)!!.first
        list = Profiles.add(list, "Four", 0, false)!!.first
        list = Profiles.add(list, "Five", 0, false)!!.first
        assertEquals(5, list.size)
        assertNull(Profiles.add(list, "Six", 0, false))
        assertNull(Profiles.add(Profiles.decode(null), "   ", 0, false))
    }

    @Test
    fun `the first profile cannot be removed, and an unknown one changes nothing`() {
        val list = Profiles.add(Profiles.decode(null), "Dana", 1, false)!!.first
        assertNull(Profiles.remove(list, "p1"))
        assertNull(Profiles.remove(list, "p4"))
        assertNull(Profiles.update(list, "p4", "X", 0, false))
        assertNull(Profiles.update(list, "p2", " ", 0, false))
        assertEquals(Profile("p1", "Mom", 3, false), Profiles.update(list, "p1", "Mom", 3, false)!![0])
        assertEquals("p1", Profiles.active(list, "p4").id)
        assertEquals("p2", Profiles.active(list, "p2").id)
    }

    @Test
    fun `a name is one clean line`() {
        assertEquals("Dana K", Profiles.cleanName(" Dana \n  K "))
        assertEquals("a".repeat(20), Profiles.cleanName("a".repeat(50)))
        // Direction marks could turn the text around the name on screen.
        assertEquals("abc", Profiles.cleanName("‮abc‏"))
        assertNull(Profiles.cleanName("‮ ​"))
        assertEquals("דנה", Profiles.cleanName("דנה"))
    }

    @Test
    fun `the stored list is read as untrusted`() {
        val list = Profiles.add(Profiles.add(Profiles.decode(null), "Dana", 1, false)!!.first, "Kid", 2, true)!!.first
        assertEquals(list, Profiles.decode(Profiles.encode(list)))
        val hostile = """[{"id":"p2","name":"A"},{"id":"p2","name":"Twice"},{"id":"p9","name":"Bad"},{"id":"p3","name":""},
            {"id":"p4","name":"${"x".repeat(80)}","c":99,"g":"yes"},"junk",{"name":"no id"}]"""
        val read = Profiles.decode(hostile)
        assertEquals(listOf("p1", "p2", "p4"), read.map { it.id })
        assertEquals("A", read[1].name)
        assertEquals(Profile("p4", "x".repeat(20), Profiles.COLOURS - 1, false), read[2])
        assertTrue(Profiles.decode("[" + (1..40).joinToString(",") { """{"id":"p${it % 5 + 1}","name":"n"}""" } + "]").size <= Profiles.MAX)
    }

    @Test
    fun `a new profile takes a colour nobody has`() {
        assertEquals(1, Profiles.nextColour(listOf(Profile("p1", "", 0))))
        assertEquals(0, Profiles.nextColour(listOf(Profile("p1", "", 3))))
    }
}
