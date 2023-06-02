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

package com.ichi2.libanki.sched

import android.database.SQLException
import androidx.annotation.VisibleForTesting
import com.ichi2.async.CancelListener
import com.ichi2.async.CancelListener.Companion.isCancelled
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts.BUTTON_TYPE
import com.ichi2.libanki.Consts.CARD_QUEUE
import com.ichi2.libanki.Consts.DECK_STD
import com.ichi2.libanki.Consts.REVLOG_TYPE
import com.ichi2.libanki.SortOrder.AfterSqlOrderBy
import com.ichi2.libanki.sched.Counts.Queue
import com.ichi2.libanki.sched.Counts.Queue.*
import com.ichi2.libanki.stats.Stats.Companion.SECONDS_PER_DAY
import com.ichi2.utils.Assert
import com.ichi2.utils.HashUtil
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.SyncStatus.Companion.ignoreDatabaseModification
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.util.*

@KotlinCleanup("IDE Lint")
@KotlinCleanup("cleanup: use formatted string for all queries")
class Sched(col: Collection) : SchedV2(col) {
    /*
     * queue types: 0=new/cram, 1=lrn, 2=rev, 3=day lrn, -1=suspended, -2=buried
     * revlog types: 0=lrn, 1=rev, 2=relrn, 3=cram
     * positive revlog intervals are in days (rev), negative in seconds (lrn)
     */

    // Queues
    private var mRevDids = LinkedList<Long>()
    override fun answerCard(card: Card, @BUTTON_TYPE ease: Int) {
        col.log()
        col.markReview(card)
        discardCurrentCard()
        _burySiblings(card)
        card.incrReps()
        // former is for logging new cards, latter also covers filt. decks
        card.wasNew = card.type == Consts.CARD_TYPE_NEW
        val wasNewQ = card.queue == Consts.QUEUE_TYPE_NEW
        if (wasNewQ) {
            // came from the new queue, move to learning
            card.queue = Consts.QUEUE_TYPE_LRN
            // if it was a new card, it's now a learning card
            if (card.type == Consts.CARD_TYPE_NEW) {
                card.type = Consts.CARD_TYPE_LRN
            }
            // init reps to graduation
            card.left = _startingLeft(card)
            // dynamic?
            if (card.isInDynamicDeck && card.type == Consts.CARD_TYPE_REV) {
                if (_resched(card)) {
                    // reviews get their ivl boosted on first sight
                    card.ivl = _dynIvlBoost(card)
                    card.oDue = (mToday!! + card.ivl).toLong()
                }
            }
            _updateStats(card, "new")
        }
        if (card.queue == Consts.QUEUE_TYPE_LRN || card.queue == Consts.QUEUE_TYPE_DAY_LEARN_RELEARN) {
            _answerLrnCard(card, ease)
            if (!wasNewQ) {
                _updateStats(card, "lrn")
            }
        } else if (card.queue == Consts.QUEUE_TYPE_REV) {
            _answerRevCard(card, ease)
            _updateStats(card, "rev")
        } else {
            throw RuntimeException("Invalid queue")
        }
        _updateStats(card, "time", card.timeTaken(col).toLong())
        card.mod = time.intTime()
        card.usn = col.usn()
        card.flushSched(col)
    }

    override fun counts(card: Card): Counts {
        val counts = counts()
        val idx = countIdx(card)
        if (idx === LRN) {
            counts.addLrn(card.left / 1000)
        } else {
            counts.changeCount(idx, 1)
        }
        return counts
    }

    override fun countIdx(card: Card): Queue {
        return when (card.queue) {
            Consts.QUEUE_TYPE_DAY_LEARN_RELEARN, Consts.QUEUE_TYPE_LRN -> LRN
            Consts.QUEUE_TYPE_NEW -> NEW
            Consts.QUEUE_TYPE_REV -> REV
            else -> throw RuntimeException("Index " + card.queue + " does not exists.")
        }
    }

    override fun answerButtons(card: Card): Int {
        return if (card.oDue != 0L) {
            // normal review in dyn deck?
            if (card.isInDynamicDeck && card.queue == Consts.QUEUE_TYPE_REV) {
                return 4
            }
            val conf = _lrnConf(card)
            if (card.type == Consts.CARD_TYPE_NEW || card.type == Consts.CARD_TYPE_LRN || conf.getJSONArray(
                    "delays"
                ).length() > 1
            ) {
                3
            } else {
                2
            }
        } else if (card.queue == Consts.QUEUE_TYPE_REV) {
            4
        } else {
            3
        }
    }

    private fun unburyCardsForDeck(allDecks: List<Long>) {
        // Refactored to allow unburying an arbitrary deck
        val sids = Utils.ids2str(allDecks)
        col.log(col.db.queryLongList("select id from cards where " + queueIsBuriedSnippet() + " and did in " + sids))
        col.db.execute(
            "update cards set mod=?,usn=?," + _restoreQueueSnippet() + " where " + queueIsBuriedSnippet() + " and did in " + sids,
            time.intTime(),
            col.usn()
        )
    }
    /*
      Deck list **************************************************************** *******************************
     */
    /**
     * Returns [deckname, did, rev, lrn, new]
     */
    override fun deckDueList(collectionTask: CancelListener?): List<DeckDueTreeNode>? {
        _checkDay()
        col.decks.checkIntegrity()
        val allDecksSorted = col.decks.allSorted()

        @KotlinCleanup("input should be non-null")
        val lims = HashUtil.HashMapInit<String?, Array<Int>>(allDecksSorted.size)
        val deckNodes = ArrayList<DeckDueTreeNode>(allDecksSorted.size)
        for (deck in allDecksSorted) {
            if (isCancelled(collectionTask)) {
                return null
            }
            val deckName = deck.getString("name")
            val p = Decks.parent(deckName)
            // new
            var nlim = _deckNewLimitSingle(deck, false)
            var rlim = _deckRevLimitSingle(deck, false)
            if (!p.isNullOrEmpty()) {
                val parentLims = lims[Decks.normalizeName(p)]
                // 'temporary for diagnosis of bug #6383'
                Assert.that(
                    parentLims != null,
                    "Deck %s is supposed to have parent %s. It has not be found.",
                    deckName,
                    p
                )
                nlim = Math.min(nlim, parentLims!![0])
                // review
                rlim = Math.min(rlim, parentLims[1])
            }
            val _new = _newForDeck(deck.getLong("id"), nlim)
            // learning
            val lrn = _lrnForDeck(deck.getLong("id"))
            // reviews
            val rev = _revForDeck(deck.getLong("id"), rlim)
            // save to list
            deckNodes.add(
                DeckDueTreeNode(
                    deck.getString("name"),
                    deck.getLong("id"),
                    rev,
                    lrn,
                    _new,
                    false,
                    false
                )
            )
            // add deck as a parent
            lims[Decks.normalizeName(deck.getString("name"))] = arrayOf(nlim, rlim)
        }
        return deckNodes
    }
    /*
      Getting the next card ****************************************************
      *******************************************
     */
    /**
     * Return the next due card, or null.
     */
    override fun _getCard(): Card? {
        // learning card due?
        var c = _getLrnCard(false)
        if (c != null) {
            return c
        }
        // new first, or time for one?
        if (_timeForNewCard()) {
            c = _getNewCard()
            if (c != null) {
                return c
            }
        }
        // Card due for review?
        c = _getRevCard()
        if (c != null) {
            return c
        }
        // day learning card due?
        c = _getLrnDayCard()
        if (c != null) {
            return c
        }
        // New cards left?
        c = _getNewCard()
        return c ?: _getLrnCard(true)
        // collapse or finish
    }

    @KotlinCleanup("simplify fun with when")
    override fun _fillNextCard(): Array<CardQueue<out Card.Cache>> {
        // learning card due?
        if (_preloadLrnCard(false)) {
            return arrayOf(mLrnQueue)
        }
        // new first, or time for one?
        if (_timeForNewCard()) {
            if (_fillNew()) {
                return arrayOf(mLrnQueue, mNewQueue)
            }
        }
        // Card due for review?
        if (_fillRev()) {
            return arrayOf(mLrnQueue, mRevQueue)
        }
        // day learning card due?
        if (_fillLrnDay()) {
            return arrayOf(mLrnQueue, mLrnDayQueue)
        }
        // New cards left?
        if (_fillNew()) {
            return arrayOf(mLrnQueue, mNewQueue)
        }
        // collapse or finish
        return if (_preloadLrnCard(true)) {
            arrayOf(mLrnQueue)
        } else {
            arrayOf()
        }
    }

    /**
     * Learning queues *********************************************************** ************************************
     */
    override fun _resetLrnCount() {
        _resetLrnCount(null)
    }

    override fun _resetLrnCount(cancelListener: CancelListener?) {
        // sub-day
        mLrnCount = col.db.queryScalar(
            "SELECT sum(left / 1000) FROM (SELECT left FROM cards WHERE did IN " + _deckLimit() +
                " AND queue = " + Consts.QUEUE_TYPE_LRN + " AND due < ? and id != ? LIMIT ?)",
            dayCutoff,
            currentCardId(),
            mReportLimit
        )
        if (isCancelled(cancelListener)) return
        // day
        mLrnCount += col.db.queryScalar(
            "SELECT count() FROM cards WHERE did IN " + _deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + " AND due <= ? " +
                "AND id != ? LIMIT ?",
            mToday!!,
            currentCardId(),
            mReportLimit
        )
    }

    override fun _resetLrnQueue() {
        mLrnQueue.clear()
        mLrnDayQueue.clear()
        mLrnDids = col.decks.active()
    }

    // sub-day learning
    override fun _fillLrn(): Boolean {
        if (mHaveCounts && mLrnCount == 0) {
            return false
        }
        if (!mLrnQueue.isEmpty) {
            return true
        }
        mLrnQueue.clear()
        /* Difference with upstream:
         * Current card can't come in the queue.
         *
         * In standard usage, a card is not requested before the previous card is marked as reviewed. However, if we
         * decide to query a second card sooner, we don't want to get the same card a second time. This simulate
         * _getLrnCard which did remove the card from the queue. _sortIntoLrn will add the card back to the queue if
         * required when the card is reviewed.
         */mLrnQueue.setFilled()
        col.db.query(
            "SELECT due, id FROM cards WHERE did IN " + _deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_LRN + " AND due < ? AND id != ? LIMIT ?",
            dayCutoff,
            currentCardId(),
            mReportLimit
        ).use { cur ->
            while (cur.moveToNext()) {
                mLrnQueue.add(cur.getLong(0), cur.getLong(1))
            }
            // as it arrives sorted by did first, we need to sort it
            mLrnQueue.sort()
            return !mLrnQueue.isEmpty
        }
    }

    override fun _getLrnCard(collapse: Boolean): Card? {
        if (_fillLrn()) {
            var cutoff = time.intTime()
            if (collapse) {
                cutoff += col.get_config_int("collapseTime").toLong()
            }
            if (mLrnQueue.firstDue < cutoff) {
                return mLrnQueue.removeFirstCard()
                // mLrnCount -= card.getLeft() / 1000; See decrementCount()
            }
        }
        return null
    }

    /**
     * @param ease 1=no, 2=yes, 3=remove
     */
    override fun _answerLrnCard(card: Card, @BUTTON_TYPE ease: Int) {
        val conf = _lrnConf(card)

        @REVLOG_TYPE val type: Int
        type = if (card.isInDynamicDeck && !card.wasNew) {
            Consts.REVLOG_CRAM
        } else if (card.type == Consts.CARD_TYPE_REV) {
            Consts.REVLOG_RELRN
        } else {
            Consts.REVLOG_LRN
        }
        var leaving = false
        // lrnCount was decremented once when card was fetched
        val lastLeft = card.left
        // immediate graduate?
        if (ease == Consts.BUTTON_THREE) {
            _rescheduleAsRev(card, conf, true)
            leaving = true
            // graduation time?
        } else if (ease == Consts.BUTTON_TWO && card.left % 1000 - 1 <= 0) {
            _rescheduleAsRev(card, conf, false)
            leaving = true
        } else {
            // one step towards graduation
            if (ease == Consts.BUTTON_TWO) {
                // decrement real left count and recalculate left today
                val left = card.left % 1000 - 1
                card.left = _leftToday(conf.getJSONArray("delays"), left) * 1000 + left
                // failed
            } else {
                card.left = _startingLeft(card)
                val resched = _resched(card)
                if (conf.has("mult") && resched) {
                    // review that's lapsed
                    card.ivl = Math.max(
                        Math.max(1, (card.ivl * conf.getDouble("mult")).toInt()),
                        conf.getInt("minInt")
                    )
                } else {
                    // new card; no ivl adjustment
                    // pass
                }
                if (resched && card.isInDynamicDeck) {
                    card.oDue = (mToday!! + 1).toLong()
                }
            }
            var delay = _delayForGrade(conf, card.left)
            if (card.due < time.intTime()) {
                // not collapsed; add some randomness
                delay *= Utils.randomFloatInRange(1f, 1.25f).toInt()
            }
            card.due = time.intTime() + delay

            // due today?
            if (card.due < dayCutoff) {
                mLrnCount += card.left / 1000
                // if the queue is not empty and there's nothing else to do, make
                // sure we don't put it at the head of the queue and end up showing
                // it twice in a row
                card.queue = Consts.QUEUE_TYPE_LRN
                if (!mLrnQueue.isEmpty && revCount() == 0 && newCount() == 0) {
                    val smallestDue = mLrnQueue.firstDue
                    card.due = Math.max(card.due, smallestDue + 1)
                }
                _sortIntoLrn(card.due, card.id)
            } else {
                // the card is due in one or more days, so we need to use the day learn queue
                val ahead = (card.due - dayCutoff) / SECONDS_PER_DAY + 1
                card.due = mToday!! + ahead
                card.queue = Consts.QUEUE_TYPE_DAY_LEARN_RELEARN
            }
        }
        _logLrn(card, ease, conf, leaving, type, lastLeft)
    }

    override fun _lrnConf(card: Card): JSONObject {
        return if (card.type == Consts.CARD_TYPE_REV) {
            _lapseConf(card)
        } else {
            _newConf(card)
        }
    }

    override fun _rescheduleAsRev(card: Card, conf: JSONObject, early: Boolean) {
        val lapse = card.type == Consts.CARD_TYPE_REV
        if (lapse) {
            if (_resched(card)) {
                card.due = Math.max((mToday!! + 1).toLong(), card.oDue)
            } else {
                card.due = card.oDue
            }
            card.oDue = 0
        } else {
            _rescheduleNew(card, conf, early)
        }
        card.queue = Consts.QUEUE_TYPE_REV
        card.type = Consts.CARD_TYPE_REV
        // if we were dynamic, graduating means moving back to the old deck
        val resched = _resched(card)
        if (card.isInDynamicDeck) {
            card.did = card.oDid
            card.oDue = 0
            card.oDid = 0
            // if rescheduling is off, it needs to be set back to a new card
            if (!resched && !lapse) {
                card.type = Consts.CARD_TYPE_NEW
                card.queue = Consts.QUEUE_TYPE_NEW
                card.due = col.nextID("pos").toLong()
            }
        }
    }

    override fun _startingLeft(card: Card): Int {
        val conf: JSONObject
        conf = if (card.type == Consts.CARD_TYPE_REV) {
            _lapseConf(card)
        } else {
            _lrnConf(card)
        }
        val tot = conf.getJSONArray("delays").length()
        val tod = _leftToday(conf.getJSONArray("delays"), tot)
        return tot + tod * 1000
    }

    private fun _graduatingIvl(card: Card, conf: JSONObject, early: Boolean, adj: Boolean): Int {
        if (card.type == Consts.CARD_TYPE_REV) {
            // lapsed card being relearnt
            if (card.isInDynamicDeck) {
                if (conf.getBoolean("resched")) {
                    return _dynIvlBoost(card)
                }
            }
            return card.ivl
        }
        val ideal: Int
        val ints = conf.getJSONArray("ints")
        ideal = if (!early) {
            // graduate
            ints.getInt(0)
        } else {
            ints.getInt(1)
        }
        return if (adj) {
            _adjRevIvl(ideal)
        } else {
            ideal
        }
    }

    /* Reschedule a new card that's graduated for the first time. */
    private fun _rescheduleNew(card: Card, conf: JSONObject, early: Boolean) {
        card.ivl = _graduatingIvl(card, conf, early)
        card.due = (mToday!! + card.ivl).toLong()
        card.factor = conf.getInt("initialFactor")
    }

    @VisibleForTesting
    fun removeLrn() {
        removeLrn(null)
    }

    /** Remove cards from the learning queues.  */
    private fun removeLrn(ids: LongArray?) {
        val extra: String
        extra = if (ids != null && ids.size > 0) {
            " AND id IN " + Utils.ids2str(ids)
        } else {
            // benchmarks indicate it's about 10x faster to search all decks with the index than scan the table
            " AND did IN " + Utils.ids2str(col.decks.allIds())
        }
        // review cards in relearning
        col.db.execute(
            "update cards set due = odue, queue = " + Consts.QUEUE_TYPE_REV + ", mod = ?" +
                ", usn = ?, odue = 0 where queue IN (" + Consts.QUEUE_TYPE_LRN + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ") and type = " + Consts.CARD_TYPE_REV + " " + extra,
            time.intTime(),
            col.usn()
        )
        // new cards in learning
        forgetCards(col.db.queryLongList("SELECT id FROM cards WHERE queue IN (" + Consts.QUEUE_TYPE_LRN + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ") " + extra))
    }

    private fun _lrnForDeck(did: Long): Int {
        return try {
            val cnt = col.db.queryScalar(
                "SELECT sum(left / 1000) FROM (SELECT left FROM cards WHERE did = ?" +
                    " AND queue = " + Consts.QUEUE_TYPE_LRN + " AND due < ?" +
                    " LIMIT ?)",
                did,
                time.intTime() + col.get_config_int("collapseTime"),
                mReportLimit
            )
            cnt + col.db.queryScalar(
                "SELECT count() FROM (SELECT 1 FROM cards WHERE did = ?" +
                    " AND queue = " + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + " AND due <= ?" +
                    " LIMIT ?)",
                did,
                mToday!!,
                mReportLimit
            )
        } catch (e: SQLException) {
            throw RuntimeException(e)
        }
    }
    /*
      Reviews ****************************************************************** *****************************
     */
    /**
     *
     * @param considerCurrentCard Whether current card should be counted if it is in this deck
     */
    protected fun _deckRevLimit(did: Long, considerCurrentCard: Boolean): Int {
        return _deckNewLimit(
            did,
            { d: Deck? -> _deckRevLimitSingle(d, considerCurrentCard) },
            considerCurrentCard
        )
    }

    /**
     * Maximal number of rev card still to see today in deck d. It's computed as:
     * the number of rev card to see by day according
     * minus the number of rev cards seen today in deck d or a descendant
     * plus the number of extra cards to see today in deck d, a parent or a descendant.
     *
     * Limits of its ancestors are not applied.  Current card is treated the same way as other cards.
     * @param considerCurrentCard Whether current card should be counted if it is in this deck
     */
    @KotlinCleanup("remove nullable on deck")
    override fun _deckRevLimitSingle(d: Deck?, considerCurrentCard: Boolean): Int {
        if (d!!.isDyn) {
            return mReportLimit
        }
        val did = d.getLong("id")
        val c = col.decks.confForDid(did)
        var lim = Math.max(
            0,
            c.getJSONObject("rev").getInt("perDay") - d.getJSONArray("revToday").getInt(1)
        )
        if (considerCurrentCard && currentCardIsInQueueWithDeck(Consts.QUEUE_TYPE_REV, did)) {
            lim--
        }
        // The counts shown in the reviewer does not consider the current card. E.g. if it indicates 6 rev card, it means, 6 rev card including current card will be seen today.
        // So currentCard does not have to be taken into consideration in this method
        return lim
    }

    private fun _revForDeck(did: Long, lim: Int): Int {
        @Suppress("NAME_SHADOWING")
        var lim = lim
        lim = Math.min(lim, mReportLimit)
        return col.db.queryScalar(
            "SELECT count() FROM (SELECT 1 FROM cards WHERE did = ? AND queue = " + Consts.QUEUE_TYPE_REV + " AND due <= ? LIMIT ?)",
            did,
            mToday!!,
            lim
        )
    }

    @KotlinCleanup("see if these functions can be combined into one")
    override fun _resetRevCount() {
        _resetRevCount(null)
    }

    override fun _resetRevCount(cancelListener: CancelListener?) {
        mRevCount = _walkingCount(
            { d: Deck? -> _deckRevLimitSingle(d, true) },
            { did: Long, lim: Int -> _cntFnRev(did, lim) },
            cancelListener
        )
    }

    // Dynamically invoked in _walkingCount, passed as a parameter in _resetRevCount
    protected fun _cntFnRev(did: Long, lim: Int): Int {
        // protected because _walkingCount need to be able to access it.
        return col.db.queryScalar(
            "SELECT count() FROM (SELECT id FROM cards WHERE did = ? AND queue = " + Consts.QUEUE_TYPE_REV + " and due <= ? " +
                " AND id != ? LIMIT ?)",
            did,
            mToday!!,
            currentCardId(),
            lim
        )
    }

    override fun _resetRevQueue() {
        mRevQueue.clear()
        mRevDids = col.decks.active()
    }

    override fun _fillRev(allowSibling: Boolean): Boolean {
        if (!mRevQueue.isEmpty) {
            return true
        }
        if (mHaveCounts && mRevCount == 0) {
            return false
        }
        while (!mRevDids.isEmpty()) {
            val did = mRevDids.first
            val lim = Math.min(mQueueLimit, _deckRevLimit(did, false))
            if (lim != 0) {
                mRevQueue.clear()
                // fill the queue with the current did
                val idName = if (allowSibling) "id" else "nid"
                val id = if (allowSibling) currentCardId() else currentCardNid()
                for (
                cid in col.db.queryLongList(
                    "SELECT id FROM cards WHERE did = ? AND queue = " + Consts.QUEUE_TYPE_REV + " AND due <= ?" +
                        " AND " + idName + " != ? LIMIT ?",
                    did,
                    mToday!!,
                    id,
                    lim
                )
                ) {
                    /* Difference with upstream: we take current card into account.
                     *
                     * When current card is answered, the card is not due anymore, so does not belong to the queue.
                     * Furthermore, _burySiblings ensure that the siblings of the current cards are removed from the
                     * queue to ensure same day spacing. We simulate this action by ensuring that those siblings are not
                     * filled, except if we know there are cards and we didn't find any non-sibling card. This way, the
                     * queue is not empty if it should not be empty (important for the conditional belows), but the
                     * front of the queue contains distinct card.
                     */
                    mRevQueue.add(cid)
                }
                if (!mRevQueue.isEmpty) {
                    // ordering
                    if (col.decks.get(did).isDyn) {
                        // dynamic decks need due order preserved
                        // Note: libanki reverses mRevQueue and returns the last element in _getRevCard().
                        // AnkiDroid differs by leaving the queue intact and returning the *first* element
                        // in _getRevCard().
                    } else {
                        @KotlinCleanup(".apply")
                        val r = Random()
                        r.setSeed(mToday!!.toLong())
                        mRevQueue.shuffle(r)
                    }
                    // is the current did empty?
                    if (mRevQueue.size() < lim) {
                        mRevDids.remove()
                    }
                    return true
                }
            }
            // nothing left in the deck; move to next
            mRevDids.remove()
        }
        if (mHaveCounts && mRevCount != 0) {
            // if we didn't get a card but the count is non-zero,
            // we need to check again for any cards that were
            // removed from the queue but not buried
            _resetRev()
            return _fillRev(true)
        }
        return false
    }

    /**
     * Answering a review card **************************************************
     * *********************************************
     */
    override fun _answerRevCard(card: Card, @BUTTON_TYPE ease: Int) {
        var delay = 0
        if (ease == Consts.BUTTON_ONE) {
            delay = _rescheduleLapse(card)
        } else {
            _rescheduleRev(card, ease)
        }
        _logRev(card, ease, delay, Consts.REVLOG_REV)
    }

    override fun _rescheduleLapse(card: Card): Int {
        val conf = _lapseConf(card)
        card.lastIvl = card.ivl
        if (_resched(card)) {
            card.lapses = card.lapses + 1
            card.ivl = _nextLapseIvl(card, conf)
            card.factor = Math.max(1300, card.factor - 200)
            card.due = (mToday!! + card.ivl).toLong()
            // if it's a filtered deck, update odue as well
            if (card.isInDynamicDeck) {
                card.oDue = card.due
            }
        }
        // if suspended as a leech, nothing to do
        var delay = 0
        if (_checkLeech(card, conf) && card.queue == Consts.QUEUE_TYPE_SUSPENDED) {
            return delay
        }
        // if no relearning steps, nothing to do
        if (conf.getJSONArray("delays").length() == 0) {
            return delay
        }
        // record rev due date for later
        if (card.oDue == 0L) {
            card.oDue = card.due
        }
        delay = _delayForGrade(conf, 0)
        card.due = delay + time.intTime()
        card.left = _startingLeft(card)
        // queue 1
        if (card.due < dayCutoff) {
            mLrnCount += card.left / 1000
            card.queue = Consts.QUEUE_TYPE_LRN
            _sortIntoLrn(card.due, card.id)
        } else {
            // day learn queue
            val ahead = (card.due - dayCutoff) / SECONDS_PER_DAY + 1
            card.due = mToday!! + ahead
            card.queue = Consts.QUEUE_TYPE_DAY_LEARN_RELEARN
        }
        return delay
    }

    private fun _nextLapseIvl(card: Card, conf: JSONObject): Int {
        return Math.max(conf.getInt("minInt"), (card.ivl * conf.getDouble("mult")).toInt())
    }

    private fun _rescheduleRev(card: Card, @BUTTON_TYPE ease: Int) {
        // update interval
        card.lastIvl = card.ivl
        if (_resched(card)) {
            _updateRevIvl(card, ease)
            // then the rest
            card.factor = Math.max(1300, card.factor + FACTOR_ADDITION_VALUES[ease - 2])
            card.due = (mToday!! + card.ivl).toLong()
        } else {
            card.due = card.oDue
        }
        if (card.isInDynamicDeck) {
            card.did = card.oDid
            card.oDid = 0
            card.oDue = 0
        }
    }
    /*
      Interval management ******************************************************
      *****************************************
     */
    /**
     * Ideal next interval for CARD, given EASE.
     */
    private fun _nextRevIvl(card: Card, @BUTTON_TYPE ease: Int): Int {
        val delay = _daysLate(card)
        var interval = 0
        val conf = _revConf(card)
        val fct = card.factor / 1000.0
        val ivl2 =
            _constrainedIvl(((card.ivl + delay / 4) * 1.2).toInt(), conf, card.ivl.toDouble())
        val ivl3 = _constrainedIvl(((card.ivl + delay / 2) * fct).toInt(), conf, ivl2.toDouble())
        val ivl4 = _constrainedIvl(
            ((card.ivl + delay) * fct * conf.getDouble("ease4")).toInt(),
            conf,
            ivl3.toDouble()
        )
        if (ease == Consts.BUTTON_TWO) {
            interval = ivl2
        } else if (ease == Consts.BUTTON_THREE) {
            interval = ivl3
        } else if (ease == Consts.BUTTON_FOUR) {
            interval = ivl4
        }
        // interval capped?
        return Math.min(interval, conf.getInt("maxIvl"))
    }

    /** Integer interval after interval factor and prev+1 constraints applied  */
    private fun _constrainedIvl(ivl: Int, conf: JSONObject, prev: Double): Int {
        val newIvl = ivl * conf.optDouble("ivlFct", 1.0)
        return Math.max(newIvl, prev + 1).toInt()
    }

    @KotlinCleanup("remove catch")
    override fun _updateRevIvl(card: Card, @BUTTON_TYPE ease: Int) {
        try {
            val idealIvl = _nextRevIvl(card, ease)
            val conf = _revConf(card)
            card.ivl = Math.min(
                Math.max(_adjRevIvl(idealIvl), card.ivl + 1),
                conf.getInt("maxIvl")
            )
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
    }

    // it's unused upstream as well
    @KotlinCleanup("simplify fun")
    private fun _adjRevIvl(idealIvl: Int): Int {
        @Suppress("NAME_SHADOWING")
        var idealIvl = idealIvl
        idealIvl = _fuzzedIvl(idealIvl)
        return idealIvl
    }

    /**
     * Dynamic deck handling ******************************************************************
     * *****************************
     */
    override fun rebuildDyn(did: Long) {
        val deck = col.decks.get(did)
        if (deck.isStd) {
            Timber.e("error: deck is not a filtered deck")
            return
        }
        // move any existing cards back first, then fill
        emptyDyn(did)
        val ids = _fillDyn(deck)
        if (ids.isEmpty()) {
            return
        }
        // and change to our new deck
        col.decks.select(did)
    }

    private fun _fillDyn(deck: Deck): List<Long> {
        val terms = deck.getJSONArray("terms").getJSONArray(0)
        var search = terms.getString(0)
        val limit = terms.getInt(1)
        val order = terms.getInt(2)
        val orderLimit: SortOrder = AfterSqlOrderBy(_dynOrder(order, limit))
        if (search.trim { it <= ' ' }.isNotEmpty()) {
            search = String.format(Locale.US, "(%s)", search)
        }
        search =
            String.format(Locale.US, "%s -is:suspended -is:buried -deck:filtered -is:learn", search)
        val ids = col.findCards(search, orderLimit)
        if (ids.isEmpty()) {
            return ids
        }
        // move the cards over
        col.log(deck.getLong("id"), ids)
        _moveToDyn(deck.getLong("id"), ids)
        return ids
    }

    override fun emptyDyn(lim: String) {
        col.log(col.db.queryLongList("select id from cards where $lim"))
        // move out of cram queue
        col.db.execute(
            "update cards set did = odid, queue = (case when type = " + Consts.CARD_TYPE_LRN + " then " + Consts.QUEUE_TYPE_NEW + " " +
                "else type end), type = (case when type = " + Consts.CARD_TYPE_LRN + " then " + Consts.CARD_TYPE_NEW + " else type end), " +
                "due = odue, odue = 0, odid = 0, usn = ? where " + lim,
            col.usn()
        )
    }

    private fun _moveToDyn(did: Long, ids: List<Long>) {
        val data = ArrayList<Array<Any?>>(ids.size)
        // long t = getTime().intTime(); // unused variable present (and unused) upstream
        val u = col.usn()
        for (c in ids.indices) {
            // start at -100000 so that reviews are all due
            data.add(arrayOf(did, -100000 + c, u, ids[c]))
        }
        // due reviews stay in the review queue. careful: can't use "odid or did", as sqlite converts to boolean
        val queue =
            "(CASE WHEN type = " + Consts.CARD_TYPE_REV + " AND (CASE WHEN odue THEN odue <= " + mToday +
                " ELSE due <= " + mToday + " END) THEN " + Consts.QUEUE_TYPE_REV + " ELSE " + Consts.QUEUE_TYPE_NEW + " END)"
        col.db.executeMany(
            "UPDATE cards SET odid = (CASE WHEN odid THEN odid ELSE did END), " +
                "odue = (CASE WHEN odue THEN odue ELSE due END), did = ?, queue = " +
                queue + ", due = ?, usn = ? WHERE id = ?",
            data
        )
    }

    private fun _dynIvlBoost(card: Card): Int {
        if (!card.isInDynamicDeck || card.type != Consts.CARD_TYPE_REV || card.factor == 0) {
            Timber.e("error: deck is not a filtered deck")
            return 0
        }
        val elapsed = card.ivl - (card.oDue - mToday!!)
        val factor = (card.factor / 1000.0 + 1.2) / 2.0
        val ivl = Math.max(1, Math.max(card.ivl, (elapsed * factor).toInt()))
        val conf = _revConf(card)
        return Math.min(conf.getInt("maxIvl"), ivl)
    }
    /*
      Leeches ****************************************************************** *****************************
     */
    /** Leech handler. True if card was a leech.  */
    override fun _checkLeech(card: Card, conf: JSONObject): Boolean {
        val lf = conf.getInt("leechFails")
        if (lf == 0) {
            return false
        }
        // if over threshold or every half threshold reps after that
        if (card.lapses >= lf && (card.lapses - lf) % Math.max(lf / 2, 1) == 0) {
            // add a leech tag
            val n = card.note(col)
            n.addTag("leech")
            n.flush(col)
            // handle
            if (conf.getInt("leechAction") == Consts.LEECH_SUSPEND) {
                // if it has an old due, remove it from cram/relearning
                if (card.oDue != 0L) {
                    card.due = card.oDue
                }
                if (card.isInDynamicDeck) {
                    card.did = card.oDid
                }
                card.oDue = 0
                card.oDid = 0
                card.queue = Consts.QUEUE_TYPE_SUSPENDED
            }
            // notify UI
            if (mContextReference != null) {
                val context = mContextReference!!.get()
                leech(card, context)
            }
            return true
        }
        return false
    }

    /**
     * Tools ******************************************************************** ***************************
     */
    override fun _newConf(card: Card): JSONObject {
        val conf = _cardConf(card)
        if (!card.isInDynamicDeck) {
            return conf.getJSONObject("new")
        }
        // dynamic deck; override some attributes, use original deck for others
        val oconf = col.decks.confForDid(card.oDid)

        @KotlinCleanup("use ?:")
        var delays = conf.optJSONArray("delays")
        if (delays == null) {
            delays = oconf.getJSONObject("new").getJSONArray("delays")
        }
        @KotlinCleanup("use apply with dict")
        val dict = JSONObject()
        // original deck
        dict.put("ints", oconf.getJSONObject("new").getJSONArray("ints"))
        dict.put("initialFactor", oconf.getJSONObject("new").getInt("initialFactor"))
        dict.put("bury", oconf.getJSONObject("new").optBoolean("bury", true))
        // overrides
        dict.put("delays", delays)
        dict.put("separate", conf.getBoolean("separate"))
        dict.put("order", Consts.NEW_CARDS_DUE)
        dict.put("perDay", mReportLimit)
        return dict
    }

    override fun _lapseConf(card: Card): JSONObject {
        val conf = _cardConf(card)
        if (!card.isInDynamicDeck) {
            return conf.getJSONObject("lapse")
        }
        // dynamic deck; override some attributes, use original deck for others
        val oconf = col.decks.confForDid(card.oDid)
        var delays = conf.optJSONArray("delays")
        @KotlinCleanup("use :?")
        if (delays == null) {
            delays = oconf.getJSONObject("lapse").getJSONArray("delays")
        }
        @KotlinCleanup("use apply with dict")
        val dict = JSONObject()
        // original deck
        dict.put("minInt", oconf.getJSONObject("lapse").getInt("minInt"))
        dict.put("leechFails", oconf.getJSONObject("lapse").getInt("leechFails"))
        dict.put("leechAction", oconf.getJSONObject("lapse").getInt("leechAction"))
        dict.put("mult", oconf.getJSONObject("lapse").getDouble("mult"))
        // overrides
        dict.put("delays", delays)
        dict.put("resched", conf.getBoolean("resched"))
        return dict
    }

    @KotlinCleanup("conf.getInt(dyn) == DECK_STD or conf.getBoolean(resched)")
    private fun _resched(card: Card): Boolean {
        val conf = _cardConf(card)
        return if (conf.getInt("dyn") == DECK_STD) {
            true
        } else {
            conf.getBoolean("resched")
        }
    }

    /**
     * Daily cutoff ************************************************************* **********************************
     * This function uses GregorianCalendar so as to be sensitive to leap years, daylight savings, etc.
     */
    override fun _updateCutoff() {
        val oldToday = mToday
        // days since col created
        mToday = ((time.intTime() - col.crt) / SECONDS_PER_DAY).toInt()
        // end of day cutoff
        dayCutoff = col.crt + (mToday!! + 1) * SECONDS_PER_DAY
        if (mToday != oldToday) {
            col.log(mToday, dayCutoff)
        }
        // update all daily counts, but don't save decks to prevent needless conflicts. we'll save on card answer
        // instead
        for (deck in col.decks.all()) {
            update(deck)
        }
        // unbury if the day has rolled over
        @Suppress("USELESS_CAST") // not useless
        val unburied: Int = col.get_config("lastUnburied", 0 as Int)!!
        if (unburied < mToday!!) {
            ignoreDatabaseModification { unburyCards() }
        }
    }

    /**
     * Deck finished state ******************************************************
     * *****************************************
     */
    @KotlinCleanup("convert to expression")
    override fun haveBuried(): Boolean {
        return haveBuried(col.decks.active())
    }

    @KotlinCleanup("convert to expression")
    private fun haveBuried(allDecks: List<Long>): Boolean {
        // Refactored to allow querying an arbitrary deck
        val sdids = Utils.ids2str(allDecks)
        val cnt = col.db.queryScalar(
            "select 1 from cards where " + queueIsBuriedSnippet() + " and did in " + sdids + " limit 1"
        )
        return cnt != 0
    }
    /*
      Next time reports ********************************************************
      ***************************************
     */
    /**
     * Return the next interval for CARD, in seconds.
     */
    override fun nextIvl(card: Card, @BUTTON_TYPE ease: Int): Long {
        return if (card.queue == Consts.QUEUE_TYPE_NEW || card.queue == Consts.QUEUE_TYPE_LRN || card.queue == Consts.QUEUE_TYPE_DAY_LEARN_RELEARN) {
            _nextLrnIvl(card, ease)
        } else if (ease == Consts.BUTTON_ONE) {
            // lapsed
            val conf = _lapseConf(card)
            if (conf.getJSONArray("delays").length() > 0) {
                (conf.getJSONArray("delays").getDouble(0) * 60.0).toLong()
            } else {
                _nextLapseIvl(
                    card,
                    conf
                ) * SECONDS_PER_DAY
            }
        } else {
            // review
            _nextRevIvl(card, ease) * SECONDS_PER_DAY
        }
    }

    override fun _nextLrnIvl(card: Card, @BUTTON_TYPE ease: Int): Long {
        // this isn't easily extracted from the learn code
        if (card.queue == Consts.QUEUE_TYPE_NEW) {
            card.left = _startingLeft(card)
        }
        val conf = _lrnConf(card)
        return if (ease == Consts.BUTTON_ONE) {
            // fail
            _delayForGrade(conf, conf.getJSONArray("delays").length()).toLong()
        } else if (ease == Consts.BUTTON_THREE) {
            // early removal
            if (!_resched(card)) {
                0
            } else {
                _graduatingIvl(
                    card,
                    conf,
                    true,
                    false
                ) * SECONDS_PER_DAY
            }
        } else {
            val left = card.left % 1000 - 1
            if (left <= 0) {
                // graduate
                if (!_resched(card)) {
                    0
                } else {
                    _graduatingIvl(
                        card,
                        conf,
                        false,
                        false
                    ) * SECONDS_PER_DAY
                }
            } else {
                _delayForGrade(conf, left).toLong()
            }
        }
    }
    /*
      Suspending *************************************************************** ********************************
     */
    /**
     * Suspend cards.
     */
    override fun suspendCards(ids: LongArray) {
        col.log(*ids.toTypedArray())
        remFromDyn(ids)
        removeLrn(ids)
        col.db.execute(
            "UPDATE cards SET queue = " + Consts.QUEUE_TYPE_SUSPENDED + ", mod = ?, usn = ? WHERE id IN " +
                Utils.ids2str(ids),
            time.intTime(),
            col.usn()
        )
    }

    override fun queueIsBuriedSnippet(): String {
        return "queue = " + Consts.QUEUE_TYPE_SIBLING_BURIED
    }

    override fun _restoreQueueSnippet(): String {
        return "queue = type"
    }

    /**
     * Unsuspend cards
     */
    override fun buryCards(cids: LongArray, manual: Boolean) {
        // The boolean is useless here. However, it ensures that we are override the method with same parameter in SchedV2.
        col.log(*cids.toTypedArray())
        remFromDyn(cids)
        removeLrn(cids)
        col.db.execute(
            "update cards set " + queueIsBuriedSnippet() + ",mod=?,usn=? where id in " + Utils.ids2str(
                cids
            ),
            time.intTime(),
            col.usn()
        )
    }

    /*
     * ***********************************************************
     * The methods below are not in LibAnki.
     * ***********************************************************
     */
    override fun haveBuried(did: Long): Boolean {
        val all: MutableList<Long> = ArrayList(col.decks.children(did).values)
        all.add(did)
        return haveBuried(all)
    }

    override fun unburyCardsForDeck(did: Long) {
        val all: MutableList<Long> = ArrayList(col.decks.children(did).values)
        all.add(did)
        unburyCardsForDeck(all)
    }

    /* Need to override. Otherwise it get SchedV2.mName variable*/
    @KotlinCleanup("don't need 'get'")
    override val name: String
        get() = "std"
    /*
      Counts
     */
    /**
     * This is used when card is currently in the reviewer, to adapt the counts by removing this card from it.
     *
     * @param discardCard A card sent to reviewer that should not be
     * counted.
     */
    override fun decrementCounts(discardCard: Card?) {
        if (discardCard == null) {
            return
        }
        @CARD_QUEUE val type = discardCard.queue
        when (type) {
            Consts.QUEUE_TYPE_NEW -> mNewCount--
            Consts.QUEUE_TYPE_LRN -> mLrnCount -= discardCard.left / 1000
            Consts.QUEUE_TYPE_REV -> mRevCount--
            Consts.QUEUE_TYPE_DAY_LEARN_RELEARN -> mLrnCount--
        }
    }

    /** The button to press on a new card to answer "good".*/
    override val goodNewButton: Int
        get() = Consts.BUTTON_TWO

    companion object {
        // Not in libanki
        private val FACTOR_ADDITION_VALUES = intArrayOf(-150, 0, 150)
    }
}
