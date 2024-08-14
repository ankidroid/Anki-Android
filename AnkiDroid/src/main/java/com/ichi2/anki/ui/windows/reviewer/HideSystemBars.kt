/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.ui.windows.reviewer

import android.content.Context
import androidx.annotation.StringRes
import com.ichi2.anki.R
import com.ichi2.anki.preferences.sharedPrefs

enum class HideSystemBars(@StringRes val valueRes: Int) {
    NONE(R.string.hide_system_bars_none_value),
    STATUS_BAR(R.string.hide_system_bars_status_bar_value),
    NAVIGATION_BAR(R.string.hide_system_bars_navigation_bar_value),
    ALL(R.string.hide_system_bars_all_value);

    companion object {
        fun from(context: Context): HideSystemBars {
            val prefKey = context.getString(R.string.hide_system_bars_key)
            val value = context.sharedPrefs().getString(prefKey, context.getString(NONE.valueRes))
            return entries.first { value == context.getString(it.valueRes) }
        }
    }
}
