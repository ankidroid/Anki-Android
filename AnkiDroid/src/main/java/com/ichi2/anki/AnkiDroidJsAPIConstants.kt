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

object AnkiDroidJsAPIConstants {
    // JS API ERROR CODE
    const val ankiJsErrorCodeDefault: Int = 0
    const val ankiJsErrorCodeMarkCard: Int = 1
    const val ankiJsErrorCodeFlagCard: Int = 2

    const val ankiJsErrorCodeBuryCard: Int = 3
    const val ankiJsErrorCodeSuspendCard: Int = 4
    const val ankiJsErrorCodeBuryNote: Int = 5
    const val ankiJsErrorCodeSuspendNote: Int = 6
    const val ankiJsErrorCodeSetDue: Int = 7

    // js api developer contact
    const val sCurrentJsApiVersion = "0.0.2"
    const val sMinimumJsApiVersion = "0.0.2"

    const val MARK_CARD = "markCard"
    const val TOGGLE_FLAG = "toggleFlag"

    const val BURY_CARD = "buryCard"
    const val BURY_NOTE = "buryNote"
    const val SUSPEND_CARD = "suspendCard"
    const val SUSPEND_NOTE = "suspendNote"
    const val SET_CARD_DUE = "setCardDue"
    const val RESET_PROGRESS = "setCardDue"

    fun initApiMap(): HashMap<String, Boolean> {
        val jsApiListMap = HashMap<String, Boolean>()
        jsApiListMap[MARK_CARD] = false
        jsApiListMap[TOGGLE_FLAG] = false

        jsApiListMap[BURY_CARD] = false
        jsApiListMap[BURY_NOTE] = false
        jsApiListMap[SUSPEND_CARD] = false
        jsApiListMap[SUSPEND_NOTE] = false
        jsApiListMap[SET_CARD_DUE] = false
        jsApiListMap[RESET_PROGRESS] = false

        return jsApiListMap
    }
}
