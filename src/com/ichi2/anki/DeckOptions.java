
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
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import com.ichi2.async.DeckTask;
import com.ichi2.libanki.Collection;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;

/**
 * Preferences for the current deck.
 */
public class DeckOptions extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    private JSONObject mOptions;
    private JSONObject mDeck;
    private Collection mCol;

    private BroadcastReceiver mUnmountReceiver = null;

    public class OptionRange {
        public int min;
        public int max;
        
        public OptionRange(int min, int max) {
            this.min = min;
            this.max = max;
        }
    }
        
    public class DeckPreferenceHack implements SharedPreferences {

        private Map<String, String> mValues = new HashMap<String, String>();
        private Map<String, String> mSummaries = new HashMap<String, String>();
        private StyledProgressDialog mProgressDialog;
        private Map<String, OptionRange> mNumericOptions = new HashMap<String, OptionRange>();
        private Set<String> mBoolOptions = new HashSet<String>();

        public DeckPreferenceHack() {
            mNumericOptions.put("maxAnswerTime", new OptionRange(30, 3600));
            mNumericOptions.put("newFactor", new OptionRange(100, 999));
            mNumericOptions.put("newOrder", new OptionRange(0, Integer.MAX_VALUE)); // list preference
            mNumericOptions.put("newPerDay", new OptionRange(0, 9999));
            mNumericOptions.put("newGradIvl", new OptionRange(1, 99));
            mNumericOptions.put("newEasy", new OptionRange(1, 99));
            mNumericOptions.put("revPerDay", new OptionRange(0, 9999));
            mNumericOptions.put("revSpaceMax", new OptionRange(0, 100));
            mNumericOptions.put("revSpaceMin", new OptionRange(0, 99));
            mNumericOptions.put("easyBonus", new OptionRange(100, 1000));
            mNumericOptions.put("revIvlFct", new OptionRange(0, 999));
            mNumericOptions.put("revMaxIvl", new OptionRange(1, 99999));
            mNumericOptions.put("lapMinIvl", new OptionRange(1, 99));
            mNumericOptions.put("lapLeechThres", new OptionRange(0, 99));
            mNumericOptions.put("lapLeechAct", new OptionRange(0, Integer.MAX_VALUE)); // list preference
            mNumericOptions.put("lapNewIvl", new OptionRange(0, 100));
            
            mBoolOptions.add("showAnswerTimer");
            mBoolOptions.add("autoPlayAudio");
            mBoolOptions.add("replayQuestion");
            mBoolOptions.add("newSeparate");
                        
            this.cacheValues();
        }

        public int getValidatedNumericInput(String key, Object value) {
            String valueString = (String) value;
            int valueInt;
            
            if (TextUtils.isEmpty(valueString)) {
                // Resort to using currently set value if user enters nothing
                valueInt = Integer.parseInt(mValues.get(key));
            } else {
                valueInt = Integer.parseInt(valueString);
            }

            if (valueInt < mNumericOptions.get(key).min) {
                return mNumericOptions.get(key).min;
            } else if (valueInt > mNumericOptions.get(key).max) {
                return mNumericOptions.get(key).max;
            } else {
                return valueInt;
            }
        }
        
        public JSONArray getValidatedStepsInput(String steps) {
            JSONArray ja = new JSONArray();
            if (TextUtils.isEmpty(steps)) {
                return ja;
            }
            for (String s : steps.split("\\s+")) {
                try {
                    float f = Float.parseFloat(s);
                    // Steps can't be 0, negative, or non-serializable (too large), so it's invalid
                    if (f <= 0 || Float.isInfinite(f)) {
                        return null;
                    }
                    // Use whole numbers if we can (but still allow decimals)
                    int i = (int)f;
                    if (i == f) {
                        ja.put(i);
                    } else {
                        ja.put(f);
                    }
                } catch (NumberFormatException e) {
                    return null;
                } catch (JSONException e) {
                    throw new RuntimeException();
                }
            }
            return ja;
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
                mValues.put("newSteps", getSteps(newOptions.getJSONArray("delays")));
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
                mValues.put("revIvlFct", Integer.toString((int) (revOptions.getDouble("ivlFct") * 100)));
                mValues.put("revMaxIvl", revOptions.getString("maxIvl"));
                // lapse
                JSONObject lapOptions = mOptions.getJSONObject("lapse");
                mValues.put("lapSteps", getSteps(lapOptions.getJSONArray("delays")));
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
                        String key =  entry.getKey();
                        Log.i(AnkiDroidApp.TAG, "Change value for key '" + key + "': " + entry.getValue());

                        if (mNumericOptions.containsKey(key)) {
                            // All numeric options must be non-empty and within range.
                            int value = getValidatedNumericInput(key, entry.getValue());
                        
                            if (key.equals("maxAnswerTime")) {
                                mOptions.put("maxTaken", value);
                            } else if (key.equals("newFactor")) {
                                mOptions.getJSONObject("new").put("initialFactor", value * 10);
                            } else if (key.equals("newOrder")) {
                                // Sorting is slow, so only do it if we change order
                                int oldValue = mOptions.getJSONObject("new").getInt("order");
                                if (oldValue != value) {
                                    mOptions.getJSONObject("new").put("order", value);
                                    DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REORDER, mReorderHandler,
                                            new DeckTask.TaskData(new Object[]{mCol, mOptions}));
                                }
                                mOptions.getJSONObject("new").put("order", value);
                            } else if (key.equals("newPerDay")) {
                                mOptions.getJSONObject("new").put("perDay", value);
                            } else if (key.equals("newGradIvl")) {
                                JSONArray ja = new JSONArray(); //[graduating, easy]
                                ja.put(value);
                                ja.put(mOptions.getJSONObject("new").getJSONArray("ints").get(1));
                                mOptions.getJSONObject("new").put("ints", ja);
                            } else if (key.equals("newEasy")) {
                                JSONArray ja = new JSONArray(); //[graduating, easy]
                                ja.put(mOptions.getJSONObject("new").getJSONArray("ints").get(0));
                                ja.put(value);
                                mOptions.getJSONObject("new").put("ints", ja);
                            } else if (key.equals("revPerDay")) {
                                mOptions.getJSONObject("rev").put("perDay", value);
                            } else if (key.equals("revSpaceMax")) {
                                mOptions.getJSONObject("rev").put("fuzz", value / 100.0f);
                            } else if (key.equals("revSpaceMin")) {
                                mOptions.getJSONObject("rev").put("minSpace", value);
                            } else if (key.equals("easyBonus")) {
                                mOptions.getJSONObject("rev").put("ease4", value / 100.0f);
                            } else if (key.equals("revIvlFct")) {
                                mOptions.getJSONObject("rev").put("ivlFct", value / 100.0f);
                            } else if (key.equals("revMaxIvl")) {
                                mOptions.getJSONObject("rev").put("maxIvl", value);
                            } else if (key.equals("lapMinIvl")) {
                                mOptions.getJSONObject("lapse").put("minInt", value);
                            } else if (key.equals("lapLeechThres")) {
                                mOptions.getJSONObject("lapse").put("leechFails", value);
                            } else if (key.equals("lapLeechAct")) {
                                mOptions.getJSONObject("lapse").put("leechAction", value);
                            } else if (key.equals("lapNewIvl")) {
                                mOptions.getJSONObject("lapse").put("mult", value / 100.0f);
                            }
                        } else if (mBoolOptions.contains(key)){
                            boolean enabled = (Boolean) entry.getValue();
                            
                            if (key.equals("showAnswerTimer")) {
                                mOptions.put("timer", enabled ? 1 : 0);
                            } else if (key.equals("autoPlayAudio")) {
                                mOptions.put("autoplay", enabled);
                            } else if (key.equals("replayQuestion")) {
                                mOptions.put("replayq", enabled);
                            } else if (key.equals("newSeparate")) {
                                mOptions.getJSONObject("new").put("separate", enabled);
                            }
                        } else {
                            // The rest of the options are all strings
                            String value =  (String) entry.getValue();
                            
                            if (key.equals("name")) {
                                if (!mCol.getDecks().rename(mDeck, value)) {
                                    Themes.showThemedToast(DeckOptions.this,
                                            getResources().getString(R.string.rename_error, mDeck.get("name")), false);
                                }
                            } else if (key.equals("desc")) {
                                mDeck.put("desc", value);
                                mCol.getDecks().save(mDeck);
                            } else if (key.equals("deckConf")) {
                                mCol.getDecks().setConf(mDeck, Long.parseLong(value));
                                mOptions = mCol.getDecks().confForDid(mDeck.getLong("id"));
                            } else if (key.equals("newSteps")) {
                                JSONArray steps = getValidatedStepsInput(value);
                                if (steps == null) {
                                    Themes.showThemedToast(DeckOptions.this,
                                            getResources().getString(R.string.steps_error), false);
                                } else if (steps.length() == 0) {
                                    Themes.showThemedToast(DeckOptions.this,
                                            getResources().getString(R.string.steps_min_error), false);
                                } else {
                                    mOptions.getJSONObject("new").put("delays", steps);
                                }
                            } else if (key.equals("lapSteps")) {
                                JSONArray steps = getValidatedStepsInput(value);
                                if (steps != null) {
                                    mOptions.getJSONObject("lapse").put("delays", steps);
                                } else {
                                    Themes.showThemedToast(DeckOptions.this,
                                            getResources().getString(R.string.steps_error), false);
                                }
                            }
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
            
            private DeckTask.TaskListener mReorderHandler = new DeckTask.TaskListener() {
                @Override
                public void onPreExecute() {
                    Resources res = getResources();
                    mProgressDialog = StyledProgressDialog.show(DeckOptions.this, "",
                            res.getString(R.string.reordering_cards), true);
                }


                @Override
                public void onProgressUpdate(DeckTask.TaskData... values) {
                }

                
                @Override
                public void onPostExecute(DeckTask.TaskData result) {
                    mProgressDialog.dismiss();
                }
            };

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


    public static String getSteps(JSONArray a) {
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
