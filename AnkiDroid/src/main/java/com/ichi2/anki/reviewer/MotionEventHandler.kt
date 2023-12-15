/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.reviewer

import android.content.Context
import android.view.MotionEvent
import com.ichi2.anki.AbstractFlashcardViewer
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.compat.CompatHelper
import timber.log.Timber

/**
 * Handles motion events, such as joystick/pedal input and converts them to [ViewerCommand]s
 *
 * A [MotionEvent] is a **batched** collection of [actions][MotionEvent.getAction] ond [axes][Axis]
 *
 * Axes can either be bidirectional, or unidirectional. AnkiDroid allows a user to define an action
 * which occurs if the extreme of an axis [-1, 1] is met
 */
class MotionEventHandler(
    private val commandProcessor: ViewerCommand.CommandProcessor,
    private val detectors: List<SingleAxisDetector>
) {
    data class SingleAxisDetector(val axis: Axis, val command: ViewerCommand, val threshold: Float) {
        constructor(command: ViewerCommand, binding: Binding.AxisButtonBinding) : this(
            command = command,
            axis = binding.axis,
            threshold = binding.threshold
        )

        /** If the command has been executed and we have not returned lower than the threshold */
        private var sentCommand: Boolean = false

        /**
         * If multiple events above the threshold are obtained, only return 1 command
         * until the value is under the threshold
         */
        private val debouncedCommand: ViewerCommand?
            get() {
                if (sentCommand) return null
                sentCommand = true
                return command
            }

        /**
         * Given a [MotionEvent], determine whether we've reached [threshold].
         *
         * If we have, and [command] has not been sent in the period that we reached the threshold
         * then send the command once and wait for the value to go back under the threshold
         *
         * @return [command] or `null`
         */
        fun getCommand(ev: MotionEvent): ViewerCommand? {
            // TODO: We may need to handle historical events as well
            val value = ev.getAxisValue(axis.motionEventValue)
            when {
                threshold > 0 -> { if (value >= threshold) return debouncedCommand }
                threshold < 0 -> { if (value <= threshold) return debouncedCommand }
            }
            sentCommand = false
            return null
        }

        fun toDisplayString(context: Context) = Binding.AxisButtonBinding(axis, threshold).toDisplayString(context)
    }

    /**
     * Accepts a [MotionEvent] and determines if one or more commands need to be executed
     * @return whether one or more commands were executed
     */
    fun onGenericMotionEvent(ev: MotionEvent?): Boolean {
        if (ev == null || !detectors.any()) return false

        var processed = false
        for (detector in detectors) {
            val command = detector.getCommand(ev) ?: continue
            processed = true
            Timber.v("executing command: %s due to %s", command, detector.toDisplayString(AnkiDroidApp.instance))
            this.commandProcessor.executeCommand(command, null)
        }
        return processed
    }

    companion object {
        fun createInstance(viewer: AbstractFlashcardViewer): MotionEventHandler {
            val handlers = getAxisButtonBindings(viewer).toList()
            Timber.d("setting up %d motion handlers", handlers.size)
            return MotionEventHandler(viewer, handlers)
        }

        private fun getAxisButtonBindings(context: Context) = sequence {
            for ((command, bindings) in MappableBinding.allMappings(context.sharedPrefs())) {
                for (binding in bindings.map { it.binding }.filterIsInstance<Binding.AxisButtonBinding>()) {
                    yield(SingleAxisDetector(command, binding))
                }
            }
        }
    }
}

/**
 * A type of movement, typically a Joystick/Trigger on a gamepad
 *
 * Constants used in [MotionEvent.getAxisValue]. Axes can be in a bidirectional range [-1, 1]
 * @see MotionEvent
 */
enum class Axis(val motionEventValue: Int) {
    /** @see MotionEvent.AXIS_X */
    X(MotionEvent.AXIS_X),

    /** @see MotionEvent.AXIS_Y */
    Y(MotionEvent.AXIS_Y),

    /** @see MotionEvent.AXIS_Z */
    Z(MotionEvent.AXIS_Z),

    /** @see MotionEvent.AXIS_RZ */
    RZ(MotionEvent.AXIS_RZ),

    /** @see MotionEvent.AXIS_BRAKE */
    BRAKE(MotionEvent.AXIS_BRAKE),

    /** @see MotionEvent.AXIS_GAS */
    GAS(MotionEvent.AXIS_GAS),

    /** @see MotionEvent.AXIS_RTRIGGER */
    R_TRIGGER(MotionEvent.AXIS_RTRIGGER),

    /** @see MotionEvent.AXIS_LTRIGGER */
    L_TRIGGER(MotionEvent.AXIS_LTRIGGER),

    /** @see MotionEvent.AXIS_DISTANCE */
    DISTANCE(MotionEvent.AXIS_DISTANCE),

    /** @see MotionEvent.AXIS_GENERIC_1 */
    GENERIC_1(MotionEvent.AXIS_GENERIC_1),

    /** @see MotionEvent.AXIS_GENERIC_2 */
    GENERIC_2(MotionEvent.AXIS_GENERIC_2),

    /** @see MotionEvent.AXIS_GENERIC_3 */
    GENERIC_3(MotionEvent.AXIS_GENERIC_3),

    /** @see MotionEvent.AXIS_GENERIC_4 */
    GENERIC_4(MotionEvent.AXIS_GENERIC_4),

    /** @see MotionEvent.AXIS_GENERIC_5 */
    GENERIC_5(MotionEvent.AXIS_GENERIC_5),

    /** @see MotionEvent.AXIS_GENERIC_6 */
    GENERIC_6(MotionEvent.AXIS_GENERIC_6),

    /** @see MotionEvent.AXIS_GENERIC_7 */
    GENERIC_7(MotionEvent.AXIS_GENERIC_7),

    /** @see MotionEvent.AXIS_GENERIC_8 */
    GENERIC_8(MotionEvent.AXIS_GENERIC_8),

    /** @see MotionEvent.AXIS_GENERIC_9 */
    GENERIC_9(MotionEvent.AXIS_GENERIC_9),

    /** @see MotionEvent.AXIS_GENERIC_10 */
    GENERIC_10(MotionEvent.AXIS_GENERIC_10),

    /** @see MotionEvent.AXIS_GENERIC_11 */
    GENERIC_11(MotionEvent.AXIS_GENERIC_11),

    /** @see MotionEvent.AXIS_GENERIC_12 */
    GENERIC_12(MotionEvent.AXIS_GENERIC_12),

    /** @see MotionEvent.AXIS_GENERIC_13 */
    GENERIC_13(MotionEvent.AXIS_GENERIC_13),

    /** @see MotionEvent.AXIS_GENERIC_14 */
    GENERIC_14(MotionEvent.AXIS_GENERIC_14),

    /** @see MotionEvent.AXIS_GENERIC_15 */
    GENERIC_15(MotionEvent.AXIS_GENERIC_15),

    /** @see MotionEvent.AXIS_GENERIC_16 */
    GENERIC_16(MotionEvent.AXIS_GENERIC_16),

    /** @see MotionEvent.AXIS_HSCROLL */
    H_SCROLL(MotionEvent.AXIS_HSCROLL),

    /** @see MotionEvent.AXIS_ORIENTATION */
    ORIENTATION(MotionEvent.AXIS_ORIENTATION),

    /** @see MotionEvent.AXIS_PRESSURE */
    PRESSURE(MotionEvent.AXIS_PRESSURE),

    /** @see MotionEvent.AXIS_RUDDER */
    RUDDER(MotionEvent.AXIS_RUDDER),

    /** @see MotionEvent.AXIS_RX */
    RX(MotionEvent.AXIS_RX),

    /** @see MotionEvent.AXIS_RY */
    RY(MotionEvent.AXIS_RY),

    /** @see MotionEvent.AXIS_HAT_X */
    HAT_X(MotionEvent.AXIS_HAT_X),

    /** @see MotionEvent.AXIS_HAT_Y */
    HAT_Y(MotionEvent.AXIS_HAT_Y),

    /** @see MotionEvent.AXIS_RELATIVE_X */
    AXIS_RELATIVE_X(CompatHelper.compat.AXIS_RELATIVE_X),

    /** @see MotionEvent.AXIS_RELATIVE_Y */
    AXIS_RELATIVE_Y(CompatHelper.compat.AXIS_RELATIVE_Y),

    /** @see MotionEvent.AXIS_GESTURE_X_OFFSET */
    AXIS_GESTURE_X_OFFSET(CompatHelper.compat.AXIS_GESTURE_X_OFFSET),

    /** @see MotionEvent.AXIS_GESTURE_Y_OFFSET */
    AXIS_GESTURE_Y_OFFSET(CompatHelper.compat.AXIS_GESTURE_Y_OFFSET),

    /** @see MotionEvent.AXIS_GESTURE_PINCH_SCALE_FACTOR */
    AXIS_GESTURE_PINCH_SCALE_FACTOR(CompatHelper.compat.AXIS_GESTURE_PINCH_SCALE_FACTOR),

    /** @see MotionEvent.AXIS_GESTURE_SCROLL_X_DISTANCE */
    AXIS_GESTURE_SCROLL_X_DISTANCE(CompatHelper.compat.AXIS_GESTURE_SCROLL_X_DISTANCE),

    /** @see MotionEvent.AXIS_GESTURE_SCROLL_Y_DISTANCE */
    AXIS_GESTURE_SCROLL_Y_DISTANCE(CompatHelper.compat.AXIS_GESTURE_SCROLL_Y_DISTANCE)

    ;

    /**
     * @return `AXIS_X` etc...
     * @see MotionEvent.axisToString
     */
    override fun toString(): String = MotionEvent.axisToString(this.motionEventValue)

    companion object {
        /** @throws NoSuchElementException if [value] is invalid */
        fun fromInt(value: Int): Axis = Axis.entries.single { it.motionEventValue == value }
    }
}
