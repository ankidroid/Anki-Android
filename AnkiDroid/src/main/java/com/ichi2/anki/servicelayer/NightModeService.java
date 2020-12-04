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
import android.content.res.Configuration;

import com.ichi2.anki.AnkiDroidApp;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import timber.log.Timber;

public class NightModeService {

    private static final String NIGHT_MODE_PREFERENCE = "invertedColors";
    private static final String FOLLOW_SYSTEM_NIGHT_MODE_PREFERENCE = "followSystemNightMode";

    @CheckResult
    public static NightMode setupNightMode(Configuration configuration) {
        final SharedPreferences preferences = getPreferences();

        if (!isFollowingSystemNightMode(preferences)) {
            return NightMode.fromManualNightMode(getManualNightMode(preferences));
        }

        SystemNightMode systemNightMode = getSystemNightModeStatus(configuration);

        if (systemNightMode == SystemNightMode.UNDEFINED) {
            Timber.w("Disabling follow system night mode - could not get value");
            setFollowingSystemNightMode(false);
            return NightMode.fromManualFallback(getManualNightMode(preferences));
        }

        return NightMode.fromValidSystemNightMode(systemNightMode);
    }

    public static void setManualNightModeMode(boolean value) {
        final SharedPreferences preferences = getPreferences();
        Timber.i("Night mode was %s", value ? "enabled" : "disabled");
        preferences.edit().putBoolean(NIGHT_MODE_PREFERENCE, value).apply();
    }

    /** @return the new value of "Following System Night Mode" */
    public static boolean toggleFollowingSystemNightMode() {
        Timber.i("Toggling 'following system night mode'");
        boolean systemNightMode = isFollowingSystemNightMode(getPreferences());
        setFollowingSystemNightMode(!systemNightMode);
        return !systemNightMode;
    }

    public static void setFollowingSystemNightMode(boolean value) {
        final SharedPreferences preferences = getPreferences();
        Timber.i("Following system mode was %s", value ? "enabled" : "disabled");
        preferences.edit().putBoolean(FOLLOW_SYSTEM_NIGHT_MODE_PREFERENCE, value).apply();
    }

    protected static boolean isFollowingSystemNightMode(SharedPreferences preferences) {
        return preferences.getBoolean(FOLLOW_SYSTEM_NIGHT_MODE_PREFERENCE, true);
    }

    protected static boolean getManualNightMode(SharedPreferences preferences) {
        return preferences.getBoolean(NIGHT_MODE_PREFERENCE, false);
    }

    protected static SharedPreferences getPreferences() {
        return AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance());
    }

    private static SystemNightMode getSystemNightModeStatus(@NonNull Configuration configuration) {
        switch (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK) {
            case Configuration.UI_MODE_NIGHT_NO:
                return SystemNightMode.OFF;
            case Configuration.UI_MODE_NIGHT_YES:
                return SystemNightMode.ON;
            case Configuration.UI_MODE_NIGHT_UNDEFINED:
            default:
                return SystemNightMode.UNDEFINED;
        }
    }


    public static class NightMode {
        private final boolean mNightModeEnabled;
        private final boolean mFollowingSystemNightMode;
        private final boolean mErrorWithAutoNightMode;


        public NightMode(boolean nightModeEnabled, boolean followingSystemNightMode, boolean errorWithAutoNightMode) {
            mNightModeEnabled = nightModeEnabled;
            mErrorWithAutoNightMode = errorWithAutoNightMode;
            mFollowingSystemNightMode = followingSystemNightMode;
        }


        public static NightMode fromValidSystemNightMode(SystemNightMode systemNightMode) {
            return new NightMode(systemNightMode == SystemNightMode.ON, true, false);
        }

        public static NightMode fromManualNightMode(boolean nightModeEnabled) {
            return new NightMode(nightModeEnabled, false, false);
        }

        public static NightMode fromManualFallback(boolean nightModeEnabled) {
            return new NightMode(nightModeEnabled, false, true);
        }

        public boolean isNightModeEnabled() {
            return mNightModeEnabled;
        }

        public boolean isFollowingSystem() {
            return mFollowingSystemNightMode;
        }

        public boolean isUsingFallback() {
            return mErrorWithAutoNightMode;
        }
    }


    public enum SystemNightMode {
        ON,
        OFF,
        UNDEFINED
    }
}
