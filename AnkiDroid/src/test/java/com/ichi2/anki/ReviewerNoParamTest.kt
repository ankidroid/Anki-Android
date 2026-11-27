// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import androidx.annotation.CheckResult
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.AnkiDroidApp.Companion.sharedPrefs
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.Gesture.SWIPE_DOWN
import com.ichi2.anki.cardviewer.Gesture.SWIPE_RIGHT
import com.ichi2.anki.cardviewer.Gesture.SWIPE_UP
import com.ichi2.anki.cardviewer.GestureProcessor
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.common.preferences.SharedPreferencesTestHook
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.model.WhiteboardPenColor
import com.ichi2.anki.pages.CardInfoFragment
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.FullScreenMode
import com.ichi2.anki.reviewer.FullScreenMode.Companion.setPreference
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.anki.utils.ext.addBinding
import com.ichi2.anki.utils.ext.cardStatsNoCardClean
import com.ichi2.testutils.common.Flaky
import com.ichi2.testutils.common.OS
import com.ichi2.testutils.ext.snackbarText
import com.ichi2.themes.Themes.currentTheme
import kotlinx.coroutines.test.advanceUntilIdle
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.json.JSONArray
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.fakes.RoboMenuItem
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A non-parameterized ReviewerTest - we should probably rename ReviewerTest in future  */
@RunWith(AndroidJUnit4::class)
class ReviewerNoParamTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Before
    override fun setUp() {
        super.setUp()
        // This doesn't do an upgrade in the correct place
        MetaDB.resetDB(targetContext)
    }

    /**
     * After answering one card and burying the next, Previous card info should open the buried card.
     * The user saw the buried card immediately before the current question.
     */
    @Test
    fun `burying a card makes it the previous card in the legacy reviewer`() =
        runTest {
            updateDeckConfig(col.decks.selected()) { autoplay = false }
            // Three cards let us answer one, bury another, and keep the Reviewer open on the third.
            addBasicNote("First question", "First answer")
            addBasicNote("Second question", "Second answer")
            addBasicNote("Third question", "Third answer")
            val reviewer = startActivityNormallyOpenCollectionWithIntent(Reviewer::class.java, Intent())
            advanceRobolectricLooperUntil { reviewer.currentCardId != null }
            reviewer.answerCardInner(Rating.EASY)
            reviewer.updateCardAndRedraw()

            val previouslyShownCardId = assertNotNull(reviewer.currentCardId)
            assertTrue(reviewer.buryCard())
            // The burial refreshes asynchronously. Wait for the current card to change before opening the menu.
            advanceRobolectricLooperUntil { reviewer.currentCardId?.let { it != previouslyShownCardId } == true }
            // Redrawing the current card should leave the earlier card in the history.
            reviewer.updateCardAndRedraw()
            assertTrue(reviewer.onOptionsItemSelected(RoboMenuItem(R.id.action_previous_card_info)))
            val reviewerShadow = shadowOf(reviewer)
            // Card info may check the collection asynchronously before navigating.
            advanceRobolectricLooperUntil { reviewerShadow.peekNextStartedActivity() != null }
            val started = assertNotNull(reviewerShadow.nextStartedActivity)
            val arguments = assertNotNull(started.getBundleExtra(SingleFragmentActivity.EXTRA_FRAGMENT_ARGS))

            // The buggy result uses the first card's answer instead of the more recent buried card.
            assertThat(arguments.getLong(CardInfoFragment.ARG_CARD_ID), equalTo(previouslyShownCardId))
        }

    /** A same-ID repetition after Again should still expose the card from the previous presentation. */
    @Test
    fun `reviewing the same card again makes it the previous card in the legacy reviewer`() =
        runTest {
            // A short learning step within learn-ahead gives the scheduler the same card twice.
            col.config.set("collapseTime", 1200)
            updateDeckConfig(col.decks.selected()) {
                new.delays = JSONArray(listOf(1))
                autoplay = false
            }
            val cardId = addBasicNote().firstCard().id
            val reviewer = startActivityNormallyOpenCollectionWithIntent(Reviewer::class.java, Intent())
            advanceRobolectricLooperUntil { reviewer.currentCardId == cardId }

            // This is a new scheduler presentation after answering, even though the ID is unchanged.
            reviewer.answerCardInner(Rating.AGAIN)
            reviewer.updateCardAndRedraw()
            assertThat(reviewer.currentCardId, equalTo(cardId))
            assertTrue(reviewer.onOptionsItemSelected(RoboMenuItem(R.id.action_previous_card_info)))
            val reviewerShadow = shadowOf(reviewer)
            advanceRobolectricLooperUntil {
                reviewerShadow.peekNextStartedActivity() != null || reviewer.snackbarText == TR.cardStatsNoCardClean()
            }

            // The buggy ID-equality guard treats the repetition as a redraw, so no Card Info opens.
            val started = assertNotNull(reviewerShadow.nextStartedActivity)
            val arguments = assertNotNull(started.getBundleExtra(SingleFragmentActivity.EXTRA_FRAGMENT_ARGS))
            assertThat(arguments.getLong(CardInfoFragment.ARG_CARD_ID), equalTo(cardId))
        }

    @Test
    fun `audio completion after the review queue is empty is ignored`() =
        runTest {
            val config = col.decks.configDictForDeckId(Consts.DEFAULT_DECK_ID)
            config.autoplay = false
            col.decks.save(config)
            addBasicNote()
            val reviewer = startActivityNormallyOpenCollectionWithIntent(Reviewer::class.java, Intent())
            advanceRobolectricLooperUntil { reviewer.currentCardId != null }
            reviewer.answerCardInner(Rating.EASY)
            reviewer.updateCardAndRedraw()
            assertThat(reviewer.isFinishing, equalTo(true))

            reviewer.onMediaGroupCompleted()
            advanceUntilIdle()
            advanceRobolectricLooper()
        }

    @Test
    fun defaultWhiteboardColorIsUsedOnFirstRun() {
        val whiteboard = startReviewerForWhiteboard()

        assertThat("Pen color defaults to black", whiteboard.penColor, equalTo(DEFAULT_LIGHT_PEN_COLOR))
    }

    @Test
    fun whiteboardLightModeColorIsUsed() {
        storeLightModeColor(ARBITRARY_PEN_COLOR_VALUE)

        val whiteboard = startReviewerForWhiteboard()

        assertThat("Pen color defaults to black", whiteboard.penColor, equalTo(555))
    }

    @Test
    fun whiteboardDarkModeColorIsUsed() {
        storeDarkModeColor(555)

        val whiteboard = startReviewerForWhiteboardInDarkMode()

        assertThat("Pen color defaults to black", whiteboard.penColor, equalTo(555))
    }

    @Test
    fun whiteboardPenColorChangeChangesDatabaseLight() {
        val whiteboard = startReviewerForWhiteboard()

        whiteboard.penColor = ARBITRARY_PEN_COLOR_VALUE

        val penColor = penColor
        assertThat("Light pen color is changed", penColor.lightPenColor, equalTo(ARBITRARY_PEN_COLOR_VALUE))
    }

    @Test
    fun whiteboardPenColorChangeChangesDatabaseDark() {
        val whiteboard = startReviewerForWhiteboardInDarkMode()

        whiteboard.penColor = ARBITRARY_PEN_COLOR_VALUE

        val penColor = penColor
        assertThat("Dark pen color is changed", penColor.darkPenColor, equalTo(ARBITRARY_PEN_COLOR_VALUE))
    }

    @Test
    fun whiteboardDarkPenColorIsNotUsedInLightMode() {
        storeDarkModeColor(555)

        val whiteboard = startReviewerForWhiteboard()

        assertThat("Pen color defaults to black, even if dark mode color is changed", whiteboard.penColor, equalTo(DEFAULT_LIGHT_PEN_COLOR))
    }

    @Test
    fun differentDeckPenColorDoesNotAffectCurrentDeck() {
        val did = 2L
        storeLightModeColor(ARBITRARY_PEN_COLOR_VALUE, did)

        val whiteboard = startReviewerForWhiteboard()

        assertThat("Pen color defaults to black", whiteboard.penColor, equalTo(DEFAULT_LIGHT_PEN_COLOR))
    }

    @Test
    fun flippingCardHidesFullscreen() {
        addBasicNote("Hello", "World")
        val reviewer = startReviewerFullScreen()

        val hideCount = reviewer.delayedHideCount

        reviewer.displayCardAnswer()

        assertThat("Hide should be called after flipping a card", reviewer.delayedHideCount, greaterThan(hideCount))
    }

    @Test
    @Flaky(
        OS.ALL,
        "Hide should be called after answering a card" +
            "    Expected: a value greater than <2>" +
            "         but: <2> was equal to <2>",
    )
    fun showingCardHidesFullScreen() {
        addBasicNote("Hello", "World")
        val reviewer = startReviewerFullScreen()

        reviewer.displayCardAnswer()
        advanceRobolectricLooper()

        val hideCount = reviewer.delayedHideCount

        reviewer.answerCard(Rating.AGAIN)
        advanceRobolectricLooper()

        assertThat("Hide should be called after answering a card", reviewer.delayedHideCount, greaterThan(hideCount))
    }

    @Test
    @Flaky(OS.ALL, "Expected: a value greater than <2> but: <2> was equal to <2>")
    fun undoingCardHidesFullScreen() =
        runTest {
            addBasicNote("Hello", "World")
            val reviewer = startReviewerFullScreen()

            reviewer.displayCardAnswer()
            advanceRobolectricLooper()
            reviewer.answerCard(Rating.AGAIN)
            advanceRobolectricLooper()

            val hideCount = reviewer.delayedHideCount

            reviewer.undo()

            advanceRobolectricLooper()

            assertThat("Hide should be called after answering a card", reviewer.delayedHideCount, greaterThan(hideCount))
        }

    @Test
    fun defaultDrawerConflictIsTrueIfGesturesEnabled() =
        withIsolatedGesturePreferences {
            enableGestureSetting()
            enableGesture(SWIPE_RIGHT)
            val reviewer = startReviewerFullScreen()
            advanceRobolectricLooper()
            assertThat(reviewer.hasDrawerSwipeConflicts(), equalTo(true))
        }

    @Test
    fun noDrawerConflictsBeforeOnCreate() =
        withIsolatedGesturePreferences {
            enableGestureSetting()
            val controller = Robolectric.buildActivity(Reviewer::class.java, Intent())
            saveControllerForCleanup(controller)
            try {
                assertThat("no conflicts before onCreate", controller.get().hasDrawerSwipeConflicts(), equalTo(false))
            } finally {
                try {
                    enableGesture(SWIPE_UP)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

    @Test
    fun noDrawerConflictsIfGesturesDisabled() =
        withIsolatedGesturePreferences {
            disableGestureSetting()
            enableGesture(SWIPE_UP)
            val reviewer = startReviewerFullScreen()
            assertThat("gestures should be disabled", gestureProcessor.isEnabled, equalTo(false))
            assertThat(reviewer.hasDrawerSwipeConflicts(), equalTo(false))
        }

    @Test
    fun noDrawerConflictsIfNoGestures() =
        withIsolatedGesturePreferences {
            enableGestureSetting()
            disableConflictGestures()
            val reviewer = startReviewerFullScreen()
            assertThat("gestures should be enabled", gestureProcessor.isEnabled, equalTo(true))
            assertThat("no conflicts, so no conflicts detected", reviewer.hasDrawerSwipeConflicts(), equalTo(false))
        }

    @Test
    fun drawerConflictsIfUp() =
        withIsolatedGesturePreferences {
            enableGestureSetting()
            disableConflictGestures()
            enableGesture(SWIPE_UP)
            val reviewer = startReviewerFullScreen()
            assertThat("gestures should be enabled", gestureProcessor.isEnabled, equalTo(true))
            advanceRobolectricLooper()
            assertThat(reviewer.hasDrawerSwipeConflicts(), equalTo(true))
        }

    @Test
    fun drawerConflictsIfDown() =
        withIsolatedGesturePreferences {
            enableGestureSetting()
            disableConflictGestures()
            enableGesture(SWIPE_DOWN)
            val reviewer = startReviewerFullScreen()
            assertThat("gestures should be enabled", gestureProcessor.isEnabled, equalTo(true))
            advanceRobolectricLooper()
            assertThat(reviewer.hasDrawerSwipeConflicts(), equalTo(true))
        }

    @Test
    fun drawerConflictsIfRight() =
        withIsolatedGesturePreferences {
            enableGestureSetting()
            disableConflictGestures()
            enableGesture(SWIPE_RIGHT)
            val reviewer = startReviewerFullScreen()
            assertThat("gestures should be enabled", gestureProcessor.isEnabled, equalTo(true))
            advanceRobolectricLooper()
            assertThat(reviewer.hasDrawerSwipeConflicts(), equalTo(true))
        }

    @Test
    fun normalReviewerDoesNotFitSystemWindows() {
        // edge-to-edge: insets are applied manually, see Reviewer.setupEdgeToEdge()
        val reviewer = startReviewer()
        assertThat(reviewer.fitsSystemWindows(), equalTo(false))
    }

    @Test
    fun fullscreenDoesNotFitSystemWindow() {
        val reviewer = startReviewerFullScreen()
        assertThat(reviewer.fitsSystemWindows(), equalTo(false))
    }

    private val gestureProcessor: GestureProcessor
        get() {
            val gestureProcessor = GestureProcessor(null)
            gestureProcessor.init(sharedPrefs())
            return gestureProcessor
        }

    private fun disableConflictGestures() {
        disableGestures(SWIPE_UP, SWIPE_DOWN, SWIPE_RIGHT)
    }

    private fun enableGestureSetting() {
        setGestureSetting(true)
    }

    private fun disableGestureSetting() {
        setGestureSetting(false)
    }

    private fun setGestureSetting(value: Boolean) {
        sharedPrefs().edit {
            putBoolean(GestureProcessor.PREF_KEY, value)
        }
    }

    private fun disableGestures(vararg gestures: Gesture) {
        val prefs = sharedPrefs()
        for (command in ViewerCommand.entries) {
            for (mappableBinding in ReviewerBinding.fromPreference(prefs, command)) {
                val gestureBinding = mappableBinding.binding as? Binding.GestureInput? ?: continue
                if (gestureBinding.gesture in gestures) {
                    val bindings = ReviewerBinding.fromPreferenceString(command.preferenceKey).toMutableList()
                    bindings.remove(mappableBinding)
                    prefs.edit {
                        putString(command.preferenceKey, bindings.toPreferenceString())
                    }
                }
            }
        }
    }

    /** Enables a gesture (without changing the overall setting of whether gestures are allowed)  */
    private fun enableGesture(gesture: Gesture) {
        val prefs = sharedPrefs()
        ViewerCommand.ANSWER_AGAIN.addBinding(
            prefs,
            ReviewerBinding.fromGesture(gesture),
        )
    }

    private fun withIsolatedGesturePreferences(block: () -> Unit) {
        val previousHook = SharedPreferencesTestHook.override
        SharedPreferencesTestHook.override = cloneDefaultSharedPreferences()
        try {
            block()
        } finally {
            SharedPreferencesTestHook.override = previousHook
        }
    }

    private fun cloneDefaultSharedPreferences(): SharedPreferences {
        val previousHook = SharedPreferencesTestHook.override
        SharedPreferencesTestHook.override = null
        try {
            val source = PreferenceManager.getDefaultSharedPreferences(targetContext)
            val target = SPMockBuilder().createSharedPreferences()
            target.edit {
                for ((key, value) in source.all) {
                    when (value) {
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                        else -> throw IllegalArgumentException("Unsupported preference type for $key")
                    }
                }
            }
            return target
        } finally {
            SharedPreferencesTestHook.override = previousHook
        }
    }

    private fun startReviewerFullScreen(): ReviewerExt {
        CollectionManager.getColUnsafe()
        val preferences = sharedPrefs()
        setPreference(preferences, FullScreenMode.BUTTONS_ONLY)
        return ReviewerTest.startReviewer(this, ReviewerExt::class.java)
    }

    @Suppress("SameParameterValue")
    private fun storeDarkModeColor(value: Int) {
        MetaDB.storeWhiteboardPenColor(targetContext, Consts.DEFAULT_DECK_ID, false, value)
    }

    @Suppress("SameParameterValue")
    private fun storeLightModeColor(
        value: Int,
        did: DeckId?,
    ) {
        MetaDB.storeWhiteboardPenColor(targetContext, did!!, false, value)
    }

    @Suppress("SameParameterValue")
    private fun storeLightModeColor(value: Int) {
        MetaDB.storeWhiteboardPenColor(targetContext, Consts.DEFAULT_DECK_ID, true, value)
    }

    private val penColor: WhiteboardPenColor
        get() = MetaDB.getWhiteboardPenColor(targetContext, Consts.DEFAULT_DECK_ID)

    @CheckResult
    private fun startReviewerForWhiteboard(): Whiteboard {
        // we need a card for the reviewer to start
        addBasicNote("Hello", "World")

        val reviewer = startReviewer()

        reviewer.toggleWhiteboard()

        return reviewer.whiteboard
            ?: throw IllegalStateException("Could not get whiteboard")
    }

    @CheckResult
    private fun startReviewerForWhiteboardInDarkMode(): Whiteboard {
        addBasicNote("Hello", "World")

        val reviewer = startReviewer()
        currentTheme = NightTheme.DARK
        reviewer.toggleWhiteboard()

        return reviewer.whiteboard
            ?: throw IllegalStateException("Could not get whiteboard")
    }

    private fun startReviewer(): Reviewer = ReviewerTest.startReviewer(this)

    private class ReviewerExt : Reviewer() {
        var delayedHideCount = 0

        override fun delayedHide(delayMillis: Int) {
            delayedHideCount++
            super.delayedHide(delayMillis)
        }
    }

    companion object {
        const val DEFAULT_LIGHT_PEN_COLOR = Color.BLACK
        const val ARBITRARY_PEN_COLOR_VALUE = 555
    }
}
