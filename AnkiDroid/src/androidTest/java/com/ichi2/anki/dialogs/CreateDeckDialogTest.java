package com.ichi2.anki.dialogs;

import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.NoteEditor;
import com.ichi2.anki.R;

import org.junit.Test;

import androidx.test.core.app.ActivityScenario;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

public class CreateDeckDialogTest {

    @Test
    public void testDialogInDeckPicker() {
        // dialog in DeckPicker test
        try (ActivityScenario activityScenario = ActivityScenario.launch(DeckPicker.class)) {
            onView(withId(R.id.fab_main)).perform(click());
            onView(withId(R.id.add_deck_action)).perform(click());
            onView(withText(R.string.new_deck)).check(matches(isDisplayed()));
        }
    }
}