// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.study

import android.content.Context
import android.content.Intent
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.time.TIME_HOUR
import com.ichi2.anki.common.time.TIME_MINUTE
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.pages.CongratsPage
import com.ichi2.anki.snackbar.showSnackbar
import kotlin.math.round

/** Presents the end of a review session in the activity that owns its result. */
object StudyCompletionUi {
    /** Result [Intent] extra containing the card ID from [ReviewerResult.NoMoreCards.WithPreviousCard]. */
    const val EXTRA_PREVIOUS_CARD_ID = "previous_card_id"

    /**
     * Sets the activity result and finishes the Reviewer.
     *
     * @param activity The legacy Reviewer activity or the activity hosting the Reviewer fragment.
     * @param result The [ReviewerResult] to deliver to the launcher.
     */
    fun finishReview(
        activity: FragmentActivity,
        result: ReviewerResult,
    ) {
        val previousCardId = (result as? ReviewerResult.NoMoreCards.WithPreviousCard)?.previousCardId
        val data = previousCardId?.let { Intent().putExtra(EXTRA_PREVIOUS_CARD_ID, it) }
        activity.setResult(result.resultCode, data)
        activity.finish()
    }

    fun openCongratsPage(activity: FragmentActivity) {
        activity.startActivity(CongratsPage.getIntent(activity))
    }

    fun displayFromShortcut(activity: FragmentActivity) {
        if (displayNewCongratsScreen(activity)) {
            openCongratsPage(activity)
        } else {
            activity.launchCatchingTask {
                val state = withCol { currentDeckStudyState() }
                showThemedToast(activity, finishedMessage(activity, state), false)
            }
        }
    }

    fun onReviewsCompleted(
        activity: FragmentActivity,
        state: DeckStudyState,
    ) {
        if (displayNewCongratsScreen(activity)) {
            openCongratsPage(activity)
            return
        }
        when (state.availability) {
            StudyAvailability.NoCardsAvailable -> activity.showSnackbar(finishedMessage(activity, state))
            StudyAvailability.CardsAvailable -> activity.showSnackbar(CommonString.studyoptions_no_cards_due)
        }
    }

    private fun displayNewCongratsScreen(context: Context) = context.sharedPrefs().getBoolean("new_congrats_screen", false)

    private fun finishedMessage(
        context: Context,
        state: DeckStudyState,
    ): String {
        val seconds = state.nextLearningIn?.inWholeSeconds ?: return context.getString(CommonString.studyoptions_congrats_finished)
        val (unit, amount) =
            if (seconds < TIME_MINUTE) {
                "seconds" to seconds.toDouble()
            } else if (seconds < TIME_HOUR) {
                "minutes" to seconds / TIME_MINUTE
            } else {
                "hours" to seconds / TIME_HOUR
            }
        val nextLearnDue = TR.schedulingNextLearnDue(unit, round(amount).toInt())
        return context.getString(CommonString.studyoptions_congrats_next_due_in, nextLearnDue)
    }
}
