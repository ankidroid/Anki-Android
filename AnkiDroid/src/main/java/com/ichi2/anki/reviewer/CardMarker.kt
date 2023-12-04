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

package com.ichi2.anki.reviewer

import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.IntDef
import com.ichi2.anki.R

/** Handles the star and flag marker for the card viewer  */
class CardMarker(private val markView: ImageView, private val flagView: ImageView) {
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    @IntDef(FLAG_NONE, FLAG_RED, FLAG_ORANGE, FLAG_GREEN, FLAG_BLUE, FLAG_PINK, FLAG_TURQUOISE, FLAG_PURPLE)
    annotation class FlagDef

    /** Sets the mark icon on a card (the star)  */
    fun displayMark(markStatus: Boolean) {
        if (markStatus) {
            markView.visibility = View.VISIBLE
            markView.setImageResource(R.drawable.ic_star_white_bordered_24dp)
        } else {
            markView.visibility = View.INVISIBLE
        }
    }

    /** Whether the mark icon is visible on the toolbar */
    val isDisplayingMark: Boolean
        get() = markView.visibility == View.VISIBLE

    /** Sets the flag icon on the card  */
    fun displayFlag(@FlagDef flagStatus: Int) {
        when (flagStatus) {
            FLAG_RED -> setFlagView(R.drawable.ic_flag_red)
            FLAG_ORANGE -> setFlagView(R.drawable.ic_flag_orange)
            FLAG_GREEN -> setFlagView(R.drawable.ic_flag_green)
            FLAG_BLUE -> setFlagView(R.drawable.ic_flag_blue)
            FLAG_PINK -> setFlagView(R.drawable.ic_flag_pink)
            FLAG_TURQUOISE -> setFlagView(R.drawable.ic_flag_turquoise)
            FLAG_PURPLE -> setFlagView(R.drawable.ic_flag_purple)
            FLAG_NONE -> flagView.visibility = View.INVISIBLE
            else -> flagView.visibility = View.INVISIBLE
        }
    }

    private fun setFlagView(@DrawableRes drawableId: Int) {
        // set the resource before to ensure we display the correct icon.
        flagView.setImageResource(drawableId)
        flagView.visibility = View.VISIBLE
    }

    companion object {
        const val FLAG_NONE = 0
        const val FLAG_RED = 1
        const val FLAG_ORANGE = 2
        const val FLAG_GREEN = 3
        const val FLAG_BLUE = 4
        const val FLAG_PINK = 5
        const val FLAG_TURQUOISE = 6
        const val FLAG_PURPLE = 7
    }
}
