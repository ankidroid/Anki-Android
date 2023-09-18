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
import com.ichi2.anki.utils.SECONDS_PER_DAY
import com.ichi2.libanki.Consts.BUTTON_FOUR
import com.ichi2.libanki.Consts.BUTTON_ONE
import com.ichi2.libanki.Consts.BUTTON_THREE
import com.ichi2.libanki.Consts.BUTTON_TWO
import com.ichi2.libanki.Consts.CARD_TYPE_LRN
import com.ichi2.libanki.Consts.CARD_TYPE_NEW
import com.ichi2.libanki.Consts.CARD_TYPE_RELEARNING
import com.ichi2.libanki.Consts.CARD_TYPE_REV
import com.ichi2.libanki.Consts.LEECH_SUSPEND
import com.ichi2.libanki.Consts.QUEUE_TYPE_DAY_LEARN_RELEARN
import com.ichi2.libanki.Consts.QUEUE_TYPE_LRN
import com.ichi2.libanki.Consts.QUEUE_TYPE_MANUALLY_BURIED
import com.ichi2.libanki.Consts.QUEUE_TYPE_NEW
import com.ichi2.libanki.Consts.QUEUE_TYPE_REV
import com.ichi2.libanki.Consts.QUEUE_TYPE_SIBLING_BURIED
import com.ichi2.libanki.Consts.STARTING_FACTOR
import com.ichi2.libanki.Consts.SYNC_VER
import com.ichi2.libanki.exception.ConfirmModSchemaException
import com.ichi2.libanki.sched.Counts
import com.ichi2.libanki.sched.Scheduler
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.libanki.utils.TimeManager.time
import com.ichi2.testutils.AnkiAssert
import com.ichi2.testutils.JvmTest
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.json.JSONArray
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.Exception
import java.time.Instant
import java.time.ZoneId
import java.util.*
import kotlin.Throws
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
// please wait for #11808 to be merged before starting cleanup
open class SchedulerTest : JvmTest() {
    @Test
    @Throws(ConfirmModSchemaException::class)
    fun handlesSmallSteps() {
        val col = col
        // a delay of 0 crashed the app (step of 0.01).
        addNoteUsingBasicModel("Hello", "World")
        col.decks.allConfig()[0].getJSONObject("new")
            .put("delays", JSONArray(listOf(0.01, 10)))
        val c = col.sched.card
        MatcherAssert.assertThat(c, Matchers.notNullValue())
        col.sched.answerCard(c!!, BUTTON_ONE)
    }

    @Test
    fun newTimezoneHandling() {
        val col = col
        // #5805
        MatcherAssert.assertThat(
            "Sync ver should be updated if we have a valid Rust collection",
            SYNC_VER,
            Matchers.equalTo(10)
        )
        MatcherAssert.assertThat(
            "localOffset should be set if using V2 Scheduler",
            col.config.get<Int?>("localOffset") != null,
            Matchers.equalTo(true)
        )
        val sched = col.sched
        MatcherAssert.assertThat(
            "new timezone should be enabled by default",
            sched._new_timezone_enabled(),
            Matchers.equalTo(true)
        )

        // a second call should be fine
        sched.set_creation_offset()
        MatcherAssert.assertThat(
            "new timezone should still be enabled",
            sched._new_timezone_enabled(),
            Matchers.equalTo(true)
        )
        // we can obtain the offset from "crt" without an issue - do not test the return as it depends on the local timezone
        sched._current_timezone_offset()
        sched.clear_creation_offset()
        MatcherAssert.assertThat(
            "new timezone should be disabled after clear",
            sched._new_timezone_enabled(),
            Matchers.equalTo(false)
        )
    }

    @Test
    @Throws(Exception::class)
    fun test_basics() {
        val col = col
        assertNull(col.sched.card)
    }

    @Test
    @Throws(Exception::class)
    fun test_new_v2() {
        val col = col
        Assert.assertEquals(0, col.sched.newCount().toLong())
        // add a note
        val note = col.newNote()
        note.setItem("Front", "one")
        note.setItem("Back", "two")
        col.addNote(note)
        Assert.assertEquals(1, col.sched.newCount().toLong())
        // fetch it
        val c = col.sched.card!!
        assertNotNull(c)
        Assert.assertEquals(QUEUE_TYPE_NEW, c.queue)
        Assert.assertEquals(CARD_TYPE_NEW, c.type)
        // if we answer it, it should become a learn card
        val t = time.intTime()
        col.sched.answerCard(c, BUTTON_ONE)
        Assert.assertEquals(QUEUE_TYPE_LRN, c.queue)
        Assert.assertEquals(CARD_TYPE_LRN, c.type)
        MatcherAssert.assertThat(c.due, Matchers.greaterThanOrEqualTo(t))

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
        //     col.getSched().answerCard(c, BUTTON_TWO)
        // }
    }

    @Test
    @Throws(Exception::class)
    fun test_newLimits_V2() {
        val col = col
        // add some notes
        val deck2 = addDeck("Default::foo")
        for (i in 0..29) {
            val note = col.newNote()
            note.setItem("Front", i.toString())
            if (i > 4) {
                note.model().put("did", deck2)
            }
            col.addNote(note)
        }
        // give the child deck a different configuration
        val c2 = col.decks.confId("new conf")
        col.decks.setConf(col.decks.get(deck2)!!, c2)
        // both confs have defaulted to a limit of 20
        Assert.assertEquals(20, col.sched.newCount().toLong())
        // first card we get comes from parent
        val c = col.sched.card!!
        Assert.assertEquals(1, c.did)
        // limit the parent to 10 cards, meaning we get 10 in total
        val conf1 = col.decks.confForDid(1)
        conf1.getJSONObject("new").put("perDay", 10)
        col.decks.save(conf1)
        Assert.assertEquals(10, col.sched.newCount().toLong())
        // if we limit child to 4, we should get 9
        val conf2 = col.decks.confForDid(deck2)
        conf2.getJSONObject("new").put("perDay", 4)
        col.decks.save(conf2)
        Assert.assertEquals(9, col.sched.newCount().toLong())
    }

    @Test
    @Throws(Exception::class)
    fun test_newBoxes_v2() {
        val col = col
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        val c = col.sched.card!!
        val conf = col.sched._cardConf(c)
        conf.getJSONObject("new").put("delays", JSONArray(doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)))
        col.decks.save(conf)
        col.sched.answerCard(c, BUTTON_TWO)
        // should handle gracefully
        conf.getJSONObject("new").put("delays", JSONArray(doubleArrayOf(1.0)))
        col.decks.save(conf)
        col.sched.answerCard(c, BUTTON_TWO)
    }

    @Test
    @Throws(Exception::class)
    @KotlinCleanup("This is flaky just before 4AM")
    fun test_learnV2() {
        if (Instant.now().atZone(ZoneId.systemDefault()).hour.let { it in 2..3 }) {
            // The backend shifts the current time around rollover, and expects the frontend to
            // do so as well. This could potentially be done with TimeManager in the future.
            return
        }
        TimeManager.reset()
        val col = col
        // add a note
        val note = col.newNote()
        note.setItem("Front", "one")
        note.setItem("Back", "two")
        col.addNote(note)
        // set as a learn card and rebuild queues
        col.db.execute("update cards set queue=0, type=0")
        // sched.getCard should return it, since it's due in the past
        val c = col.sched.card!!
        assertNotNull(c)
        val conf = col.sched._cardConf(c)
        conf.getJSONObject("new").put("delays", JSONArray(doubleArrayOf(0.5, 3.0, 10.0)))
        col.decks.save(conf)
        // fail it
        col.sched.answerCard(c, BUTTON_ONE)
        // it should have three reps left to graduation
        Assert.assertEquals(3, (c.left % 1000).toLong())
        // it should be due in 30 seconds
        val t = (c.due - time.intTime())
        MatcherAssert.assertThat(t, Matchers.greaterThanOrEqualTo(25L))
        MatcherAssert.assertThat(t, Matchers.lessThanOrEqualTo(40L))
        // pass it once
        col.sched.answerCard(c, BUTTON_THREE)
        // it should be due in 3 minutes
        var dueIn = c.due - time.intTime()
        MatcherAssert.assertThat(dueIn, Matchers.greaterThanOrEqualTo(178L))
        MatcherAssert.assertThat(
            dueIn,
            Matchers.lessThanOrEqualTo((180 * 1.25).toLong())
        )
        Assert.assertEquals(2, (c.left % 1000).toLong())
        // check log is accurate
        val log = col.db.database.query("select * from revlog order by id desc")
        Assert.assertTrue(log.moveToFirst())
        Assert.assertEquals(3, log.getInt(3).toLong())
        Assert.assertEquals(-180, log.getInt(4).toLong())
        Assert.assertEquals(-30, log.getInt(5).toLong())
        // pass again
        col.sched.answerCard(c, BUTTON_THREE)
        // it should be due in 10 minutes
        dueIn = c.due - time.intTime()
        MatcherAssert.assertThat(dueIn, Matchers.greaterThanOrEqualTo(599L))
        MatcherAssert.assertThat(
            dueIn,
            Matchers.lessThanOrEqualTo((600 * 1.25).toLong())
        )
        Assert.assertEquals(1, (c.left % 1000).toLong())
        // the next pass should graduate the card
        Assert.assertEquals(QUEUE_TYPE_LRN, c.queue)
        Assert.assertEquals(CARD_TYPE_LRN, c.type)
        col.sched.answerCard(c, BUTTON_THREE)
        Assert.assertEquals(QUEUE_TYPE_REV, c.queue)
        Assert.assertEquals(CARD_TYPE_REV, c.type)
        // should be due tomorrow, with an interval of 1
        Assert.assertEquals((col.sched.today + 1).toLong(), c.due)
        Assert.assertEquals(1, c.ivl)
        // or normal removal
        c.type = CARD_TYPE_NEW
        c.queue = QUEUE_TYPE_LRN
        c.flush()
        col.sched.answerCard(c, BUTTON_FOUR)
        Assert.assertEquals(CARD_TYPE_REV, c.type)
        Assert.assertEquals(QUEUE_TYPE_REV, c.queue)
        Assert.assertTrue(AnkiAssert.checkRevIvl(c, 4))
        // revlog should have been updated each time
        Assert.assertEquals(
            5,
            col.db.queryScalar("select count() from revlog where type = 0").toLong()
        )
    }

    @Test
    @Throws(Exception::class)
    fun test_relearn() {
        TimeManager.reset()
        val col = col
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        var c = note.cards()[0].apply {
            ivl = 100
            due = col.sched.today.toLong()
            queue = QUEUE_TYPE_REV
            type = CARD_TYPE_REV
        }
        c.flush()

        // fail the card
        c = col.sched.card!!
        col.sched.answerCard(c, BUTTON_ONE)
        Assert.assertEquals(QUEUE_TYPE_LRN, c.queue)
        Assert.assertEquals(CARD_TYPE_RELEARNING, c.type)
        Assert.assertEquals(1, c.ivl)

        // immediately graduate it
        col.sched.answerCard(c, BUTTON_FOUR)
        Assert.assertEquals(CARD_TYPE_REV, c.type)
        Assert.assertEquals(QUEUE_TYPE_REV, c.queue)
        Assert.assertEquals(2, c.ivl)
        Assert.assertEquals((col.sched.today + c.ivl).toLong(), c.due)
    }

    @Test
    @Throws(Exception::class)
    fun test_relearn_no_steps() {
        val col = col
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        var c = note.cards()[0].apply {
            ivl = 100
            due = col.sched.today.toLong()
            queue = QUEUE_TYPE_REV
            type = CARD_TYPE_REV
        }
        c.flush()
        val conf = col.decks.confForDid(1)
        conf.getJSONObject("lapse").put("delays", JSONArray(doubleArrayOf()))
        col.decks.save(conf)

        // fail the card
        c = col.sched.card!!
        col.sched.answerCard(c, BUTTON_ONE)
        Assert.assertEquals(CARD_TYPE_REV, c.type)
        Assert.assertEquals(QUEUE_TYPE_REV, c.queue)
    }

    @Test
    @Throws(Exception::class)
    fun test_learn_collapsedV2() {
        val col = col
        // add 2 notes
        var note = col.newNote()
        note.setItem("Front", "1")
        col.addNote(note)
        note = col.newNote()
        note.setItem("Front", "2")
        col.addNote(note)
        // set as a learn card and rebuild queues
        col.db.execute("update cards set queue=0, type=0")
        // should get '1' first
        var c = col.sched.card!!
        Assert.assertTrue(c.q().endsWith("1"))
        // pass it so it's due in 10 minutes
        col.sched.answerCard(c, BUTTON_THREE)
        // get the other card
        c = col.sched.card!!
        Assert.assertTrue(c.q().endsWith("2"))
        // fail it so it's due in 1 minute
        col.sched.answerCard(c, BUTTON_ONE)
        // we shouldn't get the same card again
        c = col.sched.card!!
        Assert.assertFalse(c.q().endsWith("2"))
    }

    @Test
    @Throws(Exception::class)
    fun test_learn_dayV2() {
        TimeManager.reset()
        val col = col
        // add a note
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        var c = col.sched.card!!
        var conf = col.sched._cardConf(c)
        conf.getJSONObject("new").put("delays", JSONArray(doubleArrayOf(1.0, 10.0, 1440.0, 2880.0)))
        col.decks.save(conf)
        // pass it
        col.sched.answerCard(c, BUTTON_THREE)
        // two reps to graduate, 1 more today
        Assert.assertEquals(3, (c.left % 1000).toLong())
        Assert.assertEquals(Counts(0, 1, 0), col.sched.counts())
        c = col.sched.card!!
        Assert.assertEquals(SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_THREE))
        // answering it will place it in queue 3
        col.sched.answerCard(c, BUTTON_THREE)
        Assert.assertEquals((col.sched.today + 1).toLong(), c.due)
        Assert.assertEquals(QUEUE_TYPE_DAY_LEARN_RELEARN, c.queue)
        assertNull(col.sched.card)
        // for testing, move it back a day
        c.due = c.due - 1
        c.flush()
        Assert.assertEquals(Counts(0, 1, 0), col.sched.counts())
        c = col.sched.card!!
        // nextIvl should work
        Assert.assertEquals(SECONDS_PER_DAY * 2, col.sched.nextIvl(c, BUTTON_THREE))
        // if we fail it, it should be back in the correct queue
        col.sched.answerCard(c, BUTTON_ONE)
        Assert.assertEquals(QUEUE_TYPE_LRN, c.queue)
        col.undo()
        c = col.sched.card!!
        col.sched.answerCard(c, BUTTON_THREE)
        // simulate the passing of another two days
        c.due = c.due - 2
        c.flush()
        // the last pass should graduate it into a review card
        Assert.assertEquals(SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_THREE))
        col.sched.answerCard(c, BUTTON_THREE)
        Assert.assertEquals(CARD_TYPE_REV, c.type)
        Assert.assertEquals(QUEUE_TYPE_REV, c.queue)
        // if the lapse step is tomorrow, failing it should handle the counts
        // correctly
        c.due = 0
        c.flush()
        Assert.assertEquals(Counts(0, 0, 1), col.sched.counts())
        conf = col.sched._cardConf(c)
        conf.getJSONObject("lapse").put("delays", JSONArray(doubleArrayOf(1440.0)))
        col.decks.save(conf)
        c = col.sched.card!!
        col.sched.answerCard(c, BUTTON_ONE)
        Assert.assertEquals(QUEUE_TYPE_DAY_LEARN_RELEARN, c.queue)
        Assert.assertEquals(Counts(0, 0, 0), col.sched.counts())
    }

    @Test
    @Throws(Exception::class)
    fun test_reviewsV2() {
        TimeManager.reset()
        val col = col
        // add a note
        val note = col.newNote()
        note.setItem("Front", "one")
        note.setItem("Back", "two")
        col.addNote(note)
        // set the card up as a review card, due 8 days ago
        var c = note.cards()[0].apply {
            type = CARD_TYPE_REV
            queue = QUEUE_TYPE_REV
            due = (col.sched.today - 8).toLong()
            factor = STARTING_FACTOR
            setReps(3)
            lapses = 1
            ivl = 100
        }
        c.startTimer()
        c.flush()
        // save it for later use as well
        val cardcopy = c.clone()
        // try with an ease of 2
        // //////////////////////////////////////////////////////////////////////////////////////////////////
        c = cardcopy.clone()
        c.flush()
        col.sched.answerCard(c, BUTTON_TWO)
        Assert.assertEquals(QUEUE_TYPE_REV, c.queue)
        // the new interval should be (100) * 1.2 = 120
        Assert.assertTrue(AnkiAssert.checkRevIvl(c, 120))
        Assert.assertEquals((col.sched.today + c.ivl).toLong(), c.due)
        // factor should have been decremented
        Assert.assertEquals(2350, c.factor)
        // check counters
        Assert.assertEquals(1, c.lapses)
        Assert.assertEquals(4, c.reps)
        // ease 3
        // //////////////////////////////////////////////////////////////////////////////////////////////////
        c = cardcopy.clone()
        c.flush()
        col.sched.answerCard(c, BUTTON_THREE)
        // the new interval should be (100 + 8/2) * 2.5 = 260
        Assert.assertTrue(AnkiAssert.checkRevIvl(c, 260))
        Assert.assertEquals((col.sched.today + c.ivl).toLong(), c.due)
        // factor should have been left alone
        Assert.assertEquals(STARTING_FACTOR, c.factor)
        // ease 4
        // //////////////////////////////////////////////////////////////////////////////////////////////////
        c = cardcopy.clone()
        c.flush()
        col.sched.answerCard(c, BUTTON_FOUR)
        // the new interval should be (100 + 8) * 2.5 * 1.3 = 351
        Assert.assertTrue(AnkiAssert.checkRevIvl(c, 351))
        Assert.assertEquals((col.sched.today + c.ivl).toLong(), c.due)
        // factor should have been increased
        Assert.assertEquals(2650, c.factor)
        // leech handling
        // //////////////////////////////////////////////////////////////////////////////////////////////////
        val conf = col.decks.getConf(1)
        conf.getJSONObject("lapse").put("leechAction", LEECH_SUSPEND)
        col.decks.save(conf)
        c = cardcopy.clone()
        c.lapses = 7
        c.flush()
        /* todo hook
        // steup hook
        hooked = new [] {};

        def onLeech(card):
        hooked.append(1);

        hooks.card_did_leech.append(onLeech);
        col.getSched().answerCard(c, BUTTON_ONE);
        assertTrue(hooked);
        assertEquals(QUEUE_TYPE_SUSPENDED, c.getQueue());
        c.load();
        assertEquals(QUEUE_TYPE_SUSPENDED, c.getQueue());
        */
    }

    @Test
    @Throws(Exception::class)
    fun test_button_spacingV2() {
        val col = col
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        // 1 day ivl review card due now
        val c = note.cards()[0]
        c.type = CARD_TYPE_REV
        c.queue = QUEUE_TYPE_REV
        c.due = col.sched.today.toLong()
        c.setReps(1)
        c.ivl = 1
        c.startTimer()
        c.flush()
        // Upstream, there is no space in 2d
        Assert.assertEquals(
            "2d",
            AnkiAssert.without_unicode_isolation(col.sched.nextIvlStr(c, BUTTON_TWO))
        )
        Assert.assertEquals(
            "3d",
            AnkiAssert.without_unicode_isolation(
                col.sched.nextIvlStr(
                    c,
                    BUTTON_THREE
                )
            )
        )
        Assert.assertEquals(
            "4d",
            AnkiAssert.without_unicode_isolation(
                col.sched.nextIvlStr(
                    c,
                    BUTTON_FOUR
                )
            )
        )

        // if hard factor is <= 1, then hard may not increase
        val conf = col.decks.confForDid(1)
        conf.getJSONObject("rev").put("hardFactor", 1)
        col.decks.save(conf)
        Assert.assertEquals(
            "1d",
            AnkiAssert.without_unicode_isolation(col.sched.nextIvlStr(c, BUTTON_TWO))
        )
    }

    @Test
    @Ignore("Disabled upstream")
    fun test_overdue_lapseV2() {
        // disabled in commit 3069729776990980f34c25be66410e947e9d51a2
        /* Upstream does not execute it
           Collection col = getColV2()  // pylint: disable=unreachable
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
           ;
           col.getSched().reset();
           assertEquals(new Counts(0, 2, 0), col.getSched().counts());
           c = getCard();
           col.getSched().answerCard(c, BUTTON_THREE);
           // it should be due tomorrow
           assertEquals(col.getSched().getToday()+ 1, c.getDue());
           // revert to before
           / * todo: rollback
           col.rollback();
           // with the default settings, the overdue card should be removed from the
           // learning queue
           col.getSched().reset();
           assertEquals(new Counts(0, 0, 1), col.getSched().counts());
        */
    }

    @Test
    @Throws(Exception::class)
    fun test_finishedV2() {
        val col = col
        // nothing due
        MatcherAssert.assertThat(
            col.sched.finishedMsg().toString(),
            Matchers.containsString("Congratulations")
        )
        MatcherAssert.assertThat(
            col.sched.finishedMsg().toString(),
            Matchers.not(
                Matchers.containsString("limit")
            )
        )
        val note = col.newNote()
        note.setItem("Front", "one")
        note.setItem("Back", "two")
        col.addNote(note)
        // have a new card
        MatcherAssert.assertThat(
            col.sched.finishedMsg().toString(),
            Matchers.containsString("new cards available")
        )
        // turn it into a review
        val c = note.cards()[0]
        c.startTimer()
        col.sched.answerCard(c, BUTTON_THREE)
        // nothing should be due tomorrow, as it's due in a week
        MatcherAssert.assertThat(
            col.sched.finishedMsg().toString(),
            Matchers.containsString("Congratulations")
        )
        MatcherAssert.assertThat(
            col.sched.finishedMsg().toString(),
            Matchers.not(
                Matchers.containsString("limit")
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun test_nextIvlV2() {
        val col = col
        val note = col.newNote()
        note.setItem("Front", "one")
        note.setItem("Back", "two")
        col.addNote(note)
        val conf = col.decks.confForDid(1)
        conf.getJSONObject("new").put("delays", JSONArray(doubleArrayOf(0.5, 3.0, 10.0)))
        conf.getJSONObject("lapse").put("delays", JSONArray(doubleArrayOf(1.0, 5.0, 9.0)))
        col.decks.save(conf)
        val c = col.sched.card!!
        // new cards
        // //////////////////////////////////////////////////////////////////////////////////////////////////
        Assert.assertEquals(30, col.sched.nextIvl(c, BUTTON_ONE))
        Assert.assertEquals(((30 + 180) / 2).toLong(), col.sched.nextIvl(c, BUTTON_TWO))
        Assert.assertEquals(180, col.sched.nextIvl(c, BUTTON_THREE))
        Assert.assertEquals(4 * SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_FOUR))
        col.sched.answerCard(c, BUTTON_ONE)
        // cards in learning
        // //////////////////////////////////////////////////////////////////////////////////////////////////
        Assert.assertEquals(30, col.sched.nextIvl(c, BUTTON_ONE))
        Assert.assertEquals(((30 + 180) / 2).toLong(), col.sched.nextIvl(c, BUTTON_TWO))
        Assert.assertEquals(180, col.sched.nextIvl(c, BUTTON_THREE))
        Assert.assertEquals(4 * SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_FOUR))
        col.sched.answerCard(c, BUTTON_THREE)
        Assert.assertEquals(30, col.sched.nextIvl(c, BUTTON_ONE))
        Assert.assertEquals(180, col.sched.nextIvl(c, BUTTON_TWO))
        Assert.assertEquals(600, col.sched.nextIvl(c, BUTTON_THREE))
        Assert.assertEquals(4 * SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_FOUR))
        col.sched.answerCard(c, BUTTON_THREE)
        // normal graduation is tomorrow
        Assert.assertEquals(SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_THREE))
        Assert.assertEquals(4 * SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_FOUR))
        // lapsed cards
        // //////////////////////////////////////////////////////////////////////////////////////////////////
        c.type = CARD_TYPE_RELEARNING
        c.ivl = 100
        c.factor = STARTING_FACTOR
        c.flush()
        Assert.assertEquals(60, col.sched.nextIvl(c, BUTTON_ONE))
        Assert.assertEquals(100 * SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_THREE))
        Assert.assertEquals(101 * SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_FOUR))
        // review cards
        // //////////////////////////////////////////////////////////////////////////////////////////////////
        c.type = CARD_TYPE_REV
        c.queue = QUEUE_TYPE_REV
        c.ivl = 100
        c.factor = STARTING_FACTOR
        c.flush()
        // failing it should put it at 60s
        Assert.assertEquals(60, col.sched.nextIvl(c, BUTTON_ONE))
        // or 1 day if relearn is false
        conf.getJSONObject("lapse").put("delays", JSONArray(doubleArrayOf()))
        col.decks.save(conf)
        Assert.assertEquals(SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_ONE))
        // (* 100 1.2 SECONDS_PER_DAY)10368000.0
        Assert.assertEquals(10368000, col.sched.nextIvl(c, BUTTON_TWO))
        // (* 100 2.5 SECONDS_PER_DAY)21600000.0
        Assert.assertEquals(21600000, col.sched.nextIvl(c, BUTTON_THREE))
        // (* 100 2.5 1.3 SECONDS_PER_DAY)28080000.0
        Assert.assertEquals(28080000, col.sched.nextIvl(c, BUTTON_FOUR))
        MatcherAssert.assertThat(
            AnkiAssert.without_unicode_isolation(
                col.sched.nextIvlStr(
                    c,
                    BUTTON_FOUR
                )
            ),
            Matchers.equalTo("10.8mo")
        )
    }

    @Test
    @Throws(Exception::class)
    fun test_bury() {
        val col = col
        var note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        val c = note.cards()[0]
        note = col.newNote()
        note.setItem("Front", "two")
        col.addNote(note)
        val c2 = note.cards()[0]
        // burying
        col.sched.buryCards(listOf(c.id), true)
        c.load()
        Assert.assertEquals(QUEUE_TYPE_MANUALLY_BURIED, c.queue)
        col.sched.buryCards(listOf(c2.id), false)
        c2.load()
        Assert.assertEquals(QUEUE_TYPE_SIBLING_BURIED, c2.queue)
        assertNull(col.sched.card)
        col.sched.unburyCardsForDeck(Scheduler.UnburyType.MANUAL)
        c.load()
        Assert.assertEquals(QUEUE_TYPE_NEW, c.queue)
        c2.load()
        Assert.assertEquals(QUEUE_TYPE_SIBLING_BURIED, c2.queue)
        col.sched.unburyCardsForDeck(Scheduler.UnburyType.SIBLINGS)
        c2.load()
        Assert.assertEquals(QUEUE_TYPE_NEW, c2.queue)
        col.sched.buryCards(listOf(c.id, c2.id))
        col.sched.unburyCardsForDeck(Scheduler.UnburyType.ALL)
        Assert.assertEquals(Counts(2, 0, 0), col.sched.counts())
    }

    @Test
    @Throws(Exception::class)
    fun test_suspendv2() {
        val col = col
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        var c = note.cards()[0]
        // suspending
        assertNotNull(col.sched.card)
        col.sched.suspendCards(listOf(c.id))
        assertNull(col.sched.card)
        // unsuspending
        col.sched.unsuspendCards(listOf(c.id))
        assertNotNull(col.sched.card)
        // should cope with rev cards being relearnt
        c.due = 0
        c.ivl = 100
        c.type = CARD_TYPE_REV
        c.queue = QUEUE_TYPE_REV
        c.flush()
        c = col.sched.card!!
        col.sched.answerCard(c, BUTTON_ONE)
        MatcherAssert.assertThat(
            c.due,
            Matchers.greaterThanOrEqualTo(time.intTime())
        )
        val due = c.due
        Assert.assertEquals(QUEUE_TYPE_LRN, c.queue)
        Assert.assertEquals(CARD_TYPE_RELEARNING, c.type)
        col.sched.suspendCards(listOf(c.id))
        col.sched.unsuspendCards(listOf(c.id))
        c.load()
        Assert.assertEquals(QUEUE_TYPE_LRN, c.queue)
        Assert.assertEquals(CARD_TYPE_RELEARNING, c.type)
        Assert.assertEquals(due, c.due)
        // should cope with cards in cram decks
        c.due = 1
        c.flush()
        addDynamicDeck("tmp")
        col.sched.rebuildDyn()
        c.load()
        Assert.assertNotEquals(1, c.due)
        Assert.assertNotEquals(1, c.did)
        col.sched.suspendCards(listOf(c.id))
        c.load()
        Assert.assertNotEquals(1, c.due)
        Assert.assertNotEquals(1, c.did)
        Assert.assertEquals(1, c.oDue)
    }

    @Test
    @Throws(Exception::class)
    fun test_filt_reviewing_early_normal() {
        TimeManager.reset()
        val col = col
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        var c = note.cards()[0].apply {
            ivl = 100
            queue = QUEUE_TYPE_REV
            type = CARD_TYPE_REV
            // due in 25 days, so it's been waiting 75 days
            due = (col.sched.today + 25).toLong()
            mod = 1
            factor = STARTING_FACTOR
        }

        c.startTimer()
        c.flush()
        Assert.assertEquals(Counts(0, 0, 0), col.sched.counts())
        // create a dynamic deck and refresh it
        val did = addDynamicDeck("Cram")
        col.sched.rebuildDyn(did)
        // should appear as normal in the deck list
        /* todo sort
           assertEquals(1, sorted(col.getSched().deckDueTree().getChildren())[0].review_count);
        */
        // and should appear in the counts
        Assert.assertEquals(Counts(0, 0, 1), col.sched.counts())
        // grab it and check estimates
        c = col.sched.card!!
        Assert.assertEquals(600, col.sched.nextIvl(c, BUTTON_ONE))
        Assert.assertEquals(
            (75 * 1.2).roundToInt() * SECONDS_PER_DAY,
            col.sched.nextIvl(c, BUTTON_TWO)
        )
        val toLong = fun (v: Double) = v.roundToLong() * SECONDS_PER_DAY
        MatcherAssert.assertThat(
            col.sched.nextIvl(c, BUTTON_THREE),
            equalTo(toLong(75 * 2.5))
        )
        MatcherAssert.assertThat(
            col.sched.nextIvl(c, BUTTON_FOUR),
            equalTo(toLong(75 * 2.5 * 1.15))
        )

        // answer 'good'
        col.sched.answerCard(c, BUTTON_THREE)
        AnkiAssert.checkRevIvl(c, 90)
        Assert.assertEquals((col.sched.today + c.ivl).toLong(), c.due)
        Assert.assertEquals(0L, c.oDue)
        // should not be in learning
        Assert.assertEquals(QUEUE_TYPE_REV, c.queue)
        // should be logged as a cram rep
        Assert.assertEquals(
            3,
            col.db.queryLongScalar("select type from revlog order by id desc limit 1")
        )

        // due in 75 days, so it's been waiting 25 days
        c.ivl = 100
        c.due = (col.sched.today + 75).toLong()
        c.flush()
        col.sched.rebuildDyn(did)
        c = col.sched.card!!
        Assert.assertEquals(60 * SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_TWO))
        Assert.assertEquals(100 * SECONDS_PER_DAY, col.sched.nextIvl(c, BUTTON_THREE))
        Assert.assertEquals(toLong(114.5), col.sched.nextIvl(c, BUTTON_FOUR))
    }

    @Test
    @Throws(Exception::class)
    fun test_filt_keep_lrn_state() {
        val col = col
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)

        // fail the card outside filtered deck
        val c = col.sched.card!!
        val conf = col.sched._cardConf(c)
        conf.getJSONObject("new").put("delays", JSONArray(doubleArrayOf(1.0, 10.0, 61.0)))
        col.decks.save(conf)
        col.sched.answerCard(c, BUTTON_ONE)
        Assert.assertEquals(CARD_TYPE_LRN, c.queue)
        Assert.assertEquals(QUEUE_TYPE_LRN, c.type)
        Assert.assertEquals(3, c.left % 1000)
        col.sched.answerCard(c, BUTTON_THREE)
        Assert.assertEquals(CARD_TYPE_LRN, c.queue)
        Assert.assertEquals(QUEUE_TYPE_LRN, c.type)

        // create a dynamic deck and refresh it
        val did = addDynamicDeck("Cram")
        col.sched.rebuildDyn(did)

        // card should still be in learning state
        c.load()
        Assert.assertEquals(CARD_TYPE_LRN, c.queue)
        Assert.assertEquals(QUEUE_TYPE_LRN, c.type)
        Assert.assertEquals(2, c.left % 1000)

        // should be able to advance learning steps
        col.sched.answerCard(c, BUTTON_THREE)
        // should be due at least an hour in the future
        MatcherAssert.assertThat(
            c.due - time.intTime(),
            Matchers.greaterThan(60 * 60L)
        )

        // emptying the deck preserves learning state
        col.sched.emptyDyn(did)
        c.load()
        Assert.assertEquals(CARD_TYPE_LRN, c.queue)
        Assert.assertEquals(QUEUE_TYPE_LRN, c.type)
        Assert.assertEquals(1, c.left % 1000)
        MatcherAssert.assertThat(
            c.due - time.intTime(),
            Matchers.greaterThan(60 * 60L)
        )
    }

    @Test
    @Throws(Exception::class)
    fun test_preview() {
        // add cards
        val col = col
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        var c = note.cards()[0]
        val orig = c.clone()
        val note2 = col.newNote()
        note2.setItem("Front", "two")
        col.addNote(note2)
        // cram deck
        val did = addDynamicDeck("Cram")
        val cram = col.decks.get(did)!!
        cram.put("resched", false)
        col.decks.save(cram)
        col.sched.rebuildDyn(did)
        // grab the first card
        c = col.sched.card!!
        Assert.assertEquals(600, col.sched.nextIvl(c, BUTTON_ONE))
        Assert.assertEquals(900, col.sched.nextIvl(c, BUTTON_TWO))
        // failing it will push its due time back
        val due = c.due
        col.sched.answerCard(c, BUTTON_ONE)
        Assert.assertNotEquals(c.due, due)

        // the other card should come next
        val c2 = col.sched.card!!
        Assert.assertNotEquals(c2.id, c.id)

        // passing it will remove it
        col.sched.answerCard(c2, BUTTON_FOUR)
        Assert.assertEquals(QUEUE_TYPE_NEW, c2.queue)
        Assert.assertEquals(0, c2.reps)
        Assert.assertEquals(CARD_TYPE_NEW, c2.type)

        // the other card should appear again
        c = col.sched.card!!
        Assert.assertEquals(orig.id, c.id)

        // emptying the filtered deck should restore card
        col.sched.emptyDyn(did)
        c.load()
        Assert.assertEquals(QUEUE_TYPE_NEW, c.queue)
        Assert.assertEquals(0, c.reps)
        Assert.assertEquals(CARD_TYPE_NEW, c.type)
    }

    @Test
    @Throws(Exception::class)
    fun test_ordcycleV2() {
        val col = col
        // add two more templates and set second active
        val m = col.notetypes.current()
        val mm = col.notetypes
        var t = Notetypes.newTemplate("Reverse")
        t.put("qfmt", "{{Back}}")
        t.put("afmt", "{{Front}}")
        mm.addTemplateModChanged(m, t)
        t = Notetypes.newTemplate("f2")
        t.put("qfmt", "{{Front}}1")
        t.put("afmt", "{{Back}}")
        mm.addTemplateModChanged(m, t)
        mm.save(m)
        // create a new note; it should have 3 cards
        val note = col.newNote()
        note.setItem("Front", "1")
        note.setItem("Back", "1")
        col.addNote(note)
        Assert.assertEquals(3, col.cardCount().toLong())
        // ordinals should arrive in order
        val sched = col.sched
        var c = sched.card
        sched.answerCard(
            c!!,
            3
        ) // not upstream. But we are not expecting multiple getCard without review
        Assert.assertEquals(0, c.ord)
        c = sched.card
        sched.answerCard(
            c!!,
            3
        ) // not upstream. But we are not expecting multiple getCard without review
        Assert.assertEquals(1, c.ord)
        c = sched.card
        sched.answerCard(
            c!!,
            3
        ) // not upstream. But we are not expecting multiple getCard without review
        Assert.assertEquals(2, c.ord)
    }

    @Test
    @Throws(Exception::class)
    fun test_counts_idxV3() {
        val col = col
        val note = col.newNote()
        note.setItem("Front", "one")
        note.setItem("Back", "two")
        col.addNote(note)
        val note2 = col.newNote()
        note2.setItem("Front", "one")
        note2.setItem("Back", "two")
        col.addNote(note2)
        Assert.assertEquals(Counts(2, 0, 0), col.sched.counts())
        var c = col.sched.card!!
        // getCard does not decrement counts
        Assert.assertEquals(Counts(2, 0, 0), col.sched.counts())
        Assert.assertEquals(Counts.Queue.NEW, col.sched.countIdx())
        // answer to move to learn queue
        col.sched.answerCard(c, BUTTON_ONE)
        Assert.assertEquals(Counts(1, 1, 0), col.sched.counts())
        // fetching next will not decrement the count
        Assert.assertEquals(Counts(1, 1, 0), col.sched.counts())
        Assert.assertEquals(Counts.Queue.NEW, col.sched.countIdx())
    }

    @Test
    @Throws(Exception::class)
    fun test_repCountsV2() {
        val col = col
        var note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        // lrnReps should be accurate on pass/fail
        Assert.assertEquals(Counts(1, 0, 0), col.sched.counts())
        col.sched.answerCard(col.sched.card!!, BUTTON_ONE)
        Assert.assertEquals(Counts(0, 1, 0), col.sched.counts())
        col.sched.answerCard(col.sched.card!!, BUTTON_ONE)
        Assert.assertEquals(Counts(0, 1, 0), col.sched.counts())
        col.sched.answerCard(col.sched.card!!, BUTTON_THREE)
        Assert.assertEquals(Counts(0, 1, 0), col.sched.counts())
        col.sched.answerCard(col.sched.card!!, BUTTON_ONE)
        Assert.assertEquals(Counts(0, 1, 0), col.sched.counts())
        col.sched.answerCard(col.sched.card!!, BUTTON_THREE)
        Assert.assertEquals(Counts(0, 1, 0), col.sched.counts())
        col.sched.answerCard(col.sched.card!!, BUTTON_THREE)
        Assert.assertEquals(Counts(0, 0, 0), col.sched.counts())
        note = col.newNote()
        note.setItem("Front", "two")
        col.addNote(note)
        // initial pass should be correct too
        col.sched.answerCard(col.sched.card!!, BUTTON_THREE)
        Assert.assertEquals(Counts(0, 1, 0), col.sched.counts())
        col.sched.answerCard(col.sched.card!!, BUTTON_ONE)
        Assert.assertEquals(Counts(0, 1, 0), col.sched.counts())
        col.sched.answerCard(col.sched.card!!, BUTTON_FOUR)
        Assert.assertEquals(Counts(0, 0, 0), col.sched.counts())
        // immediate graduate should work
        note = col.newNote()
        note.setItem("Front", "three")
        col.addNote(note)
        col.sched.answerCard(col.sched.card!!, BUTTON_FOUR)
        Assert.assertEquals(Counts(0, 0, 0), col.sched.counts())
        // and failing a review should too
        note = col.newNote()
        note.setItem("Front", "three")
        col.addNote(note)
        val c = note.cards()[0]
        c.type = CARD_TYPE_REV
        c.queue = QUEUE_TYPE_REV
        c.due = col.sched.today.toLong()
        c.flush()
        Assert.assertEquals(Counts(0, 0, 1), col.sched.counts())
        col.sched.answerCard(col.sched.card!!, BUTTON_ONE)
        Assert.assertEquals(Counts(0, 1, 0), col.sched.counts())
    }

    @Test
    @Throws(Exception::class)
    fun test_timingV2() {
        val col = col
        // add a few review cards, due today
        for (i in 0..4) {
            val note = col.newNote()
            note.setItem("Front", "num$i")
            col.addNote(note)
            val c = note.cards()[0]
            c.type = CARD_TYPE_REV
            c.queue = QUEUE_TYPE_REV
            c.due = 0
            c.flush()
        }
        // fail the first one
        var c = col.sched.card!!
        col.sched.answerCard(c, BUTTON_ONE)
        // the next card should be another review
        val c2 = col.sched.card!!
        Assert.assertEquals(QUEUE_TYPE_REV, c2.queue)
        // if the failed card becomes due, it should show first
        c.due = time.intTime() - 1
        c.flush()
        c = col.sched.card!!
        Assert.assertEquals(QUEUE_TYPE_LRN, c.queue)
    }

    @Test
    @Throws(Exception::class)
    fun test_collapseV2() {
        val col = col
        // add a note
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        // test collapsing
        var c = col.sched.card
        col.sched.answerCard(c!!, BUTTON_ONE)
        c = col.sched.card
        col.sched.answerCard(c!!, BUTTON_FOUR)
        assertNull(col.sched.card)
    }

    @Test
    @Throws(Exception::class)
    fun test_deckDueV2() {
        val col = col
        // add a note with default deck
        var note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        // and one that's a child
        note = col.newNote()
        note.setItem("Front", "two")
        val default1 = addDeck("Default::1")
        note.model().put("did", default1)
        col.addNote(note)
        // make it a review card
        val c = note.cards()[0]
        c.queue = QUEUE_TYPE_REV
        c.due = 0
        c.flush()
        // add one more with a new deck
        note = col.newNote()
        note.setItem("Front", "two")
        note.model().put("did", addDeck("foo::bar"))
        col.addNote(note)
        // and one that's a sibling
        note = col.newNote()
        note.setItem("Front", "three")
        note.model().put("did", addDeck("foo::baz"))
        col.addNote(note)
        Assert.assertEquals(5, col.decks.allNamesAndIds().size.toLong())
        val tree = col.sched.deckDueTree().children[0]
        Assert.assertEquals("Default", tree.lastDeckNameComponent)
        // sum of child and parent
        Assert.assertEquals(1, tree.did)
        Assert.assertEquals(1, tree.revCount.toLong())
        Assert.assertEquals(1, tree.newCount.toLong())
        // child count is just review
        val value = tree.children[0]
        Assert.assertEquals("1", value.lastDeckNameComponent)
        Assert.assertEquals(default1, value.did)
        Assert.assertEquals(1, value.revCount.toLong())
        Assert.assertEquals(0, value.newCount.toLong())
        // code should not fail if a card has an invalid deck
        c.did = 12345
        c.flush()
        col.sched.deckDueTree()
    }

    @Test
    @Throws(Exception::class)
    fun test_deckTree() {
        val col = col
        addDeck("new::b::c")
        addDeck("new2")
        // new should not appear twice in tree
        val names: MutableList<String> = ArrayList()
        for (value in col.sched.deckDueTree().children) {
            names.add(value.lastDeckNameComponent)
        }
        names.remove("new")
        Assert.assertFalse(names.contains("new"))
    }

    @Test
    @Throws(Exception::class)
    fun test_deckFlowV2() {
        val col = col
        // add a note with default deck
        var note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        // and one that's a child
        note = col.newNote()
        note.setItem("Front", "two")
        var default1 = addDeck("Default::2")
        note.model().put("did", default1)
        col.addNote(note)
        // and another that's higher up
        note = col.newNote()
        note.setItem("Front", "three")
        default1 = addDeck("Default::1")
        note.model().put("did", default1)
        col.addNote(note)
        // should get top level one first, then ::1, then ::2
        Assert.assertEquals(Counts(3, 0, 0), col.sched.counts())
        for (i in arrayOf("one", "three", "two")) {
            val c = col.sched.card!!
            Assert.assertEquals(i, c.note().getItem("Front"))
            col.sched.answerCard(c, BUTTON_THREE)
        }
    }

    @Test
    @Throws(Exception::class)
    fun test_reorder() {
        val col = col
        // add a note with default deck
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        val note2 = col.newNote()
        note2.setItem("Front", "two")
        col.addNote(note2)
        Assert.assertEquals(2, note2.cards()[0].due)
        var found = false
        // 50/50 chance of being reordered
        for (i in 0..19) {
            col.sched.randomizeCards(1)
            if (note.cards()[0].due != note.id) {
                found = true
                break
            }
        }
        Assert.assertTrue(found)
        col.sched.orderCards(1)
        Assert.assertEquals(1, note.cards()[0].due)
        // shifting
        val note3 = col.newNote()
        note3.setItem("Front", "three")
        col.addNote(note3)
        val note4 = col.newNote()
        note4.setItem("Front", "four")
        col.addNote(note4)
        Assert.assertEquals(1, note.cards()[0].due)
        Assert.assertEquals(2, note2.cards()[0].due)
        Assert.assertEquals(3, note3.cards()[0].due)
        Assert.assertEquals(4, note4.cards()[0].due)
        /* todo: start
           col.getSched().sortCards(new long [] {note3.cards().get(0).getId(), note4.cards().get(0).getId()}, start=1, shift=true);
           assertEquals(3, note.cards().get(0).getDue());
           assertEquals(4, note2.cards().get(0).getDue());
           assertEquals(1, note3.cards().get(0).getDue());
           assertEquals(2, note4.cards().get(0).getDue());
        */
    }

    @Test
    @Throws(Exception::class)
    fun test_forgetV2() {
        val col = col
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        val c = note.cards()[0]
        c.queue = QUEUE_TYPE_REV
        c.type = CARD_TYPE_REV
        c.ivl = 100
        c.due = 0
        c.flush()
        Assert.assertEquals(Counts(0, 0, 1), col.sched.counts())
        col.sched.forgetCards(listOf(c.id))
        Assert.assertEquals(Counts(1, 0, 0), col.sched.counts())
    }

    @Test
    @Throws(Exception::class)
    fun test_reschedV2() {
        TimeManager.reset()
        val col = col
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        val c = note.cards()[0]
        col.sched.reschedCards(listOf(c.id), 0, 0)
        c.load()
        Assert.assertEquals(col.sched.today.toLong(), c.due)
        Assert.assertEquals(1, c.ivl)
        Assert.assertEquals(QUEUE_TYPE_REV, c.type)
        Assert.assertEquals(CARD_TYPE_REV, c.queue)
        col.sched.reschedCards(listOf(c.id), 1, 1)
        c.load()
        Assert.assertEquals((col.sched.today + 1).toLong(), c.due)
        Assert.assertEquals(+1, c.ivl)
    }

    @Test
    @Throws(Exception::class)
    fun test_norelearnV2() {
        val col = col
        // add a note
        val note = col.newNote()
        note.setItem("Front", "one")
        col.addNote(note)
        val c = note.cards()[0]
        c.type = CARD_TYPE_REV
        c.queue = QUEUE_TYPE_REV
        c.due = 0
        c.factor = STARTING_FACTOR
        c.setReps(3)
        c.lapses = 1
        c.ivl = 100
        c.startTimer()
        c.flush()
        col.sched.answerCard(c, BUTTON_ONE)
        col.sched._cardConf(c).getJSONObject("lapse").put("delays", JSONArray(doubleArrayOf()))
        col.sched.answerCard(c, BUTTON_ONE)
    }

    @Test
    @Throws(Exception::class)
    fun test_failmultV2() {
        val col = col
        val note = col.newNote()
        note.setItem("Front", "one")
        note.setItem("Back", "two")
        col.addNote(note)
        var c = note.cards()[0]
        c.type = CARD_TYPE_REV
        c.queue = QUEUE_TYPE_REV
        c.ivl = 100
        c.due = (col.sched.today - c.ivl).toLong()
        c.factor = STARTING_FACTOR
        c.setReps(3)
        c.lapses = 1
        c.startTimer()
        c.flush()
        val conf = col.sched._cardConf(c)
        conf.getJSONObject("lapse").put("mult", 0.5)
        col.decks.save(conf)
        c = col.sched.card!!
        col.sched.answerCard(c, BUTTON_ONE)
        Assert.assertEquals(50, c.ivl)
        col.sched.answerCard(c, BUTTON_ONE)
        Assert.assertEquals(25, c.ivl)
    }

    // cards with a due date earlier than the collection should retain
    // their due date when removed
    @Test
    @Throws(Exception::class)
    fun test_negativeDueFilter() {
        val col = col

        // card due prior to collection date
        val note = col.newNote()
        note.setItem("Front", "one")
        note.setItem("Back", "two")
        col.addNote(note)
        val c = note.cards()[0]
        c.due = -5
        c.queue = QUEUE_TYPE_REV
        c.ivl = 5
        c.flush()

        // into and out of filtered deck
        val did = addDynamicDeck("Cram")
        col.sched.rebuildDyn(did)
        col.sched.emptyDyn(did)
        c.load()
        Assert.assertEquals(-5, c.due)
    }

    // hard on the first step should be the average of again and good,
    // and it should be logged properly
    @Test
    @Ignore("Port anki@a9c93d933cadbf5d9c7e3e2b4f7a25d2c59da5d3")
    @Throws(
        Exception::class
    )
    fun test_initial_repeat() {
        val col = col
        val note = col.newNote()
        note.setItem("Front", "one")
        note.setItem("Back", "two")
        col.addNote(note)
        val c = col.sched.card!!
        col.sched.answerCard(c, BUTTON_TWO)
        // should be due in ~ 5.5 mins
        val expected = time.intTime() + (5.5 * 60).toInt()
        val due = c.due
        MatcherAssert.assertThat(expected - 10, Matchers.lessThan(due))
        MatcherAssert.assertThat(
            due,
            Matchers.lessThanOrEqualTo((expected * 1.25).toLong())
        )
        val ivl = col.db.queryLongScalar("select ivl from revlog")
        Assert.assertEquals((-5.5 * 60).toLong(), ivl)
    }

    @Test
    @Throws(Exception::class)
    fun regression_test_preview() {
        // "https://github.com/ankidroid/Anki-Android/issues/7285"
        val col = col
        val decks = col.decks
        val sched = col.sched
        addNoteUsingBasicModel("foo", "bar")
        val did = addDynamicDeck("test")
        val deck = decks.get(did)!!
        deck.put("resched", false)
        sched.rebuildDyn(did)
        var card: Card?
        for (i in 0..2) {
            card = sched.card
            assertNotNull(card)
            sched.answerCard(card, BUTTON_ONE)
        }
        Assert.assertEquals(1, sched.lrnCount().toLong())
        card = sched.card
        Assert.assertEquals(1, sched.counts().lrn.toLong())
        sched.answerCard(card!!, BUTTON_ONE)
        AnkiAssert.assertDoesNotThrow { col.undo() }
    }
}
