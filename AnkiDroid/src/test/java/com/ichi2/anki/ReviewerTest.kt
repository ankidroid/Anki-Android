/*
 *  Copyright (c) 2021 Mike Hardy <github@mikehardy.net>
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
package com.ichi2.anki

import android.content.Intent
import android.view.Menu
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AbstractFlashcardViewer.Companion.RESULT_DEFAULT
import com.ichi2.anki.AnkiDroidJsAPITest.Companion.formatApiResult
import com.ichi2.anki.AnkiDroidJsAPITest.Companion.getDataFromRequest
import com.ichi2.anki.AnkiDroidJsAPITest.Companion.jsApiContract
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.cardviewer.ViewerCommand.FLIP_OR_ANSWER_EASE1
import com.ichi2.anki.cardviewer.ViewerCommand.MARK
import com.ichi2.anki.preferences.PreferenceTestUtils
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.reviewer.ActionButtonStatus
import com.ichi2.libanki.Card
import com.ichi2.libanki.Consts
import com.ichi2.libanki.NotetypeJson
import com.ichi2.libanki.Notetypes
import com.ichi2.libanki.exception.ConfirmModSchemaException
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.testutils.Flaky
import com.ichi2.testutils.MockTime
import com.ichi2.testutils.OS
import com.ichi2.utils.deepClone
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.json.JSONArray
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith
import kotlin.test.junit5.JUnit5Asserter.assertNotNull

@RunWith(AndroidJUnit4::class)
class ReviewerTest : RobolectricTest() {
    @Test
    fun verifyStartupNoCollection() {
        enableNullCollection()
        ActivityScenario.launch(Reviewer::class.java)
            .use { scenario -> scenario.onActivity { reviewer: Reviewer -> assertFailsWith<Exception> { reviewer.getColUnsafe } } }
    }

    @Ignore("flaky")
    @Test
    @RunInBackground
    fun verifyNormalStartup() {
        ActivityScenario.launch(Reviewer::class.java).use { scenario ->
            scenario.onActivity { reviewer: Reviewer ->
                assertNotNull(
                    "Collection should be non-null",
                    reviewer.getColUnsafe
                )
            }
        }
    }

    @Ignore("flaky")
    @Test
    @RunInBackground
    @Flaky(os = OS.WINDOWS, "startUp: BackendCollectionAlreadyOpenException")
    fun exitCommandWorksAfterControlsAreBlocked() {
        ensureCollectionLoadIsSynchronous()
        ActivityScenario.launchActivityForResult(Reviewer::class.java).use { scenario ->
            scenario.onActivity { reviewer: Reviewer ->
                reviewer.blockControls(true)
                reviewer.executeCommand(ViewerCommand.EXIT)
            }
            assertThat(scenario.result.resultCode, equalTo(RESULT_DEFAULT))
        }
    }

    @Test
    fun noErrorShouldOccurIfSoundFileNotPresent() {
        val firstNote = addNoteUsingBasicModel("[[sound:not_on_file_system.mp3]]", "World")
        moveToReviewQueue(firstNote.firstCard())

        val reviewer = startReviewer()
        reviewer.generateQuestionSoundList()
        reviewer.displayCardQuestion()

        assertThat("If the sound file with given name is not present, then no error occurs", true)
    }

    @Test
    @Flaky(os = OS.WINDOWS, "Issue 14308")
    fun nothingAppearsInAppBarIfAllAppBarButtonsAreDisabled() {
        disableAllReviewerAppBarButtons()

        val reviewer = startReviewer(ReviewerForMenuItems::class.java)

        val visibleButtons: List<String> = reviewer.getVisibleButtonNames()

        assertThat(
            "No menu items should be visible if all are disabled in Settings - Reviewer - App Bar Buttons",
            visibleButtons,
            empty()
        )
    }

    @Test
    fun onlyDisableWhiteboardAppearsInAppBarIfAllAppBarButtonsAreDisabledWithWhiteboard() {
        disableAllReviewerAppBarButtons()

        val reviewer = startReviewer(ReviewerForMenuItems::class.java)

        toggleWhiteboard(reviewer)

        val visibleButtons = reviewer.getVisibleButtonNamesExcept(R.id.action_toggle_whiteboard)

        assertThat(
            "No menu items should be visible if all are disabled in Settings - Reviewer - App Bar Buttons",
            visibleButtons,
            empty()
        )
    }

    @Test
    @Synchronized
    @Throws(ConfirmModSchemaException::class)
    fun testMultipleCards() = runTest {
        addNoteWithThreeCards()
        val nw = col.decks.confForDid(1).getJSONObject("new")
        val time = collectionTime
        nw.put("delays", JSONArray(intArrayOf(1, 10, 60, 120)))

        waitForAsyncTasksToComplete()

        val reviewer = startReviewer()

        waitForAsyncTasksToComplete()

        assertCounts(reviewer, 3, 0, 0)
        answerCardOrdinalAsGood(reviewer, 1) // card 1 is shown
        time.addM(3) // card get scheduler in [10, 12.5] minutes
        // We wait 3 minutes to ensure card 2 is scheduled after card 1
        answerCardOrdinalAsGood(reviewer, 2) // card 2 is shown
        time.addM(3) // Same as above
        answerCardOrdinalAsGood(reviewer, 3) // card 3 is shown

        undo(reviewer)
        assertCurrentOrdIs(reviewer, 3)

        answerCardOrdinalAsGood(reviewer, 3) // card 3 is shown

        assertCurrentOrdIsNot(reviewer, 3) // Anki Desktop shows "1"
    }

    @Test
    fun testLrnQueueAfterUndo() = runTest {
        val nw = col.decks.confForDid(1).getJSONObject("new")
        val time = TimeManager.time as MockTime
        nw.put("delays", JSONArray(intArrayOf(1, 10, 60, 120)))

        val cards = arrayOf(
            addRevNoteUsingBasicModelDueToday("1", "bar").firstCard(),
            addNoteUsingBasicModel("2", "bar").firstCard(),
            addNoteUsingBasicModel("3", "bar").firstCard()
        )
        waitForAsyncTasksToComplete()

        val reviewer = startReviewer()

        waitForAsyncTasksToComplete()

        equalFirstField(cards[0], reviewer.currentCard!!)
        reviewer.answerCard(Consts.BUTTON_ONE)
        waitForAsyncTasksToComplete()

        equalFirstField(cards[1], reviewer.currentCard!!)
        reviewer.answerCard(Consts.BUTTON_ONE)
        waitForAsyncTasksToComplete()

        undo(reviewer)
        waitForAsyncTasksToComplete()

        equalFirstField(cards[1], reviewer.currentCard!!)
        reviewer.answerCard(Consts.BUTTON_THREE)
        waitForAsyncTasksToComplete()

        equalFirstField(cards[2], reviewer.currentCard!!)
        time.addM(2)
        reviewer.answerCard(Consts.BUTTON_THREE)
        advanceRobolectricLooperWithSleep()
        equalFirstField(
            cards[0],
            reviewer.currentCard!!
        ) // This failed in #6898 because this card was not in the queue
    }

    @Test
    fun jsAnkiGetDeckName() = runTest {
        val models = col.notetypes
        val decks = col.decks

        val didAb = addDeck("A::B")
        val basic = models.byName(AnkiDroidApp.appResources.getString(R.string.basic_model_name))
        basic!!.put("did", didAb)
        addNoteUsingBasicModel("foo", "bar")

        val didA = addDeck("A")
        decks.select(didA)

        val reviewer = startReviewer()
        val javaScriptFunction = reviewer.javaScriptFunction()

        waitForAsyncTasksToComplete()
        assertThat(
            javaScriptFunction.handleJsApiRequest("deckName", jsApiContract(), true)
                .decodeToString(),
            equalTo(formatApiResult("B"))
        )
    }

    @Ignore("needs update for v3")
    @Test
    @Throws(InterruptedException::class)
    fun testUndoResetsCardCountsToCorrectValue() = runTest {
        val reviewer = startReviewer()

        waitForAsyncTasksToComplete()

        // #6587
        addNoteUsingBasicModel("Hello", "World")

        val col = col
        val sched = col.sched

        val cardBeforeUndo = sched.card
        val countsBeforeUndo = sched.counts()

        sched.answerCard(cardBeforeUndo!!, Consts.BUTTON_THREE)

        reviewer.undoAndShowSnackbar()

        val countsAfterUndo = sched.counts()

        assertThat(
            "Counts after an undo should be the same as before an undo",
            countsAfterUndo,
            equalTo(countsBeforeUndo)
        )
    }

    @Test
    fun `A card is not flipped after 'mark' Issue 14656`() = runTest {
        startReviewer(withCards = 1).apply {
            executeCommand(FLIP_OR_ANSWER_EASE1)
            assertThat("card is showing answer", isDisplayingAnswer)
            executeCommand(MARK)
            assertThat("card is showing answer after mark", isDisplayingAnswer)
        }
    }

    @Test
    fun `Marking a card is undone by marking again`() = runTest {
        startReviewer(withCards = 1).apply {
            assertThat("card is not marked before action", !isDisplayingMark)
            executeCommand(MARK)
            assertThat("card is marked after action", isDisplayingMark)
            executeCommand(MARK)
            assertThat("marking a card twice disables the mark", !isDisplayingMark)
        }
    }

    private fun toggleWhiteboard(reviewer: ReviewerForMenuItems) {
        reviewer.toggleWhiteboard()

        assumeTrue("Whiteboard should now be enabled", reviewer.prefWhiteboard)

        advanceRobolectricLooperWithSleep()
    }

    private fun disableAllReviewerAppBarButtons() {
        val keys = PreferenceTestUtils.getAllCustomButtonKeys(targetContext)

        val preferences = targetContext.sharedPrefs()

        preferences.edit {
            for (k in keys) {
                putString(k, ActionButtonStatus.MENU_DISABLED.toString())
            }
        }
    }

    private fun assertCurrentOrdIsNot(r: Reviewer, @Suppress("SameParameterValue") i: Int) {
        waitForAsyncTasksToComplete()
        val ord = r.currentCard!!.ord

        assertThat("Unexpected card ord", ord + 1, not(equalTo(i)))
    }

    private fun undo(reviewer: Reviewer) {
        reviewer.undo()
    }

    @Suppress("SameParameterValue")
    private fun assertCounts(r: Reviewer, newCount: Int, stepCount: Int, revCount: Int) = runTest {
        val jsApi = r.javaScriptFunction()
        val countList = listOf(
            getDataFromRequest("newCardCount", jsApi),
            getDataFromRequest("lrnCardCount", jsApi),
            getDataFromRequest("revCardCount", jsApi)
        )
        val expected = listOf(
            formatApiResult(newCount),
            formatApiResult(stepCount),
            formatApiResult(revCount)
        )
        assertThat(
            countList.toString(),
            equalTo(expected.toString())
        ) // We use toString as hamcrest does not print the whole array and stops at [0].
    }

    private fun answerCardOrdinalAsGood(r: Reviewer, i: Int) {
        assertCurrentOrdIs(r, i)

        r.answerCard(Consts.BUTTON_THREE)

        waitForAsyncTasksToComplete()
    }

    private fun assertCurrentOrdIs(r: Reviewer, i: Int) {
        waitForAsyncTasksToComplete()
        val ord = r.currentCard!!.ord

        assertThat("Unexpected card ord", ord + 1, equalTo(i))
    }

    @Throws(ConfirmModSchemaException::class)
    private fun addNoteWithThreeCards() {
        val models = col.notetypes
        var m: NotetypeJson? = models.copy(models.current())
        m!!.put("name", "Three")
        models.add(m)
        m = models.byName("Three")

        cloneTemplate(models, m, "1")
        cloneTemplate(models, m, "2")

        val newNote = col.newNote()
        newNote.setField(0, "Hello")
        assertThat(newNote.notetype["name"], equalTo("Three"))

        assertThat(col.addNote(newNote), equalTo(3))
    }

    @Throws(ConfirmModSchemaException::class)
    private fun cloneTemplate(notetypes: Notetypes, m: NotetypeJson?, extra: String) {
        val tmpls = m!!.getJSONArray("tmpls")
        val defaultTemplate = tmpls.getJSONObject(0)

        val newTemplate = defaultTemplate.deepClone()
        newTemplate.put("ord", tmpls.length())

        val cardName = targetContext.getString(R.string.card_n_name, tmpls.length() + 1)
        newTemplate.put("name", cardName)
        newTemplate.put("qfmt", newTemplate.getString("qfmt") + extra)

        notetypes.addTemplate(m, newTemplate)
    }

    private fun startReviewer(withCards: Int = 0): Reviewer {
        for (i in 0 until withCards) {
            addNoteUsingBasicModel()
        }
        return startReviewer(this)
    }

    private fun <T : Reviewer?> startReviewer(clazz: Class<T>): T {
        return startReviewer(this, clazz)
    }

    private fun moveToReviewQueue(reviewCard: Card) {
        reviewCard.queue = Consts.QUEUE_TYPE_REV
        reviewCard.type = Consts.CARD_TYPE_REV
        reviewCard.due = 0
        reviewCard.col.updateCard(reviewCard, skipUndoEntry = true)
    }

    private class ReviewerForMenuItems : Reviewer() {
        var menu: Menu? = null
            private set

        override fun onCreateOptionsMenu(menu: Menu): Boolean {
            this.menu = menu
            return super.onCreateOptionsMenu(menu)
        }

        fun getVisibleButtonNames(): List<String> {
            return getVisibleButtonNamesExcept()
        }

        fun getVisibleButtonNamesExcept(vararg doNotReturn: Int): List<String> {
            val visibleButtons = arrayListOf<String>()
            val toSkip = hashSetOf(*doNotReturn.toTypedArray())
            val menu = menu
            for (i in 0 until menu!!.size()) {
                val item = menu.getItem(i)
                if (toSkip.contains(item.itemId)) {
                    continue
                }
                if (item.isVisible) {
                    visibleButtons.add(item.title.toString())
                }
            }
            return visibleButtons
        }
    }

    companion object {
        fun startReviewer(testClass: RobolectricTest): Reviewer {
            return startReviewer(testClass, Reviewer::class.java)
        }

        fun <T : Reviewer?> startReviewer(testClass: RobolectricTest, clazz: Class<T>): T =
            startActivityNormallyOpenCollectionWithIntent(testClass, clazz, Intent())
    }
}

val Reviewer.isDisplayingMark: Boolean get() = this.mCardMarker!!.isDisplayingMark
