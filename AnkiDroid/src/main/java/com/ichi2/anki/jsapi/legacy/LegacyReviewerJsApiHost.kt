// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.jsapi.legacy

import androidx.lifecycle.lifecycleScope
import anki.scheduler.CardAnswer.Rating
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.AbstractFlashcardViewer
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.common.destinations.BrowserDestination
import com.ichi2.anki.common.destinations.navigate
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.servicelayer.rescheduleCards
import com.ichi2.anki.servicelayer.resetCards
import com.ichi2.anki.snackbar.showSnackbar

class LegacyReviewerJsApiHost(
    private val activity: AbstractFlashcardViewer,
) : LegacyJsApiHost {
    override val context get() = activity
    override val scope get() = activity.lifecycleScope
    override val collection get() = activity.getColUnsafe
    override val isDisplayingAnswer get() = activity.isDisplayingAnswer
    override val isFullscreen get() = activity.isFullscreen
    override val isTopbarShown get() = activity.prefShowTopbar
    override val isInNightMode get() = activity.isInNightMode
    override val supportsSpeechRecognition = true
    override val supportsSearchCardWithCallback = true

    override suspend fun currentCard() = activity.currentCard!!

    override suspend fun cardData() = activity.getCardDataForJsApi()

    override fun executeCommand(command: ViewerCommand) = activity.executeCommand(command)

    override fun showSnackbar(
        message: String,
        duration: Int,
        configure: Snackbar.() -> Unit,
    ) {
        activity.showSnackbar(message, duration, configure)
    }

    override fun openUrl(url: String) = activity.openUrl(url)

    override fun searchCards(query: String) = with(activity) { navigate(BrowserDestination.Search(query, allDecks = false)) }

    override fun showTagsDialog() = activity.showTagsDialog()

    override fun setHorizontalScrollbar(enabled: Boolean): Boolean {
        activity.webView!!.isHorizontalScrollBarEnabled = enabled
        return true
    }

    override fun setVerticalScrollbar(enabled: Boolean): Boolean {
        activity.webView!!.isVerticalScrollBarEnabled = enabled
        return true
    }

    override fun showNavigationDrawer(): Boolean {
        activity.onNavigationPressed()
        return true
    }

    override fun showOptionsMenu(): Boolean {
        activity.openOptionsMenu()
        return true
    }

    override fun showToast(
        text: String,
        shortLength: Boolean,
    ): Boolean {
        showThemedToast(activity, activity.decodeUrl(text), shortLength)
        return true
    }

    override fun showAnswer() = activity.displayCardAnswer()

    override fun answerCard(rating: Rating) = activity.flipOrAnswerCard(rating)

    override fun evaluateJavascript(script: String) = activity.webView!!.evaluateJavascript(script, null)

    override fun setDue(
        cardId: Long,
        days: Int,
    ) {
        activity.launchCatchingTask { activity.rescheduleCards(listOf(cardId), days) }
    }

    override fun resetProgress(cardId: Long) {
        activity.launchCatchingTask { activity.resetCards(listOf(cardId)) }
    }
}
