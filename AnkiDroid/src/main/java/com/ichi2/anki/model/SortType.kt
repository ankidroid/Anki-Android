/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.model

import android.os.Parcelable
import anki.search.BrowserColumns.Column
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.browser.BrowserColumnKey
import com.ichi2.anki.libanki.BrowserConfig
import com.ichi2.anki.libanki.SortOrder
import com.ichi2.anki.model.CardsOrNotes.NOTES
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.PrefsRepository
import kotlinx.parcelize.Parcelize
import timber.log.Timber

/**
 * How to sort the rows in the [CardBrowser]
 *
 * A parcelable subset of the [SortOrders][SortOrder] which AnkiDroid supports
 *
 * [NoOrdering] is not supported by the upstream browser.
 * See: [Prefs.cardBrowserNoSorting]
 *
 * Other properties are stored in the collection config and synced:
 * [getBrowserColumnKey], [getSortBackwards]; [BrowserConfig]
 */
@Parcelize
sealed class SortType : Parcelable {
    /**
     * @see SortOrder.NoOrdering
     */
    data object NoOrdering : SortType()

    /**
     * @see SortOrder.UseCollectionOrdering
     * @see SortOrder.BuiltinColumnSortKind
     */
    data class CollectionOrdering(
        val key: BrowserColumnKey,
        val reverse: Boolean,
    ) : SortType()

    suspend fun save(cardsOrNotes: CardsOrNotes) {
        Timber.i("saving %s", this)

        when (this) {
            is NoOrdering -> Prefs.cardBrowserNoSorting = true
            is CollectionOrdering -> {
                val isNotesMode = cardsOrNotes == NOTES

                val sortKey = BrowserConfig.sortColumnKey(isNotesMode)
                val reverseKey = BrowserConfig.sortBackwardsKey(isNotesMode)

                withCol { config.set(sortKey, this@SortType.key.value) }
                withCol { config.set(reverseKey, this@SortType.reverse) }

                Prefs.cardBrowserNoSorting = false
            }
        }
    }

    companion object {
        suspend fun build(cardsOrNotes: CardsOrNotes) =
            when (Prefs.cardBrowserNoSorting) {
                true -> NoOrdering
                false -> {
                    val browserColumnKey = getBrowserColumnKey(cardsOrNotes)
                    val browserColumn: Column? = withCol { getBrowserColumn(browserColumnKey) }

                    if (browserColumn == null) {
                        NoOrdering
                    } else {
                        val reverse = getSortBackwards(cardsOrNotes)
                        val key = BrowserColumnKey.from(browserColumn)
                        CollectionOrdering(key = key, reverse = reverse)
                    }
                }
            }

        fun buildSortOrder(): SortOrder =
            when (Prefs.cardBrowserNoSorting) {
                true -> SortOrder.NoOrdering
                false -> SortOrder.UseCollectionOrdering
            }
    }
}

/**
 * Whether the Card Browser should use an efficient 'no sorting' mode when displaying results
 *
 * **AnkiDroid Only**
 *
 * TODO: This should differentiate cards & notes mode
 */
var PrefsRepository.cardBrowserNoSorting: Boolean
    get() = getBoolean(R.string.pref_browser_no_sorting, false)
    set(value) {
        putBoolean(R.string.pref_browser_no_sorting, value)
    }

private suspend fun getBrowserColumnKey(cardsOrNotes: CardsOrNotes): String {
    val isNotesMode = cardsOrNotes == NOTES
    val sortKey = BrowserConfig.sortColumnKey(isNotesMode)

    return withCol { config.get<String>(sortKey) } ?: "noteFld"
}

private suspend fun getSortBackwards(cardsOrNotes: CardsOrNotes): Boolean {
    val isNotesMode = cardsOrNotes == NOTES
    val sortKey = BrowserConfig.sortBackwardsKey(isNotesMode)

    return withCol { config.get<Boolean>(sortKey) } ?: false
}
