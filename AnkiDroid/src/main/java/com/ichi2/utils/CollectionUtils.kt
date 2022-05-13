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
    /** Throws IndexOutOfBoundsException on empty list */
    @JvmStatic
    @KotlinCleanup("Replace with List<T>.last() Kotlin extension")
    fun <T> getLastListElement(l: List<T>): T {
        return l[l.size - 1]
    }

    /**
     * @param c A collection in which to add elements of it
     * @param it An iterator returning things to add to C
     * @param <T> Type of elements to copy from iterator to collection
     */
    @JvmStatic
    @KotlinCleanup("replace with Kotlin extension: MutableCollections.addAll")
    fun <T> addAll(c: MutableCollection<T>, it: Iterable<T>) {
        for (elt in it) {
            c.add(elt)
        }
    }

    /**
     * Given an array: `[A, B, C]`, returns `[[A, B], [A, C], [B, C]]`
     * @return Each pair `[A, B]` for `A` occurring before `B` in the input list.
     */
    fun <T> List<T>.combinations(): Sequence<Pair<T, T>> = sequence {
        this@combinations.let { list ->
            for (i in 0 until list.size - 1) {
                for (j in i + 1 until list.size) {
                    yield(Pair(list[i], list[j]))
                }
            }
        }
    }
}
