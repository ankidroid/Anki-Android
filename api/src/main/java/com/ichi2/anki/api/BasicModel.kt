/*
 * Copyright (c) 2022 Sanjaykumar Sargam <sargamsanjaykumar@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.api

/**
 * Definitions of the basic model
 */
internal object BasicModel {
    @JvmField // required for API
    @Suppress("ktlint:standard:property-naming")
    val FIELDS = arrayOf("Front", "Back")

    // List of card names that will be used in AnkiDroid (one for each direction of learning)
    @JvmField // required for API
    val CARD_NAMES = arrayOf("Card 1")

    // Template for the question of each card
    @JvmField // required for API
    val QFMT = arrayOf("{{Front}}")

    @JvmField // required for API
    val AFMT =
        arrayOf(
            """{{FrontSide}}

        |<hr id="answer">

        |{{Back}}
            """.trimMargin(),
        )
}
