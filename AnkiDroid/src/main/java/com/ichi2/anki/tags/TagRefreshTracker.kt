// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

/**
 * Tracks requests to reload the tag tree, so a collection change reported while a tag
 * operation is running is reloaded when that operation finishes, rather than being lost.
 *
 * Not thread-safe: [ManageTagsViewModel] uses it on the main thread.
 */
internal class TagRefreshTracker {
    /** Whether the tags changed since the last [load][trackLoad] started. */
    var isRefreshRequested = false
        private set

    fun requestRefresh() {
        isRefreshRequested = true
    }

    /**
     * Runs [load], which satisfies the requests made before it started.
     * Requests made while [load] runs remain pending.
     */
    suspend fun <T> trackLoad(load: suspend () -> T): T {
        isRefreshRequested = false
        return load()
    }
}
