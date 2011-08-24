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

import com.ichi2.themes.StyledDialog;

import android.speech.tts.TextToSpeech;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.util.Log;

public class ReadText {
    private static TextToSpeech mTts;
    private static ArrayList<String[]> availableTtsLocales = new ArrayList<String[]>();
    private static String mTextToSpeak;
    private static Context mReviewer;
    private static String mDeckFilename;
    private static long mModelId;
    private static long mCardModelId;
    private static int mQuestionAnswer;
    public static final String NO_TTS = "0";

    //private boolean mTtsReady = false;

    
    private static void speak(String loc) {
    	int result = mTts.setLanguage(new Locale(loc));
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
        	Log.e(AnkiDroidApp.TAG, "Error loading locale "+ loc.toString());
        } else {
          	mTts.speak(mTextToSpeak, TextToSpeech.QUEUE_FLUSH, null);
        }    	
    }


    public static void setLanguageInformation(long modelId, long cardModelId) {
    	mModelId = modelId;
    	mCardModelId = cardModelId;    	
    }


    public static String getLanguage(int qa) {
        return MetaDB.getLanguage(mReviewer, mDeckFilename,  mModelId, mCardModelId, qa);
    }


    public static void textToSpeech(String text, int qa) {
    	mTextToSpeak = text;
    	mQuestionAnswer = qa;

    	String language = getLanguage(mQuestionAnswer);
        if (availableTtsLocales.isEmpty()) {
	    	Locale[] systemLocales = Locale.getAvailableLocales();
			for (Locale loc : systemLocales) {
				if (mTts.isLanguageAvailable(loc) == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
					availableTtsLocales.add(new String[]{loc.getISO3Language(), loc.getDisplayName()});
				}
			}			
		}

        // Check, if stored language is available
        for (int i = 0; i < availableTtsLocales.size(); i++) {
    		if (language.equals(NO_TTS)) {
    		    return;
    		} else if (language.equals(availableTtsLocales.get(i)[0])) {
    			speak(language);
    			return;
    		}
		}

        // Otherwise ask 
        Resources res = mReviewer.getResources();
        StyledDialog.Builder builder = new StyledDialog.Builder(mReviewer);
        if (availableTtsLocales.size() == 0) {
        	builder.setTitle(res.getString(R.string.no_tts_available_title));
            builder.setMessage(res.getString(R.string.no_tts_available_message));
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setPositiveButton(res.getString(R.string.ok), null);
        } else {
        	ArrayList<CharSequence> dialogItems = new ArrayList<CharSequence>();
        	final ArrayList<String> dialogIds = new ArrayList<String>();
            builder.setTitle(R.string.select_locale_title);
            // Add option: "no tts"
            dialogItems.add(res.getString(R.string.tts_no_tts));
            dialogIds.add(NO_TTS);
            for (int i = 0; i < availableTtsLocales.size(); i++) {
                dialogItems.add(availableTtsLocales.get(i)[1]);
                dialogIds.add(availableTtsLocales.get(i)[0]);
            }
            String[] items = new String[dialogItems.size()];
            dialogItems.toArray(items);

            builder.setItems(items, new DialogInterface.OnClickListener() {
    			@Override
    			public void onClick(DialogInterface dialog, int which) {
    				MetaDB.storeLanguage(mReviewer, mDeckFilename,  mModelId, mCardModelId, mQuestionAnswer, dialogIds.get(which));
    				speak(dialogIds.get(which));
    			}
            });        	
        }
        builder.create().show();
    }


    public static void initializeTts(Context context, String deckFilename) {
    	mReviewer = context;
    	mDeckFilename = deckFilename;
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
