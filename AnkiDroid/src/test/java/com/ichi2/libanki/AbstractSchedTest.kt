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
package com.ichi2.libanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.libanki.sched.Counts
import com.ichi2.testutils.JvmTest
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers.*
import org.json.JSONArray
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

// Note: These tests can't be run individually but can from the class-level
// gradlew AnkiDroid:testDebug --tests "com.ichi2.libanki.AbstractSchedTest.*"
@KotlinCleanup("is -> equalTo")
@KotlinCleanup("reduce newlines in asserts")
@KotlinCleanup("improve increaseAndAssertNewCountsIs")
@RunWith(AndroidJUnit4::class)
class AbstractSchedTest : JvmTest() {
    @Test
    fun ensureUndoCorrectCounts() {
        val col = col
        val sched = col.sched
        val dconf = col.decks.getConf(1)
        assertThat(dconf, notNullValue())
        dconf.getJSONObject("new").put("perDay", 10)
        col.decks.save(dconf)
        for (i in 0..19) {
            val note = col.newNote()
            note.setField(0, "a")
            col.addNote(note)
        }
        assertThat(col.cardCount(), `is`(20))
        assertThat(sched.newCount(), `is`(10))
        val card = sched.card
        assertThat(sched.newCount(), `is`(10))
        assertThat(sched.counts().new, `is`(10))
        sched.answerCard(card!!, 3)
        sched.card
        col.undo()
        assertThat(sched.newCount(), `is`(10))
    }

    @Test
    fun undoAndRedo() {
        val col = col
        val conf = col.decks.confForDid(1)
        conf.getJSONObject("new").put("delays", JSONArray(doubleArrayOf(1.0, 3.0, 5.0, 10.0)))
        col.decks.save(conf)
        col.config.set("collapseTime", 20 * 60)
        val sched = col.sched

        addNoteUsingBasicModel("foo", "bar")

        var card = sched.card
        assertNotNull(card)
        assertEquals(Counts(1, 0, 0), sched.counts())

        sched.answerCard(card, 3)

        card = sched.card
        assertNotNull(card)
        assertEquals(
            Counts(0, 1, 0),
            sched.counts()
        )

        sched.answerCard(card, 3)

        card = sched.card
        assertNotNull(card)
        assertEquals(
            Counts(0, 1, 0),
            sched.counts()
        )

        assertNotNull(card)

        assertEquals(
            Counts(0, 1, 0),
            sched.counts()
        )

        card = sched.card!!
        sched.answerCard(card, 3)
        card = sched.card
        assertNotNull(card)
        assertEquals(
            Counts(0, 1, 0),
            sched.counts()
        )
        assertNotNull(card)
    }
}
