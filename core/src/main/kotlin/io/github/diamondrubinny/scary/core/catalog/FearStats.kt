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
) {
    val isEmpty: Boolean get() = watchedCount == 0 && lovedCount == 0

    companion object {
        /** [watched] newest first, as the watched list keeps them. */
        fun of(watched: List<Movie>, lovedCount: Int): FearStats {
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
            )
        }
    }
}
