/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
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

package com.ichi2.anki

import android.content.Context
import java.util.BitSet

object OnboardingUtils {

    /**
     * Check if the tutorial for a particular feature should be displayed or not.
     * If the bit at an index is set, then the corresponding tutorial has been seen.
     */
    fun <T : Enum<T>> checkIfNotAlreadyVisited(enum: Enum<T>, context: Context): Boolean {
        return (AnkiDroidApp.getSharedPrefs(context).getLong(enum.name, 0) and (1L shl enum.ordinal)) == 0L
    }

    /**
     * Set the bit at the index defined for a feature once the tutorial for that feature is seen by the user.
     */
    fun <T : Enum<T>> setAsVisited(enum: Enum<T>, context: Context) {
        val currentValue = AnkiDroidApp.getSharedPrefs(context).getLong(enum.name, 0)
        val bitset = BitSet.valueOf(longArrayOf(currentValue))
        bitset.set(enum.ordinal)
        return AnkiDroidApp.getSharedPrefs(context).edit().putLong(enum.name, bitset.toLongArray()[0]).apply()
    }
}
