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

package com.ichi2.exceptions

import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import java.lang.Exception
import java.lang.RuntimeException

/**
 * An exception which describes separate failures.
 * For example: if an operation should continue after some errors, but those errors would stop
 * the successful completion of an operation
 */
class AggregateException(message: String, val exceptions: List<Exception>) : RuntimeException(message) {

    override val message: String
        get() = "${exceptions.size} errors, the last being: '${exceptions.last().message}' [${super.message}]"

    override fun getLocalizedMessage(): String {
        return AnkiDroidApp.instance.resources.getQuantityString(
            R.plurals.aggregate_exception_user_facing_message,
            exceptions.size,
            super.message,
            exceptions.size,
            exceptions.last().localizedMessage
        )
    }

    override fun toString(): String {
        return "$message\n ${exceptions.joinToString(separator = "\n") { it.stackTraceToString() }}"
    }

    companion object {
        /**
         * Returns an [AggregateException] containing the provided exceptions
         * Or: returns the single exception is the provided list contains a single element
         *
         * @param message the message to include in the returned [AggregateException] (unused if only one exception provided)
         * @param exceptions the exceptions to include in the [AggregateException]
         * @throws IllegalStateException if [exceptions] is empty
         */
        fun raise(message: String, exceptions: List<Exception>): Exception {
            if (exceptions.isEmpty()) {
                throw IllegalStateException("no exceptions provided ")
            }

            if (exceptions.size == 1) {
                return exceptions.single()
            }

            return AggregateException(message, exceptions)
        }
    }
}
