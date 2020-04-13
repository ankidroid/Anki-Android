package com.ichi2.anki;

import android.content.Intent;

import com.ichi2.libanki.Note;
import com.ichi2.testutils.AnkiAssert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.LooperMode;

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

import static android.os.Looper.getMainLooper;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

@RunWith(AndroidJUnit4.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class AbstractFlashcardViewerTest extends RobolectricTest {

    public static class NonAbstractFlashcardViewer extends AbstractFlashcardViewer {
        @Override
        protected void setTitle() {
        }

        public String getTypedInput() {
            return super.getTypedInputText();
        }
    }

    public String typeAnsAnswerFilter(String buf, String userAnswer, String correctAnswer) {
        NonAbstractFlashcardViewer nafv = new NonAbstractFlashcardViewer();
        return nafv.typeAnsAnswerFilter(buf, userAnswer, correctAnswer);
    }

    @Test
    public void testTypeAnsAnswerFilterNormalCorrect() {
        String buf = "<style>.card {\n" +
                " font-family: arial;\n" +
                " font-size: 20px;\n" +
                " text-align: center;\n" +
                " color: black;\n" +
                " background-color: white;\n" +
                "}\n" +
                "</style>Type in hello\n" +
                "[[type:Back]]\n" +
                "\n" +
                "<hr id=answer>\n" +
                "\n" +
                "$!";

        String expectedOutput = "<style>.card {\n" +
                " font-family: arial;\n" +
                " font-size: 20px;\n" +
                " text-align: center;\n" +
                " color: black;\n" +
                " background-color: white;\n" +
                "}\n" +
                "</style>Type in hello\n" +
                "<div><code id=\"typeans\"><span class=\"typeGood\">hello</span><span id=\"typecheckmark\">✔</span></code></div>\n" +
                "\n" +
                "<hr id=answer>\n" +
                "\n" +
                "$!";

        assertEquals(expectedOutput, typeAnsAnswerFilter(buf, "hello", "hello"));
    }

    @Test
    public void testTypeAnsAnswerFilterNormalIncorrect()  {
        String buf = "<style>.card {\n" +
                " font-family: arial;\n" +
                " font-size: 20px;\n" +
                " text-align: center;\n" +
                " color: black;\n" +
                " background-color: white;\n" +
                "}\n" +
                "</style>Type in hello\n" +
                "[[type:Back]]\n" +
                "\n" +
                "<hr id=answer>\n" +
                "\n" +
                "hello";

        String expectedOutput = "<style>.card {\n" +
                " font-family: arial;\n" +
                " font-size: 20px;\n" +
                " text-align: center;\n" +
                " color: black;\n" +
                " background-color: white;\n" +
                "}\n" +
                "</style>Type in hello\n" +
                "<div><code id=\"typeans\"><span class=\"typeBad\">hello</span><br><span id=\"typearrow\">&darr;</span><br><span class=\"typeMissed\">xyzzy$$$22</span></code></div>\n" +
                "\n" +
                "<hr id=answer>\n" +
                "\n" +
                "hello";
        // Make sure $! as typed shows up as $!
        assertEquals(expectedOutput, typeAnsAnswerFilter(buf, "hello", "xyzzy$$$22"));
    }

    @Test
    public void testTypeAnsAnswerFilterNormalEmpty() {
        String buf = "<style>.card {\n" +
                " font-family: arial;\n" +
                " font-size: 20px;\n" +
                " text-align: center;\n" +
                " color: black;\n" +
                " background-color: white;\n" +
                "}\n" +
                "</style>Type in hello\n" +
                "[[type:Back]]\n" +
                "\n" +
                "<hr id=answer>\n" +
                "\n" +
                "hello";

        String expectedOutput = "<style>.card {\n" +
                " font-family: arial;\n" +
                " font-size: 20px;\n" +
                " text-align: center;\n" +
                " color: black;\n" +
                " background-color: white;\n" +
                "}\n" +
                "</style>Type in hello\n" +
                "<div><code id=\"typeans\"><span class=\"typeMissed\">hello</span></code></div>\n" +
                "\n" +
                "<hr id=answer>\n" +
                "\n" +
                "hello";
        // Make sure $! as typed shows up as $!
        assertEquals(expectedOutput, typeAnsAnswerFilter(buf, "", "hello"));
    }

    @Test
    public void testTypeAnsAnswerFilterDollarSignsCorrect() {
        String buf = "<style>.card {\n" +
                " font-family: arial;\n" +
                " font-size: 20px;\n" +
                " text-align: center;\n" +
                " color: black;\n" +
                " background-color: white;\n" +
                "}\n" +
                "</style>Type in $!\n" +
                "[[type:Back]]\n" +
                "\n" +
                "<hr id=answer>\n" +
                "\n" +
                "$!";

        String expectedOutput = "<style>.card {\n" +
                " font-family: arial;\n" +
                " font-size: 20px;\n" +
                " text-align: center;\n" +
                " color: black;\n" +
                " background-color: white;\n" +
                "}\n" +
                "</style>Type in $!\n" +
                "<div><code id=\"typeans\"><span class=\"typeGood\">$!</span><span id=\"typecheckmark\">✔</span></code></div>\n" +
                "\n" +
                "<hr id=answer>\n" +
                "\n" +
                "$!";
        // Make sure $! as typed shows up as $!
        assertEquals(expectedOutput, typeAnsAnswerFilter(buf, "$!", "$!"));
    }

    @Test
    public void testTypeAnsAnswerFilterDollarSignsIncorrect() {
        String buf = "<style>.card {\n" +
                " font-family: arial;\n" +
                " font-size: 20px;\n" +
                " text-align: center;\n" +
                " color: black;\n" +
                " background-color: white;\n" +
                "}\n" +
                "</style>Type in $!\n" +
                "[[type:Back]]\n" +
                "\n" +
                "<hr id=answer>\n" +
                "\n" +
                "$!";

        String expectedOutput = "<style>.card {\n" +
                " font-family: arial;\n" +
                " font-size: 20px;\n" +
                " text-align: center;\n" +
                " color: black;\n" +
                " background-color: white;\n" +
                "}\n" +
                "</style>Type in $!\n" +
                "<div><code id=\"typeans\"><span class=\"typeBad\">$!</span><br><span id=\"typearrow\">&darr;</span><br><span class=\"typeMissed\">hello</span></code></div>\n" +
                "\n" +
                "<hr id=answer>\n" +
                "\n" +
                "$!";
        // Make sure $! as typed shows up as $!
        assertEquals(expectedOutput, typeAnsAnswerFilter(buf, "$!", "hello"));
    }

    @Test
    public void testTypeAnsAnswerFilterDollarSignsEmpty() {
        String buf = "<style>.card {\n" +
                " font-family: arial;\n" +
                " font-size: 20px;\n" +
                " text-align: center;\n" +
                " color: black;\n" +
                " background-color: white;\n" +
                "}\n" +
                "</style>Type in $!\n" +
                "[[type:Back]]\n" +
                "\n" +
                "<hr id=answer>\n" +
                "\n" +
                "$!";

        String expectedOutput = "<style>.card {\n" +
                " font-family: arial;\n" +
                " font-size: 20px;\n" +
                " text-align: center;\n" +
                " color: black;\n" +
                " background-color: white;\n" +
                "}\n" +
                "</style>Type in $!\n" +
                "<div><code id=\"typeans\"><span class=\"typeMissed\">$!</span></code></div>\n" +
                "\n" +
                "<hr id=answer>\n" +
                "\n" +
                "$!";
        // Make sure $! as typed shows up as $!
        assertEquals(expectedOutput, typeAnsAnswerFilter(buf, "", "$!"));
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


    private NonAbstractFlashcardViewer getViewer() {
        Note n = getCol().newNote();
        n.setField(0, "a");
        getCol().addNote(n);

        ActivityController multimediaController = Robolectric.buildActivity(NonAbstractFlashcardViewer.class, new Intent())
                .create().start().resume().visible();

        NonAbstractFlashcardViewer viewer = (NonAbstractFlashcardViewer) multimediaController.get();
        viewer.onCollectionLoaded(getCol());
        viewer.loadInitialCard();
        // Without this, AbstractFlashcardViewer.mCard is still null, and RobolectricTest.tearDown executes before
        // AsyncTasks spawned by by loading the viewer finish. Is there a way to synchronize these things while under test?
        try { Thread.sleep(2000); } catch (Throwable t) { /* nothing */ }
        shadowOf(getMainLooper()).idle();
        return viewer;
    }
}