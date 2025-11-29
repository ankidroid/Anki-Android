/**
 * Copyright 2025 Brayan Oliveira
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import type { Result } from "../types";
import { Service } from "./service";

/**
 * Service for running Text-to-speech (TTS).
 * Based on the TextToSpeech Android class.
 *
 * @see https://developer.android.com/reference/kotlin/android/speech/tts/TextToSpeech
 */
export class Tts extends Service {
    protected readonly base: string = "tts";

    /**
     * Speaks the text using the specified queuing strategy.
     * This method is asynchronous, i.e. the method just adds the request to the queue of TTS requests and then returns.
     *
     * @param text the string of text to be spoken.
     * @param queueMode how the new entry is added to the playback queue.
     * * 0 (QUEUE_FLUSH): all entries are dropped and replaced by the new entry.
     * * 1 (QUEUE_ADD): the new entry is added at the end of the playback queue.
     *
     * @see https://developer.android.com/reference/kotlin/android/speech/tts/TextToSpeech#speak(kotlin.CharSequence,%20kotlin.Int,%20android.os.Bundle,%20kotlin.String)
     */
    public speak(text: string, queueMode: number): Promise<Result<void>> {
        if (queueMode !== 0 && queueMode !== 1) {
            return Promise.resolve({ success: false, error: "Invalid queue mode." });
        }
        return this.request("speak", { text: text, queueMode: queueMode });
    }

    /**
     * Sets the text-to-speech language. The TTS engine will try to use the closest match to the specified language
     * as represented by the Locale, but there is no guarantee that the exact same Locale will be used
     *
     * @param locale specifying the language to speak.
     *
     * @returns Code indicating the support status for the locale
     * *  0 (LANG_AVAILABLE): language is available for the language by the locale, but not the country and variant.
     * *  1 (LANG_COUNTRY_AVAILABLE): language is available for the language and country specified by the locale, but not the variant.
     * *  2 (LANG_COUNTRY_VAR_AVAILABLE): language is available exactly as specified by the locale.
     * * -1 (LANG_MISSING_DATA): language data is missing.
     * * -2 (LANG_NOT_SUPPORTED): language is not supported.
     *
     * @see https://developer.android.com/reference/kotlin/android/speech/tts/TextToSpeech#setlanguage
     */
    public setLanguage(locale: string): Promise<Result<number>> {
        return this.request("set-language", { locale: locale });
    }

    /**
     * Sets the speech pitch for the TextToSpeech engine.
     *
     * @param pitch Speech pitch. 1.0 is the normal pitch, lower values lower the tone of the synthesized voice,
     *   greater values increase it.
     *
     * @see https://developer.android.com/reference/kotlin/android/speech/tts/TextToSpeech#setpitch
     */
    public setPitch(pitch: number): Promise<Result<void>> {
        return this.request("set-pitch", { pitch: pitch });
    }

    /**
     * Sets the speech rate.
     *
     * @param speechRate Speech rate. 1.0 is the normal speech rate, lower values slow down the speech
     *   (0.5 is half the normal speech rate), greater values accelerate it (2.0 is twice the normal speech rate).
     *
     * @see https://developer.android.com/reference/kotlin/android/speech/tts/TextToSpeech#setspeechrate
     */
    public setSpeechRate(speechRate: number): Promise<Result<void>> {
        return this.request("set-speech-rate", { speechRate });
    }

    /**
     * @returns whether the TTS engine is busy speaking.
     */
    public isSpeaking(): Promise<Result<boolean>> {
        return this.request("is-speaking");
    }

    /**
     * Interrupts the current utterance
     * and discards other utterances in the queue.
     *
     * @see https://developer.android.com/reference/kotlin/android/speech/tts/TextToSpeech#stop
     */
    public stop(): Promise<Result<void>> {
        return this.request("stop");
    }
}
