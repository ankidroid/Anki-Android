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

    //Testing DeckNameComparator by sorting an array of deck names.
    @Test
    public void sortDeckNames() {
        String[] deckNames = new String[]{"AA", "ab", "BB", "aa", "aa::bb", "aa::ab", "aa::ab::Aa", "aa::ab::aB", "aa::ab:bB"};
        sort(deckNames, deckNameComparator);

        assertThat(deckNames, is(new String[]{"AA", "aa", "aa::ab", "aa::ab::Aa", "aa::ab::aB", "aa::ab:bB", "aa::bb", "ab", "BB"}));
    }
}
