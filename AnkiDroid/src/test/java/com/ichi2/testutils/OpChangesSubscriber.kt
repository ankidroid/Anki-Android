/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

class OpChangesSubscriber private constructor() : ChangeManager.Subscriber {
    val size: Int get() = changes.size
    val changes = mutableListOf<OpChanges>()
    override fun opExecuted(changes: OpChanges, handler: Any?) {
        this.changes.add(changes)
    }
    companion object {
        /**
         * Creates an instance of [OpChangesSubscriber] and subscribes to the feed
         */
        fun createAndSubscribe(): OpChangesSubscriber = OpChangesSubscriber().apply {
            // we need this class as the subscriber is stored as a weak reference
            ChangeManager.subscribe(this)
        }
    }
}
