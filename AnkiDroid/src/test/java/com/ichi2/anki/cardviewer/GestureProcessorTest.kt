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
import com.ichi2.utils.KotlinCleanup
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.eq

@KotlinCleanup("use mockk for all mocking")
@KotlinCleanup("`when` -> whenever()")
@KotlinCleanup("IDE lint")
class GestureProcessorTest : ViewerCommand.CommandProcessor {
    private val mSut = GestureProcessor(this)
    private val mExecutedCommands: MutableList<ViewerCommand> = ArrayList()
    override fun executeCommand(which: ViewerCommand): Boolean {
        mExecutedCommands.add(which)
        return true
    }

    fun singleResult(): ViewerCommand {
        assertThat<List<ViewerCommand>>(mExecutedCommands, hasSize(1))
        return mExecutedCommands[0]
    }

    @Test
    fun integrationTest() {
        val prefs = mock(SharedPreferences::class.java, RETURNS_DEEP_STUBS)
        `when`(prefs.getString(nullable(String::class.java), nullable(String::class.java))).thenReturn("0")
        `when`(prefs.getString(eq("gestureTapCenter"), nullable(String::class.java))).thenReturn("1")
        `when`(prefs.getBoolean(eq("gestureCornerTouch"), anyBoolean())).thenReturn(true)
        mSut.init(prefs)
        mSut.onTap(100, 100, 50f, 50f)
        assertThat(singleResult(), `is`(ViewerCommand.COMMAND_SHOW_ANSWER))
    }

    companion object {
        @KotlinCleanup("scope function")
        @BeforeClass
        @JvmStatic
        fun before() {
            mockkStatic(ViewConfiguration::class)
            every { ViewConfiguration.get(any()) } answers { mock(ViewConfiguration::class.java) }
        }

        @JvmStatic
        @AfterClass
        fun after() {
            unmockkStatic(ViewConfiguration::class)
        }
    }
}
