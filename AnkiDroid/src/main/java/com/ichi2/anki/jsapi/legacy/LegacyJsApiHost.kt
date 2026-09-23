// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.jsapi.legacy

import android.content.Context
import anki.scheduler.CardAnswer.Rating
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.AnkiDroidJsAPI.CardDataForJsApi
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber

interface LegacyJsApiHost {
    val context: Context
    val scope: CoroutineScope
    val collection: Collection
    val isDisplayingAnswer: Boolean
    val isFullscreen: Boolean?
        get() = null
    val isTopbarShown: Boolean?
        get() = null
    val isInNightMode: Boolean?
        get() = null
    val supportsSpeechRecognition: Boolean
        get() = false
    val supportsSearchCardWithCallback: Boolean
        get() = false

    suspend fun currentCard(): Card

    suspend fun cardData(): CardDataForJsApi

    fun executeCommand(command: ViewerCommand): Boolean

    fun showSnackbar(
        message: String,
        duration: Int = Snackbar.LENGTH_LONG,
        configure: Snackbar.() -> Unit = {},
    )

    fun openUrl(url: String)

    fun searchCards(query: String)

    fun showTagsDialog()

    fun setHorizontalScrollbar(enabled: Boolean): Boolean = false

    fun setVerticalScrollbar(enabled: Boolean): Boolean = false

    fun showNavigationDrawer(): Boolean = false

    fun showOptionsMenu(): Boolean = false

    fun showToast(
        text: String,
        shortLength: Boolean,
    ): Boolean = false

    fun showAnswer()

    fun answerCard(rating: Rating)

    fun evaluateJavascript(script: String) {
        Timber.w("JavaScript callbacks are not supported by this host")
    }

    fun warnUnsupported(
        method: String,
        argument: String? = null,
    ) {}

    fun setDue(
        cardId: Long,
        days: Int,
    )

    fun resetProgress(cardId: Long)
}
