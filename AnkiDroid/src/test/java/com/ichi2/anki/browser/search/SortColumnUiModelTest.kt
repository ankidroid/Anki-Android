/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser.search

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.CardBrowserColumn
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.runCardBrowserViewModelTest
import com.ichi2.anki.browser.search.SortOrderBottomSheetFragment.ColumnUiModel
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.jupiter.api.assertInstanceOf
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Tests [SortOrderBottomSheetFragment.ColumnUiModel] */
@RunWith(AndroidJUnit4::class)
class SortColumnUiModelTest : RobolectricTest() {
    @Test
    fun `ordering is correct`() =
        runCardBrowserViewModelTest {
            updateActiveColumns(
                CardBrowserColumn.ORIGINAL_POSITION,
                CardBrowserColumn.QUESTION,
                CardBrowserColumn.LAPSES,
            )

            val list = ColumnUiModel.buildList(this)

            val first = listOf(null)
            val selected = listOf("originalPosition", "question", "cardLapses")
            val excluded = listOf("answer")
            // all known columns, in our expected order
            // CardBrowserColumn defines this custom order
            val all =
                listOf(
                    "question",
                    "answer",
                    "noteFld",
                    "deck",
                    "noteTags",
                    "template",
                    "cardDue",
                    "cardEase",
                    "cardMod",
                    "noteCrt",
                    "noteMod",
                    "cardIvl",
                    "cardLapses",
                    "note",
                    "cardReps",
                    "difficulty",
                    "retrievability",
                    "stability",
                    "originalPosition",
                )

            val expected =
                first +
                    selected +
                    all.filter { !selected.contains(it) && !excluded.contains(it) } +
                    excluded

            assertEquals(expected, list.map { it.keyOrNull })
        }

    @Test
    fun `properties of initial item`() =
        runCardBrowserViewModelTest {
            val initialItem = ColumnUiModel.buildList(this).first()

            val item = assertInstanceOf<ColumnUiModel.NoOrdering>(initialItem)
            assertEquals(true, item.available)
            assertEquals("No sorting (faster)", item.getLabel(targetContext))
        }

    @Test
    fun `properties of unselected column`() =
        runCardBrowserViewModelTest {
            val column =
                ColumnUiModel
                    .buildList(this)
                    .filterIsInstance<ColumnUiModel.AnkiColumn>()
                    .single { it.key.value == "cardEase" }

            val item = assertInstanceOf<ColumnUiModel.AnkiColumn>(column)
            assertEquals("cardEase", item.key.value)
            assertEquals("Ease", item.label)
            assertEquals(null, item.tooltipValue)
            assertEquals(true, item.canBeSorted)
            assertEquals(false, item.isShownInUI)
        }

    @Test
    fun `properties of selected column`() =
        runCardBrowserViewModelTest {
            updateActiveColumns(CardBrowserColumn.EASE)

            val column =
                ColumnUiModel
                    .buildList(this)
                    .filterIsInstance<ColumnUiModel.AnkiColumn>()
                    .single { it.key.value == "cardEase" }

            val item = assertInstanceOf<ColumnUiModel.AnkiColumn>(column)
            assertEquals("cardEase", item.key.value)
            assertEquals("Ease", item.label)
            assertEquals(null, item.tooltipValue)
            assertEquals(true, item.canBeSorted)
            assertEquals(true, item.isShownInUI)
        }

    @Test
    fun `properties of unusable column`() =
        runCardBrowserViewModelTest {
            val column =
                ColumnUiModel
                    .buildList(this)
                    .filterIsInstance<ColumnUiModel.AnkiColumn>()
                    .single { it.key.value == "question" }

            val item = assertInstanceOf<ColumnUiModel.AnkiColumn>(column)
            assertEquals("question", item.key.value)
            assertEquals("Question", item.label)
            assertEquals(null, item.tooltipValue)
            assertEquals(false, item.canBeSorted)
            assertEquals(false, item.isShownInUI)
        }

    @Test
    fun `properties of unusable column in UI`() =
        runCardBrowserViewModelTest {
            updateActiveColumns(CardBrowserColumn.QUESTION)

            val column =
                ColumnUiModel
                    .buildList(this)
                    .filterIsInstance<ColumnUiModel.AnkiColumn>()
                    .single { it.key.value == "question" }

            val item = assertInstanceOf<ColumnUiModel.AnkiColumn>(column)
            assertEquals("question", item.key.value)
            assertEquals("Question", item.label)
            assertEquals(null, item.tooltipValue)
            assertEquals(false, item.canBeSorted)
            assertEquals(true, item.isShownInUI)
        }

    fun CardBrowserViewModel.updateActiveColumns(vararg columns: CardBrowserColumn) {
        assertTrue(updateActiveColumns(columns.toList(), this.cardsOrNotes))
    }
}

val ColumnUiModel.keyOrNull get() =
    when (this) {
        is ColumnUiModel.NoOrdering -> null
        is ColumnUiModel.AnkiColumn -> this.key.value
    }
