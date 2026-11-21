// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.profiles

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * "Who is watching?" (docs/PROFILES_PLAN.md): up to [MAX] viewers on one TV, each with their own lists. A profile is a
 * name and a colour, nothing else: no photo, age or account. The first profile ([FIRST]) is what the TV always held:
 * its lists stay under the keys they always had, so a TV with one viewer changes in nothing and nothing is moved.
 * Every further profile keeps its lists under the same keys with a [suffix].
 *
 * A "gentle" profile always shows only what family night shows; leaving it asks for the PIN when one is set.
 */
object Profiles {
    const val MAX = 5
    const val MAX_NAME = 20
    const val COLOURS = 6
    const val FIRST = "p1"
    private val ids = (1..MAX).map { "p$it" }
    private val json = Json { ignoreUnknownKeys = true }

    /** [name] is empty only for the first profile before anyone named it; the screen then shows a default name. */
    data class Profile(val id: String, val name: String = "", val colour: Int = 0, val gentle: Boolean = false)

    private val onlyFirst = listOf(Profile(FIRST))

    /** What goes after a list's key for this profile: nothing for the first, "@p2" for the second. */
    fun suffix(id: String?): String = if (id != null && id != FIRST && id in ids) "@$id" else ""

    /**
     * A name as it is kept, or null when nothing is left of it: one line, no control or direction characters (a name
     * must not turn the text around it), single spaces, at most [MAX_NAME] characters.
     */
    fun cleanName(text: String): String? = text
        .filterNot { it.isISOControl() || it in '​'..'‏' || it in '‪'..'‮' || it in '⁦'..'⁩' || it == '﻿' }
        .trim().replace(Regex("\\s+"), " ").take(MAX_NAME).trim().ifEmpty { null }

    /** The stored list, read as untrusted: always the first profile, then at most four more, each id once. */
    fun decode(raw: String?): List<Profile> {
        val array = try {
            raw?.let { json.parseToJsonElement(it) as? JsonArray }
        } catch (e: IllegalArgumentException) {
            null
        } ?: return onlyFirst
        val read = array.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val id = (o["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it in ids } ?: return@mapNotNull null
            val name = (o["name"] as? JsonPrimitive)?.contentOrNull?.let(::cleanName).orEmpty()
            if (name.isEmpty() && id != FIRST) return@mapNotNull null
            Profile(id, name, ((o["c"] as? JsonPrimitive)?.intOrNull ?: 0).coerceIn(0, COLOURS - 1), (o["g"] as? JsonPrimitive)?.booleanOrNull ?: false)
        }.distinctBy { it.id }
        val first = read.firstOrNull { it.id == FIRST } ?: Profile(FIRST)
        return (listOf(first) + read.filter { it.id != FIRST }).take(MAX)
    }

    fun encode(profiles: List<Profile>): String = buildJsonArray {
        profiles.forEach { profile ->
            add(
                buildJsonObject {
                    put("id", profile.id)
                    put("name", profile.name)
                    put("c", profile.colour)
                    put("g", profile.gentle)
                },
            )
        }
    }.toString()

    /** The profile on screen: [activeId] when it still exists, else the first. */
    fun active(profiles: List<Profile>, activeId: String?): Profile =
        profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull { it.id == FIRST } ?: Profile(FIRST)

    /** The list with a new profile and that profile, or null when the name is empty or the TV already has [MAX]. */
    fun add(profiles: List<Profile>, name: String, colour: Int, gentle: Boolean): Pair<List<Profile>, Profile>? {
        val clean = cleanName(name) ?: return null
        val id = ids.firstOrNull { free -> profiles.none { it.id == free } } ?: return null
        if (profiles.size >= MAX) return null
        val added = Profile(id, clean, colour.coerceIn(0, COLOURS - 1), gentle)
        return (profiles + added) to added
    }

    /** The list with one profile's name, colour and gentleness changed; null for an empty name or an unknown id. */
    fun update(profiles: List<Profile>, id: String, name: String, colour: Int, gentle: Boolean): List<Profile>? {
        val clean = cleanName(name) ?: return null
        if (profiles.none { it.id == id }) return null
        return profiles.map { if (it.id == id) it.copy(name = clean, colour = colour.coerceIn(0, COLOURS - 1), gentle = gentle) else it }
    }

    /** The list without one profile; null for the first (it is the TV's own lists) or an unknown id. */
    fun remove(profiles: List<Profile>, id: String): List<Profile>? =
        if (id == FIRST || profiles.none { it.id == id }) null else profiles.filter { it.id != id }

    /** The colour a new profile gets: the first one no profile uses yet. */
    fun nextColour(profiles: List<Profile>): Int = (0 until COLOURS).firstOrNull { c -> profiles.none { it.colour == c } } ?: 0
}
