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
 * this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.                  *
 *                                                                                      *
 * *************************************************************************************/

package com.ichi2.anki

class AnkiDroidJsAPIConst {
    // JS API ERROR CODE
    @kotlin.jvm.JvmField
    var ankiJsErrorCodeDefault: Int = 0
    @kotlin.jvm.JvmField
    val ankiJsErrorCodeMarkCard: Int = 1
    @kotlin.jvm.JvmField
    val ankiJsErrorCodeFlagCard: Int = 2

    @kotlin.jvm.JvmField
    val ankiJsErrorCodeBuryCard: Int = 3
    @kotlin.jvm.JvmField
    val ankiJsErrorCodeBuryNote: Int = 4
    @kotlin.jvm.JvmField
    val ankiJsErrorCodeSuspendCard: Int = 5
    @kotlin.jvm.JvmField
    val ankiJsErrorCodeSuspendNote: Int = 6

    @kotlin.jvm.JvmField
    val ankiJsErrorCodeSetCardDid: Int = 7
    @kotlin.jvm.JvmField
    val ankiJsErrorCodeSetCardNid: Int = 8
    @kotlin.jvm.JvmField
    val ankiJsErrorCodeSetCardODid: Int = 9
    @kotlin.jvm.JvmField
    val ankiJsErrorCodeSetCardType: Int = 10
    @kotlin.jvm.JvmField
    val ankiJsErrorCodeSetCardDue: Int = 11
    @kotlin.jvm.JvmField
    val ankiJsErrorCodeSetCardIvl: Int = 12
    @kotlin.jvm.JvmField
    val ankiJsErrorCodeSetCardLastIvl: Int = 13
    @kotlin.jvm.JvmField
    val ankiJsErrorCodeSetCardQueue: Int = 14
    @kotlin.jvm.JvmField
    val ankiJsErrorCodeSetCardReps: Int = 15
    @kotlin.jvm.JvmField
    val ankiJsErrorCodeSetCardLapses: Int = 16
    @kotlin.jvm.JvmField
    val ankiJsErrorCodeSetCardLeft: Int = 17
    @kotlin.jvm.JvmField
    val ankiJsErrorCodeSetCardMod: Int = 18
    @kotlin.jvm.JvmField
    val ankiJsErrorCodeSetCardOrd: Int = 19
    @kotlin.jvm.JvmField
    val ankiJsErrorCodeSetCardFactor: Int = 20
    @kotlin.jvm.JvmField
    val ankiJsErrorCodeSetCardWasNew: Int = 21

    // js api developer contact
    @kotlin.jvm.JvmField
    var mCardSuppliedDeveloperContact = ""
    @kotlin.jvm.JvmField
    var mCardSuppliedApiVersion = ""

    val sCurrentJsApiVersion = "0.0.1"
    val sMinimumJsApiVersion = "0.0.1"

    @kotlin.jvm.JvmField
    val MARK_CARD = "markCard"
    @kotlin.jvm.JvmField
    val TOGGLE_FLAG = "toggleFlag"

    @kotlin.jvm.JvmField
    val BURY_CARD = "buryCard"
    @kotlin.jvm.JvmField
    val BURY_NOTE = "buryNote"
    @kotlin.jvm.JvmField
    val SUSPEND_CARD = "suspendCard"
    @kotlin.jvm.JvmField
    val SUSPEND_NOTE = "suspendNote"

    @kotlin.jvm.JvmField
    val SET_CARD_DID = "setCardDid"

    @kotlin.jvm.JvmField
    val SET_CARD_NID = "setCardNid"

    @kotlin.jvm.JvmField
    val SET_CARD_ODID = "setCardODid"

    @kotlin.jvm.JvmField
    val SET_CARD_TYPE = "setCardType"

    @kotlin.jvm.JvmField
    val SET_CARD_DUE = "setCardDue"

    @kotlin.jvm.JvmField
    val SET_CARD_IVL = "setCardIvl"

    @kotlin.jvm.JvmField
    val SET_CARD_LAST_IVL = "setCardLastIvl"

    @kotlin.jvm.JvmField
    val SET_CARD_QUEUE = "setCardQueue"

    @kotlin.jvm.JvmField
    val SET_CARD_REPS = "setCardReps"

    @kotlin.jvm.JvmField
    val SET_CARD_LAPSES = "setCardLapses"

    @kotlin.jvm.JvmField
    val SET_CARD_LEFT = "setCardLeft"

    @kotlin.jvm.JvmField
    val SET_CARD_MOD = "setCardMod"

    @kotlin.jvm.JvmField
    val SET_CARD_ORD = "setCardOrd"

    @kotlin.jvm.JvmField
    val SET_CARD_Factor = "setCardFactor"

    @kotlin.jvm.JvmField
    val SET_CARD_WAS_NEW = "setCardWasNew"

    fun initApiMap(): HashMap<String, Boolean> {
        val mJsApiListMap = HashMap<String, Boolean>()
        mJsApiListMap[MARK_CARD] = false
        mJsApiListMap[TOGGLE_FLAG] = false

        mJsApiListMap[BURY_CARD] = false
        mJsApiListMap[BURY_NOTE] = false
        mJsApiListMap[SUSPEND_CARD] = false
        mJsApiListMap[SUSPEND_NOTE] = false

        mJsApiListMap[SET_CARD_DID] = false
        mJsApiListMap[SET_CARD_NID] = false
        mJsApiListMap[SET_CARD_ODID] = false
        mJsApiListMap[SET_CARD_TYPE] = false
        mJsApiListMap[SET_CARD_DUE] = false
        mJsApiListMap[SET_CARD_IVL] = false
        mJsApiListMap[SET_CARD_LAST_IVL] = false
        mJsApiListMap[SET_CARD_QUEUE] = false
        mJsApiListMap[SET_CARD_REPS] = false
        mJsApiListMap[SET_CARD_LAPSES] = false
        mJsApiListMap[SET_CARD_LEFT] = false
        mJsApiListMap[SET_CARD_MOD] = false
        mJsApiListMap[SET_CARD_ORD] = false
        mJsApiListMap[SET_CARD_Factor] = false
        mJsApiListMap[SET_CARD_WAS_NEW] = false
        return mJsApiListMap
    }
}
