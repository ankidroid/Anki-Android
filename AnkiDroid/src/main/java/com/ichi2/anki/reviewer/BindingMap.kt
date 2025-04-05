/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.reviewer

import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.MotionEvent
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
                    else -> {}
                }
            }
        }
        axisDetectors = axisList.toList()
    }

    fun setProcessor(processor: BindingProcessor<B, A>) {
        this.processor = processor
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
     * Accepts a [MotionEvent] and determines if one or more commands need to be executed
     * @return whether one or more commands were executed
     */
    fun onGenericMotionEvent(ev: MotionEvent?): Boolean {
        if (ev == null || axisDetectors.isEmpty()) return false

        var processed = false
        for (detector in axisDetectors) {
            val action = detector.getAction(ev) ?: continue
            processed = true
            processor?.processAction(action, detector.mappableBinding)
        }
        return processed
    }
}
