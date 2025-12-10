/*
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
 *
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.common.json

interface NamedObject {
    val name: String
}

class NamedJSONComparator : Comparator<NamedObject> {
    override fun compare(
        lhs: NamedObject,
        rhs: NamedObject,
    ): Int {
        val o1 = lhs.name
        val o2 = rhs.name
        return o1.compareTo(o2, ignoreCase = true)
    }

    companion object {
        val INSTANCE = NamedJSONComparator()
    }
}
