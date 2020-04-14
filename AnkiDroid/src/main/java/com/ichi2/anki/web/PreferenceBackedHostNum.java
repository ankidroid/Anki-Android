package com.ichi2.anki.web;

import android.content.SharedPreferences;

import com.ichi2.libanki.Consts;
import com.ichi2.libanki.sync.HostNum;

import timber.log.Timber;

public class PreferenceBackedHostNum extends HostNum {

    private final SharedPreferences mPreferences;

    public PreferenceBackedHostNum(String hostNum, SharedPreferences preferences) {
        super(hostNum);
        this.mPreferences = preferences;
    }

    public static PreferenceBackedHostNum fromPreferences(SharedPreferences preferences) {
        String hostNum = preferences.getString("hostNum", null);
        if (hostNum == null) {
            return new PreferenceBackedHostNum(Consts.DEFAULT_HOST_NUM, preferences);
        }
        return new PreferenceBackedHostNum(hostNum, preferences);
    }

    @Override
    public String getHostNum() {
        String hostNum = mPreferences.getString("hostNum", null);
        return hostNum != null ? hostNum : Consts.DEFAULT_HOST_NUM;
    }

    @Override
    public void setHostNum(String newHostNum) {
        Timber.d("Setting hostnum to %s", newHostNum);
        mPreferences.edit().putString("hostNum", newHostNum).apply();
        super.setHostNum(newHostNum);
    }
}
