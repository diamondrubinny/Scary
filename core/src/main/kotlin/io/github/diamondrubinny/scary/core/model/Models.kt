// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** UI languages. [code] keys localized text; [nativeName] is how the language names itself in the picker. */
enum class Language(val code: String, val nativeName: String, val tmdbTag: String, val rtl: Boolean) {
    HE("he", "עברית", "he-IL", true),
    EN("en", "English", "en-US", false),
    PT("pt", "Português", "pt-BR", false),
    RU("ru", "Русский", "ru-RU", false),
    AR("ar", "العربية", "ar-SA", true);

    fun next(): Language = entries[(ordinal + 1) % entries.size]

    companion object {
        /** Android reports Hebrew as either "he" or the legacy "iw". Unknown languages fall back to English. */
        fun fromCode(code: String): Language = when (code.lowercase()) {
            "he", "iw" -> HE
            "pt" -> PT
            "ru" -> RU
            "ar" -> AR
            else -> EN
        }
    }
}

@Serializable
enum class Genre {
    @SerialName("horror") HORROR,
    @SerialName("thriller") THRILLER,
}

@Serializable
enum class Subgenre {
    @SerialName("psychological") PSYCHOLOGICAL,
    @SerialName("supernatural") SUPERNATURAL,
    @SerialName("possession") POSSESSION,
    @SerialName("ghost") GHOST,
    @SerialName("slasher") SLASHER,
    @SerialName("creature") CREATURE,
    @SerialName("folk") FOLK,
    @SerialName("social") SOCIAL,
    @SerialName("found_footage") FOUND_FOOTAGE,
    @SerialName("survival") SURVIVAL,
    @SerialName("mystery") MYSTERY,
    @SerialName("crime") CRIME,
    @SerialName("zombie") ZOMBIE,
    @SerialName("vampire") VAMPIRE,
    @SerialName("body") BODY,
    @SerialName("haunted_house") HAUNTED_HOUSE,
}

/** A film, or a series (whose runtime is an episode's, and whose page in Stremio lists the episodes). */
@Serializable
enum class MediaKind {
    @SerialName("movie") MOVIE,
    @SerialName("series") SERIES,
}

/** Editorial notes on how a movie feels, for moods that subgenres alone cannot tell. */
@Serializable
enum class MovieTag {
    /** Dread that builds slowly rather than a run of shocks. */
    @SerialName("slow_burn") SLOW_BURN,
}

@Serializable
data class Movie(
    val id: String,
    val imdbId: String,
    val title: String,
    /** Hebrew title from TMDB when one exists; empty otherwise. */
    val titleHe: String = "",
    val year: Int,
    val director: String = "",
    val runtimeMin: Int = 0,
    val genre: Genre,
    val subgenres: List<Subgenre> = emptyList(),
    /** Editorial scare rating, 1 (mild) to 5 (relentless). Null for movies outside the curated library. */
    val fear: Int? = null,
    /** Poster tint used for generated artwork, 0..359. */
    val hue: Int = 0,
    /** Poster saturation in percent, 0..100. */
    val saturation: Int = 55,
    val synopsisHe: String = "",
    val synopsisEn: String = "",
    /** Artwork and extras filled in from Cinemeta at runtime; absent in the bundled catalog. */
    val posterUrl: String? = null,
    val backgroundUrl: String? = null,
    val logoUrl: String? = null,
    val imdbRating: Double? = null,
    val cast: List<String> = emptyList(),
    val trailerYoutubeId: String? = null,
    /**
     * Other trailers of the same movie, in Cinemeta's order: many studio trailers may not play inside apps (YouTube
     * error 150), so the player tries these next.
     */
    val trailerAlternates: List<String> = emptyList(),
    /** ISO release date of a film not out yet (from TMDB's coming-soon row); null otherwise. */
    val releaseDate: String? = null,
    /** Titles and synopses in other languages, keyed by [Language.code] (curated translations or TMDB). */
    val titles: Map<String, String> = emptyMap(),
    val synopses: Map<String, String> = emptyMap(),
    /** True for the hand-picked library, false for rows pulled from Cinemeta. */
    val curated: Boolean = true,
    val tags: List<MovieTag> = emptyList(),
    /** A film or a series. Everything else in this class means the same for both; [runtimeMin] is one episode's. */
    val kind: MediaKind = MediaKind.MOVIE,
    /** A series' last year; null for films, for one-season series and for series still running ([ongoing]). */
    val endYear: Int? = null,
    /** A series still running: its years read "2022–". */
    val ongoing: Boolean = false,
) {
    val isSeries: Boolean get() = kind == MediaKind.SERIES

    /** "1988" for a film or a single year, "2016–2025" for a series that ended, "2022–" for one still running. */
    val yearsLabel: String get() = when {
        !isSeries -> year.toString()
        ongoing -> "$year–"
        endYear != null && endYear > year -> "$year–$endYear"
        else -> year.toString()
    }

    fun displayTitle(language: Language): String =
        titles[language.code]?.takeIf(String::isNotBlank)
            ?: titleHe.takeIf { language == Language.HE && it.isNotBlank() }
            ?: title

    /** Hebrew synopsis when there is one, otherwise English, so online-only movies still show text. */
    fun synopsis(language: Language): String =
        synopses[language.code]?.takeIf(String::isNotBlank)
            ?: synopsisHe.takeIf { language == Language.HE && it.isNotBlank() }
            ?: synopsisEn.ifBlank { synopsisHe }

    /** The release date when it is after [today]: the film is coming soon. */
    fun comingOn(today: java.time.LocalDate): java.time.LocalDate? =
        releaseDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }?.takeIf { it.isAfter(today) }

    /** Every known trailer, the main one first. */
    val trailerIds: List<String> get() = (listOfNotNull(trailerYoutubeId) + trailerAlternates).distinct()

    /** Unknown fear counts as the maximum for the parental lock, so unrated movies stay behind the PIN. */
    val fearForLock: Int get() = fear ?: 5
}

@Serializable
data class MovieCollection(
    val id: String,
    val titleHe: String,
    val titleEn: String,
    val movieIds: List<String>,
    /** Titles in further languages, keyed by [Language.code]. */
    val titles: Map<String, String> = emptyMap(),
) {
    fun title(language: Language): String = when {
        language == Language.HE -> titleHe
        language == Language.EN -> titleEn
        else -> titles[language.code]?.takeIf(String::isNotBlank) ?: titleEn
    }
}

@Serializable
data class Catalog(
    val version: Int,
    val movies: List<Movie>,
    val collections: List<MovieCollection>,
) {
    private val byId: Map<String, Movie> by lazy { movies.associateBy { it.id } }

    fun movie(id: String): Movie? = byId[id]

    fun moviesOf(collection: MovieCollection): List<Movie> = collection.movieIds.mapNotNull(::movie)
}
