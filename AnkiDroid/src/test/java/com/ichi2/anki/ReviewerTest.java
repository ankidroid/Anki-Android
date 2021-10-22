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

package com.ichi2.anki;

import android.content.Intent;
import android.content.SharedPreferences;
import android.view.Menu;
import android.view.MenuItem;

import com.ichi2.anki.cardviewer.ViewerCommand;
import com.ichi2.anki.reviewer.ActionButtonStatus;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.DeckManager;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.ModelManager;
import com.ichi2.libanki.Note;
import com.ichi2.testutils.MockTime;
import com.ichi2.testutils.PreferenceUtils;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;

import androidx.test.core.app.ActivityScenario;
import timber.log.Timber;

import static com.ichi2.anki.AbstractFlashcardViewer.RESULT_DEFAULT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(ParameterizedRobolectricTestRunner.class)
public class ReviewerTest extends RobolectricTest {


    @ParameterizedRobolectricTestRunner.Parameter
    public int schedVersion;

    @ParameterizedRobolectricTestRunner.Parameters(name = "SchedV{0}")
    public static java.util.Collection<Object[]> initParameters() {
        // This does one run with schedVersion injected as 1, and one run as 2
        return Arrays.asList(new Object[][] { { 1 }, { 2 } });
    }

    @Before
    @Override
    public void setUp() {
        super.setUp();
        try {
            Timber.d("scheduler version is %d", schedVersion);
            getCol().changeSchedulerVer(schedVersion);
        } catch (ConfirmModSchemaException e) {
            throw new RuntimeException("Could not change schedVer", e);
        }
    }


    @Test
    public void verifyStartupNoCollection() {
        enableNullCollection();
        try (ActivityScenario<Reviewer> scenario = ActivityScenario.launch(Reviewer.class)) {
            scenario.onActivity(reviewer -> assertNull("Collection should have been null", reviewer.getCol()));
        }
    }

    @Test
    @RunInBackground
    public void verifyNormalStartup() {
        try (ActivityScenario<Reviewer> scenario = ActivityScenario.launch(Reviewer.class)) {
            scenario.onActivity(reviewer -> assertNotNull("Collection should be non-null", reviewer.getCol()));
        }
    }

    @Test
    @RunInBackground
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
        AnkiDroidJsAPI javaScriptFunction = reviewer.javaScriptFunction();


        // The answer needs to be displayed to be able to get the time.
        displayAnswer(reviewer);
        assertThat("4 buttons should be displayed", reviewer.getAnswerButtonCount(), is(4));

        String nextTime = javaScriptFunction.ankiGetNextTime4();
        assertThat(nextTime, not(isEmptyString()));

        // Display the next answer
        reviewer.answerCard(Consts.BUTTON_FOUR);

        displayAnswer(reviewer);

        if (schedVersion == 1) {
            assertThat("The 4th button should not be visible", reviewer.getAnswerButtonCount(), is(3));
            String learnTime = javaScriptFunction.ankiGetNextTime4();
            assertThat("If the 4th button is not visible, there should be no time4 in JS", learnTime, isEmptyString());
        }
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

    @Test
    public synchronized void testMultipleCards() throws ConfirmModSchemaException {
        addNoteWithThreeCards();
        Collection col = getCol();
        JSONObject nw = col.getDecks().confForDid(1).getJSONObject("new");
        MockTime time = getCollectionTime();
        nw.put("delays", new JSONArray(new int[] {1, 10, 60, 120}));

        waitForAsyncTasksToComplete();

        Reviewer reviewer = startReviewer();

        waitForAsyncTasksToComplete();

        assertCounts(reviewer,3, 0, 0);

        answerCardOrdinalAsGood(reviewer, 1); // card 1 is shown
        time.addM(3); // card get scheduler in [10, 12.5] minutes
        // We wait 3 minutes to ensure card 2 is scheduled after card 1
        answerCardOrdinalAsGood(reviewer, 2); // card 2 is shown
        time.addM(3); // Same as above
        answerCardOrdinalAsGood(reviewer, 3); // card 3 is shown

        undo(reviewer);

        assertCurrentOrdIs(reviewer, 3);

        answerCardOrdinalAsGood(reviewer, 3); // card 3 is shown

        assertCurrentOrdIsNot(reviewer, 3); // Anki Desktop shows "1"
    }

    @Test
    public void testLrnQueueAfterUndo() {
        Collection col = getCol();
        JSONObject nw = col.getDecks().confForDid(1).getJSONObject("new");
        MockTime time = (MockTime) col.getTime();
        nw.put("delays", new JSONArray(new int[] {1, 10, 60, 120}));

        Card[] cards = new Card[4];
        cards[0] = addRevNoteUsingBasicModelDueToday("1", "bar").firstCard();
        cards[1] = addNoteUsingBasicModel("2", "bar").firstCard();
        cards[2] = addNoteUsingBasicModel("3", "bar").firstCard();

        waitForAsyncTasksToComplete();

        Reviewer reviewer = startReviewer();

        waitForAsyncTasksToComplete();


        equalFirstField(cards[0], reviewer.mCurrentCard);
        reviewer.answerCard(Consts.BUTTON_ONE);
        waitForAsyncTasksToComplete();

        equalFirstField(cards[1], reviewer.mCurrentCard);
        reviewer.answerCard(Consts.BUTTON_ONE);
        waitForAsyncTasksToComplete();

        undo(reviewer);
        waitForAsyncTasksToComplete();

        equalFirstField(cards[1], reviewer.mCurrentCard);
        reviewer.answerCard(getCol().getSched().getGoodNewButton());
        waitForAsyncTasksToComplete();

        equalFirstField(cards[2], reviewer.mCurrentCard);
        time.addM(2);
        reviewer.answerCard(getCol().getSched().getGoodNewButton());
        advanceRobolectricLooperWithSleep();
        equalFirstField(cards[0], reviewer.mCurrentCard); // This failed in #6898 because this card was not in the queue
    }

    @Test
    public void baseDeckName() {
        Collection col = getCol();
        ModelManager models = col.getModels();

        DeckManager decks = col.getDecks();
        Long didAb = addDeck("A::B");
        Model basic = models.byName(AnkiDroidApp.getAppResources().getString(R.string.basic_model_name));
        basic.put("did", didAb);
        addNoteUsingBasicModel("foo", "bar");
        Long didA = addDeck("A");
        decks.select(didA);
        Reviewer reviewer = startReviewer();
        waitForAsyncTasksToComplete();
        assertThat(reviewer.getSupportActionBar().getTitle(), is("B"));
    }

    @Test
    public void jsAnkiGetDeckName() {
        Collection col = getCol();
        ModelManager models = col.getModels();
        DeckManager decks = col.getDecks();

        Long didAb = addDeck("A::B");
        Model basic = models.byName(AnkiDroidApp.getAppResources().getString(R.string.basic_model_name));
        basic.put("did", didAb);
        addNoteUsingBasicModel("foo", "bar");

        Long didA = addDeck("A");
        decks.select(didA);

        Reviewer reviewer = startReviewer();
        AnkiDroidJsAPI javaScriptFunction = reviewer.javaScriptFunction();

        waitForAsyncTasksToComplete();
        assertThat(javaScriptFunction.ankiGetDeckName(), is("B"));
    }

    private void toggleWhiteboard(ReviewerForMenuItems reviewer) {
        reviewer.toggleWhiteboard();

        assumeTrue("Whiteboard should now be enabled", reviewer.mPrefWhiteboard);

        advanceRobolectricLooperWithSleep();
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

    private void assertCurrentOrdIsNot(Reviewer r, int i) {
        waitForAsyncTasksToComplete();
        int ord = r.mCurrentCard.getOrd();

        assertThat("Unexpected card ord", ord + 1, not(is(i)));
    }


    private void undo(Reviewer reviewer) {
        reviewer.undo();
        waitForAsyncTasksToComplete();
    }


    private void assertCounts(Reviewer r, int newCount, int stepCount, int revCount) {

        List<String> countList = new ArrayList<>();
        AnkiDroidJsAPI jsApi = r.javaScriptFunction();
        countList.add(jsApi.ankiGetNewCardCount());
        countList.add(jsApi.ankiGetLrnCardCount());
        countList.add(jsApi.ankiGetRevCardCount());

        List<Integer> expexted = new ArrayList<>();
        expexted.add(newCount);
        expexted.add(stepCount);
        expexted.add(revCount);

        assertThat(countList.toString(), is(expexted.toString())); // We use toString as hamcrest does not print the whole array and stops at [0].
    }


    private void answerCardOrdinalAsGood(Reviewer r, int i) {
        assertCurrentOrdIs(r, i);

        r.answerCard(getCol().getSched().getGoodNewButton());

        waitForAsyncTasksToComplete();
    }


    private void assertCurrentOrdIs(Reviewer r, int i) {
        waitForAsyncTasksToComplete();
        int ord = r.mCurrentCard.getOrd();

        assertThat("Unexpected card ord", ord + 1, is(i));
    }


    private void addNoteWithThreeCards() throws ConfirmModSchemaException {
        ModelManager models = getCol().getModels();
        Model m = models.copy(models.current());
        m.put("name", "Three");
        models.add(m);
        m = models.byName("Three");
        models.flush();
        cloneTemplate(models, m);
        cloneTemplate(models, m);

        @NonNull Note newNote = getCol().newNote();
        newNote.setField(0, "Hello");
        assertThat(newNote.model().get("name"), is("Three"));

        assertThat(getCol().addNote(newNote), is(3));
    }


    private void cloneTemplate(ModelManager models, Model m) throws ConfirmModSchemaException {
        JSONArray tmpls = m.getJSONArray("tmpls");
        JSONObject defaultTemplate = tmpls.getJSONObject(0);

        JSONObject newTemplate = defaultTemplate.deepClone();
        newTemplate.put("ord", tmpls.length());

        String card_name = getTargetContext().getString(R.string.card_n_name, tmpls.length() + 1);
        newTemplate.put("name", card_name);

        models.addTemplate(m, newTemplate);
    }


    private void displayAnswer(Reviewer reviewer) {
        waitForAsyncTasksToComplete();
        reviewer.displayCardAnswer();
        waitForAsyncTasksToComplete();
    }

    private Reviewer startReviewer() {
        return startReviewer(this);
    }

    static Reviewer startReviewer(RobolectricTest testClass) {
        return startReviewer(testClass, Reviewer.class);
    }

    private <T extends Reviewer> T startReviewer(Class<T> clazz) {
        return startReviewer(this, clazz);
    }

    public static <T extends Reviewer> T startReviewer(RobolectricTest testClass, Class<T> clazz) {
        T reviewer = startActivityNormallyOpenCollectionWithIntent(testClass, clazz, new Intent());
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
