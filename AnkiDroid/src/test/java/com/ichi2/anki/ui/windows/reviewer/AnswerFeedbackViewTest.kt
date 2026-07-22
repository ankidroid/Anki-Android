/*
 * Copyright (c) 2026 AnkiDroid Contributors
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
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.settings.Prefs
import com.ichi2.themes.Themes
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class AnswerFeedbackViewTest {
    private lateinit var context: Context
    private lateinit var view: AnswerFeedbackView

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Themes.setTheme(context)
        Prefs.putBoolean(R.string.safe_display_key, false)
        view = AnswerFeedbackView(context)
    }

    @After
    fun tearDown() {
        Prefs.putBoolean(R.string.safe_display_key, false)
    }

    @Test
    fun `toggle without animations shows then hides feedback`() {
        Prefs.putBoolean(R.string.safe_display_key, true)

        view.toggle()

        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(1f, view.alpha)

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(View.GONE, view.visibility)
        assertEquals(1f, view.alpha)
    }

    @Test
    fun `toggle uses property animation instead of legacy Animation`() {
        // Attach so ViewPropertyAnimator scheduling matches production.
        FrameLayout(context).addView(view)

        view.toggle()

        // Legacy AlphaAnimation would set View.getAnimation(); property animation must not.
        assertNull(view.animation)
        ShadowLooper.idleMainLooper()
        assertNull(view.animation)
        assertEquals(View.VISIBLE, view.visibility)

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertNull(view.animation)
        assertEquals(View.GONE, view.visibility)
        assertEquals(1f, view.alpha)
    }

    @Test
    fun `re-toggle cancels prior fade and remains stable`() {
        Prefs.putBoolean(R.string.safe_display_key, true)

        view.toggle()
        view.toggle()

        assertEquals(View.VISIBLE, view.visibility)

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `detach cancels pending hide`() {
        Prefs.putBoolean(R.string.safe_display_key, true)
        val parent = FrameLayout(context)
        parent.addView(view)

        view.toggle()
        assertEquals(View.VISIBLE, view.visibility)

        // Simulate leaving the reviewer while feedback is visible.
        parent.removeView(view)

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Detach cancelled the hide runnable; visibility is left as-is (parent is gone).
        assertEquals(View.VISIBLE, view.visibility)
    }
}
