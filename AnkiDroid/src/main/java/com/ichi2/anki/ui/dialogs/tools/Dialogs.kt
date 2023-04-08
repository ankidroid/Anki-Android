/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.ui.dialogs.tools

import android.content.Context
import android.view.View
import android.widget.CheckBox
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.ichi2.anki.R

/**
 * Configure, create and show a dialog.
 *
 *     showAlertDialog {
 *         setTitle(title)
 *         setMessage(message)
 *     }
 *
 * Note that this dialog may disappear if the activity is recreated,
 * for example, when the device is rotated, or when it goes into battery saver mode.
 * For more important dialogs, consider using [DialogFragment] instead.
 */
fun Context.showAlertDialog(block: AlertDialog.Builder.() -> Unit) {
    AlertDialog.Builder(this)
        .apply(block)
        .show()
}

/**
 * Configure, create and show a dialog.
 * The fragment must be attached to the context.
 *
 *     showAlertDialog {
 *         setTitle(title)
 *         setMessage(message)
 *     }
 *
 * Note that this dialog may disappear if the fragment is recreated,
 * for example, when the device is rotated, or when it goes into battery saver mode.
 * For more important dialogs, consider using [DialogFragment] instead.
 */
fun Fragment.showAlertDialog(block: AlertDialog.Builder.() -> Unit) {
    AlertDialog.Builder(requireContext())
        .apply(block)
        .show()
}

/**
 * Add a checkbox with a title to the dialog.
 * If the dialog has a message, it is retained.
 *
 * @param stringResId the resource ID of the string to be shown for the checkbox label
 * @param isChecked whether or not the checkbox is initially checked
 * @param onCheckedChangeListener a listener invoked when the checkbox is checked or unchecked
 */
fun AlertDialog.Builder.setCheckBoxPrompt(
    @StringRes stringResId: Int,
    isChecked: Boolean = false,
    onCheckedChangeListener: (checked: Boolean) -> Unit
) = apply {
    val checkBoxLayout = View.inflate(this.context, R.layout.alert_dialog_checkbox, null)
    val checkBox = checkBoxLayout.findViewById<CheckBox>(R.id.checkbox)

    checkBox.setText(stringResId)
    checkBox.isChecked = isChecked
    checkBox.setOnCheckedChangeListener { _, isChecked -> onCheckedChangeListener(isChecked) }

    setView(checkBoxLayout)
}
