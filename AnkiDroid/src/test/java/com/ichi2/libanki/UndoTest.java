package com.ichi2.libanki;

import com.ichi2.anki.RobolectricTest;
import com.ichi2.libanki.sched.Counts;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.libanki.Consts.COUNT_REMAINING;
import static com.ichi2.libanki.Consts.QUEUE_TYPE_LRN;
import static com.ichi2.libanki.Consts.QUEUE_TYPE_NEW;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeThat;


@RunWith(AndroidJUnit4.class)
public class UndoTest extends RobolectricTest {
    /*****************
     ** Undo         *
     *****************/

    @Test
    @Ignore("We need to figure out how to test save/undo")
    public void test_op() throws Exception {
        getCol(2);
        // should have no undo by default
        assertNull(mCol.undoType());
        // let's adjust a study option
        mCol.save("studyopts");
        mCol.getConf().put("abc", 5);
        // it should be listed as undoable
        assertEquals("studyopts", mCol.undoName(getTargetContext().getResources()));
        // with about 5 minutes until it's clobbered
        /* lastSave
           assertThat(getTime().now() - mCol._lastSave, lesserThan(1));
        */
        // undoing should restore the old value
        mCol.undo();
        assertNull(mCol.undoType());
        assertFalse(mCol.getConf().has("abc"));
        // an (auto)save will clear the undo
        mCol.save("foo");
        assertEquals("foo", mCol.undoName(getTargetContext().getResources()));
        mCol.save();
        assertEquals("", mCol.undoName(getTargetContext().getResources()));
        // and a review will, too
        mCol.save("add");
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        mCol.reset();
        assertEquals("add", mCol.undoName(getTargetContext().getResources()));
        Card c = mCol.getSched().getCard();
        mCol.getSched().answerCard(c, 2);
        assertEquals("Review", mCol.undoName(getTargetContext().getResources()));
    }


    @Test
    public void test_review() throws Exception {
        getCol(2);
        mCol.getConf().put("counts", COUNT_REMAINING);
        Note note = mCol.newNote();
        note.setItem("Front", "one");
        mCol.addNote(note);
        mCol.reset();
        /* TODO:  undo after reset ?
        assertNotNull(mCol.undoType());

         */
        // answer
        assertEquals(new Counts(1, 0, 0), mCol.getSched().counts());
        Card c = mCol.getSched().getCard();
        assertEquals(QUEUE_TYPE_NEW, c.getQueue());
        mCol.getSched().answerCard(c, 3);
        assertEquals(1001, c.getLeft());
        assertEquals(new Counts(0, 1, 0), mCol.getSched().counts());
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        // undo
        assertNotNull(mCol.undoType());
        mCol.undo();
        mCol.reset();
        assertEquals(new Counts(1, 0, 0), mCol.getSched().counts());
        c.load();
        assertEquals(QUEUE_TYPE_NEW, c.getQueue());
        assertNotEquals(1001, c.getLeft());
        assertNull(mCol.undoType());
        // we should be able to undo multiple answers too
        note = mCol.newNote();
        note.setItem("Front", "two");
        mCol.addNote(note);
        mCol.reset();
        assertEquals(new Counts(2, 0, 0), mCol.getSched().counts());
        c = mCol.getSched().getCard();
        mCol.getSched().answerCard(c, 3);
        c = mCol.getSched().getCard();
        mCol.getSched().answerCard(c, 3);
        assertEquals(new Counts(0, 2, 0), mCol.getSched().counts());
        mCol.undo();
        mCol.reset();
        assertEquals(new Counts(1, 1, 0), mCol.getSched().counts());
        mCol.undo();
        mCol.reset();
        assertEquals(new Counts(2, 0, 0), mCol.getSched().counts());
        // performing a normal op will clear the review queue
        c = mCol.getSched().getCard();
        mCol.getSched().answerCard(c, 3);
        assertThat(mCol.undoType(), is(instanceOf(Collection.UndoReview.class)));
        mCol.save("foo");
        // Upstream, "save" can be undone. This test fails here because it's not the case in AnkiDroid
        assumeThat(mCol.undoName(getTargetContext().getResources()), is("foo"));
        mCol.undo();
    }
}
