/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.libanki.sched

import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.libanki.*
import com.ichi2.libanki.utils.TimeManager.time
import com.ichi2.testutils.AnkiAssert
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers.*
import org.json.JSONArray
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// Note: These tests can't be run individually but can from the class-level
// gradlew AnkiDroid:testDebug --tests "com.ichi2.libanki.sched.AbstractSchedTest.*"
@KotlinCleanup("is -> equalTo")
@KotlinCleanup("reduce newlines in asserts")
@KotlinCleanup("improve increaseAndAssertNewCountsIs")
@RunWith(ParameterizedRobolectricTestRunner::class)
class AbstractSchedTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField // required for Parameter
    var schedVersion = 0

    @Before
    override fun setUp() {
        super.setUp()
        try {
            col.changeSchedulerVer(schedVersion)
        } catch (e: ConfirmModSchemaException) {
            throw RuntimeException("Could not change schedVer", e)
        }
    }

    @Test
    fun ensureUndoCorrectCounts() {
        val col = col
        val sched = col.sched
        val dconf = col.decks.getConf(1)
        assertThat(dconf, notNullValue())
        dconf.getJSONObject("new").put("perDay", 10)
        col.decks.save(dconf)
        for (i in 0..19) {
            val note = col.newNote()
            note.setField(0, "a")
            col.addNote(note)
        }
        col.reset()
        assertThat(col.cardCount(), `is`(20))
        assertThat(sched.newCount(), `is`(10))
        val card = sched.card
        assertThat(sched.newCount(), `is`(9))
        assertThat(sched.counts(card!!).new, `is`(10))
        sched.answerCard(card, sched.goodNewButton)
        sched.card
        col.legacyV2ReviewUndo()
        assertThat(sched.newCount(), `is`(10))
    }

    @Test
    fun testCardQueue() {
        val col = col
        val sched = col.sched as SchedV2
        val queue = SimpleCardQueue(sched)
        assertThat(queue.size(), `is`(0))
        val nbCard = 6
        val cids = LongArray(nbCard)
        for (i in 0 until nbCard) {
            val note = addNoteUsingBasicModel("foo", "bar")
            val card = note.firstCard()
            val cid = card.id
            cids[i] = cid
            queue.add(cid)
        }
        assertThat(queue.size(), `is`(nbCard))
        assertEquals(cids[0], queue.removeFirstCard().id)
        assertThat(queue.size(), `is`(nbCard - 1))
        queue.remove(cids[1])
        assertThat(queue.size(), `is`(nbCard - 2))
        queue.remove(cids[3])
        assertThat(queue.size(), `is`(nbCard - 3))
        assertEquals(cids[2], queue.removeFirstCard().id)
        assertThat(queue.size(), `is`(nbCard - 4))
        assertEquals(cids[4], queue.removeFirstCard().id)
        assertThat(queue.size(), `is`(nbCard - 5))
    }

    @Test
    fun siblingCorrectlyBuried() {
        // #6903
        val col = col
        val sched = col.sched
        val dconf = col.decks.getConf(1)
        assertThat(dconf, notNullValue())
        dconf.getJSONObject("new").put("bury", true)
        col.decks.save(dconf)
        val nbNote = 2
        val notes = arrayOfNulls<Note>(nbNote)
        for (i in 0 until nbNote) {
            val note = addNoteUsingBasicAndReversedModel("front", "back")
            notes[i] = note
        }

        col.reset()
        for (i in 0 until nbNote) {
            val card = sched.card
            val counts = sched.counts(card!!)
            assertThat(
                counts.new,
                `is`(greaterThan(nbNote - i))
            ) // Actual number of new card.
            assertThat(
                counts.new,
                `is`(lessThanOrEqualTo(nbNote * 2 - i))
            ) // Maximal number potentially shown,
            // because decrementing does not consider burying sibling
            assertEquals(0, counts.lrn.toLong())
            assertEquals(0, counts.rev.toLong())
            assertEquals(notes[i]!!.firstCard().id, card.id)
            assertEquals(Consts.QUEUE_TYPE_NEW, card.queue)
            sched.answerCard(card, sched.answerButtons(card))
        }

        val card = sched.card
        assertNull(card)
    }

    @Test
    fun deckDueTreeInconsistentDecksPasses() {
        // https://github.com/ankidroid/Anki-Android/issues/6383#issuecomment-686266966
        // The bad data came from AnkiWeb, this passes using "addDeck" but we can't assume this is always called.
        val parent = "DANNY SULLIVAN MCM DECK"
        val child = "Danny Sullivan MCM Deck::*MCM_UNTAGGED_CARDS"

        addDeckWithExactName(parent)
        addDeckWithExactName(child)

        col.decks.checkIntegrity()
        AnkiAssert.assertDoesNotThrow { col.sched.deckDueTree() }
    }

    private inner class IncreaseToday {
        private val mAId: Long = addDeck("A")
        private val mBId: Long = addDeck("A::B")
        private val mCId: Long = addDeck("A::B::C")
        private val mDId: Long = addDeck("A::B::D")
        private val mDecks = col.decks
        private val mSched: AbstractSched = col.sched

        private fun assertNewCountIs(explanation: String, did: Long, expected: Int) {
            mDecks.select(did)
            mSched.resetCounts()
            assertThat(explanation, mSched.newCount(), `is`(expected))
        }

        private fun increaseAndAssertNewCountsIs(
            explanation: String,
            did: Long,
            a: Int,
            b: Int,
            c: Int,
            d: Int
        ) {
            extendNew(did)
            assertNewCountsIs(explanation, a, b, c, d)
        }

        private fun assertNewCountsIs(explanation: String, a: Int, b: Int, c: Int, d: Int) {
            assertNewCountIs(explanation, mAId, a)
            assertNewCountIs(explanation, mBId, b)
            assertNewCountIs(explanation, mCId, c)
            assertNewCountIs(explanation, mDId, d)
        }

        private fun extendNew(did: Long) {
            mDecks.select(did)
            mSched.extendLimits(1, 0)
        }

        fun test() {
            val col = col
            val models = col.notetypes

            val dconf = mDecks.getConf(1)
            assertThat(dconf, notNullValue())
            dconf.getJSONObject("new").put("perDay", 0)
            mDecks.save(dconf)

            val model = models.byName("Basic")
            for (did in longArrayOf(mCId, mDId)) {
                // The note is added in model's did. So change model's did.
                model!!.put("did", did)
                for (i in 0..3) {
                    addNoteUsingBasicModel("foo", "bar")
                }
            }

            assertNewCountsIs("All daily limits are 0", 0, 0, 0, 0)
            increaseAndAssertNewCountsIs(
                "Adding a review in C add it in its parents too",
                mCId,
                1,
                1,
                1,
                0
            )
            increaseAndAssertNewCountsIs(
                "Adding a review in A add it in its children too",
                mAId,
                2,
                2,
                2,
                1
            )
            increaseAndAssertNewCountsIs(
                "Adding a review in B add it in its parents and children too",
                mBId,
                3,
                3,
                3,
                2
            )
            increaseAndAssertNewCountsIs(
                "Adding a review in D add it in its parents too",
                mDId,
                4,
                4,
                3,
                3
            )
            increaseAndAssertNewCountsIs(
                "Adding a review in D add it in its parents too",
                mDId,
                5,
                5,
                3,
                4
            )
            mDecks.select(mCId)
            col.reset()
            for (i in 0..2) {
                val card = mSched.card
                mSched.answerCard(card!!, mSched.answerButtons(card))
            }
            assertNewCountsIs(
                "All cards from C are reviewed. Still 4 cards to review in D, but only two available because of A's limit.",
                2,
                2,
                0,
                2
            )
            increaseAndAssertNewCountsIs(
                "Increasing the number of card in C increase it in its parents too. This allow for more review in any children, and in particular in D.",
                mCId,
                3,
                3,
                1,
                3
            )
            // D increase because A's limit changed.
            // This means that increasing C, which is not related to D, can increase D
            // This follows upstream but is really counter-intuitive.
            increaseAndAssertNewCountsIs(
                "Adding a review in C add it in its parents too, even if c has no more card. This allow one more card in d.",
                mCId,
                4,
                4,
                1,
                4
            )
            /* I would have expected :
             increaseAndAssertNewCountsIs("", cId, 3, 3, 1, 3);
             But it seems that applying "increase c", while not actually increasing c (because there are no more new card)
             still increases A.

             Upstream, the number of new card to add is limited to the number of cards in the deck not already planned for today.
             So, to reproduce it, you either need to temporary add a card in A::B::C, increase today limit and delete the card
             or to do
```python
from aqt import mw
c = mw.col.decks.byName("A::B::C")
mw.col.sched.extendLimits(1, 0)
```
             */
        }
    }

    /** Those test may be unintuitive, but they follow upstream as close as possible.  */
    @Test
    fun increaseToday() {
        IncreaseToday().test()
    }

    private fun undoAndRedo(preload: Boolean) {
        val col = col
        val conf = col.decks.confForDid(1)
        conf.getJSONObject("new").put("delays", JSONArray(doubleArrayOf(1.0, 3.0, 5.0, 10.0)))
        col.decks.save(conf)
        col.config.set("collapseTime", 20 * 60)
        val sched = col.sched

        addNoteUsingBasicModel("foo", "bar")

        col.reset()
        advanceRobolectricLooper()

        var card = sched.card
        assertNotNull(card)
        assertEquals(Counts(1, 0, 0), sched.counts(card))
        if (preload) {
            sched.preloadNextCard()
        }

        sched.answerCard(card, sched.goodNewButton)
        advanceRobolectricLooper()

        card = sched.card
        assertNotNull(card)
        assertEquals(
            Counts(0, if (schedVersion == 1) 3 else 1, 0),
            sched.counts(card)
        )
        if (preload) {
            sched.preloadNextCard()
        }

        sched.answerCard(card, sched.goodNewButton)
        advanceRobolectricLooper()

        card = sched.card
        assertNotNull(card)
        assertEquals(
            Counts(0, if (schedVersion == 1) 2 else 1, 0),
            sched.counts(card)
        )
        if (preload) {
            sched.preloadNextCard()
            advanceRobolectricLooper()
        }

        assertNotNull(card)

        col.legacyV2ReviewUndo()
        advanceRobolectricLooper()
        assertEquals(
            Counts(0, if (schedVersion == 1) 3 else 1, 0),
            sched.counts()
        )
        if (preload) {
            sched.preloadNextCard()
            advanceRobolectricLooper()
        }

        card = sched.card!!
        sched.answerCard(card, sched.goodNewButton)
        advanceRobolectricLooper()
        card = sched.card
        assertNotNull(card)
        if (preload) {
            sched.preloadNextCard()
        }
        assertEquals(
            Counts(0, if (schedVersion == 1) 2 else 1, 0),
            sched.counts(card)
        )
        assertNotNull(card)
    }

    @Test
    fun undoAndRedoPreload() {
        undoAndRedo(true)
    }

    @Test
    fun undoAndRedoNoPreload() {
        undoAndRedo(false)
    }

    private fun addDeckWithExactName(name: String) {
        val decks = col.decks

        val did = addDeck(name)
        val d = decks.get(did)
        d.put("name", name)
        decks.update(d)

        @KotlinCleanup("Replace stream() with kotlin collection operators")
        val hasMatch = decks.all().stream().anyMatch { x: Deck -> name == x.getString("name") }
        @KotlinCleanup("remove .format")
        assertThat(
            "Deck $name should exist",
            hasMatch,
            `is`(true)
        )
    }

    @Test
    fun regression_7066() {
        val col = col
        val dconf = col.decks.getConf(1)
        dconf.getJSONObject("new").put("bury", true)
        val sched = col.sched
        addNoteUsingBasicAndReversedModel("foo", "bar")
        addNoteUsingBasicModel("plop", "foo")
        col.reset()
        val card = sched.card
        sched.preloadNextCard()
        sched.answerCard(card!!, Consts.BUTTON_THREE)
        @Suppress("UNUSED_VARIABLE")
        var unusedCard = sched.card
        AnkiAssert.assertDoesNotThrow { sched.preloadNextCard() }
    }

    @Test
    @KotlinCleanup("remove arrayOfNulls to remove the !!")
    @KotlinCleanup("make `gotten` non-null")
    fun regression_7984() {
        val col = col
        val sched = col.sched as SchedV2
        val time = time
        val cards = arrayOfNulls<Card>(2)
        for (i in 0..1) {
            cards[i] = addNoteUsingBasicModel(i.toString(), "").cards()[0]
            cards[i]!!.queue = Consts.QUEUE_TYPE_LRN
            cards[i]!!.type = Consts.CARD_TYPE_LRN
            cards[i]!!.due = time.intTime() - 20 * 60 + i
            cards[i]!!.flush()
        }
        col.reset()
        // Regression test success non deterministically without the sleep
        var gotten: Card? = sched.card
        advanceRobolectricLooperWithSleep()
        assertThat(
            gotten,
            `is`(
                cards[0]
            )
        )
        sched.answerCard(gotten!!, Consts.BUTTON_ONE)

        gotten = sched.card
        assertThat(
            gotten,
            `is`(
                cards[1]
            )
        )
        sched.answerCard(gotten!!, Consts.BUTTON_ONE)
        gotten = sched.card
        assertThat(
            gotten,
            `is`(
                cards[0]
            )
        )
    }

    companion object {
        @Suppress("unused")
        @ParameterizedRobolectricTestRunner.Parameters(name = "SchedV{0}")
        @JvmStatic // required for initParameters
        @KotlinCleanup("fix array init")
        fun initParameters(): Collection<Array<Any>> {
            return listOf(arrayOf(2))
        }
    }
}
