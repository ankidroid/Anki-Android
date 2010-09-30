/*
 * Copyright 2008 Tom Gibara
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tomgibara.android.veecheck.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.tomgibara.android.veecheck.VeecheckSettings;

/**
 * A convenient implementation of {@link VeecheckSettings} that stores its state in a {@link SharedPreferences}.
 * 
 * @author Tom Gibara
 */

public class PrefSettings implements VeecheckSettings {

    /**
     * The name of the shared preferences for this class, as supplied to
     * {@link Context#getSharedPreferences(String, int)}.
     */

    public static final String SHARED_PREFS_NAME = VeecheckSettings.class.getPackage().getName();

    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_PERIOD = "period";
    public static final String KEY_CHECK_URI = "check_uri";
    // was min_period, leaving to applications to deal with switch over
    public static final String KEY_CHECK_INTERVAL = "check_interval";

    public static final boolean DEFAULT_ENABLED = true;
    public static final long DEFAULT_PERIOD = 24 * 60 * 60 * 1000L;
    public static final String DEFAULT_CHECK_URI = null;
    public static final long DEFAULT_CHECK_INTERVAL = 3 * 24 * 60 * 60 * 1000L;


    /**
     * @param context the invoking context
     * @return the preferences into which these settings are persisted
     */

    public static SharedPreferences getSharedPrefs(Context context) {
        return context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
    }

    private final SharedPreferences prefs;


    public PrefSettings(SharedPreferences prefs) {
        this.prefs = prefs;
    }


    public PrefSettings(Context context) {
        this(getSharedPrefs(context));
    }


    @Override
    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED);
    }


    @Override
    public long getPeriod() {
        return prefs.getLong(KEY_PERIOD, DEFAULT_PERIOD);
    }


    @Override
    public String getCheckUri() {
        return prefs.getString(KEY_CHECK_URI, DEFAULT_CHECK_URI);
    }


    @Override
    public long getCheckInterval() {
        return prefs.getLong(KEY_CHECK_INTERVAL, DEFAULT_CHECK_INTERVAL);
    }

}
