// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.study

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.time.SECONDS_PER_DAY
import com.ichi2.anki.common.time.TIME_HOUR
import com.ichi2.anki.common.time.TIME_MINUTE
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.pages.CongratsPage
import com.ichi2.anki.snackbar.showSnackbar
import kotlin.math.round

/** Presents the end of a review session in the activity that owns its result. */
object StudyCompletionUi {
    fun openCongratsPage(activity: FragmentActivity) {
        activity.startActivity(CongratsPage.getIntent(activity))
    }

    private fun displayNewCongratsScreen(context: Context): Boolean = context.sharedPrefs().getBoolean("new_congrats_screen", false)

    fun displayFromShortcut(activity: FragmentActivity) {
        if (displayNewCongratsScreen(activity)) {
            openCongratsPage(activity)
        } else {
            activity.launchCatchingTask {
                val message = getDeckFinishedMessage(activity)
                showThemedToast(activity, message, false)
            }
        }
    }

    fun onReviewsCompleted(
        activity: FragmentActivity,
        cardsInDeck: Boolean,
    ) {
        if (displayNewCongratsScreen(activity)) {
            openCongratsPage(activity)
            return
        }

        // Show a message when reviewing has finished
        if (cardsInDeck) {
            activity.launchCatchingTask {
                val message = getDeckFinishedMessage(activity)
                activity.showSnackbar(message)
            }
        } else {
            activity.showSnackbar(CommonString.studyoptions_no_cards_due)
        }
    }

    // based in https://github.com/ankitects/anki/blob/9b4dd54312de8798a3f2bee07892bb3a488d1f9b/ts/routes/congrats/lib.ts#L8C17-L8C34
    private suspend fun getDeckFinishedMessage(activity: FragmentActivity): String {
        val info = withCol { sched.congratulationsInfo() }
        val secsUntilNextLearn = info.secsUntilNextLearn
        if (secsUntilNextLearn >= SECONDS_PER_DAY) {
            return activity.getString(CommonString.studyoptions_congrats_finished)
        }
        // https://github.com/ankitects/anki/blob/9b4dd54312de8798a3f2bee07892bb3a488d1f9b/ts/lib/tslib/time.ts#L22
        val (unit, amount) =
            if (secsUntilNextLearn < TIME_MINUTE) {
                "seconds" to secsUntilNextLearn.toDouble()
            } else if (secsUntilNextLearn < TIME_HOUR) {
                "minutes" to secsUntilNextLearn / TIME_MINUTE
            } else {
                "hours" to secsUntilNextLearn / TIME_HOUR
            }

        val nextLearnDue = TR.schedulingNextLearnDue(unit, round(amount).toInt())
        return activity.getString(CommonString.studyoptions_congrats_next_due_in, nextLearnDue)
    }
}
