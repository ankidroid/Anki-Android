
package com.ichi2.anim;

import com.ichi2.anki.R;
import org.json.JSONException;

public class Upgrade {

    public static boolean upgradeJSONIfNecessary(Collection col, JSONObject conf, String name, boolean defaultValue) {
	    boolean val = defaultValue;
	    try {
	            val = conf.getBoolean(name);
	    } catch (JSONException e) {
		    // workaround to repair wrong values from older libanki versions
		    conf.put(name, val);
	            mCol.save();
	    }
	    return val;
    }
}
