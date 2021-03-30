package com.ichi2.utils;

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class DeckNameComparatorTest {
    private DeckNameComparator deckNameComparator;

    @Before
    public void setUp(){
        deckNameComparator = new DeckNameComparator();
    }

    //Tests the comparision of two strings Lexicographically (Dictionary Order)
    @Test
    public void compareLexicographically() {
       assertThat(deckNameComparator.compare("Aa", "Az"), is(-25));
    }

    //Tests the comparision of two strings by Length
    @Test
    public void compareByLength() {
        assertThat(deckNameComparator.compare("AnkiDroid", "Anki"), is(5));
    }

    //Tests the comparision of two strings when they are equal
    @Test
    public void compareEqual() {
        assertThat(deckNameComparator.compare("AnkiDroid", "AnkiDroid"), is(0));
    }
}