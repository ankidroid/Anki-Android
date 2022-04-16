/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.content.Intent
import android.graphics.Color
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import java.util.*

@RunWith(ParameterizedRobolectricTestRunner::class)
@KotlinCleanup("IDE lint")
@KotlinCleanup("`is` -> equalTo")
class WhiteboardDefaultForegroundColorTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField
    var mIsInverted = false

    @ParameterizedRobolectricTestRunner.Parameter(1)
    @JvmField
    var mExpectedResult = 0
    @Test
    fun testDefaultForegroundColor() {
        assertThat(foregroundColor, `is`(mExpectedResult))
    }

    protected val foregroundColor: Int
        get() {
            val mock: AbstractFlashcardViewer = super.startActivityNormallyOpenCollectionWithIntent(Reviewer::class.java, Intent())
            return Whiteboard(mock, true, mIsInverted).foregroundColor
        }

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters
        @JvmStatic
        fun initParameters(): Collection<Array<Any>> {
            return Arrays.asList(*arrayOf(arrayOf(true, Color.WHITE), arrayOf(false, Color.BLACK)))
        }
    }
}
