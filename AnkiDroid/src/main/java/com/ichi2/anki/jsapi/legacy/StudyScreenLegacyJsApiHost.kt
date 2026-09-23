// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.jsapi.legacy

import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import anki.scheduler.CardAnswer.Rating
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.AnkiDroidJsAPI.CardDataForJsApi
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.common.destinations.BrowserDestination
import com.ichi2.anki.common.destinations.navigate
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.sched.Counts
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.servicelayer.rescheduleCards
import com.ichi2.anki.servicelayer.resetCards
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.windows.reviewer.ReviewerFragment
import com.ichi2.anki.ui.windows.reviewer.ReviewerViewModel
import com.ichi2.anki.utils.openUrl

class StudyScreenLegacyJsApiHost(
    private val fragment: ReviewerFragment,
    private val viewModel: ReviewerViewModel = fragment.viewModel,
) : LegacyJsApiHost {
    override val context get() = fragment.requireContext()
    override val scope get() = fragment.viewLifecycleOwner.lifecycleScope
    override val collection get() = CollectionManager.getColUnsafe()
    override val isDisplayingAnswer get() = viewModel.showingAnswer.value

    override suspend fun currentCard() = viewModel.currentCard.await()

    override suspend fun cardData() =
        CardDataForJsApi().apply {
            val counts = viewModel.countsFlow.value
            newCardCount = counts.new.toInt()
            lrnCardCount = counts.learn.toInt()
            revCardCount = counts.review.toInt()
            eta = withCol { sched.eta(Counts(newCardCount, lrnCardCount, revCardCount), false) }
            viewModel.answerButtonsNextTimeFlow.value?.let {
                nextTime1 = it.again
                nextTime2 = it.hard
                nextTime3 = it.good
                nextTime4 = it.easy
            }
        }

    override fun executeCommand(command: ViewerCommand): Boolean {
        val action =
            when (command) {
                ViewerCommand.MARK -> ViewerAction.MARK
                ViewerCommand.BURY_CARD -> ViewerAction.BURY_CARD
                ViewerCommand.BURY_NOTE -> ViewerAction.BURY_NOTE
                ViewerCommand.SUSPEND_CARD -> ViewerAction.SUSPEND_CARD
                ViewerCommand.SUSPEND_NOTE -> ViewerAction.SUSPEND_NOTE
                ViewerCommand.UNSET_FLAG -> ViewerAction.UNSET_FLAG
                ViewerCommand.TOGGLE_FLAG_RED -> ViewerAction.TOGGLE_FLAG_RED
                ViewerCommand.TOGGLE_FLAG_ORANGE -> ViewerAction.TOGGLE_FLAG_ORANGE
                ViewerCommand.TOGGLE_FLAG_GREEN -> ViewerAction.TOGGLE_FLAG_GREEN
                ViewerCommand.TOGGLE_FLAG_BLUE -> ViewerAction.TOGGLE_FLAG_BLUE
                ViewerCommand.TOGGLE_FLAG_PINK -> ViewerAction.TOGGLE_FLAG_PINK
                ViewerCommand.TOGGLE_FLAG_TURQUOISE -> ViewerAction.TOGGLE_FLAG_TURQUOISE
                ViewerCommand.TOGGLE_FLAG_PURPLE -> ViewerAction.TOGGLE_FLAG_PURPLE
                else -> return false
            }
        viewModel.executeAction(action)
        return true
    }

    override fun showSnackbar(
        message: String,
        duration: Int,
        configure: Snackbar.() -> Unit,
    ) {
        fragment.showSnackbar(message, duration, configure)
    }

    override fun openUrl(url: String) {
        fragment.openUrl(url.toUri())
    }

    override fun searchCards(query: String) = with(fragment) { navigate(BrowserDestination.Search(query, allDecks = false)) }

    override fun showTagsDialog() = viewModel.executeAction(ViewerAction.TAG)

    override fun showAnswer() {
        if (!isDisplayingAnswer) viewModel.onShowAnswer()
    }

    override fun answerCard(rating: Rating) {
        if (isDisplayingAnswer) viewModel.answerCard(rating) else viewModel.onShowAnswer()
    }

    override fun setDue(
        cardId: Long,
        days: Int,
    ) {
        fragment.activity?.apply {
            launchCatchingTask { rescheduleCards(listOf(cardId), days) }
        }
    }

    override fun resetProgress(cardId: Long) {
        fragment.activity?.apply {
            launchCatchingTask { resetCards(listOf(cardId)) }
        }
    }
}

fun LegacyJsApiBridge.attach(fragment: ReviewerFragment) {
    attach(fragment.viewLifecycleOwner, StudyScreenLegacyJsApiHost(fragment))
}
