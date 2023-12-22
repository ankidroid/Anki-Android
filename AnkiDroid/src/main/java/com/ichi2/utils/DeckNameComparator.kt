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

import com.ichi2.libanki.Decks
import java.util.Comparator

class DeckNameComparator : Comparator<String> {
    override fun compare(
        lhs: String,
        rhs: String,
    ): Int {
        val o1 = Decks.path(lhs)
        val o2 = Decks.path(rhs)
        for (i in 0 until o1.size.coerceAtMost(o2.size)) {
            val result = o1[i].compareTo(o2[i], ignoreCase = true)
            if (result != 0) {
                return result
            }
        }
        return o1.size.compareTo(o2.size)
    }

    companion object {
        val INSTANCE = DeckNameComparator()
    }
}
