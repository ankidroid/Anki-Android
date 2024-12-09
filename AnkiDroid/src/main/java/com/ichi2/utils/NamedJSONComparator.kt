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

import org.json.JSONObject

class NamedJSONComparator : Comparator<JSONObject> {
    override fun compare(
        lhs: JSONObject,
        rhs: JSONObject,
    ): Int {
        val o1 = lhs.getString("name")
        val o2 = rhs.getString("name")
        return o1.compareTo(o2, ignoreCase = true)
    }

    companion object {
        val INSTANCE = NamedJSONComparator()
    }
}
