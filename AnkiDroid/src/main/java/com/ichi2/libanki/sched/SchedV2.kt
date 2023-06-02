/****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2013 Houssam Salem <houssam.salem.au@gmail.com>                        *
 * Copyright (c) 2018 Chris Williams <chris@chrispwill.com>                             *
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

import android.annotation.SuppressLint
import android.app.Activity
import android.database.SQLException
import android.database.sqlite.SQLiteConstraintException
import androidx.annotation.VisibleForTesting
import com.ichi2.async.CancelListener
import com.ichi2.async.CancelListener.Companion.isCancelled
import com.ichi2.async.CollectionTask.Reset
import com.ichi2.async.TaskManager
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts.BUTTON_TYPE
import com.ichi2.libanki.Consts.CARD_QUEUE
import com.ichi2.libanki.Consts.DYN_PRIORITY
import com.ichi2.libanki.Consts.NEW_CARD_ORDER
import com.ichi2.libanki.Consts.REVLOG_TYPE
import com.ichi2.libanki.SortOrder.AfterSqlOrderBy
import com.ichi2.libanki.sched.Counts.Queue.*
import com.ichi2.libanki.sched.SchedV2.CountMethod
import com.ichi2.libanki.sched.SchedV2.LimitMethod
import com.ichi2.libanki.stats.Stats
import com.ichi2.libanki.utils.Time
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.utils.*
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.RustCleanup
import org.intellij.lang.annotations.Language
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.*

@KotlinCleanup("IDE Lint")
@KotlinCleanup("much to do - keep in line with libAnki")
@SuppressLint("VariableNamingDetector") // mCurrentCard: complex setter
open class SchedV2(col: Collection) : AbstractSched(col) {
    protected val mQueueLimit = 50
    protected var mReportLimit = 99999
    private val mDynReportLimit = 99999
    override var reps = 0
        protected set
    protected var haveQueues = false
    protected var mHaveCounts = false
    protected var mToday: Int? = null

    @KotlinCleanup("replace Sched.getDayCutoff() with dayCutoff")
    final override var dayCutoff: Long = 0
    private var mLrnCutoff: Long = 0
    protected var mNewCount = 0

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    internal var mLrnCount = 0
    protected var mRevCount = 0
    private var mNewCardModulus = 0

    // Queues
    protected val mNewQueue = SimpleCardQueue(this)
    protected val mLrnQueue = LrnCardQueue(this)
    protected val mLrnDayQueue = SimpleCardQueue(this)
    protected val mRevQueue = SimpleCardQueue(this)
    private var mNewDids = LinkedList<Long>()
    protected var mLrnDids = LinkedList<Long>()

    // Not in libanki
    protected var mContextReference: WeakReference<Activity>? = null

    fun interface LimitMethod {
        fun operation(g: Deck): Int
    }

    /** Given a deck, compute the number of cards to see today, taking its pre-computed limit into consideration.  It
     * considers either review or new cards. Used by WalkingCount to consider all subdecks and parents of a specific
     * decks.  */
    fun interface CountMethod {
        fun operation(did: Long, lim: Int): Int
    }

    /**
     * The card currently being reviewed.
     *
     * Must not be returned during prefetching (as it is currently shown)
     */
    @KotlinCleanup("fix naming")
    protected var mCurrentCard: Card? = null

    /** The list of parent decks of the current card.
     * Cached for performance .
     *
     * Null iff mNextCard is null. */
    protected var currentCardParentsDid: List<Long>? =
        null

    /**
     * Pop the next card from the queue. null if finished.
     */
    override val card: Card?
        get() {
            _checkDay()
            if (!haveQueues) {
                resetQueues(false)
            }
            var card = _getCard()
            if (card == null && !mHaveCounts) {
                // maybe we didn't refill queues because counts were not
                // set. This could only occur if the only card is a buried
                // sibling. So let's try to set counts and check again.
                reset()
                card = _getCard()
            }
            if (card != null) {
                col.log(card)
                incrReps()
                // In upstream, counts are decremented when the card is
                // gotten; i.e. in _getLrnCard, _getRevCard and
                // _getNewCard. This can not be done anymore since we use
                // those methods to pre-fetch the next card. Instead we
                // decrement the counts here, when the card is returned to
                // the reviewer.
                decrementCounts(card)
                setCurrentCard(card)
                card.startTimer()
            } else {
                discardCurrentCard()
            }
            if (!mHaveCounts) {
                // Need to reset queues once counts are reset
                TaskManager.launchCollectionTask(Reset())
            }
            return card
        }

    /** Ensures that reset is executed before the next card is selected  */
    override fun deferReset(undoneCard: Card?) {
        haveQueues = false
        mHaveCounts = false
        if (undoneCard != null) {
            setCurrentCard(undoneCard)
        } else {
            discardCurrentCard()
            col.decks.update_active()
        }
    }

    override fun reset() {
        col.decks.update_active()
        _updateCutoff()
        resetCounts(false)
        resetQueues(false)
    }

    fun resetCounts(cancelListener: CancelListener?) {
        resetCounts(cancelListener, true)
    }

    fun resetCounts(checkCutoff: Boolean) {
        resetCounts(null, checkCutoff)
    }

    override fun resetCounts() {
        resetCounts(null, true)
    }

    /** @param checkCutoff whether we should check cutoff before resetting
     */
    private fun resetCounts(cancelListener: CancelListener?, checkCutoff: Boolean) {
        if (checkCutoff) {
            _updateCutoff()
        }

        // Indicate that the counts can't be assumed to be correct since some are computed again and some not
        // In theory it is useless, as anything that change counts should have set mHaveCounts to false
        mHaveCounts = false
        _resetLrnCount(cancelListener)
        if (isCancelled(cancelListener)) {
            Timber.v("Cancel computing counts of deck %s", col.decks.current().getString("name"))
            return
        }
        _resetRevCount(cancelListener)
        if (isCancelled(cancelListener)) {
            Timber.v("Cancel computing counts of deck %s", col.decks.current().getString("name"))
            return
        }
        _resetNewCount(cancelListener)
        if (isCancelled(cancelListener)) {
            Timber.v("Cancel computing counts of deck %s", col.decks.current().getString("name"))
            return
        }
        mHaveCounts = true
    }

    /** @param checkCutoff whether we should check cutoff before resetting
     */
    private fun resetQueues(checkCutoff: Boolean) {
        if (checkCutoff) {
            _updateCutoff()
        }
        _resetLrnQueue()
        _resetRevQueue()
        _resetNewQueue()
        haveQueues = true
    }

    /**
     * Does all actions required to answer the card. That is:
     * Change its interval, due value, queue, mod time, usn, number of step left (if in learning)
     * Put it in learning if required
     * Log the review.
     * Remove from filtered if required.
     * Remove the siblings for the queue for same day spacing
     * Bury siblings if required by the options
     * Overridden
     */
    override fun answerCard(card: Card, @BUTTON_TYPE ease: Int) {
        col.log()
        discardCurrentCard()
        col.markReview(card)
        _burySiblings(card)

        _answerCard(card, ease)

        _updateStats(card, "time", card.timeTaken(col).toLong())
        card.mod = time.intTime()
        card.usn = col.usn()
        card.flushSched(col)
    }

    fun _answerCard(card: Card, @BUTTON_TYPE ease: Int) {
        if (_previewingCard(card)) {
            _answerCardPreview(card, ease)
            return
        }
        card.incrReps()
        if (card.queue == Consts.QUEUE_TYPE_NEW) {
            // came from the new queue, move to learning
            card.queue = Consts.QUEUE_TYPE_LRN
            card.type = Consts.CARD_TYPE_LRN
            // init reps to graduation
            card.left = _startingLeft(card)
            // update daily limit
            _updateStats(card, "new")
        }
        if (card.queue == Consts.QUEUE_TYPE_LRN || card.queue == Consts.QUEUE_TYPE_DAY_LEARN_RELEARN) {
            _answerLrnCard(card, ease)
        } else if (card.queue == Consts.QUEUE_TYPE_REV) {
            _answerRevCard(card, ease)
            // Update daily limit
            _updateStats(card, "rev")
        } else {
            throw RuntimeException("Invalid queue")
        }

        // once a card has been answered once, the original due date
        // no longer applies
        if (card.oDue > 0) {
            card.oDue = 0
        }
    }

    // note: when adding revlog entries in the future, make sure undo
    // code deletes the entries
    fun _answerCardPreview(card: Card, @BUTTON_TYPE ease: Int) {
        if (ease == Consts.BUTTON_ONE) {
            // Repeat after delay
            card.queue = Consts.QUEUE_TYPE_PREVIEW
            card.due = time.intTime() + _previewDelay(card)
            mLrnCount += 1
        } else if (ease == Consts.BUTTON_TWO) {
            // Restore original card state and remove from filtered deck
            _restorePreviewCard(card)
            _removeFromFiltered(card)
        } else {
            // This is in place of the assert
            throw RuntimeException("Invalid ease")
        }
    }

    /** new count, lrn count, rev count.   */
    override fun counts(cancelListener: CancelListener?): Counts {
        if (!mHaveCounts) {
            resetCounts(cancelListener)
        }
        return Counts(mNewCount, mLrnCount, mRevCount)
    }

    /**
     * Same as counts(), but also count `card`. In practice, we use it because `card` is in the reviewer and that is the
     * number we actually want.
     * Overridden: left / 1000 in V1
     */
    override fun counts(card: Card): Counts {
        val counts = counts()
        val idx = countIdx(card)
        counts.changeCount(idx, 1)
        return counts
    }

    /**
     * Which of the three numbers shown in reviewer/overview should the card be counted. 0:new, 1:rev, 2: any kind of learning.
     * Overridden: V1 does not have preview
     */
    override fun countIdx(card: Card): Counts.Queue {
        return when (card.queue) {
            Consts.QUEUE_TYPE_DAY_LEARN_RELEARN, Consts.QUEUE_TYPE_LRN, Consts.QUEUE_TYPE_PREVIEW -> LRN
            Consts.QUEUE_TYPE_NEW -> NEW
            Consts.QUEUE_TYPE_REV -> REV
            else -> throw RuntimeException("Index " + card.queue + " does not exists.")
        }
    }

    /** Number of buttons to show in the reviewer for `card`.
     * Overridden  */
    override fun answerButtons(card: Card): Int {
        val conf = _cardConf(card)
        return if (card.isInDynamicDeck && !conf.getBoolean("resched")) {
            2
        } else {
            4
        }
    }

    /**
     * Rev/lrn/time daily stats *************************************************
     * **********************************************
     */
    protected fun _updateStats(card: Card, type: String) {
        _updateStats(card, type, 1)
    }

    fun _updateStats(card: Card, type: String, cnt: Long) {
        val key = type + "Today"
        val did = card.did
        val list = col.decks.parents(did).toMutableList()
        list.add(col.decks.get(did))
        for (g in list) {
            val a = g.getJSONArray(key)
            // add
            a.put(1, a.getLong(1) + cnt)
            col.decks.save(g)
        }
    }

    override fun extendLimits(newc: Int, rev: Int) {
        if (!BackendFactory.defaultLegacySchema) {
            super.extendLimits(newc, rev)
            return
        }
        val cur = col.decks.current()
        val decks = col.decks.parents(cur.getLong("id")).toMutableList()
        decks.add(cur)
        for (did in col.decks.children(cur.getLong("id")).values) {
            decks.add(col.decks.get(did))
        }
        for (g in decks) {
            // add
            var today = g.getJSONArray("newToday")
            today.put(1, today.getInt(1) - newc)
            today = g.getJSONArray("revToday")
            today.put(1, today.getInt(1) - rev)
            col.decks.save(g)
        }
    }

    /**
     * @param limFn Method sending a deck to the maximal number of card it can have. Normally into account both limits and cards seen today
     * @param cntFn Method sending a deck to the number of card it has got to see today.
     * @param cancelListener Whether the task is not useful anymore
     * @return -1 if it's cancelled. Sum of the results of cntFn, limited by limFn,
     */
    protected fun _walkingCount(
        limFn: LimitMethod,
        cntFn: CountMethod,
        cancelListener: CancelListener? = null
    ): Int {
        var tot = 0
        val pcounts = HashUtil.HashMapInit<Long, Int>(col.decks.count())
        // for each of the active decks
        for (did in col.decks.active()) {
            if (isCancelled(cancelListener)) return -1
            // get the individual deck's limit
            var lim = limFn.operation(col.decks.get(did))
            if (lim == 0) {
                continue
            }
            // check the parents
            val parents = col.decks.parents(did)
            for (p in parents) {
                // add if missing
                val id = p.getLong("id")
                if (!pcounts.containsKey(id)) {
                    pcounts[id] = limFn.operation(p)
                }
                // take minimum of child and parent
                lim = Math.min(pcounts[id]!!, lim)
            }
            // see how many cards we actually have
            val cnt = cntFn.operation(did, lim)
            // if non-zero, decrement from parents counts
            for (p in parents) {
                val id = p.getLong("id")
                pcounts[id] = pcounts[id]!! - cnt
            }
            // we may also be a parent
            pcounts[did] = lim - cnt
            // and add to running total
            tot += cnt
        }
        return tot
    }

    /*
      Deck list **************************************************************** *******************************
     */
    /**
     * Returns [deckname, did, rev, lrn, new]
     *
     * Return nulls when deck task is cancelled.
     */
    @KotlinCleanup("remove/set default")
    private fun deckDueList(): List<DeckDueTreeNode> {
        return deckDueList(null)!!
    }
    // Overridden
    /**
     * Return sorted list of all decks. */
    protected open fun deckDueList(collectionTask: CancelListener?): List<DeckDueTreeNode>? {
        _checkDay()
        col.decks.checkIntegrity()
        val allDecksSorted = col.decks.allSorted()
        val lims = HashUtil.HashMapInit<String?, Array<Int>>(allDecksSorted.size)
        val deckNodes = ArrayList<DeckDueTreeNode>(allDecksSorted.size)
        val childMap = col.decks.childMap()
        for (deck in allDecksSorted) {
            if (isCancelled(collectionTask)) {
                return null
            }
            val deckName = deck.getString("name")
            val p = Decks.parent(deckName)
            // new
            var nlim = _deckNewLimitSingle(deck, false)
            var plim: Int? = null
            if (p?.isNotEmpty() == true) {
                val parentLims = lims[Decks.normalizeName(p)]
                // 'temporary for diagnosis of bug #6383'
                Assert.that(
                    parentLims != null,
                    "Deck %s is supposed to have parent %s. It has not be found.",
                    deckName,
                    p
                )
                nlim = Math.min(nlim, parentLims!![0])
                // reviews
                plim = parentLims[1]
            }
            val _new = _newForDeck(deck.getLong("id"), nlim)
            // learning
            val lrn = _lrnForDeck(deck.getLong("id"))
            // reviews
            val rlim = _deckRevLimitSingle(deck, plim, false)
            val rev = _revForDeck(deck.getLong("id"), rlim, childMap)
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

    /** Similar to deck due tree, but ignore the number of cards.
     *
     * It may takes a lot of time to compute the number of card, it
     * requires multiple database access by deck.  Ignoring this number
     * lead to the creation of a tree more quickly. */
    @RustCleanup("consider updating callers to use col.deckTreeLegacy() directly, and removing this")
    @Suppress("UNCHECKED_CAST")
    override fun <T : AbstractDeckTreeNode> quickDeckDueTree(): List<TreeNode<T>> {
        if (!BackendFactory.defaultLegacySchema) {
            return super.quickDeckDueTree()
        }

        // Similar to deckDueList
        @KotlinCleanup("simplify with map {}")
        val allDecksSorted = ArrayList<DeckTreeNode>()
        for (deck in col.decks.allSorted()) {
            val g = DeckTreeNode(deck.getString("name"), deck.getLong("id"))
            allDecksSorted.add(g)
        }
        // End of the similar part.
        return (_groupChildren(allDecksSorted, false) as List<TreeNode<T>>)
    }

    @RustCleanup("once defaultLegacySchema is removed, cancelListener can be removed")
    override fun deckDueTree(cancelListener: CancelListener?): List<TreeNode<DeckDueTreeNode>>? {
        if (!BackendFactory.defaultLegacySchema) {
            return super.deckDueTree(null)
        }
        _checkDay()
        val allDecksSorted = deckDueList(cancelListener) ?: return null
        return _groupChildren(allDecksSorted, true)
    }

    /**
     * @return the tree with `allDecksSorted` content.
     * @param allDecksSorted the set of all decks of the collection. Sorted.
     * @param checkDone Whether the set of deck was checked. If false, we can't assume all decks have parents
     * and that there is no duplicate. Instead, we'll ignore problems.
     */
    protected fun <T : AbstractDeckTreeNode> _groupChildren(
        allDecksSorted: List<T>,
        checkDone: Boolean
    ): List<TreeNode<T>> {
        return _groupChildren(allDecksSorted, 0, checkDone)
    }

    /**
     * @return the tree structure of all decks from @descendants, starting
     * at specified depth.
     * @param sortedDescendants a list of decks of dept at least depth, having all
     * the same first depth name elements, sorted in deck order.
     * @param depth The depth of the tree we are creating
     * @param checkDone whether the set of deck was checked. If
     * false, we can't assume all decks have parents and that there
     * is no duplicate. Instead, we'll ignore problems.
     */
    protected fun <T : AbstractDeckTreeNode> _groupChildren(
        sortedDescendants: List<T>,
        depth: Int,
        checkDone: Boolean
    ): List<TreeNode<T>> {
        val sortedChildren: MutableList<TreeNode<T>> = ArrayList()
        // group and recurse
        val it = sortedDescendants.listIterator()
        while (it.hasNext()) {
            val child = it.next()
            val head = child.getDeckNameComponent(depth)
            val sortedDescendantsOfChild: MutableList<T> = ArrayList()
            /* Compose the "sortedChildren" node list. The sortedChildren is a
             * list of all the nodes that proceed the current one that
             * contain the same at depth `depth`, except for the
             * current one itself.  I.e., they are subdecks that stem
             * from this descendant.  This is our version of python's
             * itertools.groupby. */if (!checkDone && child.depth != depth) {
                val deck = col.decks.get(child.did)
                Timber.d(
                    "Deck %s (%d)'s parent is missing. Ignoring for quick display.",
                    deck.getString("name"),
                    child.did
                )
                continue
            }
            while (it.hasNext()) {
                val descendantOfChild = it.next()
                if (head == descendantOfChild.getDeckNameComponent(depth)) {
                    // Same head - add to tail of current head.
                    if (!checkDone && descendantOfChild.depth == depth) {
                        val deck = col.decks.get(descendantOfChild.did)
                        Timber.d(
                            "Deck %s (%d)'s is a duplicate name. Ignoring for quick display.",
                            deck.getString("name"),
                            descendantOfChild.did
                        )
                        continue
                    }
                    sortedDescendantsOfChild.add(descendantOfChild)
                } else {
                    // We've iterated past this head, so step back in order to use this descendant as the
                    // head in the next iteration of the outer loop.
                    it.previous()
                    break
                }
            }
            // the childrenNode set contains direct child of `child`, but not
            // any descendants of the children of `child`...
            val childrenNode = _groupChildren(sortedDescendantsOfChild, depth + 1, checkDone)

            // Add the child nodes, and process the addition
            val toAdd = TreeNode(child)
            toAdd.children.addAll(childrenNode)
            val childValues = childrenNode.map { it.value }
            child.processChildren(col, childValues, "std" == name)
            sortedChildren.add(toAdd)
        }
        return sortedChildren
    }
    /*
      Getting the next card ****************************************************
      *******************************************
     */
    /**
     * Return the next due card, or null.
     * Overridden: V1 does not allow dayLearnFirst
     */
    protected open fun _getCard(): Card? {
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
        // Day learning first and card due?
        val dayLearnFirst = col.get_config("dayLearnFirst", false)!!
        if (dayLearnFirst) {
            c = _getLrnDayCard()
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
        if (!dayLearnFirst) {
            c = _getLrnDayCard()
            if (c != null) {
                return c
            }
        }
        // New cards left?
        c = _getNewCard()
        return c ?: _getLrnCard(true)
        // collapse or finish
    }

    /** similar to _getCard but only fill the queues without taking the card.
     * Returns lists that may contain the next cards.
     */
    protected open fun _fillNextCard(): Array<CardQueue<out Card.Cache>> {
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
        // Day learning first and card due?
        val dayLearnFirst = col.get_config("dayLearnFirst", false)!!
        if (dayLearnFirst) {
            if (_fillLrnDay()) {
                return arrayOf(mLrnQueue, mLrnDayQueue)
            }
        }
        // Card due for review?
        if (_fillRev()) {
            return arrayOf(mLrnQueue, mRevQueue)
        }
        // day learning card due?
        if (!dayLearnFirst) {
            if (_fillLrnDay()) {
                return arrayOf(mLrnQueue, mLrnDayQueue)
            }
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

    /** pre load the potential next card. It may loads many card because, depending on the time taken, the next card may
     * be a card in review or not.  */
    override fun preloadNextCard() {
        _checkDay()
        if (!mHaveCounts) {
            resetCounts(false)
        }
        if (!haveQueues) {
            resetQueues(false)
        }
        for (caches in _fillNextCard()) {
            caches.loadFirstCard()
        }
    }

    /**
     * New cards **************************************************************** *******************************
     */
    protected fun _resetNewCount(cancelListener: CancelListener? = null) {
        mNewCount = _walkingCount(
            LimitMethod { g: Deck -> _deckNewLimitSingle(g, true) },
            CountMethod { did: Long, lim: Int -> _cntFnNew(did, lim) },
            cancelListener
        )
    }

    // Used as an argument for _walkingCount() in _resetNewCount() above
    protected fun _cntFnNew(did: Long, lim: Int): Int {
        return col.db.queryScalar(
            "SELECT count() FROM (SELECT 1 FROM cards WHERE did = ? AND queue = " + Consts.QUEUE_TYPE_NEW + " AND id != ? LIMIT ?)",
            did,
            currentCardId(),
            lim
        )
    }

    private fun _resetNew() {
        _resetNewCount()
        _resetNewQueue()
    }

    private fun _resetNewQueue() {
        mNewDids = LinkedList(col.decks.active())
        mNewQueue.clear()
        _updateNewCardRatio()
    }

    /**
     * @return The id of the note currently in the reviewer. 0 if no
     * such card.
     */
    protected fun currentCardNid(): Long {
        val currentCard = mCurrentCard
        /* mCurrentCard may be set to null when the reviewer gets closed. So we copy it to be sure to avoid
           NullPointerException */return if (mCurrentCard == null) {
            /* This method is used to determine whether two cards are siblings. Since 0 is not a valid nid, all cards
            will have a nid distinct from 0. As it is used in sql statement, it is not possible to just use a function
            areSiblings()*/
            0
        } else {
            currentCard!!.nid
        }
    }

    /**
     * @return The id of the card currently in the reviewer. 0 if no
     * such card.
     */
    protected fun currentCardId(): Long {
        return if (mCurrentCard == null) {
            /* This method is used to ensure that query don't return current card. Since 0 is not a valid nid, all cards
            will have a nid distinct from 0. As it is used in sql statement, it is not possible to just use a function
            areSiblings()*/
            0
        } else {
            mCurrentCard!!.id
        }
    }

    protected fun _fillNew(): Boolean {
        return _fillNew(false)
    }

    private fun _fillNew(allowSibling: Boolean): Boolean {
        if (!mNewQueue.isEmpty) {
            return true
        }
        if (mHaveCounts && mNewCount == 0) {
            return false
        }
        while (!mNewDids.isEmpty()) {
            val did = mNewDids.first
            val lim = Math.min(mQueueLimit, _deckNewLimit(did, true))
            if (lim != 0) {
                mNewQueue.clear()
                val idName = if (allowSibling) "id" else "nid"
                val id = if (allowSibling) currentCardId() else currentCardNid()
                /* Difference with upstream: we take current card into account.
                     *
                     * When current card is answered, the card is not due anymore, so does not belong to the queue.
                     * Furthermore, _burySiblings ensure that the siblings of the current cards are removed from the
                     * queue to ensure same day spacing. We simulate this action by ensuring that those siblings are not
                     * filled, except if we know there are cards and we didn't find any non-sibling card. This way, the
                     * queue is not empty if it should not be empty (important for the conditional belows), but the
                     * front of the queue contains distinct card.
                 */
                // fill the queue with the current did
                for (
                cid in col.db.queryLongList(
                    "SELECT id FROM cards WHERE did = ? AND queue = " + Consts.QUEUE_TYPE_NEW + " AND " + idName + "!= ? ORDER BY due, ord LIMIT ?",
                    did,
                    id,
                    lim
                )
                ) {
                    mNewQueue.add(cid)
                }
                if (!mNewQueue.isEmpty) {
                    // Note: libanki reverses mNewQueue and returns the last element in _getNewCard().
                    // AnkiDroid differs by leaving the queue intact and returning the *first* element
                    // in _getNewCard().
                    return true
                }
            }
            // nothing left in the deck; move to next
            mNewDids.remove()
        }
        if (mHaveCounts && mNewCount != 0) {
            // if we didn't get a card but the count is non-zero,
            // we need to check again for any cards that were
            // removed from the queue but not buried
            _resetNew()
            return _fillNew(true)
        }
        return false
    }

    protected fun _getNewCard(): Card? {
        return if (_fillNew()) {
            // mNewCount -= 1; see decrementCounts()
            mNewQueue.removeFirstCard()
        } else {
            null
        }
    }

    private fun _updateNewCardRatio() {
        if (col.get_config_int("newSpread") == Consts.NEW_CARDS_DISTRIBUTE) {
            if (mNewCount != 0) {
                mNewCardModulus = (mNewCount + mRevCount) / mNewCount
                // if there are cards to review, ensure modulo >= 2
                if (mRevCount != 0) {
                    mNewCardModulus = Math.max(2, mNewCardModulus)
                }
                return
            }
        }
        mNewCardModulus = 0
    }

    /**
     * @return True if it's time to display a new card when distributing.
     */
    protected fun _timeForNewCard(): Boolean {
        if (mHaveCounts && mNewCount == 0) {
            return false
        }
        @NEW_CARD_ORDER val spread = col.get_config_int("newSpread")
        return if (spread == Consts.NEW_CARDS_LAST) {
            false
        } else if (spread == Consts.NEW_CARDS_FIRST) {
            true
        } else if (mNewCardModulus != 0) {
            // if the counter has not yet been reset, this value is
            // random. This will occur only for the first card of review.
            reps != 0 && reps % mNewCardModulus == 0
        } else {
            false
        }
    }

    /**
     *
     * @param considerCurrentCard Whether current card should be counted if it is in this deck
     */
    protected fun _deckNewLimit(did: Long, considerCurrentCard: Boolean): Int {
        return _deckNewLimit(did, null, considerCurrentCard)
    }

    /**
     *
     * @param considerCurrentCard Whether current card should be counted if it is in this deck
     */
    protected fun _deckNewLimit(did: Long, fn: LimitMethod?, considerCurrentCard: Boolean): Int {
        @Suppress("NAME_SHADOWING")
        var fn = fn
        if (fn == null) {
            fn = LimitMethod { g: Deck -> _deckNewLimitSingle(g, considerCurrentCard) }
        }
        val decks = col.decks.parents(did).toMutableList()
        decks.add(col.decks.get(did))
        var lim = -1
        // for the deck and each of its parents
        var rem: Int
        for (g in decks) {
            rem = fn.operation(g)
            lim = if (lim == -1) {
                rem
            } else {
                Math.min(rem, lim)
            }
        }
        return lim
    }

    /** New count for a single deck.  */
    fun _newForDeck(did: Long, lim: Int): Int {
        @Suppress("NAME_SHADOWING")
        var lim = lim
        if (lim == 0) {
            return 0
        }
        lim = Math.min(lim, mReportLimit)
        return col.db.queryScalar(
            "SELECT count() FROM (SELECT 1 FROM cards WHERE did = ? AND queue = " + Consts.QUEUE_TYPE_NEW + " LIMIT ?)",
            did,
            lim
        )
    }

    /**
     * Maximal number of new card still to see today in deck g. It's computed as:
     * the number of new card to see by day according to the deck options
     * minus the number of new cards seen today in deck d or a descendant
     * plus the number of extra new cards to see today in deck d, a parent or a descendant.
     *
     * Limits of its ancestors are not applied.
     * @param considerCurrentCard whether the current card should be taken from the limit (if it belongs to this deck)
     */
    fun _deckNewLimitSingle(g: Deck, considerCurrentCard: Boolean): Int {
        if (g.isDyn) {
            return mDynReportLimit
        }
        val did = g.getLong("id")
        val c = col.decks.confForDid(did)
        var lim = Math.max(
            0,
            c.getJSONObject("new").getInt("perDay") - g.getJSONArray("newToday").getInt(1)
        )
        // The counts shown in the reviewer does not consider the current card. E.g. if it indicates 6 new card, it means, 6 new card including current card will be seen today.
        // So currentCard does not have to be taken into consideration in this method
        if (considerCurrentCard && currentCardIsInQueueWithDeck(Consts.QUEUE_TYPE_NEW, did)) {
            lim--
        }
        return lim
    }

    /**
     * Learning queues *********************************************************** ************************************
     */
    private fun _updateLrnCutoff(force: Boolean): Boolean {
        val nextCutoff = time.intTime() + col.get_config_int("collapseTime")
        if (nextCutoff - mLrnCutoff > 60 || force) {
            mLrnCutoff = nextCutoff
            return true
        }
        return false
    }

    private fun _maybeResetLrn(force: Boolean) {
        if (_updateLrnCutoff(force)) {
            _resetLrn()
        }
    }

    // Overridden: V1 has less queues
    protected open fun _resetLrnCount() {
        _resetLrnCount(null)
    }

    protected open fun _resetLrnCount(cancelListener: CancelListener?) {
        _updateLrnCutoff(true)
        // sub-day
        mLrnCount = col.db.queryScalar(
            "SELECT count() FROM cards WHERE did IN " + _deckLimit() +
                " AND queue = " + Consts.QUEUE_TYPE_LRN + " AND id != ? AND due < ?",
            currentCardId(),
            mLrnCutoff
        )
        if (isCancelled(cancelListener)) return
        // day
        mLrnCount += col.db.queryScalar(
            "SELECT count() FROM cards WHERE did IN " + _deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + " AND due <= ? AND id != ?",
            mToday!!,
            currentCardId()
        )
        if (isCancelled(cancelListener)) return
        // previews
        mLrnCount += col.db.queryScalar(
            "SELECT count() FROM cards WHERE did IN " + _deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_PREVIEW + " AND id != ? ",
            currentCardId()
        )
    }

    // Overridden: _updateLrnCutoff not called in V1
    protected fun _resetLrn() {
        _resetLrnCount()
        _resetLrnQueue()
    }

    protected open fun _resetLrnQueue() {
        mLrnQueue.clear()
        mLrnDayQueue.clear()
        mLrnDids = col.decks.active()
    }

    // sub-day learning
    // Overridden: a single kind of queue in V1
    protected open fun _fillLrn(): Boolean {
        if (mHaveCounts && mLrnCount == 0) {
            return false
        }
        if (!mLrnQueue.isEmpty) {
            return true
        }
        val cutoff = time.intTime() + col.get_config_long("collapseTime")
        mLrnQueue.clear()
        col
            .db
            .query(
                "SELECT due, id FROM cards WHERE did IN " + _deckLimit() + " AND queue IN (" + Consts.QUEUE_TYPE_LRN + ", " + Consts.QUEUE_TYPE_PREVIEW + ") AND due < ?" +
                    " AND id != ? LIMIT ?",
                cutoff,
                currentCardId(),
                mReportLimit
            ).use { cur ->
                mLrnQueue.setFilled()
                while (cur.moveToNext()) {
                    mLrnQueue.add(cur.getLong(0), cur.getLong(1))
                }
                // as it arrives sorted by did first, we need to sort it
                mLrnQueue.sort()
                return !mLrnQueue.isEmpty
            }
    }

    // Overridden: no _maybeResetLrn in V1
    protected open fun _getLrnCard(collapse: Boolean): Card? {
        _maybeResetLrn(collapse && mLrnCount == 0)
        if (_fillLrn()) {
            var cutoff = time.intTime()
            if (collapse) {
                cutoff += col.get_config_int("collapseTime").toLong()
            }
            if (mLrnQueue.firstDue < cutoff) {
                return mLrnQueue.removeFirstCard()
                // mLrnCount -= 1; see decrementCounts()
            }
        }
        return null
    }

    protected fun _preloadLrnCard(collapse: Boolean): Boolean {
        _maybeResetLrn(collapse && mLrnCount == 0)
        if (_fillLrn()) {
            var cutoff = time.intTime()
            if (collapse) {
                cutoff += col.get_config_int("collapseTime").toLong()
            }
            // mLrnCount -= 1; see decrementCounts()
            return mLrnQueue.firstDue < cutoff
        }
        return false
    }

    // daily learning
    protected fun _fillLrnDay(): Boolean {
        if (mHaveCounts && mLrnCount == 0) {
            return false
        }
        if (!mLrnDayQueue.isEmpty) {
            return true
        }
        while (!mLrnDids.isEmpty()) {
            val did = mLrnDids.first
            // fill the queue with the current did
            mLrnDayQueue.clear()
            /* Difference with upstream:
                 * Current card can't come in the queue.
                 *
                 * In standard usage, a card is not requested before
                 * the previous card is marked as reviewed. However,
                 * if we decide to query a second card sooner, we
                 * don't want to get the same card a second time. This
                 * simulate _getLrnDayCard which did remove the card
                 * from the queue.
                 */for (
            cid in col.db.queryLongList(
                "SELECT id FROM cards WHERE did = ? AND queue = " + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + " AND due <= ? and id != ? LIMIT ?",
                did,
                mToday!!,
                currentCardId(),
                mQueueLimit
            )
            ) {
                mLrnDayQueue.add(cid)
            }
            if (!mLrnDayQueue.isEmpty) {
                // order
                @KotlinCleanup(".apply { }")
                val r = Random()
                r.setSeed(mToday!!.toLong())
                mLrnDayQueue.shuffle(r)
                // is the current did empty?
                if (mLrnDayQueue.size() < mQueueLimit) {
                    mLrnDids.remove()
                }
                return true
            }
            // nothing left in the deck; move to next
            mLrnDids.remove()
        }
        return false
    }

    protected fun _getLrnDayCard(): Card? {
        return if (_fillLrnDay()) {
            // mLrnCount -= 1; see decrementCounts()
            mLrnDayQueue.removeFirstCard()
        } else {
            null
        }
    }

    // Overridden
    protected open fun _answerLrnCard(card: Card, @BUTTON_TYPE ease: Int) {
        val conf = _lrnConf(card)

        @REVLOG_TYPE val type: Int
        type =
            if (card.type == Consts.CARD_TYPE_REV || card.type == Consts.CARD_TYPE_RELEARNING) {
                Consts.REVLOG_RELRN
            } else {
                Consts.REVLOG_LRN
            }

        // lrnCount was decremented once when card was fetched
        val lastLeft = card.left
        var leaving = false

        // immediate graduate?
        if (ease == Consts.BUTTON_FOUR) {
            _rescheduleAsRev(card, conf, true)
            leaving = true
            // next step?
        } else if (ease == Consts.BUTTON_THREE) {
            // graduation time?
            if (card.left % 1000 - 1 <= 0) {
                _rescheduleAsRev(card, conf, false)
                leaving = true
            } else {
                _moveToNextStep(card, conf)
            }
        } else if (ease == Consts.BUTTON_TWO) {
            _repeatStep(card, conf)
        } else {
            // move back to first step
            _moveToFirstStep(card, conf)
        }
        _logLrn(card, ease, conf, leaving, type, lastLeft)
    }

    protected fun _updateRevIvlOnFail(card: Card, conf: JSONObject) {
        card.lastIvl = card.ivl
        card.ivl = _lapseIvl(card, conf)
    }

    private fun _moveToFirstStep(card: Card, conf: JSONObject): Int {
        card.left = _startingLeft(card)

        // relearning card?
        if (card.type == Consts.CARD_TYPE_RELEARNING) {
            _updateRevIvlOnFail(card, conf)
        }
        return _rescheduleLrnCard(card, conf)
    }

    private fun _moveToNextStep(card: Card, conf: JSONObject) {
        // decrement real left count and recalculate left today
        val left = card.left % 1000 - 1
        card.left = _leftToday(conf.getJSONArray("delays"), left) * 1000 + left
        _rescheduleLrnCard(card, conf)
    }

    private fun _repeatStep(card: Card, conf: JSONObject) {
        val delay = _delayForRepeatingGrade(conf, card.left)
        _rescheduleLrnCard(card, conf, delay)
    }

    private fun _rescheduleLrnCard(card: Card, conf: JSONObject, delay: Int? = null): Int {
        // normal delay for the current step?
        @Suppress("NAME_SHADOWING")
        var delay = delay
        if (delay == null) {
            delay = _delayForGrade(conf, card.left)
        }
        card.due = time.intTime() + delay

        // due today?
        if (card.due < dayCutoff) {
            // Add some randomness, up to 5 minutes or 25%
            val maxExtra = Math.min(300, (delay * 0.25).toInt())
            val fuzz = Random().nextInt(Math.max(maxExtra, 1))
            card.due = Math.min(dayCutoff - 1, card.due + fuzz)
            card.queue = Consts.QUEUE_TYPE_LRN
            if (card.due < time.intTime() + col.get_config_int("collapseTime")) {
                mLrnCount += 1
                // if the queue is not empty and there's nothing else to do, make
                // sure we don't put it at the head of the queue and end up showing
                // it twice in a row
                if (!mLrnQueue.isEmpty && revCount() == 0 && newCount() == 0) {
                    val smallestDue = mLrnQueue.firstDue
                    card.due = Math.max(card.due, smallestDue + 1)
                }
                _sortIntoLrn(card.due, card.id)
            }
        } else {
            // the card is due in one or more days, so we need to use the day learn queue
            val ahead = (card.due - dayCutoff) / Stats.SECONDS_PER_DAY + 1
            card.due = mToday!! + ahead
            card.queue = Consts.QUEUE_TYPE_DAY_LEARN_RELEARN
        }
        return delay
    }

    protected fun _delayForGrade(conf: JSONObject, left: Int): Int {
        @Suppress("NAME_SHADOWING")
        var left = left
        left = left % 1000
        return try {
            val delay: Double
            val delays = conf.getJSONArray("delays")
            val len = delays.length()
            delay = try {
                delays.getDouble(len - left)
            } catch (e: JSONException) {
                Timber.w(e)
                if (conf.getJSONArray("delays").length() > 0) {
                    conf.getJSONArray("delays").getDouble(0)
                } else {
                    // user deleted final step; use dummy value
                    1.0
                }
            }
            (delay * 60.0).toInt()
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
    }

    private fun _delayForRepeatingGrade(conf: JSONObject, left: Int): Int {
        // halfway between last and  next
        val delay1 = _delayForGrade(conf, left)
        val delay2: Int
        delay2 = if (conf.getJSONArray("delays").length() > 1) {
            _delayForGrade(conf, left - 1)
        } else {
            delay1 * 2
        }
        return (delay1 + Math.max(delay1, delay2)) / 2
    }

    // Overridden: RELEARNING does not exists in V1
    protected open fun _lrnConf(card: Card): JSONObject {
        return if (card.type == Consts.CARD_TYPE_REV || card.type == Consts.CARD_TYPE_RELEARNING) {
            _lapseConf(card)
        } else {
            _newConf(card)
        }
    }

    // Overridden
    protected open fun _rescheduleAsRev(card: Card, conf: JSONObject, early: Boolean) {
        val lapse = card.type == Consts.CARD_TYPE_REV || card.type == Consts.CARD_TYPE_RELEARNING
        if (lapse) {
            _rescheduleGraduatingLapse(card, early)
        } else {
            _rescheduleNew(card, conf, early)
        }
        // if we were dynamic, graduating means moving back to the old deck
        if (card.isInDynamicDeck) {
            _removeFromFiltered(card)
        }
    }

    private fun _rescheduleGraduatingLapse(card: Card, early: Boolean) {
        if (early) {
            card.ivl = card.ivl + 1
        }
        card.apply {
            due = (mToday!! + card.ivl).toLong()
            queue = Consts.QUEUE_TYPE_REV
            type = Consts.CARD_TYPE_REV
        }
    }

    // Overridden: V1 has type rev for relearning
    protected open fun _startingLeft(card: Card): Int {
        val conf: JSONObject
        conf = if (card.type == Consts.CARD_TYPE_RELEARNING) {
            _lapseConf(card)
        } else {
            _lrnConf(card)
        }
        val tot = conf.getJSONArray("delays").length()
        val tod = _leftToday(conf.getJSONArray("delays"), tot)
        return tot + tod * 1000
    }

    /** the number of steps that can be completed by the day cutoff  */
    protected fun _leftToday(delays: JSONArray, left: Int): Int {
        return _leftToday(delays, left, 0)
    }

    private fun _leftToday(delays: JSONArray, left: Int, now: Long): Int {
        @Suppress("NAME_SHADOWING")
        var now = now
        if (now == 0L) {
            now = time.intTime()
        }
        var ok = 0
        val offset = Math.min(left, delays.length())
        for (i in 0 until offset) {
            now += (delays.getDouble(delays.length() - offset + i) * 60.0).toInt().toLong()
            if (now > dayCutoff) {
                break
            }
            ok = i
        }
        return ok + 1
    }

    protected fun _graduatingIvl(card: Card, conf: JSONObject, early: Boolean): Int {
        return _graduatingIvl(card, conf, early, true)
    }

    private fun _graduatingIvl(card: Card, conf: JSONObject, early: Boolean, fuzz: Boolean): Int {
        if (card.type == Consts.CARD_TYPE_REV || card.type == Consts.CARD_TYPE_RELEARNING) {
            val bonus = if (early) 1 else 0
            return card.ivl + bonus
        }
        var ideal: Int
        val ints = conf.getJSONArray("ints")
        ideal = if (!early) {
            // graduate
            ints.getInt(0)
        } else {
            // early remove
            ints.getInt(1)
        }
        if (fuzz) {
            ideal = _fuzzedIvl(ideal)
        }
        return ideal
    }

    /** Reschedule a new card that's graduated for the first time.
     * Overridden: V1 does not set type and queue */
    private fun _rescheduleNew(card: Card, conf: JSONObject, early: Boolean) {
        card.apply {
            ivl = _graduatingIvl(card, conf, early)
            due = (mToday!! + card.ivl).toLong()
            factor = conf.getInt("initialFactor")
            type = Consts.CARD_TYPE_REV
            queue = Consts.QUEUE_TYPE_REV
        }
    }

    protected fun _logLrn(
        card: Card,
        @BUTTON_TYPE ease: Int,
        conf: JSONObject,
        leaving: Boolean,
        @REVLOG_TYPE type: Int,
        lastLeft: Int
    ) {
        val lastIvl = -_delayForGrade(conf, lastLeft)
        val ivl = if (leaving) card.ivl else -_delayForGrade(conf, card.left)
        log(card.id, col.usn(), ease, ivl, lastIvl, card.factor, card.timeTaken(col), type)
    }

    protected fun log(
        id: Long,
        usn: Int,
        @BUTTON_TYPE ease: Int,
        ivl: Int,
        lastIvl: Int,
        factor: Int,
        timeTaken: Int,
        @REVLOG_TYPE type: Int
    ) {
        try {
            col.db.execute(
                "INSERT INTO revlog VALUES (?,?,?,?,?,?,?,?,?)",
                time.intTimeMS(), id, usn, ease, ivl, lastIvl, factor, timeTaken, type
            )
        } catch (e: SQLiteConstraintException) {
            Timber.w(e)
            try {
                Thread.sleep(10)
            } catch (e1: InterruptedException) {
                throw RuntimeException(e1)
            }
            log(id, usn, ease, ivl, lastIvl, factor, timeTaken, type)
        }
    }

    // Overridden: uses left/1000 in V1
    private fun _lrnForDeck(did: Long): Int {
        return try {
            val cnt = col.db.queryScalar(
                "SELECT count() FROM (SELECT null FROM cards WHERE did = ?" +
                    " AND queue = " + Consts.QUEUE_TYPE_LRN + " AND due < ?" +
                    " LIMIT ?)",
                did,
                time.intTime() + col.get_config_int("collapseTime"),
                mReportLimit
            )
            cnt + col.db.queryScalar(
                "SELECT count() FROM (SELECT null FROM cards WHERE did = ?" +
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
     * Maximal number of rev card still to see today in current deck. It's computed as:
     * the number of rev card to see by day according to the deck options
     * minus the number of rev cards seen today in this deck or a descendant
     * plus the number of extra cards to see today in this deck, a parent or a descendant.
     *
     * Respects the limits of its ancestor. Current card is treated the same way as other cards.
     * @param considerCurrentCard whether the current card should be taken from the limit (if it belongs to this deck)
     */
    private fun _currentRevLimit(considerCurrentCard: Boolean): Int {
        val d = col.decks.get(col.decks.selected(), false)
        return _deckRevLimitSingle(d, considerCurrentCard)
    }

    /**
     * Maximal number of rev card still to see today in deck d. It's computed as:
     * the number of rev card to see by day according to the deck options
     * minus the number of rev cards seen today in deck d or a descendant
     * plus the number of extra cards to see today in deck d, a parent or a descendant.
     *
     * Respects the limits of its ancestor
     * Overridden: V1 does not consider parents limit
     * @param considerCurrentCard whether the current card should be taken from the limit (if it belongs to this deck)
     */
    protected open fun _deckRevLimitSingle(d: Deck?, considerCurrentCard: Boolean): Int {
        return _deckRevLimitSingle(d, null, considerCurrentCard)
    }

    /**
     * Maximal number of rev card still to see today in deck d. It's computed as:
     * the number of rev card to see by day according to the deck options
     * minus the number of rev cards seen today in deck d or a descendant
     * plus the number of extra cards to see today in deck d, a parent or a descendant.
     *
     * Respects the limits of its ancestor, either given as parentLimit, or through direct computation.
     * @param parentLimit Limit of the parent, this is an upper bound on the limit of this deck
     * @param considerCurrentCard whether the current card should be taken from the limit (if it belongs to this deck)
     */
    @KotlinCleanup("remove unused parameter")
    private fun _deckRevLimitSingle(
        d: Deck?,
        @Suppress("UNUSED_PARAMETER") parentLimit: Int?,
        considerCurrentCard: Boolean
    ): Int {
        // invalid deck selected?
        if (d == null) {
            return 0
        }
        if (d.isDyn) {
            return mDynReportLimit
        }
        val did = d.getLong("id")
        val c = col.decks.confForDid(did)
        var lim = Math.max(
            0,
            c.getJSONObject("rev").getInt("perDay") - d.getJSONArray("revToday").getInt(1)
        )
        // The counts shown in the reviewer does not consider the current card. E.g. if it indicates 6 rev card, it means, 6 rev card including current card will be seen today.
        // So currentCard does not have to be taken into consideration in this method
        if (considerCurrentCard && currentCardIsInQueueWithDeck(Consts.QUEUE_TYPE_REV, did)) {
            lim--
        }
        return lim
    }

    protected fun _revForDeck(did: Long, lim: Int, childMap: Decks.Node): Int {
        @Suppress("NAME_SHADOWING")
        var lim = lim
        val dids = col.decks.childDids(did, childMap).toMutableList()
        dids.add(0, did)
        lim = Math.min(lim, mReportLimit)
        return col.db.queryScalar(
            "SELECT count() FROM (SELECT 1 FROM cards WHERE did in " + Utils.ids2str(dids) + " AND queue = " + Consts.QUEUE_TYPE_REV + " AND due <= ? LIMIT ?)",
            mToday!!,
            lim
        )
    }

    // Overridden: V1 uses _walkingCount
    @KotlinCleanup("see if the two versions of this function can be combined")
    protected open fun _resetRevCount() {
        _resetRevCount(null)
    }

    protected open fun _resetRevCount(cancelListener: CancelListener?) {
        val lim = _currentRevLimit(true)
        if (isCancelled(cancelListener)) return
        mRevCount = col.db.queryScalar(
            "SELECT count() FROM (SELECT id FROM cards WHERE did in " + _deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_REV + " AND due <= ? AND id != ? LIMIT ?)",
            mToday!!,
            currentCardId(),
            lim
        )
    }

    // Overridden: V1 remove clear
    protected fun _resetRev() {
        _resetRevCount()
        _resetRevQueue()
    }

    protected open fun _resetRevQueue() {
        mRevQueue.clear()
    }

    protected fun _fillRev(): Boolean {
        return _fillRev(false)
    }

    // Override: V1 loops over dids
    protected open fun _fillRev(allowSibling: Boolean): Boolean {
        if (!mRevQueue.isEmpty) {
            return true
        }
        if (mHaveCounts && mRevCount == 0) {
            return false
        }
        val lim = Math.min(mQueueLimit, _currentRevLimit(true))
        if (lim != 0) {
            mRevQueue.clear()
            // fill the queue with the current did
            val idName = if (allowSibling) "id" else "nid"
            val id = if (allowSibling) currentCardId() else currentCardNid()
            col.db.query(
                "SELECT id FROM cards WHERE did in " + _deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_REV + " AND due <= ? AND " + idName + " != ?" +
                    " ORDER BY due, random()  LIMIT ?",
                mToday!!,
                id,
                lim
            ).use { cur ->
                while (cur.moveToNext()) {
                    mRevQueue.add(cur.getLong(0))
                }
            }
            if (!mRevQueue.isEmpty) {
                // preserve order
                // Note: libanki reverses mRevQueue and returns the last element in _getRevCard().
                // AnkiDroid differs by leaving the queue intact and returning the *first* element
                // in _getRevCard().
                return true
            }
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

    protected fun _getRevCard(): Card? {
        return if (_fillRev()) {
            // mRevCount -= 1; see decrementCounts()
            mRevQueue.removeFirstCard()
        } else {
            null
        }
    }

    /**
     * Answering a review card **************************************************
     * *********************************************
     */
    // Overridden: v1 does not deal with early
    protected open fun _answerRevCard(card: Card, @BUTTON_TYPE ease: Int) {
        var delay = 0
        val early = card.isInDynamicDeck && card.oDue > mToday!!
        val type = if (early) 3 else 1
        if (ease == Consts.BUTTON_ONE) {
            delay = _rescheduleLapse(card)
        } else {
            _rescheduleRev(card, ease, early)
        }
        _logRev(card, ease, delay, type)
    }

    // Overridden
    protected open fun _rescheduleLapse(card: Card): Int {
        val conf = _lapseConf(card)
        card.lapses = card.lapses + 1
        card.factor = Math.max(1300, card.factor - 200)
        val delay: Int
        val suspended = _checkLeech(card, conf) && card.queue == Consts.QUEUE_TYPE_SUSPENDED
        if (conf.getJSONArray("delays").length() != 0 && !suspended) {
            card.type = Consts.CARD_TYPE_RELEARNING
            delay = _moveToFirstStep(card, conf)
        } else {
            // no relearning steps
            _updateRevIvlOnFail(card, conf)
            _rescheduleAsRev(card, conf, false)
            // need to reset the queue after rescheduling
            if (suspended) {
                card.queue = Consts.QUEUE_TYPE_SUSPENDED
            }
            delay = 0
        }
        return delay
    }

    private fun _lapseIvl(card: Card, conf: JSONObject): Int {
        return Math.max(
            1,
            Math.max(conf.getInt("minInt"), (card.ivl * conf.getDouble("mult")).toInt())
        )
    }

    protected fun _rescheduleRev(card: Card, @BUTTON_TYPE ease: Int, early: Boolean) {
        // update interval
        card.lastIvl = card.ivl
        if (early) {
            _updateEarlyRevIvl(card, ease)
        } else {
            _updateRevIvl(card, ease)
        }

        // then the rest
        card.factor = Math.max(1300, card.factor + FACTOR_ADDITION_VALUES[ease - 2])
        card.due = (mToday!! + card.ivl).toLong()

        // card leaves filtered deck
        _removeFromFiltered(card)
    }

    protected fun _logRev(card: Card, @BUTTON_TYPE ease: Int, delay: Int, type: Int) {
        log(
            card.id,
            col.usn(),
            ease,
            if (delay != 0) -delay else card.ivl,
            card.lastIvl,
            card.factor,
            card.timeTaken(col),
            type
        )
    }
    /*
      Interval management ******************************************************
      *****************************************
     */
    /**
     * Next interval for CARD, given EASE.
     */
    protected fun _nextRevIvl(card: Card, @BUTTON_TYPE ease: Int, fuzz: Boolean): Int {
        val delay = _daysLate(card)
        val conf = _revConf(card)
        val fct = card.factor / 1000.0
        val hardFactor = conf.optDouble("hardFactor", 1.2)
        val hardMin: Int
        hardMin = if (hardFactor > 1) {
            card.ivl
        } else {
            0
        }
        val ivl2 = _constrainedIvl(card.ivl * hardFactor, conf, hardMin.toDouble(), fuzz)
        if (ease == Consts.BUTTON_TWO) {
            return ivl2
        }
        val ivl3 = _constrainedIvl((card.ivl + delay / 2) * fct, conf, ivl2.toDouble(), fuzz)
        return if (ease == Consts.BUTTON_THREE) {
            ivl3
        } else {
            _constrainedIvl(
                (card.ivl + delay) * fct * conf.getDouble("ease4"),
                conf,
                ivl3.toDouble(),
                fuzz
            )
        }
    }

    fun _fuzzedIvl(ivl: Int): Int {
        val minMax = _fuzzIvlRange(ivl)
        // Anki's python uses random.randint(a, b) which returns x in [a, b] while the eq Random().nextInt(a, b)
        // returns x in [0, b-a), hence the +1 diff with libanki
        return Random().nextInt(minMax.second - minMax.first + 1) + minMax.first
    }

    protected fun _constrainedIvl(ivl: Double, conf: JSONObject, prev: Double, fuzz: Boolean): Int {
        var newIvl = (ivl * conf.optDouble("ivlFct", 1.0)).toInt()
        if (fuzz) {
            newIvl = _fuzzedIvl(newIvl)
        }
        newIvl = Math.max(Math.max(newIvl.toDouble(), prev + 1), 1.0).toInt()
        newIvl = Math.min(newIvl, conf.getInt("maxIvl"))
        return newIvl
    }

    /**
     * Number of days later than scheduled.
     */
    protected fun _daysLate(card: Card): Long {
        val due = if (card.isInDynamicDeck) card.oDue else card.due
        return Math.max(0, mToday!! - due)
    }

    // Overridden
    protected open fun _updateRevIvl(card: Card, @BUTTON_TYPE ease: Int) {
        card.ivl = _nextRevIvl(card, ease, true)
    }

    private fun _updateEarlyRevIvl(card: Card, @BUTTON_TYPE ease: Int) {
        card.ivl = _earlyReviewIvl(card, ease)
    }

    /** next interval for card when answered early+correctly  */
    private fun _earlyReviewIvl(card: Card, @BUTTON_TYPE ease: Int): Int {
        if (!card.isInDynamicDeck || card.type != Consts.CARD_TYPE_REV || card.factor == 0) {
            throw RuntimeException("Unexpected card parameters")
        }
        if (ease <= 1) {
            throw RuntimeException("Ease must be greater than 1")
        }
        val elapsed = card.ivl - (card.oDue - mToday!!)
        val conf = _revConf(card)
        var easyBonus = 1.0
        // early 3/4 reviews shouldn't decrease previous interval
        var minNewIvl = 1.0
        val factor: Double
        if (ease == Consts.BUTTON_TWO) {
            factor = conf.optDouble("hardFactor", 1.2)
            // hard cards shouldn't have their interval decreased by more than 50%
            // of the normal factor
            minNewIvl = factor / 2
        } else if (ease == 3) {
            factor = card.factor / 1000.0
        } else { // ease == 4
            factor = card.factor / 1000.0
            val ease4 = conf.getDouble("ease4")
            // 1.3 -> 1.15
            easyBonus = ease4 - (ease4 - 1) / 2
        }
        var ivl = Math.max(elapsed * factor, 1.0)

        // cap interval decreases
        ivl = Math.max(card.ivl * minNewIvl, ivl) * easyBonus
        return _constrainedIvl(ivl, conf, 0.0, false)
    }

    /*
      Dynamic deck handling ******************************************************************
      *****************************
     */
    override fun rebuildDyn(did: Long) {
        if (!BackendFactory.defaultLegacySchema) {
            super.rebuildDyn(did)
            return
        }
        val deck = col.decks.get(did)
        if (deck.isStd) {
            Timber.e("error: deck is not a filtered deck")
            return
        }
        // move any existing cards back first, then fill
        emptyDyn(did)
        val cnt = _fillDyn(deck)
        if (cnt == 0) {
            return
        }
        // and change to our new deck
        col.decks.select(did)
    }

    /**
     * Whether the filtered deck is empty
     * Overridden
     */
    private fun _fillDyn(deck: Deck): Int {
        val start = -100000
        var total = 0
        var ids: List<Long?>
        val terms = deck.getJSONArray("terms")
        for (term in terms.jsonArrayIterable()) {
            var search = term.getString(0)
            val limit = term.getInt(1)
            val order = term.getInt(2)
            val orderLimit = _dynOrder(order, limit)
            if (search.trim { it <= ' ' }.isNotEmpty()) {
                search = String.format(Locale.US, "(%s)", search)
            }
            search = String.format(Locale.US, "%s -is:suspended -is:buried -deck:filtered", search)
            ids = col.findCards(search, AfterSqlOrderBy(orderLimit))
            if (ids.isEmpty()) {
                return total
            }
            // move the cards over
            col.log(deck.getLong("id"), ids)
            _moveToDyn(deck.getLong("id"), ids, start + total)
            total += ids.size
        }
        return total
    }

    override fun emptyDyn(did: Long) {
        if (!BackendFactory.defaultLegacySchema) {
            super.emptyDyn(did)
            return
        }
        emptyDyn("did = $did")
    }

    /**
     * Generates the required SQL for order by and limit clauses, for dynamic decks.
     *
     * @param o deck["order"]
     * @param l deck["limit"]
     * @return The generated SQL to be suffixed to "select ... from ... order by "
     */
    protected fun _dynOrder(@DYN_PRIORITY o: Int, l: Int): String {
        val t: String
        t = when (o) {
            Consts.DYN_OLDEST -> "c.mod"
            Consts.DYN_RANDOM -> "random()"
            Consts.DYN_SMALLINT -> "ivl"
            Consts.DYN_BIGINT -> "ivl desc"
            Consts.DYN_LAPSES -> "lapses desc"
            Consts.DYN_ADDED -> "n.id"
            Consts.DYN_REVADDED -> "n.id desc"
            Consts.DYN_DUEPRIORITY -> String.format(
                Locale.US,
                "(case when queue=" + Consts.QUEUE_TYPE_REV + " and due <= %d then (ivl / cast(%d-due+0.001 as real)) else 100000+due end)",
                mToday,
                mToday
            )
            Consts.DYN_DUE -> // if we don't understand the term, default to due order
                "c.due"
            else -> "c.due"
        }
        return "$t limit $l"
    }

    protected fun _moveToDyn(did: Long, ids: List<Long?>, start: Int) {
        val deck = col.decks.get(did)
        val data = ArrayList<Array<Any?>>(ids.size)
        val u = col.usn()
        var due = start
        for (id in ids) {
            data.add(
                arrayOf(
                    did,
                    due,
                    u,
                    id
                )
            )
            due += 1
        }
        var queue = ""
        if (!deck.getBoolean("resched")) {
            queue = ", queue = " + Consts.QUEUE_TYPE_REV + ""
        }
        col.db.executeMany(
            "UPDATE cards SET odid = did, " +
                "odue = due, did = ?, due = (case when due <= 0 then due else ? end), usn = ? " + queue + " WHERE id = ?",
            data
        )
    }

    private fun _removeFromFiltered(card: Card) {
        if (card.isInDynamicDeck) {
            card.did = card.oDid
            card.oDue = 0
            card.oDid = 0
        }
    }

    private fun _restorePreviewCard(card: Card) {
        if (!card.isInDynamicDeck) {
            throw RuntimeException("ODid wasn't set")
        }
        card.due = card.oDue

        // learning and relearning cards may be seconds-based or day-based;
        // other types map directly to queues
        if (card.type == Consts.CARD_TYPE_LRN || card.type == Consts.CARD_TYPE_RELEARNING) {
            if (card.oDue > 1000000000) {
                card.queue = Consts.QUEUE_TYPE_LRN
            } else {
                card.queue = Consts.QUEUE_TYPE_DAY_LEARN_RELEARN
            }
        } else {
            card.queue = card.type
        }
    }
    /*
      Leeches ****************************************************************** *****************************
     */
    /** Leech handler. True if card was a leech.
     * Overridden: in V1, due and did are changed */
    protected open fun _checkLeech(card: Card, conf: JSONObject): Boolean {
        val lf = conf.getInt("leechFails")
        if (lf == 0) {
            return false
        }
        // if over threshold or every half threshold reps after that
        if (card.lapses >= lf && (card.lapses - lf) % Math.max(lf / 2, 1) == 0) {
            // add a leech tag
            val n = card.note(col)
            n.addTag("leech")
            n.flush()
            // handle
            if (conf.getInt("leechAction") == Consts.LEECH_SUSPEND) {
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
    // Overridden: different delays for filtered cards.
    protected open fun _newConf(card: Card): JSONObject {
        val conf = _cardConf(card)
        if (!card.isInDynamicDeck) {
            return conf.getJSONObject("new")
        }
        // dynamic deck; override some attributes, use original deck for others
        val oconf = col.decks.confForDid(card.oDid)
        return JSONObject().apply {
            // original deck
            put("ints", oconf.getJSONObject("new").getJSONArray("ints"))
            put("initialFactor", oconf.getJSONObject("new").getInt("initialFactor"))
            put("bury", oconf.getJSONObject("new").optBoolean("bury", true))
            put("delays", oconf.getJSONObject("new").getJSONArray("delays"))
            // overrides
            put("separate", conf.getBoolean("separate"))
            put("order", Consts.NEW_CARDS_DUE)
            put("perDay", mReportLimit)
        }
    }

    // Overridden: different delays for filtered cards.
    protected open fun _lapseConf(card: Card): JSONObject {
        val conf = _cardConf(card)
        if (!card.isInDynamicDeck) {
            return conf.getJSONObject("lapse")
        }
        // dynamic deck; override some attributes, use original deck for others
        val oconf = col.decks.confForDid(card.oDid)
        return JSONObject().apply {
            // original deck
            put("minInt", oconf.getJSONObject("lapse").getInt("minInt"))
            put("leechFails", oconf.getJSONObject("lapse").getInt("leechFails"))
            put("leechAction", oconf.getJSONObject("lapse").getInt("leechAction"))
            put("mult", oconf.getJSONObject("lapse").getDouble("mult"))
            put("delays", oconf.getJSONObject("lapse").getJSONArray("delays"))
            // overrides
            put("resched", conf.getBoolean("resched"))
        }
    }

    protected fun _revConf(card: Card): JSONObject {
        val conf = _cardConf(card)
        return if (!card.isInDynamicDeck) {
            conf.getJSONObject("rev")
        } else {
            col.decks.confForDid(card.oDid).getJSONObject("rev")
        }
    }

    private fun _previewingCard(card: Card): Boolean {
        val conf = _cardConf(card)
        return conf.isDyn && !conf.getBoolean("resched")
    }

    private fun _previewDelay(card: Card): Int {
        return _cardConf(card).optInt("previewDelay", 10) * 60
    }

    /**
     * Daily cutoff ************************************************************* **********************************
     * This function uses GregorianCalendar so as to be sensitive to leap years, daylight savings, etc.
     */
    /* Overridden: other way to count time*/
    open fun _updateCutoff() {
        val oldToday = if (mToday == null) 0 else mToday!!
        val timing = _timingToday()
        mToday = timing.daysElapsed
        dayCutoff = timing.nextDayAt
        if (oldToday != mToday) {
            col.log(mToday, dayCutoff)
        }
        // update all daily counts, but don't save decks to prevent needless conflicts. we'll save on card answer
        // instead
        for (deck in col.decks.all()) {
            update(deck)
        }
        // unbury if the day has rolled over
        val unburied: Int = @Suppress("USELESS_CAST")
        col.get_config("lastUnburied", 0 as Int)!!
        if (unburied < mToday!!) {
            SyncStatus.ignoreDatabaseModification { unburyCards() }
            col.set_config("lastUnburied", mToday)
        }
    }

    protected fun update(g: Deck) {
        for (t in arrayOf("new", "rev", "lrn", "time")) {
            val key = t + "Today"
            val tToday = g.getJSONArray(key)
            if (g.getJSONArray(key).getInt(0) != mToday) {
                tToday.put(0, mToday!!)
                tToday.put(1, 0)
            }
        }
    }

    fun _checkDay() {
        // check if the day has rolled over
        if (time.intTime() > dayCutoff) {
            reset()
        }
    }

    /** true if there are cards in learning, with review due the same
     * day, in the selected decks.  */
    /* not in upstream anki. As revDue and newDue, it's used to check
     * what to do when a deck is selected in deck picker. When this
     * method is called, we already know that no cards is due
     * immediately. It answers whether cards will be due later in the
     * same deck. */
    override fun hasCardsTodayAfterStudyAheadLimit(): Boolean {
        return if (!BackendFactory.defaultLegacySchema) {
            super.hasCardsTodayAfterStudyAheadLimit()
        } else {
            col.db.queryScalar(
                "SELECT 1 FROM cards WHERE did IN " + _deckLimit() +
                    " AND queue = " + Consts.QUEUE_TYPE_LRN + " LIMIT 1"
            ) != 0
        }
    }

    fun haveBuriedSiblings(): Boolean {
        return haveBuriedSiblings(col.decks.active())
    }

    private fun haveBuriedSiblings(allDecks: List<Long>): Boolean {
        // Refactored to allow querying an arbitrary deck
        val sdids = Utils.ids2str(allDecks)
        val cnt = col.db.queryScalar(
            "select 1 from cards where queue = " + Consts.QUEUE_TYPE_SIBLING_BURIED + " and did in " + sdids + " limit 1"
        )
        return cnt != 0
    }

    fun haveManuallyBuried(): Boolean {
        return haveManuallyBuried(col.decks.active())
    }

    private fun haveManuallyBuried(allDecks: List<Long>): Boolean {
        // Refactored to allow querying an arbitrary deck
        val sdids = Utils.ids2str(allDecks)
        val cnt = col.db.queryScalar(
            "select 1 from cards where queue = " + Consts.QUEUE_TYPE_MANUALLY_BURIED + " and did in " + sdids + " limit 1"
        )
        return cnt != 0
    }

    override fun haveBuried(): Boolean {
        return if (!BackendFactory.defaultLegacySchema) {
            super.haveBuried()
        } else {
            haveManuallyBuried() || haveBuriedSiblings()
        }
    }
    /*
      Next time reports ********************************************************
      ***************************************
     */
    /**
     * Return the next interval for CARD, in seconds.
     */
    // Overridden
    override fun nextIvl(card: Card, @BUTTON_TYPE ease: Int): Long {
        // preview mode?
        if (_previewingCard(card)) {
            return if (ease == Consts.BUTTON_ONE) {
                _previewDelay(card).toLong()
            } else {
                0
            }
        }
        // (re)learning?
        return if (card.queue == Consts.QUEUE_TYPE_NEW || card.queue == Consts.QUEUE_TYPE_LRN || card.queue == Consts.QUEUE_TYPE_DAY_LEARN_RELEARN) {
            _nextLrnIvl(card, ease)
        } else if (ease == Consts.BUTTON_ONE) {
            // lapse
            val conf = _lapseConf(card)
            if (conf.getJSONArray("delays").length() > 0) {
                (conf.getJSONArray("delays").getDouble(0) * 60.0).toLong()
            } else {
                _lapseIvl(card, conf) * Stats.SECONDS_PER_DAY
            }
        } else {
            // review
            val early = card.isInDynamicDeck && card.oDue > mToday!!
            if (early) {
                _earlyReviewIvl(card, ease) * Stats.SECONDS_PER_DAY
            } else {
                _nextRevIvl(card, ease, false) * Stats.SECONDS_PER_DAY
            }
        }
    }

    // this isn't easily extracted from the learn code
    // Overridden
    protected open fun _nextLrnIvl(card: Card, @BUTTON_TYPE ease: Int): Long {
        if (card.queue == Consts.QUEUE_TYPE_NEW) {
            card.left = _startingLeft(card)
        }
        val conf = _lrnConf(card)
        return if (ease == Consts.BUTTON_ONE) {
            // fail
            _delayForGrade(conf, conf.getJSONArray("delays").length()).toLong()
        } else if (ease == Consts.BUTTON_TWO) {
            _delayForRepeatingGrade(conf, card.left).toLong()
        } else if (ease == Consts.BUTTON_FOUR) {
            _graduatingIvl(
                card,
                conf,
                true,
                false
            ) * Stats.SECONDS_PER_DAY
        } else { // ease == 3
            val left = card.left % 1000 - 1
            if (left <= 0) {
                // graduate
                _graduatingIvl(
                    card,
                    conf,
                    false,
                    false
                ) * Stats.SECONDS_PER_DAY
            } else {
                _delayForGrade(conf, left).toLong()
            }
        }
    }
    /*
      Suspending & burying ********************************************************** ********************************
     */
    /**
     * learning and relearning cards may be seconds-based or day-based;
     * other types map directly to queues
     *
     * Overridden: in V1, queue becomes type.
     */
    protected open fun _restoreQueueSnippet(): String {
        return """queue = (case when type in (${Consts.CARD_TYPE_LRN},${Consts.CARD_TYPE_RELEARNING}) then
  (case when (case when odue then odue else due end) > 1000000000 then 1 else ${Consts.QUEUE_TYPE_DAY_LEARN_RELEARN} end)
else
  type
end)  """
    }

    /**
     * Overridden: in V1 only sibling buried exits. */
    protected open fun queueIsBuriedSnippet(): String {
        return " queue in (" + Consts.QUEUE_TYPE_SIBLING_BURIED + ", " + Consts.QUEUE_TYPE_MANUALLY_BURIED + ") "
    }

    /**
     * Suspend cards.
     *
     * Overridden: in V1 remove from dyn and lrn
     */
    override fun suspendCards(ids: LongArray) {
        if (!BackendFactory.defaultLegacySchema) {
            super.suspendCards(ids)
            return
        }
        col.log(*ids.toTypedArray())
        col.db.execute(
            "UPDATE cards SET queue = " + Consts.QUEUE_TYPE_SUSPENDED + ", mod = ?, usn = ? WHERE id IN " +
                Utils.ids2str(ids),
            time.intTime(),
            col.usn()
        )
    }

    /**
     * Unsuspend cards
     */
    override fun unsuspendCards(ids: LongArray) {
        if (!BackendFactory.defaultLegacySchema) {
            super.unsuspendCards(ids)
            return
        }
        col.log(*ids.toTypedArray())
        col.db.execute(
            "UPDATE cards SET " + _restoreQueueSnippet() + ", mod = ?, usn = ?" +
                " WHERE queue = " + Consts.QUEUE_TYPE_SUSPENDED + " AND id IN " + Utils.ids2str(ids),
            time.intTime(),
            col.usn()
        )
    }

    // Overridden: V1 also remove from dyns and lrn
    /**
     * Bury all cards with id in cids. Set as manual bury if [manual]
     */
    @VisibleForTesting
    override fun buryCards(cids: LongArray, manual: Boolean) {
        if (!BackendFactory.defaultLegacySchema) {
            super.buryCards(cids, manual)
            return
        }
        val queue =
            if (manual) Consts.QUEUE_TYPE_MANUALLY_BURIED else Consts.QUEUE_TYPE_SIBLING_BURIED
        col.log(*cids.toTypedArray())
        col.db.execute(
            "update cards set queue=?,mod=?,usn=? where id in " + Utils.ids2str(cids),
            queue,
            time.intTime(),
            col.usn()
        )
    }

    /**
     * Unbury the cards of deck [did] and its descendants.
     * @param type See [UnburyType]
     */
    override fun unburyCardsForDeck(did: Long, type: UnburyType) {
        if (!BackendFactory.defaultLegacySchema) {
            super.unburyCardsForDeck(did, type)
            return
        }
        val dids = col.decks.childDids(did, col.decks.childMap()).toMutableList()
        dids.add(did)
        unburyCardsForDeck(type, dids)
    }

    /**
     * Unbury the cards of some decks.
     * @param type See [UnburyType]
     * @param allDecks the decks from which cards should be unburied. If None, unbury for all decks.
     * Only cards directly in a deck of this lists are considered, not subdecks.
     */
    fun unburyCardsForDeck(type: UnburyType, allDecks: List<Long>?) {
        @Language("SQL")
        val queue = when (type) {
            UnburyType.ALL -> queueIsBuriedSnippet()
            UnburyType.MANUAL -> "queue = " + Consts.QUEUE_TYPE_MANUALLY_BURIED
            UnburyType.SIBLINGS -> "queue = " + Consts.QUEUE_TYPE_SIBLING_BURIED
        }
        val deckConstraint = if (allDecks == null) {
            ""
        } else {
            " and did in " + Utils.ids2str(allDecks)
        }
        col.log(col.db.queryLongList("select id from cards where $queue $deckConstraint"))
        col.db.execute(
            "update cards set mod=?,usn=?, " + _restoreQueueSnippet() + " where " + queue + deckConstraint,
            time.intTime(),
            col.usn()
        )
    }

    override fun unburyCards() {
        unburyCardsForDeck(UnburyType.ALL, null)
    }

    /**
     * Bury all cards for note until next session.
     * @param nid The id of the targeted note.
     */
    override fun buryNote(nid: Long) {
        if (!BackendFactory.defaultLegacySchema) {
            super.buryNote(nid)
            return
        }
        val cids = col.db.queryLongList(
            "SELECT id FROM cards WHERE nid = ? AND queue >= " + Consts.CARD_TYPE_NEW,
            nid
        ).toLongArray()
        buryCards(cids)
    }

    /**
     * Sibling spacing
     * ********************
     */
    protected fun _burySiblings(card: Card) {
        val toBury = ArrayList<Long>()
        val nconf = _newConf(card)
        val buryNew = nconf.optBoolean("bury", true)
        val rconf = _revConf(card)
        val buryRev = rconf.optBoolean("bury", true)
        col.db.query(
            "select id, queue from cards where nid=? and id!=? " +
                "and (queue=" + Consts.QUEUE_TYPE_NEW + " or (queue=" + Consts.QUEUE_TYPE_REV + " and due<=?))",
            card.nid,
            card.id,
            mToday!!
        ).use { cur ->
            while (cur.moveToNext()) {
                val cid = cur.getLong(0)
                val queue = cur.getInt(1)
                var queue_object: SimpleCardQueue
                if (queue == Consts.QUEUE_TYPE_REV) {
                    queue_object = mRevQueue
                    if (buryRev) {
                        toBury.add(cid)
                    }
                } else {
                    queue_object = mNewQueue
                    if (buryNew) {
                        toBury.add(cid)
                    }
                }
                // even if burying disabled, we still discard to give
                // same-day spacing
                queue_object.remove(cid)
            }
        }
        // then bury
        if (!toBury.isEmpty()) {
            buryCards(toBury.toLongArray(), false)
        }
    }
    /*
     * Resetting **************************************************************** *******************************
     */
    /** Put cards at the end of the new queue.  */
    override fun forgetCards(ids: List<Long>) {
        // Currently disabled, as this causes a breakage in some tests due to
        // the AnkiDroid implementation not using nextPos to determine next position.
        //        if (!BackendFactory.getDefaultLegacySchema()) {
        //            super.forgetCards(ids);
        //            return;
        //        }
        remFromDyn(ids)
        col.db.execute(
            "update cards set type=" + Consts.CARD_TYPE_NEW + ",queue=" + Consts.QUEUE_TYPE_NEW + ",ivl=0,due=0,odue=0,factor=" + Consts.STARTING_FACTOR +
                " where id in " + Utils.ids2str(ids)
        )
        val pmax =
            col.db.queryScalar("SELECT max(due) FROM cards WHERE type=" + Consts.CARD_TYPE_NEW + "")
        // takes care of mod + usn
        sortCards(ids, pmax + 1)
        col.log(ids)
    }

    /**
     * Put cards in review queue with a new interval in days (min, max).
     *
     * @param ids The list of card ids to be affected
     * @param imin the minimum interval (inclusive)
     * @param imax The maximum interval (inclusive)
     */
    override fun reschedCards(ids: List<Long>, imin: Int, imax: Int) {
        // Currently disabled, as this causes a breakage in the V2 tests due to
        // the use of a mocked time.
        //        if (!BackendFactory.getDefaultLegacySchema()) {
        //            super.reschedCards(ids, imin, imax);
        //            return;
        //        }
        val d = ArrayList<Array<Any?>>(ids.size)
        val t = mToday!!
        val mod = time.intTime()
        val rnd = Random()
        for (id in ids) {
            val r = rnd.nextInt(imax - imin + 1) + imin
            d.add(arrayOf(Math.max(1, r), r + t, col.usn(), mod, RESCHEDULE_FACTOR, id))
        }
        remFromDyn(ids)
        col.db.executeMany(
            "update cards set type=" + Consts.CARD_TYPE_REV + ",queue=" + Consts.QUEUE_TYPE_REV + ",ivl=?,due=?,odue=0, " +
                "usn=?,mod=?,factor=? where id=?",
            d
        )
        col.log(ids)
    }

    /**
     * Repositioning new cards **************************************************
     * *********************************************
     */
    override fun sortCards(
        cids: List<Long>,
        start: Int,
        step: Int,
        shuffle: Boolean,
        shift: Boolean
    ) {
        if (!BackendFactory.defaultLegacySchema) {
            super.sortCards(cids, start, step, shuffle, shift)
            return
        }
        val scids = Utils.ids2str(cids)
        val now = time.intTime()
        val nids = ArrayList<Long?>(cids.size)
        // List of cid from `cids` and its `nid`
        val cid2nid = ArrayList<Pair<Long, Long>>(cids.size)
        for (id in cids) {
            val nid = col.db.queryLongScalar("SELECT nid FROM cards WHERE id = ?", id)
            if (!nids.contains(nid)) {
                nids.add(nid)
            }
            cid2nid.add(Pair(id, nid))
        }
        if (nids.isEmpty()) {
            // no new cards
            return
        }
        // determine nid ordering
        val due = HashUtil.HashMapInit<Long?, Long>(nids.size)
        if (shuffle) {
            Collections.shuffle(nids)
        }
        for (c in nids.indices) {
            due[nids[c]] = (start + c * step).toLong()
        }
        val high = start + step * (nids.size - 1)
        // shift?
        if (shift) {
            val low = col.db.queryScalar(
                "SELECT min(due) FROM cards WHERE due >= ? AND type = " + Consts.CARD_TYPE_NEW + " AND id NOT IN " + scids,
                start
            )
            if (low != 0) {
                val shiftBy = high - low + 1
                col.db.execute(
                    "UPDATE cards SET mod = ?, usn = ?, due = due + ?" +
                        " WHERE id NOT IN " + scids + " AND due >= ? AND type = " + Consts.CARD_TYPE_NEW,
                    now,
                    col.usn(),
                    shiftBy,
                    low
                )
            }
        }
        // reorder cards
        val d = ArrayList<Array<Any?>>(cids.size)
        for (pair in cid2nid) {
            val cid = pair.first
            val nid = pair.second
            d.add(arrayOf(due[nid], now, col.usn(), cid))
        }
        col.db.executeMany("UPDATE cards SET due = ?, mod = ?, usn = ? WHERE id = ?", d)
    }

    override fun randomizeCards(did: Long) {
        if (!BackendFactory.defaultLegacySchema) {
            super.randomizeCards(did)
            return
        }
        val cids: List<Long> = col.db.queryLongList(
            "select id from cards where type = " + Consts.CARD_TYPE_NEW + " and did = ?",
            did
        )
        sortCards(cids, 1, 1, true, false)
    }

    override fun orderCards(did: Long) {
        if (!BackendFactory.defaultLegacySchema) {
            super.orderCards(did)
            return
        }
        val cids: List<Long> = col.db.queryLongList(
            "SELECT id FROM cards WHERE type = " + Consts.CARD_TYPE_NEW + " AND did = ? ORDER BY nid",
            did
        )
        sortCards(cids, 1, 1, false, false)
    }

    /**
     * Changing scheduler versions **************************************************
     * *********************************************
     */
    private fun _emptyAllFiltered() {
        col.db.execute(
            "update cards set did = odid, queue = (case when type = " + Consts.CARD_TYPE_LRN + " then " + Consts.QUEUE_TYPE_NEW + " when type = " + Consts.CARD_TYPE_RELEARNING + " then " + Consts.QUEUE_TYPE_REV + " else type end), type = (case when type = " + Consts.CARD_TYPE_LRN + " then " + Consts.CARD_TYPE_NEW + " when type = " + Consts.CARD_TYPE_RELEARNING + " then " + Consts.CARD_TYPE_REV + " else type end), due = odue, odue = 0, odid = 0, usn = ? where odid != 0",
            col.usn()
        )
    }

    private fun _removeAllFromLearning(schedVer: Int = 2) {
        // remove review cards from relearning
        if (schedVer == 1) {
            col.db.execute(
                "update cards set due = odue, queue = " + Consts.QUEUE_TYPE_REV + ", type = " + Consts.CARD_TYPE_REV + ", mod = ?, usn = ?, odue = 0 where queue in (" + Consts.QUEUE_TYPE_LRN + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ") and type in (" + Consts.CARD_TYPE_REV + "," + Consts.CARD_TYPE_RELEARNING + ")",
                time.intTime(),
                col.usn()
            )
        } else {
            col.db.execute(
                "update cards set due = ?+ivl, queue = " + Consts.QUEUE_TYPE_REV + ", type = " + Consts.CARD_TYPE_REV + ", mod = ?, usn = ?, odue = 0 where queue in (" + Consts.QUEUE_TYPE_LRN + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ") and type in (" + Consts.CARD_TYPE_REV + "," + Consts.CARD_TYPE_RELEARNING + ")",
                mToday,
                time.intTime(),
                col.usn()
            )
        }

        // remove new cards from learning
        forgetCards(col.db.queryLongList("select id from cards where queue in (" + Consts.QUEUE_TYPE_LRN + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ")"))
    }

    // v1 doesn't support buried/suspended (re)learning cards
    private fun _resetSuspendedLearning() {
        col.db.execute(
            "update cards set type = (case when type = " + Consts.CARD_TYPE_LRN + " then " + Consts.CARD_TYPE_NEW + " when type in (" + Consts.CARD_TYPE_REV + ", " + Consts.CARD_TYPE_RELEARNING + ") then " + Consts.CARD_TYPE_REV + " else type end), due = (case when odue then odue else due end), odue = 0, mod = ?, usn = ? where queue < 0",
            time.intTime(),
            col.usn()
        )
    }

    // no 'manually buried' queue in v1
    private fun _moveManuallyBuried() {
        col.db.execute(
            "update cards set queue=" + Consts.QUEUE_TYPE_SIBLING_BURIED + ", mod=? where queue=" + Consts.QUEUE_TYPE_MANUALLY_BURIED,
            time.intTime()
        )
    }

    // adding 'hard' in v2 scheduler means old ease entries need shifting
    // up or down
    private fun _remapLearningAnswers(sql: String) {
        col.db.execute("update revlog set " + sql + " and type in (" + Consts.REVLOG_LRN + ", " + Consts.REVLOG_RELRN + ")")
    }

    fun moveToV1() {
        _emptyAllFiltered()
        _removeAllFromLearning()
        _moveManuallyBuried()
        _resetSuspendedLearning()
        _remapLearningAnswers("ease=ease-1 where ease in (" + Consts.BUTTON_THREE + "," + Consts.BUTTON_FOUR + ")")
    }

    fun moveToV2() {
        _emptyAllFiltered()
        _removeAllFromLearning(1)
        _remapLearningAnswers("ease=ease+1 where ease in (" + Consts.BUTTON_TWO + "," + Consts.BUTTON_THREE + ")")
    }

    /*
     * ***********************************************************
     * The methods below are not in LibAnki.
     * ***********************************************************
     */
    // Overridden: In sched v1, a single type of burying exist
    override fun haveBuried(did: Long): Boolean {
        val all: MutableList<Long> = ArrayList(col.decks.children(did).values)
        all.add(did)
        return haveBuriedSiblings(all) || haveManuallyBuried(all)
    }

    open fun unburyCardsForDeck(did: Long) {
        val all: MutableList<Long> = ArrayList(col.decks.children(did).values)
        all.add(did)
        unburyCardsForDeck(UnburyType.ALL, all)
    }

    override val name: String
        get() = "std2"
    override var today: Int
        get() = mToday!!
        set(today) {
            mToday = today
        }

    protected fun incrReps() {
        reps++
    }

    protected fun decrReps() {
        reps--
    }

    /**
     * Change the counts to reflect that `card` should not be counted anymore. In practice, it means that the card has
     * been sent to the reviewer. Either through `getCard()` or through `undo`. Assumes that card's queue has not yet
     * changed.
     * Overridden */
    open fun decrementCounts(discardCard: Card?) {
        if (discardCard == null) {
            return
        }
        when (discardCard.queue) {
            Consts.QUEUE_TYPE_NEW -> mNewCount--
            Consts.QUEUE_TYPE_LRN, Consts.QUEUE_TYPE_DAY_LEARN_RELEARN, Consts.QUEUE_TYPE_PREVIEW -> mLrnCount--
            Consts.QUEUE_TYPE_REV -> mRevCount--
        }
    }

    /**
     * Sorts a card into the lrn queue LIBANKI: not in libanki
     */
    protected fun _sortIntoLrn(due: Long, id: Long) {
        if (!mLrnQueue.isFilled) {
            // We don't want to add an element to the queue if it's not yet assumed to have its normal content.
            // Adding anything is useless while the queue awaits being filled
            return
        }
        val i = mLrnQueue.listIterator()
        while (i.hasNext()) {
            if (i.next().due > due) {
                i.previous()
                break
            }
        }
        i.add(LrnCard(due, id))
    }

    fun leechActionSuspend(card: Card): Boolean {
        val conf = _cardConf(card).getJSONObject("lapse")
        return conf.getInt("leechAction") == Consts.LEECH_SUSPEND
    }

    override fun setContext(contextReference: WeakReference<Activity>) {
        mContextReference = contextReference
    }

    override fun undoReview(card: Card, wasLeech: Boolean) {
        // remove leech tag if it didn't have it before
        if (!wasLeech && card.note(col).hasTag("leech")) {
            card.note(col).delTag("leech")
            card.note(col).flush()
        }
        Timber.i("Undo Review of card %d, leech: %b", card.id, wasLeech)
        // write old data
        card.flush(col, false)
        val conf = _cardConf(card)
        val previewing = conf.isDyn && !conf.getBoolean("resched")
        if (!previewing) {
            // and delete revlog entry
            val last = col.db.queryLongScalar(
                "SELECT id FROM revlog WHERE cid = ? ORDER BY id DESC LIMIT 1",
                card.id
            )
            col.db.execute("DELETE FROM revlog WHERE id = $last")
        }
        // restore any siblings
        col.db.execute(
            "update cards set queue=type,mod=?,usn=? where queue=" + Consts.QUEUE_TYPE_SIBLING_BURIED + " and nid=?",
            time.intTime(),
            col.usn(),
            card.nid
        )
        // and finally, update daily count
        @CARD_QUEUE val n =
            if (card.queue == Consts.QUEUE_TYPE_DAY_LEARN_RELEARN || card.queue == Consts.QUEUE_TYPE_PREVIEW) Consts.QUEUE_TYPE_LRN else card.queue
        val type = arrayOf("new", "lrn", "rev")[n]
        _updateStats(card, type, -1)
        decrReps()
    }

    val time: Time
        get() = TimeManager.time

    /** Notifies the scheduler that there is no more current card. This is the case when a card is answered, when the
     * scheduler is reset... #5666  */
    fun discardCurrentCard() {
        mCurrentCard = null
        currentCardParentsDid = null
    }

    /**
     * This imitate the action of the method answerCard, except that it does not change the state of any card.
     *
     * It means in particular that: + it removes the siblings of card from all queues + change the next card if required
     * it also set variables, so that when querying the next card, the current card can be taken into account.
     */
    fun setCurrentCard(card: Card) {
        mCurrentCard = card
        val did = card.did
        val parents = col.decks.parents(did)
        val currentCardParentsDid: MutableList<Long> = ArrayList(parents.size + 1)
        for (parent in parents) {
            currentCardParentsDid.add(parent.getLong("id"))
        }
        currentCardParentsDid.add(did)
        // We set the member only once it is filled, to ensure we avoid null pointer exception if `discardCurrentCard`
        // were called during `setCurrentCard`.
        this.currentCardParentsDid = currentCardParentsDid
        _burySiblings(card)
        // if current card is next card or in the queue
        mRevQueue.remove(card.id)
        mNewQueue.remove(card.id)
    }

    protected fun currentCardIsInQueueWithDeck(@CARD_QUEUE queue: Int, did: Long): Boolean {
        // mCurrentCard may be set to null when the reviewer gets closed. So we copy it to be sure to avoid NullPointerException
        val currentCard = mCurrentCard
        val currentCardParentsDid = currentCardParentsDid
        return currentCard != null && currentCard.queue == queue && currentCardParentsDid != null && currentCardParentsDid.contains(
            did
        )
    }

    companion object {
        // Not in libanki
        private val FACTOR_ADDITION_VALUES = intArrayOf(-150, 0, 150)
        const val RESCHEDULE_FACTOR = Consts.STARTING_FACTOR
        fun _fuzzIvlRange(ivl: Int): Pair<Int, Int> {
            var fuzz: Int
            fuzz = if (ivl < 2) {
                return Pair(1, 1)
            } else if (ivl == 2) {
                return Pair(2, 3)
            } else if (ivl < 7) {
                (ivl * 0.25).toInt()
            } else if (ivl < 30) {
                Math.max(2, (ivl * 0.15).toInt())
            } else {
                Math.max(4, (ivl * 0.05).toInt())
            }
            // fuzz at least a day
            fuzz = Math.max(fuzz, 1)
            return Pair(ivl - fuzz, ivl + fuzz)
        }
    }
    /* The next card that will be sent to the reviewer. I.e. the result of a second call to getCard, which is not the
     * current card nor a sibling.
     */
    /**
     * card types: 0=new, 1=lrn, 2=rev, 3=relrn
     * queue types: 0=new, 1=(re)lrn, 2=rev, 3=day (re)lrn,
     * 4=preview, -1=suspended, -2=sibling buried, -3=manually buried
     * revlog types: 0=lrn, 1=rev, 2=relrn, 3=early review
     * positive revlog intervals are in days (rev), negative in seconds (lrn)
     * odue/odid store original due/did when cards moved to filtered deck
     *
     */
    init {
        _updateCutoff()
    }

    @Consts.BUTTON_TYPE
    override val goodNewButton: Int
        get() = Consts.BUTTON_THREE
}
