/***************************************************************************************
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

import com.hlidskialf.android.preference.SeekBarPreference;
import com.ichi2.libanki.Utils;
import com.tomgibara.android.veecheck.Veecheck;
import com.tomgibara.android.veecheck.util.PrefSettings;

/**
 * Preferences dialog.
 */
public class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    private boolean mVeecheckStatus;
    private PreferenceManager mPrefMan;
    private CheckBoxPreference zoomCheckboxPreference;
    private CheckBoxPreference swipeCheckboxPreference;
    private ListPreference mLanguageSelection;
    private CharSequence[] mLanguageDialogLabels;
    private CharSequence[] mLanguageDialogValues;
    private static String[] mAppLanguages = {"ca", "cs", "de", "el", "es_ES", "fi", "fr", "it", "ja", "ko", "pl", "pt_PT", "ro", "ru", "sr", "sv-SE", "zh-CN", "zh-TW", "en"};
    private static String[] mShowValueInSummList = {"language", "startup_mode", "hideQuestionInAnswer", "dictionary", "reportErrorMode", "minimumCardsDueForNotification", "deckOrder", "gestureShake", "gestureSwipeUp", "gestureSwipeDown", "gestureSwipeLeft", "gestureSwipeRight", "gestureDoubleTap", "gestureTapTop", "gestureTapBottom", "gestureTapRight", "gestureTapLeft"};
    private static String[] mShowValueInSummSeek = {"relativeDisplayFontSize", "relativeCardBrowserFontSize", "answerButtonSize", "whiteBoardStrokeWidth", "minShakeIntensity", "swipeSensibility", "timeoutAnswerSeconds"};
    private TreeMap<String, String> mListsToUpdate = new TreeMap<String, String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefMan = getPreferenceManager();
        mPrefMan.setSharedPreferencesName(PrefSettings.SHARED_PREFS_NAME);

        addPreferencesFromResource(R.xml.preferences);
        mVeecheckStatus = mPrefMan.getSharedPreferences().getBoolean(PrefSettings.KEY_ENABLED, PrefSettings.DEFAULT_ENABLED);
        
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        swipeCheckboxPreference = (CheckBoxPreference) getPreferenceScreen().findPreference("swipe");
        zoomCheckboxPreference = (CheckBoxPreference) getPreferenceScreen().findPreference("zoom");
        zoomCheckboxPreference.setEnabled(!swipeCheckboxPreference.isChecked());
        initializeLanguageDialog();
        initializeCustomFontsDialog();
        for (String key : mShowValueInSummList) {
            updateListPreference(key);
        }
        for (String key : mShowValueInSummSeek) {
            updateSeekBarPreference(key);
        }
    }


    private void updateListPreference(String key) {
        ListPreference listpref = (ListPreference) getPreferenceScreen().findPreference(key);
        String entry;
        try {
            entry = listpref.getEntry().toString();            
        } catch (NullPointerException e) {
            Log.e(AnkiDroidApp.TAG, "Error getting set preference value of " + key + ": " + e);
            entry = "?";
        }
        if (mListsToUpdate.containsKey(key)) {
            listpref.setSummary(replaceString(mListsToUpdate.get(key), entry));
        } else {
            String oldsum = (String) listpref.getSummary();
            if (oldsum.contains("XXX")) {
                mListsToUpdate.put(key, oldsum);
                listpref.setSummary(replaceString(oldsum, entry));
            } else {
                listpref.setSummary(entry);
            }
        }
    }


    private void updateSeekBarPreference(String key) {
        SeekBarPreference seekpref = (SeekBarPreference) getPreferenceScreen().findPreference(key);
        if (mListsToUpdate.containsKey(key)) {
            seekpref.setSummary(replaceString(mListsToUpdate.get(key), Integer.toString(seekpref.getValue())));
        } else {
            String oldsum = (String) seekpref.getSummary();
            if (oldsum.contains("XXX")) {
                mListsToUpdate.put(key, oldsum);
                seekpref.setSummary(replaceString(oldsum, Integer.toString(seekpref.getValue())));
            } else {
                seekpref.setSummary(Integer.toString(seekpref.getValue()));
            }
        }
    }


    private String replaceString(String str, String value) {
        if (str.contains("XXX")) {
            return str.replace("XXX", value);
        } else {
            return str;
        }
    }


    private void initializeLanguageDialog() {
    	TreeMap<String, String> items = new TreeMap<String, String>();
        for (String localeCode : mAppLanguages) {
			Locale loc;
			if (localeCode.length() > 2) {
				loc = new Locale(localeCode.substring(0,2), localeCode.substring(3,5));				
			} else {
				loc = new Locale(localeCode);				
			}
	    	items.put(loc.getDisplayName(), loc.toString());
		}
		mLanguageDialogLabels = new CharSequence[items.size() + 1];
		mLanguageDialogValues = new CharSequence[items.size() + 1];
		mLanguageDialogLabels[0] = getResources().getString(R.string.language_system);
		mLanguageDialogValues[0] = ""; 
		int i = 1;
		for (Map.Entry<String, String> e : items.entrySet()) {
			mLanguageDialogLabels[i] = e.getKey();
			mLanguageDialogValues[i] = e.getValue();
			i++;
		}
        mLanguageSelection = (ListPreference) getPreferenceScreen().findPreference("language");
        mLanguageSelection.setEntries(mLanguageDialogLabels);
        mLanguageSelection.setEntryValues(mLanguageDialogValues);
    }


    /** Initializes the list of custom fonts shown in the preferences. */
    private void initializeCustomFontsDialog() {
        ListPreference customFontsPreference =
            (ListPreference) getPreferenceScreen().findPreference("defaultFont");
        customFontsPreference.setEntries(getCustomFonts("System default"));
        customFontsPreference.setEntryValues(getCustomFonts(""));
    }


    @Override
    protected void onPause() {
        super.onPause();
        // Reschedule the checking in case the user has changed the veecheck switch
        if (mVeecheckStatus ^ mPrefMan.getSharedPreferences().getBoolean(PrefSettings.KEY_ENABLED, mVeecheckStatus)) {
            sendBroadcast(new Intent(Veecheck.getRescheduleAction(this)));
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("swipe")) {
        	zoomCheckboxPreference.setChecked(false);
        	zoomCheckboxPreference.setEnabled(!swipeCheckboxPreference.isChecked());
        } else if (key.equals("language")) {
        	Intent i = getBaseContext().getPackageManager().getLaunchIntentForPackage( getBaseContext().getPackageName());
        	i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        	startActivity(i);
        } else if (Arrays.asList(mShowValueInSummList).contains(key)) {
            updateListPreference(key);
        } else if (Arrays.asList(mShowValueInSummSeek).contains(key)) {
            updateSeekBarPreference(key);
        }
    }


    /** Returns a list of the names of the installed custom fonts. */
    private String[] getCustomFonts(String defaultValue) {
        File[] files = Utils.getCustomFonts(this);
        int count = files.length;
        Log.d(AnkiDroidApp.TAG, "There are " + count + " custom fonts");
        String[] names = new String[count + 1];
        for (int index = 0; index < count; ++index) {
            names[index] = Utils.removeExtension(files[index].getName());
            Log.d(AnkiDroidApp.TAG, "Adding custom font: " + names[index]);
        }
        names[count] = defaultValue;
        return names;
    }
}
