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
import android.net.Uri
import com.github.zafarkhaja.semver.Version
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.snackbar.setMaxLines
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.libanki.Card
import com.ichi2.libanki.CardId
import com.ichi2.libanki.Consts.CARD_QUEUE
import com.ichi2.libanki.Consts.CARD_TYPE
import com.ichi2.libanki.Decks
import com.ichi2.libanki.SortOrder
import com.ichi2.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

@Suppress("unused")
open class AnkiDroidJsAPI(private val activity: AbstractFlashcardViewer) {
    private val currentCard: Card
        get() = activity.currentCard!!

    /**
     Javascript Interface class for calling Java function from AnkiDroid WebView
     see card.js for available functions
     */

    private val context: Context = activity
    private var cardSuppliedDeveloperContact = ""
    private var cardSuppliedApiVersion = ""
    private var cardSuppliedData = ""

    // JS api list enable/disable status
    private var mJsApiListMap = AnkiDroidJsAPIConstants.initApiMap()

    // Text to speech
    private val mTalker = JavaScriptTTS()

    open fun convertToByteArray(boolean: Boolean): ByteArray {
        return boolean.toString().toByteArray()
    }

    open fun convertToByteArray(int: Int): ByteArray {
        return int.toString().toByteArray()
    }

    open fun convertToByteArray(long: Long): ByteArray {
        return long.toString().toByteArray()
    }

    open fun convertToByteArray(string: String): ByteArray {
        return string.toByteArray()
    }

    // init or reset api list
    fun init() {
        cardSuppliedApiVersion = ""
        cardSuppliedDeveloperContact = ""
        cardSuppliedData = ""
        mJsApiListMap = AnkiDroidJsAPIConstants.initApiMap()
    }

    /**
     * The method parse json data and check for api version, developer contact
     * and extract card supplied data if api version and developer contact
     * provided then enable js api otherwise disable js api.
     * @param byteArray
     * @return card supplied data, it may be empty, or specific to js api,
     * in case of tts api it contains json string of text and queueMode which parsed in speak tts api
     */
    open fun checkJsApiContract(byteArray: ByteArray): String {
        val data = JSONObject(byteArray.decodeToString())
        cardSuppliedApiVersion = data.optString("version", "")
        cardSuppliedDeveloperContact = data.optString("developer", "")
        cardSuppliedData = data.optString("data", "")
        if (requireApiVersion(cardSuppliedApiVersion, cardSuppliedDeveloperContact)) {
            enableJsApi()
        } else {
            mJsApiListMap = AnkiDroidJsAPIConstants.initApiMap()
        }
        return cardSuppliedData
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
    private fun isInit(apiName: String, apiErrorCode: Int): Boolean {
        if (isAnkiApiNull(apiName)) {
            showDeveloperContact(AnkiDroidJsAPIConstants.ankiJsErrorCodeDefault)
            return false
        } else if (!getJsApiListMap()[apiName]!!) {
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
        val snackbarMsg: String = context.getString(R.string.api_version_developer_contact, cardSuppliedDeveloperContact, errorMsg)

        activity.showSnackbar(snackbarMsg, Snackbar.LENGTH_INDEFINITE) {
            setMaxLines(3)
            setAction(R.string.reviewer_invalid_api_version_visit_documentation) {
                activity.openUrl(Uri.parse("https://github.com/ankidroid/Anki-Android/wiki"))
            }
        }
    }

    /**
     * Supplied api version must be equal to current api version to call mark card, toggle flag functions etc.
     */
    private fun requireApiVersion(apiVer: String, apiDevContact: String): Boolean {
        try {
            if (apiDevContact.isEmpty()) {
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
                        activity.showSnackbar(context.getString(R.string.update_js_api_version, cardSuppliedDeveloperContact))
                    }
                    versionSupplied.greaterThanOrEqualTo(Version.valueOf(AnkiDroidJsAPIConstants.sMinimumJsApiVersion))
                }
                else -> {
                    activity.runOnUiThread {
                        activity.showSnackbar(context.getString(R.string.valid_js_api_version, cardSuppliedDeveloperContact))
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

    protected fun getJsApiListMap(): HashMap<String, Boolean> {
        return mJsApiListMap
    }

    suspend fun init(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        var apiStatusJson = ""
        try {
            checkJsApiContract(byteArray)
            apiStatusJson = JSONObject(mJsApiListMap as Map<String, Boolean>).toString()
        } catch (j: JSONException) {
            Timber.w(j)
            activity.runOnUiThread {
                activity.showSnackbar(context.getString(R.string.invalid_json_data, j.localizedMessage))
            }
        }
        convertToByteArray(apiStatusJson)
    }

    // This method and the one belows return "default" values when there is no count nor ETA.
    // Javascript may expect ETA and Counts to be set, this ensure it does not bug too much by providing a value of correct type
    // but with a clearly incorrect value.
    // It's overridden in the Reviewer, where those values are actually defined.
    open suspend fun ankiGetNewCardCount(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(-1)
    }

    open suspend fun ankiGetLrnCardCount(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(-1)
    }

    open suspend fun ankiGetRevCardCount(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(-1)
    }

    open suspend fun ankiGetETA(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(-1)
    }

    suspend fun ankiGetCardMark(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(currentCard.note().hasTag("marked"))
    }

    suspend fun ankiGetCardFlag(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(currentCard.userFlag())
    }

    // behavior change ankiGetNextTime1...4
    open suspend fun ankiGetNextTime1(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(activity.easeButton1!!.nextTime)
    }

    open suspend fun ankiGetNextTime2(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(activity.easeButton2!!.nextTime)
    }

    open suspend fun ankiGetNextTime3(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(activity.easeButton3!!.nextTime)
    }

    open suspend fun ankiGetNextTime4(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(activity.easeButton4!!.nextTime)
    }

    suspend fun ankiGetCardReps(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(currentCard.reps)
    }

    suspend fun ankiGetCardInterval(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(currentCard.ivl)
    }

    /** Returns the ease as an int (percentage * 10). Default: 2500 (250%). Minimum: 1300 (130%)  */
    suspend fun ankiGetCardFactor(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(currentCard.factor)
    }

    /** Returns the last modified time as a Unix timestamp in seconds. Example: 1477384099  */
    suspend fun ankiGetCardMod(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(currentCard.mod)
    }

    /** Returns the ID of the card. Example: 1477380543053  */
    suspend fun ankiGetCardId(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(currentCard.id)
    }

    /** Returns the ID of the note which generated the card. Example: 1590418157630  */
    suspend fun ankiGetCardNid(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(currentCard.nid)
    }

    @CARD_TYPE
    suspend fun ankiGetCardType(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(currentCard.type)
    }

    /** Returns the ID of the deck which contains the card. Example: 1595967594978  */
    suspend fun ankiGetCardDid(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(currentCard.did)
    }

    suspend fun ankiGetCardLeft(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(currentCard.left)
    }

    /** Returns the ID of the home deck for the card if it is filtered, or 0 if not filtered. Example: 1595967594978  */
    suspend fun ankiGetCardODid(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(currentCard.oDid)
    }

    suspend fun ankiGetCardODue(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(currentCard.oDue)
    }

    @CARD_QUEUE
    suspend fun ankiGetCardQueue(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(currentCard.queue)
    }

    suspend fun ankiGetCardLapses(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(currentCard.lapses)
    }

    suspend fun ankiGetCardDue(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(currentCard.due)
    }

    suspend fun ankiIsInFullscreen(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(activity.isFullscreen)
    }

    suspend fun ankiIsTopbarShown(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(activity.prefShowTopbar)
    }

    suspend fun ankiIsInNightMode(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(activity.isInNightMode)
    }

    suspend fun ankiIsDisplayingAnswer(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(activity.isDisplayingAnswer)
    }

    suspend fun ankiGetDeckName(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(Decks.basename(activity.getColUnsafe.decks.name(currentCard.did)))
    }

    suspend fun ankiBuryCard(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        checkJsApiContract(byteArray)
        if (!isInit(AnkiDroidJsAPIConstants.BURY_CARD, AnkiDroidJsAPIConstants.ankiJsErrorCodeBuryCard)) {
            return@withContext convertToByteArray(false)
        }

        convertToByteArray(activity.buryCard())
    }

    suspend fun ankiBuryNote(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        checkJsApiContract(byteArray)
        if (!isInit(AnkiDroidJsAPIConstants.BURY_NOTE, AnkiDroidJsAPIConstants.ankiJsErrorCodeBuryNote)) {
            return@withContext convertToByteArray(false)
        }

        convertToByteArray(activity.buryNote())
    }

    suspend fun ankiSuspendCard(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        checkJsApiContract(byteArray)
        if (!isInit(AnkiDroidJsAPIConstants.SUSPEND_CARD, AnkiDroidJsAPIConstants.ankiJsErrorCodeSuspendCard)) {
            return@withContext convertToByteArray(false)
        }

        convertToByteArray(activity.suspendCard())
    }

    suspend fun ankiSuspendNote(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        checkJsApiContract(byteArray)
        if (!isInit(AnkiDroidJsAPIConstants.SUSPEND_NOTE, AnkiDroidJsAPIConstants.ankiJsErrorCodeSuspendNote)) {
            return@withContext convertToByteArray(false)
        }

        convertToByteArray(activity.suspendNote())
    }

    suspend fun ankiAddTagToCard(): ByteArray = withContext(Dispatchers.Main) {
        activity.runOnUiThread { activity.showTagsDialog() }
        convertToByteArray(true)
    }

    suspend fun ankiSearchCard(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        val query = checkJsApiContract(byteArray)
        val intent = Intent(context, CardBrowser::class.java)
        val currentCardId: CardId = currentCard.id
        intent.putExtra("currentCard", currentCardId)
        intent.putExtra("search_query", query)
        activity.startActivityWithAnimation(intent, ActivityTransitionAnimation.Direction.START)
        convertToByteArray(true)
    }

    suspend fun ankiIsActiveNetworkMetered(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(NetworkUtils.isActiveNetworkMetered())
    }

    // Know if {{tts}} is supported - issue #10443
    // Return false for now
    suspend fun ankiTtsFieldModifierIsAvailable(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(false)
    }

    suspend fun ankiTtsSpeak(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        val data = checkJsApiContract(byteArray)
        val jsonObject = JSONObject(data)
        val text = jsonObject.getString("text")
        val queueMode = jsonObject.getInt("queueMode")
        convertToByteArray(mTalker.speak(text, queueMode))
    }

    suspend fun ankiTtsSetLanguage(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        val loc = checkJsApiContract(byteArray)
        convertToByteArray(mTalker.setLanguage(loc))
    }

    suspend fun ankiTtsSetPitch(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        val pitch = checkJsApiContract(byteArray)
        convertToByteArray(mTalker.setPitch(pitch.toFloat()))
    }

    suspend fun ankiTtsSetSpeechRate(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        val speechRate = checkJsApiContract(byteArray)
        convertToByteArray(mTalker.setSpeechRate(speechRate.toFloat()))
    }

    suspend fun ankiTtsIsSpeaking(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(mTalker.isSpeaking)
    }

    suspend fun ankiTtsStop(): ByteArray = withContext(Dispatchers.Main) {
        convertToByteArray(mTalker.stop())
    }

    suspend fun ankiEnableHorizontalScrollbar(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        val scroll = checkJsApiContract(byteArray)
        activity.webView!!.isHorizontalScrollBarEnabled = scroll.toBoolean()
        convertToByteArray(true)
    }

    suspend fun ankiEnableVerticalScrollbar(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        val scroll = checkJsApiContract(byteArray)
        activity.webView!!.isVerticalScrollBarEnabled = scroll.toBoolean()
        convertToByteArray(true)
    }

    suspend fun ankiSearchCardWithCallback(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        val query = checkJsApiContract(byteArray)
        val cards = try {
            searchForCards(query, SortOrder.UseCollectionOrdering(), CardsOrNotes.CARDS)
        } catch (exc: Exception) {
            activity.webView!!.evaluateJavascript(
                "console.log('${context.getString(R.string.search_card_js_api_no_results)}')",
                null
            )
            return@withContext convertToByteArray(false)
        }
        val searchResult: MutableList<String> = ArrayList()
        for (s in cards) {
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
        val jsonEncodedString = JSONObject.quote(searchResult.toString())
        activity.runOnUiThread {
            activity.webView!!.evaluateJavascript("ankiSearchCard($jsonEncodedString)", null)
        }
        convertToByteArray(true)
    }

    open suspend fun ankiSetCardDue(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        // the function is overridden in Reviewer.kt
        // it may be called in previewer so just return true value here
        convertToByteArray(true)
    }

    open suspend fun ankiResetProgress(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        // the function is overridden in Reviewer.kt
        // it may be called in previewer so just return true value here
        convertToByteArray(true)
    }

    open suspend fun ankiMarkCard(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        // the function is overridden in Reviewer.kt
        // it may be called in previewer so just return true value here
        convertToByteArray(true)
    }

    open suspend fun ankiToggleFlag(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        // the function is overridden in Reviewer.kt
        // it may be called in previewer so just return true value here
        convertToByteArray(true)
    }
}
