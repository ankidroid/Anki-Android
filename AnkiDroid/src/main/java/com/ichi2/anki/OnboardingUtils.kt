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
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.ichi2.anki.IntroductionActivity.Companion.INTRODUCTION_SLIDES_SHOWN
import com.ichi2.anki.preferences.sharedPrefs
import timber.log.Timber
import java.util.*

class OnboardingUtils {
    companion object {
        /**
         * SHOW_ONBOARDING represents the preference key for checking if onboarding is enabled.
         * Preference can be toggled by visiting 'Advanced' settings in the app.
         */
        const val SHOW_ONBOARDING = "showOnboarding"

        @VisibleForTesting
        val featureConstants: MutableSet<String> = HashSet()

        /** Register this feature category as an onboarding feature.
         * It ensures it gets reset if asked. */
        fun addFeature(featureCategory: String) {
            featureConstants.add(featureCategory)
        }

        /** Register all feature categories as onboarding features.
         * They all get reset when reset is pressed. */
        fun addFeatures(featureCategory: Iterable<String>) {
            featureCategory.forEach(::addFeature)
        }

        /**
         * Check if the tutorial for a feature should be displayed or not.
         */
        fun isVisited(
            featureIdentifier: OnboardingFlag,
            context: Context,
        ): Boolean {
            // Return if onboarding is not enabled.
            if (!context.sharedPrefs().getBoolean(SHOW_ONBOARDING, false)) {
                return true
            }

            val visitedFeatures = getAllVisited(context, featureIdentifier.getFeatureConstant())

            // If the bit at an index is set, then the corresponding tutorial has been seen.
            // Return true if seen, otherwise false.
            return visitedFeatures.get(featureIdentifier.getOnboardingEnumValue())
        }

        /**
         * Set the tutorial for a feature as visited.
         */
        fun setVisited(
            featureIdentifier: OnboardingFlag,
            context: Context,
        ) {
            val visitedFeatures = getAllVisited(context, featureIdentifier.getFeatureConstant())

            // Set the bit at the index defined for a feature once the tutorial for that feature is seen by the user.
            visitedFeatures.set(featureIdentifier.getOnboardingEnumValue())

            context.sharedPrefs().edit {
                putLong(
                    featureIdentifier.getFeatureConstant(),
                    visitedFeatures.toLongArray()[0],
                )
            }
        }

        /**
         * Returns a BitSet where the set bits indicate the visited screens.
         */
        private fun getAllVisited(
            context: Context,
            featureConstant: String,
        ): BitSet {
            val currentValue = context.sharedPrefs().getLong(featureConstant, 0)
            return BitSet.valueOf(longArrayOf(currentValue))
        }

        fun reset(context: Context) {
            Timber.i("Resetting all onboarding")
            reset(context, featureConstants)
        }

        private fun reset(
            context: Context,
            featureConstants: Collection<String>,
        ) {
            context.sharedPrefs().edit {
                featureConstants.forEach {
                    this@edit.putLong(it, 0)
                }
                // Reset introduction slides preference
                putBoolean(INTRODUCTION_SLIDES_SHOWN, false)
            }
        }
    }
}
