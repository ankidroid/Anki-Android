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

import android.content.Context
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.themes.Themes

/**
 * Handles mapping from an answer button to its position.
 *
 * Anki accepts the ordinal of the answer button, therefore
 * each button can represent multiple answers
 */
class AnswerButtons {
    companion object {
        @JvmStatic
        fun getBackgroundColors(ctx: AnkiActivity): IntArray {
            val backgroundIds: IntArray =
                if (ctx.animationEnabled()) {
                    intArrayOf(
                        R.attr.againButtonRippleRef,
                        R.attr.hardButtonRippleRef,
                        R.attr.goodButtonRippleRef,
                        R.attr.easyButtonRippleRef
                    )
                } else {
                    intArrayOf(
                        R.attr.againButtonRef,
                        R.attr.hardButtonRef,
                        R.attr.goodButtonRef,
                        R.attr.easyButtonRef
                    )
                }
            return Themes.getResFromAttr(ctx, backgroundIds)
        }

        @JvmStatic
        fun getTextColors(ctx: Context): IntArray {
            return Themes.getColorFromAttr(
                ctx,
                intArrayOf(
                    R.attr.againButtonTextColor,
                    R.attr.hardButtonTextColor,
                    R.attr.goodButtonTextColor,
                    R.attr.easyButtonTextColor
                )
            )
        }
    }
}
