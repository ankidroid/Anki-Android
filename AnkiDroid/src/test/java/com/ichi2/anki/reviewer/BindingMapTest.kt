// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.MotionEvent
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.cardviewer.ViewerCommand
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BindingMapTest {
    @Test
    fun flagAndAnswerDoNotConflict() {
        val processed: MutableList<ViewerCommand> = ArrayList()

        val sharedPrefs = SPMockBuilder().createSharedPreferences()
        val bindingMap = BindingMap(sharedPrefs, ViewerCommand.entries) { e: ViewerCommand, _ -> processed.add(e) }
        val event = mock(KeyEvent::class.java)
        whenever(event.unicodeChar).thenReturn(0)
        whenever(event.isCtrlPressed).thenReturn(true)
        whenever(event.getUnicodeChar(0)).thenReturn(49)
        whenever(event.keyCode).thenReturn(KeyEvent.KEYCODE_1)

        assertThat(event.unicodeChar.toChar(), equalTo('\u0000'))
        assertThat(event.getUnicodeChar(0).toChar(), equalTo('1'))
        bindingMap.dispatchKeyEvent(event)

        assertThat<List<ViewerCommand>>(processed, hasSize(1))
        assertThat(processed[0], equalTo(ViewerCommand.TOGGLE_FLAG_RED))
    }

    @Test
    fun `dispatchKeyEvent consumes the whole handled press`() {
        val processed = mutableListOf<ViewerCommand>()
        val bindingMap = BindingMap(SPMockBuilder().createSharedPreferences(), ViewerCommand.entries) { e, _ -> processed.add(e) }

        assertTrue(bindingMap.dispatchKeyEvent(keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B)), "press")
        assertTrue(bindingMap.dispatchKeyEvent(keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B, repeatCount = 1)), "repeat")
        assertTrue(bindingMap.dispatchKeyEvent(keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B)), "release")
        assertThat(processed, hasSize(1))
    }

    @Test
    fun `dispatchKeyEvent does not consume unhandled presses`() {
        val bindingMap = BindingMap(SPMockBuilder().createSharedPreferences(), ViewerCommand.entries) { _, _ -> true }

        assertFalse(bindingMap.dispatchKeyEvent(keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_L1)), "press")
        assertFalse(bindingMap.dispatchKeyEvent(keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_L1, repeatCount = 1)), "repeat")
        assertFalse(bindingMap.dispatchKeyEvent(keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_L1)), "release")
    }

    @Test
    fun `dispatchKeyEvent forgets a handled press when the key is pressed again`() {
        var handle = true
        val bindingMap = BindingMap(SPMockBuilder().createSharedPreferences(), ViewerCommand.entries) { _, _ -> handle }
        // the release of this press never arrives, e.g. another view took the focus
        assertTrue(bindingMap.dispatchKeyEvent(keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B)))

        handle = false
        assertFalse(bindingMap.dispatchKeyEvent(keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B)), "press")
        assertFalse(bindingMap.dispatchKeyEvent(keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B)), "release")
    }

    @Test
    fun `onGenericMotionEvent only handles axis movements accepted by the processor`() {
        var handle = false
        val bindingMap = BindingMap(SPMockBuilder().createSharedPreferences(), listOf(AxisAction)) { _, _ -> handle }

        assertFalse(bindingMap.onGenericMotionEvent(axisEvent(1f)), "rejected")
        assertFalse(bindingMap.onGenericMotionEvent(axisEvent(1f)), "rejected, held")
        bindingMap.onGenericMotionEvent(axisEvent(0f))

        handle = true
        assertTrue(bindingMap.onGenericMotionEvent(axisEvent(1f)), "accepted")
    }

    @Test
    fun `onGenericMotionEvent consumes the whole handled axis movement`() {
        val processed = mutableListOf<AxisAction>()
        val bindingMap = BindingMap(SPMockBuilder().createSharedPreferences(), listOf(AxisAction)) { action, _ -> processed.add(action) }

        assertTrue(bindingMap.onGenericMotionEvent(axisEvent(1f)), "past the threshold")
        assertTrue(bindingMap.onGenericMotionEvent(axisEvent(1f)), "held")
        assertFalse(bindingMap.onGenericMotionEvent(axisEvent(0f)), "released")
        assertThat(processed, hasSize(1))
    }

    @Test
    fun `onGenericMotionEvent leaves a handled movement to its binding`() {
        val heldAxes = mutableSetOf<Axis>()
        var lowerPriorityActions = 0
        val bindingMap = BindingMap(SPMockBuilder().createSharedPreferences(), listOf(AxisAction)) { _, _ -> true }
        val lowerPriorityBindingMap =
            BindingMap(SPMockBuilder().createSharedPreferences(), listOf(AxisAction)) { _, _ ->
                lowerPriorityActions++
                true
            }

        bindingMap.onGenericMotionEvent(axisEvent(1f), heldAxes)
        lowerPriorityBindingMap.onGenericMotionEvent(axisEvent(1f), heldAxes)

        assertThat(heldAxes, equalTo(setOf(Axis.X)))
        assertThat("the lower priority binding is ignored", lowerPriorityActions, equalTo(0))
    }

    private object AxisAction : MappableAction<ReviewerBinding> {
        override val preferenceKey = "axis_action"

        override fun getBindings(prefs: SharedPreferences) = listOf(ReviewerBinding(Binding.AxisButtonBinding(Axis.X, 1f), CardSide.ANSWER))
    }

    private fun keyEvent(
        action: Int,
        keyCode: Int,
        repeatCount: Int = 0,
    ): KeyEvent =
        mock(KeyEvent::class.java).also {
            whenever(it.action).thenReturn(action)
            whenever(it.keyCode).thenReturn(keyCode)
            whenever(it.repeatCount).thenReturn(repeatCount)
        }

    private fun axisEvent(x: Float): MotionEvent =
        mock(MotionEvent::class.java).also {
            whenever(it.getAxisValue(MotionEvent.AXIS_X)).thenReturn(x)
        }
}
