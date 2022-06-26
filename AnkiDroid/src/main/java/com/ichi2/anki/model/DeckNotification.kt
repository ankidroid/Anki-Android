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
    val schedHour: Int,
    val schedMinutes: Int,
) {

    // Empty constructor. Required for jackson to serialize.
    constructor() : this(
        false,
        Decks.NOT_FOUND_DECK_ID,
        0,
        0
    )
}

/**
 * User Specific preference for notification.
 * @param did Id of deck whose preference is stored.
 * @param started Day on which deck started.
 * @param completed Deck is done for the day or not.
 * */
data class UserNotificationPreference(
    val did: DeckId,
    val started: Long,
    val completed: Boolean
) {
    constructor() : this(
        Decks.NOT_FOUND_DECK_ID,
        -1L,
        false
    )
}
