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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.preferences.PreferenceTestUtils
import com.ichi2.anki.preferences.PreferenceTestUtils.getAttrsFromXml
import com.ichi2.anki.preferences.SettingsFragment
import com.ichi2.anki.settings.enums.PrefEnum
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.spy
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class PrefsRobolectricTest : RobolectricTest() {
    @Before
    fun setup() {
        AnkiDroidApp.sharedPreferencesTestingOverride =
            SPMockBuilder().createSharedPreferences()
    }

    private fun getKeysAndDefaultValues(): MutableMap<String, Any?> {
        val settingsSpy = spy(Prefs)
        val keysAndDefaultValues: MutableMap<String, Any?> = mutableMapOf()
        var key = ""

        doAnswer { invocation ->
            key = invocation.arguments[0] as String
            keysAndDefaultValues[key] = null
            invocation.callRealMethod()
        }.run {
            whenever(settingsSpy).getBoolean(anyString(), anyBoolean())
            whenever(settingsSpy).getString(anyString(), anyString())
            whenever(settingsSpy).getInt(anyString(), anyInt())
        }

        for (property in Prefs::class.memberProperties) {
            if (property.visibility != KVisibility.PUBLIC) continue
            val defaultValue = property.getter.call(settingsSpy)
            keysAndDefaultValues[key] =
                if (defaultValue is PrefEnum) {
                    defaultValue.entryValue
                } else {
                    defaultValue
                }
        }
        return keysAndDefaultValues
    }

    @Test
    fun `all default values match the preference XMLs`() {
        val keysAndDefaultValues = getKeysAndDefaultValues()
        val devOptionsKeys = PreferenceTestUtils.getDevOptionsKeys(targetContext)
        val prefs =
            PreferenceTestUtils
                .getAllPreferencesFragments(targetContext)
                .asSequence()
                .filterIsInstance<SettingsFragment>()
                .map { it.preferenceResource }
                .flatMap { getAttrsFromXml(targetContext, it, listOf("defaultValue", "key")) }
                .filter { it["key"] != null }
                .associate { PreferenceTestUtils.attrValueToString(it["key"]!!, targetContext) to it["defaultValue"] }

        for ((key, defaultValue) in keysAndDefaultValues.entries) {
            if (key !in prefs || key in devOptionsKeys) continue
            val prefsDefaultValue = prefs.getValue(key)
            assertThat("The default value of '$key' matches the preference XML", defaultValue.toString(), equalTo(prefsDefaultValue))
        }
    }
}
