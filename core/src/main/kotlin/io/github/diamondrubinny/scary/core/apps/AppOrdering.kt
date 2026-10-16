package io.github.diamondrubinny.scary.core.apps

object AppOrdering {
    /**
     * Pinned packages first (in pinned order, only if installed), then everything else
     * sorted by label, case-insensitively. Hidden packages are dropped.
     */
    fun <T> order(
        apps: List<T>,
        packageOf: (T) -> String,
        labelOf: (T) -> String,
        pinned: List<String>,
        hidden: Set<String> = emptySet(),
    ): List<T> {
        val visible = apps.filterNot { packageOf(it) in hidden }.distinctBy(packageOf)
        val byPackage = visible.associateBy(packageOf)
        val pinnedApps = pinned.mapNotNull(byPackage::get)
        val pinnedSet = pinnedApps.map(packageOf).toSet()
        val rest = visible
            .filterNot { packageOf(it) in pinnedSet }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, labelOf))
        return pinnedApps + rest
    }
}

/** Edits to the user's ordered list of pinned packages. Pure, so the UI and tests share one behavior. */
object PinnedList {
    fun toggle(pinned: List<String>, packageName: String): List<String> =
        if (packageName in pinned) pinned - packageName else pinned + packageName

    /** Moves [packageName] by [delta] positions, clamped to the list bounds. Unknown packages leave the list as is. */
    fun move(pinned: List<String>, packageName: String, delta: Int): List<String> {
        val from = pinned.indexOf(packageName)
        if (from < 0) return pinned
        val to = (from + delta).coerceIn(0, pinned.lastIndex)
        if (to == from) return pinned
        return pinned.toMutableList().apply { add(to, removeAt(from)) }
    }
}
