// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import android.app.Activity
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class AnswerFeedbackViewTest {
    private lateinit var activity: Activity
    private lateinit var view: AnswerFeedbackView

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        view = AnswerFeedbackView(activity).also { activity.setContentView(it) }
    }

    @Test
    fun `feedback is opaque when animations are disabled after an animated hide`() {
        setAnimationsEnabled(true)
        view.toggle()
        advanceBy(1000)
        assertEquals(View.GONE, view.visibility)

        setAnimationsEnabled(false)
        view.toggle()

        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(1f, view.alpha)
    }

    @Test
    fun `toggle during fade out cancels the pending hide`() {
        setAnimationsEnabled(true)
        view.toggle()
        advanceBy(600)

        view.toggle()
        advanceBy(500)

        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(1f, view.alpha)
    }

    private fun setAnimationsEnabled(enabled: Boolean) {
        Settings.Global.putFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, if (enabled) 1f else 0f)
    }

    private fun advanceBy(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
}
