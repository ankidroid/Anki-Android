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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.utils.append
import com.ichi2.anki.preferences.PreferenceTestUtils
import com.ichi2.anki.preferences.PreferenceTestUtils.getAttrsFromXml
import com.ichi2.anki.preferences.SettingsFragment
import com.ichi2.anki.settings.enums.PrefEnum
import com.ichi2.testutils.EmptyApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.spy
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import kotlin.reflect.KClass
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.sequences.map
import kotlin.test.assertContains
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class PrefsRobolectricTest : RobolectricTest() {
    private fun getKeysAndDefaultValues(): MutableMap<String, Any?> {
        val spy = spy(SPMockBuilder().createSharedPreferences())
        AnkiDroidApp.sharedPreferencesTestingOverride = spy
        val keysAndDefaultValues: MutableMap<String, Any?> = mutableMapOf()

        val mockResources = mockk<Resources>()
        every { mockResources.getString(any()) } answers { invocation.args[0].toString() }
        mockkObject(Prefs)
        every { Prefs.resources } returns mockResources
        doAnswer { invocation ->
            val key = invocation.arguments[0] as String
            keysAndDefaultValues[key] = invocation.arguments[1]
            invocation.callRealMethod()
        }.run {
            whenever(spy).getBoolean(anyString(), anyBoolean())
            whenever(spy).getString(anyString(), anyOrNull())
            whenever(spy).getInt(anyString(), anyInt())
        }

        for (property in Prefs::class.memberProperties) {
            if (property.visibility != KVisibility.PUBLIC) continue
            property.getter.call(Prefs)
        }
        unmockkObject(Prefs)
        AnkiDroidApp.sharedPreferencesTestingOverride = null
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

    private fun getPropertyNamesAndKeys(): MutableMap<String, String> {
        val spy = spy(SPMockBuilder().createSharedPreferences())
        AnkiDroidApp.sharedPreferencesTestingOverride = spy
        val keys = mutableListOf<String>()

        val mockResources = mockk<Resources>()
        every { mockResources.getString(any()) } answers { invocation.args[0].toString() }
        mockkObject(Prefs)
        every { Prefs.resources } returns mockResources
        doAnswer { invocation ->
            val key = PreferenceTestUtils.attrValueToString("@${invocation.arguments[0]}", targetContext)
            keys.append(key)
            invocation.callRealMethod()
        }.run {
            whenever(spy).getBoolean(anyString(), anyBoolean())
            whenever(spy).getString(anyString(), anyOrNull())
            whenever(spy).getInt(anyString(), anyInt())
        }
        val propertyNamesAndKeys = mutableMapOf<String, String>()
        for (property in Prefs::class.memberProperties) {
            if (property.visibility != KVisibility.PUBLIC) continue
            property.getter.call(Prefs)
            propertyNamesAndKeys[property.name] = keys.last()
        }
        unmockkObject(Prefs)
        AnkiDroidApp.sharedPreferencesTestingOverride = null
        return propertyNamesAndKeys
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `PrefEnum values match their preference entries`() {
        // Prefs property name (String) -> Key (String)
        val allPropertiesAndKeys = getPropertyNamesAndKeys()
        val enumProperties =
            Prefs::class.memberProperties.filter {
                it.returnType.isSubtypeOf(PrefEnum::class.createType())
            }
        // Key (String) -> Prefs property
        val enumPropertiesMap = enumProperties.associateBy { allPropertiesAndKeys.getValue(it.name) }
        // Key (String) -> PrefEnum entryValues (List<String>)
        val prefsEnumKeysAndValues = mutableMapOf<String, List<String>>()
        for ((key, property) in enumPropertiesMap.entries) {
            val enumConstants = ((property.returnType.classifier as KClass<*>).java.enumConstants) as Array<PrefEnum>
            prefsEnumKeysAndValues[key] = enumConstants.map { targetContext.resources.getString(it.entryResId) }
        }

        val listPreferences =
            PreferenceTestUtils
                .getAllPreferencesFragments(targetContext)
                .filterIsInstance<SettingsFragment>()
                .map { it.preferenceResource }
                .flatMap { getAttrsFromXml(targetContext, it, listOf("key", "entryValues")) }
                .filter { it["entryValues"] != null }
                .associate {
                    PreferenceTestUtils.attrValueToString(it["key"]!!, targetContext) to
                        PreferenceTestUtils.attrToStringArray(it["entryValues"]!!, targetContext).toList()
                }

        for ((key, enumValues) in prefsEnumKeysAndValues) {
            assertContains(listPreferences, key)
            assertEquals(enumValues, listPreferences[key])
        }
    }
}
