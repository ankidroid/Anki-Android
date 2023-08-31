/****************************************************************************************
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                              *
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
package com.ichi2.utils

/**
 * Represents a computed value or a failure. Similar to c++ absl::StatusOr<U>, Rust Result<U>
 * @param ComputedType The value of a successful computation
 */
// "Result" is used as a type parameter in AsyncTask, where this class is used a lot. Hence,
// `Result` would not be an acceptable type name.
// We use "Any" to disallow nullable types. Use an [Optional] instead.
class Computation<out ComputedType : Any> {

    private val mValue: ComputedType?
    fun succeeded(): Boolean = mValue != null
    fun <TNewOut : Any> map(f: (ComputedType) -> TNewOut): Computation<TNewOut> {
        if (!succeeded()) {
            return err()
        }
        return ok(f(value))
    }

    /**
     * The computed value in case of success. [IllegalStateException] in case of failure
     */
    val value: ComputedType
        get() {
            check(succeeded()) { "Computation returned error" }
            return mValue!!
        }

    private constructor() {
        mValue = null
    }

    private constructor(value: ComputedType) {
        mValue = value
    }

    companion object {
        val ERR: Computation<*> = Computation<Any>()
        val OK: Computation<*> = Computation(Any())

        /** A strongly typed error return value */
        fun <ComputedType : Any> err(): Computation<ComputedType> {
            return Computation()
        }

        fun <ComputedType : Any> ok(value: ComputedType): Computation<ComputedType> {
            return Computation(value)
        }
    }
}
