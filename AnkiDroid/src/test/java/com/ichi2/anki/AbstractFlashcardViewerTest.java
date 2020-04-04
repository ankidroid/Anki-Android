package com.ichi2.anki;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.anki.AbstractFlashcardViewer.WebViewSignalParserUtils.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AbstractFlashcardViewerTest extends RobolectricTest {

    public class NonabstractFlashcardViewer extends AbstractFlashcardViewer {
        @Override
        protected void setTitle() {
        }
    }

    public String typeAnsAnswerFilter(String buf, String userAnswer, String correctAnswer) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        NonabstractFlashcardViewer nafv = new NonabstractFlashcardViewer();

        Class[] argClasses = {String.class, String.class, String.class};
        Method method = AbstractFlashcardViewer.class.getDeclaredMethod("typeAnsAnswerFilter", argClasses);
        method.setAccessible(true);
        return (String) method.invoke(nafv, buf, userAnswer, correctAnswer);
    }

    @Test
    public void testTypeAnsAnswerFilterNormalCorrect() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
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
    public void testTypeAnsAnswerFilterNormalIncorrect() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
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
    public void testTypeAnsAnswerFilterNormalEmpty() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
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
    public void testTypeAnsAnswerFilterDollarSignsCorrect() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
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
    public void testTypeAnsAnswerFilterDollarSignsIncorrect() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
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
    public void testTypeAnsAnswerFilterDollarSignsEmpty() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
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
}