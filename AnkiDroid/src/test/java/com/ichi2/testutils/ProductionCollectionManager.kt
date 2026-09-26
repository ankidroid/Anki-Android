// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import com.ichi2.anki.CollectionManager
import com.ichi2.anki.libanki.testutils.TestCollectionManager
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Adapts [CollectionManager] to [TestCollectionManager]
 */
object ProductionCollectionManager : TestCollectionManager {
    override fun getColUnsafe() = CollectionManager.getColUnsafe()

    override suspend fun discardBackend() {
        CollectionManager.discardBackend()
    }

    /** @see CollectionManager.setTestDispatcher */
    fun setTestDispatcher(dispatcher: CoroutineDispatcher) {
        CollectionManager.setTestDispatcher(dispatcher)
    }
}
