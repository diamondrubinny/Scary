// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.apps.AppOrdering
import io.github.diamondrubinny.scary.core.catalog.NightPick
import io.github.diamondrubinny.scary.core.catalog.MyList
import io.github.diamondrubinny.scary.core.catalog.RecentList
import io.github.diamondrubinny.scary.core.catalog.Similar
import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.model.Subgenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CurationTest {

    private fun movie(id: String, fear: Int = 3, genre: Genre = Genre.HORROR, vararg subs: Subgenre) = Movie(
        id = id, imdbId = "tt0000001", title = id, year = 2000, director = "d", runtimeMin = 90,
        genre = genre, subgenres = subs.toList().ifEmpty { listOf(Subgenre.GHOST) }, fear = fear, hue = 0,
        synopsisHe = "ת", synopsisEn = "s",
    )

    @Test
    fun `night pick is stable for a date and respects max fear`() {
        val movies = (1..20).map { movie("m$it", fear = 1 + it % 5) }
        val date = LocalDate.of(2026, 10, 31)
        assertEquals(NightPick.pick(movies, date), NightPick.pick(movies.shuffled(), date))
        repeat(30) { day ->
            val pick = NightPick.pick(movies, date.plusDays(day.toLong()), maxFear = 2)!!
            assertTrue(pick.fear!! <= 2)
        }
        assertNull(NightPick.pick(movies, date, maxFear = 0))
    }

    @Test
    fun `night pick changes across days`() {
        val movies = (1..20).map { movie("m$it") }
        val start = LocalDate.of(2026, 1, 1)
        val picks = (0 until 14).map { NightPick.pick(movies, start.plusDays(it.toLong()))!!.id }.toSet()
        assertTrue("expected variety, got $picks", picks.size >= 7)
    }

    @Test
    fun `similar ranks shared subgenres first and excludes the movie itself`() {
        val target = movie("t", 3, Genre.HORROR, Subgenre.GHOST, Subgenre.PSYCHOLOGICAL)
        val both = movie("both", 3, Genre.HORROR, Subgenre.GHOST, Subgenre.PSYCHOLOGICAL)
        val one = movie("one", 3, Genre.HORROR, Subgenre.GHOST)
        val unrelated = movie("unrelated", 3, Genre.THRILLER, Subgenre.CRIME)
        val result = Similar.find(listOf(unrelated, one, target, both), target)
        assertEquals(listOf("both", "one"), result.map { it.id })
    }

    @Test
    fun `recent list dedupes, moves to front and caps`() {
        var list = emptyList<String>()
        (1..15).forEach { list = RecentList.push(list, "m$it") }
        list = RecentList.push(list, "m10")
        assertEquals("m10", list.first())
        assertEquals(RecentList.MAX_SIZE, list.size)
        assertEquals(list.size, list.toSet().size)
        assertEquals(list, RecentList.decode(RecentList.encode(list)))
        assertEquals(emptyList<String>(), RecentList.decode(null))
        assertEquals(listOf("a", "b"), RecentList.decode(" a, ,b,"))
    }

    @Test
    fun `surprise stays within the fear limit, skips locked and the current movie`() {
        val movies = (1..20).map { movie("m$it", fear = 1 + it % 5) } + movie("online", fear = 1).copy(fear = null, curated = false)
        val random = kotlin.random.Random(1)
        repeat(50) {
            val pick = NightPick.surprise(movies, maxFear = 2, random = random)!!
            assertTrue(pick.curated && pick.fear!! <= 2)
        }
        val mild = movies.filter { it.fear == 1 }
        repeat(20) { assertTrue(NightPick.surprise(mild, 1, random, exclude = mild.first().id)!!.id != mild.first().id) }
        // With a single candidate, excluding it still returns it rather than nothing.
        assertEquals("m5", NightPick.surprise(listOf(movie("m5", fear = 1)), 1, random, exclude = "m5")!!.id)
        assertNull(NightPick.surprise(movies, maxFear = 5, random = random, isLocked = { true }))
    }

    @Test
    fun `my list adds at the front, removes on second toggle and caps the size`() {
        var list = MyList.toggle(emptyList(), "a")
        list = MyList.toggle(list, "b")
        assertEquals(listOf("b", "a"), list)
        assertEquals(listOf("b"), MyList.toggle(list, "a"))

        var full = emptyList<String>()
        (1..MyList.MAX_SIZE + 5).forEach { full = MyList.toggle(full, "m$it") }
        assertEquals(MyList.MAX_SIZE, full.size)
        assertEquals("m${MyList.MAX_SIZE + 5}", full.first())
        assertTrue("m1" !in full)
    }

    @Test
    fun `apps order puts installed pinned first then sorts by label`() {
        data class App(val pkg: String, val label: String)
        val apps = listOf(
            App("z.zoo", "zoo"), App("com.android.vending", "Play"), App("a.alpha", "Alpha"),
            App("com.stremio.one", "Stremio"), App("h.hidden", "Hidden"), App("a.alpha", "Alpha dup"),
        )
        val ordered = AppOrdering.order(
            apps, App::pkg, App::label,
            pinned = listOf("com.stremio.one", "com.android.vending", "not.installed"),
            hidden = setOf("h.hidden"),
        )
        assertEquals(listOf("com.stremio.one", "com.android.vending", "a.alpha", "z.zoo"), ordered.map { it.pkg })
    }

    @Test
    fun `language codes map hebrew legacy and modern`() {
        assertEquals(Language.HE, Language.fromCode("iw"))
        assertEquals(Language.HE, Language.fromCode("he"))
        assertEquals(Language.EN, Language.fromCode("en"))
    }
}
