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

/**
 * Enumeration related to onboarding to use fixed integral values for enum constants
 * instead of using ordinals and ensuring that the used values are distinct. Implement this
 * interface whenever an onboarding related enum is needed.
 *
 * For removing constants, comment out the constant instead of removing it so that older values
 * are not used again.
 */
interface OnboardingFlag {
    /**
     * Distinct values should be used in a particular onboarding enum class.
     * The returned value should be between 0 and 63, the position range of bits in Long.
     */
    fun getOnboardingEnumValue(): Int
    // TODO: Add lint check.

    /**
     * Constant used to represent preference key for screens.
     * Values once defined should not be changed.
     */
    fun getFeatureConstant(): String
}
