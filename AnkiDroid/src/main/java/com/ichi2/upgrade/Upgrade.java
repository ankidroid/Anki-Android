
package com.ichi2.upgrade;

import com.ichi2.libanki.Collection;

import org.json.JSONException;
import org.json.JSONObject;

public class Upgrade {

    public static boolean upgradeJSONIfNecessary(Collection col, JSONObject conf, String name, boolean defaultValue) {
        boolean val = defaultValue;
        try {
            val = conf.getBoolean(name);
        } catch (JSONException e) {
            // workaround to repair wrong values from older libanki versions
            try {
                conf.put(name, val);
            } catch (JSONException e1) {
                // do nothing
            }
            col.save();
        }
        return val;
    }
}
