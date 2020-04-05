package com.ichi2.libanki;

import com.ichi2.anki.RobolectricTest;
import com.ichi2.libanki.exception.NoSuchDeckException;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class CardsTimeLimitIntegrationTest extends RobolectricTest {

    private final RoboelectricBackend backend = new RoboelectricBackend();

    @Test
    public void timeLimitIsReadFromJson() {
        Card c = backend.cardWithDeckOptionsMaxTaken(30);

        int actualValue = c.timeLimit();

        assertThat("The conf 'maxTaken' value should be used if available", actualValue, is(30 * 1000));
    }


    @Test
    public void noTimeLimitReturnsDefaultValue() {
        int defaultLimit = backend.getDefaultAnkiTimeLimitInMs();

        Card c = backend.cardWithNoDeckOptionsMaxTaken();

        int actualValue = c.timeLimit();

        assertThat("A default timeLimit() (returned when no value is available) should equal the default conf value",
                actualValue,
                is(defaultLimit));
    }

    @Test
    public void timeLimitErrorReturnsDefault() {
        //This one is arguable, in many cases we want an exception if the col is not accessible.
        //Since this is a UI feature, I'd rather return a sensible default then kill the app.
        Card c = backend.getCardWithExceptionGettingTimeLimit();

        int actualInMs = c.timeLimit();

        int expected = backend.getDefaultAnkiTimeLimitInMs();
        assertThat("Problematic timeLimit() should return default", actualInMs, is(expected));
    }

    @Test
    public void noTimeLimitDefaultIsCorrect() {
        int actualDefault = backend.getDefaultAnkiTimeLimitInMs();

        assertThat("Default time limit should be 60 seconds",actualDefault , is(60000));
    }


    private class RoboelectricBackend {
        public Card cardWithDeckOptionsMaxTaken(int i) {
            Note n = CardsTimeLimitIntegrationTest.super.addNote(i);
            Card c = n.cards().get(0);
            getCol().getDecks().confForDid(c.getDid()).put("maxTaken", i);
            return c;
        }

        public int getDefaultAnkiTimeLimitInMs() {
            return getCol().getDecks().confForDid(1).getInt("maxTaken") * 1000;
        }


        public Card getCardWithExceptionGettingTimeLimit() {
            Card ret = cardWithDeckOptionsMaxTaken(1);
            try {
                getCol().getDecks().removeDeckOptions(1);
                assertThat(getCol().getDecks().hasDeckOptions(ret.getDid()), is(false));
            } catch (NoSuchDeckException e) {
                throw new AssertionError(e);
            }

            return ret;
        }


        public Card cardWithNoDeckOptionsMaxTaken() {
            Card ret = cardWithDeckOptionsMaxTaken(1);
            getCol().getDecks().confForDid(ret.getODid() == 0 ? ret.getDid() : ret.getODid()).put("maxTaken", null);
            return ret;
        }
    }
}
