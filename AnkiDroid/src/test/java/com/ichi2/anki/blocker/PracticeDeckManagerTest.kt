// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Tim Farrelly <timf34@gmail.com>

package com.ichi2.anki.blocker

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Note
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The practice deck is the riskiest part of the blocker: it writes to the user's
 * real collection. These tests pin down that it never damages scheduling.
 */
@RunWith(AndroidJUnit4::class)
class PracticeDeckManagerTest : RobolectricTest() {
    @Test
    fun `queue is used as-is when cards are due`() =
        runTest {
            val deckId = addDeck("Blocker", setAsSelected = true)
            addNoteInDeck(deckId)

            val prepared = PracticeDeckManager.prepareQueue(deckId)

            assertThat(prepared, notNullValue())
            assertThat("no practice deck needed", prepared!!.practiceDeckId, nullValue())
            assertThat(prepared.sourceDeckId, equalTo(deckId))
            assertThat(practiceDeckId(), nullValue())
        }

    @Test
    fun `practice deck is built when nothing is due`() =
        runTest {
            val deckId = addDeck("Blocker", setAsSelected = true)
            addNoteInDeck(deckId)
            studyUntilNothingIsDue()

            val prepared = PracticeDeckManager.prepareQueue(deckId)

            assertThat(prepared, notNullValue())
            assertThat("practice deck built", prepared!!.practiceDeckId, notNullValue())
            assertThat("gate has a card to show", col.sched.currentQueueState(), notNullValue())
        }

    @Test
    fun `practice deck does not reschedule`() =
        runTest {
            val deckId = addDeck("Blocker", setAsSelected = true)
            addNoteInDeck(deckId)
            studyUntilNothingIsDue()

            val practiceDeckId = PracticeDeckManager.buildPracticeDeck(deckId)

            assertThat(practiceDeckId, notNullValue())
            assertThat("filtered deck", col.decks.getLegacy(practiceDeckId!!)?.isFiltered, equalTo(true))
            assertThat(
                "practice must not alter real scheduling",
                col.sched
                    .getOrCreateFilteredDeck(practiceDeckId)
                    .config.reschedule,
                equalTo(false),
            )
        }

    @Test
    fun `removing the practice deck returns cards to their home deck`() =
        runTest {
            val deckId = addDeck("Blocker", setAsSelected = true)
            val note = addNoteInDeck(deckId)
            studyUntilNothingIsDue()
            val practiceDeckId = PracticeDeckManager.buildPracticeDeck(deckId)!!
            assertThat("card moved into the practice deck", cardDeckId(note), equalTo(practiceDeckId))

            PracticeDeckManager.remove(practiceDeckId)

            assertThat("card returned home", cardDeckId(note), equalTo(deckId))
            assertThat(practiceDeckId(), nullValue())
        }

    @Test
    fun `answering in practice mode leaves the real due count unchanged`() =
        runTest {
            val deckId = addDeck("Blocker", setAsSelected = true)
            val note = addNoteInDeck(deckId)
            studyUntilNothingIsDue()
            val dueBefore = note.firstCard(col).due

            val practiceDeckId = PracticeDeckManager.buildPracticeDeck(deckId)!!
            val state = col.sched.currentQueueState()!!
            col.sched.answerCard(state, Rating.GOOD)
            PracticeDeckManager.remove(practiceDeckId)

            assertThat("scheduling untouched by practice", note.firstCard(col).due, equalTo(dueBefore))
        }

    @Test
    fun `a leftover practice deck from a crash is cleaned up`() =
        runTest {
            val deckId = addDeck("Blocker", setAsSelected = true)
            addNoteInDeck(deckId)
            studyUntilNothingIsDue()
            PracticeDeckManager.buildPracticeDeck(deckId)
            assertThat("deck exists before the sweep", practiceDeckId(), notNullValue())

            PracticeDeckManager.cleanupStale()

            assertThat(practiceDeckId(), nullValue())
        }

    @Test
    fun `an empty deck cannot produce a practice deck`() =
        runTest {
            val emptyDeckId = addDeck("Empty", setAsSelected = true)

            assertThat(PracticeDeckManager.buildPracticeDeck(emptyDeckId), nullValue())
            assertThat("no empty deck is left behind", practiceDeckId(), nullValue())
        }

    @Test
    fun `a deck whose cards are all buried fails open instead of throwing`() =
        runTest {
            val deckId = addDeck("Blocker", setAsSelected = true)
            addNoteInDeck(deckId)
            buryEverything(deckId)

            // The backend rejects a filtered deck with no eligible cards; the gate must
            // let the user through rather than crash or trap them.
            assertThat(PracticeDeckManager.buildPracticeDeck(deckId), nullValue())
            assertThat(PracticeDeckManager.prepareQueue(deckId), nullValue())
        }

    private fun addNoteInDeck(deckId: DeckId) =
        addBasicNote("Front", "Back").also { note ->
            val card = note.firstCard(col)
            card.did = deckId
            col.updateCards(listOf(card), skipUndoEntry = true)
        }

    /**
     * Studies everything so the deck still has cards but none are due today —
     * the normal "caught up" state the practice deck exists for.
     */
    private fun studyUntilNothingIsDue() {
        var guard = 0
        while (true) {
            val state = col.sched.currentQueueState() ?: break
            col.sched.answerCard(state, Rating.EASY)
            check(guard++ < 100) { "deck never emptied" }
        }
        assertThat("precondition: nothing due", col.sched.currentQueueState(), nullValue())
    }

    /** Buries every card: they stay in the deck but are ineligible for filtered decks. */
    private fun buryEverything(deckId: DeckId) {
        val deckName = col.decks.name(deckId)
        col.sched.buryCards(col.findCards("deck:\"$deckName\""))
    }

    private fun practiceDeckId(): DeckId? = col.decks.idForName(PracticeDeckManager.PRACTICE_DECK_NAME)

    private fun cardDeckId(note: Note): DeckId = note.firstCard(col).did
}
