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
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.async.TaskListener;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.Note;
import com.ichi2.utils.JSONArray;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

import java.util.Arrays;

import static com.ichi2.anki.AbstractFlashcardViewer.EASE_3;
import static com.ichi2.async.CollectionTask.TASK_TYPE.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;

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
            getCol().changeSchedulerVer(schedVersion);
        } catch (ConfirmModSchemaException e) {
            throw new RuntimeException("Could not change schedVer", e);
        }
    }

    @Test
    public void testUndoResetsCardCountsToCorrectValue() throws InterruptedException {
        // #6587
        addNoteUsingBasicModel("Hello", "World");

        AbstractSched sched = getCol().getSched();
        sched.reset();

        Card cardBeforeUndo = sched.getCard();
        int[] countsBeforeUndo = sched.counts();
        // Not shown in the UI, but there is a state where the card has been removed from the queue, but not answered
        // where the counts are decremented.
        assertThat(countsBeforeUndo, is(new int[] { 0, 0, 0 }));

        sched.answerCard(cardBeforeUndo, EASE_3);

        waitForTask(UNDO, 5000);

        int[] countsAfterUndo = sched.counts();

        assertThat("Counts after an undo should be the same as before an undo", countsAfterUndo, is(countsBeforeUndo));
    }

    @Test
    public void ensureUndoCorrectCounts() {
        Collection col = getCol();
        AbstractSched sched = col.getSched();
        Deck deck = col.getDecks().get(1);
        DeckConfig dconf = col.getDecks().getConf(1);
        dconf.getJSONObject("new").put("perDay", 10);
        JSONArray newCount = deck.getJSONArray("newToday");
        for (int i = 0; i < 20; i++) {
            Note note = col.newNote();
            note.setField(0, "a");
            col.addNote(note);
        }
        sched.reset();
        assertThat(col.cardCount(), is(20));
        assertThat(sched.counts()[0], is(10));
        Card card = sched.getCard();
        assertThat(sched.counts()[0], is(9));
        assertThat(sched.counts(card)[0], is(10));
        sched.answerCard(card, 3);
        sched.getCard();
        final boolean[] executed = {false};
        CollectionTask.launchCollectionTask(UNDO,
                new TaskListener() {
                    Card card;
                    @Override
                    public void onPreExecute() {

                    }

                    @Override
                    public void onProgressUpdate(TaskData data) {
                        card = data.getCard();
                    }


                    @Override
                    public void onPostExecute(TaskData result) {
                        assertThat(sched.counts()[0], is(9));
                        assertThat(sched.counts(card)[0], is(10));
                        executed[0] = true;
                    }
                });
        waitForAsyncTasksToComplete();
        assertTrue(executed[0]);
    }
}
