// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ayush <ayushdevraj9@gmail.com>

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardUtilsTest : RobolectricTest() {
    @Test
    fun getDeckIdForCard_regularDeck_returnsDid() {
        val note = addBasicNote("Front", "Back")
        val card = note.firstCard()

        assertThat("Card should be in a regular deck", card.oDid, equalTo(0L))

        val deckId = CardUtils.getDeckIdForCard(card)

        assertThat("Should return the card's did", deckId, equalTo(card.did))
    }

    @Test
    fun getDeckIdForCard_cramDeck_returnsODid() {
        val note = addBasicNote("Front", "Back")
        val card = note.firstCard()

        val filteredDid = addDynamicDeck("Filtered")
        col.sched.rebuildFilteredDeck(filteredDid)
        card.load()

        assertThat("Card should have oDid set in filtered deck", card.oDid != 0L, equalTo(true))

        val deckId = CardUtils.getDeckIdForCard(card)

        assertThat("Should return the original deck ID (oDid)", deckId, equalTo(card.oDid))
    }

    @Test
    fun getDeckIdForCard_zeroODid_returnsDid() {
        val note = addBasicNote("Front", "Back")
        val card = note.firstCard()

        assertThat("Card should be in a regular deck", card.oDid, equalTo(0L))

        val deckId = CardUtils.getDeckIdForCard(card)

        assertThat(deckId, equalTo(card.did))
    }
}
