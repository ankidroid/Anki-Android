/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.testutils

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.RobolectricTest.Companion.advanceRobolectricLooper
import com.ichi2.anki.ui.DoubleTapListener
import org.robolectric.Shadows

/**
 * Constant to be used - extracted from StackOverflow code
 */
private val uptime = SystemClock.uptimeMillis()

/** Simulates a double tap for a [DoubleTapListener] */
fun View.simulateDoubleTap() {
    fun simulateEvent(
        action: Int,
        delta: Int = 0,
    ) = simulateEvent(this, action, delta)
    simulateEvent(MotionEvent.ACTION_DOWN)
    simulateEvent(MotionEvent.ACTION_UP)
    // delta needs to be > 30 in Robolectric. GestureDetector: DOUBLE_TAP_MIN_TIME
    simulateEvent(MotionEvent.ACTION_DOWN, 50)
    simulateEvent(MotionEvent.ACTION_UP, 50)
}

/**
 * Simulates an unconfirmed single tap for a [DoubleTapListener].
 * Calling this twice will not result in a double-tap
 */
fun View.simulateUnconfirmedSingleTap() {
    fun simulateEvent(action: Int) = simulateEvent(this, action)
    simulateEvent(MotionEvent.ACTION_DOWN)
    simulateEvent(MotionEvent.ACTION_UP)
}

/**
 * https://stackoverflow.com/a/10124199
 */
private fun simulateEvent(
    target: View,
    action: Int,
    delta: Int = 0,
) {
    val event =
        obtainMotionEvent(
            downTime = uptime + delta,
            eventTime = uptime + 100 + delta,
            action = action,
            x = 0.0f,
            y = 0.0f,
            metaState = 0,
        )

    Shadows.shadowOf(target).onTouchListener.onTouch(target, event)
}

/**
 * Kotlin wrapper for [MotionEvent.obtain] allowing named arguments
 * @see MotionEvent.obtain
 */
@Suppress("SameParameterValue")
private fun obtainMotionEvent(
    downTime: Long,
    eventTime: Long,
    action: Int,
    x: Float,
    y: Float,
    metaState: Int,
): MotionEvent =
    MotionEvent.obtain(
        downTime,
        eventTime,
        action,
        x,
        y,
        metaState,
    )!!

/**
 * Runs [block] with a view displayed in an activity: laid out, and attached to a window so
 * [View.post] executes.
 *
 * @param height the height of the view, in pixels. Defaults to the height of the screen
 * @param createView creates the view. The activity is provided as the context, so the AnkiDroid
 * theme is applied
 */
fun <V : View> withViewOnScreen(
    height: Int = MATCH_PARENT,
    createView: (Context) -> V,
    block: (V) -> Unit,
) {
    Robolectric.registerTestActivity<EmptyAnkiActivity>()
    ActivityScenario.launch(EmptyAnkiActivity::class.java).use { scenario ->
        scenario.onActivity { activity ->
            val view = createView(activity)
            activity.setContentView(view, LayoutParams(MATCH_PARENT, height))
            advanceRobolectricLooper() // lay out
            block(view)
        }
    }
}

/** Dispatches a touch [action], such as [MotionEvent.ACTION_UP], at the view's origin */
fun View.dispatchTouch(action: Int) {
    dispatchTouchEvent(obtainMotionEvent(downTime = 0, eventTime = 0, action = action, x = 0f, y = 0f, metaState = 0))
}
