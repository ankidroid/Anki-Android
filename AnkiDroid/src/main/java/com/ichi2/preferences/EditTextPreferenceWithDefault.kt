/****************************************************************************************
 * Copyright (c) 2023 Arthur Milchior <arthur@milchior.fr>                              *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.preferences

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.util.AttributeSet
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import com.ichi2.anki.R
import com.ichi2.anki.preferences.getDefaultValue

/**
 * Similar to EditTextPreference, but add an extra default button.
 * The default value should be set explicitly.
 */
class EditTextPreferenceWithDefault(
    context: Context,
    attrs: AttributeSet? = null
) : EditTextPreference(context, attrs), DialogFragmentProvider {

    class EditTextPreferenceWithResetButtonDialogFragmentCompat(val preference: EditTextPreferenceWithDefault) : EditTextPreferenceDialogFragmentCompat() {

        @Override
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val dialog = super.onCreateDialog(savedInstanceState) as AlertDialog
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.reset_custom_buttons)) {
                    _: DialogInterface, _: Int ->
                val defaultValue = preference.getDefaultValue() as String?
                val changeSucceeded = preference.callChangeListener(defaultValue)
                if (changeSucceeded) {
                    preference.text = defaultValue
                }
            }

            return dialog
        }
    }
    override fun makeDialogFragment() =
        EditTextPreferenceWithResetButtonDialogFragmentCompat(this)
}
