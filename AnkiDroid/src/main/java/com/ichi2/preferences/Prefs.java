/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import com.ichi2.anki.AnkiDroidApp;

public class Prefs {

    // TODO: StringSet - ensure that it does not accept and return a mutable class.
    // COULD_BE_BETTER: Consider improving this pattern via method: "Integer.parseInt(preferences.getString())"

    private final SharedPreferences mPreferences;

    public Prefs(SharedPreferences preferences) {
        mPreferences = preferences;
    }

    public boolean getBoolean(PreferenceKeys.PreferenceKey<Boolean> key) {
        return mPreferences.getBoolean(key.key, key.defaultValue);
    }

    public int getInt(PreferenceKeys.PreferenceKey<Integer> key) {
        return mPreferences.getInt(key.key, key.defaultValue);
    }

    public String getString(PreferenceKeys.PreferenceKey<String> key) {
        return mPreferences.getString(key.key, key.defaultValue);
    }

    public long getLong(PreferenceKeys.PreferenceKey<Long> key) {
        return mPreferences.getLong(key.key, key.defaultValue);
    }

    public static boolean getBoolean(Context context, PreferenceKeys.PreferenceKey<Boolean> key) {
        return new Prefs(AnkiDroidApp.getSharedPrefs(context)).getBoolean(key);
    }

    public static int getInt(Context context, PreferenceKeys.PreferenceKey<Integer> key) {
        return new Prefs(AnkiDroidApp.getSharedPrefs(context)).getInt(key);
    }

    public static String getString(Context context, PreferenceKeys.PreferenceKey<String> key) {
        return new Prefs(AnkiDroidApp.getSharedPrefs(context)).getString(key);
    }

    public static long getLong(Context context, PreferenceKeys.PreferenceKey<Long> key) {
        return new Prefs(AnkiDroidApp.getSharedPrefs(context)).getLong(key);
    }

    public static boolean getBoolean(SharedPreferences preferences, PreferenceKeys.PreferenceKey<Boolean> key) {
        return new Prefs(preferences).getBoolean(key);
    }

    public static int getInt(SharedPreferences preferences, PreferenceKeys.PreferenceKey<Integer> key) {
        return new Prefs(preferences).getInt(key);
    }

    public static String getString(SharedPreferences preferences, PreferenceKeys.PreferenceKey<String> key) {
        return new Prefs(preferences).getString(key);
    }
    public static long getLong(SharedPreferences preferences, PreferenceKeys.PreferenceKey<Long> key) {
        return new Prefs(preferences).getLong(key);
    }
}
