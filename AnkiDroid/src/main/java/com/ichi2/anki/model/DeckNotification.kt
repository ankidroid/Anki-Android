/*
 * Copyright (c) 2022 Prateek Singh <prateeksingh3212@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.model

import com.ichi2.libanki.DeckId
import com.ichi2.libanki.Decks

/**
 * Notification details of particular deck.
 * */
data class DeckNotification(
    val enabled: Boolean,
    val did: DeckId,
    val notificationTime: NotificationTime,
    val deckPreference: DeckPreference,
    val includeSubdecks: Boolean
) {

    // Empty constructor. Required for jackson to serialize.
    constructor() : this(
        false,
        Decks.NOT_FOUND_DECK_ID,
        NotificationTime(),
        DeckPreference(),
        false
    )

    /**
     * Stores the time at which the deck notification is going to trigger.
     * */
    data class NotificationTime(
        val hour: Int,
        val minutes: Int
    ) {

        // Empty constructor. Required for jackson to serialize.
        constructor() : this(
            0,
            0
        )
    }

    /**
     * Store the preference of deck which is checked while triggering the Deck Notification.
     * */
    data class DeckPreference(
        val number: Int,
        val valueType: PreferenceType,
    ) {

        // Empty constructor. Required for jackson to serialize.
        constructor() : this(
            10,
            PreferenceType.CARDS
        )

        /**
         * Defines the type of value stored in [number] in [DeckPreference].
         * if [valueType] is [CARDS] then [number] represents the card studied and
         * if [valueType] is [MINUTES] then [number] represents the minutes studied.
         * */
        enum class PreferenceType {
            CARDS,
            MINUTES
        }
    }
}
