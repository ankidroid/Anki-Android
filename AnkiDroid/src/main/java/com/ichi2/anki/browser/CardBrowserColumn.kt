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

package com.ichi2.anki.browser

import anki.search.BrowserColumns
import com.ichi2.anki.CardBrowser
import net.ankiweb.rsdroid.Backend

/**
 * A column available in the [browser][CardBrowser]
 *
 * @see COLUMN1_KEYS - values for first column
 * @see COLUMN2_KEYS - values for second column
 * @see CardBrowser.CardCache.getColumnHeaderText - how columns are rendered
 *
 * @param ankiColumnKey The key used in [Backend.setActiveBrowserColumns]
 */
enum class CardBrowserColumn(val ankiColumnKey: String) {
    /** Rendered front side of the first card of the note */
    QUESTION("question"),

    /** Rendered back side of the first card of the note */
    ANSWER("answer"),

    /** The value of the field marked as "Sort by this field in the Browser" */
    SFLD("noteFld"),

    /**
     * Cards -> The deck which contains the card
     * Notes -> Either the deck containing the card, or `(n)`, where n is the number of
     * distinct decks
     */
    DECK("deck"),

    /** A list of tags for the note */
    TAGS("noteTags"),

    /**
     * Cards -> Card Type
     * Notes -> Cards (card count)
     */
    CARD("template"),
    DUE("cardDue"),

    /**
     * Cards -> Ease
     * Notes -> Average Ease
     */
    EASE("cardEase"),

    /**
     * Cards -> Timestamp the card was modified
     * Notes -> Most recent timestamp a card of the note was modified
     */
    CHANGED("cardMod"),

    /**
     * Timestamp the note was created
     */
    CREATED("noteCrt"),

    /**
     * Timestamp the note was last changed
     */
    EDITED("noteMod"),

    /**
     * Cards -> Interval
     * Notes -> Interval
     */
    INTERVAL("cardIvl"),
    LAPSES("cardLapses"),

    /**
     * The name of the note type `Basic (and reversed card)`
     */
    NOTE_TYPE("note"),
    REVIEWS("cardReps"),

    /**
     * The inherent complexity associated with a particular memory.
     * Used in FSRS, blank if using SM-2
     */
    FSRS_DIFFICULTY("difficulty"),

    /**
     * The probability of recalling a specific memory at a given moment.
     * Used in FSRS, blank if using SM-2
     */
    FSRS_RETRIEVABILITY("retrievability"),

    /**
     * The time required for the probability of recall for a particular memory to decline from
     * 100% to 90%.
     * Used in FSRS, blank if using SM-2
     */
    FSRS_STABILITY("stability"),

    /**
     * The position of the card, independent of any resets by the user.
     */
    ORIGINAL_POSITION("originalPosition");

    companion object {

        val COLUMN1_KEYS = arrayOf(QUESTION, SFLD)

        // list of available keys in mCards corresponding to the column names in R.array.browser_column2_headings.
        // Note: the last 6 are currently hidden
        val COLUMN2_KEYS = arrayOf(ANSWER, CARD, DECK, NOTE_TYPE, QUESTION, TAGS, LAPSES, REVIEWS, INTERVAL, EASE, DUE, CHANGED, CREATED, EDITED, ORIGINAL_POSITION)

        fun fromColumnKey(key: String): CardBrowserColumn =
            entries.firstOrNull { it.ankiColumnKey == key }
                ?: throw IllegalArgumentException("Invalid key: $key")
    }
}

fun List<BrowserColumns.Column>.find(column: CardBrowserColumn): BrowserColumns.Column =
    this.firstOrNull { it.key == column.ankiColumnKey }
        ?: throw IllegalArgumentException("Invalid column: ${column.ankiColumnKey}")
