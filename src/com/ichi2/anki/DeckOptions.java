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

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.ichi2.anki2.R;
import com.ichi2.libanki.Collection;

/**
 * Preferences for the current deck.
 */
public class DeckOptions extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	private JSONObject mOptions;
	private JSONObject mDeck;

	public class DeckPreferenceHack implements SharedPreferences {

        private Map<String, String> mValues = new HashMap<String, String>();

        public DeckPreferenceHack(Collection col) {
            this.cacheValues(col);
        }

        protected void cacheValues(Collection col) {
            Log.i(AnkiDroidApp.TAG, "DeckOptions - CacheValues");

            mDeck = col.getDecks().current();
            try {
				mOptions = col.getDecks().confForDid(mDeck.getLong("id"));
	            mValues.put("name", mDeck.getString("name"));
	            mValues.put("desc", mDeck.getString("desc"));
	            mValues.put("maxAnswerTime", mOptions.getString("maxTaken"));
	            mValues.put("showAnswerTime", mOptions.getString("timer"));
	            mValues.put("autoPlayAudio", Boolean.toString(mOptions.getBoolean("autoplay")));
			// new
	            JSONObject newOptions = mOptions.getJSONObject("new");
	            mValues.put("newSteps", getDelays(newOptions.getJSONArray("delays")));
	            mValues.put("newGradIvl", newOptions.getJSONArray("ints").getString(0));
	            mValues.put("newEasy", newOptions.getJSONArray("ints").getString(1));
	            mValues.put("newFactor", Integer.toString(newOptions.getInt("initialFactor") / 10);
	            mValues.put("newOrder", newOptions.getString("order"));
	            mValues.put("newPerDay", newOptions.getString("perDay"));
	            mValues.put("newSeparate", Boolean.toString(newOptions.getBoolean("seperate")));
			// rev
	            JSONObject revOptions = mOptions.getJSONObject("rev");
	            mValues.put("revPerDay", revOptions.getString("perDay"));
	            mValues.put("revOrder", revOptions.getString("order"));
	            mValues.put("revSpaceMax", Integer.toString((int)(revOptions.getDouble("fuzz")*100)));
	            mValues.put("revSpaceMin", revOptions.getString("minSpace"));
	            mValues.put("easyBonus", Integer.toString((int)(revOptions.getDouble("ease4")*100)));
	            mValues.put("revDesForIdx", revOptions.getJSONArray("fi").getString(0));
	            mValues.put("revAssForIdx", revOptions.getJSONArray("fi").getString(1));
			// lapse
	            JSONObject lapOptions = mOptions.getJSONObject("lapse");
	            mValues.put("lapSteps", getDelays(lapOptions.getJSONArray("delays")));
	            mValues.put("lapNewIvl", Integer.toString((int)(revOptions.getDouble("mult")*100)));
	            mValues.put("lapMinIvl", lapOptions.getString("minInt"));
	            mValues.put("lapLeechThres", lapOptions.getString("leechFails"));
	            mValues.put("lapLeechAct", lapOptions.getString("leechAction"));
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
                Log.d(AnkiDroidApp.TAG, "DeckOptions - commit() changes back to database");

                // make sure we refresh the parent cached values
                // cacheValues();

            	try {
			mDeck.put("desc", mUpdate.get("desc"));
			mOptions.put("maxTaken", Integer.parseInt(mUpdate.get("maxAnswerTime")));
			mOptions.put("timer", Integer.parseInt(mUpdate.get("showAnswerTime")));
			mOptions.put("autoplay", Boolean.parseBool(mOptions.get("autoPlayAudio"));
			JSONObject newOptions = mOptions.getJSONObject("new");
			newOptions.put("delays", getDelays(mUpdate.get("newSteps")));
			JSONArray ja = new JSONArray();
			ja.put(Integer.parseInt(Integer.parseInt(mUpdate.get("newGradIvl"))));
			ja.put(Integer.parseInt(Integer.parseInt(mUpdate.get("newEasy"))));
			newOptions.put("ints", ja);
			newOptions.put("initialFactor", (int)(Double.parseDouble(mUpdate.get("newFactor")) * 10));
			newOptions.put("order", Integer.parseInt(mOptions.get("newOrder"));
			newOptions.put("perDay", Integer.parseInt(mOptions.get("newPerDay"));
			newOptions.put("seperate", Boolean.parseBool(mOptions.get("newSeparate"));
			JSONObject revOptions = mOptions.getJSONObject("rev");
			revOptions.put("perDay", Integer.parseInt(mOptions.get("revPerDay"));
			revOptions.put("order", Integer.parseInt(mOptions.get("revOrder"));
			revOptions.put("fuzz", Double.parseDouble(mOptions.get("revSpaceMax") / 100.0d);
			revOptions.put("minSpace", Integer.parseInt(mOptions.get("revSpaceMin"));
			revOptions.put("ease4", Double.parseDouble(mOptions.get("easyBonus") / 100.0d);
			JSONArray ja = new JSONArray();
			ja.put(Integer.parseInt(Integer.parseInt(mUpdate.get("newGradIvl"))));
			ja.put(Integer.parseInt(Integer.parseInt(mUpdate.get("newEasy"))));
			revOptions.put("fi", ja);
			JSONObject lapseOptions = mOptions.getJSONObjec
t("lapse");
			lapseOptions.put("delays", getDelays(mUpdate.get("lapSteps")));
			lapseOptions.put("minInt", Integer.parseInt(mOptions.get("lapMinIvl"));
			lapseOptions.put("leechFails", Integer.parseInt(mOptions.get("lapLeechThres"));
			lapseOptions.put("leechAction", Integer.parseInt(mOptions.get("lapLeechAct"));
			
				} catch (JSONException e) {
					throw new RuntimeException(e);
				}
                // make sure we refresh the parent cached values
//                cacheValues();

                // and update any listeners
                for (OnSharedPreferenceChangeListener listener : listeners) {
                    listener.onSharedPreferenceChanged(DeckPreferenceHack.this, null);
                }

                return true;
            }


            @Override
            public SharedPreferences.Editor putBoolean(String key, boolean value) {
                return this.putString(key, Boolean.toString(value));
            }


            @Override
            public SharedPreferences.Editor putFloat(String key, float value) {
                return this.putString(key, Float.toString(value));
            }


            @Override
            public SharedPreferences.Editor putInt(String key, int value) {
                return this.putString(key, Integer.toString(value));
            }


            @Override
            public SharedPreferences.Editor putLong(String key, long value) {
                return this.putString(key, Long.toString(value));
            }


            @Override
            public SharedPreferences.Editor putString(String key, String value) {
                Log.d(this.getClass().toString(), String.format("Editor.putString(key=%s, value=%s)", key, value));
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


			// @Override  On Android 1.5 this is not Override
			public android.content.SharedPreferences.Editor putStringSet(
					String arg0, Set<String> arg1) {
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


		// @Override  On Android 1.5 this is not Override
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
        super.onCreate(icicle);

        Collection col = Collection.currentCollection();
        if (col == null) {
            Log.i(AnkiDroidApp.TAG, "DeckOptions - No Collection loaded");
            finish();
            return;
        } else {
            mPref = new DeckPreferenceHack(col);
            mPref.registerOnSharedPreferenceChangeListener(this);

            this.addPreferencesFromResource(R.xml.deck_options);
         	this.updateSummaries();
        }
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // update values on changed preference
    	this.updateSummaries();
    }


    protected void updateSummaries() {
        // for all text preferences, set summary as current database value
        for (String key : mPref.mValues.keySet()) {
            Preference pref = this.findPreference(key);
            if (pref == null) {
                continue;
            }
            if (pref instanceof CheckBoxPreference) {
                continue;
            }
            pref.setSummary(this.mPref.getString(key, ""));
        }
    }


    private static String getDelays(JSONArray a) {
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


    private static JSONArray getDelays(String) {
    	JSONArray ja = new JSONArray();
		try {
	    	for (String s : String.split(" ")) {
			ja.put(s);
	    	}
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
    	return ja;
    }

}
