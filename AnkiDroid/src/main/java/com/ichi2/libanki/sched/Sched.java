/****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2013 Houssam Salem <houssam.salem.au@gmail.com>                        *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General private License as published by the Free Software       *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General private License for more details.            *
 *                                                                                      *
 * You should have received a copy of the GNU General private License along with        *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.libanki.sched;

import android.app.Activity;
import android.database.Cursor;
import android.database.SQLException;
import android.text.TextUtils;

import com.ichi2.async.CancelListener;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.SortOrder;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.DeckConfig;

import com.ichi2.utils.Assert;
import com.ichi2.utils.HashUtil;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;
import com.ichi2.utils.SyncStatus;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import timber.log.Timber;


import static com.ichi2.async.CancelListener.isCancelled;
import static com.ichi2.libanki.Consts.DECK_STD;
import static com.ichi2.libanki.sched.Counts.Queue.*;
import static com.ichi2.libanki.sched.Counts.Queue;
import static com.ichi2.libanki.stats.Stats.SECONDS_PER_DAY;

@SuppressWarnings({"PMD.ExcessiveClassLength", "PMD.AvoidThrowingRawExceptionTypes","PMD.AvoidReassigningParameters",
                    "PMD.NPathComplexity","PMD.MethodNamingConventions","PMD.AvoidBranchingStatementAsLastInLoop",
                    "PMD.SwitchStmtsShouldHaveDefault","PMD.CollapsibleIfStatements","PMD.EmptyIfStmt"})
public class Sched extends SchedV2 {



    // Not in libanki
    private static final int[] FACTOR_ADDITION_VALUES = { -150, 0, 150 };

    // Queues
    private @NonNull LinkedList<Long> mRevDids = new LinkedList<>();

    /**
     * queue types: 0=new/cram, 1=lrn, 2=rev, 3=day lrn, -1=suspended, -2=buried
     * revlog types: 0=lrn, 1=rev, 2=relrn, 3=cram
     * positive revlog intervals are in days (rev), negative in seconds (lrn)
     */

    public Sched(@NonNull Collection col) {
        super(col);
    }

    @Override
    public void answerCard(@NonNull Card card, @Consts.BUTTON_TYPE int ease) {
        mCol.log();
        mCol.markReview(card);
        discardCurrentCard();
        _burySiblings(card);
        card.incrReps();
        // former is for logging new cards, latter also covers filt. decks
        card.setWasNew((card.getType() == Consts.CARD_TYPE_NEW));
        boolean wasNewQ = (card.getQueue() == Consts.QUEUE_TYPE_NEW);
        if (wasNewQ) {
            // came from the new queue, move to learning
            card.setQueue(Consts.QUEUE_TYPE_LRN);
            // if it was a new card, it's now a learning card
            if (card.getType() == Consts.CARD_TYPE_NEW) {
                card.setType(Consts.CARD_TYPE_LRN);
            }
            // init reps to graduation
            card.setLeft(_startingLeft(card));
            // dynamic?
            if (card.isInDynamicDeck() && card.getType() == Consts.CARD_TYPE_REV) {
                if (_resched(card)) {
                    // reviews get their ivl boosted on first sight
                    card.setIvl(_dynIvlBoost(card));
                    card.setODue(mToday + card.getIvl());
                }
            }
            _updateStats(card, "new");
        }
        if (card.getQueue() == Consts.QUEUE_TYPE_LRN || card.getQueue() == Consts.QUEUE_TYPE_DAY_LEARN_RELEARN) {
            _answerLrnCard(card, ease);
            if (!wasNewQ) {
                _updateStats(card, "lrn");
            }
        } else if (card.getQueue() == Consts.QUEUE_TYPE_REV) {
            _answerRevCard(card, ease);
            _updateStats(card, "rev");
        } else {
            throw new RuntimeException("Invalid queue");
        }
        _updateStats(card, "time", card.timeTaken());
        card.setMod(getTime().intTime());
        card.setUsn(mCol.usn());
        card.flushSched();
    }


    @Override
    public @NonNull Counts counts(@NonNull Card card) {
        Counts counts = counts();
        Counts.Queue idx = countIdx(card);
        if (idx == LRN) {
            counts.addLrn(card.getLeft() / 1000);
        } else {
            counts.changeCount(idx, 1);
        }
        return counts;
    }


    @Override
    public Queue countIdx(@NonNull Card card) {
        switch (card.getQueue()) {
            case Consts.QUEUE_TYPE_DAY_LEARN_RELEARN:
            case Consts.QUEUE_TYPE_LRN:
                return LRN;
            case Consts.QUEUE_TYPE_NEW:
                return NEW;
            case Consts.QUEUE_TYPE_REV:
                return REV;
            default:
                throw new RuntimeException("Index " + card.getQueue() + " does not exists.");
        }
    }


    @Override
    public int answerButtons(@NonNull Card card) {
        if (card.getODue() != 0) {
            // normal review in dyn deck?
            if (card.isInDynamicDeck() && card.getQueue() == Consts.QUEUE_TYPE_REV) {
                return 4;
            }
            JSONObject conf = _lrnConf(card);
            if (card.getType() == Consts.CARD_TYPE_NEW || card.getType() == Consts.CARD_TYPE_LRN || conf.getJSONArray("delays").length() > 1) {
                return 3;
            }
            return 2;
        } else if (card.getQueue() == Consts.QUEUE_TYPE_REV) {
            return 4;
        } else {
            return 3;
        }
    }


    /*
     * Unbury cards.
     */
    @Override
    public void unburyCards() {
        mCol.set_config("lastUnburied", mToday);
        mCol.log(mCol.getDb().queryLongList("select id from cards where " + queueIsBuriedSnippet()));
        mCol.getDb().execute("update cards set " + _restoreQueueSnippet() + " where " + queueIsBuriedSnippet());
    }


    @Override
    public void unburyCardsForDeck() {
        unburyCardsForDeck(mCol.getDecks().active());
    }

    private void unburyCardsForDeck(@NonNull List<Long> allDecks) {
        // Refactored to allow unburying an arbitrary deck
        String sids = Utils.ids2str(allDecks);
        mCol.log(mCol.getDb().queryLongList("select id from cards where " + queueIsBuriedSnippet() + " and did in " + sids));
        mCol.getDb().execute("update cards set mod=?,usn=?," + _restoreQueueSnippet() + " where " + queueIsBuriedSnippet() + " and did in " + sids,
                getTime().intTime(), mCol.usn());
    }

    /*
      Deck list **************************************************************** *******************************
     */


    /**
     * Returns [deckname, did, rev, lrn, new]
     */
    @Override
    public @Nullable List<DeckDueTreeNode> deckDueList(@Nullable CancelListener cancelListener) {
        _checkDay();
        mCol.getDecks().checkIntegrity();
        List<Deck> decks = mCol.getDecks().allSorted();
        HashMap<String, Integer[]> lims = HashUtil.HashMapInit(decks.size());
        ArrayList<DeckDueTreeNode> deckNodes = new ArrayList<>(decks.size());
        for (Deck deck : decks) {
            if (isCancelled(cancelListener)) {
                return null;
            }
            String deckName = deck.getString("name");
            String p = Decks.parent(deckName);
            // new
            int nlim = _deckNewLimitSingle(deck, false);
            int rlim = _deckRevLimitSingle(deck, false);
            if (!TextUtils.isEmpty(p)) {
                Integer[] parentLims = lims.get(Decks.normalizeName(p));
                // 'temporary for diagnosis of bug #6383'
                Assert.that(parentLims != null, "Deck %s is supposed to have parent %s. It has not be found.", deckName, p);
                nlim = Math.min(nlim, parentLims[0]);
                // review
                rlim = Math.min(rlim, parentLims[1]);
            }
            int _new = _newForDeck(deck.getLong("id"), nlim);
            // learning
            int lrn = _lrnForDeck(deck.getLong("id"));
            // reviews
            int rev = _revForDeck(deck.getLong("id"), rlim);
            // save to list
            deckNodes.add(new DeckDueTreeNode(mCol, deck.getString("name"), deck.getLong("id"), rev, lrn, _new));
            // add deck as a parent
            lims.put(Decks.normalizeName(deck.getString("name")), new Integer[]{nlim, rlim});
        }
        return deckNodes;
    }


    /*
      Getting the next card ****************************************************
      *******************************************
     */

    /**
     * Return the next due card, or null.
     */
    @Override
    protected @Nullable Card _getCard() {
        // learning card due?
        @Nullable Card c = _getLrnCard(false);
        if (c != null) {
            return c;
        }
        // new first, or time for one?
        if (_timeForNewCard()) {
            c = _getNewCard();
            if (c != null) {
                return c;
            }
        }
        // Card due for review?
        c = _getRevCard();
        if (c != null) {
            return c;
        }
        // day learning card due?
        c = _getLrnDayCard();
        if (c != null) {
            return c;
        }
        // New cards left?
        c = _getNewCard();
        if (c != null) {
            return c;
        }
        // collapse or finish
        return _getLrnCard(true);
    }


    protected @NonNull CardQueue<? extends Card.Cache>[] _fillNextCard() {
        // learning card due?
        if (_preloadLrnCard(false)) {
            return new CardQueue<?>[]{mLrnQueue};
        }
        // new first, or time for one?
        if (_timeForNewCard()) {
            if (_fillNew()) {
                return new CardQueue<?>[]{mLrnQueue, mNewQueue};
            }
        }
        // Card due for review?
        if (_fillRev()) {
            return new CardQueue<?>[]{mLrnQueue, mRevQueue};
        }
        // day learning card due?
        if (_fillLrnDay()) {
            return new CardQueue<?>[]{mLrnQueue, mLrnDayQueue};
        }
        // New cards left?
        if (_fillNew()) {
            return new CardQueue<?>[]{mLrnQueue, mNewQueue};
        }
        // collapse or finish
        if (_preloadLrnCard(true)) {
            return new CardQueue<?>[]{mLrnQueue};
        }
        return new CardQueue<?>[]{};
    }
    /**
     * Learning queues *********************************************************** ************************************
     */

    @Override
    protected void _resetLrnCount() {
        _resetLrnCount(null);
    }
    protected void _resetLrnCount(@Nullable CancelListener cancelListener) {
        // sub-day
        mLrnCount = mCol.getDb().queryScalar(
                "SELECT sum(left / 1000) FROM (SELECT left FROM cards WHERE did IN " + _deckLimit()
                + " AND queue = " + Consts.QUEUE_TYPE_LRN + " AND due < ? and id != ? LIMIT ?)",
                mDayCutoff, currentCardId(), mReportLimit);
        if (isCancelled(cancelListener)) return;
        // day
        mLrnCount += mCol.getDb().queryScalar(
                "SELECT count() FROM cards WHERE did IN " + _deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + " AND due <= ? "+
                        "AND id != ? LIMIT ?",
                mToday, currentCardId(), mReportLimit);
    }

    @Override
    protected void _resetLrnQueue() {
        mLrnQueue.clear();
        mLrnDayQueue.clear();
        mLrnDids = mCol.getDecks().active();
    }


    // sub-day learning
    @Override
    protected boolean _fillLrn() {
        if (mHaveCounts && mLrnCount == 0) {
            return false;
        }
        if (!mLrnQueue.isEmpty()) {
            return true;
        }
        mLrnQueue.clear();
        /* Difference with upstream:
         * Current card can't come in the queue.
         *
         * In standard usage, a card is not requested before the previous card is marked as reviewed. However, if we
         * decide to query a second card sooner, we don't want to get the same card a second time. This simulate
         * _getLrnCard which did remove the card from the queue. _sortIntoLrn will add the card back to the queue if
         * required when the card is reviewed.
         */
        mLrnQueue.setFilled();
        try (Cursor cur = mCol.getDb().query(
                           "SELECT due, id FROM cards WHERE did IN " + _deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_LRN + " AND due < ? AND id != ? LIMIT ?",
                           mDayCutoff, currentCardId(), mReportLimit)) {
            while (cur.moveToNext()) {
                mLrnQueue.add(cur.getLong(0), cur.getLong(1));
            }
            // as it arrives sorted by did first, we need to sort it
            mLrnQueue.sort();
            return !mLrnQueue.isEmpty();
        }
    }


    @Override
    protected @Nullable Card _getLrnCard(boolean collapse) {
        if (_fillLrn()) {
            long cutoff = getTime().intTime();
            if (collapse) {
                cutoff += mCol.get_config_int("collapseTime");
            }
            if (mLrnQueue.getFirstDue() < cutoff) {
                return mLrnQueue.removeFirstCard();
                // mLrnCount -= card.getLeft() / 1000; See decrementCount()
            }
        }
        return null;
    }


    /**
     * @param ease 1=no, 2=yes, 3=remove
     */
    @Override
    protected void _answerLrnCard(@NonNull Card card, @Consts.BUTTON_TYPE int ease) {
        JSONObject conf = _lrnConf(card);
        @Consts.CARD_TYPE int type;
        if (card.isInDynamicDeck() && !card.getWasNew()) {
            type = Consts.CARD_TYPE_RELEARNING;
        } else if (card.getType() == Consts.CARD_TYPE_REV) {
            type = Consts.CARD_TYPE_REV;
        } else {
            type = Consts.CARD_TYPE_NEW;
        }
        boolean leaving = false;
        // lrnCount was decremented once when card was fetched
        int lastLeft = card.getLeft();
        // immediate graduate?
        if (ease == Consts.BUTTON_THREE) {
            _rescheduleAsRev(card, conf, true);
            leaving = true;
            // graduation time?
        } else if (ease == Consts.BUTTON_TWO && (card.getLeft() % 1000) - 1 <= 0) {
            _rescheduleAsRev(card, conf, false);
            leaving = true;
        } else {
            // one step towards graduation
            if (ease == Consts.BUTTON_TWO) {
                // decrement real left count and recalculate left today
                int left = (card.getLeft() % 1000) - 1;
                card.setLeft(_leftToday(conf.getJSONArray("delays"), left) * 1000 + left);
                // failed
            } else {
                card.setLeft(_startingLeft(card));
                boolean resched = _resched(card);
                if (conf.has("mult") && resched) {
                    // review that's lapsed
                    card.setIvl(Math.max(Math.max(1, (int) (card.getIvl() * conf.getDouble("mult"))), conf.getInt("minInt")));
                } else {
                    // new card; no ivl adjustment
                    // pass
                }
                if (resched && card.isInDynamicDeck()) {
                    card.setODue(mToday + 1);
                }
            }
            int delay = _delayForGrade(conf, card.getLeft());
            if (card.getDue() < getTime().intTime()) {
                // not collapsed; add some randomness
                delay *= Utils.randomFloatInRange(1f, 1.25f);
            }
            card.setDue(getTime().intTime() + delay);

            // due today?
            if (card.getDue() < mDayCutoff) {
                mLrnCount += card.getLeft() / 1000;
                // if the queue is not empty and there's nothing else to do, make
                // sure we don't put it at the head of the queue and end up showing
                // it twice in a row
                card.setQueue(Consts.QUEUE_TYPE_LRN);
                if (!mLrnQueue.isEmpty() && revCount() == 0 && newCount() == 0) {
                    long smallestDue = mLrnQueue.getFirstDue();
                    card.setDue(Math.max(card.getDue(), smallestDue + 1));
                }
                _sortIntoLrn(card.getDue(), card.getId());
            } else {
                // the card is due in one or more days, so we need to use the day learn queue
                long ahead = ((card.getDue() - mDayCutoff) / SECONDS_PER_DAY) + 1;
                card.setDue(mToday + ahead);
                card.setQueue(Consts.QUEUE_TYPE_DAY_LEARN_RELEARN);
            }
        }
        _logLrn(card, ease, conf, leaving, type, lastLeft);
    }


    @Override
    protected @NonNull JSONObject _lrnConf(@NonNull Card card) {
        if (card.getType() == Consts.CARD_TYPE_REV) {
            return _lapseConf(card);
        } else {
            return _newConf(card);
        }
    }


    @Override
    protected void _rescheduleAsRev(@NonNull Card card, @NonNull JSONObject conf, boolean early) {
        boolean lapse = (card.getType() == Consts.CARD_TYPE_REV);
        if (lapse) {
            if (_resched(card)) {
                card.setDue(Math.max(mToday + 1, card.getODue()));
            } else {
                card.setDue(card.getODue());
            }
            card.setODue(0);
        } else {
            _rescheduleNew(card, conf, early);
        }
        card.setQueue(Consts.QUEUE_TYPE_REV);
        card.setType(Consts.CARD_TYPE_REV);
        // if we were dynamic, graduating means moving back to the old deck
        boolean resched = _resched(card);
        if (card.isInDynamicDeck()) {
            card.setDid(card.getODid());
            card.setODue(0);
            card.setODid(0);
            // if rescheduling is off, it needs to be set back to a new card
            if (!resched && !lapse) {
                card.setType(Consts.CARD_TYPE_NEW);
                card.setQueue(Consts.QUEUE_TYPE_NEW);
                card.setDue(mCol.nextID("pos"));
            }
        }
    }


    @Override
    protected int _startingLeft(@NonNull Card card) {
        JSONObject conf;
    	if (card.getType() == Consts.CARD_TYPE_REV) {
    		conf = _lapseConf(card);
    	} else {
    		conf = _lrnConf(card);
    	}
        int tot = conf.getJSONArray("delays").length();
        int tod = _leftToday(conf.getJSONArray("delays"), tot);
        return tot + tod * 1000;
    }


    private int _graduatingIvl(@NonNull Card card, @NonNull JSONObject conf, boolean early, boolean adj) {
        if (card.getType() == Consts.CARD_TYPE_REV) {
            // lapsed card being relearnt
            if (card.isInDynamicDeck()) {
                if (conf.getBoolean("resched")) {
                    return _dynIvlBoost(card);
                }
            }
            return card.getIvl();
        }
        int ideal;
        JSONArray ints = conf.getJSONArray("ints");
        if (!early) {
            // graduate
            ideal = ints.getInt(0);
        } else {
            ideal = ints.getInt(1);
        }
        if (adj) {
            return _adjRevIvl(card, ideal);
        } else {
            return ideal;
        }
    }


    /* Reschedule a new card that's graduated for the first time. */
    private void _rescheduleNew(@NonNull Card card, @NonNull JSONObject conf, boolean early) {
        card.setIvl(_graduatingIvl(card, conf, early));
        card.setDue(mToday + card.getIvl());
        card.setFactor(conf.getInt("initialFactor"));
    }


    @VisibleForTesting
    public void removeLrn() {
    	removeLrn(null);
    }

    /** Remove cards from the learning queues. */
    private void removeLrn(@NonNull long[] ids) {
        String extra;
        if (ids != null && ids.length > 0) {
            extra = " AND id IN " + Utils.ids2str(ids);
        } else {
            // benchmarks indicate it's about 10x faster to search all decks with the index than scan the table
            extra = " AND did IN " + Utils.ids2str(mCol.getDecks().allIds());
        }
        // review cards in relearning
        mCol.getDb().execute(
                "update cards set due = odue, queue = " + Consts.QUEUE_TYPE_REV + ", mod = ?" +
                ", usn = ?, odue = 0 where queue IN (" + Consts.QUEUE_TYPE_LRN + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ") and type = " + Consts.CARD_TYPE_REV + " " + extra,
                getTime().intTime(), mCol.usn());
        // new cards in learning
        forgetCards(mCol.getDb().queryLongList( "SELECT id FROM cards WHERE queue IN (" + Consts.QUEUE_TYPE_LRN + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ") " + extra));
    }

    private int _lrnForDeck(long did) {
        try {
            int cnt = mCol.getDb().queryScalar(
                    "SELECT sum(left / 1000) FROM (SELECT left FROM cards WHERE did = ?"
                            + " AND queue = " + Consts.QUEUE_TYPE_LRN + " AND due < ?"
                            + " LIMIT ?)",
                    did, (getTime().intTime() + mCol.get_config_int("collapseTime")), mReportLimit);
            return cnt + mCol.getDb().queryScalar(
                    "SELECT count() FROM (SELECT 1 FROM cards WHERE did = ?"
                            + " AND queue = " + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + " AND due <= ?"
                            + " LIMIT ?)",
                    did, mToday, mReportLimit);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    /*
      Reviews ****************************************************************** *****************************
     */

    /**
     *
     * @param considerCurrentCard Whether current card should be conted if it is in this deck
     */
    protected int _deckRevLimit(long did, boolean considerCurrentCard) {
        return _deckNewLimit(did, d -> _deckRevLimitSingle(d, considerCurrentCard), considerCurrentCard);
    }

    /**
     * Maximal number of rev card still to see today in deck d. It's computed as:
     * the number of rev card to see by day according
     * minus the number of rev cards seen today in deck d or a descendant
     * plus the number of extra cards to see today in deck d, a parent or a descendant.
     *
     * Limits of its ancestors are not applied.  Current card is treated the same way as other cards.
     * @param considerCurrentCard Whether current card should be conted if it is in this deck
     * */
    @Override
    protected int _deckRevLimitSingle(@NonNull Deck d, boolean considerCurrentCard) {
        if (d.isDyn()) {
            return mReportLimit;
        }
        long did = d.getLong("id");
        DeckConfig c = mCol.getDecks().confForDid(did);
        int lim = Math.max(0, c.getJSONObject("rev").getInt("perDay") - d.getJSONArray("revToday").getInt(1));
        if (considerCurrentCard && currentCardIsInQueueWithDeck(Consts.QUEUE_TYPE_REV, did)) {
            lim--;
        }
        // The counts shown in the reviewer does not consider the current card. E.g. if it indicates 6 rev card, it means, 6 rev card including current card will be seen today.
        // So currentCard does not have to be taken into consideration in this method
        return lim;
    }


    private int _revForDeck(long did, int lim) {
    	lim = Math.min(lim, mReportLimit);
    	return mCol.getDb().queryScalar("SELECT count() FROM (SELECT 1 FROM cards WHERE did = ? AND queue = " + Consts.QUEUE_TYPE_REV + " AND due <= ? LIMIT ?)",
                                        did, mToday, lim);
    }


    @Override
    protected void _resetRevCount() {
        _resetRevCount(null);
    }
    protected void _resetRevCount(@Nullable CancelListener cancelListener) {
        mRevCount = _walkingCount(d -> _deckRevLimitSingle(d, true),
                                  this::_cntFnRev, cancelListener);
    }


    // Dynamically invoked in _walkingCount, passed as a parameter in _resetRevCount
    @SuppressWarnings("unused")
    protected int _cntFnRev(long did, int lim) {
        //protected because _walkingCount need to be able to access it.
        return mCol.getDb().queryScalar(
                "SELECT count() FROM (SELECT id FROM cards WHERE did = ? AND queue = " + Consts.QUEUE_TYPE_REV + " and due <= ? "
                        + " AND id != ? LIMIT ?)", did, mToday, currentCardId(), lim);
    }


    @Override
    protected void _resetRevQueue() {
        mRevQueue.clear();
        mRevDids = mCol.getDecks().active();
    }


    @Override
    protected boolean _fillRev(boolean allowSibling) {
        if (!mRevQueue.isEmpty()) {
            return true;
        }
        if (mHaveCounts && mRevCount == 0) {
            return false;
        }
        while (!mRevDids.isEmpty()) {
            long did = mRevDids.getFirst();
            int lim = Math.min(mQueueLimit, _deckRevLimit(did, false));
            if (lim != 0) {
                mRevQueue.clear();
                // fill the queue with the current did
                String idName = (allowSibling) ? "id": "nid";
                long id = (allowSibling) ? currentCardId(): currentCardNid();
                for (long cid: mCol.getDb().queryLongList(
                        "SELECT id FROM cards WHERE did = ? AND queue = " + Consts.QUEUE_TYPE_REV + " AND due <= ?"
                                + " AND " + idName + " != ? LIMIT ?",
                        did, mToday, id, lim)) {
                    /* Difference with upstream: we take current card into account.
                     *
                     * When current card is answered, the card is not due anymore, so does not belong to the queue.
                     * Furthermore, _burySiblings ensure that the siblings of the current cards are removed from the
                     * queue to ensure same day spacing. We simulate this action by ensuring that those siblings are not
                     * filled, except if we know there are cards and we didn't find any non-sibling card. This way, the
                     * queue is not empty if it should not be empty (important for the conditional belows), but the
                     * front of the queue contains distinct card.
                     */
                    mRevQueue.add(cid);
                }
                if (!mRevQueue.isEmpty()) {
                    // ordering
                    if (mCol.getDecks().get(did).isDyn()) {
                        // dynamic decks need due order preserved
                        // Note: libanki reverses mRevQueue and returns the last element in _getRevCard().
                        // AnkiDroid differs by leaving the queue intact and returning the *first* element
                        // in _getRevCard().
                    } else {
                        Random r = new Random();
                        r.setSeed(mToday);
                        mRevQueue.shuffle(r);
                    }
                    // is the current did empty?
                    if (mRevQueue.size() < lim) {
                        mRevDids.remove();
                    }
                    return true;
                }
            }
            // nothing left in the deck; move to next
            mRevDids.remove();
        }
        if (mHaveCounts && mRevCount != 0) {
            // if we didn't get a card but the count is non-zero,
            // we need to check again for any cards that were
            // removed from the queue but not buried
            _resetRev();
            return _fillRev(true);
        }
        return false;
    }


    /**
     * Answering a review card **************************************************
     * *********************************************
     */

    @Override
    protected void _answerRevCard(@NonNull Card card, @Consts.BUTTON_TYPE int ease) {
        int delay = 0;
        if (ease == Consts.BUTTON_ONE) {
            delay = _rescheduleLapse(card);
        } else {
            _rescheduleRev(card, ease);
        }
        _logRev(card, ease, delay, Consts.REVLOG_REV);
    }


    @Override
    protected int _rescheduleLapse(@NonNull Card card) {
        JSONObject conf = _lapseConf(card);
        card.setLastIvl(card.getIvl());
        if (_resched(card)) {
            card.setLapses(card.getLapses() + 1);
            card.setIvl(_nextLapseIvl(card, conf));
            card.setFactor(Math.max(1300, card.getFactor() - 200));
            card.setDue(mToday + card.getIvl());
            // if it's a filtered deck, update odue as well
            if (card.isInDynamicDeck()) {
                card.setODue(card.getDue());
            }
        }
        // if suspended as a leech, nothing to do
        int delay = 0;
        if (_checkLeech(card, conf) && card.getQueue() == Consts.QUEUE_TYPE_SUSPENDED) {
            return delay;
        }
        // if no relearning steps, nothing to do
        if (conf.getJSONArray("delays").length() == 0) {
            return delay;
        }
        // record rev due date for later
        if (card.getODue() == 0) {
            card.setODue(card.getDue());
        }
        delay = _delayForGrade(conf, 0);
        card.setDue(delay + getTime().intTime());
        card.setLeft(_startingLeft(card));
        // queue 1
        if (card.getDue() < mDayCutoff) {
            mLrnCount += card.getLeft() / 1000;
            card.setQueue(Consts.QUEUE_TYPE_LRN);
            _sortIntoLrn(card.getDue(), card.getId());
        } else {
            // day learn queue
            long ahead = ((card.getDue() - mDayCutoff) / SECONDS_PER_DAY) + 1;
            card.setDue(mToday + ahead);
            card.setQueue(Consts.QUEUE_TYPE_DAY_LEARN_RELEARN);
        }
        return delay;
    }


    private int _nextLapseIvl(@NonNull Card card, @NonNull JSONObject conf) {
        return Math.max(conf.getInt("minInt"), (int)(card.getIvl() * conf.getDouble("mult")));
    }


    private void _rescheduleRev(@NonNull Card card, @Consts.BUTTON_TYPE int ease) {
        // update interval
        card.setLastIvl(card.getIvl());
        if (_resched(card)) {
            _updateRevIvl(card, ease);
            // then the rest
            card.setFactor(Math.max(1300, card.getFactor() + FACTOR_ADDITION_VALUES[ease - 2]));
            card.setDue(mToday + card.getIvl());
        } else {
            card.setDue(card.getODue());
        }
        if (card.isInDynamicDeck()) {
            card.setDid(card.getODid());
            card.setODid(0);
            card.setODue(0);
        }
    }


    /*
      Interval management ******************************************************
      *****************************************
     */

    /**
     * Ideal next interval for CARD, given EASE.
     */
    private int _nextRevIvl(@NonNull Card card, @Consts.BUTTON_TYPE int ease) {
        long delay = _daysLate(card);
        int interval = 0;
        JSONObject conf = _revConf(card);
        double fct = card.getFactor() / 1000.0;
        int ivl2 = _constrainedIvl((int)((card.getIvl() + delay/4) * 1.2), conf, card.getIvl());
        int ivl3 = _constrainedIvl((int)((card.getIvl() + delay/2) * fct), conf, ivl2);
        int ivl4 = _constrainedIvl((int)((card.getIvl() + delay) * fct * conf.getDouble("ease4")), conf, ivl3);
        if (ease == Consts.BUTTON_TWO) {
            interval = ivl2;
        } else if (ease == Consts.BUTTON_THREE) {
            interval = ivl3;
        } else if (ease == Consts.BUTTON_FOUR) {
            interval = ivl4;
        }
        // interval capped?
        return Math.min(interval, conf.getInt("maxIvl"));
    }


    /** Integer interval after interval factor and prev+1 constraints applied */
    private int _constrainedIvl(int ivl, @NonNull JSONObject conf, double prev) {
    	double newIvl = ivl * conf.optDouble("ivlFct",1.0);
        return (int) Math.max(newIvl, prev + 1);
    }



    @Override
    protected void _updateRevIvl(@NonNull Card card, @Consts.BUTTON_TYPE int ease) {
        try {
            int idealIvl = _nextRevIvl(card, ease);
            JSONObject conf = _revConf(card);
            card.setIvl(Math.min(
                    Math.max(_adjRevIvl(card, idealIvl), card.getIvl() + 1),
                    conf.getInt("maxIvl")));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }

    @SuppressWarnings("PMD.UnusedFormalParameter") // it's unused upstream as well
    private int _adjRevIvl(@NonNull Card card, int idealIvl) {
        idealIvl = _fuzzedIvl(idealIvl);
        return idealIvl;
    }


    /**
     * Dynamic deck handling ******************************************************************
     * *****************************
     */

    /* Rebuild a dynamic deck. */
    @Override
    public void rebuildDyn() {
        rebuildDyn(0);
    }


    @Override
    public void rebuildDyn(long did) {
        if (did == 0) {
            did = mCol.getDecks().selected();
        }
        Deck deck = mCol.getDecks().get(did);
        if (deck.isStd()) {
            Timber.e("error: deck is not a filtered deck");
            return;
        }
        // move any existing cards back first, then fill
        emptyDyn(did);
        List<Long> ids = _fillDyn(deck);
        if (ids.isEmpty()) {
            return;
        }
        // and change to our new deck
        mCol.getDecks().select(did);
    }


    private List<Long> _fillDyn(@NonNull Deck deck) {
        JSONArray terms = deck.getJSONArray("terms").getJSONArray(0);
        String search = terms.getString(0);
        int limit = terms.getInt(1);
        int order = terms.getInt(2);
        SortOrder orderlimit = new SortOrder.AfterSqlOrderBy(_dynOrder(order, limit));
        if (!TextUtils.isEmpty(search.trim())) {
            search = String.format(Locale.US, "(%s)", search);
        }
        search = String.format(Locale.US, "%s -is:suspended -is:buried -deck:filtered -is:learn", search);
        List<Long> ids = mCol.findCards(search, orderlimit);
        if (ids.isEmpty()) {
            return ids;
        }
        // move the cards over
        mCol.log(deck.getLong("id"), ids);
        _moveToDyn(deck.getLong("id"), ids);
        return ids;
    }


    @Override
    public void emptyDyn(long did, String lim) {
        if (lim == null) {
            lim = "did = " + did;
        }
        mCol.log(mCol.getDb().queryLongList("select id from cards where " + lim));
        // move out of cram queue
        mCol.getDb().execute(
                "update cards set did = odid, queue = (case when type = " + Consts.CARD_TYPE_LRN + " then " + Consts.QUEUE_TYPE_NEW + " " +
                "else type end), type = (case when type = " + Consts.CARD_TYPE_LRN + " then " + Consts.CARD_TYPE_NEW + " else type end), " +
                "due = odue, odue = 0, odid = 0, usn = ? where " + lim,
                mCol.usn());
    }


    private void _moveToDyn(long did, @NonNull List<Long> ids) {
        ArrayList<Object[]> data = new ArrayList<>(ids.size());
        //long t = getTime().intTime(); // unused variable present (and unused) upstream
        int u = mCol.usn();
        for (long c = 0; c < ids.size(); c++) {
            // start at -100000 so that reviews are all due
            data.add(new Object[] { did, -100000 + c, u, ids.get((int) c) });
        }
        // due reviews stay in the review queue. careful: can't use "odid or did", as sqlite converts to boolean
        String queue = "(CASE WHEN type = " + Consts.CARD_TYPE_REV + " AND (CASE WHEN odue THEN odue <= " + mToday +
                " ELSE due <= " + mToday + " END) THEN " + Consts.QUEUE_TYPE_REV + " ELSE " + Consts.QUEUE_TYPE_NEW + " END)";
        mCol.getDb().executeMany(
                "UPDATE cards SET odid = (CASE WHEN odid THEN odid ELSE did END), " +
                        "odue = (CASE WHEN odue THEN odue ELSE due END), did = ?, queue = " +
                        queue + ", due = ?, usn = ? WHERE id = ?", data);
    }


    private int _dynIvlBoost(@NonNull Card card) {
        if (!card.isInDynamicDeck() || card.getType() != Consts.CARD_TYPE_REV || card.getFactor() == 0) {
            Timber.e("error: deck is not a filtered deck");
            return 0;
        }
        long elapsed = card.getIvl() - (card.getODue() - mToday);
        double factor = ((card.getFactor() / 1000.0) + 1.2) / 2.0;
        int ivl = Math.max(1, Math.max(card.getIvl(), (int) (elapsed * factor)));
        JSONObject conf = _revConf(card);
        return Math.min(conf.getInt("maxIvl"), ivl);
    }


    /*
      Leeches ****************************************************************** *****************************
     */

    /** Leech handler. True if card was a leech. */
    @Override
    protected boolean _checkLeech(@NonNull Card card, @NonNull JSONObject conf) {
        int lf = conf.getInt("leechFails");
        if (lf == 0) {
            return false;
        }
        // if over threshold or every half threshold reps after that
        if (card.getLapses() >= lf && (card.getLapses() - lf) % Math.max(lf / 2, 1) == 0) {
            // add a leech tag
            Note n = card.note();
            n.addTag("leech");
            n.flush();
            // handle
            if (conf.getInt("leechAction") == Consts.LEECH_SUSPEND) {
                // if it has an old due, remove it from cram/relearning
                if (card.getODue() != 0) {
                    card.setDue(card.getODue());
                }
                if (card.isInDynamicDeck()) {
                    card.setDid(card.getODid());
                }
                card.setODue(0);
                card.setODid(0);
                card.setQueue(Consts.QUEUE_TYPE_SUSPENDED);
            }
            // notify UI
            if (mContextReference != null) {
                Activity context = mContextReference.get();
                leech(card, context);
            }
            return true;
        }
        return false;
    }


    /**
     * Tools ******************************************************************** ***************************
     */

    @Override
    protected @NonNull JSONObject _newConf(@NonNull Card card) {
        DeckConfig conf = _cardConf(card);
        if (!card.isInDynamicDeck()) {
            return conf.getJSONObject("new");
        }
        // dynamic deck; override some attributes, use original deck for others
        DeckConfig oconf = mCol.getDecks().confForDid(card.getODid());
        JSONArray delays = conf.optJSONArray("delays");
        if (delays == null) {
            delays = oconf.getJSONObject("new").getJSONArray("delays");
        }
        JSONObject dict = new JSONObject();
        // original deck
        dict.put("ints", oconf.getJSONObject("new").getJSONArray("ints"));
        dict.put("initialFactor", oconf.getJSONObject("new").getInt("initialFactor"));
        dict.put("bury", oconf.getJSONObject("new").optBoolean("bury", true));
        // overrides
        dict.put("delays", delays);
        dict.put("separate", conf.getBoolean("separate"));
        dict.put("order", Consts.NEW_CARDS_DUE);
        dict.put("perDay", mReportLimit);
        return dict;
    }


    @Override
    protected @NonNull JSONObject _lapseConf(@NonNull Card card) {
        DeckConfig conf = _cardConf(card);
        if (!card.isInDynamicDeck()) {
            return conf.getJSONObject("lapse");
        }
        // dynamic deck; override some attributes, use original deck for others
        DeckConfig oconf = mCol.getDecks().confForDid(card.getODid());
        JSONArray delays = conf.optJSONArray("delays");
        if (delays == null) {
            delays = oconf.getJSONObject("lapse").getJSONArray("delays");
        }
        JSONObject dict = new JSONObject();
        // original deck
        dict.put("minInt", oconf.getJSONObject("lapse").getInt("minInt"));
        dict.put("leechFails", oconf.getJSONObject("lapse").getInt("leechFails"));
        dict.put("leechAction", oconf.getJSONObject("lapse").getInt("leechAction"));
        dict.put("mult", oconf.getJSONObject("lapse").getDouble("mult"));
        // overrides
        dict.put("delays", delays);
        dict.put("resched", conf.getBoolean("resched"));
        return dict;
    }


    private boolean _resched(@NonNull Card card) {
        DeckConfig conf = _cardConf(card);
        if (conf.getInt("dyn") == DECK_STD) {
            return true;
        }
        return conf.getBoolean("resched");
    }


    /**
     * Daily cutoff ************************************************************* **********************************
     * This function uses GregorianCalendar so as to be sensitive to leap years, daylight savings, etc.
     */

    @Override
    public void _updateCutoff() {
        Integer oldToday = mToday;
        // days since col created
        mToday = (int) ((getTime().intTime() - mCol.getCrt()) / SECONDS_PER_DAY);
        // end of day cutoff
        mDayCutoff = mCol.getCrt() + ((mToday + 1) * SECONDS_PER_DAY);
        if (!mToday.equals(oldToday)) {
            mCol.log(mToday, mDayCutoff);
        }
        // update all daily counts, but don't save decks to prevent needless conflicts. we'll save on card answer
        // instead
        for (Deck deck : mCol.getDecks().all()) {
            update(deck);
        }
        // unbury if the day has rolled over
        int unburied = mCol.get_config("lastUnburied", 0);
        if (unburied < mToday) {
            SyncStatus.ignoreDatabaseModification(this::unburyCards);
        }
    }


    /**
     * Deck finished state ******************************************************
     * *****************************************
     */


    @Override
    public boolean haveBuried() {
        return haveBuried(mCol.getDecks().active());
    }

    private boolean haveBuried(@NonNull List<Long> allDecks) {
        // Refactored to allow querying an arbitrary deck
        String sdids = Utils.ids2str(allDecks);
        int cnt = mCol.getDb().queryScalar(
                "select 1 from cards where " + queueIsBuriedSnippet() + " and did in " + sdids + " limit 1");
        return cnt != 0;
    }


    /*
      Next time reports ********************************************************
      ***************************************
     */

    /**
     * Return the next interval for CARD, in seconds.
     */
    @Override
    protected long nextIvl(@NonNull Card card, @Consts.BUTTON_TYPE int ease) {
        if (card.getQueue() == Consts.QUEUE_TYPE_NEW || card.getQueue() == Consts.QUEUE_TYPE_LRN || card.getQueue() == Consts.QUEUE_TYPE_DAY_LEARN_RELEARN) {
            return _nextLrnIvl(card, ease);
        } else if (ease == Consts.BUTTON_ONE) {
            // lapsed
            JSONObject conf = _lapseConf(card);
            if (conf.getJSONArray("delays").length() > 0) {
                return (long) (conf.getJSONArray("delays").getDouble(0) * 60.0);
            }
            return _nextLapseIvl(card, conf) * SECONDS_PER_DAY;
        } else {
            // review
            return _nextRevIvl(card, ease) * SECONDS_PER_DAY;
        }
    }


    @Override
    protected long _nextLrnIvl(@NonNull Card card, @Consts.BUTTON_TYPE int ease) {
        // this isn't easily extracted from the learn code
        if (card.getQueue() == Consts.QUEUE_TYPE_NEW) {
            card.setLeft(_startingLeft(card));
        }
        JSONObject conf = _lrnConf(card);
        if (ease == Consts.BUTTON_ONE) {
            // fail
            return _delayForGrade(conf, conf.getJSONArray("delays").length());
        } else if (ease == Consts.BUTTON_THREE) {
            // early removal
            if (!_resched(card)) {
                return 0;
            }
            return _graduatingIvl(card, conf, true, false) * SECONDS_PER_DAY;
        } else {
            int left = card.getLeft() % 1000 - 1;
            if (left <= 0) {
                // graduate
                if (!_resched(card)) {
                    return 0;
                }
                return _graduatingIvl(card, conf, false, false) * SECONDS_PER_DAY;
            } else {
                return _delayForGrade(conf, left);
            }
        }
    }


    /*
      Suspending *************************************************************** ********************************
     */

    /**
     * Suspend cards.
     */
    @Override
    public void suspendCards(@NonNull long[] ids) {
        mCol.log(ids);
        remFromDyn(ids);
        removeLrn(ids);
        mCol.getDb().execute(
                "UPDATE cards SET queue = " + Consts.QUEUE_TYPE_SUSPENDED + ", mod = ?, usn = ? WHERE id IN "
                        + Utils.ids2str(ids),
                getTime().intTime(), mCol.usn());
    }

    protected @NonNull String queueIsBuriedSnippet() {
        return "queue = " + Consts.QUEUE_TYPE_SIBLING_BURIED;
    }

    protected @NonNull String _restoreQueueSnippet() {
        return "queue = type";
    }

    /**
     * Unsuspend cards
     */
    @Override
    public void buryCards(@NonNull long[] cids) {
        buryCards(cids, false);
    }

    @Override
    public void buryCards(@NonNull long[] cids, boolean manual) {
        // The boolean is useless here. However, it ensures that we are override the method with same parameter in SchedV2.
        mCol.log(cids);
        remFromDyn(cids);
        removeLrn(cids);
        mCol.getDb().execute("update cards set " + queueIsBuriedSnippet() + ",mod=?,usn=? where id in " + Utils.ids2str(cids),
                getTime().intTime(), mCol.usn());
    }




    /*
     * ***********************************************************
     * The methods below are not in LibAnki.
     * ***********************************************************
     */
    @Override
    public boolean haveBuried(long did) {
        List<Long> all = new ArrayList<>(mCol.getDecks().children(did).values());
        all.add(did);
        return haveBuried(all);
    }

    @Override
    public void unburyCardsForDeck(long did) {
        List<Long> all = new ArrayList<>(mCol.getDecks().children(did).values());
        all.add(did);
        unburyCardsForDeck(all);
    }

    /* Need to override. Otherwise it get SchedV2.mName variable*/
    @NonNull
    @Override
    public String getName() {
        return "std";
    }

    /*
      Counts
     */

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
    @Override
    public int eta(@NonNull Counts counts, boolean reload) {
        double newRate;
        double newTime;
        double revRate;
        double revTime;
        double relrnRate;
        double relrnTime;

        if (reload || mEtaCache[0] == -1) {
            try (Cursor cur = mCol
                        .getDb()
                        .query("select "
                                + "avg(case when type = " + Consts.CARD_TYPE_NEW + " then case when ease > 1 then 1.0 else 0.0 end else null end) as newRate, avg(case when type = " + Consts.CARD_TYPE_NEW + " then time else null end) as newTime, "
                                + "avg(case when type in (" + Consts.CARD_TYPE_LRN + ", " + Consts.CARD_TYPE_RELEARNING+ ") then case when ease > 1 then 1.0 else 0.0 end else null end) as revRate, avg(case when type in (" + Consts.CARD_TYPE_LRN + ", " + Consts.CARD_TYPE_RELEARNING + ") then time else null end) as revTime, "
                                + "avg(case when type = " + Consts.CARD_TYPE_REV + " then case when ease > 1 then 1.0 else 0.0 end else null end) as relrnRate, avg(case when type = " + Consts.CARD_TYPE_REV + " then time else null end) as relrnTime "
                                + "from revlog where id > "
                                + ((mCol.getSched().getDayCutoff() - (10 * SECONDS_PER_DAY)) * 1000))) {
                if (!cur.moveToFirst()) {
                    return -1;
                }

                newRate = cur.getDouble(0);
                newTime = cur.getDouble(1);
                revRate = cur.getDouble(2);
                revTime = cur.getDouble(3);
                relrnRate = cur.getDouble(4);
                relrnTime = cur.getDouble(5);

                if (!cur.isClosed()) {
                    cur.close();
                }

            }

            // If the collection has no revlog data to work with, assume a 20 second average rep for that type
            newTime = newTime == 0 ? 20000 : newTime;
            revTime = revTime == 0 ? 20000 : revTime;
            relrnTime = relrnTime == 0 ? 20000 : relrnTime;
            // And a 100% success rate
            newRate = newRate == 0 ? 1 : newRate;
            revRate = revRate == 0 ? 1 : revRate;
            relrnRate = relrnRate == 0 ? 1 : relrnRate;

            mEtaCache[0] = newRate;
            mEtaCache[1] = newTime;
            mEtaCache[2] = revRate;
            mEtaCache[3] = revTime;
            mEtaCache[4] = relrnRate;
            mEtaCache[5] = relrnTime;

        } else {
            newRate = mEtaCache[0];
            newTime = mEtaCache[1];
            revRate= mEtaCache[2];
            revTime = mEtaCache[3];
            relrnRate = mEtaCache[4];
            relrnTime = mEtaCache[5];
        }

        // Calculate the total time for each queue based on the historical average duration per rep
        double newTotal = newTime * counts.getNew();
        double relrnTotal = relrnTime * counts.getLrn();
        double revTotal = revTime * counts.getRev();

        // Now we have to predict how many additional relrn cards are going to be generated while reviewing the above
        // queues, and how many relrn cards *those* reps will generate (and so on, until 0).

        // Every queue has a failure rate, and each failure will become a relrn
        int toRelrn = counts.getNew(); // Assume every new card becomes 1 relrn
        toRelrn += Math.ceil((1 - relrnRate) * counts.getLrn());
        toRelrn += Math.ceil((1 - revRate) * counts.getRev());

        // Use the accuracy rate of the relrn queue to estimate how many reps we will end up with if the cards
        // currently in relrn continue to fail at that rate. Loop through the failures of the failures until we end up
        // with no predicted failures left.

        // Cap the lower end of the success rate to ensure the loop ends (it could be 0 if no revlog history, or
        // negative for other reasons). 5% seems reasonable to ensure the loop doesn't iterate too much.
        relrnRate = Math.max(relrnRate, 0.05);
        int futureReps = 0;
        do {
            // Truncation ensures the failure rate always decreases
            int failures = (int) ((1 - relrnRate) * toRelrn);
            futureReps += failures;
            toRelrn = failures;
        } while (toRelrn > 1);
        double futureRelrnTotal = relrnTime * futureReps;

        return (int) Math.round((newTotal + relrnTotal + revTotal + futureRelrnTotal) / 60000);
    }


    /**
     * This is used when card is currently in the reviewer, to adapt the counts by removing this card from it.
     *
     * @param discardCard A card sent to reviewer that should not be
     * counted.
     */
    @Override
    public void decrementCounts(@Nullable Card discardCard) {
        if (discardCard == null) {
            return;
        }
        @Consts.CARD_QUEUE int type = discardCard.getQueue();
        switch (type) {
        case Consts.QUEUE_TYPE_NEW:
            mNewCount--;
            break;
        case Consts.QUEUE_TYPE_LRN:
            mLrnCount -= discardCard.getLeft() / 1000;
            break;
        case Consts.QUEUE_TYPE_REV:
            mRevCount--;
            break;
        case Consts.QUEUE_TYPE_DAY_LEARN_RELEARN:
            mLrnCount--;
            break;
        }
    }

    /** The button to press on a new card to answer "good".*/
    @Override
    @VisibleForTesting
    public @Consts.BUTTON_TYPE int getGoodNewButton() {
        return Consts.BUTTON_TWO;
    }
}
