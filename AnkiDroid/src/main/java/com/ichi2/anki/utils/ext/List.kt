/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.utils.ext

/**
 * Returns the index of the first occurrence of the specified element in the list, or `null` if
 * the specified element is not contained in the list.
 *
 * For lists containing more than [Int.MAX_VALUE] elements, a result of this function is unspecified.
 */
fun <T> List<T>.indexOfOrNull(value: T): Int? {
    val index = indexOf(value)
    return if (index >= 0) index else null
}
