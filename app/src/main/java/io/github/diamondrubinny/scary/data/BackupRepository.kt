// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import io.github.diamondrubinny.scary.core.backup.Backup
import io.github.diamondrubinny.scary.core.catalog.OriginFilter
import io.github.diamondrubinny.scary.core.cinemeta.Cinemeta
import io.github.diamondrubinny.scary.core.cinemeta.CinemetaMeta
import io.github.diamondrubinny.scary.core.model.MediaKind
import io.github.diamondrubinny.scary.remote.PhoneRemoteServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The viewer's backup (docs/NEXT_OPTIONS.md, stage 3): their lists and a few plain switches, made into a file's text
 * for the paired phone, and merged back from a file the phone sends. The file holds film ids and yes/no values only
 * (core Backup): no key, token, account, PIN, file name or link is ever read into it.
 *
 * A file read back is untrusted text: Backup.decode checks all of it, and restoring only adds to the lists. Films
 * the TV does not know yet get their details from Cinemeta afterwards, by IMDb id, when online data is on.
 */
class BackupRepository(
    private val settings: SettingsRepository,
    private val savedMovies: SavedMoviesRepository,
    private val cinemeta: CinemetaRepository,
    private val catalog: CatalogRepository,
    private val scope: CoroutineScope,
    private val appVersion: String,
) : PhoneRemoteServer.Backups {

    sealed interface Event {
        /** A backup went to the phone. */
        data object Saved : Event

        /** A file was merged in; [originsChanged] when the rule for hidden films changed with it. */
        data class Restored(val added: Backup.Added, val originsChanged: Boolean) : Event
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)

    /** For the screen, while SCARY shows: it says what happened. */
    val events: SharedFlow<Event> = _events.asSharedFlow()

    override suspend fun backup(): String? = try {
        savedMovies.load()
        val series = savedMovies.items.value.filter { it.kind == MediaKind.SERIES }.mapTo(HashSet()) { Cinemeta.ID_PREFIX + it.imdbId }
        val text = Backup.encode(settings.backupSnapshot(series), appVersion, LocalDate.now().toEpochDay())
        _events.tryEmit(Event.Saved)
        text
    } catch (e: java.io.IOException) {
        null
    }

    override suspend fun restore(text: String): String {
        val file = Backup.decode(text) ?: return "restore_invalid"
        val restored = try {
            settings.applyBackup(file)
        } catch (e: java.io.IOException) {
            return "restore_failed"
        }
        if (restored.originsChanged) {
            OriginFilter.enabled = settings.prefs.first().hideOrigins
            catalog.askForFreshAll()
        }
        scope.launch { fetchDetails(restored.after, file.series) }
        _events.tryEmit(Event.Restored(Backup.added(restored.before, restored.after), restored.originsChanged))
        return "restore_ok"
    }

    /**
     * My list and the bucket list show a film only with its details. Films found online that this TV has not kept
     * (a new TV, a reinstall) are asked for from Cinemeta, one at a time, by IMDb id only.
     */
    private suspend fun fetchDetails(lists: Backup.Snapshot, series: Set<String>) {
        val prefs = settings.prefs.first()
        if (!prefs.onlineEnabled) return
        savedMovies.load()
        val kept = savedMovies.items.value.mapTo(HashSet()) { it.imdbId }
        val listed = (lists.myList + lists.bucket).distinct().filter { it.startsWith(Cinemeta.ID_PREFIX) }
        // Everything whose saved details must stay, as when a film is saved from its page: the restored lists, and
        // what the TV already keeps for Recently opened.
        val keep = (listed + prefs.recentIds).filter { it.startsWith(Cinemeta.ID_PREFIX) }.mapTo(HashSet()) { it.removePrefix(Cinemeta.ID_PREFIX) }
        val found = ArrayList<CinemetaMeta>()
        for (id in listed) {
            val imdb = id.removePrefix(Cinemeta.ID_PREFIX)
            if (imdb in kept || !IMDB_ID.matches(imdb)) continue
            cinemeta.meta(imdb, if (id in series) MediaKind.SERIES else MediaKind.MOVIE)?.let(found::add)
            delay(REQUEST_GAP_MS)
        }
        savedMovies.saveAll(found, keep)
    }

    private companion object {
        val IMDB_ID = Regex("tt\\d{5,10}")
        const val REQUEST_GAP_MS = 150L
    }
}
