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
    public Collection getColV2() throws Exception {
        Collection col = getCol();
        col.changeSchedulerVer(2);
        return col;
    }


    @Test
    @Ignore("We need to figure out how to test save/undo")
    public void test_op() throws Exception {
        Collection col = getColV2();
        // should have no undo by default
        assertNull(col.mUndo.undoType());
        // let's adjust a study option
        col.save("studyopts");
        col.getConf().put("abc", 5);
        // it should be listed as undoable
        assertEquals("studyopts", col.mUndo.undoName(getTargetContext().getResources()));
        // with about 5 minutes until it's clobbered
        /* lastSave
           assertThat(getTime().now() - col._lastSave, lesserThan(1));
        */
        // undoing should restore the old value
        col.mUndo.undo(col);
        assertNull(col.mUndo.undoType());
        assertFalse(col.getConf().has("abc"));
        // an (auto)save will clear the undo
        col.save("foo");
        assertEquals("foo", col.mUndo.undoName(getTargetContext().getResources()));
        col.save();
        assertEquals("", col.mUndo.undoName(getTargetContext().getResources()));
        // and a review will, too
        col.save("add");
        Note note = col.newNote();
        note.setItem("Front", "one");
        col.addNote(note);
        col.reset();
        assertEquals("add", col.mUndo.undoName(getTargetContext().getResources()));
        Card c = col.getSched().getCard();
        col.getSched().answerCard(c, 2);
        assertEquals("Review", col.mUndo.undoName(getTargetContext().getResources()));
    }


    @Test
    public void test_review() throws Exception {
        Collection col = getColV2();
        col.getConf().put("counts", COUNT_REMAINING);
        Note note = col.newNote();
        note.setItem("Front", "one");
        col.addNote(note);
        col.reset();
        /* TODO:  undo after reset ?
        assertNotNull(col.undoType());

         */
        // answer
        assertEquals(new Counts(1, 0, 0), col.getSched().counts());
        Card c = col.getSched().getCard();
        assertEquals(QUEUE_TYPE_NEW, c.getQueue());
        col.getSched().answerCard(c, 3);
        assertEquals(1001, c.getLeft());
        assertEquals(new Counts(0, 1, 0), col.getSched().counts());
        assertEquals(QUEUE_TYPE_LRN, c.getQueue());
        // undo
        assertNotNull(col.mUndo.undoType());
        col.mUndo.undo(col);
        col.reset();
        assertEquals(new Counts(1, 0, 0), col.getSched().counts());
        c.load();
        assertEquals(QUEUE_TYPE_NEW, c.getQueue());
        assertNotEquals(1001, c.getLeft());
        assertNull(col.mUndo.undoType());
        // we should be able to undo multiple answers too
        note = col.newNote();
        note.setItem("Front", "two");
        col.addNote(note);
        col.reset();
        assertEquals(new Counts(2, 0, 0), col.getSched().counts());
        c = col.getSched().getCard();
        col.getSched().answerCard(c, 3);
        c = col.getSched().getCard();
        col.getSched().answerCard(c, 3);
        assertEquals(new Counts(0, 2, 0), col.getSched().counts());
        col.mUndo.undo(col);
        col.reset();
        assertEquals(new Counts(1, 1, 0), col.getSched().counts());
        col.mUndo.undo(col);
        col.reset();
        assertEquals(new Counts(2, 0, 0), col.getSched().counts());
        // performing a normal op will clear the review queue
        c = col.getSched().getCard();
        col.getSched().answerCard(c, 3);
        assertThat(col.mUndo.undoType(), is(instanceOf(UndoManager.UndoReview.class)));
        col.save("foo");
        // Upstream, "save" can be undone. This test fails here because it's not the case in AnkiDroid
        assumeThat(col.mUndo.undoName(getTargetContext().getResources()), is("foo"));
        col.mUndo.undo(col);
    }
}
