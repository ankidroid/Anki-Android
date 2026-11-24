// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.annotation.WorkerThread
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.EpochSeconds
import com.ichi2.anki.libanki.sched.Scheduler
import net.ankiweb.rsdroid.BackendException
import timber.log.Timber

/**
 * Tracks changes to the study day cutoff ([Scheduler.dayCutoff]) of the open collection.
 *
 * Accessed only on the collection queue. [CollectionManager] records the cutoff when it opens
 * a collection, so a cutoff crossed before the first [check] is still reported.
 */
object DayCutoffTracker {
    private var lastCutoff: EpochSeconds? = null
    private var checked = false

    /** Establishes the baseline for a newly opened collection. */
    @WorkerThread
    internal fun onCollectionOpened(col: Collection) {
        checked = false
        lastCutoff =
            try {
                col.sched.dayCutoff
            } catch (e: BackendException) {
                Timber.w(e, "Could not establish the study day cutoff at collection open")
                null
            }
    }

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
        if (checked && previous == current) return null

        lastCutoff = current
        checked = true
        return DayCutoffCheck(previous, current)
    }
}

data class DayCutoffCheck(
    val previous: EpochSeconds?,
    val current: EpochSeconds,
) {
    val hasRolledOver get() = previous != null && previous != current
}
