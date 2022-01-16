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
package com.ichi2.anki.cardviewer

import android.content.SharedPreferences
import android.view.ViewConfiguration
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.util.*

class GestureProcessorTest : ViewerCommand.CommandProcessor {
    private val mSut = GestureProcessor(this)
    private val mExecutedCommands: MutableList<ViewerCommand> = ArrayList()
    override fun executeCommand(which: ViewerCommand): Boolean {
        mExecutedCommands.add(which)
        return true
    }

    private fun singleResult(): ViewerCommand {
        MatcherAssert.assertThat<List<ViewerCommand>>(mExecutedCommands, Matchers.hasSize(1))
        return mExecutedCommands[0]
    }

    @Test
    fun integrationTest() {
        val prefs = Mockito.mock(SharedPreferences::class.java, Mockito.RETURNS_DEEP_STUBS)
        `when`(prefs.getString(ArgumentMatchers.nullable(String::class.java), ArgumentMatchers.nullable(String::class.java))).thenReturn("0")
        `when`(prefs.getString(ArgumentMatchers.eq("gestureTapCenter"), ArgumentMatchers.nullable(String::class.java))).thenReturn("1")
        `when`(prefs.getBoolean(ArgumentMatchers.eq("gestureCornerTouch"), ArgumentMatchers.anyBoolean())).thenReturn(true)
        mSut.init(prefs)
        mSut.onTap(100, 100, 50f, 50f)
        MatcherAssert.assertThat(singleResult(), Matchers.`is`(ViewerCommand.COMMAND_SHOW_ANSWER))
    }

    companion object {
        @JvmStatic
        private lateinit var utilities: MockedStatic<ViewConfiguration>

        @BeforeClass
        fun before() {
            utilities = Mockito.mockStatic(ViewConfiguration::class.java)
            utilities.`when`<Any> { ViewConfiguration.get(ArgumentMatchers.any()) }.thenReturn(Mockito.mock(ViewConfiguration::class.java))
        }

        @AfterClass
        fun after() {
            utilities.close()
        }
    }
}
