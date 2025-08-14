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
import com.ichi2.anki.AnkiDroidApp
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.BeforeClass
import org.junit.Test

class PrefsDelegatesTest {
    @Test
    fun `booleanSetting getter and setter work`() {
        var setting by Prefs.booleanPref(123, false)
        assertThat(setting, equalTo(false))

        setting = true
        assertThat(setting, equalTo(true))
    }

    @Test
    fun `stringSetting getter and setter work`() {
        var setting by Prefs.stringPref(456, "defaultValue")
        assertThat(setting, equalTo("defaultValue"))

        setting = "newValue"
        assertThat(setting, equalTo("newValue"))
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun before() {
            val mockResources = mockk<Resources>()
            AnkiDroidApp.sharedPreferencesTestingOverride = SPMockBuilder().createSharedPreferences()

            every { mockResources.getString(any()) } answers {
                val resId = invocation.args[0] as Int
                resId.toString()
            }

            mockkObject(Prefs)
            every { Prefs.resources } returns mockResources
        }

        @BeforeClass
        @JvmStatic
        fun after() {
            unmockkObject(Prefs)
            AnkiDroidApp.sharedPreferencesTestingOverride = null
        }
    }
}
