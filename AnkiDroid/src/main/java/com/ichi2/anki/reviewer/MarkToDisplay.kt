// TODO: header
package com.ichi2.anki.reviewer

import android.view.View
import com.ichi2.anki.R

class MarkToDisplay private constructor(val visibility: Int, val icon: Int?) {

    companion object {
        val VISIBLE = MarkToDisplay(View.VISIBLE, R.drawable.ic_star_white_bordered_24dp)
        val HIDDEN = MarkToDisplay(View.INVISIBLE, null)

        fun forState(isCardMarked: Boolean, isOnAppBar: Boolean, isFullscreen: Boolean): MarkToDisplay {
            if (!isCardMarked) {
                return HIDDEN
            }
            if (!isOnAppBar || isFullscreen) {
                return VISIBLE
            }
            return HIDDEN
        }
    }
}
