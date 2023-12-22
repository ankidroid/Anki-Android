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
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import anki.ankidroid.schedTimingTodayLegacyRequest
import anki.collection.OpChanges
import anki.collection.OpChangesWithCount
import anki.config.OptionalStringConfigKey
import anki.frontend.SchedulingStatesWithContext
import anki.i18n.FormatTimespanRequest
import anki.scheduler.*
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.R
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.SECONDS_PER_DAY
import com.ichi2.libanki.Card
import com.ichi2.libanki.CardId
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.DeckConfig
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.NoteId
import com.ichi2.libanki.Utils
import com.ichi2.libanki.utils.TimeManager.time
import timber.log.Timber

data class CurrentQueueState(
    val topCard: Card,
    val countsIndex: Counts.Queue,
    var states: SchedulingStates,
    val context: SchedulingContext,
    val counts: Counts,
    val timeboxReached: Collection.TimeboxReached?,
    val learnAheadSecs: Int,
    val customSchedulingJs: String
) {
    fun schedulingStatesWithContext(): SchedulingStatesWithContext {
        return anki.frontend.schedulingStatesWithContext {
            states = this@CurrentQueueState.states
            context = this@CurrentQueueState.context
        }
    }
}

@WorkerThread
open class Scheduler(val col: Collection) {
    /** Legacy API */
    open val card: Card?
        get() = queuedCards.cardsList.firstOrNull()?.card?.let {
            Card(col, it).apply { startTimer() }
        }

    fun currentQueueState(): CurrentQueueState? {
        val queue = queuedCards
        return queue.cardsList.firstOrNull()?.let {
            CurrentQueueState(
                topCard = Card(col, it.card).apply { startTimer() },
                countsIndex = when (it.queue) {
                    QueuedCards.Queue.NEW -> Counts.Queue.NEW
                    QueuedCards.Queue.LEARNING -> Counts.Queue.LRN
                    QueuedCards.Queue.REVIEW -> Counts.Queue.REV
                    QueuedCards.Queue.UNRECOGNIZED, null -> TODO("unrecognized queue")
                },
                states = it.states,
                context = it.context,
                counts = Counts(queue.newCount, queue.learningCount, queue.reviewCount),
                timeboxReached = col.timeboxReached(),
                learnAheadSecs = learnAheadSeconds(),
                customSchedulingJs = col.config.get("cardStateCustomizer") ?: ""
            )
        }
    }

    /** The time labels for the four answer buttons. */
    fun describeNextStates(states: SchedulingStates): List<String> {
        return col.backend.describeNextStates(states)
    }

    private val queuedCards: QueuedCards
        get() = col.backend.getQueuedCards(fetchLimit = 1, intradayLearningOnly = false)

    open fun answerCard(info: CurrentQueueState, ease: Int): OpChanges {
        return col.backend.answerCard(buildAnswer(info.topCard, info.states, ease)).also {
            reps += 1
        }
    }

    /** Legacy path, used by tests. */
    open fun answerCard(card: Card, ease: Int) {
        val top = queuedCards.cardsList.first()
        val answer = buildAnswer(card, top.states, ease)
        col.backend.answerCard(answer)
        reps += 1
        // tests assume the card was mutated
        card.load()
    }

    fun againIsLeech(info: CurrentQueueState): Boolean {
        return col.backend.stateIsLeech(info.states.again)
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

    /**
     * @return Number of new, rev and lrn card to review in selected deck. Sum of elements of counts.
     */
    fun totalCount(): Int {
        return counts().count()
    }

    fun counts(): Counts {
        return queuedCards.let {
            Counts(it.newCount, it.learningCount, it.reviewCount)
        }
    }

    // only used by tests
    fun newCount(): Int {
        return counts().new
    }

    // only used by a test
    fun lrnCount(): Int {
        return counts().lrn
    }

    /** Only used by tests. */
    fun countIdx(): Counts.Queue {
        return when (queuedCards.cardsList.first().queue) {
            QueuedCards.Queue.NEW -> Counts.Queue.NEW
            QueuedCards.Queue.LEARNING -> Counts.Queue.LRN
            QueuedCards.Queue.REVIEW -> Counts.Queue.REV
            QueuedCards.Queue.UNRECOGNIZED, null -> TODO("unrecognized queue")
        }
    }

    /** @return Number of repetitions today. Note that a repetition is the fact that the scheduler sent a card, and not the fact that the card was answered.
     * So buried, suspended, ... cards are also counted as repetitions.
     */
    var reps: Int = 0

    /** Only provided for legacy unit tests. */
    fun nextIvl(card: Card, ease: Int): Long {
        val states = col.backend.getSchedulingStates(card.id)
        val state = stateFromEase(states, ease)
        return intervalForState(state)
    }

    /** Update a V1 scheduler collection to V2. Requires full sync. */
    fun upgradeToV2() {
        col.modSchema()
        col.backend.upgradeScheduler()
        col._loadScheduler()
    }

    /**
     * @param cids Ids of cards to bury
     */
    fun buryCards(cids: Iterable<CardId>): OpChangesWithCount {
        return buryCards(cids, manual = true)
    }

    /**
     * @param ids Id of cards to suspend
     */
    open fun suspendCards(ids: Iterable<CardId>): OpChangesWithCount {
        return col.backend.buryOrSuspendCards(
            cardIds = ids.toList(),
            noteIds = listOf(),
            mode = BuryOrSuspendCardsRequest.Mode.SUSPEND
        )
    }

    open fun suspendNotes(ids: Iterable<NoteId>): OpChangesWithCount {
        return col.backend.buryOrSuspendCards(
            cardIds = listOf(),
            noteIds = ids,
            mode = BuryOrSuspendCardsRequest.Mode.SUSPEND
        )
    }

    /**
     * @param ids Id of cards to unsuspend
     */
    open fun unsuspendCards(ids: Iterable<CardId>): OpChanges {
        return col.backend.restoreBuriedAndSuspendedCards(
            cids = ids.toList()
        )
    }

    /**
     * @param cids Ids of the cards to bury
     * @param manual Whether bury is made manually or not. Only useful for sched v2.
     */
    @VisibleForTesting
    open fun buryCards(cids: Iterable<CardId>, manual: Boolean): OpChangesWithCount {
        val mode = if (manual) {
            BuryOrSuspendCardsRequest.Mode.BURY_USER
        } else {
            BuryOrSuspendCardsRequest.Mode.BURY_SCHED
        }
        return col.backend.buryOrSuspendCards(
            cardIds = cids,
            noteIds = listOf(),
            mode = mode
        )
    }

    /**
     * Bury all cards for note until next session.
     * @param nid The id of the targeted note.
     */
    open fun buryNotes(nids: List<NoteId>): OpChangesWithCount {
        return col.backend.buryOrSuspendCards(
            cardIds = listOf(),
            noteIds = nids,
            mode = BuryOrSuspendCardsRequest.Mode.BURY_USER
        )
    }

    /**
     * Unbury cards.
     * @param type Which kind of cards should be unburied. See [UnburyType]
     * @param did: the deck whose cards must be unburied
     */
    open fun unburyCardsForDeck(did: DeckId, type: UnburyType = UnburyType.ALL) {
        val mode = when (type) {
            UnburyType.ALL -> UnburyDeckRequest.Mode.ALL
            UnburyType.MANUAL -> UnburyDeckRequest.Mode.USER_ONLY
            UnburyType.SIBLINGS -> UnburyDeckRequest.Mode.SCHED_ONLY
        }
        col.backend.unburyDeck(deckId = did, mode = mode)
    }

    /**
     * Parameter to describe what kind of cards must be unburied.
     */
    enum class UnburyType {
        /**
         * Represents all buried cards
         */
        ALL,

        /**
         * Represents cards that have been buried explicitly by the user using the reviewer
         */
        MANUAL,

        /**
         * Represents cards that were buried because they are the siblings of a reviewed cards.
         */
        SIBLINGS
    }

    /**
     * Unbury all buried cards in selected decks
     */
    fun unburyCardsForDeck(type: UnburyType = UnburyType.ALL) {
        unburyCardsForDeck(col.decks.selected(), type)
    }

    /**
     * @return Whether there are buried card is selected deck
     */
    open fun haveBuriedInCurrentDeck(): Boolean {
        return col.backend.congratsInfo().run {
            haveUserBuried || haveSchedBuried
        }
    }

    /** @return whether there are cards in learning, with review due the same
     * day, in the selected decks.
     */
    open fun hasCardsTodayAfterStudyAheadLimit(): Boolean {
        return col.backend.congratsInfo().secsUntilNextLearn < 86_400
    }

    /**
     * @param ids Ids of cards to put at the end of the new queue.
     */
    open fun forgetCards(ids: List<CardId>): OpChanges {
        val request = scheduleCardsAsNewRequest {
            cardIds.addAll(ids)
            log = true
            restorePosition = false
            resetCounts = false
        }
        return col.backend.scheduleCardsAsNew(request)
    }

    /**
     * Put cards in review queue with a new interval in days (min, max).
     *
     * @param ids The list of card ids to be affected
     * @param imin the minimum interval (inclusive)
     * @param imax The maximum interval (inclusive)
     */
    open fun reschedCards(ids: List<CardId>, imin: Int, imax: Int): OpChanges {
        return col.backend.setDueDate(ids, "$imin-$imax!", OptionalStringConfigKey.getDefaultInstance())
    }

    /**
     * @param cids Ids of card to set to new and sort
     * @param start The lowest due value for those cards
     * @param step The step between two successive due value set to those cards
     * @param shuffle Whether the list should be shuffled.
     * @param shift Whether the cards already new should be shifted to make room for cards of cids
     */
    open fun sortCards(
        cids: List<CardId>,
        start: Int,
        step: Int = 1,
        shuffle: Boolean = false,
        shift: Boolean = false
    ): OpChangesWithCount {
        return col.backend.sortCards(
            cardIds = cids,
            startingFrom = start,
            stepSize = step,
            randomize = shuffle,
            shiftExisting = shift
        )
    }

    /**
     * Randomize the cards of did
     * @param did Id of a deck
     */
    open fun randomizeCards(did: DeckId) {
        col.backend.sortDeck(deckId = did, randomize = true)
    }

    /**
     * Sort the cards of deck `id` by creation date of the note
     * @param did Id of a deck
     */
    open fun orderCards(did: DeckId) {
        col.backend.sortDeck(deckId = did, randomize = false)
    }

    /**
     * @param newc Extra number of NEW cards to see today in selected deck
     * @param rev Extra number of REV cards to see today in selected deck
     */
    open fun extendLimits(newc: Int, rev: Int) {
        col.backend.extendLimits(
            deckId = col.decks.selected(),
            newDelta = newc,
            reviewDelta = rev
        )
    }

    /** Rebuild a dynamic deck.
     * @param did The deck to rebuild. 0 means current deck.
     */
    open fun rebuildDyn(did: DeckId) {
        col.backend.rebuildFilteredDeck(did)
    }

    fun rebuildDyn() {
        rebuildDyn(col.decks.selected())
    }

    /** Remove all cards from a dynamic deck
     * @param did The deck to empty. 0 means current deck.
     */
    open fun emptyDyn(did: DeckId) {
        col.backend.emptyFilteredDeck(did)
    }

    fun deckDueTree(): DeckNode {
        return deckTree(true)
    }

    fun deckTree(includeCounts: Boolean): DeckNode {
        return DeckNode(col.backend.deckTree(now = if (includeCounts) time.intTime() else 0), "")
    }

    fun deckLimit(): String {
        return Utils.ids2str(col.decks.active())
    }

    /**
     * @return Number of new card in current deck and its descendants. Capped at [REPORT_LIMIT]
     */
    fun totalNewForCurrentDeck(): Int {
        return col.db.queryScalar(
            "SELECT count() FROM cards WHERE id IN (SELECT id FROM cards WHERE did IN " + deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_NEW + " LIMIT ?)",
            REPORT_LIMIT
        )
    }

    /** @return Number of review cards in current deck.
     */
    fun totalRevForCurrentDeck(): Int {
        return col.db.queryScalar(
            "SELECT count() FROM cards WHERE id IN (SELECT id FROM cards WHERE did IN " + deckLimit() + "  AND queue = " + Consts.QUEUE_TYPE_REV + " AND due <= ? LIMIT ?)",
            today,
            REPORT_LIMIT
        )
    }

    fun studiedToday(): String {
        return col.backend.studiedToday()
    }

    /**
     * @return Number of days since creation of the collection.
     */
    open val today: Int
        get() = timingToday().daysElapsed

    /**
     * @return Timestamp of when the day ends. Takes into account hour at which day change for anki and timezone
     */
    open val dayCutoff: Long
        get() = timingToday().nextDayAt

    /* internal */
    fun timingToday(): SchedTimingTodayResponse {
        return if (true) { // (BackendFactory.defaultLegacySchema) {
            val request = schedTimingTodayLegacyRequest {
                createdSecs = col.crt
                col.config.get<Int?>("creationOffset")?.let {
                    createdMinsWest = it
                }
                nowSecs = time.intTime()
                nowMinsWest = currentTimezoneOffset()
                rolloverHour = rolloverHour()
            }
            return col.backend.schedTimingTodayLegacy(request)
        } else {
            // this currently breaks a bunch of unit tests that assume a mocked time,
            // as it uses the real time to calculate daysElapsed
            col.backend.schedTimingToday()
        }
    }

    fun rolloverHour(): Int {
        return col.config.get("rollover") ?: 4
    }

    open fun currentTimezoneOffset(): Int {
        return localMinutesWest(time.intTime())
    }

    /**
     * For the given timestamp, return minutes west of UTC in the local timezone.
     *
     * eg, Australia at +10 hours is -600.
     * Includes the daylight savings offset if applicable.
     *
     * @param timestampSeconds The timestamp in seconds
     * @return minutes west of UTC in the local timezone
     */
    fun localMinutesWest(timestampSeconds: Long): Int {
        return col.backend.localMinutesWestLegacy(timestampSeconds)
    }

    /**
     * Save the UTC west offset at the time of creation into the DB.
     * Once stored, this activates the new timezone handling code.
     */
    fun setCreationOffset() {
        val minsWest = localMinutesWest(col.crt)
        col.config.set("creationOffset", minsWest)
    }

    // New timezone handling
    // ////////////////////////////////////////////////////////////////////////

    fun newTimezoneEnabled(): Boolean {
        return col.config.get<Int?>("creationOffset") != null
    }

    fun useNewTimezoneCode() {
        setCreationOffset()
    }

    fun clearCreationOffset() {
        col.config.remove("creationOffset")
    }

    /** true if there are any rev cards due.  */
    open fun revDue(): Boolean {
        return col.db
            .queryScalar(
                "SELECT 1 FROM cards WHERE did IN " + deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_REV + " AND due <= ?" +
                    " LIMIT 1",
                today
            ) != 0
    }

    /** true if there are any new cards due.  */
    open fun newDue(): Boolean {
        return col.db.queryScalar("SELECT 1 FROM cards WHERE did IN " + deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_NEW + " LIMIT 1") != 0
    }

    /** @return Number of cards in the current deck and its descendants.
     */
    fun cardCount(): Int {
        val dids = deckLimit()
        return col.db.queryScalar("SELECT count() FROM cards WHERE did IN $dids")
    }

    private val etaCache: DoubleArray = doubleArrayOf(-1.0, -1.0, -1.0, -1.0, -1.0, -1.0)

    /**
     * Return an estimate, in minutes, for how long it will take to complete all the reps in `counts`.
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
    fun eta(counts: Counts, reload: Boolean = true): Int {
        var newRate: Double
        var newTime: Double
        var revRate: Double
        var revTime: Double
        var relrnRate: Double
        var relrnTime: Double
        if (reload || etaCache.get(0) == -1.0) {
            col
                .db
                .query(
                    "select " +
                        "avg(case when type = " + Consts.CARD_TYPE_NEW + " then case when ease > 1 then 1.0 else 0.0 end else null end) as newRate, avg(case when type = " + Consts.CARD_TYPE_NEW + " then time else null end) as newTime, " +
                        "avg(case when type in (" + Consts.CARD_TYPE_LRN + ", " + Consts.CARD_TYPE_RELEARNING + ") then case when ease > 1 then 1.0 else 0.0 end else null end) as revRate, avg(case when type in (" + Consts.CARD_TYPE_LRN + ", " + Consts.CARD_TYPE_RELEARNING + ") then time else null end) as revTime, " +
                        "avg(case when type = " + Consts.CARD_TYPE_REV + " then case when ease > 1 then 1.0 else 0.0 end else null end) as relrnRate, avg(case when type = " + Consts.CARD_TYPE_REV + " then time else null end) as relrnTime " +
                        "from revlog where id > " +
                        "?",
                    (col.sched.dayCutoff - (10 * SECONDS_PER_DAY)) * 1000
                ).use { cur ->
                    if (!cur.moveToFirst()) {
                        return -1
                    }
                    newRate = cur.getDouble(0)
                    newTime = cur.getDouble(1)
                    revRate = cur.getDouble(2)
                    revTime = cur.getDouble(3)
                    relrnRate = cur.getDouble(4)
                    relrnTime = cur.getDouble(5)
                }

            // If the collection has no revlog data to work with, assume a 20 second average rep for that type
            newTime = if (newTime == 0.0) 20000.0 else newTime
            revTime = if (revTime == 0.0) 20000.0 else revTime
            relrnTime = if (relrnTime == 0.0) 20000.0 else relrnTime
            // And a 100% success rate
            newRate = if (newRate == 0.0) 1.0 else newRate
            revRate = if (revRate == 0.0) 1.0 else revRate
            relrnRate = if (relrnRate == 0.0) 1.0 else relrnRate
            etaCache[0] = newRate
            etaCache[1] = newTime
            etaCache[2] = revRate
            etaCache[3] = revTime
            etaCache[4] = relrnRate
            etaCache[5] = relrnTime
        } else {
            newRate = etaCache.get(0)
            newTime = etaCache.get(1)
            revRate = etaCache.get(2)
            revTime = etaCache.get(3)
            relrnRate = etaCache.get(4)
            relrnTime = etaCache.get(5)
        }

        // Calculate the total time for each queue based on the historical average duration per rep
        val newTotal = newTime * counts.new
        val relrnTotal = relrnTime * counts.lrn
        val revTotal = revTime * counts.rev

        // Now we have to predict how many additional relrn cards are going to be generated while reviewing the above
        // queues, and how many relrn cards *those* reps will generate (and so on, until 0).

        // Every queue has a failure rate, and each failure will become a relrn
        var toRelrn = counts.new // Assume every new card becomes 1 relrn
        toRelrn += Math.ceil((1 - relrnRate) * counts.lrn).toInt()
        toRelrn += Math.ceil((1 - revRate) * counts.rev).toInt()

        // Use the accuracy rate of the relrn queue to estimate how many reps we will end up with if the cards
        // currently in relrn continue to fail at that rate. Loop through the failures of the failures until we end up
        // with no predicted failures left.

        // Cap the lower end of the success rate to ensure the loop ends (it could be 0 if no revlog history, or
        // negative for other reasons). 5% seems reasonable to ensure the loop doesn't iterate too much.
        relrnRate = Math.max(relrnRate, 0.05)
        var futureReps = 0
        do {
            // Truncation ensures the failure rate always decreases
            val failures = ((1 - relrnRate) * toRelrn).toInt()
            futureReps += failures
            toRelrn = failures
        } while (toRelrn > 1)
        val futureRelrnTotal = relrnTime * futureReps
        return Math.round((newTotal + relrnTotal + revTotal + futureRelrnTotal) / 60000).toInt()
    }

    /** Used only by V1/V2, and unit tests.
     * @param card A random card
     * @return The conf of the deck of the card.
     */
    fun cardConf(card: Card): DeckConfig {
        return col.decks.confForDid(card.did)
    }

    /*
      Next time reports ********************************************************
      ***************************************
     */

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
    open fun nextIvlStr(card: Card, @Consts.BUTTON_TYPE ease: Int): String {
        val secs = nextIvl(card, ease)
        return col.backend.formatTimespan(secs.toFloat(), FormatTimespanRequest.Context.ANSWER_BUTTONS)
    }

    fun learnAheadSeconds(): Int {
        return col.config.get("collapseTime") ?: 1200
    }

    fun timeboxSecs(): Int {
        return col.config.get("timeLim") ?: 0
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

const val REPORT_LIMIT = 99999

private fun stateFromEase(states: SchedulingStates, ease: Int): SchedulingState {
    return when (ease) {
        1 -> states.again
        2 -> states.hard
        3 -> states.good
        4 -> states.easy
        else -> TODO("invalid ease: $ease")
    }
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

fun Collection.computeFsrsWeightsRaw(input: ByteArray): ByteArray {
    return backend.computeFsrsWeightsRaw(input = input)
}
fun Collection.computeOptimalRetentionRaw(input: ByteArray): ByteArray {
    return backend.computeOptimalRetentionRaw(input = input)
}
fun Collection.evaluateWeightsRaw(input: ByteArray): ByteArray {
    return backend.evaluateWeightsRaw(input = input)
}
