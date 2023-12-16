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

import androidx.annotation.CheckResult
import com.ichi2.libanki.utils.TimeManager
import timber.log.Timber

/**
 * Usage:
 *
 * ```kotlin
 * val result = measureTime("operation") { operation() }
 * ```
 * -> `D/TimeUtilKt executed mHtmlGenerator in 23ms`
 */
fun <T> measureTime(functionName: String? = "", function: () -> T): T {
    val startTime = TimeManager.time.intTimeMS()
    val result = function()
    val endTime = TimeManager.time.intTimeMS()
    Timber.d(
        "executed %sin %dms",
        if (functionName.isNullOrEmpty()) "" else "$functionName ",
        endTime - startTime
    )
    return result
}

/**
 * Used to time an operation across two function calls
 *
 * ```kotlin
 * private val renderStopwatch: Stopwatch = Stopwatch.init("page render")
 *
 * fun start() {
 *     renderStopwatch.reset()
 * }
 *
 * fun stop() {
 *     renderStopwatch.logElapsed()
 * }
 * ```
 *
 * -> `D/Stopwatch executed page render in 67ms`
 */
class Stopwatch(private val executionName: String?) {
    private var startTime = TimeManager.time.intTimeMS()

    fun logElapsed() {
        val endTime = TimeManager.time.intTimeMS()
        Timber.d(
            "executed %sin %dms",
            if (executionName.isNullOrEmpty()) "" else "$executionName ",
            endTime - startTime
        )
    }

    fun reset() {
        startTime = TimeManager.time.intTimeMS()
    }
    companion object {

        /** initializes the stopwatch to ensure `stop()` before `start()` won't crash */
        @CheckResult
        fun init(executionName: String? = null) = Stopwatch(executionName)
    }
}
