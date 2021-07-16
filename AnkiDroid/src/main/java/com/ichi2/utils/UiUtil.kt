/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.utils

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.Spinner

object UiUtil {
    @JvmStatic
    fun makeBold(s: String): Spannable {
        val str = SpannableStringBuilder(s)
        str.setSpan(StyleSpan(Typeface.BOLD), 0, s.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return str
    }

    @JvmStatic
    fun makeColored(s: String, color: Int): Spannable {
        val str = SpannableStringBuilder(s)
        str.setSpan(ForegroundColorSpan(color), 0, s.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return str
    }

    fun Spinner.setSelectedValue(value: Any?) {
        for (position in 0 until this.adapter.count) {
            if (this.adapter.getItem(position) != value) continue
            this.setSelection(position)
            return
        }
    }
}
