// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.realdebrid.FileMatch
import io.github.diamondrubinny.scary.core.realdebrid.Magnets
import io.github.diamondrubinny.scary.core.realdebrid.RealDebrid
import io.github.diamondrubinny.scary.core.torbox.TorBox
import io.github.diamondrubinny.scary.data.RealDebridRepository.Files
import io.github.diamondrubinny.scary.data.RealDebridRepository.Playable
import io.github.diamondrubinny.scary.data.RealDebridRepository.Pull
import io.github.diamondrubinny.scary.data.RealDebridRepository.PullError
import java.io.File

/**
 * The viewer's accounts as one (docs/TORBOX_PLAN.md): Real-Debrid and TorBox each hold the viewer's own files, and the
 * screens show them in one list. A file's key and a torrent's id say whose it is ("torbox:…", "torbox42"), so each
 * request goes to the account that holds the thing; nothing is ever sent to the other one.
 */
class DebridAccounts(private val realDebrid: RealDebridRepository?, private val torBox: TorBoxRepository?) {
    /** Where a torrent the viewer adds goes; chosen by the viewer when both accounts are connected. */
    enum class Service { REAL_DEBRID, TORBOX }

    /** True when at least one account is connected. */
    val signedIn: Boolean get() = realDebrid?.signedIn == true || torBox?.signedIn == true

    /** The connected accounts, Real-Debrid first. */
    val services: List<Service>
        get() = listOfNotNull(Service.REAL_DEBRID.takeIf { realDebrid?.signedIn == true }, Service.TORBOX.takeIf { torBox?.signedIn == true })

    /** Which account [file] is in. */
    fun serviceOf(file: RealDebrid.RdFile): Service = if (isTorBox(file.key)) Service.TORBOX else Service.REAL_DEBRID

    private fun isTorBox(ref: String) = TorBox.parseFileRef(ref) != null

    private fun isTorBoxTorrent(id: String) = TorBox.parseStatusId(id) != null

    /**
     * The videos of every connected account, Real-Debrid's first. One account that cannot be reached does not hide the
     * other's files; the list is unreachable only when no connected account answered.
     */
    suspend fun files(force: Boolean = false): Files {
        val answers = listOfNotNull(
            realDebrid?.takeIf { it.signedIn }?.files(force),
            torBox?.takeIf { it.signedIn }?.files(force),
        )
        val ready = answers.filterIsInstance<Files.Ready>()
        return when {
            ready.isNotEmpty() -> Files.Ready(ready.flatMap { it.files })
            answers.any { it == Files.Unreachable } -> Files.Unreachable
            else -> Files.SignedOut
        }
    }

    /** The viewer's files that are [movie] (FileMatch), or empty when not signed in or not reachable. */
    suspend fun filesFor(movie: Movie): List<RealDebrid.RdFile> =
        (files() as? Files.Ready)?.files?.let { FileMatch.forMovie(movie, it) }.orEmpty()

    /** Adds [magnet] to the account [service] names; with none named, to the only connected one (Real-Debrid first). */
    suspend fun pull(magnet: Magnets.Magnet, service: Service? = null): Pull = when (service ?: services.firstOrNull()) {
        Service.REAL_DEBRID -> realDebrid?.takeIf { it.signedIn }?.pull(magnet)
        Service.TORBOX -> torBox?.takeIf { it.signedIn }?.pull(magnet)
        null -> null
    } ?: Pull.Failed(PullError.UNREACHABLE)

    /** The newest torrents of every connected account; null when none could be listed. */
    suspend fun recentTorrents(): List<RealDebrid.TorrentStatus>? {
        val asked = listOfNotNull(realDebrid?.takeIf { it.signedIn }, torBox?.takeIf { it.signedIn })
        if (asked.isEmpty()) return emptyList()
        val lists = listOf(realDebrid?.takeIf { it.signedIn }?.recentTorrents(), torBox?.takeIf { it.signedIn }?.recentTorrents())
        if (lists.all { it == null }) return null
        return lists.flatMap { it.orEmpty() }
    }

    /** Real-Debrid's count of active places; TorBox reports none. */
    suspend fun activeCount(): RealDebrid.ActiveCount? = realDebrid?.takeIf { it.signedIn }?.activeCount()

    suspend fun torrentStatus(id: String): RealDebrid.TorrentStatus? =
        if (isTorBoxTorrent(id)) torBox?.torrentStatus(id) else realDebrid?.torrentStatus(id)

    suspend fun torrentVideos(id: String): List<RealDebrid.RdFile>? =
        if (isTorBoxTorrent(id)) torBox?.torrentVideos(id) else realDebrid?.torrentVideos(id)

    suspend fun deleteTorrent(id: String): Boolean =
        (if (isTorBoxTorrent(id)) torBox?.deleteTorrent(id) else realDebrid?.deleteTorrent(id)) ?: false

    suspend fun deleteFile(file: RealDebrid.RdFile): Boolean =
        (if (isTorBox(file.key)) torBox?.deleteFile(file) else realDebrid?.deleteFile(file)) ?: false

    /** Deletes all of [files], each from its own account. How many could not be deleted. */
    suspend fun deleteFiles(files: List<RealDebrid.RdFile>): Int {
        val (theirs, ours) = files.partition { isTorBox(it.key) }
        var failed = 0
        if (ours.isNotEmpty()) failed += realDebrid?.deleteFiles(ours) ?: ours.size
        if (theirs.isNotEmpty()) failed += torBox?.deleteFiles(theirs) ?: theirs.size
        return failed
    }

    suspend fun playable(file: RealDebrid.RdFile): Playable? =
        if (isTorBox(file.key)) torBox?.playable(file) else realDebrid?.playable(file)

    suspend fun subtitleFile(subtitle: RealDebrid.RdSubtitle): File? =
        if (isTorBox(subtitle.link)) torBox?.subtitleFile(subtitle) else realDebrid?.subtitleFile(subtitle)
}
