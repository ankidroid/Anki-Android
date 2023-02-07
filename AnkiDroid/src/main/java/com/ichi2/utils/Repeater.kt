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

package com.ichi2.utils

import com.ichi2.utils.HandlerUtils.newHandler
import timber.log.Timber

/**
 * Schedules repeating events
 * @param delayMs the interval to wait after the execution of [runnable] before the next call is made
 * @param runnable Called once every [delayMs] until [terminate] is called
 */
class Repeater private constructor(val delayMs: Long, val runnable: () -> Unit) {
    private var terminated = false

    fun terminate() {
        Timber.v("Terminated Repeater $this")
        terminated = true
    }

    fun repeatDelayed() {
        if (terminated) { return }
        val handler = newHandler()
        handler.postDelayed(
            object : Runnable {
                override fun run() {
                    Timber.v("Executing $this")
                    if (!terminated) {
                        runnable()
                        handler.postDelayed(this, delayMs)
                    }
                }
            },
            delayMs
        )
    }

    companion object {
        fun createAndStart(delayMs: Long, runnable: () -> Unit) =
            Repeater(delayMs, runnable).also { it.repeatDelayed() }
    }
}
