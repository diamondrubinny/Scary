// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.model.Catalog
import io.github.diamondrubinny.scary.core.model.Movie

/**
 * "Family night": only the gentlest movies of the curated library, everywhere SCARY shows movies. Online movies
 * have no editorial fear level, so they never pass.
 */
object FamilyNight {
    const val MAX_FEAR = 2

    fun allows(movie: Movie): Boolean = movie.curated && (movie.fear ?: Int.MAX_VALUE) <= MAX_FEAR

    /** The catalog as family night sees it; collections keep only the movies that are left. */
    fun filter(catalog: Catalog): Catalog = catalog.copy(movies = catalog.movies.filter(::allows))
}
