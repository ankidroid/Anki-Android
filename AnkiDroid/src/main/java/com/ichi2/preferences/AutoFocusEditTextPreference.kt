/*
 *  Copyright (c) 2022 Dorrin Sotoudeh <dorrinsotoudeh123@gmail.com>
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

package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import com.ichi2.utils.AndroidUiUtils.setFocusAndOpenKeyboard
import com.ichi2.utils.moveCursorToEnd

interface AutoFocusable {
    fun autoFocusAndMoveCursorToEnd(editText: EditText) {
        setFocusAndOpenKeyboard(editText) { editText.moveCursorToEnd() }
    }
}

// used in .xml files
@Suppress("deprecation", "unused")
open class AutoFocusEditTextPreference(
    context: Context?,
    attrs: AttributeSet?,
) : android.preference.EditTextPreference(context, attrs),
    AutoFocusable {
    @Suppress("OVERRIDE_DEPRECATION") // TODO: Why?
    override fun onBindDialogView(view: View?) {
        super.onBindDialogView(view)
        autoFocusAndMoveCursorToEnd(editText)
    }
}
