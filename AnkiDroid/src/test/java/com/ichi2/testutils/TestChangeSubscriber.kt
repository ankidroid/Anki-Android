/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.testutils

import anki.collection.OpChanges
import com.ichi2.libanki.ChangeManager
import com.ichi2.libanki.undoableOp
import timber.log.Timber
import kotlin.test.fail

/**
 * Ensures no calls to [ChangeManager.notifySubscribers] via [undoableOp]
 */
suspend fun ensureNoOpsExecuted(block: suspend () -> Unit) {
    val subscription = ChangeCounter()
    ChangeManager.subscribe(subscription)
    block()
    if (!subscription.hasChanges) {
        Timber.d("ensureNoOpsExecuted: success")
        return
    }

    fail("ChangeManager should not be called; ${ChangeManager.subscriberCount} subscribers")
}

/**
 * Ensures no calls to [ChangeManager.notifySubscribers] via [undoableOp]
 */
suspend fun ensureOpsExecuted(count: Int, block: suspend () -> Unit) {
    val subscription = ChangeCounter()

    Timber.v("Listening for ChangeManager ops")
    ChangeManager.subscribe(subscription)
    block()
    if (subscription.changeCount == count) {
        Timber.d("ensureOpsExecuted: success")
        return
    }

    fail("ChangeManager: expected $count calls; ${ChangeManager.subscriberCount} subscribers")
}

// used to ensure a strong reference to the subscription is held
private class ChangeCounter : ChangeManager.Subscriber {
    private var changes = 0
    val changeCount get() = changes
    val hasChanges get() = changes > 0
    override fun opExecuted(changes: OpChanges, handler: Any?) {
        Timber.d("ChangeManager op detected")
        this.changes++
    }
}
