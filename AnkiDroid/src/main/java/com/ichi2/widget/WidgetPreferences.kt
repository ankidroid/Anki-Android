/*
 *  Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>
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

package com.ichi2.widget

import android.content.Context
import androidx.core.content.edit

/**
 * A class to manage the shared preferences for widget configuration, specifically for the Deck Picker Widget and Card Analysis Widget.
 *
 * @param context the context used to access the shared preferences
 */
class WidgetPreferences(context: Context) {

    private val deckPickerSharedPreferences = context.getSharedPreferences("DeckPickerWidgetPrefs", Context.MODE_PRIVATE)

    // Deletes the stored data for a specific widget for DeckPickerWidget
    fun deleteDeckPickerWidgetData(appWidgetId: Int) {
        deckPickerSharedPreferences.edit {
            remove("deck_picker_widget_selected_decks_$appWidgetId")
        }
    }

    // Get selected deck IDs from shared preferences for DeckPickerWidget
    fun getSelectedDeckIdsFromPreferencesDeckPickerWidget(appWidgetId: Int): LongArray {
        val selectedDecksString = deckPickerSharedPreferences.getString("deck_picker_widget_selected_decks_$appWidgetId", "")
        return if (!selectedDecksString.isNullOrEmpty()) {
            selectedDecksString.split(",").map { it.toLong() }.toLongArray()
        } else {
            longArrayOf()
        }
    }

    // Save selected deck IDs to shared preferences for DeckPickerWidget
    fun saveSelectedDecks(appWidgetId: Int, selectedDecks: List<String>) {
        deckPickerSharedPreferences.edit {
            putString("deck_picker_widget_selected_decks_$appWidgetId", selectedDecks.joinToString(","))
        }
    }
}
