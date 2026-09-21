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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.reviewer.AutomaticAnswerAction.Companion.answerAction
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class AutomaticAnswerAndroidTest : RobolectricTest() {
    @Test
    fun default_is_bury() {
        assertThat("no value", createInstance().settings.answerAction, equalTo(AutomaticAnswerAction.BURY_CARD))
        assertThat("default", AutomaticAnswer.defaultInstance(mock()).settings.answerAction, equalTo(AutomaticAnswerAction.BURY_CARD))
    }

    @Test
    fun preference_sets_action() {
        setActionType(AutomaticAnswerAction.ANSWER_AGAIN)
        assertThat(createInstance().settings.answerAction, equalTo(AutomaticAnswerAction.ANSWER_AGAIN))
        // reset the value
        resetPrefs()
        assertThat("default", createInstance().settings.answerAction, equalTo(AutomaticAnswerAction.BURY_CARD))
    }

    @Test
    fun `milliseconds are handled`() {
        setShowQuestionDuration(1.5)
        assertThat(createInstance().settings.millisecondsToShowQuestionFor, equalTo(1500))
    }

    @Test
    fun `queryOptions maps question and answer settings correctly`() {
        setDeckDurations(questionDuration = 3.5, answerDuration = 7.0)
        setActionType(AutomaticAnswerAction.ANSWER_GOOD)
        val settings = AutomaticAnswerSettings.queryOptions(col, col.decks.selected())

        assertThat(settings.millisecondsToShowQuestionFor, equalTo(3500L))
        assertThat(settings.millisecondsToShowAnswerFor, equalTo(7000L))
        assertThat(settings.answerAction, equalTo(AutomaticAnswerAction.ANSWER_GOOD))
        assertThat(settings.autoAdvanceIfShowingQuestion, equalTo(true))
        assertThat(settings.autoAdvanceIfShowingAnswer, equalTo(true))
        assertThat(settings.isUsable, equalTo(true))
    }

    @Test
    fun `queryOptions handles zero durations`() {
        setDeckDurations(questionDuration = 0.0, answerDuration = 0.0)
        val settings = AutomaticAnswerSettings.queryOptions(col, col.decks.selected())

        assertThat(settings.millisecondsToShowQuestionFor, equalTo(0L))
        assertThat(settings.millisecondsToShowAnswerFor, equalTo(0L))
        assertThat(settings.autoAdvanceIfShowingQuestion, equalTo(false))
        assertThat(settings.autoAdvanceIfShowingAnswer, equalTo(false))
        assertThat(settings.isUsable, equalTo(false))
    }

    @Test
    fun `queryOptions handles question only and answer only durations`() {
        setDeckDurations(questionDuration = 5.0, answerDuration = 0.0)
        var settings = AutomaticAnswerSettings.queryOptions(col, col.decks.selected())
        assertThat(settings.millisecondsToShowQuestionFor, equalTo(5000L))
        assertThat(settings.millisecondsToShowAnswerFor, equalTo(0L))
        assertThat(settings.autoAdvanceIfShowingQuestion, equalTo(true))
        assertThat(settings.autoAdvanceIfShowingAnswer, equalTo(false))
        assertThat(settings.isUsable, equalTo(true))

        setDeckDurations(questionDuration = 0.0, answerDuration = 6.0)
        settings = AutomaticAnswerSettings.queryOptions(col, col.decks.selected())
        assertThat(settings.millisecondsToShowQuestionFor, equalTo(0L))
        assertThat(settings.millisecondsToShowAnswerFor, equalTo(6000L))
        assertThat(settings.autoAdvanceIfShowingQuestion, equalTo(false))
        assertThat(settings.autoAdvanceIfShowingAnswer, equalTo(true))
        assertThat(settings.isUsable, equalTo(true))
    }

    @Test
    fun `queryOptions respects selected deck id`() {
        val otherDid = col.decks.addNormalDeckWithName("Other Deck").id
        val newConfId = col.decks.addConfigReturningId("Other Config")
        col.decks.setConfigIdForDeckDict(col.decks.getLegacy(otherDid)!!, newConfId)

        setDeckDurations(deckId = otherDid, questionDuration = 4.0, answerDuration = 8.0)
        setDeckDurations(deckId = col.decks.selected(), questionDuration = 1.0, answerDuration = 2.0)

        val settings = AutomaticAnswerSettings.queryOptions(col, otherDid)
        assertThat(settings.millisecondsToShowQuestionFor, equalTo(4000L))
        assertThat(settings.millisecondsToShowAnswerFor, equalTo(8000L))

        val selectedSettings = AutomaticAnswerSettings.queryOptions(col, col.decks.selected())
        assertThat(selectedSettings.millisecondsToShowQuestionFor, equalTo(1000L))
        assertThat(selectedSettings.millisecondsToShowAnswerFor, equalTo(2000L))
    }

    private fun resetPrefs() {
        val conf =
            col.decks.configDictForDeckId(col.decks.selected()).apply {
                removeAnswerAction()
            }
        col.decks.save(conf)
    }

    @Suppress("SameParameterValue")
    private fun setActionType(value: AutomaticAnswerAction) {
        val conf =
            col.decks.configDictForDeckId(col.decks.selected()).apply {
                answerAction = value
            }
        col.decks.save(conf)
    }

    @Suppress("SameParameterValue")
    private fun setShowQuestionDuration(value: Double) {
        val conf =
            col.decks.configDictForDeckId(col.decks.selected()).apply {
                secondsToShowQuestion = value
            }
        col.decks.save(conf)
    }

    private fun setDeckDurations(
        deckId: DeckId = col.decks.selected(),
        questionDuration: Double = 0.0,
        answerDuration: Double = 0.0,
    ) {
        val conf =
            col.decks.configDictForDeckId(deckId).apply {
                secondsToShowQuestion = questionDuration
                secondsToShowAnswer = answerDuration
            }
        col.decks.save(conf)
    }

    private fun createInstance() = AutomaticAnswer.createInstance(mock(), super.col)
}
