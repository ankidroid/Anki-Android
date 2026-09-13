// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.annotation.SuppressLint
import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.R
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.utils.show
import com.ichi2.utils.title

/** Allows selecting between [CardSide.QUESTION], [CardSide.ANSWER] or [CardSide.BOTH] */
class CardSideSelectionDialog {
    companion object {
        @SuppressLint("CheckResult")
        fun displayInstance(
            ctx: Context,
            callback: (c: CardSide) -> Unit,
        ) {
            val items =
                listOf(
                    R.string.card_side_both,
                    R.string.card_side_question,
                    R.string.card_side_answer,
                )

            AlertDialog.Builder(ctx).show {
                title(R.string.card_side_selection_title)
                setItems(items.map { ctx.getString(it) }.toTypedArray()) { _, index ->
                    when (items[index]) {
                        R.string.card_side_both -> callback(CardSide.BOTH)
                        R.string.card_side_question -> callback(CardSide.QUESTION)
                        R.string.card_side_answer -> callback(CardSide.ANSWER)
                    }
                }
            }
        }
    }
}
