/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser

import com.ichi2.anki.CardBrowser
import com.ichi2.anki.browser.CardBrowserColumn.CHANGED
import com.ichi2.anki.browser.CardBrowserColumn.CREATED
import com.ichi2.anki.browser.CardBrowserColumn.DUE
import com.ichi2.anki.browser.CardBrowserColumn.EDITED
import com.ichi2.anki.browser.CardBrowserColumn.FSRS_DIFFICULTY
import com.ichi2.anki.browser.CardBrowserColumn.FSRS_RETRIEVABILITY
import com.ichi2.anki.browser.CardBrowserColumn.FSRS_STABILITY
import com.ichi2.anki.browser.CardBrowserColumn.ORIGINAL_POSITION
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.testutils.JvmTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.`in`
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import timber.log.Timber
import kotlin.test.assertNotNull

/** @see CardBrowserColumn */
@RunWith(ParameterizedRobolectricTestRunner::class)
class CardBrowserColumnTest : JvmTest() {

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<Array<Any>> {
            return CardBrowserColumn.entries
                .map { arrayOf(it) }
        }
    }

    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField // required for Parameter
    var columnParam: CardBrowserColumn? = null
    private val column get() = columnParam!!

    @Test
    fun ensureAllColumnsMapped() {
        val collectionColumns = col.backend.allBrowserColumns()
        Timber.w("%s", collectionColumns.joinToString { it.key })
        assertNotNull(collectionColumns.find(column))
    }

    @Test
    fun `cards - ensure old values match backend values`() {
        CardsOrNotes.CARDS.saveToCollection(col)
        `ensure old values match backend values`(CardsOrNotes.CARDS)
    }

    @Test
    fun `notes - ensure old values match backend values`() {
        CardsOrNotes.NOTES.saveToCollection(col)
        `ensure old values match backend values`(CardsOrNotes.NOTES)
    }

    private fun `ensure old values match backend values`(cardsOrNotes: CardsOrNotes) {
        // dates seem correct - we don't currently display minutes
        assumeThat(column, not(`in`(listOf(CHANGED, CREATED, EDITED))))
        // FSRS is not implemented
        assumeThat(
            column,
            not(
                `in`(
                    listOf(
                        FSRS_DIFFICULTY,
                        FSRS_RETRIEVABILITY,
                        FSRS_STABILITY
                    )
                )
            )
        )

        val note = addNoteUsingBasicModel()
        val cid = note.cids()[0]
        val nid = note.id

        var oldData = CardBrowser.CardCache(cid, col, 0, cardsOrNotes)
            .getColumnHeaderText(column)

        val newData = column.let {
            col.backend.setActiveBrowserColumns(listOf(it.ankiColumnKey))
            val rowId = if (cardsOrNotes == CardsOrNotes.CARDS) cid else nid
            col.backend.browserRowForId(rowId).getCells(0).text
        }

        if (column == DUE) {
            oldData = when (cardsOrNotes) {
                CardsOrNotes.CARDS -> "New #\u2068${oldData}\u2069"
                CardsOrNotes.NOTES -> ""
            }
        } else if (column == ORIGINAL_POSITION) {
            // original position is generated in the backend.
            // should be "1" since this is our first card.
            oldData = "1"
        }

        assertThat(newData, equalTo(oldData))
    }
}
