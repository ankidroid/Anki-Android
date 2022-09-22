/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.libanki.utils

import android.annotation.SuppressLint
import androidx.annotation.VisibleForTesting
import java.util.*

/** Singleton providing an instance of [Time].
 * Used for tests to mock the time provider
 * without forcing the direct dependency on a [Time] instance
 *
 * For later: move this into a DI container
 */
@SuppressLint("DirectSystemTimeInstantiation")
object TimeManager {
    /**
     * Executes the provided functionality, returning [timeOverride] while in the code block
     */
    @VisibleForTesting
    fun <T : Time> withMockInstance(timeOverride: T, f: ((T) -> Unit)) {
        try {
            mockInstances.push(timeOverride)
            f(timeOverride)
        } finally {
            mockInstances.remove(timeOverride)
        }
    }

    @VisibleForTesting
    fun reset() {
        mockInstances.clear()
    }

    @VisibleForTesting
    fun resetWith(mockTime: Time) {
        reset()
        mockInstances.push(mockTime)
    }
    private var mockInstances: Stack<Time> = Stack()

    var time: Time = SystemTime()
        get() = if (mockInstances.any()) mockInstances.peek() else field
        private set
}
