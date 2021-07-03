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

import android.annotation.SuppressLint
import android.content.Context
import java.util.BitSet

@SuppressLint("ConstantFieldName")
class OnboardingUtils {

    companion object {
        const val SHOW_ONBOARDING = "showOnboarding"

        /**
         * Check if the tutorial for a particular feature should be displayed or not.
         * If the bit at an index is set, then the corresponding tutorial has been seen.
         */
        fun <T> isVisited(featureIdentifier: T, context: Context): Boolean where T : Enum<T>, T : OnboardingFlag {
            if (!AnkiDroidApp.getSharedPrefs(context).getBoolean(SHOW_ONBOARDING, false)) {
                return true
            }

            val visitedFeatures = getAllVisited(context, featureIdentifier.getFeatureConstant())
            return visitedFeatures.get(featureIdentifier.getOnboardingEnumValue())
        }

        /**
         * Set the bit at the index defined for a feature once the tutorial for that feature is seen by the user.
         */
        fun <T> setVisited(featureIdentifier: T, context: Context) where T : Enum<T>, T : OnboardingFlag {
            val visitedFeatures = getAllVisited(context, featureIdentifier.getFeatureConstant())
            visitedFeatures.set(featureIdentifier.getOnboardingEnumValue())
            return AnkiDroidApp.getSharedPrefs(context).edit().putLong(featureIdentifier.getFeatureConstant(), visitedFeatures.toLongArray()[0]).apply()
        }

        /**
         * Returns a BitSet where the set bits indicate the visited screens.
         */
        private fun getAllVisited(context: Context, featureConstant: String): BitSet {
            val currentValue = AnkiDroidApp.getSharedPrefs(context).getLong(featureConstant, 0)
            return BitSet.valueOf(longArrayOf(currentValue))
        }
    }
}
