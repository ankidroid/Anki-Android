package com.ichi2.anki;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import androidx.test.ext.junit.runners.AndroidJUnit4;

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
                "<div><code id=typeans><span class=\"typeGood\">hello</span>✔</code></div>\n" +
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
                "<div><code id=typeans><span class=\"typeBad\">hello</span><br>&darr;<br><span class=\"typeMissed\">xyzzy$$$22</span></code></div>\n" +
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
                "<div><code id=typeans><span class=\"typeMissed\">hello</span></code></div>\n" +
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
                "<div><code id=typeans><span class=\"typeGood\">$!</span>✔</code></div>\n" +
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
                "<div><code id=typeans><span class=\"typeBad\">$!</span><br>&darr;<br><span class=\"typeMissed\">hello</span></code></div>\n" +
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
                "<div><code id=typeans><span class=\"typeMissed\">$!</span></code></div>\n" +
                "\n" +
                "<hr id=answer>\n" +
                "\n" +
                "$!";
        // Make sure $! as typed shows up as $!
        assertEquals(expectedOutput, typeAnsAnswerFilter(buf, "", "$!"));
    }
}