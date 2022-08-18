/***************************************************************************************
 * Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>                       *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

/**
 * With the Rust backend, operations that modify the collection return a description of changes (OpChanges).
 * The UI can subscribe to these changes, so it can update itself when actions have been performed
 * (eg, the deck list can check if studyQueues has been updated, and if so, it will redraw the list).
 *
 * The optional handler argument can be used so that the initiator of an action can tell when a
 * OpChanges action was caused by itself. This can be useful when the default change behaviour
 * should be ignored, in favour of specific handling (eg the UI wishes to directly update the
 * displayed flag, without redrawing the entire review screen).
 */

// BackendFactory.defaultLegacySchema must be false to use this code.

package com.ichi2.libanki

import androidx.annotation.VisibleForTesting
import anki.collection.OpChanges
import anki.collection.OpChangesAfterUndo
import anki.collection.OpChangesWithCount
import anki.collection.OpChangesWithId
import anki.import_export.ImportResponse
import com.ichi2.anki.CollectionManager.withCol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

object ChangeManager {
    interface Subscriber {
        /**
         * Called after a backend method invoked via col.op() or col.opWithProgress()
         * has modified the collection. Subscriber should inspect the changes, and update
         * the UI if necessary.
         */
        fun opExecuted(changes: OpChanges, handler: Any?)
    }

    private val subscribers = mutableListOf<WeakReference<Subscriber>>()

    fun subscribe(subscriber: Subscriber) {
        subscribers.add(WeakReference(subscriber))
    }

    private fun notifySubscribers(changes: OpChanges, handler: Any?) {
        val expired = mutableListOf<WeakReference<Subscriber>>()
        for (subscriber in subscribers) {
            val ref = subscriber.get()
            if (ref == null) {
                expired.add(subscriber)
            } else {
                ref.opExecuted(changes, handler)
            }
        }
        for (item in expired) {
            subscribers.remove(item)
        }
    }

    @VisibleForTesting
    fun clearSubscribers() {
        subscribers.clear()
    }

    internal fun <T> notifySubscribers(changes: T, initiator: Any?) {
        val opChanges = when (changes) {
            is OpChanges -> changes
            is OpChangesWithCount -> changes.changes
            is OpChangesWithId -> changes.changes
            is OpChangesAfterUndo -> changes.changes
            is ImportResponse -> changes.changes
            else -> TODO("unhandled change type")
        }
        notifySubscribers(opChanges, initiator)
    }
}

/** Wrap a routine that returns OpChanges* or similar undo info with this
 * to notify change subscribers of the changes. */
suspend fun <T> undoableOp(handler: Any? = null, block: CollectionV16.() -> T): T {
    return withCol {
        val result = newBackend.block()
        // any backend operation clears legacy undo and resets study queues if it
        // succeeds
        clearUndo()
        reset()
        result
    }.also {
        withContext(Dispatchers.Main) {
            ChangeManager.notifySubscribers(it, handler)
        }
    }
}
