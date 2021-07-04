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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.OnboardingUtils.isVisited
import com.ichi2.anki.OnboardingUtils.setVisited
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingFlagTest : RobolectricAbstractTest() {

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

    private enum class FirstEnum(var mValue: Int) : OnboardingFlag {
        FIRST(0),
        MIDDLE(1);

        override fun getOnboardingEnumValue(): Int {
            return mValue
        }
    }

    private enum class SecondEnum(var mValue: Int) : OnboardingFlag {
        MIDDLE(0),
        LAST(1);

        override fun getOnboardingEnumValue(): Int {
            return mValue
        }
    }

    private fun <T> isVisited(enum: T): Boolean where T : Enum<T>, T : OnboardingFlag {
        return isVisited(enum, targetContext)
    }

    private fun <T> setVisited(enum: T) where T : Enum<T>, T : OnboardingFlag {
        setVisited(enum, targetContext)
    }
}
