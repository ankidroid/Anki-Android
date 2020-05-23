
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

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.view.KeyEvent;
import android.view.MenuItem;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.libanki.Collection;
import com.ichi2.preferences.StepsPreference;
import com.ichi2.themes.Themes;
import com.ichi2.ui.AppCompatPreferenceActivity;
import com.ichi2.anki.analytics.UsageAnalytics;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import timber.log.Timber;

/**
 * Preferences for the current deck.
 */
public class FilteredDeckOptions extends AppCompatPreferenceActivity implements OnSharedPreferenceChangeListener {

    private JSONObject mDeck;
    private Collection mCol;
    private boolean mAllowCommit = true;
    private boolean mPrefChanged = false;

    private BroadcastReceiver mUnmountReceiver = null;

    // TODO: not anymore used in libanki?
    private String[] dynExamples = new String[] { null,
            "{'search'=\"is:new\", 'resched'=False, 'steps'=\"1\", 'order'=5}",
            "{'search'=\"added:1\", 'resched'=False, 'steps'=\"1\", 'order'=5}",
            "{'search'=\"rated:1:1\", 'order'=4}",
            "{'search'=\"prop:due<=2\", 'order'=6}",
            "{'search'=\"is:due tag:TAG\", 'order'=6}",
            "{'search'=\"is:due\", 'order'=3}",
            "{'search'=\"\", 'steps'=\"1 10 20\", 'order'=0}" };

    public class DeckPreferenceHack implements SharedPreferences {

        private Map<String, String> mValues = new HashMap<>();
        private Map<String, String> mSummaries = new HashMap<>();


        public DeckPreferenceHack() {
            this.cacheValues();
        }


        protected void cacheValues() {
            Timber.d("cacheValues()");

            JSONArray ar = mDeck.getJSONArray("terms").getJSONArray(0);
            mValues.put("search", ar.getString(0));
            mValues.put("limit", ar.getString(1));
            mValues.put("order", ar.getString(2));
            JSONArray delays = mDeck.optJSONArray("delays");
            if (delays != null) {
                mValues.put("steps", StepsPreference.convertFromJSON(delays));
                mValues.put("stepsOn", Boolean.toString(true));
            } else {
                mValues.put("steps", "1 10");
                mValues.put("stepsOn", Boolean.toString(false));
            }
            mValues.put("resched", Boolean.toString(mDeck.getBoolean("resched")));
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
                Timber.d("commit() changes back to database");

                for (Entry<String, Object> entry : mUpdate.valueSet()) {
                    Timber.i("Change value for key '" + entry.getKey() + "': " + entry.getValue());
                    if ("search".equals(entry.getKey())) {
                        JSONArray ar = mDeck.getJSONArray("terms");
                        ar.getJSONArray(0).put(0, entry.getValue());
                        mDeck.put("terms", ar);
                    } else if ("limit".equals(entry.getKey())) {
                        JSONArray ar = mDeck.getJSONArray("terms");
                        ar.getJSONArray(0).put(1, entry.getValue());
                        mDeck.put("terms", ar);
                    } else if ("order".equals(entry.getKey())) {
                        JSONArray ar = mDeck.getJSONArray("terms");
                        ar.getJSONArray(0).put(2, Integer.parseInt((String) entry.getValue()));
                        mDeck.put("terms", ar);
                    } else if ("resched".equals(entry.getKey())) {
                        mDeck.put("resched", entry.getValue());
                    } else if ("stepsOn".equals(entry.getKey())) {
                        boolean on = (Boolean) entry.getValue();
                        if (on) {
                            JSONArray steps =  StepsPreference.convertToJSON(mValues.get("steps"));
                            if (steps.length() > 0) {
                                mDeck.put("delays", steps);
                            }
                        } else {
                            mDeck.put("delays", JSONObject.NULL);
                        }
                    } else if ("steps".equals(entry.getKey())) {
                        mDeck.put("delays", StepsPreference.convertToJSON((String) entry.getValue()));
                    } else if ("preset".equals(entry.getKey())) {
                        int i = Integer.parseInt((String) entry.getValue());
                        if (i > 0) {
                            JSONObject presetValues = new JSONObject(dynExamples[i]);
                            JSONArray ar = presetValues.names();
                            for (int j = 0; j < ar.length(); j++) {
                                String name = ar.getString(j);
                                if ("steps".equals(name)) {
                                    mUpdate.put("stepsOn", true);
                                }
                                if ("resched".equals(name)) {
                                    mUpdate.put(name, presetValues.getBoolean(name));
                                    mValues.put(name, Boolean.toString(presetValues.getBoolean(name)));
                                } else {
                                    mUpdate.put(name, presetValues.getString(name));
                                    mValues.put(name, presetValues.getString(name));
                                }
                            }
                            mUpdate.put("preset", "0");
                            commit();
                        }
                    }
                }

                // save deck
                try {
                    mCol.getDecks().save(mDeck);
                } catch (RuntimeException e) {
                    Timber.e(e, "RuntimeException on saving deck");
                    AnkiDroidApp.sendExceptionReport(e, "FilteredDeckOptionsSaveDeck");
                    setResult(DeckPicker.RESULT_DB_ERROR);
                    finish();
                }

                // make sure we refresh the parent cached values
                cacheValues();
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
                if (mAllowCommit) {
                    commit();
                }
            }


            // @Override On Android 1.5 this is not Override
            public android.content.SharedPreferences.Editor putStringSet(String arg0, Set<String> arg1) {
                // TODO Auto-generated method stub
                return null;
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
    @SuppressWarnings("deprecation") // Tracked as #5019 on github
    protected void onCreate(Bundle icicle) {
        Themes.setThemeLegacy(this);
        super.onCreate(icicle);
        UsageAnalytics.sendAnalyticsScreenView(this);

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

        if (mCol == null || mDeck.getInt("dyn") != 1) {
            Timber.w("No Collection loaded or deck is not a dyn deck");
            finish();
            return;
        } else {
            mPref = new DeckPreferenceHack();
            mPref.registerOnSharedPreferenceChangeListener(this);

            this.addPreferencesFromResource(R.xml.cram_deck_options);
            this.buildLists();
            this.updateSummaries();
        }
        
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

        // Add a home button to the actionbar
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                closeDeckOptions();
                return true;
        }
        return false;
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // update values on changed preference
        this.updateSummaries();
        mPrefChanged = true;
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Timber.i("DeckOptions - onBackPressed()");
            closeDeckOptions();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void closeDeckOptions() {
        if (mPrefChanged) {
            // Rebuild the filtered deck if a setting has changed
            try {
                mCol.getSched().rebuildDyn(mDeck.getLong("id"));
            } catch (JSONException e) {
                Timber.e(e);
            }
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


    @SuppressWarnings("deprecation") // conversion to fragments tracked in github as #5019
    protected void updateSummaries() {
        mAllowCommit = false;
        // for all text preferences, set summary as current database value
        for (String key : mPref.mValues.keySet()) {
            Preference pref = this.findPreference(key);
            String value;
            if (pref == null) {
                continue;
            } else if (pref instanceof CheckBoxPreference) {
                continue;
            } else if (pref instanceof ListPreference) {
                ListPreference lp = (ListPreference) pref;
                CharSequence entry = lp.getEntry();
                if (entry != null) {
                    value = entry.toString();
                } else {
                    value = "";
                }
            } else {
                value = this.mPref.getString(key, "");
            }
            // update value for EditTexts
            if (pref instanceof EditTextPreference) {
                ((EditTextPreference) pref).setText(value);
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
        mAllowCommit = true;
    }


    @SuppressWarnings("deprecation") // Tracked as #5019 on github
    protected void buildLists() {
        ListPreference newOrderPref = (ListPreference) findPreference("order");
        newOrderPref.setEntries(R.array.cram_deck_conf_order_labels);
        newOrderPref.setEntryValues(R.array.cram_deck_conf_order_values);
        newOrderPref.setValue(mPref.getString("order", "0"));
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

}
