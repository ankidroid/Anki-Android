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

package com.ichi2.anki.cardviewer

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.themes.Themes

/** The "preview" controls at the bottom of the screen for [com.ichi2.anki.Previewer] and [com.ichi2.anki.CardTemplatePreviewer] */
class PreviewLayout(
    private val buttonsLayout: FrameLayout,
    @VisibleForTesting val prevCard: ImageView,
    @VisibleForTesting val nextCard: ImageView,
    private val toggleAnswerText: TextView,
) {
    fun displayAndInit(toggleAnswerHandler: View.OnClickListener) {
        buttonsLayout.visibility = View.VISIBLE
        buttonsLayout.setOnClickListener(toggleAnswerHandler)
    }

    /** Sets the answer text to "show answer" or "hide answer" */
    fun setShowingAnswer(showingAnswer: Boolean) = setToggleAnswerText(if (showingAnswer) R.string.hide_answer else R.string.show_answer)

    fun setToggleAnswerText(
        @StringRes res: Int,
    ) = toggleAnswerText.setText(res)

    fun setOnPreviousCard(listener: View.OnClickListener) = prevCard.setOnClickListener(listener)

    fun setOnNextCard(listener: View.OnClickListener) = nextCard.setOnClickListener(listener)

    fun setNextButtonEnabled(enabled: Boolean) = setEnabled(nextCard, enabled)

    fun setPrevButtonEnabled(enabled: Boolean) = setEnabled(prevCard, enabled)

    private fun setEnabled(
        button: ImageView,
        enabled: Boolean,
    ) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.38f
    }

    fun enableAnimation(context: Context) {
        val resId = Themes.getResFromAttr(context, R.attr.hardButtonRippleRef)
        buttonsLayout.setBackgroundResource(resId)
        prevCard.setBackgroundResource(R.drawable.item_background_light_selectable_borderless)
        nextCard.setBackgroundResource(R.drawable.item_background_light_selectable_borderless)
    }

    fun hideNavigationButtons() {
        prevCard.visibility = View.GONE
        nextCard.visibility = View.GONE
    }

    fun showNavigationButtons() {
        prevCard.visibility = View.VISIBLE
        nextCard.visibility = View.VISIBLE
    }

    companion object {
        fun createAndDisplay(
            activity: AnkiActivity,
            toggleAnswerHandler: View.OnClickListener,
        ): PreviewLayout {
            val buttonsLayout = activity.findViewById<FrameLayout>(R.id.preview_buttons_layout)
            val prevCard = activity.findViewById<ImageView>(R.id.preview_previous_flashcard)
            val nextCard = activity.findViewById<ImageView>(R.id.preview_next_flashcard)
            val toggleAnswerText = activity.findViewById<TextView>(R.id.preview_flip_flashcard)

            val previewLayout = PreviewLayout(buttonsLayout, prevCard, nextCard, toggleAnswerText)
            previewLayout.displayAndInit(toggleAnswerHandler)
            if (activity.animationEnabled()) {
                previewLayout.enableAnimation(activity)
            }
            return previewLayout
        }
    }
}
