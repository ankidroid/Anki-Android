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
    fun initTest() {
        val models = col.notetypes
        val decks = col.decks
        val didA = addDeck("Test")
        val basic = models.byName(AnkiDroidApp.appResources.getString(R.string.basic_model_name))
        basic!!.put("did", didA)
        addNoteUsingBasicModel("foo", "bar")
        decks.select(didA)

        val reviewer: Reviewer = startReviewer()
        val javaScriptFunction = reviewer.javaScriptFunction()

        val data = JSONObject()
        data.put("version", "0.0.1")
        data.put("developer", "dev@mail.com")

        // this will be changed when new api added
        // TODO - make this test to auto add api from list
        val expected = "{\"setCardDue\":true,\"suspendNote\":true,\"markCard\":true,\"suspendCard\":true,\"buryCard\":true,\"toggleFlag\":true,\"buryNote\":true}"

        waitForAsyncTasksToComplete()
        assertThat(javaScriptFunction.init(data.toString()), equalTo(expected))
    }

    @Test
    fun ankiGetNextTimeTest() {
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

        assertThat(javaScriptFunction.ankiGetNextTime1(), equalTo("< 1 min"))
        assertThat(javaScriptFunction.ankiGetNextTime2(), equalTo("< 6 min"))
        assertThat(javaScriptFunction.ankiGetNextTime3(), equalTo("< 10 min"))
        assertThat(javaScriptFunction.ankiGetNextTime4(), equalTo("4 d"))
    }

    @Test
    fun ankiTestCurrentCard() {
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
        assertThat(javaScriptFunction.ankiGetCardDid(), equalTo(currentCard.did))
        // Card Id
        assertThat(javaScriptFunction.ankiGetCardId(), equalTo(currentCard.id))
        // Card Nid
        assertThat(javaScriptFunction.ankiGetCardNid(), equalTo(currentCard.nid))
        // Card ODid
        assertThat(javaScriptFunction.ankiGetCardODid(), equalTo(currentCard.oDid))
        // Card Type
        assertThat(javaScriptFunction.ankiGetCardType(), equalTo(currentCard.type))
        // Card ODue
        assertThat(javaScriptFunction.ankiGetCardODue(), equalTo(currentCard.oDue))
        // Card Due
        assertThat(javaScriptFunction.ankiGetCardDue(), equalTo(currentCard.due))
        // Card Factor
        assertThat(javaScriptFunction.ankiGetCardFactor(), equalTo(currentCard.factor))
        // Card Lapses
        assertThat(javaScriptFunction.ankiGetCardLapses(), equalTo(currentCard.lapses))
        // Card Ivl
        assertThat(javaScriptFunction.ankiGetCardInterval(), equalTo(currentCard.ivl))
        // Card mod
        assertThat(javaScriptFunction.ankiGetCardMod(), equalTo(currentCard.mod))
        // Card Queue
        assertThat(javaScriptFunction.ankiGetCardQueue(), equalTo(currentCard.queue))
        // Card Reps
        assertThat(javaScriptFunction.ankiGetCardReps(), equalTo(currentCard.reps))
        // Card left
        assertThat(javaScriptFunction.ankiGetCardLeft(), equalTo(currentCard.left))

        // Card Flag
        assertThat(javaScriptFunction.ankiGetCardFlag(), equalTo(0))
        reviewer.currentCard!!.setFlag(1)
        assertThat(javaScriptFunction.ankiGetCardFlag(), equalTo(1))

        // Card Mark
        assertThat(javaScriptFunction.ankiGetCardMark(), equalTo(false))
        reviewer.currentCard!!.note().addTag("marked")
        assertThat(javaScriptFunction.ankiGetCardMark(), equalTo(true))
    }

    @Test
    fun ankiJsUiTest() {
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
        assertThat(javaScriptFunction.ankiIsDisplayingAnswer(), equalTo(reviewer.isDisplayingAnswer))
        reviewer.displayCardAnswer()
        assertThat(javaScriptFunction.ankiIsDisplayingAnswer(), equalTo(reviewer.isDisplayingAnswer))

        // Full Screen
        assertThat(javaScriptFunction.ankiIsInFullscreen(), equalTo(reviewer.isFullscreen))
        // Top bar
        assertThat(javaScriptFunction.ankiIsTopbarShown(), equalTo(reviewer.prefShowTopbar))
        // Night Mode
        assertThat(javaScriptFunction.ankiIsInNightMode(), equalTo(reviewer.isInNightMode))
    }

    @Test
    fun ankiMarkAndFlagCardTest() {
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
        assertThat(javaScriptFunction.ankiGetCardMark(), equalTo(false))

        // call javascript function defined in card.js to mark card
        var markCardJs = "javascript:(function () {\n"

        // add js api developer contract
        markCardJs += "var jsApi = {\"version\" : \"0.0.1\", \"developer\" : \"dev@mail.com\"};\n"

        // init JS API
        markCardJs += "AnkiDroidJS.init(JSON.stringify(jsApi));\n"

        // call function defined in card.js to mark card
        markCardJs += "ankiMarkCard();\n"

        // get card mark status for test
        markCardJs += "AnkiDroidJS.ankiGetCardMark();\n" +
            "})();"

        reviewer.webView!!.evaluateJavascript(markCardJs) { s -> assertThat(s, equalTo(true)) }

        // ---------------
        // Card flag test
        // ---------------
        // before toggling flag
        assertThat(javaScriptFunction.ankiGetCardFlag(), equalTo(0))

        // call javascript function defined in card.js to toggle flag
        var flagCardJs = "javascript:(function () {\n"

        // add js api developer contract
        flagCardJs += "var jsApi = {\"version\" : \"0.0.1\", \"developer\" : \"test@example.com\"};\n"

        // init JS API
        flagCardJs += "AnkiDroidJS.init(JSON.stringify(jsApi));\n"

        // call function defined in card.js to flag card to red
        flagCardJs += "ankiToggleFlag(\"red\");\n"

        // get flag status for test
        flagCardJs += "AnkiDroidJS.ankiGetCardFlag();\n" +
            "})();"

        reviewer.webView!!.evaluateJavascript(flagCardJs) { s -> assertThat(s, equalTo(1)) }
    }

    fun ankiBurySuspendTest() {
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

        waitForAsyncTasksToComplete()

        // ----------
        // Bury Card
        // ----------
        var jsScript = createTestScript("AnkiDroidJS.ankiBuryCard();")
        // call script to bury current card
        reviewer.webView!!.evaluateJavascript(jsScript) { s -> assertThat(s, equalTo(true)) }

        // count number of notes
        assertThat(reviewer.sched!!.cardCount(), equalTo(4))

        // ----------
        // Bury Note
        // ----------
        jsScript = createTestScript("AnkiDroidJS.ankiBuryNote();")
        // call script to bury current note
        reviewer.webView!!.evaluateJavascript(jsScript) { s -> assertThat(s, equalTo(true)) }

        // count number of notes
        assertThat(reviewer.sched!!.cardCount(), equalTo(3))

        // -------------
        // Suspend Card
        // -------------
        jsScript = createTestScript("AnkiDroidJS.ankiSuspendCard();")
        // call script to suspend current card
        reviewer.webView!!.evaluateJavascript(jsScript) { s -> assertThat(s, equalTo(true)) }

        // count number of notes
        assertThat(reviewer.sched!!.cardCount(), equalTo(2))

        // -------------
        // Suspend Note
        // -------------
        jsScript = createTestScript("AnkiDroidJS.ankiSuspendNote();")
        // call script to suspend current note
        reviewer.webView!!.evaluateJavascript(jsScript) { s -> assertThat(s, equalTo(true)) }

        // count number of notes
        assertThat(reviewer.sched!!.cardCount(), equalTo(1))
    }

    private fun createTestScript(apiName: String): String {
        // create js script for evaluating in webview
        var script = "javascript:(function () {\n"

        // add js api developer contract
        script += "var jsApi = {\"version\" : \"0.0.1\", \"developer\" : \"test@example.com\"};\n"

        // init JS API
        script += "AnkiDroidJS.init(JSON.stringify(jsApi));\n"

        // call js api
        script += "$apiName\n})();"

        return script
    }

    private fun startReviewer(): Reviewer {
        return ReviewerTest.startReviewer(this)
    }

    @Test
    fun ankiSetCardDueTest() = runTest {
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
        javaScriptFunction.init(initJsApiContract())
        // get card id for testing due
        val cardId = javaScriptFunction.ankiGetCardId()

        // test that card rescheduled for 15 days interval and returned true
        assertTrue("Card rescheduled, so returns true", javaScriptFunction.ankiSetCardDue(15))
        waitForAsyncTasksToComplete()

        // verify that it did get rescheduled
        // --------------------------------
        val cardAfterRescheduleCards = col.getCard(cardId)
        assertEquals("Card is rescheduled", 15, cardAfterRescheduleCards.due)
    }

    private fun initJsApiContract(): String {
        val data = JSONObject()
        data.put("version", "0.0.1")
        data.put("developer", "test@example.com")
        return data.toString()
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
        javaScriptFunction.init(initJsApiContract())
        // get card id for testing due
        val cardId = javaScriptFunction.ankiGetCardId()

        // test that card reset
        assertTrue("Card progress reset", javaScriptFunction.ankiResetProgress())
        waitForAsyncTasksToComplete()

        // verify that card progress reset
        // --------------------------------
        val cardAfterReset = col.getCard(cardId)
        assertEquals("Card due after reset", 1, cardAfterReset.due)
        assertEquals("Card interval after reset", 0, cardAfterReset.ivl)
        assertEquals("Card ease after reset", 2500, cardAfterReset.factor)
        assertEquals("Card type after reset", Consts.CARD_TYPE_NEW, cardAfterReset.type)
    }
}
