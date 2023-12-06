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

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.constraintlayout.widget.ConstraintLayout
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.GestureListener
import com.ichi2.utils.UiUtil.setSelectedValue
import timber.log.Timber

/** [View] which allows selection of a gesture either via taps/swipes, or via a [Spinner]
 * The spinner aids discoverability of [Gesture.DOUBLE_TAP] and [Gesture.LONG_TAP]
 * as they're not explained in [GestureDisplay].
 *
 * Current use is via [com.ichi2.anki.dialogs.GestureSelectionDialogBuilder]
 */
// This class exists as elements resized when adding in the spinner to GestureDisplay.kt
class GesturePicker(ctx: Context, attributeSet: AttributeSet? = null, defStyleAttr: Int = 0) :
    ConstraintLayout(ctx, attributeSet, defStyleAttr) {

    private val mGestureSpinner: Spinner
    private val mGestureDisplay: GestureDisplay

    private var mOnGestureListener: GestureListener? = null

    init {
        val inflater = ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.gesture_picker, this)
        mGestureDisplay = findViewById(R.id.gestureDisplay)
        mGestureSpinner = findViewById(R.id.spinner_gesture)
        mGestureDisplay.setGestureChangedListener(this::onGesture)
        mGestureSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, allGestures())
        mGestureSpinner.onItemSelectedListener = InnerSpinner()
    }

    fun getGesture() = mGestureDisplay.getGesture()

    private fun onGesture(gesture: Gesture?) {
        Timber.d("gesture: %s", gesture?.toDisplayString(context))

        setGesture(gesture)

        if (gesture == null) {
            return
        }

        mOnGestureListener?.onGesture(gesture)
    }

    private fun setGesture(gesture: Gesture?) {
        mGestureSpinner.setSelectedValue(GestureWrapper(gesture))
        mGestureDisplay.setGesture(gesture)
    }

    /** Not fired if deselected */
    fun setGestureChangedListener(listener: GestureListener?) {
        mOnGestureListener = listener
    }

    fun allGestures(): List<GestureWrapper> {
        return (listOf(null) + availableGestures()).map(this::GestureWrapper).toList()
    }

    private fun availableGestures() = mGestureDisplay.availableValues()

    inner class GestureWrapper(val gesture: Gesture?) {
        override fun toString(): String {
            return gesture?.toDisplayString(context) ?: resources.getString(R.string.gestures_none)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as GestureWrapper

            return gesture == other.gesture
        }

        override fun hashCode() = gesture?.hashCode() ?: 0
    }

    private inner class InnerSpinner : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val wrapper = parent?.getItemAtPosition(position) as? GestureWrapper
            onGesture(wrapper?.gesture)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }
}
