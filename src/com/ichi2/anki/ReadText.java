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

package com.ichi2.anki;

import java.util.Locale;
import android.speech.tts.TextToSpeech;
import android.content.Context;
import android.util.Log;

public class ReadText {
    private static TextToSpeech mTts;
    //private boolean mTtsReady = false;
    
    public static void textToSpeech(String text, Locale locale) {
   		int result = mTts.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
        } else {
          	mTts.speak(Utils.stripHTML(text), TextToSpeech.QUEUE_FLUSH, null);
        }
    }


    public static void initializeTts(Context context) {
    	mTts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
			@Override
			public void onInit(int status) {
		        if (status != TextToSpeech.SUCCESS) {
		            int result = mTts.setLanguage(Locale.US);
		            if (result == TextToSpeech.LANG_MISSING_DATA ||
		                result == TextToSpeech.LANG_NOT_SUPPORTED) {
		            } else {
		            	Log.e(AnkiDroidApp.TAG, "TTS initialized and set to US" );
		            }
		        } else {
		        	Log.e(AnkiDroidApp.TAG, "Initialization of TTS failed" );
		        }
			}
        });            	
    }


    public static void releaseTts() {
        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
        }    	
    }


    public static void stopTts() {
        if (mTts != null) {
            mTts.stop();
        }
    }
}
