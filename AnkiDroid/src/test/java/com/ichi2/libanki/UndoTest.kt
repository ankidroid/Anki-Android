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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.libanki.Collection.UndoReview
import com.ichi2.libanki.Consts.COUNT_REMAINING
import com.ichi2.libanki.Consts.QUEUE_TYPE_LRN
import com.ichi2.libanki.Consts.QUEUE_TYPE_NEW
import com.ichi2.libanki.sched.Counts
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*

@RunWith(AndroidJUnit4::class)
class UndoTest : RobolectricTest() {
    /*****************
     * Undo
     */
    @get:Throws(Exception::class)
    private val colV2: Collection
        get() {
            val col = col
            col.changeSchedulerVer(2)
            return col
        }

    @Test
    @Ignore("We need to figure out how to test save/undo")
    @Throws(Exception::class)
    fun test_op() {
        val col = colV2
        with(col) {
            // should have no undo by default
            assertNull(undoType())
            // let's adjust a study option
            save("studyopts")
            set_config("abc", 5)
            // it should be listed as undoable
            assertEquals("studyopts", undoName(targetContext.resources))
            // with about 5 minutes until it's clobbered
            /* lastSave
               assertThat(getTime().now() - col._lastSave, lesserThan(1));
            */
            // undoing should restore the old value
            undo()
            assertNull(undoType())
            assertFalse(has_config("abc"))
            // an (auto)save will clear the undo
            save("foo")
            assertEquals("foo", undoName(targetContext.resources))
            save()
            assertEquals("", undoName(targetContext.resources))
            // and a review will, too
            save("add")
            val note = newNote()
            note.setItem("Front", "one")
            addNote(note)
            reset()
            assertEquals("add", undoName(targetContext.resources))
            val c = sched.card
            sched.answerCard(c!!, Consts.BUTTON_TWO)
            assertEquals("Review", undoName(targetContext.resources))
        }
    }

    // TODO why is this test ignored if it doesn't have @Ignore(happens for both java and kotlin versions)
    @Test
    @Throws(Exception::class)
    fun test_review() {
        val col = colV2
        with(col) {
            set_config("counts", COUNT_REMAINING)
            var note = col.newNote()
            note.setItem("Front", "one")
            addNote(note)
            reset()
            /* TODO:  undo after reset ?
               assertNotNull(col.undoType())
            */
            // answer
            assertEquals(Counts(1, 0, 0), sched.counts())
            var c = sched.card
            assertEquals(QUEUE_TYPE_NEW, c!!.queue)
            sched.answerCard(c, Consts.BUTTON_THREE)
            assertEquals(1001, c.left)
            assertEquals(Counts(0, 1, 0), sched.counts())
            assertEquals(QUEUE_TYPE_LRN, c.queue)
            // undo
            assertNotNull(undoType())
            undo()
            reset()
            assertEquals(Counts(1, 0, 0), sched.counts())
            c.load()
            assertEquals(QUEUE_TYPE_NEW, c.queue)
            assertNotEquals(1001, c.left)
            assertNull(undoType())
            // we should be able to undo multiple answers too
            note = newNote()
            note.setItem("Front", "two")
            addNote(note)
            reset()
            assertEquals(Counts(2, 0, 0), sched.counts())
            c = sched.card
            sched.answerCard(c!!, Consts.BUTTON_THREE)
            c = sched.card
            sched.answerCard(c!!, Consts.BUTTON_THREE)
            assertEquals(Counts(0, 2, 0), sched.counts())
            undo()
            reset()
            assertEquals(Counts(1, 1, 0), sched.counts())
            undo()
            reset()
            assertEquals(Counts(2, 0, 0), sched.counts())
            // performing a normal op will clear the review queue
            c = sched.card
            sched.answerCard(c!!, Consts.BUTTON_THREE)
            assertThat(undoType(), instanceOf(UndoReview::class.java))
            save("foo")
            // Upstream, "save" can be undone. This test fails here because it's not the case in AnkiDroid
            assumeThat(undoName(targetContext.resources), equalTo("foo"))
            undo()
        }
    }
}
