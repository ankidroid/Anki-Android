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
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.reviewer.Binding.Companion.possibleKeyBindings
import com.ichi2.anki.reviewer.CardSide.Companion.fromAnswer
import com.ichi2.anki.reviewer.MappableBinding.*
import com.ichi2.anki.reviewer.MappableBinding.Companion.fromPreference

/** Accepts peripheral input, mapping via various keybinding strategies,
 * and converting them to commands for the Reviewer.  */
class PeripheralKeymap(reviewerUi: ReviewerUi, commandProcessor: ViewerCommand.CommandProcessor) {
    private val keyMap: KeyMap
    private var hasSetup = false
    fun setup() {
        val preferences = AnkiDroidApp.instance.sharedPrefs()
        setup(preferences)
    }

    fun setup(preferences: SharedPreferences) {
        for (command in ViewerCommand.entries) {
            add(command, preferences)
        }
        hasSetup = true
    }

    private fun add(command: ViewerCommand, preferences: SharedPreferences) {
        val bindings = fromPreference(preferences, command)
            .filter { it.screen is Screen.Reviewer }
        for (b in bindings) {
            if (!b.isKey) {
                continue
            }
            keyMap[b] = command
        }
    }

    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (!hasSetup || event.repeatCount > 0) {
            false
        } else {
            keyMap.onKeyDown(keyCode, event)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return false
    }

    class KeyMap(
        private val processor: ViewerCommand.CommandProcessor,
        private val reviewerUI: ReviewerUi,
        private val screenBuilder: (CardSide) -> Screen
    ) {
        val bindingMap = HashMap<MappableBinding, ViewerCommand>()

        @Suppress("UNUSED_PARAMETER")
        fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
            var ret = false
            val bindings = possibleKeyBindings(event!!)
            val side = fromAnswer(reviewerUI.isDisplayingAnswer)
            for (b in bindings) {
                val binding = MappableBinding(b, screenBuilder(side))
                val command = bindingMap[binding] ?: continue
                ret = ret or processor.executeCommand(command, fromGesture = null)
            }
            return ret
        }

        operator fun set(key: MappableBinding, value: ViewerCommand) {
            bindingMap[key] = value
        }

        operator fun get(key: MappableBinding): ViewerCommand? {
            return bindingMap[key]
        }
    }

    init {
        keyMap = KeyMap(commandProcessor, reviewerUi) { Screen.Reviewer(it) }
    }
}
