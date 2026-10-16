package io.github.diamondrubinny.scary.ui.home

import androidx.annotation.StringRes
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.model.MovieCollection
import io.github.diamondrubinny.scary.data.InstalledApp

data class HomeUiState(
    val loading: Boolean = true,
    val catalogError: Boolean = false,
    val nightPick: Movie? = null,
    /** The movie shown in the hero area: the focused one, or tonight's pick. */
    val hero: Movie? = null,
    val rows: List<HomeRow> = emptyList(),
    val effectsEnabled: Boolean = false,
)

sealed interface HomeRow {
    val key: String

    data class Recent(val movies: List<Movie>) : HomeRow {
        override val key = "recent"
    }

    data class Apps(val tiles: List<AppTile>) : HomeRow {
        override val key = "apps"
    }

    data class Collection(val collection: MovieCollection, val movies: List<Movie>) : HomeRow {
        override val key = "collection:${collection.id}"
    }
}

sealed interface AppTile {
    val key: String

    data class Installed(val app: InstalledApp) : AppTile {
        override val key = app.packageName
    }

    /** A pinned app that is not installed yet; selecting it opens Google Play. */
    data class Suggestion(val packageName: String, val label: String) : AppTile {
        override val key = "suggest:$packageName"
    }

    data object SystemSettings : AppTile {
        override val key = "system_settings"
    }
}

data class MovieDetails(val movie: Movie, val similar: List<Movie>)

sealed interface Screen {
    data object Home : Screen
    data class Details(val movieId: String) : Screen
}

data class UserMessage(val id: Long, @StringRes val text: Int, val args: List<String> = emptyList())
