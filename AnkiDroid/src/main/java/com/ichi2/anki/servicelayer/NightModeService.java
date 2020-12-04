/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.servicelayer;

import android.content.SharedPreferences;

import com.ichi2.anki.AnkiDroidApp;

import timber.log.Timber;

public class NightModeService {

    private static final String NIGHT_MODE_PREFERENCE = "invertedColors";

    public static NightMode getNightMode() {
        final SharedPreferences preferences = getPreferences();

        return new NightMode(getManualNightMode(preferences));
    }

    public static void setManualNightModeMode(boolean isNightMode) {
        final SharedPreferences preferences = getPreferences();
        Timber.i("Night mode was %s", isNightMode ? "enabled" : "disabled");
        preferences.edit().putBoolean(NIGHT_MODE_PREFERENCE, isNightMode).apply();
    }

    protected static boolean getManualNightMode(SharedPreferences preferences) {
        return preferences.getBoolean(NIGHT_MODE_PREFERENCE, false);
    }

    protected static SharedPreferences getPreferences() {
        return AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance());
    }

    public static class NightMode {
        private final boolean mNightModeEnabled;

        public NightMode(boolean nightModeEnabled) {
            mNightModeEnabled = nightModeEnabled;
        }

        public boolean isNightModeEnabled() {
            return mNightModeEnabled;
        }
    }
}
