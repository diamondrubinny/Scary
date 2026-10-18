package io.github.diamondrubinny.scary.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Language {
    HE,
    EN;

    companion object {
        /** Android reports Hebrew as either "he" or the legacy "iw". */
        fun fromCode(code: String): Language = if (code == "he" || code == "iw") HE else EN
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
}

@Serializable
data class Movie(
    val id: String,
    val imdbId: String,
    val title: String,
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
    /** True for the hand-picked library, false for rows pulled from Cinemeta. */
    val curated: Boolean = true,
) {
    /** Hebrew synopsis when there is one, otherwise English, so online-only movies still show text. */
    fun synopsis(language: Language): String =
        if (language == Language.HE && synopsisHe.isNotBlank()) synopsisHe else synopsisEn.ifBlank { synopsisHe }

    /** Unknown fear counts as the maximum for the parental lock, so unrated movies stay behind the PIN. */
    val fearForLock: Int get() = fear ?: 5
}

@Serializable
data class MovieCollection(
    val id: String,
    val titleHe: String,
    val titleEn: String,
    val movieIds: List<String>,
) {
    fun title(language: Language): String = if (language == Language.HE) titleHe else titleEn
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
