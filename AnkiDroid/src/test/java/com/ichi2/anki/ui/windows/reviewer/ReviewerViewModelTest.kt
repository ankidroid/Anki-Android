/*
 *  Copyright (c) 2026 Ayush Patel <ayushpatel2731@gmail.com>
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

package com.ichi2.anki.ui.windows.reviewer

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.collection.OpChanges
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.preferences.reviewer.ViewerAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.sameInstance
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReviewerViewModelTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun `editing note template updates queue state preventing undo error`() =
        runTest {
            addBasicNote("Front", "Back")
            val handle = SavedStateHandle()
            val viewModel = ReviewerViewModel(handle)
            val card = viewModel.currentCard.await()
            card.noteType().update {
                templates.first().qfmt += " Modified"
            }
            val changes = OpChanges.newBuilder().setNoteText(true).build()
            viewModel.opExecuted(changes, null)
            val newCard = viewModel.currentCard.await()
            assertThat("Card should be refreshed (new instance)", newCard, not(sameInstance(card)))
            assertDoesNotThrow {
                viewModel.answerCard(Rating.GOOD)
            }
            viewModel.executeAction(ViewerAction.UNDO)
        }

    private fun NotetypeJson.update(block: NotetypeJson.() -> Unit) {
        block(this)
        col.notetypes.update(this)
    }
}
