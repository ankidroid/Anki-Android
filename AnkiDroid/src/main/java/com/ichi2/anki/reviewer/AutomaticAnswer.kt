/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.reviewer

import android.content.SharedPreferences
import android.os.Handler
import androidx.annotation.VisibleForTesting
import com.ichi2.libanki.Collection

class AutomaticAnswer(
    target: AutomaticallyAnswered,
    private val settings: AutomaticAnswerSettings
) {

    private val showAnswerTask = Runnable(target::automaticShowAnswer)
    private val showQuestionTask = Runnable(target::automaticShowQuestion)

    /**
     * Handler for the delay in auto showing question and/or answer
     * One toggle for both question and answer, could set longer delay for auto next question
     */
    @Suppress("Deprecation") //  #7111: new Handler()
    @VisibleForTesting
    val timeoutHandler = Handler()

    @VisibleForTesting
    fun delayedShowQuestion(delay: Long) {
        timeoutHandler.postDelayed(showQuestionTask, delay)
    }

    @VisibleForTesting
    fun delayedShowAnswer(delay: Long) {
        timeoutHandler.postDelayed(showAnswerTask, delay)
    }

    private fun stopShowingQuestion() {
        timeoutHandler.removeCallbacks(showQuestionTask)
    }

    private fun stopShowingAnswer() {
        timeoutHandler.removeCallbacks(showAnswerTask)
    }

    fun stopAll() {
        stopShowingAnswer()
        stopShowingQuestion()
    }

    fun onDisplayQuestion() {
        if (!settings.useTimer) return
        if (!settings.autoAdvanceAnswer) return

        stopShowingAnswer()
    }

    fun onDisplayAnswer() {
        if (!settings.useTimer) return
        if (!settings.autoAdvanceQuestion) return

        stopShowingQuestion()
    }

    // region TODO: These attempt to stop a race condition between a manual answer and the automated answer
    // I don't believe this is thread-safe

    fun onSelectEase() {
        stopShowingQuestion()
    }

    fun onShowAnswer() {
        stopShowingAnswer()
    }

    // endregion

    @JvmOverloads
    fun scheduleDisplayAnswer(additionalDelay: Long = 0) {
        if (!settings.useTimer) return
        if (!settings.autoAdvanceAnswer) return
        delayedShowAnswer(settings.answerDelayMilliseconds + additionalDelay)
    }

    @JvmOverloads
    fun scheduleDisplayQuestion(additionalDelay: Long = 0) {
        if (!settings.useTimer) return
        if (!settings.autoAdvanceQuestion) return
        delayedShowQuestion(settings.questionDelayMilliseconds + additionalDelay)
    }

    fun isEnabled(): Boolean {
        return settings.useTimer
    }

    interface AutomaticallyAnswered {
        fun automaticShowAnswer()
        fun automaticShowQuestion()
    }

    companion object {
        @JvmStatic
        fun defaultInstance(target: AutomaticallyAnswered): AutomaticAnswer {
            return AutomaticAnswer(target, AutomaticAnswerSettings())
        }

        @JvmStatic
        fun createInstance(target: AutomaticallyAnswered, preferences: SharedPreferences, col: Collection): AutomaticAnswer {
            val settings = AutomaticAnswerSettings.createInstance(preferences, col)
            return AutomaticAnswer(target, settings)
        }
    }
}

class AutomaticAnswerSettings(
    @get:JvmName("useTimer") val useTimer: Boolean = false,
    private val questionDelaySeconds: Int = 60,
    private val answerDelaySeconds: Int = 20
) {

    val questionDelayMilliseconds = questionDelaySeconds * 1000L
    val answerDelayMilliseconds = answerDelaySeconds * 1000L

    // a wait of zero means auto-advance is disabled
    val autoAdvanceAnswer; get() = answerDelaySeconds > 0
    val autoAdvanceQuestion; get() = questionDelaySeconds > 0

    companion object {
        @JvmStatic
        fun queryDeckSpecificOptions(
            col: Collection,
            selectedDid: Long
        ): AutomaticAnswerSettings? {
            // Dynamic don't have review options; attempt to get deck-specific auto-advance options
            // but be prepared to go with all default if it's a dynamic deck
            if (col.decks.isDyn(selectedDid)) {
                return null
            }

            val revOptions = col.decks.confForDid(selectedDid).getJSONObject("rev")

            if (revOptions.optBoolean("useGeneralTimeoutSettings", true)) {
                // we want to use the general settings, no need for per-deck settings
                return null
            }

            val useTimer = revOptions.optBoolean("timeoutAnswer", false)
            val waitQuestionSecond = revOptions.optInt("timeoutQuestionSeconds", 60)
            val waitAnswerSecond = revOptions.optInt("timeoutAnswerSeconds", 20)
            return AutomaticAnswerSettings(useTimer, waitQuestionSecond, waitAnswerSecond)
        }

        @JvmStatic
        fun queryFromPreferences(preferences: SharedPreferences): AutomaticAnswerSettings {
            val prefUseTimer: Boolean = preferences.getBoolean("timeoutAnswer", false)
            val prefWaitQuestionSecond: Int = preferences.getInt("timeoutQuestionSeconds", 60)
            val prefWaitAnswerSecond: Int = preferences.getInt("timeoutAnswerSeconds", 20)
            return AutomaticAnswerSettings(prefUseTimer, prefWaitQuestionSecond, prefWaitAnswerSecond)
        }

        @JvmStatic
        fun createInstance(preferences: SharedPreferences, col: Collection): AutomaticAnswerSettings {
            // deck specific options take precedence over general (preference-based) options
            return queryDeckSpecificOptions(col, col.decks.selected()) ?: queryFromPreferences(preferences)
        }
    }
}
