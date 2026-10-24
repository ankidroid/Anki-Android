// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import com.ichi2.anki.CollectionManager
import kotlinx.coroutines.CoroutineDispatcher

suspend fun <T> withCollectionAccessOn(
    dispatcher: CoroutineDispatcher,
    block: suspend () -> T,
): T {
    val previousQueue = CollectionManager.setTestDispatcher(dispatcher, useReentrantLock = false)
    try {
        return block()
    } finally {
        CollectionManager.setTestDispatcher(previousQueue)
    }
}
