// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.study

import android.app.Activity.RESULT_OK
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.utils.ext.getCardOrNull

/**
 * The outcome of closing either Reviewer.
 *
 * @property resultCode The code delivered to the launcher in [androidx.activity.result.ActivityResult.resultCode].
 */
sealed class ReviewerResult(
    val resultCode: Int,
) {
    /** The Reviewer's Exit command or the legacy Reviewer's Back action. */
    data object Exit : ReviewerResult(RESULT_DEFAULT)

    /** The legacy Reviewer's Up action. */
    data object NavigateUp : ReviewerResult(RESULT_OK)

    /** The scheduler has no more cards available for this session. */
    sealed class NoMoreCards : ReviewerResult(RESULT_NO_MORE_CARDS) {
        /**
         * The queue is empty and no card info is available. This happens when no card was shown
         * or the last shown card was deleted.
         */
        data object WithoutPreviousCard : NoMoreCards()

        /**
         * Previous card info on Congrats opens the final card shown in the session. During
         * review, the same action opens the card shown before the current card.
         * If the scheduler repeats a card after grading it, that previous presentation counts.
         */
        data class WithPreviousCard(
            val previousCardId: CardId,
        ) : NoMoreCards()

        companion object {
            suspend fun fromLastShownCard(lastShownCardId: CardId?): NoMoreCards {
                val cardId = lastShownCardId ?: return WithoutPreviousCard
                val cardExists = withCol { getCardOrNull(cardId) != null }
                return if (cardExists) WithPreviousCard(cardId) else WithoutPreviousCard
            }
        }
    }

    companion object {
        /** Android activity result code for [NoMoreCards]. */
        const val RESULT_NO_MORE_CARDS = 52

        private const val RESULT_DEFAULT = 50
    }
}
