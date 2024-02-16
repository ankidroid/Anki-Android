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
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.Reviewer
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.reviewer.AnswerButtons.*
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.Collection
import com.ichi2.libanki.DeckConfig
import com.ichi2.libanki.DeckId
import com.ichi2.utils.HandlerUtils
import timber.log.Timber

/**
 * AnkiDroid contains a setting: "Automatic display answer" which displays the
 * question/answer automatically after a set period of time.
 *
 * ## Settings
 * See: [AutomaticAnswerSettings]
 *
 * ## Implementation
 *
 * If "Enabled" is disabled, then nothing happens
 * If Time to show Answer is 0, the automatic flipping code is disabled
 * If Time to show next question is 0, the automatic answering code is disabled
 *
 * When a card is displayed (Front)
 *
 * If it has neither TTS nor audio, schedule the answer to display in "Time to show answer" seconds
 * If it has audio, calculate the time for the audio to play
 *   * schedule the answer to display in "Time to show answer" + "audio runtime" seconds
 * If it has TTS, add a handler to the completion of TTS
 *   * schedule the answer to display in "Time to show answer" seconds
 *
 * Do the same for the Back, but use the "Time to show next question" setting.
 *
 * If the "next question" is shown, "Again" is pressed.
 *
 * The implementation originally had multiple bugs regarding double presses of the answer buttons.
 * This is because the user can press show the card at the same instant that the automatic code executes
 *
 * This has been worked around via the calls: [onSelectEase] and [onShowAnswer].
 * Both of which clear the task queue for the question ([onSelectEase] or vice-versa)
 */
class AutomaticAnswer(
    target: AutomaticallyAnswered,
    @VisibleForTesting val settings: AutomaticAnswerSettings
) {

    /** Whether any tasks should be executed/scheduled.
     *
     * Ensures that auto answer does not occur if the reviewer is minimised
     */
    var isDisabled: Boolean = false
        private set

    /**
     * Whether the sounds for the current side of the card have been played
     * So [scheduleAutomaticDisplayAnswer] and [scheduleAutomaticDisplayQuestion] do nothing
     * if called multiple times on the same side of the card
     */
    private var hasPlayedSounds: Boolean = false

    private val showAnswerTask = Runnable {
        if (isDisabled) {
            Timber.d("showAnswer: disabled")
            return@Runnable
        }
        target.automaticShowAnswer()
    }
    private val showQuestionTask = Runnable {
        if (isDisabled) {
            Timber.d("showQuestion: disabled")
            return@Runnable
        }
        target.automaticShowQuestion(settings.answerAction)
    }

    /**
     * Handler for the delay in auto showing question and/or answer
     * One toggle for both question and answer, could set longer delay for auto next question
     */
    @VisibleForTesting
    val timeoutHandler = HandlerUtils.newHandler()

    @VisibleForTesting
    fun delayedShowQuestion(delay: Long) {
        if (isDisabled) {
            Timber.d("showQuestion: disabled")
            return
        }
        Timber.i("Automatically showing question in %dms", delay)
        timeoutHandler.postDelayed(showQuestionTask, delay)
    }

    @VisibleForTesting
    fun delayedShowAnswer(delay: Long) {
        if (isDisabled) {
            Timber.d("showAnswer: disabled")
            return
        }
        Timber.i("Automatically showing answer in %dms", delay)
        timeoutHandler.postDelayed(showAnswerTask, delay)
    }

    private fun stopShowQuestionTask() {
        Timber.i("stop: automatically show question")
        timeoutHandler.removeCallbacks(showQuestionTask)
    }

    private fun stopShowAnswerTask() {
        Timber.i("stop: automatically show answer")
        timeoutHandler.removeCallbacks(showAnswerTask)
    }

    fun enable() {
        isDisabled = false
    }

    fun disable() {
        isDisabled = true
        stopShowAnswerTask()
        stopShowQuestionTask()
    }

    /** Stop any "Automatic show answer" tasks in order to avoid race conditions */
    fun onDisplayQuestion() {
        if (!settings.useTimer) return
        if (!settings.autoAdvanceIfShowingQuestion) return
        hasPlayedSounds = false

        stopShowAnswerTask()
    }

    /** Stop any "Automatic show question" tasks in order to avoid race conditions */
    fun onDisplayAnswer() {
        if (!settings.useTimer) return
        if (!settings.autoAdvanceIfShowingAnswer) return
        hasPlayedSounds = false

        stopShowQuestionTask()
    }

    // region TODO: These attempt to stop a race condition between a manual answer and the automated answer
    // I don't believe this is thread-safe

    /** Stops the current automatic display if the user has selected an answer */
    fun onSelectEase() {
        Timber.v("onSelectEase")
        stopShowQuestionTask()
    }

    /** Stops the current automatic display if the user has flipped the card */
    fun onShowAnswer() {
        Timber.v("onShowAnswer")
        stopShowAnswerTask()
    }

    // endregion

    /**
     * If enabled in preferences, call [AutomaticallyAnswered.automaticShowAnswer]
     * after a user-specified duration, plus an additional delay for media
     */
    fun scheduleAutomaticDisplayAnswer(additionalDelay: Long = 0) {
        if (!settings.useTimer) return
        if (!settings.autoAdvanceIfShowingQuestion) return
        if (hasPlayedSounds) return
        hasPlayedSounds = true
        delayedShowAnswer(settings.millisecondsToShowQuestionFor + additionalDelay)
    }

    /**
     * If enabled in preferences, call [AutomaticallyAnswered.automaticShowQuestion]
     * after a user-specified duration, plus an additional delay for media
     */
    fun scheduleAutomaticDisplayQuestion(additionalMediaDelay: Long = 0) {
        if (!settings.useTimer) return
        if (!settings.autoAdvanceIfShowingAnswer) return
        if (hasPlayedSounds) return
        hasPlayedSounds = true
        delayedShowQuestion(settings.millisecondsToShowAnswerFor + additionalMediaDelay)
    }

    fun isEnabled(): Boolean {
        return settings.useTimer
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun simulateCardFlip() {
        Timber.d("simulateCardFlip")
        hasPlayedSounds = false
    }

    interface AutomaticallyAnswered {
        fun automaticShowAnswer()
        fun automaticShowQuestion(action: AutomaticAnswerAction)
    }

    companion object {
        @CheckResult
        fun defaultInstance(target: AutomaticallyAnswered): AutomaticAnswer {
            return AutomaticAnswer(target, AutomaticAnswerSettings())
        }

        @CheckResult
        fun createInstance(target: AutomaticallyAnswered, preferences: SharedPreferences, col: Collection): AutomaticAnswer {
            val settings = AutomaticAnswerSettings.createInstance(preferences, col)
            return AutomaticAnswer(target, settings)
        }
    }
}

/**
 * ### Reviewing - Automatic display answer
 *
 * * Enabled
 * * Time to show answer (20s)
 * * Time to show next question (60s)
 * * Automatic action: the action to take when reviewing
 *
 * ### Deck Options - Reviews - Automatic Display Answer
 *
 * These settings also exist in an options group
 * Although they're under "reviews", it counts for all cards
 *
 * * Use General settings (using the settings above - enabled by default)
 * * Enabled, etc...
 */
class AutomaticAnswerSettings(
    val answerAction: AutomaticAnswerAction = AutomaticAnswerAction.BURY_CARD,
    @get:JvmName("useTimer") val useTimer: Boolean = false,
    private val secondsToShowQuestionFor: Int = 60,
    private val secondsToShowAnswerFor: Int = 20
) {

    val millisecondsToShowQuestionFor = secondsToShowQuestionFor * 1000L
    val millisecondsToShowAnswerFor = secondsToShowAnswerFor * 1000L

    // a wait of zero means auto-advance is disabled
    val autoAdvanceIfShowingAnswer; get() = secondsToShowAnswerFor > 0
    val autoAdvanceIfShowingQuestion; get() = secondsToShowQuestionFor > 0

    companion object {
        /**
         * Obtains the options for [AutomaticAnswer] in the deck config
         */
        @NeedsTest("ensure question setting maps to question parameter")
        fun queryOptions(
            preferences: SharedPreferences,
            col: Collection,
            selectedDid: DeckId
        ): AutomaticAnswerSettings {
            val conf = col.decks.confForDid(selectedDid)
            val action = getAction(conf)
            val useTimer = preferences.getBoolean("timeoutAnswer", false)
            val waitQuestionSecond = conf.optInt("secondsToShowQuestion", 0)
            val waitAnswerSecond = conf.optInt("secondsToShowAnswer", 0)

            return AutomaticAnswerSettings(
                answerAction = action,
                useTimer = useTimer,
                secondsToShowQuestionFor = waitQuestionSecond,
                secondsToShowAnswerFor = waitAnswerSecond
            )
        }

        fun createInstance(preferences: SharedPreferences, col: Collection): AutomaticAnswerSettings {
            return queryOptions(preferences, col, col.decks.selected())
        }

        private fun getAction(conf: DeckConfig): AutomaticAnswerAction {
            return try {
                val value: Int = conf.optInt(AutomaticAnswerAction.CONFIG_KEY)
                AutomaticAnswerAction.fromConfigValue(value)
            } catch (e: Exception) {
                AutomaticAnswerAction.BURY_CARD
            }
        }
    }
}

/**
 * Represents a value from [anki.deck_config.DeckConfig.Config.AnswerAction]
 * Executed when answering a card (showing the question).
 */
enum class AutomaticAnswerAction(private val configValue: Int) {
    /** Default: least invasive action */
    BURY_CARD(0),
    ANSWER_AGAIN(1),
    ANSWER_GOOD(2),
    ANSWER_HARD(3),
    SHOW_REMINDER(4);

    fun execute(reviewer: Reviewer) {
        val numberOfButtons = 4
        val actualAction = handleInvalidButtons(numberOfButtons)
        val action = actualAction.toCommand(numberOfButtons)
        if (action != null) {
            Timber.i("Executing %s", action)
            reviewer.executeCommand(action)
        } else {
            reviewer.showSnackbar(TR.studyingAnswerTimeElapsed())
        }
    }

    /** Handle **Hard/Easy** uf they don't appear */
    private fun handleInvalidButtons(numberOfButtons: Int): AutomaticAnswerAction {
        return when (this) {
            ANSWER_HARD -> if (AnswerButtons.canAnswerHard(numberOfButtons)) ANSWER_HARD else ANSWER_GOOD
            // Again and Good always appear. So does Bury
            else -> this
        }
    }

    /** Convert to a [ViewerCommand] */
    private fun toCommand(numberOfButtons: Int): ViewerCommand? {
        return when (this) {
            BURY_CARD -> ViewerCommand.BURY_CARD
            ANSWER_AGAIN -> AGAIN.toViewerCommand(numberOfButtons)
            ANSWER_HARD -> HARD.toViewerCommand(numberOfButtons)
            ANSWER_GOOD -> GOOD.toViewerCommand(numberOfButtons)
            else -> null
        }
    }

    companion object {
        /**
         * An integer representing the action when Automatic Answer flips a card from answer to question
         *
         * @see AutomaticAnswerAction
         */
        const val CONFIG_KEY = "answerAction"

        /** convert from [anki.deck_config.DeckConfig.Config.AnswerAction] to the enum */
        fun fromConfigValue(i: Int): AutomaticAnswerAction {
            return values().firstOrNull { it.configValue == i } ?: BURY_CARD
        }
    }
}
