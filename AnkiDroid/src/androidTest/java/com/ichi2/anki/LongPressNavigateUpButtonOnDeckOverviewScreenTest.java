/*
 *  Copyright (c) 2022 MrPenguins <zhanghaoqi321@gmail.com>
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


import android.annotation.SuppressLint;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.ichi2.anki.tests.InstrumentedTest;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;

import androidx.annotation.RequiresApi;
import androidx.test.espresso.ViewInteraction;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.rule.GrantPermissionRule;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

public class LongPressNavigateUpButtonOnDeckOverviewScreenTest {

    @Rule
    public ActivityScenarioRule<IntentHandler> mActivityTestRule = new ActivityScenarioRule<>(IntentHandler.class);

    @Rule
    public GrantPermissionRule mGrantPermissionRule =
            GrantPermissionRule.grant(
                    "android.permission.READ_EXTERNAL_STORAGE",
                    "android.permission.WRITE_EXTERNAL_STORAGE");


    @RequiresApi(api = Build.VERSION_CODES.N)
    @Test
    public void longPressNavigateUpButtonOnDeckOverviewScreenTest() {
        assumeTrue(InstrumentedTest.isEmulator());
        assumeFalse("Test flaky in CI - #9282, skipping", TestUtils.wasBuiltOnCI());


        ViewInteraction floatingActionButton = onView(
                allOf(withId(R.id.fab_main),
                        childAtPosition(
                                childAtPosition(
                                        withClassName(is("android.widget.LinearLayout")),
                                        3),
                                0),
                        isDisplayed()));
        floatingActionButton.perform(click());

        ViewInteraction floatingActionButton2 = onView(
                allOf(withId(R.id.add_deck_action),
                        childAtPosition(
                                allOf(withId(R.id.add_deck_layout),
                                        childAtPosition(
                                                withClassName(is("android.widget.LinearLayout")),
                                                0)),
                                1),
                        isDisplayed()));
        floatingActionButton2.perform(click());

        ViewInteraction appCompatEditText = onView(
                allOf(withId(android.R.id.input),
                        childAtPosition(
                                childAtPosition(
                                        withClassName(is("android.widget.LinearLayout")),
                                        1),
                                0),
                        isDisplayed()));
        appCompatEditText.perform(replaceText("asd"), closeSoftKeyboard());

        @SuppressLint("VariableNamingDetector") ViewInteraction mDButton2 = onView(
                allOf(withId(R.id.md_buttonDefaultPositive), withText("OK"),
                        childAtPosition(
                                childAtPosition(
                                        withId(android.R.id.content),
                                        0),
                                4),
                        isDisplayed()));
        mDButton2.perform(click());

        ViewInteraction recyclerView = onView(
                allOf(withId(R.id.files),
                        childAtPosition(
                                withId(R.id.deck_picker_content),
                                0)));
        recyclerView.perform(actionOnItemAtPosition(0, click()));

        ViewInteraction appCompatButton = onView(
                allOf(withId(R.id.snackbar_action), withText("Add card"),
                        childAtPosition(
                                childAtPosition(
                                        withClassName(is("com.google.android.material.snackbar.Snackbar$SnackbarLayout")),
                                        0),
                                1),
                        isDisplayed()));
        appCompatButton.perform(click());

        ViewInteraction fieldEditText = onView(
                allOf(withId(R.id.id_note_editText), withContentDescription("Front"),
                        childAtPosition(
                                allOf(withId(R.id.constraint_layout),
                                        childAtPosition(
                                                withClassName(is("com.ichi2.anki.FieldEditLine")),
                                                0)),
                                5),
                        isDisplayed()));
        fieldEditText.perform(replaceText("Asdf"), closeSoftKeyboard());

        ViewInteraction fieldEditText2 = onView(
                allOf(withId(R.id.id_note_editText), withContentDescription("Back"),
                        childAtPosition(
                                allOf(withId(R.id.constraint_layout),
                                        childAtPosition(
                                                withClassName(is("com.ichi2.anki.FieldEditLine")),
                                                0)),
                                5),
                        isDisplayed()));
        fieldEditText2.perform(replaceText("Qeru"), closeSoftKeyboard());

        ViewInteraction actionMenuItemView = onView(
                allOf(withId(R.id.action_save), withContentDescription("Save"),
                        childAtPosition(
                                childAtPosition(
                                        withId(R.id.toolbar),
                                        4),
                                0),
                        isDisplayed()));
        actionMenuItemView.perform(click());

        ViewInteraction appCompatImageButton = onView(
                allOf(withContentDescription("Navigate up"),
                        childAtPosition(
                                allOf(withId(R.id.toolbar),
                                        childAtPosition(
                                                withId(R.id.note_editor_layout),
                                                0)),
                                0),
                        isDisplayed()));
        appCompatImageButton.perform(click());

        ViewInteraction linearLayout = onView(
                allOf(withId(R.id.counts_layout), withContentDescription("Open the deck overview page containing the number of cards to see today."),
                        childAtPosition(
                                allOf(withId(R.id.DeckPickerHoriz),
                                        childAtPosition(
                                                withId(R.id.files),
                                                0)),
                                2),
                        isDisplayed()));
        linearLayout.perform(click());

        ViewInteraction appCompatImageButton2 = onView(
                allOf(withContentDescription("Navigate up"),
                        childAtPosition(
                                allOf(withId(R.id.studyOptionsToolbar),
                                        childAtPosition(
                                                withClassName(is("android.widget.RelativeLayout")),
                                                0)),
                                0),
                        isDisplayed()));
        appCompatImageButton2.perform(longClick());

        ViewInteraction viewGroup = onView(
                allOf(withId(R.id.studyOptionsToolbar),
                        withParent(withParent(withId(R.id.studyoptions_main))),
                        isDisplayed()));
        viewGroup.check(matches(isDisplayed()));
    }


    private static Matcher<View> childAtPosition(
            final Matcher<View> parentMatcher, final int position) {

        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("Child at position " + position + " in parent ");
                parentMatcher.describeTo(description);
            }


            @Override
            public boolean matchesSafely(View view) {
                ViewParent parent = view.getParent();
                return parent instanceof ViewGroup && parentMatcher.matches(parent)
                        && view.equals(((ViewGroup) parent).getChildAt(position));
            }
        };
    }
}
