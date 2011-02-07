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

import java.util.ArrayList;
import java.util.Locale;
import android.speech.tts.TextToSpeech;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;

public class ReadText {
    private static TextToSpeech mTts;
    private static ArrayList<String[]> availableTtsLocales = new ArrayList<String[]>();
    private static String mTextToSpeak;
    private static Context mReviewer;
    //private boolean mTtsReady = false;
    
    public static void textToSpeech(String text, String loc) {
    	mTextToSpeak = text;
    	if (loc == null) {
    		selectLocale();
   		} else {
   	    	speak(loc);   			
   		}
    }
    
    public static void speak(String loc) {
    	int result = mTts.setLanguage(new Locale(loc));
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
        } else {
          	mTts.speak(mTextToSpeak, TextToSpeech.QUEUE_FLUSH, null);
        }    	
    }


    public static void selectLocale() {
    	ArrayList<CharSequence> dialogItems = new ArrayList<CharSequence>();
       final ArrayList<String> dialogIds = new ArrayList<String>();

        if (availableTtsLocales.isEmpty()) {
	    	Locale[] systemLocales = Locale.getAvailableLocales();
			for (Locale loc : systemLocales) {
				if (mTts.isLanguageAvailable(loc) == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
					availableTtsLocales.add(new String[]{loc.getISO3Language(), loc.getDisplayName()});
				}
			}			
		}

        AlertDialog.Builder builder = new AlertDialog.Builder(mReviewer);
        builder.setTitle(R.string.select_locale_title);

        for (int i = 0; i < availableTtsLocales.size(); i++) {
            dialogItems.add(availableTtsLocales.get(i)[1]);
            dialogIds.add(availableTtsLocales.get(i)[0]);
        }
        CharSequence[] items = new CharSequence[dialogItems.size()];
        dialogItems.toArray(items);

        builder.setItems(items, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				speak(dialogIds.get(which));
			}
        });
        AlertDialog alert = builder.create();
        alert.show();
    }


    public static void initializeTts(Context context) {
    	mReviewer = context;
    	mTts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
			@Override
			public void onInit(int status) {
				// TODO: check if properly initialized (does not work yet)
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
