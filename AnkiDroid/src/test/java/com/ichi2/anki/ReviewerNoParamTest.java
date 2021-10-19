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

package com.ichi2.anki;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;

import com.ichi2.anki.cardviewer.Gesture;
import com.ichi2.anki.cardviewer.GestureProcessor;
import com.ichi2.anki.cardviewer.ViewerCommand;
import com.ichi2.anki.model.WhiteboardPenColor;
import com.ichi2.anki.reviewer.FullScreenMode;
import com.ichi2.libanki.Consts;

import net.ankiweb.rsdroid.database.NotImplementedException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

/** A non-parmaterized ReviewerTest - we should probably rename ReviewerTest in future */
@RunWith(AndroidJUnit4.class)
public class ReviewerNoParamTest extends RobolectricTest {
    public static final int DEFAULT_LIGHT_PEN_COLOR = Color.BLACK;
    public static final int ARBITRARY_PEN_COLOR_VALUE = 555;


    @Before
    @Override
    public void setUp() {
        super.setUp();
        // This doesn't do an upgrade in the correct place
        MetaDB.resetDB(getTargetContext());
    }

    @Test
    public void defaultWhiteboardColorIsUsedOnFirstRun() {
        Whiteboard whiteboard = startReviewerForWhiteboard();

        assertThat("Pen color defaults to black", whiteboard.getPenColor(), is(DEFAULT_LIGHT_PEN_COLOR));
    }

    @Test
    public void whiteboardLightModeColorIsUsed() {
        storeLightModeColor(ARBITRARY_PEN_COLOR_VALUE);

        Whiteboard whiteboard = startReviewerForWhiteboard();

        assertThat("Pen color defaults to black", whiteboard.getPenColor(), is(555));
    }

    @Test
    public void whiteboardDarkModeColorIsUsed() {
        storeDarkModeColor(555);
        enableDarkMode();

        Whiteboard whiteboard = startReviewerForWhiteboard();

        assertThat("Pen color defaults to black", whiteboard.getPenColor(), is(555));
    }


    @Test
    public void whiteboardPenColorChangeChangesDatabaseLight() {
        Whiteboard whiteboard = startReviewerForWhiteboard();

        whiteboard.setPenColor(ARBITRARY_PEN_COLOR_VALUE);

        WhiteboardPenColor penColor = getPenColor();
        assertThat("Light pen color is changed", penColor.getLightPenColor(), is(ARBITRARY_PEN_COLOR_VALUE));
    }

    @Test
    public void whiteboardPenColorChangeChangesDatabaseDark() {
        enableDarkMode();

        Whiteboard whiteboard = startReviewerForWhiteboard();

        whiteboard.setPenColor(ARBITRARY_PEN_COLOR_VALUE);

        WhiteboardPenColor penColor = getPenColor();
        assertThat("Dark pen color is changed", penColor.getDarkPenColor(), is(ARBITRARY_PEN_COLOR_VALUE));
    }


    @Test
    public void whiteboardDarkPenColorIsNotUsedInLightMode() {
        storeDarkModeColor(555);

        Whiteboard whiteboard = startReviewerForWhiteboard();

        assertThat("Pen color defaults to black, even if dark mode color is changed", whiteboard.getPenColor(), is(DEFAULT_LIGHT_PEN_COLOR));
    }

    @Test
    public void differentDeckPenColorDoesNotAffectCurrentDeck() {
        long did = 2L;
        storeLightModeColor(ARBITRARY_PEN_COLOR_VALUE, did);

        Whiteboard whiteboard = startReviewerForWhiteboard();

        assertThat("Pen color defaults to black", whiteboard.getPenColor(), is(DEFAULT_LIGHT_PEN_COLOR));
    }


    @Test
    public void flippingCardHidesFullscreen() {
        addNoteUsingBasicModel("Hello", "World");
        ReviewerExt reviewer = startReviewerFullScreen();

        int hideCount = reviewer.getDelayedHideCount();

        reviewer.displayCardAnswer();

        assertThat("Hide should be called after flipping a card", reviewer.getDelayedHideCount(), greaterThan(hideCount));
    }


    @Test
    public void showingCardHidesFullScreen() {
        addNoteUsingBasicModel("Hello", "World");
        ReviewerExt reviewer = startReviewerFullScreen();

        reviewer.displayCardAnswer();
        advanceRobolectricLooperWithSleep();

        int hideCount = reviewer.getDelayedHideCount();

        reviewer.answerCard(Consts.BUTTON_ONE);
        advanceRobolectricLooperWithSleep();

        assertThat("Hide should be called after answering a card", reviewer.getDelayedHideCount(), greaterThan(hideCount));
    }

    @Test
    public void undoingCardHidesFullScreen() {
        addNoteUsingBasicModel("Hello", "World");
        ReviewerExt reviewer = startReviewerFullScreen();

        reviewer.displayCardAnswer();
        advanceRobolectricLooperWithSleep();
        reviewer.answerCard(Consts.BUTTON_ONE);
        advanceRobolectricLooperWithSleep();

        int hideCount = reviewer.getDelayedHideCount();

        reviewer.executeCommand(ViewerCommand.COMMAND_UNDO);
        advanceRobolectricLooperWithSleep();


        assertThat("Hide should be called after answering a card", reviewer.getDelayedHideCount(), greaterThan(hideCount));
    }

    @Test
    @RunInBackground
    public void defaultDrawerConflictIsTrueIfGesturesEnabled() {
        enableGestureSetting();
        ReviewerExt reviewer = startReviewerFullScreen();

        assertThat(reviewer.hasDrawerSwipeConflicts(), is(true));
    }


    @Test
    public void noDrawerConflictsBeforeOnCreate() {
        enableGestureSetting();
        ActivityController<Reviewer> controller = Robolectric.buildActivity(Reviewer.class, new Intent());
        try {
            assertThat("no conflicts before onCreate", controller.get().hasDrawerSwipeConflicts(), is(false));
        } finally {
            try {
                enableGesture(Gesture.SWIPE_UP);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    @Test
    public void noDrawerConflictsIfGesturesDisabled() {
        disableGestureSetting();
        enableGesture(Gesture.SWIPE_UP);
        ReviewerExt reviewer = startReviewerFullScreen();
        assertThat("gestures should be disabled", getGestureProcessor().isEnabled(), is(false));
        assertThat(reviewer.hasDrawerSwipeConflicts(), is(false));
    }


    @Test
    public void noDrawerConflictsIfNoGestures() {
        enableGestureSetting();
        disableConflictGestures();
        ReviewerExt reviewer = startReviewerFullScreen();
        assertThat("gestures should be enabled", getGestureProcessor().isEnabled(), is(true));
        assertThat("no conflicts, so no conflicts detected", reviewer.hasDrawerSwipeConflicts(), is(false));
    }

    @Test
    @RunInBackground
    public void drawerConflictsIfUp() {
        enableGestureSetting();
        disableConflictGestures();
        enableGesture(Gesture.SWIPE_UP);
        ReviewerExt reviewer = startReviewerFullScreen();
        assertThat("gestures should be enabled", getGestureProcessor().isEnabled(), is(true));
        assertThat(reviewer.hasDrawerSwipeConflicts(), is(true));
    }


    @Test
    @RunInBackground
    public void drawerConflictsIfDown() {
        enableGestureSetting();
        disableConflictGestures();
        enableGesture(Gesture.SWIPE_DOWN);
        ReviewerExt reviewer = startReviewerFullScreen();
        assertThat("gestures should be enabled", getGestureProcessor().isEnabled(), is(true));
        assertThat(reviewer.hasDrawerSwipeConflicts(), is(true));
    }

    @Test
    @RunInBackground
    public void drawerConflictsIfRight() {
        enableGestureSetting();
        disableConflictGestures();
        enableGesture(Gesture.SWIPE_RIGHT);
        ReviewerExt reviewer = startReviewerFullScreen();
        assertThat("gestures should be enabled", getGestureProcessor().isEnabled(), is(true));
        assertThat(reviewer.hasDrawerSwipeConflicts(), is(true));
    }


    @Test
    public void normalReviewerFitsSystemWindows() {
        Reviewer reviewer = startReviewer();
        assertThat(reviewer.fitsSystemWindows(), is(true));
    }

    @Test
    public void fullscreenDoesNotFitSystemWindow() {
        ReviewerExt reviewer = startReviewerFullScreen();
        assertThat(reviewer.fitsSystemWindows(), is(false));
    }

    protected GestureProcessor getGestureProcessor() {
        GestureProcessor gestureProcessor = new GestureProcessor(null);
        gestureProcessor.init(AnkiDroidApp.getSharedPrefs(this.getTargetContext()));
        return gestureProcessor;
    }

    protected void disableConflictGestures() {
        disableGestures(Gesture.SWIPE_UP, Gesture.SWIPE_DOWN, Gesture.SWIPE_RIGHT);
    }

    private void enableGestureSetting() {
        setGestureSetting(true);
    }

    private void disableGestureSetting() {
        setGestureSetting(false);
    }

    private void setGestureSetting(boolean value) {
        Editor settings = AnkiDroidApp.getSharedPrefs(getTargetContext()).edit();
        settings.putBoolean("gestures", value);
        settings.apply();
    }

    private void disableGestures(Gesture... gestures) {
        Editor settings = AnkiDroidApp.getSharedPrefs(getTargetContext()).edit();
        for (Gesture g: gestures) {
            String k = getKey(g);
            settings.putString(k, ViewerCommand.COMMAND_NOTHING.toPreferenceString());
        }
        settings.apply();
    }

    /** Enables a gesture (without changing the overall setting of whether gestures are allowed) */
    private void enableGesture(Gesture gesture) {
        Editor settings = AnkiDroidApp.getSharedPrefs(getTargetContext()).edit();
        String k = getKey(gesture);
        settings.putString(k, ViewerCommand.COMMAND_FLIP_OR_ANSWER_EASE1.toPreferenceString());
        settings.apply();
    }

    private String getKey(Gesture gesture) {
        switch (gesture) {
            case SWIPE_UP: return "gestureSwipeUp";
            case SWIPE_DOWN: return "gestureSwipeDown";
            case SWIPE_LEFT: return "gestureSwipeLeft";
            case SWIPE_RIGHT: return "gestureSwipeRight";
            default: throw new NotImplementedException(gesture.toString());
        }
    }

    private ReviewerExt startReviewerFullScreen() {
        SharedPreferences sharedPrefs = AnkiDroidApp.getSharedPrefs(getTargetContext());
        FullScreenMode.setPreference(sharedPrefs, FullScreenMode.BUTTONS_ONLY);
        ReviewerExt reviewer = ReviewerTest.startReviewer(this, ReviewerExt.class);
        return reviewer;
    }

    protected void storeDarkModeColor(@SuppressWarnings("SameParameterValue") int value) {
        MetaDB.storeWhiteboardPenColor(getTargetContext(), Consts.DEFAULT_DECK_ID, false, value);
    }

    protected void storeLightModeColor(@SuppressWarnings("SameParameterValue") int value, Long did) {
        MetaDB.storeWhiteboardPenColor(getTargetContext(), did, false, value);
    }

    protected void storeLightModeColor(@SuppressWarnings("SameParameterValue") int value) {
        MetaDB.storeWhiteboardPenColor(getTargetContext(), Consts.DEFAULT_DECK_ID, true, value);
    }

    private void enableDarkMode() {
        AnkiDroidApp.getSharedPrefs(getTargetContext()).edit().putBoolean("invertedColors", true).apply();
    }

    @NonNull
    protected WhiteboardPenColor getPenColor() {
        return MetaDB.getWhiteboardPenColor(getTargetContext(), Consts.DEFAULT_DECK_ID);
    }

    @CheckResult
    @NonNull
    protected Whiteboard startReviewerForWhiteboard() {
        // we need a card for the reviewer to start
        addNoteUsingBasicModel("Hello", "World");

        Reviewer reviewer = startReviewer();

        reviewer.toggleWhiteboard();

        Whiteboard whiteboard = reviewer.getWhiteboard();
        if (whiteboard == null) {
            throw new IllegalStateException("Could not get whiteboard");
        }
        return whiteboard;
    }


    private Reviewer startReviewer() {
        return ReviewerTest.startReviewer(this);
    }

    private static class ReviewerExt extends Reviewer {

        int mDelayedCount = 0;
        @Override
        protected void delayedHide(int delayMillis) {
            mDelayedCount++;
            super.delayedHide(delayMillis);
        }

        public int getDelayedHideCount() {
            return mDelayedCount;
        }
    }
}
