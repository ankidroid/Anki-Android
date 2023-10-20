/****************************************************************************************
 * Copyright (c) 2020 Mani infinyte01@gmail.com                                         *
 *                                                                                      *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                            *
 *                                                                                      *
 * *************************************************************************************/

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.libanki.Consts
import com.ichi2.libanki.utils.TimeManager
import net.ankiweb.rsdroid.withoutUnicodeIsolation
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnkiDroidJsAPITest : RobolectricTest() {

    @Test
    fun initTest() = runTest {
        val models = col.notetypes
        val decks = col.decks
        val didA = addDeck("Test")
        val basic = models.byName(AnkiDroidApp.appResources.getString(R.string.basic_model_name))
        basic!!.put("did", didA)
        addNoteUsingBasicModel("foo", "bar")
        decks.select(didA)

        val reviewer: Reviewer = startReviewer()
        val javaScriptFunction = reviewer.javaScriptFunction()

        // this will be changed when new api added
        // TODO - make this test to auto add api from list
        val expected =
            "{\"setCardDue\":true,\"suspendNote\":true,\"markCard\":true,\"suspendCard\":true,\"buryCard\":true,\"toggleFlag\":true,\"buryNote\":true}"

        waitForAsyncTasksToComplete()
        assertThat(javaScriptFunction.init(jsApiContract()).decodeToString(), equalTo(expected))
    }

    @Test
    fun ankiGetNextTimeTest() = runTest {
        val models = col.notetypes
        val decks = col.decks
        val didA = addDeck("Test")
        val basic = models.byName(AnkiDroidApp.appResources.getString(R.string.basic_model_name))
        basic!!.put("did", didA)
        addNoteUsingBasicModel("foo", "bar")
        decks.select(didA)

        val reviewer: Reviewer = startReviewer()
        val javaScriptFunction = reviewer.javaScriptFunction()

        reviewer.displayCardAnswer()

        waitForAsyncTasksToComplete()

        assertThat(
            javaScriptFunction.ankiGetNextTime1().decodeToString().withoutUnicodeIsolation(),
            equalTo("<1m")
        )
        assertThat(
            javaScriptFunction.ankiGetNextTime2().decodeToString().withoutUnicodeIsolation(),
            equalTo("<6m")
        )
        assertThat(
            javaScriptFunction.ankiGetNextTime3().decodeToString().withoutUnicodeIsolation(),
            equalTo("<10m")
        )
        assertThat(
            javaScriptFunction.ankiGetNextTime4().decodeToString().withoutUnicodeIsolation(),
            equalTo("4d")
        )
    }

    @Test
    fun ankiTestCurrentCard() = runTest {
        val models = col.notetypes
        val decks = col.decks
        val didA = addDeck("Test")
        val basic = models.byName(AnkiDroidApp.appResources.getString(R.string.basic_model_name))
        basic!!.put("did", didA)
        addNoteUsingBasicModel("foo", "bar")
        decks.select(didA)

        val reviewer: Reviewer = startReviewer()
        val javaScriptFunction = reviewer.javaScriptFunction()
        reviewer.displayCardAnswer()

        waitForAsyncTasksToComplete()

        val currentCard = reviewer.currentCard!!

        // Card Did
        assertThat(
            javaScriptFunction.ankiGetCardDid().decodeToString().toLong(),
            equalTo(currentCard.did)
        )
        // Card Id
        assertThat(
            javaScriptFunction.ankiGetCardId().decodeToString().toLong(),
            equalTo(currentCard.id)
        )
        // Card Nid
        assertThat(
            javaScriptFunction.ankiGetCardNid().decodeToString().toLong(),
            equalTo(currentCard.nid)
        )
        // Card ODid
        assertThat(
            javaScriptFunction.ankiGetCardODid().decodeToString().toLong(),
            equalTo(currentCard.oDid)
        )
        // Card Type
        assertThat(
            javaScriptFunction.ankiGetCardType().decodeToString().toInt(),
            equalTo(currentCard.type)
        )
        // Card ODue
        assertThat(
            javaScriptFunction.ankiGetCardODue().decodeToString().toLong(),
            equalTo(currentCard.oDue)
        )
        // Card Due
        assertThat(
            javaScriptFunction.ankiGetCardDue().decodeToString().toLong(),
            equalTo(currentCard.due)
        )
        // Card Factor
        assertThat(
            javaScriptFunction.ankiGetCardFactor().decodeToString().toInt(),
            equalTo(currentCard.factor)
        )
        // Card Lapses
        assertThat(
            javaScriptFunction.ankiGetCardLapses().decodeToString().toInt(),
            equalTo(currentCard.lapses)
        )
        // Card Ivl
        assertThat(
            javaScriptFunction.ankiGetCardInterval().decodeToString().toInt(),
            equalTo(currentCard.ivl)
        )
        // Card mod
        assertThat(
            javaScriptFunction.ankiGetCardMod().decodeToString().toLong(),
            equalTo(currentCard.mod)
        )
        // Card Queue
        assertThat(
            javaScriptFunction.ankiGetCardQueue().decodeToString().toInt(),
            equalTo(currentCard.queue)
        )
        // Card Reps
        assertThat(
            javaScriptFunction.ankiGetCardReps().decodeToString().toInt(),
            equalTo(currentCard.reps)
        )
        // Card left
        assertThat(
            javaScriptFunction.ankiGetCardLeft().decodeToString().toInt(),
            equalTo(currentCard.left)
        )

        // Card Flag
        assertThat(javaScriptFunction.ankiGetCardFlag().decodeToString().toInt(), equalTo(0))
        reviewer.currentCard!!.setFlag(1)
        assertThat(javaScriptFunction.ankiGetCardFlag().decodeToString().toInt(), equalTo(1))

        // Card Mark
        assertThat(
            javaScriptFunction.ankiGetCardMark().decodeToString().toBoolean(),
            equalTo(false)
        )
        reviewer.currentCard!!.note().addTag("marked")
        assertThat(javaScriptFunction.ankiGetCardMark().decodeToString().toBoolean(), equalTo(true))
    }

    @Test
    fun ankiJsUiTest() = runTest {
        val models = col.notetypes
        val decks = col.decks
        val didA = addDeck("Test")
        val basic = models.byName(AnkiDroidApp.appResources.getString(R.string.basic_model_name))
        basic!!.put("did", didA)
        addNoteUsingBasicModel("foo", "bar")
        decks.select(didA)

        val reviewer: Reviewer = startReviewer()
        val javaScriptFunction = reviewer.javaScriptFunction()

        waitForAsyncTasksToComplete()

        // Displaying question
        assertThat(
            javaScriptFunction.ankiIsDisplayingAnswer().decodeToString().toBoolean(),
            equalTo(reviewer.isDisplayingAnswer)
        )
        reviewer.displayCardAnswer()
        assertThat(
            javaScriptFunction.ankiIsDisplayingAnswer().decodeToString().toBoolean(),
            equalTo(reviewer.isDisplayingAnswer)
        )

        // Full Screen
        assertThat(
            javaScriptFunction.ankiIsInFullscreen().decodeToString().toBoolean(),
            equalTo(reviewer.isFullscreen)
        )
        // Top bar
        assertThat(
            javaScriptFunction.ankiIsTopbarShown().decodeToString().toBoolean(),
            equalTo(reviewer.prefShowTopbar)
        )
        // Night Mode
        assertThat(
            javaScriptFunction.ankiIsInNightMode().decodeToString().toBoolean(),
            equalTo(reviewer.isInNightMode)
        )
    }

    @Test
    fun ankiMarkAndFlagCardTest() = runTest {
        // js api test for marking and flagging card
        val models = col.notetypes
        val decks = col.decks
        val didA = addDeck("Test")
        val basic = models.byName(AnkiDroidApp.appResources.getString(R.string.basic_model_name))
        basic!!.put("did", didA)
        addNoteUsingBasicModel("foo", "bar")
        decks.select(didA)

        val reviewer: Reviewer = startReviewer()
        val javaScriptFunction = reviewer.javaScriptFunction()

        waitForAsyncTasksToComplete()

        // ---------------
        // Card mark test
        // ---------------
        // Before marking card
        assertThat(
            javaScriptFunction.ankiGetCardMark().decodeToString().toBoolean(),
            equalTo(false)
        )

        // get card mark status for test
        javaScriptFunction.ankiMarkCard(jsApiContract())
        assertThat(javaScriptFunction.ankiGetCardMark().decodeToString().toBoolean(), equalTo(true))

        // ---------------
        // Card flag test
        // ---------------
        // before toggling flag
        assertThat(javaScriptFunction.ankiGetCardFlag().decodeToString().toInt(), equalTo(0))

        // call javascript function defined in card.js to toggle flag
        javaScriptFunction.ankiToggleFlag(jsApiContract("red"))
        assertThat(javaScriptFunction.ankiGetCardFlag().decodeToString().toInt(), equalTo(1))
    }

    // TODO - update test
    fun ankiBurySuspendTest() = runTest {
        // js api test for bury and suspend notes and cards
        // add five notes, four will be buried and suspended
        // count number of notes, if buried or suspended then
        // in scheduling the count will be less than previous scheduling
        val models = col.notetypes
        val decks = col.decks
        val didA = addDeck("Test")
        val basic = models.byName(AnkiDroidApp.appResources.getString(R.string.basic_model_name))
        basic!!.put("did", didA)
        addNoteUsingBasicModel("foo", "bar")
        addNoteUsingBasicModel("baz", "bak")
        addNoteUsingBasicModel("Anki", "Droid")
        addNoteUsingBasicModel("Test Card", "Bury and Suspend Card")
        addNoteUsingBasicModel("Test Note", "Bury and Suspend Note")
        decks.select(didA)

        val reviewer: Reviewer = startReviewer()
        val javaScriptFunction = reviewer.javaScriptFunction()
        // init js api
        javaScriptFunction.init(jsApiContract())
        waitForAsyncTasksToComplete()

        // ----------
        // Bury Card
        // ----------
        // call script to bury current card
        javaScriptFunction.ankiBuryCard(jsApiContract())
        waitForAsyncTasksToComplete()

        // count number of notes
        val sched = reviewer.getColUnsafe
        assertThat(sched.cardCount(), equalTo(4))

        // ----------
        // Bury Note
        // ----------
        // call script to bury current note
        javaScriptFunction.ankiBuryNote(jsApiContract())

        // count number of notes
        assertThat(sched.cardCount(), equalTo(3))

        // -------------
        // Suspend Card
        // -------------
        // call script to suspend current card
        javaScriptFunction.ankiSuspendCard(jsApiContract())

        // count number of notes
        assertThat(sched.cardCount(), equalTo(2))

        // -------------
        // Suspend Note
        // -------------
        // call script to suspend current note
        javaScriptFunction.ankiSuspendNote(jsApiContract())

        // count number of notes
        assertThat(sched.cardCount(), equalTo(1))
    }

    private fun startReviewer(): Reviewer {
        return ReviewerTest.startReviewer(this)
    }

    @Test
    fun ankiSetCardDueTest() = runTest {
        TimeManager.reset()
        val models = col.notetypes
        val decks = col.decks
        val didA = addDeck("Test")
        val basic = models.byName(AnkiDroidApp.appResources.getString(R.string.basic_model_name))
        basic!!.put("did", didA)
        addNoteUsingBasicModel("foo", "bar")
        addNoteUsingBasicModel("baz", "bak")
        decks.select(didA)

        val reviewer: Reviewer = startReviewer()
        waitForAsyncTasksToComplete()

        val javaScriptFunction = reviewer.javaScriptFunction()
        // init js api
        javaScriptFunction.init(jsApiContract())
        // get card id for testing due
        val cardId = javaScriptFunction.ankiGetCardId().decodeToString().toLong()

        // test that card rescheduled for 15 days interval and returned true
        assertTrue(
            "Card rescheduled, so returns true",
            javaScriptFunction.ankiSetCardDue(jsApiContract("15")).decodeToString().toBoolean()
        )
        waitForAsyncTasksToComplete()

        // verify that it did get rescheduled
        // --------------------------------
        val cardAfterRescheduleCards = col.getCard(cardId)
        assertEquals("Card is rescheduled", 15L + col.sched.today, cardAfterRescheduleCards.due)
    }

    private fun jsApiContract(data: String = ""): ByteArray {
        val jsonObject = JSONObject()
        jsonObject.put("version", "0.0.2")
        jsonObject.put("developer", "test@example.com")
        jsonObject.put("data", data)
        return jsonObject.toString().toByteArray()
    }

    @Test
    fun ankiResetProgressTest() = runTest {
        val n = addNoteUsingBasicModel("Front", "Back")
        val c = n.firstCard()

        // Make card review with 28L due and 280% ease
        c.type = Consts.CARD_TYPE_REV
        c.due = 28L
        c.factor = 2800
        c.ivl = 8

        // before reset
        assertEquals("Card due before reset", 28L, c.due)
        assertEquals("Card interval before reset", 8, c.ivl)
        assertEquals("Card ease before reset", 2800, c.factor)
        assertEquals("Card type before reset", Consts.CARD_TYPE_REV, c.type)

        val reviewer: Reviewer = startReviewer()
        waitForAsyncTasksToComplete()

        val javaScriptFunction = reviewer.javaScriptFunction()
        // init js api
        javaScriptFunction.init(jsApiContract())
        // get card id for testing due
        val cardId = javaScriptFunction.ankiGetCardId().decodeToString().toLong()

        // test that card reset
        assertTrue(
            "Card progress reset",
            javaScriptFunction.ankiResetProgress(jsApiContract()).decodeToString().toBoolean()
        )
        waitForAsyncTasksToComplete()

        // verify that card progress reset
        // --------------------------------
        val cardAfterReset = col.getCard(cardId)
        assertEquals("Card due after reset", 2, cardAfterReset.due)
        assertEquals("Card interval after reset", 0, cardAfterReset.ivl)
        assertEquals("Card type after reset", Consts.CARD_TYPE_NEW, cardAfterReset.type)
    }
}
