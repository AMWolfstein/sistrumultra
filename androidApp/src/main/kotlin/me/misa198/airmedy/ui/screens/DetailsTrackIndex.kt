package me.misa198.airmedy.ui.screens

import me.misa198.airmedy.sync.LibraryTrack

/**
 * Groups tracks under every key they belong to (a track can have several artists, genres or
 * composers). The details ViewModels build these indexes once per library emission on
 * Dispatchers.Default, so the per-page `*DetailsUiStateFor` lookups that run in composition
 * don't parse every track's metadata JSON on the main thread.
 */
internal fun <K> List<LibraryTrack>.indexByKeys(keys: (LibraryTrack) -> Iterable<K>): Map<K, List<LibraryTrack>> {
    val index = HashMap<K, MutableList<LibraryTrack>>()
    forEach { track -> keys(track).forEach { key -> index.getOrPut(key) { mutableListOf() }.add(track) } }
    return index
}
