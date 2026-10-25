// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.home

import io.github.diamondrubinny.scary.core.security.ParentalGate
import androidx.annotation.StringRes
import io.github.diamondrubinny.scary.core.catalog.Mood
import io.github.diamondrubinny.scary.core.catalog.Season
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.tmdb.WatchProvider
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
    val familyNight: Boolean = false,
    val seasonalRows: Boolean = true,
    /** Ids the viewer has seen, for the badge on cards and the button on a movie's page. */
    val watchedIds: Set<String> = emptySet(),
    val autoWatched: Boolean = true,
) {
    fun isLocked(movie: Movie): Boolean = ParentalGate.isLocked(parentalEnabled, sessionUnlocked, lockFromFear, movie)

    fun isWatched(movie: Movie): Boolean = movie.id in watchedIds
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
    data class SearchIn(val movie: Movie, val app: WatchOption) : GatedAction
    data class SearchEverywhere(val movie: Movie) : GatedAction
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

    /** Shown only around its dates (Halloween, Friday the 13th). */
    data class Seasonal(val season: Season, val movies: List<Movie>) : HomeRow {
        override val key = "season:${season.key}"
    }

    /** "What do you feel like tonight?": mood chips, and the chosen mood's movies once one is picked. */
    data class Moods(
        val selected: Mood?,
        val movies: List<Movie>,
        /** The chosen decade (1980 for the 1980s), or null. */
        val decade: Int? = null,
        /** Decades offered as chips, oldest first. */
        val decades: List<Int> = emptyList(),
    ) : HomeRow {
        override val key = "moods"
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

/**
 * A streaming app on this TV that a movie page can send the viewer to. [canSearch]: it accepts the title as a search;
 * otherwise SCARY only opens it. Nothing here says the movie is available in that app.
 */
data class WatchOption(val packageName: String, val label: String, val canSearch: Boolean)

/** What pressing a store or service on a movie page does. */
sealed interface OfferAction {
    /** The app is on this device: search the movie in it. */
    data class Search(val option: WatchOption) : OfferAction

    /** The app is known but not installed: open its page in the store. */
    data class Store(val packageName: String) : OfferAction

    /** Nothing to open on this device; the QR code leads to the store instead. */
    data object None : OfferAction
}

data class ProviderOffer(val provider: WatchProvider, val action: OfferAction)

/** "Didn't find it? Rent or buy" on a movie page. */
sealed interface RentOrBuyState {
    /** Online data is off, or loading failed: the section stays out of the way. */
    data object Hidden : RentOrBuyState

    /** Store data comes from TMDB, which needs the viewer's own key. */
    data object NeedsKey : RentOrBuyState

    /** Neither the settings nor the device name a country. */
    data object NoCountry : RentOrBuyState

    data class Loading(val country: String) : RentOrBuyState

    data class Ready(
        val country: String,
        val rentOrBuy: List<ProviderOffer>,
        val included: List<ProviderOffer>,
        /** TMDB's page for the movie in [country], shown as a QR code. */
        val link: String?,
    ) : RentOrBuyState
}

/** [RentOrBuyState] for one movie, so a page never shows the offers of the one before it. */
data class RentOrBuyUi(val movieId: String, val state: RentOrBuyState)

/** The short shiver screen between pressing Watch and the other app coming up. */
data class LaunchingState(val movie: Movie, val appLabel: String)

data class UserMessage(val id: Long, @StringRes val text: Int, val args: List<String> = emptyList())
