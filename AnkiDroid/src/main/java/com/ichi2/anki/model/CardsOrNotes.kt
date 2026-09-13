// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.model

import android.os.Parcelable
import anki.config.ConfigKey
import com.ichi2.anki.libanki.Collection
import kotlinx.parcelize.Parcelize

/**
 * Config: Whether the `CardBrowser` is in "Cards" or "Notes" mode
 *
 * @see ConfigKey.Bool.BROWSER_TABLE_SHOW_NOTES_MODE
 */
@Parcelize
enum class CardsOrNotes : Parcelable {
    CARDS,
    NOTES,
    ;

    fun saveToCollection(col: Collection) {
        col.config.setBool(ConfigKey.Bool.BROWSER_TABLE_SHOW_NOTES_MODE, this == NOTES)
    }

    companion object {
        fun fromCollection(col: Collection): CardsOrNotes =
            when (col.config.getBool(ConfigKey.Bool.BROWSER_TABLE_SHOW_NOTES_MODE)) {
                true -> NOTES
                false -> CARDS
            }
    }
}
