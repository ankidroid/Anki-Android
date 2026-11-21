// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

/**
 * Tracks requests to reload the tag tree, so a collection change reported while a tag
 * operation is running is reloaded when that operation finishes, rather than being lost.
 *
 * Not thread-safe: [ManageTagsViewModel] uses it on the main thread.
 */
internal class TagRefreshTracker {
    /** Incremented for each refresh request. Compare two values to detect requests made in between. */
    var revision = 0L
        private set

    private var revisionAtLoadStart = 0L
    private var lastLoadSucceeded = false

    /** Whether the tags changed since the last [load][trackLoad] started. */
    val isRefreshRequested: Boolean
        get() = revision != revisionAtLoadStart

    /** Whether the loaded tags may be stale: a refresh was requested, or the last load failed. */
    val needsReload: Boolean
        get() = isRefreshRequested || !lastLoadSucceeded

    fun requestRefresh() {
        revision++
    }

    /**
     * Runs [load], which satisfies the requests made before it started.
     * Requests made while [load] runs remain pending. If [load] fails, [needsReload] remains set.
     */
    suspend fun <T> trackLoad(load: suspend () -> T): T {
        revisionAtLoadStart = revision
        lastLoadSucceeded = false
        return load().also { lastLoadSucceeded = true }
    }
}
