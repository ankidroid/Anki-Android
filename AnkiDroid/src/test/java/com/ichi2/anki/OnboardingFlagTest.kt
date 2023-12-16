/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Arthur Milchior <arthur@milchior.fr>                              *
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

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.OnboardingUtils.Companion.SHOW_ONBOARDING
import com.ichi2.anki.preferences.sharedPrefs
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingFlagTest : RobolectricTest() {

    companion object {
        const val FIRST_ENUM = "FirstEnum"
        const val SECOND_ENUM = "SecondEnum"
    }

    @Before
    fun setShowOnboardingPreference() {
        targetContext.sharedPrefs().edit { putBoolean(SHOW_ONBOARDING, true) }
    }

    @After
    fun after() {
        Onboarding.resetOnboardingForTesting() // #9597 - global needs resetting
    }

    @Test
    fun verifyThatEnumsAreNotSet() {
        assertFalse(isVisited(FirstEnum.FIRST))
        assertFalse(isVisited(FirstEnum.MIDDLE))
        assertFalse(isVisited(SecondEnum.MIDDLE))
        assertFalse(isVisited(SecondEnum.LAST))
    }

    @Test
    fun verifyOnlyOneEnumIsSet() {
        setVisited(FirstEnum.MIDDLE)

        assertFalse(isVisited(FirstEnum.FIRST))
        assertTrue(isVisited(FirstEnum.MIDDLE))
        assertFalse(isVisited(SecondEnum.MIDDLE))
        assertFalse(isVisited(SecondEnum.LAST))
    }

    @Test
    fun verifyEnumsWithSameConstantNameAreSetSeparately() {
        setVisited(FirstEnum.MIDDLE)
        setVisited(SecondEnum.MIDDLE)

        assertFalse(isVisited(FirstEnum.FIRST))
        assertTrue(isVisited(FirstEnum.MIDDLE))
        assertTrue(isVisited(SecondEnum.MIDDLE))
        assertFalse(isVisited(SecondEnum.LAST))
    }

    @Test
    fun verifyAllOfCardBrowserOnboardingEnumsAreSet() {
        setVisited(FirstEnum.FIRST)
        setVisited(FirstEnum.MIDDLE)

        assertTrue(isVisited(FirstEnum.FIRST))
        assertTrue(isVisited(FirstEnum.MIDDLE))
        assertFalse(isVisited(SecondEnum.MIDDLE))
        assertFalse(isVisited(SecondEnum.LAST))
    }

    @Test
    fun verifyReset() {
        OnboardingUtils.addFeature(FIRST_ENUM)
        setVisited(FirstEnum.FIRST)
        setVisited(SecondEnum.LAST)
        OnboardingUtils.reset(targetContext)
        assertFalse(isVisited(FirstEnum.FIRST))
        assertTrue(isVisited(SecondEnum.LAST))
    }

    private enum class FirstEnum(var valueFirst: Int) : OnboardingFlag {
        FIRST(0),
        MIDDLE(1),
        ;

        override fun getOnboardingEnumValue(): Int {
            return valueFirst
        }

        override fun getFeatureConstant(): String {
            return FIRST_ENUM
        }
    }

    private enum class SecondEnum(var valueSecond: Int) : OnboardingFlag {
        MIDDLE(0),
        LAST(1),
        ;

        override fun getOnboardingEnumValue(): Int {
            return valueSecond
        }

        override fun getFeatureConstant(): String {
            return SECOND_ENUM
        }
    }

    private fun isVisited(featureIdentifier: OnboardingFlag): Boolean {
        return OnboardingUtils.isVisited(featureIdentifier, targetContext)
    }

    private fun setVisited(featureIdentifier: OnboardingFlag) {
        OnboardingUtils.setVisited(featureIdentifier, targetContext)
    }
}
