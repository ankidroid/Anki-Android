//noinspection MissingCopyrightHeader #8659

package com.ichi2.utils

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import java.util.Arrays.sort

class DeckNameComparatorTest {
    private var mDeckNameComparator: DeckNameComparator? = null

    @Before
    fun setUp() {
        mDeckNameComparator = DeckNameComparator()
    }

    // Testing DeckNameComparator by sorting an array of deck names.
    @Test
    fun sortDeckNames() {
        val deckNames = arrayOf("AA", "ab", "BB", "aa", "aa::bb", "aa::ab", "aa::ab::Aa", "aa::ab::aB", "aa::ab:bB")
        sort(deckNames, mDeckNameComparator)
        assertThat(deckNames, equalTo(arrayOf("AA", "aa", "aa::ab", "aa::ab::Aa", "aa::ab::aB", "aa::ab:bB", "aa::bb", "ab", "BB")))
    }
}
