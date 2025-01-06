/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.deckpicker

import androidx.annotation.CheckResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.RobolectricTest
import com.ichi2.libanki.CardId
import com.ichi2.libanki.undoStatus
import com.ichi2.testutils.ensureOpsExecuted
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

/** Test of [DeckPickerViewModel] */
@RunWith(AndroidJUnit4::class)
class DeckPickerViewModelTest : RobolectricTest() {
    private val viewModel = DeckPickerViewModel()

    @Test
    fun `empty cards - flow`() =
        runTest {
            val cardsToEmpty = createEmptyCards()

            viewModel.emptyCardsNotification.test {
                // test a 'normal' deletion
                viewModel.deleteEmptyCards(cardsToEmpty).join()

                expectMostRecentItem().also {
                    assertThat("cards deleted", it.cardsDeleted, equalTo(1))
                }

                // ensure a duplicate output is displayed to the user
                val newCardsToEmpty = createEmptyCards()
                viewModel.deleteEmptyCards(newCardsToEmpty).join()

                expectMostRecentItem().also {
                    assertThat("cards deleted: duplicate output", it.cardsDeleted, equalTo(1))
                }

                // send the same collection in, but with the same ids.
                // the output should only show 1 card deleted
                val emptyCardsSentTwice = createEmptyCards()
                viewModel.deleteEmptyCards(emptyCardsSentTwice + emptyCardsSentTwice).join()

                expectMostRecentItem().also {
                    assertThat("cards deleted: duplicate input", it.cardsDeleted, equalTo(1))
                }

                // test an empty list: a no-op should inform the user, rather than do nothing
                viewModel.deleteEmptyCards(listOf()).join()

                expectMostRecentItem().also {
                    assertThat("'no cards deleted' is notified", it.cardsDeleted, equalTo(0))
                }
            }
        }

    @Test
    fun `empty cards - undoable`() =
        runTest {
            val cardsToEmpty = createEmptyCards()

            // ChangeManager assert
            ensureOpsExecuted(1) {
                viewModel.deleteEmptyCards(cardsToEmpty).join()
            }

            // backend assert
            assertThat("col undo status", col.undoStatus().undo, equalTo("Empty Cards"))
        }

    @CheckResult
    private suspend fun createEmptyCards(): List<CardId> {
        addNoteUsingNoteTypeName("Cloze", "{{c1::Hello}} {{c2::World}}", "").apply {
            setField(0, "{{c1::Hello}}")
            col.updateNote(this)
        }
        return viewModel.findEmptyCards().also { cardsToEmpty ->
            assertThat("there are empty cards", cardsToEmpty, not(empty()))
            Timber.d("created %d empty cards: [%s]", cardsToEmpty.size, cardsToEmpty)
        }
    }

    /** test helper to use [deleteEmptyCards] without an [EmptyCards] instance */
    private fun DeckPickerViewModel.deleteEmptyCards(list: List<CardId>) = deleteEmptyCards(EmptyCards(list))
}
