/***************************************************************************************
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.WindowManager.BadTokenException;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.anki.exception.StorageAccessException;
import com.ichi2.anki.services.BootService;
import com.ichi2.anki.services.NotificationService;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.hooks.AdvancedStatistics;
import com.ichi2.libanki.hooks.ChessFilter;
import com.ichi2.libanki.hooks.HebrewFixFilter;
import com.ichi2.libanki.hooks.Hooks;
import com.ichi2.preferences.NumberRangePreference;
import com.ichi2.themes.Themes;
import com.ichi2.ui.AppCompatPreferenceActivity;
import com.ichi2.ui.ConfirmationPreference;
import com.ichi2.ui.SeekBarPreference;
import com.ichi2.utils.LanguageUtil;
import com.ichi2.anki.analytics.UsageAnalytics;
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

interface PreferenceContext {
    void addPreferencesFromResource(int preferencesResId);
    PreferenceScreen getPreferenceScreen();
}

/**
 * Preferences dialog.
 */
public class Preferences extends AppCompatPreferenceActivity implements PreferenceContext, OnSharedPreferenceChangeListener {

    private static final int DIALOG_HEBREW_FONT = 3;

    /** Key of the language preference */
    public static final String LANGUAGE = "language";

    // Other variables
    private final HashMap<String, String> mOriginalSumarries = new HashMap<>();
    private static final String [] sCollectionPreferences = {"showEstimates", "showProgress",
            "learnCutoff", "timeLimit", "useCurrent", "newSpread", "dayOffset", "schedVer"};


    // ----------------------------------------------------------------------------
    // Overridden methods
    // ----------------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Themes.setThemeLegacy(this);
        super.onCreate(savedInstanceState);

        // Add a home button to the actionbar
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private Collection getCol() {
        return CollectionHelper.getInstance().getCol(this);
    }


    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.preference_headers, target);
    }


    @Override
    protected boolean isValidFragment(String fragmentName) {
        return SettingsFragment.class.getName().equals(fragmentName);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return false;
    }

    @Override
    @SuppressWarnings("deprecation") // Tracked as #5019 on github - convert to fragments
    protected MaterialDialog onCreateDialog(int id) {
        Resources res = getResources();
        MaterialDialog.Builder builder = new MaterialDialog.Builder(this);
        switch (id) {
            case DIALOG_HEBREW_FONT:
                builder.title(res.getString(R.string.fix_hebrew_text));
                builder.content(res.getString(R.string.fix_hebrew_instructions,
                        CollectionHelper.getCurrentAnkiDroidDirectory(this)));
                builder.onPositive((dialog, which) -> {
                        Intent intent = new Intent("android.intent.action.VIEW", Uri.parse(getResources().getString(
                                R.string.link_hebrew_font)));
                        startActivity(intent);
                    });
                builder.positiveText(res.getString(R.string.fix_hebrew_download_font));
                builder.negativeText(R.string.dialog_cancel);
        }
        return builder.show();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updatePreference(sharedPreferences, key, this);
    }


    // ----------------------------------------------------------------------------
    // Class methods
    // ----------------------------------------------------------------------------

    public static Intent getPreferenceSubscreenIntent(Context context, String subscreen) {
        Intent i = new Intent(context, Preferences.class);
        i.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT, "com.ichi2.anki.Preferences$SettingsFragment");
        Bundle extras = new Bundle();
        extras.putString("subscreen", subscreen);
        i.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS, extras);
        i.putExtra(PreferenceActivity.EXTRA_NO_HEADERS, true);
        return i;
    }

    private void initSubscreen(String action, PreferenceContext listener) {
        PreferenceScreen screen;
        switch (action) {
            case "com.ichi2.anki.prefs.general":
                listener.addPreferencesFromResource(R.xml.preferences_general);
                screen = listener.getPreferenceScreen();
                // Build languages
                initializeLanguageDialog(screen);
                break;
            case "com.ichi2.anki.prefs.reviewing":
                listener.addPreferencesFromResource(R.xml.preferences_reviewing);
                screen = listener.getPreferenceScreen();
                // Show error toast if the user tries to disable answer button without gestures on
                ListPreference fullscreenPreference = (ListPreference)
                        screen.findPreference("fullscreenMode");
                fullscreenPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(Preferences.this);
                    if (prefs.getBoolean("gestures", false) || !newValue.equals("2")) {
                        return true;
                    } else {
                        Toast.makeText(getApplicationContext(),
                                R.string.full_screen_error_gestures, Toast.LENGTH_LONG).show();
                        return false;
                    }
                });
                // Custom buttons options
                Preference customButtonsPreference = screen.findPreference("custom_buttons_link");
                customButtonsPreference.setOnPreferenceClickListener(preference -> {
                    Intent i = getPreferenceSubscreenIntent(Preferences.this,
                            "com.ichi2.anki.prefs.custom_buttons");
                    startActivity(i);
                    return true;
                });
                break;
            case "com.ichi2.anki.prefs.appearance":
                listener.addPreferencesFromResource(R.xml.preferences_appearance);
                screen = listener.getPreferenceScreen();
                initializeCustomFontsDialog(screen);
                break;
            case "com.ichi2.anki.prefs.gestures":
                listener.addPreferencesFromResource(R.xml.preferences_gestures);
                break;
            case "com.ichi2.anki.prefs.custom_buttons":
                getSupportActionBar().setTitle(R.string.custom_buttons);
                listener.addPreferencesFromResource(R.xml.preferences_custom_buttons);
                screen = listener.getPreferenceScreen();
                // Reset toolbar button customizations
                Preference reset_custom_buttons = screen.findPreference("reset_custom_buttons");
                reset_custom_buttons.setOnPreferenceClickListener(preference -> {
                    SharedPreferences.Editor edit = AnkiDroidApp.getSharedPrefs(getBaseContext()).edit();
                    edit.remove("customButtonUndo");
                    edit.remove("customButtonScheduleCard");
                    edit.remove("customButtonMarkCard");
                    edit.remove("customButtonEditCard");
                    edit.remove("customButtonAddCard");
                    edit.remove("customButtonReplay");
                    edit.remove("customButtonSelectTts");
                    edit.remove("customButtonDeckOptions");
                    edit.remove("customButtonBury");
                    edit.remove("customButtonSuspend");
                    edit.remove("customButtonDelete");
                    edit.remove("customButtonClearWhiteboard");
                    edit.remove("customButtonShowHideWhiteboard");
                    edit.apply();
                    //finish();
                    //TODO: Should reload the preferences screen on completion
                    return true;
                });
                break;
            case "com.ichi2.anki.prefs.advanced":
                listener.addPreferencesFromResource(R.xml.preferences_advanced);
                screen = listener.getPreferenceScreen();
                // Check that input is valid before committing change in the collection path
                EditTextPreference collectionPathPreference = (EditTextPreference) screen.findPreference("deckPath");
                collectionPathPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    final String newPath = (String) newValue;
                    try {
                        CollectionHelper.initializeAnkiDroidDirectory(newPath);
                        return true;
                    } catch (StorageAccessException e) {
                        Timber.e(e, "Could not initialize directory: %s", newPath);
                        Toast.makeText(getApplicationContext(), R.string.dialog_collection_path_not_dir, Toast.LENGTH_LONG).show();
                        return false;
                    }
                });
                // Custom sync server option
                Preference customSyncServerPreference = screen.findPreference("custom_sync_server_link");
                customSyncServerPreference.setOnPreferenceClickListener(preference -> {
                    Intent i = getPreferenceSubscreenIntent(Preferences.this,
                            "com.ichi2.anki.prefs.custom_sync_server");
                    startActivity(i);
                    return true;
                });
                // Advanced statistics option
                Preference advancedStatisticsPreference = screen.findPreference("advanced_statistics_link");
                advancedStatisticsPreference.setOnPreferenceClickListener(preference -> {
                    Intent i = getPreferenceSubscreenIntent(Preferences.this,
                            "com.ichi2.anki.prefs.advanced_statistics");
                    startActivity(i);
                    return true;
                });
                // Make it possible to test crash reporting, but only for DEBUG builds
                if (BuildConfig.DEBUG) {
                    Timber.i("Debug mode, allowing for test crashes");
                    Preference triggerTestCrashPreference = new Preference(this);
                    triggerTestCrashPreference.setKey("trigger_crash_preference");
                    triggerTestCrashPreference.setTitle("Trigger test crash");
                    triggerTestCrashPreference.setSummary("Touch here for an immediate test crash");
                    triggerTestCrashPreference.setOnPreferenceClickListener(preference -> {
                        Timber.w("Crash triggered on purpose from advanced preferences in debug mode");
                        throw new RuntimeException("This is a test crash");
                    });
                    screen.addPreference(triggerTestCrashPreference);
                }
                // Make it possible to test analytics, but only for DEBUG builds
                if (BuildConfig.DEBUG) {
                    Timber.i("Debug mode, allowing for dynamic analytics config");
                    Preference analyticsDebugMode = new Preference(this);
                    analyticsDebugMode.setKey("analytics_debug_preference");
                    analyticsDebugMode.setTitle("Switch Analytics to dev mode");
                    analyticsDebugMode.setSummary("Touch here to use Analytics dev tag and 100% sample rate");
                    analyticsDebugMode.setOnPreferenceClickListener(preference -> {
                        UsageAnalytics.setDevMode();
                        return true;
                    });
                    screen.addPreference(analyticsDebugMode);
                }
                // Force full sync option
                ConfirmationPreference fullSyncPreference = (ConfirmationPreference)screen.findPreference("force_full_sync");
                fullSyncPreference.setDialogMessage(R.string.force_full_sync_summary);
                fullSyncPreference.setDialogTitle(R.string.force_full_sync_title);
                fullSyncPreference.setOkHandler(() -> {
                    if (getCol() == null) {
                        Toast.makeText(getApplicationContext(), R.string.directory_inaccessible, Toast.LENGTH_LONG).show();
                        return;
                    }
                    getCol().modSchemaNoCheck();
                    getCol().setMod();
                    Toast.makeText(getApplicationContext(), android.R.string.ok, Toast.LENGTH_SHORT).show();
                });
                // Workaround preferences
                removeUnnecessaryAdvancedPrefs(screen);
                break;
            case "com.ichi2.anki.prefs.custom_sync_server":
                getSupportActionBar().setTitle(R.string.custom_sync_server_title);
                listener.addPreferencesFromResource(R.xml.preferences_custom_sync_server);
                break;
            case "com.ichi2.anki.prefs.advanced_statistics":
                getSupportActionBar().setTitle(R.string.advanced_statistics_title);
                listener.addPreferencesFromResource(R.xml.preferences_advanced_statistics);
                break;
        }
    }


    /**
     * Loop over every preference in the list and set the summary text
     */
    private void initAllPreferences(PreferenceScreen screen) {
        for (int i = 0; i < screen.getPreferenceCount(); ++i) {
            Preference preference = screen.getPreference(i);
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
            Collection col = getCol();
            if (col != null) {
                try {
                    JSONObject conf = col.getConf();
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
                            Timestamp timestamp = new Timestamp(col.getCrt() * 1000);
                            calendar.setTimeInMillis(timestamp.getTime());
                            ((SeekBarPreference)pref).setValue(calendar.get(Calendar.HOUR_OF_DAY));
                            break;
                        case "schedVer":
                            ((CheckBoxPreference)pref).setChecked(conf.optInt("schedVer", 1) == 2);
                    }
                } catch (JSONException | NumberFormatException e) {
                    throw new RuntimeException();
                }
            } else {
                // Disable Col preferences if Collection closed
                pref.setEnabled(false);
            }
        } else if ("minimumCardsDueForNotification".equals(pref.getKey())) {
            updateNotificationPreference((ListPreference) pref);
        }
        // Set the value from the summary cache
        CharSequence s = pref.getSummary();
        mOriginalSumarries.put(pref.getKey(), (s != null) ? s.toString() : "");
        // Update summary
        updateSummary(pref);
    }


    /**
     * Code which is run when a SharedPreference change has been detected
     * @param prefs instance of SharedPreferences
     * @param key key in prefs which is being updated
     * @param listener PreferenceActivity of PreferenceFragment which is hosting the preference
     */
    @SuppressWarnings("deprecation") // Tracked as #5019 on github - convert to fragments
    private void updatePreference(SharedPreferences prefs, String key, PreferenceContext listener) {
        try {
            PreferenceScreen screen = listener.getPreferenceScreen();
            Preference pref = screen.findPreference(key);
            if (pref == null) {
                Timber.e("Preferences: no preference found for the key: %s", key);
                return;
            }
            // Handle special cases
            switch (key) {
                case "timeoutAnswer": {
                    CheckBoxPreference keepScreenOn = (CheckBoxPreference) screen.findPreference("keepScreenOn");
                    keepScreenOn.setChecked(((CheckBoxPreference) pref).isChecked());
                    break;
                }
                case LANGUAGE:
                    closePreferences();
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
                case "advanced_statistics_enabled":
                    if (((CheckBoxPreference) pref).isChecked()) {
                        AdvancedStatistics.install(Hooks.getInstance(getApplicationContext()));
                    } else {
                        AdvancedStatistics.uninstall(Hooks.getInstance(getApplicationContext()));
                    }
                    break;
                case "showProgress":
                    getCol().getConf().put("dueCounts", ((CheckBoxPreference) pref).isChecked());
                    getCol().setMod();
                    break;
                case "showEstimates":
                    getCol().getConf().put("estTimes", ((CheckBoxPreference) pref).isChecked());
                    getCol().setMod();
                    break;
                case "newSpread":
                    getCol().getConf().put("newSpread", Integer.parseInt(((ListPreference) pref).getValue()));
                    getCol().setMod();
                    break;
                case "timeLimit":
                    getCol().getConf().put("timeLim", ((NumberRangePreference) pref).getValue() * 60);
                    getCol().setMod();
                    break;
                case "learnCutoff":
                    getCol().getConf().put("collapseTime", ((NumberRangePreference) pref).getValue() * 60);
                    getCol().setMod();
                    break;
                case "useCurrent":
                    getCol().getConf().put("addToCur", ((ListPreference) pref).getValue().equals("0"));
                    getCol().setMod();
                    break;
                case "dayOffset": {
                    int hours = ((SeekBarPreference) pref).getValue();
                    Timestamp crtTime = new Timestamp(getCol().getCrt() * 1000);
                    Calendar date = GregorianCalendar.getInstance();
                    date.setTimeInMillis(crtTime.getTime());
                    date.set(Calendar.HOUR_OF_DAY, hours);
                    getCol().setCrt(date.getTimeInMillis() / 1000);
                    getCol().setMod();
                    BootService.scheduleNotification(this);
                    break;
                }
                case "minimumCardsDueForNotification": {
                    ListPreference listpref = (ListPreference) screen.findPreference("minimumCardsDueForNotification");
                    if (listpref != null) {
                        updateNotificationPreference(listpref);
                        if (Integer.valueOf(listpref.getValue()) < 1000000) {
                            BootService.scheduleNotification(this);
                        } else {
                            PendingIntent intent = PendingIntent.getBroadcast(this, 0,
                                    new Intent(this, NotificationService.class), 0);
                            final AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
                            alarmManager.cancel(intent);
                        }
                    }
                    break;
                }
                case AnkiDroidApp.FEEDBACK_REPORT_KEY: {
                    String value = prefs.getString(AnkiDroidApp.FEEDBACK_REPORT_KEY, "");
                    AnkiDroidApp.getInstance().setAcraReportingMode(value);
                    // If the user changed error reporting, make sure future reports have a chance to post
                    AnkiDroidApp.deleteACRALimiterData(this);
                    // We also need to re-chain our UncaughtExceptionHandlers
                    UsageAnalytics.reInitialize();
                    break;
                }
                case "syncAccount": {
                    SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
                    String username = preferences.getString("username", "");
                    Preference syncAccount = screen.findPreference("syncAccount");
                    if (syncAccount != null) {
                        if (TextUtils.isEmpty(username)) {
                            syncAccount.setSummary(R.string.sync_account_summ_logged_out);
                        } else {
                            syncAccount.setSummary(getString(R.string.sync_account_summ_logged_in, username));
                        }
                    }
                    break;
                }
                case "providerEnabled": {
                    ComponentName providerName = new ComponentName(this, "com.ichi2.anki.provider.CardContentProvider");
                    PackageManager pm = getPackageManager();
                    int state;
                    if (((CheckBoxPreference) pref).isChecked()) {
                         state = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
                        Timber.i("AnkiDroid ContentProvider enabled by user");
                    } else {
                        state = PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                        Timber.i("AnkiDroid ContentProvider disabled by user");
                    }
                    pm.setComponentEnabledSetting(providerName, state, PackageManager.DONT_KILL_APP);
                    break;
                }
                case "schedVer": {
                    boolean wantNew = ((CheckBoxPreference) pref).isChecked();
                    boolean haveNew = getCol().schedVer() == 2;
                    // northing to do?
                    if (haveNew == wantNew) {
                        break;
                    }
                    MaterialDialog.Builder builder = new MaterialDialog.Builder(this);
                    if (haveNew && !wantNew) {
                        // Going back to V1
                        builder.title(R.string.sched_ver_toggle_title);
                        builder.content(R.string.sched_ver_2to1);
                        builder.onPositive((dialog, which) -> {
                            getCol().modSchemaNoCheck();
                            try {
                                getCol().changeSchedulerVer(1);
                                ((CheckBoxPreference) pref).setChecked(false);
                            } catch (ConfirmModSchemaException e2) {
                                // This should never be reached as we explicitly called modSchemaNoCheck()
                                throw new RuntimeException(e2);
                            }
                        });
                        builder.onNegative((dialog, which) -> {
                            ((CheckBoxPreference) pref).setChecked(true);
                        });
                        builder.positiveText(R.string.dialog_ok);
                        builder.negativeText(R.string.dialog_cancel);
                        builder.show();
                        break;
                    }
                    // Going to V2
                    builder.title(R.string.sched_ver_toggle_title);
                    builder.content(R.string.sched_ver_1to2);
                    builder.onPositive((dialog, which) -> {
                        getCol().modSchemaNoCheck();
                        try {
                            getCol().changeSchedulerVer(2);
                            ((CheckBoxPreference) pref).setChecked(true);
                        } catch (ConfirmModSchemaException e2) {
                            // This should never be reached as we explicitly called modSchemaNoCheck()
                            throw new RuntimeException(e2);
                        }
                    });
                    builder.onNegative((dialog, which) -> {
                        ((CheckBoxPreference) pref).setChecked(false);
                    });
                    builder.positiveText(R.string.dialog_ok);
                    builder.negativeText(R.string.dialog_cancel);
                    builder.show();
                    break;
                }
            }
            // Update the summary text to reflect new value
            updateSummary(pref);
        } catch (BadTokenException e) {
            Timber.e(e, "Preferences: BadTokenException on showDialog");
        } catch (NumberFormatException | JSONException e) {
            throw new RuntimeException();
        }
    }

    public void updateNotificationPreference(ListPreference listpref) {
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

    private void updateSummary(Preference pref) {
        if (pref == null || pref.getKey() == null) {
            return;
        }
        // Handle special cases
        switch (pref.getKey()) {
            case "about_dialog_preference":
                pref.setSummary(getResources().getString(R.string.about_version) + " " + VersionUtils.getPkgVersionName());
                break;
            case "custom_sync_server_link":
                if (!AnkiDroidApp.getSharedPrefs(this).getBoolean("useCustomSyncServer", false)) {
                    pref.setSummary(R.string.disabled);
                } else {
                    pref.setSummary(AnkiDroidApp.getSharedPrefs(this).getString("syncBaseUrl", ""));
                }
                break;
            case "advanced_statistics_link":
                if (!AnkiDroidApp.getSharedPrefs(this).getBoolean("advanced_statistics_enabled", false)) {
                    pref.setSummary(R.string.disabled);
                } else {
                    pref.setSummary(R.string.enabled);
                }
                break;
        }
        // Get value text
        String value;
        try {
            if (pref instanceof  NumberRangePreference) {
                value = Integer.toString(((NumberRangePreference) pref).getValue());
            } else if (pref instanceof SeekBarPreference) {
                value = Integer.toString(((SeekBarPreference) pref).getValue());
            } else if (pref instanceof ListPreference) {
                value = ((ListPreference) pref).getEntry().toString();
            } else if (pref instanceof EditTextPreference) {
                value = ((EditTextPreference) pref).getText();
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

    private void initializeLanguageDialog(PreferenceScreen screen) {
        ListPreference languageSelection = (ListPreference) screen.findPreference(LANGUAGE);
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

    private void removeUnnecessaryAdvancedPrefs(PreferenceScreen screen) {
        PreferenceCategory plugins = (PreferenceCategory) screen.findPreference("category_plugins");
        // Disable the emoji/kana buttons to scroll preference if those keys don't exist
        if (!CompatHelper.hasKanaAndEmojiKeys()) {
            CheckBoxPreference emojiScrolling = (CheckBoxPreference) screen.findPreference("scrolling_buttons");
            if (emojiScrolling != null && plugins != null) {
                plugins.removePreference(emojiScrolling);
            }
        }
        // Disable the double scroll preference if no scrolling keys
        if (!CompatHelper.hasScrollKeys() && !CompatHelper.hasKanaAndEmojiKeys()) {
            CheckBoxPreference doubleScrolling = (CheckBoxPreference) screen.findPreference("double_scrolling");
            if (doubleScrolling != null && plugins != null) {
                plugins.removePreference(doubleScrolling);
            }
        }

        PreferenceCategory workarounds = (PreferenceCategory) screen.findPreference("category_workarounds");
        if (workarounds != null) {
            CheckBoxPreference fixHebrewText = (CheckBoxPreference) screen.findPreference("fixHebrewText");
            CompatHelper.removeHiddenPreferences(this.getApplicationContext());

            if (CompatHelper.getSdkVersion() >= 16) {
                workarounds.removePreference(fixHebrewText);
            }
        }
    }


    /** Initializes the list of custom fonts shown in the preferences. */
    private void initializeCustomFontsDialog(PreferenceScreen screen) {
        ListPreference defaultFontPreference = (ListPreference) screen.findPreference("defaultFont");
        if (defaultFontPreference != null) {
            defaultFontPreference.setEntries(getCustomFonts("System default"));
            defaultFontPreference.setEntryValues(getCustomFonts(""));
        }
        ListPreference browserEditorCustomFontsPreference = (ListPreference) screen.findPreference("browserEditorFont");
        browserEditorCustomFontsPreference.setEntries(getCustomFonts("System default"));
        browserEditorCustomFontsPreference.setEntryValues(getCustomFonts("", true));
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


    private void closePreferences() {
        finish();
        ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.FADE);
        if (getCol() != null && getCol().getDb()!= null) {
            getCol().save();
        }
    }

    // ----------------------------------------------------------------------------
    // Inner classes
    // ----------------------------------------------------------------------------

    @SuppressWarnings("deprecation") // Tracked as #5019 on github
    public static class SettingsFragment extends android.preference.PreferenceFragment implements PreferenceContext, OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            String subscreen = getArguments().getString("subscreen");
            UsageAnalytics.sendAnalyticsScreenView(subscreen.replaceFirst("^com.ichi2.anki.", ""));
            ((Preferences) getActivity()).initSubscreen(subscreen, this);
            ((Preferences) getActivity()).initAllPreferences(getPreferenceScreen());
        }

        @Override
        public void onResume() {
            super.onResume();
            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
            prefs.registerOnSharedPreferenceChangeListener(this);
            // syncAccount's summary can change while preferences are still open (user logs
            // in from preferences screen), so we need to update it here.
            ((Preferences) getActivity()).updatePreference(prefs, "syncAccount", this);
            ((Preferences) getActivity()).updatePreference(prefs, "custom_sync_server_link", this);
            ((Preferences) getActivity()).updatePreference(prefs, "advanced_statistics_link", this);
        }

        @Override
        public void onPause() {
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
            super.onPause();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            ((Preferences) getActivity()).updatePreference(sharedPreferences, key, this);
        }
    }
}
