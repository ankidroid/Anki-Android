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

class AnkiDroidJsAPIConstants {
    // JS API ERROR CODE
    @kotlin.jvm.JvmField
    var ankiJsErrorCodeDefault: Int = 0
    @kotlin.jvm.JvmField
    val ankiJsErrorCodeMarkCard: Int = 1
    @kotlin.jvm.JvmField
    val ankiJsErrorCodeFlagCard: Int = 2

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

    fun initApiMap(): HashMap<String, Boolean> {
        val jsApiListMap = HashMap<String, Boolean>()
        jsApiListMap[MARK_CARD] = false
        jsApiListMap[TOGGLE_FLAG] = false
        return jsApiListMap
    }
}
