/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki;

import android.Manifest;
import android.annotation.SuppressLint;

import com.ichi2.anki.tests.InstrumentedTest;
import com.ichi2.anki.testutil.ThreadUtils;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.PerformException;
import androidx.test.espresso.ViewAssertion;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.matcher.ViewMatchers.Visibility;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.rule.GrantPermissionRule;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.ichi2.anki.TestUtils.clickChildViewWithId;
import static com.ichi2.anki.TestUtils.getActivityInstance;
import static com.ichi2.anki.TestUtils.isScreenSw600dp;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

@SuppressLint("DirectSystemCurrentTimeMillisUsage")
public class DeckPickerTest {
    private void deleteAllDecks() {
        deleteAllNotes();

        while (true) {
            try {
                deleteDeck(0);
            } catch (PerformException e) {
                break; // no more decks left
            }
        }
    }


    private void deleteAllNotes() {
        onView(withContentDescription(R.string.drawer_open)).perform(click());
        onView(withText(R.string.card_browser)).perform(click());
        onView(withText(R.string.card_browser_all_decks)).perform(click());
        try {
            openActionBarOverflowOrOptionsMenu(getInstrumentation().getTargetContext());
            onView(withText(R.string.card_browser_select_all)).perform(click());
            openActionBarOverflowOrOptionsMenu(getInstrumentation().getTargetContext());
            onView(withText(R.string.card_browser_delete_card)).perform(click());
        } catch (NoMatchingViewException ignored) {
            pressBack(); // close overflow menu
        }
        pressBack();
    }


    private void deleteDeck(int position) {
        onView(withId(R.id.files)).perform(RecyclerViewActions.actionOnItemAtPosition(position, longClick()));
        onView(withText(R.string.contextmenu_deckpicker_delete_deck)).perform(click());
    }


    @Rule
    public ActivityScenarioRule<DeckPicker> mActivityRule = new ActivityScenarioRule<>(DeckPicker.class);

    @Rule
    public GrantPermissionRule mRuntimePermissionRule =
            GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

    @Ignore("This test appears to be flaky everywhere")
    @Test
    public void checkIfClickOnCountsLayoutOpensStudyOptionsOnMobile() {
        // Run the test only on emulator.
        assumeTrue(InstrumentedTest.isEmulator());
        assumeFalse("Test flaky in CI - #9282, skipping", TestUtils.wasBuiltOnCI());

        // For mobile. If it is not a mobile, then test will be ignored.
        assumeTrue(!isScreenSw600dp());

        String testString = System.currentTimeMillis() + "";
        createDeckWithCard(testString);

        // Go to RecyclerView item having "Test Deck" and click on the counts layout
        onView(withId(R.id.files)).perform(RecyclerViewActions.actionOnItem(hasDescendant(withText("TestDeck" + testString)), clickChildViewWithId(R.id.counts_layout)));

        // without this sleep, the study options fragment sometimes loses the "load and become active" race vs the assertion below.
        // It actually won the race sometimes so sleeping a full second is generous. This should be quite stable
        ThreadUtils.sleep(1000);

        // Check if currently open Activity is StudyOptionsActivity
        assertThat(getActivityInstance(), instanceOf(StudyOptionsActivity.class));
    }

    @Test
    public void checkIfStudyOptionsIsDisplayedOnTablet() {
        // Run the test only on emulator.
        assumeTrue(InstrumentedTest.isEmulator());
        assumeFalse("Test flaky in CI - #9282, skipping", TestUtils.wasBuiltOnCI());

        // For tablet. If it is not a tablet, then test will be ignored.
        assumeTrue(isScreenSw600dp());

        String testString = System.currentTimeMillis() + "";
        createDeckWithCard(testString);

        // Check if currently open Fragment is StudyOptionsFragment
        onView(withId(R.id.studyoptions_fragment)).check(matches(isDisplayed()));
    }


    @Test
    public void deckNormalCreationAndDeletionMakesSearchDecksIconVisible() {
        // Run the test only on emulator.
        assumeTrue(InstrumentedTest.isEmulator());
        deleteAllDecks();

        int iconId = R.id.deck_picker_action_filter;
        ensureIsGone(iconId);

        for (int count = 1; count < 9; count++) {
            createEmptyDeck("Deck" + count);
            ensureIsGone(iconId);
        }
        createEmptyDeck("Deck9");
        ensureIsVisible(iconId);

        deleteDeck(0);
        ensureIsGone(iconId);
    }


    @Test
    public void subdeckCreationAndDeletionMakesSearchDecksIconVisible() {
        // Run the test only on emulator.
        assumeTrue(InstrumentedTest.isEmulator());
        deleteAllDecks();

        int iconId = R.id.deck_picker_action_filter;
        ensureIsGone(iconId);

        createEmptyDeck(deckTreeName(1, 9, "Deck"));
        ensureIsVisible(iconId);

        deleteAllDecks();
        ensureIsGone(iconId);

        createEmptyDeck(deckTreeName(1, 11, "Deck"));
        ensureIsVisible(iconId);

        deleteAllDecks();
        ensureIsGone(iconId);

        createEmptyDeck(deckTreeName(1, 5, "Deck"));
        ensureIsGone(iconId);
        createEmptyDeck(deckTreeName(6, 11, "Deck"));
        ensureIsVisible(iconId);
    }


    String deckTreeName(int startInclusive, int endInclusive, String prefix) {
        return Arrays.stream(IntStream.rangeClosed(startInclusive, endInclusive).toArray())
                .mapToObj(i -> prefix + i)
                .collect(Collectors.joining("::"));
    }


    private void ensureIsGone(int iconId) {
        checkVisibility(iconId, false);
    }


    private void ensureIsVisible(int iconId) {
        checkVisibility(iconId, true);
    }


    private void checkVisibility(int iconId, boolean expectedVisibility) {
        boolean searchDeckIconIsVisible = true;
        try {
            ViewInteraction icon = onView(withId(iconId));
            if (expectedVisibility) {
                icon.check(isVisible());
            } else {
                icon.check(isGone());
            }
        } catch (NoMatchingViewException e) {
            searchDeckIconIsVisible = false;
        }
        assertEquals("Icon should be " + (expectedVisibility ? "" : "in") + "visible", searchDeckIconIsVisible, expectedVisibility);
    }


    private void createDeckWithCard(String testString) {
        createEmptyDeck("TestDeck" + testString);

        // The deck is currently empty, so if we tap on it, it becomes the selected deck but doesn't enter
        onView(withId(R.id.files)).perform(RecyclerViewActions.actionOnItem(hasDescendant(withText("TestDeck" + testString)), clickChildViewWithId(R.id.counts_layout)));

        // Create a card belonging to the new deck, using Basic type (guaranteed to exist)
        onView(withId(R.id.fab_main)).perform(click());
        onView(withId(R.id.add_note_action)).perform(click());

        // Close the keyboard, it auto-focuses and obscures enough of the screen
        // on some devices that espresso complains about global visibility being <90%
        closeSoftKeyboard();

        onView(withId(R.id.note_type_spinner)).perform(click());
        onView(withText("Basic")).perform(click());
        onView(withContentDescription("Front")).perform(typeText("SampleText" + testString));
        onView(withId(R.id.action_save)).perform(click());

        closeSoftKeyboard();

        // Go back to Deck Picker
        pressBack();
    }


    private void createEmptyDeck(String deckName) {
        onView(withId(R.id.fab_main)).perform(click());
        onView(withId(R.id.add_deck_action)).perform(click());
        createDeckDialog(deckName);
    }


    private void createDeckDialog(String deckName) {
        int editTextId = 16908297;
        onView(withId(editTextId)).perform(typeText(deckName));
        onView(withId(R.id.md_buttonDefaultPositive)).perform(click());

    }


    private ViewAssertion isGone() {
        return getViewAssertion(GONE);
    }


    private ViewAssertion isVisible() {
        return getViewAssertion(VISIBLE);
    }


    private ViewAssertion getViewAssertion(Visibility visibility) {
        return matches(withEffectiveVisibility(visibility));
    }
}
