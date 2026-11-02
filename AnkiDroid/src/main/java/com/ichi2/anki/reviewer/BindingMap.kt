// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.MotionEvent
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.reviewer.Binding.Companion.possibleKeyBindings

/**
 * Maps the given [MappableAction]s with their configured [MappableBinding]s.
 *
 * That way, key presses and joystick movements can be detected to trigger their actions.
 *
 * * [onKeyDown]: captures key presses
 * * [onGenericMotionEvent]: captures joystick/pedal input. Axes can either be bidirectional,
 *   or unidirectional.
 */
class BindingMap<B : MappableBinding, A : MappableAction<B>>(
    sharedPrefs: SharedPreferences,
    actions: List<A>,
    private var processor: BindingProcessor<B, A>? = null,
) {
    private val keyMap = HashMap<Binding, List<Pair<A, B>>>()
    private val axisDetectors: List<SingleAxisDetector<B, A>>
    private val gestureMap = HashMap<Gesture, List<Pair<A, B>>>()

    init {
        val axisList = mutableListOf<SingleAxisDetector<B, A>>()
        for (action in actions) {
            val mappableBindings = action.getBindings(sharedPrefs)
            for (mappableBinding in mappableBindings) {
                when (val binding = mappableBinding.binding) {
                    is Binding.KeyBinding -> {
                        if (binding in keyMap) {
                            (keyMap[binding] as MutableList).add(action to mappableBinding)
                        } else {
                            keyMap[binding] = mutableListOf(action to mappableBinding)
                        }
                    }
                    is Binding.AxisButtonBinding -> {
                        axisList.add(SingleAxisDetector(action, mappableBinding))
                    }
                    is Binding.GestureInput -> {
                        if (binding.gesture in gestureMap) {
                            (gestureMap[binding.gesture] as MutableList).add(action to mappableBinding)
                        } else {
                            gestureMap[binding.gesture] = mutableListOf(action to mappableBinding)
                        }
                    }
                }
            }
        }
        axisDetectors = axisList.toList()
    }

    fun onKeyDown(event: KeyEvent): Boolean {
        if (event.repeatCount > 0) {
            return false
        }
        val bindings = possibleKeyBindings(event)
        for (binding in bindings) {
            val actionAndMappableBindings = keyMap[binding] ?: continue
            for ((action, mappableBinding) in actionAndMappableBindings) {
                if (processor?.processAction(action, mappableBinding) == true) return true
            }
        }
        return false
    }

    /**
     * Triggers the actions bound to axis movements past their threshold, once per movement.
     * A handled movement belongs to its binding until released.
     *
     * @param heldAxes the axes of the movements handled by bindings of higher priority, which are
     * ignored here. The axes of the movements handled here are added to it
     * @return whether [ev] should be consumed: it triggered a handled action, or it continues a
     * handled movement that Android would otherwise convert into D-pad presses. A joystick event
     * carries all the axes of the device, so the movements of other axes still reach Android's
     * joystick-to-D-pad conversion
     */
    fun onGenericMotionEvent(
        ev: MotionEvent?,
        heldAxes: MutableSet<Axis> = mutableSetOf(),
    ): Boolean {
        if (ev == null || axisDetectors.isEmpty()) return false

        var consumed = false
        for (detector in axisDetectors) {
            if (detector.axis in heldAxes) continue
            if (detector.onGenericMotionEvent(ev, processor)) consumed = true
        }
        axisDetectors.filter { it.isHandled }.mapNotNullTo(heldAxes) { it.axis }
        return consumed
    }

    fun onGesture(gesture: Gesture): Boolean {
        val mappableBindings = gestureMap[gesture] ?: return false
        for ((action, mappableBinding) in mappableBindings) {
            if (processor?.processAction(action, mappableBinding) == true) return true
        }
        return false
    }

    fun isBound(gesture: Gesture): Boolean = gesture in gestureMap
}
