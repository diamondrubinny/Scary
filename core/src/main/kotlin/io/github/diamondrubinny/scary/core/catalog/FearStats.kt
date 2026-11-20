// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.model.Subgenre

/** "Your year in fear": what the viewer's watched list says about them. Computed on the TV, never sent anywhere. */
data class FearStats(
    val watchedCount: Int,
    val totalMinutes: Int,
    /** Over the watched films with a fear level; null when none has one. */
    val averageFear: Double?,
    /** The scariest film watched; of equals, the one watched most recently. */
    val scariest: Movie?,
    val favoriteSubgenre: Subgenre?,
    /** 1980 for the 1980s. */
    val favoriteDecade: Int?,
    val lovedCount: Int,
    /** The director with the most watched films, when at least [MIN_FAVORITE] are theirs; null otherwise. */
    val favoriteDirector: String? = null,
    /** The actor in the most watched films, when at least [MIN_FAVORITE] hold them; null otherwise. */
    val topActor: String? = null,
    /** How many of the watched are series. */
    val seriesCount: Int = 0,
    /** 1 to 12: the month with the most films marked as watched, among those with a day; null when no film has one. */
    val busiestMonth: Int? = null,
) {
    val isEmpty: Boolean get() = watchedCount == 0 && lovedCount == 0

    companion object {
        /** A director or an actor is a favourite from this many watched films on: one film says nothing. */
        const val MIN_FAVORITE = 2

        /**
         * [watched] newest first, as the watched list keeps them. [watchedDays] (film id to the epoch day it was
         * marked as watched) is for the busiest month: given for one calendar year, never across years.
         */
        fun of(watched: List<Movie>, lovedCount: Int, watchedDays: Map<String, Long> = emptyMap()): FearStats {
            val rated = watched.filter { it.fear != null }
            return FearStats(
                watchedCount = watched.size,
                totalMinutes = watched.sumOf { it.runtimeMin },
                averageFear = rated.takeIf { it.isNotEmpty() }?.map { it.fear!! }?.average(),
                scariest = rated.maxByOrNull { it.fear!! },
                favoriteSubgenre = watched.flatMap { it.subgenres }.groupingBy { it }.eachCount()
                    .maxWithOrNull(compareBy<Map.Entry<Subgenre, Int>> { it.value }.thenByDescending { it.key.ordinal })?.key,
                favoriteDecade = watched.groupingBy(Decades::of).eachCount()
                    .maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })?.key,
                lovedCount = lovedCount,
                favoriteDirector = mostSeen(watched.map { film -> film.director.split(',').map(String::trim) }),
                topActor = mostSeen(watched.map { it.cast }),
                seriesCount = watched.count { it.isSeries },
                busiestMonth = watched.mapNotNull { watchedDays[it.id] }
                    .groupingBy { java.time.LocalDate.ofEpochDay(it).monthValue }.eachCount()
                    // Of equals, the later month: the one fresher in memory.
                    .maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })?.key,
            )
        }

        /**
         * The name found in the most films of [namesPerFilm] (each film counts a name once; case aside), written as
         * it first appears. Of equals, the one in the more recently watched film. Null below [MIN_FAVORITE] films.
         */
        private fun mostSeen(namesPerFilm: List<List<String>>): String? {
            val counts = LinkedHashMap<String, Int>()
            val written = HashMap<String, String>()
            for (names in namesPerFilm) {
                for (name in names.filter { it.isNotBlank() }.distinctBy { it.lowercase() }) {
                    val key = name.lowercase()
                    counts[key] = (counts[key] ?: 0) + 1
                    written.putIfAbsent(key, name)
                }
            }
            // maxByOrNull keeps the first of equals, and the map keeps the order the names came in: newest film first.
            return counts.entries.maxByOrNull { it.value }?.takeIf { it.value >= MIN_FAVORITE }?.let { written[it.key] }
        }
    }
}
