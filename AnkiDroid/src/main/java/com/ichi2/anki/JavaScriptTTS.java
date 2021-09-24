/****************************************************************************************
 * Copyright (c) 2021 mikunimaru <com.mikuni0@gmail.com>                          *
 *
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
 * this program. If not, see <http://www.gnu.org/licenses/>.                            *
 ****************************************************************************************/

package com.ichi2.anki;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

/**
 * Since it is assumed that only advanced users will use the JavaScript api,
 * here, Android's TextToSpeech is converted for JavaScript almost as it is, giving priority to free behavior.
 * https://developer.android.com/reference/android/speech/tts/TextToSpeech
 */
public class JavaScriptTTS implements TextToSpeech.OnInitListener {

    private static final int TTS_SUCCESS = TextToSpeech.SUCCESS;
    private static final int TTS_ERROR = TextToSpeech.ERROR;
    private static final int TTS_QUEUE_ADD = TextToSpeech.QUEUE_ADD;
    private static final int TTS_QUEUE_FLUSH = TextToSpeech.QUEUE_FLUSH;
    private static final int TTS_LANG_AVAILABLE = TextToSpeech.LANG_AVAILABLE;
    private static final int TTS_LANG_COUNTRY_AVAILABLE = TextToSpeech.LANG_COUNTRY_AVAILABLE;
    private static final int TTS_LANG_COUNTRY_VAR_AVAILABLE = TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE;
    private static final int TTS_LANG_MISSING_DATA = TextToSpeech.LANG_MISSING_DATA;
    private static final int TTS_LANG_NOT_SUPPORTED = TextToSpeech.LANG_NOT_SUPPORTED;

    @IntDef({TTS_SUCCESS, TTS_ERROR})
    public @interface ErrorOrSuccess {}

    @IntDef({TTS_QUEUE_ADD, TTS_QUEUE_FLUSH})
    public @interface QueueMode {}

    @IntDef({TTS_LANG_AVAILABLE, TTS_LANG_COUNTRY_AVAILABLE, TTS_LANG_COUNTRY_VAR_AVAILABLE, TTS_LANG_MISSING_DATA, TTS_LANG_NOT_SUPPORTED})
    public @interface TTSLangResult {}

    @NonNull
    private static TextToSpeech mTts;
    private static boolean mTtsOk;
    private static final Bundle mTtsParams = new Bundle();

    JavaScriptTTS() {
        Context context = AnkiDroidApp.getInstance().getApplicationContext();
        mTts = new TextToSpeech(context, this);
    }

    /** OnInitListener method to receive the TTS engine status */
    @Override
    public void onInit(@ErrorOrSuccess int status) {
        mTtsOk = status == TextToSpeech.SUCCESS;
    }

    /**
     * A method to speak something
     * @param text Content to speak
     * @param queueMode 1 for QUEUE_ADD and 0 for QUEUE_FLUSH.
     * @return ERROR(-1) SUCCESS(0)
     */
    @ErrorOrSuccess
    public int speak(String text, @QueueMode int queueMode) {
        return mTts.speak(text, queueMode, mTtsParams, "stringId");
    }

    /**
     * If only a string is given, set QUEUE_FLUSH to the default behavior.
     * @param text Content to speak
     * @return ERROR(-1) SUCCESS(0)
     */
    @ErrorOrSuccess
    public int speak(String text) {
        return mTts.speak(text, TextToSpeech.QUEUE_FLUSH, mTtsParams, "stringId");
    }

    /**
     * Sets the text-to-speech language.
     * The TTS engine will try to use the closest match to the specified language as represented by the Locale, but there is no guarantee that the exact same Locale will be used.
     * @param loc Specifying the language to speak
     * @return  0 Denotes the language is available for the language by the locale, but not the country and variant.
     *     <li> 1 Denotes the language is available for the language and country specified by the locale, but not the variant.
     *     <li> 2 Denotes the language is available exactly as specified by the locale.
     *     <li> -1 Denotes the language data is missing.
     *     <li> -2 Denotes the language is not supported.
     */
    @TTSLangResult
    public int setLanguage(String loc) {
        // The Int values will be returned
        // Code indicating the support status for the locale. See LANG_AVAILABLE, LANG_COUNTRY_AVAILABLE, LANG_COUNTRY_VAR_AVAILABLE, LANG_MISSING_DATA and LANG_NOT_SUPPORTED.
        return mTts.setLanguage(LanguageUtils.localeFromStringIgnoringScriptAndExtensions(loc));
    }


    /**
     * Sets the speech pitch for the TextToSpeech engine. This has no effect on any pre-recorded speech.
     * @param pitch float: Speech pitch. 1.0 is the normal pitch, lower values lower the tone of the synthesized voice, greater values increase it.
     * @return ERROR(-1) SUCCESS(0)
     */
    @ErrorOrSuccess
    public int setPitch(float pitch) {
        // The following Int values will be returned
        // ERROR(-1) SUCCESS(0)
        return mTts.setPitch(pitch);
    }

    /**
     *
     * @param speechRate Sets the speech rate. 1.0 is the normal speech rate. This has no effect on any pre-recorded speech.
     * @return ERROR(-1) SUCCESS(0)
     */
    @ErrorOrSuccess
    public int setSpeechRate(float speechRate) {
        // The following Int values will be returned
        // ERROR(-1) SUCCESS(0)
        return mTts.setSpeechRate(speechRate);
    }

    /** 
     * Checks whether the TTS engine is busy speaking. 
     * Note that a speech item is considered complete once it's audio data has 
     * been sent to the audio mixer, or written to a file.
     *  
     */
    public boolean isSpeaking() {
        return mTts.isSpeaking();
    }

    /**
     * Interrupts the current utterance (whether played or rendered to file) and discards other utterances in the queue.
     * @return ERROR(-1) SUCCESS(0)
     */
    @ErrorOrSuccess
    public int stop() {
        return mTts.stop();
    }

}
