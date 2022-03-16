//noinspection MissingCopyrightHeader #8659
package com.ichi2.upgrade;

import com.ichi2.libanki.Collection;

import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;

import timber.log.Timber;

public class Upgrade {

    public static boolean upgradeJSONIfNecessary(Collection col, String name, boolean defaultValue) {
        boolean val = defaultValue;
        try {
            val = col.get_config_boolean(name);
        } catch (JSONException e) {
            Timber.w(e);
            // workaround to repair wrong values from older libanki versions
            try {
                 col.set_config(name, val);
            } catch (JSONException e1) {
                Timber.w(e1);
                // do nothing
            }
            col.save();
        }
        return val;
    }
}
