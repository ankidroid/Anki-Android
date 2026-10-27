// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings

import android.content.res.Resources
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

class PrefsTest {
    @Test
    fun `getters and setters use the same key`() {
        val sharedPrefs = SPMockBuilder().createSharedPreferences()
        val mockResources = mockk<Resources>()
        var lastKey = 0
        every { mockResources.getString(any()) } answers {
            val resId = invocation.args[0] as Int
            lastKey = resId
            resId.toString()
        }
        val prefs = PrefsRepository(sharedPrefs, mockResources)

        for (property in PrefsRepository::class.memberProperties) {
            if (property.visibility != KVisibility.PUBLIC || property !is KMutableProperty<*>) continue

            property.getter.call(prefs)
            val getterKey = lastKey

            when (property.returnType.classifier) {
                Boolean::class -> property.setter.call(prefs, false)
                String::class -> property.setter.call(prefs, "foo")
                else -> continue
            }
            val setterKey = lastKey

            assertThat("The getter and setter of '${property.name}' use the same key", getterKey, equalTo(setterKey))
        }
    }
}
