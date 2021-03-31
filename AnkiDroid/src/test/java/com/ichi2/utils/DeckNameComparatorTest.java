package com.ichi2.utils;

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

public class DeckNameComparatorTest {
    private DeckNameComparator deckNameComparator;

    @Before
    public void setUp() {
        deckNameComparator = new DeckNameComparator();
    }

    @Test
    public void compareLessThan() {
       assertThat(deckNameComparator.compare("aa", "aa:bb"), lessThan(0));
    }

    @Test
    public void compareGreaterThan() {
        assertThat(deckNameComparator.compare("aa:bb", "aa"), is(greaterThan(0)));
    }

    @Test
    public void compareCaseLessThan() {
        assertThat(deckNameComparator.compare("ab", "BB"), is(lessThan(0)));
    }

    @Test
    public void compareCaseGreaterThan() {
        assertThat(deckNameComparator.compare("AB", "Aa"), is(greaterThan(0)));
    }

    //Test to further check implementation of case insensitivity.
    @Test
    public void compareCaseInsensitivity() {
        assertThat(deckNameComparator.compare("a", "A"), is(0));
    }
}
