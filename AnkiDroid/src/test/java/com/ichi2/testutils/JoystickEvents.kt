// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.view.InputDevice
import android.view.MotionEvent
import com.ichi2.anki.reviewer.Axis

/** A joystick movement of [axis] to [value] */
fun joystickEvent(
    axis: Axis,
    value: Float,
): MotionEvent =
    MotionEvent.obtain(
        0L,
        0L,
        MotionEvent.ACTION_MOVE,
        1,
        arrayOf(MotionEvent.PointerProperties().apply { id = 0 }),
        arrayOf(MotionEvent.PointerCoords().apply { setAxisValue(axis.motionEventValue, value) }),
        0,
        0,
        1f,
        1f,
        0,
        0,
        InputDevice.SOURCE_JOYSTICK,
        0,
    )
