package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.catalog.FamilyNight
import io.github.diamondrubinny.scary.core.model.Catalog
import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.model.MovieCollection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FamilyNightTest {
    private fun movie(id: String, fear: Int?, curated: Boolean = true) =
        Movie(id = id, imdbId = "tt$id", title = id, year = 2000, genre = Genre.HORROR, fear = fear, curated = curated)

    private val catalog = Catalog(
        version = 1,
        movies = listOf(movie("mild", 1), movie("edge", 2), movie("scary", 3), movie("online", null, curated = false)),
        collections = listOf(MovieCollection("all", "הכול", "All", listOf("mild", "scary", "edge", "online"))),
    )

    @Test
    fun keepsOnlyGentleCuratedMovies() {
        val family = FamilyNight.filter(catalog)
        assertEquals(listOf("mild", "edge"), family.movies.map { it.id })
        assertNull(family.movie("scary"))
        assertNull(family.movie("online"))
    }

    @Test
    fun collectionsShowOnlyWhatIsLeft() {
        val family = FamilyNight.filter(catalog)
        assertEquals(listOf("mild", "edge"), family.moviesOf(family.collections.single()).map { it.id })
    }
}
