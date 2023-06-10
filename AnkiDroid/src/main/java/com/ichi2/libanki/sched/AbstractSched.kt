/*
 *  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
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
package com.ichi2.libanki.sched

import android.app.Activity
import androidx.annotation.VisibleForTesting
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.R
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.async.CancelListener
import com.ichi2.libanki.Card
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts.BUTTON_TYPE
import com.ichi2.libanki.DeckId
import timber.log.Timber
import java.lang.ref.WeakReference

/**
 * In this documentation, I will call "normal use" the fact that between two successive calls to `getCard`, either the
 * result of the first `getCard` is sent to `answerCard` or the scheduler is reset (through `reset` or `defer
 * reset`). Some promise only apply in normal use.
 *
 */
abstract class AbstractSched(col: Collection) : BaseSched(col) {
    /**
     * Pop the next card from the queue. null if finished.
     *
     * We always guarantee that it is a card that should be reviewed today.
     *
     * In normal use we guarantee that this is the card promised by the scheduler configuration. I.e. card in learning
     * if any is due, otherwise cards in reviewing/new queue according to the preferred order from the configuration,
     * otherwise cards in learning from previous day.
     *
     * When normal use is not followed, a small sequence of cards is returned infinitely many time.
     *
     * @return the next card from the queue. null if finished.
     */
    abstract val card: Card?

    /**
     * The collection saves some numbers such as counts, queues of cards to review, queues of decks potentially having some cards.
     * Reset all of this and compute from scratch. This occurs because anything else than the sequence of getCard/answerCard did occur.
     */
    // Should ideally be protected. It's public only because CollectionTask should call it when the scheduler planned this task
    abstract fun reset()

    /** Ensure that the question on the potential next card can be accessed quickly. */
    abstract fun preloadNextCard()

    /** Recompute the counts of the currently selected deck.  */
    abstract fun resetCounts(cancelListener: CancelListener? = null, checkCutoff: Boolean = true)

    /**
     * Ensure that reset will be called before returning any card or count.
     *
     * When `reset` is done, it then simulates that `getCard` returned [undoneCard] if it's not null. I.e. it will
     * assume this card is currently in the reviewer and so should not be added in queue and should not be
     * counted. This is called by `undo` with the card send back to the reviewer. */
    abstract fun deferReset(undoneCard: Card? = null)

    /**
     * Does all actions required to answer the card. That is:
     * Change its interval, due value, queue, mod time, usn, number of step left (if in learning)
     * Put it in learning if required
     * Log the review.
     * Remove from filtered if required.
     * Remove the siblings for the queue for same day spacing
     * Bury siblings if required by the options
     *
     * @param card The card answered
     * @param ease The button pressed by the user
     */
    abstract fun answerCard(card: Card, @BUTTON_TYPE ease: Int)

    /**
     * @return Number of new, rev and lrn card to review in selected deck. Sum of elements of counts.
     */
    fun count(): Int {
        return counts().count()
    }

    /**
     *
     * @return The number of cards new, rev, and lrn in the selected deck.
     * In sched V1, the number of remaining steps for cards in learning is returned
     * In sched V2, the number of cards in learning is returned.
     * The card currently in the reviewer is not counted.
     *
     * Technically, it counts the number of cards to review in current deck last time `reset` was called
     * Minus the number of cards returned by getCard.
     */
    // TODO: consider counting the card currently in the reviewer, this would simplify the code greatly
    // We almost never want to consider the card in the reviewer differently, and a lot of code is added to correct this.
    abstract fun counts(cancelListener: CancelListener? = null): Counts

    /**
     * @param card A card that should be added to the count result.
     * @return same array as counts(), apart that Card is added
     */
    abstract fun counts(card: Card): Counts

    /** @return Number of new card in selected decks. Recompute it if we reseted.
     */
    fun newCount(): Int {
        // We need to actually recompute the three elements, because we potentially need to deal with undid card
        // in any deck where it may be
        return counts().new
    }

    /** @return Number of lrn card in selected decks. Recompute it if we reseted.
     */
    fun lrnCount(): Int {
        return counts().lrn
    }

    /** @return Number of rev card in selected decks. Recompute it if we reseted.
     */
    fun revCount(): Int {
        return counts().rev
    }

    /**
     * @param card A Card which is in a mode allowing review. I.e. neither suspended nor buried.
     * @return Which of the three numbers shown in reviewer/overview should the card be counted. 0:new, 1:rev, 2: any kind of learning.
     */
    abstract fun countIdx(card: Card): Counts.Queue

    /**
     * @param card A card in a queue allowing review.
     * @return Number of buttons to show in the reviewer for `card`.
     */
    abstract fun answerButtons(card: Card): Int

    /**
     * specific-deck case not supported by the backend; UI only uses this
     * for long-press on deck
     * @param did An id of a deck
     * @return Whether there is any buried cards in the deck
     */
    abstract fun haveBuried(did: DeckId): Boolean

    /**
     * @return Name of the scheduler. std or std2 currently.
     */
    abstract val name: String

    /** @return Number of repetitions today. Note that a repetition is the fact that the scheduler sent a card, and not the fact that the card was answered.
     * So buried, suspended, ... cards are also counted as repetitions.
     */
    abstract val reps: Int

    /**
     * @param contextReference An activity on which a message can be shown. Does not force the activity to remains in memory
     */
    abstract fun setContext(contextReference: WeakReference<Activity>)

    /**
     * Reverts answering a card.
     *
     * @param card The data of the card before the review was made
     * @param wasLeech Whether the card was a leech before the review was made (if false, remove the leech tag)
     */
    abstract fun undoReview(card: Card, wasLeech: Boolean)

    /** @return The button to press to enter "good" on a new card.
     */
    @get:BUTTON_TYPE
    @get:VisibleForTesting
    abstract val goodNewButton: Int

    companion object {
        /**
         * Tell the user the current card has leeched and whether it was suspended. Timber if no activity.
         * @param card A card that just became a leech
         * @param activity An activity on which a message can be shown
         */
        @JvmStatic // Using protected members which are not @JvmStatic in the superclass companion is unsupported yet
        protected fun leech(card: Card, activity: Activity?) {
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
    }
}
