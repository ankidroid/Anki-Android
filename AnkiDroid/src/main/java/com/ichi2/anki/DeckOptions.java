
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
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.text.TextUtils;

import android.view.KeyEvent;
import android.view.MenuItem;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.anki.services.ReminderService;
import com.ichi2.async.DeckTask;
import com.ichi2.libanki.Collection;
import com.ichi2.preferences.StepsPreference;
import com.ichi2.preferences.TimePreference;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;
import com.ichi2.ui.AppCompatPreferenceActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import timber.log.Timber;

/**
 * Preferences for the current deck.
 */
public class DeckOptions extends AppCompatPreferenceActivity implements OnSharedPreferenceChangeListener {

    private JSONObject mOptions;
    private JSONObject mDeck;
    private Collection mCol;
    private boolean mPreferenceChanged = false;

    private BroadcastReceiver mUnmountReceiver = null;

    public class DeckPreferenceHack implements SharedPreferences {

        private Map<String, String> mValues = new HashMap<>();
        private Map<String, String> mSummaries = new HashMap<>();
        private MaterialDialog mProgressDialog;


        public DeckPreferenceHack() {
            this.cacheValues();
        }


        protected void cacheValues() {
            Timber.i("DeckOptions - CacheValues");

            try {
                mOptions = mCol.getDecks().confForDid(mDeck.getLong("id"));

                mValues.put("name", mDeck.getString("name"));
                mValues.put("desc", mDeck.getString("desc"));
                mValues.put("deckConf", mDeck.getString("conf"));
                // general
                mValues.put("maxAnswerTime", mOptions.getString("maxTaken"));
                mValues.put("showAnswerTimer", Boolean.toString(mOptions.getInt("timer") == 1));
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
                mValues.put("easyBonus", Integer.toString((int) (revOptions.getDouble("ease4") * 100)));
                mValues.put("revIvlFct", Integer.toString((int) (revOptions.getDouble("ivlFct") * 100)));
                mValues.put("revMaxIvl", revOptions.getString("maxIvl"));
                mValues.put("revBury", Boolean.toString(revOptions.optBoolean("bury", true)));
                // lapse
                JSONObject lapOptions = mOptions.getJSONObject("lapse");
                mValues.put("lapSteps", StepsPreference.convertFromJSON(lapOptions.getJSONArray("delays")));
                mValues.put("lapNewIvl", Integer.toString((int) (lapOptions.getDouble("mult") * 100)));
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
                    mValues.put("reminderTime", String.format("%1$02d:%2$02d", reminderTime.get(0), reminderTime.get(1)));
                } else {
                    mValues.put("reminderEnabled", "false");
                    mValues.put("reminderTime", TimePreference.DEFAULT_VALUE);
                }
            } catch (JSONException e) {
                finish();
            }
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
                        Timber.i("Change value for key '" + key + "': " + value);

                        if (key.equals("maxAnswerTime")) {
                            mOptions.put("maxTaken", value);
                        } else if (key.equals("newFactor")) {
                            mOptions.getJSONObject("new").put("initialFactor", (Integer) value * 10);
                        } else if (key.equals("newOrder")) {
                            int newValue = Integer.parseInt((String) value);
                            // Sorting is slow, so only do it if we change order
                            int oldValue = mOptions.getJSONObject("new").getInt("order");
                            if (oldValue != newValue) {
                                mOptions.getJSONObject("new").put("order", newValue);
                                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REORDER, mConfChangeHandler,
                                        new DeckTask.TaskData(new Object[] { mOptions }));
                            }
                            mOptions.getJSONObject("new").put("order", Integer.parseInt((String) value));
                        } else if (key.equals("newPerDay")) {
                            mOptions.getJSONObject("new").put("perDay", value);
                        } else if (key.equals("newGradIvl")) {
                            JSONArray ja = new JSONArray(); // [graduating, easy]
                            ja.put(value);
                            ja.put(mOptions.getJSONObject("new").getJSONArray("ints").get(1));
                            mOptions.getJSONObject("new").put("ints", ja);
                        } else if (key.equals("newEasy")) {
                            JSONArray ja = new JSONArray(); // [graduating, easy]
                            ja.put(mOptions.getJSONObject("new").getJSONArray("ints").get(0));
                            ja.put(value);
                            mOptions.getJSONObject("new").put("ints", ja);
                        } else if (key.equals("newBury")) {
                            mOptions.getJSONObject("new").put("bury", value);
                        } else if (key.equals("revPerDay")) {
                            mOptions.getJSONObject("rev").put("perDay", value);
                        } else if (key.equals("easyBonus")) {
                            mOptions.getJSONObject("rev").put("ease4", (Integer) value / 100.0f);
                        } else if (key.equals("revIvlFct")) {
                            mOptions.getJSONObject("rev").put("ivlFct", (Integer) value / 100.0f);
                        } else if (key.equals("revMaxIvl")) {
                            mOptions.getJSONObject("rev").put("maxIvl", value);
                        } else if (key.equals("revBury")) {
                            mOptions.getJSONObject("rev").put("bury", value);
                        } else if (key.equals("lapMinIvl")) {
                            mOptions.getJSONObject("lapse").put("minInt", value);
                        } else if (key.equals("lapLeechThres")) {
                            mOptions.getJSONObject("lapse").put("leechFails", value);
                        } else if (key.equals("lapLeechAct")) {
                            mOptions.getJSONObject("lapse").put("leechAction", Integer.parseInt((String) value));
                        } else if (key.equals("lapNewIvl")) {
                            mOptions.getJSONObject("lapse").put("mult", (Integer) value / 100.0f);
                        } else if (key.equals("showAnswerTimer")) {
                            mOptions.put("timer", (Boolean) value ? 1 : 0);
                        } else if (key.equals("autoPlayAudio")) {
                            mOptions.put("autoplay", value);
                        } else if (key.equals("replayQuestion")) {
                            mOptions.put("replayq", value);
                        } else if (key.equals("desc")) {
                            mDeck.put("desc", value);
                            mCol.getDecks().save(mDeck);
                        } else if (key.equals("newSteps")) {
                            mOptions.getJSONObject("new").put("delays", StepsPreference.convertToJSON((String) value));
                        } else if (key.equals("lapSteps")) {
                            mOptions.getJSONObject("lapse")
                                    .put("delays", StepsPreference.convertToJSON((String) value));
                        } else if (key.equals("deckConf")) {
                            long newConfId = Long.parseLong((String) value);
                            mOptions = mCol.getDecks().getConf(newConfId);
                            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_CONF_CHANGE, mConfChangeHandler,
                                    new DeckTask.TaskData(new Object[] { mDeck, mOptions }));
                        } else if (key.equals("confRename")) {
                            String newName = (String) value;
                            if (!TextUtils.isEmpty(newName)) {
                                mOptions.put("name", newName);
                            }
                        } else if (key.equals("confReset")) {
                            if ((Boolean) value) {
                                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_CONF_RESET, mConfChangeHandler,
                                        new DeckTask.TaskData(new Object[] { mOptions }));
                            }
                        } else if (key.equals("confAdd")) {
                            String newName = (String) value;
                            if (!TextUtils.isEmpty(newName)) {
                                // New config clones current config
                                long id = mCol.getDecks().confId(newName, mOptions.toString());
                                mDeck.put("conf", id);
                                mCol.getDecks().save(mDeck);
                            }
                        } else if (key.equals("confRemove")) {
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
                                            .callback(new MaterialDialog.ButtonCallback() {
                                                @Override
                                                public void onPositive(MaterialDialog dialog) {
                                                    mCol.modSchemaNoCheck();
                                                    try {
                                                        remConf();
                                                    } catch (ConfirmModSchemaException e) {
                                                        // This should never be reached as we just forced modSchema
                                                        throw new RuntimeException(e);
                                                    }
                                                }
                                            })
                                            .build().show();
                                }
                            }
                        } else if (key.equals("confSetSubdecks")) {
                            if ((Boolean) value) {
                                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_CONF_SET_SUBDECKS, mConfChangeHandler,
                                        new DeckTask.TaskData(new Object[] { mDeck, mOptions }));
                            }
                        } else if (key.equals("reminderEnabled")) {
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
                                    (int) mDeck.getLong("id"),
                                    new Intent(getApplicationContext(), ReminderService.class).putExtra
                                            (ReminderService.EXTRA_DECK_ID, mDeck.getLong("id")),
                                    0
                            );

                            alarmManager.cancel(reminderIntent);
                            if ((Boolean) value) {
                                final Calendar calendar = Calendar.getInstance();

                                calendar.set(Calendar.HOUR_OF_DAY, reminder.getJSONArray("time").getInt(0));
                                calendar.set(Calendar.MINUTE, reminder.getJSONArray("time").getInt(1));
                                calendar.set(Calendar.SECOND, 0);

                                alarmManager.setRepeating(
                                        AlarmManager.RTC_WAKEUP,
                                        calendar.getTimeInMillis(),
                                        AlarmManager.INTERVAL_DAY,
                                        reminderIntent
                                );
                            }
                        } else if (key.equals("reminderTime")) {
                            final JSONObject reminder = new JSONObject();

                            reminder.put("enabled", true);
                            reminder.put("time", new JSONArray().put(TimePreference.parseHours((String) value))
                                    .put(TimePreference.parseMinutes((String) value)));

                            mOptions.put("reminder", reminder);

                            final AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
                            final PendingIntent reminderIntent = PendingIntent.getBroadcast(
                                    getApplicationContext(),
                                    (int) mDeck.getLong("id"),
                                    new Intent(getApplicationContext(), ReminderService.class).putExtra
                                            (ReminderService.EXTRA_DECK_ID, mDeck.getLong("id")),
                                    0
                            );

                            alarmManager.cancel(reminderIntent);

                            final Calendar calendar = Calendar.getInstance();

                            calendar.set(Calendar.HOUR_OF_DAY, reminder.getJSONArray("time").getInt(0));
                            calendar.set(Calendar.MINUTE, reminder.getJSONArray("time").getInt(1));
                            calendar.set(Calendar.SECOND, 0);

                            alarmManager.setRepeating(
                                    AlarmManager.RTC_WAKEUP,
                                    calendar.getTimeInMillis(),
                                    AlarmManager.INTERVAL_DAY,
                                    reminderIntent
                            );
                        }
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

                // save conf
                try {
                    mCol.getDecks().save(mOptions);
                } catch (RuntimeException e) {
                    Timber.e("DeckOptions - RuntimeException on saving conf: " + e);
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

            private DeckTask.TaskListener mConfChangeHandler = new DeckTask.TaskListener() {
                @Override
                public void onPreExecute() {
                    Resources res = getResources();
                    mProgressDialog = StyledProgressDialog.show(DeckOptions.this, "",
                            res.getString(R.string.reordering_cards), false);
                }


                @Override
                public void onProgressUpdate(DeckTask.TaskData... values) {
                }


                @Override
                public void onPostExecute(DeckTask.TaskData result) {
                    cacheValues();
                    buildLists();
                    updateSummaries();
                    mProgressDialog.dismiss();
                    // Restart to reflect the new preference values
                    restartActivity();
                }


                @Override
                public void onCancelled() {
                    // TODO Auto-generated method stub
                    
                }
            };

            /**
             * Remove the currently selected options group
             * @throws ConfirmModSchemaException
             */
            private void remConf() throws ConfirmModSchemaException {
                try {
                    // Remove options group, asking user to confirm full sync if necessary
                    mCol.getDecks().remConf(mOptions.getLong("id"));
                    // Run the CPU intensive re-sort operation in a background thread
                    DeckTask.launchDeckTask(DeckTask.TASK_TYPE_CONF_REMOVE, mConfChangeHandler,
                            new DeckTask.TaskData(new Object[] { mOptions }));
                    mDeck.put("conf", 1);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
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
            return Boolean.valueOf(this.getString(key, Boolean.toString(defValue)));
        }


        @Override
        public float getFloat(String key, float defValue) {
            return Float.valueOf(this.getString(key, Float.toString(defValue)));
        }


        @Override
        public int getInt(String key, int defValue) {
            return Integer.valueOf(this.getString(key, Integer.toString(defValue)));
        }


        @Override
        public long getLong(String key, long defValue) {
            return Long.valueOf(this.getString(key, Long.toString(defValue)));
        }


        @Override
        public String getString(String key, String defValue) {
            Timber.d("getString(key=%s, defValue=%s)", key, defValue);
            if (!mValues.containsKey(key)) {
                return defValue;
            }
            return mValues.get(key);
        }

        public List<OnSharedPreferenceChangeListener> listeners = new LinkedList<>();


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

    private DeckPreferenceHack mPref;


    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        Timber.d("getSharedPreferences(name=%s)", name);
        return mPref;
    }


    @Override
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
            mPref.registerOnSharedPreferenceChangeListener(this);

            this.addPreferencesFromResource(R.xml.deck_options);
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
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
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
    @SuppressWarnings("deprecation")
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference)
    {
        super.onPreferenceTreeClick(preferenceScreen, preference);
        if (preference!=null)
            if (preference instanceof PreferenceScreen) {
                if (((PreferenceScreen) preference).getDialog() != null) {
                    ((PreferenceScreen) preference).getDialog().getWindow().getDecorView().setBackgroundDrawable(
                            this.getWindow().getDecorView().getBackground().getConstantState().newDrawable());
                }
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
        ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.FADE);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
    }


    protected void updateSummaries() {
        Resources res = getResources();
        // for all text preferences, set summary as current database value
        for (String key : mPref.mValues.keySet()) {
            Preference pref = this.findPreference(key);
            if (key.equals("deckConf")) {
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
            } else if (pref instanceof CheckBoxPreference) {
                continue;
            } else if (pref instanceof ListPreference) {
                ListPreference lp = (ListPreference) pref;
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


    protected void buildLists() {
        ListPreference deckConfPref = (ListPreference) findPreference("deckConf");
        ArrayList<JSONObject> confs = mCol.getDecks().allConf();
        Collections.sort(confs, new JSONNameComparator());
        String[] confValues = new String[confs.size()];
        String[] confLabels = new String[confs.size()];
        try {
            for (int i = 0; i < confs.size(); i++) {
                JSONObject o = confs.get(i);
                confValues[i] = o.getString("id");
                confLabels[i] = o.getString("name");
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        deckConfPref.setEntries(confLabels);
        deckConfPref.setEntryValues(confValues);
        deckConfPref.setValue(mPref.getString("deckConf", "0"));

        ListPreference newOrderPref = (ListPreference) findPreference("newOrder");
        newOrderPref.setEntries(R.array.new_order_labels);
        newOrderPref.setEntryValues(R.array.new_order_values);
        newOrderPref.setValue(mPref.getString("newOrder", "0"));

        ListPreference leechActPref = (ListPreference) findPreference("lapLeechAct");
        leechActPref.setEntries(R.array.leech_action_labels);
        leechActPref.setEntryValues(R.array.leech_action_values);
        leechActPref.setValue(mPref.getString("lapLeechAct", "0"));
    }


    /**
     * Returns the number of decks using the options group of the current deck.
     */
    private int getOptionsGroupCount() {
        int count = 0;
        try {
            long conf = mDeck.getLong("conf");
            for (JSONObject deck : mCol.getDecks().all()) {
                if (deck.getInt("dyn") == 1) {
                    continue;
                }
                if (deck.getLong("conf") == conf) {
                    count++;
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return count;
    }


    /**
     * Get the name of the currently set options group
     */
    private String getOptionsGroupName() {
        long confId = mPref.getLong("deckConf", 0);
        try {
            return mCol.getDecks().getConf(confId).getString("name");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Get the number of (non-dynamic) subdecks for the current deck
     */
    private int getSubdeckCount() {
        try {
            int count = 0;
            long did = mDeck.getLong("id");
            TreeMap<String, Long> children = mCol.getDecks().children(did);
            for (Map.Entry<String, Long> entry : children.entrySet()) {
                JSONObject child = mCol.getDecks().get(entry.getValue());
                if (child.getInt("dyn") == 1) {
                    continue;
                }
                count++;
            }
            return count;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public class JSONNameComparator implements Comparator<JSONObject> {
        @Override
        public int compare(JSONObject lhs, JSONObject rhs) {
            String o1;
            String o2;
            try {
                o1 = lhs.getString("name");
                o2 = rhs.getString("name");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            return o1.compareToIgnoreCase(o2);
        }
    }


    /**
     * finish when sd card is ejected
     */
    private void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(SdCardReceiver.MEDIA_EJECT)) {
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
}
