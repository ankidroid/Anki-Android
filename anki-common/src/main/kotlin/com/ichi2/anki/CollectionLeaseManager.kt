// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.common.time.TimeManager
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.ankiweb.rsdroid.Backend
import timber.log.Timber

/**
 * Owns [collection lease][CollectionLease] acquisition, publication and cleanup.
 *
 * @see withLease
 */
internal class CollectionLeaseManager {
    // Serialize ownership before entering the collection queue, so a waiter cannot replace its owner.
    private val mutex = Mutex()

    val flowOfLease: StateFlow<CollectionLease?>
        field = MutableStateFlow<CollectionLease?>(null)

    val lease: CollectionLease? get() = flowOfLease.value

    /**
     * Publishes one lease at a time, cancelling queued work through its own coroutine scope.
     *
     * If a lease is already active, suspend until the lease is released.
     */
    suspend fun <T> withLease(
        operation: CollectionOperation,
        onCancel: (Backend) -> Unit = {},
        block: suspend (CollectionLease) -> T,
    ): T =
        mutex.withLock {
            coroutineScope {
                val lease =
                    CollectionLease(
                        operation = operation,
                        startedAt = TimeManager.time.intTimeMS(),
                        cancelQueued = { cancel() },
                        cancelRunning = onCancel,
                    )
                flowOfLease.value = lease
                Timber.d("collection lease acquired: %s", operation)
                try {
                    block(lease)
                } finally {
                    lease.finish()
                    flowOfLease.value = null
                    Timber.d("collection lease released: %s", lease)
                }
            }
        }

    /**
     * Skips disposable work when a lease is held.
     *
     * Note: due to interleaving, a lease acquired after the check may still cause this call
     * to wait for the collection queue.
     */
    suspend fun <T> tryWithoutLease(block: suspend () -> T): T? {
        lease?.let {
            Timber.d("tryWithCol: skipped, %s", it)
            return null
        }
        return block()
    }
}
