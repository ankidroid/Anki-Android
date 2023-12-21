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

import android.content.SharedPreferences
import androidx.core.content.edit
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.R
import com.ichi2.libanki.Config
import com.ichi2.libanki.SortOrder
import timber.log.Timber

/**
 * How to sort the rows in the [CardBrowser]
 *
 * This is an adapter from our [SharedPreferences] based handling of sorting
 * to Anki's [Config]
 *
 * We can likely remove the SharedPreferences and rely entirely on Anki
 *
 * @param ankiSortType The value to be passed into Anki's "sortType" config
 * @param cardBrowserLabelIndex The index into [R.array.card_browser_order_labels]
 */
@Suppress("unused") // 'unused' entries are iterated over by .entries
enum class SortType(val ankiSortType: String?, val cardBrowserLabelIndex: Int) {
    NO_SORTING(null, 0),
    SORT_FIELD("noteFld", 1),
    CREATED_TIME("noteCrt", 2),
    NOTE_MODIFICATION_TIME("noteMod", 3),
    CARD_MODIFICATION_TIME("cardMod", 4),
    DUE_TIME("cardDue", 5),
    INTERVAL("cardIvl", 6),
    EASE("cardEase", 7),
    REVIEWS("cardReps", 8),
    LAPSES("cardLapses", 9);

    fun save(config: Config, preferences: SharedPreferences) {
        Timber.v("update config to %s", this)
        // in the case of 'no sorting', we still need a sort type.
        // The inverse is handled in `fromCol`
        config.set("sortType", this.ankiSortType ?: SORT_FIELD.ankiSortType)
        config.set("noteSortType", this.ankiSortType ?: SORT_FIELD.ankiSortType)
        preferences.edit {
            putBoolean("cardBrowserNoSorting", this@SortType == NO_SORTING)
        }
    }

    /** Converts the [SortType] to a [SortOrder] */
    fun toSortOrder(): SortOrder =
        if (this == NO_SORTING) SortOrder.NoOrdering() else SortOrder.UseCollectionOrdering()

    companion object {
        fun fromCol(config: Config, preferences: SharedPreferences): SortType {
            val colOrder = config.get<String>("sortType")
            val type = entries.firstOrNull { it.ankiSortType == colOrder } ?: NO_SORTING
            if (type == SORT_FIELD && preferences.getBoolean("cardBrowserNoSorting", false)) {
                return NO_SORTING
            }
            return type
        }

        fun fromCardBrowserLabelIndex(index: Int): SortType {
            return entries.firstOrNull { it.cardBrowserLabelIndex == index } ?: NO_SORTING
        }
    }
}
