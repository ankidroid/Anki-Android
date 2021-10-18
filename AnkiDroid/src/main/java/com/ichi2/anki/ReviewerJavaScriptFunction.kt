/****************************************************************************************
 * Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>                      *
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

import android.webkit.JavascriptInterface

class ReviewerJavaScriptFunction(private var activity: Reviewer) : AnkiDroidJsAPI(activity) {

    @JavascriptInterface
    override fun ankiGetNewCardCount(): String? {
        return activity.mNewCount.toString()
    }

    @JavascriptInterface
    override fun ankiGetLrnCardCount(): String? {
        return activity.mLrnCount.toString()
    }

    @JavascriptInterface
    override fun ankiGetRevCardCount(): String? {
        return activity.mRevCount.toString()
    }

    @JavascriptInterface
    override fun ankiGetETA(): Int {
        return activity.mEta
    }

    @JavascriptInterface
    override fun ankiGetNextTime1(): String {
        return activity.mEaseButton1.nextTime
    }

    @JavascriptInterface
    override fun ankiGetNextTime2(): String {
        return activity.mEaseButton2.nextTime
    }

    @JavascriptInterface
    override fun ankiGetNextTime3(): String {
        return activity.mEaseButton3.nextTime
    }

    @JavascriptInterface
    override fun ankiGetNextTime4(): String {
        return activity.mEaseButton4.nextTime
    }

    @JavascriptInterface
    override fun ankiSetCardNid(nid: Long): Boolean {
        if (!retErrorCode(const.SET_CARD_NID, const.ankiJsErrorCodeSetCardNid)) {
            return false
        }

        activity.currentCard.nid = nid
        return true
    }

    @JavascriptInterface
    override fun ankiSetCardDid(did: Long): Boolean {
        if (!retErrorCode(const.SET_CARD_DID, const.ankiJsErrorCodeSetCardDid)) {
            return false
        }

        activity.currentCard.did = did
        return true
    }

    @JavascriptInterface
    override fun ankiSetCardODid(odid: Long): Boolean {
        if (!retErrorCode(const.SET_CARD_ODID, const.ankiJsErrorCodeSetCardODid)) {
            return false
        }

        activity.currentCard.oDid = odid
        return true
    }

    @JavascriptInterface
    override fun ankiSetCardType(type: Int): Boolean {
        if (!retErrorCode(const.SET_CARD_TYPE, const.ankiJsErrorCodeSetCardType)) {
            return false
        }

        activity.currentCard.type = type
        return true
    }

    @JavascriptInterface
    override fun ankiSetCardDue(due: Long): Boolean {
        if (!retErrorCode(const.SET_CARD_DUE, const.ankiJsErrorCodeSetCardDue)) {
            return false
        }

        activity.currentCard.due = due
        return true
    }

    @JavascriptInterface
    override fun ankiSetCardIvl(ivl: Int): Boolean {
        if (!retErrorCode(const.SET_CARD_IVL, const.ankiJsErrorCodeSetCardIvl)) {
            return false
        }

        activity.currentCard.ivl = ivl
        return true
    }

    @JavascriptInterface
    override fun ankiSetCardLastIvl(ivl: Int): Boolean {
        if (!retErrorCode(const.SET_CARD_LAST_IVL, const.ankiJsErrorCodeSetCardLastIvl)) {
            return false
        }

        activity.currentCard.lastIvl = ivl
        return true
    }

    @JavascriptInterface
    override fun ankiSetCardQueue(queue: Int): Boolean {
        if (!retErrorCode(const.SET_CARD_QUEUE, const.ankiJsErrorCodeSetCardQueue)) {
            return false
        }

        activity.currentCard.queue = queue
        return true
    }

    @JavascriptInterface
    override fun ankiSetCardReps(reps: Int): Boolean {
        if (!retErrorCode(const.SET_CARD_REPS, const.ankiJsErrorCodeSetCardReps)) {
            return false
        }

        activity.currentCard.reps = reps
        return true
    }

    @JavascriptInterface
    override fun ankiSetCardLapses(lapses: Int): Boolean {
        if (!retErrorCode(const.SET_CARD_LAPSES, const.ankiJsErrorCodeSetCardLapses)) {
            return false
        }

        activity.currentCard.lapses = lapses
        return true
    }

    @JavascriptInterface
    override fun ankiSetCardLeft(left: Int): Boolean {
        if (!retErrorCode(const.SET_CARD_LEFT, const.ankiJsErrorCodeSetCardLeft)) {
            return false
        }

        activity.currentCard.left = left
        return true
    }

    @JavascriptInterface
    override fun ankiSetCardMod(mod: Long): Boolean {
        if (!retErrorCode(const.SET_CARD_MOD, const.ankiJsErrorCodeSetCardMod)) {
            return false
        }

        activity.currentCard.mod = mod
        return true
    }

    @JavascriptInterface
    override fun ankiSetCardOrd(ord: Int): Boolean {
        if (!retErrorCode(const.SET_CARD_ORD, const.ankiJsErrorCodeSetCardOrd)) {
            return false
        }

        activity.currentCard.ord = ord
        return true
    }

    @JavascriptInterface
    override fun ankiSetCardFactor(factor: Int): Boolean {
        if (!retErrorCode(const.SET_CARD_Factor, const.ankiJsErrorCodeSetCardFactor)) {
            return false
        }

        activity.currentCard.factor = factor
        return true
    }

    @JavascriptInterface
    override fun ankiSetCardWasNew(wasNew: Boolean): Boolean {
        if (!retErrorCode(const.SET_CARD_WAS_NEW, const.ankiJsErrorCodeSetCardWasNew)) {
            return false
        }

        activity.currentCard.wasNew = wasNew
        return true
    }
}
