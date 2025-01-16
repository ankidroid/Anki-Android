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

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.AnkiDroidApp
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.spy
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

class PrefsTest : OnSharedPreferenceChangeListener {
    private var lastSetKey: String? = null

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?,
    ) {
        lastSetKey = key
    }

    @Test
    fun `booleanSetting getter and setter work`() {
        AnkiDroidApp.sharedPreferencesTestingOverride = SPMockBuilder().createSharedPreferences()
        var setting by Prefs.booleanPref("boolKey", false)
        assertThat(setting, equalTo(false))

        setting = true
        assertThat(setting, equalTo(true))
    }

    @Test
    fun `stringSetting getter and setter work`() {
        AnkiDroidApp.sharedPreferencesTestingOverride = SPMockBuilder().createSharedPreferences()
        var setting by Prefs.stringPref("stringKey", "defaultValue")
        assertThat(setting, equalTo("defaultValue"))

        setting = "newValue"
        assertThat(setting, equalTo("newValue"))
    }

    @Test
    fun `getters and setters use the same key`() {
        val sharedPrefsSpy = spy(SPMockBuilder().createSharedPreferences())
        AnkiDroidApp.sharedPreferencesTestingOverride = sharedPrefsSpy

        var getterKey = ""
        doAnswer { invocation ->
            getterKey = invocation.arguments[0] as String
            invocation.callRealMethod()
        }.run {
            whenever(sharedPrefsSpy).getBoolean(anyString(), anyBoolean())
            whenever(sharedPrefsSpy).getString(anyString(), anyOrNull())
            whenever(sharedPrefsSpy).getInt(anyString(), anyInt())
        }

        sharedPrefsSpy.registerOnSharedPreferenceChangeListener(this)
        for (property in Prefs::class.memberProperties) {
            if (property.visibility != KVisibility.PUBLIC || property !is KMutableProperty<*>) continue

            property.getter.call(Prefs)

            when (property.returnType.classifier) {
                Boolean::class -> property.setter.call(Prefs, false)
                String::class -> property.setter.call(Prefs, "foo")
                else -> continue
            }

            assertThat("The getter and setter of '${property.name}' use the same key", getterKey, equalTo(lastSetKey))
        }
        sharedPrefsSpy.unregisterOnSharedPreferenceChangeListener(this)
    }
}
