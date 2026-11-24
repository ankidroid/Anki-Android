// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.annotation.WorkerThread
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.EpochSeconds
import com.ichi2.anki.libanki.sched.Scheduler

/**
 * Tracks changes to the study day cutoff ([Scheduler.dayCutoff]) of the open collection.
 *
 * Accessed only on the collection queue.
 */
object DayCutoffTracker {
    private var lastCutoff: EpochSeconds? = null

    /**
     * Checks for a new study day, which also lets the scheduler unbury cards.
     * Returns a result on the first check and whenever the cutoff changes, so the caller can arm its alarm.
     *
     * Call inside [CollectionManager.withOpenColOrNull].
     */
    @WorkerThread
    fun check(col: Collection): DayCutoffCheck? {
        val current = col.sched.dayCutoff
        val previous = lastCutoff
        if (previous == current) return null

        lastCutoff = current
        return DayCutoffCheck(previous, current)
    }
}

data class DayCutoffCheck(
    val previous: EpochSeconds?,
    val current: EpochSeconds,
) {
    val hasRolledOver get() = previous != null && previous != current
}
