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

import com.ichi2.anki.tests.InstrumentedTest;
import com.ichi2.anki.testutil.ThreadUtils;

import org.junit.Rule;
import org.junit.Test;

import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.rule.GrantPermissionRule;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.ichi2.anki.TestUtils.clickChildViewWithId;
import static com.ichi2.anki.TestUtils.getActivityInstance;
import static com.ichi2.anki.TestUtils.isScreenSw600dp;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

public class DeckPickerTest {
    @Rule
    public ActivityScenarioRule<DeckPicker> mActivityRule = new ActivityScenarioRule<>(DeckPicker.class);

    @Rule
    public GrantPermissionRule mRuntimePermissionRule =
            GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

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

    private void createDeckWithCard(String testString) {
        // Create a new deck
        onView(withId(R.id.fab_main)).perform(click());
        onView(withId(R.id.add_deck_action)).perform(click());
        onView(withId(R.id.action_edit)).perform(typeText("TestDeck" + testString));
        onView(withId(R.id.md_buttonDefaultPositive)).perform(click());

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
}
