/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.utils

inline fun <T, reified R> Array<T>.mapToArray(transform: (T) -> R): Array<R> {
    @Suppress("UNCHECKED_CAST")
    return arrayOfNulls<R>(size).also { out ->
        this.forEachIndexed { index, element -> out[index] = transform(element) }
    } as Array<R>
}
