/****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.view.WindowManager.BadTokenException
import androidx.annotation.VisibleForTesting
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.UIUtils.showSnackbar
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.libanki.Sound.SoundSide
import com.ichi2.libanki.TTSTag
import com.ichi2.utils.HandlerUtils.postDelayedOnNewHandler
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.*

object ReadText {
    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @get:JvmStatic
    var textToSpeech: TextToSpeech? = null
        private set
    private val availableTtsLocales = ArrayList<Locale>()

    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    var textToSpeak: String? = null
        private set
    private var mReviewer: WeakReference<Context>? = null
    private var mDid: Long = 0
    private var mOrd = 0
    var questionAnswer: SoundSide? = null
        private set
    const val NO_TTS = "0"
    private val mTtsParams = Bundle()
    private var mCompletionListener: ReadTextListener? = null

    private fun speak(text: String?, loc: String, queueMode: Int) {
        val result = textToSpeech!!.setLanguage(LanguageUtils.localeFromStringIgnoringScriptAndExtensions(loc))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            showThemedToast(
                mReviewer!!.get(),
                mReviewer!!.get()!!.getString(R.string.no_tts_available_message) +
                    " (" + loc + ")",
                false
            )
            Timber.e("Error loading locale %s", loc)
        } else {
            if (textToSpeech!!.isSpeaking && queueMode == TextToSpeech.QUEUE_FLUSH) {
                Timber.d("tts engine appears to be busy... clearing queue")
                stopTts()
                // sTextQueue.add(new String[] { text, loc });
            }
            Timber.d("tts text '%s' to be played for locale (%s)", text, loc)
            textToSpeech!!.speak(textToSpeak, queueMode, mTtsParams, "stringId")
        }
    }

    private fun getLanguage(did: Long, ord: Int, qa: SoundSide?): String {
        return MetaDB.getLanguage(mReviewer!!.get(), did, ord, qa)
    }

    /**
     * Ask the user what language they want.
     *
     * @param text The text to be read
     * @param did  The deck id
     * @param ord  The card template ordinal
     * @param qa   The card question or card answer
     */
    fun selectTts(text: String?, did: Long, ord: Int, qa: SoundSide?) {
        // TODO: Consolidate with ReadText.readCardSide
        textToSpeak = text
        questionAnswer = qa
        mDid = did
        mOrd = ord
        val res = mReviewer!!.get()!!.resources
        val builder = MaterialDialog.Builder(mReviewer!!.get()!!)
        // Build the language list if it's empty
        if (availableTtsLocales.isEmpty()) {
            buildAvailableLanguages()
        }
        if (availableTtsLocales.isEmpty()) {
            Timber.w("ReadText.textToSpeech() no TTS languages available")
            builder.content(res.getString(R.string.no_tts_available_message))
                .iconAttr(R.attr.dialogErrorIcon)
                .positiveText(R.string.dialog_ok)
        } else {
            val dialogItems = ArrayList<CharSequence>(availableTtsLocales.size)
            val dialogIds = ArrayList<String>(availableTtsLocales.size)
            // Add option: "no tts"
            dialogItems.add(res.getString(R.string.tts_no_tts))
            dialogIds.add(NO_TTS)
            for (i in availableTtsLocales.indices) {
                dialogItems.add(availableTtsLocales[i].displayName)
                dialogIds.add(availableTtsLocales[i].isO3Language)
            }
            val items = arrayOfNulls<String>(dialogItems.size)
            dialogItems.toArray(items)
            builder.title(res.getString(R.string.select_locale_title))
                .items(*items)
                .itemsCallback { _: MaterialDialog?, _: View?, which: Int, _: CharSequence? ->
                    val locale = dialogIds[which]
                    Timber.d("ReadText.selectTts() user chose locale '%s'", locale)
                    MetaDB.storeLanguage(mReviewer!!.get(), mDid, mOrd, questionAnswer, locale)
                    if (locale != NO_TTS) {
                        speak(textToSpeak, locale, TextToSpeech.QUEUE_FLUSH)
                    } else {
                        mCompletionListener!!.onDone(qa)
                    }
                }
        }
        // Show the dialog after short delay so that user gets a chance to preview the card
        showDialogAfterDelay(builder, 500)
    }

    internal fun showDialogAfterDelay(builder: MaterialDialog.Builder, delayMillis: Int) {
        postDelayedOnNewHandler({
            try {
                builder.build().show()
            } catch (e: BadTokenException) {
                Timber.w(e, "Activity invalidated before TTS language dialog could display")
            }
        }, delayMillis.toLong())
    }

    /**
     * Read a card side using a TTS service.
     *
     * @param textsToRead      Data for the TTS to read
     * @param cardSide         Card side to be read; SoundSide.SOUNDS_QUESTION or SoundSide.SOUNDS_ANSWER.
     * @param did              Index of the deck containing the card.
     * @param ord              The card template ordinal.
     */
    fun readCardSide(textsToRead: List<TTSTag>, cardSide: SoundSide, did: Long, ord: Int) {
        var isFirstText = true
        var playedSound = false
        for (textToRead in textsToRead) {
            if (textToRead.fieldText.isEmpty()) {
                continue
            }
            playedSound = playedSound or textToSpeech(
                textToRead, did, ord, cardSide,
                if (isFirstText) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            )
            isFirstText = false
        }
        // if we didn't play a sound, call the completion listener
        if (!playedSound) {
            mCompletionListener!!.onDone(cardSide)
        }
    }

    /**
     * Read the given text using an appropriate TTS voice.
     *
     *
     * The voice is chosen as follows:
     *
     *
     * 1. If localeCode is a non-empty string representing a locale in the format returned
     * by Locale.toString(), and a voice matching the language of this locale (and ideally,
     * but not necessarily, also the country and variant of the locale) is available, then this
     * voice is used.
     * 2. Otherwise, if the database contains a saved language for the given 'did', 'ord' and 'qa'
     * arguments, and a TTS voice matching that language is available, then this voice is used
     * (unless the saved language is NO_TTS, in which case the text is not read at all).
     * 3. Otherwise, the user is asked to select a language from among those for which a voice is
     * available.
     *
     * @param queueMode TextToSpeech.QUEUE_ADD or TextToSpeech.QUEUE_FLUSH.
     * @return false if a sound was not played
     */
    private fun textToSpeech(tag: TTSTag, did: Long, ord: Int, qa: SoundSide, queueMode: Int): Boolean {
        textToSpeak = tag.fieldText
        questionAnswer = qa
        mDid = did
        mOrd = ord
        Timber.d("ReadText.textToSpeech() method started for string '%s', locale '%s'", tag.fieldText, tag.lang)
        var localeCode = tag.lang
        val originalLocaleCode = localeCode
        if (!localeCode.isEmpty()) {
            if (!isLanguageAvailable(localeCode)) {
                localeCode = ""
            }
        }
        if (localeCode.isEmpty()) {
            // get the user's existing language preference
            localeCode = getLanguage(mDid, mOrd, questionAnswer)
            Timber.d("ReadText.textToSpeech() method found language choice '%s'", localeCode)
        }
        if (localeCode == NO_TTS) {
            // user has chosen not to read the text
            return false
        }
        if (!localeCode.isEmpty() && isLanguageAvailable(localeCode)) {
            speak(textToSpeak, localeCode, queueMode)
            return true
        }

        // Otherwise ask the user what language they want to use
        if (!originalLocaleCode.isEmpty()) {
            // (after notifying them first that no TTS voice was found for the locale
            // they originally requested)
            showThemedToast(
                mReviewer!!.get(),
                mReviewer!!.get()!!.getString(R.string.no_tts_available_message) +
                    " (" + originalLocaleCode + ")",
                false
            )
        }
        selectTts(textToSpeak, mDid, mOrd, questionAnswer)
        return true
    }

    /**
     * Returns true if the TTS engine supports the language of the locale represented by localeCode
     * (which should be in the format returned by Locale.toString()), false otherwise.
     */
    private fun isLanguageAvailable(localeCode: String): Boolean {
        return textToSpeech!!.isLanguageAvailable(LanguageUtils.localeFromStringIgnoringScriptAndExtensions(localeCode)) >=
            TextToSpeech.LANG_AVAILABLE
    }

    @JvmStatic
    fun initializeTts(context: Context, listener: ReadTextListener) {
        // Store weak reference to Activity to prevent memory leak
        mReviewer = WeakReference(context)
        mCompletionListener = listener
        // Create new TTS object and setup its onInit Listener
        textToSpeech = TextToSpeech(context) { status: Int ->
            if (status == TextToSpeech.SUCCESS) {
                // build list of available languages
                buildAvailableLanguages()
                if (!availableTtsLocales.isEmpty()) {
                    // notify the reviewer that TTS has been initialized
                    Timber.d("TTS initialized and available languages found")
                    (mReviewer!!.get() as AbstractFlashcardViewer?)!!.ttsInitialized()
                } else {
                    showThemedToast(mReviewer!!.get(), mReviewer!!.get()!!.getString(R.string.no_tts_available_message), false)
                    Timber.w("TTS initialized but no available languages found")
                }
                textToSpeech!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onDone(arg0: String) {
                        listener.onDone(questionAnswer)
                    }

                    @Deprecated("")
                    override fun onError(utteranceId: String) {
                        Timber.v("Android TTS failed. Check logcat for error. Indicates a problem with Android TTS engine.")
                        val helpUrl = Uri.parse(mReviewer!!.get()!!.getString(R.string.link_faq_tts))
                        val ankiActivity = mReviewer!!.get() as AnkiActivity?
                        ankiActivity!!.mayOpenUrl(helpUrl)
                        showSnackbar(
                            ankiActivity, R.string.no_tts_available_message, false, R.string.help,
                            { openTtsHelpUrl(helpUrl) }, ankiActivity.findViewById(R.id.root_layout),
                            Snackbar.Callback()
                        )
                    }

                    override fun onStart(arg0: String) {
                        // no nothing
                    }
                })
            } else {
                showThemedToast(mReviewer!!.get(), mReviewer!!.get()!!.getString(R.string.no_tts_available_message), false)
                Timber.w("TTS not successfully initialized")
            }
        }
        // Show toast that it's getting initialized, as it can take a while before the sound plays the first time
        showThemedToast(context, context.getString(R.string.initializing_tts), false)
    }

    private fun openTtsHelpUrl(helpUrl: Uri) {
        val activity = mReviewer!!.get() as AnkiActivity?
        activity!!.openUrl(helpUrl)
    }

    fun buildAvailableLanguages() {
        availableTtsLocales.clear()
        val systemLocales = Locale.getAvailableLocales()
        availableTtsLocales.ensureCapacity(systemLocales.size)
        for (loc in systemLocales) {
            try {
                val retCode = textToSpeech!!.isLanguageAvailable(loc)
                if (retCode >= TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                    availableTtsLocales.add(loc)
                } else {
                    Timber.v("ReadText.buildAvailableLanguages() :: %s  not available (error code %d)", loc.displayName, retCode)
                }
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "Error checking if language %s available", loc.displayName)
            }
        }
    }

    /**
     * Request that TextToSpeech is stopped and shutdown after it it no longer being used
     * by the context that initialized it.
     * No-op if the current instance of TextToSpeech was initialized by another Context
     * @param context The context used during [.initializeTts]
     */
    @JvmStatic
    fun releaseTts(context: Context) {
        if (textToSpeech != null && mReviewer!!.get() === context) {
            textToSpeech!!.stop()
            textToSpeech!!.shutdown()
        }
    }

    @JvmStatic
    fun stopTts() {
        if (textToSpeech != null) {
            textToSpeech!!.stop()
        }
    }

    @JvmStatic
    fun closeForTests() {
        if (textToSpeech != null) {
            textToSpeech!!.shutdown()
        }
        textToSpeech = null
        MetaDB.close()
        System.gc()
    }

    interface ReadTextListener {
        fun onDone(playedSide: SoundSide?)
    }
}
