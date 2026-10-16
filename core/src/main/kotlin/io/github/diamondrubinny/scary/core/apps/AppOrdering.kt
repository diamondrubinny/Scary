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
