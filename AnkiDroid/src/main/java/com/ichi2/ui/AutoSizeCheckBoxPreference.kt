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
package com.ichi2.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.ichi2.utils.ViewGroupUtils.getAllChildren

// extending androidx.preference didn't work:
// java.lang.ClassCastException: com.ichi2.ui.AutoSizeCheckBoxPreference cannot be cast to android.preference.Preference

@Suppress("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
class AutoSizeCheckBoxPreference : android.preference.CheckBoxPreference {
    @Suppress("unused")
    constructor(context: Context?) : super(context) {}

    @Suppress("unused")
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {}

    @Suppress("unused")
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    @Suppress("unused")
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {}

    @Deprecated("Deprecated in Java")
    override fun onBindView(view: View) {
        makeMultiline(view)
        super.onBindView(view)
    }

    private fun makeMultiline(view: View) {
        // https://stackoverflow.com/q/4267939/13121290
        if (view is ViewGroup) {
            for (child in getAllChildren(view)) {
                makeMultiline(child)
            }
        } else if (view is TextView) {
            view.isSingleLine = false
            view.ellipsize = null
        }
    }
}
