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
 * this program.  If not, see http://www.gnu.org/licenses/>.                            *
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
import android.webkit.WebView
import android.widget.TextView
import com.github.zafarkhaja.semver.Version
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.servicelayer.SearchService
import com.ichi2.async.CollectionTask.SearchCards
import com.ichi2.async.TaskListener
import com.ichi2.async.TaskManager
import com.ichi2.libanki.Consts.CARD_QUEUE
import com.ichi2.libanki.Consts.CARD_TYPE
import com.ichi2.libanki.Decks
import com.ichi2.libanki.SortOrder
import com.ichi2.utils.JSONException
import com.ichi2.utils.JSONObject
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber

@KotlinCleanup("replace activity.currentCard!! with property")
open class AnkiDroidJsAPI(private val activity: AbstractFlashcardViewer) {
    /**
     Javascript Interface class for calling Java function from AnkiDroid WebView
     see card.js for available functions
     */

    private val context: Context = activity
    private var cardSuppliedDeveloperContact = ""
    private var cardSuppliedApiVersion = ""

    // JS api list enable/disable status
    private var mJsApiListMap = AnkiDroidJsAPIConstants.initApiMap()

    // Text to speech
    private val mTalker = JavaScriptTTS()

    // init or reset api list
    fun init() {
        cardSuppliedApiVersion = ""
        cardSuppliedDeveloperContact = ""
        mJsApiListMap = AnkiDroidJsAPIConstants.initApiMap()
    }

    // Check if value null
    private fun isAnkiApiNull(api: String): Boolean {
        return mJsApiListMap[api] == null
    }

    /**
     * Before calling js api check it init or not. It requires api name its error code.
     * If developer contract provided with correct js api version then it returns true
     *
     *
     * @param apiName
     * @param apiErrorCode
     */
    fun isInit(apiName: String, apiErrorCode: Int): Boolean {
        if (isAnkiApiNull(apiName)) {
            showDeveloperContact(AnkiDroidJsAPIConstants.ankiJsErrorCodeDefault)
            return false
        } else if (!getJsApiListMap()?.get(apiName)!!) {
            // see 02-string.xml
            showDeveloperContact(apiErrorCode)
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
        val snackbarMsg: String = context.getString(R.string.api_version_developer_contact, cardSuppliedDeveloperContact, errorMsg)
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
            val versionCurrent = Version.valueOf(AnkiDroidJsAPIConstants.sCurrentJsApiVersion)
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
                    activity.runOnUiThread {
                        showThemedToast(context, context.getString(R.string.update_js_api_version, cardSuppliedDeveloperContact), false)
                    }
                    versionSupplied.greaterThanOrEqualTo(Version.valueOf(AnkiDroidJsAPIConstants.sMinimumJsApiVersion))
                }
                else -> {
                    activity.runOnUiThread {
                        showThemedToast(context, context.getString(R.string.valid_js_api_version, cardSuppliedDeveloperContact), false)
                    }
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

    private fun getJsApiListMap(): HashMap<String, Boolean>? {
        return mJsApiListMap
    }

    @JavascriptInterface
    fun init(jsonData: String?): String {
        val data: JSONObject
        var apiStatusJson = ""
        try {
            data = JSONObject(jsonData)
            cardSuppliedApiVersion = data.optString("version", "")
            cardSuppliedDeveloperContact = data.optString("developer", "")
            if (requireApiVersion(cardSuppliedApiVersion, cardSuppliedDeveloperContact)) {
                enableJsApi()
            }
            apiStatusJson = JSONObject.fromMap(mJsApiListMap).toString()
        } catch (j: JSONException) {
            Timber.w(j)
            activity.runOnUiThread {
                showThemedToast(context, context.getString(R.string.invalid_json_data, j.localizedMessage), false)
            }
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
        return activity.currentCard!!.note().hasTag("marked")
    }

    @JavascriptInterface
    fun ankiGetCardFlag(): Int {
        return activity.currentCard!!.userFlag()
    }

    // behavior change ankiGetNextTime1...4
    @JavascriptInterface
    open fun ankiGetNextTime1(): String {
        return activity.easeButton1!!.nextTime
    }

    @JavascriptInterface
    open fun ankiGetNextTime2(): String {
        return activity.easeButton2!!.nextTime
    }

    @JavascriptInterface
    open fun ankiGetNextTime3(): String {
        return activity.easeButton3!!.nextTime
    }

    @JavascriptInterface
    open fun ankiGetNextTime4(): String {
        return activity.easeButton4!!.nextTime
    }

    @JavascriptInterface
    fun ankiGetCardReps(): Int {
        return activity.currentCard!!.reps
    }

    @JavascriptInterface
    fun ankiGetCardInterval(): Int {
        return activity.currentCard!!.ivl
    }

    /** Returns the ease as an int (percentage * 10). Default: 2500 (250%). Minimum: 1300 (130%)  */
    @JavascriptInterface
    fun ankiGetCardFactor(): Int {
        return activity.currentCard!!.factor
    }

    /** Returns the last modified time as a Unix timestamp in seconds. Example: 1477384099  */
    @JavascriptInterface
    fun ankiGetCardMod(): Long {
        return activity.currentCard!!.mod
    }

    /** Returns the ID of the card. Example: 1477380543053  */
    @JavascriptInterface
    fun ankiGetCardId(): Long {
        return activity.currentCard!!.id
    }

    /** Returns the ID of the note which generated the card. Example: 1590418157630  */
    @JavascriptInterface
    fun ankiGetCardNid(): Long {
        return activity.currentCard!!.nid
    }

    @JavascriptInterface
    @CARD_TYPE
    fun ankiGetCardType(): Int {
        return activity.currentCard!!.type
    }

    /** Returns the ID of the deck which contains the card. Example: 1595967594978  */
    @JavascriptInterface
    fun ankiGetCardDid(): Long {
        return activity.currentCard!!.did
    }

    @JavascriptInterface
    fun ankiGetCardLeft(): Int {
        return activity.currentCard!!.left
    }

    /** Returns the ID of the home deck for the card if it is filtered, or 0 if not filtered. Example: 1595967594978  */
    @JavascriptInterface
    fun ankiGetCardODid(): Long {
        return activity.currentCard!!.oDid
    }

    @JavascriptInterface
    fun ankiGetCardODue(): Long {
        return activity.currentCard!!.oDue
    }

    @JavascriptInterface
    @CARD_QUEUE
    fun ankiGetCardQueue(): Int {
        return activity.currentCard!!.queue
    }

    @JavascriptInterface
    fun ankiGetCardLapses(): Int {
        return activity.currentCard!!.lapses
    }

    @JavascriptInterface
    fun ankiGetCardDue(): Long {
        return activity.currentCard!!.due
    }

    @JavascriptInterface
    fun ankiIsInFullscreen(): Boolean {
        return activity.isFullscreen
    }

    @JavascriptInterface
    fun ankiIsTopbarShown(): Boolean {
        return activity.prefShowTopbar
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
        return Decks.basename(activity.col.decks.get(activity.currentCard!!.did).getString("name"))
    }

    @JavascriptInterface
    fun ankiBuryCard(): Boolean {
        if (!isInit(AnkiDroidJsAPIConstants.BURY_CARD, AnkiDroidJsAPIConstants.ankiJsErrorCodeBuryCard)) {
            return false
        }

        return activity.buryCard()
    }

    @JavascriptInterface
    fun ankiBuryNote(): Boolean {
        if (!isInit(AnkiDroidJsAPIConstants.BURY_NOTE, AnkiDroidJsAPIConstants.ankiJsErrorCodeBuryNote)) {
            return false
        }

        return activity.buryNote()
    }

    @JavascriptInterface
    fun ankiSuspendCard(): Boolean {
        if (!isInit(AnkiDroidJsAPIConstants.SUSPEND_CARD, AnkiDroidJsAPIConstants.ankiJsErrorCodeSuspendCard)) {
            return false
        }

        return activity.suspendCard()
    }

    @JavascriptInterface
    fun ankiSuspendNote(): Boolean {
        if (!isInit(AnkiDroidJsAPIConstants.SUSPEND_NOTE, AnkiDroidJsAPIConstants.ankiJsErrorCodeSuspendNote)) {
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
        val currentCardId: Long = activity.currentCard!!.id
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

    // Know if {{tts}} is supported - issue #10443
    // Return false for now
    @JavascriptInterface
    fun ankiTtsFieldModifierIsAvailable(): Boolean {
        return false
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
    fun ankiTtsSetLanguage(loc: String): Int {
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
        activity.webView!!.isHorizontalScrollBarEnabled = scroll
    }

    @JavascriptInterface
    fun ankiEnableVerticalScrollbar(scroll: Boolean) {
        activity.webView!!.isVerticalScrollBarEnabled = scroll
    }

    @JavascriptInterface
    fun ankiSearchCardWithCallback(query: String) {
        val task = SearchCards(query, SortOrder.UseCollectionOrdering(), 0, 0, 0)
        val listener = SearchCardListener(activity.webView!!, context)
        activity.runOnUiThread {
            TaskManager.launchCollectionTask(task, listener)
        }
    }

    class SearchCardListener(private val webView: WebView, private val context: Context) : TaskListener<List<CardBrowser.CardCache>, SearchService.SearchCardsResult>() {
        override fun onPreExecute() {
            // nothing to do
        }

        override fun onPostExecute(result: SearchService.SearchCardsResult) {
            val searchResult: MutableList<String> = ArrayList()

            if (result.result == null) {
                webView.evaluateJavascript("console.log('${context.getString(R.string.search_card_js_api_no_results)}')", null)
            }

            for (s in result.result!!) {
                val jsonObject = JSONObject()
                val fieldsData = s.card.note().fields
                val fieldsName = s.card.model().fieldsNames

                val noteId = s.card.note().id
                val cardId = s.card.id
                jsonObject.put("cardId", cardId)
                jsonObject.put("noteId", noteId)

                val jsonFieldObject = JSONObject()
                fieldsName.zip(fieldsData).forEach { pair ->
                    jsonFieldObject.put(pair.component1(), pair.component2())
                }
                jsonObject.put("fieldsData", jsonFieldObject)

                searchResult.add(jsonObject.toString())
            }

            // quote result to prevent JSON injection attack
            val jsonEncodedString = org.json.JSONObject.quote(searchResult.toString())
            webView.evaluateJavascript("ankiSearchCard($jsonEncodedString)", null)
        }
    }
}
