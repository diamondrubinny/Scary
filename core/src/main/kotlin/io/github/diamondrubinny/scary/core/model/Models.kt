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
    val director: String,
    val runtimeMin: Int,
    val genre: Genre,
    val subgenres: List<Subgenre>,
    /** Editorial scare rating, 1 (mild) to 5 (relentless). */
    val fear: Int,
    /** Poster tint used for generated artwork, 0..359. */
    val hue: Int,
    /** Poster saturation in percent, 0..100. */
    val saturation: Int = 55,
    val synopsisHe: String,
    val synopsisEn: String,
) {
    fun synopsis(language: Language): String = if (language == Language.HE) synopsisHe else synopsisEn
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
