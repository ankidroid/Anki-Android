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
import anki.scheduler.*
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.R
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.libanki.Card
import com.ichi2.libanki.Collection
import com.ichi2.libanki.utils.TimeManager.time
import timber.log.Timber
import java.lang.ref.WeakReference

/**
 * This code currently tries to fit within the constraints of the AbstractSched API. In the
 * future, it would be better for the reviewer to fetch queuedCards directly, so they only
 * need to be fetched once.
 */
open class Scheduler(col: Collection) : BaseSched(col) {
    private var activityForLeechNotification: WeakReference<Activity>? = null

    fun reset() {
        // backend automatically resets queues as operations are performed
    }

    fun resetCounts() {
        // backend automatically resets queues as operations are performed
    }

    // could be made more efficient by constructing a native Card object from
    // the backend card object, instead of doing a separate fetch
    open val card: Card?
        get() = queuedCards.cardsList.firstOrNull()?.card?.id?.let {
            col.getCard(it).apply { startTimer() }
        }

    private val queuedCards: QueuedCards
        get() = col.backend.getQueuedCards(fetchLimit = 1, intradayLearningOnly = false)

    open fun answerCard(card: Card, ease: Int) {
        val top = queuedCards.cardsList.first()
        val answer = buildAnswer(card, top.states, ease)
        col.backend.answerCard(answer)
        reps += 1
        // if this were checked in the UI, there'd be no need to store an activity here
        if (col.backend.stateIsLeech(answer.newState)) {
            activityForLeechNotification?.get()?.let { leech(card, it) }
        }
        // tests assume the card was mutated
        card.load()
    }

    fun buildAnswer(card: Card, states: SchedulingStates, ease: Int): CardAnswer {
        return cardAnswer {
            cardId = card.id
            currentState = states.current
            newState = stateFromEase(states, ease)
            rating = ratingFromEase(ease)
            answeredAtMillis = time.intTimeMS()
            millisecondsTaken = card.timeTaken()
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

    /**
     * @return Number of new, rev and lrn card to review in selected deck. Sum of elements of counts.
     */
    fun count(): Int {
        return counts(null).count()
    }

    @Suppress("unused_parameter")
    fun counts(card: Card? = null): Counts {
        return queuedCards.let {
            Counts(it.newCount, it.learningCount, it.reviewCount)
        }
    }

    /** @return Number of new card in selected decks. Recompute it if we reseted.
     */
    fun newCount(): Int {
        // We need to actually recompute the three elements, because we potentially need to deal with undid card
        // in any deck where it may be
        return counts(null).new
    }

    /** @return Number of lrn card in selected decks. Recompute it if we reseted.
     */
    fun lrnCount(): Int {
        return counts(null).lrn
    }

    /** Ignores provided card and uses top of queue */
    @Suppress("unused_parameter")
    fun countIdx(card: Card): Counts.Queue {
        return when (queuedCards.cardsList.first().queue) {
            QueuedCards.Queue.NEW -> Counts.Queue.NEW
            QueuedCards.Queue.LEARNING -> Counts.Queue.LRN
            QueuedCards.Queue.REVIEW -> Counts.Queue.REV
            QueuedCards.Queue.UNRECOGNIZED, null -> TODO("unrecognized queue")
        }
    }

    @Suppress("unused_parameter")
    fun answerButtons(card: Card): Int {
        return 4
    }

    val name = "std3"

    /** @return Number of repetitions today. Note that a repetition is the fact that the scheduler sent a card, and not the fact that the card was answered.
     * So buried, suspended, ... cards are also counted as repetitions.
     */
    var reps: Int = 0

    fun setContext(contextReference: WeakReference<Activity>) {
        this.activityForLeechNotification = contextReference
    }

    /** Only provided for legacy unit tests. */
    override fun nextIvl(card: Card, ease: Int): Long {
        val states = col.backend.getSchedulingStates(card.id)
        val state = stateFromEase(states, ease)
        return intervalForState(state)
    }

    private fun intervalForState(state: SchedulingState): Long {
        return when (state.kindCase) {
            SchedulingState.KindCase.NORMAL -> intervalForNormalState(state.normal)
            SchedulingState.KindCase.FILTERED -> intervalForFilteredState(state.filtered)
            SchedulingState.KindCase.KIND_NOT_SET, null -> TODO("invalid scheduling state")
        }
    }

    private fun intervalForNormalState(normal: SchedulingState.Normal): Long {
        return when (normal.kindCase) {
            SchedulingState.Normal.KindCase.NEW -> 0
            SchedulingState.Normal.KindCase.LEARNING -> normal.learning.scheduledSecs.toLong()
            SchedulingState.Normal.KindCase.REVIEW -> normal.review.scheduledDays.toLong() * 86400
            SchedulingState.Normal.KindCase.RELEARNING -> normal.relearning.learning.scheduledSecs.toLong()
            SchedulingState.Normal.KindCase.KIND_NOT_SET, null -> TODO("invalid normal state")
        }
    }

    private fun intervalForFilteredState(filtered: SchedulingState.Filtered): Long {
        return when (filtered.kindCase) {
            SchedulingState.Filtered.KindCase.PREVIEW -> filtered.preview.scheduledSecs.toLong()
            SchedulingState.Filtered.KindCase.RESCHEDULING -> intervalForNormalState(filtered.rescheduling.originalState)
            SchedulingState.Filtered.KindCase.KIND_NOT_SET, null -> TODO("invalid filtered state")
        }
    }
}

/**
 * Tell the user the current card has leeched and whether it was suspended. Timber if no activity.
 * @param card A card that just became a leech
 * @param activity An activity on which a message can be shown
 */
fun leech(card: Card, activity: Activity?) {
    if (activity != null) {
        val res = activity.resources
        val leechMessage: String = if (card.queue < 0) {
            res.getString(R.string.leech_suspend_notification)
        } else {
            res.getString(R.string.leech_notification)
        }
        activity.showSnackbar(leechMessage, Snackbar.LENGTH_SHORT)
    } else {
        Timber.w("LeechHook :: could not show leech snackbar as activity was null")
    }
}
