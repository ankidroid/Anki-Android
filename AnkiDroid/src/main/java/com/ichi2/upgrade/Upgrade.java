
package com.ichi2.upgrade;

import com.ichi2.libanki.Collection;

import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;

import timber.log.Timber;

public class Upgrade {

    public static boolean upgradeJSONIfNecessary(Collection col, JSONObject conf, String name, boolean defaultValue) {
        boolean val = defaultValue;
        try {
            val = conf.getBoolean(name);
        } catch (JSONException e) {
            Timber.w(e);
            // workaround to repair wrong values from older libanki versions
            try {
                conf.put(name, val);
            } catch (JSONException e1) {
                Timber.w(e1);
                // do nothing
            }
            col.save();
        }
        return val;
    }
}
