//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.LocaleList;
import android.webkit.RenderProcessGoneDetail;

import com.ichi2.anki.cardviewer.ViewerCommand;
import com.ichi2.anki.reviewer.AutomaticAnswer;
import com.ichi2.anki.reviewer.AutomaticAnswerAction;
import com.ichi2.anki.reviewer.AutomaticAnswerSettings;
import com.ichi2.anki.servicelayer.LanguageHintService;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Note;
import com.ichi2.testutils.AnkiAssert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

import java.util.Locale;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
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
import static com.ichi2.libanki.StdModels.BASIC_TYPING_MODEL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

@RequiresApi(api = Build.VERSION_CODES.O) //getImeHintLocales, toLanguageTags, onRenderProcessGone, RenderProcessGoneDetail
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

        public String getHintLocale() {
            LocaleList imeHintLocales = this.mAnswerField.getImeHintLocales();
            if (imeHintLocales == null) {
                return null;
            }
            return imeHintLocales.toLanguageTags();
        }

        public boolean hasAutomaticAnswerQueued() {
            return mAutomaticAnswer.getTimeoutHandler().hasMessages(0);
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

    @Test
    public void defaultLanguageIsNull() {
        NonAbstractFlashcardViewer viewer = getViewer();
        assertThat(viewer.getHintLocale(), nullValue());
    }

    @Test
    public void typedLanguageIsSet() {
        Model withLanguage = BASIC_TYPING_MODEL.add(getCol(), "a");
        Model normal = BASIC_TYPING_MODEL.add(getCol(), "b");
        int typedField = 1; // BACK

        LanguageHintService.setLanguageHintForField(getCol().getModels(), withLanguage, typedField, new Locale("ja"));

        addNoteUsingModelName(withLanguage.getString("name"), "ichi", "ni");
        addNoteUsingModelName(normal.getString("name"), "one", "two");

        NonAbstractFlashcardViewer viewer = getViewer(false);

        assertThat("A model with a language hint (japanese) should use it", viewer.getHintLocale(), equalTo("ja"));

        showNextCard(viewer);

        assertThat("A default model should have no preference", viewer.getHintLocale(), nullValue());
    }

    @Test
    public void automaticAnswerDisabledProperty() {
        ActivityController<NonAbstractFlashcardViewer> controller = getViewerController(true);
        NonAbstractFlashcardViewer viewer = controller.get();
        assertThat("not disabled initially", viewer.mAutomaticAnswer.isDisabled(), is(false));
        controller.pause();
        assertThat("disabled after pause", viewer.mAutomaticAnswer.isDisabled(), is(true));
        controller.resume();
        assertThat("enabled after resume", viewer.mAutomaticAnswer.isDisabled(), is(false));
    }

    @Test
    public void noAutomaticAnswerAfterRenderProcessGoneAndPaused_issue9632() {
        ActivityController<NonAbstractFlashcardViewer> controller = getViewerController(true);
        NonAbstractFlashcardViewer viewer = controller.get();
        viewer.mAutomaticAnswer = new AutomaticAnswer(viewer, new AutomaticAnswerSettings(AutomaticAnswerAction.BURY_CARD, true, 5, 5));
        viewer.executeCommand(ViewerCommand.COMMAND_SHOW_ANSWER);
        assertThat("messages after flipping card", viewer.hasAutomaticAnswerQueued(), equalTo(true));
        controller.pause();
        assertThat("disabled after pause", viewer.mAutomaticAnswer.isDisabled(), is(true));
        assertThat("no auto answer after pause", viewer.hasAutomaticAnswerQueued(), equalTo(false));
        viewer.mOnRenderProcessGoneDelegate.onRenderProcessGone(viewer.getWebView(), mock(RenderProcessGoneDetail.class));
        assertThat("no auto answer after onRenderProcessGone when paused", viewer.hasAutomaticAnswerQueued(), equalTo(false));
    }


    private void showNextCard(NonAbstractFlashcardViewer viewer) {
        viewer.executeCommand(ViewerCommand.COMMAND_FLIP_OR_ANSWER_BETTER_THAN_RECOMMENDED);
        viewer.executeCommand(ViewerCommand.COMMAND_FLIP_OR_ANSWER_BETTER_THAN_RECOMMENDED);
    }


    @CheckResult
    private NonAbstractFlashcardViewer getViewer() {
        return getViewer(true);
    }

    @CheckResult
    private NonAbstractFlashcardViewer getViewer(boolean addCard) {
        return getViewerController(addCard).get();
    }

    @CheckResult
    private ActivityController<NonAbstractFlashcardViewer> getViewerController(boolean addCard) {
        if (addCard) {
            @NonNull Note n = getCol().newNote();
            n.setField(0, "a");
            getCol().addNote(n);
        }

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
        return multimediaController;
    }
}
