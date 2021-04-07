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

import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.backend.exception.BackendNotSupportedException;
import com.ichi2.testutils.MockTime;
import com.ichi2.testutils.libanki.FilteredDeckUtil;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.libanki.Consts.CARD_TYPE_LRN;
import static com.ichi2.libanki.Consts.CARD_TYPE_NEW;
import static com.ichi2.libanki.Consts.CARD_TYPE_RELEARNING;
import static com.ichi2.libanki.Consts.CARD_TYPE_REV;
import static com.ichi2.libanki.Consts.LEECH_SUSPEND;
import static com.ichi2.libanki.Consts.QUEUE_TYPE_DAY_LEARN_RELEARN;
import static com.ichi2.libanki.Consts.QUEUE_TYPE_LRN;
import static com.ichi2.libanki.Consts.QUEUE_TYPE_MANUALLY_BURIED;
import static com.ichi2.libanki.Consts.QUEUE_TYPE_NEW;
import static com.ichi2.libanki.Consts.QUEUE_TYPE_REV;
import static com.ichi2.libanki.Consts.QUEUE_TYPE_SIBLING_BURIED;
import static com.ichi2.libanki.Consts.STARTING_FACTOR;
import static com.ichi2.libanki.DecksTest.TEST_DECKS;
import static com.ichi2.libanki.sched.Counts.Queue.LRN;
import static com.ichi2.libanki.sched.Counts.Queue.NEW;
import static com.ichi2.libanki.stats.Stats.SECONDS_PER_DAY;
import static com.ichi2.testutils.AnkiAssert.assertDoesNotThrow;
import static com.ichi2.testutils.AnkiAssert.checkRevIvl;
import static com.ichi2.testutils.AnkiAssert.without_unicode_isolation;
import static com.ichi2.testutils.libanki.CollectionAssert.assertSuspended;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.platform.commons.util.CollectionUtils.getOnlyElement;

@RunWith(AndroidJUnit4.class)
public class SchedV2Test extends RobolectricTest {

    protected static List<DeckDueTreeNode> expectedTree(Collection mCol, boolean addRev) {
        AbstractSched sched = mCol.getSched();
        DeckDueTreeNode caz = new DeckDueTreeNode(mCol, "cmxieunwoogyxsctnjmv::abcdefgh::ZYXW", 1, 0, 0, 0);
        caz.setChildren(new ArrayList<>(), addRev);
        DeckDueTreeNode ca = new DeckDueTreeNode(mCol, "cmxieunwoogyxsctnjmv::abcdefgh", 1, 0, 0, 0);
        ca.setChildren(Collections.singletonList(caz), addRev);
        DeckDueTreeNode ci = new DeckDueTreeNode(mCol, "cmxieunwoogyxsctnjmv::INSBGDS", 1, 0, 0, 0);
        ci.setChildren(new ArrayList<>(), addRev);
        DeckDueTreeNode c = new DeckDueTreeNode(mCol, "cmxieunwoogyxsctnjmv", 1, 0, 0, 0);
        c.setChildren(Arrays.asList(ci, ca), addRev);
        DeckDueTreeNode defaul = new DeckDueTreeNode(mCol, "Default", 1, 0, 0, 0);
        defaul.setChildren(new ArrayList<>(), addRev);
        DeckDueTreeNode s = new DeckDueTreeNode(mCol, "scxipjiyozczaaczoawo", 1, 0, 0, 0);
        s.setChildren(new ArrayList<>(), addRev);
        return Arrays.asList(defaul, c, s); // Default is first, because start by an Upper case
    }


    /**
     * Reported by /u/CarelessSecretary9 on reddit:
     */
    @Test
    public void filteredDeckSchedulingOptionsRegressionTest() {
        mCol.setCrt(1587852900L);
        //30 minutes learn ahead. required as we have 20m delay
        mCol.getConf().put("collapseTime", 1800);

        long homeDeckId = addDeck("Poorretention");

        DeckConfig homeDeckConf = mDecks.confForDid(homeDeckId);
        JSONObject lapse = homeDeckConf.getJSONObject("lapse");

        lapse.put("minInt", 2);
        lapse.put("mult", 0.7d);
        lapse.put("delays", new JSONArray("[20]"));

        ensureLapseMatchesSppliedAnkiDesktopConfig(lapse);

        mCol.flush();

        long dynId = addDynamicDeck("Dyn");

        /*
        >>> pp(self.reviewer.card)
        {'data': '', 'did': 1587939535230, 'due': 0, 'factor': 1300, 'flags': 0, 'id': 1510928829863, 'ivl': 25,
        'lapses': 5, 'left': 1004, 'mod': 1587921512, 'nid': 1510928805161, 'odid': 1587920944107,
        'odue': 0, 'ord': 0, 'queue': 2, 'reps': 22, 'type': 2, 'usn': -1}

         */
        Note n = addNoteUsingBasicModel("Hello", "World");
        Card c = getOnlyElement(n.cards());
        c.setType(Consts.CARD_TYPE_REV);
        c.setQueue(Consts.QUEUE_TYPE_REV);
        c.setIvl(25);
        c.setDue(0);
        c.setLapses(5);
        c.setFactor(1300);
        c.setLeft(1004);
        c.setODid(homeDeckId);
        c.setDid(dynId);
        c.flush();

        SchedV2 v2 = new SchedV2(mCol);

        Card schedCard = v2.getCard();
        assertThat(schedCard, Matchers.notNullValue());
        v2.answerCard(schedCard, Consts.BUTTON_ONE);
        assertThat("The lapsed card should now be counted as lrn", v2.mLrnCount, is(1));
        Card after = v2.getCard();
        assertThat("A card should be returned ", after, Matchers.notNullValue());

        /* Data from Anki - pp(self.reviewer.card)
        {'data': '', 'did': 1587939535230, 'due': 1587941137, 'factor': 1300,
        'flags': 0, 'id': 1510928829863, 'ivl': 17, 'lapses': 6, 'left': 1001,
        'mod': 1587939720, 'nid': 1510928805161, 'odid': 1587920944107, 'odue': 0,
        'ord': 0, 'queue': 1, 'reps': 23, 'type': 3, 'usn': -1}
         */
        assertThat(after.getType(), is(Consts.CARD_TYPE_RELEARNING));
        assertThat(after.getQueue(), is(Consts.QUEUE_TYPE_LRN));
        assertThat(after.getLeft(), is(1001));
        assertThat("ivl is reduced by 70%", after.getIvl(), is(17));
        assertThat("One lapse is added", after.getLapses(), is(6));

        assertThat(v2.answerButtons(after), is(4));

        long one = v2.nextIvl(after, Consts.BUTTON_ONE);
        long two = v2.nextIvl(after, Consts.BUTTON_TWO);
        long three = v2.nextIvl(after, Consts.BUTTON_THREE);
        long four = v2.nextIvl(after, Consts.BUTTON_FOUR);

        assertThat("Again should pick the current step", one, is(1200L));      // 20 mins
        assertThat("Repeating single step - 20 minutes * 1.5", two, is(1800L));      // 30 mins
        assertThat("Good should take the reduced interval (25 * 0.7)", three, is(1468800L)); // 17 days
        assertThat("Easy should have a bonus day over good", four, is(1555200L));  // 18 days
    }


    private void ensureLapseMatchesSppliedAnkiDesktopConfig(JSONObject lapse) {
        assertThat(lapse.getInt("minInt"), is(2));
        assertThat(lapse.getDouble("mult"), is(0.7d));
        assertThat(lapse.getJSONArray("delays").length(), is(1));
        assertThat(lapse.getJSONArray("delays").getDouble(0), is(20.));

    }


    @Test
    public void ensureDeckTree() {
        for (String deckName : TEST_DECKS) {
            addDeck(deckName);
        }
        AbstractSched sched = mCol.getSched();
        List<DeckDueTreeNode> tree = sched.deckDueTree();
        Assert.assertEquals("Tree has not the expected structure", expectedTree(mCol, true), tree);
    }

    @Test
    public void emptyFilteredDeckSuspendHandling() throws ConfirmModSchemaException {
        getCol(2);

        long cardId = addNoteUsingBasicModel("Hello", "World").firstCard().getId();

        long filteredDid = FilteredDeckUtil.createFilteredDeck(mCol, "Filtered", "(is:new or is:due)");

        assertThat("No cards in filtered deck before rebuild", mCol.cardCount(filteredDid), is(0));

        mCol.getSched().rebuildDyn(filteredDid);

        assertThat("Card is in filtered deck after rebuild", mCol.cardCount(filteredDid), is(1));

        mCol.getSched().suspendCards(new long[] { cardId });

        assertSuspended(mCol, cardId);

        mCol.getSched().rebuildDyn(filteredDid);

        assertSuspended(mCol, cardId);

        assertThat("Card should be moved to the home deck", mCol.getCard(cardId).getDid(), is(1L));
        assertThat("Card should not be in a filtered deck", mCol.getCard(cardId).getODid(), is(0L));
    }



    @Test
    public void rebuildFilteredDeckSuspendHandling() throws ConfirmModSchemaException {
        getCol(2);

        long cardId = addNoteUsingBasicModel("Hello", "World").firstCard().getId();

        long filteredDid = FilteredDeckUtil.createFilteredDeck(mCol, "Filtered", "(is:new or is:due)");

        assertThat("No cards in filtered deck before rebuild", mCol.cardCount(filteredDid), is(0));

        mCol.getSched().rebuildDyn(filteredDid);

        assertThat("Card is in filtered deck after rebuild", mCol.cardCount(filteredDid), is(1));

        mCol.getSched().suspendCards(new long[] { cardId });

        assertSuspended(mCol, cardId);

        mCol.getSched().emptyDyn(filteredDid);

        assertSuspended(mCol, cardId);

        assertThat("Card should be moved to the home deck", mCol.getCard(cardId).getDid(), is(1L));
        assertThat("Card should not be in a filtered deck", mCol.getCard(cardId).getODid(), is(0L));
    }

    @Test
    public void handlesSmallSteps() throws ConfirmModSchemaException {
        // a delay of 0 crashed the app (step of 0.01).
        getCol(2);

        addNoteUsingBasicModel("Hello", "World");

        mDecks.allConf().get(0).getJSONObject("new").put("delays", new JSONArray(Arrays.asList(0.01, 10)));

        Card c = mCol.getSched().getCard();

        assertThat(c, notNullValue());

        mCol.getSched().answerCard(c, 1);
    }

    @Test
    public void newTimezoneHandling() throws ConfirmModSchemaException, BackendNotSupportedException {
        // #5805
        assertThat("localOffset should not be set if using V1 Scheduler", mCol.getConf().has("localOffset"), is(false));

        assertThat("Sync ver should be updated if we have a valid Rust collection", Consts.SYNC_VER, is(10));

        getCol(2);

        assertThat("localOffset should be set if using V2 Scheduler", mCol.getConf().has("localOffset"), is(true));

        SchedV2 sched = (SchedV2) mCol.getSched();

        assertThat("new timezone should not be enabled by default", sched._new_timezone_enabled(), is(false));

        sched.set_creation_offset();

        assertThat("new timezone should now be enabled", sched._new_timezone_enabled(), is(true));

        // a second call should be fine
        sched.set_creation_offset();

        // we can obtain the offset from "crt" without an issue - do not test the return as it depends on the local timezone
        sched._current_timezone_offset();

        sched.clear_creation_offset();

        assertThat("new timezone should be disabled after clear", sched._new_timezone_enabled(), is(false));
    }


    /*****************
     ** autogenerated from https://github.com/ankitects/anki/blob/2c73dcb2e547c44d9e02c20a00f3c52419dc277b/pylib/tests/test_cards.py
     *****************/
    private final MockTime mTime = new MockTime(2020, 7, 4,11, 22, 19, 0, 10);

    @Test
    public void test_clock() throws Exception {
        getCol(2);
        if ((mCol.getSched().getDayCutoff() - mTime.intTime()) < 10 * 60) {
            throw new Exception("Unit tests will fail around the day rollover.");
        }
    }


    @Test
    public void test_basics() throws Exception {
        getCol(2);
        mCol.reset();
        assertNull(getCard());
    }


    @Test
    public void test_new_v2() throws Exception {
        getCol(2);
        mCol.reset();
        assertEquals(0, mCol.getSched().newCount());
        // add a note
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        mCol.addNote(note);
        mCol.reset();
        assertEquals(1, mCol.getSched().newCount());
        // fetch it
        Card c = getCard();
        assertNotNull(c);
        assertEquals(QUEUE_TYPE_NEW, c.getQueue());
        assertEquals(CARD_TYPE_NEW, c.getType());
        // if we answer it, it should become a learn card
        long t = mCol.getTime().intTime();
        mCol.getSched().answerCard(c, 1);
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
        //     mCol.getSched().answerCard(c, 2)
        // }
    }


    @Test
    public void test_newLimits_V2() throws Exception {
        getCol(2);
        // add some notes
        long deck2 = addDeck("Default::foo");
        for (int i = 0; i < 30; i++) {
            Note note = mCol.newNote();
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
        assertEquals(20, mCol.getSched().newCount());
        // first card we get comes from parent
        Card c = getCard();
        assertEquals(1, c.getDid());
        // limit the parent to 10 cards, meaning we get 10 in total
        DeckConfig conf1 = mDecks.confForDid(1);
        conf1.getJSONObject("new").put("perDay", 10);
        mDecks.save(conf1);
        mCol.reset();
        assertEquals(10, mCol.getSched().newCount());
        // if we limit child to 4, we should get 9
        DeckConfig conf2 = mDecks.confForDid(deck2);
        conf2.getJSONObject("new").put("perDay", 4);
        mDecks.save(conf2);
        mCol.reset();
        assertEquals(9, mCol.getSched().newCount());
    }


    @Test
    public void test_newBoxes_v2() throws Exception {
        getCol(2);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        mCol.reset();
        Card c = getCard();
        DeckConfig conf = mCol.getSched()._cardConf(c);
        conf.getJSONObject("new").put("delays", new JSONArray(new double[] {1, 2, 3, 4, 5}));
        mDecks.save(conf);
        mCol.getSched().answerCard(c, 2);
        // should handle gracefully
        conf.getJSONObject("new").put("delays", new JSONArray(new double[] {1}));
        mDecks.save(conf);
        mCol.getSched().answerCard(c, 2);
    }


    @Test
    public void test_learnV2() throws Exception {
        getCol(2);
        // add a note
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        mCol.addNote(note);
        // set as a learn card and rebuild queues
        mCol.getDb().execute("update cards set queue=0, type=0");
        mCol.reset();
        // sched.getCard should return it, since it's due in the past
        Card c = getCard();
        assertNotNull(c);
        DeckConfig conf = mCol.getSched()._cardConf(c);
        conf.getJSONObject("new").put("delays", new JSONArray(new double[] {0.5, 3, 10}));
        mDecks.save(conf);
        // fail it
        mCol.getSched().answerCard(c, 1);
        // it should have three reps left to graduation
        assertEquals(3, c.getLeft() % 1000);
        assertEquals(3, c.getLeft() / 1000);
        // it should be due in 30 seconds
        long t = Math.round(c.getDue() - mCol.getTime().intTime());
        assertThat(t, is(greaterThanOrEqualTo(25L)));
        assertThat(t, is(lessThanOrEqualTo(40L)));
        // pass it once
        mCol.getSched().answerCard(c, 3);
        // it should be due in 3 minutes
        long dueIn = c.getDue() - mCol.getTime().intTime();
        assertThat(dueIn, is(greaterThanOrEqualTo(178L)));
        assertThat(dueIn, is(lessThanOrEqualTo((long)(180 * 1.25))));
        assertEquals(2, c.getLeft() % 1000);
        assertEquals(2, c.getLeft() / 1000);
        // check log is accurate
        Cursor log = mCol.getDb().getDatabase().query("select * from revlog order by id desc");
        assertTrue(log.moveToFirst());
        assertEquals(3, log.getInt(3));
        assertEquals(-180, log.getInt(4));
        assertEquals(-30, log.getInt(5));
        // pass again
        mCol.getSched().answerCard(c, 3);
        // it should be due in 10 minutes
        dueIn = c.getDue() - mCol.getTime().intTime();
        assertThat(dueIn, is(greaterThanOrEqualTo(599L)));
        assertThat(dueIn, is(lessThanOrEqualTo((long)(600 * 1.25))));
        assertEquals(1, c.getLeft() % 1000);
        assertEquals(1, c.getLeft() / 1000);
        // the next pass should graduate the card
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        assertEquals(CARD_TYPE_LRN, c.getType());
        mCol.getSched().answerCard(c, 3);
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        assertEquals(CARD_TYPE_REV, c.getType());
        // should be due tomorrow, with an interval of 1
        assertEquals(mCol.getSched().getToday() + 1, c.getDue());
        assertEquals(1, c.getIvl());
        // or normal removal
        c.setType(CARD_TYPE_NEW);
        c.setQueue(QUEUE_TYPE_LRN);
        mCol.getSched().answerCard(c, 4);
        assertEquals(CARD_TYPE_REV, c.getType());
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        assertTrue(checkRevIvl(mCol, c, 4));
        // revlog should have been updated each time
        assertEquals(5, mCol.getDb().queryScalar("select count() from revlog where type = 0"));
    }


    @Test
    public void test_relearn() throws Exception {
        getCol(2);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        Card c = note.cards().get(0);
        c.setIvl(100);
        c.setDue(mCol.getSched().getToday());
        c.setQueue(CARD_TYPE_REV);
        c.setType(QUEUE_TYPE_REV);
        c.flush();

        // fail the card
        mCol.reset();
        c = getCard();
        mCol.getSched().answerCard(c, 1);
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        assertEquals(CARD_TYPE_RELEARNING, c.getType());
        assertEquals(1, c.getIvl());

        // immediately graduate it
        mCol.getSched().answerCard(c, 4);
        assertEquals(CARD_TYPE_REV, c.getType());
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        assertEquals(2, c.getIvl());
        assertEquals(mCol.getSched().getToday() + c.getIvl(), c.getDue());
    }


    @Test
    public void test_relearn_no_steps() throws Exception {
        getCol(2);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        Card c = note.cards().get(0);
        c.setIvl(100);
        c.setDue(mCol.getSched().getToday());
        c.setQueue(CARD_TYPE_REV);
        c.setType(QUEUE_TYPE_REV);
        c.flush();

        DeckConfig conf = mDecks.confForDid(1);
        conf.getJSONObject("lapse").put("delays", new JSONArray(new double[] {}));
        mDecks.save(conf);

        // fail the card
        mCol.reset();
        c = getCard();
        mCol.getSched().answerCard(c, 1);
        assertEquals(CARD_TYPE_REV, c.getType());
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
    }


    @Test
    public void test_learn_collapsedV2() throws Exception {
        getCol(2);
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
        mCol.getSched().answerCard(c, 3);
        // get the other card
        c = getCard();
        assertTrue(c.q().endsWith("2"));
        // fail it so it's due in 1 minute
        mCol.getSched().answerCard(c, 1);
        // we shouldn't get the same card again
        c = getCard();
        assertFalse(c.q().endsWith("2"));
    }


    @Test
    public void test_learn_dayV2() throws Exception {
        getCol(2);
        // add a note
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        mCol.reset();
        Card c = getCard();
        DeckConfig conf = mCol.getSched()._cardConf(c);
        conf.getJSONObject("new").put("delays", new JSONArray(new double[] {1, 10, 1440, 2880}));
        mDecks.save(conf);
        // pass it
        mCol.getSched().answerCard(c, 3);
        // two reps to graduate, 1 more today
        assertEquals(3, c.getLeft() % 1000);
        assertEquals(1, c.getLeft() / 1000);
        assertEquals(new Counts(0, 1, 0), mCol.getSched().counts());
        c = getCard();

        assertEquals(SECONDS_PER_DAY, mCol.getSched().nextIvl(c, 3));
        // answering it will place it in queue 3
        mCol.getSched().answerCard(c, 3);
        assertEquals(mCol.getSched().getToday() + 1, c.getDue());
        assertEquals(QUEUE_TYPE_DAY_LEARN_RELEARN, c.getQueue());
        assertNull(getCard());
        // for testing, move it back a day
        c.setDue(c.getDue() - 1);
        c.flush();
        mCol.reset();
        assertEquals(new Counts(0, 1, 0), mCol.getSched().counts());
        c = getCard();
        // nextIvl should work
        assertEquals(SECONDS_PER_DAY * 2, mCol.getSched().nextIvl(c, 3));
        // if we fail it, it should be back in the correct queue
        mCol.getSched().answerCard(c, 1);
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        mCol.undo();
        mCol.reset();
        c = getCard();
        mCol.getSched().answerCard(c, 3);
        // simulate the passing of another two days
        c.setDue(c.getDue() - 2);
        c.flush();
        mCol.reset();
        // the last pass should graduate it into a review card
        assertEquals(SECONDS_PER_DAY, mCol.getSched().nextIvl(c, 3));
        mCol.getSched().answerCard(c, 3);
        assertEquals(CARD_TYPE_REV, c.getType());
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        // if the lapse step is tomorrow, failing it should handle the counts
        // correctly
        c.setDue(0);
        c.flush();
        mCol.reset();
        assertEquals(new Counts(0, 0, 1), mCol.getSched().counts());
        conf = mCol.getSched()._cardConf(c);
        conf.getJSONObject("lapse").put("delays", new JSONArray(new double[] {1440}));
        mDecks.save(conf);
        c = getCard();
        mCol.getSched().answerCard(c, 1);
        assertEquals(QUEUE_TYPE_DAY_LEARN_RELEARN, c.getQueue());
        assertEquals(new Counts(0, 0, 0), mCol.getSched().counts());
    }


    @Test
    public void test_reviewsV2() throws Exception {
        getCol(2);
        // add a note
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        mCol.addNote(note);
        // set the card up as a review card, due 8 days ago
        Card c = note.cards().get(0);
        c.setType(CARD_TYPE_REV);
        c.setQueue(QUEUE_TYPE_REV);
        c.setDue(mCol.getSched().getToday() - 8);
        c.setFactor(STARTING_FACTOR);
        c.setReps(3);
        c.setLapses(1);
        c.setIvl(100);
        c.startTimer();
        c.flush();
        // save it for later use as well
        Card cardcopy = c.clone();
        // try with an ease of 2
        ////////////////////////////////////////////////////////////////////////////////////////////////////
        c = cardcopy.clone();
        c.flush();
        mCol.reset();
        mCol.getSched().answerCard(c, 2);
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        // the new interval should be (100) * 1.2 = 120
        assertTrue(checkRevIvl(mCol, c, 120));
        assertEquals(mCol.getSched().getToday() + c.getIvl(), c.getDue());
        // factor should have been decremented
        assertEquals(2350, c.getFactor());
        // check counters
        assertEquals(1, c.getLapses());
        assertEquals(4, c.getReps());
        // ease 3
        ////////////////////////////////////////////////////////////////////////////////////////////////////
        c = cardcopy.clone();
        c.flush();
        mCol.getSched().answerCard(c, 3);
        // the new interval should be (100 + 8/2) * 2.5 = 260
        assertTrue(checkRevIvl(mCol, c, 260));
        assertEquals(mCol.getSched().getToday() + c.getIvl(), c.getDue());
        // factor should have been left alone
        assertEquals(STARTING_FACTOR, c.getFactor());
        // ease 4
        ////////////////////////////////////////////////////////////////////////////////////////////////////
        c = cardcopy.clone();
        c.flush();
        mCol.getSched().answerCard(c, 4);
        // the new interval should be (100 + 8) * 2.5 * 1.3 = 351
        assertTrue(checkRevIvl(mCol, c, 351));
        assertEquals(mCol.getSched().getToday() + c.getIvl(), c.getDue());
        // factor should have been increased
        assertEquals(2650, c.getFactor());
        // leech handling
        ////////////////////////////////////////////////////////////////////////////////////////////////////
        DeckConfig conf = mDecks.getConf(1);
        conf.getJSONObject("lapse").put("leechAction", LEECH_SUSPEND);
        mDecks.save(conf);
        c = cardcopy.clone();
        c.setLapses(7);
        c.flush();
        /* todo hook
        // steup hook
        hooked = new [] {};

        def onLeech(card):
        hooked.append(1);

        hooks.card_did_leech.append(onLeech);
        mCol.getSched().answerCard(c, 1);
        assertTrue(hooked);
        assertEquals(QUEUE_TYPE_SUSPENDED, c.getQueue());
        c.load();
        assertEquals(QUEUE_TYPE_SUSPENDED, c.getQueue());
        */
    }


    @Test
    public void test_review_limits() throws Exception {
        getCol(2);

        Deck parent = mDecks.get(addDeck("parent"));
        Deck child = mDecks.get(addDeck("parent::child"));

        DeckConfig pconf = mDecks.getConf(mDecks.confId("parentConf"));
        DeckConfig cconf = mDecks.getConf(mDecks.confId("childConf"));

        pconf.getJSONObject("rev").put("perDay", 5);
        mDecks.updateConf(pconf);
        mDecks.setConf(parent, pconf.getLong("id"));
        cconf.getJSONObject("rev").put("perDay", 10);
        mDecks.updateConf(cconf);
        mDecks.setConf(child, cconf.getLong("id"));

        Model m = mModels.current();
        m.put("did", child.getLong("id"));
        mModels.save(m, false);

        // add some cards
        for (int i = 0; i < 20; i++) {
            Note note = mCol.newNote();
            note.setItem("Front", "one");
            note.setItem("Back", "two");
            mCol.addNote(note);

            // make them reviews
            Card c = note.cards().get(0);
            c.setQueue(CARD_TYPE_REV);
            c.setType(QUEUE_TYPE_REV);
            c.setDue(0);
            c.flush();
        }

        // position 0 is default deck. Different from upstream
        DeckDueTreeNode tree = mCol.getSched().deckDueTree().get(1);
        // (('parent', 1514457677462, 5, 0, 0, (('child', 1514457677463, 5, 0, 0, ()),)))
        assertEquals("parent", tree.getFullDeckName());
        assertEquals(5, tree.getRevCount());  // paren, tree.review_count)t
        assertEquals(10, tree.getChildren().get(0).getRevCount());

        // .counts() should match
        mDecks.select(child.getLong("id"));
        mCol.reset();
        assertEquals(new Counts(0, 0, 10), mCol.getSched().counts());

        // answering a card in the child should decrement parent count
        Card c = getCard();
        mCol.getSched().answerCard(c, 3);
        assertEquals(new Counts(0, 0, 9), mCol.getSched().counts());

        tree = mCol.getSched().deckDueTree().get(1);
        assertEquals(4, tree.getRevCount());
        assertEquals(9, tree.getChildren().get(0).getRevCount());
    }


    @Test
    public void test_button_spacingV2() throws Exception {
        getCol(2);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        // 1 day ivl review card due now
        Card c = note.cards().get(0);
        c.setType(CARD_TYPE_REV);
        c.setQueue(QUEUE_TYPE_REV);
        c.setDue(mCol.getSched().getToday());
        c.setReps(1);
        c.setIvl(1);
        c.startTimer();
        c.flush();
        mCol.reset();
        // Upstream, there is no space in 2d
        assertEquals("2 d", without_unicode_isolation(mCol.getSched().nextIvlStr(getTargetContext(), c, 2)));
        assertEquals("3 d", without_unicode_isolation(mCol.getSched().nextIvlStr(getTargetContext(), c, 3)));
        assertEquals("4 d", without_unicode_isolation(mCol.getSched().nextIvlStr(getTargetContext(), c, 4)));

        // if hard factor is <= 1, then hard may not increase
        DeckConfig conf = mDecks.confForDid(1);
        conf.getJSONObject("rev").put("hardFactor", 1);
        mDecks.save(conf);
        assertEquals("1 d", without_unicode_isolation(mCol.getSched().nextIvlStr(getTargetContext(), c, 2)));
    }


    @Test
    @Ignore("Disabled upstream")
    public void test_overdue_lapseV2() {
        // disabled in commit 3069729776990980f34c25be66410e947e9d51a2
        /* Upstream does not execute it
           getCol(2)  // pylint: disable=unreachable
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
           mCol.getSched().reset();
           assertEquals(new Counts(0, 2, 0), mCol.getSched().counts());
           c = getCard();
           mCol.getSched().answerCard(c, 3);
           // it should be due tomorrow
           assertEquals(mCol.getSched().getToday()+ 1, c.getDue());
           // revert to before
           /* todo: rollback
           mCol.rollback();
           // with the default settings, the overdue card should be removed from the
           // learning queue
           mCol.getSched().reset();
           assertEquals(new Counts(0, 0, 1), mCol.getSched().counts());
        */

    }


    @Test
    public void test_finishedV2() throws Exception {
        getCol(2);
        // nothing due
        assertThat(mCol.getSched().finishedMsg(getTargetContext()).toString(), containsString("Congratulations"));
        assertThat(mCol.getSched().finishedMsg(getTargetContext()).toString(), not(containsString("limit")));
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        mCol.addNote(note);
        // have a new card
        assertThat(mCol.getSched().finishedMsg(getTargetContext()).toString(), containsString("new cards available"));
        // turn it into a review
        mCol.reset();
        Card c = note.cards().get(0);
        c.startTimer();
        mCol.getSched().answerCard(c, 3);
        // nothing should be due tomorrow, as it's due in a week
        assertThat(mCol.getSched().finishedMsg(getTargetContext()).toString(), containsString("Congratulations"));
        assertThat(mCol.getSched().finishedMsg(getTargetContext()).toString(), not(containsString("limit")));
    }


    @Test
    public void test_nextIvlV2() throws Exception {
        getCol(2);
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

        assertEquals(30, mCol.getSched().nextIvl(c, 1));
        assertEquals((30 + 180) / 2, mCol.getSched().nextIvl(c, 2));
        assertEquals(180, mCol.getSched().nextIvl(c, 3));
        assertEquals(4 * SECONDS_PER_DAY, mCol.getSched().nextIvl(c, 4));
        mCol.getSched().answerCard(c, 1);
        // cards in learning
        ////////////////////////////////////////////////////////////////////////////////////////////////////
        assertEquals(30, mCol.getSched().nextIvl(c, 1));
        assertEquals((30 + 180) / 2, mCol.getSched().nextIvl(c, 2));
        assertEquals(180, mCol.getSched().nextIvl(c, 3));
        assertEquals(4 * SECONDS_PER_DAY, mCol.getSched().nextIvl(c, 4));
        mCol.getSched().answerCard(c, 3);
        assertEquals(30, mCol.getSched().nextIvl(c, 1));
        assertEquals((180 + 600) / 2, mCol.getSched().nextIvl(c, 2));
        assertEquals(600, mCol.getSched().nextIvl(c, 3));
        assertEquals(4 * SECONDS_PER_DAY, mCol.getSched().nextIvl(c, 4));
        mCol.getSched().answerCard(c, 3);
        // normal graduation is tomorrow
        assertEquals(SECONDS_PER_DAY, mCol.getSched().nextIvl(c, 3));
        assertEquals(4 * SECONDS_PER_DAY, mCol.getSched().nextIvl(c, 4));
        // lapsed cards
        ////////////////////////////////////////////////////////////////////////////////////////////////////
        c.setType(CARD_TYPE_REV);
        c.setIvl(100);
        c.setFactor(STARTING_FACTOR);
        assertEquals(60, mCol.getSched().nextIvl(c, 1));
        assertEquals(100 * SECONDS_PER_DAY, mCol.getSched().nextIvl(c, 3));
        assertEquals(101 * SECONDS_PER_DAY, mCol.getSched().nextIvl(c, 4));
        // review cards
        ////////////////////////////////////////////////////////////////////////////////////////////////////
        c.setQueue(QUEUE_TYPE_REV);
        c.setIvl(100);
        c.setFactor(STARTING_FACTOR);
        // failing it should put it at 60s
        assertEquals(60, mCol.getSched().nextIvl(c, 1));
        // or 1 day if relearn is false
        conf.getJSONObject("lapse").put("delays", new JSONArray(new double[] {}));
        mDecks.save(conf);
        assertEquals(SECONDS_PER_DAY, mCol.getSched().nextIvl(c, 1));
        // (* 100 1.2 SECONDS_PER_DAY)10368000.0
        assertEquals(10368000, mCol.getSched().nextIvl(c, 2));
        // (* 100 2.5 SECONDS_PER_DAY)21600000.0
        assertEquals(21600000, mCol.getSched().nextIvl(c, 3));
        // (* 100 2.5 1.3 SECONDS_PER_DAY)28080000.0
        assertEquals(28080000, mCol.getSched().nextIvl(c, 4));

        assertThat(without_unicode_isolation(mCol.getSched().nextIvlStr(getTargetContext(), c, 4)), is("10.8 mo"));
    }


    @Test
    public void test_bury() throws Exception {
        getCol(2);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        Card c = note.cards().get(0);
        note = mCol.newNote();
        note.setItem("Front", "two");
        mCol.addNote(note);
        Card c2 = note.cards().get(0);
        // burying
        mCol.getSched().buryCards(new long[] {c.getId()}, true);
        c.load();
        assertEquals(QUEUE_TYPE_MANUALLY_BURIED, c.getQueue());
        mCol.getSched().buryCards(new long[] {c2.getId()}, false);
        c2.load();
        assertEquals(QUEUE_TYPE_SIBLING_BURIED, c2.getQueue());

        mCol.reset();
        assertNull(getCard());

        mCol.getSched().unburyCardsForDeck(AbstractSched.UnburyType.MANUAL);
        c.load();
        assertEquals(QUEUE_TYPE_NEW, c.getQueue());
        c2.load();
        assertEquals(QUEUE_TYPE_SIBLING_BURIED, c2.getQueue());

        mCol.getSched().unburyCardsForDeck(AbstractSched.UnburyType.SIBLINGS);
        c2.load();
        assertEquals(QUEUE_TYPE_NEW, c2.getQueue());

        mCol.getSched().buryCards(new long[] {c.getId(), c2.getId()});
        mCol.getSched().unburyCardsForDeck(AbstractSched.UnburyType.ALL);

        mCol.reset();

        assertEquals(new Counts(2, 0, 0), mCol.getSched().counts());
    }


    @Test
    public void test_suspendv2() throws Exception {
        getCol(2);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        Card c = note.cards().get(0);
        // suspending
        mCol.reset();
        assertNotNull(getCard());
        mCol.getSched().suspendCards(new long[] {c.getId()});
        mCol.reset();
        assertNull(getCard());
        // unsuspending
        mCol.getSched().unsuspendCards(new long[] {c.getId()});
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
        mCol.getSched().answerCard(c, 1);
        assertThat(c.getDue(), is(greaterThanOrEqualTo(mCol.getTime().intTime())));
        long due = c.getDue();
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        assertEquals(CARD_TYPE_RELEARNING, c.getType());
        mCol.getSched().suspendCards(new long[] {c.getId()});
        mCol.getSched().unsuspendCards(new long[] {c.getId()});
        c.load();
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        assertEquals(CARD_TYPE_RELEARNING, c.getType());
        assertEquals(due, c.getDue());
        // should cope with cards in cram decks
        c.setDue(1);
        c.flush();
        addDynamicDeck("tmp");
        mCol.getSched().rebuildDyn();
        c.load();
        assertNotEquals(1, c.getDue());
        assertNotEquals(1, c.getDid());
        mCol.getSched().suspendCards(new long[] {c.getId()});
        c.load();
        assertNotEquals(1, c.getDue());
        assertNotEquals(1, c.getDid());
        assertEquals(1, c.getODue());
    }


    @Test
    public void test_filt_reviewing_early_normal() throws Exception {
        getCol(2);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        Card c = note.cards().get(0);
        c.setIvl(100);
        c.setQueue(CARD_TYPE_REV);
        c.setType(QUEUE_TYPE_REV);
        // due in 25 days, so it's been waiting 75 days
        c.setDue(mCol.getSched().getToday() + 25);
        c.setMod(1);
        c.setFactor(STARTING_FACTOR);
        c.startTimer();
        c.flush();
        mCol.reset();
        assertEquals(new Counts(0, 0, 0), mCol.getSched().counts());
        // create a dynamic deck and refresh it
        long did = addDynamicDeck("Cram");
        mCol.getSched().rebuildDyn(did);
        mCol.reset();
        // should appear as normal in the deck list
        /* todo sort
           assertEquals(1, sorted(mCol.getSched().deckDueTree().getChildren())[0].review_count);
        */
        // and should appear in the counts
        assertEquals(new Counts(0, 0, 1), mCol.getSched().counts());
        // grab it and check estimates
        c = getCard();
        assertEquals(4, mCol.getSched().answerButtons(c));
        assertEquals(600, mCol.getSched().nextIvl(c, 1));
        assertEquals(Math.round(75 * 1.2) * SECONDS_PER_DAY, mCol.getSched().nextIvl(c, 2));
        assertThat(mCol.getSched().nextIvl(c, 3), is((long)(75 * 2.5) * SECONDS_PER_DAY));
        assertThat(mCol.getSched().nextIvl(c, 4), is((long)(75 * 2.5 * 1.15) * SECONDS_PER_DAY));

        // answer 'good'
        mCol.getSched().answerCard(c, 3);
        checkRevIvl(mCol, c, 90);
        assertEquals(mCol.getSched().getToday() + c.getIvl(), c.getDue());
        assertEquals(0L, c.getODue());
        // should not be in learning
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        // should be logged as a cram rep
        assertEquals(3, mCol.getDb().queryLongScalar("select type from revlog order by id desc limit 1"));

        // due in 75 days, so it's been waiting 25 days
        c.setIvl(100);
        c.setDue(mCol.getSched().getToday() + 75);
        c.flush();
        mCol.getSched().rebuildDyn(did);
        mCol.reset();
        c = getCard();

        assertEquals(60 * SECONDS_PER_DAY, mCol.getSched().nextIvl(c, 2));
        assertEquals(100 * SECONDS_PER_DAY, mCol.getSched().nextIvl(c, 3));
        assertEquals(114 * SECONDS_PER_DAY, mCol.getSched().nextIvl(c, 4));
    }


    @Test
    public void test_filt_keep_lrn_state() throws Exception {
        getCol(2);

        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);

        // fail the card outside filtered deck
        Card c = getCard();
        DeckConfig conf = mCol.getSched()._cardConf(c);
        conf.getJSONObject("new").put("delays", new JSONArray(new double[] {1, 10, 61}));
        mDecks.save(conf);

        mCol.getSched().answerCard(c, 1);

        assertEquals(CARD_TYPE_LRN, c.getQueue());
        assertEquals(QUEUE_TYPE_LRN, c.getType());
        assertEquals(3003, c.getLeft());

        mCol.getSched().answerCard(c, 3);
        assertEquals(CARD_TYPE_LRN, c.getQueue());
        assertEquals(QUEUE_TYPE_LRN, c.getType());

        // create a dynamic deck and refresh it
        long did = addDynamicDeck("Cram");
        mCol.getSched().rebuildDyn(did);
        mCol.reset();

        // card should still be in learning state
        c.load();
        assertEquals(CARD_TYPE_LRN, c.getQueue());
        assertEquals(QUEUE_TYPE_LRN, c.getType());
        assertEquals(2002, c.getLeft());

        // should be able to advance learning steps
        mCol.getSched().answerCard(c, 3);
        // should be due at least an hour in the future
        assertThat(c.getDue() - mCol.getTime().intTime(), is(greaterThan(60 * 60L)));

        // emptying the deck preserves learning state
        mCol.getSched().emptyDyn(did);
        c.load();
        assertEquals(CARD_TYPE_LRN, c.getQueue());
        assertEquals(QUEUE_TYPE_LRN, c.getType());
        assertEquals(1001, c.getLeft());
        assertThat(c.getDue() - mCol.getTime().intTime(), is(greaterThan(60 * 60L)));
    }


    @Test
    public void test_preview() throws Exception {
        // add cards
        getCol(2);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        Card c = note.cards().get(0);
        Card orig = c.clone();
        Note note2 = mCol.newNote();
        note2.setItem("Front", "two");
        mCol.addNote(note2);
        // cram deck
        long did = addDynamicDeck("Cram");
        Deck cram = mDecks.get(did);
        cram.put("resched", false);
        mDecks.save(cram);
        mCol.getSched().rebuildDyn(did);
        mCol.reset();
        // grab the first card
        c = getCard();
        assertEquals(2, mCol.getSched().answerButtons(c));
        assertEquals(600, mCol.getSched().nextIvl(c, 1));
        assertEquals(0, mCol.getSched().nextIvl(c, 2));
        // failing it will push its due time back
        long due = c.getDue();
        mCol.getSched().answerCard(c, 1);
        assertNotEquals(c.getDue(), due);

        // the other card should come next
        Card c2 = getCard();
        assertNotEquals(c2.getId(), c.getId());

        // passing it will remove it
        mCol.getSched().answerCard(c2, 2);
        assertEquals(QUEUE_TYPE_NEW, c2.getQueue());
        assertEquals(0, c2.getReps());
        assertEquals(CARD_TYPE_NEW, c2.getType());

        // the other card should appear again
        c = getCard();
        assertEquals(orig.getId(), c.getId());

        // emptying the filtered deck should restore card
        mCol.getSched().emptyDyn(did);
        c.load();
        assertEquals(QUEUE_TYPE_NEW, c.getQueue());
        assertEquals(0, c.getReps());
        assertEquals(CARD_TYPE_NEW, c.getType());
    }


    @Test
    public void test_ordcycleV2() throws Exception {
        getCol(2);
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
        AbstractSched sched = mCol.getSched();
        Card c = sched.getCard();
        advanceRobolectricLooperWithSleep();
        sched.answerCard(c, sched.answerButtons(c) - 1); // not upstream. But we are not expecting multiple getCard without review
        assertEquals(0, c.getOrd());
        c = sched.getCard();
        advanceRobolectricLooperWithSleep();
        sched.answerCard(c, sched.answerButtons(c) - 1); // not upstream. But we are not expecting multiple getCard without review
        assertEquals(1, c.getOrd());
        c = sched.getCard();
        advanceRobolectricLooperWithSleep();
        sched.answerCard(c, sched.answerButtons(c) - 1); // not upstream. But we are not expecting multiple getCard without review
        advanceRobolectricLooperWithSleep();
        assertEquals(2, c.getOrd());
    }


    @Test
    public void test_counts_idxV2() throws Exception {
        getCol(2);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        mCol.addNote(note);
        mCol.reset();
        assertEquals(new Counts(1, 0, 0), mCol.getSched().counts());
        Card c = getCard();
        // counter's been decremented but idx indicates 1
        assertEquals(new Counts(0, 0, 0), mCol.getSched().counts());
        assertEquals(NEW, mCol.getSched().countIdx(c));
        // answer to move to learn queue
        mCol.getSched().answerCard(c, 1);
        assertEquals(new Counts(0, 1, 0), mCol.getSched().counts());
        // fetching again will decrement the count
        c = getCard();
        assertEquals(new Counts(0, 0, 0), mCol.getSched().counts());
        assertEquals(LRN, mCol.getSched().countIdx(c));
        // answering should add it back again
        mCol.getSched().answerCard(c, 1);
        assertEquals(new Counts(0, 1, 0), mCol.getSched().counts());
    }


    @Test
    public void test_repCountsV2() throws Exception {
        getCol(2);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        mCol.reset();
        // lrnReps should be accurate on pass/fail
        assertEquals(new Counts(1, 0, 0), mCol.getSched().counts());
        mCol.getSched().answerCard(getCard(), 1);
        assertEquals(new Counts(0, 1, 0), mCol.getSched().counts());
        mCol.getSched().answerCard(getCard(), 1);
        assertEquals(new Counts(0, 1, 0), mCol.getSched().counts());
        mCol.getSched().answerCard(getCard(), 3);
        assertEquals(new Counts(0, 1, 0), mCol.getSched().counts());
        mCol.getSched().answerCard(getCard(), 1);
        assertEquals(new Counts(0, 1, 0), mCol.getSched().counts());
        mCol.getSched().answerCard(getCard(), 3);
        assertEquals(new Counts(0, 1, 0), mCol.getSched().counts());
        mCol.getSched().answerCard(getCard(), 3);
        assertEquals(new Counts(0, 0, 0), mCol.getSched().counts());
        note = mCol.newNote();
        note.setItem("Front", "two");
        mCol.addNote(note);
        mCol.reset();
        // initial pass should be correct too
        mCol.getSched().answerCard(getCard(), 3);
        assertEquals(new Counts(0, 1, 0), mCol.getSched().counts());
        mCol.getSched().answerCard(getCard(), 1);
        assertEquals(new Counts(0, 1, 0), mCol.getSched().counts());
        mCol.getSched().answerCard(getCard(), 4);
        assertEquals(new Counts(0, 0, 0), mCol.getSched().counts());
        // immediate graduate should work
        note = mCol.newNote();
        note.setItem("Front", "three");
        mCol.addNote(note);
        mCol.reset();
        mCol.getSched().answerCard(getCard(), 4);
        assertEquals(new Counts(0, 0, 0), mCol.getSched().counts());
        // and failing a review should too
        note = mCol.newNote();
        note.setItem("Front", "three");
        mCol.addNote(note);
        Card c = note.cards().get(0);
        c.setType(CARD_TYPE_REV);
        c.setQueue(QUEUE_TYPE_REV);
        c.setDue(mCol.getSched().getToday());
        c.flush();
        mCol.reset();
        assertEquals(new Counts(0, 0, 1), mCol.getSched().counts());
        mCol.getSched().answerCard(getCard(), 1);
        assertEquals(new Counts(0, 1, 0), mCol.getSched().counts());
    }


    @Test
    public void test_timingV2() throws Exception {
        getCol(2);
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
        mCol.getSched().answerCard(c, 1);
        // the next card should be another review
        Card c2 = getCard();
        assertEquals(QUEUE_TYPE_REV, c2.getQueue());
        // if the failed card becomes due, it should show first
        c.setDue(mCol.getTime().intTime() - 1);
        c.flush();
        mCol.reset();
        c = getCard();
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
    }


    @Test
    public void test_collapseV2() throws Exception {
        getCol(2);
        // add a note
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        mCol.reset();
        // test collapsing
        Card c = getCard();
        mCol.getSched().answerCard(c, 1);
        c = getCard();
        mCol.getSched().answerCard(c, 4);
        assertNull(getCard());
    }


    @Test
    public void test_deckDueV2() throws Exception {
        getCol(2);
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
        note.model().put("did", addDeck("foo::bar"));
        mCol.addNote(note);
        // and one that's a sibling
        note = mCol.newNote();
        note.setItem("Front", "three");
        note.model().put("did", addDeck("foo::baz"));
        mCol.addNote(note);
        mCol.reset();
        assertEquals(5, mDecks.allSortedNames().size());
        DeckDueTreeNode tree = mCol.getSched().deckDueTree().get(0);
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
        mCol.getSched().deckDueTree();
    }


    @Test
    public void test_deckTree() throws Exception {
        getCol(2);
        addDeck("new::b::c");
        addDeck("new2");
        // new should not appear twice in tree
        List<String> names = new ArrayList<>();
        for (DeckDueTreeNode tree : mCol.getSched().deckDueTree()) {
            names.add(tree.getLastDeckNameComponent());
        }
        names.remove("new");
        assertFalse(names.contains("new"));
    }


    @Test
    public void test_deckFlowV2() throws Exception {
        getCol(2);
        // add a note with default deck
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        // and one that's a child
        note = mCol.newNote();
        note.setItem("Front", "two");
        long default1 = addDeck("Default::2");
        note.model().put("did", default1);
        mCol.addNote(note);
        // and another that's higher up
        note = mCol.newNote();
        note.setItem("Front", "three");
        default1 = addDeck("Default::1");
        note.model().put("did", default1);
        mCol.addNote(note);
        // should get top level one first, then ::1, then ::2
        mCol.reset();
        assertEquals(new Counts(3, 0, 0), mCol.getSched().counts());
        for (String i : new String[] {"one", "three", "two"}) {
            Card c = getCard();
            assertEquals(i, c.note().getItem("Front"));
            mCol.getSched().answerCard(c, 3);
        }
    }


    @Test
    public void test_reorder() throws Exception {
        getCol(2);
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
            mCol.getSched().randomizeCards(1);
            if (note.cards().get(0).getDue() != note.getId()) {
                found = true;
                break;
            }
        }
        assertTrue(found);
        mCol.getSched().orderCards(1);
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
        /* todo: start
           mCol.getSched().sortCards(new long [] {note3.cards().get(0).getId(), note4.cards().get(0).getId()}, start=1, shift=true);
           assertEquals(3, note.cards().get(0).getDue());
           assertEquals(4, note2.cards().get(0).getDue());
           assertEquals(1, note3.cards().get(0).getDue());
           assertEquals(2, note4.cards().get(0).getDue());
        */
    }


    @Test
    public void test_forgetV2() throws Exception {
        getCol(2);
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
        assertEquals(new Counts(0, 0, 1), mCol.getSched().counts());
        mCol.getSched().forgetCards(Collections.singletonList(c.getId()));
        mCol.reset();
        assertEquals(new Counts(1, 0, 0), mCol.getSched().counts());
    }


    @Test
    public void test_reschedV2() throws Exception {
        getCol(2);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        Card c = note.cards().get(0);
        mCol.getSched().reschedCards(Collections.singletonList(c.getId()), 0, 0);
        c.load();
        assertEquals(mCol.getSched().getToday(), c.getDue());
        assertEquals(1, c.getIvl());
        assertEquals(QUEUE_TYPE_REV, c.getType());
        assertEquals(CARD_TYPE_REV, c.getQueue());
        mCol.getSched().reschedCards(Collections.singletonList(c.getId()), 1, 1);
        c.load();
        assertEquals(mCol.getSched().getToday() + 1, c.getDue());
        assertEquals(+1, c.getIvl());
    }


    @Test
    public void test_norelearnV2() throws Exception {
        getCol(2);
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
        mCol.getSched().answerCard(c, 1);
        mCol.getSched()._cardConf(c).getJSONObject("lapse").put("delays", new JSONArray(new double[] {}));
        mCol.getSched().answerCard(c, 1);
    }


    @Test
    public void test_failmultV2() throws Exception {
        getCol(2);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        mCol.addNote(note);
        Card c = note.cards().get(0);
        c.setType(CARD_TYPE_REV);
        c.setQueue(QUEUE_TYPE_REV);
        c.setIvl(100);
        c.setDue(mCol.getSched().getToday() - c.getIvl());
        c.setFactor(STARTING_FACTOR);
        c.setReps(3);
        c.setLapses(1);
        c.startTimer();
        c.flush();
        DeckConfig conf = mCol.getSched()._cardConf(c);
        conf.getJSONObject("lapse").put("mult", 0.5);
        mDecks.save(conf);
        c = getCard();
        advanceRobolectricLooper();
        mCol.getSched().answerCard(c, 1);
        assertEquals(50, c.getIvl());
        advanceRobolectricLooperWithSleep();
        mCol.getSched().answerCard(c, 1);
        assertEquals(25, c.getIvl());
    }


    @Test
    public void test_moveVersions() throws Exception {
        getCol(2);
        getCol(1);

        Note n = mCol.newNote();
        n.setItem("Front", "one");
        mCol.addNote(n);

        // make it a learning card
        mCol.reset();
        Card c = getCard();
        mCol.getSched().answerCard(c, 1);

        // the move to v2 should reset it to new
        getCol(2);
        c.load();
        assertEquals(QUEUE_TYPE_NEW, c.getQueue());
        assertEquals(CARD_TYPE_NEW, c.getType());

        // fail it again, and manually bury it
        mCol.reset();
        c = getCard();
        mCol.getSched().answerCard(c, 1);
        mCol.getSched().buryCards(new long[] {c.getId()});
        c.load();
        assertEquals(QUEUE_TYPE_MANUALLY_BURIED, c.getQueue());

        // revert to version 1
        getCol(1);

        // card should have moved queues
        c.load();
        assertEquals(QUEUE_TYPE_SIBLING_BURIED, c.getQueue());

        // and it should be new again when unburied
        mCol.getSched().unburyCards();
        c.load();
        assertEquals(CARD_TYPE_NEW, c.getQueue());
        assertEquals(QUEUE_TYPE_NEW, c.getType());

        // make sure relearning cards transition correctly to v1
        getCol(2);
        // card with 100 day interval, answering again
        mCol.getSched().reschedCards(Collections.singletonList(c.getId()), 100, 100);
        c.load();
        c.setDue(0);
        c.flush();
        DeckConfig conf = mCol.getSched()._cardConf(c);
        conf.getJSONObject("lapse").put("mult", 0.5);
        mDecks.save(conf);
        mCol.reset();
        c = getCard();
        mCol.getSched().answerCard(c, 1);
        c.load();
        assertEquals(50, c.getIvl());
        // due should be correctly set when removed from learning early
        getCol(1);
        c.load();
        assertEquals(QUEUE_TYPE_REV, c.getQueue());
        assertEquals(CARD_TYPE_REV, c.getType());
        assertEquals(50, c.getDue());
    }


    // cards with a due date earlier than the collection should retain
    // their due date when removed
    @Test
    public void test_negativeDueFilter() throws Exception {
        getCol(2);

        // card due prior to collection date
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        mCol.addNote(note);
        Card c = note.cards().get(0);
        c.setDue(-5);
        c.setQueue(QUEUE_TYPE_REV);
        c.setIvl(5);
        c.flush();

        // into and out of filtered deck
        long did = addDynamicDeck("Cram");
        mCol.getSched().rebuildDyn(did);
        mCol.getSched().emptyDyn(did);
        mCol.reset();

        c.load();
        assertEquals(-5, c.getDue());
    }


    // hard on the first step should be the average of again and good,
    // and it should be logged properly


    @Test
    @Ignore("Port anki@a9c93d933cadbf5d9c7e3e2b4f7a25d2c59da5d3")
    public void test_initial_repeat() throws Exception {
        getCol(2);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        note.setItem("Back", "two");
        mCol.addNote(note);

        mCol.reset();
        Card c = getCard();
        mCol.getSched().answerCard(c, 2);
        // should be due in ~ 5.5 mins
        long expected = mCol.getTime().intTime() + (int)(5.5 * 60);
        long due = c.getDue();
        assertThat(expected - 10, is(lessThan(due)));
        assertThat(due, is(lessThanOrEqualTo((long)(expected * 1.25))));

        long ivl = mCol.getDb().queryLongScalar("select ivl from revlog");
        assertEquals((long) (-5.5 * 60), ivl);
    }

    @Test
    public void regression_test_preview() throws Exception {
        //"https://github.com/ankidroid/Anki-Android/issues/7285"
        getCol(2);
        AbstractSched sched = mCol.getSched();
        addNoteUsingBasicModel("foo", "bar");
        long did = addDynamicDeck("test");
        Deck deck = mDecks.get(did);
        deck.put("resched", false);
        sched.rebuildDyn(did);
        mCol.reset();
        advanceRobolectricLooper();
        Card card;
        for(int i = 0; i < 3; i++) {
            card = sched.getCard();
            assertNotNull(card);
            sched.answerCard(card, Consts.BUTTON_ONE);
        }
        assertEquals(1, sched.lrnCount());
        card = sched.getCard();
        assertEquals(1, sched.counts(card).getLrn());
        sched.answerCard(card, Consts.BUTTON_ONE);
        assertDoesNotThrow(mCol::undo);
    }
}
