
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
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Collections;
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
public class DeckOptions extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    private JSONObject mOptions;
    private JSONObject mDeck;
    private Collection mCol;

    private BroadcastReceiver mUnmountReceiver = null;

    public class DeckPreferenceHack implements SharedPreferences {

        private Map<String, String> mValues = new HashMap<String, String>();
        private Map<String, String> mSummaries = new HashMap<String, String>();


        public DeckPreferenceHack() {
            this.cacheValues();
        }


        protected void cacheValues() {
            Log.i(AnkiDroidApp.TAG, "DeckOptions - CacheValues");

            try {
                mOptions = mCol.getDecks().confForDid(mDeck.getLong("id"));

                mValues.put("name", mDeck.getString("name"));
                mValues.put("desc", mDeck.getString("desc"));
                mValues.put("deckConf", mDeck.getString("conf"));
                mValues.put("maxAnswerTime", mOptions.getString("maxTaken"));
                mValues.put("showAnswerTimer", Boolean.toString(mOptions.getInt("timer") == 1));
                mValues.put("autoPlayAudio", Boolean.toString(mOptions.getBoolean("autoplay")));
                mValues.put("replayQuestion", Boolean.toString(mOptions.getBoolean("replayq")));
                // new
                JSONObject newOptions = mOptions.getJSONObject("new");
                mValues.put("newSteps", getDelays(newOptions.getJSONArray("delays")));
                mValues.put("newGradIvl", newOptions.getJSONArray("ints").getString(0));
                mValues.put("newEasy", newOptions.getJSONArray("ints").getString(1));
                mValues.put("newFactor", Integer.toString(newOptions.getInt("initialFactor") / 10));
                mValues.put("newOrder", newOptions.getString("order"));
                mValues.put("newPerDay", newOptions.getString("perDay"));
                mValues.put("newSeparate", Boolean.toString(newOptions.getBoolean("separate")));
                // rev
                JSONObject revOptions = mOptions.getJSONObject("rev");
                mValues.put("revPerDay", revOptions.getString("perDay"));
                mValues.put("revSpaceMax", Integer.toString((int) (revOptions.getDouble("fuzz") * 100)));
                mValues.put("revSpaceMin", revOptions.getString("minSpace"));
                mValues.put("easyBonus", Integer.toString((int) (revOptions.getDouble("ease4") * 100)));
                mValues.put("revIvlFct", revOptions.getString("ivlFct"));
                mValues.put("revMaxIvl", revOptions.getString("maxIvl"));
                // lapse
                JSONObject lapOptions = mOptions.getJSONObject("lapse");
                mValues.put("lapSteps", getDelays(lapOptions.getJSONArray("delays")));
                mValues.put("lapNewIvl", Integer.toString((int) (lapOptions.getDouble("mult") * 100)));
                mValues.put("lapMinIvl", lapOptions.getString("minInt"));
                mValues.put("lapLeechThres", lapOptions.getString("leechFails"));
                mValues.put("lapLeechAct", lapOptions.getString("leechAction"));
            } catch (JSONException e) {
            	addMissingValues();
            	finish();
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
                Log.d(AnkiDroidApp.TAG, "DeckOptions - commit() changes back to database");

                try {
                    for (Entry<String, Object> entry : mUpdate.valueSet()) {
                        Log.i(AnkiDroidApp.TAG, "Change value for key '" + entry.getKey() + "': " + entry.getValue());
                        if (entry.getKey().equals("name")) {
                            if (!mCol.getDecks().rename(mDeck, (String) entry.getValue())) {
                                Themes.showThemedToast(DeckOptions.this,
                                        getResources().getString(R.string.rename_error, mDeck.get("name")), false);
                            }
                        } else if (entry.getKey().equals("desc")) {
                            mDeck.put("desc", (String) entry.getValue());
                            mCol.getDecks().save(mDeck);
                        } else if (entry.getKey().equals("deckConf")) {
                            mCol.getDecks().setConf(mDeck, Long.parseLong((String) entry.getValue()));
                            mOptions = mCol.getDecks().confForDid(mDeck.getLong("id"));
                        } else if (entry.getKey().equals("maxAnswerTime")) {
                            mOptions.put("maxTaken", Integer.parseInt((String) entry.getValue()));
                        } else if (entry.getKey().equals("showAnswerTimer")) {
                            mOptions.put("timer", (Boolean) entry.getValue() ? 1 : 0);
                        } else if (entry.getKey().equals("autoPlayAudio")) {
                            mOptions.put("autoplay", (Boolean) entry.getValue());
                        } else if (entry.getKey().equals("replayQuestion")) {
                            mOptions.put("replayq", (Boolean) entry.getValue());
                        } else if (entry.getKey().equals("newSteps")) {
                            String steps = (String) entry.getValue();
                            if (steps.matches("[0-9\\s]*")) {
                                mOptions.getJSONObject("new").put("delays", getDelays(steps));
                            }
                        } else if (entry.getKey().equals("newGradIvl")) {
                            JSONArray ja = new JSONArray();
                            ja.put(Integer.parseInt((String) entry.getValue()));
                            ja.put(mOptions.getJSONObject("new").getJSONArray("ints").get(1));
                            mOptions.getJSONObject("new").put("ints", ja);
                        } else if (entry.getKey().equals("newEasy")) {
                            JSONArray ja = new JSONArray();
                            ja.put(mOptions.getJSONObject("new").getJSONArray("ints").get(0));
                            ja.put(Integer.parseInt((String) entry.getValue()));
                            mOptions.getJSONObject("new").put("ints", ja);
                        } else if (entry.getKey().equals("initialFactor")) {
                            mOptions.getJSONObject("new").put("initialFactor",
                                    (int) (Integer.parseInt((String) entry.getValue()) * 10));
                        } else if (entry.getKey().equals("newOrder")) {
                            mOptions.getJSONObject("new").put("order", Integer.parseInt((String) entry.getValue()));
                        } else if (entry.getKey().equals("newPerDay")) {
                            mOptions.getJSONObject("new").put("perDay", Integer.parseInt((String) entry.getValue()));
                        } else if (entry.getKey().equals("newSeparate")) {
                            mOptions.getJSONObject("new").put("separate", (Boolean) entry.getValue());
                        } else if (entry.getKey().equals("revPerDay")) {
                            mOptions.getJSONObject("rev").put("perDay", Integer.parseInt((String) entry.getValue()));
                        } else if (entry.getKey().equals("revSpaceMax")) {
                            mOptions.getJSONObject("rev").put("fuzz",
                                    Integer.parseInt((String) entry.getValue()) / 100.0f);
                        } else if (entry.getKey().equals("revSpaceMin")) {
                            mOptions.getJSONObject("rev").put("minSpace", Integer.parseInt((String) entry.getValue()));
                        } else if (entry.getKey().equals("easyBonus")) {
                            mOptions.getJSONObject("rev").put("ease4",
                                    Integer.parseInt((String) entry.getValue()) / 100.0f);
                        } else if (entry.getKey().equals("revIvlFct")) {
                            mOptions.getJSONObject("rev").put("ivlFct", Double.parseDouble((String) entry.getValue()));
                        } else if (entry.getKey().equals("revMaxIvl")) {
                            mOptions.getJSONObject("rev").put("maxIvl", Integer.parseInt((String) entry.getValue()));
                        } else if (entry.getKey().equals("lapSteps")) {
                            String steps = (String) entry.getValue();
                            if (steps.matches("[0-9\\s]*")) {
                                mOptions.getJSONObject("lapse").put("delays", getDelays(steps));
                            }
                        } else if (entry.getKey().equals("lapNewIvl")) {
                            mOptions.getJSONObject("lapse").put("mult", Float.parseFloat((String) entry.getValue()));
                        } else if (entry.getKey().equals("lapMinIvl")) {
                            mOptions.getJSONObject("lapse").put("minInt", Integer.parseInt((String) entry.getValue()));
                        } else if (entry.getKey().equals("lapLeechThres")) {
                            mOptions.getJSONObject("lapse").put("leechFails",
                                    Integer.parseInt((String) entry.getValue()));
                        } else if (entry.getKey().equals("lapLeechAct")) {
                            mOptions.getJSONObject("lapse").put("leechAction",
                                    Integer.parseInt((String) entry.getValue()));
                        }
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

                // save conf
                try {
                    mCol.getDecks().save(mOptions);
                } catch (RuntimeException e) {
                    Log.e(AnkiDroidApp.TAG, "DeckOptions - RuntimeException on saving conf: " + e);
                    AnkiDroidApp.saveExceptionReportFile(e, "DeckOptionsSaveConf");
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
                commit();
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

        registerExternalStorageListener();

        if (mCol == null) {
            Log.i(AnkiDroidApp.TAG, "DeckOptions - No Collection loaded");
            finish();
            return;
        } else {
            mPref = new DeckPreferenceHack();
            mPref.registerOnSharedPreferenceChangeListener(this);

            this.addPreferencesFromResource(R.xml.deck_options);
            this.buildLists();
            this.updateSummaries();
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

    private void addMissingValues() {
    	try {
    		for (JSONObject o : mCol.getDecks().all()) {
    			JSONObject conf = mCol.getDecks().confForDid(o.getLong("id"));
    			if (!conf.has("replayq")) {
    				conf.put("replayq", true);
					mCol.getDecks().save(conf);            				
    			}
    		}
		} catch (JSONException e1) {
			// nothing
		}
    }

}
