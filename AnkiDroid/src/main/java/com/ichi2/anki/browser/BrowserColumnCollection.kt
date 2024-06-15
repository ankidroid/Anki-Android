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

import android.content.SharedPreferences
import androidx.annotation.CheckResult
import androidx.core.content.edit
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.CardsOrNotes.CARDS
import com.ichi2.anki.model.CardsOrNotes.NOTES
import com.ichi2.libanki.BrowserConfig
import com.ichi2.libanki.BrowserConfig.ACTIVE_CARD_COLUMNS_KEY
import com.ichi2.libanki.BrowserConfig.ACTIVE_NOTE_COLUMNS_KEY
import com.ichi2.libanki.BrowserDefaults
import net.ankiweb.rsdroid.Backend
import timber.log.Timber

/**
 * A collection of columns available in the [browser][CardBrowser]
 *
 * These are stored in [SharedPreferences] under either:
 * * [ACTIVE_CARD_COLUMNS_KEY]
 * * [ACTIVE_NOTE_COLUMNS_KEY]
 *
 * @see Backend.setActiveBrowserColumns
 * @see BrowserConfig.activeColumnsKey
 */
class BrowserColumnCollection(val columns: List<CardBrowserColumn>) {
    val backendKeys: Iterable<String> get() = columns.map { it.ankiColumnKey }
    val count = columns.size
    operator fun get(index: Int) = columns[index]

    companion object {
        private const val SEPARATOR_CHAR = '|'

        @CheckResult
        fun load(prefs: SharedPreferences, mode: CardsOrNotes): BrowserColumnCollection {
            val key = mode.toPreferenceKey()
            val columns = try {
                val value = prefs.getString(key, mode.defaultColumns())!!
                value.split(SEPARATOR_CHAR).map { CardBrowserColumn.fromColumnKey(it) }
            } catch (e: Exception) {
                Timber.w(e, "error loading columns, returning default")
                val value = mode.defaultColumns()
                value.split(SEPARATOR_CHAR).map { CardBrowserColumn.fromColumnKey(it) }
            }
            return BrowserColumnCollection(columns)
        }

        /**
         * @param block Update the column list here. `null` meaning 'none'
         */
        fun update(
            prefs: SharedPreferences,
            mode: CardsOrNotes,
            block: (MutableList<CardBrowserColumn?>) -> Boolean
        ) {
            val valuesToUpdate: MutableList<CardBrowserColumn?> = load(prefs, mode).columns.toMutableList()
            if (!block(valuesToUpdate)) {
                Timber.d("no changes requested")
                return
            }
            // as in AnkiMobile, this converts: [QUESTION, NONE, TAGS] into [QUESTION, TAGS]
            val updatedValues = valuesToUpdate.filterNotNull()
            save(prefs, mode, BrowserColumnCollection(updatedValues))
        }

        fun save(prefs: SharedPreferences, mode: CardsOrNotes, value: BrowserColumnCollection) {
            val key = mode.toPreferenceKey()
            val preferenceValue = value.columns
                .joinToString(separator = SEPARATOR_CHAR.toString()) { it.ankiColumnKey }
            Timber.d("updating '%s' to '%s'", key, preferenceValue)
            prefs.edit { putString(key, preferenceValue) }
        }

        private fun CardsOrNotes.toPreferenceKey() =
            BrowserConfig.activeColumnsKey(isNotesMode = this == NOTES)

        private fun CardsOrNotes.defaultColumns() =
            (if (this == CARDS) BrowserDefaults.CARD_COLUMNS else BrowserDefaults.NOTE_COLUMNS)
                .joinToString(separator = SEPARATOR_CHAR.toString())
    }
}
