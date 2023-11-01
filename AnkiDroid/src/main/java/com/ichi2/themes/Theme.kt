/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.themes

import androidx.annotation.StyleRes
import com.ichi2.anki.R

enum class Theme(val id: String, @StyleRes val resId: Int, val isNightMode: Boolean) {
    // IDs must correspond to the ones at @array/app_theme_values on res/values/constants.xml
    // Follow system is "0", so it starts at "1"
    LIGHT("1", R.style.Theme_Light, false),
    PLAIN("2", R.style.Theme_Light_Plain, false),
    BLACK("3", R.style.Theme_Dark_Black, true),
    DARK("4", R.style.Theme_Dark, true);

    companion object {
        val fallback: Theme
            get() = LIGHT

        fun ofId(id: String): Theme {
            return values().find { it.id == id } ?: fallback
        }
    }
}
