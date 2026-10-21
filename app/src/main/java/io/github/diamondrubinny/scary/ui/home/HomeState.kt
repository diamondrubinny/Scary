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
    /** Every installed TV app, hidden ones included, for the all-apps drawer. */
    val drawer: List<DrawerApp> = emptyList(),
    val nightMaxFear: Int = 5,
    /** The whole catalog, for search. */
    val movies: List<Movie> = emptyList(),
    val parentalEnabled: Boolean = false,
    val lockFromFear: Int = 4,
    /** True after the PIN was entered, until the launcher leaves the screen or 30 minutes pass. */
    val sessionUnlocked: Boolean = false,
    val soundsEnabled: Boolean = false,
    val onlineEnabled: Boolean = true,
    /** Ids on "My list", for the add/remove button on a movie's page. */
    val myListIds: Set<String> = emptySet(),
) {
    fun isLocked(movie: Movie): Boolean = parentalEnabled && !sessionUnlocked && movie.fearForLock >= lockFromFear
}

/** Web search under the library results: [loading] while waiting, [failed] when Cinemeta could not be reached. */
data class WebSearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<Movie> = emptyList(),
    val failed: Boolean = false,
)

data class TmdbDialogState(
    val busy: Boolean = false,
    val result: io.github.diamondrubinny.scary.data.TmdbKeyResult? = null,
)

data class LauncherStatus(val isDefaultHome: Boolean, val homeButtonServiceOn: Boolean) {
    /** Show the "make SCARY the home screen" prompt only when neither route is active. */
    val needsSetup: Boolean get() = !isDefaultHome && !homeButtonServiceOn
}

/** Something the parental PIN guards; it runs once the PIN is accepted. */
sealed interface GatedAction {
    data class Details(val movieId: String) : GatedAction
    data class Watch(val movie: Movie) : GatedAction
    data class Trailer(val movie: Movie) : GatedAction
    data object Settings : GatedAction
}

sealed interface PinStep {
    data class Verify(val action: GatedAction) : PinStep
    data object Create : PinStep
    data class Confirm(val firstEntry: String) : PinStep
}

data class PinDialogState(
    val step: PinStep,
    @StringRes val error: Int? = null,
    val lockedUntilMillis: Long = 0L,
    val busy: Boolean = false,
)

data class DrawerApp(val app: InstalledApp, val pinned: Boolean, val hidden: Boolean)

sealed interface HomeRow {
    val key: String

    data class Recent(val movies: List<Movie>) : HomeRow {
        override val key = "recent"
    }

    data class MyList(val movies: List<Movie>) : HomeRow {
        override val key = "mylist"
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

    data object SetAsHome : AppTile {
        override val key = "set_as_home"
    }

    data object AllApps : AppTile {
        override val key = "all_apps"
    }

    data object SystemSettings : AppTile {
        override val key = "system_settings"
    }
}

data class MovieDetails(val movie: Movie, val similar: List<Movie>)

/** Read-only texts reachable from settings. */
enum class DocumentKind { Privacy, Accessibility }

sealed interface Screen {
    data object Home : Screen
    data class Details(val movieId: String) : Screen
    data object AllApps : Screen
    data object Settings : Screen
    data object Search : Screen
    data class Document(val kind: DocumentKind) : Screen
}

data class UserMessage(val id: Long, @StringRes val text: Int, val args: List<String> = emptyList())
