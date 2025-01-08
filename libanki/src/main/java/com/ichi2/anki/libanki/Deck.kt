// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>

package com.ichi2.anki.libanki

import androidx.annotation.VisibleForTesting
import anki.decks.Deck.Filtered.SearchTerm.Order
import com.ichi2.anki.common.json.JSONObjectHolder
import com.ichi2.anki.libanki.utils.NotInPyLib
import net.ankiweb.rsdroid.Translations
import org.intellij.lang.annotations.Language
import org.json.JSONObject

/** Wraps legacy deck JSON without copying it. Property changes update the supplied object. */
data class Deck(
    @VisibleForTesting override val jsonObject: JSONObject,
) : JSONObjectHolder {
    /**
     * Creates a deck object from a JSON string
     */
    constructor(
        @Language("JSON") json: String,
    ) : this(JSONObject(json))

    val isFiltered: Boolean
        get() = jsonObject.getInt("dyn") != 0

    val isNormal: Boolean
        get() = !isFiltered

    var name: String
        get() = jsonObject.getString("name")
        set(value) {
            jsonObject.put("name", value)
        }

    var collapsed: Boolean
        get() = jsonObject.getBoolean("collapsed")
        set(value) {
            jsonObject.put("collapsed", value)
        }

    var browserCollapsed: Boolean
        get() = jsonObject.optBoolean("browserCollapsed", false)
        set(value) {
            jsonObject.put("browserCollapsed", value)
        }

    /**
     * Unique identifier of the deck
     *
     * @see DeckId
     */
    var id: DeckId
        get() = jsonObject.getLong("id")
        set(value) {
            jsonObject.put("id", value)
        }

    var conf: DeckConfigId
        get() {
            val value = jsonObject.optLong("conf")
            return if (value > 0) value else 1
        }
        set(value) {
            jsonObject.put("conf", value)
        }

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

    override fun toString(): String = jsonObject.toString()
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
        Order.RELATIVE_OVERDUENESS -> translations.decksRelativeOverdueness()
        Order.UNRECOGNIZED -> throw IllegalArgumentException("Can't display an unknown enum value.")
    }

@NotInPyLib
internal fun Deck.confOrNull(): DeckConfigId? =
    try {
        val value = jsonObject.getLong("conf")
        if (value > 0) value else null
    } catch (e: Exception) {
        null
    }
