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

package com.ichi2.anki.utils

import kotlinx.coroutines.Job
import timber.log.Timber

/**
 * Prevent multiple instances of a method being executed simultaneously
 */
object OnlyOnce {
    private val blockedFunctions = mutableSetOf<Any>()

    enum class Method {
        ANSWER_CARD,
        UNIT_TEST
    }

    /**
     * Prevents multiple instances of a method being executed simultaneously
     *
     * If the provided method is not running, run it
     * If the provided method is running, do nothing more
     */
    fun preventSimultaneousExecutions(name: Method, function: () -> Job) {
        if (!blockedFunctions.add(name)) {
            Timber.w("simultaneously executions of $name blocked")
            return
        }
        Timber.v("executing $name")
        function().invokeOnCompletion {
            Timber.v("completed $name")
            blockedFunctions.remove(name)
        }
    }
}
