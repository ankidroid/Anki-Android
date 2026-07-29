// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Tim Farrelly <timf34@gmail.com>

package com.ichi2.anki.blocker

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commitNow
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.AbstractFlashcardViewer
import com.ichi2.anki.R
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.ui.windows.reviewer.ReviewerFragment
import com.ichi2.anki.ui.windows.reviewer.ReviewerViewModel
import com.ichi2.anki.utils.ext.collectIn
import kotlinx.coroutines.Job

/**
 * Hosts a [ReviewerFragment] inside a gate and reports every answer to [callbacks].
 *
 * This is deliberately the only blocker file that touches the (work-in-progress)
 * new-reviewer API, so upstream changes to it have a one-file blast radius here.
 * The consumed surface is: [ReviewerFragment], [ReviewerViewModel.answerFeedbackFlow],
 * [ReviewerViewModel.finishResultFlow], [ReviewerViewModel.currentCard] and the
 * `back_button` view id.
 */
class GateReviewHost(
    private val activity: AppCompatActivity,
    private val containerId: Int,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        /** Called once per committed answer with the answered card's id. */
        fun onAnswered(
            cardId: CardId,
            rating: Rating,
        )

        /**
         * The reviewer ran out of cards. Called synchronously *before* the fragment's
         * own reaction, which is to finish the hosting activity.
         */
        fun onQueueExhausted()
    }

    private val collectionJobs = mutableListOf<Job>()

    fun install() {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                // onFragmentCreated runs before the fragment registers its own flow
                // collectors (in onViewCreated), so the gate's collectors are earlier in
                // the shared flows' subscription order: on queue exhaustion the gate
                // reacts before the fragment finishes the activity.
                override fun onFragmentCreated(
                    fm: FragmentManager,
                    fragment: Fragment,
                    savedInstanceState: Bundle?,
                ) {
                    if (fragment is ReviewerFragment) hookViewModel(fragment)
                }

                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    fragment: Fragment,
                    view: View,
                    savedInstanceState: Bundle?,
                ) {
                    if (fragment is ReviewerFragment) {
                        // The gate must not be escapable through the reviewer's own exit button
                        view.findViewById<View>(R.id.back_button)?.isVisible = false
                        // The overflow menu navigates away to the note editor, card browser
                        // and statistics. None of those grant access, but a gate should be
                        // one thing only: answer the cards.
                        view.findViewById<View>(R.id.reviewer_menu_view)?.isVisible = false
                    }
                }
            },
            false,
        )
    }

    /**
     * Attaches a fresh [ReviewerFragment], which builds a fresh queue from the
     * currently selected deck. Replacing an exhausted reviewer with a new instance
     * is how the gate switches to a practice deck mid-session.
     */
    fun attachFreshReviewer() {
        activity.supportFragmentManager.commitNow {
            replace(containerId, ReviewerFragment())
        }
    }

    private fun hookViewModel(fragment: ReviewerFragment) {
        collectionJobs.forEach(Job::cancel)
        collectionJobs.clear()
        val viewModel = ViewModelProvider(fragment)[ReviewerViewModel::class.java]
        collectionJobs +=
            viewModel.answerFeedbackFlow.collectIn(activity.lifecycleScope) { rating ->
                // Capture the deferred synchronously: the view model swaps [currentCard]
                // to the next card right after this emission, but the captured reference
                // keeps pointing at the card that was just answered.
                val answeredCard = viewModel.currentCard
                callbacks.onAnswered(answeredCard.await().id, rating)
            }
        collectionJobs +=
            viewModel.finishResultFlow.collectIn(activity.lifecycleScope) { result ->
                if (result == AbstractFlashcardViewer.RESULT_NO_MORE_CARDS) {
                    callbacks.onQueueExhausted()
                }
                // Other results (e.g. an explicit exit action) fall through to the
                // fragment finishing the activity, handled by GateActivity.finish().
            }
    }
}
