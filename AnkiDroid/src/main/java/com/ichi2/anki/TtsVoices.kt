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
 *
 *  This file incorporates code under the following license
 *  https://github.com/ankitects/anki/blob/9600f033f745bfae4e00dd9fa43e44d3b30c22d2/qt/aqt/tts.py
 *
 *    Copyright: Ankitects Pty Ltd and contributors
 *    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 */

package com.ichi2.anki

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.TemplateManager
import com.ichi2.libanki.TtsVoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.Locale
import kotlin.coroutines.resume

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

    /** An immutable list of voices available for TTS */
    private lateinit var availableVoices: Set<AndroidTtsVoice>

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

    suspend fun refresh() {
        launchBuildLocalesJob()
        buildLocalesJob?.join()
        loadTtsVoicesData()
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
     * Returns the list of available voices for use in TTS
     */
    suspend fun allTtsVoices(): Set<AndroidTtsVoice> {
        if (this::availableLocaleData.isInitialized) {
            return this.availableVoices
        }

        launchBuildLocalesJob()
        buildLocalesJob?.join()
        return this.availableVoices
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
            availableVoices = emptySet()
            availableLocaleData = emptyList()
            return
        }

        // Samsung TextToSpeech engine returns locales with a displayName of "GBR,DEFAULT"/"GBR,f00"
        // so normalize them before displaying them to users
        // sample of problematic data: language = "eng", region = "GBR", variant = "f00"
        try {
            // TODO: Handle multiple engines
            val ttsEngine = tts.defaultEngine
            availableVoices = tts.voices.map { it.toTtsVoice(ttsEngine) }.toSet()
            availableLocaleData = tts.availableLanguages.map { CompatHelper.compat.normalize(it) }
        } catch (e: Exception) {
            availableVoices = emptySet()
            availableLocaleData = emptyList()
        } finally {
            tts.shutdown()
        }
    }

    /**
     * Creates a usable instance of a [TextToSpeech] as a `suspend` function
     *
     * @return a usable [TextToSpeech] instance, or `null` if the [TextToSpeech.OnInitListener]
     * returns [TextToSpeech.ERROR]
     */
    suspend fun createTts(context: Context = AnkiDroidApp.instance) =
        suspendCancellableCoroutine { continuation ->
            var textToSpeech: TextToSpeech? = null
            continuation.invokeOnCancellation {
                Timber.v("TTS creation cancelled")
                textToSpeech?.stop()
                textToSpeech?.shutdown()
            }
            Timber.v("begin TTS creation")
            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    Timber.v("TTS creation success")
                    continuation.resume(textToSpeech)
                } else {
                    Timber.e("TTS creation failed. status: %d", status)
                    textToSpeech?.shutdown()
                    continuation.resume(null)
                }
            }
        }
}

/**
 * `{{tts-voices:}}` A filter which lists all available TTS Voices for the current engine
 */
class TtsVoicesFieldFilter : TemplateManager.FieldFilter() {
    // modified from libAnki: tts.py: on_tts_voices
    override fun apply(
        fieldText: String,
        fieldName: String,
        filterName: String,
        ctx: TemplateManager.TemplateRenderContext
    ): String {
        if (filterName != "tts-voices") {
            return fieldText
        }
        // This is not translated in Anki Desktop
        return "<a href=\"tts-voices:\"/>Open TTS voices settings</a>"
    }

    companion object {
        /** Enables the {{tts-voices}} filter */
        fun ensureApplied() {
            TemplateManager.fieldFilters.putIfAbsent("tts-voices", TtsVoicesFieldFilter())
        }
    }
}

/**
 * Converts a [Voice] to a [TtsVoice] for use in libAnki
 *
 * @param engine The package name of the TTS Engine
 */
fun Voice.toTtsVoice(engine: String) = AndroidTtsVoice(this, engine)

/**
 * An instance of [TtsVoice] which allows access to the underlying [Voice] object
 */
// We include the engine name in the TTS 'name' to future-proof the feature of
// allowing a user to switch between TTS providers on the same card
// a name looks like: com.google.android.tts-cmn-cn-x-ccc-local
// com.google.android.tts + cmn-cn-x-ccc-local
class AndroidTtsVoice(val voice: Voice, val engine: String) : TtsVoice(name = "$engine-${voice.name}", lang = toAnkiTwoLetterCode(voice.locale)) {
    override fun unavailable(): Boolean {
        return voice.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
    }

    /**
     * The locale of the voice normalized to a human readable language/country, missing the variant
     * Designed for [Locale.getDisplayName]
     */
    val normalizedLocale: Locale
        // on Samsung phones, the variant (f001/DEFAULT) looks awful in the UI
        // normalise: "en-GBR" is "English (GBR)". "en-GB" is "English (United Kingdom)"
        // then remove the variant: We want English (United Kingdom), not (United Kingdom,DEFAULT)
        get() = CompatHelper.compat.normalize(voice.locale).let { Locale(it.language, it.country) }

    val isNetworkConnectionRequired
        get() = voice.isNetworkConnectionRequired

    companion object {
        /**
         * Returns an Anki-compatible 'two letter' code (ISO-639-1 + ISO 3166-1 [alpha-2 preferred])
         * ```
         * Locale("spa", "MEX", "001") => "es_MX"
         * Locale("ar", "") => "ar"
         * ```
         *
         * This differs from [Locale.toLanguageTag]:
         * * [Locale.variant][Locale.getVariant] is not output
         * * A "_" is used instead of a "-" to match Anki Desktop
         */
        fun toAnkiTwoLetterCode(locale: Locale): String = CompatHelper.compat.normalize(locale).run {
            return if (country.isBlank()) language else "${language}_$country"
        }
    }
}
