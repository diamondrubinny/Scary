// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.util.Log
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Subtitle files kept on the TV as UTF-8 text while they are in use (the app's cache, never backed up, never shared):
 * the torrent's files and those found online. The newest [MAX_FILES] are kept; the rest are deleted as new ones come.
 * A file is named by a hash of its source, so a link or an address never appears in a file name. Files here are only
 * read as text by the player, never run.
 */
class SubtitleCache(private val dir: File) {
    /** The file for [source] ("a Real-Debrid link", "an online subtitle's address") and its [extension]. */
    fun fileFor(source: String, extension: String): File = File(dir, sha256(source) + "." + extension.filter { it.isLetterOrDigit() }.take(5).ifEmpty { "srt" })

    /** The kept file, made newest again, or null when there is none. */
    fun kept(file: File): File? = file.takeIf { it.isFile && it.length() > 0 }?.also { it.setLastModified(System.currentTimeMillis()) }

    /** Writes [text] to [file] (all or nothing), deleting the oldest files beyond the limit. Null when it cannot. */
    fun keep(file: File, text: String): File? = try {
        dir.mkdirs()
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(MAX_FILES - 1)?.forEach { it.delete() }
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (tmp.renameTo(file)) {
            file
        } else {
            tmp.delete()
            null
        }
    } catch (e: IOException) {
        Log.w(TAG, "Could not keep a subtitle file: ${e.javaClass.simpleName}")
        null
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)

    companion object {
        const val MAX_FILES = 30
        private const val TAG = "SubtitleCache"
    }
}
