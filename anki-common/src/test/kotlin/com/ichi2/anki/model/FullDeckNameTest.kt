// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.model

import anki.search.SearchNode
import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FullDeckNameTest : InMemoryAnkiTest() {
    @Test
    fun `ordinary search finds the deck named filtered`() {
        val borrowedCard = addBasicNote("Borrowed card").firstCard()
        val deckId = addDeck("filtered")
        val card = addBasicNote("Card in the deck named filtered").firstCard()
        col.setDeck(listOf(card.id), deckId)
        val filteredDeckId = addDynamicDeck("Borrowed cards", search = "deck:Default")
        assertEquals(filteredDeckId, col.getCard(borrowedCard.id).did)

        val deckSearch: SearchNode = FullDeckName("filtered").toSearchNode()
        assertEquals(listOf(card.id), col.findCards(col.buildSearchString(listOf(deckSearch))))
    }

    @Test
    fun `stored-name search rejects the exact filtered keyword`() {
        assertNull(FullDeckName("filtered").toStoredNameSearchNode())
        assertEquals("Filtered", FullDeckName("Filtered").toStoredNameSearchNode()?.deck)
    }
}
