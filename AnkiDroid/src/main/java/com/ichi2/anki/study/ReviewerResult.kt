// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.study

import android.app.Activity.RESULT_OK

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
    data object NoMoreCards : ReviewerResult(RESULT_NO_MORE_CARDS)

    companion object {
        /** Android activity result code for [NoMoreCards]. */
        const val RESULT_NO_MORE_CARDS = 52

        private const val RESULT_DEFAULT = 50
    }
}
