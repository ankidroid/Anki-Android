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

package com.ichi2.anki.dialogs

import android.content.Context
import android.view.KeyEvent
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.R
import com.ichi2.anki.reviewer.Binding
import com.ichi2.ui.KeyPicker

typealias KeyCode = Int

class KeySelectionDialogBuilder(context: Context) : MaterialDialog.Builder(context) {
    private val mKeyPicker: KeyPicker = KeyPicker.inflate(context)

    /** Supplies a callback which is called each time the user presses a key
     *
     * This is **not** when the binding is submitted */
    fun onBindingChanged(listener: (Binding) -> Unit): KeySelectionDialogBuilder {
        mKeyPicker.setBindingChangedListener(listener)
        return this
    }

    /** Supplies a callback to be called when the user presses "OK" with a valid binding */
    fun onBindingSubmitted(listener: (Binding) -> Unit): KeySelectionDialogBuilder {
        onPositive { _, _ ->
            val binding = mKeyPicker.getBinding() ?: return@onPositive
            listener(binding)
        }
        return this
    }

    fun disallowModifierKeys(): KeySelectionDialogBuilder {
        mKeyPicker.setKeycodeValidation(disallowModifierKeyCodes())
        return this
    }

    init {
        positiveText(R.string.dialog_ok)
        title(R.string.binding_add_key)
        customView(mKeyPicker.rootLayout, false)
    }

    companion object {
        fun disallowModifierKeyCodes(): (KeyCode) -> Boolean {
            val modifierKeyCodes = hashSetOf(
                KeyEvent.KEYCODE_SHIFT_LEFT,
                KeyEvent.KEYCODE_SHIFT_RIGHT,
                KeyEvent.KEYCODE_CTRL_LEFT,
                KeyEvent.KEYCODE_CTRL_RIGHT,
                KeyEvent.KEYCODE_ALT_LEFT,
                KeyEvent.KEYCODE_ALT_RIGHT,
                KeyEvent.KEYCODE_META_LEFT,
                KeyEvent.KEYCODE_META_RIGHT,
                KeyEvent.KEYCODE_FUNCTION,
            )
            return { !modifierKeyCodes.contains(it) }
        }
    }
}
