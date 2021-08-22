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

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
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
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.WindowManager.BadTokenException;
import android.webkit.URLUtil;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.analytics.Acra;
import com.ichi2.anki.contextmenu.AnkiCardContextMenu;
import com.ichi2.anki.contextmenu.CardBrowserContextMenu;
import com.ichi2.anki.debug.DatabaseLock;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.anki.exception.StorageAccessException;
import com.ichi2.anki.reviewer.FullScreenMode;
import com.ichi2.anki.services.BootService;
import com.ichi2.anki.services.NotificationService;
import com.ichi2.anki.web.CustomSyncServer;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.backend.exception.BackendNotSupportedException;
import com.ichi2.libanki.sched.AbstractSched;
import com.ichi2.preferences.NumberRangePreference;
import com.ichi2.themes.Themes;
import com.ichi2.ui.AppCompatPreferenceActivity;
import com.ichi2.ui.ConfirmationPreference;
import com.ichi2.ui.SeekBarPreference;
import com.ichi2.utils.AdaptionUtil;
import com.ichi2.utils.LanguageUtil;
import com.ichi2.anki.analytics.UsageAnalytics;
import com.ichi2.utils.VersionUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.XmlRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;
import timber.log.Timber;

import static com.ichi2.anim.ActivityTransitionAnimation.Direction.FADE;

/**
 * Preferences dialog.
 */
@SuppressWarnings("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
public class Preferences extends AppCompatPreferenceActivity {

    /** Key of the language preference */
    public static final String LANGUAGE = "language";

    /* Only enable AnkiDroid notifications unrelated to due reminders */
    public static final int PENDING_NOTIFICATIONS_ONLY = 1000000;

    // Other variables
    private final HashMap<String, String> mOriginalSumarries = new HashMap<>();
    /**
     * Represents in Android preferences the collections configuration "estTime": i.e. whether the buttons should indicate the duration of the interval if we click on them.
     */
    private static final String SHOW_ESTIMATE = "showEstimates";
    /**
     * Represents in Android preferences the collections configuration "dueCounts": i.e.
     * whether the remaining number of cards should be shown.
     */
    private static final String SHOW_PROGRESS = "showProgress";
    /**
     * Represents in Android preferences the collections configuration "collapseTime": i.e.
     * if there are no card to review now, but there are learning cards remaining for today, we show those learning cards if they are due before LEARN_CUTOFF minutes
     * Note that "collapseTime" is in second while LEARN_CUTOFF is in minute.
     */
    private static final String LEARN_CUTOFF = "learnCutoff";
    /**
     * Represents in Android preferences the collections configuration "timeLim": i.e.
     * the duration of a review timebox in minute. Each TIME_LIMIT minutes, a message appear suggesting to halt and giving the number of card reviewed
     * Note that "timeLim" is in seconds while TIME_LIMIT is in minutes.
     */
    private static final String TIME_LIMIT = "timeLimit";
    /**
     * Represents in Android preferences the collections configuration "addToCur": i.e.
     * if true, then add note to current decks, otherwise let the note type's configuration decide
     * Note that "addToCur" is a boolean while USE_CURRENT is "0" or "1"
     */
    private static final String USE_CURRENT = "useCurrent";
    /**
     * Represents in Android preferences the collections configuration "newSpread": i.e.
     * whether the new cards are added at the end of the queue or randomly in it.
     * It seems not to be implemented in Ankidroid and only used in anki.
     * TODO: port sched v2 from upstream
     */
    private static final String NEW_SPREAD = "newSpread";
    /**
     * Represents in Android preference the collection's configuration "rollover"
     * in sched v2, and crt in sched v1. I.e. at which time of the day does the scheduler reset
     */
    private static final String DAY_OFFSET = "dayOffset";
    /**
     * Represents in Android preference the collection's configuration "pastePNG" , i.e.
     * whether to convert clipboard uri to png format or not.
     * TODO: convert to png if a image file has transparency, or at least if it supports it.
     */
    private static final String PASTE_PNG = "pastePNG";

    /**
     * The number of cards that should be due today in a deck to justify adding a notification.
     */
    public static final String MINIMUM_CARDS_DUE_FOR_NOTIFICATION = "minimumCardsDueForNotification";
    private static final String NEW_TIMEZONE_HANDLING = "newTimezoneHandling";
    private static final String [] sCollectionPreferences = {SHOW_ESTIMATE, SHOW_PROGRESS,
            LEARN_CUTOFF, TIME_LIMIT, USE_CURRENT, NEW_SPREAD, DAY_OFFSET, NEW_TIMEZONE_HANDLING};

    /** The collection path when Preferences was opened  */
    private String mOldCollectionPath = null;

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

        // onRestoreInstanceState takes priority, this is only set on init.
        mOldCollectionPath = CollectionHelper.getCollectionPath(this);
    }

    private Collection getCol() {
        return CollectionHelper.getInstance().getCol(this);
    }


    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.preference_headers, target);
        Iterator<Header> iterator = target.iterator();
        while (iterator.hasNext()) {
            Header header = iterator.next();
            if ((header.titleRes == R.string.pref_cat_advanced) && AdaptionUtil.isRestrictedLearningDevice()){
                iterator.remove();
            }
        }
    }


    @Override
    protected boolean isValidFragment(String fragmentName) {
        // Fragments are valid if they are inner classes of Preferences.java
        return fragmentName.startsWith("com.ichi2.anki.Preferences$");
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        // If the collection path has changed, we want to move back to the deck picker immediately
        // This performs the move when back is pressed on the "Advanced" screen
        if (!Utils.equals(CollectionHelper.getCollectionPath(this), mOldCollectionPath)) {
            restartWithNewDeckPicker();
        } else {
            super.onBackPressed();
        }
    }


    protected void restartWithNewDeckPicker() {
        // PERF: DB access on foreground thread
        CollectionHelper.getInstance().closeCollection(true, "Preference Modification: collection path changed");
        Intent deckPicker = new Intent(this, DeckPicker.class);
        deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(deckPicker);
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("mOldCollectionPath", mOldCollectionPath);
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        mOldCollectionPath = state.getString("mOldCollectionPath");
    }


    // ----------------------------------------------------------------------------
    // Class methods
    // ----------------------------------------------------------------------------


    /**
     * Loop over every preference in the list and set the summary text
     */
    private void initAllPreferences(android.preference.PreferenceScreen screen) {
        for (int i = 0; i < screen.getPreferenceCount(); ++i) {
            android.preference.Preference preference = screen.getPreference(i);
            if (preference instanceof android.preference.PreferenceGroup) {
                android.preference.PreferenceGroup preferenceGroup = (android.preference.PreferenceGroup) preference;
                for (int j = 0; j < preferenceGroup.getPreferenceCount(); ++j) {
                    android.preference.Preference nestedPreference = preferenceGroup.getPreference(j);
                    if (nestedPreference instanceof android.preference.PreferenceGroup) {
                        android.preference.PreferenceGroup nestedPreferenceGroup = (android.preference.PreferenceGroup) nestedPreference;
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



    private void initPreference(android.preference.Preference pref) {
        // Load stored values from Preferences which are stored in the Collection
        if (Arrays.asList(sCollectionPreferences).contains(pref.getKey())) {
            Collection col = getCol();
            if (col != null) {
                try {
                    switch (pref.getKey()) {
                        case SHOW_ESTIMATE:
                            ((android.preference.CheckBoxPreference)pref).setChecked(col.get_config_boolean("estTimes"));
                            break;
                        case SHOW_PROGRESS:
                            ((android.preference.CheckBoxPreference)pref).setChecked(col.get_config_boolean("dueCounts"));
                            break;
                        case LEARN_CUTOFF:
                            ((NumberRangePreference)pref).setValue(col.get_config_int("collapseTime") / 60);
                            break;
                        case TIME_LIMIT:
                            ((NumberRangePreference)pref).setValue(col.get_config_int("timeLim") / 60);
                            break;
                        case USE_CURRENT:
                            ((android.preference.ListPreference)pref).setValueIndex(col.get_config("addToCur", true) ? 0 : 1);
                            break;
                        case NEW_SPREAD:
                            ((android.preference.ListPreference)pref).setValueIndex(col.get_config_int("newSpread"));
                            break;
                        case DAY_OFFSET:
                            ((SeekBarPreference)pref).setValue(getDayOffset(col));
                            break;
                        case PASTE_PNG:
                            ((android.preference.CheckBoxPreference)pref).setChecked(col.get_config("pastePNG", false));
                            break;
                        case NEW_TIMEZONE_HANDLING:
                            android.preference.CheckBoxPreference checkBox = (android.preference.CheckBoxPreference) pref;
                            checkBox.setChecked(col.getSched()._new_timezone_enabled());
                            if (col.schedVer() <= 1 || !col.isUsingRustBackend()) {
                                Timber.d("Disabled 'newTimezoneHandling' box");
                                checkBox.setEnabled(false);
                            }
                            break;
                    }
                } catch (NumberFormatException e) {
                    throw new RuntimeException(e);
                }
            } else {
                // Disable Col preferences if Collection closed
                pref.setEnabled(false);
            }
        } else if (MINIMUM_CARDS_DUE_FOR_NOTIFICATION.equals(pref.getKey())) {
            updateNotificationPreference((android.preference.ListPreference) pref);
        }
        // Set the value from the summary cache
        CharSequence s = pref.getSummary();
        mOriginalSumarries.put(pref.getKey(), (s != null) ? s.toString() : "");
        // Update summary
        updateSummary(pref);
    }

    /** Returns the hour that the collection rolls over to the next day */
    public static int getDayOffset(Collection col) {
        switch (col.schedVer()) {
            default:
            case 1:
                Calendar calendar = col.crtGregorianCalendar();
                return calendar.get(Calendar.HOUR_OF_DAY);
            case 2:
                return col.get_config("rollover", 4);
        }
    }

    /** Sets the hour that the collection rolls over to the next day */
    @VisibleForTesting
    public void setDayOffset(int hours) {
        switch (getSchedVer(getCol())) {
            default:
            case 1:
                Calendar date = getCol().crtGregorianCalendar();
                date.set(Calendar.HOUR_OF_DAY, hours);
                getCol().setCrt(date.getTimeInMillis() / 1000);
                getCol().setMod();
                break;
            case 2:
                getCol().set_config("rollover", hours);
                getCol().flush();
                break;
        }
        BootService.scheduleNotification(getCol().getTime(), this);
    }


    protected static int getSchedVer(Collection col) {
        int ver = col.schedVer();
        if (ver < 1 || ver > 2) {
            Timber.w("Unknown scheduler version: %d", ver);
        }
        return ver;
    }


    public void updateNotificationPreference(android.preference.ListPreference listpref) {
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

    private void updateSummary(android.preference.Preference pref) {
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
            } else if (pref instanceof android.preference.ListPreference) {
                value = ((android.preference.ListPreference) pref).getEntry().toString();
            } else if (pref instanceof android.preference.EditTextPreference) {
                value = ((android.preference.EditTextPreference) pref).getText();
            } else {
                return;
            }
        } catch (NullPointerException e) {
            Timber.w(e);
            value = "";
        }
        // Get summary text
        String oldSummary = mOriginalSumarries.get(pref.getKey());
        // Replace summary text with value according to some rules
        if ("".equals(oldSummary)) {
            pref.setSummary(value);
        } else if ("".equals(value)) {
            pref.setSummary(oldSummary);
        } else if (MINIMUM_CARDS_DUE_FOR_NOTIFICATION.equals(pref.getKey())) {
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
            Timber.w(e);
            return value;
        }
    }


    private void closePreferences() {
        finish();
        ActivityTransitionAnimation.slide(this, FADE);
        if (getCol() != null && getCol().getDb()!= null) {
            getCol().save();
        }
    }


    /** This is not fit for purpose (other than testing a single screen) */
    @NonNull
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public Set<String> getLoadedPreferenceKeys() {
        return mOriginalSumarries.keySet();
    }

    // ----------------------------------------------------------------------------
    // Inner classes
    // ----------------------------------------------------------------------------

    public static class HeaderFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preference_headers, rootKey);

            if (AdaptionUtil.isRestrictedLearningDevice()) {
                this.findPreference("pref_screen_advanced").setVisible(false);
            }
        }
    }

    @SuppressWarnings("deprecation") // Tracked as #5019 on github
    public abstract static class SettingsFragment extends android.preference.PreferenceFragment implements OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            String screenName = getAnalyticsScreenNameConstant();
            UsageAnalytics.sendAnalyticsScreenView(screenName);
            initSubscreen();
            ((Preferences) getActivity()).initAllPreferences(getPreferenceScreen());
        }

        /** Obtains a non-null reference to the preference defined by the key, or throws */
        @NonNull
        @SuppressWarnings("unchecked")
        protected <T extends android.preference.Preference> T requirePreference(String key) {
            android.preference.Preference preference = findPreference(key);
            if (preference == null) {
                throw new IllegalStateException("missing preference: '" + key + "'");
            }
            return (T) preference;
        }

        /** Obtains a non-null reference to the preference defined by the key, or throws */
        @NonNull
        @SuppressWarnings("unchecked")
        protected static <T extends android.preference.Preference> T requirePreference(android.preference.PreferenceScreen screen, String key) {
            android.preference.Preference preference = screen.findPreference(key);
            if (preference == null) {
                throw new IllegalStateException("missing preference: '" + key + "'");
            }
            return (T) preference;
        }

        @NonNull
        protected abstract String getAnalyticsScreenNameConstant();


        /**
         * Loads preferences (via addPreferencesFromResource) and sets up appropriate listeners for the preferences
         * Called by base class, do not call directly.
         */
        protected abstract void initSubscreen();


        @Override
        public void onResume() {
            super.onResume();
            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
            prefs.registerOnSharedPreferenceChangeListener(this);
            // syncAccount's summary can change while preferences are still open (user logs
            // in from preferences screen), so we need to update it here.
            updatePreference(((Preferences) getActivity()), prefs, "syncAccount");
            updatePreference(((Preferences) getActivity()), prefs, "custom_sync_server_link");
            updatePreference(((Preferences) getActivity()), prefs, "advanced_statistics_link");
        }

        @Override
        public void onPause() {
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
            super.onPause();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            updatePreference(((Preferences) getActivity()), sharedPreferences, key);
        }

        /**
         * Code which is run when a SharedPreference change has been detected
         * @param preferencesActivity A handle to the calling activity
         * @param prefs instance of SharedPreferences
         * @param key key in prefs which is being updated
         */
        @SuppressWarnings("deprecation") // Tracked as #5019 on github - convert to fragments
        private void updatePreference(Preferences preferencesActivity, SharedPreferences prefs, String key) {
            try {
                android.preference.PreferenceScreen screen = getPreferenceScreen();
                android.preference.Preference pref = screen.findPreference(key);
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
                        CustomSyncServer.handleSyncServerPreferenceChange(preferencesActivity.getBaseContext());
                        break;
                    case "timeoutAnswer": {
                        android.preference.CheckBoxPreference keepScreenOn = (android.preference.CheckBoxPreference) screen.findPreference("keepScreenOn");
                        keepScreenOn.setChecked(((android.preference.CheckBoxPreference) pref).isChecked());
                        break;
                    }
                    case LANGUAGE:
                        preferencesActivity.closePreferences();
                        break;
                    case SHOW_PROGRESS:
                        preferencesActivity.getCol().set_config("dueCounts", ((android.preference.CheckBoxPreference) pref).isChecked());
                        preferencesActivity.getCol().setMod();
                        break;
                    case SHOW_ESTIMATE:
                        preferencesActivity.getCol().set_config("estTimes", ((android.preference.CheckBoxPreference) pref).isChecked());
                        preferencesActivity.getCol().setMod();
                        break;
                    case NEW_SPREAD:
                        preferencesActivity.getCol().set_config("newSpread", Integer.parseInt(((android.preference.ListPreference) pref).getValue()));
                        preferencesActivity.getCol().setMod();
                        break;
                    case TIME_LIMIT:
                        preferencesActivity.getCol().set_config("timeLim", ((NumberRangePreference) pref).getValue() * 60);
                        preferencesActivity.getCol().setMod();
                        break;
                    case LEARN_CUTOFF:
                        preferencesActivity.getCol().set_config("collapseTime", ((NumberRangePreference) pref).getValue() * 60);
                        preferencesActivity.getCol().setMod();
                        break;
                    case USE_CURRENT:
                        preferencesActivity.getCol().set_config("addToCur", "0".equals(((android.preference.ListPreference) pref).getValue()));
                        preferencesActivity.getCol().setMod();
                        break;
                    case DAY_OFFSET: {
                        preferencesActivity.setDayOffset(((SeekBarPreference) pref).getValue());
                        break;
                    }
                    case PASTE_PNG:
                        preferencesActivity.getCol().set_config("pastePNG", ((android.preference.CheckBoxPreference) pref).isChecked());
                        preferencesActivity.getCol().setMod();
                        break;
                    case MINIMUM_CARDS_DUE_FOR_NOTIFICATION: {
                        android.preference.ListPreference listpref = (android.preference.ListPreference) screen.findPreference(MINIMUM_CARDS_DUE_FOR_NOTIFICATION);
                        if (listpref != null) {
                            preferencesActivity.updateNotificationPreference(listpref);
                            if (Integer.parseInt(listpref.getValue()) < PENDING_NOTIFICATIONS_ONLY) {
                                BootService.scheduleNotification(preferencesActivity.getCol().getTime(), preferencesActivity);
                            } else {
                                PendingIntent intent = CompatHelper.getCompat().getImmutableBroadcastIntent(preferencesActivity, 0,
                                        new Intent(preferencesActivity, NotificationService.class), 0);
                                final AlarmManager alarmManager = (AlarmManager) preferencesActivity.getSystemService(ALARM_SERVICE);
                                alarmManager.cancel(intent);
                            }
                        }
                        break;
                    }
                    case AnkiDroidApp.FEEDBACK_REPORT_KEY: {
                        String value = prefs.getString(AnkiDroidApp.FEEDBACK_REPORT_KEY, "");
                        Acra.onPreferenceChanged(preferencesActivity, value);
                        break;
                    }
                    case "syncAccount": {
                        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(preferencesActivity.getBaseContext());
                        String username = preferences.getString("username", "");
                        android.preference.Preference syncAccount = screen.findPreference("syncAccount");
                        if (syncAccount != null) {
                            if (TextUtils.isEmpty(username)) {
                                syncAccount.setSummary(R.string.sync_account_summ_logged_out);
                            } else {
                                syncAccount.setSummary(preferencesActivity.getString(R.string.sync_account_summ_logged_in, username));
                            }
                        }
                        break;
                    }
                    case "providerEnabled": {
                        ComponentName providerName = new ComponentName(preferencesActivity, "com.ichi2.anki.provider.CardContentProvider");
                        PackageManager pm = preferencesActivity.getPackageManager();
                        int state;
                        if (((android.preference.CheckBoxPreference) pref).isChecked()) {
                            state = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
                            Timber.i("AnkiDroid ContentProvider enabled by user");
                        } else {
                            state = PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                            Timber.i("AnkiDroid ContentProvider disabled by user");
                        }
                        pm.setComponentEnabledSetting(providerName, state, PackageManager.DONT_KILL_APP);
                        break;
                    }
                    case NEW_TIMEZONE_HANDLING : {
                        if (preferencesActivity.getCol().schedVer() != 1 && preferencesActivity.getCol().isUsingRustBackend()) {
                            AbstractSched sched = preferencesActivity.getCol().getSched();
                            boolean was_enabled = sched._new_timezone_enabled();
                            boolean is_enabled = ((android.preference.CheckBoxPreference) pref).isChecked();
                            if (was_enabled != is_enabled) {
                                if (is_enabled) {
                                    try {
                                        sched.set_creation_offset();
                                    } catch (BackendNotSupportedException e) {
                                        throw e.alreadyUsingRustBackend();
                                    }
                                } else {
                                    sched.clear_creation_offset();
                                }
                            }
                        }
                        break;
                    }
                    case CardBrowserContextMenu.CARD_BROWSER_CONTEXT_MENU_PREF_KEY:
                        CardBrowserContextMenu.ensureConsistentStateWithSharedPreferences(preferencesActivity);
                        break;
                    case AnkiCardContextMenu.ANKI_CARD_CONTEXT_MENU_PREF_KEY:
                        AnkiCardContextMenu.ensureConsistentStateWithSharedPreferences(preferencesActivity);
                        break;
                    case "gestureCornerTouch": {
                        GesturesSettingsFragment.updateGestureCornerTouch(preferencesActivity, screen);
                    }
                }
                // Update the summary text to reflect new value
                preferencesActivity.updateSummary(pref);
            } catch (BadTokenException e) {
                Timber.e(e, "Preferences: BadTokenException on showDialog");
            } catch (NumberFormatException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Temporary abstraction
     * Due to deprecation, we need to move from all Preference code in the Preference activity
     * into separate fragments.
     *
     * Fragments will inherit from this class
     *
     * This class adds methods which were previously in Preferences, and are now shared between Settings Fragments
     * To be merged with SettingsFragment once it can be made abstract
     */
    public abstract static class SpecificSettingsFragment extends SettingsFragment {
        /** @return The XML file which defines the preferences displayed by this PreferenceFragment */
        @XmlRes
        public abstract int getPreferenceResource();

        /**
         * Refreshes all values on the screen
         * Call if a large number of values are changed from one preference.
         */
        protected void refreshScreen() {
            getPreferenceScreen().removeAll();
            initSubscreen();
        }

        /**
         * Returns a non-null context object
         * @throws IllegalStateException if the fragment is not attached to an activity
         */
        @NonNull
        protected Context requireContext() {
            Context context = getActivity();
            if (context == null) {
                throw new IllegalStateException("no context was associated with the activity.");
            }
            return context;
        }

        @Nullable
        protected Collection getCol() {
            return CollectionHelper.getInstance().getCol(requireContext());
        }

        @NonNull
        protected static Intent getSubscreenIntent(Context context, String className) {
            Intent i = new Intent(context, Preferences.class);
            i.putExtra(android.preference.PreferenceActivity.EXTRA_SHOW_FRAGMENT, "com.ichi2.anki.Preferences$" + className);
            i.putExtra(android.preference.PreferenceActivity.EXTRA_NO_HEADERS, true);
            return i;
        }

        /** Sets the title of the window to the provided string */
        protected void setTitle(@StringRes int stringRes) {
            Activity activity = getActivity();

            ActionBar supportActionBar = null;
            if (activity instanceof AppCompatActivity) {
                AppCompatActivity acActivity = (AppCompatActivity) activity;
                acActivity.getSupportActionBar();
            } else if (activity instanceof AppCompatPreferenceActivity) {
                AppCompatPreferenceActivity apActivity = (AppCompatPreferenceActivity) activity;
                supportActionBar = apActivity.getSupportActionBar();
            } else {
                Timber.w("Activity was of the wrong type");
                return;
            }

            if (supportActionBar == null) {
                Timber.w("No action bar detected");
                return;
            }

            supportActionBar.setTitle(stringRes);
        }

        /**
         * Loads preferences (via addPreferencesFromResource) and sets up appropriate listeners for the preferences
         * Called by base class, do not call directly.
         */
        protected abstract void initSubscreen();
    }

    public static class GeneralSettingsFragment extends SpecificSettingsFragment {
        @Override
        public int getPreferenceResource() {
            return R.xml.preferences_general;
        }


        @NonNull
        @Override
        protected String getAnalyticsScreenNameConstant() {
            return "prefs.general";
        }


        @Override
        protected void initSubscreen() {
            addPreferencesFromResource(R.xml.preferences_general);
            android.preference.PreferenceScreen screen = getPreferenceScreen();
            if (AdaptionUtil.isRestrictedLearningDevice()) {
                android.preference.CheckBoxPreference mCheckBoxPref_Vibrate = requirePreference("widgetVibrate");
                android.preference.CheckBoxPreference mCheckBoxPref_Blink = requirePreference("widgetBlink");
                android.preference.PreferenceCategory mCategory = requirePreference("category_general_notification_pref");
                mCategory.removePreference(mCheckBoxPref_Vibrate);
                mCategory.removePreference(mCheckBoxPref_Blink);
            }
            // Build languages
            initializeLanguageDialog(screen);
        }

        private void initializeLanguageDialog(android.preference.PreferenceScreen screen) {
            android.preference.ListPreference languageSelection = (android.preference.ListPreference) screen.findPreference(LANGUAGE);
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
    }

    public static class ReviewingSettingsFragment extends SpecificSettingsFragment {
        @Override
        public int getPreferenceResource() {
            return R.xml.preferences_reviewing;
        }


        @NonNull
        @Override
        protected String getAnalyticsScreenNameConstant() {
            return "prefs.reviewing";
        }


        @Override
        protected void initSubscreen() {
            addPreferencesFromResource(R.xml.preferences_reviewing);
            // Show error toast if the user tries to disable answer button without gestures on
            android.preference.ListPreference fullscreenPreference = requirePreference(FullScreenMode.PREF_KEY);
            fullscreenPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(requireContext());
                if (prefs.getBoolean("gestures", false) || !FullScreenMode.FULLSCREEN_ALL_GONE.getPreferenceValue().equals(newValue)) {
                    return true;
                } else {
                    UIUtils.showThemedToast(requireContext(),
                            R.string.full_screen_error_gestures, false);
                    return false;
                }
            });
        }
    }

    public static class AppearanceSettingsFragment extends SpecificSettingsFragment {

        private static final int RESULT_LOAD_IMG = 111;
        private android.preference.CheckBoxPreference mBackgroundImage;


        @Override
        public int getPreferenceResource() {
            return R.xml.preferences_appearance;
        }


        @NonNull
        @Override
        protected String getAnalyticsScreenNameConstant() {
            return "prefs.appearance";
        }


        @Override
        protected void initSubscreen() {
            addPreferencesFromResource(R.xml.preferences_appearance);
            mBackgroundImage = requirePreference("deckPickerBackground");
            mBackgroundImage.setOnPreferenceClickListener(preference -> {
                if (mBackgroundImage.isChecked()) {
                    try {
                        Intent galleryIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                        startActivityForResult(galleryIntent, RESULT_LOAD_IMG);
                        mBackgroundImage.setChecked(true);
                    } catch (Exception ex) {
                        Timber.e("%s", ex.getLocalizedMessage());
                    }
                } else {
                    mBackgroundImage.setChecked(false);
                    String currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(requireContext());
                    File imgFile = new File(currentAnkiDroidDirectory, "DeckPickerBackground.png");
                    if (imgFile.exists()) {
                        if (imgFile.delete()) {
                            UIUtils.showThemedToast(requireContext(), getString(R.string.background_image_removed), false);
                        } else {
                            UIUtils.showThemedToast(requireContext(), getString(R.string.error_deleting_image), false);
                        }
                    } else {
                        UIUtils.showThemedToast(requireContext(), getString(R.string.background_image_removed), false);
                    }
                }
                return true;
            });
            initializeCustomFontsDialog();
        }

        /** Initializes the list of custom fonts shown in the preferences. */
        private void initializeCustomFontsDialog() {
            android.preference.ListPreference defaultFontPreference = requirePreference("defaultFont");
            defaultFontPreference.setEntries(getCustomFonts("System default"));
            defaultFontPreference.setEntryValues(getCustomFonts(""));
            android.preference.ListPreference browserEditorCustomFontsPreference = requirePreference("browserEditorFont");
            browserEditorCustomFontsPreference.setEntries(getCustomFonts("System default"));
            browserEditorCustomFontsPreference.setEntryValues(getCustomFonts("", true));
        }

        /** Returns a list of the names of the installed custom fonts. */
        private String[] getCustomFonts(String defaultValue) {
            return getCustomFonts(defaultValue, false);
        }


        private String[] getCustomFonts(String defaultValue, boolean useFullPath) {
            List<AnkiFont> mFonts = Utils.getCustomFonts(requireContext());
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
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            // DEFECT #5973: Does not handle Google Drive downloads
            try {
                if (requestCode == RESULT_LOAD_IMG && resultCode == RESULT_OK && null != data) {
                    Uri selectedImage = data.getData();
                    String[] filePathColumn = { MediaStore.MediaColumns.SIZE };
                    try (Cursor cursor = requireContext().getContentResolver().query(selectedImage, filePathColumn, null, null, null)) {
                        cursor.moveToFirst();
                        // file size in MB
                        long fileLength = cursor.getLong(0) / (1024 * 1024);

                        String currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(requireContext());
                        String imageName = "DeckPickerBackground.png";
                        File destFile = new File(currentAnkiDroidDirectory, imageName);
                        // Image size less than 10 MB copied to AnkiDroid folder
                        if (fileLength < 10) {
                            try (FileChannel sourceChannel = ((FileInputStream) requireContext().getContentResolver().openInputStream(selectedImage)).getChannel();
                                 FileChannel destChannel = new FileOutputStream(destFile).getChannel()) {
                                destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
                                UIUtils.showThemedToast(requireContext(), getString(R.string.background_image_applied), false);
                            }
                        } else {
                            mBackgroundImage.setChecked(false);
                            UIUtils.showThemedToast(requireContext(), getString(R.string.image_max_size_allowed, 10), false);
                        }
                    }
                } else {
                    mBackgroundImage.setChecked(false);
                    UIUtils.showThemedToast(requireContext(), getString(R.string.no_image_selected), false);
                }
            } catch (OutOfMemoryError | Exception e) {
                Timber.w(e);
                UIUtils.showThemedToast(requireContext(), getString(R.string.error_selecting_image, e.getLocalizedMessage()), false);
            }
        }
    }

    public static class GesturesSettingsFragment extends SpecificSettingsFragment {
        @Override
        public int getPreferenceResource() {
            return R.xml.preferences_gestures;
        }


        @NonNull
        @Override
        protected String getAnalyticsScreenNameConstant() {
            return "prefs.gestures";
        }


        @Override
        protected void initSubscreen() {
            addPreferencesFromResource(R.xml.preferences_gestures);
            android.preference.PreferenceScreen screen = getPreferenceScreen();
            updateGestureCornerTouch(screen);
        }

        private void updateGestureCornerTouch(android.preference.PreferenceScreen screen) {
            updateGestureCornerTouch(requireContext(), screen);
        }

        public static void updateGestureCornerTouch(Context context, android.preference.PreferenceScreen screen) {
            boolean gestureCornerTouch = AnkiDroidApp.getSharedPrefs(context).getBoolean("gestureCornerTouch", false);
            if (gestureCornerTouch) {
                requirePreference(screen, "gestureTapTop").setTitle(R.string.gestures_corner_tap_top_center);
                requirePreference(screen, "gestureTapLeft").setTitle(R.string.gestures_corner_tap_middle_left);
                requirePreference(screen, "gestureTapRight").setTitle(R.string.gestures_corner_tap_middle_right);
                requirePreference(screen, "gestureTapBottom").setTitle(R.string.gestures_corner_tap_bottom_center);
            } else {
                requirePreference(screen, "gestureTapTop").setTitle(R.string.gestures_tap_top);
                requirePreference(screen, "gestureTapLeft").setTitle(R.string.gestures_tap_left);
                requirePreference(screen, "gestureTapRight").setTitle(R.string.gestures_tap_right);
                requirePreference(screen, "gestureTapBottom").setTitle(R.string.gestures_tap_bottom);
            }
        }
    }

    public static class AdvancedSettingsFragment extends SpecificSettingsFragment {

        @Override
        public int getPreferenceResource() {
            return R.xml.preferences_advanced;
        }

        @NonNull
        public static Intent getSubscreenIntent(Context context) {
            return getSubscreenIntent(context, AdvancedSettingsFragment.class.getSimpleName());
        }


        @NonNull
        @Override
        protected String getAnalyticsScreenNameConstant() {
            return "prefs.advanced";
        }


        @Override
        protected void initSubscreen() {
            addPreferencesFromResource(R.xml.preferences_advanced);
            android.preference.PreferenceScreen screen = getPreferenceScreen();
            // Check that input is valid before committing change in the collection path
            android.preference.EditTextPreference collectionPathPreference = requirePreference("deckPath");
            collectionPathPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                final String newPath = (String) newValue;
                try {
                    CollectionHelper.initializeAnkiDroidDirectory(newPath);
                    return true;
                } catch (StorageAccessException e) {
                    Timber.e(e, "Could not initialize directory: %s", newPath);
                    MaterialDialog materialDialog = new MaterialDialog.Builder(requireContext())
                            .title(R.string.dialog_collection_path_not_dir)
                            .positiveText(R.string.dialog_ok)
                            .negativeText(R.string.reset_custom_buttons)
                            .onPositive((dialog, which) -> dialog.dismiss())
                            .onNegative((dialog, which) -> collectionPathPreference.setText(CollectionHelper.getDefaultAnkiDroidDirectory(requireContext())))
                            .show();
                    return false;
                }
            });
            setupContextMenuPreference(CardBrowserContextMenu.CARD_BROWSER_CONTEXT_MENU_PREF_KEY, R.string.card_browser_context_menu);
            setupContextMenuPreference(AnkiCardContextMenu.ANKI_CARD_CONTEXT_MENU_PREF_KEY, R.string.context_menu_anki_card_label);

            if (getCol().schedVer() == 1) {
                Timber.i("Displaying V1-to-V2 scheduler preference");
                android.preference.CheckBoxPreference schedVerPreference = new android.preference.CheckBoxPreference(requireContext());
                schedVerPreference.setTitle(R.string.sched_v2);
                schedVerPreference.setSummary(R.string.sched_v2_summ);
                schedVerPreference.setOnPreferenceChangeListener((preference, o) -> {

                    MaterialDialog.Builder builder = new MaterialDialog.Builder(requireContext());
                    // Going to V2
                    builder.title(R.string.sched_ver_toggle_title);
                    builder.content(R.string.sched_ver_1to2);
                    builder.onPositive((dialog, which) -> {
                        getCol().modSchemaNoCheck();
                        try {
                            getCol().changeSchedulerVer(2);
                            screen.removePreference(schedVerPreference);
                        } catch (ConfirmModSchemaException e2) {
                            // This should never be reached as we explicitly called modSchemaNoCheck()
                            throw new RuntimeException(e2);
                        }
                    });
                    builder.onNegative((dialog, which) -> schedVerPreference.setChecked(false));
                    builder.onNeutral((dialog, which) -> {
                        // call v2 scheduler documentation website
                        Uri uri = Uri.parse(getString(R.string.link_anki_2_scheduler));
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        startActivity(intent);
                    });
                    builder.positiveText(R.string.dialog_ok);
                    builder.neutralText(R.string.help);
                    builder.negativeText(R.string.dialog_cancel);
                    builder.show();
                    return false;

                });
                // meaning of order here is the position of Preference in xml layout.
                schedVerPreference.setOrder(5);
                screen.addPreference(schedVerPreference);
            }


            // Make it possible to test crash reporting, but only for DEBUG builds
            if (BuildConfig.DEBUG && !AdaptionUtil.isUserATestClient()) {
                Timber.i("Debug mode, allowing for test crashes");
                android.preference.Preference triggerTestCrashPreference = new android.preference.Preference(requireContext());
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
                android.preference.Preference analyticsDebugMode = new android.preference.Preference(requireContext());
                analyticsDebugMode.setKey("analytics_debug_preference");
                analyticsDebugMode.setTitle("Switch Analytics to dev mode");
                analyticsDebugMode.setSummary("Touch here to use Analytics dev tag and 100% sample rate");
                analyticsDebugMode.setOnPreferenceClickListener(preference -> {
                    if (UsageAnalytics.isEnabled()) {
                        UIUtils.showThemedToast(requireContext(), "Analytics set to dev mode", true);
                    } else {
                        UIUtils.showThemedToast(requireContext(), "Done! Enable Analytics in 'General' settings to use.", true);
                    }
                    UsageAnalytics.setDevMode();
                    return true;
                });
                screen.addPreference(analyticsDebugMode);
            }
            if (BuildConfig.DEBUG) {
                Timber.i("Debug mode, allowing database lock preference");
                android.preference.Preference lockDbPreference = new android.preference.Preference(requireContext());
                lockDbPreference.setKey("debug_lock_database");
                lockDbPreference.setTitle("Lock Database");
                lockDbPreference.setSummary("Touch here to lock the database (all threads block in-process, exception if using second process)");
                lockDbPreference.setOnPreferenceClickListener(preference -> {
                    DatabaseLock.engage(requireContext());
                    return true;
                });
                screen.addPreference(lockDbPreference);
            }
            if (BuildConfig.DEBUG) {
                Timber.i("Debug mode, option for showing onboarding walkthrough");
                android.preference.CheckBoxPreference onboardingPreference = new android.preference.CheckBoxPreference(requireContext());
                onboardingPreference.setKey("showOnboarding");
                onboardingPreference.setTitle(R.string.show_onboarding);
                onboardingPreference.setSummary(R.string.show_onboarding_desc);
                screen.addPreference(onboardingPreference);
            }
            if (BuildConfig.DEBUG) {
                Timber.i("Debug mode, option for resetting onboarding walkthrough");
                android.preference.Preference onboardingPreference = new android.preference.Preference(requireContext());
                onboardingPreference.setKey("resetOnboarding");
                onboardingPreference.setTitle(R.string.reset_onboarding);
                onboardingPreference.setSummary(R.string.reset_onboarding_desc);
                onboardingPreference.setOnPreferenceClickListener(preference -> {
                    OnboardingUtils.Companion.reset(requireContext());
                    return true;
                });
                screen.addPreference(onboardingPreference);
            }
            // Adding change logs in both debug and release builds
            Timber.i("Adding open changelog");
            android.preference.Preference changelogPreference = new android.preference.Preference(requireContext());
            changelogPreference.setTitle(R.string.open_changelog);
            Intent infoIntent = new Intent(requireContext(), Info.class);
            infoIntent.putExtra(Info.TYPE_EXTRA, Info.TYPE_NEW_VERSION);
            changelogPreference.setIntent(infoIntent);
            screen.addPreference(changelogPreference);
            // Force full sync option
            ConfirmationPreference fullSyncPreference = requirePreference("force_full_sync");
            fullSyncPreference.setDialogMessage(R.string.force_full_sync_summary);
            fullSyncPreference.setDialogTitle(R.string.force_full_sync_title);
            fullSyncPreference.setOkHandler(() -> {
                if (getCol() == null) {
                    UIUtils.showThemedToast(requireContext(), R.string.directory_inaccessible, false);
                    return;
                }
                getCol().modSchemaNoCheck();
                getCol().setMod();
                UIUtils.showThemedToast(requireContext(), android.R.string.ok, true);
            });
            // Workaround preferences
            removeUnnecessaryAdvancedPrefs();
            addThirdPartyAppsListener();
        }

        private void setupContextMenuPreference(String key, @StringRes int contextMenuName) {
            // FIXME: The menu is named in the system language (as it's defined in the manifest which may be
            //  different than the app language
            android.preference.CheckBoxPreference cardBrowserContextMenuPreference = requirePreference(key);
            String menuName = getString(contextMenuName);
            // Note: The below format strings are generic, not card browser specific despite the name
            cardBrowserContextMenuPreference.setTitle(getString(R.string.card_browser_enable_external_context_menu, menuName));
            cardBrowserContextMenuPreference.setSummary(getString(R.string.card_browser_enable_external_context_menu_summary, menuName));
        }

        private void removeUnnecessaryAdvancedPrefs() {
            android.preference.PreferenceCategory plugins = (android.preference.PreferenceCategory) findPreference("category_plugins");
            // Disable the emoji/kana buttons to scroll preference if those keys don't exist
            if (!CompatHelper.hasKanaAndEmojiKeys()) {
                android.preference.CheckBoxPreference emojiScrolling = (android.preference.CheckBoxPreference) findPreference("scrolling_buttons");
                if (emojiScrolling != null && plugins != null) {
                    plugins.removePreference(emojiScrolling);
                }
            }
            // Disable the double scroll preference if no scrolling keys
            if (!CompatHelper.hasScrollKeys() && !CompatHelper.hasKanaAndEmojiKeys()) {
                android.preference.CheckBoxPreference doubleScrolling = (android.preference.CheckBoxPreference) findPreference("double_scrolling");
                if (doubleScrolling != null && plugins != null) {
                    plugins.removePreference(doubleScrolling);
                }
            }
        }


        private void addThirdPartyAppsListener() {
            // #5864 - some people don't have a browser so we can't use <intent>
            // and need to handle the keypress ourself.
            android.preference.Preference showThirdParty = requirePreference("thirdpartyapps_link");
            final String githubThirdPartyAppsUrl = "https://github.com/ankidroid/Anki-Android/wiki/Third-Party-Apps";
            showThirdParty.setOnPreferenceClickListener((preference) -> {
                try {
                    Intent openThirdPartyAppsIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(githubThirdPartyAppsUrl));
                    super.startActivity(openThirdPartyAppsIntent);
                } catch (ActivityNotFoundException e) {
                    Timber.w(e);
                    //We use a different message here. We have limited space in the snackbar
                    String error = getString(R.string.activity_start_failed_load_url, githubThirdPartyAppsUrl);
                    UIUtils.showSimpleSnackbar(getActivity(), error, false);
                }
                return true;
            });
        }
    }

    public static class CustomButtonsSettingsFragment extends SpecificSettingsFragment {

        @NonNull
        public static Intent getSubscreenIntent(Context context) {
            return getSubscreenIntent(context, CustomButtonsSettingsFragment.class.getSimpleName());
        }


        @Override
        public int getPreferenceResource() {
            return R.xml.preferences_custom_buttons;
        }


        @NonNull
        @Override
        protected String getAnalyticsScreenNameConstant() {
            return "prefs.custom_buttons";
        }


        @Override
        protected void initSubscreen() {
            setTitle(R.string.custom_buttons);
            addPreferencesFromResource(R.xml.preferences_custom_buttons);
            android.preference.PreferenceScreen screen = getPreferenceScreen();
            // Reset toolbar button customizations
            android.preference.Preference reset_custom_buttons = requirePreference("reset_custom_buttons");
            reset_custom_buttons.setOnPreferenceClickListener(preference -> {
                SharedPreferences.Editor edit = AnkiDroidApp.getSharedPrefs(requireContext()).edit();
                edit.remove("customButtonUndo");
                edit.remove("customButtonScheduleCard");
                edit.remove("customButtonEditCard");
                edit.remove("customButtonTags");
                edit.remove("customButtonAddCard");
                edit.remove("customButtonReplay");
                edit.remove("customButtonCardInfo");
                edit.remove("customButtonSelectTts");
                edit.remove("customButtonDeckOptions");
                edit.remove("customButtonMarkCard");
                edit.remove("customButtonToggleMicToolBar");
                edit.remove("customButtonBury");
                edit.remove("customButtonSuspend");
                edit.remove("customButtonFlag");
                edit.remove("customButtonDelete");
                edit.remove("customButtonEnableWhiteboard");
                edit.remove("customButtonSaveWhiteboard");
                edit.remove("customButtonWhiteboardPenColor");
                edit.remove("customButtonClearWhiteboard");
                edit.remove("customButtonShowHideWhiteboard");
                edit.apply();
                // #9263: refresh the screen to display the changes
                refreshScreen();
                return true;
            });
        }
    }

    public static class AdvancedStatisticsSettingsFragment extends SpecificSettingsFragment {

        @Override
        public int getPreferenceResource() {
            return R.xml.preferences_advanced_statistics;
        }

        @NonNull
        @Override
        protected String getAnalyticsScreenNameConstant() {
            return "prefs.advanced_statistics";
        }


        @Override
        protected void initSubscreen() {
            setTitle(R.string.advanced_statistics_title);
            addPreferencesFromResource(R.xml.preferences_advanced_statistics);
        }
    }

    public static class CustomSyncServerSettingsFragment extends SpecificSettingsFragment {

        @Override
        public int getPreferenceResource() {
            return R.xml.preferences_custom_sync_server;
        }

        @NonNull
        public static Intent getSubscreenIntent(Context context) {
            return getSubscreenIntent(context, CustomSyncServerSettingsFragment.class.getSimpleName());
        }


        @NonNull
        @Override
        protected String getAnalyticsScreenNameConstant() {
            return "prefs.custom_sync_server";
        }


        @Override
        protected void initSubscreen() {
            setTitle(R.string.custom_sync_server_title);
            addPreferencesFromResource(R.xml.preferences_custom_sync_server);
            android.preference.Preference syncUrlPreference = requirePreference("syncBaseUrl");
            android.preference.Preference mSyncUrlPreference = requirePreference("syncMediaUrl");
            syncUrlPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                String newUrl = newValue.toString();
                if (!URLUtil.isValidUrl(newUrl)) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.custom_sync_server_base_url_invalid)
                            .setPositiveButton(R.string.dialog_ok, null)
                            .show();

                    return false;
                }

                return true;
            });
            mSyncUrlPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                String newUrl = newValue.toString();
                if (!URLUtil.isValidUrl(newUrl)) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.custom_sync_server_media_url_invalid)
                            .setPositiveButton(R.string.dialog_ok, null)
                            .show();

                    return false;
                }

                return true;
            });
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public void attachBaseContext(Context context) {
        super.attachBaseContext(context);
    }
}
