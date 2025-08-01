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

import android.app.Application
import android.content.Intent
import android.view.Menu
import androidx.annotation.CheckResult
import androidx.core.content.edit
import androidx.core.os.BundleCompat
import androidx.core.view.iterator
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.AnkiDroidJsAPITest.Companion.formatApiResult
import com.ichi2.anki.AnkiDroidJsAPITest.Companion.getDataFromRequest
import com.ichi2.anki.AnkiDroidJsAPITest.Companion.jsApiContract
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.ViewerCommand.FLIP_OR_ANSWER_EASE1
import com.ichi2.anki.cardviewer.ViewerCommand.MARK
import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardType
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.Notetypes
import com.ichi2.anki.libanki.QueueType
import com.ichi2.anki.libanki.exception.ConfirmModSchemaException
import com.ichi2.anki.libanki.testutils.ext.BASIC_NOTE_TYPE_NAME
import com.ichi2.anki.libanki.testutils.ext.addNote
import com.ichi2.anki.libanki.testutils.ext.newNote
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.preferences.PreferenceTestUtils
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.reviewer.ActionButtonStatus
import com.ichi2.testutils.common.Flaky
import com.ichi2.testutils.common.OS
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.json.JSONArray
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import timber.log.Timber
import kotlin.test.junit5.JUnit5Asserter.assertNotNull

@RunWith(AndroidJUnit4::class)
class ReviewerTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Ignore("flaky")
    @Test
    fun verifyNormalStartup() {
        ActivityScenario.launch(Reviewer::class.java).use { scenario ->
            scenario.onActivity { reviewer: Reviewer ->
                assertNotNull(
                    "Collection should be non-null",
                    reviewer.getColUnsafe,
                )
            }
        }
    }

    @Test
    fun testOnSelectedTags() {
        // Add a note using basic model
        addBasicNote()

        // Start the Reviewer activity
        val viewer = startRegularActivity<Reviewer>()

        // Create a list of tags
        val tags = listOf("tag1", "tag2")

        // Define an arbitrary filter
        val arbitraryFilter = CardStateFilter.DUE

        // Assert that currentCard is not null before calling onSelectedTags
        assertNotNull("currentCard should not be null", viewer.currentCard)
        assertTrue(!viewer.isDisplayingAnswer)

        Timber.d("Before first call to onSelectedTags")

        // Call onSelectedTags method
        viewer.onSelectedTags(tags, emptyList(), arbitraryFilter)

        Timber.d("After first call to onSelectedTags")

        // Assert that the card is not flipped
        assertFalse(viewer.isDisplayingAnswer)

        Timber.d("Before second call to onSelectedTags")

        // Call onSelectedTags method again
        viewer.onSelectedTags(tags, emptyList(), arbitraryFilter)

        Timber.d("After second call to onSelectedTags")

        // Assert that the card is not flipped
        assertTrue(!viewer.isDisplayingAnswer)
    }

    @Test
    fun testAddNoteAnimation() {
        // Arrange
        val reviewer = startRegularActivity<Reviewer>()
        val fromGesture = Gesture.SWIPE_DOWN

        // Act
        reviewer.addNote(fromGesture)

        // Assert
        val shadowApplication = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
        val intent = shadowApplication.nextStartedActivity
        val fragmentBundle = intent.getBundleExtra(NoteEditorActivity.FRAGMENT_ARGS_EXTRA)
        val actualAnimation =
            BundleCompat.getParcelable(
                fragmentBundle!!,
                AnkiActivity.FINISH_ANIMATION_EXTRA,
                ActivityTransitionAnimation.Direction::class.java,
            )
        val expectedAnimation =
            ActivityTransitionAnimation.getInverseTransition(
                AbstractFlashcardViewer.getAnimationTransitionFromGesture(fromGesture),
            )

        assertEquals("Animation from swipe should be inverse to the finishing one", expectedAnimation, actualAnimation)
    }

    @Test
    fun noErrorShouldOccurIfSoundFileNotPresent() {
        addBasicNote("[[sound:not_on_file_system.mp3]]", "World")
            .firstCard()
            .moveToReviewQueue()

        val reviewer = startReviewer()
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
            empty(),
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
            empty(),
        )
    }

    @Test
    @Synchronized
    @Throws(ConfirmModSchemaException::class)
    @Flaky(OS.ALL, "java.lang.AssertionError: Unexpected card ord Expected: <2> but: was <1>")
    fun testMultipleCards() =
        runTest {
            addNoteWithThreeCards()
            val new = defaultDeckConfig.new
            val time = collectionTime
            new.delays = JSONArray(intArrayOf(1, 10, 60, 120))

            advanceRobolectricLooper()

            val reviewer = startReviewer()

            advanceRobolectricLooper()

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
    @Flaky(OS.ALL, "java.lang.AssertionError: Expected: \"2\" but: was \"1\"")
    fun testLrnQueueAfterUndo() =
        runTest {
            val new = defaultDeckConfig.new
            val time = TimeManager.time as MockTime
            new.delays = JSONArray(intArrayOf(1, 10, 60, 120))

            val cards =
                arrayOf(
                    addBasicNote("1", "bar").firstCard(),
                    addBasicNote("2", "bar").firstCard(),
                    addBasicNote("3", "bar").firstCard(),
                )
            advanceRobolectricLooper()

            val reviewer = startReviewer()

            advanceRobolectricLooper()

            equalFirstField(cards[0], reviewer.currentCard!!)
            reviewer.answerCard(Rating.AGAIN)
            advanceRobolectricLooper()

            equalFirstField(cards[1], reviewer.currentCard!!)
            reviewer.answerCard(Rating.AGAIN)
            advanceRobolectricLooper()

            undo(reviewer)
            advanceRobolectricLooper()

            equalFirstField(cards[1], reviewer.currentCard!!)
            reviewer.answerCard(Rating.GOOD)
            advanceRobolectricLooper()

            equalFirstField(cards[2], reviewer.currentCard!!)
            time.addM(2)
            reviewer.answerCard(Rating.GOOD)
            advanceRobolectricLooper()
            equalFirstField(
                cards[0],
                reviewer.currentCard!!,
            ) // This failed in #6898 because this card was not in the queue
        }

    @Test
    fun jsAnkiGetDeckName() =
        runTest {
            val didAb = addDeck("A::B")
            val basic = col.notetypes.byName(BASIC_NOTE_TYPE_NAME)
            basic!!.did = didAb
            addBasicNote("foo", "bar")

            addDeck("A", setAsSelected = true)

            val reviewer = startReviewer()
            val jsApi = reviewer.jsApi

            advanceRobolectricLooper()
            assertThat(
                jsApi
                    .handleJsApiRequest("deckName", jsApiContract(), false)
                    .decodeToString(),
                equalTo(formatApiResult("B")),
            )
        }

    @Ignore("needs update for v3")
    @Test
    @Throws(InterruptedException::class)
    fun testUndoResetsCardCountsToCorrectValue() =
        runTest {
            val reviewer = startReviewer()

            advanceRobolectricLooper()

            // #6587
            addBasicNote("Hello", "World")

            val sched = col.sched

            val cardBeforeUndo = sched.card
            val countsBeforeUndo = sched.counts()

            sched.answerCard(cardBeforeUndo!!, Rating.GOOD)

            reviewer.undoAndShowSnackbar()

            val countsAfterUndo = sched.counts()

            assertThat(
                "Counts after an undo should be the same as before an undo",
                countsAfterUndo,
                equalTo(countsBeforeUndo),
            )
        }

    @Test
    fun `A card is not flipped after 'mark' Issue 14656`() =
        runTest {
            startReviewer(withCards = 1).apply {
                executeCommand(FLIP_OR_ANSWER_EASE1)
                assertThat("card is showing answer", isDisplayingAnswer)
                executeCommand(MARK)
                assertThat("card is showing answer after mark", isDisplayingAnswer)
            }
        }

    @Test
    fun `Marking a card is undone by marking again`() =
        runTest {
            startReviewer(withCards = 1).apply {
                assertThat("card is not marked before action", !isDisplayingMark)
                executeCommand(MARK)
                assertThat("card is marked after action", isDisplayingMark)
                executeCommand(MARK)
                assertThat("marking a card twice disables the mark", !isDisplayingMark)
            }
        }

    @Test
    @Flaky(OS.ALL) // had a flake on Windows due to flipOrAnswerCard, let's not block the release
    fun `changing deck refreshes card`() =
        runReviewer(cards = listOf("One", "Two")) {
            val nonDefaultDeck = addDeck("Hello")
            assertThat("first card is shown", this.cardContent, containsString("One"))
            flipOrAnswerCard(Rating.GOOD)
            // answer good, 'Rating.GOOD' should now be < 10m
            assertThat("initial time is 10m", this.getCardDataForJsApi().nextTime3, equalTo("<\u206810\u2069m"))
            flipOrAnswerCard(Rating.GOOD)
            assertThat("next card is shown", this.cardContent, containsString("Two"))

            undoableOp { col.setDeck(listOf(currentCard!!.id), nonDefaultDeck) }

            flipOrAnswerCard(Rating.GOOD)
            assertThat("buttons should be updated", this.getCardDataForJsApi().nextTime3, equalTo("\u20681\u2069d"))
            assertThat("content should be updated", this.cardContent, containsString("One"))
        }

    private fun toggleWhiteboard(reviewer: ReviewerForMenuItems) {
        reviewer.toggleWhiteboard()

        assumeTrue("Whiteboard should now be enabled", reviewer.prefWhiteboard)

        advanceRobolectricLooper()
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

    private fun assertCurrentOrdIsNot(
        r: Reviewer,
        @Suppress("SameParameterValue") i: Int,
    ) {
        advanceRobolectricLooper()
        val ord = r.currentCard!!.ord

        assertThat("Unexpected card ord", ord + 1, not(equalTo(i)))
    }

    private fun undo(reviewer: Reviewer) {
        reviewer.undo()
    }

    @Suppress("SameParameterValue")
    private fun assertCounts(
        r: Reviewer,
        newCount: Int,
        stepCount: Int,
        revCount: Int,
    ) = runTest {
        val jsApi = r.jsApi
        val countList =
            listOf(
                getDataFromRequest("newCardCount", jsApi),
                getDataFromRequest("lrnCardCount", jsApi),
                getDataFromRequest("revCardCount", jsApi),
            )
        val expected =
            listOf(
                formatApiResult(newCount),
                formatApiResult(stepCount),
                formatApiResult(revCount),
            )
        assertThat(
            countList.toString(),
            equalTo(expected.toString()),
        ) // We use toString as hamcrest does not print the whole array and stops at [0].
    }

    private fun answerCardOrdinalAsGood(
        r: Reviewer,
        i: Int,
    ) {
        assertCurrentOrdIs(r, i)

        r.answerCard(Rating.GOOD)

        advanceRobolectricLooper()
    }

    private fun assertCurrentOrdIs(
        r: Reviewer,
        i: Int,
    ) {
        advanceRobolectricLooper()
        val ord = r.currentCard!!.ord

        assertThat("Unexpected card ord", ord + 1, equalTo(i))
    }

    @Throws(ConfirmModSchemaException::class)
    @KotlinCleanup("use a assertNotNull which returns rather than !!")
    private fun addNoteWithThreeCards() {
        var notetype = col.notetypes.copy(col.notetypes.current())
        notetype.name = "Three"
        col.notetypes.add(notetype)
        notetype = col.notetypes.byName("Three")!!

        cloneTemplate(col.notetypes, notetype, "1")
        cloneTemplate(col.notetypes, notetype, "2")

        val newNote = col.newNote()
        newNote.setField(0, "Hello")
        assertThat(newNote.notetype.name, equalTo("Three"))

        assertThat(col.addNote(newNote), equalTo(3))
    }

    @Throws(ConfirmModSchemaException::class)
    private fun cloneTemplate(
        notetypes: Notetypes,
        notetype: NotetypeJson,
        extra: String,
    ) {
        val tmpls = notetype.templates
        val defaultTemplate = tmpls.first()

        val newTemplate = defaultTemplate.deepClone()
        newTemplate.setOrd(tmpls.length())

        val cardName = TR.cardTemplatesCard(tmpls.length() + 1)
        newTemplate.name = cardName
        newTemplate.qfmt += extra

        notetypes.addTemplate(notetype, newTemplate)
    }

    @CheckResult
    private fun startReviewer(withCards: Int = 0): Reviewer {
        for (i in 0 until withCards) {
            addBasicNote()
        }
        return startReviewer(this)
    }

    @CheckResult
    private fun <T : Reviewer?> startReviewer(clazz: Class<T>): T = startReviewer(this, clazz)

    private fun runReviewer(
        cards: List<String>,
        block: suspend Reviewer.() -> Unit,
    ) = runTest {
        for (frontSide in cards) {
            addBasicNote(front = frontSide)
        }
        val reviewer = startReviewer(this@ReviewerTest)
        block(reviewer)
    }

    private fun Card.moveToReviewQueue() {
        update {
            queue = QueueType.Rev
            type = CardType.Rev
            due = 0
        }
    }

    private val defaultDeckConfig
        get() = col.decks.configDictForDeckId(Consts.DEFAULT_DECK_ID)

    private class ReviewerForMenuItems : Reviewer() {
        var menu: Menu? = null
            private set

        override fun onCreateOptionsMenu(menu: Menu): Boolean {
            this.menu = menu
            return super.onCreateOptionsMenu(menu)
        }

        fun getVisibleButtonNames(): List<String> = getVisibleButtonNamesExcept()

        fun getVisibleButtonNamesExcept(vararg doNotReturn: Int): List<String> {
            val visibleButtons = arrayListOf<String>()
            val toSkip = hashSetOf(*doNotReturn.toTypedArray())
            for (item in menu!!) {
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
        fun startReviewer(testClass: RobolectricTest): Reviewer = startReviewer(testClass, Reviewer::class.java)

        fun <T : Reviewer?> startReviewer(
            testClass: RobolectricTest,
            clazz: Class<T>,
        ): T = startActivityNormallyOpenCollectionWithIntent(testClass, clazz, Intent())
    }
}

val Reviewer.isDisplayingMark: Boolean get() = this.cardMarker!!.isDisplayingMark
