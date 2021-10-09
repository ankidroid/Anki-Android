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

package com.ichi2.libanki.sched;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.util.Pair;

import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.async.CancelListener;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.backend.exception.BackendNotSupportedException;

import java.lang.ref.WeakReference;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import timber.log.Timber;

/**
 * In this documentation, I will call "normal use" the fact that between two successive calls to `getCard`, either the
 * result of the first `getCard` is sent to `answerCard` or the scheduler is reset (through `reset` or `defer
 * reset`). Some promise only apply in normal use.
 *
 */

public abstract class AbstractSched {
    protected Collection mCol;

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
    public abstract @Nullable Card getCard();

    /**
     * The collection saves some numbers such as counts, queues of cards to review, queues of decks potentially having some cards.
     * Reset all of this and compute from scratch. This occurs because anything else than the sequence of getCard/answerCard did occur.
     */
    // Should ideally be protected. It's public only because CollectionTask should call it when the scheduler planned this task
    public abstract void reset();

    /** Check whether we are a new day, and update if so. */
    public abstract void _updateCutoff();

    /** Ensure that the question on the potential next card can be accessed quickly.*/
    public abstract void preloadNextCard();

    /** Recompute the counts of the currently selected deck. */
    public abstract void resetCounts();
    public abstract void resetCounts(CancelListener cancelListener);

    /** Ensure that reset will be called before returning any card or count. */
    public abstract void deferReset();

    /**
     * Same as deferReset(). When `reset` is done, it then simulates that `getCard` returned undoneCard. I.e. it will
     * assume this card is currently in the reviewer and so should not be added in queue and should not be
     * counted. This is called by `undo` with the card send back to the reviewer.*/
    public abstract void deferReset(@Nullable Card undoneCard);

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
     * @param ease The button pressed by the user*/
    public abstract void answerCard(@NonNull Card card, @Consts.BUTTON_TYPE int ease);

    /**
     * @return Number of new, rev and lrn card to review in selected deck. Sum of elements of counts.*/
    public int count() {
        return counts().count();
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
    public abstract @NonNull Counts counts();

    /** @return Number of new card in selected decks. Recompute it if we reseted.
     */
    public int newCount() {
        // We need to actually recompute the three elements, because we potentially need to deal with undid card
        // in any deck where it may be
        return counts().getNew();
    }

    /** @return Number of lrn card in selected decks. Recompute it if we reseted.*/
    public int lrnCount() {
        return counts().getLrn();
    }

    /** @return Number of rev card in selected decks. Recompute it if we reseted.*/
    public int revCount() {
        return counts().getRev();
    }

    /**
     * @param card A card that should be added to the count result.
     * @return same array as counts(), apart that Card is added*/
    public abstract @NonNull Counts counts(@NonNull Card card);
    public abstract @NonNull Counts counts(@NonNull CancelListener cancelListener);

    /**
     * @param days A number of day
     * @return counts over next DAYS. Includes today.
     */
    public abstract int dueForecast(int days);

    /**
     * @param card A Card which is in a mode allowing review. I.e. neither suspended nor buried.
     * @return Which of the three numbers shown in reviewer/overview should the card be counted. 0:new, 1:rev, 2: any kind of learning.*/
    public abstract Counts.Queue countIdx(@NonNull Card card);

    /**
     * @param card A card in a queue allowing review.
     * @return Number of buttons to show in the reviewer for `card`.*/
    public abstract int answerButtons(@NonNull Card card);

    /**
     * Unbury all buried cards in all decks
     */
    public abstract void unburyCards();

    /**
     * Unbury all buried cards in selected decks
     */
    public abstract void unburyCardsForDeck();

    /**
     * @param newc Extra number of NEW cards to see today in selected deck
     * @param rev Extra number of REV cards to see today in selected deck
     */
    public abstract void extendLimits(int newc, int rev);

    /**
     * @return [deckname, did, rev, lrn, new]
     */
    public abstract @NonNull List<DeckDueTreeNode> deckDueList();

    /**
     * @param cancelListener A task that is potentially cancelled
     * @return the due tree. null if task is cancelled*/
    public abstract @Nullable List<DeckDueTreeNode> deckDueTree(CancelListener cancelListener);

    /**
     * @return the due tree. null if task is cancelled. */
    public abstract @NonNull List<DeckDueTreeNode> deckDueTree();

    /**
     * @return The tree of decks, without numbers
     */
    public abstract @NonNull List<DeckTreeNode> quickDeckDueTree();

    /** New count for a single deck.
     * @param did The deck to consider (descendants and ancestors are ignored)
     * @param lim Value bounding the result. It is supposed to be the limit taking deck configuration and today's review into account
     * @return Number of new card in deck `did` that should be seen today, at most `lim`. */
    public abstract int _newForDeck(long did, int lim);

    /**
     * @return Number of new card in current deck and its descendants. Capped at reportLimit = 99999. */
    public abstract int totalNewForCurrentDeck();

    /** @return Number of review cards in current deck.  */
    public abstract int totalRevForCurrentDeck();

    /**
     * @param ivl A number of days for the interval before fuzzying.
     * @return An interval around `ivl`, with a few less or more days for fuzzying.
     */
    // In this abstract class for testing purpose only
    public abstract @NonNull Pair<Integer, Integer> _fuzzIvlRange(int ivl);

    // In this abstract class for testing purpose only
    /** Rebuild selected dynamic deck. */
    protected abstract void rebuildDyn();

    /** Rebuild a dynamic deck.
     * @param did The deck to rebuild. 0 means current deck. */
    public abstract void rebuildDyn(long did);

    /** Remove all cards from a dynamic deck
     * @param did The deck to empty. 0 means current deck.
     */
    public abstract void emptyDyn(long did);

    /**
     *i @param cids Cards to remove from their dynamic deck (it is assumed they are in one)
     */
    // In this abstract class for testing purpose only
    public abstract void remFromDyn(List<Long> cids);
    public abstract void remFromDyn(long[] cids);

    /**
     * @param card A random card
     * @return The conf of the deck of the card.
     */
    // In this abstract class for testing purpose only
    protected abstract @NonNull DeckConfig _cardConf(@NonNull Card card);

    public abstract void _checkDay();

    /**
     * @param context Some Context to access the lang
     * @return A message to show to user when they reviewed the last card. Let them know if they can see learning card later today
     * or if they could see more card today by extending review.
     */
    public abstract @NonNull CharSequence finishedMsg(@NonNull Context context);

    /** @return whether there are any rev cards due. */
    public abstract boolean revDue();

    /** @return whether there are any new cards due.
     * */
    public abstract boolean newDue();

    /** @return whether there are cards in learning, with review due the same
     * day, in the selected decks.
     * */
    public abstract boolean hasCardsTodayAfterStudyAheadLimit();

    /**
     * @return Whether there are buried card is selected deck
     */
    public abstract boolean haveBuried();

    /**
     * Return the next interval for a card and ease as a string.
     *
     * For a given card and ease, this returns a string that shows when the card will be shown again when the
     * specific ease button (AGAIN, GOOD etc.) is touched. This uses unit symbols like “s” rather than names
     * (“second”), like Anki desktop.
     *
     * @param context The app context, used for localization
     * @param card The card being reviewed
     * @param ease The button number (easy, good etc.)
     * @return A string like “1 min” or “1.7 mo”
     */
    public abstract @NonNull String nextIvlStr(@NonNull Context context, @NonNull Card card, @Consts.BUTTON_TYPE int ease);

    /**
     * @param card A card
     * @param ease a button, between 1 and answerButtons(card)
     * @return the next interval for CARD, in seconds if ease is pressed.
     */
    // In this abstract class for testing purpose only
    protected abstract long nextIvl(@NonNull Card card, @Consts.BUTTON_TYPE int ease);

    /**
     * @param ids Id of cards to suspend
     */
    public abstract void suspendCards(@NonNull long[] ids);

    /**
     * @param ids Id of cards to unsuspend
     */
    public abstract void unsuspendCards(@NonNull long[] ids);

    /**
     * @param cids Ids of cards to bury
     */
    public abstract void buryCards(@NonNull long[] cids);

    /**
     * @param cids Ids of the cards to bury
     * @param manual Whether bury is made manually or not. Only useful for sched v2.
     */
    @VisibleForTesting
    public abstract void buryCards(@NonNull long[] cids, boolean manual);

    /**
     * Bury all cards for note until next session.
     * @param nid The id of the targeted note.
     */
    public abstract void buryNote(long nid);

    /**
     * @param ids Ids of cards to put at the end of the new queue. */
    public abstract void forgetCards(@NonNull List<Long> ids);

    /**
     * Put cards in review queue with a new interval in days (min, max).
     *
     * @param ids The list of card ids to be affected
     * @param imin the minimum interval (inclusive)
     * @param imax The maximum interval (inclusive)
     */
    public abstract void reschedCards(@NonNull List<Long> ids, int imin, int imax);

    /**
     * @param ids Ids of cards to reset for export
     */
    public abstract void resetCards(@NonNull Long[] ids);

    /**
     * @param cids Ids of card to set to new and sort
     * @param start The lowest due value for those cards
     * @param step The step between two successive due value set to those cards
     * @param shuffle Whether the list should be shuffled.
     * @param shift Whether the cards already new should be shifted to make room for cards of cids
     */
    public abstract void sortCards(@NonNull List<Long> cids, int start, int step, boolean shuffle, boolean shift);

    /**
     * Randomize the cards of did
     * @param did Id of a deck
     */
    public abstract void randomizeCards(long did);

    /**
     * Sort the cards of deck `id` by creation date of the note
     * @param did Id of a deck
     */
    public abstract void orderCards(long did);

    /**
     * Sort or randomize all cards of all decks with this deck configuration.
     * @param conf A deck configuration
     */
    public abstract void resortConf(@NonNull DeckConfig conf);

    /**
     * If the deck with id did is set to random order, then randomize their card.
     * This is used to deal which are imported
     * @param did Id of a deck
     */
    public abstract void maybeRandomizeDeck(@Nullable Long did);

    /**
     * @param did An id of a deck
     * @return Whether there is any buried cards in the deck
     */
    public abstract boolean haveBuried(long did);

    public enum UnburyType {
        ALL,
        MANUAL,
        SIBLINGS
    }


    /**
     * Unbury cards of active decks
     * @param type Which kind of cards should be unburied.
     */
    public abstract void unburyCardsForDeck(@NonNull UnburyType type);

    /**
     * Unbury all buried card of the deck
     * @param did An id of the deck
     */
    public abstract void unburyCardsForDeck(long did);

    /**
     * @return Name of the scheduler. std or std2 currently.
     */
    public abstract @NonNull String getName();

    /**
     * @return Number of days since creation of the collection.
     */
    public abstract int getToday();

    /**
     * @return Timestamp of when the day ends. Takes into account hour at which day change for anki and timezone
     */
    public abstract long getDayCutoff();

    /**
     * Increment the number of reps for today. Currently any getCard is counted,
     * even if the card is never actually reviewed.
     */
    protected abstract void incrReps();

    /**
     * Decrement the number of reps for today (useful for undo reviews)
     */
    protected abstract void decrReps();

    /** @return Number of repetitions today. Note that a repetition is the fact that the scheduler sent a card, and not the fact that the card was answered.
     * So buried, suspended, ... cards are also counted as repetitions.*/
    public abstract int getReps();

    /** @return Number of cards in the current decks, its descendants and ancestors. */
    public abstract int cardCount();

    /**
     * Return an estimate, in minutes, for how long it will take to complete all the reps in {@code counts}.
     *
     * The estimator builds rates for each queue type by looking at 10 days of history from the revlog table. For
     * efficiency, and to maintain the same rates for a review session, the rates are cached and reused until a
     * reload is forced.
     *
     * Notes:
     * - Because the revlog table does not record deck IDs, the rates cannot be reduced to a single deck and thus cover
     * the whole collection which may be inaccurate for some decks.
     * - There is no efficient way to determine how many lrn cards are generated by each new card. This estimator
     * assumes 1 card is generated as a compromise.
     * - If there is no revlog data to work with, reasonable defaults are chosen as a compromise to predicting 0 minutes.
     *
     * @param counts An array of [new, lrn, rev] counts from the scheduler's counts() method.
     * @param reload Force rebuild of estimator rates using the revlog.
     */
    public abstract int eta(Counts counts, boolean reload);

    /** Same as above and force reload.*/
    public abstract int eta(Counts counts);

    /**
     * @param contextReference An activity on which a message can be shown. Does not force the activity to remains in memory
     */
    public abstract void setContext(@Nullable WeakReference<Activity> contextReference);

    /**
     * Change the maximal number shown in counts.
     * @param reportLimit A maximal number of cards added in the queue at once.
     */
    public abstract void setReportLimit(int reportLimit);

    /**
     * Reverts answering a card.
     * 
     * @param card The data of the card before the review was made
     * @param wasLeech Whether the card was a leech before the review was made (if false, remove the leech tag)
     * */
    public abstract void undoReview(@NonNull Card card, boolean wasLeech);


    public interface LimitMethod {
        int operation(Deck g);
    }

    /** Given a deck, compute the number of cards to see today, taking its pre-computed limit into consideration.  It
     * considers either review or new cards. Used by WalkingCount to consider all subdecks and parents of a specific
     * decks.*/
    public interface CountMethod {
        int operation(long did, int lim);
    }


    /**
     * Tell the user the current card has leeched and whether it was suspended. Timber if no activity.
     * @param card A card that just became a leech
     * @param activity An activity on which a message can be shown
     */
    protected static void leech(@NonNull Card card, @Nullable Activity activity) {
        if (activity != null) {
            Resources res = activity.getResources();
            final String leechMessage;
            if (card.getQueue() < 0) {
                leechMessage = res.getString(R.string.leech_suspend_notification);
            } else {
                leechMessage = res.getString(R.string.leech_notification);
            }
            activity.runOnUiThread(() -> UIUtils.showThemedToast(activity, leechMessage, true));

        } else {
            Timber.w("LeechHook :: could not show leech toast as activity was null");
        }
    }

    /**
     * Notifies the scheduler that the provided card is being reviewed. Ensures that a different card is prefetched.
     *
     * Note that counts() does not consider current card, since number are decreased as soon as a card is sent to reviewer.
     *
     * @param card the current card in the reviewer
     */
    public abstract void setCurrentCard(@NonNull Card card);

    /** Notifies the scheduler that there is no more current card. This is the case when a card is answered, when the
     * scheduler is reset... */
    public abstract void discardCurrentCard();

    /**
     * @return The collection to which the scheduler is linked
     */
    public abstract Collection getCol();

    /** @return The button to press to enter "good" on a new card. */
    @VisibleForTesting
    public abstract @Consts.BUTTON_TYPE int getGoodNewButton();

    /**
     * @return The number of revlog in the collection
     */
    public abstract int logCount();

    public abstract int _current_timezone_offset() throws BackendNotSupportedException;

    public abstract boolean _new_timezone_enabled();
    /**
     * Save the UTC west offset at the time of creation into the DB.
     * Once stored, this activates the new timezone handling code.
     */
    public abstract void set_creation_offset() throws BackendNotSupportedException;

    public abstract void clear_creation_offset();
}
