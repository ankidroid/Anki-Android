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

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

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
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager.BadTokenException;

import com.hlidskialf.android.preference.SeekBarPreference;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.async.DeckTask;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.hooks.ChessFilter;
import com.ichi2.libanki.hooks.HebrewFixFilter;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;

/**
 * Preferences dialog.
 */
public class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    private static final int DIALOG_ASYNC = 1;
    private static final int DIALOG_BACKUP = 2;
    private static final int DIALOG_HEBREW_FONT = 3;
    private static final int DIALOG_WRITE_ANSWERS = 4;
    public static final String PLUGIN_PREFERENCE_PREFIX = "plugin.enabled:";

    // private boolean mVeecheckStatus;
    private PreferenceManager mPrefMan;
    private CheckBoxPreference zoomCheckboxPreference;
    private CheckBoxPreference keepScreenOnCheckBoxPreference;
    private CheckBoxPreference showAnswerCheckBoxPreference;
    private CheckBoxPreference swipeCheckboxPreference;
    private CheckBoxPreference animationsCheckboxPreference;
    private CheckBoxPreference useBackupPreference;
    private CheckBoxPreference asyncModePreference;
    private CheckBoxPreference eInkDisplayPreference;
    private CheckBoxPreference fadeScrollbars;
    private CheckBoxPreference convertFenText;
    private CheckBoxPreference fixHebrewText;
    private ListPreference mLanguageSelection;
    private CharSequence[] mLanguageDialogLabels;
    private CharSequence[] mLanguageDialogValues;
    private static String[] mAppLanguages = { "ar", "bg", "ca", "cs", "de", "el", "es-AR", "es-ES", "et", "fa", "fi", "fr", "hu", "id",
            "it", "ja", "ko", "nl", "no", "pl", "pt_PT", "pt_BR", "ro", "ru", "sr", "sv", "th", "tr", "uk", "vi",
            "zh_CN", "zh_TW", "en" };
    private static String[] mShowValueInSummList = { "language", "dictionary", "reportErrorMode",
            "minimumCardsDueForNotification", "gestureShake", "gestureSwipeUp", "gestureSwipeDown", "gestureSwipeLeft",
            "gestureSwipeRight", "gestureDoubleTap", "gestureTapTop", "gestureTapBottom", "gestureTapRight",
            "gestureLongclick", "gestureTapLeft"};//, "theme" };
    private static String[] mShowValueInSummSeek = { "relativeDisplayFontSize", "relativeCardBrowserFontSize",
            "answerButtonSize", "whiteBoardStrokeWidth", "minShakeIntensity", "swipeSensibility",
            "timeoutAnswerSeconds", "timeoutQuestionSeconds", "animationDuration", "backupMax" };
    private static String[] mShowValueInSummEditText = { "simpleInterfaceExcludeTags" };
    private TreeMap<String, String> mListsToUpdate = new TreeMap<String, String>();
    private StyledProgressDialog mProgressDialog;
    private boolean lockCheckAction = false;
    private String dialogMessage;
    Map<String, String> mPlugins;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Workaround for bug 4611: http://code.google.com/p/android/issues/detail?id=4611
        if (AnkiDroidApp.SDK_VERSION >= 7 && AnkiDroidApp.SDK_VERSION <= 10) {
            Themes.applyTheme(this, Themes.THEME_ANDROID_DARK);
        }
        super.onCreate(savedInstanceState);

        mPrefMan = getPreferenceManager();
        mPrefMan.setSharedPreferencesName(AnkiDroidApp.SHARED_PREFS_NAME);

        addPreferencesFromResource(R.xml.preferences);

        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        swipeCheckboxPreference = (CheckBoxPreference) getPreferenceScreen().findPreference("swipe");
        zoomCheckboxPreference = (CheckBoxPreference) getPreferenceScreen().findPreference("zoom");
        keepScreenOnCheckBoxPreference = (CheckBoxPreference) getPreferenceScreen().findPreference("keepScreenOn");
        showAnswerCheckBoxPreference = (CheckBoxPreference) getPreferenceScreen().findPreference("timeoutAnswer");
        animationsCheckboxPreference = (CheckBoxPreference) getPreferenceScreen().findPreference("themeAnimations");
        useBackupPreference = (CheckBoxPreference) getPreferenceScreen().findPreference("useBackup");
        asyncModePreference = (CheckBoxPreference) getPreferenceScreen().findPreference("asyncMode");
        eInkDisplayPreference = (CheckBoxPreference) getPreferenceScreen().findPreference("eInkDisplay");
        fadeScrollbars = (CheckBoxPreference) getPreferenceScreen().findPreference("fadeScrollbars");
//        ListPreference listpref = (ListPreference) getPreferenceScreen().findPreference("theme");
        convertFenText = (CheckBoxPreference) getPreferenceScreen().findPreference("convertFenText");
        fixHebrewText = (CheckBoxPreference) getPreferenceScreen().findPreference("fixHebrewText");
//        String theme = listpref.getValue();
//        animationsCheckboxPreference.setEnabled(theme.equals("2") || theme.equals("3"));
        zoomCheckboxPreference.setEnabled(!swipeCheckboxPreference.isChecked());
        initializeLanguageDialog();
        initializeCustomFontsDialog();
        mPlugins = new HashMap<String, String>();
        for (String key : mShowValueInSummList) {
            updateListPreference(key);
        }
        for (String key : mShowValueInSummSeek) {
            updateSeekBarPreference(key);
        }
        for (String key : mShowValueInSummEditText) {
            updateEditTextPreference(key);
        }
        if (AnkiDroidApp.SDK_VERSION <= 4) {
            fadeScrollbars.setChecked(false);
            fadeScrollbars.setEnabled(false);
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
                loc = new Locale(localeCode.substring(0, 2), localeCode.substring(3, 5));
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
        ListPreference customFontsPreference = (ListPreference) getPreferenceScreen().findPreference("defaultFont");
        customFontsPreference.setEntries(getCustomFonts("System default"));
        customFontsPreference.setEntryValues(getCustomFonts(""));
        ListPreference browserEditorCustomFontsPreference = (ListPreference) getPreferenceScreen().findPreference("browserEditorFont");
        browserEditorCustomFontsPreference.setEntries(getCustomFonts("System default"));
        browserEditorCustomFontsPreference.setEntryValues(getCustomFonts("", true));
    }

    @Override
    protected void onResume() {
    	super.onResume();
    	preparePluginsScreen();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }


    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        try {
            if (key.equals("swipe")) {
                zoomCheckboxPreference.setChecked(false);
                zoomCheckboxPreference.setEnabled(!swipeCheckboxPreference.isChecked());
            } else if (key.equals("timeoutAnswer")) {
                keepScreenOnCheckBoxPreference.setChecked(showAnswerCheckBoxPreference.isChecked());
            } else if (key.equals("language")) {
                closePreferences();
            } else if (key.equals("theme")) {
                String theme = sharedPreferences.getString("theme", "3");
                if (theme.equals("2") || theme.equals("3")) {
                    animationsCheckboxPreference.setChecked(false);
                    animationsCheckboxPreference.setEnabled(false);
                } else {
                    animationsCheckboxPreference.setEnabled(true);
                }
                Themes.loadTheme();
                switch (Integer.parseInt(sharedPreferences.getString("theme", "3"))) {
                    case Themes.THEME_ANDROID_DARK:
                    case Themes.THEME_ANDROID_LIGHT:
                    case Themes.THEME_BLUE:
                        sharedPreferences.edit().putString("defaultFont", "").commit();
                        break;
                    case Themes.THEME_FLAT:
                        sharedPreferences.edit().putString("defaultFont", "OpenSans").commit();
                        break;
                    case Themes.THEME_WHITE:
                        sharedPreferences.edit().putString("defaultFont", "OpenSans").commit();
                        break;
                }
                Intent intent = this.getIntent();
                setResult(DeckPicker.RESULT_RESTART, intent);
                closePreferences();
            } else if (Arrays.asList(mShowValueInSummList).contains(key)) {
                updateListPreference(key);
            } else if (Arrays.asList(mShowValueInSummSeek).contains(key)) {
                updateSeekBarPreference(key);
            } else if (Arrays.asList(mShowValueInSummEditText).contains(key)) {
                updateEditTextPreference(key);
            } else if (key.equals("writeAnswers") && sharedPreferences.getBoolean("writeAnswers", true)) {
                showDialog(DIALOG_WRITE_ANSWERS);
            } else if (key.equals("useBackup")) {
                if (lockCheckAction) {
                    lockCheckAction = false;
                } else if (!useBackupPreference.isChecked()) {
                    lockCheckAction = true;
                    useBackupPreference.setChecked(true);
                    showDialog(DIALOG_BACKUP);
                }
            } else if (key.equals("asyncMode")) {
                if (lockCheckAction) {
                    lockCheckAction = false;
                } else if (asyncModePreference.isChecked()) {
                    lockCheckAction = true;
                    asyncModePreference.setChecked(false);
                    showDialog(DIALOG_ASYNC);
                }
            } else if (key.equals("deckPath")) {
                File decksDirectory = new File(AnkiDroidApp.getCurrentAnkiDroidDirectory());
                if (decksDirectory.exists()) {
                    AnkiDroidApp.createNoMediaFileIfMissing(decksDirectory);
                }
            } else if (key.equals("eInkDisplay")) {
                animationsCheckboxPreference.setChecked(false);
                animationsCheckboxPreference.setEnabled(!eInkDisplayPreference.isChecked());
                fadeScrollbars.setChecked(false);
                fadeScrollbars.setEnabled(!eInkDisplayPreference.isChecked());
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
            } else if (mPlugins.containsKey(key)) {
                AnkiDroidApp.getPluginManager().pluginPrefChanged(this, mPlugins.get(key), sharedPreferences.getBoolean(key, false));
            }
        } catch (BadTokenException e) {
            Log.e(AnkiDroidApp.TAG, "Preferences: BadTokenException on showDialog: " + e);
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
                names[index] = mFonts.get(index-1).getPath();
                Log.d(AnkiDroidApp.TAG, "Adding custom font: " + names[index]);
            }
        } else {
            for (int index = 1; index < count + 1; ++index) {
                names[index] = mFonts.get(index-1).getName();
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
        if (AnkiDroidApp.SDK_VERSION > 4) {
            ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.FADE);
        }
    }


    @Override
    protected Dialog onCreateDialog(int id) {
        Resources res = getResources();
        StyledDialog.Builder builder = new StyledDialog.Builder(this);
        switch (id) {
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
                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DELETE_BACKUPS, mDeckOperationHandler,
                                (DeckTask.TaskData[]) null);
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
                    }
                });
                builder.setNegativeButton(res.getString(R.string.no), null);
                break;
            case DIALOG_WRITE_ANSWERS:
                builder.setTitle(res.getString(R.string.write_answers));
                builder.setCancelable(false);
                builder.setMessage(res.getString(R.string.write_answers_message));
                builder.setNegativeButton(res.getString(R.string.ok), null);
                break;
            case DIALOG_HEBREW_FONT:
                builder.setTitle(res.getString(R.string.fix_hebrew_text));
                builder.setCancelable(false);
                builder.setMessage(res.getString(R.string.fix_hebrew_instructions,
                		AnkiDroidApp.getCurrentAnkiDroidDirectory()));
                builder.setNegativeButton(R.string.cancel, null);
                builder.setPositiveButton(
                        res.getString(R.string.fix_hebrew_download_font), new OnClickListener(){
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent("android.intent.action.VIEW",
                                        Uri.parse(getResources().getString(R.string.link_hebrew_font)));
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
    };

    /**
     * Create the dynamic PreferenceScreen that hosts the plugins.
     */
    void preparePluginsScreen() {
        // Get list of installed plugins
        PluginManager plm = AnkiDroidApp.getPluginManager();
        plm.discoverPlugins();
        mPlugins.clear();
        if (plm.hasPlugins()) {
        	Map<String, Map<String, String>> controllers = plm.getControllerPlugins();
    		PreferenceCategory contrCategory = (PreferenceCategory) getPreferenceScreen().findPreference("plugins_controllers");
        	if (controllers.size() > 0) {
        		contrCategory.removeAll();
        		contrCategory.setEnabled(true);
        		for (String pluginKey : controllers.keySet()) {
        			Map<String, String> plugin = controllers.get(pluginKey);
        			CheckBoxPreference contrPref = new CheckBoxPreference(this);
        			String pluginPrefKey = PLUGIN_PREFERENCE_PREFIX + pluginKey;
        			contrPref.setKey(pluginPrefKey);
        			contrPref.setDefaultValue(false);
        			contrPref.setTitle(plugin.get("name"));
        			contrPref.setSummary(plugin.get("package"));
        			contrCategory.addPreference(contrPref);
        			mPlugins.put(pluginPrefKey, pluginKey);
        		}
        	} else {
        		contrCategory.setEnabled(false);
        	}
        }
    }
}
