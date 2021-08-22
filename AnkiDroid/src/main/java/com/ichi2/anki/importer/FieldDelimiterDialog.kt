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

package com.ichi2.anki.importer

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.ColorRes
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.internal.MDTintHelper
import com.afollestad.materialdialogs.util.DialogUtils
import com.ichi2.anki.R
import timber.log.Timber

/** A dialog accepting a single character OR `\t` to represent a tab */
object FieldDelimiterDialog {

    fun show(context: Context, currentValue: String, onPositive: (Char) -> Unit) {
        var nowValue: Char? = currentValue[0]
        val initialValue = if (currentValue != "\t") currentValue else "\\t"

        val dialog = MaterialDialog.Builder(context)
            .title(context.getString(R.string.import_delimiter_select_title))
            .positiveText(R.string.dialog_ok)
            .negativeText(R.string.dialog_cancel)
            .inputType(InputType.TYPE_CLASS_TEXT)
            .inputRange(1, 1) // or TAB
            .alwaysCallInputCallback()
            .content(context.getString(R.string.import_delimiter_select_content))
            .input("", initialValue) { dialog, callbackString ->
                // This callback is always called, and is only used for setting the return value
                // and removing validation if we find a tab
                if (callbackString.isNotEmpty()) {
                    nowValue = callbackString[0]
                }
                if (callbackString.isTab()) {
                    nowValue = '\t'
                }
                val isValidInput = isValid(callbackString)
                dialog.getActionButton(DialogAction.POSITIVE).isEnabled = isValidInput

                if (isValidInput && callbackString.length == 2) {
                    temporarilyDisableValidationError(dialog)
                }
            }
            .onPositive {
                _, _ ->
                onPositive(nowValue!!)
            }
            .show()

        // disable validation error initially if the previous value was a tab
        if (initialValue == "\\t") {
            temporarilyDisableValidationError(dialog)
        }
    }

    /** Disables the red highlighting if we have length == 2 and a valid value*/
    private fun temporarilyDisableValidationError(dialog: MaterialDialog) {
        // hacky code, just try..catch if we fail
        try {
            val inputMinMax = dialog.view.findViewById<TextView>(R.id.md_minMax)
            val input = dialog.view.findViewById<EditText>(android.R.id.input)

            val colorText: Int = getDefaultContentColor(dialog.context)
            inputMinMax.setTextColor(colorText)

            val colorWidget: Int = getDefaultWidgetColor(dialog.context)
            MDTintHelper.setTint(input, colorWidget)
        } catch (e: Exception) {
            Timber.w(e)
        }
    }

    @ColorRes
    private fun getDefaultWidgetColor(context: Context): Int {
        // This code is wrong: we should use DialogUtils.resolveColor(context, R.attr.colorAccent, materialBlue);
        // but it looks better to use a different color
        return DialogUtils.resolveColor(context, android.R.attr.colorAccent, 0)
    }

    @ColorRes
    fun getDefaultContentColor(context: Context): Int {
        val contentColorFallback = DialogUtils.resolveColor(context, android.R.attr.textColorSecondary)
        return DialogUtils.resolveColor(context, com.afollestad.materialdialogs.R.attr.md_content_color, contentColorFallback)
    }

    private fun isValid(callbackString: CharSequence) =
        callbackString.length == 1 || callbackString.isTab()

    // note: .toString() is required, we use an extension method to make this seem less suspect
    private fun CharSequence.isTab() = this.toString() == "\\t"
}
