//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki

import com.ichi2.anki.servicelayer.NoteService.isMarked
import com.ichi2.libanki.Card
import com.ichi2.libanki.Note
import com.ichi2.utils.HashUtil.HashSetInit
import java.util.*

/**
 * Utilities for working on multiple cards
 */
object CardUtils {
    /**
     * @return List of corresponding notes without duplicates, even if the input list has multiple cards of the same note.
     */
    fun getNotes(cards: Collection<Card>): Set<Note> {
        val notes: MutableSet<Note> = HashSetInit(cards.size)
        for (card in cards) {
            notes.add(card.note())
        }
        return notes
    }

    /**
     * @return All cards of all notes
     */
    fun getAllCards(notes: Set<Note>): List<Card> {
        val allCards: MutableList<Card> = ArrayList(notes.size)
        for (note in notes) {
            allCards.addAll(note.cards())
        }
        return allCards
    }

    fun markAll(notes: List<Note>, mark: Boolean) {
        for (note in notes) {
            if (mark) {
                if (!isMarked(note)) {
                    note.addTag("marked")
                    note.flush()
                }
            } else {
                note.delTag("marked")
                note.flush()
            }
        }
    }

    /**
     * Returns the deck ID of the given [Card].
     *
     * @param card The [Card] to get the deck ID
     * @return The deck ID of the [Card]
     */
    fun getDeckIdForCard(card: Card): Long {
        // Try to get the configuration by the original deck ID (available in case of a cram deck),
        // else use the direct deck ID (in case of a 'normal' deck.
        return if (card.oDid == 0L) card.did else card.oDid
    }
}
