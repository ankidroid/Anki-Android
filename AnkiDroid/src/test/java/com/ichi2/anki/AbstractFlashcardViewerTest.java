//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki;

import android.app.Activity;
import android.content.Intent;

import com.ichi2.anki.cardviewer.ViewerCommand;
import com.ichi2.libanki.Note;
import com.ichi2.testutils.AnkiAssert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.anki.AbstractFlashcardViewer.WebViewSignalParserUtils.ANSWER_ORDINAL_1;
import static com.ichi2.anki.AbstractFlashcardViewer.WebViewSignalParserUtils.ANSWER_ORDINAL_2;
import static com.ichi2.anki.AbstractFlashcardViewer.WebViewSignalParserUtils.ANSWER_ORDINAL_3;
import static com.ichi2.anki.AbstractFlashcardViewer.WebViewSignalParserUtils.ANSWER_ORDINAL_4;
import static com.ichi2.anki.AbstractFlashcardViewer.WebViewSignalParserUtils.RELINQUISH_FOCUS;
import static com.ichi2.anki.AbstractFlashcardViewer.WebViewSignalParserUtils.SHOW_ANSWER;
import static com.ichi2.anki.AbstractFlashcardViewer.WebViewSignalParserUtils.SIGNAL_NOOP;
import static com.ichi2.anki.AbstractFlashcardViewer.WebViewSignalParserUtils.TYPE_FOCUS;
import static com.ichi2.anki.AbstractFlashcardViewer.WebViewSignalParserUtils.getSignalFromUrl;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class AbstractFlashcardViewerTest extends RobolectricTest {

    public static class NonAbstractFlashcardViewer extends AbstractFlashcardViewer {
        public Integer mAnswered;
        private int mLastTime = 0;

        @Override
        protected void setTitle() {
        }


        @Override
        protected void performReload() {
            // intentionally blank
        }


        @Override
        protected void answerCard(int ease) {
            super.answerCard(ease);
            this.mAnswered = ease;
        }


        @Override
        protected long getElapsedRealTime() {
            mLastTime += AnkiDroidApp.getSharedPrefs(getBaseContext()).getInt(DOUBLE_TAP_TIME_INTERVAL, DEFAULT_DOUBLE_TAP_TIME_INTERVAL);
            return mLastTime;
        }


        public String getTypedInput() {
            return super.getTypedInputText();
        }
    }

    @Test
    public void relinquishFocusIsParsedFromSignal() {
        String url = "signal:relinquishFocus"; //confirmed data from JS transition via debugger.
        assertEquals(RELINQUISH_FOCUS, getSignalFromUrl(url));
    }

    @Test
    public void typeFocusIsParsedFromSignal() {
        String url = "signal:typefocus";
        assertEquals(TYPE_FOCUS, getSignalFromUrl(url));
    }

    @Test
    public void showAnswerIsParsedFromSignal() {
        String url = "signal:show_answer";
        assertEquals(SHOW_ANSWER, getSignalFromUrl(url));
    }

    //I'd love to turn these int parameterised tests, but it feels like more overhead for just 4 tests.
    @Test
    public void ease1IsParsedFromSignal() {
        String url = "signal:answer_ease1";
        assertEquals(ANSWER_ORDINAL_1, getSignalFromUrl(url));
    }
    @Test
    public void ease2IsParsedFromSignal() {
        String url = "signal:answer_ease2";
        assertEquals(ANSWER_ORDINAL_2, getSignalFromUrl(url));
    }
    @Test
    public void ease3IsParsedFromSignal() {
        String url = "signal:answer_ease3";
        assertEquals(ANSWER_ORDINAL_3, getSignalFromUrl(url));
    }
    @Test
    public void ease4IsParsedFromSignal() {
        String url = "signal:answer_ease4";
        assertEquals(ANSWER_ORDINAL_4, getSignalFromUrl(url));
    }

    @Test
    public void invalidEaseIsParsedFromSignal() {
        String url = "signal:answer_ease0";
        assertEquals(SIGNAL_NOOP, getSignalFromUrl(url));
    }

    @Test
    public void invalidEncodingDoesNotCrash() {
        //#5944 - input came in as: 'typeblurtext:%'. We've fixed the encoding, but want to make sure there's no crash
        // as JS can call this function with arbitrary data.
        String url = "typeblurtext:%";

        NonAbstractFlashcardViewer nafv = getViewer();
        AnkiAssert.assertDoesNotThrow(() -> nafv.handleUrlFromJavascript(url));
    }

    @Test
    public void validEncodingSetsAnswerCorrectly() {
        //你好%
        String url = "typeblurtext:%E4%BD%A0%E5%A5%BD%25";
        NonAbstractFlashcardViewer nafv = getViewer();

        nafv.handleUrlFromJavascript(url);

        assertThat(nafv.getTypedInput(), is("你好%"));
    }

    @Test
    public void testEditingCardChangesTypedAnswer() {
        // 7363
       addNoteUsingBasicTypedModel("Hello", "World");

        NonAbstractFlashcardViewer nafv = getViewer();

        assertThat(nafv.getCorrectTypedAnswer(), is("World"));

        waitForAsyncTasksToComplete();

        AbstractFlashcardViewer.setEditorCard(nafv.mCurrentCard);

        Note note = nafv.mCurrentCard.note();
        note.setField(1, "David");

        nafv.onActivityResult(AbstractFlashcardViewer.EDIT_CURRENT_CARD, Activity.RESULT_OK, new Intent());

        waitForAsyncTasksToComplete();

        assertThat(nafv.getCorrectTypedAnswer(), is("David"));
    }

    @Test
    public void testEditingCardChangesTypedAnswerOnDisplayAnswer() {
        // 7363
        addNoteUsingBasicTypedModel("Hello", "World");

        NonAbstractFlashcardViewer nafv = getViewer();

        assertThat(nafv.getCorrectTypedAnswer(), is("World"));

        nafv.displayCardAnswer();

        assertThat(nafv.getCardContent(), containsString("World"));

        waitForAsyncTasksToComplete();

        AbstractFlashcardViewer.setEditorCard(nafv.mCurrentCard);

        Note note = nafv.mCurrentCard.note();
        note.setField(1, "David");

        nafv.onActivityResult(AbstractFlashcardViewer.EDIT_CURRENT_CARD, Activity.RESULT_OK, new Intent());

        waitForAsyncTasksToComplete();

        assertThat(nafv.getCorrectTypedAnswer(), is("David"));
        assertThat(nafv.getCardContent(), not(containsString("World")));
        assertThat(nafv.getCardContent(), containsString("David"));
    }

    @Test
    public void testCommandPerformsAnswerCard() {
        // Regression for #8527/#8572
        // Note: Couldn't get a spy working, so overriding the method
        NonAbstractFlashcardViewer viewer = getViewer();

        assertThat("Displaying question", viewer.isDisplayingAnswer(), is(false));
        viewer.executeCommand(ViewerCommand.COMMAND_FLIP_OR_ANSWER_BETTER_THAN_RECOMMENDED);

        assertThat("Displaying answer", viewer.isDisplayingAnswer(), is(true));

        viewer.executeCommand(ViewerCommand.COMMAND_FLIP_OR_ANSWER_BETTER_THAN_RECOMMENDED);

        assertThat(viewer.mAnswered, notNullValue());
    }

    private NonAbstractFlashcardViewer getViewer() {
        @NonNull Note n = getCol().newNote();
        n.setField(0, "a");
        getCol().addNote(n);

        ActivityController<NonAbstractFlashcardViewer> multimediaController = Robolectric.buildActivity(NonAbstractFlashcardViewer.class, new Intent())
                .create().start().resume().visible();
        saveControllerForCleanup((multimediaController));

        NonAbstractFlashcardViewer viewer = multimediaController.get();
        viewer.onCollectionLoaded(getCol());
        viewer.loadInitialCard();
        // Without this, AbstractFlashcardViewer.mCard is still null, and RobolectricTest.tearDown executes before
        // AsyncTasks spawned by by loading the viewer finish. Is there a way to synchronize these things while under test?
        advanceRobolectricLooperWithSleep();
        advanceRobolectricLooperWithSleep();
        return viewer;
    }
}
