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
    fun <T> getLastListElement(l: List<T>): T {
        return l[l.size - 1]
    }

    /**
     * @param c A collection in which to add elements of it
     * @param it An iterator returning things to add to C
     * @param <T> Type of elements to copy from iterator to collection
     </T> */
    @JvmStatic
    fun <T> addAll(c: MutableCollection<T>, it: Iterable<T>) {
        for (elt in it) {
            c.add(elt)
        }
    }
}
