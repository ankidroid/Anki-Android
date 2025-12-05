/*
 * Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.settings

import android.content.res.Resources
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.settings.enums.PrefEnum
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class PrefsDelegatesTest {
    val prefs: PrefsRepository

    init {
        val sharedPrefs = SPMockBuilder().createSharedPreferences()
        val mockResources = mockk<Resources>()
        every { mockResources.getString(any()) } answers {
            val resId = invocation.args[0] as Int
            resId.toString()
        }
        prefs = PrefsRepository(sharedPrefs, mockResources)
    }

    @Test
    fun `booleanSetting getter and setter work`() {
        var setting by prefs.booleanPref(123, false)
        assertThat(setting, equalTo(false))

        setting = true
        assertThat(setting, equalTo(true))
    }

    @Test
    fun `stringSetting getter and setter work`() {
        var setting by prefs.stringPref(456, "defaultValue")
        assertThat(setting, equalTo("defaultValue"))

        setting = "newValue"
        assertThat(setting, equalTo("newValue"))
    }

    @Test
    fun `enumPref getter and setter work`() {
        var pref by prefs.enumPref(789, TestEnum.SECOND)
        assertThat(pref, equalTo(TestEnum.SECOND))

        pref = TestEnum.FIRST
        assertThat(pref, equalTo(TestEnum.FIRST))
    }

    private enum class TestEnum(
        override val entryResId: Int,
    ) : PrefEnum {
        FIRST(0),
        SECOND(1),
    }
}
