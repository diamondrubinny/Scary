// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.backup.Backup
import io.github.diamondrubinny.scary.core.backup.Backup.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private val full = Snapshot(
        myList = listOf("the-shining-1980", "cm_tt0078748"),
        watched = listOf("cm_tt0078748", "hereditary-2018"),
        watchedDays = mapOf("cm_tt0078748" to 20_700L),
        loved = listOf("hereditary-2018"),
        disliked = listOf("cm_tt1234567"),
        bucket = listOf("the-shining-1980"),
        series = setOf("cm_tt0078748"),
        hiddenRows = setOf("moods", "c:slashers"),
        switches = mapOf("hide_watched" to true, "trailer_sound" to false),
    )

    @Test
    fun aBackupReadsBackAsItWasWritten() {
        val text = Backup.encode(full, "0.5.20", 20_776)
        assertEquals(full, Backup.decode(text))
        // One line of plain text: it travels as a command's text, which takes no control characters.
        assertTrue(text.none { it < ' ' || it > '~' })
        assertTrue(text.length < Backup.MAX_CHARS)
    }

    @Test
    fun aBackupHoldsNoSecretsAndNothingOutsideItsList() {
        val text = Backup.encode(full.copy(switches = full.switches + mapOf("parental_pin_hash" to true, "online_artwork" to true)), "0.5.20", 20_776)
        assertFalse("pin" in text)
        assertFalse("online_artwork" in text)
        for (word in listOf("token", "key", "password", "secret")) assertFalse(word, word in text.lowercase())
    }

    @Test
    fun whatIsNotABackupIsRefused() {
        assertNull(Backup.decode(""))
        assertNull(Backup.decode("not json"))
        assertNull(Backup.decode("[1,2,3]"))
        assertNull(Backup.decode("""{"format":"other","version":1}"""))
        assertNull(Backup.decode("""{"format":"scary-backup"}"""))
        assertNull(Backup.decode("""{"format":"scary-backup","version":"1"}"""))
        // A file from a newer SCARY may mean something else: it is not guessed at.
        assertNull(Backup.decode("""{"format":"scary-backup","version":${Backup.VERSION + 1}}"""))
        assertNull(Backup.decode("""{"format":"scary-backup","version":1,"pad":"${"x".repeat(Backup.MAX_CHARS)}"}"""))
        // The least a backup can be.
        assertNotNull(Backup.decode("""{"format":"scary-backup","version":1}"""))
    }

    @Test
    fun aFileIsNeverTrusted() {
        val text = """{"format":"scary-backup","version":1,
            "myList":["ok-1","<script>","a b","ok-1",7,null,{"x":1},"${"x".repeat(81)}","../../etc"],
            "watched":"not a list","watchedDays":{"ok-1":20000,"gone":20000,"ok-2":"20000"},
            "loved":["both","l1"],"disliked":["both","d1"],
            "bucket":[${(1..40).joinToString(",") { "\"b$it\"" }}],
            "series":["ok-1","stranger"],"hiddenRows":["moods","bad key!"],
            "settings":{"hide_watched":true,"parental_pin_hash":true,"trailer_sound":"yes","family_night":false},
            "extra":{"deep":[1,2,3]}}"""
        val read = Backup.decode(text)!!
        assertEquals(listOf("ok-1"), read.myList)
        assertEquals(emptyList<String>(), read.watched)
        // A day is kept only for a film in the watched list.
        assertEquals(emptyMap<String, Long>(), read.watchedDays)
        assertEquals(listOf("both", "l1"), read.loved)
        assertEquals(listOf("d1"), read.disliked)
        assertEquals(Backup.MAX_BUCKET, read.bucket.size)
        assertEquals(setOf("ok-1"), read.series)
        assertEquals(setOf("moods"), read.hiddenRows)
        assertEquals(mapOf("hide_watched" to true), read.switches)
    }

    @Test
    fun listsAreCapped() {
        val many = (1..3_000).map { "f$it" }
        val read = Backup.decode(Backup.encode(Snapshot(myList = many, watched = many, loved = many, bucket = many), "x", 20_000))!!
        assertEquals(Backup.MAX_MY_LIST, read.myList.size)
        assertEquals(Backup.MAX_WATCHED, read.watched.size)
        assertEquals(Backup.MAX_RATED, read.loved.size)
        assertEquals(Backup.MAX_BUCKET, read.bucket.size)
    }

    @Test
    fun restoringAddsAndNeverRemoves() {
        val here = Snapshot(
            myList = listOf("a", "b"), watched = listOf("w1"), watchedDays = mapOf("w1" to 20_500L),
            loved = listOf("x"), disliked = listOf("y"), bucket = listOf("k"), hiddenRows = setOf("time"),
            switches = mapOf("hide_watched" to false, "auto_watched" to false),
        )
        val file = Snapshot(
            myList = listOf("b", "c"), watched = listOf("w1", "w2"), watchedDays = mapOf("w1" to 20_000L, "w2" to 20_100L),
            loved = listOf("y", "z"), disliked = listOf("x", "q"), bucket = listOf("k", "m"), series = setOf("c"),
            hiddenRows = setOf("moods"), switches = mapOf("hide_watched" to true),
        )
        val merged = Backup.merge(here, file)
        assertEquals(listOf("a", "b", "c"), merged.myList)
        assertEquals(listOf("w1", "w2"), merged.watched)
        // The TV's own day for a film stays.
        assertEquals(mapOf("w1" to 20_500L, "w2" to 20_100L), merged.watchedDays)
        // "x" is loved here and "y" is not for me here: the file's opposite answers are not taken.
        assertEquals(listOf("x", "z"), merged.loved)
        assertEquals(listOf("y", "q"), merged.disliked)
        assertEquals(listOf("k", "m"), merged.bucket)
        assertEquals(setOf("c"), merged.series)
        // Settings come from the file; a switch the file does not have stays.
        assertEquals(setOf("moods"), merged.hiddenRows)
        assertEquals(mapOf("hide_watched" to true, "auto_watched" to false), merged.switches)
        assertEquals(Backup.Added(myList = 1, watched = 1, rated = 2, bucket = 1), Backup.added(here, merged))
        // Restoring the same file again changes nothing.
        assertEquals(merged, Backup.merge(merged, file))
        assertEquals(Backup.Added(0, 0, 0, 0), Backup.added(merged, Backup.merge(merged, file)))
    }

    @Test
    fun aFullListKeepsWhatTheTvHasFirst() {
        val here = Snapshot(myList = (1..Backup.MAX_MY_LIST).map { "mine$it" })
        val merged = Backup.merge(here, Snapshot(myList = listOf("new")))
        assertEquals(here.myList, merged.myList)
    }
}
