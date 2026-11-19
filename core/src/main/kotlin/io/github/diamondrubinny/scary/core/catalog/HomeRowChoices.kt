// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

/**
 * Which rows of the home screen the viewer turned off (Settings → Home screen rows). A row is known by a key: one of
 * the fixed kinds below, or a collection's id behind [COLLECTION_PREFIX] (a library category, a row from the web, a TMDB
 * row). "My apps" has no key: a home screen without its apps is a dead end, so it cannot be turned off. The rows with
 * switches of their own (the seasonal rows, Ask SCARY) keep them.
 */
object HomeRowChoices {
    const val CONTINUE = "continue"
    const val STREMIO = "stremio"
    const val RECENT = "recent"
    const val MY_LIST = "mylist"
    const val BECAUSE = "because"
    const val MOODS = "moods"
    const val TIME = "time"
    const val NOW_OUT = "nowout"

    /** The fixed kinds, in the order the home screen shows them. */
    val FIXED = listOf(CONTINUE, STREMIO, NOW_OUT, RECENT, MY_LIST, BECAUSE, MOODS, TIME)

    const val COLLECTION_PREFIX = "c:"

    /** The most rows kept as turned off: more than the home screen can hold. */
    const val MAX = 300

    private val KEY = Regex("[A-Za-z0-9:_.-]{1,80}")

    fun collectionKey(collectionId: String): String = COLLECTION_PREFIX + collectionId

    /** [hidden] with [key] turned off when it was on, and on when it was off. */
    fun toggled(hidden: Set<String>, key: String): Set<String> = sanitize(if (key in hidden) hidden - key else hidden + key)

    /** What is kept: well-formed keys only, at most [MAX]. */
    fun sanitize(raw: Collection<String>): Set<String> = raw.asSequence().filter(KEY::matches).take(MAX).toCollection(LinkedHashSet())
}
