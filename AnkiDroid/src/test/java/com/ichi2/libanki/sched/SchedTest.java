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

package com.ichi2.libanki.sched;


import android.database.Cursor;

import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.Note;
import com.ichi2.testutils.MockTime;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.libanki.Consts.CARD_TYPE_LRN;
import static com.ichi2.libanki.Consts.CARD_TYPE_NEW;
import static com.ichi2.libanki.Consts.CARD_TYPE_RELEARNING;
import static com.ichi2.libanki.Consts.CARD_TYPE_REV;
import static com.ichi2.libanki.Consts.QUEUE_TYPE_DAY_LEARN_RELEARN;
import static com.ichi2.libanki.Consts.QUEUE_TYPE_LRN;
import static com.ichi2.libanki.Consts.QUEUE_TYPE_NEW;
import static com.ichi2.libanki.Consts.QUEUE_TYPE_REV;
import static com.ichi2.libanki.Consts.STARTING_FACTOR;
import static com.ichi2.libanki.DecksTest.TEST_DECKS;
import static com.ichi2.libanki.stats.Stats.SECONDS_PER_DAY;
import static com.ichi2.testutils.AnkiAssert.checkRevIvl;
import static com.ichi2.testutils.AnkiAssert.without_unicode_isolation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import static com.ichi2.libanki.sched.Counts.Queue.*;

@RunWith(AndroidJUnit4.class)
public class SchedTest extends RobolectricTest {

    @Test
    public void unburyWorksIfDeckIsNotSelected() {
        //Issue 6200

        Sched sched = new Sched(mCol);
        Card buriedCard = createBuriedCardInDefaultDeck();
        assertThat(buriedCard.getDid(), is(Consts.DEFAULT_DECK_ID));

        assertThat("Card should be buried", getCardInDefaultDeck(sched), nullValue());

        //We want to assert that we can unbury, even if the deck we're unburying from isn't selected
        selectNewDeck();
        mSched.unburyCardsForDeck(Consts.DEFAULT_DECK_ID);

        assertThat("Card should no longer be buried", getCardInDefaultDeck(sched), notNullValue());
    }

    @Test
    public void learnCardsAreNotFiltered() {
        //Replicates Anki commit: 13c54e02d8fd2b35f6c2f4b796fc44dec65043b8

        addNoteUsingBasicModel("Hello", "World");

        Sched sched = new Sched(mCol);

        markNextCardAsGood(sched);

        long dynDeck = addDynamicDeck("Hello");

        //Act
        mSched.rebuildDyn(dynDeck);

        //Assert
        DeckDueTreeNode dynamicDeck = getCountsForDid(dynDeck);

        assertThat("A learn card should not be moved into a dyn deck", dynamicDeck.getLrnCount(), is(0));
        assertThat("A learn card should not be moved into a dyn deck", dynamicDeck.getNewCount(), is(0));
        assertThat("A learn card should not be moved into a dyn deck", dynamicDeck.getRevCount(), is(0));
    }


    private void markNextCardAsGood(Sched sched) {
        Card toAnswer = mSched.getCard();
        assertThat(toAnswer, notNullValue());

        mSched.answerCard(toAnswer, AbstractFlashcardViewer.EASE_2); //Good
    }


    @NonNull
    private DeckDueTreeNode getCountsForDid(double didToFind) {
        List<DeckDueTreeNode> tree = mSched.deckDueTree();

        for (DeckDueTreeNode node : tree) {
            if (node.getDid() == didToFind) {
                return node;
            }
        }

        throw new IllegalStateException(String.format("Could not find deck %s", didToFind));
    }


    private Card getCardInDefaultDeck(Sched s) {
        selectDefaultDeck();
        s.deferReset();
        return s.getCard();
    }


    @NonNull
    private Card createBuriedCardInDefaultDeck() {
        Note n = addNoteUsingBasicModel("Hello", "World");
        Card c = n.firstCard();
        c.setQueue(Consts.QUEUE_TYPE_SIBLING_BURIED);
        c.flush();
        return c;
    }


    private void selectNewDeck() {
        long did = addDeck("New");
        mDecks.select(did);
    }


    @Test
    public void ensureDeckTree() {
        for (String deckName : TEST_DECKS) {
            addDeck(deckName);
        }
        mSched.deckDueTree();
        List<DeckDueTreeNode> tree = mSched.deckDueTree();
        Assert.assertEquals("Tree has not the expected structure", SchedV2Test.expectedTree(mCol, false), tree);

    }

    @Test
    public void testRevLogValues() {
        addNoteUsingBasicModel("Hello", "World");

        MockTime time = (MockTime) mCol.getTime();
        Card c = mSched.getCard();
        time.setFrozen(true);
        long currentTime = time.getInternalTimeMs();
        mSched.answerCard(c, 1);

        long timeAnswered = mCol.getDb().queryLongScalar("select id from revlog");
        assertThat(timeAnswered, is(currentTime));
    }


    private void selectDefaultDeck() {
        mDecks.select(Consts.DEFAULT_DECK_ID);
    }


    /*****************
     ** autogenerated from https://github.com/ankitects/anki/blob/2c73dcb2e547c44d9e02c20a00f3c52419dc277b/pylib/tests/test_cards.py*
     *****************/

    public void test_new_v1() throws Exception {
        getCol(1);
        mCol.reset();
        assertEquals(0, mSched.newCount());
        // add a note
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        mCol.addNote(note);
        mCol.reset();
        assertEquals(1, mSched.counts().getNew());
        // fetch it
        Card c = getCard();
        assertNotNull(c);
        assertEquals(QUEUE_TYPE_NEW, c.getQueue());
        assertEquals(CARD_TYPE_NEW, c.getType());
        // if we answer it, it should become a learn card
        long t = mCol.getTime().intTime();
        mSched.answerCard(c, 1);
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        assertEquals(CARD_TYPE_LRN, c.getType());
        assertThat(c.getDue(), is(greaterThanOrEqualTo(t)));

        // disabled for now, as the learn fudging makes this randomly fail
        // // the default order should ensure siblings are not seen together, and
        // // should show all cards
        // Model m = mModels.current(); Models mm = mModels
        // JSONObject t = mm.newTemplate("Reverse")
        // t['qfmt'] = "{{Back}}"
        // t['afmt'] = "{{Front}}"
        // mm.addTemplateModChanged(m, t)
        // mm.save(m)
        // note = mCol.newNote()
        // note['Front'] = u"2"; note['Back'] = u"2"
        // mCol.addNote(note)
        // note = mCol.newNote()
        // note['Front'] = u"3"; note['Back'] = u"3"
        // mCol.addNote(note)
        // mCol.reset()
        // qs = ("2", "3", "2", "3")
        // for (int n = 0; n < 4; n++) {
        //     c = getCard()
        //     assertTrue(qs[n] in c.q())
        //     mSched.answerCard(c, 2)
        // }
    }


    @Test
    public void test_newLimits_V1() throws Exception {
        getCol(1);
        // add some notes
        long deck2 = addDeck("Default::foo");
        Note note;
        for (int i = 0; i < 30; i++) {
            note = mCol.newNote();
            note.setItem("Front", Integer.toString(i));
            if (i > 4) {
                note.model().put("did", deck2);
            }
            mCol.addNote(note);
        }
        // give the child deck a different configuration
        long c2 = mDecks.confId("new conf");
        mDecks.setConf(mDecks.get(deck2), c2);
        mCol.reset();
        // both confs have defaulted to a limit of 20
        assertEquals("both confs have defaulted to a limit of 20", 20, mSched.counts().getNew());
        // first card we get comes from parent
        Card c = getCard();
        assertEquals(1, c.getDid());
        // limit the parent to 10 cards, meaning we get 10 in total
        DeckConfig conf1 = mDecks.confForDid(1);
        conf1.getJSONObject("new").put("perDay", 10);
        mDecks.save(conf1);
        mCol.reset();
        assertEquals(10, mSched.counts().getNew());
        // if we limit child to 4, we should get 9
        DeckConfig conf2 = mDecks.confForDid(deck2);
        conf2.getJSONObject("new").put("perDay", 4);
        mDecks.save(conf2);
        mCol.reset();
        assertEquals(9, mSched.counts().getNew());
    }


    @Test
    public void test_newBoxes_v1() throws Exception {
        getCol(1);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        mCol.reset();
        Card c = getCard();
        DeckConfig conf = mSched._cardConf(c);
        conf.getJSONObject("new").put("delays", new JSONArray(new double[] {1, 2, 3, 4, 5}));
        mDecks.save(conf);
        mSched.answerCard(c, 2);
        // should handle gracefully
        conf.getJSONObject("new").put("delays", new JSONArray(new double[] {1}));
        mDecks.save(conf);
        mSched.answerCard(c, 2);
    }


    @Test
    public void test_learnV1() throws Exception {
        getCol(1);
        // add a note
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        mCol.addNote(note);
        // set as a learn card and rebuild queues
        mCol.getDb().execute("update cards set queue=0, type=0");
        mCol.reset();
        // mSched.getCard should return it, since it's due in the past
        Card c = getCard();
        assertNotNull(c);
        DeckConfig conf = mSched._cardConf(c);
        conf.getJSONObject("new").put("delays", new JSONArray(new double[] {0.5, 3, 10}));
        mDecks.save(conf);
        // fail it
        mSched.answerCard(c, 1);
        // it should have three reps left to graduation
        assertEquals(3, c.getLeft() % 1000);
        assertEquals(3, c.getLeft() / 1000);
        // it should be due in 30 seconds
        long t = Math.round(c.getDue() - mCol.getTime().intTime());
        assertThat(t, is(greaterThanOrEqualTo(25L)));
        assertThat(t, is(lessThanOrEqualTo(40L)));
        // pass it once
        mSched.answerCard(c, 2);
        // it should be due in 3 minutes
        assertEquals(Math.round(c.getDue() - mCol.getTime().intTime()), 179, 1);
        assertEquals(2, c.getLeft() % 1000);
        assertEquals(2, c.getLeft() / 1000);
        // check log is accurate
        Cursor log = mCol.getDb().getDatabase().query("select * from revlog order by id desc");
        assertTrue(log.moveToFirst());
        assertEquals(2, log.getInt(3));
        assertEquals(-180, log.getInt(4));
        assertEquals(-30, log.getInt(5));
        // pass again
        mSched.answerCard(c, 2);
        // it should be due in 10 minutes
        assertEquals(c.getDue() - mCol.getTime().intTime(), 599, 1);
        assertEquals(1, c.getLeft() % 1000);
        assertEquals(1, c.getLeft() / 1000);
        // the next pass should graduate the card
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        assertEquals(CARD_TYPE_LRN, c.getType());
        mSched.answerCard(c, 2);
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        assertEquals(CARD_TYPE_REV, c.getType());
        // should be due tomorrow, with an interval of 1
        assertEquals(mSched.getToday() + 1, c.getDue());
        assertEquals(1, c.getIvl());
        // or normal removal
        c.setType(CARD_TYPE_NEW);
        c.setQueue(QUEUE_TYPE_LRN);
        mSched.answerCard(c, 3);
        assertEquals(CARD_TYPE_REV, c.getType());
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        assertTrue(checkRevIvl(mCol, c, 4));
        // revlog should have been updated each time
        assertEquals(5, mCol.getDb().queryScalar("select count() from revlog where type = 0"));
        // now failed card handling
        c.setType(CARD_TYPE_REV);
        c.setQueue(QUEUE_TYPE_LRN);
        c.setODue(123);
        mSched.answerCard(c, 3);
        assertEquals(123, c.getDue());
        assertEquals(CARD_TYPE_REV, c.getType());
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        // we should be able to remove manually, too
        c.setType(CARD_TYPE_REV);
        c.setQueue(QUEUE_TYPE_LRN);
        c.setODue(321);
        c.flush();
        ((Sched) mSched).removeLrn();
        c.load();
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        assertEquals(321, c.getDue());
    }


    @Test
    public void test_learn_collapsedV1() throws Exception {
        getCol(1);
        // add 2 notes
        Note note = mCol.newNote();
        note.setItem("Front", "1");
        mCol.addNote(note);
        note = mCol.newNote();
        note.setItem("Front", "2");
        mCol.addNote(note);
        // set as a learn card and rebuild queues
        mCol.getDb().execute("update cards set queue=0, type=0");
        mCol.reset();
        // should get '1' first
        Card c = getCard();
        assertTrue(c.q().endsWith("1"));
        // pass it so it's due in 10 minutes
        mSched.answerCard(c, 2);
        // get the other card
        c = getCard();
        assertTrue(c.q().endsWith("2"));
        // fail it so it's due in 1 minute
        mSched.answerCard(c, 1);
        // we shouldn't get the same card again
        c = getCard();
        assertFalse(c.q().endsWith("2"));
    }


    @Test
    public void test_learn_dayV1() throws Exception {
        getCol(1);
        // add a note
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        mCol.reset();
        Card c = getCard();
        DeckConfig conf = mSched._cardConf(c);
        conf.getJSONObject("new").put("delays", new JSONArray(new double[] {1, 10, 1440, 2880}));
        mDecks.save(conf);
        // pass it
        mSched.answerCard(c, 2);
        // two reps to graduate, 1 more today
        assertEquals(3, c.getLeft() % 1000);
        assertEquals(1, c.getLeft() / 1000);
        assertEquals(new Counts(0, 1, 0), mSched.counts());
        c = getCard();

        assertEquals(SECONDS_PER_DAY, mSched.nextIvl(c, 2));
        // answering it will place it in queue 3
        mSched.answerCard(c, 2);
        assertEquals(mSched.getToday() + 1, c.getDue());
        assertEquals(QUEUE_TYPE_DAY_LEARN_RELEARN, c.getQueue());
        assertNull(getCard());
        // for testing, move it back a day
        c.setDue(c.getDue() - 1);
        c.flush();
        mCol.reset();
        assertEquals(new Counts(0, 1, 0), mSched.counts());
        c = getCard();
        // nextIvl should work
        assertEquals(SECONDS_PER_DAY * 2, mSched.nextIvl(c, 2));
        // if we fail it, it should be back in the correct queue
        mSched.answerCard(c, 1);
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        mCol.undo();
        mCol.reset();
        c = getCard();
        mSched.answerCard(c, 2);
        // simulate the passing of another two days
        c.setDue(c.getDue() - 2);
        c.flush();
        mCol.reset();
        // the last pass should graduate it into a review card
        assertEquals(SECONDS_PER_DAY, mSched.nextIvl(c, 2));
        mSched.answerCard(c, 2);
        assertEquals(CARD_TYPE_REV, c.getType());
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        // if the lapse step is tomorrow, failing it should handle the counts
        // correctly
        c.setDue(0);
        c.flush();
        mCol.reset();
        assertEquals(new Counts(0, 0, 1), mSched.counts());
        conf = mSched._cardConf(c);
        conf.getJSONObject("lapse").put("delays", new JSONArray(new double[] {1440}));
        mDecks.save(conf);
        c = getCard();
        mSched.answerCard(c, 1);
        assertEquals(CARD_TYPE_RELEARNING, c.getQueue());
        assertEquals(new Counts(0, 0, 0), mSched.counts());
    }


    @Test
    public void test_reviewsV1() throws Exception {
        getCol(1);
        // add a note
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        mCol.addNote(note);
        // set the card up as a review card, due 8 days ago
        Card c = note.cards().get(0);
        c.setType(CARD_TYPE_REV);
        c.setQueue(QUEUE_TYPE_REV);
        c.setDue(mSched.getToday() - 8);
        c.setFactor(STARTING_FACTOR);
        c.setReps(3);
        c.setLapses(1);
        c.setIvl(100);
        c.startTimer();
        c.flush();
        // save it for later use as well
        Card cardcopy = c.clone();
        // failing it should put it in the learn queue with the default options
        ////////////////////////////////////////////////////////////////////////////////////////////////////
        // different delay to new
        mCol.reset();
        DeckConfig conf = mSched._cardConf(c);
        conf.getJSONObject("lapse").put("delays", new JSONArray(new double[] {2, 20}));
        mDecks.save(conf);
        mSched.answerCard(c, 1);
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        // it should be due tomorrow, with an interval of 1
        assertEquals(mSched.getToday() + 1, c.getODue());
        assertEquals(1, c.getIvl());
        // but because it's in the learn queue, its current due time should be in
        // the future
        assertThat(c.getDue(), is(greaterThanOrEqualTo(mCol.getTime().intTime())));
        assertThat(c.getDue() - mCol.getTime().intTime(), is(greaterThan(118L)));
        // factor should have been decremented
        assertEquals(2300, c.getFactor());
        // check counters
        assertEquals(2, c.getLapses());
        assertEquals(4, c.getReps());
        // check ests.

        assertEquals(120, mSched.nextIvl(c, 1));
        assertEquals(20 * 60, mSched.nextIvl(c, 2));
        // try again with an ease of 2 instead
        ////////////////////////////////////////////////////////////////////////////////////////////////////
        c = cardcopy.clone();
        c.flush();
        mSched.answerCard(c, 2);
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        // the new interval should be (100 + 8/4) * 1.2 = 122
        assertTrue(checkRevIvl(mCol, c, 122));
        assertEquals(mSched.getToday() + c.getIvl(), c.getDue());
        // factor should have been decremented
        assertEquals(2350, c.getFactor());
        // check counters
        assertEquals(1, c.getLapses());
        assertEquals(4, c.getReps());
        // ease 3
        ////////////////////////////////////////////////////////////////////////////////////////////////////
        c = cardcopy.clone();
        c.flush();
        mSched.answerCard(c, 3);
        // the new interval should be (100 + 8/2) * 2.5 = 260
        assertTrue(checkRevIvl(mCol, c, 260));
        assertEquals(mSched.getToday() + c.getIvl(), c.getDue());
        // factor should have been left alone
        assertEquals(STARTING_FACTOR, c.getFactor());
        // ease 4
        ////////////////////////////////////////////////////////////////////////////////////////////////////
        c = cardcopy.clone();
        c.flush();
        mSched.answerCard(c, 4);
        // the new interval should be (100 + 8) * 2.5 * 1.3 = 351
        assertTrue(checkRevIvl(mCol, c, 351));
        assertEquals(mSched.getToday() + c.getIvl(), c.getDue());
        // factor should have been increased
        assertEquals(2650, c.getFactor());
    }




    @Test
    public void test_button_spacingV1() throws Exception {
        getCol(1);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        // 1 day ivl review card due now
        Card c = note.cards().get(0);
        c.setType(CARD_TYPE_REV);
        c.setQueue(QUEUE_TYPE_REV);
        c.setDue(mSched.getToday());
        c.setReps(1);
        c.setIvl(1);
        c.startTimer();
        c.flush();
        mCol.reset();
        // Upstream, there is no space in 2d
        assertEquals("2 d", without_unicode_isolation(mSched.nextIvlStr(getTargetContext(), c, 2)));
        assertEquals("3 d", without_unicode_isolation(mSched.nextIvlStr(getTargetContext(), c, 3)));
        assertEquals("4 d", without_unicode_isolation(mSched.nextIvlStr(getTargetContext(), c, 4)));
    }


    @Test
    @Ignore("disabled in commit anki@3069729776990980f34c25be66410e947e9d51a2")
    public void test_overdue_lapseV1() {
        // disabled in commit anki@3069729776990980f34c25be66410e947e9d51a2
        /*
          getCol(1);
          // add a note
          Note note = mCol.newNote();
          note.setItem("Front","one");
          mCol.addNote(note);
          // simulate a review that was lapsed and is now due for its normal review
          Card c = note.cards().get(0);
          c.setType(CARD_TYPE_REV);
          c.setQueue(QUEUE_TYPE_LRN);
          c.setDue(-1);
          c.setODue(-1);
          c.setFactor(STARTING_FACTOR);
          c.setLeft(2002);
          c.setIvl(0);
          c.flush();
          // checkpoint
          mCol.save();
          mSched.reset();
          assertEquals(new Counts(0, 2, 0), mSched.counts());
          c = getCard();
          mSched.answerCard(c, 3);
          // it should be due tomorrow
          assertEquals(mSched.getToday()+ 1, c.getDue());
          // revert to before
          /* rollback
          mCol.rollback();
          // with the default settings, the overdue card should be removed from the
          // learning queue
          mSched.reset();
          assertEquals(new Counts(0, 0, 1), mSched.counts());
        */
    }


    @Test
    public void test_finishedV1() throws Exception {
        getCol(1);
        // nothing due
        assertThat(mSched.finishedMsg(getTargetContext()).toString(), containsString("Congratulations"));
        assertThat(mSched.finishedMsg(getTargetContext()).toString(), not(containsString("limit")));
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        mCol.addNote(note);
        // have a new card
        assertThat(mSched.finishedMsg(getTargetContext()).toString(), containsString("new cards available"));
        // turn it into a review
        mCol.reset();
        Card c = note.cards().get(0);
        c.startTimer();
        mSched.answerCard(c, 3);
        // nothing should be due tomorrow, as it's due in a week
        assertThat(mSched.finishedMsg(getTargetContext()).toString(), containsString("Congratulations"));
        assertThat(mSched.finishedMsg(getTargetContext()).toString(), not(containsString("limit")));
    }


    @Test
    public void test_nextIvlV1() throws Exception {
        getCol(1);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        mCol.addNote(note);
        mCol.reset();
        DeckConfig conf = mDecks.confForDid(1);
        conf.getJSONObject("new").put("delays", new JSONArray(new double[] {0.5, 3, 10}));
        conf.getJSONObject("lapse").put("delays", new JSONArray(new double[] {1, 5, 9}));
        mDecks.save(conf);
        Card c = getCard();
        // new cards
        ////////////////////////////////////////////////////////////////////////////////////////////////////

        assertEquals(30, mSched.nextIvl(c, 1));
        assertEquals(180, mSched.nextIvl(c, 2));
        assertEquals(4 * SECONDS_PER_DAY, mSched.nextIvl(c, 3));
        mSched.answerCard(c, 1);
        // cards in learning
        ////////////////////////////////////////////////////////////////////////////////////////////////////
        assertEquals(30, mSched.nextIvl(c, 1));
        assertEquals(180, mSched.nextIvl(c, 2));
        assertEquals(4 * SECONDS_PER_DAY, mSched.nextIvl(c, 3));
        mSched.answerCard(c, 2);
        assertEquals(30, mSched.nextIvl(c, 1));
        assertEquals(600, mSched.nextIvl(c, 2));
        assertEquals(4 * SECONDS_PER_DAY, mSched.nextIvl(c, 3));
        mSched.answerCard(c, 2);
        // normal graduation is tomorrow
        assertEquals(SECONDS_PER_DAY, mSched.nextIvl(c, 2));
        assertEquals(4 * SECONDS_PER_DAY, mSched.nextIvl(c, 3));
        // lapsed cards
        ////////////////////////////////////////////////////////////////////////////////////////////////////
        c.setType(CARD_TYPE_REV);
        c.setIvl(100);
        c.setFactor(STARTING_FACTOR);
        assertEquals(60, mSched.nextIvl(c, 1));
        assertEquals(100 * SECONDS_PER_DAY, mSched.nextIvl(c, 2));
        assertEquals(100 * SECONDS_PER_DAY, mSched.nextIvl(c, 3));
        // review cards
        ////////////////////////////////////////////////////////////////////////////////////////////////////
        c.setQueue(QUEUE_TYPE_REV);
        c.setIvl(100);
        c.setFactor(STARTING_FACTOR);
        // failing it should put it at 60s
        assertEquals(60, mSched.nextIvl(c, 1));
        // or 1 day if relearn is false
        conf.getJSONObject("lapse").put("delays", new JSONArray(new double[] {}));
        mDecks.save(conf);
        assertEquals(SECONDS_PER_DAY, mSched.nextIvl(c, 1));
        // (* 100 1.2 SECONDS_PER_DAY)10368000.0
        assertEquals(10368000, mSched.nextIvl(c, 2));
        // (* 100 2.5 SECONDS_PER_DAY)21600000.0
        assertEquals(21600000, mSched.nextIvl(c, 3));
        // (* 100 2.5 1.3 SECONDS_PER_DAY)28080000.0
        assertEquals(28080000, mSched.nextIvl(c, 4));

        assertThat(without_unicode_isolation(mSched.nextIvlStr(getTargetContext(), c, 4)), is("10.8 mo"));
    }


    @Test
    public void test_misc() throws Exception {
        getCol(1);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        Card c = note.cards().get(0);
        // burying
        mSched.buryNote(c.getNid());
        mCol.reset();
        assertNull(getCard());
        mSched.unburyCards();
        mCol.reset();
        assertNotNull(getCard());
    }


    @Test
    public void test_suspendV1() throws Exception {
        getCol(1);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        Card c = note.cards().get(0);
        // suspending
        mCol.reset();
        assertNotNull(getCard());
        mSched.suspendCards(new long[] {c.getId()});
        mCol.reset();
        assertNull(getCard());
        // unsuspending
        mSched.unsuspendCards(new long[] {c.getId()});
        mCol.reset();
        assertNotNull(getCard());
        // should cope with rev cards being relearnt
        c.setDue(0);
        c.setIvl(100);
        c.setType(CARD_TYPE_REV);
        c.setQueue(QUEUE_TYPE_REV);
        c.flush();
        mCol.reset();
        c = getCard();
        mSched.answerCard(c, 1);
        assertThat(c.getDue(), is(greaterThanOrEqualTo(mCol.getTime().intTime())));
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        assertEquals(CARD_TYPE_REV, c.getType());
        mSched.suspendCards(new long[] {c.getId()});
        mSched.unsuspendCards(new long[] {c.getId()});
        c.load();
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        assertEquals(CARD_TYPE_REV, c.getType());
        assertEquals(1, c.getDue());
        // should cope with cards in cram decks
        c.setDue(1);
        c.flush();
        addDynamicDeck("tmp");
        mSched.rebuildDyn();
        c.load();
        assertNotEquals(1, c.getDue());
        assertNotEquals(1, c.getDid());
        mSched.suspendCards(new long[] {c.getId()});
        c.load();
        assertEquals(1, c.getDue());
        assertEquals(1, c.getDid());
    }


    @Test
    public void test_cram() throws Exception {
        getCol(1);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        Card c = note.cards().get(0);
        c.setIvl(100);
        c.setQueue(CARD_TYPE_REV);
        c.setType(QUEUE_TYPE_REV);
        // due in 25 days, so it's been waiting 75 days
        c.setDue(mSched.getToday() + 25);
        c.setMod(1);
        c.setFactor(STARTING_FACTOR);
        c.startTimer();
        c.flush();
        mCol.reset();
        assertEquals(new Counts(0, 0, 0), mSched.counts());
        Card cardcopy = c.clone();
        // create a dynamic deck and refresh it
        long did = addDynamicDeck("Cram");
        mSched.rebuildDyn(did);
        mCol.reset();
        // should appear as new in the deck list
        // todo: which sort
        // and should appear in the counts
        assertEquals(new Counts(1, 0, 0), mSched.counts());
        // grab it and check estimates
        c = getCard();
        assertEquals(2, mSched.answerButtons(c));
        assertEquals(600, mSched.nextIvl(c, 1));
        assertEquals(138 * 60 * 60 * 24, mSched.nextIvl(c, 2));
        Deck cram = mDecks.get(did);
        cram.put("delays", new JSONArray(new double[] {1, 10}));
        mDecks.save(cram);
        assertEquals(3, mSched.answerButtons(c));
        assertEquals(60, mSched.nextIvl(c, 1));
        assertEquals(600, mSched.nextIvl(c, 2));
        assertEquals(138 * 60 * 60 * 24, mSched.nextIvl(c, 3));
        mSched.answerCard(c, 2);
        // elapsed time was 75 days
        // factor = 2.5+1.2/2 = 1.85
        // int(75*1.85) = 138
        assertEquals(138, c.getIvl());
        assertEquals(138, c.getODue());
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        // should be logged as a cram rep
        assertEquals(3, mCol.getDb().queryLongScalar("select type from revlog order by id desc limit 1"));
        // check ivls again
        assertEquals(60, mSched.nextIvl(c, 1));
        assertEquals(138 * 60 * 60 * 24, mSched.nextIvl(c, 2));
        assertEquals(138 * 60 * 60 * 24, mSched.nextIvl(c, 3));
        // when it graduates, due is updated
        c = getCard();
        mSched.answerCard(c, 2);
        assertEquals(138, c.getIvl());
        assertEquals(138, c.getDue());
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        // and it will have moved back to the previous deck
        assertEquals(1, c.getDid());
        // cram the deck again
        mSched.rebuildDyn(did);
        mCol.reset();
        c = getCard();
        // check ivls again - passing should be idempotent
        assertEquals(60, mSched.nextIvl(c, 1));
        assertEquals(600, mSched.nextIvl(c, 2));
        assertEquals(138 * 60 * 60 * 24, mSched.nextIvl(c, 3));
        mSched.answerCard(c, 2);
        assertEquals(138, c.getIvl());
        assertEquals(138, c.getODue());
        // fail
        mSched.answerCard(c, 1);
        assertEquals(60, mSched.nextIvl(c, 1));
        assertEquals(600, mSched.nextIvl(c, 2));
        assertEquals(SECONDS_PER_DAY, mSched.nextIvl(c, 3));
        // delete the deck, returning the card mid-study
        mDecks.rem(mDecks.selected());
        assertEquals(1, mSched.deckDueTree().size());
        c.load();
        assertEquals(1, c.getIvl());
        assertEquals(mSched.getToday() + 1, c.getDue());
        // make it due
        mCol.reset();
        assertEquals(new Counts(0, 0, 0), mSched.counts());
        c.setDue(-5);
        c.setIvl(100);
        c.flush();
        mCol.reset();
        assertEquals(new Counts(0, 0, 1), mSched.counts());
        // cram again
        did = addDynamicDeck("Cram");
        mSched.rebuildDyn(did);
        mCol.reset();
        assertEquals(new Counts(0, 0, 1), mSched.counts());
        c.load();
        assertEquals(4, mSched.answerButtons(c));
        // add a sibling so we can test minSpace, etc
        Card c2 = c.clone();
        c2.setId(0);
        c2.setOrd(1);
        c2.setDue(325);
        c2.flush();
        // should be able to answer it
        c = getCard();
        mSched.answerCard(c, 4);
        // it should have been moved back to the original deck
        assertEquals(1, c.getDid());
    }


    @Test
    public void test_cram_rem() throws Exception {
        getCol(1);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        long oldDue = note.cards().get(0).getDue();
        long did = addDynamicDeck("Cram");
        mSched.rebuildDyn(did);
        mCol.reset();
        Card c = getCard();
        mSched.answerCard(c, 2);
        // answering the card will put it in the learning queue
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        assertEquals(CARD_TYPE_LRN, c.getType());
        assertNotEquals(c.getDue(), oldDue);
        // if we terminate cramming prematurely it should be set back to new
        mSched.emptyDyn(did);
        c.load();
        assertEquals(QUEUE_TYPE_NEW, c.getQueue());
        assertEquals(CARD_TYPE_NEW, c.getType());
        assertEquals(oldDue, c.getDue());
    }


    @Test
    public void test_cram_resched() throws Exception {
        // add card
        getCol(1);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        // cram deck
        long did = addDynamicDeck("Cram");
        Deck cram = mDecks.get(did);
        cram.put("resched", false);
        mDecks.save(cram);
        mSched.rebuildDyn(did);
        mCol.reset();
        // graduate should return it to new
        Card c = getCard();

        assertEquals(60, mSched.nextIvl(c, 1));
        assertEquals(600, mSched.nextIvl(c, 2));
        assertEquals(0, mSched.nextIvl(c, 3));
        assertEquals("(end)", mSched.nextIvlStr(getTargetContext(), c, 3));
        mSched.answerCard(c, 3);
        assertEquals(CARD_TYPE_NEW, c.getType());
        assertEquals(QUEUE_TYPE_NEW, c.getQueue());
        // undue reviews should also be unaffected
        c.setIvl(100);
        c.setQueue(CARD_TYPE_REV);
        c.setType(QUEUE_TYPE_REV);
        c.setDue(mSched.getToday() + 25);
        c.setFactor(STARTING_FACTOR);
        c.flush();
        Card cardcopy = c.clone();
        mSched.rebuildDyn(did);
        mCol.reset();
        c = getCard();
        assertEquals(600, mSched.nextIvl(c, 1));
        assertEquals(0, mSched.nextIvl(c, 2));
        assertEquals(0, mSched.nextIvl(c, 3));
        mSched.answerCard(c, 2);
        assertEquals(100, c.getIvl());
        assertEquals(mSched.getToday() + 25, c.getDue());
        // check failure too
        c = cardcopy;
        c.flush();
        mSched.rebuildDyn(did);
        mCol.reset();
        c = getCard();
        mSched.answerCard(c, 1);
        mSched.emptyDyn(did);
        c.load();
        assertEquals(100, c.getIvl());
        assertEquals(mSched.getToday() + 25, c.getDue());
        // fail+grad early
        c = cardcopy;
        c.flush();
        mSched.rebuildDyn(did);
        mCol.reset();
        c = getCard();
        mSched.answerCard(c, 1);
        mSched.answerCard(c, 3);
        mSched.emptyDyn(did);
        c.load();
        assertEquals(100, c.getIvl());
        assertEquals(mSched.getToday() + 25, c.getDue());
        // due cards - pass
        c = cardcopy;
        c.setDue(-25);
        c.flush();
        mSched.rebuildDyn(did);
        mCol.reset();
        c = getCard();
        mSched.answerCard(c, 3);
        mSched.emptyDyn(did);
        c.load();
        assertEquals(100, c.getIvl());
        assertEquals(-25, c.getDue());
        // fail
        c = cardcopy;
        c.setDue(-25);
        c.flush();
        mSched.rebuildDyn(did);
        mCol.reset();
        c = getCard();
        mSched.answerCard(c, 1);
        mSched.emptyDyn(did);
        c.load();
        assertEquals(100, c.getIvl());
        assertEquals(-25, c.getDue());
        // fail with normal grad
        c = cardcopy;
        c.setDue(-25);
        c.flush();
        mSched.rebuildDyn(did);
        mCol.reset();
        c = getCard();
        mSched.answerCard(c, 1);
        mSched.answerCard(c, 3);
        c.load();
        assertEquals(100, c.getIvl());
        assertEquals(-25, c.getDue());
        // lapsed card pulled into cram
        // mSched._cardConf(c)['lapse']['mult']=0.5
        // mSched.answerCard(c, 1)
        // mSched.rebuildDyn(did)
        // mCol.reset()
        // c = getCard()
        // mSched.answerCard(c, 2)
        // print c.__dict__
    }


    @Test
    public void test_ordcycleV1() throws Exception {
        getCol(1);
        // add two more templates and set second active
        Model m = mModels.current();
        Models mm = mModels;
        JSONObject t = Models.newTemplate("Reverse");
        t.put("qfmt", "{{Back}}");
        t.put("afmt", "{{Front}}");
        mm.addTemplateModChanged(m, t);
        t = Models.newTemplate("f2");
        t.put("qfmt", "{{Front}}");
        t.put("afmt", "{{Back}}");
        mm.addTemplateModChanged(m, t);
        mm.save(m);
        // create a new note; it should have 3 cards
        Note note = mCol.newNote();
        note.setItem("Front", "1");
        note.setItem("Back", "1");
        mCol.addNote(note);
        assertEquals(3, mCol.cardCount());
        mCol.reset();
        // ordinals should arrive in order
        Card c = mSched.getCard();
        mSched.answerCard(c, mSched.answerButtons(c) - 1); // not upstream. But we are not expecting multiple getCard without review
        assertEquals(0, c.getOrd());
        c = mSched.getCard();
        mSched.answerCard(c, mSched.answerButtons(c) - 1); // not upstream. But we are not expecting multiple getCard without review
        assertEquals(1, c.getOrd());
        c = mSched.getCard();
        mSched.answerCard(c, mSched.answerButtons(c) - 1); // not upstream. But we are not expecting multiple getCard without review
        assertEquals(2, c.getOrd());
    }


    @Test
    public void test_counts_idxV1() throws Exception {
        getCol(1);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        mCol.addNote(note);
        mCol.reset();
        assertEquals(new Counts(1, 0, 0), mSched.counts());
        Card c = getCard();
        // counter's been decremented but idx indicates 1
        assertEquals(new Counts(0, 0, 0), mSched.counts());
        assertEquals(NEW, mSched.countIdx(c));
        // answer to move to learn queue
        mSched.answerCard(c, 1);
        assertEquals(new Counts(0, 2, 0), mSched.counts());
        // fetching again will decrement the count
        c = getCard();
        assertEquals(new Counts(0, 0, 0), mSched.counts());
        assertEquals(LRN, mSched.countIdx(c));
        // answering should add it back again
        mSched.answerCard(c, 1);
        assertEquals(new Counts(0, 2, 0), mSched.counts());
    }


    @Test
    public void test_repCountsV1() throws Exception {
        getCol(1);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        mCol.reset();
        // lrnReps should be accurate on pass/fail
        assertEquals(new Counts(1, 0, 0), mSched.counts());
        mSched.answerCard(getCard(), 1);
        assertEquals(new Counts(0, 2, 0), mSched.counts());
        mSched.answerCard(getCard(), 1);
        assertEquals(new Counts(0, 2, 0), mSched.counts());
        mSched.answerCard(getCard(), 2);
        assertEquals(new Counts(0, 1, 0), mSched.counts());
        mSched.answerCard(getCard(), 1);
        assertEquals(new Counts(0, 2, 0), mSched.counts());
        mSched.answerCard(getCard(), 2);
        assertEquals(new Counts(0, 1, 0), mSched.counts());
        mSched.answerCard(getCard(), 2);
        assertEquals(new Counts(0, 0, 0), mSched.counts());
        note = mCol.newNote();
        note.setItem("Front", "two");
        mCol.addNote(note);
        mCol.reset();
        // initial pass should be correct too
        mSched.answerCard(getCard(), 2);
        assertEquals(new Counts(0, 1, 0), mSched.counts());
        mSched.answerCard(getCard(), 1);
        assertEquals(new Counts(0, 2, 0), mSched.counts());
        mSched.answerCard(getCard(), 3);
        assertEquals(new Counts(0, 0, 0), mSched.counts());
        // immediate graduate should work
        note = mCol.newNote();
        note.setItem("Front", "three");
        mCol.addNote(note);
        mCol.reset();
        mSched.answerCard(getCard(), 3);
        assertEquals(new Counts(0, 0, 0), mSched.counts());
        // and failing a review should too
        note = mCol.newNote();
        note.setItem("Front", "three");
        mCol.addNote(note);
        Card c = note.cards().get(0);
        c.setType(CARD_TYPE_REV);
        c.setQueue(QUEUE_TYPE_REV);
        c.setDue(mSched.getToday());
        c.flush();
        mCol.reset();
        assertEquals(new Counts(0, 0, 1), mSched.counts());
        mSched.answerCard(getCard(), 1);
        assertEquals(new Counts(0, 1, 0), mSched.counts());
    }


    @Test
    public void test_timingV1() throws Exception {
        getCol(1);
        // add a few review cards, due today
        for (int i = 0; i < 5; i++) {
            Note note = mCol.newNote();
            note.setItem("Front", "num" + i);
            mCol.addNote(note);
            Card c = note.cards().get(0);
            c.setType(CARD_TYPE_REV);
            c.setQueue(QUEUE_TYPE_REV);
            c.setDue(0);
            c.flush();
        }
        // fail the first one
        mCol.reset();
        Card c = getCard();
        // set a a fail delay of 4 seconds
        DeckConfig conf = mSched._cardConf(c);
        conf.getJSONObject("lapse").getJSONArray("delays").put(0, 1 / 15.0);
        mDecks.save(conf);
        mSched.answerCard(c, 1);
        // the next card should be another review
        c = getCard();
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
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
    public void test_collapseV1() throws Exception {
        getCol(1);
        // add a note
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        mCol.reset();
        // test collapsing
        Card c = getCard();
        mSched.answerCard(c, 1);
        c = getCard();
        mSched.answerCard(c, 3);
        assertNull(getCard());
    }


    @Test
    public void test_deckDueV1() throws Exception {
        getCol(1);
        // add a note with default deck
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        // and one that's a child
        note = mCol.newNote();
        note.setItem("Front", "two");
        long default1 = addDeck("Default::1");
        note.model().put("did", default1);
        mCol.addNote(note);
        // make it a review card
        Card c = note.cards().get(0);
        c.setQueue(QUEUE_TYPE_REV);
        c.setDue(0);
        c.flush();
        // add one more with a new deck
        note = mCol.newNote();
        note.setItem("Front", "two");
        JSONObject foobar = note.model().put("did", addDeck("foo::bar"));
        mCol.addNote(note);
        // and one that's a sibling
        note = mCol.newNote();
        note.setItem("Front", "three");
        JSONObject foobaz = note.model().put("did", addDeck("foo::baz"));
        mCol.addNote(note);
        mCol.reset();
        assertEquals(5, mDecks.allSortedNames().size());
        DeckDueTreeNode tree = mSched.deckDueTree().get(0);
        assertEquals("Default", tree.getLastDeckNameComponent());
        // sum of child and parent
        assertEquals(1, tree.getDid());
        assertEquals(1, tree.getRevCount());
        assertEquals(1, tree.getNewCount());
        // child count is just review
        DeckDueTreeNode child = tree.getChildren().get(0);
        assertEquals("1", child.getLastDeckNameComponent());
        assertEquals(default1, child.getDid());
        assertEquals(1, child.getRevCount());
        assertEquals(0, child.getNewCount());
        // code should not fail if a card has an invalid deck
        c.setDid(12345);
        c.flush();
        mSched.deckDueTree();
    }


    @Test
    public void test_deckFlowV1() throws Exception {
        getCol(1);
        // add a note with default deck
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        // and one that's a child
        note = mCol.newNote();
        note.setItem("Front", "two");
        JSONObject default1 = note.model().put("did", addDeck("Default::2"));
        mCol.addNote(note);
        // and another that's higher up
        note = mCol.newNote();
        note.setItem("Front", "three");
        default1 = note.model().put("did", addDeck("Default::1"));
        mCol.addNote(note);
        // should get top level one first, then ::1, then ::2
        mCol.reset();
        assertEquals(new Counts(3, 0, 0), mSched.counts());
        for (String i : new String[] {"one", "three", "two"}) {
            Card c = getCard();
            assertEquals(c.note().getItem("Front"), i);
            mSched.answerCard(c, 2);
        }
    }


    @Test
    public void test_reorderV1() throws Exception {
        getCol(1);
        // add a note with default deck
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        Note note2 = mCol.newNote();
        note2.setItem("Front", "two");
        mCol.addNote(note2);
        assertEquals(2, note2.cards().get(0).getDue());
        boolean found = false;
        // 50/50 chance of being reordered
        for (int i = 0; i < 20; i++) {
            mSched.randomizeCards(1);
            if (note.cards().get(0).getDue() != note.getId()) {
                found = true;
                break;
            }
        }
        assertTrue(found);
        mSched.orderCards(1);
        assertEquals(1, note.cards().get(0).getDue());
        // shifting
        Note note3 = mCol.newNote();
        note3.setItem("Front", "three");
        mCol.addNote(note3);
        Note note4 = mCol.newNote();
        note4.setItem("Front", "four");
        mCol.addNote(note4);
        assertEquals(1, note.cards().get(0).getDue());
        assertEquals(2, note2.cards().get(0).getDue());
        assertEquals(3, note3.cards().get(0).getDue());
        assertEquals(4, note4.cards().get(0).getDue());
        /* todo sortCard
           mSched.sortCards(new long [] {note3.cards().get(0).getId(), note4.cards().get(0).getId()}, start=1, shift=true);
           assertEquals(3, note.cards().get(0).getDue());
           assertEquals(4, note2.cards().get(0).getDue());
           assertEquals(1, note3.cards().get(0).getDue());
           assertEquals(2, note4.cards().get(0).getDue());
        */
    }


    @Test
    public void test_forgetV1() throws Exception {
        getCol(1);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        Card c = note.cards().get(0);
        c.setQueue(QUEUE_TYPE_REV);
        c.setType(CARD_TYPE_REV);
        c.setIvl(100);
        c.setDue(0);
        c.flush();
        mCol.reset();
        assertEquals(new Counts(0, 0, 1), mSched.counts());
        mSched.forgetCards(Collections.singletonList(c.getId()));
        mCol.reset();
        assertEquals(new Counts(1, 0, 0), mSched.counts());
    }


    @Test
    public void test_reschedV1() throws Exception {
        getCol(1);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        Card c = note.cards().get(0);
        mSched.reschedCards(Collections.singletonList(c.getId()), 0, 0);
        c.load();
        assertEquals(mSched.getToday(), c.getDue());
        assertEquals(1, c.getIvl());
        assertEquals(CARD_TYPE_REV, c.getType());
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        mSched.reschedCards(Collections.singletonList(c.getId()), 1, 1);
        c.load();
        assertEquals(mSched.getToday() + 1, c.getDue());
        assertEquals(+1, c.getIvl());
    }


    @Test
    public void test_norelearnV1() throws Exception {
        getCol(1);
        // add a note
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        Card c = note.cards().get(0);
        c.setType(CARD_TYPE_REV);
        c.setQueue(QUEUE_TYPE_REV);
        c.setDue(0);
        c.setFactor(STARTING_FACTOR);
        c.setReps(3);
        c.setLapses(1);
        c.setIvl(100);
        c.startTimer();
        c.flush();
        mCol.reset();
        mSched.answerCard(c, 1);
        mSched._cardConf(c).getJSONObject("lapse").put("delays", new JSONArray(new double[] {}));
        mSched.answerCard(c, 1);
    }


    @Test
    public void test_failmultV1() throws Exception {
        getCol(1);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        mCol.addNote(note);
        Card c = note.cards().get(0);
        c.setType(CARD_TYPE_REV);
        c.setQueue(QUEUE_TYPE_REV);
        c.setIvl(100);
        c.setDue(mSched.getToday() - c.getIvl());
        c.setFactor(STARTING_FACTOR);
        c.setReps(3);
        c.setLapses(1);
        c.startTimer();
        c.flush();
        DeckConfig conf = mSched._cardConf(c);
        conf.getJSONObject("lapse").put("mult", 0.5);
        mDecks.save(conf);
        c = getCard();
        mSched.answerCard(c, 1);
        assertEquals(50, c.getIvl());
        mSched.answerCard(c, 1);
        assertEquals(25, c.getIvl());
    }
}
