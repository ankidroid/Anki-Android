// SPDX-License-Identifier: GPL-3.0-or-later

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
        val (mode, ignoreAccents) = withCol { CardsOrNotes.fromCollection(this) to config.ignoreAccentsInSearch }
        cardsOrNotes.value = mode
        ignoreAccentsInSearch.value = ignoreAccents
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
