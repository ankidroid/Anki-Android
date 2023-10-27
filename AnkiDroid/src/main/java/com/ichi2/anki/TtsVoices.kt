/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import android.speech.tts.TextToSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Voices which an be used in TTS (Text to Speech)
 *
 * This is a singleton: it requires the TTS Engine to build, which can take multiple
 * seconds to initialise. This moves initialisation to a background thread on app startup
 *
 * In addition, the list of available TTS Voices shouldn't change during execution
 */
object TtsVoices {

    // A new instance of this list is not required if the app language changes: .displayName returns
    // the new values
    /** An immutable list of locales available for TTS */
    private lateinit var availableLocaleData: List<Locale>

    /** A job which populates [availableLocaleData] */
    private var buildLocalesJob: Job? = null

    /**
     * Returns the list of available locales for use in TTS
     *
     * This is a blocking function in the worst case scenario, but under all normal circumstances
     * this should return instantly
     *
     * @return The list of available languages, or an empty list if an error occurred
     */
    @Deprecated("blocking function", replaceWith = ReplaceWith("availableLocales"))
    fun availableLocalesBlocking(): List<Locale> {
        if (this::availableLocaleData.isInitialized) {
            return this.availableLocaleData
        }
        Timber.w("availableLocales was unexpectedly a blocking function")

        launchBuildLocalesJob()
        runBlocking {
            buildLocalesJob?.join()
        }

        return this.availableLocaleData
    }

    /**
     * Returns the list of available locales for use in TTS
     *
     * Under all normal circumstances this should return instantly
     *
     * @return The list of available languages, or an empty list if an error occurred
     */
    suspend fun availableLocales(): List<Locale> {
        if (this::availableLocaleData.isInitialized) {
            return this.availableLocaleData
        }

        launchBuildLocalesJob()
        buildLocalesJob?.join()

        return this.availableLocaleData
    }

    /**
     * Launches a [Job] to populate the list of available locales for use in TTS
     *
     * This is run in the background, without blocking the main thread
     *
     * [availableLocales] awaits the result of this function
     */
    fun launchBuildLocalesJob() {
        if (this::availableLocaleData.isInitialized || buildLocalesJob != null) {
            Timber.d("job already started")
            return
        }

        Timber.d("launching job")
        // This is intended to be a global singleton outside the lifecycle of a specific activity
        // Most of the time of execution is waiting for the TTS Engine to initialize
        buildLocalesJob = AnkiDroidApp.applicationScope.launch(Dispatchers.IO) {
            Timber.d("executing job")
            loadTtsVoicesData()
            buildLocalesJob = null
            Timber.d("%d TTS Voices available", availableLocaleData.size)
        }
    }

    /**
     * Populates [availableLocaleData] with the list of available TTS voices
     */
    private suspend fun loadTtsVoicesData() {
        val tts = createTts()
        if (tts == null) {
            Timber.e("Unable to build list of TTS Voices")
            availableLocaleData = emptyList()
            return
        }

        // Samsung TextToSpeech engine returns locales with a displayName of "GBR,DEFAULT"/"GBR,f00"
        // so use getAvailableLocales and check if they're available
        // sample of problematic data: language = "eng", region = "GBR", variant = "f00"
        val availableTtsLocales = mutableListOf<Locale>()
        try {
            val systemLocales = Locale.getAvailableLocales()
            for (loc in systemLocales) {
                try {
                    val retCode = tts.isLanguageAvailable(loc)
                    if (retCode >= TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                        availableTtsLocales.add(loc)
                    } else {
                        Timber.v(
                            "%s not available (error code %d)",
                            loc.displayName,
                            retCode
                        )
                    }
                } catch (e: IllegalArgumentException) {
                    Timber.w(e, "Error checking if language %s available", loc.displayName)
                }
            }
        } finally {
            availableLocaleData = availableTtsLocales
            tts.shutdown()
        }
    }

    /**
     * Creates a usable instance of a [TextToSpeech] as a `suspend` function
     *
     * @return a usable [TextToSpeech] instance, or `null` if the [TextToSpeech.OnInitListener]
     * returns [TextToSpeech.ERROR]
     */
    private suspend fun createTts() =
        suspendCoroutine { continuation ->
            var textToSpeech: TextToSpeech? = null
            textToSpeech = TextToSpeech(AnkiDroidApp.instance) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    continuation.resume(textToSpeech)
                } else {
                    Timber.e("TTS Creation failed. status: %d", status)
                    textToSpeech?.shutdown()
                    continuation.resume(null)
                }
            }
        }
}
