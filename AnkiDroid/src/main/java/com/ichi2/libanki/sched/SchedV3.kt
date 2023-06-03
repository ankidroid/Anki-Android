/***************************************************************************************
 * Copyright (c) 2022 Ankitects Pty Ltd <https://apps.ankiweb.net>                      *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.libanki.sched

import android.app.Activity
import anki.scheduler.CardAnswer
import anki.scheduler.QueuedCards
import anki.scheduler.SchedulingState
import anki.scheduler.SchedulingStates
import anki.scheduler.cardAnswer
import com.ichi2.async.CancelListener
import com.ichi2.libanki.Card
import com.ichi2.libanki.CollectionV16
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.utils.TimeManager.time
import java.lang.ref.WeakReference

/**
 * This code currently tries to fit within the constraints of the AbstractSched API. In the
 * future, it would be better for the reviewer to fetch queuedCards directly, so they only
 * need to be fetched once.
 */
class SchedV3(col: CollectionV16) : AbstractSched(col) {
    private var activityForLeechNotification: WeakReference<Activity>? = null

    override fun today() = col.backend.schedTimingToday().daysElapsed

    override fun reset() {
        // backend automatically resets queues as operations are performed
    }

    override fun resetCounts() {
        // backend automatically resets queues as operations are performed
    }

    override fun deferReset(undoneCard: Card?) {
        // backend automatically resets queues as operations are performed
    }

    // could be made more efficient by constructing a native Card object from
    // the backend card object, instead of doing a separate fetch
    override val card: Card?
        get() = queuedCards.cardsList.firstOrNull()?.card?.id?.let {
            col.getCard(it).apply { startTimer() }
        }

    private val queuedCards: QueuedCards
        get() = col.backend.getQueuedCards(fetchLimit = 1, intradayLearningOnly = false)

    override fun preloadNextCard() {
        // if this proves necessary in the future, it could be implemented by increasing
        // fetchLimit above
    }

    override fun answerCard(card: Card, ease: Int) {
        val top = queuedCards.cardsList.first()
        val answer = buildAnswer(card, top.states, ease)
        col.backend.answerCard(answer)
        reps += 1
        // if this were checked in the UI, there'd be no need to store an activity here
        if (col.backend.stateIsLeech(answer.newState)) {
            activityForLeechNotification?.get()?.let { leech(card, it) }
        }
        // tests assume the card was mutated
        card.load(col)
    }

    fun buildAnswer(card: Card, states: SchedulingStates, ease: Int): CardAnswer {
        return cardAnswer {
            cardId = card.id
            currentState = states.current
            newState = stateFromEase(states, ease)
            rating = ratingFromEase(ease)
            answeredAtMillis = time.intTimeMS()
            millisecondsTaken = card.timeTaken(col)
        }
    }

    private fun ratingFromEase(ease: Int): CardAnswer.Rating {
        return when (ease) {
            1 -> CardAnswer.Rating.AGAIN
            2 -> CardAnswer.Rating.HARD
            3 -> CardAnswer.Rating.GOOD
            4 -> CardAnswer.Rating.EASY
            else -> TODO("invalid ease: $ease")
        }
    }

    private fun stateFromEase(states: SchedulingStates, ease: Int): SchedulingState {
        return when (ease) {
            1 -> states.again
            2 -> states.hard
            3 -> states.good
            4 -> states.easy
            else -> TODO("invalid ease: $ease")
        }
    }

    override fun counts(cancelListener: CancelListener?): Counts {
        return queuedCards.let {
            Counts(it.newCount, it.learningCount, it.reviewCount)
        }
    }

    override fun counts(card: Card): Counts {
        return counts(null)
    }

    /** Ignores provided card and uses top of queue */
    override fun countIdx(card: Card): Counts.Queue {
        return when (queuedCards.cardsList.first().queue) {
            QueuedCards.Queue.NEW -> Counts.Queue.NEW
            QueuedCards.Queue.LEARNING -> Counts.Queue.LRN
            QueuedCards.Queue.REVIEW -> Counts.Queue.REV
            QueuedCards.Queue.UNRECOGNIZED, null -> TODO("unrecognized queue")
        }
    }

    override fun answerButtons(card: Card): Int {
        return 4
    }

    override val goodNewButton: Int = 3

    override fun haveBuried(did: DeckId): Boolean {
        // Backend does not support checking bury status of an arbitrary deck. This is
        // only used to decide whether to show an "unbury" option on a long press of a
        // deck.
        return false
    }

    override val name = "std3"

    override var reps: Int = 0

    override fun setContext(contextReference: WeakReference<Activity>) {
        this.activityForLeechNotification = contextReference
    }

    override fun undoReview(card: Card, wasLeech: Boolean) {
        // Only used by UndoTest
        TODO("Not yet implemented")
    }

    /** Only provided for legacy unit tests. */
    override fun nextIvl(card: Card, ease: Int): Long {
        val states = col.backend.getSchedulingStates(card.id)
        val state = stateFromEase(states, ease)
        return intervalForState(state)
    }

    private fun intervalForState(state: SchedulingState): Long {
        return when (state.valueCase) {
            SchedulingState.ValueCase.NORMAL -> intervalForNormalState(state.normal)
            SchedulingState.ValueCase.FILTERED -> intervalForFilteredState(state.filtered)
            SchedulingState.ValueCase.VALUE_NOT_SET, null -> TODO("invalid scheduling state")
        }
    }

    private fun intervalForNormalState(normal: SchedulingState.Normal): Long {
        return when (normal.valueCase) {
            SchedulingState.Normal.ValueCase.NEW -> 0
            SchedulingState.Normal.ValueCase.LEARNING -> normal.learning.scheduledSecs.toLong()
            SchedulingState.Normal.ValueCase.REVIEW -> normal.review.scheduledDays.toLong() * 86400
            SchedulingState.Normal.ValueCase.RELEARNING -> normal.relearning.learning.scheduledSecs.toLong()
            SchedulingState.Normal.ValueCase.VALUE_NOT_SET, null -> TODO("invalid normal state")
        }
    }

    private fun intervalForFilteredState(filtered: SchedulingState.Filtered): Long {
        return when (filtered.valueCase) {
            SchedulingState.Filtered.ValueCase.PREVIEW -> filtered.preview.scheduledSecs.toLong()
            SchedulingState.Filtered.ValueCase.RESCHEDULING -> intervalForNormalState(filtered.rescheduling.originalState)
            SchedulingState.Filtered.ValueCase.VALUE_NOT_SET, null -> TODO("invalid filtered state")
        }
    }
}
