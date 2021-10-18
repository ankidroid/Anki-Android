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

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.text.TextUtils
import android.view.View
import android.webkit.JavascriptInterface
import android.widget.TextView
import com.github.zafarkhaja.semver.Version
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.UIUtils.showSimpleSnackbar
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.libanki.Card
import com.ichi2.libanki.Consts.CARD_QUEUE
import com.ichi2.libanki.Consts.CARD_TYPE
import com.ichi2.libanki.Decks
import com.ichi2.utils.JSONException
import com.ichi2.utils.JSONObject
import timber.log.Timber
import java.util.*

open class AnkiDroidJsAPI(private var activity: AbstractFlashcardViewer) {
    private var currentCard: Card = activity.currentCard
    private val context: Context = activity

    /**
     Javascript Interface class for calling Java function from AnkiDroid WebView
     see card.js for available functions
     */

    var const = AnkiDroidJsAPIConst()

    // JS api list enable/disable status
    private val mJsApiListMap = const.initApiMap()

    // init boolean value to check if api initialize or not
    protected var mIsJsApiInit: Boolean = false

    // Text to speech
    private val mTalker = JavaScriptTTS()

    // init or reset api list
    fun init() {
        const.mCardSuppliedApiVersion = ""
        const.mCardSuppliedDeveloperContact = ""
    }

    // Check if value null
    protected fun isAnkiApiNull(api: String): Boolean {
        return mJsApiListMap[api] == null
    }

    // If Api is not initialize or JS API disabled then show snackbar
    fun retErrorCode(apiName: String, errorCode: Int): Boolean {
        if (!mIsJsApiInit) {
            showDeveloperContact(const.ankiJsErrorCodeDefault)
            return false
        } else if (!getJsApiListMap()?.get(apiName)!!) {
            showDeveloperContact(errorCode)
            return false
        }
        return true
    }

    /*
     * see 02-strings.xml
     * Show Error code when mark card or flag card unsupported
     * 1 - mark card
     * 2 - flag card
     *
     * show developer contact if js api used in card is deprecated
     */
    fun showDeveloperContact(errorCode: Int) {
        val errorMsg: String = context.getString(R.string.anki_js_error_code, errorCode)
        val parentLayout: View = activity.findViewById(android.R.id.content)
        val snackbarMsg: String = context.getString(R.string.api_version_developer_contact, const.mCardSuppliedDeveloperContact, errorMsg)
        val snackbar: Snackbar? = UIUtils.showSnackbar(
            activity,
            snackbarMsg,
            false,
            R.string.reviewer_invalid_api_version_visit_documentation,
            { activity.openUrl(Uri.parse("https://github.com/ankidroid/Anki-Android/wiki")) },
            parentLayout,
            null
        )
        val snackbarTextView = snackbar!!.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        snackbarTextView.maxLines = 3
        snackbar.show()
    }

    /**
     * Supplied api version must be equal to current api version to call mark card, toggle flag functions etc.
     */
    private fun requireApiVersion(apiVer: String, apiDevContact: String): Boolean {
        try {
            if (TextUtils.isEmpty(apiDevContact)) {
                return false
            }
            val versionCurrent = Version.valueOf(const.sCurrentJsApiVersion)
            val versionSupplied = Version.valueOf(apiVer)

            /*
            * if api major version equals to supplied major version then return true and also check for minor version and patch version
            * show toast for update and contact developer if need updates
            * otherwise return false
            */
            return when {
                versionSupplied == versionCurrent -> {
                    true
                }
                versionSupplied.lessThan(versionCurrent) -> {
                    showThemedToast(context, context.getString(R.string.update_js_api_version, const.mCardSuppliedDeveloperContact), false)
                    versionSupplied.greaterThanOrEqualTo(Version.valueOf(const.sMinimumJsApiVersion))
                }
                else -> {
                    showThemedToast(context, context.getString(R.string.valid_js_api_version, const.mCardSuppliedDeveloperContact), false)
                    false
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "requireApiVersion::exception")
        }
        return false
    }

    // if supplied api version match then enable api
    private fun enableJsApi() {
        for (api in mJsApiListMap) {
            mJsApiListMap[api.key] = true
        }
    }

    fun getJsApiListMap(): HashMap<String, Boolean>? {
        return mJsApiListMap
    }

    @JavascriptInterface
    fun init(jsonData: String?): String {
        val data: JSONObject
        var apiStatusJson = ""
        try {
            data = JSONObject(jsonData)
            const.mCardSuppliedApiVersion = data.optString("version", "")
            const.mCardSuppliedDeveloperContact = data.optString("developer", "")
            if (requireApiVersion(const.mCardSuppliedApiVersion, const.mCardSuppliedDeveloperContact)) {
                enableJsApi()
                mIsJsApiInit = true
            }
            apiStatusJson = JSONObject.fromMap(mJsApiListMap).toString()
        } catch (j: JSONException) {
            Timber.w(j)
            showThemedToast(context, context.getString(R.string.invalid_json_data, j.localizedMessage), false)
        }
        return apiStatusJson
    }

    // This method and the one belows return "default" values when there is no count nor ETA.
    // Javascript may expect ETA and Counts to be set, this ensure it does not bug too much by providing a value of correct type
    // but with a clearly incorrect value.
    // It's overridden in the Reviewer, where those values are actually defined.
    @JavascriptInterface
    open fun ankiGetNewCardCount(): String? {
        return "-1"
    }

    @JavascriptInterface
    open fun ankiGetLrnCardCount(): String? {
        return "-1"
    }

    @JavascriptInterface
    open fun ankiGetRevCardCount(): String? {
        return "-1"
    }

    @JavascriptInterface
    open fun ankiGetETA(): Int {
        return -1
    }

    @JavascriptInterface
    fun ankiGetCardMark(): Boolean {
        return currentCard.note().hasTag("marked")
    }

    @JavascriptInterface
    fun ankiGetCardFlag(): Int {
        return currentCard.userFlag()
    }

    // behavior change ankiGetNextTime1...4
    @JavascriptInterface
    open fun ankiGetNextTime1(): String {
        return activity.mEaseButton1.nextTime
    }

    @JavascriptInterface
    open fun ankiGetNextTime2(): String {
        return activity.mEaseButton2.nextTime
    }

    @JavascriptInterface
    open fun ankiGetNextTime3(): String {
        return activity.mEaseButton3.nextTime
    }

    @JavascriptInterface
    open fun ankiGetNextTime4(): String {
        return activity.mEaseButton4.nextTime
    }

    @JavascriptInterface
    fun ankiGetCardReps(): Int {
        return activity.currentCard.reps
    }

    @JavascriptInterface
    fun ankiGetCardInterval(): Int {
        return activity.currentCard.ivl
    }

    /** Returns the ease as an int (percentage * 10). Default: 2500 (250%). Minimum: 1300 (130%)  */
    @JavascriptInterface
    fun ankiGetCardFactor(): Int {
        return currentCard.factor
    }

    /** Returns the last modified time as a Unix timestamp in seconds. Example: 1477384099  */
    @JavascriptInterface
    fun ankiGetCardMod(): Long {
        return currentCard.mod
    }

    /** Returns the ID of the card. Example: 1477380543053  */
    @JavascriptInterface
    fun ankiGetCardId(): Long {
        return currentCard.id
    }

    /** Returns the ID of the note which generated the card. Example: 1590418157630  */
    @JavascriptInterface
    fun ankiGetCardNid(): Long {
        return currentCard.nid
    }

    @JavascriptInterface
    @CARD_TYPE
    fun ankiGetCardType(): Int {
        return currentCard.type
    }

    /** Returns the ID of the deck which contains the card. Example: 1595967594978  */
    @JavascriptInterface
    fun ankiGetCardDid(): Long {
        return currentCard.did
    }

    @JavascriptInterface
    fun ankiGetCardLeft(): Int {
        return currentCard.left
    }

    /** Returns the ID of the home deck for the card if it is filtered, or 0 if not filtered. Example: 1595967594978  */
    @JavascriptInterface
    fun ankiGetCardODid(): Long {
        return currentCard.oDid
    }

    @JavascriptInterface
    fun ankiGetCardODue(): Long {
        return currentCard.oDue
    }

    @JavascriptInterface
    @CARD_QUEUE
    fun ankiGetCardQueue(): Int {
        return currentCard.queue
    }

    @JavascriptInterface
    fun ankiGetCardLapses(): Int {
        return currentCard.lapses
    }

    @JavascriptInterface
    fun ankiGetCardDue(): Long {
        return currentCard.due
    }

    @JavascriptInterface
    fun ankiIsInFullscreen(): Boolean {
        return activity.isFullscreen
    }

    @JavascriptInterface
    fun ankiIsTopbarShown(): Boolean {
        return activity.mPrefShowTopbar
    }

    @JavascriptInterface
    fun ankiIsInNightMode(): Boolean {
        return activity.isInNightMode
    }

    @JavascriptInterface
    fun ankiIsDisplayingAnswer(): Boolean {
        return activity.isDisplayingAnswer
    }

    @JavascriptInterface
    fun ankiGetDeckName(): String {
        return Decks.basename(activity.col.decks.get(currentCard.did).getString("name"))
    }

    @JavascriptInterface
    fun ankiBuryCard(): Boolean {
        if (!retErrorCode(const.BURY_CARD, const.ankiJsErrorCodeBuryCard)) {
            return false
        }

        return activity.buryCard()
    }

    @JavascriptInterface
    fun ankiBuryNote(): Boolean {
        if (!retErrorCode(const.BURY_NOTE, const.ankiJsErrorCodeBuryNote)) {
            return false
        }

        return activity.buryNote()
    }

    @JavascriptInterface
    fun ankiSuspendCard(): Boolean {
        if (!retErrorCode(const.SUSPEND_CARD, const.ankiJsErrorCodeSuspendCard)) {
            return false
        }

        return activity.suspendCard()
    }

    @JavascriptInterface
    fun ankiSuspendNote(): Boolean {
        if (!retErrorCode(const.SUSPEND_NOTE, const.ankiJsErrorCodeSuspendNote)) {
            return false
        }

        return activity.suspendNote()
    }

    @JavascriptInterface
    fun ankiAddTagToCard() {
        activity.runOnUiThread { activity.showTagsDialog() }
    }

    @JavascriptInterface
    fun ankiSearchCard(query: String?) {
        val intent = Intent(context, CardBrowser::class.java)
        val currentCardId: Long = currentCard.id
        intent.putExtra("currentCard", currentCardId)
        intent.putExtra("search_query", query)
        activity.startActivityForResultWithAnimation(intent, NavigationDrawerActivity.REQUEST_BROWSE_CARDS, ActivityTransitionAnimation.Direction.START)
    }

    @JavascriptInterface
    fun ankiIsActiveNetworkMetered(): Boolean {
        return try {
            val cm = AnkiDroidApp.getInstance().applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.isActiveNetworkMetered
        } catch (e: Exception) {
            Timber.w(e, "Exception obtaining metered connection - assuming metered connection")
            true
        }
    }

    @JavascriptInterface
    fun ankiTtsSpeak(text: String?, queueMode: Int): Int {
        return mTalker.speak(text, queueMode)
    }

    @JavascriptInterface
    fun ankiTtsSpeak(text: String?): Int {
        return mTalker.speak(text)
    }

    @JavascriptInterface
    fun ankiTtsSetLanguage(loc: String?): Int {
        return mTalker.setLanguage(loc)
    }

    @JavascriptInterface
    fun ankiTtsSetPitch(pitch: Float): Int {
        return mTalker.setPitch(pitch)
    }

    @JavascriptInterface
    fun ankiTtsSetPitch(pitch: Double): Int {
        return mTalker.setPitch(pitch.toFloat())
    }

    @JavascriptInterface
    fun ankiTtsSetSpeechRate(speechRate: Float): Int {
        return mTalker.setSpeechRate(speechRate)
    }

    @JavascriptInterface
    fun ankiTtsSetSpeechRate(speechRate: Double): Int {
        return mTalker.setSpeechRate(speechRate.toFloat())
    }

    @JavascriptInterface
    fun ankiTtsIsSpeaking(): Boolean {
        return mTalker.isSpeaking
    }

    @JavascriptInterface
    fun ankiTtsStop(): Int {
        return mTalker.stop()
    }

    @JavascriptInterface
    fun ankiEnableHorizontalScrollbar(scroll: Boolean) {
        activity.webView.isHorizontalScrollBarEnabled = scroll
    }

    @JavascriptInterface
    fun ankiEnableVerticalScrollbar(scroll: Boolean) {
        activity.webView.isVerticalScrollBarEnabled = scroll
    }

    /**
     * The functions below are used to set value to current card
     *
     * The function will show snackbar with values that can be set in previewer.
     * These are overridden in ReviewerJavascriptFunction to set values to current card
     */

    @JavascriptInterface
    open fun ankiSetCardNid(nid: Long): Boolean {
        if (!mIsJsApiInit) {
            showDeveloperContact(const.ankiJsErrorCodeDefault)
            return false
        }
        showSimpleSnackbar(activity, context.getString(R.string.anki_set_card_msg, currentCard.nid.toString(), nid.toString()), false)
        return true
    }

    @JavascriptInterface
    open fun ankiSetCardDid(did: Long): Boolean {
        if (!mIsJsApiInit) {
            showDeveloperContact(const.ankiJsErrorCodeDefault)
            return false
        }
        showSimpleSnackbar(activity, context.getString(R.string.anki_set_card_msg, currentCard.did.toString(), did.toString()), false)
        return true
    }

    @JavascriptInterface
    open fun ankiSetCardODid(odid: Long): Boolean {
        if (!mIsJsApiInit) {
            showDeveloperContact(const.ankiJsErrorCodeDefault)
            return false
        }
        showSimpleSnackbar(activity, context.getString(R.string.anki_set_card_msg, currentCard.oDid.toString(), odid.toString()), false)
        return true
    }

    @JavascriptInterface
    open fun ankiSetCardType(type: Int): Boolean {
        if (!mIsJsApiInit) {
            showDeveloperContact(const.ankiJsErrorCodeDefault)
            return false
        }
        showSimpleSnackbar(activity, context.getString(R.string.anki_set_card_msg, currentCard.type.toString(), type.toString()), false)
        return true
    }

    @JavascriptInterface
    open fun ankiSetCardDue(due: Long): Boolean {
        if (!mIsJsApiInit) {
            showDeveloperContact(const.ankiJsErrorCodeDefault)
            return false
        }
        showSimpleSnackbar(activity, context.getString(R.string.anki_set_card_msg, currentCard.due.toString(), due.toString()), false)
        return true
    }

    @JavascriptInterface
    open fun ankiSetCardIvl(ivl: Int): Boolean {
        if (!mIsJsApiInit) {
            showDeveloperContact(const.ankiJsErrorCodeDefault)
            return false
        }
        showSimpleSnackbar(activity, context.getString(R.string.anki_set_card_msg, currentCard.ivl.toString(), ivl.toString()), false)
        return true
    }

    @JavascriptInterface
    open fun ankiSetCardLastIvl(ivl: Int): Boolean {
        if (!mIsJsApiInit) {
            showDeveloperContact(const.ankiJsErrorCodeDefault)
            return false
        }
        showSimpleSnackbar(activity, context.getString(R.string.anki_set_card_msg, currentCard.lastIvl.toString(), ivl.toString()), false)
        return true
    }

    @JavascriptInterface
    open fun ankiSetCardQueue(queue: Int): Boolean {
        if (!mIsJsApiInit) {
            showDeveloperContact(const.ankiJsErrorCodeDefault)
            return false
        }
        showSimpleSnackbar(activity, context.getString(R.string.anki_set_card_msg, currentCard.queue.toString(), queue.toString()), false)
        return true
    }

    @JavascriptInterface
    open fun ankiSetCardReps(reps: Int): Boolean {
        if (!mIsJsApiInit) {
            showDeveloperContact(const.ankiJsErrorCodeDefault)
            return false
        }
        showSimpleSnackbar(activity, context.getString(R.string.anki_set_card_msg, currentCard.reps.toString(), reps.toString()), false)
        return true
    }

    @JavascriptInterface
    open fun ankiSetCardLapses(lapses: Int): Boolean {
        if (!mIsJsApiInit) {
            showDeveloperContact(const.ankiJsErrorCodeDefault)
            return false
        }
        showSimpleSnackbar(activity, context.getString(R.string.anki_set_card_msg, currentCard.lapses.toString(), lapses.toString()), false)
        return true
    }

    @JavascriptInterface
    open fun ankiSetCardLeft(left: Int): Boolean {
        if (!mIsJsApiInit) {
            showDeveloperContact(const.ankiJsErrorCodeDefault)
            return false
        }
        showSimpleSnackbar(activity, context.getString(R.string.anki_set_card_msg, currentCard.left.toString(), left.toString()), false)
        return true
    }

    @JavascriptInterface
    open fun ankiSetCardMod(mod: Long): Boolean {
        if (!mIsJsApiInit) {
            showDeveloperContact(const.ankiJsErrorCodeDefault)
            return false
        }
        showSimpleSnackbar(activity, context.getString(R.string.anki_set_card_msg, currentCard.mod.toString(), mod.toString()), false)
        return true
    }

    @JavascriptInterface
    open fun ankiSetCardOrd(ord: Int): Boolean {
        if (!mIsJsApiInit) {
            showDeveloperContact(const.ankiJsErrorCodeDefault)
            return false
        }
        showSimpleSnackbar(activity, context.getString(R.string.anki_set_card_msg, currentCard.ord.toString(), ord.toString()), false)
        return true
    }

    @JavascriptInterface
    open fun ankiSetCardFactor(factor: Int): Boolean {
        if (!mIsJsApiInit) {
            showDeveloperContact(const.ankiJsErrorCodeDefault)
            return false
        }
        showSimpleSnackbar(activity, context.getString(R.string.anki_set_card_msg, currentCard.factor.toString(), factor.toString()), false)
        return true
    }

    @JavascriptInterface
    open fun ankiSetCardWasNew(wasNew: Boolean): Boolean {
        if (!mIsJsApiInit) {
            showDeveloperContact(const.ankiJsErrorCodeDefault)
            return false
        }
        showSimpleSnackbar(activity, context.getString(R.string.anki_set_card_msg, currentCard.wasNew.toString(), wasNew.toString()), false)
        return true
    }
}
