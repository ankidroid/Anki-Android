/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.ui

import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.ichi2.anki.R
import com.ichi2.anki.reviewer.Binding
import timber.log.Timber

typealias KeyCode = Int

/**
 * Square dialog which allows a user to select a [Binding] for a key press
 * This does not yet support bluetooth headsets.
 */
class KeyPicker(val rootLayout: View) {
    private val textView: TextView = rootLayout.findViewById(R.id.key_picker_selected_key)

    private val context: Context get() = rootLayout.context

    private var text: String
        set(value) {
            textView.text = value
        }
        get() = textView.text.toString()

    private var onBindingChangedListener: ((Binding) -> Unit)? = null
    private var isValidKeyCode: ((KeyCode) -> Boolean)? = null

    /** Currently bound key */
    private var binding: Binding? = null

    fun getBinding(): Binding? = binding

    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return true

        // When accepting a keypress, we only want to find the keycode, not the unicode character.
        val newBinding =
            Binding.possibleKeyBindings(event)
                .filterIsInstance<Binding.KeyCode>()
                .firstOrNull { binding -> isValidKeyCode?.invoke(binding.keycode) != false } ?: return true
        Timber.d("Changed key to '%s'", newBinding)
        binding = newBinding
        text = newBinding.toDisplayString(context)
        onBindingChangedListener?.invoke(newBinding)
        return true
    }

    fun setBindingChangedListener(listener: (Binding) -> Unit) {
        onBindingChangedListener = listener
    }

    fun setKeycodeValidation(validation: (KeyCode) -> Boolean) {
        isValidKeyCode = validation
    }

    init {
        textView.requestFocus()
        textView.setOnKeyListener { _, _, event -> dispatchKeyEvent(event) }
    }

    companion object {
        fun inflate(context: Context): KeyPicker {
            val layout = LayoutInflater.from(context).inflate(R.layout.dialog_key_picker, null)
            return KeyPicker(layout)
        }
    }
}
