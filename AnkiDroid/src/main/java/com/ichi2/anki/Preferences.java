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
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.support.annotation.NonNull;
import android.preference.PreferenceScreen;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.WindowManager.BadTokenException;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.ui.AppCompatPreferenceActivity;
import com.ichi2.ui.SeekBarPreference;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.exception.StorageAccessException;
import com.ichi2.async.DeckTask;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.hooks.ChessFilter;
import com.ichi2.libanki.hooks.HebrewFixFilter;
import com.ichi2.libanki.hooks.Hooks;
import com.ichi2.preferences.NumberRangePreference;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.utils.LanguageUtil;
import com.ichi2.utils.VersionUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import timber.log.Timber;

/**
 * Preferences dialog.
 */
// TODO: change to PreferenceFragment to get rid of the deprecation warnings
@SuppressWarnings("deprecation")
public class Preferences extends AppCompatPreferenceActivity implements OnSharedPreferenceChangeListener {

    private static final int DIALOG_BACKUP = 2;
    private static final int DIALOG_HEBREW_FONT = 3;
    public static boolean COMING_FROM_ADD = false;

    /** Key of the language preference */
    public static final String LANGUAGE = "language";
    private Collection mCol;

    // Other variables
    private MaterialDialog mProgressDialog;
    private boolean lockCheckAction = false;
    private String dialogMessage;
    private final HashMap<String, String> mOriginalSumarries = new HashMap<>();
    private static final String [] sCollectionPreferences = {"showEstimates", "showProgress",
            "learnCutoff", "timeLimit", "useCurrent", "newSpread", "dayOffset"};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.preferences);
        mCol = CollectionHelper.getInstance().getCol(this);

        // TODO: Only use intent approach on pre-honeycomb, using PreferenceFragment on newer devices
        String action = getIntent().getAction();
        if (action == null) {
            // Headers screen
            addPreferencesFromResource(R.xml.preference_headers_legacy);
        } else {
            // Subscreen
            switch (action) {
                case "com.ichi2.anki.prefs.general":
                    addPreferencesFromResource(R.xml.preferences_general);
                    // Build languages
                    initializeLanguageDialog();
                    // Check that input is valid before committing change in the collection path
                    EditTextPreference collectionPathPreference = (EditTextPreference) getPreferenceScreen().findPreference("deckPath");
                    collectionPathPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                        public boolean onPreferenceChange(Preference preference, final Object newValue) {
                            final String newPath = (String) newValue;
                            try {
                                CollectionHelper.initializeAnkiDroidDirectory(newPath);
                                return true;
                            } catch (StorageAccessException e) {
                                Timber.e(e, "Could not initialize directory: %s", newPath);
                                Toast.makeText(getApplicationContext(), R.string.dialog_collection_path_not_dir, Toast.LENGTH_LONG).show();
                                return false;
                            }
                        }
                    });
                    break;
                case "com.ichi2.anki.prefs.reviewing":
                    addPreferencesFromResource(R.xml.preferences_reviewing);
                    break;
                case "com.ichi2.anki.prefs.fonts":
                    addPreferencesFromResource(R.xml.preferences_fonts);
                    initializeCustomFontsDialog();
                    break;
                case "com.ichi2.anki.prefs.gestures":
                    addPreferencesFromResource(R.xml.preferences_gestures);
                    break;
                case "com.ichi2.anki.prefs.advanced":
                    addPreferencesFromResource(R.xml.preferences_advanced);
                    // Force full sync option
                    Preference fullSyncPreference = getPreferenceScreen().findPreference("force_full_sync");
                    fullSyncPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                        public boolean onPreferenceClick(Preference preference) {
                            if (mCol != null && mCol.getDb() != null) {
                                // TODO: Could be useful to show the full confirmation dialog
                                mCol.modSchemaNoCheck();
                                mCol.setMod();
                                Toast.makeText(getApplicationContext(), R.string.ok, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getApplicationContext(), R.string.vague_error, Toast.LENGTH_SHORT).show();
                            }
                            return true;
                        }
                    });
                    // Workaround preferences
                    removeUnnecessaryWorkarounds();
                    break;
            }
        }

        // Set the text for the summary of each of the preferences
        initAllPreferences();

        // Add an Actionbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return false;
    }


    // Workaround for Android bug 4611: http://code.google.com/p/android/issues/detail?id=4611
    @SuppressWarnings("deprecation")
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference)
    {
        super.onPreferenceTreeClick(preferenceScreen, preference);
        if (preference!=null && !CompatHelper.isHoneycomb()) {
            if (preference instanceof PreferenceScreen) {
                if (((PreferenceScreen) preference).getDialog() != null) {
                    ((PreferenceScreen) preference).getDialog().getWindow().getDecorView().setBackgroundDrawable(
                            this.getWindow().getDecorView().getBackground().getConstantState().newDrawable());
                }
            }
        }
        return false;
    }

    /**
     * Loop over every preference in the list and set the summary text
     */
    private void initAllPreferences() {
        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); ++i) {
            Preference preference = getPreferenceScreen().getPreference(i);
            if (preference instanceof PreferenceGroup) {
                PreferenceGroup preferenceGroup = (PreferenceGroup) preference;
                for (int j = 0; j < preferenceGroup.getPreferenceCount(); ++j) {
                    Preference nestedPreference = preferenceGroup.getPreference(j);
                    if (nestedPreference instanceof PreferenceGroup) {
                        PreferenceGroup nestedPreferenceGroup = (PreferenceGroup) nestedPreference;
                        for (int k = 0; k < nestedPreferenceGroup.getPreferenceCount(); ++k) {
                            initPreference(nestedPreferenceGroup.getPreference(k));
                        }
                    } else {
                        initPreference(preferenceGroup.getPreference(j));
                    }
                }
            } else {
                initPreference(preference);
            }
        }
    }



    private void initPreference(Preference pref) {
        // Load stored values from Preferences which are stored in the Collection
        if (Arrays.asList(sCollectionPreferences).contains(pref.getKey())) {
            if (mCol != null) {
                try {
                    JSONObject conf = mCol.getConf();
                    switch (pref.getKey()) {
                        case "showEstimates":
                            ((CheckBoxPreference)pref).setChecked(conf.getBoolean("estTimes"));
                            break;
                        case "showProgress":
                            ((CheckBoxPreference)pref).setChecked(conf.getBoolean("dueCounts"));
                            break;
                        case "learnCutoff":
                            ((NumberRangePreference)pref).setValue(conf.getInt("collapseTime") / 60);
                            break;
                        case "timeLimit":
                            ((NumberRangePreference)pref).setValue(conf.getInt("timeLim") / 60);
                            break;
                        case "useCurrent":
                            ((ListPreference)pref).setValueIndex(conf.optBoolean("addToCur", true) ? 0 : 1);
                            break;
                        case "newSpread":
                            ((ListPreference)pref).setValueIndex(conf.getInt("newSpread"));
                            break;
                        case "dayOffset":
                            Calendar calendar = new GregorianCalendar();
                            Timestamp timestamp = new Timestamp(mCol.getCrt() * 1000);
                            calendar.setTimeInMillis(timestamp.getTime());
                            ((SeekBarPreference)pref).setValue(calendar.get(Calendar.HOUR_OF_DAY));
                            break;
                    }
                } catch (JSONException | NumberFormatException e) {
                    throw new RuntimeException();
                }
            } else {
                // Disable Col preferences if Collection closed
                pref.setEnabled(false);
            }
        }
        // Set the value from the summary cache
        CharSequence s = pref.getSummary();
        mOriginalSumarries.put(pref.getKey(), (s != null) ? s.toString() : "");
        // Update summary
        updateSummary(pref);
    }

    private void updateSummary(Preference pref) {
         if (pref.getKey() == null) {
             return;
         }
         // Handle about dialog separately
        if (pref.getKey().equals("about_dialog_preference")) {
            pref.setSummary(getResources().getString(R.string.about_version) + " " + VersionUtils.getPkgVersionName());
            return;
        }
         // Get value text
        String value;
        try {
            if (pref instanceof ListPreference) {
                value = ((ListPreference) pref).getEntry().toString();
            } else if (pref instanceof EditTextPreference) {
                value = ((EditTextPreference) pref).getText();
            } else if (pref instanceof  NumberRangePreference) {
                value = Integer.toString(((NumberRangePreference) pref).getValue());
            } else if (pref instanceof SeekBarPreference) {
                value = Integer.toString(((SeekBarPreference) pref).getValue());
            } else {
                return;
            }
        } catch (NullPointerException e) {
            value = "";
        }
        // Get summary text
        String oldSummary = mOriginalSumarries.get(pref.getKey());
        // Replace summary text with value according to some rules
        if (oldSummary.equals("")) {
            pref.setSummary(value);
        } else if (value.equals("")) {
            pref.setSummary(oldSummary);
        } else if (pref.getKey().equals("minimumCardsDueForNotification")) {
            pref.setSummary(replaceStringIfNumeric(oldSummary, value));
        } else {
            pref.setSummary(replaceString(oldSummary, value));
        }
    }

    private void updateNotificationPreference() {
        ListPreference listpref = (ListPreference) getPreferenceScreen().findPreference("minimumCardsDueForNotification");
        if (listpref != null) {
            CharSequence[] entries = listpref.getEntries();
            CharSequence[] values = listpref.getEntryValues();
            for (int i = 0; i < entries.length; i++) {
                int value = Integer.parseInt(values[i].toString());
                if (entries[i].toString().contains("%d")) {
                    entries[i] = String.format(entries[i].toString(), value);
                }
            }
            listpref.setEntries(entries);
            listpref.setSummary(listpref.getEntry().toString());
        }
    }


    private String replaceString(String str, String value) {
        if (str.contains("XXX")) {
            return str.replace("XXX", value);
        } else {
            return str;
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private String replaceStringIfNumeric(String str, String value) {
        try {
            Double.parseDouble(value);
            return replaceString(str, value);
        } catch (NumberFormatException e){
            return value;
        }
    }

    private void initializeLanguageDialog() {
        ListPreference languageSelection = (ListPreference) getPreferenceScreen().findPreference(LANGUAGE);
        if (languageSelection != null) {
            Map<String, String> items = new TreeMap<>();
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

            languageSelection.setEntries(languageDialogLabels);
            languageSelection.setEntryValues(languageDialogValues);
        }
    }

    private void removeUnnecessaryWorkarounds() {
        PreferenceCategory workarounds = (PreferenceCategory) getPreferenceScreen().findPreference("category_workarounds");
        if (workarounds != null) {
            CheckBoxPreference inputWorkaround = (CheckBoxPreference) getPreferenceScreen().findPreference("inputWorkaround");
            CheckBoxPreference longclickWorkaround = (CheckBoxPreference) getPreferenceScreen().findPreference("textSelectionLongclickWorkaround");
            CheckBoxPreference fixHebrewText = (CheckBoxPreference) getPreferenceScreen().findPreference("fixHebrewText");
            CheckBoxPreference fixArabicText = (CheckBoxPreference) getPreferenceScreen().findPreference("fixArabicText");
            CheckBoxPreference safeDisplayMode = (CheckBoxPreference) getPreferenceScreen().findPreference("safeDisplay");
            CompatHelper.removeHiddenPreferences(this.getApplicationContext());
            if (CompatHelper.getSdkVersion() >= 9) {
                workarounds.removePreference(fixArabicText);
            }
            if (CompatHelper.isHoneycomb()) {
                workarounds.removePreference(longclickWorkaround);
            }
            if (CompatHelper.getSdkVersion() >= 13) {
                workarounds.removePreference(safeDisplayMode);
            }
            if (CompatHelper.getSdkVersion() >= 15) {
                workarounds.removePreference(inputWorkaround);
            }
            if (CompatHelper.getSdkVersion() >= 16) {
                workarounds.removePreference(fixHebrewText);
                getPreferenceScreen().removePreference(workarounds);     // group itself can be hidden for API 16
            }
        }
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
        // Loop over all preferences


        // syncAccount's summary can change while preferences are still open (user logs
        // in from preferences screen), so we need to update it here.
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        String username = preferences.getString("username", "");
        Preference syncAccount = getPreferenceScreen().findPreference("syncAccount");
        if (syncAccount != null) {
            if (TextUtils.isEmpty(username)) {
                syncAccount.setSummary(R.string.sync_account_summ_logged_out);
            } else {
                syncAccount.setSummary(getString(R.string.sync_account_summ_logged_in, username));
            }
        }
        updateNotificationPreference();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        try {
            Preference pref = getPreferenceScreen().findPreference(key);

            switch (key) {
                case "timeoutAnswer":
                    CheckBoxPreference keepScreenOn = (CheckBoxPreference) getPreferenceScreen().findPreference("keepScreenOn");
                    keepScreenOn.setChecked(((CheckBoxPreference) pref).isChecked());
                    break;
                case LANGUAGE:
                    closePreferences();
                    break;
                case "useBackup":
                    if (lockCheckAction) {
                        lockCheckAction = false;
                    } else if (!((CheckBoxPreference) pref).isChecked()) {
                        lockCheckAction = true;
                        ((CheckBoxPreference) pref).setChecked(true);
                        showDialog(DIALOG_BACKUP);
                    }
                    break;
                case "convertFenText":
                    if (((CheckBoxPreference) pref).isChecked()) {
                        ChessFilter.install(Hooks.getInstance(getApplicationContext()));
                    } else {
                        ChessFilter.uninstall(Hooks.getInstance(getApplicationContext()));
                    }
                    break;
                case "fixHebrewText":
                    if (((CheckBoxPreference) pref).isChecked()) {
                        HebrewFixFilter.install(Hooks.getInstance(getApplicationContext()));
                        showDialog(DIALOG_HEBREW_FONT);
                    } else {
                        HebrewFixFilter.uninstall(Hooks.getInstance(getApplicationContext()));
                    }
                    break;
                case "showProgress":
                    mCol.getConf().put("dueCounts", ((CheckBoxPreference) pref).isChecked());
                    mCol.setMod();
                    break;
                case "showEstimates":
                    mCol.getConf().put("estTimes", ((CheckBoxPreference) pref).isChecked());
                    mCol.setMod();
                    break;
                case "newSpread":
                    mCol.getConf().put("newSpread", Integer.parseInt(((ListPreference) pref).getValue()));
                    mCol.setMod();
                    break;
                case "timeLimit":
                    mCol.getConf().put("timeLim", ((NumberRangePreference) pref).getValue() * 60);
                    mCol.setMod();
                    break;
                case "learnCutoff":
                    mCol.getConf().put("collapseTime", ((NumberRangePreference) pref).getValue() * 60);
                    mCol.setMod();
                    break;
                case "useCurrent":
                    mCol.getConf().put("addToCur", ((ListPreference) pref).getValue().equals("0"));
                    mCol.setMod();
                    break;
                case "dayOffset":
                    int hours = ((SeekBarPreference) pref).getValue();
                    Calendar date = new GregorianCalendar();
                    date.set(Calendar.HOUR_OF_DAY, hours);
                    mCol.setCrt(date.getTimeInMillis() / 1000);
                    mCol.setMod();
                    break;
                case "minimumCardsDueForNotification":
                    updateNotificationPreference();
                    break;
                case "reportErrorMode":
                    String value = sharedPreferences.getString("reportErrorMode", "");
                    AnkiDroidApp.getInstance().setAcraReportingMode(value);
                    break;
            }
            // Update the summary text to reflect new value
            updateSummary(findPreference(key));
        } catch (BadTokenException e) {
            Timber.e(e, "Preferences: BadTokenException on showDialog");
        } catch (NumberFormatException | JSONException e) {
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
        Timber.d("There are %d custom fonts", count);
        String[] names = new String[count + 1];
        names[0] = defaultValue;
        if (useFullPath) {
            for (int index = 1; index < count + 1; ++index) {
                names[index] = mFonts.get(index - 1).getPath();
                Timber.d("Adding custom font: %s", names[index]);
            }
        } else {
            for (int index = 1; index < count + 1; ++index) {
                names[index] = mFonts.get(index - 1).getName();
                Timber.d("Adding custom font: %s", names[index]);
            }
        }
        return names;
    }


    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Timber.i("Preferences:: onBackPressed()");
            closePreferences();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    private void closePreferences() {
        finish();
        ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.FADE);
        if (mCol != null && mCol.getDb()!= null) {
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
                        CheckBoxPreference useBackup = (CheckBoxPreference) getPreferenceScreen().findPreference("useBackup");
                        useBackup.setChecked(false);
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
                        CollectionHelper.getCurrentAnkiDroidDirectory(this)));
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
            mProgressDialog = StyledProgressDialog.show(Preferences.this, "", dialogMessage, false);
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
