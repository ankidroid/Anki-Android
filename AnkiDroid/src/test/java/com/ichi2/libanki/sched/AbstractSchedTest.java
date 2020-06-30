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
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Consts;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

import java.util.Arrays;

import static com.ichi2.anki.AbstractFlashcardViewer.EASE_3;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

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

        waitForTask(CollectionTask.TASK_TYPE_UNDO, 5000);

        int[] countsAfterUndo = sched.counts();

        assertThat("Counts after an undo should be the same as before an undo", countsAfterUndo, is(countsBeforeUndo));
    }
}
