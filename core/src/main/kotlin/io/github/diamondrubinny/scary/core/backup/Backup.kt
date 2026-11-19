// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The viewer's backup file: their lists (My list, watched and when, loved, not for me, the bucket list), which home
 * rows they turned off, and a few plain switches. Film ids and yes/no values only: never a key, a token, an account,
 * the parental PIN or its settings, and no file names or links.
 *
 * The file comes back from a phone, so reading it trusts nothing: a strict shape, a size cap, every id checked
 * against a pattern, every list capped, unknown fields ignored. Restoring merges: nothing the TV holds is removed.
 */
object Backup {
    const val FORMAT = "scary-backup"
    const val VERSION = 1

    /** A backup is far smaller than this (1,000 watched films are about 35 KB); anything larger is not one. */
    const val MAX_CHARS = 120_000

    const val MAX_MY_LIST = 100
    const val MAX_WATCHED = 1_000
    const val MAX_RATED = 500
    const val MAX_BUCKET = 30
    const val MAX_HIDDEN_ROWS = 300

    /** The switches a backup may carry. None unlocks anything, contacts a service or belongs to the parental lock. */
    val SWITCHES = listOf("seasonal_rows", "auto_watched", "hide_watched", "hide_origins", "trailer_autoplay", "trailer_sound")

    data class Snapshot(
        val myList: List<String> = emptyList(),
        val watched: List<String> = emptyList(),
        /** The day (days since 1970) each film was marked watched here. */
        val watchedDays: Map<String, Long> = emptyMap(),
        val loved: List<String> = emptyList(),
        val disliked: List<String> = emptyList(),
        val bucket: List<String> = emptyList(),
        /** Ids above that are series: their details are asked for as series after a restore. */
        val series: Set<String> = emptySet(),
        val hiddenRows: Set<String> = emptySet(),
        val switches: Map<String, Boolean> = emptyMap(),
    ) {
        val isEmpty: Boolean
            get() = myList.isEmpty() && watched.isEmpty() && loved.isEmpty() && disliked.isEmpty() && bucket.isEmpty() &&
                hiddenRows.isEmpty() && switches.isEmpty()
    }

    /** What a restore added to the TV. */
    data class Added(val myList: Int, val watched: Int, val rated: Int, val bucket: Int)

    private val ID = Regex("[A-Za-z0-9:_.-]{1,80}")
    private val json = Json { ignoreUnknownKeys = true }

    /** The file's text: compact JSON, plain ASCII (ids and keys are checked), no line breaks. */
    fun encode(snapshot: Snapshot, appVersion: String, madeDay: Long): String {
        val clean = sanitize(snapshot)
        return buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("app", appVersion.filter { it.isLetterOrDigit() || it == '.' || it == '-' }.take(20))
            put("madeDay", madeDay)
            putJsonArray("myList") { clean.myList.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("watched") { clean.watched.forEach { add(JsonPrimitive(it)) } }
            putJsonObject("watchedDays") { clean.watchedDays.forEach { (id, day) -> put(id, day) } }
            putJsonArray("loved") { clean.loved.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("disliked") { clean.disliked.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("bucket") { clean.bucket.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("series") { clean.series.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("hiddenRows") { clean.hiddenRows.forEach { add(JsonPrimitive(it)) } }
            putJsonObject("settings") { clean.switches.forEach { (key, value) -> put(key, value) } }
        }.toString()
    }

    /**
     * What a file holds, or null when it is not a SCARY backup this version reads (another format, a newer version,
     * too large, not JSON). Anything in it that does not fit is left out, not trusted.
     */
    fun decode(text: String): Snapshot? {
        if (text.length > MAX_CHARS) return null
        val o = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            return null
        }
        if ((o["format"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull != FORMAT) return null
        val version = (o["version"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: return null
        if (version !in 1..VERSION) return null
        val days = (o["watchedDays"] as? JsonObject).orEmpty().entries.take(MAX_WATCHED * 2).mapNotNull { (id, day) ->
            (day as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.let { id to it }
        }.toMap()
        val switches = (o["settings"] as? JsonObject).orEmpty().entries.take(SWITCHES.size * 4).mapNotNull { (key, value) ->
            (value as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull?.let { key to it }
        }.toMap()
        return sanitize(
            Snapshot(
                myList = o.ids("myList"), watched = o.ids("watched"), watchedDays = days, loved = o.ids("loved"),
                disliked = o.ids("disliked"), bucket = o.ids("bucket"), series = o.ids("series").toSet(),
                hiddenRows = o.ids("hiddenRows").toSet(), switches = switches,
            ),
        )
    }

    /** [snapshot] with only what a backup may hold: checked ids, capped lists, known switches, plausible days. */
    fun sanitize(snapshot: Snapshot): Snapshot {
        val myList = clean(snapshot.myList, MAX_MY_LIST)
        val watched = clean(snapshot.watched, MAX_WATCHED)
        val loved = clean(snapshot.loved, MAX_RATED)
        // Loved and "not for me" exclude each other; a film in both is loved.
        val disliked = clean(snapshot.disliked.filterNot(loved::contains), MAX_RATED)
        // The bucket list is films only: an id the file itself marks as a series does not go in.
        val bucket = clean(snapshot.bucket.filterNot(snapshot.series::contains), MAX_BUCKET)
        val listed = (myList + watched + loved + disliked + bucket).toSet()
        val watchedSet = watched.toSet()
        return Snapshot(
            myList = myList,
            watched = watched,
            watchedDays = snapshot.watchedDays.filter { (id, day) -> id in watchedSet && day in MIN_DAY..MAX_DAY },
            loved = loved,
            disliked = disliked,
            bucket = bucket,
            series = snapshot.series.filterTo(LinkedHashSet()) { it in listed },
            hiddenRows = snapshot.hiddenRows.asSequence().filter(ID::matches).take(MAX_HIDDEN_ROWS).toCollection(LinkedHashSet()),
            switches = snapshot.switches.filterKeys { it in SWITCHES },
        )
    }

    /**
     * The TV's lists after a restore: everything in [current] stays, in its place; what [restored] adds comes after.
     * Where the two disagree about a film ("loved" here, "not for me" in the file), the TV's own answer stays. The
     * home rows turned off and the switches are taken from the file, since they are settings, not lists.
     */
    fun merge(current: Snapshot, restored: Snapshot): Snapshot {
        val here = sanitize(current)
        val file = sanitize(restored)
        val watched = clean(here.watched + file.watched, MAX_WATCHED)
        val loved = clean(here.loved + file.loved.filterNot(here.disliked::contains), MAX_RATED)
        val disliked = clean(here.disliked + file.disliked.filterNot(loved::contains), MAX_RATED)
        return sanitize(
            Snapshot(
                myList = clean(here.myList + file.myList, MAX_MY_LIST),
                watched = watched,
                watchedDays = file.watchedDays + here.watchedDays,
                loved = loved,
                disliked = disliked,
                bucket = clean(here.bucket + file.bucket, MAX_BUCKET),
                series = here.series + file.series,
                hiddenRows = file.hiddenRows,
                switches = here.switches + file.switches,
            ),
        )
    }

    /** What [after] holds that [before] did not. */
    fun added(before: Snapshot, after: Snapshot): Added = Added(
        myList = after.myList.count { it !in before.myList },
        watched = after.watched.size - after.watched.count(before.watched.toSet()::contains),
        rated = after.loved.count { it !in before.loved } + after.disliked.count { it !in before.disliked },
        bucket = after.bucket.count { it !in before.bucket },
    )

    private fun clean(ids: List<String>, max: Int): List<String> = ids.asSequence().filter(ID::matches).distinct().take(max).toList()

    private fun JsonObject.ids(key: String): List<String> =
        (this[key] as? JsonArray).orEmpty().asSequence().take(MAX_WATCHED * 2)
            .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull }.toList()

    private fun JsonObject?.orEmpty(): Map<String, kotlinx.serialization.json.JsonElement> = this ?: emptyMap()

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()

    // 2000-01-01 to 2100-01-01, in days since 1970: a day outside is not when a film was watched.
    private const val MIN_DAY = 10_957L
    private const val MAX_DAY = 47_482L
}
