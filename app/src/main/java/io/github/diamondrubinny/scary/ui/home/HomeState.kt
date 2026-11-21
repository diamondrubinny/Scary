// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.home

import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.security.ParentalGate
import androidx.annotation.StringRes
import io.github.diamondrubinny.scary.core.catalog.Mood
import io.github.diamondrubinny.scary.core.catalog.Season
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.tmdb.WatchProvider
import io.github.diamondrubinny.scary.core.model.MovieCollection
import io.github.diamondrubinny.scary.data.InstalledApp
import io.github.diamondrubinny.scary.data.TvInput
import io.github.diamondrubinny.scary.core.catalog.TimeBudget
import io.github.diamondrubinny.scary.core.model.MediaKind
import io.github.diamondrubinny.scary.core.catalog.TimePlanResult

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
    val hideWatched: Boolean = false,
    val hideOrigins: Boolean = true,
    /** Home screen rows the viewer turned off (core HomeRowChoices keys). */
    val hiddenRows: Set<String> = emptySet(),
    /** Films with a "Remind me" set. */
    val remindedIds: Set<String> = emptySet(),
    val trailerAutoplay: Boolean = true,
    val trailerSound: Boolean = false,
    val trailerIntroSeen: Boolean = false,
    val lovedIds: Set<String> = emptySet(),
    val dislikedIds: Set<String> = emptySet(),
    /** When the sleep timer ends (wall clock), or null when it is off. */
    val sleepAtMillis: Long? = null,
    /** Films or series: the home screen's tab. */
    val tab: MediaKind = MediaKind.MOVIE,
    /** Picking films for the bucket list: OK on a poster adds or removes it instead of opening it. */
    val picking: Boolean = false,
    /** The bucket list, in order. */
    val bucketIds: List<String> = emptyList(),
    /** How many films are in "Recently opened" (settings offers to clear them). */
    val recentCount: Int = 0,
    val autoUpdateCheck: Boolean = true,
    val aiEnabled: Boolean = false,
    /** The AI section of settings, as stored (keys are not here: only whether each has one, read on demand). */
    val aiProvider: String = "",
    val aiModels: Map<String, String> = emptyMap(),
    val aiCustomUrl: String = "",
    val aiShareTaste: Boolean = false,
    val aiHomeRow: Boolean = true,
) {
    /** A film's place in the bucket list, from 1; null when it is not in it. */
    fun bucketNumber(movie: Movie): Int? = bucketIds.indexOf(movie.id).takeIf { it >= 0 }?.plus(1)

    fun isLocked(movie: Movie): Boolean = ParentalGate.isLocked(parentalEnabled, sessionUnlocked, lockFromFear, movie)

    fun isWatched(movie: Movie): Boolean = movie.id in watchedIds
}

/**
 * An actor's films: those SCARY holds whose cast names them and, once TMDB answered ([fromTmdb]), their horror,
 * thrillers and mysteries from there. [loading] while TMDB is being asked.
 */
data class PersonUi(val name: String, val movies: List<Movie>, val loading: Boolean, val fromTmdb: Boolean)

/** The whole series a film belongs to, its films in the order they came out (TMDB, with the viewer's key). */
data class FranchiseUi(val movieId: String, val name: String, val movies: List<Movie>, val language: Language? = null)

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
    /** The viewer's Real-Debrid files: personal files have no fear level, so the parental lock asks for the PIN. */
    data object RealDebrid : GatedAction
    /** Adding torrents to the viewer's Real-Debrid account (the sources feature): personal files again. */
    data object RdTorrents : GatedAction
    /** "Find sources" on a film's page: the viewer's indexer, asked for that film. */
    data class RdSourcesFor(val movie: Movie) : GatedAction
    /** [startAtMs] null: from the saved spot or the start, as the viewer chooses when there is a spot to go back to. */
    data class PlayRealDebrid(
        val file: io.github.diamondrubinny.scary.core.realdebrid.RealDebrid.RdFile,
        val movieId: String?,
        val startAtMs: Long? = null,
    ) : GatedAction
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

    /** "Continue in Stremio": films and series stopped in the middle in the viewer's own Stremio account. */
    data class StremioContinue(val items: List<ContinueItem>) : HomeRow {
        override val key = "stremio_continue"
    }

    /** "Continue watching": files of the viewer's Real-Debrid account stopped in the middle or with bookmarks. */
    data class ContinueWatching(val items: List<ContinueItem>) : HomeRow {
        override val key = "continue"
    }

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

    /** "Because you loved X": films like one the viewer loved. */
    data class BecauseYouLoved(val because: Movie, val movies: List<Movie>) : HomeRow {
        override val key = "because"
    }

    /** "Because you watched X": films like the one the viewer watched most recently. */
    data class BecauseYouWatched(val because: Movie, val movies: List<Movie>) : HomeRow {
        override val key = "because_watched"
    }

    /** "Now out": films the viewer asked to be reminded of, whose release day has come. */
    data class NowOut(val movies: List<Movie>) : HomeRow {
        override val key = "now_out"
    }

    /** Films the viewer watched, newest first: shown when watched films are hidden from the other rows. */
    data class Watched(val movies: List<Movie>) : HomeRow {
        override val key = "watched"
    }

    /** "How much time do you have?": the budget chips, and once one is picked, the films or the lineup for it. */
    data class TimePlan(val selected: TimeBudget?, val plan: TimePlanResult?) : HomeRow {
        override val key = "time"
    }

    /** "What do you love?": asks a viewer SCARY knows little about to pick films they love, until they do or say "Not now". */
    data object TasteInvite : HomeRow {
        override val key = "taste_invite"
    }

    /** "Ask SCARY": one-press AI requests, and a way to ask in one's own words. */
    data object AskAi : HomeRow {
        override val key = "ask_ai"
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

    /** One of the TV's own inputs: HDMI 1, the antenna... */
    data class Input(val input: TvInput) : AppTile {
        override val key = "input:${input.id}"
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

data class MovieDetails(
    val movie: Movie,
    val similar: List<Movie>,
    /** The director's other films in the library and the offline index; empty when the director is not known. */
    val byDirector: List<Movie> = emptyList(),
)

/** Read-only texts reachable from settings. */
enum class DocumentKind { Privacy, Accessibility }

sealed interface Screen {
    data object Home : Screen
    data class Details(val movieId: String) : Screen
    data object AllApps : Screen
    data object Settings : Screen
    data object Search : Screen
    data class Document(val kind: DocumentKind) : Screen
    data object Stats : Screen
    data object Bucket : Screen
    data object PhoneRemote : Screen
    data object TmdbAccount : Screen

    /** Settings → Stremio account. */
    data object StremioAccount : Screen
    data object TastePicker : Screen
    /** Settings → Home screen rows: each row on or off. */
    data object HomeRows : Screen

    /** "Films with <actor>", opened from the cast row of a film's page. */
    data class Person(val name: String) : Screen
    /** "Ask SCARY": suggestions, or ([chat]) the open conversation. */
    data class Ask(val chat: Boolean = false) : Screen
    /** The viewer's Real-Debrid account and its videos. */
    data object RealDebrid : Screen
    /** Adding a magnet link to the viewer's Real-Debrid account, and its newest torrents (the sources feature). */
    data object RdTorrents : Screen
    /** Searching the viewer's own indexer for a release to add (the sources feature); with [movieId], for that film. */
    data class RdSources(val movieId: String? = null) : Screen
    /** The internal player, playing the session in HomeViewModel.player. */
    data object Player : Screen
}

/** The viewer's Real-Debrid videos, as the account screen shows them. */
sealed interface RdFilesState {
    data object Idle : RdFilesState
    data object Loading : RdFilesState
    data class Ready(val files: List<io.github.diamondrubinny.scary.core.realdebrid.RealDebrid.RdFile>) : RdFilesState
    data object Unreachable : RdFilesState
}

/** What became of the magnet link the viewer last gave. */
sealed interface RdPullState {
    data object Idle : RdPullState
    data object Working : RdPullState
    data object Invalid : RdPullState
    data class Downloading(val progress: Int) : RdPullState
    data class Failed(val reason: io.github.diamondrubinny.scary.data.RealDebridRepository.PullError) : RdPullState
}

/** What the viewer's indexer answered for the last search. */
sealed interface RdSearchState {
    data object Idle : RdSearchState
    data object Loading : RdSearchState
    data class Ready(val candidates: List<io.github.diamondrubinny.scary.core.sources.Releases.Candidate>) : RdSearchState
    data object Unreachable : RdSearchState
    data object Refused : RdSearchState
    data object Unreadable : RdSearchState

    /** The source answers by film only, and this search is not a film's (the free search, or a series). */
    data object NeedsFilm : RdSearchState
}

/** The torrents screen: the last pull, the account's newest torrents (null while loading) and its active places. */
data class RdTorrentsState(
    val pull: RdPullState = RdPullState.Idle,
    val torrents: List<io.github.diamondrubinny.scary.core.realdebrid.RealDebrid.TorrentStatus>? = null,
    val unreachable: Boolean = false,
    val active: io.github.diamondrubinny.scary.core.realdebrid.RealDebrid.ActiveCount? = null,
)

/** What the internal player is playing: the file's key (for resuming), a link, and the film it is, if known. */
class PlayerSession(
    val key: String,
    val playable: io.github.diamondrubinny.scary.data.RealDebridRepository.Playable,
    val movieId: String?,
    val startAtMs: Long,
    /** The file after this one in the same torrent (a series, a film in parts), offered at the end. */
    val next: io.github.diamondrubinny.scary.core.realdebrid.RealDebrid.RdFile? = null,
    /** Subtitle files from the same torrent, offered with the file's own subtitles. */
    val subtitles: List<io.github.diamondrubinny.scary.core.realdebrid.RealDebrid.RdSubtitle> = emptyList(),
    /** Fetches one of [subtitles] as a UTF-8 file on the TV; null when it cannot be read. */
    val fetchSubtitle: suspend (io.github.diamondrubinny.scary.core.realdebrid.RealDebrid.RdSubtitle) -> java.io.File? = { null },
    /** Subtitles found online by the film's name; null when this file cannot be searched (tests, no repository). */
    val online: OnlineSubtitleSource? = null,
)

/**
 * Subtitles for one played file from the online service (docs/SUBTITLE_SEARCH_PLAN.md). [search] lists what the service
 * has for the film: null when it cannot be reached, empty when the film is not known or has none. [fetch] brings one
 * of them as a UTF-8 file on the TV; null when it cannot be read.
 */
class OnlineSubtitleSource(
    val search: suspend () -> List<io.github.diamondrubinny.scary.core.player.OnlineSubtitles.Entry>?,
    val fetch: suspend (io.github.diamondrubinny.scary.core.player.OnlineSubtitles.Entry) -> java.io.File?,
)

/** A file with a spot saved: play it from there, or from the start? */
class ResumeChoice(
    val file: io.github.diamondrubinny.scary.core.realdebrid.RealDebrid.RdFile,
    val movieId: String?,
    val positionMs: Long,
)

/** A card under "Continue watching": the file's history, and the SCARY film it was played for, if known. */
data class ContinueItem(val entry: io.github.diamondrubinny.scary.core.player.WatchEntry, val movie: Movie?)

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

/**
 * What SCARY asks on coming back from a bucket-list film: [finished] when the time away looks like the whole film (it
 * then leaves the list), otherwise the viewer may go back to it. [next] is the film after it, if any.
 */
data class BucketPrompt(val current: Movie, val next: Movie?, val finished: Boolean)

/**
 * One line of Settings → Home screen rows: a fixed kind of row ([title], a string resource) or a collection (a library
 * category, a row from the web or from TMDB), and whether it shows.
 */
data class RowChoice(val key: String, @param:androidx.annotation.StringRes val title: Int?, val collection: MovieCollection?, val shown: Boolean)
