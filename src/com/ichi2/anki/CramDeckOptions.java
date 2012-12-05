
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
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.KeyEvent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.R;
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.libanki.Collection;
import com.ichi2.themes.Themes;

/**
 * Preferences for the current deck.
 */
public class CramDeckOptions extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    private JSONObject mDeck;
    private Collection mCol;
    private String mPresetSearchSuffix;
    private boolean mAllowCommit = true;

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

        private Map<String, String> mValues = new HashMap<String, String>();
        private Map<String, String> mSummaries = new HashMap<String, String>();


        public DeckPreferenceHack() {
            this.cacheValues();
        }


        protected void cacheValues() {
            Log.i(AnkiDroidApp.TAG, "CramDeckOptions - CacheValues");

            try {
                JSONArray ar = mDeck.getJSONArray("terms").getJSONArray(0);
                mValues.put("search", ar.getString(0));
                mValues.put("limit", ar.getString(1));
                mValues.put("order", ar.getString(2));
                try {
                    mValues.put("steps", getDelays(mDeck.getJSONArray("delays")));
                    mValues.put("stepsOn", Boolean.toString(true));
                } catch (JSONException e) {
                    mValues.put("steps", "1 10");
                    mValues.put("stepsOn", Boolean.toString(false));
                }
                mValues.put("resched", Boolean.toString(mDeck.getBoolean("resched")));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        public class Editor implements SharedPreferences.Editor {

            private ContentValues mUpdate = new ContentValues();

            @Override
            public SharedPreferences.Editor clear() {
                Log.d(AnkiDroidApp.TAG, "clear()");
                mUpdate = new ContentValues();
                return this;
            }


            @Override
            public boolean commit() {
                Log.d(AnkiDroidApp.TAG, "CramDeckOptions - commit() changes back to database");

                try {
                    for (Entry<String, Object> entry : mUpdate.valueSet()) {
                        Log.i(AnkiDroidApp.TAG, "Change value for key '" + entry.getKey() + "': " + entry.getValue());
                        if (entry.getKey().equals("search")) {
                            JSONArray ar = mDeck.getJSONArray("terms");
                            ar.getJSONArray(0).put(0, (String) entry.getValue());
                            mDeck.put("terms", ar);
                        } else if (entry.getKey().equals("limit")) {
                            JSONArray ar = mDeck.getJSONArray("terms");
                            ar.getJSONArray(0).put(1, Integer.parseInt((String) entry.getValue()));
                            mDeck.put("terms", ar);
                        } else if (entry.getKey().equals("order")) {
                            JSONArray ar = mDeck.getJSONArray("terms");
                            ar.getJSONArray(0).put(2, Integer.parseInt((String) entry.getValue()));
                            mDeck.put("terms", ar);
                        } else if (entry.getKey().equals("resched")) {
                            mDeck.put("resched", (Boolean) entry.getValue());
                        } else if (entry.getKey().equals("stepsOn")) {
                            boolean on = (Boolean) entry.getValue();
                            if (on) {
                                mDeck.put("delays", getDelays((String) mValues.get("steps")));
                            } else {
                                mValues.put("steps", "1 10");
                                mDeck.put("delays", new JSONArray());
                            }
                        } else if (entry.getKey().equals("steps")) {
                            String steps = (String) entry.getValue();
                            if (steps.matches("[0-9\\s]*")) {
                                mDeck.put("delays", getDelays(steps));
                            }
                        } else if (entry.getKey().equals("preset")) {
                            int i = Integer.parseInt((String) entry.getValue());
                            if (i > 0) {
                                JSONObject presetValues = new JSONObject(dynExamples[i]);
                                JSONArray ar = presetValues.names();
                                for (int j = 0; j < ar.length(); j++) {
                                    String name = ar.getString(j);
                                    if (name.equals("steps")) {
                                        mUpdate.put("stepsOn", true);
                                    }
                                    if (name.equals("resched")) {
                                        mUpdate.put(name, presetValues.getBoolean(name));
                                        mValues.put(name, Boolean.toString(presetValues.getBoolean(name)));
                                    } else if (name.equals("search")) {
                                        if (mPresetSearchSuffix != null) {
                                            mUpdate.put(name, presetValues.getString(name) + " " + mPresetSearchSuffix);
                                            mValues.put(name, presetValues.getString(name) + " " + mPresetSearchSuffix);
                                        } else {
                                            mUpdate.put(name, presetValues.getString(name));
                                            mValues.put(name, presetValues.getString(name));
                                        }
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
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

                // save deck
                try {
                    mCol.getDecks().save(mDeck);
                } catch (RuntimeException e) {
                    Log.e(AnkiDroidApp.TAG, "CramDeckOptions - RuntimeException on saving deck: " + e);
                    AnkiDroidApp.saveExceptionReportFile(e, "CramDeckOptionsSaveDeck");
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
                Log.d(this.getClass().toString(), String.format("Editor.remove(key=%s)", key));
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
            Log.d(this.getClass().toString(), String.format("getString(key=%s, defValue=%s)", key, defValue));
            if (!mValues.containsKey(key)) {
                return defValue;
            }
            return mValues.get(key);
        }

        public List<OnSharedPreferenceChangeListener> listeners = new LinkedList<OnSharedPreferenceChangeListener>();


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
        Log.d(this.getClass().toString(), String.format("getSharedPreferences(name=%s)", name));
        return mPref;
    }


    @Override
    protected void onCreate(Bundle icicle) {
        // Workaround for bug 4611: http://code.google.com/p/android/issues/detail?id=4611
        // This screen doesn't suffer from this bug really as it doesn't have nested
        // PreferenceScreens, but we have change the background for consistency with
        // DeckOptions background.
        if (AnkiDroidApp.SDK_VERSION >= 7 && AnkiDroidApp.SDK_VERSION <= 10) {
            Themes.applyTheme(this, Themes.THEME_ANDROID_DARK);
        }
        super.onCreate(icicle);

        mCol = AnkiDroidApp.getCol();
        if (mCol == null) {
            finish();
            return;
        }
        mDeck = mCol.getDecks().current();

        Bundle initialConfig = getIntent().getBundleExtra("cramInitialConfig");
        if (initialConfig != null) {
            if (initialConfig.containsKey("searchSuffix")) {
                mPresetSearchSuffix = initialConfig.getString("searchSuffix");
            }
        }

        registerExternalStorageListener();

        try {
            if (mCol == null || mDeck.getInt("dyn") != 1) {
                Log.w(AnkiDroidApp.TAG, "CramDeckOptions - No Collection loaded or deck is not a dyn deck");
                finish();
                return;
            } else {
                mPref = new DeckPreferenceHack();
                mPref.registerOnSharedPreferenceChangeListener(this);

                this.addPreferencesFromResource(R.xml.cram_deck_options);
                this.buildLists();
                this.updateSummaries();
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // update values on changed preference
        this.updateSummaries();
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Log.i(AnkiDroidApp.TAG, "DeckOptions - onBackPressed()");
            finish();
            if (AnkiDroidApp.SDK_VERSION > 4) {
                ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.FADE);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
    }

    protected void updateSummaries() {
        mAllowCommit = false;
        // for all text preferences, set summary as current database value
        for (String key : mPref.mValues.keySet()) {
            Preference pref = this.findPreference(key);
            String value = null;
            if (pref == null) {
                continue;
            } else if (pref instanceof CheckBoxPreference) {
                continue;
            } else if (pref instanceof ListPreference) {
                ListPreference lp = (ListPreference) pref;
                value = lp.getEntry().toString();
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


    protected void buildLists() {
        ListPreference newOrderPref = (ListPreference) findPreference("order");
        newOrderPref.setEntries(R.array.cram_deck_conf_order_labels);
        newOrderPref.setEntryValues(R.array.cram_deck_conf_order_values);
        newOrderPref.setValue(mPref.getString("order", "0"));

        if (mPresetSearchSuffix != null) {
            EditTextPreference searchPref = (EditTextPreference) findPreference("search");
            searchPref.setText(mPresetSearchSuffix);
        }
    }


    public static String getDelays(JSONArray a) {
        StringBuilder sb = new StringBuilder();
        try {
            for (int i = 0; i < a.length(); i++) {
                sb.append(a.getString(i)).append(" ");
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return sb.toString().trim();
    }


    public static JSONArray getDelays(String delays) {
        JSONArray ja = new JSONArray();
        for (String s : delays.split(" ")) {
            ja.put(Integer.parseInt(s));
        }
        return ja;
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

}
