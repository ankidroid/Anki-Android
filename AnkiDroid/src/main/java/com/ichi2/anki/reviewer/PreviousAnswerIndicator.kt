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

package com.ichi2.anki.reviewer

import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ichi2.anki.AbstractFlashcardViewer
import com.ichi2.anki.R
import com.ichi2.libanki.Consts
import timber.log.Timber

/**
 * A visual element in the top bar showing a number of colored dots based on the previous answer
 *
 * Informs a user of their previous answer
 *
 * This is hidden after a timer
 */
class PreviousAnswerIndicator(private val chosenAnswerText: TextView) {
    /**
     * Displays a number of dots based on the answer
     *
     * The number of dots is the ordinal of the button
     * The color of the dots is the color of the answer
     *
     * Note that the ordinal does not define the color on its own:
     * in SchedV1, button 2 could be hard or good
     *
     * @param ease The ordinal of the button answered
     * @param buttonCount The number of buttons
     */
    fun displayAnswerIndicator(ease: Int, buttonCount: Int) {
        when (ease) {
            AbstractFlashcardViewer.EASE_1 -> {
                chosenAnswerText.text = "\u2022"
                chosenAnswerText.setTextColor(getColor(R.color.material_red_500))
            }
            AbstractFlashcardViewer.EASE_2 -> {
                chosenAnswerText.text = "\u2022\u2022"
                chosenAnswerText.setTextColor(getColor(if (buttonCount == Consts.BUTTON_FOUR) R.color.material_blue_grey_600 else R.color.material_green_500))
            }
            AbstractFlashcardViewer.EASE_3 -> {
                chosenAnswerText.text = "\u2022\u2022\u2022"
                chosenAnswerText.setTextColor(getColor(if (buttonCount == Consts.BUTTON_FOUR) R.color.material_green_500 else R.color.material_light_blue_500))
            }
            AbstractFlashcardViewer.EASE_4 -> {
                chosenAnswerText.text = "\u2022\u2022\u2022\u2022"
                chosenAnswerText.setTextColor(getColor(R.color.material_light_blue_500))
            }
            else -> Timber.w("Unknown easy type %s", ease)
        }
    }

    fun clear() {
        chosenAnswerText.text = ""
    }

    fun setVisibility(visibility: Int) {
        chosenAnswerText.visibility = visibility
    }

    private fun getColor(color: Int) = ContextCompat.getColor(chosenAnswerText.context, color)
}
