/***************************************************************************************
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager.BadTokenException;

import com.hlidskialf.android.preference.SeekBarPreference;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.async.DeckTask;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.hooks.ChessFilter;
import com.ichi2.libanki.hooks.HebrewFixFilter;
import com.ichi2.preferences.NumberRangePreference;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;
import com.ichi2.utils.LanguageUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Preferences dialog.
 */
public class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    private static final int DIALOG_BACKUP = 2;
    private static final int DIALOG_HEBREW_FONT = 3;
    public static boolean COMING_FROM_ADD = false;

    /** Key of the language preference */
    public static final String LANGUAGE = "language";

    // private boolean mVeecheckStatus;
    private Collection mCol;
    private PreferenceManager mPrefMan;
    private CheckBoxPreference keepScreenOnCheckBoxPreference;
    private CheckBoxPreference showAnswerCheckBoxPreference;
    private CheckBoxPreference useBackupPreference;
    private CheckBoxPreference convertFenText;
    private CheckBoxPreference inputWorkaround;
    private CheckBoxPreference longclickWorkaround;
    private CheckBoxPreference fixHebrewText;
    private CheckBoxPreference fixArabicText;
    private EditTextPreference collectionPathPreference;
    private Preference syncAccount;
    private static String[] sShowValueInSummList = { LANGUAGE, "dictionary", "reportErrorMode",
            "minimumCardsDueForNotification", "gestureSwipeUp", "gestureSwipeDown", "gestureSwipeLeft",
            "gestureSwipeRight", "gestureDoubleTap", "gestureTapTop", "gestureTapBottom", "gestureTapRight",
            "gestureLongclick", "gestureTapLeft", "newSpread", "useCurrent", "defaultFont", "overrideFontBehavior", "browserEditorFont" };
    private static String[] sListNumericCheck = {"minimumCardsDueForNotification"};
    private static String[] sShowValueInSummSeek = { "relativeDisplayFontSize", "relativeCardBrowserFontSize",
            "relativeImageSize", "answerButtonSize", "whiteBoardStrokeWidth", "swipeSensitivity",
            "timeoutAnswerSeconds", "timeoutQuestionSeconds", "backupMax", "dayOffset" };
    private static String[] sShowValueInSummEditText = { "simpleInterfaceExcludeTags", "deckPath" };
    private static String[] sShowValueInSummNumRange = { "timeLimit", "learnCutoff" };
    private TreeMap<String, String> mListsToUpdate = new TreeMap<String, String>();
    private StyledProgressDialog mProgressDialog;
    private boolean lockCheckAction = false;
    private String dialogMessage;

    // Used for calculating dayOffset
    private Calendar mStartDate;

    // The ones below are persisted in the collection
    private CheckBoxPreference showEstimates;
    private CheckBoxPreference showProgress;
    private NumberRangePreference learnCutoff;
    private NumberRangePreference timeLimit;
    private ListPreference useCurrent;
    private ListPreference newSpread;
    private SeekBarPreference dayOffset;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Workaround for bug 4611: http://code.google.com/p/android/issues/detail?id=4611
        if (AnkiDroidApp.SDK_VERSION <= 10) {
            Themes.applyTheme(this, Themes.THEME_ANDROID_DARK);
        }
        super.onCreate(savedInstanceState);

        mCol = AnkiDroidApp.getCol();
        mPrefMan = getPreferenceManager();
        mPrefMan.setSharedPreferencesName(AnkiDroidApp.SHARED_PREFS_NAME);

        addPreferencesFromResource(R.xml.preferences);

        collectionPathPreference = (EditTextPreference) getPreferenceScreen().findPreference("deckPath");
        keepScreenOnCheckBoxPreference = (CheckBoxPreference) getPreferenceScreen().findPreference("keepScreenOn");
        showAnswerCheckBoxPreference = (CheckBoxPreference) getPreferenceScreen().findPreference("timeoutAnswer");
        useBackupPreference = (CheckBoxPreference) getPreferenceScreen().findPreference("useBackup");
        convertFenText = (CheckBoxPreference) getPreferenceScreen().findPreference("convertFenText");
        syncAccount = (Preference) getPreferenceScreen().findPreference("syncAccount");
        showEstimates = (CheckBoxPreference) getPreferenceScreen().findPreference("showEstimates");
        showProgress = (CheckBoxPreference) getPreferenceScreen().findPreference("showProgress");
        learnCutoff = (NumberRangePreference) getPreferenceScreen().findPreference("learnCutoff");
        timeLimit = (NumberRangePreference) getPreferenceScreen().findPreference("timeLimit");
        useCurrent = (ListPreference) getPreferenceScreen().findPreference("useCurrent");
        newSpread = (ListPreference) getPreferenceScreen().findPreference("newSpread");
        dayOffset = (SeekBarPreference) getPreferenceScreen().findPreference("dayOffset");
        // Workaround preferences
        PreferenceCategory workarounds = (PreferenceCategory) getPreferenceScreen().findPreference("category_workarounds");
        inputWorkaround = (CheckBoxPreference) getPreferenceScreen().findPreference("inputWorkaround");
        longclickWorkaround = (CheckBoxPreference) getPreferenceScreen().findPreference("textSelectionLongclickWorkaround");
        fixHebrewText = (CheckBoxPreference) getPreferenceScreen().findPreference("fixHebrewText");
        fixArabicText = (CheckBoxPreference) getPreferenceScreen().findPreference("fixArabicText");
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        if (AnkiDroidApp.SDK_VERSION > 14){
            workarounds.removePreference(inputWorkaround);
            preferences.edit().putBoolean("inputWorkaround", false);
        }
        if (AnkiDroidApp.SDK_VERSION > 10){
            workarounds.removePreference(longclickWorkaround);
            preferences.edit().putBoolean("longclickWorkaround", false);
        }
        if (AnkiDroidApp.SDK_VERSION > 8){
            workarounds.removePreference(fixArabicText);
            preferences.edit().putBoolean("fixArabicText", false);
        }

        initializeLanguageDialog();
        
        // Make custom fonts generated when fonts dialog opened
        Preference fontsPreference = (Preference) getPreferenceScreen().findPreference("font_preference_group");
        fontsPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    initializeCustomFontsDialog();
                    return false;
                }
            });         
        
        // Check that input is valid when changing the collection path
        collectionPathPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, final Object newValue) {
                File collectionDirectory = new File((String) newValue);
                if (!collectionDirectory.exists()) {
                    Dialog pathCheckDialog = new AlertDialog.Builder(Preferences.this)
                    .setTitle(R.string.dialog_collection_path_title)
                    .setMessage(R.string.dialog_collection_path_text)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            AnkiDroidApp.getSharedPrefs(getBaseContext()).edit().putString("deckPath", (String) newValue).commit();
                            updateEditTextPreference("deckPath");
                            
                        }
                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {}
                    })
                    .create();
                    pathCheckDialog.show();
                    return false;
                } else {
                    return true;
                }
                
            }
        });
        
        // About dialog
        Preference dialogPreference = (Preference) getPreferenceScreen().findPreference("about_dialog_preference");
        dialogPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(Preferences.this, Info.class));
                    return true;
                }
            });
        dialogPreference.setSummary(getResources().getString(R.string.about_version) + " " + AnkiDroidApp.getPkgVersionName());

        if (mCol != null) {
            // For collection preferences, we need to fetch the correct values from the collection
            mStartDate = GregorianCalendar.getInstance();
            Timestamp timestamp = new Timestamp(mCol.getCrt() * 1000);
            mStartDate.setTimeInMillis(timestamp.getTime());
            dayOffset.setValue(mStartDate.get(Calendar.HOUR_OF_DAY));
            try {
                JSONObject conf = mCol.getConf();
                learnCutoff.setValue(conf.getInt("collapseTime") / 60);
                timeLimit.setValue(conf.getInt("timeLim") / 60);
                showEstimates.setChecked(conf.getBoolean("estTimes"));
                showProgress.setChecked(conf.getBoolean("dueCounts"));
                newSpread.setValueIndex(conf.getInt("newSpread"));
                useCurrent.setValueIndex(conf.optBoolean("addToCur", true) ? 0 : 1);
            } catch (JSONException e) {
                throw new RuntimeException();
            } catch (NumberFormatException e) {
                throw new RuntimeException();
            }
        } else {
            // It's possible to open the preferences from the loading screen if no SD card is found.
            // In that case, there will be no collection loaded, so we need to disable the settings
            // that read from and write to the collection.
            dayOffset.setEnabled(false);
            learnCutoff.setEnabled(false);
            timeLimit.setEnabled(false);
            showEstimates.setEnabled(false);
            showProgress.setEnabled(false);
            newSpread.setEnabled(false);
            useCurrent.setEnabled(false);
        }

        for (String key : sShowValueInSummList) {
            if (Arrays.asList(sListNumericCheck).contains(key)) {
                updateListPreference(key, true);
            } else {
                updateListPreference(key, false);
            }
        }
        for (String key : sShowValueInSummSeek) {
            updateSeekBarPreference(key);
        }
        for (String key : sShowValueInSummEditText) {
            updateEditTextPreference(key);
        }
        for (String key : sShowValueInSummNumRange) {
            updateNumberRangePreference(key);
        }

    }


    private void updateListPreference(String key, final boolean numericCheck) {
        ListPreference listpref = (ListPreference) getPreferenceScreen().findPreference(key);
        String entry;
        try {
            entry = listpref.getEntry().toString();
        } catch (NullPointerException e) {
            Log.e(AnkiDroidApp.TAG, "Error getting set preference value of " + key + ": " + e);
            entry = "?";
        }
        if (mListsToUpdate.containsKey(key)) {
            if (numericCheck){
                // replace any XXX with the value if value numeric, otherwise return value
                listpref.setSummary(replaceStringIfNumeric(mListsToUpdate.get(key), entry));
            } else {
                // replace any XXX with the value
                listpref.setSummary(replaceString(mListsToUpdate.get(key), entry));
            }
        } else {
            String oldsum = (String) listpref.getSummary();
            if (oldsum.contains("XXX")) {
                mListsToUpdate.put(key, oldsum);
                if (numericCheck) {
                    // replace any XXX with the value if value numeric, otherwise return value
                    listpref.setSummary(replaceStringIfNumeric(oldsum, entry));
                } else {
                    // replace any XXX with the value
                    listpref.setSummary(replaceString(oldsum, entry));
                }

            } else {
                listpref.setSummary(entry);
            }
        }
    }


    private void updateEditTextPreference(String key) {
        EditTextPreference pref = (EditTextPreference) getPreferenceScreen().findPreference(key);
        String entry;
        try {
            entry = pref.getText();
        } catch (NullPointerException e) {
            Log.e(AnkiDroidApp.TAG, "Error getting set preference value of " + key + ": " + e);
            entry = "?";
        }
        if (mListsToUpdate.containsKey(key)) {
            pref.setSummary(replaceString(mListsToUpdate.get(key), entry));
        } else {
            String oldsum = (String) pref.getSummary();
            if (oldsum.contains("XXX")) {
                mListsToUpdate.put(key, oldsum);
                pref.setSummary(replaceString(oldsum, entry));
            } else {
                pref.setSummary(entry);
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


    private void updateNumberRangePreference(String key) {
        NumberRangePreference numPref = (NumberRangePreference) getPreferenceScreen().findPreference(key);
        try {
            String value = Integer.toString(numPref.getValue());
            if (mListsToUpdate.containsKey(key)) {
                numPref.setSummary(replaceString(mListsToUpdate.get(key), value));
            } else {
                String oldSum = (String) numPref.getSummary();
                if (oldSum.contains("XXX")) {
                    mListsToUpdate.put(key, oldSum);
                    numPref.setSummary(replaceString(oldSum, value));
                } else {
                    numPref.setSummary(value);
                }
            }
        } catch (NullPointerException e) {
            Log.e(AnkiDroidApp.TAG, "Exception when updating NumberRangePreference: " + e);
        }
    }


    private String replaceString(String str, String value) {
        if (str.contains("XXX")) {
            return str.replace("XXX", value);
        } else {
            return str;
        }
    }

    private String replaceStringIfNumeric(String str, String value) {
        try {
            Double.parseDouble(value);
            return replaceString(str, value);
        } catch (NumberFormatException e){
            return value;
        }
    }

    private void initializeLanguageDialog() {
        Map<String, String> items = new TreeMap<String, String>();
        for (String localeCode : LanguageUtil.APP_LANGUAGES) {
            Locale loc = LanguageUtil.getLocale(localeCode);
            items.put(loc.getDisplayName(), loc.toString());
        }
        CharSequence[] languageDialogLabels = new CharSequence[items.size() + 1];
        CharSequence[] languageDialogValues = new CharSequence[items.size() + 1];
        languageDialogLabels[0] = getResources().getString(R.string.language_system);
        languageDialogValues[0] = "";
        int i = 1;
        for (Map.Entry<String, String> e : items.entrySet()) {
            languageDialogLabels[i] = e.getKey();
            languageDialogValues[i] = e.getValue();
            i++;
        }
        ListPreference languageSelection = (ListPreference) getPreferenceScreen().findPreference(LANGUAGE);
        languageSelection.setEntries(languageDialogLabels);
        languageSelection.setEntryValues(languageDialogValues);
    }


    /** Initializes the list of custom fonts shown in the preferences. */
    private void initializeCustomFontsDialog() {
        ListPreference defaultFontPreference = (ListPreference) getPreferenceScreen().findPreference("defaultFont");
        if (defaultFontPreference != null) {
            defaultFontPreference.setEntries(getCustomFonts("System default"));
            defaultFontPreference.setEntryValues(getCustomFonts(""));
        }

        ListPreference browserEditorCustomFontsPreference = (ListPreference) getPreferenceScreen().findPreference(
                "browserEditorFont");
        browserEditorCustomFontsPreference.setEntries(getCustomFonts("System default"));
        browserEditorCustomFontsPreference.setEntryValues(getCustomFonts("", true));
        updateListPreference("defaultFont", false);
        updateListPreference("browserEditorFont", false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }


    @Override
    protected void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        // syncAccount's summary can change while preferences are still open (user logs
        // in from preferences screen), so we need to update it here.
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        String username = preferences.getString("username", "");
        if (TextUtils.isEmpty(username)) {
            syncAccount.setSummary(R.string.sync_account_summ_logged_out);
        } else {
            syncAccount.setSummary(getString(R.string.sync_account_summ_logged_in, username));
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        try {
            if (key.equals("timeoutAnswer")) {
                keepScreenOnCheckBoxPreference.setChecked(showAnswerCheckBoxPreference.isChecked());
            } else if (key.equals(LANGUAGE)) {
                closePreferences();
            } else if (key.equals("useBackup")) {
                if (lockCheckAction) {
                    lockCheckAction = false;
                } else if (!useBackupPreference.isChecked()) {
                    lockCheckAction = true;
                    useBackupPreference.setChecked(true);
                    showDialog(DIALOG_BACKUP);
                }
            } else if (key.equals("deckPath")) {
                File decksDirectory = new File(AnkiDroidApp.getCurrentAnkiDroidDirectory());
                if (decksDirectory.exists()) {
                    AnkiDroidApp.createNoMediaFileIfMissing(decksDirectory);
                }
            } else if (key.equals("convertFenText")) {
                if (convertFenText.isChecked()) {
                    ChessFilter.install(AnkiDroidApp.getHooks());
                } else {
                    ChessFilter.uninstall(AnkiDroidApp.getHooks());
                }
            } else if (key.equals("fixHebrewText")) {
                if (fixHebrewText.isChecked()) {
                    HebrewFixFilter.install(AnkiDroidApp.getHooks());
                    showDialog(DIALOG_HEBREW_FONT);
                } else {
                    HebrewFixFilter.uninstall(AnkiDroidApp.getHooks());
                }
            } else if (key.equals("showProgress")) {
                mCol.getConf().put("dueCounts", showProgress.isChecked());
                mCol.setMod();
            } else if (key.equals("showEstimates")) {
                mCol.getConf().put("estTimes", showEstimates.isChecked());
                mCol.setMod();
            } else if (key.equals("newSpread")) {
                mCol.getConf().put("newSpread", Integer.parseInt(newSpread.getValue()));
                mCol.setMod();
            } else if (key.equals("timeLimit")) {
                mCol.getConf().put("timeLim", timeLimit.getValue() * 60);
                mCol.setMod();
            } else if (key.equals("learnCutoff")) {
                mCol.getConf().put("collapseTime", learnCutoff.getValue() * 60);
                mCol.setMod();
            } else if (key.equals("useCurrent")) {
                mCol.getConf().put("addToCur", useCurrent.getValue().equals("0"));
                mCol.setMod();
            } else if (key.equals("dayOffset")) {
                int hours = dayOffset.getValue();
                Calendar date = (Calendar) mStartDate.clone();
                date.set(Calendar.HOUR_OF_DAY, hours);
                mCol.setCrt(date.getTimeInMillis() / 1000);
                mCol.setMod();
            }
            if (Arrays.asList(sShowValueInSummList).contains(key)) {
                if (Arrays.asList(sListNumericCheck).contains(key)) {
                    updateListPreference(key, true);
                } else {
                    updateListPreference(key, false);
                }
            } else if (Arrays.asList(sShowValueInSummSeek).contains(key)) {
                updateSeekBarPreference(key);
            } else if (Arrays.asList(sShowValueInSummEditText).contains(key)) {
                updateEditTextPreference(key);
            } else if (Arrays.asList(sShowValueInSummNumRange).contains(key)) {
                updateNumberRangePreference(key);
            }
        } catch (BadTokenException e) {
            Log.e(AnkiDroidApp.TAG, "Preferences: BadTokenException on showDialog: " + e);
        } catch (NumberFormatException e) {
            throw new RuntimeException();
        } catch (JSONException e) {
            throw new RuntimeException();
        }
    }


    /** Returns a list of the names of the installed custom fonts. */
    private String[] getCustomFonts(String defaultValue) {
        return getCustomFonts(defaultValue, false);
    }


    private String[] getCustomFonts(String defaultValue, boolean useFullPath) {
        List<AnkiFont> mFonts = Utils.getCustomFonts(this);
        int count = mFonts.size();
        Log.d(AnkiDroidApp.TAG, "There are " + count + " custom fonts");
        String[] names = new String[count + 1];
        names[0] = defaultValue;
        if (useFullPath) {
            for (int index = 1; index < count + 1; ++index) {
                names[index] = mFonts.get(index - 1).getPath();
                Log.d(AnkiDroidApp.TAG, "Adding custom font: " + names[index]);
            }
        } else {
            for (int index = 1; index < count + 1; ++index) {
                names[index] = mFonts.get(index - 1).getName();
                Log.d(AnkiDroidApp.TAG, "Adding custom font: " + names[index]);
            }
        }
        return names;
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Log.i(AnkiDroidApp.TAG, "DeckOptions - onBackPressed()");
            closePreferences();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    private void closePreferences() {
        finish();
        ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.FADE);
        if (mCol != null) {
            mCol.save();
        }
    }


    @Override
    protected Dialog onCreateDialog(int id) {
        Resources res = getResources();
        StyledDialog.Builder builder = new StyledDialog.Builder(this);
        switch (id) {
            case DIALOG_BACKUP:
                builder.setTitle(res.getString(R.string.pref_disable_backup_title));
                builder.setCancelable(false);
                builder.setMessage(res.getString(R.string.pref_disable_backup_warning));
                builder.setPositiveButton(res.getString(R.string.dialog_positive_disable), new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        lockCheckAction = true;
                        useBackupPreference.setChecked(false);
                        dialogMessage = getResources().getString(R.string.backup_delete);
                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DELETE_BACKUPS, mDeckOperationHandler,
                                (DeckTask.TaskData[]) null);
                    }
                });
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
                break;
            case DIALOG_HEBREW_FONT:
                builder.setTitle(res.getString(R.string.fix_hebrew_text));
                builder.setCancelable(false);
                builder.setMessage(res.getString(R.string.fix_hebrew_instructions,
                        AnkiDroidApp.getCurrentAnkiDroidDirectory()));
                builder.setNegativeButton(R.string.dialog_cancel, null);
                builder.setPositiveButton(res.getString(R.string.fix_hebrew_download_font), new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent("android.intent.action.VIEW", Uri.parse(getResources().getString(
                                R.string.link_hebrew_font)));
                        startActivity(intent);
                    }
                });
                break;
        }
        return builder.create();
    }

    private DeckTask.TaskListener mDeckOperationHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            mProgressDialog = StyledProgressDialog.show(Preferences.this, "", dialogMessage, true);
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


        @Override
        public void onCancelled() {
            // TODO Auto-generated method stub
            
        }
    };
}
