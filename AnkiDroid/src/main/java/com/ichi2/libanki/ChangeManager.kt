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

package com.ichi2.libanki

import androidx.annotation.VisibleForTesting
import anki.collection.OpChanges
import anki.collection.OpChangesAfterUndo
import anki.collection.OpChangesOnly
import anki.collection.OpChangesWithCount
import anki.collection.OpChangesWithId
import anki.collection.opChanges
import anki.import_export.ImportResponse
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.utils.ext.ifNotZero
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

object ChangeManager {
    // do not make this a 'fun interface' - lambdas may immediately be GCed
    // due to the use of WeakReference
    interface Subscriber {
        /**
         * Called after a backend method invoked via col.op() or col.opWithProgress()
         * has modified the collection. Subscriber should inspect the changes, and update
         * the UI if necessary.
         */
        fun opExecuted(changes: OpChanges, handler: Any?)
    }

    // Maybe fixes #16217 - CopyOnWriteArrayList makes this object thread-safe
    private val subscribers = CopyOnWriteArrayList(mutableListOf<WeakReference<Subscriber>>())

    val subscriberCount get() = subscribers.size

    fun subscribe(subscriber: Subscriber) {
        subscribers.add(WeakReference(subscriber))
    }

    private fun notifySubscribers(changes: OpChanges, handler: Any?) {
        val expired = mutableListOf<WeakReference<Subscriber>>()
        for (subscriber in subscribers) {
            val ref = try {
                subscriber.get()
            } catch (e: Exception) {
                CrashReportService.sendExceptionReport(e, "notifySubscribers", "16217: invalid subscriber")
                null
            }
            if (ref == null) {
                expired.add(subscriber)
            } else {
                ref.opExecuted(changes, handler)
            }
        }
        expired.size.ifNotZero { size -> Timber.v("removing %d expired subscribers", size) }
        for (item in expired) {
            subscribers.remove(item)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun clearSubscribers() {
        subscribers.size.ifNotZero { size -> Timber.d("clearing %d subscribers", size) }
        subscribers.clear()
    }

    internal fun <T> notifySubscribers(changes: T, initiator: Any?) {
        val opChanges = when (changes) {
            is OpChanges -> changes
            is OpChangesWithCount -> changes.changes
            is OpChangesWithId -> changes.changes
            is OpChangesAfterUndo -> changes.changes
            is OpChangesOnly -> changes.changes
            is ImportResponse -> changes.changes
            else -> TODO("unhandled change type")
        }
        notifySubscribers(opChanges, initiator)
    }

    fun notifySubscribersAllValuesChanged(handler: Any? = null) {
        notifySubscribers(ALL, handler)
    }

    /**
     * An OpChanges that ensures that all data should be considered as potentially changed.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val ALL = opChanges {
        card = true
        note = true
        deck = true
        tag = true
        notetype = true
        config = true
        deckConfig = true
        mtime = true
        browserTable = true
        browserSidebar = true
        noteText = true
        studyQueues = true
    }
}

/** Wrap a routine that returns OpChanges* or similar undo info with this
 * to notify change subscribers of the changes. */
suspend fun <T> undoableOp(handler: Any? = null, block: Collection.() -> T): T {
    return withCol {
        block()
    }.also {
        withContext(Dispatchers.Main) {
            ChangeManager.notifySubscribers(it, handler)
        }
    }
}
