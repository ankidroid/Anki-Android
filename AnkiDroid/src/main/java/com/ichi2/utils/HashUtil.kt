/***************************************************************************************
 * Copyright (c) 2021 Arthur Milchior <arthur@milchior.fr>                              *
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

import java.util.HashMap
import java.util.HashSet

object HashUtil {
    /**
     * @param size Number of elements expected in the hash structure
     * @return Initial capacity for the hash structure. Copied from HashMap code
     */
    private fun capacity(size: Int): Int {
        return Math.max((size / .75f).toInt() + 1, 16)
    }

    @JvmStatic
    fun <T> HashSetInit(size: Int): HashSet<T> {
        return HashSet(capacity(size))
    }

    @JvmStatic
    fun <T, U> HashMapInit(size: Int): HashMap<T, U> {
        return HashMap(capacity(size))
    }
}
