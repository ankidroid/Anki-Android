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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

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
    private static String[] mAppLanguages = {"ca", "cs", "de", "el", "es_ES", "fi", "fr", "it", "ja", "ko", "pl", "pt_PT", "ro", "ru", "sr", "sv-SE", "zh-CN", "zh-TW"};


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
    }


    private void initializeLanguageDialog() {
    	ArrayList<CharSequence> dialogItems = new ArrayList<CharSequence>();
    	ArrayList<String> dialogIds = new ArrayList<String>();
        dialogItems.add(getResources().getString(R.string.language_system));
        dialogIds.add("");
		for (String localeCode : mAppLanguages) {
			Locale loc;
			if (localeCode.length() > 2) {
				loc = new Locale(localeCode.substring(0,2), localeCode.substring(3,5));				
			} else {
				loc = new Locale(localeCode);				
			}
			dialogItems.add(loc.getDisplayName());
			dialogIds.add(loc.toString());
		}
		mLanguageDialogLabels = new CharSequence[dialogItems.size()];
		mLanguageDialogValues = new CharSequence[dialogItems.size()];
        dialogItems.toArray(mLanguageDialogLabels);
        dialogIds.toArray(mLanguageDialogValues);
        mLanguageSelection = (ListPreference) getPreferenceScreen().findPreference("language");
        mLanguageSelection.setEntries(mLanguageDialogLabels);
        mLanguageSelection.setEntryValues(mLanguageDialogValues);
    }


    @Override
    protected void onPause() {
        super.onPause();
        // Reschedule the checking in case the user has changed the veecheck
        // switch
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
        }
    }

}
