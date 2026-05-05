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

package com.ichi2.anki.browser

import android.content.SharedPreferences
import androidx.core.content.edit
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.utils.ext.ignoreAccentsInSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * Source of truth for the values controlled by [com.ichi2.anki.dialogs.BrowserOptionsDialog]
 */
class BrowserOptionsRepository(
    private val sharedPrefs: SharedPreferences,
) {
    val cardsOrNotes: StateFlow<CardsOrNotes>
        field = MutableStateFlow(CardsOrNotes.CARDS)

    val isTruncated: StateFlow<Boolean>
        field = MutableStateFlow(sharedPrefs.getBoolean(PREF_IS_TRUNCATED, false))

    val ignoreAccentsInSearch: StateFlow<Boolean>
        field = MutableStateFlow(false)

    /** Reads persisted values into the flows. Call once during ViewModel init. */
    suspend fun load() {
        cardsOrNotes.value = withCol { CardsOrNotes.fromCollection(this) }
        ignoreAccentsInSearch.value = withCol { config.ignoreAccentsInSearch }
    }

    suspend fun setCardsOrNotes(value: CardsOrNotes) {
        Timber.i("setting cards/notes mode to %s", value)
        withCol { value.saveToCollection(this) }
        cardsOrNotes.value = value
    }

    fun setIsTruncated(value: Boolean) {
        Timber.d("setting truncated to %s", value)
        sharedPrefs.edit { putBoolean(PREF_IS_TRUNCATED, value) }
        isTruncated.value = value
    }

    suspend fun setIgnoreAccentsInSearch(value: Boolean) {
        Timber.d("setting ignore accents in search to %s", value)
        withCol { config.ignoreAccentsInSearch = value }
        ignoreAccentsInSearch.value = value
    }

    companion object {
        private const val PREF_IS_TRUNCATED = "isTruncated"
    }
}
