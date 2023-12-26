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
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnkiDroidJsAPITest : RobolectricTest() {

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
        val jsapi = reviewer.jsApi

        reviewer.displayCardAnswer()

        waitForAsyncTasksToComplete()

        assertThat(
            getDataFromRequest("nextTime1", jsapi).withoutUnicodeIsolation(),
            equalTo(formatApiResult("<1m"))
        )
        assertThat(
            getDataFromRequest("nextTime2", jsapi).withoutUnicodeIsolation(),
            equalTo(formatApiResult("<6m"))
        )
        assertThat(
            getDataFromRequest("nextTime3", jsapi).withoutUnicodeIsolation(),
            equalTo(formatApiResult("<10m"))
        )
        assertThat(
            getDataFromRequest("nextTime4", jsapi).withoutUnicodeIsolation(),
            equalTo(formatApiResult("4d"))
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
        val jsapi = reviewer.jsApi
        reviewer.displayCardAnswer()

        waitForAsyncTasksToComplete()

        val currentCard = reviewer.currentCard!!

        // Card Did
        assertThat(
            getDataFromRequest("cardDid", jsapi),
            equalTo(formatApiResult(currentCard.did))
        )
        // Card Id
        assertThat(
            getDataFromRequest("cardId", jsapi),
            equalTo(formatApiResult(currentCard.id))
        )
        // Card Nid
        assertThat(
            getDataFromRequest("cardNid", jsapi),
            equalTo(formatApiResult(currentCard.nid))
        )
        // Card ODid
        assertThat(
            getDataFromRequest("cardODid", jsapi),
            equalTo(formatApiResult(currentCard.oDid))
        )
        // Card Type
        assertThat(
            getDataFromRequest("cardType", jsapi),
            equalTo(formatApiResult(currentCard.type))
        )
        // Card ODue
        assertThat(
            getDataFromRequest("cardODue", jsapi),
            equalTo(formatApiResult(currentCard.oDue))
        )
        // Card Due
        assertThat(
            getDataFromRequest("cardDue", jsapi),
            equalTo(formatApiResult(currentCard.due))
        )
        // Card Factor
        assertThat(
            getDataFromRequest("cardFactor", jsapi),
            equalTo(formatApiResult(currentCard.factor))
        )
        // Card Lapses
        assertThat(
            getDataFromRequest("cardLapses", jsapi),
            equalTo(formatApiResult(currentCard.lapses))
        )
        // Card Ivl
        assertThat(
            getDataFromRequest("cardInterval", jsapi),
            equalTo(formatApiResult(currentCard.ivl))
        )
        // Card mod
        assertThat(
            getDataFromRequest("cardMod", jsapi),
            equalTo(formatApiResult(currentCard.mod))
        )
        // Card Queue
        assertThat(
            getDataFromRequest("cardQueue", jsapi),
            equalTo(formatApiResult(currentCard.queue))
        )
        // Card Reps
        assertThat(
            getDataFromRequest("cardReps", jsapi),
            equalTo(formatApiResult(currentCard.reps))
        )
        // Card left
        assertThat(
            getDataFromRequest("cardLeft", jsapi),
            equalTo(formatApiResult(currentCard.left))
        )

        // Card Flag
        assertThat(
            getDataFromRequest("cardFlag", jsapi),
            equalTo(formatApiResult(0))
        )
        reviewer.currentCard!!.setFlag(1)
        assertThat(
            getDataFromRequest("cardFlag", jsapi),
            equalTo(formatApiResult(1))
        )

        // Card Mark
        assertThat(
            getDataFromRequest("cardMark", jsapi),
            equalTo(formatApiResult(false))
        )
        reviewer.currentCard!!.note().addTag("marked")
        assertThat(
            getDataFromRequest("cardMark", jsapi),
            equalTo(formatApiResult(true))
        )
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
        val jsapi = reviewer.jsApi

        waitForAsyncTasksToComplete()

        // Displaying question
        assertThat(
            getDataFromRequest("isDisplayingAnswer", jsapi),
            equalTo(formatApiResult(reviewer.isDisplayingAnswer))
        )
        reviewer.displayCardAnswer()
        assertThat(
            getDataFromRequest("isDisplayingAnswer", jsapi),
            equalTo(formatApiResult(reviewer.isDisplayingAnswer))
        )

        // Full Screen
        assertThat(
            getDataFromRequest("isInFullscreen", jsapi),
            equalTo(formatApiResult(reviewer.isFullscreen))
        )
        // Top bar
        assertThat(
            getDataFromRequest("isTopbarShown", jsapi),
            equalTo(formatApiResult(reviewer.prefShowTopbar))
        )
        // Night Mode
        assertThat(
            getDataFromRequest("isInNightMode", jsapi),
            equalTo(formatApiResult(reviewer.isInNightMode))
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
        val jsapi = reviewer.jsApi

        waitForAsyncTasksToComplete()

        // ---------------
        // Card mark test
        // ---------------
        // Before marking card
        assertThat(
            getDataFromRequest("cardMark", jsapi),
            equalTo(formatApiResult(false))
        )

        // Mark card
        assertThat(
            getDataFromRequest("markCard", jsapi, "true"),
            equalTo(formatApiResult(true))
        )

        // After marking card
        assertThat(
            getDataFromRequest("cardMark", jsapi),
            equalTo(formatApiResult(true))
        )

        // ---------------
        // Card flag test
        // ---------------
        // before toggling flag
        assertThat(
            getDataFromRequest("cardFlag", jsapi),
            equalTo(formatApiResult(0))
        )

        // call javascript function to toggle flag
        assertThat(
            getDataFromRequest("toggleFlag", jsapi, "red"),
            equalTo(formatApiResult(true))
        )

        // after toggling flag
        assertThat(
            getDataFromRequest("cardFlag", jsapi),
            equalTo(formatApiResult(1))
        )
    }

    @Ignore("the test need to be updated")
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
        val jsapi = reviewer.jsApi

        // ----------
        // Bury Card
        // ----------
        // call script to bury current card
        assertThat(
            getDataFromRequest("buryCard", jsapi),
            equalTo(formatApiResult(true))
        )

        // count number of notes
        val sched = reviewer.getColUnsafe
        assertThat(sched.cardCount(), equalTo(4))

        // ----------
        // Bury Note
        // ----------
        // call script to bury current note
        assertThat(
            getDataFromRequest("buryNote", jsapi),
            equalTo(formatApiResult(true))
        )

        // count number of notes
        assertThat(sched.cardCount(), equalTo(3))

        // -------------
        // Suspend Card
        // -------------
        // call script to suspend current card
        assertThat(
            getDataFromRequest("suspendCard", jsapi),
            equalTo(formatApiResult(true))
        )

        // count number of notes
        assertThat(sched.cardCount(), equalTo(2))

        // -------------
        // Suspend Note
        // -------------
        // call script to suspend current note
        assertThat(
            getDataFromRequest("suspendNote", jsapi),
            equalTo(formatApiResult(true))
        )

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

        val jsapi = reviewer.jsApi
        // get card id for testing due
        val cardIdRes = getDataFromRequest("cardId", jsapi)
        val jsonObject = JSONObject(cardIdRes)
        val cardId = jsonObject.get("value").toString().toLong()

        // test that card rescheduled for 15 days interval and returned true
        assertThat(getDataFromRequest("setCardDue", jsapi, "15"), equalTo(formatApiResult(true)))
        waitForAsyncTasksToComplete()

        // verify that it did get rescheduled
        // --------------------------------
        val cardToBeReschedule = col.getCard(cardId)
        assertEquals("Card is rescheduled", 15L + col.sched.today, cardToBeReschedule.due)
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

        val jsapi = reviewer.jsApi

        // test that card reset
        assertThat(getDataFromRequest("resetProgress", jsapi), equalTo(formatApiResult(true)))
        waitForAsyncTasksToComplete()

        // verify that card progress reset
        // --------------------------------
        val cardAfterReset = col.getCard(reviewer.currentCard!!.id)
        assertEquals("Card due after reset", 2, cardAfterReset.due)
        assertEquals("Card interval after reset", 0, cardAfterReset.ivl)
        assertEquals("Card type after reset", Consts.CARD_TYPE_NEW, cardAfterReset.type)
    }

    companion object {
        fun jsApiContract(data: String = ""): ByteArray {
            return JSONObject().apply {
                put("version", "0.0.2")
                put("developer", "test@example.com")
                put("data", data)
            }.toString().toByteArray()
        }

        fun formatApiResult(res: Any): String {
            return "{\"success\":true,\"value\":\"$res\"}"
        }

        suspend fun getDataFromRequest(
            methodName: String,
            jsAPI: AnkiDroidJsAPI,
            apiData: String = ""
        ): String {
            return jsAPI.handleJsApiRequest(methodName, jsApiContract(apiData), true)
                .decodeToString()
        }
    }
}
