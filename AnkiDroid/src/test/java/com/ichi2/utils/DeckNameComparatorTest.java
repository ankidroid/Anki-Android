package com.ichi2.utils;

import org.junit.Before;
import org.junit.Test;

import static java.util.Arrays.sort;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class DeckNameComparatorTest {
    private DeckNameComparator deckNameComparator;

    @Before
    public void setUp() {
        deckNameComparator = new DeckNameComparator();
    }

    //Testing DeckNameComparator by sorting an array of different string size.
    @Test
    public void sortDecksBySize() {
        String[] deckNames = new String[]{"aa", "aa::bb"};
        sort(deckNames, deckNameComparator);

        assertThat(deckNames, is(new String[] {"aa", "aa::bb"}));
    }

    //Testing DeckNameComparator by sorting an array of equal string length with both cases.
    @Test
    public void sortDecks() {
        String[] deckNames = new String[]{"AA", "ab", "BB"};
        sort(deckNames, deckNameComparator);

        assertThat(deckNames, is(new String[]{"AA", "ab", "BB"}));
    }
}
