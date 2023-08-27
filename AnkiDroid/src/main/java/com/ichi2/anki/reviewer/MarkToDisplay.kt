/****************************************************************************************
 * Copyright (c) 2023 Paul Tietz <tietz.paul@gmail.com>                                 *
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
package com.ichi2.anki.reviewer

import android.view.View
import com.ichi2.anki.R

enum class MarkToDisplay(val visibility: Int, val icon: Int?) {
    VISIBLE(View.VISIBLE, R.drawable.ic_star_white_bordered_24dp),
    HIDDEN(View.INVISIBLE, null);

    companion object {
        fun forState(isCardMarked: Boolean, isOnAppBar: Boolean, isFullscreen: Boolean): MarkToDisplay {
            return when {
                !isCardMarked -> HIDDEN
                isOnAppBar && !isFullscreen -> HIDDEN
                else -> VISIBLE
            }
        }
    }
}
