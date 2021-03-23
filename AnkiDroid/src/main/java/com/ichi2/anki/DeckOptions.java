
package com.ichi2.anki;

/****************************************************************************************
 * Copyright (c) 2009 Casey Link <unnamedrambler@gmail.com>                             *
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;

import android.view.KeyEvent;
import android.view.MenuItem;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.anki.services.ReminderService;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskListenerWithContext;
import com.ichi2.async.TaskManager;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.utils.Time;
import com.ichi2.preferences.NumberRangePreference;
import com.ichi2.preferences.StepsPreference;
import com.ichi2.preferences.TimePreference;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;
import com.ichi2.ui.AppCompatPreferenceActivity;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;
import com.ichi2.utils.NamedJSONComparator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import androidx.annotation.NonNull;
import timber.log.Timber;

import static com.ichi2.anim.ActivityTransitionAnimation.Direction.FADE;

/**
 * Preferences for the current deck.
 */
public class DeckOptions extends AppCompatPreferenceActivity implements OnSharedPreferenceChangeListener {

    private DeckConfig mOptions;
    private Deck mDeck;
    private Collection mCol;
    private boolean mPreferenceChanged = false;
    private BroadcastReceiver mUnmountReceiver = null;
    private DeckPreferenceHack mPref;

    public class DeckPreferenceHack implements SharedPreferences {

        private final Map<String, String> mValues = new HashMap<>(30); // At most as many as in cacheValues
        private final Map<String, String> mSummaries = new HashMap<>();
        private MaterialDialog mProgressDialog;
        private final List<OnSharedPreferenceChangeListener> listeners = new LinkedList<>();


        private DeckPreferenceHack() {
            this.cacheValues();
        }

        public DeckOptions getDeckOptions() {
            return DeckOptions.this;
        }

        private void cacheValues() {
            Timber.i("DeckOptions - CacheValues");

            try {
                mOptions = mCol.getDecks().confForDid(mDeck.getLong("id"));

                mValues.put("name", mDeck.getString("name"));
                mValues.put("desc", mDeck.getString("desc"));
                mValues.put("deckConf", mDeck.getString("conf"));
                // general
                mValues.put("maxAnswerTime", mOptions.getString("maxTaken"));
                mValues.put("showAnswerTimer", Boolean.toString(parseTimerValue(mOptions)));
                mValues.put("autoPlayAudio", Boolean.toString(mOptions.getBoolean("autoplay")));
                mValues.put("replayQuestion", Boolean.toString(mOptions.optBoolean("replayq", true)));
                // new
                JSONObject newOptions = mOptions.getJSONObject("new");
                mValues.put("newSteps", StepsPreference.convertFromJSON(newOptions.getJSONArray("delays")));
                mValues.put("newGradIvl", newOptions.getJSONArray("ints").getString(0));
                mValues.put("newEasy", newOptions.getJSONArray("ints").getString(1));
                mValues.put("newFactor", Integer.toString(newOptions.getInt("initialFactor") / 10));
                mValues.put("newOrder", newOptions.getString("order"));
                mValues.put("newPerDay", newOptions.getString("perDay"));
                mValues.put("newBury", Boolean.toString(newOptions.optBoolean("bury", true)));
                // rev
                JSONObject revOptions = mOptions.getJSONObject("rev");
                mValues.put("revPerDay", revOptions.getString("perDay"));
                mValues.put("easyBonus", String.format(Locale.ROOT, "%.0f", revOptions.getDouble("ease4") * 100));
                mValues.put("hardFactor", String.format(Locale.ROOT, "%.0f", revOptions.optDouble("hardFactor", 1.2) * 100));
                mValues.put("revIvlFct", String.format(Locale.ROOT, "%.0f", revOptions.getDouble("ivlFct") * 100));
                mValues.put("revMaxIvl", revOptions.getString("maxIvl"));
                mValues.put("revBury", Boolean.toString(revOptions.optBoolean("bury", true)));

                mValues.put("revUseGeneralTimeoutSettings", Boolean.toString(revOptions.optBoolean("useGeneralTimeoutSettings", true)));
                mValues.put("revTimeoutAnswer", Boolean.toString(revOptions.optBoolean("timeoutAnswer", false)));
                mValues.put("revTimeoutAnswerSeconds", Integer.toString(revOptions.optInt("timeoutAnswerSeconds", 6)));
                mValues.put("revTimeoutQuestionSeconds", Integer.toString(revOptions.optInt("timeoutQuestionSeconds", 60)));

                // lapse
                JSONObject lapOptions = mOptions.getJSONObject("lapse");
                mValues.put("lapSteps", StepsPreference.convertFromJSON(lapOptions.getJSONArray("delays")));
                mValues.put("lapNewIvl", String.format(Locale.ROOT, "%.0f", lapOptions.getDouble("mult") * 100));
                mValues.put("lapMinIvl", lapOptions.getString("minInt"));
                mValues.put("lapLeechThres", lapOptions.getString("leechFails"));
                mValues.put("lapLeechAct", lapOptions.getString("leechAction"));
                // options group management
                mValues.put("currentConf", mCol.getDecks().getConf(mDeck.getLong("conf")).getString("name"));
                // reminders
                if (mOptions.has("reminder")) {
                    final JSONObject reminder = mOptions.getJSONObject("reminder");
                    final JSONArray reminderTime = reminder.getJSONArray("time");

                    mValues.put("reminderEnabled", Boolean.toString(reminder.getBoolean("enabled")));
                    mValues.put("reminderTime", String.format("%1$02d:%2$02d", reminderTime.getLong(0), reminderTime.getLong(1)));
                } else {
                    mValues.put("reminderEnabled", "false");
                    mValues.put("reminderTime", TimePreference.DEFAULT_VALUE);
                }
            } catch (JSONException e) {
                Timber.e(e, "DeckOptions - cacheValues");
                AnkiDroidApp.sendExceptionReport(e, "DeckOptions: cacheValues");
                Resources r = DeckOptions.this.getResources();
                UIUtils.showThemedToast(DeckOptions.this, r.getString(R.string.deck_options_corrupt, e.getLocalizedMessage()), false);
                finish();
            }
        }


        private boolean parseTimerValue(DeckConfig options) {
            return DeckConfig.parseTimerOpt(options, true);
        }


        public class Editor implements SharedPreferences.Editor {

            private ContentValues mUpdate = new ContentValues();


            @Override
            public SharedPreferences.Editor clear() {
                Timber.d("clear()");
                mUpdate = new ContentValues();
                return this;
            }


            @Override
            public boolean commit() {
                Timber.d("DeckOptions - commit() changes back to database");

                try {
                    for (Entry<String, Object> entry : mUpdate.valueSet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();
                        Timber.i("Change value for key '%s': %s", key, value);

                        switch (key) {
                            case "maxAnswerTime":
                                mOptions.put("maxTaken", value);
                                break;
                            case "newFactor":
                                mOptions.getJSONObject("new").put("initialFactor", (Integer) value * 10);
                                break;
                            case "newOrder": {
                                int newValue = Integer.parseInt((String) value);
                                // Sorting is slow, so only do it if we change order
                                int oldValue = mOptions.getJSONObject("new").getInt("order");
                                if (oldValue != newValue) {
                                    mOptions.getJSONObject("new").put("order", newValue);
                                    TaskManager.launchCollectionTask(new CollectionTask.Reorder(mOptions), confChangeHandler());
                                }
                                mOptions.getJSONObject("new").put("order", Integer.parseInt((String) value));
                                break;
                            }
                            case "newPerDay":
                                mOptions.getJSONObject("new").put("perDay", value);
                                break;
                            case "newGradIvl": {
                                JSONArray newInts = new JSONArray(); // [graduating, easy]

                                newInts.put(value);
                                newInts.put(mOptions.getJSONObject("new").getJSONArray("ints").getInt(1));
                                mOptions.getJSONObject("new").put("ints", newInts);
                                break;
                            }
                            case "newEasy": {
                                JSONArray newInts = new JSONArray(); // [graduating, easy]

                                newInts.put(mOptions.getJSONObject("new").getJSONArray("ints").getInt(0));
                                newInts.put(value);
                                mOptions.getJSONObject("new").put("ints", newInts);
                                break;
                            }
                            case "newBury":
                                mOptions.getJSONObject("new").put("bury", value);
                                break;
                            case "revPerDay":
                                mOptions.getJSONObject("rev").put("perDay", value);
                                break;
                            case "easyBonus":
                                mOptions.getJSONObject("rev").put("ease4", (Integer) value / 100.0f);
                                break;
                            case "hardFactor":
                                mOptions.getJSONObject("rev").put("hardFactor", (Integer) value / 100.0f);
                                break;
                            case "revIvlFct":
                                mOptions.getJSONObject("rev").put("ivlFct", (Integer) value / 100.0f);
                                break;
                            case "revMaxIvl":
                                mOptions.getJSONObject("rev").put("maxIvl", value);
                                break;
                            case "revBury":
                                mOptions.getJSONObject("rev").put("bury", value);
                                break;
                            case "revUseGeneralTimeoutSettings":
                                mOptions.getJSONObject("rev").put("useGeneralTimeoutSettings", value);
                                break;
                            case "revTimeoutAnswer":
                                mOptions.getJSONObject("rev").put("timeoutAnswer", value);
                                break;
                            case "revTimeoutAnswerSeconds":
                                mOptions.getJSONObject("rev").put("timeoutAnswerSeconds", value);
                                break;
                            case "revTimeoutQuestionSeconds":
                                mOptions.getJSONObject("rev").put("timeoutQuestionSeconds", value);
                                break;
                            case "lapMinIvl":
                                mOptions.getJSONObject("lapse").put("minInt", value);
                                break;
                            case "lapLeechThres":
                                mOptions.getJSONObject("lapse").put("leechFails", value);
                                break;
                            case "lapLeechAct":
                                mOptions.getJSONObject("lapse").put("leechAction", Integer.parseInt((String) value));
                                break;
                            case "lapNewIvl":
                                mOptions.getJSONObject("lapse").put("mult", (Integer) value / 100.0f);
                                break;
                            case "showAnswerTimer":
                                mOptions.put("timer", (Boolean) value ? 1 : 0);
                                break;
                            case "autoPlayAudio":
                                mOptions.put("autoplay", value);
                                break;
                            case "replayQuestion":
                                mOptions.put("replayq", value);
                                break;
                            case "desc":
                                mDeck.put("desc", value);
                                mCol.getDecks().save(mDeck);
                                break;
                            case "newSteps":
                                mOptions.getJSONObject("new").put("delays", StepsPreference.convertToJSON((String) value));
                                break;
                            case "lapSteps":
                                mOptions.getJSONObject("lapse")
                                        .put("delays", StepsPreference.convertToJSON((String) value));
                                break;
                            case "deckConf": {
                                long newConfId = Long.parseLong((String) value);
                                mOptions = mCol.getDecks().getConf(newConfId);
                                TaskManager.launchCollectionTask(new CollectionTask.ConfChange(mDeck, mOptions), confChangeHandler());
                                break;
                            }
                            case "confRename": {
                                String newName = (String) value;
                                if (!TextUtils.isEmpty(newName)) {
                                    mOptions.put("name", newName);
                                }
                                break;
                            }
                            case "confReset":
                                if ((Boolean) value) {
                                    TaskManager.launchCollectionTask(new CollectionTask.ConfReset(mOptions), confChangeHandler());
                                }
                                break;
                            case "confAdd": {
                                String newName = (String) value;
                                if (!TextUtils.isEmpty(newName)) {
                                    // New config clones current config
                                    long id = mCol.getDecks().confId(newName, mOptions.toString());
                                    mDeck.put("conf", id);
                                    mCol.getDecks().save(mDeck);
                                }
                                break;
                            }
                            case "confRemove":
                                if (mOptions.getLong("id") == 1) {
                                    // Don't remove the options group if it's the default group
                                    UIUtils.showThemedToast(DeckOptions.this,
                                            getResources().getString(R.string.default_conf_delete_error), false);
                                } else {
                                    // Remove options group, handling the case where the user needs to confirm full sync
                                    try {
                                        remConf();
                                    } catch (ConfirmModSchemaException e) {
                                        // Libanki determined that a full sync will be required, so confirm with the user before proceeding
                                        // TODO : Use ConfirmationDialog DialogFragment -- not compatible with PreferenceActivity
                                        new MaterialDialog.Builder(DeckOptions.this)
                                                .content(R.string.full_sync_confirmation)
                                                .positiveText(R.string.dialog_ok)
                                                .negativeText(R.string.dialog_cancel)
                                                .onPositive((dialog, which) -> {
                                                            mCol.modSchemaNoCheck();
                                                            try {
                                                                remConf();
                                                            } catch (ConfirmModSchemaException cmse) {
                                                                // This should never be reached as we just forced modSchema
                                                                throw new RuntimeException(cmse);
                                                            }
                                                        }
                                                )
                                                .build().show();
                                    }
                                }
                                break;
                            case "confSetSubdecks":
                                if ((Boolean) value) {
                                    TaskManager.launchCollectionTask(new CollectionTask.ConfSetSubdecks(mDeck, mOptions), confChangeHandler());
                                }
                                break;
                            case "reminderEnabled": {
                                final JSONObject reminder = new JSONObject();

                                reminder.put("enabled", value);
                                if (mOptions.has("reminder")) {
                                    reminder.put("time", mOptions.getJSONObject("reminder").getJSONArray("time"));
                                } else {
                                    reminder.put("time", new JSONArray()
                                            .put(TimePreference.parseHours(TimePreference.DEFAULT_VALUE))
                                            .put(TimePreference.parseMinutes(TimePreference.DEFAULT_VALUE)));
                                }

                                mOptions.put("reminder", reminder);

                                final AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
                                final PendingIntent reminderIntent = PendingIntent.getBroadcast(
                                        getApplicationContext(),
                                        (int) mOptions.getLong("id"),
                                        new Intent(getApplicationContext(), ReminderService.class).putExtra
                                                (ReminderService.EXTRA_DECK_OPTION_ID, mOptions.getLong("id")),
                                        0
                                );

                                alarmManager.cancel(reminderIntent);
                                if ((Boolean) value) {
                                    final Calendar calendar = reminderToCalendar(mCol.getTime(), reminder);

                                    alarmManager.setRepeating(
                                            AlarmManager.RTC_WAKEUP,
                                            calendar.getTimeInMillis(),
                                            AlarmManager.INTERVAL_DAY,
                                            reminderIntent
                                    );
                                }
                                break;
                            }
                            case "reminderTime": {
                                final JSONObject reminder = new JSONObject();

                                reminder.put("enabled", true);
                                reminder.put("time", new JSONArray().put(TimePreference.parseHours((String) value))
                                        .put(TimePreference.parseMinutes((String) value)));

                                mOptions.put("reminder", reminder);

                                final AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
                                final PendingIntent reminderIntent = PendingIntent.getBroadcast(
                                        getApplicationContext(),
                                        (int) mOptions.getLong("id"),
                                        new Intent(getApplicationContext(), ReminderService.class).putExtra
                                                (ReminderService.EXTRA_DECK_OPTION_ID, mOptions.getLong("id")),
                                        0
                                );

                                alarmManager.cancel(reminderIntent);

                                final Calendar calendar = reminderToCalendar(mCol.getTime(), reminder);

                                alarmManager.setRepeating(
                                        AlarmManager.RTC_WAKEUP,
                                        calendar.getTimeInMillis(),
                                        AlarmManager.INTERVAL_DAY,
                                        reminderIntent
                                );
                                break;
                            }
                            default:
                                Timber.w("Unknown key type: %s", key);
                                break;
                        }
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

                // save conf
                try {
                    mCol.getDecks().save(mOptions);
                } catch (RuntimeException e) {
                    Timber.e(e, "DeckOptions - RuntimeException on saving conf");
                    AnkiDroidApp.sendExceptionReport(e, "DeckOptionsSaveConf");
                    setResult(DeckPicker.RESULT_DB_ERROR);
                    finish();
                }

                // make sure we refresh the parent cached values
                cacheValues();
                buildLists();
                updateSummaries();

                // and update any listeners
                for (OnSharedPreferenceChangeListener listener : listeners) {
                    listener.onSharedPreferenceChanged(DeckPreferenceHack.this, null);
                }

                return true;
            }


            @Override
            public SharedPreferences.Editor putBoolean(String key, boolean value) {
                mUpdate.put(key, value);
                return this;
            }


            @Override
            public SharedPreferences.Editor putFloat(String key, float value) {
                mUpdate.put(key, value);
                return this;
            }


            @Override
            public SharedPreferences.Editor putInt(String key, int value) {
                mUpdate.put(key, value);
                return this;
            }


            @Override
            public SharedPreferences.Editor putLong(String key, long value) {
                mUpdate.put(key, value);
                return this;
            }


            @Override
            public SharedPreferences.Editor putString(String key, String value) {
                mUpdate.put(key, value);
                return this;
            }


            @Override
            public SharedPreferences.Editor remove(String key) {
                Timber.d("Editor.remove(key=%s)", key);
                mUpdate.remove(key);
                return this;
            }


            public void apply() {
                commit();
            }


            // @Override On Android 1.5 this is not Override
            public android.content.SharedPreferences.Editor putStringSet(String arg0, Set<String> arg1) {
                // TODO Auto-generated method stub
                return null;
            }

            public DeckPreferenceHack getDeckPreferenceHack() {
                return DeckPreferenceHack.this;
            }

            public ConfChangeHandler confChangeHandler() {
                return new ConfChangeHandler(DeckPreferenceHack.this);
            }

            /**
             * Remove the currently selected options group
             */
            private void remConf() throws ConfirmModSchemaException {
                // Remove options group, asking user to confirm full sync if necessary
                mCol.getDecks().remConf(mOptions.getLong("id"));
                // Run the CPU intensive re-sort operation in a background thread
                TaskManager.launchCollectionTask(new CollectionTask.ConfRemove(mOptions), confChangeHandler());
                mDeck.put("conf", 1);
            }
        }

        @Override
        public boolean contains(String key) {
            return mValues.containsKey(key);
        }


        @Override
        public Editor edit() {
            return new Editor();
        }


        @Override
        public Map<String, ?> getAll() {
            return mValues;
        }


        @Override
        public boolean getBoolean(String key, boolean defValue) {
            return Boolean.parseBoolean(this.getString(key, Boolean.toString(defValue)));
        }


        @Override
        public float getFloat(String key, float defValue) {
            return Float.parseFloat(this.getString(key, Float.toString(defValue)));
        }


        @Override
        public int getInt(String key, int defValue) {
            return Integer.parseInt(this.getString(key, Integer.toString(defValue)));
        }


        @Override
        public long getLong(String key, long defValue) {
            return Long.parseLong(this.getString(key, Long.toString(defValue)));
        }


        @Override
        public String getString(String key, String defValue) {
            Timber.d("getString(key=%s, defValue=%s)", key, defValue);
            if (!mValues.containsKey(key)) {
                return defValue;
            }
            return mValues.get(key);
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            listeners.add(listener);
        }


        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            listeners.remove(listener);
        }


        // @Override On Android 1.5 this is not Override
        public Set<String> getStringSet(String arg0, Set<String> arg1) {
            // TODO Auto-generated method stub
            return null;
        }

    }

    private static class ConfChangeHandler extends TaskListenerWithContext<DeckPreferenceHack, Void, Boolean> {
        public ConfChangeHandler(DeckPreferenceHack deckPreferenceHack) {
            super(deckPreferenceHack);
        }

        @Override
        public void actualOnPreExecute(@NonNull DeckPreferenceHack deckPreferenceHack) {
            Resources res = deckPreferenceHack.getDeckOptions().getResources();
            deckPreferenceHack.mProgressDialog = StyledProgressDialog.show(deckPreferenceHack.getDeckOptions(), null,
                    res.getString(R.string.reordering_cards), false);
        }


        @Override
        public void actualOnPostExecute(@NonNull DeckPreferenceHack deckPreferenceHack, Boolean result) {
            deckPreferenceHack.cacheValues();
            deckPreferenceHack.getDeckOptions().buildLists();
            deckPreferenceHack.getDeckOptions().updateSummaries();
            deckPreferenceHack.mProgressDialog.dismiss();
            // Restart to reflect the new preference values
            deckPreferenceHack.getDeckOptions().restartActivity();
        }
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        Timber.d("getSharedPreferences(name=%s)", name);
        return mPref;
    }


    @Override
    @SuppressWarnings("deprecation") // conversion to fragments tracked as #5019 in github
    protected void onCreate(Bundle icicle) {
        Themes.setThemeLegacy(this);
        super.onCreate(icicle);

        mCol = CollectionHelper.getInstance().getCol(this);
        if (mCol == null) {
            finish();
            return;
        }
        Bundle extras = getIntent().getExtras();
        if (extras != null && extras.containsKey("did")) {
            mDeck = mCol.getDecks().get(extras.getLong("did"));
        } else {
            mDeck = mCol.getDecks().current();
        }
        registerExternalStorageListener();

        if (mCol == null) {
            Timber.w("DeckOptions - No Collection loaded");
            finish();
        } else {
            mPref = new DeckPreferenceHack();
            //#6068 - constructor can call finish()
            if (this.isFinishing()) {
                return;
            }
            mPref.registerOnSharedPreferenceChangeListener(this);

            this.addPreferencesFromResource(R.xml.deck_options);
            if (this.isSchedV2()) {
                this.enableSchedV2Preferences();
            }
            this.buildLists();
            this.updateSummaries();
            // Set the activity title to include the name of the deck
            String title = getResources().getString(R.string.deckpreferences_title);
            if (title.contains("XXX")) {
                try {
                    title = title.replace("XXX", mDeck.getString("name"));
                } catch (JSONException e) {
                    title = title.replace("XXX", "???");
                }
            }
            this.setTitle(title);
        }

        // Add a home button to the actionbar
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    }

    @Override
    @SuppressWarnings("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            closeWithResult();
            return true;
        }
        return false;
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // update values on changed preference
        mPreferenceChanged = true;
        this.updateSummaries();
    }

    // Workaround for bug 4611: http://code.google.com/p/android/issues/detail?id=4611
    @SuppressWarnings("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
    @Override
    public boolean onPreferenceTreeClick(android.preference.PreferenceScreen preferenceScreen, android.preference.Preference preference)
    {
        super.onPreferenceTreeClick(preferenceScreen, preference);
        if (preference instanceof android.preference.PreferenceScreen &&
                ((android.preference.PreferenceScreen) preference).getDialog() != null) {
                    ((android.preference.PreferenceScreen) preference).getDialog().getWindow().getDecorView().setBackgroundDrawable(
                            this.getWindow().getDecorView().getBackground().getConstantState().newDrawable());
        }

        return false;
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Timber.i("DeckOptions - onBackPressed()");
            closeWithResult();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void closeWithResult() {
        if (mPreferenceChanged) {
            setResult(RESULT_OK);
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
        ActivityTransitionAnimation.slide(this, FADE);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
    }


    @SuppressWarnings("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
    protected void updateSummaries() {
        Resources res = getResources();
        // for all text preferences, set summary as current database value
        for (String key : mPref.mValues.keySet()) {
            android.preference.Preference pref = this.findPreference(key);
            if ("deckConf".equals(key)) {
                String groupName = getOptionsGroupName();
                int count = getOptionsGroupCount();
                // Escape "%" in groupName as it's treated as a token
                groupName = groupName.replaceAll("%", "%%");
                pref.setSummary(res.getQuantityString(R.plurals.deck_conf_group_summ, count, groupName, count));
                continue;
            }

            String value = null;
            if (pref == null) {
                continue;
            } else if (pref instanceof android.preference.CheckBoxPreference) {
                continue;
            } else if (pref instanceof android.preference.ListPreference) {
                android.preference.ListPreference lp = (android.preference.ListPreference) pref;
                value = lp.getEntry() != null ? lp.getEntry().toString() : "";
            } else {
                value = this.mPref.getString(key, "");
            }
            // update summary
            if (!mPref.mSummaries.containsKey(key)) {
                CharSequence s = pref.getSummary();
                mPref.mSummaries.put(key, s != null ? pref.getSummary().toString() : null);
            }
            String summ = mPref.mSummaries.get(key);
            if (summ != null && summ.contains("XXX")) {
                pref.setSummary(summ.replace("XXX", value));
            } else {
                pref.setSummary(value);
            }
        }
        // Update summaries of preference items that don't have values (aren't in mValues)
        int subDeckCount = getSubdeckCount();
        this.findPreference("confSetSubdecks").setSummary(
                res.getQuantityString(R.plurals.deck_conf_set_subdecks_summ, subDeckCount, subDeckCount));
    }


    @SuppressWarnings("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
    protected void buildLists() {
        android.preference.ListPreference deckConfPref = (android.preference.ListPreference) findPreference("deckConf");
        ArrayList<DeckConfig> confs = mCol.getDecks().allConf();
        Collections.sort(confs, NamedJSONComparator.instance);
        String[] confValues = new String[confs.size()];
        String[] confLabels = new String[confs.size()];
        for (int i = 0; i < confs.size(); i++) {
            DeckConfig o = confs.get(i);
            confValues[i] = o.getString("id");
            confLabels[i] = o.getString("name");
        }
        deckConfPref.setEntries(confLabels);
        deckConfPref.setEntryValues(confValues);
        deckConfPref.setValue(mPref.getString("deckConf", "0"));

        android.preference.ListPreference newOrderPref = (android.preference.ListPreference) findPreference("newOrder");
        newOrderPref.setEntries(R.array.new_order_labels);
        newOrderPref.setEntryValues(R.array.new_order_values);
        newOrderPref.setValue(mPref.getString("newOrder", "0"));

        android.preference.ListPreference leechActPref = (android.preference.ListPreference) findPreference("lapLeechAct");
        leechActPref.setEntries(R.array.leech_action_labels);
        leechActPref.setEntryValues(R.array.leech_action_values);
        leechActPref.setValue(mPref.getString("lapLeechAct", Integer.toString(Consts.LEECH_SUSPEND)));
    }


    private boolean isSchedV2() {
        return mCol.schedVer() == 2;
    }


    /**
     * Enable deck preferences that are only available with Scheduler V2.
     */
    @SuppressWarnings("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
    protected void enableSchedV2Preferences() {
            NumberRangePreference hardFactorPreference = (NumberRangePreference) findPreference("hardFactor");
            hardFactorPreference.setEnabled(true);
    }


    /**
     * Returns the number of decks using the options group of the current deck.
     */
    private int getOptionsGroupCount() {
        int count = 0;
        long conf = mDeck.getLong("conf");
        for (Deck deck : mCol.getDecks().all()) {
            if (deck.isDyn()) {
                continue;
            }
            if (deck.getLong("conf") == conf) {
                count++;
            }
        }
        return count;
    }


    /**
     * Get the name of the currently set options group
     */
    private String getOptionsGroupName() {
        long confId = mPref.getLong("deckConf", 0);
        return mCol.getDecks().getConf(confId).getString("name");
    }


    /**
     * Get the number of (non-dynamic) subdecks for the current deck
     */
    private int getSubdeckCount() {
        int count = 0;
        long did = mDeck.getLong("id");
        TreeMap<String, Long> children = mCol.getDecks().children(did);
        for (long childDid : children.values()) {
            Deck child = mCol.getDecks().get(childDid);
            if (child.isDyn()) {
                continue;
            }
            count++;
        }
        return count;
    }


    /**
     * finish when sd card is ejected
     */
    private void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (SdCardReceiver.MEDIA_EJECT.equals(intent.getAction())) {
                        finish();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(SdCardReceiver.MEDIA_EJECT);
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }


    private void restartActivity() {
        recreate();
    }

    public static Calendar reminderToCalendar(Time time, JSONObject reminder) {

        final Calendar calendar = time.calendar();

        calendar.set(Calendar.HOUR_OF_DAY, reminder.getJSONArray("time").getInt(0));
        calendar.set(Calendar.MINUTE, reminder.getJSONArray("time").getInt(1));
        calendar.set(Calendar.SECOND, 0);
        return calendar;
    }
}
