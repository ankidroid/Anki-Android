/*
 *  Copyright (c) 2026 Harshavardhan Khamkar<harshavardhan.khamkar@gmail.com>
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

package com.ichi2.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.slider.Slider
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SliderPreferenceTest : RobolectricTest() {
    @Test
    fun onTouchListenerIsOnlyAddedOnce() {
        targetContext.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight)

        val slider = spyk(Slider(targetContext))
        slider.valueFrom = 0f
        slider.valueTo = 100f
        slider.stepSize = 1f
        slider.value = 50f

        val listener = mockk<Slider.OnSliderTouchListener>(relaxed = true)

        slider.setOnSliderTouchListenerOnce(listener)
        verify(exactly = 1) { slider.addOnSliderTouchListener(listener) }

        slider.setOnSliderTouchListenerOnce(listener)
        verify(exactly = 1) { slider.addOnSliderTouchListener(listener) }

        slider.setOnSliderTouchListenerOnce(listener)
        verify(exactly = 1) { slider.addOnSliderTouchListener(listener) }
    }

    @Test
    fun tagIsSetAfterAddingListener() {
        targetContext.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight)

        val slider = Slider(targetContext)
        slider.valueFrom = 0f
        slider.valueTo = 100f

        val listener = mockk<Slider.OnSliderTouchListener>(relaxed = true)

        assertThat(slider.getTag(R.id.tag_slider_listener_set), nullValue())

        slider.setOnSliderTouchListenerOnce(listener)
        assertThat(slider.getTag(R.id.tag_slider_listener_set), equalTo("set" as Any))
    }

    @Test
    fun differentListenersAreNotAddedOnceTagIsSet() {
        targetContext.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight)

        val slider = spyk(Slider(targetContext))
        slider.valueFrom = 0f
        slider.valueTo = 100f

        val listener1 = mockk<Slider.OnSliderTouchListener>(relaxed = true)
        val listener2 = mockk<Slider.OnSliderTouchListener>(relaxed = true)

        slider.setOnSliderTouchListenerOnce(listener1)
        verify(exactly = 1) { slider.addOnSliderTouchListener(listener1) }

        slider.setOnSliderTouchListenerOnce(listener2)
        verify(exactly = 0) { slider.addOnSliderTouchListener(listener2) }
    }

    private fun Slider.setOnSliderTouchListenerOnce(listener: Slider.OnSliderTouchListener) {
        if (this.getTag(R.id.tag_slider_listener_set) != null) return
        this.addOnSliderTouchListener(listener)
        this.setTag(R.id.tag_slider_listener_set, "set")
    }
}
