/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.web;

import android.content.SharedPreferences;

import com.ichi2.libanki.sync.HostNum;

import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import timber.log.Timber;

public class PreferenceBackedHostNum extends HostNum {

    private final SharedPreferences mPreferences;

    public PreferenceBackedHostNum(Integer hostNum, SharedPreferences preferences) {
        super(hostNum);
        this.mPreferences = preferences;
    }

    public static PreferenceBackedHostNum fromPreferences(SharedPreferences preferences) {
        Integer hostNum = getHostNum(preferences);
        return new PreferenceBackedHostNum(hostNum, preferences);
    }

    /** Clearing hostNum whenever on log out/changes the server URL should avoid any problems with malicious servers*/
    @Override
    public void reset() {
        setHostNum(getDefaultHostNum());
    }

    @Override
    public Integer getHostNum() {
        return getHostNum(this.mPreferences);
    }

    @Override
    public void setHostNum(@Nullable Integer newHostNum) {
        Timber.d("Setting hostnum to %s", newHostNum);
        String prefValue = convertToPreferenceValue(newHostNum);
        mPreferences.edit().putString("hostNum", prefValue).apply();
        super.setHostNum(newHostNum);
    }

    private static Integer getHostNum(SharedPreferences preferences) {
        try {
            String hostNum = preferences.getString("hostNum", null);
            Timber.v("Obtained hostNum: %s", hostNum);
            return convertFromPreferenceValue(hostNum);
        } catch (Exception e) {
            Timber.e(e, "Failed to get hostNum");
            return getDefaultHostNum();
        }
    }

    private static Integer convertFromPreferenceValue(String hostNum) {
        if (hostNum == null) {
            return getDefaultHostNum();
        }
        try {
            return Integer.parseInt(hostNum);
        } catch (Exception e) {
            Timber.w(e);
            return getDefaultHostNum();
        }
    }


    @CheckResult
    private String convertToPreferenceValue(Integer newHostNum) {
        if (newHostNum == null) {
            return null;
        } else {
            return newHostNum.toString();
        }
    }
}
