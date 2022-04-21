/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
import android.graphics.Color
import androidx.annotation.CheckResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.Gesture.SWIPE_DOWN
import com.ichi2.anki.cardviewer.Gesture.SWIPE_LEFT
import com.ichi2.anki.cardviewer.Gesture.SWIPE_RIGHT
import com.ichi2.anki.cardviewer.Gesture.SWIPE_UP
import com.ichi2.anki.cardviewer.GestureProcessor
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.model.WhiteboardPenColor
import com.ichi2.anki.reviewer.FullScreenMode
import com.ichi2.anki.reviewer.FullScreenMode.Companion.setPreference
import com.ichi2.libanki.Consts
import com.ichi2.utils.KotlinCleanup
import net.ankiweb.rsdroid.database.NotImplementedException
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.`is`
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** A non-parameterized ReviewerTest - we should probably rename ReviewerTest in future  */
@RunWith(AndroidJUnit4::class)
@KotlinCleanup("IDE-lint")
@KotlinCleanup("`is` -> equalTo")
class ReviewerNoParamTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        // This doesn't do an upgrade in the correct place
        MetaDB.resetDB(targetContext)
    }

    @Test
    fun defaultWhiteboardColorIsUsedOnFirstRun() {
        val whiteboard = startReviewerForWhiteboard()

        assertThat("Pen color defaults to black", whiteboard.penColor, `is`(DEFAULT_LIGHT_PEN_COLOR))
    }

    @Test
    fun whiteboardLightModeColorIsUsed() {
        storeLightModeColor(ARBITRARY_PEN_COLOR_VALUE)

        val whiteboard = startReviewerForWhiteboard()

        assertThat("Pen color defaults to black", whiteboard.penColor, `is`(555))
    }

    @Test
    fun whiteboardDarkModeColorIsUsed() {
        storeDarkModeColor(555)

        val whiteboard = startReviewerForWhiteboardInDarkMode()

        assertThat("Pen color defaults to black", whiteboard.penColor, `is`(555))
    }

    @Test
    fun whiteboardPenColorChangeChangesDatabaseLight() {
        val whiteboard = startReviewerForWhiteboard()

        whiteboard.penColor = ARBITRARY_PEN_COLOR_VALUE

        val penColor = penColor
        assertThat("Light pen color is changed", penColor.lightPenColor, `is`(ARBITRARY_PEN_COLOR_VALUE))
    }

    @Test
    fun whiteboardPenColorChangeChangesDatabaseDark() {
        val whiteboard = startReviewerForWhiteboardInDarkMode()

        whiteboard.penColor = ARBITRARY_PEN_COLOR_VALUE

        val penColor = penColor
        assertThat("Dark pen color is changed", penColor.darkPenColor, `is`(ARBITRARY_PEN_COLOR_VALUE))
    }

    @Test
    fun whiteboardDarkPenColorIsNotUsedInLightMode() {
        storeDarkModeColor(555)

        val whiteboard = startReviewerForWhiteboard()

        assertThat("Pen color defaults to black, even if dark mode color is changed", whiteboard.penColor, `is`(DEFAULT_LIGHT_PEN_COLOR))
    }

    @Test
    fun differentDeckPenColorDoesNotAffectCurrentDeck() {
        val did = 2L
        storeLightModeColor(ARBITRARY_PEN_COLOR_VALUE, did)

        val whiteboard = startReviewerForWhiteboard()

        assertThat("Pen color defaults to black", whiteboard.penColor, `is`(DEFAULT_LIGHT_PEN_COLOR))
    }

    @Test
    fun flippingCardHidesFullscreen() {
        addNoteUsingBasicModel("Hello", "World")
        val reviewer = startReviewerFullScreen()

        val hideCount = reviewer.delayedHideCount

        reviewer.displayCardAnswer()

        assertThat("Hide should be called after flipping a card", reviewer.delayedHideCount, greaterThan(hideCount))
    }

    @Test
    fun showingCardHidesFullScreen() {
        addNoteUsingBasicModel("Hello", "World")
        val reviewer = startReviewerFullScreen()

        reviewer.displayCardAnswer()
        advanceRobolectricLooperWithSleep()

        val hideCount = reviewer.delayedHideCount

        reviewer.answerCard(Consts.BUTTON_ONE)
        advanceRobolectricLooperWithSleep()

        assertThat("Hide should be called after answering a card", reviewer.delayedHideCount, greaterThan(hideCount))
    }

    @Test
    fun undoingCardHidesFullScreen() {
        addNoteUsingBasicModel("Hello", "World")
        val reviewer = startReviewerFullScreen()

        reviewer.displayCardAnswer()
        advanceRobolectricLooperWithSleep()
        reviewer.answerCard(Consts.BUTTON_ONE)
        advanceRobolectricLooperWithSleep()

        val hideCount = reviewer.delayedHideCount

        reviewer.executeCommand(ViewerCommand.COMMAND_UNDO)
        advanceRobolectricLooperWithSleep()

        assertThat("Hide should be called after answering a card", reviewer.delayedHideCount, greaterThan(hideCount))
    }

    @Test
    @RunInBackground
    fun defaultDrawerConflictIsTrueIfGesturesEnabled() {
        enableGestureSetting()
        val reviewer = startReviewerFullScreen()

        assertThat(reviewer.hasDrawerSwipeConflicts(), `is`(true))
    }

    @Test
    fun noDrawerConflictsBeforeOnCreate() {
        enableGestureSetting()
        val controller = Robolectric.buildActivity(Reviewer::class.java, Intent())
        try {
            assertThat("no conflicts before onCreate", controller.get().hasDrawerSwipeConflicts(), `is`(false))
        } finally {
            try {
                enableGesture(Gesture.SWIPE_UP)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    @Test
    fun noDrawerConflictsIfGesturesDisabled() {
        disableGestureSetting()
        enableGesture(Gesture.SWIPE_UP)
        val reviewer = startReviewerFullScreen()
        assertThat("gestures should be disabled", gestureProcessor.isEnabled, `is`(false))
        assertThat(reviewer.hasDrawerSwipeConflicts(), `is`(false))
    }

    @Test
    fun noDrawerConflictsIfNoGestures() {
        enableGestureSetting()
        disableConflictGestures()
        val reviewer = startReviewerFullScreen()
        assertThat("gestures should be enabled", gestureProcessor.isEnabled, `is`(true))
        assertThat("no conflicts, so no conflicts detected", reviewer.hasDrawerSwipeConflicts(), `is`(false))
    }

    @Test
    @RunInBackground
    fun drawerConflictsIfUp() {
        enableGestureSetting()
        disableConflictGestures()
        enableGesture(Gesture.SWIPE_UP)
        val reviewer = startReviewerFullScreen()
        assertThat("gestures should be enabled", gestureProcessor.isEnabled, `is`(true))
        assertThat(reviewer.hasDrawerSwipeConflicts(), `is`(true))
    }

    @Test
    @RunInBackground
    fun drawerConflictsIfDown() {
        enableGestureSetting()
        disableConflictGestures()
        enableGesture(Gesture.SWIPE_DOWN)
        val reviewer = startReviewerFullScreen()
        assertThat("gestures should be enabled", gestureProcessor.isEnabled, `is`(true))
        assertThat(reviewer.hasDrawerSwipeConflicts(), `is`(true))
    }

    @Test
    @RunInBackground
    fun drawerConflictsIfRight() {
        enableGestureSetting()
        disableConflictGestures()
        enableGesture(Gesture.SWIPE_RIGHT)
        val reviewer = startReviewerFullScreen()
        assertThat("gestures should be enabled", gestureProcessor.isEnabled, `is`(true))
        assertThat(reviewer.hasDrawerSwipeConflicts(), `is`(true))
    }

    @Test
    fun normalReviewerFitsSystemWindows() {
        val reviewer = startReviewer()
        assertThat(reviewer.fitsSystemWindows(), `is`(true))
    }

    @Test
    fun fullscreenDoesNotFitSystemWindow() {
        val reviewer = startReviewerFullScreen()
        assertThat(reviewer.fitsSystemWindows(), `is`(false))
    }

    protected val gestureProcessor: GestureProcessor
        get() {
            val gestureProcessor = GestureProcessor(null)
            gestureProcessor.init(AnkiDroidApp.getSharedPrefs(targetContext))
            return gestureProcessor
        }

    protected fun disableConflictGestures() {
        disableGestures(Gesture.SWIPE_UP, Gesture.SWIPE_DOWN, Gesture.SWIPE_RIGHT)
    }

    private fun enableGestureSetting() {
        setGestureSetting(true)
    }

    private fun disableGestureSetting() {
        setGestureSetting(false)
    }

    @KotlinCleanup(".edit {}")
    private fun setGestureSetting(value: Boolean) {
        val settings = AnkiDroidApp.getSharedPrefs(targetContext).edit()
        settings.putBoolean(GestureProcessor.PREF_KEY, value)
        settings.apply()
    }

    @KotlinCleanup(".edit {}")
    private fun disableGestures(vararg gestures: Gesture) {
        val settings = AnkiDroidApp.getSharedPrefs(targetContext).edit()
        for (g in gestures) {
            val k = getKey(g)
            settings.putString(k, ViewerCommand.COMMAND_NOTHING.toPreferenceString())
        }
        settings.apply()
    }

    /** Enables a gesture (without changing the overall setting of whether gestures are allowed)  */
    @KotlinCleanup(".edit {}")
    private fun enableGesture(gesture: Gesture) {
        val settings = AnkiDroidApp.getSharedPrefs(targetContext).edit()
        val k = getKey(gesture)
        settings.putString(k, ViewerCommand.COMMAND_FLIP_OR_ANSWER_EASE1.toPreferenceString())
        settings.apply()
    }

    private fun getKey(gesture: Gesture): String {
        return when (gesture) {
            SWIPE_UP -> "gestureSwipeUp"
            SWIPE_DOWN -> "gestureSwipeDown"
            SWIPE_LEFT -> "gestureSwipeLeft"
            SWIPE_RIGHT -> "gestureSwipeRight"
            else -> throw NotImplementedException(gesture.toString())
        }
    }

    private fun startReviewerFullScreen(): ReviewerExt {
        val sharedPrefs = AnkiDroidApp.getSharedPrefs(targetContext)
        setPreference(sharedPrefs, FullScreenMode.BUTTONS_ONLY)
        return ReviewerTest.startReviewer(this, ReviewerExt::class.java)
    }

    protected fun storeDarkModeColor(value: Int) {
        MetaDB.storeWhiteboardPenColor(targetContext, Consts.DEFAULT_DECK_ID, false, value)
    }

    protected fun storeLightModeColor(value: Int, did: Long?) {
        MetaDB.storeWhiteboardPenColor(targetContext, did!!, false, value)
    }

    protected fun storeLightModeColor(value: Int) {
        MetaDB.storeWhiteboardPenColor(targetContext, Consts.DEFAULT_DECK_ID, true, value)
    }

    private fun enableDarkMode() {
        AnkiDroidApp.getSharedPrefs(targetContext).edit().putBoolean("invertedColors", true).apply()
    }

    protected val penColor: WhiteboardPenColor
        get() = MetaDB.getWhiteboardPenColor(targetContext, Consts.DEFAULT_DECK_ID)

    @CheckResult
    protected fun startReviewerForWhiteboard(): Whiteboard {
        // we need a card for the reviewer to start
        addNoteUsingBasicModel("Hello", "World")

        val reviewer = startReviewer()

        reviewer.toggleWhiteboard()

        return reviewer.whiteboard
            ?: throw IllegalStateException("Could not get whiteboard")
    }

    @CheckResult
    protected fun startReviewerForWhiteboardInDarkMode(): Whiteboard {
        addNoteUsingBasicModel("Hello", "World")

        val reviewer = startReviewer()
        enableDarkMode()
        reviewer.toggleWhiteboard()

        return reviewer.whiteboard
            ?: throw IllegalStateException("Could not get whiteboard")
    }

    private fun startReviewer(): Reviewer {
        return ReviewerTest.startReviewer(this)
    }

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
