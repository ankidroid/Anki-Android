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

import com.ichi2.libanki.ChangeManager
import com.ichi2.libanki.undoableOp
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import timber.log.Timber

/**
 * Ensures no calls to [ChangeManager.notifySubscribers] via [undoableOp]
 */
suspend fun ensureNoOpsExecuted(block: suspend () -> Unit) {
    var changes = false
    ChangeManager.subscribe { _, _ ->
        Timber.d("ChangeManager op detected")
        changes = true
    }
    block()
    // we should be fine to not cleanup here
    assertThat("ChangeManager should not be called", !changes)
}

/**
 * Ensures no calls to [ChangeManager.notifySubscribers] via [undoableOp]
 */
suspend fun ensureOpsExecuted(count: Int, block: suspend () -> Unit) {
    var changes = 0
    ChangeManager.subscribe { _, _ ->
        Timber.d("ChangeManager op detected")
        changes++
    }
    block()
    // we should be fine to not cleanup here, as the subscriber goes out of scope
    assertThat("ChangeManager: expected $count calls", changes, equalTo(count))
}
