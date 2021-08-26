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
package com.ichi2.anki.dialogs

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.R
import com.ichi2.anki.reviewer.CardSide

/** Allows selecting between [CardSide.QUESTION], [CardSide.ANSWER] or [CardSide.BOTH0] */
class CardSideSelectionDialog {

    companion object {
        fun displayInstance(ctx: Context, callback: (c: CardSide) -> Unit) {
            val items = listOf(
                R.string.card_side_both,
                R.string.card_side_question,
                R.string.card_side_answer
            )

            MaterialDialog.Builder(ctx)
                .title(R.string.card_side_selection_title)
                .items(items.map { ctx.getString(it) })
                .itemsCallback { _, _, which, _ ->
                    when (items[which]) {
                        R.string.card_side_both -> callback(CardSide.BOTH)
                        R.string.card_side_question -> callback(CardSide.QUESTION)
                        R.string.card_side_answer -> callback(CardSide.ANSWER)
                    }
                }
                .show()
        }
    }
}
