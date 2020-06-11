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
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.database.Cursor;
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
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.WindowManager.BadTokenException;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.contextmenu.CardBrowserContextMenu;
import com.ichi2.anki.debug.DatabaseLock;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.anki.exception.StorageAccessException;
import com.ichi2.anki.services.BootService;
import com.ichi2.anki.services.NotificationService;
import com.ichi2.anki.web.CustomSyncServer;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.hooks.ChessFilter;
import com.ichi2.preferences.NumberRangePreference;
import com.ichi2.themes.Themes;
import com.ichi2.ui.AppCompatPreferenceActivity;
import com.ichi2.ui.ConfirmationPreference;
import com.ichi2.ui.SeekBarPreference;
import com.ichi2.utils.AdaptionUtil;
import com.ichi2.utils.LanguageUtil;
import com.ichi2.anki.analytics.UsageAnalytics;
import com.ichi2.utils.VersionUtils;

import com.ichi2.utils.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
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

    /** Key of the language preference */
    public static final String LANGUAGE = "language";

    /* Only enable AnkiDroid notifications unrelated to due reminders */
    public static final int PENDING_NOTIFICATIONS_ONLY = 1000000;

    // Other variables
    private final HashMap<String, String> mOriginalSumarries = new HashMap<>();
    private static final String [] sCollectionPreferences = {"showEstimates", "showProgress",
            "learnCutoff", "timeLimit", "useCurrent", "newSpread", "dayOffset", "schedVer"};

    private static final int RESULT_LOAD_IMG = 111;
    private CheckBoxPreference backgroundImage;
    private static long fileLength;
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
        setTitle(getResources().getText(R.string.preferences_title));
    }

    private Collection getCol() {
        return CollectionHelper.getInstance().getCol(this);
    }


    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.preference_headers, target);
        Iterator iterator = target.iterator();
        while (iterator.hasNext()) {
            Header header = (Header)iterator.next();
            if ((header.titleRes == R.string.pref_cat_advanced) && AdaptionUtil.isRestrictedLearningDevice()){
                iterator.remove();
            }
        }
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
                if (AdaptionUtil.isRestrictedLearningDevice()) {
                    CheckBoxPreference mCheckBoxPref_Vibrate = (CheckBoxPreference) screen.findPreference("widgetVibrate");
                    CheckBoxPreference mCheckBoxPref_Blink = (CheckBoxPreference) screen.findPreference("widgetBlink");
                    PreferenceCategory mCategory = (PreferenceCategory) screen.findPreference("category_general_notification_pref");
                    mCategory.removePreference(mCheckBoxPref_Vibrate);
                    mCategory.removePreference(mCheckBoxPref_Blink);
                }

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
                    if (prefs.getBoolean("gestures", false) || !"2".equals(newValue)) {
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
                backgroundImage = (CheckBoxPreference) screen.findPreference("deckPickerBackground");
                backgroundImage.setOnPreferenceClickListener(preference -> {
                    if (backgroundImage.isChecked()) {
                        try {
                            Intent galleryIntent = new Intent(Intent.ACTION_PICK,android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                            startActivityForResult(galleryIntent, RESULT_LOAD_IMG);
                            backgroundImage.setChecked(true);
                        } catch (Exception ex) {
                            Timber.e("%s",ex.getLocalizedMessage());
                        }
                    } else {
                        backgroundImage.setChecked(false);
                        String currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(this);
                        File imgFile = new File(currentAnkiDroidDirectory, "DeckPickerBackground.png" );
                        if (imgFile.exists()) {
                            if (imgFile.delete()) {
                                UIUtils.showThemedToast(this, getString(R.string.background_image_removed), false);
                            } else {
                                UIUtils.showThemedToast(this, getString(R.string.error_deleting_image), false);
                            }
                        } else {
                            UIUtils.showThemedToast(this, getString(R.string.background_image_removed), false);
                        }
                    }
                    return true;
                });
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
                    edit.remove("customButtonFlag");
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
                // FIXME: The menu is named in the system language (as it's defined in the manifest which may be
                //  different than the app language
                CheckBoxPreference cardBrowserContextMenuPreference = (CheckBoxPreference) screen.findPreference(CardBrowserContextMenu.CARD_BROWSER_CONTEXT_MENU_PREF_KEY);
                String menuName = getString(R.string.card_browser_context_menu);
                cardBrowserContextMenuPreference.setTitle(getString(R.string.card_browser_enable_external_context_menu, menuName));
                cardBrowserContextMenuPreference.setSummary(getString(R.string.card_browser_enable_external_context_menu_summary, menuName));

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
                if (BuildConfig.DEBUG) {
                    Timber.i("Debug mode, allowing database lock preference");
                    Preference lockDbPreference = new Preference(this);
                    lockDbPreference.setKey("debug_lock_database");
                    lockDbPreference.setTitle("Lock Database");
                    lockDbPreference.setSummary("Touch here to lock the database (all threads block in-process, exception if using second process)");
                    lockDbPreference.setOnPreferenceClickListener(preference -> {
                        DatabaseLock.engage(this);
                        return true;
                    });
                    screen.addPreference(lockDbPreference);
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
                addThirdPartyAppsListener(screen);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // DEFECT #5973: Does not handle Google Drive downloads
        try {
            if (requestCode == RESULT_LOAD_IMG && resultCode == RESULT_OK && null != data) {
                Cursor cursor = null;
                Uri selectedImage = data.getData();
                String[] filePathColumn = { MediaStore.Images.Media.DATA };
                try {
                    cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
                    cursor.moveToFirst();
                    int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                    String imgPathString = cursor.getString(columnIndex);
                    File sourceFile = new File(imgPathString);

                    // file size in MB
                    fileLength = sourceFile.length() / (1024 * 1024);

                    String currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(this);
                    String imageName = "DeckPickerBackground.png";
                    File destFile = new File(currentAnkiDroidDirectory, imageName);
                    // Image size less than 10 MB copied to AnkiDroid folder
                    if (fileLength < 10) {
                        try (FileChannel sourceChannel = new FileInputStream(sourceFile).getChannel();
                             FileChannel destChannel = new FileOutputStream(destFile).getChannel()) {
                            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
                            UIUtils.showThemedToast(this, getString(R.string.background_image_applied), false);
                        }
                    } else {
                        backgroundImage.setChecked(false);
                        UIUtils.showThemedToast(this, getString(R.string.image_max_size_allowed, 10), false);
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            } else {
                backgroundImage.setChecked(false);
                UIUtils.showThemedToast(this, getString(R.string.no_image_selected), false);
            }
        } catch (OutOfMemoryError | Exception e) {
            UIUtils.showThemedToast(this, getString(R.string.error_selecting_image, e.getLocalizedMessage()), false);
        }
    }

    private void addThirdPartyAppsListener(PreferenceScreen screen) {
        //#5864 - some people don't have a browser so we can't use <intent>
        //and need to handle the keypress ourself.
        Preference showThirdParty = screen.findPreference("thirdpartyapps_link");
        final String githubThirdPartyAppsUrl = "https://github.com/ankidroid/Anki-Android/wiki/Third-Party-Apps";
        showThirdParty.setOnPreferenceClickListener((preference) -> {
            try {
                Intent openThirdPartyAppsIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(githubThirdPartyAppsUrl));
                super.startActivity(openThirdPartyAppsIntent);
            } catch (ActivityNotFoundException e) {
                //We use a different message here. We have limited space in the snackbar
                String error = getString(R.string.activity_start_failed_load_url, githubThirdPartyAppsUrl);
                UIUtils.showSimpleSnackbar(this, error, false);
            }
            return true;
        });
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
                } catch (NumberFormatException e) {
                    throw new RuntimeException(e);
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
                case CustomSyncServer.PREFERENCE_CUSTOM_MEDIA_SYNC_URL:
                case CustomSyncServer.PREFERENCE_CUSTOM_SYNC_BASE:
                case CustomSyncServer.PREFERENCE_ENABLE_CUSTOM_SYNC_SERVER:
                    //This may be a tad hasty - performed before "back" is pressed.
                    CustomSyncServer.handleSyncServerPreferenceChange(getBaseContext());
                    break;
                case "timeoutAnswer": {
                    CheckBoxPreference keepScreenOn = (CheckBoxPreference) screen.findPreference("keepScreenOn");
                    keepScreenOn.setChecked(((CheckBoxPreference) pref).isChecked());
                    break;
                }
                case LANGUAGE:
                    closePreferences();
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
                    getCol().getConf().put("addToCur", "0".equals(((ListPreference) pref).getValue()));
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
                        if (Integer.valueOf(listpref.getValue()) < PENDING_NOTIFICATIONS_ONLY) {
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
                case CardBrowserContextMenu.CARD_BROWSER_CONTEXT_MENU_PREF_KEY:
                    CardBrowserContextMenu.ensureConsistentStateWithSharedPreferences(this);
            }
            // Update the summary text to reflect new value
            updateSummary(pref);
        } catch (BadTokenException e) {
            Timber.e(e, "Preferences: BadTokenException on showDialog");
        } catch (NumberFormatException e) {
            throw new RuntimeException(e);
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
                SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(this);
                if (!CustomSyncServer.isEnabled(preferences)) {
                    pref.setSummary(R.string.disabled);
                } else {
                    pref.setSummary(CustomSyncServer.getSyncBaseUrlOrDefault(preferences, ""));
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
        if ("".equals(oldSummary)) {
            pref.setSummary(value);
        } else if ("".equals(value)) {
            pref.setSummary(oldSummary);
        } else if ("minimumCardsDueForNotification".equals(pref.getKey())) {
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
            Map<String, String> items = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (String localeCode : LanguageUtil.APP_LANGUAGES) {
                Locale loc = LanguageUtil.getLocale(localeCode);
                items.put(loc.getDisplayName(loc), loc.toString());
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
