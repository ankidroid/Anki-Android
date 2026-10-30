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
     *
     * @return whether [ev] is part of a movement whose action was handled by the [processor].
     * That way, the rest of a handled movement doesn't reach other bindings, or Android's
     * joystick-to-D-pad conversion
     */
    fun onGenericMotionEvent(ev: MotionEvent?): Boolean {
        if (ev == null || axisDetectors.isEmpty()) return false

        var handled = false
        for (detector in axisDetectors) {
            if (detector.onGenericMotionEvent(ev, processor)) handled = true
        }
        return handled
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
