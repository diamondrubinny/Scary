// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.player

/**
 * The episodes among the viewer's own files, by their names: which file comes after the one that just ended, and the
 * order a series is listed in. A season often comes as one torrent whose files are in no useful order (E10 before E2,
 * or the order they were added in), or as one torrent an episode; either way the next episode is the one the names
 * say, not the next one in the list.
 *
 * Only names and keys are read; nothing here asks the network for anything. A file's key says where it lives:
 * "t:<torrent>:<n>" and "d:<id>" for Real-Debrid, "torbox:<torrent>:<file>" for TorBox.
 */
object Episodes {
    /** A file of the account: its key and its name. */
    data class Item(val key: String, val name: String)

    /** [season] is 1 when a name gives an episode alone ("Episode 4"). */
    data class Number(val season: Int, val episode: Int) : Comparable<Number> {
        override fun compareTo(other: Number): Int = compareValuesBy(this, other, { it.season }, { it.episode })
    }

    private const val MAX_NAME = 300

    // S01E02, s1.e2, S01 E02; then 1x02; then "Episode 2", "Ep.2", "E02" on its own (season 1).
    private val FULL = Regex("(?i)(?<![a-z0-9])s(\\d{1,2})[ ._-]?e(\\d{1,3})(?![0-9])")
    private val CROSS = Regex("(?i)(?<![a-z0-9])(\\d{1,2})x(\\d{2,3})(?![0-9a-z])")
    private val ALONE = Regex("(?i)(?<![a-z0-9])(?:episode|ep|e)[ ._-]?(\\d{1,3})(?![0-9a-z])")

    private class Found(val number: Number, val start: Int)

    private fun find(name: String): Found? {
        val text = name.take(MAX_NAME)
        FULL.find(text)?.let { m -> return found(m.groupValues[1], m.groupValues[2], m.range.first) }
        CROSS.find(text)?.let { m -> return found(m.groupValues[1], m.groupValues[2], m.range.first) }
        ALONE.find(text)?.let { m -> return found("1", m.groupValues[1], m.range.first) }
        return null
    }

    private fun found(season: String, episode: String, start: Int): Found? {
        val s = season.toIntOrNull() ?: return null
        val e = episode.toIntOrNull() ?: return null
        return Found(Number(s, e), start)
    }

    /** The season and episode [name] gives, or null when it names none (a film, a sample, a file of extras). */
    fun numberOf(name: String): Number? = find(name)?.number

    /**
     * What a series' files have in common: the words before the episode's number, without case or punctuation
     * ("The.Haunting.of.Hill.House.S01E02.1080p" and "the haunting of hill house s01e03" are one series). Null for a
     * name with no episode, or with nothing before it.
     */
    fun seriesOf(name: String): String? {
        val found = find(name) ?: return null
        return name.take(found.start).lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().takeIf { it.isNotEmpty() }
    }

    /** The torrent a key belongs to ("t:ABC", "torbox:42"); a download stands alone, so its own key. */
    fun torrentOf(key: String): String {
        val parts = key.split(':')
        return if (parts.size == 3 && (parts[0] == "t" || parts[0] == "torbox")) "${parts[0]}:${parts[1]}" else key
    }

    private fun sameSeries(a: Item, b: Item): Boolean {
        if (torrentOf(a.key) == torrentOf(b.key)) return true
        val series = seriesOf(a.name) ?: return false
        return series == seriesOf(b.name)
    }

    /**
     * The file to play after [current]: the next episode by number, among the files of the same torrent or of the
     * same series in another torrent. When the next episode is there twice (two qualities), the one in the same
     * torrent is taken. A file whose name gives no episode is followed by the next file of its own torrent, by name
     * (a film in parts). Null when nothing follows: the last episode, a film, a download on its own.
     */
    fun next(current: Item, files: Collection<Item>): Item? {
        val others = files.filter { it.key != current.key }
        val torrent = torrentOf(current.key)
        val number = numberOf(current.name)
        if (number == null) {
            val mine = others.filter { torrentOf(it.key) == torrent && numberOf(it.name) == null }
            return mine.filter { NATURAL.compare(it.name, current.name) > 0 }.minWithOrNull(compareBy(NATURAL) { it.name })
        }
        return others
            .mapNotNull { other -> numberOf(other.name)?.takeIf { it > number && sameSeries(current, other) }?.let { it to other } }
            .minWithOrNull(compareBy<Pair<Number, Item>> { it.first }.thenBy { torrentOf(it.second.key) != torrent }.thenBy(NATURAL) { it.second.name })
            ?.second
    }

    /**
     * [files] for a list: a series' episodes together and in order, wherever its first file stood, and everything
     * else where it was. Files of one torrent that give no episode keep their torrent together, by name.
     */
    fun <T> ordered(files: List<T>, item: (T) -> Item): List<T> {
        val groups = LinkedHashMap<String, MutableList<T>>()
        // Torrents of one series are one group: a season added an episode at a time lists like a season in one piece.
        val seriesGroup = HashMap<String, String>()
        for (file in files) {
            val it = item(file)
            val torrent = torrentOf(it.key)
            val series = seriesOf(it.name)
            val group = when {
                series != null -> seriesGroup.getOrPut("series:$series") { seriesGroup["torrent:$torrent"] ?: "series:$series" }
                else -> seriesGroup["torrent:$torrent"] ?: "torrent:$torrent"
            }
            seriesGroup.putIfAbsent("torrent:$torrent", group)
            groups.getOrPut(group) { ArrayList() } += file
        }
        return groups.values.flatMap { group ->
            if (group.size < 2) {
                group
            } else {
                group.sortedWith(
                    // Episodes first, in order; then what gives none (extras), by name.
                    compareBy<T> { numberOf(item(it).name) == null }
                        .thenBy { numberOf(item(it).name) ?: Number(0, 0) }
                        .thenBy(NATURAL) { item(it).name },
                )
            }
        }
    }

    /** Names as a person orders them: "Part 2" before "Part 10". */
    val NATURAL: Comparator<String> = Comparator { a, b ->
        val x = CHUNK.findAll(a.lowercase()).map { it.value }.toList()
        val y = CHUNK.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]
            val q = y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) {
                val trimmedP = p.trimStart('0')
                val trimmedQ = q.trimStart('0')
                if (trimmedP.length != trimmedQ.length) trimmedP.length.compareTo(trimmedQ.length) else trimmedP.compareTo(trimmedQ)
            } else {
                p.compareTo(q)
            }
            if (c != 0) return@Comparator c
        }
        x.size.compareTo(y.size)
    }

    private val CHUNK = Regex("\\d+|\\D+")
}
