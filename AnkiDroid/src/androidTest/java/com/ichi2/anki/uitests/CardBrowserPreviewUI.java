package com.ichi2.anki.uitests;


import android.Manifest;

import androidx.test.espresso.DataInteraction;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.GrantPermissionRule;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.azimolabs.conditionwatcher.ConditionWatcher;
import com.ichi2.anki.IntentHandler;
import com.ichi2.anki.R;
import com.ichi2.anki.tests.Shared;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.pressImeActionButton;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.is;

@androidx.test.filters.LargeTest
@SuppressWarnings({"PMD.ExcessiveMethodLength"})
@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4.class)
public class CardBrowserPreviewUI {

    @Rule
    public GrantPermissionRule mRuntimePermissionRule =
            GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

    @Rule
    public ActivityTestRule<IntentHandler> mActivityTestRule = new ActivityTestRule<>(IntentHandler.class);


    @Test
    public void cardBrowserPreview3UI() throws Exception {
        ViewInteraction appCompatImageButton = onView(
                Matchers.allOf(withContentDescription("Navigate up"),
                        childAtPosition(
                                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.toolbar),
                                        childAtPosition(
                                                ViewMatchers.withId(com.ichi2.anki.R.id.deckpicker_view),
                                                0)),
                                0),
                        isDisplayed()));
        appCompatImageButton.perform(click());

        ViewInteraction navigationMenuItemView = onView(
                Matchers.allOf(childAtPosition(
                        Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.design_navigation_view),
                                childAtPosition(
                                        ViewMatchers.withId(com.ichi2.anki.R.id.navdrawer_items_container),
                                        0)),
                        2),
                        isDisplayed()));
        navigationMenuItemView.perform(click());

        ConditionWatcher.waitForCondition(new Shared.ViewItemWaitingInstruction(com.ichi2.anki.R.id.action_add_card_from_card_browser));
        ViewInteraction actionMenuItemView = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.action_add_card_from_card_browser), withContentDescription("Add note"),
                        childAtPosition(
                                childAtPosition(
                                        ViewMatchers.withId(com.ichi2.anki.R.id.toolbar),
                                        3),
                                0),
                        isDisplayed()));
        actionMenuItemView.perform(click());

        // Added a sleep statement to match the app's execution delay.
        // The recommended way to handle such scenarios is to use Espresso idling resources:
        // https://google.github.io/android-testing-support-library/docs/espresso/idling-resource/index.html
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ViewInteraction appCompatSpinner = onView(
                Matchers.allOf(ViewMatchers.withId(R.id.note_type_spinner),
                        childAtPosition(
                                childAtPosition(
                                        ViewMatchers.withId(R.id.CardEditorLayout),
                                        0),
                                1)));
        appCompatSpinner.perform(scrollTo(), click());

        // Added a sleep statement to match the app's execution delay.
        // The recommended way to handle such scenarios is to use Espresso idling resources:
        // https://google.github.io/android-testing-support-library/docs/espresso/idling-resource/index.html
        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        DataInteraction appCompatCheckedTextView = onData(anything())
                .inAdapterView(childAtPosition(
                        withClassName(is("android.widget.PopupWindow$PopupBackgroundView")),
                        0))
                .atPosition(1);
        appCompatCheckedTextView.perform(click());

        ConditionWatcher.waitForCondition(new Shared.ViewItemWaitingInstruction(com.ichi2.anki.R.id.id_note_editText));
        ViewInteraction fieldEditText = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.id_note_editText), withContentDescription("Front"),
                        childAtPosition(
                                childAtPosition(
                                        ViewMatchers.withId(com.ichi2.anki.R.id.CardEditorEditFieldsLayout),
                                        1),
                                0)));
        String cardId = "test" + Long.toString(System.currentTimeMillis());
        fieldEditText.perform(scrollTo(), replaceText(cardId), closeSoftKeyboard());

        ViewInteraction fieldEditText2 = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.id_note_editText), withContentDescription("Back"),
                        childAtPosition(
                                childAtPosition(
                                        ViewMatchers.withId(com.ichi2.anki.R.id.CardEditorEditFieldsLayout),
                                        3),
                                0)));
        fieldEditText2.perform(scrollTo(), replaceText("back"), closeSoftKeyboard());

        ViewInteraction actionMenuItemView2 = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.action_save), withContentDescription("Save"),
                        childAtPosition(
                                childAtPosition(
                                        ViewMatchers.withId(com.ichi2.anki.R.id.toolbar),
                                        4),
                                0),
                        isDisplayed()));
        actionMenuItemView2.perform(click());

        pressBack();

        ConditionWatcher.waitForCondition(new Shared.ViewItemWaitingInstruction(com.ichi2.anki.R.id.action_search));
        ViewInteraction actionMenuItemView3 = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.action_search), withContentDescription("Search"),
                        childAtPosition(
                                childAtPosition(
                                        ViewMatchers.withId(com.ichi2.anki.R.id.toolbar),
                                        3),
                                1),
                        isDisplayed()));
        actionMenuItemView3.perform(click());

        ConditionWatcher.waitForCondition(new Shared.ViewItemWaitingInstruction(com.ichi2.anki.R.id.search_src_text));
        ViewInteraction searchAutoComplete = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.search_src_text),
                        childAtPosition(
                                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.search_plate),
                                        childAtPosition(
                                                ViewMatchers.withId(com.ichi2.anki.R.id.search_edit_frame),
                                                1)),
                                0),
                        isDisplayed()));
        searchAutoComplete.perform(replaceText(cardId + "bad"), closeSoftKeyboard());

        ViewInteraction searchAutoComplete2 = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.search_src_text), withText(cardId + "bad"),
                        childAtPosition(
                                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.search_plate),
                                        childAtPosition(
                                                ViewMatchers.withId(com.ichi2.anki.R.id.search_edit_frame),
                                                1)),
                                0),
                        isDisplayed()));
        searchAutoComplete2.perform(pressImeActionButton());

        openActionBarOverflowOrOptionsMenu(getInstrumentation().getTargetContext());
        ViewInteraction linearLayout42 = onView(
                Matchers.allOf(withText("Preview"),
                        childAtPosition(
                                IsInstanceOf.instanceOf(android.widget.FrameLayout.class),
                                0),
                        isDisplayed()));
        linearLayout42.check(doesNotExist());
        pressBack();

        ConditionWatcher.waitForCondition(new Shared.ViewItemWaitingInstruction(com.ichi2.anki.R.id.search_close_btn));
        ViewInteraction appCompatImageView = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.search_close_btn), withContentDescription("Clear query"),
                        childAtPosition(
                                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.search_plate),
                                        childAtPosition(
                                                ViewMatchers.withId(com.ichi2.anki.R.id.search_edit_frame),
                                                1)),
                                1),
                        isDisplayed()));
        appCompatImageView.perform(click());

        ViewInteraction searchAutoComplete3 = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.search_src_text),
                        childAtPosition(
                                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.search_plate),
                                        childAtPosition(
                                                ViewMatchers.withId(com.ichi2.anki.R.id.search_edit_frame),
                                                1)),
                                0),
                        isDisplayed()));
        searchAutoComplete3.perform(replaceText(cardId), closeSoftKeyboard());

        ViewInteraction searchAutoComplete4 = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.search_src_text), withText(cardId),
                        childAtPosition(
                                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.search_plate),
                                        childAtPosition(
                                                ViewMatchers.withId(com.ichi2.anki.R.id.search_edit_frame),
                                                1)),
                                0),
                        isDisplayed()));
        searchAutoComplete4.perform(pressImeActionButton());
        Thread.sleep(200);

        openActionBarOverflowOrOptionsMenu(getInstrumentation().getTargetContext());
        Thread.sleep(200);

        ViewInteraction appCompatTextView = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.title), withText("Preview"),
                        isDisplayed()));
        Thread.sleep(200);

        appCompatTextView.perform(click());
        Thread.sleep(200);

        ConditionWatcher.waitForCondition(new Shared.ViewItemWaitingInstruction(com.ichi2.anki.R.id.flashcard_layout_ease1));
        ViewInteraction textView = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.nextTime1), withText("-"),
                        isDisplayed()));
        textView.check(matches(withText("-")));

        ViewInteraction textView2 = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.nextTime2), withText(">"),
                        isDisplayed()));
        textView2.check(matches(withText(">")));

        ViewInteraction linearLayout = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.flashcard_layout_ease2),
                        childAtPosition(
                                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.answer_options_layout),
                                        childAtPosition(
                                                ViewMatchers.withId(com.ichi2.anki.R.id.bottom_area_layout),
                                                1)),
                                2),
                        isDisplayed()));
        linearLayout.perform(click());

        ViewInteraction textView3 = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.nextTime1), withText("<"),
                        isDisplayed()));
        textView3.check(matches(withText("<")));

        ViewInteraction textView4 = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.nextTime2), withText(">"),
                        isDisplayed()));
        textView4.check(matches(withText(">")));

        ViewInteraction linearLayout2 = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.flashcard_layout_ease2),
                        isDisplayed()));
        linearLayout2.perform(click());

        ViewInteraction linearLayout3 = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.flashcard_layout_ease2),
                        isDisplayed()));
        linearLayout3.perform(click());

        ViewInteraction textView5 = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.nextTime1), withText("<"),
                        isDisplayed()));
        textView5.check(matches(withText("<")));

        ViewInteraction textView6 = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.nextTime2), withText("-"),
                        isDisplayed()));
        textView6.check(matches(withText("-")));

        ViewInteraction linearLayout4 = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.flashcard_layout_ease1),
                        isDisplayed()));
        linearLayout4.perform(click());

        ViewInteraction linearLayout5 = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.flashcard_layout_ease1),
                        isDisplayed()));
        linearLayout5.perform(click());

        ViewInteraction linearLayout6 = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.flashcard_layout_ease1),
                        isDisplayed()));
        linearLayout6.perform(click());

        ViewInteraction textView8 = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.nextTime1), withText("-"),
                        isDisplayed()));
        textView8.check(matches(withText("-")));

        ViewInteraction textView9 = onView(
                Matchers.allOf(ViewMatchers.withId(com.ichi2.anki.R.id.nextTime2), withText(">"),
                        isDisplayed()));
        textView9.check(matches(withText(">")));
        pressBack();

        ConditionWatcher.waitForCondition(new Shared.ViewItemWaitingInstruction(com.ichi2.anki.R.id.search_src_text));
        ViewInteraction searchAutoComplete42 = onView(
                Matchers.allOf(ViewMatchers.withId(R.id.search_src_text),
                        isDisplayed()));
        searchAutoComplete42.check(matches(withText(cardId)));
        Assert.assertNotNull("Did not make it to the end of the UI run", searchAutoComplete42);

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
