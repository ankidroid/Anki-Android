/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
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
package com.ichi2.anki.dialogs

import java.util.ArrayList
import java.util.HashMap

object ContextMenuHelper {
    @JvmStatic
    fun getValuesFromKeys(map: HashMap<Int, String>, keys: IntArray): Array<String?> {
        val values = arrayOfNulls<String>(keys.size)
        for (i in keys.indices) {
            values[i] = map[keys[i]]
        }
        return values
    }

    @JvmStatic
    fun integerListToArray(itemIds: ArrayList<Int>): IntArray {
        val intItemIds = IntArray(itemIds.size)
        for (i in itemIds.indices) {
            intItemIds[i] = itemIds[i]
        }
        return intItemIds
    }
}
