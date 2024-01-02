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
import androidx.lifecycle.lifecycleScope
import com.github.zafarkhaja.semver.Version
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.AnkiDroidJsAPIConstants.ankiJsErrorCodeBuryCard
import com.ichi2.anki.AnkiDroidJsAPIConstants.ankiJsErrorCodeBuryNote
import com.ichi2.anki.AnkiDroidJsAPIConstants.ankiJsErrorCodeError
import com.ichi2.anki.AnkiDroidJsAPIConstants.ankiJsErrorCodeFlagCard
import com.ichi2.anki.AnkiDroidJsAPIConstants.ankiJsErrorCodeMarkCard
import com.ichi2.anki.AnkiDroidJsAPIConstants.ankiJsErrorCodeSetDue
import com.ichi2.anki.AnkiDroidJsAPIConstants.ankiJsErrorCodeSuspendCard
import com.ichi2.anki.AnkiDroidJsAPIConstants.ankiJsErrorCodeSuspendNote
import com.ichi2.anki.AnkiDroidJsAPIConstants.flagCommands
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.servicelayer.rescheduleCards
import com.ichi2.anki.servicelayer.resetCards
import com.ichi2.anki.snackbar.setMaxLines
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.libanki.Card
import com.ichi2.libanki.Decks
import com.ichi2.libanki.SortOrder
import com.ichi2.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

open class AnkiDroidJsAPI(private val activity: AbstractFlashcardViewer) {
    private val currentCard: Card
        get() = activity.currentCard!!

    /**
     Javascript Interface class for calling Java function from AnkiDroid WebView
     see js-api.js for available functions
     */

    private val context: Context = activity

    // Text to speech
    private val mTalker = JavaScriptTTS()

    // Speech to Text
    private val mSpeechRecognizer = JavaScriptSTT(context)

    open fun convertToByteArray(apiContract: ApiContract, boolean: Boolean): ByteArray {
        return ApiResult(apiContract.isValid, boolean.toString()).toString().toByteArray()
    }

    open fun convertToByteArray(apiContract: ApiContract, int: Int): ByteArray {
        return ApiResult(apiContract.isValid, int.toString()).toString().toByteArray()
    }

    open fun convertToByteArray(apiContract: ApiContract, long: Long): ByteArray {
        return ApiResult(apiContract.isValid, long.toString()).toString().toByteArray()
    }

    open fun convertToByteArray(apiContract: ApiContract, string: String): ByteArray {
        return ApiResult(apiContract.isValid, string).toString().toByteArray()
    }

    /**
     * The method parse json data and return api contract object
     * @param byteArray
     * @return apiContract or null
     */
    private fun parseJsApiContract(byteArray: ByteArray): ApiContract? {
        try {
            val data = JSONObject(byteArray.decodeToString())
            val cardSuppliedApiVersion = data.optString("version", "")
            val cardSuppliedDeveloperContact = data.optString("developer", "")
            val cardSuppliedData = data.optString("data", "")
            val isValid = requireApiVersion(cardSuppliedApiVersion, cardSuppliedDeveloperContact)
            return ApiContract(isValid, cardSuppliedDeveloperContact, cardSuppliedData)
        } catch (j: JSONException) {
            Timber.w(j)
            activity.runOnUiThread {
                activity.showSnackbar(context.getString(R.string.invalid_json_data, j.localizedMessage))
            }
        }
        return null
    }

    /*
     * see 02-strings.xml
     * Show Error code when mark card or flag card unsupported
     * 1 - mark card
     * 2 - flag card
     *
     * show developer contact if js api used in card is deprecated
     */
    private fun showDeveloperContact(errorCode: Int, apiDevContact: String) {
        val errorMsg: String = context.getString(R.string.anki_js_error_code, errorCode)
        val snackbarMsg: String = context.getString(R.string.api_version_developer_contact, apiDevContact, errorMsg)

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
            if (apiDevContact.isEmpty() || apiVer.isEmpty()) {
                activity.runOnUiThread {
                    activity.showSnackbar(context.getString(R.string.invalid_json_data, ""))
                }
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
                        activity.showSnackbar(context.getString(R.string.update_js_api_version, apiDevContact))
                    }
                    versionSupplied.greaterThanOrEqualTo(Version.valueOf(AnkiDroidJsAPIConstants.sMinimumJsApiVersion))
                }
                else -> {
                    activity.runOnUiThread {
                        activity.showSnackbar(context.getString(R.string.valid_js_api_version, apiDevContact))
                    }
                    false
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "requireApiVersion::exception")
        }
        return false
    }

    /**
     * Handle js api request,
     * some of the methods are overriden in Reviewer.kt and default values are returned.
     * @param methodName
     * @param bytes
     * @param isReviewer
     * @return
     */
    open suspend fun handleJsApiRequest(methodName: String, bytes: ByteArray, isReviewer: Boolean) = withContext(Dispatchers.Main) {
        // the method will call to set the card supplied data and is valid version for each api request
        val apiContract = parseJsApiContract(bytes)!!
        // if api not init or is api not called from reviewer then return default -1
        // also other action will not be modified
        if (!apiContract.isValid or !isReviewer) {
            return@withContext convertToByteArray(apiContract, -1)
        }

        val cardDataForJsAPI = activity.getCardDataForJsApi()
        val apiParams = apiContract.cardSuppliedData

        return@withContext when (methodName) {
            "init" -> convertToByteArray(apiContract, true)
            "newCardCount" -> convertToByteArray(apiContract, cardDataForJsAPI.newCardCount)
            "lrnCardCount" -> convertToByteArray(apiContract, cardDataForJsAPI.lrnCardCount)
            "revCardCount" -> convertToByteArray(apiContract, cardDataForJsAPI.revCardCount)
            "eta" -> convertToByteArray(apiContract, cardDataForJsAPI.eta)
            "nextTime1" -> convertToByteArray(apiContract, cardDataForJsAPI.nextTime1)
            "nextTime2" -> convertToByteArray(apiContract, cardDataForJsAPI.nextTime2)
            "nextTime3" -> convertToByteArray(apiContract, cardDataForJsAPI.nextTime3)
            "nextTime4" -> convertToByteArray(apiContract, cardDataForJsAPI.nextTime4)
            "toggleFlag" -> {
                if (apiParams !in flagCommands) {
                    showDeveloperContact(ankiJsErrorCodeFlagCard, apiContract.cardSuppliedDeveloperContact)
                    return@withContext convertToByteArray(apiContract, false)
                }
                convertToByteArray(apiContract, activity.executeCommand(flagCommands[apiParams]!!))
            }
            "markCard" -> processAction({ activity.executeCommand(ViewerCommand.MARK) }, apiContract, ankiJsErrorCodeMarkCard, ::convertToByteArray)
            "buryCard" -> processAction(activity::buryCard, apiContract, ankiJsErrorCodeBuryCard, ::convertToByteArray)
            "buryNote" -> processAction(activity::buryNote, apiContract, ankiJsErrorCodeBuryNote, ::convertToByteArray)
            "suspendCard" -> processAction(activity::suspendCard, apiContract, ankiJsErrorCodeSuspendCard, ::convertToByteArray)
            "suspendNote" -> processAction(activity::suspendNote, apiContract, ankiJsErrorCodeSuspendNote, ::convertToByteArray)
            "setCardDue" -> {
                try {
                    val days = apiParams.toInt()
                    if (days < 0 || days > 9999) {
                        showDeveloperContact(ankiJsErrorCodeSetDue, apiContract.cardSuppliedDeveloperContact)
                        return@withContext convertToByteArray(apiContract, false)
                    }
                    activity.launchCatchingTask {
                        activity.rescheduleCards(listOf(currentCard.id), days)
                    }
                    return@withContext convertToByteArray(apiContract, true)
                } catch (e: NumberFormatException) {
                    showDeveloperContact(ankiJsErrorCodeSetDue, apiContract.cardSuppliedDeveloperContact)
                    return@withContext convertToByteArray(apiContract, false)
                }
            }
            "resetProgress" -> {
                val cardIds = listOf(currentCard.id)
                activity.launchCatchingTask { activity.resetCards(cardIds) }
                convertToByteArray(apiContract, true)
            }
            "cardMark" -> convertToByteArray(apiContract, currentCard.note().hasTag("marked"))
            "cardFlag" -> convertToByteArray(apiContract, currentCard.userFlag())
            "cardReps" -> convertToByteArray(apiContract, currentCard.reps)
            "cardInterval" -> convertToByteArray(apiContract, currentCard.ivl)
            "cardFactor" -> convertToByteArray(apiContract, currentCard.factor)
            "cardMod" -> convertToByteArray(apiContract, currentCard.mod)
            "cardId" -> convertToByteArray(apiContract, currentCard.id)
            "cardNid" -> convertToByteArray(apiContract, currentCard.nid)
            "cardType" -> convertToByteArray(apiContract, currentCard.type)
            "cardDid" -> convertToByteArray(apiContract, currentCard.did)
            "cardLeft" -> convertToByteArray(apiContract, currentCard.left)
            "cardODid" -> convertToByteArray(apiContract, currentCard.oDid)
            "cardODue" -> convertToByteArray(apiContract, currentCard.oDue)
            "cardQueue" -> convertToByteArray(apiContract, currentCard.queue)
            "cardLapses" -> convertToByteArray(apiContract, currentCard.lapses)
            "cardDue" -> convertToByteArray(apiContract, currentCard.due)
            "deckName" -> convertToByteArray(apiContract, Decks.basename(activity.getColUnsafe.decks.name(currentCard.did)))
            "isActiveNetworkMetered" -> convertToByteArray(apiContract, NetworkUtils.isActiveNetworkMetered())
            "ttsSetLanguage" -> convertToByteArray(apiContract, mTalker.setLanguage(apiParams))
            "ttsSpeak" -> {
                val jsonObject = JSONObject(apiParams)
                val text = jsonObject.getString("text")
                val queueMode = jsonObject.getInt("queueMode")
                convertToByteArray(apiContract, mTalker.speak(text, queueMode))
            }
            "ttsIsSpeaking" -> convertToByteArray(apiContract, mTalker.isSpeaking)
            "ttsSetPitch" -> convertToByteArray(apiContract, mTalker.setPitch(apiParams.toFloat()))
            "ttsSetSpeechRate" -> convertToByteArray(apiContract, mTalker.setSpeechRate(apiParams.toFloat()))
            "ttsFieldModifierIsAvailable" -> {
                // Know if {{tts}} is supported - issue #10443
                // Return false for now
                convertToByteArray(apiContract, false)
            }
            "ttsStop" -> convertToByteArray(apiContract, mTalker.stop())
            "searchCard" -> {
                val intent = Intent(context, CardBrowser::class.java).apply {
                    putExtra("currentCard", currentCard.id)
                    putExtra("search_query", apiParams)
                }
                activity.startActivity(intent)
                convertToByteArray(apiContract, true)
            }
            "searchCardWithCallback" -> ankiSearchCardWithCallback(apiContract)
            "isDisplayingAnswer" -> convertToByteArray(apiContract, activity.isDisplayingAnswer)
            "addTagToCard" -> {
                activity.runOnUiThread { activity.showTagsDialog() }
                convertToByteArray(apiContract, true)
            }
            "isInFullscreen" -> convertToByteArray(apiContract, activity.isFullscreen)
            "isTopbarShown" -> convertToByteArray(apiContract, activity.prefShowTopbar)
            "isInNightMode" -> convertToByteArray(apiContract, activity.isInNightMode)
            "enableHorizontalScrollbar" -> {
                activity.webView!!.isHorizontalScrollBarEnabled = apiParams.toBoolean()
                convertToByteArray(apiContract, true)
            }
            "enableVerticalScrollbar" -> {
                activity.webView!!.isVerticalScrollBarEnabled = apiParams.toBoolean()
                convertToByteArray(apiContract, true)
            }
            "showNavigationDrawer" -> {
                activity.onNavigationPressed()
                convertToByteArray(apiContract, true)
            }
            "showOptionsMenu" -> {
                activity.openOptionsMenu()
                convertToByteArray(apiContract, true)
            }
            "showToast" -> {
                val jsonObject = JSONObject(apiParams)
                val text = jsonObject.getString("text")
                val shortLength = jsonObject.optBoolean("shortLength", true)
                val msgDecode = activity.decodeUrl(text)
                UIUtils.showThemedToast(context, msgDecode, shortLength)
                convertToByteArray(apiContract, true)
            }
            "showAnswer" -> {
                activity.displayCardAnswer()
                convertToByteArray(apiContract, true)
            }
            "answerEase1" -> {
                activity.flipOrAnswerCard(AbstractFlashcardViewer.EASE_1)
                convertToByteArray(apiContract, true)
            }
            "answerEase2" -> {
                activity.flipOrAnswerCard(AbstractFlashcardViewer.EASE_2)
                convertToByteArray(apiContract, true)
            }
            "answerEase3" -> {
                activity.flipOrAnswerCard(AbstractFlashcardViewer.EASE_3)
                convertToByteArray(apiContract, true)
            }
            "answerEase4" -> {
                activity.flipOrAnswerCard(AbstractFlashcardViewer.EASE_4)
                convertToByteArray(apiContract, true)
            }
            "sttSetLanguage" -> convertToByteArray(apiContract, mSpeechRecognizer.setLanguage(apiParams))
            "sttStart" -> {
                val callback = object : JavaScriptSTT.SpeechRecognitionCallback {
                    override fun onResult(results: List<String>) {
                        activity.lifecycleScope.launch {
                            val apiResult = ApiResult(true, Json.encodeToString(ListSerializer(String.serializer()), results))
                            val jsonEncodedString = withContext(Dispatchers.Default) { JSONObject.quote(apiResult.toString()) }
                            activity.webView!!.evaluateJavascript("ankiSttResult($jsonEncodedString)", null)
                        }
                    }
                    override fun onError(errorMessage: String) {
                        activity.lifecycleScope.launch {
                            val apiResult = ApiResult(false, errorMessage)
                            val jsonEncodedString = withContext(Dispatchers.Default) { JSONObject.quote(apiResult.toString()) }
                            activity.webView!!.evaluateJavascript("ankiSttResult($jsonEncodedString)", null)
                        }
                    }
                }
                mSpeechRecognizer.setRecognitionCallback(callback)
                convertToByteArray(apiContract, mSpeechRecognizer.start())
            }
            "sttStop" -> convertToByteArray(apiContract, mSpeechRecognizer.stop())
            else -> {
                showDeveloperContact(ankiJsErrorCodeError, apiContract.cardSuppliedDeveloperContact)
                throw Exception("unhandled request: $methodName")
            }
        }
    }

    private fun processAction(
        action: () -> Boolean,
        apiContract: ApiContract,
        errorCode: Int,
        conversion: (ApiContract, Boolean) -> ByteArray
    ): ByteArray {
        val status = action()
        if (!status) {
            showDeveloperContact(errorCode, apiContract.cardSuppliedDeveloperContact)
        }
        return conversion(apiContract, status)
    }

    private suspend fun ankiSearchCardWithCallback(apiContract: ApiContract): ByteArray = withContext(Dispatchers.Main) {
        val cards = try {
            searchForCards(apiContract.cardSuppliedData, SortOrder.UseCollectionOrdering(), CardsOrNotes.CARDS)
        } catch (exc: Exception) {
            activity.webView!!.evaluateJavascript(
                "console.log('${context.getString(R.string.search_card_js_api_no_results)}')",
                null
            )
            showDeveloperContact(AnkiDroidJsAPIConstants.ankiJsErrorCodeSearchCard, apiContract.cardSuppliedDeveloperContact)
            return@withContext convertToByteArray(apiContract, false)
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
        convertToByteArray(apiContract, true)
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

    class ApiResult(private val status: Boolean, private val value: String) {
        override fun toString(): String {
            return JSONObject().apply {
                put("success", status)
                put("value", value)
            }.toString()
        }
    }

    class ApiContract(val isValid: Boolean, val cardSuppliedDeveloperContact: String, val cardSuppliedData: String)
}
