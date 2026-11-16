// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.catalog

import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta

/**
 * Quality control for the rows. Every film a row holds is checked against the rules the rows are built from, in one
 * place: whether it belongs to the row's place ([Origin]), whether it is one of the films kept off every shelf
 * ([OriginFilter]), whether it is on theme at all (no documentaries or children's films) and whether the row lists it
 * twice. [audit] reports what it finds; [clean] is the same check used to take the offenders out, so a cache written by
 * an older version, or a row a bug let a wrong film into, never reaches the screen. Pure logic, tested on real films.
 */
object RowAudit {
    enum class Problem {
        /** An American film in a row about another place (its language is not the row's). */
        AMERICAN_LED,

        /** American too, and its original language is not known yet: held back until it is. */
        ORIGIN_UNCLEAR,

        /** In a row about a place its first country is not in. */
        OFF_REGION,

        /** One of the films kept off every shelf (Arab and Indian films). */
        HIDDEN_ORIGIN,

        /** A documentary, a film for children, or not horror, thriller or mystery at all. */
        OFF_THEME,

        /** The same film twice in one row. */
        DUPLICATE,
    }

    data class Finding(val rowId: String, val imdbId: String, val name: String, val problem: Problem)

    /** What is wrong with [meta] in the row [rowId]; null when nothing is. */
    fun check(rowId: String, meta: CinemetaMeta): Problem? {
        if (OriginFilter.hides(meta)) return Problem.HIDDEN_ORIGIN
        if (!Cinemeta.onTheme(meta)) return Problem.OFF_THEME
        val region = Categories.PLACE_ROWS[rowId] ?: return null
        return when (Origin.verdict(region, meta)) {
            Origin.Verdict.IN -> null
            Origin.Verdict.UNKNOWN -> Problem.ORIGIN_UNCLEAR
            Origin.Verdict.OUT -> if (Origin.listsUnitedStates(meta) && Origin.proposedFor(region, meta)) Problem.AMERICAN_LED else Problem.OFF_REGION
        }
    }

    /** Every problem in [rows] (row id to its films, in order). Empty when the rows are clean. */
    fun audit(rows: Map<String, List<CinemetaMeta>>): List<Finding> = rows.flatMap { (rowId, films) ->
        val seen = HashSet<String>()
        films.mapNotNull { meta ->
            val problem = if (!seen.add(meta.imdbId)) Problem.DUPLICATE else check(rowId, meta)
            problem?.let { Finding(rowId, meta.imdbId, meta.name, it) }
        }
    }

    /**
     * The categories' lists ([categories]: row id to IMDb ids, their films' details in [metas]) without the films that
     * do not belong; with how many were taken out. A film whose details are not in [metas] stays: nothing says it is wrong.
     */
    fun clean(categories: Map<String, List<String>>, metas: Map<String, CinemetaMeta>): Pair<Map<String, List<String>>, Int> {
        var removed = 0
        val cleaned = categories.mapValues { (rowId, ids) ->
            val seen = HashSet<String>()
            ids.filter { id ->
                val keep = seen.add(id) && (metas[id]?.let { check(rowId, it) == null } ?: true)
                if (!keep) removed++
                keep
            }
        }.filterValues { it.isNotEmpty() }
        return cleaned to removed
    }

    /** Films (of [pool]) that a place row proposed by country but the American check keeps out, and those still unclear. */
    data class Held(val keptOut: Int, val unclear: Int)

    fun held(pool: List<CinemetaMeta>): Held {
        var keptOut = 0
        var unclear = 0
        for (meta in pool) {
            val verdicts = Origin.Region.entries.filter { Origin.proposedFor(it, meta) }.map { Origin.verdict(it, meta) }
            if (Origin.Verdict.UNKNOWN in verdicts) unclear++ else if (verdicts.isNotEmpty() && Origin.Verdict.IN !in verdicts) keptOut++
        }
        return Held(keptOut, unclear)
    }
}
