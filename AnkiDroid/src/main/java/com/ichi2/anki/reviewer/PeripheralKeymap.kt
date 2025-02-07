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
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.reviewer.Binding.Companion.possibleKeyBindings

/**
 * Maps the [MappableAction] ([A]) with their configured [MappableBinding] ([B]).
 *
 * That way, [onKeyDown] can be used to detect key presses and trigger their actions.
 */
class PeripheralKeymap<B : MappableBinding, A : MappableAction<B>>(
    sharedPrefs: SharedPreferences,
    actions: List<A>,
    private val processor: BindingProcessor<B, A>,
) {
    private val bindingMap = HashMap<Binding, List<Pair<A, B>>>()

    init {
        for (action in actions) {
            val mappableBindings = action.getBindings(sharedPrefs)
            for (mappableBinding in mappableBindings) {
                if (!mappableBinding.isKey) continue
                val binding = mappableBinding.binding
                if (binding in bindingMap) {
                    (bindingMap[binding] as MutableList).add(action to mappableBinding)
                } else {
                    bindingMap[binding] = mutableListOf(action to mappableBinding)
                }
            }
        }
    }

    fun onKeyDown(event: KeyEvent): Boolean {
        if (event.repeatCount > 0) {
            return false
        }
        val bindings = possibleKeyBindings(event)
        for (binding in bindings) {
            val actionAndMappableBindings = bindingMap[binding] ?: continue
            for ((action, mappableBinding) in actionAndMappableBindings) {
                if (processor.processAction(action, mappableBinding)) return true
            }
        }
        return false
    }
}
