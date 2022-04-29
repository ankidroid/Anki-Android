/*
 *  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.libanki

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.libanki.Collection.UndoReview
import com.ichi2.libanki.Consts.COUNT_REMAINING
import com.ichi2.libanki.Consts.QUEUE_TYPE_LRN
import com.ichi2.libanki.Consts.QUEUE_TYPE_NEW
import com.ichi2.libanki.sched.Counts
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.`is`
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.Exception
import kotlin.Throws

@RunWith(AndroidJUnit4::class)
class UndoTest : RobolectricTest() {
    /*****************
     ** Undo         *
     *****************/
    @get:Throws(Exception::class)
    val colV2: Collection
        get() {
            val col = col
            col.changeSchedulerVer(2)
            return col
        }

    @SuppressLint("LegacyNullAssertionDetector")
    @Test
    @Ignore("We need to figure out how to test save/undo")
    @Throws(Exception::class)
    fun test_op() {
        val col = colV2
        // should have no undo by default
        assertNull(col.undoType())
        // let's adjust a study option
        col.save("studyopts")
        col.set_config("abc", 5)
        // it should be listed as undoable
        assertEquals("studyopts", col.undoName(targetContext.resources))
        // with about 5 minutes until it's clobbered
        /* lastSave
           assertThat(getTime().now() - col._lastSave, lesserThan(1));
        */
        // undoing should restore the old value
        col.undo()
        assertNull(col.undoType())
        assertFalse(col.has_config("abc"))
        // an (auto)save will clear the undo
        col.save("foo")
        assertEquals("foo", col.undoName(targetContext.resources))
        col.save()
        assertEquals("", col.undoName(targetContext.resources))
        // and a review will, too
        col.save("add")
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        col.reset()
        assertEquals("add", col.undoName(targetContext.resources))
        val c = col.sched.card
        col.sched.answerCard(c!!, Consts.BUTTON_TWO)
        assertEquals("Review", col.undoName(targetContext.resources))
    }

    @SuppressLint("LegacyNullAssertionDetector")
    @Test
    @Throws(Exception::class)
    fun test_review() {
        val col = colV2
        col.set_config("counts", COUNT_REMAINING)
        var note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        col.reset()
        /* TODO:  undo after reset ?
        assertNotNull(col.undoType());

         */
        // answer
        assertEquals(Counts(1, 0, 0), col.sched.counts())
        var c = col.sched.card
        assertEquals(QUEUE_TYPE_NEW, c!!.queue)
        col.sched.answerCard(c, Consts.BUTTON_THREE)
        assertEquals(1001, c.left)
        assertEquals(Counts(0, 1, 0), col.sched.counts())
        assertEquals(QUEUE_TYPE_LRN, c.queue)
        // undo
        assertNotNull(col.undoType())
        col.undo()
        col.reset()
        assertEquals(Counts(1, 0, 0), col.sched.counts())
        c.load()
        assertEquals(QUEUE_TYPE_NEW, c.queue)
        assertNotEquals(1001, c.left)
        assertNull(col.undoType())
        // we should be able to undo multiple answers too
        note = col.newNote()
        note.setItem("Front", "two")
        col.addNote(note)
        col.reset()
        assertEquals(Counts(2, 0, 0), col.sched.counts())
        c = col.sched.card
        col.sched.answerCard(c!!, Consts.BUTTON_THREE)
        c = col.sched.card
        col.sched.answerCard(c!!, Consts.BUTTON_THREE)
        assertEquals(Counts(0, 2, 0), col.sched.counts())
        col.undo()
        col.reset()
        assertEquals(Counts(1, 1, 0), col.sched.counts())
        col.undo()
        col.reset()
        assertEquals(Counts(2, 0, 0), col.sched.counts())
        // performing a normal op will clear the review queue
        c = col.sched.card
        col.sched.answerCard(c!!, Consts.BUTTON_THREE)
        assertThat(
            col.undoType(),
            `is`(
                instanceOf(
                    UndoReview::class.java
                )
            )
        )
        col.save("foo")
        // Upstream, "save" can be undone. This test fails here because it's not the case in AnkiDroid
        assumeThat(col.undoName(targetContext.resources), `is`("foo"))
        col.undo()
    }
}
