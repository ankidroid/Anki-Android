// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption
import com.ichi2.anki.dialogs.DeckPickerContextMenuResult
import com.ichi2.anki.dialogs.setDeckPickerContextMenuResult
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "xlarge")
class FilteredDeckTabletRefreshTest : RobolectricTest() {
    @Test
    fun `rebuilding the selected filtered deck refreshes tablet study options once`() = assertSingleRefresh(selected = true)

    @Test
    fun `rebuilding another filtered deck refreshes tablet study options once`() = assertSingleRefresh(selected = false)

    private fun assertSingleRefresh(selected: Boolean) {
        repeat(4) { addBasicNote("$it", "") }
        val deckId = addDynamicDeck("Filtered", search = "")
        col.sched.emptyFilteredDeck(deckId)
        val initialDeckId = if (selected) deckId else DEFAULT_DECK_ID
        col.decks.select(initialDeckId)

        deckPicker {
            advanceRobolectricLooperUntil {
                dueTree?.find(deckId)?.newCount == 0 &&
                    viewModel.loadDeckCounts?.isCompleted == true &&
                    fragment?.viewModel?.selectedDeckId == initialDeckId
            }
            val panel = assertNotNull(fragment)

            panel.viewModel.flowOfState.test {
                awaitItem()

                supportFragmentManager.setDeckPickerContextMenuResult(
                    DeckPickerContextMenuResult(deckId, DeckPickerContextMenuOption.CUSTOM_STUDY_REBUILD),
                )
                advanceRobolectricLooperUntil {
                    dueTree?.find(deckId)?.newCount == 4 && viewModel.loadDeckCounts?.isCompleted == true
                }

                val refreshed = assertIs<StudyOptionsState.StudyOptions>(awaitItem())
                assertEquals(4, refreshed.data.newCardsToday)
                assertEquals(deckId, panel.viewModel.selectedDeckId)
                expectNoEvents()
            }
        }
    }
}
