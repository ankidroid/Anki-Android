/*
 *  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
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

object CollectionUtils {
    /**
     * Given an array: `[A, B, C]`, returns `[[A, B], [A, C], [B, C]]`
     * @return Each pair `[A, B]` for `A` occurring before `B` in the input list.
     */
    fun <T> List<T>.combinations(): Sequence<Pair<T, T>> =
        sequence {
            this@combinations.let { list ->
                for (i in 0 until list.size - 1) {
                    for (j in i + 1 until list.size) {
                        yield(Pair(list[i], list[j]))
                    }
                }
            }
        }

    /**
     * Return the average of the elements in the iterable,
     * or null if the iterable is empty.
     */
    fun <T> Iterable<T>.average(f: (T) -> Int): Double? {
        return this.map(f).average().let { if (it.isNaN()) null else it }
    }
}
