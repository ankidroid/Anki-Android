/***************************************************************************************
 * Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>                       *
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

package com.ichi2.libanki.stats

// These take and return bytes that the frontend TypeScript code will encode/decode.

import com.ichi2.libanki.CollectionV16

fun CollectionV16.cardStatsRaw(input: ByteArray): ByteArray {
    return backend.cardStatsRaw(input)
}

fun CollectionV16.graphsRaw(input: ByteArray): ByteArray {
    return backend.graphsRaw(input)
}

fun CollectionV16.getGraphPreferencesRaw(): ByteArray {
    val prefs = backend.getGraphPreferences().toBuilder()
        .setBrowserLinksSupported(false)
        .build()
    return prefs.toByteArray()
}

fun CollectionV16.setGraphPreferencesRaw(input: ByteArray): ByteArray {
    return backend.setGraphPreferencesRaw(input)
}
