package com.ichi2.libanki.decks;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

public class ReviewingConf extends ReadOnlyJSONObject {
    public ReviewingConf() {
        super();
    }

    public ReviewingConf(JSONObject json) {
        super(json);
    }

    public JSONArray getInts(){
        return getJSON().getJSONArray("ints");
    }

    public void putLeechAction(Integer action) {
        put("leechAction", action);
    }

    public void putMult(double mult) {
        put("mult", mult);
    }

    public void putDelays(JSONArray delays) {
        put("delays", delays);
    }
}
