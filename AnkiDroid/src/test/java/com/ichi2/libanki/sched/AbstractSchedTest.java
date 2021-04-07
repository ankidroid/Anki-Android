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

import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.async.CollectionTask;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.utils.Time;
import com.ichi2.testutils.AnkiAssert;
import com.ichi2.utils.JSONArray;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

import java.util.Arrays;

import static com.ichi2.anki.AbstractFlashcardViewer.EASE_3;
import static com.ichi2.async.CollectionTask.nonTaskUndo;
import static com.ichi2.testutils.AnkiAssert.assertDoesNotThrow;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

// Note: These tests can't be run individually but can from the class-level
// gradlew AnkiDroid:testDebug --tests "com.ichi2.libanki.sched.AbstractSchedTest.*"
@RunWith(ParameterizedRobolectricTestRunner.class)
public class AbstractSchedTest extends RobolectricTest {

    @Parameter
    public int schedVersion;

    @Parameters(name = "SchedV{0}")
    public static java.util.Collection<Object[]> initParameters() {
        // This does one run with schedVersion injected as 1, and one run as 2
        return Arrays.asList(new Object[][] { { 1 }, { 2 } });
    }

    @Before
    @Override
    public void setUp() {
        super.setUp();
        try {
            getCol(schedVersion);
        } catch (ConfirmModSchemaException e) {
            throw new RuntimeException("Could not change schedVer", e);
        }
    }

    @Test
    public void testUndoResetsCardCountsToCorrectValue() throws InterruptedException {
        // #6587
        addNoteUsingBasicModel("Hello", "World");

        mCol.reset();

        Card cardBeforeUndo = mSched.getCard();
        Counts countsBeforeUndo = mSched.counts();
        // Not shown in the UI, but there is a state where the card has been removed from the queue, but not answered
        // where the counts are decremented.
        assertThat(countsBeforeUndo, is(new Counts(0, 0, 0)));

        mSched.answerCard(cardBeforeUndo, EASE_3);

        waitFortask(new CollectionTask.Undo(), 5000);

        Counts countsAfterUndo = mSched.counts();

        assertThat("Counts after an undo should be the same as before an undo", countsAfterUndo, is(countsBeforeUndo));
    }

    @Test
    public void ensureUndoCorrectCounts() {
        Deck deck = mDecks.get(1);
        DeckConfig dconf = mDecks.getConf(1);
        dconf.getJSONObject("new").put("perDay", 10);
        JSONArray newCount = deck.getJSONArray("newToday");
        for (int i = 0; i < 20; i++) {
            Note note = mCol.newNote();
            note.setField(0, "a");
            mCol.addNote(note);
        }
        mCol.reset();
        assertThat(mCol.cardCount(), is(20));
        assertThat(mSched.newCount(), is(10));
        Card card = mSched.getCard();
        assertThat(mSched.newCount(), is(9));
        assertThat(mSched.counts(card).getNew(), is(10));
        mSched.answerCard(card, mSched.getGoodNewButton());
        mSched.getCard();
        nonTaskUndo(mCol);
        card.load();
        assertThat(mSched.newCount(), is(9));
        assertThat(mSched.counts(card).getNew(), is(10));
    }

    @Test
    public void testCardQueue() {
        SimpleCardQueue queue = new SimpleCardQueue(mSched);
        assertThat(queue.size(), is(0));
        final int nbCard = 6;
        long[] cids = new long[nbCard];
        for (int i = 0; i < nbCard; i++) {
            Note note = addNoteUsingBasicModel("foo", "bar");
            Card card = note.firstCard();
            long cid = card.getId();
            cids[i] = cid;
            queue.add(cid);
        }
        assertThat(queue.size(), is(nbCard));
        assertEquals(cids[0], queue.removeFirstCard().getId());
        assertThat(queue.size(), is(nbCard - 1));
        queue.remove(cids[1]);
        assertThat(queue.size(), is(nbCard - 2));
        queue.remove(cids[3]);
        assertThat(queue.size(), is(nbCard - 3));
        assertEquals(cids[2], queue.removeFirstCard().getId());
        assertThat(queue.size(), is(nbCard - 4));
        assertEquals(cids[4], queue.removeFirstCard().getId());
        assertThat(queue.size(), is(nbCard - 5));
    }

    @Test
    public void siblingCorrectlyBuried() {
        // #6903
        DeckConfig dconf = mDecks.getConf(1);
        dconf.getJSONObject("new").put("bury", true);
        final int nbNote = 2;
        Note[] notes = new Note[nbNote];
        for (int i = 0; i < nbNote; i++) {
            Note note  = addNoteUsingBasicAndReversedModel("front", "back");
            notes[i] = note;
        }
        mCol.reset();

        for (int i = 0; i < nbNote; i++) {
            Card card = mSched.getCard();
            Counts counts = mSched.counts(card);
            mSched.setCurrentCard(card); // imitate what the reviewer does
            assertThat(counts.getNew(), is(greaterThan(nbNote - i))); // Actual number of new card.
            assertThat(counts.getNew(), is(lessThanOrEqualTo(nbNote * 2 - i))); // Maximal number potentially shown,
            // because decrementing does not consider burying sibling
            assertEquals(0, counts.getLrn());
            assertEquals(0, counts.getRev());
            assertEquals(notes[i].firstCard().getId(), card.getId());
            assertEquals(Consts.QUEUE_TYPE_NEW, card.getQueue());
            mSched.answerCard(card, mSched.answerButtons(card));
        }

        Card card = mSched.getCard();
        assertNull(card);
    }

    @Test
    public void deckDueTreeInconsistentDecksPasses() {
        // https://github.com/ankidroid/Anki-Android/issues/6383#issuecomment-686266966
        // The bad data came from AnkiWeb, this passes using "addDeck" but we can't assume this is always called.

        String parent = "DANNY SULLIVAN MCM DECK";
        String child = "Danny Sullivan MCM Deck::*MCM_UNTAGGED_CARDS";

        addDeckWithExactName(parent);
        addDeckWithExactName(child);

        mDecks.checkIntegrity();
        assertDoesNotThrow(() -> mSched.deckDueList());
    }


    private class IncreaseToday {
        private final long aId, bId, cId, dId;
        private final Decks decks;
        private final AbstractSched sched;

        public IncreaseToday() {
            decks = mDecks;
            sched = mSched;
            aId = addDeck("A");
            bId = addDeck("A::B");
            cId = addDeck("A::B::C");
            dId = addDeck("A::B::D");
        }

        private void assertNewCountIs(String explanation, long did, int expected) {
            decks.select(did);
            mSched.resetCounts();
            assertThat(explanation, mSched.newCount(), is(expected));
        }

        private void increaseAndAssertNewCountsIs(String explanation, long did, int a, int b, int c, int d) {
            extendNew(did);
            assertNewCountsIs(explanation, a, b, c, d);
        }

        private void assertNewCountsIs(String explanation, int a, int b, int c, int d) {
            assertNewCountIs(explanation, aId, a);
            assertNewCountIs(explanation, bId, b);
            assertNewCountIs(explanation, cId, c);
            assertNewCountIs(explanation, dId, d);
        }

        private void extendNew(long did) {
            decks.select(did);
            mSched.extendLimits(1, 0);
        }

        public void test() {
            DeckConfig dconf = decks.getConf(1);
            dconf.getJSONObject("new").put("perDay", 0);


            Model model = mModels.byName("Basic");
            for (long did : new long[]{cId, dId}) {
                // The note is added in model's did. So change model's did.
                model.put("did", did);
                for (int i = 0; i < 4; i++) {
                    addNoteUsingBasicModel("foo", "bar");
                }
            }

            assertNewCountsIs("All daily limits are 0", 0, 0, 0, 0);
            increaseAndAssertNewCountsIs("Adding a review in C add it in its parents too", cId, 1, 1, 1, 0);
            increaseAndAssertNewCountsIs("Adding a review in A add it in its children too", aId, 2, 2, 2, 1);
            increaseAndAssertNewCountsIs("Adding a review in B add it in its parents and children too", bId,3, 3, 3, 2);
            increaseAndAssertNewCountsIs("Adding a review in D add it in its parents too", dId, 4, 4, 3, 3);
            increaseAndAssertNewCountsIs("Adding a review in D add it in its parents too", dId, 5, 5, 3, 4);

            decks.select(cId);
            mCol.reset();
            for (int i = 0; i < 3; i++) {
                Card card = mSched.getCard();
                mSched.answerCard(card, mSched.answerButtons(card));
            }
            assertNewCountsIs("All cards from C are reviewed. Still 4 cards to review in D, but only two available because of A's limit.", 2, 2, 0, 2);

            increaseAndAssertNewCountsIs("Increasing the number of card in C increase it in its parents too. This allow for more review in any children, and in particular in D.", cId, 3, 3, 1, 3);
            // D increase because A's limit changed.
            // This means that increasing C, which is not related to D, can increase D
            // This follows upstream but is really counter-intuitive.


            increaseAndAssertNewCountsIs("Adding a review in C add it in its parents too, even if c has no more card. This allow one more card in d.", cId, 4, 4, 1, 4);
            /* I would have expected :
             increaseAndAssertNewCountsIs("", cId, 3, 3, 1, 3);
             But it seems that applying "increase c", while not actually increasing c (because there are no more new card)
             still increases A.

             Upstream, the number of new card to add is limited to the number of cards in the deck not already planned for today.
             So, to reproduce it, you either need to temporary add a card in A::B::C, increase today limit and delete the card
             or to do
```python
from aqt import mw
c = mw.mCol.decks.byName("A::B::C")
mw.mCol.mSched.extendLimits(1, 0)
```
             */
        }
    }

    /** Those test may be unintuitive, but they follow upstream as close as possible. */
    @Test
    public void increaseToday() {
        new IncreaseToday().test();
    }


    protected void undoAndRedo(boolean preload) {
        DeckConfig conf = mDecks.confForDid(1);
        conf.getJSONObject("new").put("delays", new JSONArray(new double[] {1, 3, 5, 10}));
        mCol.getConf().put("collapseTime", 20 * 60);
        Note note = addNoteUsingBasicModel("foo", "bar");

        mCol.reset();

        Card card = mSched.getCard();
        assertNotNull(card);
        assertEquals(new Counts(1, 0, 0), mSched.counts(card));
        if (preload) {
            mSched.preloadNextCard();
        }

        mSched.answerCard(card, mSched.getGoodNewButton());

        card = mSched.getCard();
        assertNotNull(card);
        assertEquals(new Counts(0, (schedVersion == 1) ? 3 : 1, 0), mSched.counts(card));
        if (preload) {
            mSched.preloadNextCard();
        }


        mSched.answerCard(card, mSched.getGoodNewButton());

        card = mSched.getCard();
        assertNotNull(card);
        assertEquals(new Counts(0, (schedVersion == 1) ? 2 : 1, 0), mSched.counts(card));
        if (preload) {
            mSched.preloadNextCard();
        }

        assertNotNull(card);

        card = nonTaskUndo(mCol);
        assertNotNull(card);
        assertEquals(new Counts(0, (schedVersion == 1) ? 3 : 1, 0), mSched.counts(card));
        mSched.count();
        if (preload) {
            mSched.preloadNextCard();
        }


        mSched.answerCard(card, mSched.getGoodNewButton());
        card = mSched.getCard();
        assertNotNull(card);
        if (preload) {
            mSched.preloadNextCard();
        }
        assertEquals(new Counts(0, (schedVersion == 1) ? 2 : 1, 0), mSched.counts(card));

        assertNotNull(card);
    }

    @Test
    public void undoAndRedoPreload() {
        undoAndRedo(true);
    }

    @Test
    public void undoAndRedoNoPreload() {
        undoAndRedo(false);
    }

    private void addDeckWithExactName(String name) {
        long did = addDeck(name);
        Deck d = mDecks.get(did);
        d.put("name", name);
        mDecks.update(d);

        boolean hasMatch = mDecks.all().stream().anyMatch(x -> name.equals(x.getString("name")));
        assertThat(String.format("Deck %s should exist", name), hasMatch, is(true));
    }



    @Test
    public void regression_7066() {
        DeckConfig dconf = mDecks.getConf(1);
        dconf.getJSONObject("new").put("bury", true);
        addNoteUsingBasicAndReversedModel("foo", "bar");
        addNoteUsingBasicModel("plop", "foo");
        mCol.reset();
        Card card = mSched.getCard();
        mSched.setCurrentCard(card);
        mSched.preloadNextCard();
        mSched.answerCard(card, Consts.BUTTON_THREE);
        card = mSched.getCard();
        mSched.setCurrentCard(card);
        AnkiAssert.assertDoesNotThrow(mSched::preloadNextCard);
    }

    @Test
    public void regression_7984() {
        Card[] cards = new Card[2];
        for (int i = 0; i < 2; i++) {
            cards[i] = addNoteUsingBasicModel(Integer.toString(i), "").cards().get(0);
            cards[i].setQueue(Consts.QUEUE_TYPE_LRN);
            cards[i].setType(Consts.CARD_TYPE_LRN);
            cards[i].setDue(mTime.intTime() - 20 * 60 + i);
            cards[i].flush();
        }
        mCol.reset();
        // Regression test success non deterministically without the sleep
        Card gotten = mSched.getCard();
        advanceRobolectricLooperWithSleep();
        assertThat(gotten, is(cards[0]));
        mSched.answerCard(gotten, Consts.BUTTON_ONE);

        gotten = mSched.getCard();
        assertThat(gotten, is(cards[1]));
        mSched.answerCard(gotten, Consts.BUTTON_ONE);
        gotten = mSched.getCard();
        assertThat(gotten, is(cards[0]));
    }
}
