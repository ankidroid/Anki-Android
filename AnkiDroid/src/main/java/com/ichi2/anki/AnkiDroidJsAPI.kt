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
import com.ichi2.anki.AnkiDroidJsAPIConstants.ankiJsErrorCodeBuryCard
import com.ichi2.anki.AnkiDroidJsAPIConstants.ankiJsErrorCodeBuryNote
import com.ichi2.anki.AnkiDroidJsAPIConstants.ankiJsErrorCodeDefault
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
import kotlinx.coroutines.withContext
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
    private var cardSuppliedDeveloperContact = ""
    private var cardSuppliedApiVersion = ""
    private var cardSuppliedData = ""
    private var isValidVersion = false

    // Text to speech
    private val mTalker = JavaScriptTTS()

    open fun convertToByteArray(boolean: Boolean): ByteArray {
        return ApiResult(isValidVersion, boolean.toString()).toString().toByteArray()
    }

    open fun convertToByteArray(int: Int): ByteArray {
        return ApiResult(isValidVersion, int.toString()).toString().toByteArray()
    }

    open fun convertToByteArray(long: Long): ByteArray {
        return ApiResult(isValidVersion, long.toString()).toString().toByteArray()
    }

    open fun convertToByteArray(string: String): ByteArray {
        return ApiResult(isValidVersion, string).toString().toByteArray()
    }

    /**
     * The method parse json data and check for api version, developer contact
     * and extract card supplied data if api version and developer contact
     * provided then enable js api otherwise disable js api.
     * @param byteArray
     * @return card supplied data, it may be empty, or specific to js api,
     * in case of tts api it contains json string of text and queueMode which parsed in speak tts api
     */
    private fun checkJsApiContract(byteArray: ByteArray) {
        try {
            val data = JSONObject(byteArray.decodeToString())
            cardSuppliedApiVersion = data.optString("version", "")
            cardSuppliedDeveloperContact = data.optString("developer", "")
            cardSuppliedData = data.optString("data", "")
            isValidVersion = requireApiVersion(cardSuppliedApiVersion, cardSuppliedDeveloperContact)
            return
        } catch (j: JSONException) {
            Timber.w(j)
            activity.runOnUiThread {
                activity.showSnackbar(
                    context.getString(
                        R.string.invalid_json_data,
                        j.localizedMessage
                    )
                )
            }
        }
        showDeveloperContact(ankiJsErrorCodeDefault)
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

    /**
     * Handle js api request,
     * some of the methods are overriden in Reviewer.kt and default values are returned.
     * @param methodName
     * @param bytes
     * @return
     */
    open suspend fun handleJsApiRequest(methodName: String, bytes: ByteArray, isReviewer: Boolean) = withContext(Dispatchers.Main) {
        // the method will call to set the card supplied data and is valid version for each api request
        checkJsApiContract(bytes)
        // if api not init or is api not called from reviewer then return default -1
        // also other action will not be modified
        if (!isValidVersion or !isReviewer) {
            return@withContext convertToByteArray(-1)
        }

        val cardDataForJsAPI = activity.getCardDataForJsApi()
        val apiParams = cardSuppliedData

        return@withContext when (methodName) {
            "init" -> convertToByteArray(isValidVersion)
            "newCardCount" -> convertToByteArray(cardDataForJsAPI.newCardCount)
            "lrnCardCount" -> convertToByteArray(cardDataForJsAPI.lrnCardCount)
            "revCardCount" -> convertToByteArray(cardDataForJsAPI.revCardCount)
            "eta" -> convertToByteArray(cardDataForJsAPI.eta)
            "nextTime1" -> convertToByteArray(cardDataForJsAPI.nextTime1)
            "nextTime2" -> convertToByteArray(cardDataForJsAPI.nextTime2)
            "nextTime3" -> convertToByteArray(cardDataForJsAPI.nextTime3)
            "nextTime4" -> convertToByteArray(cardDataForJsAPI.nextTime4)
            "toggleFlag" -> {
                if (apiParams !in flagCommands) {
                    showDeveloperContact(ankiJsErrorCodeFlagCard)
                    return@withContext convertToByteArray(false)
                }
                convertToByteArray(activity.executeCommand(flagCommands[apiParams]!!))
            }
            "markCard" -> processAction({ activity.executeCommand(ViewerCommand.MARK) }, ankiJsErrorCodeMarkCard, ::convertToByteArray)
            "buryCard" -> processAction(activity::buryCard, ankiJsErrorCodeBuryCard, ::convertToByteArray)
            "buryNote" -> processAction(activity::buryNote, ankiJsErrorCodeBuryNote, ::convertToByteArray)
            "suspendCard" -> processAction(activity::suspendCard, ankiJsErrorCodeSuspendCard, ::convertToByteArray)
            "suspendNote" -> processAction(activity::suspendNote, ankiJsErrorCodeSuspendNote, ::convertToByteArray)
            "setCardDue" -> {
                try {
                    val days = apiParams.toInt()
                    if (days < 0 || days > 9999) {
                        showDeveloperContact(ankiJsErrorCodeSetDue)
                        return@withContext convertToByteArray(false)
                    }
                    activity.launchCatchingTask { activity.rescheduleCards(listOf(currentCard.id), days) }
                    return@withContext convertToByteArray(true)
                } catch (e: NumberFormatException) {
                    showDeveloperContact(ankiJsErrorCodeSetDue)
                    return@withContext convertToByteArray(false)
                }
            }
            "resetProgress" -> {
                val cardIds = listOf(currentCard.id)
                activity.launchCatchingTask { activity.resetCards(cardIds) }
                convertToByteArray(true)
            }
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
            "ttsSetLanguage" -> convertToByteArray(mTalker.setLanguage(apiParams))
            "ttsSpeak" -> {
                val jsonObject = JSONObject(apiParams)
                val text = jsonObject.getString("text")
                val queueMode = jsonObject.getInt("queueMode")
                convertToByteArray(mTalker.speak(text, queueMode))
            }
            "ttsIsSpeaking" -> convertToByteArray(mTalker.isSpeaking)
            "ttsSetPitch" -> convertToByteArray(mTalker.setPitch(apiParams.toFloat()))
            "ttsSetSpeechRate" -> convertToByteArray(mTalker.setSpeechRate(apiParams.toFloat()))
            "ttsFieldModifierIsAvailable" -> {
                // Know if {{tts}} is supported - issue #10443
                // Return false for now
                convertToByteArray(false)
            }
            "ttsStop" -> convertToByteArray(mTalker.stop())
            "searchCard" -> {
                val intent = Intent(context, CardBrowser::class.java).apply {
                    putExtra("currentCard", currentCard.id)
                    putExtra("search_query", apiParams)
                }
                activity.startActivityWithAnimation(intent, ActivityTransitionAnimation.Direction.START)
                convertToByteArray(true)
            }
            "searchCardWithCallback" -> ankiSearchCardWithCallback(apiParams)
            "isDisplayingAnswer" -> convertToByteArray(activity.isDisplayingAnswer)
            "addTagToCard" -> {
                activity.runOnUiThread { activity.showTagsDialog() }
                convertToByteArray(true)
            }
            "isInFullscreen" -> convertToByteArray(activity.isFullscreen)
            "isTopbarShown" -> convertToByteArray(activity.prefShowTopbar)
            "isInNightMode" -> convertToByteArray(activity.isInNightMode)
            "enableHorizontalScrollbar" -> {
                activity.webView!!.isHorizontalScrollBarEnabled = apiParams.toBoolean()
                convertToByteArray(true)
            }
            "enableVerticalScrollbar" -> {
                activity.webView!!.isVerticalScrollBarEnabled = apiParams.toBoolean()
                convertToByteArray(true)
            }
            else -> {
                showDeveloperContact(ankiJsErrorCodeError)
                throw Exception("unhandled request: $methodName")
            }
        }
    }

    private fun processAction(action: () -> Boolean, errorCode: Int, conversion: (Boolean) -> ByteArray): ByteArray {
        val status = action()
        if (!status) {
            showDeveloperContact(errorCode)
        }
        return conversion(status)
    }

    private suspend fun ankiSearchCardWithCallback(query: String): ByteArray = withContext(Dispatchers.Main) {
        val cards = try {
            searchForCards(query, SortOrder.UseCollectionOrdering(), CardsOrNotes.CARDS)
        } catch (exc: Exception) {
            activity.webView!!.evaluateJavascript(
                "console.log('${context.getString(R.string.search_card_js_api_no_results)}')",
                null
            )
            showDeveloperContact(AnkiDroidJsAPIConstants.ankiJsErrorCodeSearchCard)
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
}
