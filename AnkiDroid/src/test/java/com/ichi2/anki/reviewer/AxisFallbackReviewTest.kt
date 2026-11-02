// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.app.Activity
import android.content.SharedPreferences
import android.os.Looper
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AxisFallbackReviewTest : RobolectricTest(false) {
    @Test
    fun `unbound dpad synthesizes a key with the trigger neutral`() {
        checkDpadFallback(trigger = 0f)
    }

    @Test
    fun `unbound dpad still synthesizes a key while a bound trigger is held`() {
        checkDpadFallback(trigger = 1f)
    }

    private fun checkDpadFallback(trigger: Float) {
        val keys = mutableListOf<Int>()
        var actions = 0
        val bindingMap =
            BindingMap(getPreferences(), listOf(TriggerAction)) { _, _ ->
                actions++
                true
            }
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            val activity = controller.setup().get()
            val focused =
                View(activity).apply {
                    isFocusableInTouchMode = true
                    setOnGenericMotionListener { _, event -> bindingMap.onGenericMotionEvent(event) }
                    setOnKeyListener { _, code, event ->
                        if (event.action == KeyEvent.ACTION_DOWN) keys.add(code)
                        true
                    }
                }
            activity.setContentView(focused)
            controller.visible().windowFocusChanged(true)
            assertTrue(focused.requestFocus())
            val viewRoot = ReflectionHelpers.callInstanceMethod<Any>(focused, "getViewRootImpl")

            fun inject(hatY: Float) {
                val event =
                    MotionEvent.obtain(
                        0L,
                        0L,
                        MotionEvent.ACTION_MOVE,
                        1,
                        arrayOf(MotionEvent.PointerProperties().apply { id = 0 }),
                        arrayOf(
                            MotionEvent.PointerCoords().apply {
                                setAxisValue(MotionEvent.AXIS_RTRIGGER, trigger)
                                setAxisValue(MotionEvent.AXIS_HAT_Y, hatY)
                            },
                        ),
                        0,
                        0,
                        1f,
                        1f,
                        0,
                        0,
                        InputDevice.SOURCE_JOYSTICK,
                        0,
                    )
                ReflectionHelpers.callInstanceMethod<Unit>(
                    viewRoot,
                    "dispatchInputEvent",
                    ClassParameter.from(InputEvent::class.java, event),
                )
                shadowOf(Looper.getMainLooper()).idle()
            }
            inject(0f)
            inject(1f)
            assertEquals(if (trigger == 0f) 0 else 1, actions)
            assertEquals(listOf(KeyEvent.KEYCODE_DPAD_DOWN), keys, "unbound D-pad should reach Android's key fallback")
        }
    }

    private object TriggerAction : MappableAction<ReviewerBinding> {
        override val preferenceKey = "review_trigger_action"

        override fun getBindings(prefs: SharedPreferences) =
            listOf(ReviewerBinding(Binding.AxisButtonBinding(Axis.R_TRIGGER, 1f), CardSide.BOTH))
    }
}
