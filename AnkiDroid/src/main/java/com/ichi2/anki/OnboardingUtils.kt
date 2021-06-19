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
    fun <T> isVisited(onboardingEnum: T, context: Context): Boolean where T : Enum<T>, T : OnboardingFlag {
        val visitedScreens = getAllVisited(onboardingEnum, context)
        return visitedScreens.get(onboardingEnum.getOnboardingEnumValue())
    }

    /**
     * Set the bit at the index defined for a feature once the tutorial for that feature is seen by the user.
     */
    fun <T> setVisited(onboardingEnum: T, context: Context) where T : Enum<T>, T : OnboardingFlag {
        val visitedScreens = getAllVisited(onboardingEnum, context)
        visitedScreens.set(onboardingEnum.getOnboardingEnumValue())
        return AnkiDroidApp.getSharedPrefs(context).edit().putLong(onboardingEnum.declaringClass.simpleName, visitedScreens.toLongArray()[0]).apply()
    }

    /**
     * Returns a BitSet where the set bits indicate the visited screens.
     */
    private fun <T> getAllVisited(onboardingEnum: T, context: Context): BitSet where T : Enum<T> {
        val currentValue = AnkiDroidApp.getSharedPrefs(context).getLong(onboardingEnum.declaringClass.simpleName, 0)
        return BitSet.valueOf(longArrayOf(currentValue))
    }
}
