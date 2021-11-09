/*
 Copyright (c) 2021 Tarek Mohamed <tarekkma@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils

import java.lang.ref.WeakReference

/**
 * A utility class that can put a limit on how much a function gets called.
 * It can also be used to only fire a function only once for a given reference, respecting the max execution number
 */
class MaxExecFunction(private val maxNumberOfExecutions: Int, private val func: Runnable) {
    private var mCurrentNumberOfExecutions = 0
    private var mLastExecutionReference: WeakReference<Any>? = null

    /**
     * Execute the function, if the max number of max execution hasn't been reached
     */
    fun exec() {
        if (mCurrentNumberOfExecutions >= maxNumberOfExecutions) {
            return
        }
        mCurrentNumberOfExecutions++
        func.run()
    }

    /**
     * Execute the faction, if it didn't get called for the same reference before and the number execution hasn't been reached
     *
     * @param ref the reference
     */
    @KotlinCleanup("Thread Safety")
    fun execOnceForReference(ref: Any) {
        if (mLastExecutionReference != null && mLastExecutionReference!!.get() === ref) {
            return
        }
        mLastExecutionReference = WeakReference(ref)
        exec()
    }
}
