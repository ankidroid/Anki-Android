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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts.BUTTON_FOUR
import com.ichi2.libanki.Consts.BUTTON_ONE
import com.ichi2.libanki.Consts.BUTTON_THREE
import com.ichi2.libanki.Consts.BUTTON_TWO
import com.ichi2.libanki.Consts.CARD_TYPE_LRN
import com.ichi2.libanki.Consts.CARD_TYPE_NEW
import com.ichi2.libanki.Consts.CARD_TYPE_RELEARNING
import com.ichi2.libanki.Consts.CARD_TYPE_REV
import com.ichi2.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.libanki.Consts.QUEUE_TYPE_DAY_LEARN_RELEARN
import com.ichi2.libanki.Consts.QUEUE_TYPE_LRN
import com.ichi2.libanki.Consts.QUEUE_TYPE_NEW
import com.ichi2.libanki.Consts.QUEUE_TYPE_REV
import com.ichi2.libanki.Consts.QUEUE_TYPE_SIBLING_BURIED
import com.ichi2.libanki.Consts.STARTING_FACTOR
import com.ichi2.libanki.DecksTest.Companion.TEST_DECKS
import com.ichi2.libanki.stats.Stats.Companion.SECONDS_PER_DAY
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.testutils.AnkiAssert.checkRevIvl
import com.ichi2.testutils.AnkiAssert.without_unicode_isolation
import com.ichi2.testutils.MockTime
import com.ichi2.testutils.MutableTime
import com.ichi2.utils.KotlinCleanup
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.RustCleanup
import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers.*
import org.json.JSONArray
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*
import kotlin.test.junit5.JUnit5Asserter.assertEquals

@RustCleanup("Remove, or add KotlinCleanup")
@RunWith(AndroidJUnit4::class)
class SchedTest : RobolectricTest() {
    @Test
    fun unburyWorksIfDeckIsNotSelected() {
        // Issue 6200

        val sched = Sched(col)
        val buriedCard = createBuriedCardInDefaultDeck()
        assertThat(buriedCard.did, equalTo(DEFAULT_DECK_ID))

        assertThat("Card should be buried", getCardInDefaultDeck(sched), nullValue())

        // We want to assert that we can unbury, even if the deck we're unburying from isn't selected
        selectNewDeck()
        sched.unburyCardsForDeck(DEFAULT_DECK_ID)

        assertThat("Card should no longer be buried", getCardInDefaultDeck(sched), notNullValue())
    }

    @Test
    fun learnCardsAreNotFiltered() {
        // Replicates Anki commit: 13c54e02d8fd2b35f6c2f4b796fc44dec65043b8
        addNoteUsingBasicModel("Hello", "World")
        val sched = Sched(col)
        markNextCardAsGood(sched)
        val dynDeck = addDynamicDeck("Hello")

        // Act
        sched.rebuildDyn(dynDeck)

        // Assert
        val dynamicDeck = getCountsForDid(dynDeck.toDouble())
        assertThat("A learn card should not be moved into a dyn deck", dynamicDeck.lrnCount, equalTo(0))
        assertThat("A learn card should not be moved into a dyn deck", dynamicDeck.newCount, equalTo(0))
        assertThat("A learn card should not be moved into a dyn deck", dynamicDeck.revCount, equalTo(0))
    }

    private fun markNextCardAsGood(sched: Sched) {
        val toAnswer: Card? = sched.card
        assertThat(toAnswer, notNullValue())
        sched.answerCard(toAnswer!!, BUTTON_TWO) // Good
    }

    @KotlinCleanup("simplify fun with firstOrNull and ?: ")
    private fun getCountsForDid(didToFind: Double): DeckDueTreeNode {
        val tree = col.sched.deckDueTree()
        for ((value) in tree) {
            if (value.did.toDouble() == didToFind) {
                return value
            }
        }
        throw IllegalStateException("Could not find deck $didToFind")
    }

    private fun getCardInDefaultDeck(s: Sched): Card? {
        selectDefaultDeck()
        s.deferReset()
        return s.card
    }

    private fun createBuriedCardInDefaultDeck(): Card {
        val n = addNoteUsingBasicModel("Hello", "World")
        val c = n.firstCard()
        c.queue = QUEUE_TYPE_SIBLING_BURIED
        c.flush(col)
        return c
    }

    private fun selectNewDeck() {
        val did = addDeck("New")
        col.decks.select(did)
    }

    @Test
    fun ensureDeckTree() {
        if (!BackendFactory.defaultLegacySchema) {
            // assertEquals() fails with the new backend, because the ids don't match.
            // While it could be updated to work with the new backend, it would be easier
            // to switch to the backend's tree calculation in the future, which is tested
            // in the upstream code.
            return
        }
        for (deckName in TEST_DECKS) {
            addDeck(deckName)
        }
        col.sched.deckDueTree()
        val sched = col.sched
        val tree = sched.deckDueTree()
        assertEquals("Tree has not the expected structure", SchedV2Test.expectedTree(col, false), tree)
    }

    @Test
    fun testRevLogValues() {
        TimeManager.withMockInstance(MutableTime(MockTime.timeStamp(2020, 8, 4, 11, 22, 19, 123), 10)) { time: MutableTime ->
            val col = CollectionHelper.instance.getCol(targetContext)!!
            addNoteUsingBasicModel("Hello", "World")
            val sched = col.sched
            val c = sched.card
            time.setFrozen(true)
            val currentTime = time.getInternalTimeMs()
            sched.answerCard(c!!, BUTTON_ONE)
            val timeAnswered = col.db.queryLongScalar("select id from revlog")
            assertThat(timeAnswered, equalTo(currentTime))
        }
    }

    private fun selectDefaultDeck() {
        col.decks.select(DEFAULT_DECK_ID)
    }

    /*****************
     * autogenerated from https://github.com/ankitects/anki/blob/2c73dcb2e547c44d9e02c20a00f3c52419dc277b/pylib/tests/test_cards.py*
     */
    @Throws(ConfirmModSchemaException::class)
    private fun getColV1(): Collection {
        val col = col
        col.changeSchedulerVer(1)
        return col
    }

    @Throws(Exception::class)
    fun test_new_v1() {
        val col = getColV1()
        col.reset()
        assertEquals(0, col.sched.newCount().toLong())
        // add a note
        val note = col.newNote()
        note.setItem("Front", "one")
        note.setItem("Back", "two")
        col.addNote(note)
        col.reset()
        assertEquals(1, col.sched.counts().new.toLong())
        // fetch it
        val c = card!!
        assertNotNull(c)
        assertEquals(QUEUE_TYPE_NEW, c.queue)
        assertEquals(CARD_TYPE_NEW, c.type)
        // if we answer it, it should become a learn card
        val t = TimeManager.time.intTime()
        col.sched.answerCard(c, BUTTON_ONE)
        assertEquals(QUEUE_TYPE_LRN, c.queue)
        assertEquals(CARD_TYPE_LRN, c.type)
        assertThat(c.due, greaterThanOrEqualTo(t))

        // disabled for now, as the learn fudging makes this randomly fail
        // // the default order should ensure siblings are not seen together, and
        // // should show all cards
        // Model m = col.getModels().current(); Models mm = col.getModels()
        // JSONObject t = mm.newTemplate("Reverse")
        // t['qfmt'] = "{{Back}}"
        // t['afmt'] = "{{Front}}"
        // mm.addTemplateModChanged(m, t)
        // mm.save(m)
        // note = col.newNote()
        // note['Front'] = u"2"; note['Back'] = u"2"
        // col.addNote(note)
        // note = col.newNote()
        // note['Front'] = u"3"; note['Back'] = u"3"
        // col.addNote(note)
        // col.reset()
        // qs = ("2", "3", "2", "3")
        // for (int n = 0; n < 4; n++) {
        //     c = getCard()
        //     assertTrue(qs[n] in c.q())
        //     col.getSched().answerCard(c, 2)
        // }
    }

    @Test
    @Throws(Exception::class)
    fun test_newLimits_V1() {
        val col = getColV1()
        // add some notes
        val deck2 = addDeck("Default::foo")
        var note: Note
        for (i in 0..29) {
            note = col.newNote()
            note.setItem("Front", i.toString())
            if (i > 4) {
                note.model().put("did", deck2)
            }
            col.addNote(note)
        }
        // give the child deck a different configuration
        val c2 = col.decks.confId("new conf")
        col.decks.setConf(col.decks.get(deck2), c2)
        col.reset()
        // both confs have defaulted to a limit of 20
        assertEquals("both confs have defaulted to a limit of 20", 20, col.sched.counts().new)
        // first card we get comes from parent
        val c = card!!
        assertEquals(1, c.did)
        // limit the parent to 10 cards, meaning we get 10 in total
        val conf1 = col.decks.confForDid(1)
        conf1.getJSONObject("new").put("perDay", 10)
        col.decks.save(conf1)
        col.reset()
        assertEquals(10, col.sched.counts().new.toLong())
        // if we limit child to 4, we should get 9
        val conf2 = col.decks.confForDid(deck2)
        conf2.getJSONObject("new").put("perDay", 4)
        col.decks.save(conf2)
        col.reset()
        assertEquals(9, col.sched.counts().new.toLong())
    }

    @Test
    @Throws(Exception::class)
    fun test_newBoxes_v1() {
        val col = getColV1()
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        col.reset()
        val c = card!!
        val conf = col.sched._cardConf(c)
        conf.getJSONObject("new").put("delays", JSONArray(doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)))
        col.decks.save(conf)
        col.sched.answerCard(c, BUTTON_TWO)
        // should handle gracefully
        conf.getJSONObject("new").put("delays", JSONArray(doubleArrayOf(1.0)))
        col.decks.save(conf)
        col.sched.answerCard(c, BUTTON_TWO)
    }

    @Test
    @Throws(Exception::class)
    fun test_learnV1() {
        val col = getColV1()
        // add a note
        val note = col.newNote()
        note.setItem("Front", "one")
        note.setItem("Back", "two")
        col.addNote(note)
        // set as a learn card and rebuild queues
        col.db.execute("update cards set queue=0, type=0")
        col.reset()
        // sched.getCard should return it, since it's due in the past
        val c = card!!
        assertNotNull(c)
        val conf = col.sched._cardConf(c)
        conf.getJSONObject("new").put("delays", JSONArray(doubleArrayOf(0.5, 3.0, 10.0)))
        col.decks.save(conf)
        // fail it
        col.sched.answerCard(c, BUTTON_ONE)
        // it should have three reps left to graduation
        assertEquals(3, (c.left % 1000).toLong())
        assertEquals(3, (c.left / 1000).toLong())
        // it should be due in 30 seconds
        val t = (c.due - TimeManager.time.intTime())
        assertThat(t, greaterThanOrEqualTo(25L))
        assertThat(t, lessThanOrEqualTo(40L))
        // pass it once
        col.sched.answerCard(c, BUTTON_TWO)
        // it should be due in 3 minutes
        assertEquals((c.due - TimeManager.time.intTime()).toFloat(), 179f, 1f)
        assertEquals(2, (c.left % 1000).toLong())
        assertEquals(2, (c.left / 1000).toLong())
        // check log is accurate
        val log = col.db.database.query("select * from revlog order by id desc")
        assertTrue(log.moveToFirst())
        assertEquals(2, log.getInt(3).toLong())
        assertEquals(-180, log.getInt(4).toLong())
        assertEquals(-30, log.getInt(5).toLong())
        // pass again
        col.sched.answerCard(c, BUTTON_TWO)
        // it should be due in 10 minutes
        assertEquals((c.due - TimeManager.time.intTime()).toFloat(), 599f, 1f)
        assertEquals(1, (c.left % 1000).toLong())
        assertEquals(1, (c.left / 1000).toLong())
        // the next pass should graduate the card
        assertEquals(QUEUE_TYPE_LRN, c.queue)
        assertEquals(CARD_TYPE_LRN, c.type)
        col.sched.answerCard(c, BUTTON_TWO)
        assertEquals(QUEUE_TYPE_REV, c.queue)
        assertEquals(CARD_TYPE_REV, c.type)
        // should be due tomorrow, with an interval of 1
        assertEquals((col.sched.today + 1).toLong(), c.due)
        assertEquals(1, c.ivl)
        // or normal removal
        c.type = CARD_TYPE_NEW
        c.queue = QUEUE_TYPE_LRN
        col.sched.answerCard(c, BUTTON_THREE)
        assertEquals(CARD_TYPE_REV, c.type)
        assertEquals(QUEUE_TYPE_REV, c.queue)
        assertTrue(checkRevIvl(c, 4))
        // revlog should have been updated each time
        assertEquals(
            5,
            col.db.queryScalar("select count() from revlog where type = 0").toLong()
        )
        // now failed card handling
        c.type = CARD_TYPE_REV
        c.queue = QUEUE_TYPE_LRN
        c.oDue = 123
        col.sched.answerCard(c, BUTTON_THREE)
        assertEquals(123, c.due)
        assertEquals(CARD_TYPE_REV, c.type)
        assertEquals(QUEUE_TYPE_REV, c.queue)
        // we should be able to remove manually, too
        c.type = CARD_TYPE_REV
        c.queue = QUEUE_TYPE_LRN
        c.oDue = 321
        c.flush(col)
        (col.sched as Sched).removeLrn()
        c.load(col)
        assertEquals(QUEUE_TYPE_REV, c.queue)
        assertEquals(321, c.due)
    }

    @Test
    @Throws(Exception::class)
    fun test_learn_collapsedV1() {
        val col = getColV1()
        // add 2 notes
        var note = col.newNote()
        note.setItem("Front", "1")
        col.addNote(note)
        note = col.newNote()
        note.setItem("Front", "2")
        col.addNote(note)
        // set as a learn card and rebuild queues
        col.db.execute("update cards set queue=0, type=0")
        col.reset()
        // should get '1' first
        var c = card!!
        assertTrue(c.q(col).endsWith("1"))
        // pass it so it's due in 10 minutes
        col.sched.answerCard(c, BUTTON_TWO)
        // get the other card
        c = card!!
        assertTrue(c.q(col).endsWith("2"))
        // fail it so it's due in 1 minute
        col.sched.answerCard(c, BUTTON_ONE)
        // we shouldn't get the same card again
        c = card!!
        assertFalse(c.q(col).endsWith("2"))
    }

    @Test
    @Throws(Exception::class)
    fun test_learn_dayV1() {
        val col = getColV1()
        // add a note
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        col.reset()
        var c = card!!
        var conf = col.sched._cardConf(c)
        conf.getJSONObject("new").put("delays", JSONArray(doubleArrayOf(1.0, 10.0, 1440.0, 2880.0)))
        col.decks.save(conf)
        // pass it
        col.sched.answerCard(c, BUTTON_TWO)
        // two reps to graduate, 1 more today
        assertEquals(3, (c.left % 1000).toLong())
        assertEquals(1, (c.left / 1000).toLong())
        assertEquals(Counts(0, 1, 0), col.sched.counts())
        c = card!!
        assertEquals(SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_TWO))
        // answering it will place it in queue 3
        col.sched.answerCard(c, BUTTON_TWO)
        assertEquals((col.sched.today + 1).toLong(), c.due)
        assertEquals(QUEUE_TYPE_DAY_LEARN_RELEARN, c.queue)
        assertNull(card)
        // for testing, move it back a day
        c.due = c.due - 1
        c.flush(col)
        col.reset()
        assertEquals(Counts(0, 1, 0), col.sched.counts())
        c = card!!
        // nextIvl should work
        assertEquals(SECONDS_PER_DAY * 2, col.sched.nextIvl(c, BUTTON_TWO))
        // if we fail it, it should be back in the correct queue
        col.sched.answerCard(c, BUTTON_ONE)
        assertEquals(QUEUE_TYPE_LRN, c.queue)
        col.undo()
        col.reset()
        c = card!!
        col.sched.answerCard(c, BUTTON_TWO)
        // simulate the passing of another two days
        c.due = c.due - 2
        c.flush(col)
        col.reset()
        // the last pass should graduate it into a review card
        assertEquals(SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_TWO))
        col.sched.answerCard(c, BUTTON_TWO)
        assertEquals(CARD_TYPE_REV, c.type)
        assertEquals(QUEUE_TYPE_REV, c.queue)
        // if the lapse step is tomorrow, failing it should handle the counts
        // correctly
        c.due = 0
        c.flush(col)
        col.reset()
        assertEquals(Counts(0, 0, 1), col.sched.counts())
        conf = col.sched._cardConf(c)
        conf.getJSONObject("lapse").put("delays", JSONArray(doubleArrayOf(1440.0)))
        col.decks.save(conf)
        c = card!!
        col.sched.answerCard(c, BUTTON_ONE)
        assertEquals(CARD_TYPE_RELEARNING, c.queue)
        assertEquals(Counts(0, 0, 0), col.sched.counts())
    }

    @Test
    @Throws(Exception::class)
    fun test_reviewsV1() {
        val col = getColV1()
        // add a note
        val note = col.newNote()
        note.setItem("Front", "one")
        note.setItem("Back", "two")
        col.addNote(note)
        // set the card up as a review card, due 8 days ago
        var c = note.cards()[0].apply {
            type = CARD_TYPE_REV
            queue = QUEUE_TYPE_REV
            due = (col.sched.today - 8).toLong()
            factor = STARTING_FACTOR
            setReps(3)
            lapses = 1
            ivl = 100
            startTimer()
            flush(col)
        }
        // save it for later use as well
        val cardcopy = c.clone()
        // failing it should put it in the learn queue with the default options
        // //////////////////////////////////////////////////////////////////////////////////////////////////
        // different delay to new
        col.reset()
        val conf = col.sched._cardConf(c)
        conf.getJSONObject("lapse").put("delays", JSONArray(doubleArrayOf(2.0, 20.0)))
        col.decks.save(conf)
        col.sched.answerCard(c, BUTTON_ONE)
        assertEquals(QUEUE_TYPE_LRN, c.queue)
        // it should be due tomorrow, with an interval of 1
        assertEquals((col.sched.today + 1).toLong(), c.oDue)
        assertEquals(1, c.ivl)
        // but because it's in the learn queue, its current due time should be in
        // the future
        assertThat(c.due, greaterThanOrEqualTo(TimeManager.time.intTime()))
        assertThat(c.due - TimeManager.time.intTime(), greaterThan(118L))
        // factor should have been decremented
        assertEquals(2300, c.factor)
        // check counters
        assertEquals(2, c.lapses)
        assertEquals(4, c.reps)
        // check ests.
        assertEquals(120, col.sched.nextIvl(c, BUTTON_ONE))
        assertEquals((20 * 60).toLong(), col.sched.nextIvl(c, BUTTON_TWO))
        // try again with an ease of 2 instead
        // //////////////////////////////////////////////////////////////////////////////////////////////////
        c = cardcopy.clone()
        c.flush(col)
        col.sched.answerCard(c, BUTTON_TWO)
        assertEquals(QUEUE_TYPE_REV, c.queue)
        // the new interval should be (100 + 8/4) * 1.2 = 122
        assertTrue(checkRevIvl(c, 122))
        assertEquals((col.sched.today + c.ivl).toLong(), c.due)
        // factor should have been decremented
        assertEquals(2350, c.factor)
        // check counters
        assertEquals(1, c.lapses)
        assertEquals(4, c.reps)
        // ease 3
        // //////////////////////////////////////////////////////////////////////////////////////////////////
        c = cardcopy.clone()
        c.flush(col)
        col.sched.answerCard(c, BUTTON_THREE)
        // the new interval should be (100 + 8/2) * 2.5 = 260
        assertTrue(checkRevIvl(c, 260))
        assertEquals((col.sched.today + c.ivl).toLong(), c.due)
        // factor should have been left alone
        assertEquals(STARTING_FACTOR, c.factor)
        // ease 4
        // //////////////////////////////////////////////////////////////////////////////////////////////////
        c = cardcopy.clone()
        c.flush(col)
        col.sched.answerCard(c, BUTTON_FOUR)
        // the new interval should be (100 + 8) * 2.5 * 1.3 = 351
        assertTrue(checkRevIvl(c, 351))
        assertEquals((col.sched.today + c.ivl).toLong(), c.due)
        // factor should have been increased
        assertEquals(2650, c.factor)
    }

    @Test
    @Throws(Exception::class)
    fun test_button_spacingV1() {
        val col = getColV1()
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        // 1 day ivl review card due now
        val c = note.cards()[0]
        c.type = CARD_TYPE_REV
        c.queue = QUEUE_TYPE_REV
        c.due = col.sched.today.toLong()
        c.setReps(1)
        c.ivl = 1
        c.startTimer()
        c.flush(col)
        col.reset()
        // Upstream, there is no space in 2d
        assertEquals("2 d", without_unicode_isolation(col.sched.nextIvlStr(targetContext, c, BUTTON_TWO)))
        assertEquals("3 d", without_unicode_isolation(col.sched.nextIvlStr(targetContext, c, BUTTON_THREE)))
        assertEquals("4 d", without_unicode_isolation(col.sched.nextIvlStr(targetContext, c, BUTTON_FOUR)))
    }

    @Test
    @Ignore("disabled in commit anki@3069729776990980f34c25be66410e947e9d51a2")
    fun test_overdue_lapseV1() {
        // disabled in commit anki@3069729776990980f34c25be66410e947e9d51a2
        /*
          Collection col = getColV1();
          // add a note
          Note note = col.newNote();
          note.setItem("Front","one");
          col.addNote(note);
          // simulate a review that was lapsed and is now due for its normal review
          Card c = note.cards().get(0);
          c.setType(CARD_TYPE_REV);
          c.setQueue(QUEUE_TYPE_LRN);
          c.setDue(-1);
          c.setODue(-1);
          c.setFactor(STARTING_FACTOR);
          c.setLeft(2002);
          c.setIvl(0);
          c.flush(col);
          // checkpoint
          col.save();
          col.getSched().reset();
          assertEquals(new Counts(0, 2, 0), col.getSched().counts());
          c = getCard();
          col.getSched().answerCard(c, BUTTON_THREE);
          // it should be due tomorrow
          assertEquals(col.getSched().getToday()+ 1, c.getDue());
          // revert to before
          / * rollback
          col.rollback();
          // with the default settings, the overdue card should be removed from the
          // learning queue
          col.getSched().reset();
          assertEquals(new Counts(0, 0, 1), col.getSched().counts());
        */
    }

    @Test
    @Throws(Exception::class)
    fun test_finishedV1() {
        val col = getColV1()
        // nothing due
        assertThat(col.sched.finishedMsg(targetContext).toString(), containsString("Congratulations"))
        assertThat(col.sched.finishedMsg(targetContext).toString(), not(containsString("limit")))
        val note = col.newNote()
        note.setItem("Front", "one")
        note.setItem("Back", "two")
        col.addNote(note)
        // have a new card
        assertThat(
            col.sched.finishedMsg(targetContext).toString(),
            containsString("new cards available")
        )
        // turn it into a review
        col.reset()
        val c = note.cards()[0]
        c.startTimer()
        col.sched.answerCard(c, BUTTON_THREE)
        // nothing should be due tomorrow, as it's due in a week
        assertThat(
            col.sched.finishedMsg(targetContext).toString(),
            containsString("Congratulations")
        )
        assertThat(
            col.sched.finishedMsg(targetContext).toString(),
            not(
                containsString("limit")
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun test_nextIvlV1() {
        val col = getColV1()
        val note = col.newNote()
        note.setItem("Front", "one")
        note.setItem("Back", "two")
        col.addNote(note)
        col.reset()
        val conf = col.decks.confForDid(1)
        conf.getJSONObject("new").put("delays", JSONArray(doubleArrayOf(0.5, 3.0, 10.0)))
        conf.getJSONObject("lapse").put("delays", JSONArray(doubleArrayOf(1.0, 5.0, 9.0)))
        col.decks.save(conf)
        val c = card!!
        // new cards
        // //////////////////////////////////////////////////////////////////////////////////////////////////
        assertEquals(30, col.sched.nextIvl(c, BUTTON_ONE))
        assertEquals(180, col.sched.nextIvl(c, BUTTON_TWO))
        assertEquals(4 * SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_THREE))
        col.sched.answerCard(c, BUTTON_ONE)
        // cards in learning
        // //////////////////////////////////////////////////////////////////////////////////////////////////
        assertEquals(30, col.sched.nextIvl(c, BUTTON_ONE))
        assertEquals(180, col.sched.nextIvl(c, BUTTON_TWO))
        assertEquals(4 * SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_THREE))
        col.sched.answerCard(c, BUTTON_TWO)
        assertEquals(30, col.sched.nextIvl(c, BUTTON_ONE))
        assertEquals(600, col.sched.nextIvl(c, BUTTON_TWO))
        assertEquals(4 * SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_THREE))
        col.sched.answerCard(c, BUTTON_TWO)
        // normal graduation is tomorrow
        assertEquals(SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_TWO))
        assertEquals(4 * SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_THREE))
        // lapsed cards
        // //////////////////////////////////////////////////////////////////////////////////////////////////
        c.type = CARD_TYPE_REV
        c.ivl = 100
        c.factor = STARTING_FACTOR
        assertEquals(60, col.sched.nextIvl(c, BUTTON_ONE))
        assertEquals(100 * SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_TWO))
        assertEquals(100 * SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_THREE))
        // review cards
        // //////////////////////////////////////////////////////////////////////////////////////////////////
        c.queue = QUEUE_TYPE_REV
        c.ivl = 100
        c.factor = STARTING_FACTOR
        // failing it should put it at 60s
        assertEquals(60, col.sched.nextIvl(c, BUTTON_ONE))
        // or 1 day if relearn is false
        conf.getJSONObject("lapse").put("delays", JSONArray(doubleArrayOf()))
        col.decks.save(conf)
        assertEquals(SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_ONE))
        // (* 100 1.2 SECONDS_PER_DAY)10368000.0
        assertEquals(10368000, col.sched.nextIvl(c, BUTTON_TWO))
        // (* 100 2.5 SECONDS_PER_DAY)21600000.0
        assertEquals(21600000, col.sched.nextIvl(c, BUTTON_THREE))
        // (* 100 2.5 1.3 SECONDS_PER_DAY)28080000.0
        assertEquals(28080000, col.sched.nextIvl(c, BUTTON_FOUR))
        assertThat(
            without_unicode_isolation(col.sched.nextIvlStr(targetContext, c, BUTTON_FOUR)),
            equalTo("10.8 mo")
        )
    }

    @Test
    @Throws(Exception::class)
    fun test_misc() {
        val col = getColV1()
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        val c = note.cards()[0]
        // burying
        col.sched.buryNote(c.nid)
        col.reset()
        assertNull(card)
        col.sched.unburyCards()
        col.reset()
        assertNotNull(card)
    }

    @Test
    @Throws(Exception::class)
    fun test_suspendV1() {
        val col = getColV1()
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        var c = note.cards()[0]
        // suspending
        col.reset()
        assertNotNull(card)
        col.sched.suspendCards(longArrayOf(c.id))
        col.reset()
        assertNull(card)
        // unsuspending
        col.sched.unsuspendCards(longArrayOf(c.id))
        col.reset()
        assertNotNull(card)
        // should cope with rev cards being relearned
        c.apply {
            due = 0
            ivl = 100
            type = CARD_TYPE_REV
            queue = QUEUE_TYPE_REV
            flush(col)
        }

        col.reset()
        c = card!!
        col.sched.answerCard(c, BUTTON_ONE)
        assertThat(c.due, greaterThanOrEqualTo(TimeManager.time.intTime()))
        assertEquals(QUEUE_TYPE_LRN, c.queue)
        assertEquals(CARD_TYPE_REV, c.type)
        col.sched.suspendCards(longArrayOf(c.id))
        col.sched.unsuspendCards(longArrayOf(c.id))
        c.load(col)
        assertEquals(QUEUE_TYPE_REV, c.queue)
        assertEquals(CARD_TYPE_REV, c.type)
        assertEquals(1, c.due)
        // should cope with cards in cram decks
        c.due = 1
        c.flush(col)
        addDynamicDeck("tmp")
        col.sched.rebuildDyn()
        c.load(col)
        assertNotEquals(1, c.due)
        assertNotEquals(1, c.did)
        col.sched.suspendCards(longArrayOf(c.id))
        c.load(col)
        assertEquals(1, c.due)
        assertEquals(1, c.did)
    }

    @Test
    @Throws(Exception::class)
    fun test_cram() {
        val col = getColV1()
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        var c = note.cards()[0].apply {
            ivl = 100
            queue = QUEUE_TYPE_REV
            type = CARD_TYPE_REV
            // due in 25 days, so it's been waiting 75 days
            due = (col.sched.today + 25).toLong()
            mod = 1
            factor = STARTING_FACTOR
            startTimer()
            flush(col)
        }
        col.reset()
        assertEquals(Counts(0, 0, 0), col.sched.counts())
        @Suppress("UNUSED_VARIABLE")
        val cardcopy = c.clone()
        // create a dynamic deck and refresh it
        var did = addDynamicDeck("Cram")
        col.sched.rebuildDyn(did)
        col.reset()
        // should appear as new in the deck list
        // todo: which sort
        // and should appear in the counts
        assertEquals(Counts(1, 0, 0), col.sched.counts())
        // grab it and check estimates
        c = card!!
        assertEquals(2, col.sched.answerButtons(c).toLong())
        assertEquals(600, col.sched.nextIvl(c, BUTTON_ONE))
        assertEquals((138 * 60 * 60 * 24).toLong(), col.sched.nextIvl(c, BUTTON_TWO))
        val cram = col.decks.get(did)
        cram.put("delays", JSONArray(doubleArrayOf(1.0, 10.0)))
        col.decks.save(cram)
        assertEquals(3, col.sched.answerButtons(c).toLong())
        assertEquals(60, col.sched.nextIvl(c, BUTTON_ONE))
        assertEquals(600, col.sched.nextIvl(c, BUTTON_TWO))
        assertEquals((138 * 60 * 60 * 24).toLong(), col.sched.nextIvl(c, BUTTON_THREE))
        col.sched.answerCard(c, BUTTON_TWO)
        // elapsed time was 75 days
        // factor = 2.5+1.2/2 = 1.85
        // int(75*1.85) = 138
        assertEquals(138, c.ivl)
        assertEquals(138, c.oDue)
        assertEquals(QUEUE_TYPE_LRN, c.queue)
        // should be logged as a cram rep
        assertEquals(
            3,
            col.db.queryLongScalar("select type from revlog order by id desc limit 1")
        )
        // check ivls again
        assertEquals(60, col.sched.nextIvl(c, BUTTON_ONE))
        assertEquals((138 * 60 * 60 * 24).toLong(), col.sched.nextIvl(c, BUTTON_TWO))
        assertEquals((138 * 60 * 60 * 24).toLong(), col.sched.nextIvl(c, BUTTON_THREE))
        // when it graduates, due is updated
        c = card!!
        col.sched.answerCard(c, BUTTON_TWO)
        assertEquals(138, c.ivl)
        assertEquals(138, c.due)
        assertEquals(QUEUE_TYPE_REV, c.queue)
        // and it will have moved back to the previous deck
        assertEquals(1, c.did)
        // cram the deck again
        col.sched.rebuildDyn(did)
        col.reset()
        c = card!!
        // check ivls again - passing should be idempotent
        assertEquals(60, col.sched.nextIvl(c, BUTTON_ONE))
        assertEquals(600, col.sched.nextIvl(c, BUTTON_TWO))
        assertEquals((138 * 60 * 60 * 24).toLong(), col.sched.nextIvl(c, BUTTON_THREE))
        col.sched.answerCard(c, BUTTON_TWO)
        assertEquals(138, c.ivl)
        assertEquals(138, c.oDue)
        // fail
        col.sched.answerCard(c, BUTTON_ONE)
        assertEquals(60, col.sched.nextIvl(c, BUTTON_ONE))
        assertEquals(600, col.sched.nextIvl(c, BUTTON_TWO))
        assertEquals(SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_THREE))
        // delete the deck, returning the card mid-study
        col.decks.rem(col.decks.selected())
        assertEquals(1, col.sched.deckDueTree().size.toLong())
        c.load(col)
        assertEquals(1, c.ivl)
        assertEquals((col.sched.today + 1).toLong(), c.due)
        // make it due
        col.reset()
        assertEquals(Counts(0, 0, 0), col.sched.counts())
        c.due = -5
        c.ivl = 100
        c.flush(col)
        col.reset()
        assertEquals(Counts(0, 0, 1), col.sched.counts())
        // cram again
        did = addDynamicDeck("Cram")
        col.sched.rebuildDyn(did)
        col.reset()
        assertEquals(Counts(0, 0, 1), col.sched.counts())
        c.load(col)
        assertEquals(4, col.sched.answerButtons(c).toLong())
        // add a sibling so we can test minSpace, etc
        val c2 = c.clone()
        c2.apply {
            id = 0
            ord = 1
            due = 325
            flush(col)
        }

        // should be able to answer it
        c = card!!
        col.sched.answerCard(c, BUTTON_FOUR)
        // it should have been moved back to the original deck
        assertEquals(1, c.did)
    }

    @Test
    @Throws(Exception::class)
    fun test_cram_rem() {
        val col = getColV1()
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        val oldDue = note.cards()[0].due
        val did = addDynamicDeck("Cram")
        col.sched.rebuildDyn(did)
        col.reset()
        val c = card!!
        col.sched.answerCard(c, BUTTON_TWO)
        // answering the card will put it in the learning queue
        assertEquals(QUEUE_TYPE_LRN, c.queue)
        assertEquals(CARD_TYPE_LRN, c.type)
        assertNotEquals(c.due, oldDue)
        // if we terminate cramming prematurely it should be set back to new
        col.sched.emptyDyn(did)
        c.load(col)
        assertEquals(QUEUE_TYPE_NEW, c.queue)
        assertEquals(CARD_TYPE_NEW, c.type)
        assertEquals(oldDue, c.due)
    }

    @Test
    @Throws(Exception::class)
    fun test_cram_resched() {
        // add card
        val col = getColV1()
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        // cram deck
        val did = addDynamicDeck("Cram")
        val cram = col.decks.get(did)
        cram.put("resched", false)
        col.decks.save(cram)
        col.sched.rebuildDyn(did)
        col.reset()
        // graduate should return it to new
        var c = card!!
        assertEquals(60, col.sched.nextIvl(c, BUTTON_ONE))
        assertEquals(600, col.sched.nextIvl(c, BUTTON_TWO))
        assertEquals(0, col.sched.nextIvl(c, BUTTON_THREE))
        assertEquals("(end)", col.sched.nextIvlStr(targetContext, c, BUTTON_THREE))
        col.sched.answerCard(c, BUTTON_THREE)
        assertEquals(CARD_TYPE_NEW, c.type)
        assertEquals(QUEUE_TYPE_NEW, c.queue)
        // undue reviews should also be unaffected
        c.apply {
            ivl = 100
            queue = QUEUE_TYPE_REV
            type = CARD_TYPE_REV
            due = (col.sched.today + 25).toLong()
            factor = STARTING_FACTOR
            flush(col)
        }
        val cardcopy = c.clone()
        col.sched.rebuildDyn(did)
        col.reset()
        c = card!!
        assertEquals(600, col.sched.nextIvl(c, BUTTON_ONE))
        assertEquals(0, col.sched.nextIvl(c, BUTTON_TWO))
        assertEquals(0, col.sched.nextIvl(c, BUTTON_THREE))
        col.sched.answerCard(c, BUTTON_TWO)
        assertEquals(100, c.ivl)
        assertEquals((col.sched.today + 25).toLong(), c.due)
        // check failure too
        c = cardcopy
        c.flush(col)
        col.sched.rebuildDyn(did)
        col.reset()
        c = card!!
        col.sched.answerCard(c, BUTTON_ONE)
        col.sched.emptyDyn(did)
        c.load(col)
        assertEquals(100, c.ivl)
        assertEquals((col.sched.today + 25).toLong(), c.due)
        // fail+grad early
        c = cardcopy
        c.flush(col)
        col.sched.rebuildDyn(did)
        col.reset()
        c = card!!
        col.sched.answerCard(c, BUTTON_ONE)
        col.sched.answerCard(c, BUTTON_THREE)
        col.sched.emptyDyn(did)
        c.load(col)
        assertEquals(100, c.ivl)
        assertEquals((col.sched.today + 25).toLong(), c.due)
        // due cards - pass
        c = cardcopy
        c.due = -25
        c.flush(col)
        col.sched.rebuildDyn(did)
        col.reset()
        c = card!!
        col.sched.answerCard(c, BUTTON_THREE)
        col.sched.emptyDyn(did)
        c.load(col)
        assertEquals(100, c.ivl)
        assertEquals(-25, c.due)
        // fail
        c = cardcopy
        c.due = -25
        c.flush(col)
        col.sched.rebuildDyn(did)
        col.reset()
        c = card!!
        col.sched.answerCard(c, BUTTON_ONE)
        col.sched.emptyDyn(did)
        c.load(col)
        assertEquals(100, c.ivl)
        assertEquals(-25, c.due)
        // fail with normal grad
        c = cardcopy
        c.due = -25
        c.flush(col)
        col.sched.rebuildDyn(did)
        col.reset()
        c = card!!
        col.sched.answerCard(c, BUTTON_ONE)
        col.sched.answerCard(c, BUTTON_THREE)
        c.load(col)
        assertEquals(100, c.ivl)
        assertEquals(-25, c.due)
        // lapsed card pulled into cram
        // col.getSched()._cardConf(c)['lapse']['mult']=0.5
        // col.getSched().answerCard(c, 1)
        // col.getSched().rebuildDyn(did)
        // col.reset()
        // c = getCard()
        // col.getSched().answerCard(c, 2)
        // print c.__dict__
    }

    @Test
    @Throws(Exception::class)
    fun test_ordcycleV1() {
        val col = getColV1()
        // add two more templates and set second active
        val m = col.models.current()
        val mm = col.models
        var t = Models.newTemplate("Reverse")
        t.put("qfmt", "{{Back}}")
        t.put("afmt", "{{Front}}")
        mm.addTemplateModChanged(m!!, t)
        t = Models.newTemplate("f2")
        t.put("qfmt", "{{Front}}1")
        t.put("afmt", "{{Back}}")
        mm.addTemplateModChanged(m, t)
        mm.save(m)
        // create a new note; it should have 3 cards
        val note = col.newNote()
        note.setItem("Front", "1")
        note.setItem("Back", "1")
        col.addNote(note)
        assertEquals(3, col.cardCount().toLong())
        col.reset()
        // ordinals should arrive in order
        val sched = col.sched
        var c = sched.card
        sched.answerCard(
            c!!,
            sched.answerButtons(c) - 1
        ) // not upstream. But we are not expecting multiple getCard without review
        waitForAsyncTasksToComplete()
        assertEquals(0, c.ord)
        c = sched.card
        sched.answerCard(
            c!!,
            sched.answerButtons(c) - 1
        ) // not upstream. But we are not expecting multiple getCard without review
        waitForAsyncTasksToComplete()
        assertEquals(1, c.ord)
        c = sched.card
        sched.answerCard(
            c!!,
            sched.answerButtons(c) - 1
        ) // not upstream. But we are not expecting multiple getCard without review
        assertEquals(2, c.ord)
    }

    @Test
    @Throws(Exception::class)
    fun test_counts_idxV1() {
        val col = getColV1()
        val note = col.newNote()
        note.setItem("Front", "one")
        note.setItem("Back", "two")
        col.addNote(note)
        col.reset()
        assertEquals(Counts(1, 0, 0), col.sched.counts())
        var c = card
        // counter's been decremented but idx indicates 1
        assertEquals(Counts(0, 0, 0), col.sched.counts())
        assertEquals(Counts.Queue.NEW, col.sched.countIdx(c!!))
        // answer to move to learn queue
        col.sched.answerCard(c, BUTTON_ONE)
        assertEquals(Counts(0, 2, 0), col.sched.counts())
        // fetching again will decrement the count
        c = card
        assertEquals(Counts(0, 0, 0), col.sched.counts())
        assertEquals(Counts.Queue.LRN, col.sched.countIdx(c!!))
        // answering should add it back again
        col.sched.answerCard(c, BUTTON_ONE)
        assertEquals(Counts(0, 2, 0), col.sched.counts())
    }

    @Test
    @Throws(Exception::class)
    fun test_repCountsV1() {
        val col = getColV1()
        var note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        col.reset()
        // lrnReps should be accurate on pass/fail
        assertEquals(Counts(1, 0, 0), col.sched.counts())
        col.sched.answerCard(card!!, BUTTON_ONE)
        assertEquals(Counts(0, 2, 0), col.sched.counts())
        col.sched.answerCard(card!!, BUTTON_ONE)
        assertEquals(Counts(0, 2, 0), col.sched.counts())
        col.sched.answerCard(card!!, BUTTON_TWO)
        assertEquals(Counts(0, 1, 0), col.sched.counts())
        col.sched.answerCard(card!!, BUTTON_ONE)
        assertEquals(Counts(0, 2, 0), col.sched.counts())
        col.sched.answerCard(card!!, BUTTON_TWO)
        assertEquals(Counts(0, 1, 0), col.sched.counts())
        col.sched.answerCard(card!!, BUTTON_TWO)
        assertEquals(Counts(0, 0, 0), col.sched.counts())
        note = col.newNote()
        note.setItem("Front", "two")
        col.addNote(note)
        col.reset()
        // initial pass should be correct too
        col.sched.answerCard(card!!, BUTTON_TWO)
        assertEquals(Counts(0, 1, 0), col.sched.counts())
        col.sched.answerCard(card!!, BUTTON_ONE)
        assertEquals(Counts(0, 2, 0), col.sched.counts())
        col.sched.answerCard(card!!, BUTTON_THREE)
        assertEquals(Counts(0, 0, 0), col.sched.counts())
        // immediate graduate should work
        note = col.newNote()
        note.setItem("Front", "three")
        col.addNote(note)
        col.reset()
        col.sched.answerCard(card!!, BUTTON_THREE)
        assertEquals(Counts(0, 0, 0), col.sched.counts())
        // and failing a review should too
        note = col.newNote()
        note.setItem("Front", "three")
        col.addNote(note)
        val c = note.cards()[0]
        c.apply {
            type = CARD_TYPE_REV
            queue = QUEUE_TYPE_REV
            due = col.sched.today.toLong()
            flush(col)
        }

        col.reset()
        assertEquals(Counts(0, 0, 1), col.sched.counts())
        col.sched.answerCard(card!!, BUTTON_ONE)
        assertEquals(Counts(0, 1, 0), col.sched.counts())
    }

    @Test
    @Throws(Exception::class)
    fun test_timingV1() {
        val col = getColV1()
        // add a few review cards, due today
        for (i in 0..4) {
            val note = col.newNote()
            note.setItem("Front", "num$i")
            col.addNote(note)
            val c = note.cards()[0]
            c.apply {
                type = CARD_TYPE_REV
                queue = QUEUE_TYPE_REV
                due = 0
                flush(col)
            }
        }
        // fail the first one
        col.reset()
        var c = card!!
        // set a a fail delay of 4 seconds
        val conf = col.sched._cardConf(c)
        conf.getJSONObject("lapse").getJSONArray("delays").put(0, 1 / 15.0)
        col.decks.save(conf)
        col.sched.answerCard(c, BUTTON_ONE)
        // the next card should be another review
        c = card!!
        assertEquals(QUEUE_TYPE_REV, c.queue)
        /* TODO time
        // but if we wait for a few seconds, the failed card should come back
        orig_time = time.time;

        def adjusted_time():
        return orig_time() + 5;

        time.time = adjusted_time;
        c = getCard();
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        time.time = orig_time;

        */
    }

    @Test
    @Throws(Exception::class)
    fun test_collapseV1() {
        val col = getColV1()
        // add a note
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        col.reset()
        // test collapsing
        var c = card
        col.sched.answerCard(c!!, BUTTON_ONE)
        c = card
        col.sched.answerCard(c!!, BUTTON_THREE)
        assertNull(card)
    }

    @Test
    @Throws(Exception::class)
    fun test_deckDueV1() {
        val col = getColV1()
        // add a note with default deck
        var note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        // and one that's a child
        note = col.newNote()
        note.setItem("Front", "two")
        val default1 = addDeck("Default::1")
        note.model().put("did", default1)
        col.addNote(note)
        // make it a review card
        val c = note.cards()[0]
        c.queue = QUEUE_TYPE_REV
        c.due = 0
        c.flush(col)
        // add one more with a new deck
        note = col.newNote()
        note.setItem("Front", "two")
        @Suppress("UNUSED_VARIABLE")
        val foobar = note.model().put("did", addDeck("foo::bar"))
        col.addNote(note)
        // and one that's a sibling
        note = col.newNote()
        note.setItem("Front", "three")
        @Suppress("UNUSED_VARIABLE")
        val foobaz = note.model().put("did", addDeck("foo::baz"))
        col.addNote(note)
        col.reset()
        assertEquals(5, col.decks.allSortedNames().size.toLong())
        val tree = col.sched.deckDueTree()[0]
        assertEquals("Default", tree.value.lastDeckNameComponent)
        // sum of child and parent
        assertEquals(1, tree.value.did)
        assertEquals(1, tree.value.revCount.toLong())
        assertEquals(1, tree.value.newCount.toLong())
        // child count is just review
        val (value) = tree.children[0]
        assertEquals("1", value.lastDeckNameComponent)
        assertEquals(default1, value.did)
        assertEquals(1, value.revCount.toLong())
        assertEquals(0, value.newCount.toLong())
        // code should not fail if a card has an invalid deck
        c.did = 12345
        c.flush(col)
        col.sched.deckDueTree()
    }

    @Test
    @Throws(Exception::class)
    fun test_deckFlowV1() {
        val col = getColV1()
        // add a note with default deck
        var note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        // and one that's a child
        note = col.newNote()
        note.setItem("Front", "two")
        @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
        var default1 = note.model().put("did", addDeck("Default::2"))
        col.addNote(note)
        // and another that's higher up
        note = col.newNote()
        note.setItem("Front", "three")
        @Suppress("UNUSED_VALUE")
        default1 = note.model().put("did", addDeck("Default::1"))
        col.addNote(note)
        // should get top level one first, then ::1, then ::2
        col.reset()
        assertEquals(Counts(3, 0, 0), col.sched.counts())
        for (i in arrayOf("one", "three", "two")) {
            val c = card!!
            assertEquals(c.note(col).getItem("Front"), i)
            col.sched.answerCard(c, BUTTON_TWO)
        }
    }

    @Test
    @Throws(Exception::class)
    fun test_reorderV1() {
        val col = getColV1()
        // add a note with default deck
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        val note2 = col.newNote()
        note2.setItem("Front", "two")
        col.addNote(note2)
        assertEquals(2, note2.cards()[0].due)
        var found = false
        // 50/50 chance of being reordered
        for (i in 0..19) {
            col.sched.randomizeCards(1)
            if (note.cards()[0].due != note.id) {
                found = true
                break
            }
        }
        assertTrue(found)
        col.sched.orderCards(1)
        assertEquals(1, note.cards()[0].due)
        // shifting
        val note3 = col.newNote()
        note3.setItem("Front", "three")
        col.addNote(note3)
        val note4 = col.newNote()
        note4.setItem("Front", "four")
        col.addNote(note4)
        assertEquals(1, note.cards()[0].due)
        assertEquals(2, note2.cards()[0].due)
        assertEquals(3, note3.cards()[0].due)
        assertEquals(4, note4.cards()[0].due)
        /* todo sortCard
           col.getSched().sortCards(new long [] {note3.cards().get(0).getId(), note4.cards().get(0).getId()}, start=1, shift=true);
           assertEquals(3, note.cards().get(0).getDue());
           assertEquals(4, note2.cards().get(0).getDue());
           assertEquals(1, note3.cards().get(0).getDue());
           assertEquals(2, note4.cards().get(0).getDue());
        */
    }

    @Test
    @Throws(Exception::class)
    fun test_forgetV1() {
        val col = getColV1()
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        val c = note.cards()[0].apply {
            queue = QUEUE_TYPE_REV
            type = CARD_TYPE_REV
            ivl = 100
            due = 0
            flush(col)
        }
        col.reset()
        assertEquals(Counts(0, 0, 1), col.sched.counts())
        col.sched.forgetCards(listOf(c.id))
        col.reset()
        assertEquals(Counts(1, 0, 0), col.sched.counts())
    }

    @Test
    @Throws(Exception::class)
    fun test_reschedV1() {
        val col = getColV1()
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        val c = note.cards()[0]
        col.sched.reschedCards(listOf(c.id), 0, 0)
        c.load(col)
        assertEquals(col.sched.today.toLong(), c.due)
        assertEquals(1, c.ivl)
        assertEquals(CARD_TYPE_REV, c.type)
        assertEquals(QUEUE_TYPE_REV, c.queue)
        col.sched.reschedCards(listOf(c.id), 1, 1)
        c.load(col)
        assertEquals((col.sched.today + 1).toLong(), c.due)
        assertEquals(+1, c.ivl)
    }

    @Test
    @Throws(Exception::class)
    fun test_norelearnV1() {
        val col = getColV1()
        // add a note
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        val c = note.cards()[0].apply {
            type = CARD_TYPE_REV
            queue = QUEUE_TYPE_REV
            due = 0
            factor = STARTING_FACTOR
            setReps(3)
            lapses = 1
            ivl = 100
            startTimer()
            flush(col)
        }
        col.reset()
        col.sched.answerCard(c, BUTTON_ONE)
        col.sched._cardConf(c).getJSONObject("lapse").put("delays", JSONArray(doubleArrayOf()))
        col.sched.answerCard(c, BUTTON_ONE)
    }

    @Test
    @Throws(Exception::class)
    fun test_failmultV1() {
        val col = getColV1()
        val note = col.newNote()
        note.setItem("Front", "one")
        note.setItem("Back", "two")
        col.addNote(note)
        var c = note.cards()[0].apply {
            type = CARD_TYPE_REV
            queue = QUEUE_TYPE_REV
            ivl = 100
            due = (col.sched.today - this.ivl).toLong()
            factor = STARTING_FACTOR
            setReps(3)
            lapses = 1
            startTimer()
            flush(col)
        }

        val conf = col.sched._cardConf(c)
        conf.getJSONObject("lapse").put("mult", 0.5)
        col.decks.save(conf)
        c = card!!
        col.sched.answerCard(c, BUTTON_ONE)
        assertEquals(50, c.ivl)
        col.sched.answerCard(c, BUTTON_ONE)
        assertEquals(25, c.ivl)
    }
}
