package com.ichi2.libanki.sched;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.util.Pair;
import android.widget.Toast;


import com.ichi2.anki.R;
import com.ichi2.async.CollectionTask;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.utils.Time;

import java.lang.ref.WeakReference;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import timber.log.Timber;


public abstract class AbstractSched {
    protected Collection mCol;

    /**
     * Return a card due today in current deck.
     * Between two successive calls, either the last card returned by getCard should be sent to `answerCard`
     * or the scheduler should be reset (i.e. if there is a change of deck, an action undone, a card buried/rescheduled/suspended)
     */
    public abstract Card getCard();

    /**
     * The collection saves some numbers such as counts, queues of cards to review, queues of decks potentially having some cards.
     * Reset all of this and compute from scratch. This occurs because anything else than the sequence of getCard/answerCard did occur.
     */
    public abstract void reset();

    /** Ensure that the question on the potential next card can be accessed quickly.
     */
    public abstract void preloadNextCard();
    public abstract void resetCounts();
    public abstract void resetQueues();
    /** Ensures that reset is executed before the next card is selected */
    public abstract void deferReset();
    /**
     * @param undoneCard a card undone, send back to the reviewer.*/
    public abstract void deferReset(Card undoneCard);

    /**
     * Does all actions required to answer the card. That is:
     * Change its interval, due value, queue, mod time, usn, number of step left (if in learning)
     * Put it in learning if required
     * Log the review.
     * Remove from filtered if required.
     * Remove the siblings for the queue for same day spacing
     * Bury siblings if required by the options
     *  */
    public abstract void answerCard(Card card, @Consts.BUTTON_TYPE int ease);

    /**
     * The number of cards new, rev, and lrn in the selected deck.
     * In sched V1, the number of remaining steps for cards in learning is returned
     * In sched V2, the number of cards in learning is returned.
     * The card currently in the reviewer is not counted.
     *
     * Technically, it counts the number of cards to review in current deck last time `reset` was called
     * Minus the number of cards returned by getCard.
     */
    // TODO: consider counting the card currently in the reviewer, this would simplify the code greatly
    // We almost never want to consider the card in the reviewer differently, and a lot of code is added to correct this.
    public abstract int[] counts();
    /**
     * Same as counts(), but also count `card`. In practice, we use it because `card` is in the reviewer and that is the
     * number we actually want.
     */
    public abstract int[] counts(Card card);
    /**
     * Return counts over next DAYS. Includes today.
     */
    public abstract int dueForecast();
    public abstract int dueForecast(int days);
    /** Which of the three numbers shown in reviewer/overview should the card be counted. 0:new, 1:rev, 2: any kind of learning.*/
    @Consts.CARD_QUEUE
    public abstract int countIdx(Card card);
    /** Number of buttons to show in the reviewer for `card`.*/
    public abstract int answerButtons(Card card);
    /**
     * Unbury all buried cards in all decks
     */
    public abstract void unburyCards();
    public abstract void unburyCardsForDeck();
    public abstract void _updateStats(Card card, String type, long cnt);
    public abstract void extendLimits(int newc, int rev);
    /**
     * Returns [deckname, did, rev, lrn, new]
     */
    public abstract List<DeckDueTreeNode> deckDueList();
    /** load the due tree, but halt if deck task is cancelled*/
    public abstract List<DeckDueTreeNode> deckDueTree(CollectionTask collectionTask);
    public abstract List<DeckDueTreeNode> deckDueTree();
    /** New count for a single deck. */
    public abstract int _newForDeck(long did, int lim);
    /** Limit for deck without parent limits. */
    public abstract int _deckNewLimitSingle(Deck g);
    public abstract int totalNewForCurrentDeck();
    public abstract int totalRevForCurrentDeck();
    public abstract Pair<Integer, Integer> _fuzzIvlRange(int ivl);
    /** Rebuild a dynamic deck. */
    public abstract void rebuildDyn();
    public abstract List<Long> rebuildDyn(long did);
    public abstract void emptyDyn(long did);
    public abstract void emptyDyn(long did, String lim);
    public abstract void remFromDyn(long[] cids);
    public abstract DeckConfig _cardConf(Card card);
    public abstract String _deckLimit();
    public abstract void _checkDay();
    public abstract CharSequence finishedMsg(Context context);
    public abstract String _nextDueMsg(Context context);
    /** true if there are any rev cards due. */
    public abstract boolean revDue();
    /** true if there are any new cards due. */
    public abstract boolean newDue();
    /** true if there are cards in learning, with review due the same
     * day, in the selected decks. */
    public abstract boolean hasCardsTodayAfterStudyAheadLimit();
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
    public abstract String nextIvlStr(Context context, Card card, @Consts.BUTTON_TYPE int ease);
    /**
     * Return the next interval for CARD, in seconds.
     */
    public abstract long nextIvl(Card card, @Consts.BUTTON_TYPE int ease);

    protected abstract String queueIsBuriedSnippet();
    protected abstract String _restoreQueueSnippet();
    /**
     * Suspend cards.
     */
    public abstract void suspendCards(long[] ids);
    /**
     * Unsuspend cards
     */
    public abstract void unsuspendCards(long[] ids);
    public abstract void buryCards(long[] cids);
    @VisibleForTesting
    public abstract void buryCards(long[] cids, boolean manual);
    /**
     * Bury all cards for note until next session.
     * @param nid The id of the targeted note.
     */
    public abstract void buryNote(long nid);
    /** Put cards at the end of the new queue. */
    public abstract void forgetCards(long[] ids);
    /**
     * Put cards in review queue with a new interval in days (min, max).
     *
     * @param ids The list of card ids to be affected
     * @param imin the minimum interval (inclusive)
     * @param imax The maximum interval (inclusive)
     */
    public abstract void reschedCards(long[] ids, int imin, int imax);
    /**
     * Completely reset cards for export.
     */
    public abstract void resetCards(Long[] ids);
    public abstract void sortCards(long[] cids, int start);
    public abstract void sortCards(long[] cids, int start, int step, boolean shuffle, boolean shift);
    public abstract void randomizeCards(long did);
    public abstract void orderCards(long did);
    public abstract void resortConf(DeckConfig conf);
    /**
     * for post-import
     */
    public abstract void maybeRandomizeDeck();
    public abstract void maybeRandomizeDeck(Long did);
    public abstract boolean haveBuried(long did);
    public enum UnburyType {
        ALL,
        MANUAL,
        SIBLINGS;
    }
    public abstract void unburyCardsForDeck(UnburyType type);
    public abstract void unburyCardsForDeck(long did);
    public abstract String getName();
    public abstract int getToday();
    public abstract void setToday(int today);
    public abstract long getDayCutoff();

    public abstract void incrReps();
    public abstract void decrReps();
    /** Number of repetitions today*/
    public abstract int getReps();
    /** Number of cards in the current decks, its descendants and ancestors. */
    public abstract int cardCount();

    public abstract int eta(int[] counts);
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
    public abstract int eta(int[] counts, boolean reload);
    /**
     * Change the counts to reflect that `card` should not be counted anymore. In practice, it means that the card has
     * been sent to the reviewer. Either through `getCard()` or through `undo`. Assumes that card's queue has not yet
     * changed. */
    public abstract void decrementCounts(Card card);
    public abstract boolean leechActionSuspend(Card card);
    public abstract void setContext(WeakReference<Activity> contextReference);
    public abstract int[] recalculateCounts();
    public abstract void setReportLimit(int reportLimit);

    /**
     * Reverts answering a card.
     * 
     * @param card The data of the card before the review was made
     * @param wasLeech Whether the card was a leech before the review was made (if false, remove the leech tag)
     * */
    public abstract void undoReview(@NonNull Card card, boolean wasLeech);


    /**
     * @return The current time dependency that the scheduler is using (used for Unit Testing).
     */
    @NonNull
    public abstract Time getTime();


    public interface LimitMethod {
        int operation(Deck g);
    }

    /** Given a deck, compute the number of cards to see today, taking its pre-computed limit into consideration.  It
     * considers either review or new cards. Used by WalkingCount to consider all subdecks and parents of a specific
     * decks.*/
    public interface CountMethod {
        int operation(long did, int lim);
    }

    protected static void leech(Card card, Activity activity) {
        if (activity != null) {
            Resources res = activity.getResources();
            final String leechMessage;
            if (card.getQueue() < 0) {
                leechMessage = res.getString(R.string.leech_suspend_notification);
            } else {
                leechMessage = res.getString(R.string.leech_notification);
            }
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, leechMessage, Toast.LENGTH_SHORT).show();
                }
            });

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

    public abstract Collection getCol();
}
