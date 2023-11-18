/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.Gesture.*
import com.ichi2.anki.cardviewer.GestureListener
import com.ichi2.anki.cardviewer.TapGestureMode
import timber.log.Timber

/** Allows selection, and display of a single gesture on a square grid
 * Supports swipes and a 9-point touch mode
 *
 * Note: Swipes are not displayed on < API 25 due to issues with <layer-list> display.
 *
 * Currently used by [GesturePicker]
 */
class GestureDisplay
@JvmOverloads // fixes: Error inflating class com.ichi2.ui.GestureDisplay
constructor(context: Context, attributeSet: AttributeSet? = null, defStyleAttr: Int = 0) :
    ConstraintLayout(context, attributeSet, defStyleAttr) {

    /** Converts a touch event into a call to [setGesture] */
    private val mDetector: GestureDetector

    /** "Gesture Changed" callback, invoked if the gesture is changed and non-null */
    private var mOnGestureChangeListener: GestureListener? = null

    /** see [TapGestureMode] */
    private val mTapGestureMode: TapGestureMode

    /** The last recorded gesture (null if no gestures provided, or if explicitly set)  */
    private var mGesture: Gesture? = null

    private var mSwipeView: ImageView

    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.gesture_display, this)

        val listener = OnGestureListener.createInstance(this, this::setGesture)
        mDetector = GestureDetector(context, listener)
        mTapGestureMode = listener.getTapGestureMode()
        setTapGestureMode(mTapGestureMode)
        mSwipeView = findViewById(R.id.swipe_select)
        // if we don't call mutate, state is persisted outside the dialog when we call .setImageLevel
        mSwipeView.drawable?.mutate()
    }

    /** Lists all selectable gestures from this view (excludes null) */
    fun availableValues(): List<Gesture> = entries.filter {
        (mTapGestureMode == TapGestureMode.NINE_POINT || !NINE_POINT_TAP_GESTURES.contains(it))
    }

    /** Sets a callback which is called when the gesture is changed, and non-null */
    fun setGestureChangedListener(listener: GestureListener) {
        mOnGestureChangeListener = listener
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = mDetector.onTouchEvent(event) || super.onTouchEvent(event)

    fun getGesture() = mGesture

    /** Updates the UI from a new gesture
     * fires the "Gesture Changed" event if the gesture has changed and is non-null
     */
    fun setGesture(newGesture: Gesture?) {
        Timber.d("gesture: %s", newGesture?.toDisplayString(context))

        if (mGesture == newGesture) {
            Timber.d("Ignoring nop gesture change")
            return
        }

        handleTapChange(newGesture, mGesture)
        handleSwipeChange(newGesture)

        this.mGesture = newGesture

        if (newGesture == null) return

        mOnGestureChangeListener?.onGesture(newGesture)
    }

    /**
     * Sets the "swipe" view to the provided swipe (or none if the gesture is null or non-swipe])
     * Only works on API 25+ due to issues with layer-list
     */
    private fun handleSwipeChange(gesture: Gesture?) {
        val level = when (gesture) {
            SWIPE_UP -> 1
            SWIPE_DOWN -> 2
            SWIPE_LEFT -> 3
            SWIPE_RIGHT -> 4
            else -> 0
        }
        mSwipeView.setImageLevel(level)
    }

    /**
     * Updates the tap UI (via <selector> and android_selected)
     */
    private fun handleTapChange(gesture: Gesture?, oldGesture: Gesture?) {
        // revert the old change, and implement the new change
        // does nothing if neither are taps
        tapGestureToView(oldGesture)?.isSelected = false
        tapGestureToView(gesture)?.isSelected = true
    }

    /**
     * Maps from a [Gesture] to an [ImageView].
     * @return The associated [ImageView], or null if input is null, or isn't a tap gesture
     */
    private fun tapGestureToView(gesture: Gesture?): ImageView? {
        val viewId = tapGestureToViewId(gesture) ?: return null
        return findViewById(viewId)
    }

    /** Maps from a tap gesture to its view Id, or null if the gesture isn't a tap */
    private fun tapGestureToViewId(gesture: Gesture?): Int? {
        return when (gesture) {
            TAP_TOP_LEFT -> R.id.top_left
            TAP_TOP -> R.id.top_center
            TAP_TOP_RIGHT -> R.id.top_right
            TAP_LEFT -> R.id.left
            TAP_CENTER -> R.id.center
            TAP_RIGHT -> R.id.right
            TAP_BOTTOM_LEFT -> R.id.bottom_left
            TAP_BOTTOM -> R.id.bottom_center
            TAP_BOTTOM_RIGHT -> R.id.bottom_right
            else -> null
        }
    }

    /**
     * If we are using 4-point (corner to corner) gestures, hide the 9-point (square-based) gestures
     */
    private fun setTapGestureMode(tapGestureMode: TapGestureMode) {
        val ninePointVisibility = when (tapGestureMode) {
            TapGestureMode.FOUR_POINT -> View.GONE
            TapGestureMode.NINE_POINT -> View.VISIBLE
        }

        NINE_POINT_TAP_GESTURES.forEach {
            val id = tapGestureToViewId(it) ?: return
            val view = findViewById<ImageView>(id)
            view.visibility = ninePointVisibility
        }
    }

    companion object {
        val NINE_POINT_TAP_GESTURES = listOf(TAP_TOP_LEFT, TAP_TOP_RIGHT, TAP_CENTER, TAP_BOTTOM_LEFT, TAP_BOTTOM_RIGHT)
    }
}
