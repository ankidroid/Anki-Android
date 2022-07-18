/*
 *  Copyright (c) 2022 Akshit Sinha <akshitsinha3@gmail.com>
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

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.fragment.app.Fragment
import com.ichi2.themes.Themes.getColorFromAttr

object ThemeUtils {
    /**
     * Convenience method to get the required color depending on the theme from the given attribute
     */
    @ColorInt
    fun Context.getThemedColor(@AttrRes attribute: Int): Int {
        return getColorFromAttr(
            this,
            attribute
        )
    }

    @ColorInt
    fun Fragment.getThemedColor(@AttrRes attribute: Int) =
        this.requireActivity().getThemedColor(attribute)
}
