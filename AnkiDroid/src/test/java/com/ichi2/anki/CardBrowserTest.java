package com.ichi2.anki;

import android.content.Intent;

import com.ichi2.libanki.Note;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

import javax.annotation.CheckReturnValue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@RunWith(AndroidJUnit4.class)
public class CardBrowserTest extends RobolectricTest {

    @Test
    public void browserIsNotInitiallyInMultiSelectModeWithNoCards() {
        CardBrowser browser = getBrowserWithNoCards();
        assertThat(browser.isInMultiSelectMode(), is(false));
    }

    @Test
    public void browserIsNotInitiallyInMultiSelectModeWithCards() {
        CardBrowser browser = getBrowserWithMultipleNotes();
        assertThat(browser.isInMultiSelectMode(), is(false));
    }

    private CardBrowser getBrowserWithMultipleNotes() {
        return getBrowserWithNotes(3);
    }


    private CardBrowser getBrowserWithNotes(int count) {
        for(int i = 0; i < count; i ++) {
            addNote(Integer.toString(i));
        }
        ActivityController multimediaController = Robolectric.buildActivity(CardBrowser.class, new Intent())
                .create().start().resume().visible();
        return (CardBrowser) multimediaController.get();
    }


    private void addNote(String value) {
        Note n = getCol().newNote();
        n.setField(0, value);
        if (getCol().addNote(n) != 1) {
            throw new IllegalStateException("card was not added");
        }
    }

    @CheckReturnValue
    private CardBrowser getBrowserWithNoCards() {
        ActivityController multimediaController = Robolectric.buildActivity(CardBrowser.class, new Intent())
                .create().start().resume().visible();
        return (CardBrowser) multimediaController.get();
    }
}
