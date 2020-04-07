package com.ichi2.libanki.decks;

import com.ichi2.libanki.Collection;
import java.util.Arrays;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

public class Deck extends ReadOnlyJSONObject {
    public Deck(JSONObject json) {
        super(json);
    }

    public Deck(String json) {
        super(json);
    }

    public void setDesc(Object o) {
        put("desc", o);
    }

    public void setConf(long conf) {
        put("conf", conf);
    }

    public JSONArray getTerms() {
        return getJSONArray("terms");
    }

    public void setDelays(JSONArray delays) {
        put("delays", delays);
    }

    public void setDelaysNull() {
        put("delays", JSONObject.NULL);
    }

    public JSONArray getDelays() {
        return getJSONArray("delays");
    }

    public void setResched(boolean resched) {
        put("resched", resched);
    }

    public void setMid(long mid) {
        put("mid", mid);
    }

    public void setExtend(String key, int n) {
        put("extend" + key, n);
    }

    public JSONArray getToday(String key) {
        return getJSONArray(key + "Today");
    }

    public void removeEmpty() {
        remove("empty");
    }

    /* Methods updating schema versions */
    public void version3to4(Collection col) {
        put("dyn", 0);
        put("collapsed", false);
        col.getDecks().save();
    }

    public void version10to11(Collection col) {
        if (getInt("dyn") != 0) {
            int order = getInt("order");
            // failed order was removed
            if (order >= 5) {
                order -= 1;
            }
            JSONArray ja = new JSONArray(Arrays.asList(new Object[] { getString("search"),
                                                                      getInt("limit"), order }));
            put("terms", new JSONArray());
            getJSONArray("terms").put(0, ja);
            remove("search");
            remove("limit");
            remove("order");
            put("resched", true);
            put("return", true);
        } else {
            if (!has("extendNew")) {
                put("extendNew", 10);
                put("extendRev", 50);
            }
        }
        col.getDecks().save(this);
    }
}
