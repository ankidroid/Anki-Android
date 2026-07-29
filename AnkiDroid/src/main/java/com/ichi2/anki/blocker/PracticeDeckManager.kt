// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Tim Farrelly <timf34@gmail.com>

package com.ichi2.anki.blocker

import anki.decks.Deck
import anki.decks.DeckKt.FilteredKt.searchTerm
import anki.decks.DeckKt.filtered
import anki.decks.filteredDeckForUpdate
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.observability.undoableOp
import net.ankiweb.rsdroid.BackendException
import timber.log.Timber

/**
 * Guarantees a blocker gate always has cards to study.
 *
 * If the gate deck has due cards, the normal scheduled queue is used (the gate
 * doubles as doing real reviews). When nothing is due, a temporary filtered deck
 * with `reschedule = false` is built from the gate deck, so cards can be practiced
 * without touching their real scheduling. The deck is removed when the gate closes;
 * removal returns all cards to their home decks, so it is loss-proof.
 */
object PracticeDeckManager {
    /**
     * Deliberately not translated: [cleanupStale] finds leftover decks by name,
     * which must not vary with the device locale.
     */
    const val PRACTICE_DECK_NAME = "AnkiDroid Blocker Session"

    /** Upper bound of cards pulled into a practice deck; far above any sane gate size. */
    private const val PRACTICE_CARD_LIMIT = 100

    private const val PREVIEW_AGAIN_SECS = 60
    private const val PREVIEW_HARD_SECS = 300

    /**
     * @property sourceDeckId the resolved gate deck reviews are drawn from
     * @property practiceDeckId the temporary deck to clean up afterwards,
     *   or null when the normal scheduled queue is being used
     */
    data class PreparedQueue(
        val sourceDeckId: DeckId,
        val practiceDeckId: DeckId?,
    )

    /**
     * Selects the gate deck and ensures its study queue is non-empty, building a
     * practice deck if needed.
     *
     * @return the prepared queue, or null if no cards are available at all
     *   (e.g. an empty collection) — callers should fail open.
     */
    suspend fun prepareQueue(gateDeckId: DeckId): PreparedQueue? {
        cleanupStale()
        val deckId =
            withCol {
                if (gateDeckId != BlockerPrefs.NO_GATE_DECK && decks.nameIfExists(gateDeckId) != null) {
                    decks.select(gateDeckId)
                    gateDeckId
                } else {
                    decks.selected()
                }
            }
        if (withCol { sched.currentQueueState() } != null) {
            return PreparedQueue(sourceDeckId = deckId, practiceDeckId = null)
        }
        Timber.i("Blocker: nothing due in deck %d, building practice deck", deckId)
        val practiceDeckId = buildPracticeDeck(deckId) ?: return null
        return PreparedQueue(sourceDeckId = deckId, practiceDeckId = practiceDeckId)
    }

    /**
     * Builds (or rebuilds) the practice deck from [sourceDeckId] and selects it.
     *
     * @return the practice deck id, or null if the source deck has no usable cards
     */
    suspend fun buildPracticeDeck(sourceDeckId: DeckId): DeckId? {
        val sourceName = withCol { decks.nameIfExists(sourceDeckId) } ?: return null
        val existingId = withCol { decks.idForName(PRACTICE_DECK_NAME) } ?: 0L
        val update =
            filteredDeckForUpdate {
                id = existingId
                name = PRACTICE_DECK_NAME
                allowEmpty = false
                config =
                    filtered {
                        reschedule = false
                        previewAgainSecs = PREVIEW_AGAIN_SECS
                        previewHardSecs = PREVIEW_HARD_SECS
                        previewGoodSecs = 0
                        searchTerms.add(
                            searchTerm {
                                search = "deck:\"${escapeSearch(sourceName)}\" -is:suspended"
                                limit = PRACTICE_CARD_LIMIT
                                order = Deck.Filtered.SearchTerm.Order.RANDOM
                            },
                        )
                    }
            }
        val practiceDeckId =
            try {
                undoableOp { sched.addOrUpdateFilteredDeck(update) }.id
            } catch (e: BackendException) {
                // Most often BackendDeckIsFilteredException ("no cards matched"), when the
                // deck is empty or everything in it is suspended or buried. Any backend
                // failure here must fail open rather than trap the user behind a broken gate.
                Timber.w(e, "Blocker: could not build practice deck from '%s'", sourceName)
                return null
            }
        withCol { decks.select(practiceDeckId) }
        if (withCol { sched.currentQueueState() } == null) {
            remove(practiceDeckId)
            return null
        }
        return practiceDeckId
    }

    /** Removes the practice deck, returning its cards to their home decks. */
    suspend fun remove(practiceDeckId: DeckId) {
        Timber.i("Blocker: removing practice deck %d", practiceDeckId)
        undoableOp { decks.remove(listOf(practiceDeckId)) }
    }

    /** Removes a practice deck left behind by a previous crash or force-stop. */
    suspend fun cleanupStale() {
        val staleId = withCol { decks.idForName(PRACTICE_DECK_NAME) } ?: return
        Timber.w("Blocker: cleaning up stale practice deck")
        remove(staleId)
    }

    private fun escapeSearch(deckName: String): String = deckName.replace("\\", "\\\\").replace("\"", "\\\"")
}
