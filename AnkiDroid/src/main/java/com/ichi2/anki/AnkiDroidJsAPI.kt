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
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.servicelayer.rescheduleCards
import com.ichi2.anki.servicelayer.resetCards
import com.ichi2.anki.snackbar.setMaxLines
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.libanki.Card
import com.ichi2.libanki.CardId
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
     see js-api.js for available functions
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
    open fun checkJsApiContract(byteArray: ByteArray): Pair<Boolean, String> {
        val data = JSONObject(byteArray.decodeToString())
        cardSuppliedApiVersion = data.optString("version", "")
        cardSuppliedDeveloperContact = data.optString("developer", "")
        cardSuppliedData = data.optString("data", "")
        val isValidVersion = requireApiVersion(cardSuppliedApiVersion, cardSuppliedDeveloperContact)
        if (isValidVersion) {
            enableJsApi()
        } else {
            mJsApiListMap = AnkiDroidJsAPIConstants.initApiMap()
        }
        return Pair(isValidVersion, cardSuppliedData)
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
        } else if (!mJsApiListMap[apiName]!!) {
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
    private fun showDeveloperContact(errorCode: Int) {
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

    fun init(byteArray: ByteArray): ByteArray {
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
        return convertToByteArray(apiStatusJson)
    }

    /**
     * Handle js api request,
     * some of the methods are overriden in Reviewer.kt and default values are returned.
     * @param methodName
     * @param bytes
     * @return
     */
    open suspend fun handleJsApiRequest(methodName: String, bytes: ByteArray, isReviewer: Boolean) = withContext(Dispatchers.Main) {
        val data = checkJsApiContract(bytes)
        // if api not init or is api not called from reviewer then return default -1
        // also other action will not be modified
        if (!data.first or !isReviewer) {
            return@withContext convertToByteArray(-1)
        }

        val cardDataForJsAPI = activity.getCardDataForJsApi()

        return@withContext when (methodName) {
            "init" -> init(bytes)
            "newCardCount" -> convertToByteArray(cardDataForJsAPI.newCardCount)
            "lrnCardCount" -> convertToByteArray(cardDataForJsAPI.lrnCardCount)
            "revCardCount" -> convertToByteArray(cardDataForJsAPI.revCardCount)
            "eta" -> convertToByteArray(cardDataForJsAPI.eta)
            "nextTime1" -> convertToByteArray(cardDataForJsAPI.nextTime1)
            "nextTime2" -> convertToByteArray(cardDataForJsAPI.nextTime2)
            "nextTime3" -> convertToByteArray(cardDataForJsAPI.nextTime3)
            "nextTime4" -> convertToByteArray(cardDataForJsAPI.nextTime4)
            "toggleFlag" -> ankiToggleFlag(bytes)
            "markCard" -> ankiMarkCard()
            "buryCard" -> ankiBuryCard()
            "buryNote" -> ankiBuryNote()
            "suspendCard" -> ankiSuspendCard()
            "suspendNote" -> ankiSuspendNote()
            "setCardDue" -> ankiSetCardDue(bytes)
            "resetProgress" -> ankiResetProgress()
            "cardMark" -> convertToByteArray(currentCard.note().hasTag("marked"))
            "cardFlag" -> convertToByteArray(currentCard.userFlag())
            "cardReps" -> convertToByteArray(currentCard.reps)
            "cardInterval" -> convertToByteArray(currentCard.ivl)
            "cardFactor" -> convertToByteArray(currentCard.factor)
            "cardMod" -> convertToByteArray(currentCard.mod)
            "cardId" -> convertToByteArray(currentCard.id)
            "cardNid" -> convertToByteArray(currentCard.nid)
            "cardType" -> convertToByteArray(currentCard.type)
            "cardDid" -> convertToByteArray(currentCard.did)
            "cardLeft" -> convertToByteArray(currentCard.left)
            "cardODid" -> convertToByteArray(currentCard.oDid)
            "cardODue" -> convertToByteArray(currentCard.oDue)
            "cardQueue" -> convertToByteArray(currentCard.queue)
            "cardLapses" -> convertToByteArray(currentCard.lapses)
            "cardDue" -> convertToByteArray(currentCard.due)
            "deckName" -> convertToByteArray(Decks.basename(activity.getColUnsafe.decks.name(currentCard.did)))
            "isActiveNetworkMetered" -> convertToByteArray(NetworkUtils.isActiveNetworkMetered())
            "ttsSetLanguage" -> convertToByteArray(mTalker.setLanguage(data.second))
            "ttsSpeak" -> ankiTtsSpeak(bytes)
            "ttsIsSpeaking" -> convertToByteArray(mTalker.isSpeaking)
            "ttsSetPitch" -> convertToByteArray(mTalker.setPitch(data.second.toFloat()))
            "ttsSetSpeechRate" -> convertToByteArray(mTalker.setSpeechRate(data.second.toFloat()))
            "ttsFieldModifierIsAvailable" ->
                // Know if {{tts}} is supported - issue #10443
                // Return false for now
                convertToByteArray(false)
            "ttsStop" -> convertToByteArray(mTalker.stop())
            "searchCard" -> ankiSearchCard(bytes)
            "searchCardWithCallback" -> ankiSearchCardWithCallback(bytes)
            "isDisplayingAnswer" -> convertToByteArray(activity.isDisplayingAnswer)
            "addTagToCard" -> {
                activity.runOnUiThread { activity.showTagsDialog() }
                convertToByteArray(true)
            }
            "isInFullscreen" -> convertToByteArray(activity.isFullscreen)
            "isTopbarShown" -> convertToByteArray(activity.prefShowTopbar)
            "isInNightMode" -> convertToByteArray(activity.isInNightMode)
            "enableHorizontalScrollbar" -> {
                activity.webView!!.isHorizontalScrollBarEnabled = data.second.toBoolean()
                convertToByteArray(true)
            }
            "enableVerticalScrollbar" -> {
                activity.webView!!.isVerticalScrollBarEnabled = data.second.toBoolean()
                convertToByteArray(true)
            }
            else -> {
                throw Exception("unhandled request: $methodName")
            }
        }
    }

    private suspend fun ankiBuryCard(): ByteArray = withContext(Dispatchers.Main) {
        if (!mJsApiListMap[AnkiDroidJsAPIConstants.BURY_CARD]!!) {
            showDeveloperContact(AnkiDroidJsAPIConstants.ankiJsErrorCodeBuryCard)
            return@withContext convertToByteArray(false)
        }

        convertToByteArray(activity.buryCard())
    }

    private suspend fun ankiBuryNote(): ByteArray = withContext(Dispatchers.Main) {
        if (!mJsApiListMap[AnkiDroidJsAPIConstants.BURY_NOTE]!!) {
            showDeveloperContact(AnkiDroidJsAPIConstants.ankiJsErrorCodeBuryNote)
            return@withContext convertToByteArray(false)
        }

        convertToByteArray(activity.buryNote())
    }

    private suspend fun ankiSuspendCard(): ByteArray = withContext(Dispatchers.Main) {
        if (!mJsApiListMap[AnkiDroidJsAPIConstants.SUSPEND_CARD]!!) {
            showDeveloperContact(AnkiDroidJsAPIConstants.ankiJsErrorCodeSuspendCard)
            return@withContext convertToByteArray(false)
        }

        convertToByteArray(activity.suspendCard())
    }

    private suspend fun ankiSuspendNote(): ByteArray = withContext(Dispatchers.Main) {
        if (!mJsApiListMap[AnkiDroidJsAPIConstants.SUSPEND_NOTE]!!) {
            showDeveloperContact(AnkiDroidJsAPIConstants.ankiJsErrorCodeSuspendNote)
            return@withContext convertToByteArray(false)
        }

        convertToByteArray(activity.suspendNote())
    }

    private suspend fun ankiSearchCard(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        val data = checkJsApiContract(byteArray)
        if (!data.first) {
            return@withContext convertToByteArray(false)
        }
        val intent = Intent(context, CardBrowser::class.java)
        val currentCardId: CardId = currentCard.id
        intent.putExtra("currentCard", currentCardId)
        intent.putExtra("search_query", data.second)
        activity.startActivityWithAnimation(intent, ActivityTransitionAnimation.Direction.START)
        convertToByteArray(true)
    }

    private suspend fun ankiTtsSpeak(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        val data = checkJsApiContract(byteArray)
        if (!data.first) {
            return@withContext convertToByteArray(-1)
        }
        val jsonObject = JSONObject(data.second)
        val text = jsonObject.getString("text")
        val queueMode = jsonObject.getInt("queueMode")
        convertToByteArray(mTalker.speak(text, queueMode))
    }

    private suspend fun ankiSearchCardWithCallback(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        val data = checkJsApiContract(byteArray)
        if (!data.first) {
            return@withContext convertToByteArray(false)
        }
        val cards = try {
            searchForCards(data.second, SortOrder.UseCollectionOrdering(), CardsOrNotes.CARDS)
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

    private suspend fun ankiSetCardDue(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        val data = checkJsApiContract(byteArray)
        val daysInt = data.second.toInt()
        if (!mJsApiListMap[AnkiDroidJsAPIConstants.SET_CARD_DUE]!!) {
            showDeveloperContact(AnkiDroidJsAPIConstants.ankiJsErrorCodeDefault)
            return@withContext convertToByteArray(false)
        }

        if (daysInt < 0 || daysInt > 9999) {
            showDeveloperContact(AnkiDroidJsAPIConstants.ankiJsErrorCodeSetDue)
            convertToByteArray(false)
        }

        val cardIds = listOf(currentCard.id)
        activity.launchCatchingTask {
            activity.rescheduleCards(cardIds, daysInt)
        }
        convertToByteArray(true)
    }

    private suspend fun ankiResetProgress(): ByteArray = withContext(Dispatchers.Main) {
        if (!mJsApiListMap[AnkiDroidJsAPIConstants.RESET_PROGRESS]!!) {
            showDeveloperContact(AnkiDroidJsAPIConstants.ankiJsErrorCodeDefault)
            return@withContext convertToByteArray(false)
        }
        val cardIds = listOf(currentCard.id)
        activity.launchCatchingTask {
            activity.resetCards(cardIds)
        }
        convertToByteArray(true)
    }

    private suspend fun ankiMarkCard(): ByteArray = withContext(Dispatchers.Main) {
        if (!mJsApiListMap[AnkiDroidJsAPIConstants.MARK_CARD]!!) {
            showDeveloperContact(AnkiDroidJsAPIConstants.ankiJsErrorCodeDefault)
            return@withContext convertToByteArray(false)
        }

        activity.executeCommand(ViewerCommand.MARK)
        convertToByteArray(true)
    }

    private suspend fun ankiToggleFlag(byteArray: ByteArray): ByteArray = withContext(Dispatchers.Main) {
        val flag = checkJsApiContract(byteArray).second
        // flag card (blue, green, orange, red) using javascript from AnkiDroid webview
        if (!mJsApiListMap[AnkiDroidJsAPIConstants.TOGGLE_FLAG]!!) {
            showDeveloperContact(AnkiDroidJsAPIConstants.ankiJsErrorCodeDefault)
            return@withContext convertToByteArray(false)
        }

        when (flag) {
            "none" -> {
                activity.executeCommand(ViewerCommand.UNSET_FLAG)
            }
            "red" -> {
                activity.executeCommand(ViewerCommand.TOGGLE_FLAG_RED)
            }
            "orange" -> {
                activity.executeCommand(ViewerCommand.TOGGLE_FLAG_ORANGE)
            }
            "green" -> {
                activity.executeCommand(ViewerCommand.TOGGLE_FLAG_GREEN)
            }
            "blue" -> {
                activity.executeCommand(ViewerCommand.TOGGLE_FLAG_BLUE)
            }
            "pink" -> {
                activity.executeCommand(ViewerCommand.TOGGLE_FLAG_PINK)
            }
            "turquoise" -> {
                activity.executeCommand(ViewerCommand.TOGGLE_FLAG_TURQUOISE)
            }
            "purple" -> {
                activity.executeCommand(ViewerCommand.TOGGLE_FLAG_PURPLE)
            }
            else -> {
                Timber.d("No such Flag found.")
                convertToByteArray(false)
            }
        }
        convertToByteArray(true)
    }

    open class CardDataForJsApi {
        var newCardCount = ""
        var lrnCardCount = ""
        var revCardCount = ""
        var eta = -1
        var nextTime1 = ""
        var nextTime2 = ""
        var nextTime3 = ""
        var nextTime4 = ""
    }
}
