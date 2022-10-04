/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

import com.ichi2.utils.FunctionalInterfaces.Filter
import org.json.JSONException

/** TODO: Move this to standard library in API 24  */
@KotlinCleanup("try to improve the generic code with in/out declarations for generic type parameters")
class FunctionalInterfaces {

    fun interface FunctionThrowable<TIn, TOut> {
        @Throws(JSONException::class)
        fun apply(item: TIn): TOut
    }

    fun interface Filter<TIn> {
        fun shouldInclude(item: TIn): Boolean
    }

    object Filters {
        fun <T> allowAll(): Filter<T> {
            return Filter { true }
        }
    }
}
