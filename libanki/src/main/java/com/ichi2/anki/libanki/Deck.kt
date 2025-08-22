/****************************************************************************************
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                             *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.libanki

import androidx.annotation.VisibleForTesting
import anki.decks.Deck.Filtered.SearchTerm.Order
import com.ichi2.anki.common.json.JSONObjectHolder
import net.ankiweb.rsdroid.Translations
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONObject.NULL

/**
 * Represents a deck. Use [factory] in order to create either a [FilteredDeck] or a [RegularDeck].
 */
interface Deck : JSONObjectHolder {
    companion object {
        fun isFiltered(jsonObject: JSONObject) = jsonObject.getInt("dyn") != 0

        /**
         * Those two factories returns either a [FilteredDeck] or a [RegularDeck] encapsulating the input, depending on the value of [isFiltered].
         */
        fun factory(jsonObject: JSONObject): Deck = if (isFiltered(jsonObject)) FilteredDeck(
            jsonObject
        ) else RegularDeck(jsonObject)

        fun factory(jsonObject: String) = factory(JSONObject(jsonObject))
    }

    /**
     * Whether this deck is a filtered deck.
     */
    var isFiltered: Boolean
        get() = jsonObject.getInt("dyn") != 0

        @VisibleForTesting
        set(value) {
            jsonObject.put("dyn", if (value) 1 else 0)
        }

    /**
     * Whether this deck is a normal deck. That is, not a filtered deck.
     */
    var isRegular: Boolean
        get() = !isFiltered

        @VisibleForTesting
        set(value) {
            isFiltered = !value
        }

    /**
     * The name of the deck. Mutable. If you want a way to persistently represents this deck, use [id] instead.
     */
    var name: String
        get() = jsonObject.getString("name")
        set(value) {
            jsonObject.put("name", value)
        }

    /**
     * If this deck as subdecks, whether those subdecks should be collapsed in the desktop card browser.
     * Not used in ankidroid at the moment.
     */
    var browserCollapsed: Boolean
        get() = jsonObject.getBoolean("browserCollapsed")
        set(value) {
            jsonObject.put("browserCollapsed", value)
        }

    /**
     * If this deck as subdecks, whether those subdecks should be collapsed in the deck picker.
     */
    var collapsed: Boolean
        get() = jsonObject.getBoolean("collapsed")
        set(value) {
            jsonObject.put("collapsed", value)
        }

    /**
     * The id of the deck. Should be globally unique
     * (created as a timestamp, very small chance of collision between two different decks from different users)
     */
    var id: DeckId
        get() = jsonObject.getLong("id")
        set(value) {
            jsonObject.put("id", value)
        }

    var resched: Boolean
        get() = jsonObject.getBoolean("resched")
        set(value) {
            jsonObject.put("resched", value)
        }

    var previewAgainSecs: Int
        get() = jsonObject.getInt("previewAgainSecs")
        set(value) {
            jsonObject.put("previewAgainSecs", value)
        }
    var previewHardSecs: Int
        get() = jsonObject.getInt("previewHardSecs")
        set(value) {
            jsonObject.put("previewHardSecs", value)
        }

    var previewGoodSecs: Int
        get() = jsonObject.getInt("previewGoodSecs")
        set(value) {
            jsonObject.put("previewGoodSecs", value)
        }

    /**
     * An array of string. The i-th string correspond to the number of second/minute/hour or day for the i-th learning steps.
     * See https://docs.ankiweb.net/deck-options.html#learning-steps
     */
    var delays: JSONArray?
        get() = jsonObject.optJSONArray("delays")
        set(value) {
            jsonObject.put("delays", value ?: NULL)
        }

    fun removeEmpty() {
        jsonObject.remove("empty")
    }

    fun hasEmpty() = jsonObject.has("empty")

    /**
     * The description, shown on the deck overview and optionally the congratulations screen.
     *
     * May be HTML or Markdown, depending on [descriptionAsMarkdown].
     */
    var description: String
        get() = jsonObject.optString("desc", "")
        set(value) {
            jsonObject.put("desc", value)
        }

    /**
     * Treats [description] as Markdown, cleaning HTML input and stripping images.
     *
     * If disabled, the description is only shown on the deck overview.
     * If enabled, it is also shown on the congratulations screen.
     *
     * Markdown will appear as text on Anki 2.1.40 and below.
     *
     * Anki names this feature 'md': Markdown description
     *
     * @see anki.backend.GeneratedBackend.renderMarkdown
     * @see anki.i18n.GeneratedTranslations.deckConfigDescriptionNewHandling
     * @see anki.i18n.GeneratedTranslations.deckConfigDescriptionNewHandlingHint
     */
    var descriptionAsMarkdown: Boolean
        get() = jsonObject.optBoolean("md", false)
        set(value) {
            jsonObject.put("md", value)
        }
}

/**
 * Converts a Sort Order for a filtered deck to a display string
 *
 * `Order.OLDEST_REVIEWED_FIRST` -> "Oldest seen first"
 *
 * @throws IllegalArgumentException if [Order.UNRECOGNIZED] is provided
 */
fun Order.toDisplayString(translations: Translations) =
    when (this) {
        Order.OLDEST_REVIEWED_FIRST -> translations.decksOldestSeenFirst()
        Order.RANDOM -> translations.decksRandom()
        Order.INTERVALS_ASCENDING -> translations.decksIncreasingIntervals()
        Order.INTERVALS_DESCENDING -> translations.decksDecreasingIntervals()
        Order.LAPSES -> translations.decksMostLapses()
        Order.ADDED -> translations.decksOrderAdded()
        Order.DUE -> translations.decksOrderDue()
        Order.REVERSE_ADDED -> translations.decksLatestAddedFirst()
        Order.RETRIEVABILITY_ASCENDING -> translations.deckConfigSortOrderRetrievabilityAscending()
        Order.RETRIEVABILITY_DESCENDING -> translations.deckConfigSortOrderRetrievabilityDescending()
        Order.UNRECOGNIZED -> throw IllegalArgumentException("Can't display an unknown enum value.")
    }
