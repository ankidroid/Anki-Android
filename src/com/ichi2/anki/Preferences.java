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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

import com.hlidskialf.android.preference.SeekBarPreference;
import com.ichi2.themes.Themes;
import com.tomgibara.android.veecheck.Veecheck;
import com.tomgibara.android.veecheck.util.PrefSettings;

/**
 * Preferences dialog.
 */
public class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	private static final int DIALOG_WAL = 0;
	private static final int DIALOG_ASYNC = 1;
	private static final int DIALOG_BACKUP = 2;

    private boolean mVeecheckStatus;
    private PreferenceManager mPrefMan;
    private CheckBoxPreference zoomCheckboxPreference;
    private CheckBoxPreference swipeCheckboxPreference;
    private CheckBoxPreference animationsCheckboxPreference;
    private CheckBoxPreference walModePreference;
    private CheckBoxPreference useBackupPreference;
    private CheckBoxPreference asyncModePreference;
    private ListPreference mLanguageSelection;
    private CharSequence[] mLanguageDialogLabels;
    private CharSequence[] mLanguageDialogValues;
    private static String[] mAppLanguages = {"ar", "ca", "cs", "de", "el", "es_ES", "fi", "fr", "hu", "id", "it", "ja", "ko", "pl", "pt_PT", "ro", "ru", "sr", "sv-SE", "tr", "vi", "zh-CN", "zh-TW", "en"};
    private static String[] mShowValueInSummList = {"language", "startup_mode", "hideQuestionInAnswer", "dictionary", "reportErrorMode", "minimumCardsDueForNotification", "deckOrder", "gestureShake", "gestureSwipeUp", "gestureSwipeDown", "gestureSwipeLeft", "gestureSwipeRight", "gestureDoubleTap", "gestureTapTop", "gestureTapBottom", "gestureTapRight", "gestureTapLeft", "theme"};
    private static String[] mShowValueInSummSeek = {"relativeDisplayFontSize", "relativeCardBrowserFontSize", "answerButtonSize", "whiteBoardStrokeWidth", "minShakeIntensity", "swipeSensibility", "timeoutAnswerSeconds", "timeoutQuestionSeconds", "animationDuration", "backupMax"};
    private TreeMap<String, String> mListsToUpdate = new TreeMap<String, String>();
    private ProgressDialog mProgressDialog;
    private boolean lockCheckAction = false;
    private boolean walModeInitiallySet = false;
    private String dialogMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
//    	Themes.applyTheme(this);
        super.onCreate(savedInstanceState);

        mPrefMan = getPreferenceManager();
        mPrefMan.setSharedPreferencesName(PrefSettings.SHARED_PREFS_NAME);

        addPreferencesFromResource(R.xml.preferences);
        mVeecheckStatus = mPrefMan.getSharedPreferences().getBoolean(PrefSettings.KEY_ENABLED, PrefSettings.DEFAULT_ENABLED);
        
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        swipeCheckboxPreference = (CheckBoxPreference) getPreferenceScreen().findPreference("swipe");
        zoomCheckboxPreference = (CheckBoxPreference) getPreferenceScreen().findPreference("zoom");
        animationsCheckboxPreference = (CheckBoxPreference) getPreferenceScreen().findPreference("themeAnimations");
        walModePreference = (CheckBoxPreference) getPreferenceScreen().findPreference("walMode");
        useBackupPreference = (CheckBoxPreference) getPreferenceScreen().findPreference("useBackup");
        asyncModePreference = (CheckBoxPreference) getPreferenceScreen().findPreference("asyncMode");
        walModeInitiallySet = mPrefMan.getSharedPreferences().getBoolean("walMode", false);
        ListPreference listpref = (ListPreference) getPreferenceScreen().findPreference("theme");
        animationsCheckboxPreference.setEnabled(listpref.getValue().equals("2"));
        zoomCheckboxPreference.setEnabled(!swipeCheckboxPreference.isChecked());
        initializeLanguageDialog();
        initializeCustomFontsDialog();
        for (String key : mShowValueInSummList) {
            updateListPreference(key);
        }
        for (String key : mShowValueInSummSeek) {
            updateSeekBarPreference(key);
        }
        enableWalSupport();
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
        try {
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
        } catch (NullPointerException e) {
        	Log.e(AnkiDroidApp.TAG, "Exception when updating seekbar preference: " + e);
        }
    }


    private void enableWalSupport() {
    	Cursor cursor = null;
    	String sqliteVersion = "";
    	SQLiteDatabase database = null;
        try {
        	database = SQLiteDatabase.openOrCreateDatabase(":memory:", null);
        	cursor = database.rawQuery("select sqlite_version() AS sqlite_version", null);
        	while(cursor.moveToNext()){
        	   sqliteVersion = cursor.getString(0);
        	}
        } finally {
        	database.close();
            if (cursor != null) {
            	cursor.close();
            }
        }
        if (sqliteVersion.length() >= 3 && Double.parseDouble(sqliteVersion.subSequence(0, 3).toString()) >= 3.7) {
        	walModePreference.setEnabled(true);
        } else {
        	Log.e(AnkiDroidApp.TAG, "WAL mode not available due to a SQLite version lower than 3.7.0");
        	walModePreference.setChecked(false);
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
			Intent intent = this.getIntent();
			setResult(StudyOptions.RESULT_RESTART, intent);
			finish();
        } else if (key.equals("startup_mode")) {
			Intent intent = this.getIntent();
			setResult(StudyOptions.RESULT_RESTART, intent);
			finish();
        } else if (key.equals("theme")) {
        	if (!sharedPreferences.getString("theme", "0").equals("2")) {
        		animationsCheckboxPreference.setChecked(false);
        		animationsCheckboxPreference.setEnabled(false);
        	} else {
        		animationsCheckboxPreference.setEnabled(true);
        	}
        	Themes.resetTheme();
			Intent intent = this.getIntent();
			setResult(StudyOptions.RESULT_RESTART, intent);
			finish();
        } else if (Arrays.asList(mShowValueInSummList).contains(key)) {
            updateListPreference(key);
        } else if (Arrays.asList(mShowValueInSummSeek).contains(key)) {
            updateSeekBarPreference(key);
        } else if (key.equals("walMode") && !lockCheckAction) {
        	lockCheckAction = true;
        	if (sharedPreferences.getBoolean("walMode", false)) {
        		showDialog(DIALOG_WAL);
        	} else if (walModeInitiallySet) {
        		walModeInitiallySet = false;
        		dialogMessage = getResources().getString(R.string.wal_mode_set_message);
            	DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SET_ALL_DECKS_JOURNAL_MODE, mDeckOperationHandler, new DeckTask.TaskData(AnkiDroidApp.deck(), PrefSettings.getSharedPrefs(getBaseContext()).getString("deckPath", AnkiDroidApp.getStorageDirectory())));
        	} else {
        		lockCheckAction = false;        		
        	}
        } else if (key.equals("useBackup")) {
        	if (lockCheckAction)  {
        		lockCheckAction = false;
        	} else if (!useBackupPreference.isChecked()) {
        		lockCheckAction = true;
        		useBackupPreference.setChecked(true);
    			showDialog(DIALOG_BACKUP);
        	} else {
        		setReloadDeck();
        	}
        } else if (key.equals("asyncMode")) {
        	if (lockCheckAction)  {
        		lockCheckAction = false;
        	} else if (asyncModePreference.isChecked()) {
        		lockCheckAction = true;
        		asyncModePreference.setChecked(false);
    			showDialog(DIALOG_ASYNC);
        	} else {
        		setReloadDeck();
        	}
        } else if (key.equals("deckPath")) {
        	File decksDirectory = new File(sharedPreferences.getString("deckPath", AnkiDroidApp.getStorageDirectory()));
        	if (decksDirectory.exists()) {
        		AnkiDroidApp.createNoMediaFileIfMissing(decksDirectory);
        	}
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


    private void setReloadDeck() {
    	dialogMessage = getResources().getString(R.string.close_current_deck);
    	DeckTask.launchDeckTask(DeckTask.TASK_TYPE_CLOSE_DECK, mDeckOperationHandler, new DeckTask.TaskData(0, AnkiDroidApp.deck(), 0l, false));
		setResult(StudyOptions.RESULT_RELOAD_DECK, getIntent());
    }


    @Override
    protected Dialog onCreateDialog(int id) {
		Resources res = getResources();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        switch (id) {
        case DIALOG_WAL:
    		builder.setTitle(res.getString(R.string.wal_mode));
    		builder.setCancelable(false);
    		builder.setMessage(res.getString(R.string.wal_mode_message));
    		builder.setPositiveButton(res.getString(R.string.yes), new OnClickListener() {

    			@Override
    			public void onClick(DialogInterface arg0, int arg1) {
    				walModePreference.setChecked(true);
    	        	lockCheckAction = false;
    				setReloadDeck();
    			}
    		});
    		builder.setNegativeButton(res.getString(R.string.no), new OnClickListener() {

    			@Override
    			public void onClick(DialogInterface arg0, int arg1) {
    				walModePreference.setChecked(false);
    				lockCheckAction = false;
    			}
    		});
    		break;
        case DIALOG_BACKUP:
    		builder.setTitle(res.getString(R.string.backup_manager_title));
    		builder.setCancelable(false);
    		builder.setMessage(res.getString(R.string.pref_backup_warning));
    		builder.setPositiveButton(res.getString(R.string.yes), new OnClickListener() {

    			@Override
    			public void onClick(DialogInterface arg0, int arg1) {
    				lockCheckAction = true;
    				useBackupPreference.setChecked(false);
    				dialogMessage = getResources().getString(R.string.backup_delete);
    				DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DELETE_BACKUPS, mDeckOperationHandler, null);
    			}
    		});
    		builder.setNegativeButton(res.getString(R.string.no), null);
    		break;
        case DIALOG_ASYNC:
    		builder.setTitle(res.getString(R.string.async_mode));
    		builder.setCancelable(false);
    		builder.setMessage(res.getString(R.string.async_mode_message));
    		builder.setPositiveButton(res.getString(R.string.yes), new OnClickListener() {

    			@Override
    			public void onClick(DialogInterface arg0, int arg1) {
    				lockCheckAction = true;
    				asyncModePreference.setChecked(true);
    				setReloadDeck();
    			}
    		});
    		builder.setNegativeButton(res.getString(R.string.no), null);
    		break;
        }
		return builder.create();    	
    }


    private DeckTask.TaskListener mDeckOperationHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
        	mProgressDialog = ProgressDialog.show(Preferences.this, "", dialogMessage, true);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
        	if (mProgressDialog != null && mProgressDialog.isShowing()) {
        		mProgressDialog.dismiss();
        	}
        	lockCheckAction = false;
        }
    };

}
