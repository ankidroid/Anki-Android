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
import com.ichi2.anki.AbstractFlashcardViewer.Companion.RESULT_DEFAULT
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.anki.preferences.PreferenceUtils
import com.ichi2.anki.reviewer.ActionButtonStatus
import com.ichi2.libanki.Card
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Model
import com.ichi2.libanki.ModelManager
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.testutils.Flaky
import com.ichi2.testutils.MockTime
import com.ichi2.testutils.OS
import com.ichi2.utils.deepClone
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.json.JSONArray
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import timber.log.Timber
import kotlin.test.assertFailsWith
import kotlin.test.junit5.JUnit5Asserter.assertNotNull

@RunWith(ParameterizedRobolectricTestRunner::class)
class ReviewerTest : RobolectricTest() {
    @JvmField // required for Parameter
    @ParameterizedRobolectricTestRunner.Parameter
    var schedVersion = 0

    @Before
    override fun setUp() {
        super.setUp()
        try {
            Timber.d("scheduler version is %d", schedVersion)
            col.changeSchedulerVer(schedVersion)
        } catch (e: ConfirmModSchemaException) {
            throw RuntimeException("Could not change schedVer", e)
        }
    }

    @Test
    fun verifyStartupNoCollection() {
        enableNullCollection()
        ActivityScenario.launch(Reviewer::class.java).use { scenario -> scenario.onActivity { reviewer: Reviewer -> assertFailsWith<Exception> { reviewer.col } } }
    }

    @Test
    @RunInBackground
    fun verifyNormalStartup() {
        ActivityScenario.launch(Reviewer::class.java).use { scenario -> scenario.onActivity { reviewer: Reviewer -> assertNotNull("Collection should be non-null", reviewer.col) } }
    }

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
        moveToReviewQueue(firstNote.firstCard(col))

        val reviewer = startReviewer()
        reviewer.generateQuestionSoundList()
        reviewer.displayCardQuestion()

        assertThat("If the sound file with given name is not present, then no error occurs", true)
    }

    @Test
    fun jsTime4ShouldBeBlankIfButtonUnavailable() {
        // #6623 - easy should be blank when displaying a card with 3 buttons (after displaying a review)
        val firstNote = addNoteUsingBasicModel("Hello", "World")
        moveToReviewQueue(firstNote.firstCard(col))

        addNoteUsingBasicModel("Hello", "World2")

        val reviewer = startReviewer()
        val javaScriptFunction = reviewer.javaScriptFunction()

        // The answer needs to be displayed to be able to get the time.
        displayAnswer(reviewer)
        assertThat("4 buttons should be displayed", reviewer.answerButtonCount, equalTo(4))

        val nextTime = javaScriptFunction.ankiGetNextTime4()
        assertThat(nextTime, not(emptyString()))

        // Display the next answer
        reviewer.answerCard(Consts.BUTTON_FOUR)

        displayAnswer(reviewer)

        if (schedVersion == 1) {
            assertThat("The 4th button should not be visible", reviewer.answerButtonCount, equalTo(3))
            val learnTime = javaScriptFunction.ankiGetNextTime4()
            assertThat("If the 4th button is not visible, there should be no time4 in JS", learnTime, emptyString())
        }
    }

    @Test
    fun nothingAppearsInAppBarIfAllAppBarButtonsAreDisabled() {
        disableAllReviewerAppBarButtons()

        val reviewer = startReviewer(ReviewerForMenuItems::class.java)

        val visibleButtons: List<String> = reviewer.getVisibleButtonNames()

        assertThat("No menu items should be visible if all are disabled in Settings - Reviewer - App Bar Buttons", visibleButtons, empty())
    }

    @Test
    fun onlyDisableWhiteboardAppearsInAppBarIfAllAppBarButtonsAreDisabledWithWhiteboard() {
        disableAllReviewerAppBarButtons()

        val reviewer = startReviewer(ReviewerForMenuItems::class.java)

        toggleWhiteboard(reviewer)

        val visibleButtons = reviewer.getVisibleButtonNamesExcept(R.id.action_toggle_whiteboard)

        assertThat("No menu items should be visible if all are disabled in Settings - Reviewer - App Bar Buttons", visibleButtons, empty())
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
            addRevNoteUsingBasicModelDueToday("1", "bar").firstCard(col),
            addNoteUsingBasicModel("2", "bar").firstCard(col),
            addNoteUsingBasicModel("3", "bar").firstCard(col)
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
        reviewer.answerCard(col.sched.goodNewButton)
        waitForAsyncTasksToComplete()

        equalFirstField(cards[2], reviewer.currentCard!!)
        time.addM(2)
        reviewer.answerCard(col.sched.goodNewButton)
        advanceRobolectricLooperWithSleep()
        equalFirstField(cards[0], reviewer.currentCard!!) // This failed in #6898 because this card was not in the queue
    }

    @Test
    @Flaky(os = OS.WINDOWS, "startReviewer: NullPointerException - baseDeckName")
    fun baseDeckName() {
        val models = col.models

        val decks = col.decks
        val didAb = addDeck("A::B")
        val basic = models.byName(col, AnkiDroidApp.appResources.getString(R.string.basic_model_name))
        basic!!.put("did", didAb)
        addNoteUsingBasicModel("foo", "bar")
        val didA = addDeck("A")
        decks.select(didA)
        val reviewer = startReviewer()
        waitForAsyncTasksToComplete()
        assertThat(reviewer.supportActionBar!!.title, equalTo("B"))
    }

    @Test
    fun jsAnkiGetDeckName() {
        val models = col.models
        val decks = col.decks

        val didAb = addDeck("A::B")
        val basic = models.byName(col, AnkiDroidApp.appResources.getString(R.string.basic_model_name))
        basic!!.put("did", didAb)
        addNoteUsingBasicModel("foo", "bar")

        val didA = addDeck("A")
        decks.select(didA)

        val reviewer = startReviewer()
        val javaScriptFunction = reviewer.javaScriptFunction()

        waitForAsyncTasksToComplete()
        assertThat(javaScriptFunction.ankiGetDeckName(), equalTo("B"))
    }

    private fun toggleWhiteboard(reviewer: ReviewerForMenuItems) {
        reviewer.toggleWhiteboard()

        assumeTrue("Whiteboard should now be enabled", reviewer.prefWhiteboard)

        advanceRobolectricLooperWithSleep()
    }

    private fun disableAllReviewerAppBarButtons() {
        val keys = PreferenceUtils.getAllCustomButtonKeys(targetContext)

        val preferences = AnkiDroidApp.getSharedPrefs(targetContext)

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

    private suspend fun undo(reviewer: Reviewer) {
        reviewer.undo()
    }

    @Suppress("SameParameterValue")
    private fun assertCounts(r: Reviewer, newCount: Int, stepCount: Int, revCount: Int) {
        val jsApi = r.javaScriptFunction()
        val countList = listOf(
            jsApi.ankiGetNewCardCount(),
            jsApi.ankiGetLrnCardCount(),
            jsApi.ankiGetRevCardCount()
        )

        val expected = listOf(
            newCount,
            stepCount,
            revCount
        )

        assertThat(countList.toString(), equalTo(expected.toString())) // We use toString as hamcrest does not print the whole array and stops at [0].
    }

    private fun answerCardOrdinalAsGood(r: Reviewer, i: Int) {
        assertCurrentOrdIs(r, i)

        r.answerCard(col.sched.goodNewButton)

        waitForAsyncTasksToComplete()
    }

    private fun assertCurrentOrdIs(r: Reviewer, i: Int) {
        waitForAsyncTasksToComplete()
        val ord = r.currentCard!!.ord

        assertThat("Unexpected card ord", ord + 1, equalTo(i))
    }

    @Throws(ConfirmModSchemaException::class)
    private fun addNoteWithThreeCards() {
        val models = col.models
        var m: Model? = models.copy(col, models.current(col)!!)
        m!!.put("name", "Three")
        models.add(col, m)
        m = models.byName(col, "Three")
        models.flush(col)
        cloneTemplate(models, m, "1")
        cloneTemplate(models, m, "2")

        val newNote = col.newNote()
        newNote.setField(0, "Hello")
        assertThat(newNote.model()["name"], equalTo("Three"))

        assertThat(col.addNote(newNote), equalTo(3))
    }

    @Throws(ConfirmModSchemaException::class)
    private fun cloneTemplate(models: ModelManager, m: Model?, extra: String) {
        val tmpls = m!!.getJSONArray("tmpls")
        val defaultTemplate = tmpls.getJSONObject(0)

        val newTemplate = defaultTemplate.deepClone()
        newTemplate.put("ord", tmpls.length())

        val cardName = targetContext.getString(R.string.card_n_name, tmpls.length() + 1)
        newTemplate.put("name", cardName)
        newTemplate.put("qfmt", newTemplate.getString("qfmt") + extra)

        models.addTemplate(col, m, newTemplate)
    }

    private fun displayAnswer(reviewer: Reviewer) {
        waitForAsyncTasksToComplete()
        reviewer.displayCardAnswer(col)
        waitForAsyncTasksToComplete()
    }

    private fun startReviewer(): Reviewer {
        return startReviewer(this)
    }

    private fun <T : Reviewer?> startReviewer(clazz: Class<T>): T {
        return startReviewer(this, clazz)
    }

    private fun moveToReviewQueue(reviewCard: Card) {
        reviewCard.queue = Consts.QUEUE_TYPE_REV
        reviewCard.type = Consts.CARD_TYPE_REV
        reviewCard.due = 0
        reviewCard.flush(col)
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
        @JvmStatic // required for initParameters
        @ParameterizedRobolectricTestRunner.Parameters(name = "SchedV{0}")
        fun initParameters(): Collection<Array<Any>> {
            // This does one run with schedVersion injected as 1, and one run as 2
            return listOf(arrayOf(1), arrayOf(2))
        }

        fun startReviewer(testClass: RobolectricTest): Reviewer {
            return startReviewer(testClass, Reviewer::class.java)
        }

        fun <T : Reviewer?> startReviewer(testClass: RobolectricTest, clazz: Class<T>): T {
            val reviewer = startActivityNormallyOpenCollectionWithIntent(testClass, clazz, Intent())
            waitForAsyncTasksToComplete()
            return reviewer
        }
    }
}
