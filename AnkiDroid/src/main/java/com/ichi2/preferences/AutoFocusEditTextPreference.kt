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

interface AutoFocusable {
    fun autoFocus(editText: EditText) {
        editText.requestFocus()
        editText.setSelection(editText.text.length)
    }
}

@Suppress("deprecation")
open class AutoFocusEditTextPreference : android.preference.EditTextPreference, AutoFocusable {
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?) : super(context)

    override fun onBindView(view: View?) {
        super.onBindView(view)
        autoFocus(editText)
    }
}
