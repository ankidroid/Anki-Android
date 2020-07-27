package com.ichi2.anki;

import android.content.Intent;
import android.content.SharedPreferences;
import android.view.Menu;
import android.view.MenuItem;

import com.ichi2.anki.AbstractFlashcardViewer.JavaScriptFunction;
import com.ichi2.anki.cardviewer.ViewerCommand;
import com.ichi2.anki.reviewer.ActionButtonStatus;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Note;
import com.ichi2.testutils.PreferenceUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.LooperMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.anki.AbstractFlashcardViewer.EASE_4;
import static com.ichi2.anki.AbstractFlashcardViewer.RESULT_DEFAULT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ReviewerTest extends RobolectricTest {

    @Test
    public void verifyStartupNoCollection() {
        enableNullCollection();
        try (ActivityScenario<Reviewer> scenario = ActivityScenario.launch(Reviewer.class)) {
            scenario.onActivity(reviewer -> assertNull("Collection should have been null", reviewer.getCol()));
        }
    }

    @Test
    public void verifyNormalStartup() {
        try (ActivityScenario<Reviewer> scenario = ActivityScenario.launch(Reviewer.class)) {
            scenario.onActivity(reviewer -> assertNotNull("Collection should be non-null", reviewer.getCol()));
        }
    }

    @Test
    public void exitCommandWorksAfterControlsAreBlocked() {
        ensureCollectionLoadIsSynchronous();
        try (ActivityScenario<Reviewer> scenario = ActivityScenario.launch(Reviewer.class)) {
            scenario.onActivity(reviewer -> {
                reviewer.blockControls(true);
                reviewer.executeCommand(ViewerCommand.COMMAND_EXIT);
            });
            assertThat(scenario.getResult().getResultCode(), is(RESULT_DEFAULT));
        }
    }

    @Test
    public void jsTime4ShouldBeBlankIfButtonUnavailable() {
        // #6623 - easy should be blank when displaying a card with 3 buttons (after displaying a review)
        Note firstNote = addNoteUsingBasicModel("Hello", "World");
        moveToReviewQueue(firstNote.firstCard());

        addNoteUsingBasicModel("Hello", "World2");

        Reviewer reviewer = startReviewer();
        JavaScriptFunction javaScriptFunction = reviewer.new JavaScriptFunction();


        // The answer needs to be displayed to be able to get the time.
        displayAnswer(reviewer);
        assertThat("4 buttons should be displayed", reviewer.getAnswerButtonCount(), is(4));

        String nextTime = javaScriptFunction.ankiGetNextTime4();
        assertThat(nextTime, not(isEmptyString()));

        // Display the next answer
        reviewer.answerCard(EASE_4);

        displayAnswer(reviewer);

        assertThat("The 4th button should not be visible", reviewer.getAnswerButtonCount(), is(3));

        String learnTime = javaScriptFunction.ankiGetNextTime4();
        assertThat("If the 4th button is not visible, there should be no time4 in JS", learnTime, isEmptyString());
    }

    @Test
    public void nothingAppearsInAppBarIfAllAppBarButtonsAreDisabled() {
        disableAllReviewerAppBarButtons();

        ReviewerForMenuItems reviewer = startReviewer(ReviewerForMenuItems.class);

        List<String> visibleButtons = reviewer.getVisibleButtonNames();

        assertThat("No menu items should be visible if all are disabled in Settings - Reviewer - App Bar Buttons", visibleButtons, empty());
    }

    @Test
    public void onlyDisableWhiteboardAppearsInAppBarIfAllAppBarButtonsAreDisabledWithWhiteboard() {
        disableAllReviewerAppBarButtons();

        ReviewerForMenuItems reviewer = startReviewer(ReviewerForMenuItems.class);

        toggleWhiteboard(reviewer);

        List<String> visibleButtons = reviewer.getVisibleButtonNamesExcept(R.id.action_toggle_whiteboard);

        assertThat("No menu items should be visible if all are disabled in Settings - Reviewer - App Bar Buttons", visibleButtons, empty());
    }


    private void toggleWhiteboard(ReviewerForMenuItems reviewer) {
        reviewer.toggleWhiteboard();

        assumeTrue("Whiteboard should now be enabled", reviewer.mPrefWhiteboard);

        super.advanceRobolectricLooper();
    }


    private void disableAllReviewerAppBarButtons() {
        Set<String> keys = PreferenceUtils.getAllCustomButtonKeys(getTargetContext());

        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getTargetContext());

        SharedPreferences.Editor e =  preferences.edit();
        for (String k : keys) {
            e.putString(k, Integer.toString(ActionButtonStatus.MENU_DISABLED));
        }
        e.apply();
    }


    private void displayAnswer(Reviewer reviewer) {
        waitForAsyncTasksToComplete();
        reviewer.displayCardAnswer();
        waitForAsyncTasksToComplete();
    }

    private Reviewer startReviewer() {
        return startReviewer(Reviewer.class);
    }

    private <T extends Reviewer> T startReviewer(Class<T> clazz) {
        T reviewer = super.startActivityNormallyOpenCollectionWithIntent(clazz, new Intent());
        waitForAsyncTasksToComplete();
        return reviewer;
    }


    private void moveToReviewQueue(Card reviewCard) {
        reviewCard.setQueue(Consts.QUEUE_TYPE_REV);
        reviewCard.setType(Consts.CARD_TYPE_REV);
        reviewCard.setDue(0);
        reviewCard.flush();
    }

    private static class ReviewerForMenuItems extends Reviewer {
        private Menu mMenu;


        @Override
        public boolean onCreateOptionsMenu(Menu menu) {
            this.mMenu = menu;
            return super.onCreateOptionsMenu(menu);
        }


        public Menu getMenu() {
            return mMenu;
        }

        @NonNull
        private List<String> getVisibleButtonNames() {
            return getVisibleButtonNamesExcept();
        }


        @NonNull
        private List<String> getVisibleButtonNamesExcept(Integer... doNotReturn) {
            ArrayList<String> visibleButtons = new ArrayList<>();
            HashSet<Integer> toSkip = new HashSet<>(Arrays.asList(doNotReturn));

            Menu menu = getMenu();
            for(int i = 0; i < menu.size(); i++ ){
                MenuItem item =  menu.getItem(i);
                if (toSkip.contains(item.getItemId())) {
                    continue;
                }
                if (item.isVisible()) {
                    visibleButtons.add(item.getTitle().toString());
                }
            }
            return visibleButtons;
        }
    }
}

