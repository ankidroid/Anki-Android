// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Sanjay Sargam <sargamsanjaykumar@gmail.com>

package com.ichi2.anki.libanki

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class PythonTypesTest {
    @Test
    fun test_deckId_toString_default_deck() {
        val deckId: DeckId = 1L
        assertThat(deckId.toString(), equalTo("1"))
    }

    @Test
    fun test_deckId_toString_user_created_deck() {
        val deckId: DeckId = 1428219222352L
        assertThat(deckId.toString(), equalTo("1428219222352"))
    }

    @Test
    fun test_deckId_toString_large_number() {
        val deckId: DeckId = Long.MAX_VALUE
        assertThat(deckId.toString(), equalTo("9223372036854775807"))
    }

    @Test
    fun test_deckId_toString_minimum_value() {
        val deckId: DeckId = Long.MIN_VALUE
        assertThat(deckId.toString(), equalTo("-9223372036854775808"))
    }
}
