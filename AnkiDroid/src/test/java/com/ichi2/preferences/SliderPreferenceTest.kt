// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ratnesh Jhavar <ratjhavar05@gmail.com>

package com.ichi2.preferences

import android.content.Context
import android.view.LayoutInflater
import androidx.preference.PreferenceViewHolder
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.slider.Slider
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Regression tests for [SliderPreference].
 * No matter how many times the slider is rebound, only one touch listener should exist, 
 * so one slider action produces only one callback
 */

class SliderPreferenceTest : RobolectricTest(){
    @Test
    fun `rebind does not attach the same touch listener more than once`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pref = SliderPreference(context)

        // Match the minimum required state for a valid slider preference.
        setPrivateField(pref, "valueFrom", 0)
        setPrivateField(pref, "valueTo", 10)
        setPrivateField(pref, "stepSize", 1f)
        pref.value = 0

        val layout = LayoutInflater.from(context).inflate(R.layout.preference_slider, null, false)
        val holder = PreferenceViewHolder.createInstance(layout)
        val slider = layout.findViewById<Slider>(R.id.slider)

        // Rebind the same preference view twice and ensure we still only have one listener.
        pref.onBindViewHolder(holder)
        pref.onBindViewHolder(holder)

        assertEquals(
            "The same touch listener should not be added on repeated binds",
            1,
            countTouchListenerEntries(slider),
        )
    }

    /**
     * The Material slider stores listeners in a backing collection. Count the entries to validate that
     * duplicate attachment is prevented.
     */
    private fun countTouchListenerEntries(slider: Slider): Int {
        for (field in slider.javaClass.declaredFields) {
            field.isAccessible = true
            val value = field.get(slider)
            if (value is Collection<*>) {
                return value.size
            }
        }
        fail("Could not find the slider listener collection via reflection")
    }

    private fun setPrivateField(target: Any, name: String, value: Any) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }
}