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
package com.ichi2.anki.dialogs

import android.content.res.Resources
import androidx.annotation.CheckResult
import com.ichi2.anki.R
import com.ichi2.libanki.Card
import com.ichi2.libanki.Consts
import java.util.function.Consumer

// a memory leak was caused when this was a singleton object.
class RescheduleDialog : IntegerDialog() {

    companion object {
        @CheckResult
        fun rescheduleSingleCard(
            resources: Resources,
            currentCard: Card,
            consumer: Consumer<Int>?
        ): RescheduleDialog {
            val rescheduleDialog = RescheduleDialog()
            val content = getContentString(resources, currentCard)
            rescheduleDialog.setArgs(
                title = resources.getQuantityString(
                    R.plurals.reschedule_cards_dialog_title_new,
                    1,
                    1
                ),
                prompt = resources.getString(R.string.reschedule_card_dialog_message),
                digits = 4,
                content = content
            )
            if (consumer != null) {
                rescheduleDialog.setCallbackRunnable(consumer)
            }
            return rescheduleDialog
        }

        @CheckResult
        fun rescheduleMultipleCards(
            resources: Resources,
            consumer: Consumer<Int>?,
            cardCount: Int
        ): RescheduleDialog {
            val rescheduleDialog = RescheduleDialog()
            rescheduleDialog.setArgs(
                resources.getQuantityString(
                    R.plurals.reschedule_cards_dialog_title_new,
                    cardCount,
                    cardCount
                ),
                resources.getString(R.string.reschedule_card_dialog_message),
                4
            )
            if (consumer != null) {
                rescheduleDialog.setCallbackRunnable(consumer)
            }
            return rescheduleDialog
        }

        private fun getContentString(resources: Resources, currentCard: Card): String? {
            if (currentCard.isNew) {
                return resources.getString(R.string.reschedule_card_dialog_new_card_warning)
            }

            // #5595 - Help a user reschedule cards by showing them the current interval.
            // DEFECT: We should be able to calculate this for all card types - not yet performed for non-review or dynamic cards
            if (!currentCard.isReview) {
                return null
            }
            val message = resources.getString(
                R.string.reschedule_card_dialog_warning_ease_reset,
                Consts.STARTING_FACTOR / 10
            )
            return if (currentCard.isInDynamicDeck) {
                message
            } else {
                """
     $message
     
     ${
                resources.getQuantityString(
                    R.plurals.reschedule_card_dialog_interval,
                    currentCard.ivl,
                    currentCard.ivl
                )
                }
                """.trimIndent()
            }
        }
    }
}
