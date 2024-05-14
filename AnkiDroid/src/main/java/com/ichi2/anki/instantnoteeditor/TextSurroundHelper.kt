/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.instantnoteeditor

import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.EditText
import kotlin.math.max

/**
 * Represents the state of EditText gesture  recognition
 *
 * This data class holds states of gesture recognition for an EditText,
 * allowing for clear and concise representation of its state.
 */
data class EditTextGestureState(val isEnabled: Boolean)

/**
 * Helper class to handle gesture recognition for EditText
 *
 * Facilitates the management of gesture recognition functionality
 * for an EditText component. It allows enabling or disabling gesture detection
 * based on the provided state, and provides methods for toggling the gesture state.
 */
class EditTextGestureHelper(private val editText: EditText, initialState: EditTextGestureState) {
    private var currentState: EditTextGestureState = initialState

    init {
        applyState(currentState)
    }

    private fun applyState(state: EditTextGestureState) {
        if (state.isEnabled) {
            enableGesture()
        } else {
            disableGesture()
        }
    }

    private fun setupGestureDetection() {
        val gestureDetector =
            GestureDetector(
                editText.context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        handleSingleTap(e)
                        return super.onSingleTapConfirmed(e)
                    }
                }
            )

        editText.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            editText.onTouchEvent(event)
            true
        }

        editText.apply {
            showSoftInputOnFocus = false
            isFocusable = false
            isCursorVisible = false
            isFocusableInTouchMode = true
        }
    }

    private fun handleSingleTap(event: MotionEvent) {
        singleTap(editText, event)
    }

    /**
     * Handles a single tap event on the EditText.
     * This method is responsible for detecting the tapped word, surrounding it with cloze brackets,
     * and updating the EditText with the modified text.
     *
     * @param editText The EditText where the tap event occurred.
     * @param event The MotionEvent representing the tap event.
     */
    private fun singleTap(editText: EditText, event: MotionEvent) {
        val layout = editText.layout
        val x = event.x.toInt()
        val y = event.y.toInt()

        val line = layout.getLineForVertical(y)
        val offset = layout.getOffsetForHorizontal(line, x.toFloat())

        val text = editText.text.toString()
        val start = findWordStart(text, offset)
        val end = findWordEnd(text, offset)

        val selectedWord = text.substring(start, end)
        InstantNoteEditorActivity.currentClozeNumber = max(InstantNoteEditorActivity.currentClozeNumber, 1)
        val newText = buildString {
            append(text.substring(0, start))
            append("{{c${InstantNoteEditorActivity.currentClozeNumber}::$selectedWord}}")
            append(text.substring(end))
        }
        editText.setText(newText)
    }

    private fun findWordStart(text: String, offset: Int): Int {
        var start = offset
        while (start > 0 && !text[start - 1].isWhitespace()) {
            start--
        }
        return start
    }

    private fun findWordEnd(text: String, offset: Int): Int {
        var end = offset
        while (end < text.length && !text[end].isWhitespace()) {
            end++
        }
        return end
    }

    private fun enableGesture() {
        setupGestureDetection()
    }

    private fun disableGesture() {
        editText.apply {
            setOnTouchListener(null)
            isFocusable = true
            isFocusableInTouchMode = true
            showSoftInputOnFocus = true
            isCursorVisible = true
        }
    }

    fun toggleGestureState() {
        currentState = EditTextGestureState(!currentState.isEnabled)
        applyState(currentState)
    }
}
