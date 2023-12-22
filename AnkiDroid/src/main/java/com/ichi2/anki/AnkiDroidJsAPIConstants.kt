/****************************************************************************************
 * Copyright (c) 2020 Mani infinyte01@gmail.com                                         *
 *                                                                                      *
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
 * this program.  If not, see http://www.gnu.org/licenses/>.                            *
 *                                                                                      *
 * *************************************************************************************/

package com.ichi2.anki

import com.ichi2.anki.cardviewer.ViewerCommand

object AnkiDroidJsAPIConstants {
    // JS API ERROR CODE
    const val ankiJsErrorCodeError: Int = -1
    const val ankiJsErrorCodeDefault: Int = 0
    const val ankiJsErrorCodeMarkCard: Int = 1
    const val ankiJsErrorCodeFlagCard: Int = 2

    const val ankiJsErrorCodeBuryCard: Int = 3
    const val ankiJsErrorCodeSuspendCard: Int = 4
    const val ankiJsErrorCodeBuryNote: Int = 5
    const val ankiJsErrorCodeSuspendNote: Int = 6
    const val ankiJsErrorCodeSetDue: Int = 7
    const val ankiJsErrorCodeSearchCard: Int = 8

    // js api developer contact
    const val sCurrentJsApiVersion = "0.0.2"
    const val sMinimumJsApiVersion = "0.0.2"

    val flagCommands =
        mapOf(
            "none" to ViewerCommand.UNSET_FLAG,
            "red" to ViewerCommand.TOGGLE_FLAG_RED,
            "orange" to ViewerCommand.TOGGLE_FLAG_ORANGE,
            "green" to ViewerCommand.TOGGLE_FLAG_GREEN,
            "blue" to ViewerCommand.TOGGLE_FLAG_BLUE,
            "pink" to ViewerCommand.TOGGLE_FLAG_PINK,
            "turquoise" to ViewerCommand.TOGGLE_FLAG_TURQUOISE,
            "purple" to ViewerCommand.TOGGLE_FLAG_PURPLE,
        )
}
