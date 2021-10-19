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

import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.ModelManager;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.Note;
import com.ichi2.testutils.MockTime;
import com.ichi2.testutils.MutableTime;
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

import static com.ichi2.libanki.Consts.BUTTON_FOUR;
import static com.ichi2.libanki.Consts.BUTTON_ONE;
import static com.ichi2.libanki.Consts.BUTTON_THREE;
import static com.ichi2.libanki.Consts.BUTTON_TWO;
import static com.ichi2.libanki.Consts.CARD_TYPE_LRN;
import static com.ichi2.libanki.Consts.CARD_TYPE_NEW;
import static com.ichi2.libanki.Consts.CARD_TYPE_RELEARNING;
import static com.ichi2.libanki.Consts.CARD_TYPE_REV;
import static com.ichi2.libanki.Consts.DEFAULT_DECK_ID;
import static com.ichi2.libanki.Consts.QUEUE_TYPE_DAY_LEARN_RELEARN;
import static com.ichi2.libanki.Consts.QUEUE_TYPE_LRN;
import static com.ichi2.libanki.Consts.QUEUE_TYPE_NEW;
import static com.ichi2.libanki.Consts.QUEUE_TYPE_REV;
import static com.ichi2.libanki.Consts.QUEUE_TYPE_SIBLING_BURIED;
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static com.ichi2.libanki.sched.Counts.Queue.*;

@RunWith(AndroidJUnit4.class)
public class SchedTest extends RobolectricTest {

    @Test
    public void unburyWorksIfDeckIsNotSelected() {
        //Issue 6200

        Sched sched = new Sched(getCol());
        Card buriedCard = createBuriedCardInDefaultDeck();
        assertThat(buriedCard.getDid(), is(DEFAULT_DECK_ID));

        assertThat("Card should be buried", getCardInDefaultDeck(sched), nullValue());

        //We want to assert that we can unbury, even if the deck we're unburying from isn't selected
        selectNewDeck();
        sched.unburyCardsForDeck(DEFAULT_DECK_ID);

        assertThat("Card should no longer be buried", getCardInDefaultDeck(sched), notNullValue());
    }

    @Test
    public void learnCardsAreNotFiltered() {
        //Replicates Anki commit: 13c54e02d8fd2b35f6c2f4b796fc44dec65043b8

        addNoteUsingBasicModel("Hello", "World");

        Sched sched = new Sched(getCol());

        markNextCardAsGood(sched);

        long dynDeck = addDynamicDeck("Hello");

        //Act
        sched.rebuildDyn(dynDeck);

        //Assert
        DeckDueTreeNode dynamicDeck = getCountsForDid(dynDeck);

        assertThat("A learn card should not be moved into a dyn deck", dynamicDeck.getLrnCount(), is(0));
        assertThat("A learn card should not be moved into a dyn deck", dynamicDeck.getNewCount(), is(0));
        assertThat("A learn card should not be moved into a dyn deck", dynamicDeck.getRevCount(), is(0));
    }


    private void markNextCardAsGood(Sched sched) {
        Card toAnswer = sched.getCard();
        assertThat(toAnswer, notNullValue());

        sched.answerCard(toAnswer, BUTTON_TWO); //Good
    }


    @NonNull
    private DeckDueTreeNode getCountsForDid(double didToFind) {
        List<DeckDueTreeNode> tree = getCol().getSched().deckDueTree();

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
        c.setQueue(QUEUE_TYPE_SIBLING_BURIED);
        c.flush();
        return c;
    }


    private void selectNewDeck() {
        long did = addDeck("New");
        getCol().getDecks().select(did);
    }


    @Test
    public void ensureDeckTree() {
        for (String deckName : TEST_DECKS) {
            addDeck(deckName);
        }
        getCol().getSched().deckDueTree();
        AbstractSched sched = getCol().getSched();
        List<DeckDueTreeNode> tree = sched.deckDueTree();
        Assert.assertEquals("Tree has not the expected structure", SchedV2Test.expectedTree(getCol(), false), tree);

    }

    @Test
    public void testRevLogValues() {
        MutableTime time = new MutableTime(MockTime.timeStamp(2020, 8, 4, 11, 22, 19, 123), 10);
        Collection col =  CollectionHelper.getInstance().getCol(getTargetContext(), time);
        addNoteUsingBasicModel("Hello", "World");

        AbstractSched sched = col.getSched();
        Card c = sched.getCard();
        time.setFrozen(true);
        long currentTime = time.getInternalTimeMs();
        sched.answerCard(c, BUTTON_ONE);

        long timeAnswered = col.getDb().queryLongScalar("select id from revlog");
        assertThat(timeAnswered, is(currentTime));
    }


    private void selectDefaultDeck() {
        getCol().getDecks().select(DEFAULT_DECK_ID);
    }


    /*****************
     ** autogenerated from https://github.com/ankitects/anki/blob/2c73dcb2e547c44d9e02c20a00f3c52419dc277b/pylib/tests/test_cards.py*
     *****************/
    private Collection getColV1() throws ConfirmModSchemaException {
        Collection col = getCol();
        col.changeSchedulerVer(1);
        return col;
    }


    public void test_new_v1() throws Exception {
        Collection col = getColV1();
        col.reset();
        assertEquals(0, col.getSched().newCount());
        // add a note
        Note note = col.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        col.addNote(note);
        col.reset();
        assertEquals(1, col.getSched().counts().getNew());
        // fetch it
        Card c = getCard();
        assertNotNull(c);
        assertEquals(QUEUE_TYPE_NEW, c.getQueue());
        assertEquals(CARD_TYPE_NEW, c.getType());
        // if we answer it, it should become a learn card
        long t = col.getTime().intTime();
        col.getSched().answerCard(c, BUTTON_ONE);
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        assertEquals(CARD_TYPE_LRN, c.getType());
        assertThat(c.getDue(), is(greaterThanOrEqualTo(t)));

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
    public void test_newLimits_V1() throws Exception {
        Collection col = getColV1();
        // add some notes
        long deck2 = addDeck("Default::foo");
        Note note;
        for (int i = 0; i < 30; i++) {
            note = col.newNote();
            note.setItem("Front", Integer.toString(i));
            if (i > 4) {
                note.model().put("did", deck2);
            }
            col.addNote(note);
        }
        // give the child deck a different configuration
        long c2 = col.getDecks().confId("new conf");
        col.getDecks().setConf(col.getDecks().get(deck2), c2);
        col.reset();
        // both confs have defaulted to a limit of 20
        assertEquals("both confs have defaulted to a limit of 20", 20, col.getSched().counts().getNew());
        // first card we get comes from parent
        Card c = getCard();
        assertEquals(1, c.getDid());
        // limit the parent to 10 cards, meaning we get 10 in total
        DeckConfig conf1 = col.getDecks().confForDid(1);
        conf1.getJSONObject("new").put("perDay", 10);
        col.getDecks().save(conf1);
        col.reset();
        assertEquals(10, col.getSched().counts().getNew());
        // if we limit child to 4, we should get 9
        DeckConfig conf2 = col.getDecks().confForDid(deck2);
        conf2.getJSONObject("new").put("perDay", 4);
        col.getDecks().save(conf2);
        col.reset();
        assertEquals(9, col.getSched().counts().getNew());
    }


    @Test
    public void test_newBoxes_v1() throws Exception {
        Collection col = getColV1();
        Note note = col.newNote();
        note.setItem("Front", "one");
        col.addNote(note);
        col.reset();
        Card c = getCard();
        DeckConfig conf = col.getSched()._cardConf(c);
        conf.getJSONObject("new").put("delays", new JSONArray(new double[] {1, 2, 3, 4, 5}));
        col.getDecks().save(conf);
        col.getSched().answerCard(c, BUTTON_TWO);
        // should handle gracefully
        conf.getJSONObject("new").put("delays", new JSONArray(new double[] {1}));
        col.getDecks().save(conf);
        col.getSched().answerCard(c, BUTTON_TWO);
    }


    @Test
    public void test_learnV1() throws Exception {
        Collection col = getColV1();
        // add a note
        Note note = col.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        col.addNote(note);
        // set as a learn card and rebuild queues
        col.getDb().execute("update cards set queue=0, type=0");
        col.reset();
        // sched.getCard should return it, since it's due in the past
        Card c = getCard();
        assertNotNull(c);
        DeckConfig conf = col.getSched()._cardConf(c);
        conf.getJSONObject("new").put("delays", new JSONArray(new double[] {0.5, 3, 10}));
        col.getDecks().save(conf);
        // fail it
        col.getSched().answerCard(c, BUTTON_ONE);
        // it should have three reps left to graduation
        assertEquals(3, c.getLeft() % 1000);
        assertEquals(3, c.getLeft() / 1000);
        // it should be due in 30 seconds
        long t = Math.round(c.getDue() - col.getTime().intTime());
        assertThat(t, is(greaterThanOrEqualTo(25L)));
        assertThat(t, is(lessThanOrEqualTo(40L)));
        // pass it once
        col.getSched().answerCard(c, BUTTON_TWO);
        // it should be due in 3 minutes
        assertEquals(Math.round(c.getDue() - col.getTime().intTime()), 179, 1);
        assertEquals(2, c.getLeft() % 1000);
        assertEquals(2, c.getLeft() / 1000);
        // check log is accurate
        Cursor log = col.getDb().getDatabase().query("select * from revlog order by id desc");
        assertTrue(log.moveToFirst());
        assertEquals(2, log.getInt(3));
        assertEquals(-180, log.getInt(4));
        assertEquals(-30, log.getInt(5));
        // pass again
        col.getSched().answerCard(c, BUTTON_TWO);
        // it should be due in 10 minutes
        assertEquals(c.getDue() - col.getTime().intTime(), 599, 1);
        assertEquals(1, c.getLeft() % 1000);
        assertEquals(1, c.getLeft() / 1000);
        // the next pass should graduate the card
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        assertEquals(CARD_TYPE_LRN, c.getType());
        col.getSched().answerCard(c, BUTTON_TWO);
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        assertEquals(CARD_TYPE_REV, c.getType());
        // should be due tomorrow, with an interval of 1
        assertEquals(col.getSched().getToday() + 1, c.getDue());
        assertEquals(1, c.getIvl());
        // or normal removal
        c.setType(CARD_TYPE_NEW);
        c.setQueue(QUEUE_TYPE_LRN);
        col.getSched().answerCard(c, BUTTON_THREE);
        assertEquals(CARD_TYPE_REV, c.getType());
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        assertTrue(checkRevIvl(col, c, 4));
        // revlog should have been updated each time
        assertEquals(5, col.getDb().queryScalar("select count() from revlog where type = 0"));
        // now failed card handling
        c.setType(CARD_TYPE_REV);
        c.setQueue(QUEUE_TYPE_LRN);
        c.setODue(123);
        col.getSched().answerCard(c, BUTTON_THREE);
        assertEquals(123, c.getDue());
        assertEquals(CARD_TYPE_REV, c.getType());
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        // we should be able to remove manually, too
        c.setType(CARD_TYPE_REV);
        c.setQueue(QUEUE_TYPE_LRN);
        c.setODue(321);
        c.flush();
        ((Sched) col.getSched()).removeLrn();
        c.load();
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        assertEquals(321, c.getDue());
    }


    @Test
    public void test_learn_collapsedV1() throws Exception {
        Collection col = getColV1();
        // add 2 notes
        Note note = col.newNote();
        note.setItem("Front", "1");
        col.addNote(note);
        note = col.newNote();
        note.setItem("Front", "2");
        col.addNote(note);
        // set as a learn card and rebuild queues
        col.getDb().execute("update cards set queue=0, type=0");
        col.reset();
        // should get '1' first
        Card c = getCard();
        assertTrue(c.q().endsWith("1"));
        // pass it so it's due in 10 minutes
        col.getSched().answerCard(c, BUTTON_TWO);
        // get the other card
        c = getCard();
        assertTrue(c.q().endsWith("2"));
        // fail it so it's due in 1 minute
        col.getSched().answerCard(c, BUTTON_ONE);
        // we shouldn't get the same card again
        c = getCard();
        assertFalse(c.q().endsWith("2"));
    }


    @Test
    public void test_learn_dayV1() throws Exception {
        Collection col = getColV1();
        // add a note
        Note note = col.newNote();
        note.setItem("Front", "one");
        col.addNote(note);
        col.reset();
        Card c = getCard();
        DeckConfig conf = col.getSched()._cardConf(c);
        conf.getJSONObject("new").put("delays", new JSONArray(new double[] {1, 10, 1440, 2880}));
        col.getDecks().save(conf);
        // pass it
        col.getSched().answerCard(c, BUTTON_TWO);
        // two reps to graduate, 1 more today
        assertEquals(3, c.getLeft() % 1000);
        assertEquals(1, c.getLeft() / 1000);
        assertEquals(new Counts(0, 1, 0), col.getSched().counts());
        c = getCard();

        assertEquals(SECONDS_PER_DAY, col.getSched().nextIvl(c, BUTTON_TWO));
        // answering it will place it in queue 3
        col.getSched().answerCard(c, BUTTON_TWO);
        assertEquals(col.getSched().getToday() + 1, c.getDue());
        assertEquals(QUEUE_TYPE_DAY_LEARN_RELEARN, c.getQueue());
        assertNull(getCard());
        // for testing, move it back a day
        c.setDue(c.getDue() - 1);
        c.flush();
        col.reset();
        assertEquals(new Counts(0, 1, 0), col.getSched().counts());
        c = getCard();
        // nextIvl should work
        assertEquals(SECONDS_PER_DAY * 2, col.getSched().nextIvl(c, BUTTON_TWO));
        // if we fail it, it should be back in the correct queue
        col.getSched().answerCard(c, BUTTON_ONE);
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        col.undo();
        col.reset();
        c = getCard();
        col.getSched().answerCard(c, BUTTON_TWO);
        // simulate the passing of another two days
        c.setDue(c.getDue() - 2);
        c.flush();
        col.reset();
        // the last pass should graduate it into a review card
        assertEquals(SECONDS_PER_DAY, col.getSched().nextIvl(c, BUTTON_TWO));
        col.getSched().answerCard(c, BUTTON_TWO);
        assertEquals(CARD_TYPE_REV, c.getType());
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        // if the lapse step is tomorrow, failing it should handle the counts
        // correctly
        c.setDue(0);
        c.flush();
        col.reset();
        assertEquals(new Counts(0, 0, 1), col.getSched().counts());
        conf = col.getSched()._cardConf(c);
        conf.getJSONObject("lapse").put("delays", new JSONArray(new double[] {1440}));
        col.getDecks().save(conf);
        c = getCard();
        col.getSched().answerCard(c, BUTTON_ONE);
        assertEquals(CARD_TYPE_RELEARNING, c.getQueue());
        assertEquals(new Counts(0, 0, 0), col.getSched().counts());
    }


    @Test
    public void test_reviewsV1() throws Exception {
        Collection col = getColV1();
        // add a note
        Note note = col.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        col.addNote(note);
        // set the card up as a review card, due 8 days ago
        Card c = note.cards().get(0);
        c.setType(CARD_TYPE_REV);
        c.setQueue(QUEUE_TYPE_REV);
        c.setDue(col.getSched().getToday() - 8);
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
        col.reset();
        DeckConfig conf = col.getSched()._cardConf(c);
        conf.getJSONObject("lapse").put("delays", new JSONArray(new double[] {2, 20}));
        col.getDecks().save(conf);
        col.getSched().answerCard(c, BUTTON_ONE);
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        // it should be due tomorrow, with an interval of 1
        assertEquals(col.getSched().getToday() + 1, c.getODue());
        assertEquals(1, c.getIvl());
        // but because it's in the learn queue, its current due time should be in
        // the future
        assertThat(c.getDue(), is(greaterThanOrEqualTo(col.getTime().intTime())));
        assertThat(c.getDue() - col.getTime().intTime(), is(greaterThan(118L)));
        // factor should have been decremented
        assertEquals(2300, c.getFactor());
        // check counters
        assertEquals(2, c.getLapses());
        assertEquals(4, c.getReps());
        // check ests.

        assertEquals(120, col.getSched().nextIvl(c, BUTTON_ONE));
        assertEquals(20 * 60, col.getSched().nextIvl(c, BUTTON_TWO));
        // try again with an ease of 2 instead
        ////////////////////////////////////////////////////////////////////////////////////////////////////
        c = cardcopy.clone();
        c.flush();
        col.getSched().answerCard(c, BUTTON_TWO);
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        // the new interval should be (100 + 8/4) * 1.2 = 122
        assertTrue(checkRevIvl(col, c, 122));
        assertEquals(col.getSched().getToday() + c.getIvl(), c.getDue());
        // factor should have been decremented
        assertEquals(2350, c.getFactor());
        // check counters
        assertEquals(1, c.getLapses());
        assertEquals(4, c.getReps());
        // ease 3
        ////////////////////////////////////////////////////////////////////////////////////////////////////
        c = cardcopy.clone();
        c.flush();
        col.getSched().answerCard(c, BUTTON_THREE);
        // the new interval should be (100 + 8/2) * 2.5 = 260
        assertTrue(checkRevIvl(col, c, 260));
        assertEquals(col.getSched().getToday() + c.getIvl(), c.getDue());
        // factor should have been left alone
        assertEquals(STARTING_FACTOR, c.getFactor());
        // ease 4
        ////////////////////////////////////////////////////////////////////////////////////////////////////
        c = cardcopy.clone();
        c.flush();
        col.getSched().answerCard(c, BUTTON_FOUR);
        // the new interval should be (100 + 8) * 2.5 * 1.3 = 351
        assertTrue(checkRevIvl(col, c, 351));
        assertEquals(col.getSched().getToday() + c.getIvl(), c.getDue());
        // factor should have been increased
        assertEquals(2650, c.getFactor());
    }




    @Test
    public void test_button_spacingV1() throws Exception {
        Collection col = getColV1();
        Note note = col.newNote();
        note.setItem("Front", "one");
        col.addNote(note);
        // 1 day ivl review card due now
        Card c = note.cards().get(0);
        c.setType(CARD_TYPE_REV);
        c.setQueue(QUEUE_TYPE_REV);
        c.setDue(col.getSched().getToday());
        c.setReps(1);
        c.setIvl(1);
        c.startTimer();
        c.flush();
        col.reset();
        // Upstream, there is no space in 2d
        assertEquals("2 d", without_unicode_isolation(col.getSched().nextIvlStr(getTargetContext(), c, BUTTON_TWO)));
        assertEquals("3 d", without_unicode_isolation(col.getSched().nextIvlStr(getTargetContext(), c, BUTTON_THREE)));
        assertEquals("4 d", without_unicode_isolation(col.getSched().nextIvlStr(getTargetContext(), c, BUTTON_FOUR)));
    }


    @Test
    @Ignore("disabled in commit anki@3069729776990980f34c25be66410e947e9d51a2")
    public void test_overdue_lapseV1() {
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
          c.flush();
          // checkpoint
          col.save();
          col.getSched().reset();
          assertEquals(new Counts(0, 2, 0), col.getSched().counts());
          c = getCard();
          col.getSched().answerCard(c, BUTTON_THREE);
          // it should be due tomorrow
          assertEquals(col.getSched().getToday()+ 1, c.getDue());
          // revert to before
          /* rollback
          col.rollback();
          // with the default settings, the overdue card should be removed from the
          // learning queue
          col.getSched().reset();
          assertEquals(new Counts(0, 0, 1), col.getSched().counts());
        */
    }


    @Test
    public void test_finishedV1() throws Exception {
        Collection col = getColV1();
        // nothing due
        assertThat(col.getSched().finishedMsg(getTargetContext()).toString(), containsString("Congratulations"));
        assertThat(col.getSched().finishedMsg(getTargetContext()).toString(), not(containsString("limit")));
        Note note = col.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        col.addNote(note);
        // have a new card
        assertThat(col.getSched().finishedMsg(getTargetContext()).toString(), containsString("new cards available"));
        // turn it into a review
        col.reset();
        Card c = note.cards().get(0);
        c.startTimer();
        col.getSched().answerCard(c, BUTTON_THREE);
        // nothing should be due tomorrow, as it's due in a week
        assertThat(col.getSched().finishedMsg(getTargetContext()).toString(), containsString("Congratulations"));
        assertThat(col.getSched().finishedMsg(getTargetContext()).toString(), not(containsString("limit")));
    }


    @Test
    public void test_nextIvlV1() throws Exception {
        Collection col = getColV1();
        Note note = col.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        col.addNote(note);
        col.reset();
        DeckConfig conf = col.getDecks().confForDid(1);
        conf.getJSONObject("new").put("delays", new JSONArray(new double[] {0.5, 3, 10}));
        conf.getJSONObject("lapse").put("delays", new JSONArray(new double[] {1, 5, 9}));
        col.getDecks().save(conf);
        Card c = getCard();
        // new cards
        ////////////////////////////////////////////////////////////////////////////////////////////////////

        assertEquals(30, col.getSched().nextIvl(c, BUTTON_ONE));
        assertEquals(180, col.getSched().nextIvl(c, BUTTON_TWO));
        assertEquals(4 * SECONDS_PER_DAY, col.getSched().nextIvl(c, BUTTON_THREE));
        col.getSched().answerCard(c, BUTTON_ONE);
        // cards in learning
        ////////////////////////////////////////////////////////////////////////////////////////////////////
        assertEquals(30, col.getSched().nextIvl(c, BUTTON_ONE));
        assertEquals(180, col.getSched().nextIvl(c, BUTTON_TWO));
        assertEquals(4 * SECONDS_PER_DAY, col.getSched().nextIvl(c, BUTTON_THREE));
        col.getSched().answerCard(c, BUTTON_TWO);
        assertEquals(30, col.getSched().nextIvl(c, BUTTON_ONE));
        assertEquals(600, col.getSched().nextIvl(c, BUTTON_TWO));
        assertEquals(4 * SECONDS_PER_DAY, col.getSched().nextIvl(c, BUTTON_THREE));
        col.getSched().answerCard(c, BUTTON_TWO);
        // normal graduation is tomorrow
        assertEquals(SECONDS_PER_DAY, col.getSched().nextIvl(c, BUTTON_TWO));
        assertEquals(4 * SECONDS_PER_DAY, col.getSched().nextIvl(c, BUTTON_THREE));
        // lapsed cards
        ////////////////////////////////////////////////////////////////////////////////////////////////////
        c.setType(CARD_TYPE_REV);
        c.setIvl(100);
        c.setFactor(STARTING_FACTOR);
        assertEquals(60, col.getSched().nextIvl(c, BUTTON_ONE));
        assertEquals(100 * SECONDS_PER_DAY, col.getSched().nextIvl(c, BUTTON_TWO));
        assertEquals(100 * SECONDS_PER_DAY, col.getSched().nextIvl(c, BUTTON_THREE));
        // review cards
        ////////////////////////////////////////////////////////////////////////////////////////////////////
        c.setQueue(QUEUE_TYPE_REV);
        c.setIvl(100);
        c.setFactor(STARTING_FACTOR);
        // failing it should put it at 60s
        assertEquals(60, col.getSched().nextIvl(c, BUTTON_ONE));
        // or 1 day if relearn is false
        conf.getJSONObject("lapse").put("delays", new JSONArray(new double[] {}));
        col.getDecks().save(conf);
        assertEquals(SECONDS_PER_DAY, col.getSched().nextIvl(c, BUTTON_ONE));
        // (* 100 1.2 SECONDS_PER_DAY)10368000.0
        assertEquals(10368000, col.getSched().nextIvl(c, BUTTON_TWO));
        // (* 100 2.5 SECONDS_PER_DAY)21600000.0
        assertEquals(21600000, col.getSched().nextIvl(c, BUTTON_THREE));
        // (* 100 2.5 1.3 SECONDS_PER_DAY)28080000.0
        assertEquals(28080000, col.getSched().nextIvl(c, BUTTON_FOUR));

        assertThat(without_unicode_isolation(col.getSched().nextIvlStr(getTargetContext(), c, BUTTON_FOUR)), is("10.8 mo"));
    }


    @Test
    public void test_misc() throws Exception {
        Collection col = getColV1();
        Note note = col.newNote();
        note.setItem("Front", "one");
        col.addNote(note);
        Card c = note.cards().get(0);
        // burying
        col.getSched().buryNote(c.getNid());
        col.reset();
        assertNull(getCard());
        col.getSched().unburyCards();
        col.reset();
        assertNotNull(getCard());
    }


    @Test
    public void test_suspendV1() throws Exception {
        Collection col = getColV1();
        Note note = col.newNote();
        note.setItem("Front", "one");
        col.addNote(note);
        Card c = note.cards().get(0);
        // suspending
        col.reset();
        assertNotNull(getCard());
        col.getSched().suspendCards(new long[] {c.getId()});
        col.reset();
        assertNull(getCard());
        // unsuspending
        col.getSched().unsuspendCards(new long[] {c.getId()});
        col.reset();
        assertNotNull(getCard());
        // should cope with rev cards being relearnt
        c.setDue(0);
        c.setIvl(100);
        c.setType(CARD_TYPE_REV);
        c.setQueue(QUEUE_TYPE_REV);
        c.flush();
        col.reset();
        c = getCard();
        col.getSched().answerCard(c, BUTTON_ONE);
        assertThat(c.getDue(), is(greaterThanOrEqualTo(col.getTime().intTime())));
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        assertEquals(CARD_TYPE_REV, c.getType());
        col.getSched().suspendCards(new long[] {c.getId()});
        col.getSched().unsuspendCards(new long[] {c.getId()});
        c.load();
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        assertEquals(CARD_TYPE_REV, c.getType());
        assertEquals(1, c.getDue());
        // should cope with cards in cram decks
        c.setDue(1);
        c.flush();
        addDynamicDeck("tmp");
        col.getSched().rebuildDyn();
        c.load();
        assertNotEquals(1, c.getDue());
        assertNotEquals(1, c.getDid());
        col.getSched().suspendCards(new long[] {c.getId()});
        c.load();
        assertEquals(1, c.getDue());
        assertEquals(1, c.getDid());
    }


    @Test
    public void test_cram() throws Exception {
        Collection col = getColV1();
        Note note = col.newNote();
        note.setItem("Front", "one");
        col.addNote(note);
        Card c = note.cards().get(0);
        c.setIvl(100);
        c.setQueue(QUEUE_TYPE_REV);
        c.setType(CARD_TYPE_REV);
        // due in 25 days, so it's been waiting 75 days
        c.setDue(col.getSched().getToday() + 25);
        c.setMod(1);
        c.setFactor(STARTING_FACTOR);
        c.startTimer();
        c.flush();
        col.reset();
        assertEquals(new Counts(0, 0, 0), col.getSched().counts());
        Card cardcopy = c.clone();
        // create a dynamic deck and refresh it
        long did = addDynamicDeck("Cram");
        col.getSched().rebuildDyn(did);
        col.reset();
        // should appear as new in the deck list
        // todo: which sort
        // and should appear in the counts
        assertEquals(new Counts(1, 0, 0), col.getSched().counts());
        // grab it and check estimates
        c = getCard();
        assertEquals(2, col.getSched().answerButtons(c));
        assertEquals(600, col.getSched().nextIvl(c, BUTTON_ONE));
        assertEquals(138 * 60 * 60 * 24, col.getSched().nextIvl(c, BUTTON_TWO));
        Deck cram = col.getDecks().get(did);
        cram.put("delays", new JSONArray(new double[] {1, 10}));
        col.getDecks().save(cram);
        assertEquals(3, col.getSched().answerButtons(c));
        assertEquals(60, col.getSched().nextIvl(c, BUTTON_ONE));
        assertEquals(600, col.getSched().nextIvl(c, BUTTON_TWO));
        assertEquals(138 * 60 * 60 * 24, col.getSched().nextIvl(c, BUTTON_THREE));
        col.getSched().answerCard(c, BUTTON_TWO);
        // elapsed time was 75 days
        // factor = 2.5+1.2/2 = 1.85
        // int(75*1.85) = 138
        assertEquals(138, c.getIvl());
        assertEquals(138, c.getODue());
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        // should be logged as a cram rep
        assertEquals(3, col.getDb().queryLongScalar("select type from revlog order by id desc limit 1"));
        // check ivls again
        assertEquals(60, col.getSched().nextIvl(c, BUTTON_ONE));
        assertEquals(138 * 60 * 60 * 24, col.getSched().nextIvl(c, BUTTON_TWO));
        assertEquals(138 * 60 * 60 * 24, col.getSched().nextIvl(c, BUTTON_THREE));
        // when it graduates, due is updated
        c = getCard();
        col.getSched().answerCard(c, BUTTON_TWO);
        assertEquals(138, c.getIvl());
        assertEquals(138, c.getDue());
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        // and it will have moved back to the previous deck
        assertEquals(1, c.getDid());
        // cram the deck again
        col.getSched().rebuildDyn(did);
        col.reset();
        c = getCard();
        // check ivls again - passing should be idempotent
        assertEquals(60, col.getSched().nextIvl(c, BUTTON_ONE));
        assertEquals(600, col.getSched().nextIvl(c, BUTTON_TWO));
        assertEquals(138 * 60 * 60 * 24, col.getSched().nextIvl(c, BUTTON_THREE));
        col.getSched().answerCard(c, BUTTON_TWO);
        assertEquals(138, c.getIvl());
        assertEquals(138, c.getODue());
        // fail
        col.getSched().answerCard(c, BUTTON_ONE);
        assertEquals(60, col.getSched().nextIvl(c, BUTTON_ONE));
        assertEquals(600, col.getSched().nextIvl(c, BUTTON_TWO));
        assertEquals(SECONDS_PER_DAY, col.getSched().nextIvl(c, BUTTON_THREE));
        // delete the deck, returning the card mid-study
        col.getDecks().rem(col.getDecks().selected());
        assertEquals(1, col.getSched().deckDueTree().size());
        c.load();
        assertEquals(1, c.getIvl());
        assertEquals(col.getSched().getToday() + 1, c.getDue());
        // make it due
        col.reset();
        assertEquals(new Counts(0, 0, 0), col.getSched().counts());
        c.setDue(-5);
        c.setIvl(100);
        c.flush();
        col.reset();
        assertEquals(new Counts(0, 0, 1), col.getSched().counts());
        // cram again
        did = addDynamicDeck("Cram");
        col.getSched().rebuildDyn(did);
        col.reset();
        assertEquals(new Counts(0, 0, 1), col.getSched().counts());
        c.load();
        assertEquals(4, col.getSched().answerButtons(c));
        // add a sibling so we can test minSpace, etc
        Card c2 = c.clone();
        c2.setId(0);
        c2.setOrd(1);
        c2.setDue(325);
        c2.flush();
        // should be able to answer it
        c = getCard();
        col.getSched().answerCard(c, BUTTON_FOUR);
        // it should have been moved back to the original deck
        assertEquals(1, c.getDid());
    }


    @Test
    public void test_cram_rem() throws Exception {
        Collection col = getColV1();
        Note note = col.newNote();
        note.setItem("Front", "one");
        col.addNote(note);
        long oldDue = note.cards().get(0).getDue();
        long did = addDynamicDeck("Cram");
        col.getSched().rebuildDyn(did);
        col.reset();
        Card c = getCard();
        col.getSched().answerCard(c, BUTTON_TWO);
        // answering the card will put it in the learning queue
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        assertEquals(CARD_TYPE_LRN, c.getType());
        assertNotEquals(c.getDue(), oldDue);
        // if we terminate cramming prematurely it should be set back to new
        col.getSched().emptyDyn(did);
        c.load();
        assertEquals(QUEUE_TYPE_NEW, c.getQueue());
        assertEquals(CARD_TYPE_NEW, c.getType());
        assertEquals(oldDue, c.getDue());
    }


    @Test
    public void test_cram_resched() throws Exception {
        // add card
        Collection col = getColV1();
        Note note = col.newNote();
        note.setItem("Front", "one");
        col.addNote(note);
        // cram deck
        long did = addDynamicDeck("Cram");
        Deck cram = col.getDecks().get(did);
        cram.put("resched", false);
        col.getDecks().save(cram);
        col.getSched().rebuildDyn(did);
        col.reset();
        // graduate should return it to new
        Card c = getCard();

        assertEquals(60, col.getSched().nextIvl(c, BUTTON_ONE));
        assertEquals(600, col.getSched().nextIvl(c, BUTTON_TWO));
        assertEquals(0, col.getSched().nextIvl(c, BUTTON_THREE));
        assertEquals("(end)", col.getSched().nextIvlStr(getTargetContext(), c, BUTTON_THREE));
        col.getSched().answerCard(c, BUTTON_THREE);
        assertEquals(CARD_TYPE_NEW, c.getType());
        assertEquals(QUEUE_TYPE_NEW, c.getQueue());
        // undue reviews should also be unaffected
        c.setIvl(100);
        c.setQueue(QUEUE_TYPE_REV);
        c.setType(CARD_TYPE_REV);
        c.setDue(col.getSched().getToday() + 25);
        c.setFactor(STARTING_FACTOR);
        c.flush();
        Card cardcopy = c.clone();
        col.getSched().rebuildDyn(did);
        col.reset();
        c = getCard();
        assertEquals(600, col.getSched().nextIvl(c, BUTTON_ONE));
        assertEquals(0, col.getSched().nextIvl(c, BUTTON_TWO));
        assertEquals(0, col.getSched().nextIvl(c, BUTTON_THREE));
        col.getSched().answerCard(c, BUTTON_TWO);
        assertEquals(100, c.getIvl());
        assertEquals(col.getSched().getToday() + 25, c.getDue());
        // check failure too
        c = cardcopy;
        c.flush();
        col.getSched().rebuildDyn(did);
        col.reset();
        c = getCard();
        col.getSched().answerCard(c, BUTTON_ONE);
        col.getSched().emptyDyn(did);
        c.load();
        assertEquals(100, c.getIvl());
        assertEquals(col.getSched().getToday() + 25, c.getDue());
        // fail+grad early
        c = cardcopy;
        c.flush();
        col.getSched().rebuildDyn(did);
        col.reset();
        c = getCard();
        col.getSched().answerCard(c, BUTTON_ONE);
        col.getSched().answerCard(c, BUTTON_THREE);
        col.getSched().emptyDyn(did);
        c.load();
        assertEquals(100, c.getIvl());
        assertEquals(col.getSched().getToday() + 25, c.getDue());
        // due cards - pass
        c = cardcopy;
        c.setDue(-25);
        c.flush();
        col.getSched().rebuildDyn(did);
        col.reset();
        c = getCard();
        col.getSched().answerCard(c, BUTTON_THREE);
        col.getSched().emptyDyn(did);
        c.load();
        assertEquals(100, c.getIvl());
        assertEquals(-25, c.getDue());
        // fail
        c = cardcopy;
        c.setDue(-25);
        c.flush();
        col.getSched().rebuildDyn(did);
        col.reset();
        c = getCard();
        col.getSched().answerCard(c, BUTTON_ONE);
        col.getSched().emptyDyn(did);
        c.load();
        assertEquals(100, c.getIvl());
        assertEquals(-25, c.getDue());
        // fail with normal grad
        c = cardcopy;
        c.setDue(-25);
        c.flush();
        col.getSched().rebuildDyn(did);
        col.reset();
        c = getCard();
        col.getSched().answerCard(c, BUTTON_ONE);
        col.getSched().answerCard(c, BUTTON_THREE);
        c.load();
        assertEquals(100, c.getIvl());
        assertEquals(-25, c.getDue());
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
    public void test_ordcycleV1() throws Exception {
        Collection col = getColV1();
        // add two more templates and set second active
        Model m = col.getModels().current();
        ModelManager mm = col.getModels();
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
        Note note = col.newNote();
        note.setItem("Front", "1");
        note.setItem("Back", "1");
        col.addNote(note);
        assertEquals(3, col.cardCount());
        col.reset();
        // ordinals should arrive in order
        AbstractSched sched = col.getSched();
        Card c = sched.getCard();
        sched.answerCard(c, sched.answerButtons(c) - 1); // not upstream. But we are not expecting multiple getCard without review
        waitForAsyncTasksToComplete();
        assertEquals(0, c.getOrd());
        c = sched.getCard();
        sched.answerCard(c, sched.answerButtons(c) - 1); // not upstream. But we are not expecting multiple getCard without review
        waitForAsyncTasksToComplete();
        assertEquals(1, c.getOrd());
        c = sched.getCard();
        sched.answerCard(c, sched.answerButtons(c) - 1); // not upstream. But we are not expecting multiple getCard without review
        assertEquals(2, c.getOrd());
    }


    @Test
    public void test_counts_idxV1() throws Exception {
        Collection col = getColV1();
        Note note = col.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        col.addNote(note);
        col.reset();
        assertEquals(new Counts(1, 0, 0), col.getSched().counts());
        Card c = getCard();
        // counter's been decremented but idx indicates 1
        assertEquals(new Counts(0, 0, 0), col.getSched().counts());
        assertEquals(NEW, col.getSched().countIdx(c));
        // answer to move to learn queue
        col.getSched().answerCard(c, BUTTON_ONE);
        assertEquals(new Counts(0, 2, 0), col.getSched().counts());
        // fetching again will decrement the count
        c = getCard();
        assertEquals(new Counts(0, 0, 0), col.getSched().counts());
        assertEquals(LRN, col.getSched().countIdx(c));
        // answering should add it back again
        col.getSched().answerCard(c, BUTTON_ONE);
        assertEquals(new Counts(0, 2, 0), col.getSched().counts());
    }


    @Test
    public void test_repCountsV1() throws Exception {
        Collection col = getColV1();
        Note note = col.newNote();
        note.setItem("Front", "one");
        col.addNote(note);
        col.reset();
        // lrnReps should be accurate on pass/fail
        assertEquals(new Counts(1, 0, 0), col.getSched().counts());
        col.getSched().answerCard(getCard(), BUTTON_ONE);
        assertEquals(new Counts(0, 2, 0), col.getSched().counts());
        col.getSched().answerCard(getCard(), BUTTON_ONE);
        assertEquals(new Counts(0, 2, 0), col.getSched().counts());
        col.getSched().answerCard(getCard(), BUTTON_TWO);
        assertEquals(new Counts(0, 1, 0), col.getSched().counts());
        col.getSched().answerCard(getCard(), BUTTON_ONE);
        assertEquals(new Counts(0, 2, 0), col.getSched().counts());
        col.getSched().answerCard(getCard(), BUTTON_TWO);
        assertEquals(new Counts(0, 1, 0), col.getSched().counts());
        col.getSched().answerCard(getCard(), BUTTON_TWO);
        assertEquals(new Counts(0, 0, 0), col.getSched().counts());
        note = col.newNote();
        note.setItem("Front", "two");
        col.addNote(note);
        col.reset();
        // initial pass should be correct too
        col.getSched().answerCard(getCard(), BUTTON_TWO);
        assertEquals(new Counts(0, 1, 0), col.getSched().counts());
        col.getSched().answerCard(getCard(), BUTTON_ONE);
        assertEquals(new Counts(0, 2, 0), col.getSched().counts());
        col.getSched().answerCard(getCard(), BUTTON_THREE);
        assertEquals(new Counts(0, 0, 0), col.getSched().counts());
        // immediate graduate should work
        note = col.newNote();
        note.setItem("Front", "three");
        col.addNote(note);
        col.reset();
        col.getSched().answerCard(getCard(), BUTTON_THREE);
        assertEquals(new Counts(0, 0, 0), col.getSched().counts());
        // and failing a review should too
        note = col.newNote();
        note.setItem("Front", "three");
        col.addNote(note);
        Card c = note.cards().get(0);
        c.setType(CARD_TYPE_REV);
        c.setQueue(QUEUE_TYPE_REV);
        c.setDue(col.getSched().getToday());
        c.flush();
        col.reset();
        assertEquals(new Counts(0, 0, 1), col.getSched().counts());
        col.getSched().answerCard(getCard(), BUTTON_ONE);
        assertEquals(new Counts(0, 1, 0), col.getSched().counts());
    }


    @Test
    public void test_timingV1() throws Exception {
        Collection col = getColV1();
        // add a few review cards, due today
        for (int i = 0; i < 5; i++) {
            Note note = col.newNote();
            note.setItem("Front", "num" + i);
            col.addNote(note);
            Card c = note.cards().get(0);
            c.setType(CARD_TYPE_REV);
            c.setQueue(QUEUE_TYPE_REV);
            c.setDue(0);
            c.flush();
        }
        // fail the first one
        col.reset();
        Card c = getCard();
        // set a a fail delay of 4 seconds
        DeckConfig conf = col.getSched()._cardConf(c);
        conf.getJSONObject("lapse").getJSONArray("delays").put(0, 1 / 15.0);
        col.getDecks().save(conf);
        col.getSched().answerCard(c, BUTTON_ONE);
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
        Collection col = getColV1();
        // add a note
        Note note = col.newNote();
        note.setItem("Front", "one");
        col.addNote(note);
        col.reset();
        // test collapsing
        Card c = getCard();
        col.getSched().answerCard(c, BUTTON_ONE);
        c = getCard();
        col.getSched().answerCard(c, BUTTON_THREE);
        assertNull(getCard());
    }


    @Test
    public void test_deckDueV1() throws Exception {
        Collection col = getColV1();
        // add a note with default deck
        Note note = col.newNote();
        note.setItem("Front", "one");
        col.addNote(note);
        // and one that's a child
        note = col.newNote();
        note.setItem("Front", "two");
        long default1 = addDeck("Default::1");
        note.model().put("did", default1);
        col.addNote(note);
        // make it a review card
        Card c = note.cards().get(0);
        c.setQueue(QUEUE_TYPE_REV);
        c.setDue(0);
        c.flush();
        // add one more with a new deck
        note = col.newNote();
        note.setItem("Front", "two");
        JSONObject foobar = note.model().put("did", addDeck("foo::bar"));
        col.addNote(note);
        // and one that's a sibling
        note = col.newNote();
        note.setItem("Front", "three");
        JSONObject foobaz = note.model().put("did", addDeck("foo::baz"));
        col.addNote(note);
        col.reset();
        assertEquals(5, col.getDecks().allSortedNames().size());
        DeckDueTreeNode tree = col.getSched().deckDueTree().get(0);
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
        col.getSched().deckDueTree();
    }


    @Test
    public void test_deckFlowV1() throws Exception {
        Collection col = getColV1();
        // add a note with default deck
        Note note = col.newNote();
        note.setItem("Front", "one");
        col.addNote(note);
        // and one that's a child
        note = col.newNote();
        note.setItem("Front", "two");
        JSONObject default1 = note.model().put("did", addDeck("Default::2"));
        col.addNote(note);
        // and another that's higher up
        note = col.newNote();
        note.setItem("Front", "three");
        default1 = note.model().put("did", addDeck("Default::1"));
        col.addNote(note);
        // should get top level one first, then ::1, then ::2
        col.reset();
        assertEquals(new Counts(3, 0, 0), col.getSched().counts());
        for (String i : new String[] {"one", "three", "two"}) {
            Card c = getCard();
            assertEquals(c.note().getItem("Front"), i);
            col.getSched().answerCard(c, BUTTON_TWO);
        }
    }


    @Test
    public void test_reorderV1() throws Exception {
        Collection col = getColV1();
        // add a note with default deck
        Note note = col.newNote();
        note.setItem("Front", "one");
        col.addNote(note);
        Note note2 = col.newNote();
        note2.setItem("Front", "two");
        col.addNote(note2);
        assertEquals(2, note2.cards().get(0).getDue());
        boolean found = false;
        // 50/50 chance of being reordered
        for (int i = 0; i < 20; i++) {
            col.getSched().randomizeCards(1);
            if (note.cards().get(0).getDue() != note.getId()) {
                found = true;
                break;
            }
        }
        assertTrue(found);
        col.getSched().orderCards(1);
        assertEquals(1, note.cards().get(0).getDue());
        // shifting
        Note note3 = col.newNote();
        note3.setItem("Front", "three");
        col.addNote(note3);
        Note note4 = col.newNote();
        note4.setItem("Front", "four");
        col.addNote(note4);
        assertEquals(1, note.cards().get(0).getDue());
        assertEquals(2, note2.cards().get(0).getDue());
        assertEquals(3, note3.cards().get(0).getDue());
        assertEquals(4, note4.cards().get(0).getDue());
        /* todo sortCard
           col.getSched().sortCards(new long [] {note3.cards().get(0).getId(), note4.cards().get(0).getId()}, start=1, shift=true);
           assertEquals(3, note.cards().get(0).getDue());
           assertEquals(4, note2.cards().get(0).getDue());
           assertEquals(1, note3.cards().get(0).getDue());
           assertEquals(2, note4.cards().get(0).getDue());
        */
    }


    @Test
    public void test_forgetV1() throws Exception {
        Collection col = getColV1();
        Note note = col.newNote();
        note.setItem("Front", "one");
        col.addNote(note);
        Card c = note.cards().get(0);
        c.setQueue(QUEUE_TYPE_REV);
        c.setType(CARD_TYPE_REV);
        c.setIvl(100);
        c.setDue(0);
        c.flush();
        col.reset();
        assertEquals(new Counts(0, 0, 1), col.getSched().counts());
        col.getSched().forgetCards(Collections.singletonList(c.getId()));
        col.reset();
        assertEquals(new Counts(1, 0, 0), col.getSched().counts());
    }


    @Test
    public void test_reschedV1() throws Exception {
        Collection col = getColV1();
        Note note = col.newNote();
        note.setItem("Front", "one");
        col.addNote(note);
        Card c = note.cards().get(0);
        col.getSched().reschedCards(Collections.singletonList(c.getId()), 0, 0);
        c.load();
        assertEquals(col.getSched().getToday(), c.getDue());
        assertEquals(1, c.getIvl());
        assertEquals(CARD_TYPE_REV, c.getType());
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        col.getSched().reschedCards(Collections.singletonList(c.getId()), 1, 1);
        c.load();
        assertEquals(col.getSched().getToday() + 1, c.getDue());
        assertEquals(+1, c.getIvl());
    }


    @Test
    public void test_norelearnV1() throws Exception {
        Collection col = getColV1();
        // add a note
        Note note = col.newNote();
        note.setItem("Front", "one");
        col.addNote(note);
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
        col.reset();
        col.getSched().answerCard(c, BUTTON_ONE);
        col.getSched()._cardConf(c).getJSONObject("lapse").put("delays", new JSONArray(new double[] {}));
        col.getSched().answerCard(c, BUTTON_ONE);
    }


    @Test
    public void test_failmultV1() throws Exception {
        Collection col = getColV1();
        Note note = col.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        col.addNote(note);
        Card c = note.cards().get(0);
        c.setType(CARD_TYPE_REV);
        c.setQueue(QUEUE_TYPE_REV);
        c.setIvl(100);
        c.setDue(col.getSched().getToday() - c.getIvl());
        c.setFactor(STARTING_FACTOR);
        c.setReps(3);
        c.setLapses(1);
        c.startTimer();
        c.flush();
        DeckConfig conf = col.getSched()._cardConf(c);
        conf.getJSONObject("lapse").put("mult", 0.5);
        col.getDecks().save(conf);
        c = getCard();
        col.getSched().answerCard(c, BUTTON_ONE);
        assertEquals(50, c.getIvl());
        col.getSched().answerCard(c, BUTTON_ONE);
        assertEquals(25, c.getIvl());
    }
}
